@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.earthx.explorer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.earthx.explorer.BuildConfig
import com.earthx.explorer.data.model.Place
import com.earthx.explorer.data.model.PlaceKind
import com.earthx.explorer.data.repositories.RenderQuality
import com.earthx.explorer.ui.components.InfoRow
import com.earthx.explorer.ui.components.ListItemRow
import com.earthx.explorer.ui.components.PanelHeader
import com.earthx.explorer.ui.components.SectionLabel
import com.earthx.explorer.ui.components.SourceNote
import com.earthx.explorer.ui.components.StatTile
import com.earthx.explorer.ui.components.ToggleRow
import com.earthx.explorer.ui.theme.EX
import com.earthx.explorer.util.GeoMath
import com.earthx.explorer.viewmodels.EarthViewModel
import com.earthx.explorer.viewmodels.Load
import com.earthx.explorer.viewmodels.Section
import com.earthx.explorer.viewmodels.UiState

fun kindIcon(k: PlaceKind): ImageVector = when (k) {
    PlaceKind.COUNTRY -> Icons.Rounded.Flag
    PlaceKind.CAPITAL, PlaceKind.CITY -> Icons.Rounded.LocationCity
    PlaceKind.CONTINENT -> Icons.Rounded.Public
    PlaceKind.MOUNTAIN, PlaceKind.PEAK -> Icons.Rounded.Terrain
    PlaceKind.PLATEAU, PlaceKind.DESERT -> Icons.Rounded.Landscape
    PlaceKind.OCEAN, PlaceKind.TRENCH -> Icons.Rounded.Waves
    PlaceKind.SEA -> Icons.Rounded.Water
    else -> Icons.Rounded.Place
}

@Composable
fun PanelHost(vm: EarthViewModel, ui: UiState) {
    if (ui.showDetails && ui.selection != null) { DetailsPanel(vm, ui); return }
    when (ui.section) {
        Section.HOME -> HomePanel(vm, ui)
        Section.EXPLORE -> ExplorePanel(vm, ui)
        Section.COUNTRIES -> CountriesPanel(vm, ui)
        Section.WEATHER -> WeatherPanel(vm, ui)
        Section.QUAKES -> QuakesPanel(vm, ui)
        Section.SATELLITE -> SatellitePanel(vm, ui)
        Section.TIME -> TimePanel(vm, ui)
        Section.FAVOURITES -> FavouritesPanel(vm, ui)
        Section.SETTINGS -> SettingsPanel(vm, ui)
    }
}

@Composable
fun CloseButton(onClick: () -> Unit) = IconButton(onClick = onClick) { Icon(Icons.Rounded.Close, "Close panel", tint = EX.TextDim) }

// ------------------------------------------------------------------ HOME: dashboard
@Composable
fun HomePanel(vm: EarthViewModel, ui: UiState) {
    val g = vm.geo
    LazyColumn(Modifier.fillMaxSize()) {
        item { PanelHeader("Living Planet", "Earth information dashboard") { CloseButton(vm::closePanel) } }
        item {
            val quakeCount = (ui.quakeFeed as? Load.Ok)?.data?.events?.size
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Countries", "${ui.countries.size}", Modifier.weight(1f))
                StatTile("Cities in atlas", "${g?.cities?.size ?: "—"}", Modifier.weight(1f))
                StatTile("Quakes · ${ui.quakeRange.label.lowercase()}", quakeCount?.toString() ?: if (ui.online) "…" else "offline", Modifier.weight(1f), accent = EX.Danger)
            }
        }
        item {
            val (sLat, sLon) = GeoMath.subsolarPoint(ui.time.utcMs)
            SectionLabel("Sun right now")
            InfoRow("Sub-solar point", GeoMath.formatCoords(sLat, sLon))
            InfoRow("Simulation time", com.earthx.explorer.ui.formatUtc(ui.time.utcMs))
        }
        g?.curated?.let { cur ->
            item { SectionLabel("Planet Earth — key figures") }
            items(cur.earthFacts) { f -> InfoRow(f.key, f.value) }
            item { SourceNote("Source: ${cur.sources["earth"] ?: "NASA NSSDCA Earth Fact Sheet"}") }
        }
        item {
            SectionLabel("Quick journeys")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Place("Himalayas", PlaceKind.MOUNTAIN, 28.6, 84.5, altKm = 1400.0),
                    Place("Alps", PlaceKind.MOUNTAIN, 46.4, 9.5, altKm = 900.0),
                    Place("Andes", PlaceKind.MOUNTAIN, -20.0, -68.0, altKm = 3500.0),
                    Place("Rocky Mountains", PlaceKind.MOUNTAIN, 44.0, -110.0, altKm = 2500.0),
                    Place("Tibetan Plateau", PlaceKind.PLATEAU, 33.0, 88.0, altKm = 2200.0),
                    Place("Ethiopian Highlands", PlaceKind.MOUNTAIN, 9.5, 38.5, altKm = 1500.0),
                    Place("Mariana Trench", PlaceKind.TRENCH, 11.35, 142.2, altKm = 1200.0),
                    Place("Sahara", PlaceKind.DESERT, 23.0, 12.0, altKm = 4500.0),
                ).forEach { p ->
                    FilterChip(selected = false, onClick = { vm.openPlace(p) }, label = { Text(p.name) },
                        leadingIcon = { Icon(kindIcon(p.kind), null, Modifier.padding(0.dp), tint = EX.Accent) })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ------------------------------------------------------------------ EXPLORE: search + education
@Composable
fun ExplorePanel(vm: EarthViewModel, ui: UiState) {
    val recents by vm.recents.collectAsState()
    val cur = vm.geo?.curated
    var topic by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        item { PanelHeader("Explore", "Search and educational journeys") { CloseButton(vm::closePanel) } }
        item {
            OutlinedTextField(
                value = ui.searchQuery, onValueChange = vm::onSearch, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = EX.Accent) },
                placeholder = { Text("Countries, capitals, cities, ranges, oceans…") },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EX.Accent, unfocusedBorderColor = EX.StrokeSoft, cursorColor = EX.Accent),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        if (ui.searchQuery.isNotBlank()) {
            items(ui.searchResults) { p -> ListItemRow(p.name, p.subtitle, leadingIcon = kindIcon(p.kind), trailing = p.kind.label, trailingColor = EX.TextFaint) { vm.openPlace(p) } }
        } else {
            if (recents.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Recent searches", Modifier.weight(1f))
                        TextButton(onClick = { vm.clearRecents() }) { Text("Clear", color = EX.TextDim) }
                    }
                }
                items(recents.take(8)) { r ->
                    val k = runCatching { PlaceKind.valueOf(r.kind) }.getOrDefault(PlaceKind.LOCATION)
                    ListItemRow(r.name, r.subtitle, leadingIcon = kindIcon(k)) { vm.openRecent(r) }
                }
            }
            if (cur != null) {
                item { SectionLabel("Educational mode") }
                items(cur.topics) { t ->
                    Column(Modifier.fillMaxWidth().clickable {
                        topic = if (topic == t.id) null else t.id
                        when (t.id) {
                            "plates" -> vm.setLayers { it.copy(plates = true, quakes = true) }
                            "mountains" -> vm.setLayers { it.copy(mountains = true) }
                            "trenches" -> vm.controller.flyTo(11.35, 142.2, 2500.0)
                            "daynight" -> vm.openSection(Section.TIME)
                        }
                    }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Text(t.title, style = MaterialTheme.typography.titleMedium, color = if (topic == t.id) EX.Accent else EX.Text)
                        if (topic == t.id) Text(t.body, style = MaterialTheme.typography.bodyMedium, color = EX.TextDim, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                item { SectionLabel("Continents") }
                items(cur.continents) { c ->
                    ListItemRow(c.name, "${c.area} · ${c.fact}", leadingIcon = Icons.Rounded.Public) {
                        vm.openPlace(Place(c.name, PlaceKind.CONTINENT, c.lat, c.lon, c.fact, altKm = 9000.0))
                    }
                }
                item { SectionLabel("Highest summits") }
                items(cur.peaks) { p ->
                    ListItemRow(p.name, p.range, leadingIcon = Icons.Rounded.Terrain, trailing = "%,d m".format(p.elevationM)) {
                        vm.openPlace(Place(p.name, PlaceKind.PEAK, p.lat, p.lon, "%,d m · %s".format(p.elevationM, p.range), altKm = 80.0))
                    }
                }
                item { SectionLabel("Ocean trenches") }
                items(cur.trenches) { t ->
                    ListItemRow(t.name, "${t.ocean} Ocean", leadingIcon = Icons.Rounded.Waves, trailing = "≈%,d m".format(t.depthM)) {
                        vm.openPlace(Place(t.name, PlaceKind.TRENCH, t.lat, t.lon, "≈%,d m deep".format(t.depthM), altKm = 900.0))
                    }
                }
                item { SourceNote("Depths are approximate; surveys differ. Detail beyond regional scale is limited by the bundled datasets — EARTH-X does not show street-level imagery.") }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ------------------------------------------------------------------ COUNTRIES directory
@Composable
fun CountriesPanel(vm: EarthViewModel, ui: UiState) {
    var filter by remember { mutableStateOf("") }
    var continent by remember { mutableStateOf<String?>(null) }
    val continents = remember(ui.countries) { ui.countries.map { it.continent }.filter { it.isNotBlank() }.distinct().sorted() }
    val list = remember(ui.countries, filter, continent) {
        val f = filter.trim().lowercase()
        ui.countries.filter { (continent == null || it.continent == continent) && (f.isEmpty() || it.name.lowercase().contains(f) || it.capital.lowercase().contains(f)) }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { PanelHeader("Countries", "${list.size} of ${ui.countries.size} · works offline") { CloseButton(vm::closePanel) } }
        item {
            OutlinedTextField(value = filter, onValueChange = { filter = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = EX.Accent) }, placeholder = { Text("Filter by country or capital") },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EX.Accent, unfocusedBorderColor = EX.StrokeSoft, cursorColor = EX.Accent),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = continent == null, onClick = { continent = null }, label = { Text("All") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EX.Accent.copy(alpha = 0.2f)))
                continents.forEach { c ->
                    FilterChip(selected = continent == c, onClick = { continent = if (continent == c) null else c }, label = { Text(c) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EX.Accent.copy(alpha = 0.2f)))
                }
            }
        }
        if (!ui.geoReady) item { com.earthx.explorer.ui.components.LoadingRow("Loading atlas…") }
        items(list, key = { it.id }) { c ->
            ListItemRow(c.name, listOf(c.capital, c.continent).filter { it.isNotBlank() }.joinToString(" · "), leading = c.flag.ifBlank { "🏳️" },
                trailing = GeoMath.formatPopulation(c.population).replace(" million", " M").replace(" billion", " B")) { vm.selectCountry(c) }
        }
        item { SourceNote("Boundaries & population estimates: Natural Earth (public domain). Capitals, currencies, languages, area: world-countries (ODbL). Population figures are dataset estimates for the year shown.") }
    }
}

// ------------------------------------------------------------------ FAVOURITES & collections
@Composable
fun FavouritesPanel(vm: EarthViewModel, ui: UiState) {
    val favs by vm.favorites.collectAsState()
    var newCollection by remember { mutableStateOf("") }
    val groups = favs.groupBy { it.collection }
    LazyColumn(Modifier.fillMaxSize()) {
        item { PanelHeader("Favourites", "${favs.size} saved places · stored on this tablet") { CloseButton(vm::closePanel) } }
        if (favs.isEmpty()) item {
            SourceNote("Nothing saved yet. Select a country, city or any point on the globe and tap the ☆ in its details panel.")
        }
        groups.forEach { (name, list) ->
            item { SectionLabel("$name · ${list.size}") }
            items(list, key = { it.id }) { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val k = runCatching { PlaceKind.valueOf(f.kind) }.getOrDefault(PlaceKind.LOCATION)
                        ListItemRow(f.name, "${k.label} · ${GeoMath.formatCoords(f.lat, f.lon)}", leadingIcon = kindIcon(k)) { vm.openFavorite(f) }
                    }
                    IconButton(onClick = { vm.removeFavorite(f.id) }) { Icon(Icons.Rounded.Delete, "Remove", tint = EX.TextFaint) }
                }
            }
        }
        item {
            SectionLabel("Collections")
            SourceNote("Save the current selection into a named collection (e.g. “Trip 2027”, “Volcanoes”).")
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = newCollection, onValueChange = { newCollection = it.take(40) }, singleLine = true,
                    placeholder = { Text("Collection name") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp))
                TextButton(enabled = ui.selection != null && newCollection.isNotBlank(), onClick = { vm.toggleFavorite(newCollection.trim()) }) {
                    Text("Save selection")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ------------------------------------------------------------------ SETTINGS & attribution
@Composable
fun SettingsPanel(vm: EarthViewModel, ui: UiState) {
    val s = ui.settings
    var cache by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(Unit) { cache = vm.cacheSize() }
    LazyColumn(Modifier.fillMaxSize()) {
        item { PanelHeader("Settings", "EARTH-X ${BuildConfig.VERSION_NAME}") { CloseButton(vm::closePanel) } }
        item {
            SectionLabel("Rendering quality")
            RenderQuality.entries.forEach { q ->
                val disabled = q == RenderQuality.ULTRA && !vm.ultraAvailable
                ListItemRow(q.label + if (disabled) " (8K textures not bundled — uses 4K)" else "", q.description,
                    leadingIcon = if (s.quality == q) Icons.Rounded.WbSunny else null,
                    trailing = if (s.quality == q) "Active" else null, trailingColor = EX.Accent) { vm.updateSettings { it.copy(quality = q) } }
            }
            ToggleRow("Low-power mode", "Caps at 30 fps and renders only when something changes", s.lowPower) { v -> vm.updateSettings { it.copy(lowPower = v) } }
            ToggleRow("Show frame rate", null, s.showFps) { v -> vm.updateSettings { it.copy(showFps = v) } }
        }
        item {
            SectionLabel("Globe")
            ToggleRow("Labels", null, s.labels) { v -> vm.updateSettings { it.copy(labels = v) } }
            ToggleRow("Country borders", null, s.borders) { v -> vm.updateSettings { it.copy(borders = v) } }
            ToggleRow("State & province borders", "Shown when zoomed in (level 4+)", s.states) { v -> vm.updateSettings { it.copy(states = v) } }
            ToggleRow("Clouds", "Bundled cloud texture (decorative, not live)", s.clouds) { v -> vm.updateSettings { it.copy(clouds = v) } }
            ToggleRow("Atmosphere glow", null, s.atmosphere) { v -> vm.updateSettings { it.copy(atmosphere = v) } }
            ToggleRow("City lights at night", null, s.nightLights) { v -> vm.updateSettings { it.copy(nightLights = v) } }
            ToggleRow("Regional detail imagery", "Streams NASA GIBS imagery for the area under the camera", s.detailImagery) { v -> vm.updateSettings { it.copy(detailImagery = v) } }
            ToggleRow("Slow auto-rotation", null, s.autoRotate) { v -> vm.updateSettings { it.copy(autoRotate = v) } }
        }
        item {
            SectionLabel("Storage")
            InfoRow("Cached data", if (cache < 0) "…" else "%.1f MB".format(cache / 1_048_576.0))
            TextButton(onClick = { vm.clearCache(); cache = 0 }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Clear cached weather, quakes & imagery") }
        }
        item {
            SectionLabel("Data sources & attribution")
            listOf(
                "Earth imagery: NASA Visible Earth — Blue Marble & Black Marble (public domain).",
                "Surface relief / water / cloud textures: three.js examples & three-globe (MIT), originating from NASA and Solar System Scope (CC BY 4.0).",
                "Satellite imagery: NASA Global Imagery Browse Services (GIBS), part of NASA's Earth Science Data and Information System (ESDIS).",
                "Weather: Open-Meteo.com (CC BY 4.0) — model data, not station observations.",
                "Earthquakes: U.S. Geological Survey Earthquake Hazards Program real-time feeds (public domain).",
                "Boundaries, cities, geography: Natural Earth (public domain).",
                "Country facts: world-countries by Mohammed Le Doze (ODbL 1.0).",
                "Tectonic plates: PB2002 (Bird, 2003) via Hugo Ahlenius / Nordpil (ODC-By 1.0).",
                "3D engine: three.js (MIT).",
            ).forEach { SourceNote("• $it") }
            HorizontalDivider(color = EX.StrokeSoft, modifier = Modifier.padding(16.dp))
            SourceNote("EARTH-X never fabricates live data. When a source is unreachable you will see cached values with their retrieval time, or a clear message.")
            Spacer(Modifier.height(24.dp))
        }
    }
}
