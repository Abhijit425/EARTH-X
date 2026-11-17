package com.earthx.explorer.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.earthx.explorer.EarthXApplication
import com.earthx.explorer.data.database.FavoriteEntity
import com.earthx.explorer.data.database.RecentSearchEntity
import com.earthx.explorer.data.model.City
import com.earthx.explorer.data.model.Country
import com.earthx.explorer.data.model.GIBS_LAYERS
import com.earthx.explorer.data.model.LocalWeather
import com.earthx.explorer.data.model.Place
import com.earthx.explorer.data.model.PlaceKind
import com.earthx.explorer.data.model.Quake
import com.earthx.explorer.data.model.QuakeFeed
import com.earthx.explorer.data.model.QuakeRange
import com.earthx.explorer.data.model.WeatherGrid
import com.earthx.explorer.data.model.WeatherLayer
import com.earthx.explorer.data.repositories.AppSettings
import com.earthx.explorer.data.repositories.GeoRepository
import com.earthx.explorer.data.repositories.RenderQuality
import com.earthx.explorer.engine.GlobeBridge
import com.earthx.explorer.engine.GlobeController
import com.earthx.explorer.engine.GlobeEvent
import com.earthx.explorer.util.GeoMath
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneOffset

enum class Section(val title: String) {
    HOME("Home"), EXPLORE("Explore"), COUNTRIES("Countries"), WEATHER("Weather"), QUAKES("Earthquakes"),
    SATELLITE("Satellite"), TIME("Time"), FAVOURITES("Favourites"), SETTINGS("Settings")
}

sealed interface Load<out T> {
    data object Idle : Load<Nothing>
    data object Loading : Load<Nothing>
    data class Ok<T>(val data: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

sealed interface Selection {
    val lat: Double
    val lon: Double
    val title: String
    data class OfCountry(val country: Country, val stateName: String?, val stateType: String?, override val lat: Double, override val lon: Double) : Selection {
        override val title get() = country.name
    }
    data class OfCity(val city: City, val country: Country?) : Selection {
        override val lat get() = city.lat; override val lon get() = city.lon; override val title get() = city.name
    }
    data class OfPlace(val place: Place) : Selection {
        override val lat get() = place.lat; override val lon get() = place.lon; override val title get() = place.name
    }
    data class OfLocation(override val lat: Double, override val lon: Double) : Selection {
        override val title get() = GeoMath.formatCoords(lat, lon)
    }
    data class OfQuake(val quake: Quake) : Selection {
        override val lat get() = quake.lat; override val lon get() = quake.lon; override val title get() = quake.place
    }
}

data class CameraInfo(val lat: Double = 20.0, val lon: Double = 78.0, val altKm: Double = 14000.0, val level: Int = 1)
data class TimeInfo(val utcMs: Long = System.currentTimeMillis(), val playing: Boolean = true, val speed: Double = 1.0, val live: Boolean = true)
data class MeasureInfo(val active: Boolean = false, val aLat: Double? = null, val aLon: Double? = null, val bLat: Double? = null, val bLon: Double? = null, val km: Double? = null, val bearing: Double? = null)
data class SatelliteInfo(val enabled: Boolean = false, val layerId: String = GIBS_LAYERS.first().id, val date: LocalDate = LocalDate.now(ZoneOffset.UTC).minusDays(1),
                         val globalStatus: String? = null, val detailStatus: String? = null, val loading: Boolean = false)
data class Layers(val night: Boolean = true, val elevation: Boolean = false, val mountains: Boolean = false, val plates: Boolean = false,
                  val quakes: Boolean = true, val weather: WeatherLayer = WeatherLayer.NONE)

data class UiState(
    val section: Section = Section.HOME,
    val panelOpen: Boolean = true,
    val chromeVisible: Boolean = true,
    val showDetails: Boolean = false,
    val selection: Selection? = null,
    val selectionWeather: Load<LocalWeather> = Load.Idle,
    val camera: CameraInfo = CameraInfo(),
    val online: Boolean = true,
    val engineReady: Boolean = false,
    val progress: Int = 0,
    val settings: AppSettings = AppSettings(),
    val quakeRange: QuakeRange = QuakeRange.DAY,
    val quakeFeed: Load<QuakeFeed> = Load.Idle,
    val quakeMinMag: Double = 0.0,
    val weatherGrid: Load<WeatherGrid> = Load.Idle,
    val satellite: SatelliteInfo = SatelliteInfo(),
    val time: TimeInfo = TimeInfo(),
    val layers: Layers = Layers(),
    val measure: MeasureInfo = MeasureInfo(),
    val searchQuery: String = "",
    val searchResults: List<Place> = emptyList(),
    val fps: Int? = null,
    val countries: List<Country> = emptyList(),
    val geoReady: Boolean = false,
)

class EarthViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as EarthXApplication).container
    val controller = GlobeController()
    val bridge = GlobeBridge { onEngineEvent(it) }
    val imagery get() = c.imagery

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val favorites: StateFlow<List<FavoriteEntity>> = c.favorites.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recents: StateFlow<List<RecentSearchEntity>> = c.favorites.recents.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Engine URL; changing render quality reloads the engine. */
    private val _engineUrl = MutableStateFlow<String?>(null)
    val engineUrl: StateFlow<String?> = _engineUrl.asStateFlow()
    var geo: GeoRepository.Bundle? = null
        private set

    private var searchJob: Job? = null
    private var weatherJob: Job? = null
    private var quakeRefreshJob: Job? = null

    private val has8k: Boolean = runCatching { app.assets.list("web/textures")?.contains("earth_day_8k.jpg") == true }.getOrDefault(false)
    val ultraAvailable get() = has8k

    init {
        viewModelScope.launch {
            c.settings.settings.collect { s ->
                val old = _ui.value.settings
                _ui.update { it.copy(settings = s) }
                if (_engineUrl.value == null || old.quality != s.quality || old.lowPower != s.lowPower) rebuildEngineUrl(s)
                else pushSettings(s)
            }
        }
        viewModelScope.launch {
            c.connectivity.online.collect { on ->
                val was = _ui.value.online
                _ui.update { it.copy(online = on) }
                controller.toggle("setOnline", on)
                if (on && !was) { refreshQuakes(); if (_ui.value.layers.weather != WeatherLayer.NONE) loadWeatherGrid() }
            }
        }
        viewModelScope.launch {
            val g = c.geo.load()
            geo = g
            _ui.update { it.copy(countries = g.countries, geoReady = true) }
        }
        refreshQuakes()
        // keep the latest earthquakes fresh while the app is open
        quakeRefreshJob = viewModelScope.launch {
            while (true) { delay(5 * 60_000L); if (_ui.value.online && _ui.value.layers.quakes) refreshQuakes(silent = true) }
        }
    }

    private fun rebuildEngineUrl(s: AppSettings) {
        val q = if (s.quality == RenderQuality.ULTRA && !has8k) RenderQuality.HIGH else s.quality
        val net = "https://appassets.androidplatform.net/net/gibs?"
        _engineUrl.value = "https://appassets.androidplatform.net/assets/web/index.html" +
            "?quality=${q.key}&has8k=${if (has8k) 1 else 0}&lowPower=${if (s.lowPower) 1 else 0}" +
            "&online=${if (_ui.value.online) 1 else 0}&net=" + Uri.encode(net)
        _ui.update { it.copy(engineReady = false, progress = 0) }
    }

    // ------------------------------------------------------------------ engine events
    private fun onEngineEvent(e: GlobeEvent) {
        controller.ensureReady()
        when (e) {
            GlobeEvent.Ready -> {
                _ui.update { it.copy(engineReady = true, progress = 100) }
            }
            GlobeEvent.DataReady -> pushAll()
            is GlobeEvent.Progress -> _ui.update { it.copy(progress = e.pct) }
            is GlobeEvent.Camera -> _ui.update { it.copy(camera = CameraInfo(e.lat, e.lon, e.altKm, e.level)) }
            is GlobeEvent.LocationTapped -> onLocationTapped(e)
            is GlobeEvent.CityTapped -> geo?.cities?.getOrNull(e.index)?.let { selectCity(it, fly = false) }
            is GlobeEvent.PlaceTapped -> select(Selection.OfPlace(Place(e.name, if (e.kind == "peak") PlaceKind.PEAK else if (e.kind == "trench") PlaceKind.TRENCH else PlaceKind.LANDMARK, e.lat, e.lon)))
            is GlobeEvent.QuakeTapped -> quakeById(e.id)?.let { select(Selection.OfQuake(it)) }
            is GlobeEvent.Measure -> _ui.update { it.copy(measure = it.measure.copy(aLat = e.aLat, aLon = e.aLon, bLat = e.bLat, bLon = e.bLon, km = e.km, bearing = e.bearing)) }
            is GlobeEvent.Time -> _ui.update { it.copy(time = TimeInfo(e.utcMs, e.playing, e.speed, e.live)) }
            is GlobeEvent.Imagery -> _ui.update {
                val sat = it.satellite
                if (e.scope == "global") it.copy(satellite = sat.copy(loading = e.loading, globalStatus = if (e.ok) (if (e.loading) "Loading NASA GIBS imagery…" else "Live NASA GIBS imagery loaded") else e.message))
                else it.copy(satellite = sat.copy(detailStatus = if (e.ok) (if (e.loading) "Streaming regional detail…" else "Regional detail ≈ ${e.resM ?: 0} m/pixel (NASA GIBS)") else e.message))
            }
            is GlobeEvent.Screenshot -> viewModelScope.launch {
                runCatching { c.screenshots.save(e.dataUrl) }
                    .onSuccess { _messages.emit("Screenshot saved to $it") }
                    .onFailure { _messages.emit("Screenshot failed: ${it.message}") }
            }
            is GlobeEvent.Fps -> _ui.update { it.copy(fps = e.fps) }
            GlobeEvent.ContextLost -> _messages.tryEmit("Graphics context lost — recovering…")
            GlobeEvent.ContextRestored -> pushAll()
            GlobeEvent.SpaceTapped -> {}
            GlobeEvent.Interact -> {}
            is GlobeEvent.Error -> android.util.Log.w("EARTH-X", "engine: ${e.message}")
        }
    }

    private val _engineGen = MutableStateFlow(0)
    /** Incremented when the WebView renderer died; the UI recreates the WebView. */
    val engineGeneration: StateFlow<Int> = _engineGen.asStateFlow()

    fun onRenderProcessGone() {
        _messages.tryEmit("3D engine restarted")
        controller.detach()
        _ui.update { it.copy(engineReady = false, progress = 0) }
        _engineGen.value = _engineGen.value + 1
    }

    /** Re-send the complete state (after page load or context restore). */
    private fun pushAll() {
        val s = _ui.value
        pushSettings(s.settings)
        controller.toggle("setNight", s.layers.night)
        controller.toggle("setElevation", s.layers.elevation)
        controller.toggle("setMountains", s.layers.mountains)
        controller.toggle("setPlates", s.layers.plates)
        controller.toggle("showQuakes", s.layers.quakes)
        controller.toggle("setOnline", s.online)
        (s.quakeFeed as? Load.Ok)?.let { sendQuakes(it.data) }
        (s.weatherGrid as? Load.Ok)?.let { sendGrid(it.data) }
        controller.send("setWeatherLayer", JSONObject().put("layer", s.layers.weather.key))
        if (s.satellite.enabled) sendSatellite()
        if (!s.time.live) {
            controller.send("setTime", JSONObject().put("utcMs", s.time.utcMs))
            controller.send("setPlayback", JSONObject().put("playing", s.time.playing).put("speed", s.time.speed))
        }
        (s.selection as? Selection.OfCountry)?.let { controller.selectCountry(it.country.id, false) }
    }

    private fun pushSettings(s: AppSettings) {
        controller.toggle("setClouds", s.clouds)
        controller.toggle("setAtmosphere", s.atmosphere)
        controller.toggle("setBorders", s.borders)
        controller.toggle("setStates", s.states)
        controller.send("setLabels", JSONObject().put("all", s.labels))
        controller.send("setLights", JSONObject().put("v", if (s.nightLights) 1.0 else 0.0))
        controller.toggle("setDetail", s.detailImagery)
        controller.toggle("autoRotate", s.autoRotate)
    }

    // ------------------------------------------------------------------ navigation & chrome
    fun openSection(s: Section) = _ui.update {
        if (it.section == s && it.panelOpen && !it.showDetails) it.copy(panelOpen = false)
        else it.copy(section = s, panelOpen = true, showDetails = false)
    }
    fun closePanel() = _ui.update { it.copy(panelOpen = false) }
    fun toggleChrome() = _ui.update { it.copy(chromeVisible = !it.chromeVisible) }
    fun closeDetails() {
        _ui.update { it.copy(showDetails = false, selection = null, selectionWeather = Load.Idle) }
        controller.selectCountry(null, false)
    }

    // ------------------------------------------------------------------ selection
    private fun onLocationTapped(e: GlobeEvent.LocationTapped) {
        val country = e.countryId?.let { geo?.countryById?.get(it) }
        if (country != null) select(Selection.OfCountry(country, e.stateName, e.stateType, e.lat, e.lon))
        else select(Selection.OfLocation(e.lat, e.lon))
    }

    private fun select(sel: Selection) {
        _ui.update { it.copy(selection = sel, showDetails = true, panelOpen = true, selectionWeather = Load.Idle) }
        if (sel !is Selection.OfCountry) controller.selectCountry(null, false)
        if (sel is Selection.OfQuake) controller.send("selectQuake", JSONObject().put("id", sel.quake.id))
        loadSelectionWeather(sel.lat, sel.lon)
    }

    fun selectCountry(country: Country, fly: Boolean = true) {
        select(Selection.OfCountry(country, null, null, country.lat, country.lon))
        controller.selectCountry(country.id, false)
        if (fly) {
            if (country.bbox.size == 4) controller.flyToBBox(country.bbox, country.lat, country.lon) else controller.flyTo(country.lat, country.lon, 2500.0)
        }
        viewModelScope.launch { c.favorites.addRecent(Place(country.name, PlaceKind.COUNTRY, country.lat, country.lon, country.continent, country.id)) }
    }

    fun selectCity(city: City, fly: Boolean = true) {
        select(Selection.OfCity(city, geo?.countryById?.get(city.countryId)))
        if (fly) controller.flyTo(city.lat, city.lon, 120.0)
    }

    fun openPlace(p: Place) {
        viewModelScope.launch { c.favorites.addRecent(p) }
        when (p.kind) {
            PlaceKind.COUNTRY -> p.ref?.let { geo?.countryById?.get(it) }?.let { selectCountry(it); return }
            PlaceKind.CITY, PlaceKind.CAPITAL -> p.ref?.toIntOrNull()?.let { geo?.cities?.getOrNull(it) }?.let { selectCity(it); return }
            else -> {}
        }
        select(Selection.OfPlace(p))
        controller.flyTo(p.lat, p.lon, p.altKm ?: 900.0)
    }

    fun openRecent(r: RecentSearchEntity) = openPlace(Place(r.name, runCatching { PlaceKind.valueOf(r.kind) }.getOrDefault(PlaceKind.LOCATION), r.lat, r.lon, r.subtitle, r.ref))
    fun openFavorite(f: FavoriteEntity) = openPlace(Place(f.name, runCatching { PlaceKind.valueOf(f.kind) }.getOrDefault(PlaceKind.LOCATION), f.lat, f.lon, f.collection, f.ref))

    fun zoomToSelection() {
        when (val s = _ui.value.selection) {
            is Selection.OfCountry -> if (s.country.bbox.size == 4) controller.flyToBBox(s.country.bbox, s.country.lat, s.country.lon)
            is Selection.OfCity -> controller.flyTo(s.city.lat, s.city.lon, 60.0)
            null -> {}
            else -> controller.flyTo(s.lat, s.lon, 300.0)
        }
    }

    fun selectionAsPlace(): Place? = when (val s = _ui.value.selection) {
        is Selection.OfCountry -> Place(s.country.name, PlaceKind.COUNTRY, s.country.lat, s.country.lon, s.country.continent, s.country.id)
        is Selection.OfCity -> Place(s.city.name, if (s.city.isCapital) PlaceKind.CAPITAL else PlaceKind.CITY, s.city.lat, s.city.lon, s.city.countryName, s.city.index.toString())
        is Selection.OfPlace -> s.place
        is Selection.OfLocation -> Place("Location ${GeoMath.formatCoords(s.lat, s.lon)}", PlaceKind.LOCATION, s.lat, s.lon)
        is Selection.OfQuake -> Place("M${s.quake.magnitude ?: "?"} · ${s.quake.place}", PlaceKind.LOCATION, s.lat, s.lon)
        null -> null
    }

    fun toggleFavorite(collection: String = "Favourites") {
        val p = selectionAsPlace() ?: return
        viewModelScope.launch {
            val added = c.favorites.toggle(p, collection)
            _messages.emit(if (added) "Saved to $collection" else "Removed from favourites")
        }
    }
    fun isFavorite(name: String) = favorites.value.any { it.name == name }
    fun removeFavorite(id: Long) = viewModelScope.launch { c.favorites.remove(id) }
    fun moveFavorite(id: Long, collection: String) = viewModelScope.launch { c.favorites.move(id, collection) }
    fun clearRecents() = viewModelScope.launch { c.favorites.clearRecents() }

    // ------------------------------------------------------------------ search
    fun onSearch(q: String) {
        _ui.update { it.copy(searchQuery = q) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(120)
            val r = if (q.isBlank()) emptyList() else c.geo.search(q)
            _ui.update { it.copy(searchResults = r) }
        }
    }

    // ------------------------------------------------------------------ weather
    private fun loadSelectionWeather(lat: Double, lon: Double, force: Boolean = false) {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            _ui.update { it.copy(selectionWeather = Load.Loading) }
            val r = runCatching { c.weather.local(lat, lon, _ui.value.online, force) }
            _ui.update { s ->
                val sel = s.selection
                if (sel == null || sel.lat != lat || sel.lon != lon) s
                else s.copy(selectionWeather = r.fold({ Load.Ok(it) }, { Load.Err(it.message ?: "Weather unavailable") }))
            }
        }
    }
    fun refreshSelectionWeather() { _ui.value.selection?.let { loadSelectionWeather(it.lat, it.lon, force = true) } }

    fun setWeatherLayer(l: WeatherLayer) {
        _ui.update { it.copy(layers = it.layers.copy(weather = l)) }
        controller.send("setWeatherLayer", JSONObject().put("layer", l.key))
        if (l != WeatherLayer.NONE && _ui.value.weatherGrid !is Load.Ok) loadWeatherGrid()
    }

    fun loadWeatherGrid(force: Boolean = false) = viewModelScope.launch {
        _ui.update { it.copy(weatherGrid = Load.Loading) }
        runCatching { c.weather.grid(_ui.value.online, force) }
            .onSuccess { g -> _ui.update { it.copy(weatherGrid = Load.Ok(g)) }; sendGrid(g) }
            .onFailure { e -> _ui.update { it.copy(weatherGrid = Load.Err(e.message ?: "Weather grid unavailable")) } }
    }

    private fun sendGrid(g: WeatherGrid) {
        fun arr(l: List<Number?>) = JSONArray().apply { l.forEach { put(it ?: JSONObject.NULL) } }
        controller.send("setWeatherGrid", JSONObject()
            .put("lats", arr(g.lats)).put("lons", arr(g.lons)).put("temp", arr(g.temp)).put("cloud", arr(g.cloud))
            .put("precip", arr(g.precip)).put("wspd", arr(g.windSpeed)).put("wdir", arr(g.windDir)).put("code", arr(g.code)))
    }

    // ------------------------------------------------------------------ earthquakes
    fun setQuakeRange(r: QuakeRange) { _ui.update { it.copy(quakeRange = r) }; refreshQuakes() }
    fun setQuakeMinMag(m: Double) {
        _ui.update { it.copy(quakeMinMag = m) }
        (_ui.value.quakeFeed as? Load.Ok)?.let { sendQuakes(it.data) }
    }
    fun refreshQuakes(silent: Boolean = false, force: Boolean = false) = viewModelScope.launch {
        val range = _ui.value.quakeRange
        if (!silent) _ui.update { it.copy(quakeFeed = Load.Loading) }
        runCatching { c.quakes.feed(range, _ui.value.online, force) }
            .onSuccess { f -> if (_ui.value.quakeRange == range) { _ui.update { it.copy(quakeFeed = Load.Ok(f)) }; sendQuakes(f) } }
            .onFailure { e -> if (!silent) _ui.update { it.copy(quakeFeed = Load.Err(e.message ?: "USGS feed unavailable")) } }
    }
    private fun sendQuakes(f: QuakeFeed) {
        val min = _ui.value.quakeMinMag
        val arr = JSONArray()
        f.events.filter { (it.magnitude ?: -9.0) >= min }.forEach { q ->
            arr.put(JSONObject().put("id", q.id).put("mag", q.magnitude ?: 0.0).put("depth", q.depthKm ?: 0.0).put("lat", q.lat).put("lon", q.lon))
        }
        controller.send("setQuakes", JSONObject().put("events", arr))
    }
    fun visibleQuakes(): List<Quake> {
        val f = (_ui.value.quakeFeed as? Load.Ok)?.data ?: return emptyList()
        return f.events.filter { (it.magnitude ?: -9.0) >= _ui.value.quakeMinMag }
    }
    private fun quakeById(id: String) = (_ui.value.quakeFeed as? Load.Ok)?.data?.events?.firstOrNull { it.id == id }
    fun openQuake(q: Quake) { select(Selection.OfQuake(q)); controller.flyTo(q.lat, q.lon, 1500.0) }

    // ------------------------------------------------------------------ layers
    fun setLayers(transform: (Layers) -> Layers) {
        val old = _ui.value.layers
        val n = transform(old)
        _ui.update { it.copy(layers = n) }
        if (old.night != n.night) controller.toggle("setNight", n.night)
        if (old.elevation != n.elevation) controller.toggle("setElevation", n.elevation)
        if (old.mountains != n.mountains) controller.toggle("setMountains", n.mountains)
        if (old.plates != n.plates) controller.toggle("setPlates", n.plates)
        if (old.quakes != n.quakes) { controller.toggle("showQuakes", n.quakes); if (n.quakes && _ui.value.quakeFeed !is Load.Ok) refreshQuakes() }
        if (old.weather != n.weather) setWeatherLayer(n.weather)
    }

    // ------------------------------------------------------------------ satellite
    fun setSatellite(enabled: Boolean? = null, layerId: String? = null, date: LocalDate? = null) {
        _ui.update { s -> s.copy(satellite = s.satellite.copy(enabled = enabled ?: s.satellite.enabled, layerId = layerId ?: s.satellite.layerId, date = date ?: s.satellite.date)) }
        if (!_ui.value.online && _ui.value.satellite.enabled) _messages.tryEmit("Offline — satellite imagery needs a connection (bundled texture shown)")
        sendSatellite()
    }
    private fun sendSatellite() {
        val s = _ui.value.satellite
        val layer = GIBS_LAYERS.firstOrNull { it.id == s.layerId }
        controller.send("setSatellite", JSONObject().put("on", s.enabled).put("layer", s.layerId)
            .put("date", if (layer?.daily == true) s.date.toString() else JSONObject.NULL))
    }

    // ------------------------------------------------------------------ time
    fun setLiveTime() = controller.send("setPlayback", JSONObject().put("live", true))
    fun setSimTime(utcMs: Long) = controller.send("setTime", JSONObject().put("utcMs", utcMs))
    fun setPlayback(playing: Boolean? = null, speed: Double? = null) {
        val p = JSONObject()
        if (playing != null) p.put("playing", playing)
        if (speed != null) { p.put("speed", speed); if (_ui.value.time.live) controller.send("setTime", JSONObject().put("utcMs", _ui.value.time.utcMs)) }
        controller.send("setPlayback", p)
    }

    // ------------------------------------------------------------------ tools
    fun setMeasure(on: Boolean) {
        _ui.update { it.copy(measure = MeasureInfo(active = on)) }
        controller.toggle("measure", on)
        if (on) _messages.tryEmit("Measure: tap two points on the globe")
    }
    fun screenshot() = controller.send("screenshot")
    fun zoom(dir: Int) = controller.send("zoom", JSONObject().put("dir", dir))
    fun rotate(dLon: Double, dLat: Double = 0.0) = controller.send("rotate", JSONObject().put("dLon", dLon).put("dLat", dLat))
    fun resetCamera() = controller.send("resetCamera")

    // ------------------------------------------------------------------ settings
    fun updateSettings(t: (AppSettings) -> AppSettings) = viewModelScope.launch { c.settings.update(t) }
    fun clearCache() = viewModelScope.launch {
        c.dataCache.clear(); c.imageCache.clear()
        _messages.emit("Cached data cleared")
    }
    suspend fun cacheSize(): Long = c.dataCache.sizeBytes() + c.imageCache.sizeBytes()
}
