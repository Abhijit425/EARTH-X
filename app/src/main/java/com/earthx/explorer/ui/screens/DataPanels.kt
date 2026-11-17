@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.earthx.explorer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.earthx.explorer.data.model.GIBS_LAYERS
import com.earthx.explorer.data.model.LocalWeather
import com.earthx.explorer.data.model.QuakeRange
import com.earthx.explorer.data.model.WeatherLayer
import com.earthx.explorer.ui.SPEEDS
import com.earthx.explorer.ui.components.ErrorRow
import com.earthx.explorer.ui.components.InfoRow
import com.earthx.explorer.ui.components.ListItemRow
import com.earthx.explorer.ui.components.LoadingRow
import com.earthx.explorer.ui.components.PanelHeader
import com.earthx.explorer.ui.components.SectionLabel
import com.earthx.explorer.ui.components.SourceNote
import com.earthx.explorer.ui.components.StatTile
import com.earthx.explorer.ui.components.StatusPill
import com.earthx.explorer.ui.components.ToggleRow
import com.earthx.explorer.ui.formatUtc
import com.earthx.explorer.ui.speedLabel
import com.earthx.explorer.ui.theme.EX
import com.earthx.explorer.util.GeoMath
import com.earthx.explorer.util.WeatherCodes
import com.earthx.explorer.viewmodels.EarthViewModel
import com.earthx.explorer.viewmodels.Load
import com.earthx.explorer.viewmodels.Selection
import com.earthx.explorer.viewmodels.UiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.roundToInt

private fun ago(ms: Long): String {
    val m = ((System.currentTimeMillis() - ms) / 60_000).coerceAtLeast(0)
    return when { m < 1 -> "just now"; m < 60 -> "$m min ago"; m < 1440 -> "${m / 60} h ago"; else -> "${m / 1440} d ago" }
}
private fun f1(v: Double?) = if (v == null) "—" else String.format(Locale.US, "%.1f", v)
private fun temp(v: Double?, imperial: Boolean) = if (v == null) "—" else if (imperial) "${(v * 9 / 5 + 32).roundToInt()} °F" else "${v.roundToInt()} °C"

// ------------------------------------------------------------------ DETAILS (country / city / point / quake)
@Composable
fun DetailsPanel(vm: EarthViewModel, ui: UiState) {
    val sel = ui.selection ?: return
    val favs by vm.favorites.collectAsState()
    val place = vm.selectionAsPlace()
    val isFav = place != null && favs.any { it.name == place.name && it.kind == place.kind.name }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            val subtitle = when (sel) {
                is Selection.OfCountry -> listOfNotNull(sel.country.official?.takeIf { it != sel.country.name }, sel.country.continent).joinToString(" · ")
                is Selection.OfCity -> listOf(sel.city.admin1, sel.city.countryName).filter { it.isNotBlank() }.distinct().joinToString(", ")
                is Selection.OfPlace -> sel.place.subtitle.ifBlank { sel.place.kind.label }
                is Selection.OfQuake -> "Earthquake · USGS"
                is Selection.OfLocation -> "Selected location"
            }
            val flag = (sel as? Selection.OfCountry)?.country?.flag ?: (sel as? Selection.OfCity)?.country?.flag ?: ""
            PanelHeader((if (flag.isNotBlank()) "$flag  " else "") + sel.title, subtitle) {
                IconButton(onClick = { vm.toggleFavorite() }) { Icon(if (isFav) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favourite", tint = if (isFav) EX.Amber else EX.TextDim) }
                IconButton(onClick = { vm.closeDetails() }) { Icon(Icons.Rounded.Close, "Close details", tint = EX.TextDim) }
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.zoomToSelection() }, colors = ButtonDefaults.buttonColors(containerColor = EX.Accent.copy(alpha = 0.18f), contentColor = EX.Accent)) {
                    Icon(Icons.Rounded.MyLocation, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Zoom to")
                }
            }
        }
        when (sel) {
            is Selection.OfCountry -> item { CountryFacts(sel) }
            is Selection.OfCity -> item { CityFacts(sel, ui) }
            is Selection.OfQuake -> item { QuakeFacts(sel) }
            else -> {}
        }
        item {
            SectionLabel("Location")
            InfoRow("Coordinates", GeoMath.formatCoords(sel.lat, sel.lon))
            val zone = when (sel) {
                is Selection.OfCity -> sel.city.timeZone
                is Selection.OfCountry -> sel.country.timeZone
                else -> (ui.selectionWeather as? Load.Ok)?.data?.timeZone
            }
            val (lt, exact) = GeoMath.localTimeText(ui.time.utcMs, zone, sel.lon)
            InfoRow(if (exact) "Local time" else "Approx. local time", lt)
            val elev = GeoMath.solarElevation(sel.lat, sel.lon, ui.time.utcMs)
            InfoRow("Sun", "${GeoMath.daylightLabel(elev)} · elevation ${"%.1f".format(Locale.US, elev)}°")
            if (sel is Selection.OfCountry && sel.country.timeZone.isNotBlank()) SourceNote("Local time shown for the capital's time zone; large countries span several zones.")
        }
        item { WeatherBlock(vm, ui) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CountryFacts(sel: Selection.OfCountry) {
    val c = sel.country
    if (sel.stateName != null) {
        SectionLabel("Tapped region")
        InfoRow(sel.stateType?.ifBlank { "Region" } ?: "Region", sel.stateName)
    }
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Capital", c.capital.ifBlank { "—" }, Modifier.weight(1f))
        StatTile("Population", GeoMath.formatPopulation(c.population) + (c.populationYear?.let { " ($it)" } ?: ""), Modifier.weight(1f))
        StatTile("Area", GeoMath.formatArea(c.areaKm2) + if (c.areaComputed) " ≈" else "", Modifier.weight(1f))
    }
    SectionLabel("Country profile")
    InfoRow("Continent", c.continent)
    InfoRow("Sub-region", c.subregion)
    InfoRow("Centre (label point)", GeoMath.formatCoords(c.lat, c.lon))
    if (c.currencies.isNotEmpty()) InfoRow("Currency", c.currencies.joinToString("\n"))
    if (c.languages.isNotEmpty()) InfoRow("Languages", c.languages.joinToString(", "))
    if (c.population != null && (c.areaKm2 ?: 0.0) > 0) InfoRow("Population density", "%,.0f / km²".format(Locale.US, c.population / c.areaKm2!!))
    c.gdpMillionUsd?.takeIf { it > 0 }?.let { InfoRow("GDP (estimate)", "US$ %,.1f billion".format(Locale.US, it / 1000.0)) }
    c.landlocked?.let { InfoRow("Landlocked", if (it) "Yes" else "No") }
    if (c.borders.isNotEmpty()) InfoRow("Land borders", "${c.borders.size} countries")
    c.callingCode?.let { InfoRow("Calling code", it) }
    c.tld?.let { InfoRow("Internet TLD", it) }
    c.iso2?.let { InfoRow("ISO code", "$it / ${c.id}") }
    if (c.type != null && c.type != "Sovereign country" && c.type != "Country") InfoRow("Status", "${c.type}${c.sovereign?.let { " · $it" } ?: ""}")
    SourceNote("Natural Earth & world-countries datasets (bundled, offline).${if (c.areaComputed) " Area computed from boundary polygon." else ""}")
}

@Composable
private fun CityFacts(sel: Selection.OfCity, ui: UiState) {
    val c = sel.city
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Population", GeoMath.formatPopulation(c.population), Modifier.weight(1f))
        StatTile("Role", if (c.isCapital) "National capital" else "City", Modifier.weight(1f))
    }
    sel.country?.let { InfoRow("Country", "${it.flag} ${it.name}") }
    if (c.admin1.isNotBlank()) InfoRow("Region", c.admin1)
    if (c.timeZone.isNotBlank()) InfoRow("Time zone", c.timeZone)
    SourceNote("Population: Natural Earth populated places (metropolitan estimate).")
}

@Composable
private fun QuakeFacts(sel: Selection.OfQuake) {
    val q = sel.quake
    val ctx = androidx.compose.ui.platform.LocalContext.current
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Magnitude", "${f1(q.magnitude)} ${q.magType ?: ""}".trim(), Modifier.weight(1f), accent = EX.Danger)
        StatTile("Depth", q.depthKm?.let { "${f1(it)} km" } ?: "—", Modifier.weight(1f))
    }
    InfoRow("Time (UTC)", formatUtc(q.timeMs))
    InfoRow("Location", q.place)
    q.status?.let { InfoRow("Review status", it) }
    q.alert?.let { InfoRow("PAGER alert", it) }
    if (q.tsunami) InfoRow("Tsunami flag", "Yes — check official warnings", valueColor = EX.Amber)
    q.felt?.let { InfoRow("Felt reports", "%,d".format(it)) }
    q.significance?.let { InfoRow("Significance", it.toString()) }
    q.network?.let { InfoRow("Network", it.uppercase()) }
    q.url?.let { u ->
        TextButton(onClick = { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u))) } },
            modifier = Modifier.padding(horizontal = 12.dp)) { Text("Open USGS event page") }
    }
    SourceNote("Source: USGS Earthquake Hazards Program real-time feed. Values may be revised by USGS.")
}

@Composable
private fun WeatherBlock(vm: EarthViewModel, ui: UiState) {
    val imp = ui.settings.imperial
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionLabel("Weather", Modifier.weight(1f))
        IconButton(onClick = { vm.refreshSelectionWeather() }) { Icon(Icons.Rounded.Refresh, "Refresh weather", tint = EX.TextDim) }
    }
    when (val w = ui.selectionWeather) {
        Load.Idle -> {}
        Load.Loading -> LoadingRow("Fetching Open-Meteo…")
        is Load.Err -> ErrorRow(if (!ui.online) "Offline — no cached weather for this place yet." else w.message) { vm.refreshSelectionWeather() }
        is Load.Ok -> WeatherDetails(w.data, imp)
    }
}

@Composable
private fun WeatherDetails(w: LocalWeather, imperial: Boolean) {
    val c = w.current
    if (c != null) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(WeatherCodes.icon(c.weatherCode, c.isDay), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(temp(c.temperatureC, imperial), style = MaterialTheme.typography.headlineSmall, color = EX.Text)
                Text(WeatherCodes.describe(c.weatherCode), style = MaterialTheme.typography.bodyMedium, color = EX.TextDim)
            }
        }
        StatusPill("Current conditions · model analysis (not a station observation)", EX.Accent2, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Feels like", temp(c.apparentC, imperial), Modifier.weight(1f), icon = Icons.Rounded.Thermostat)
            StatTile("Humidity", c.humidity?.let { "${it.roundToInt()} %" } ?: "—", Modifier.weight(1f), icon = Icons.Rounded.WaterDrop)
            StatTile("Cloud cover", c.cloudCover?.let { "${it.roundToInt()} %" } ?: "—", Modifier.weight(1f), icon = Icons.Rounded.Cloud)
            StatTile("Precipitation", c.precipitationMm?.let { "${f1(it)} mm" } ?: "—", Modifier.weight(1f), icon = Icons.Rounded.WaterDrop)
            StatTile("Wind", c.windSpeedKmh?.let { "${it.roundToInt()} km/h ${WeatherCodes.compass(c.windDirDeg)}" } ?: "—", Modifier.weight(1f), icon = Icons.Rounded.Air)
            StatTile("Pressure", c.pressureHpa?.let { "${it.roundToInt()} hPa" } ?: "—", Modifier.weight(1f))
        }
        InfoRow("Valid at", "${c.time} ${w.timeZone ?: ""}".trim())
    }
    if (w.hourly.isNotEmpty()) {
        SectionLabel("Next 24 hours · forecast")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            w.hourly.forEach { h ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x10FFFFFF)).padding(8.dp)) {
                    Text(h.time.takeLast(5), style = MaterialTheme.typography.labelSmall, color = EX.TextDim)
                    Text(WeatherCodes.icon(h.weatherCode), style = MaterialTheme.typography.titleMedium)
                    Text(temp(h.temperatureC, imperial), style = MaterialTheme.typography.labelLarge, color = EX.Text)
                    h.precipProb?.let { Text("${it.roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = EX.Accent) }
                }
            }
        }
    }
    if (w.daily.isNotEmpty()) {
        SectionLabel("7-day forecast")
        w.daily.forEach { d ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(runCatching { LocalDate.parse(d.date).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()) }.getOrDefault(d.date),
                    Modifier.width(48.dp), color = EX.TextDim, style = MaterialTheme.typography.bodyMedium)
                Text(WeatherCodes.icon(d.weatherCode), Modifier.width(36.dp))
                Text(WeatherCodes.describe(d.weatherCode), Modifier.weight(1f), color = EX.Text, style = MaterialTheme.typography.bodySmall)
                Text("${temp(d.minC, imperial)} / ${temp(d.maxC, imperial)}", color = EX.Text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
    SourceNote("Open-Meteo · retrieved ${ago(w.retrievedMs)} (${formatUtc(w.retrievedMs)})${if (w.fromCache) " · from cache" else ""}")
}

// ------------------------------------------------------------------ WEATHER section
@Composable
fun WeatherPanel(vm: EarthViewModel, ui: UiState) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { PanelHeader("Weather", "Open-Meteo · no API key required") { CloseButton(vm::closePanel) } }
        item {
            SectionLabel("Globe layer")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WeatherLayer.entries.forEach { l ->
                    FilterChip(selected = ui.layers.weather == l, onClick = { vm.setLayers { it.copy(weather = l) } }, label = { Text(l.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EX.Accent.copy(alpha = 0.2f), selectedLabelColor = EX.Accent))
                }
            }
            when (val g = ui.weatherGrid) {
                Load.Loading -> LoadingRow("Retrieving a global grid of current conditions…")
                is Load.Err -> ErrorRow(g.message) { vm.loadWeatherGrid(force = true) }
                is Load.Ok -> {
                    InfoRow("Grid", "${g.data.lats.size} × ${g.data.lons.size} points (15° spacing)")
                    InfoRow("Model time", g.data.validTime?.let { "$it UTC" } ?: "—")
                    InfoRow("Retrieved", "${ago(g.data.retrievedMs)}${if (g.data.fromCache) " · cached" else ""}")
                    TextButton(onClick = { vm.loadWeatherGrid(force = true) }, enabled = ui.online, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Refresh grid") }
                }
                Load.Idle -> SourceNote("Choose a layer to download the global grid.")
            }
            SourceNote("Globe colours are an interpolated visualisation between real model values at grid points — not observations at every pixel. Tap any place for its own current conditions and forecast.")
        }
        item {
            SectionLabel("Selected location")
            if (ui.selection == null) SourceNote("Tap anywhere on the globe to see its weather.")
            else { InfoRow("Place", ui.selection.title); WeatherBlock(vm, ui) }
            ToggleRow("Imperial units (°F)", null, ui.settings.imperial) { v -> vm.updateSettings { it.copy(imperial = v) } }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ------------------------------------------------------------------ EARTHQUAKES
@Composable
fun QuakesPanel(vm: EarthViewModel, ui: UiState) {
    var sortByMag by remember { mutableStateOf(false) }
    val list = remember(ui.quakeFeed, ui.quakeMinMag, sortByMag) {
        vm.visibleQuakes().let { l -> if (sortByMag) l.sortedByDescending { it.magnitude ?: -9.0 } else l.sortedByDescending { it.timeMs } }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { PanelHeader("Earthquakes", "USGS real-time feed") { CloseButton(vm::closePanel) } }
        item {
            ToggleRow("Show on globe", "Size = magnitude · colour = depth (red shallow → blue deep)", ui.layers.quakes) { v -> vm.setLayers { it.copy(quakes = v) } }
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuakeRange.entries.forEach { r ->
                    FilterChip(selected = ui.quakeRange == r, onClick = { vm.setQuakeRange(r) }, label = { Text(r.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EX.Danger.copy(alpha = 0.22f), selectedLabelColor = EX.Text))
                }
            }
            Text("Minimum magnitude: ${"%.1f".format(Locale.US, ui.quakeMinMag)}", style = MaterialTheme.typography.bodyMedium, color = EX.TextDim, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
            Slider(value = ui.quakeMinMag.toFloat(), onValueChange = { vm.setQuakeMinMag((it * 2).roundToInt() / 2.0) }, valueRange = 0f..7f, steps = 13,
                modifier = Modifier.padding(horizontal = 16.dp), colors = SliderDefaults.colors(thumbColor = EX.Danger, activeTrackColor = EX.Danger.copy(alpha = 0.7f)))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !sortByMag, onClick = { sortByMag = false }, label = { Text("Newest first") })
                FilterChip(selected = sortByMag, onClick = { sortByMag = true }, label = { Text("Strongest first") })
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.refreshQuakes(force = true) }, enabled = ui.online) { Icon(Icons.Rounded.Refresh, "Refresh", tint = EX.TextDim) }
            }
        }
        when (val f = ui.quakeFeed) {
            Load.Idle -> {}
            Load.Loading -> item { LoadingRow("Downloading ${ui.quakeRange.label.lowercase()} from USGS…") }
            is Load.Err -> item { ErrorRow(if (!ui.online) "Offline and no cached feed." else f.message) { vm.refreshQuakes(force = true) } }
            is Load.Ok -> {
                item {
                    SourceNote("${list.size} of ${f.data.events.size} events · feed generated ${formatUtc(f.data.generatedMs)} · retrieved ${ago(f.data.retrievedMs)}${if (f.data.fromCache) " (cached)" else ""}")
                }
                items(list.take(400), key = { it.id }) { q ->
                    val mag = q.magnitude
                    ListItemRow(
                        title = q.place,
                        subtitle = "${formatUtc(q.timeMs)} · depth ${f1(q.depthKm)} km",
                        trailing = "M ${f1(mag)}",
                        trailingColor = when { (mag ?: 0.0) >= 6 -> EX.Danger; (mag ?: 0.0) >= 4.5 -> EX.Amber; else -> EX.TextDim },
                    ) { vm.openQuake(q) }
                }
                if (list.size > 400) item { SourceNote("Showing the first 400 in the list; all ${list.size} are drawn on the globe.") }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ------------------------------------------------------------------ SATELLITE
@Composable
fun SatellitePanel(vm: EarthViewModel, ui: UiState) {
    val sat = ui.satellite
    var picking by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize()) {
        item { PanelHeader("Satellite imagery", "NASA GIBS · WMS (EPSG:4326)") { CloseButton(vm::closePanel) } }
        item {
            ToggleRow("Live satellite layer", if (ui.online) "Replaces the bundled texture with NASA imagery" else "Offline — bundled Blue Marble texture is shown", sat.enabled) { vm.setSatellite(enabled = it) }
            sat.globalStatus?.let { StatusPill(it, if (sat.loading) EX.Accent else EX.Good, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            sat.detailStatus?.let { StatusPill(it, EX.Accent2, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            SectionLabel("Imagery layer")
        }
        items(GIBS_LAYERS) { l ->
            ListItemRow(l.label, if (l.daily) "Daily · date selectable" else "Static composite", trailing = if (sat.layerId == l.id) "●" else null, trailingColor = EX.Accent) {
                vm.setSatellite(enabled = true, layerId = l.id)
            }
        }
        item {
            val layer = GIBS_LAYERS.firstOrNull { it.id == sat.layerId }
            SectionLabel("Imagery date")
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { picking = true }, enabled = layer?.daily == true) {
                    Icon(Icons.Rounded.Today, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(sat.date.toString())
                }
                TextButton(onClick = { vm.setSatellite(date = sat.date.minusDays(1)) }, enabled = layer?.daily == true) { Text("◀ Day") }
                TextButton(onClick = { vm.setSatellite(date = sat.date.plusDays(1)) },
                    enabled = layer?.daily == true && sat.date.isBefore(LocalDate.now(ZoneOffset.UTC))) { Text("Day ▶") }
            }
            SourceNote("Daily swaths are assembled over the day; today's image may be incomplete, so yesterday (UTC) is the default. Polar night and data gaps appear black. Regional detail is limited to ~250–500 m per pixel — no street-level imagery.")
            Spacer(Modifier.height(24.dp))
        }
    }
    if (picking) {
        val today = LocalDate.now(ZoneOffset.UTC)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = sat.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() &&
                        utcTimeMillis >= LocalDate.of(2012, 1, 20).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.setSatellite(enabled = true, date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text("Show imagery") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

// ------------------------------------------------------------------ TIME SIMULATION
@Composable
fun TimePanel(vm: EarthViewModel, ui: UiState) {
    val t = ui.time
    var picking by remember { mutableStateOf(false) }
    val day = Instant.ofEpochMilli(t.utcMs).atZone(ZoneOffset.UTC).toLocalDate()
    val start = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    LazyColumn(Modifier.fillMaxSize()) {
        item { PanelHeader("Day & night simulator", "Sun position computed for the simulated UTC time") { CloseButton(vm::closePanel) } }
        item {
            Text(formatUtc(t.utcMs), style = MaterialTheme.typography.headlineSmall, color = EX.Text, modifier = Modifier.padding(horizontal = 20.dp))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(if (t.live) "Live (real time)" else "Simulated · ×${speedLabel(t.speed)}", if (t.live) EX.Good else EX.Amber)
                StatusPill(if (t.playing) "Playing" else "Paused", EX.Accent)
            }
            SectionLabel("Time of day (UTC)")
            Slider(value = ((t.utcMs - start) / 86_400_000f).coerceIn(0f, 1f), onValueChange = { vm.setSimTime(start + (it * 86_400_000L).toLong()) },
                modifier = Modifier.padding(horizontal = 16.dp), colors = SliderDefaults.colors(thumbColor = EX.Amber, activeTrackColor = EX.Amber.copy(alpha = 0.7f)))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.setPlayback(playing = !t.playing) }) { Text(if (t.playing) "Pause" else "Play") }
                OutlinedButton(onClick = { picking = true }) { Icon(Icons.Rounded.Today, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(day.toString()) }
                TextButton(onClick = { vm.setLiveTime() }) { Text("Back to now") }
            }
            SectionLabel("Speed")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SPEEDS.forEach { s ->
                    FilterChip(selected = !t.live && t.speed == s, onClick = { vm.setPlayback(speed = s, playing = true) }, label = { Text(if (s == 1.0) "Real time" else "×${speedLabel(s)}") })
                }
            }
            SectionLabel("Seasons")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val y = day.year
                listOf("March equinox" to LocalDate.of(y, 3, 20), "June solstice" to LocalDate.of(y, 6, 21),
                    "September equinox" to LocalDate.of(y, 9, 22), "December solstice" to LocalDate.of(y, 12, 21)).forEach { (n, d) ->
                    FilterChip(selected = day == d, onClick = { vm.setSimTime(d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 12 * 3_600_000L) }, label = { Text(n) })
                }
            }
            val (sLat, sLon) = GeoMath.subsolarPoint(t.utcMs)
            SectionLabel("Sun")
            InfoRow("Sub-solar point", GeoMath.formatCoords(sLat, sLon))
            InfoRow("Solar declination", "%.2f°".format(Locale.US, sLat))
            ToggleRow("Day / night shading", "Terminator, twilight band and city lights", ui.layers.night) { v -> vm.setLayers { it.copy(night = v) } }
            SectionLabel("Selected place")
            val sel = ui.selection
            if (sel == null) SourceNote("Select a city to see its local time.")
            else {
                val zone = (sel as? Selection.OfCity)?.city?.timeZone ?: (sel as? Selection.OfCountry)?.country?.timeZone
                val (lt, _) = GeoMath.localTimeText(t.utcMs, zone, sel.lon)
                InfoRow(sel.title, lt)
                val e = GeoMath.solarElevation(sel.lat, sel.lon, t.utcMs)
                InfoRow("Sun there", "${GeoMath.daylightLabel(e)} (${"%.1f".format(Locale.US, e)}°)")
            }
            SourceNote("Sun position: low-precision solar ephemeris (≈0.01°) with Greenwich mean sidereal time. Local times use IANA time-zone rules (DST included) when the zone is known; otherwise approximate solar time.")
            Spacer(Modifier.height(24.dp))
        }
    }
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = start)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.setSimTime(it + (t.utcMs - start)) }
                    picking = false
                }) { Text("Set date") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

