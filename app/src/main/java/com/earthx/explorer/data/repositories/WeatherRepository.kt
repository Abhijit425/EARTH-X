package com.earthx.explorer.data.repositories

import com.earthx.explorer.data.model.LocalWeather
import com.earthx.explorer.data.model.WeatherGrid
import com.earthx.explorer.data.network.DiskCache
import com.earthx.explorer.data.network.HttpClient
import com.earthx.explorer.data.parse.Parsers
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.util.Locale

/**
 * Open-Meteo (https://open-meteo.com) — free, no API key, CC BY 4.0 data.
 * "Current" values are model analyses at 15-minute resolution, not station observations.
 */
class WeatherRepository(private val http: HttpClient, private val cache: DiskCache) {

    companion object {
        const val BASE = "https://api.open-meteo.com/v1/forecast"
        const val LOCAL_TTL_MS = 15 * 60 * 1000L
        const val GRID_TTL_MS = 60 * 60 * 1000L
        // 15° global grid = 11 x 24 = 264 points (kept small to respect Open-Meteo's fair-use limits)
        val GRID_LATS = (-75..75 step 15).map { it.toDouble() }
        val GRID_LONS = (-180..165 step 15).map { it.toDouble() }
        const val CHUNK = 66

        fun localUrl(lat: Double, lon: Double) = String.format(Locale.US,
            "%s?latitude=%.3f&longitude=%.3f&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m" +
                "&hourly=temperature_2m,precipitation_probability,weather_code&forecast_hours=24" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,sunrise,sunset&forecast_days=7&timezone=auto",
            BASE, lat, lon)

        fun gridUrls(): List<String> {
            val pts = GRID_LATS.flatMap { la -> GRID_LONS.map { lo -> la to lo } }
            return pts.chunked(CHUNK).map { chunk ->
                val lats = chunk.joinToString(",") { String.format(Locale.US, "%.1f", it.first) }
                val lons = chunk.joinToString(",") { String.format(Locale.US, "%.1f", it.second) }
                "$BASE?latitude=$lats&longitude=$lons&current=temperature_2m,cloud_cover,precipitation,wind_speed_10m,wind_direction_10m,weather_code&timezone=GMT"
            }
        }
    }

    suspend fun local(lat: Double, lon: Double, online: Boolean, force: Boolean = false): LocalWeather {
        val key = String.format(Locale.US, "wx:%.1f,%.1f", lat, lon)
        val hit = cache.get(key)
        val now = System.currentTimeMillis()
        if (hit != null && (!online || (!force && now - hit.retrievedMs < LOCAL_TTL_MS))) {
            return Parsers.localWeather(String(hit.bytes), hit.retrievedMs, fromCache = true)
        }
        if (!online) throw IllegalStateException("Offline and no cached weather for this location")
        return try {
            val body = http.getBytes(localUrl(lat, lon))
            cache.put(key, body, "open-meteo")
            Parsers.localWeather(String(body), now, fromCache = false)
        } catch (e: Exception) {
            if (hit != null) Parsers.localWeather(String(hit.bytes), hit.retrievedMs, fromCache = true) else throw e
        }
    }

    suspend fun grid(online: Boolean, force: Boolean = false): WeatherGrid {
        val key = "wxgrid:15deg"
        val hit = cache.get(key)
        val now = System.currentTimeMillis()
        fun fromHit(h: DiskCache.Hit): WeatherGrid {
            val arr = JSONArray(String(h.bytes))
            return Parsers.weatherGrid((0 until arr.length()).map { arr.getString(it) }, GRID_LATS, GRID_LONS, h.retrievedMs, true)
        }
        if (hit != null && (!online || (!force && now - hit.retrievedMs < GRID_TTL_MS))) return fromHit(hit)
        if (!online) throw IllegalStateException("Offline and no cached weather grid")
        return try {
            val chunks = ArrayList<String>()
            for ((i, u) in gridUrls().withIndex()) {
                if (i > 0) delay(350)
                chunks += http.getString(u)
            }
            val grid = Parsers.weatherGrid(chunks, GRID_LATS, GRID_LONS, now, false)
            val stored = JSONArray().apply { chunks.forEach { put(it) } }.toString().toByteArray()
            cache.put(key, stored, "open-meteo")
            grid
        } catch (e: Exception) {
            if (hit != null) fromHit(hit) else throw e
        }
    }
}
