@file:OptIn(ExperimentalMaterial3Api::class)

package com.earthx.explorer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SatelliteAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.earthx.explorer.data.model.WeatherLayer
import com.earthx.explorer.engine.GlobeView
import com.earthx.explorer.ui.components.GlassBar
import com.earthx.explorer.ui.components.GlassPanel
import com.earthx.explorer.ui.components.ListItemRow
import com.earthx.explorer.ui.components.RoundIconButton
import com.earthx.explorer.ui.components.StatusPill
import com.earthx.explorer.ui.screens.PanelHost
import com.earthx.explorer.ui.screens.kindIcon
import com.earthx.explorer.ui.theme.EX
import com.earthx.explorer.util.GeoMath
import com.earthx.explorer.viewmodels.EarthViewModel
import com.earthx.explorer.viewmodels.Section
import com.earthx.explorer.viewmodels.UiState
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

fun sectionIcon(s: Section): ImageVector = when (s) {
    Section.HOME -> Icons.Rounded.Home
    Section.EXPLORE -> Icons.Rounded.Explore
    Section.COUNTRIES -> Icons.Rounded.Flag
    Section.WEATHER -> Icons.Rounded.Cloud
    Section.QUAKES -> Icons.Rounded.Vibration
    Section.SATELLITE -> Icons.Rounded.SatelliteAlt
    Section.TIME -> Icons.Rounded.Schedule
    Section.FAVOURITES -> Icons.Rounded.Star
    Section.SETTINGS -> Icons.Rounded.Settings
}

private val UTC_FMT = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm 'UTC'", Locale.US)
fun formatUtc(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).format(UTC_FMT)

@Composable
fun EarthXScreen(vm: EarthViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val url by vm.engineUrl.collectAsStateWithLifecycle()
    val gen by vm.engineGeneration.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    BoxWithConstraints(Modifier.fillMaxSize().background(EX.Space)) {
        val wide = maxWidth >= 840.dp && maxWidth > maxHeight
        val panelVisible = ui.chromeVisible && ui.panelOpen
        Row(Modifier.fillMaxSize()) {
            AnimatedVisibility(ui.chromeVisible, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                SideRail(ui.section, ui.panelOpen && !ui.showDetails, compact = !wide) { vm.openSection(it) }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    url?.let { u ->
                        key(gen) {
                            GlobeView(u, vm.bridge, vm.controller, vm.imagery, vm::onRenderProcessGone, Modifier.fillMaxSize())
                        }
                    }
                    GlobeOverlays(vm, ui, wide)
                    if (!ui.engineReady) LoadingVeil(ui.progress)
                }
                if (!wide) {
                    AnimatedVisibility(panelVisible, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        GlassPanel(Modifier.fillMaxWidth().height(maxHeight * 0.46f).padding(8.dp)) { PanelHost(vm, ui) }
                    }
                }
            }
            if (wide) {
                AnimatedVisibility(panelVisible, enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                    GlassPanel(
                        Modifier.width(if (maxWidth > 1300.dp) 420.dp else 380.dp).fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 10.dp, end = 10.dp, bottom = 10.dp)
                    ) { PanelHost(vm, ui) }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
}

@Composable
private fun SideRail(current: Section, highlight: Boolean, compact: Boolean, onSelect: (Section) -> Unit) {
    NavigationRail(
        containerColor = Color(0xF2050912),
        modifier = Modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing),
        header = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                Text("EARTH", style = MaterialTheme.typography.labelSmall, color = EX.Text)
                Text("X", style = MaterialTheme.typography.titleLarge, color = EX.Accent)
            }
        },
    ) {
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 2.dp, Alignment.CenterVertically)) {
            Section.entries.forEach { s ->
                NavigationRailItem(
                    selected = highlight && s == current,
                    onClick = { onSelect(s) },
                    icon = { Icon(sectionIcon(s), contentDescription = s.title) },
                    label = { if (!compact) Text(s.title, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    alwaysShowLabel = !compact,
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = EX.Accent, selectedTextColor = EX.Accent, indicatorColor = EX.Accent.copy(alpha = 0.16f),
                        unselectedIconColor = EX.TextDim, unselectedTextColor = EX.TextDim,
                    ),
                )
            }
        }
    }
}

@Composable
private fun LoadingVeil(progress: Int) {
    Box(Modifier.fillMaxSize().background(Color(0xCC02040A)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("EARTH-X", style = MaterialTheme.typography.displaySmall, color = EX.Text)
            Text("LIVING PLANET EXPLORER", style = MaterialTheme.typography.labelSmall, color = EX.Accent)
            Spacer(Modifier.height(24.dp))
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.width(220.dp), color = EX.Accent, trackColor = Color(0x22FFFFFF))
            Spacer(Modifier.height(8.dp))
            Text("Loading Earth textures and geography…", style = MaterialTheme.typography.bodySmall, color = EX.TextDim)
        }
    }
}

@Composable
private fun GlobeOverlays(vm: EarthViewModel, ui: UiState, wide: Boolean) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(14.dp)) {
        // ---- top: search + status
        AnimatedVisibility(ui.chromeVisible, Modifier.align(Alignment.TopStart)) {
            SearchBox(vm, ui, Modifier.widthIn(max = 420.dp))
        }
        Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!ui.online) StatusPill("Offline", EX.Amber, icon = Icons.Rounded.WifiOff)
                else StatusPill("Live data", EX.Good)
                RoundIconButton(if (ui.chromeVisible) Icons.Rounded.Fullscreen else Icons.Rounded.FullscreenExit,
                    if (ui.chromeVisible) "Maximise globe" else "Show interface", size = 38.dp) { vm.toggleChrome() }
            }
            if (ui.chromeVisible) {
                val c = ui.camera
                StatusPill("L${c.level} · ${GeoMath.LEVEL_NAMES[c.level]}", EX.Accent)
                Text("${GeoMath.formatCoords(c.lat, c.lon)}  ·  alt ${"%,.0f".format(Locale.US, c.altKm)} km",
                    style = MaterialTheme.typography.labelMedium, color = EX.TextDim)
                if (ui.settings.showFps && ui.fps != null) Text("${ui.fps} fps", style = MaterialTheme.typography.labelSmall, color = EX.TextFaint)
                if (ui.layers.weather != WeatherLayer.NONE) WeatherLegend(ui.layers.weather)
            }
        }
        // ---- bottom controls
        AnimatedVisibility(ui.chromeVisible, Modifier.align(Alignment.BottomCenter)) {
            BottomControls(vm, ui, wide)
        }
        if (ui.measure.active) {
            val m = ui.measure
            GlassPanel(Modifier.align(Alignment.BottomStart).padding(bottom = 76.dp).widthIn(max = 320.dp), corner = 16.dp) {
                Column(Modifier.padding(14.dp)) {
                    Text("Great-circle measurement", style = MaterialTheme.typography.titleSmall, color = EX.Accent)
                    if (m.aLat != null) Text("A: ${GeoMath.formatCoords(m.aLat, m.aLon ?: 0.0)}", style = MaterialTheme.typography.bodySmall, color = EX.Text)
                    if (m.bLat != null) Text("B: ${GeoMath.formatCoords(m.bLat, m.bLon ?: 0.0)}", style = MaterialTheme.typography.bodySmall, color = EX.Text)
                    if (m.km != null) {
                        Text(GeoMath.formatDistance(m.km), style = MaterialTheme.typography.titleLarge, color = EX.Text)
                        Text("Initial bearing ${"%.1f".format(Locale.US, m.bearing ?: 0.0)}° · spherical Earth (R = 6,371 km)", style = MaterialTheme.typography.bodySmall, color = EX.TextDim)
                    } else Text(if (m.aLat == null) "Tap the first point" else "Tap the second point", style = MaterialTheme.typography.bodySmall, color = EX.TextDim)
                }
            }
        }
    }
}

@Composable
private fun WeatherLegend(layer: WeatherLayer) {
    val (title, stops) = when (layer) {
        WeatherLayer.TEMPERATURE -> "Temperature °C" to listOf("-40" to Color(0xFF8C3CC8), "-10" to Color(0xFF3C96F0), "0" to Color(0xFF78D2FA), "20" to Color(0xFFF0E65A), "40" to Color(0xFFDC2828))
        WeatherLayer.PRECIPITATION -> "Precipitation mm/h" to listOf("0.1" to Color(0xFF5AC8FF), "1" to Color(0xFF288CFF), "4" to Color(0xFF28DC78), "10" to Color(0xFFFADC28), "25" to Color(0xFFFA3C3C))
        WeatherLayer.CLOUDS -> "Cloud cover %" to listOf("0" to Color(0x33FFFFFF), "50" to Color(0x99FFFFFF), "100" to Color(0xFFFFFFFF))
        WeatherLayer.WIND -> "Wind (animated flow)" to listOf("0" to Color(0xFF285AC8), "30" to Color(0xFF8C78A0), "60+ km/h" to Color(0xFFE89650))
        else -> "Weather conditions" to emptyList()
    }
    GlassPanel(corner = 12.dp) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = EX.Text)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                stops.forEach { (l, c) ->
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c))
                    Text(l, style = MaterialTheme.typography.labelSmall, color = EX.TextDim)
                }
            }
            Text("Interpolated from a 15° Open-Meteo model grid", style = MaterialTheme.typography.labelSmall, color = EX.TextFaint)
        }
    }
}

@Composable
private fun SearchBox(vm: EarthViewModel, ui: UiState, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Column(modifier) {
        TextField(
            value = ui.searchQuery,
            onValueChange = vm::onSearch,
            placeholder = { Text("Search countries, cities, mountains, oceans…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = EX.Accent) },
            trailingIcon = {
                if (ui.searchQuery.isNotEmpty()) {
                    androidx.compose.material3.IconButton(onClick = { vm.onSearch(""); focus.clearFocus() }) {
                        Icon(Icons.Rounded.Close, "Clear search", tint = EX.TextDim)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(26.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { ui.searchResults.firstOrNull()?.let { vm.openPlace(it); vm.onSearch(""); focus.clearFocus() } }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = EX.GlassStrong, unfocusedContainerColor = EX.Glass,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                cursorColor = EX.Accent, focusedTextColor = EX.Text, unfocusedTextColor = EX.Text,
                focusedPlaceholderColor = EX.TextFaint, unfocusedPlaceholderColor = EX.TextFaint,
            ),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )
        if (ui.searchQuery.isNotBlank() && (focused || ui.searchResults.isNotEmpty())) {
            Spacer(Modifier.height(6.dp))
            GlassPanel(Modifier.fillMaxWidth().heightIn(max = 380.dp), corner = 18.dp) {
                if (ui.searchResults.isEmpty()) Text("No matches in the offline atlas", Modifier.padding(16.dp), color = EX.TextDim)
                LazyColumn {
                    items(ui.searchResults, key = { "${it.kind}|${it.name}|${it.lat}|${it.lon}" }) { p ->
                        ListItemRow(p.name, p.subtitle, leadingIcon = kindIcon(p.kind), trailing = p.kind.label, trailingColor = EX.TextFaint) {
                            vm.openPlace(p); vm.onSearch(""); focus.clearFocus()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomControls(vm: EarthViewModel, ui: UiState, wide: Boolean) {
    var layersOpen by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (wide) TimelineStrip(vm, ui)
        Spacer(Modifier.height(8.dp))
        GlassBar {
            RoundIconButton(Icons.Rounded.Remove, "Zoom out") { vm.zoom(-1) }
            RoundIconButton(Icons.Rounded.Add, "Zoom in") { vm.zoom(1) }
            BarDivider()
            RoundIconButton(Icons.Rounded.KeyboardArrowLeft, "Rotate west") { vm.rotate(-30.0) }
            RoundIconButton(Icons.Rounded.KeyboardArrowRight, "Rotate east") { vm.rotate(30.0) }
            RoundIconButton(Icons.Rounded.CenterFocusStrong, "Reset camera") { vm.resetCamera() }
            BarDivider()
            Box {
                RoundIconButton(Icons.Rounded.Layers, "Layers", active = layersOpen) { layersOpen = true }
                LayersMenu(vm, ui, layersOpen) { layersOpen = false }
            }
            RoundIconButton(Icons.Rounded.Straighten, "Measure distance", active = ui.measure.active) { vm.setMeasure(!ui.measure.active) }
            RoundIconButton(Icons.Rounded.PhotoCamera, "Screenshot") { vm.screenshot() }
            BarDivider()
            RoundIconButton(if (ui.time.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (ui.time.playing) "Pause time" else "Play time") {
                vm.setPlayback(playing = !ui.time.playing)
            }
            RoundIconButton(Icons.Rounded.FastForward, "Speed ×${speedLabel(ui.time.speed)}", active = !ui.time.live && ui.time.speed > 1) {
                vm.setPlayback(speed = nextSpeed(ui.time.speed), playing = true)
            }
            Column(Modifier.padding(horizontal = 10.dp)) {
                Text(if (ui.time.live) "LIVE" else "SIM ×${speedLabel(ui.time.speed)}", style = MaterialTheme.typography.labelSmall,
                    color = if (ui.time.live) EX.Good else EX.Amber)
                Text(formatUtc(ui.time.utcMs), style = MaterialTheme.typography.labelMedium, color = EX.Text)
            }
        }
    }
}

@Composable
private fun BarDivider() = Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(24.dp).background(EX.StrokeSoft))

val SPEEDS = listOf(1.0, 60.0, 600.0, 3600.0, 21600.0, 86400.0)
fun nextSpeed(cur: Double) = SPEEDS.firstOrNull { it > cur + 0.5 } ?: SPEEDS.first()
fun speedLabel(s: Double) = when {
    s >= 86400 -> "1 day/s"; s >= 3600 -> "${(s / 3600).toInt()} h/s"; s >= 60 -> "${(s / 60).toInt()} min/s"; else -> "1"
}

@Composable
private fun TimelineStrip(vm: EarthViewModel, ui: UiState) {
    val day = Instant.ofEpochMilli(ui.time.utcMs).atZone(ZoneOffset.UTC).toLocalDate()
    val start = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val frac = ((ui.time.utcMs - start) / 86_400_000f).coerceIn(0f, 1f)
    GlassBar(Modifier.widthIn(max = 640.dp)) {
        Text("00:00", style = MaterialTheme.typography.labelSmall, color = EX.TextFaint, modifier = Modifier.padding(start = 10.dp))
        androidx.compose.material3.Slider(
            value = frac,
            onValueChange = { vm.setSimTime(start + (it * 86_400_000L).toLong()) },
            modifier = Modifier.weight(1f).height(28.dp).padding(horizontal = 8.dp),
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = EX.Accent, activeTrackColor = EX.Accent.copy(alpha = 0.7f), inactiveTrackColor = Color(0x33FFFFFF)),
        )
        Text("24:00 UTC", style = MaterialTheme.typography.labelSmall, color = EX.TextFaint, modifier = Modifier.padding(end = 10.dp))
    }
}

@Composable
private fun LayersMenu(vm: EarthViewModel, ui: UiState, open: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss, modifier = Modifier.background(Color(0xF20B1222))) {
        Text("SURFACE", style = MaterialTheme.typography.labelSmall, color = EX.Accent, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        MenuCheck("Natural (Blue Marble)", !ui.satellite.enabled && !ui.layers.elevation) { vm.setSatellite(enabled = false); vm.setLayers { it.copy(elevation = false) } }
        MenuCheck("Live satellite (NASA GIBS)", ui.satellite.enabled) { vm.setSatellite(enabled = !ui.satellite.enabled) }
        MenuCheck("Elevation tint", ui.layers.elevation) { vm.setLayers { it.copy(elevation = !it.elevation) } }
        MenuCheck("Day / night shading", ui.layers.night) { vm.setLayers { it.copy(night = !it.night) } }
        HorizontalDivider(color = EX.StrokeSoft)
        Text("OVERLAYS", style = MaterialTheme.typography.labelSmall, color = EX.Accent, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        MenuCheck("Earthquakes (USGS)", ui.layers.quakes) { vm.setLayers { it.copy(quakes = !it.quakes) } }
        MenuCheck("Mountain highlighting", ui.layers.mountains) { vm.setLayers { it.copy(mountains = !it.mountains) } }
        MenuCheck("Tectonic plate boundaries", ui.layers.plates) { vm.setLayers { it.copy(plates = !it.plates) } }
        MenuCheck("Labels", ui.settings.labels) { vm.updateSettings { it.copy(labels = !it.labels) } }
        MenuCheck("Cloud layer", ui.settings.clouds) { vm.updateSettings { it.copy(clouds = !it.clouds) } }
        HorizontalDivider(color = EX.StrokeSoft)
        Text("WEATHER (Open-Meteo)", style = MaterialTheme.typography.labelSmall, color = EX.Accent, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        WeatherLayer.entries.forEach { l -> MenuCheck(l.label, ui.layers.weather == l) { vm.setLayers { it.copy(weather = l) } } }
    }
}

@Composable
private fun MenuCheck(text: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = if (checked) EX.Accent else EX.Text) },
        onClick = onClick,
        leadingIcon = { Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(if (checked) EX.Accent else Color(0x33FFFFFF))) },
    )
}
