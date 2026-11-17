package com.earthx.explorer.data.repositories

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "earthx_settings")

enum class RenderQuality(val key: String, val label: String, val description: String) {
    LOW("low", "Battery saver", "1K textures, reduced resolution"),
    HIGH("high", "High (4K)", "4K Earth textures, sharp rendering"),
    ULTRA("ultra", "Ultra HD (8K)", "8K NASA textures when the GPU supports them"),
}

data class AppSettings(
    val quality: RenderQuality = RenderQuality.ULTRA,
    val lowPower: Boolean = false,
    val labels: Boolean = true,
    val borders: Boolean = true,
    val states: Boolean = true,
    val clouds: Boolean = true,
    val atmosphere: Boolean = true,
    val nightLights: Boolean = true,
    val detailImagery: Boolean = true,
    val autoRotate: Boolean = false,
    val imperial: Boolean = false,
    val showFps: Boolean = false,
)

class SettingsRepository(private val context: Context) {
    private object K {
        val quality = stringPreferencesKey("quality")
        val lowPower = booleanPreferencesKey("low_power")
        val labels = booleanPreferencesKey("labels")
        val borders = booleanPreferencesKey("borders")
        val states = booleanPreferencesKey("states")
        val clouds = booleanPreferencesKey("clouds")
        val atmosphere = booleanPreferencesKey("atmosphere")
        val nightLights = booleanPreferencesKey("night_lights")
        val detail = booleanPreferencesKey("detail_imagery")
        val autoRotate = booleanPreferencesKey("auto_rotate")
        val imperial = booleanPreferencesKey("imperial")
        val fps = booleanPreferencesKey("show_fps")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        quality = RenderQuality.entries.firstOrNull { it.key == this[K.quality] } ?: RenderQuality.ULTRA,
        lowPower = this[K.lowPower] ?: false,
        labels = this[K.labels] ?: true,
        borders = this[K.borders] ?: true,
        states = this[K.states] ?: true,
        clouds = this[K.clouds] ?: true,
        atmosphere = this[K.atmosphere] ?: true,
        nightLights = this[K.nightLights] ?: true,
        detailImagery = this[K.detail] ?: true,
        autoRotate = this[K.autoRotate] ?: false,
        imperial = this[K.imperial] ?: false,
        showFps = this[K.fps] ?: false,
    )

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val n = transform(p.toSettings())
            p[K.quality] = n.quality.key; p[K.lowPower] = n.lowPower; p[K.labels] = n.labels; p[K.borders] = n.borders
            p[K.states] = n.states; p[K.clouds] = n.clouds; p[K.atmosphere] = n.atmosphere; p[K.nightLights] = n.nightLights
            p[K.detail] = n.detailImagery; p[K.autoRotate] = n.autoRotate; p[K.imperial] = n.imperial; p[K.fps] = n.showFps
        }
    }
}
