package com.earthx.explorer.data.parse

import com.earthx.explorer.data.model.City
import com.earthx.explorer.data.model.Continent
import com.earthx.explorer.data.model.Country
import com.earthx.explorer.data.model.Curated
import com.earthx.explorer.data.model.CuratedFact
import com.earthx.explorer.data.model.CurrentWeather
import com.earthx.explorer.data.model.DailyPoint
import com.earthx.explorer.data.model.HourlyPoint
import com.earthx.explorer.data.model.LocalWeather
import com.earthx.explorer.data.model.Peak
import com.earthx.explorer.data.model.Place
import com.earthx.explorer.data.model.PlaceKind
import com.earthx.explorer.data.model.Quake
import com.earthx.explorer.data.model.Topic
import com.earthx.explorer.data.model.Trench
import com.earthx.explorer.data.model.WeatherGrid
import org.json.JSONArray
import org.json.JSONObject

/** Null-safe JSON helpers (org.json returns NaN / "" for missing values). */
internal fun JSONObject.dbl(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() && !it.isInfinite() }

internal fun JSONObject.str(key: String): String? = if (!has(key) || isNull(key)) null else optString(key)
internal fun JSONObject.lng(key: String): Long? = if (!has(key) || isNull(key)) null else optLong(key)
internal fun JSONObject.int(key: String): Int? = if (!has(key) || isNull(key)) null else optInt(key)
internal fun JSONArray.dblAt(i: Int): Double? = if (isNull(i)) null else optDouble(i).takeIf { !it.isNaN() }
internal fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
private fun JSONObject.strList(key: String): List<String> = optJSONArray(key)?.strings() ?: emptyList()

object Parsers {

    fun countries(json: String): List<Country> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val bb = o.optJSONArray("bbox")
            Country(
                id = o.getString("id"),
                name = o.getString("name"),
                shortName = o.optString("short", o.getString("name")),
                official = o.str("official"),
                iso2 = o.str("iso2"),
                flag = o.optString("flag", ""),
                capital = o.optString("capital", ""),
                capitalLat = o.dbl("capLat"),
                capitalLon = o.dbl("capLon"),
                population = o.lng("pop"),
                populationYear = o.int("popYear"),
                areaKm2 = o.dbl("area"),
                areaComputed = o.optString("areaSrc") == "computed",
                continent = o.optString("continent", ""),
                subregion = o.optString("subregion", ""),
                lat = o.optDouble("lat", 0.0),
                lon = o.optDouble("lon", 0.0),
                currencies = o.strList("currencies"),
                languages = o.strList("languages"),
                landlocked = if (o.has("landlocked") && !o.isNull("landlocked")) o.optBoolean("landlocked") else null,
                borders = o.strList("borders"),
                tld = o.str("tld"),
                callingCode = o.str("calling"),
                gdpMillionUsd = o.lng("gdpMd"),
                type = o.str("type"),
                sovereign = o.str("sovereign"),
                bbox = if (bb != null) (0 until bb.length()).map { bb.getDouble(it) } else emptyList(),
                timeZone = o.optString("tz", ""),
            )
        }
    }

    fun cities(json: String): List<City> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            City(
                index = i, name = o.getString("n"), countryId = o.optString("c"), countryName = o.optString("cn"),
                admin1 = o.optString("a1"), lat = o.getDouble("lat"), lon = o.getDouble("lon"),
                population = o.optLong("pop"), isCapital = o.optInt("cap") == 1, timeZone = o.optString("tz", ""),
            )
        }
    }

    fun geography(json: String): List<Place> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val kind = PlaceKind.fromGeo(o.optString("k"))
            Place(
                name = o.getString("n"), kind = kind, lat = o.getDouble("lat"), lon = o.getDouble("lon"),
                subtitle = listOf(kind.label, o.optString("reg")).filter { it.isNotBlank() }.joinToString(" · "),
                rank = 10.0 - o.optDouble("r", 9.0),
                altKm = when (kind) {
                    PlaceKind.OCEAN -> 9000.0; PlaceKind.CONTINENT -> 9000.0; PlaceKind.MOUNTAIN, PlaceKind.PLATEAU, PlaceKind.DESERT -> 1800.0
                    PlaceKind.SEA -> 2500.0; else -> 900.0
                },
            )
        }
    }

    fun curated(json: String): Curated {
        val o = JSONObject(json)
        val earth = o.getJSONArray("earth").let { a -> (0 until a.length()).map { a.getJSONObject(it).let { e -> CuratedFact(e.getString("k"), e.getString("v")) } } }
        val peaks = o.getJSONArray("peaks").let { a -> (0 until a.length()).map { a.getJSONObject(it).let { e -> Peak(e.getString("n"), e.getDouble("lat"), e.getDouble("lon"), e.getInt("elev"), e.optString("range")) } } }
        val trenches = o.getJSONArray("trenches").let { a -> (0 until a.length()).map { a.getJSONObject(it).let { e -> Trench(e.getString("n"), e.getDouble("lat"), e.getDouble("lon"), e.getInt("depth"), e.optString("ocean")) } } }
        val continents = o.getJSONArray("continents").let { a -> (0 until a.length()).map { a.getJSONObject(it).let { e -> Continent(e.getString("n"), e.getDouble("lat"), e.getDouble("lon"), e.optString("area"), e.optString("fact")) } } }
        val topics = o.getJSONArray("topics").let { a -> (0 until a.length()).map { a.getJSONObject(it).let { e -> Topic(e.getString("id"), e.getString("t"), e.getString("d")) } } }
        val src = o.optJSONObject("_sources")
        val sources = src?.keys()?.asSequence()?.associateWith { src.getString(it) } ?: emptyMap()
        return Curated(earth, peaks, trenches, continents, topics, sources)
    }

    /** USGS GeoJSON summary feed → quakes. Coordinates are [lon, lat, depthKm]. Invalid features are skipped. */
    fun usgs(json: String): Pair<Long, List<Quake>> {
        val root = JSONObject(json)
        val generated = root.optJSONObject("metadata")?.lng("generated") ?: 0L
        val feats = root.optJSONArray("features") ?: JSONArray()
        val out = ArrayList<Quake>(feats.length())
        for (i in 0 until feats.length()) {
            val f = feats.optJSONObject(i) ?: continue
            val p = f.optJSONObject("properties") ?: continue
            val c = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            if (c.length() < 2) continue
            val lon = c.dblAt(0) ?: continue
            val lat = c.dblAt(1) ?: continue
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
            out += Quake(
                id = f.optString("id", "q$i"),
                magnitude = p.dbl("mag"),
                magType = p.str("magType"),
                depthKm = if (c.length() > 2) c.dblAt(2) else null,
                lat = lat, lon = lon,
                timeMs = p.lng("time") ?: 0L,
                updatedMs = p.lng("updated"),
                place = p.str("place") ?: p.str("title") ?: "Unknown location",
                url = p.str("url"),
                tsunami = (p.int("tsunami") ?: 0) == 1,
                alert = p.str("alert"),
                felt = p.int("felt"),
                significance = p.int("sig"),
                status = p.str("status"),
                network = p.str("net"),
            )
        }
        return generated to out
    }

    fun localWeather(json: String, retrievedMs: Long, fromCache: Boolean): LocalWeather {
        val o = JSONObject(json)
        val cur = o.optJSONObject("current")?.let { c ->
            CurrentWeather(
                time = c.optString("time"),
                temperatureC = c.dbl("temperature_2m"), apparentC = c.dbl("apparent_temperature"),
                humidity = c.dbl("relative_humidity_2m"), precipitationMm = c.dbl("precipitation"),
                cloudCover = c.dbl("cloud_cover"), windSpeedKmh = c.dbl("wind_speed_10m"),
                windDirDeg = c.dbl("wind_direction_10m"), weatherCode = c.int("weather_code"),
                isDay = c.int("is_day")?.let { it == 1 }, pressureHpa = c.dbl("pressure_msl"),
            )
        }
        val hourly = o.optJSONObject("hourly")?.let { h ->
            val t = h.optJSONArray("time") ?: JSONArray()
            val temp = h.optJSONArray("temperature_2m"); val pp = h.optJSONArray("precipitation_probability"); val wc = h.optJSONArray("weather_code")
            (0 until t.length()).map { i -> HourlyPoint(t.getString(i), temp?.dblAt(i), pp?.dblAt(i), wc?.dblAt(i)?.toInt()) }
        } ?: emptyList()
        val daily = o.optJSONObject("daily")?.let { d ->
            val t = d.optJSONArray("time") ?: JSONArray()
            val mx = d.optJSONArray("temperature_2m_max"); val mn = d.optJSONArray("temperature_2m_min")
            val pr = d.optJSONArray("precipitation_sum"); val wc = d.optJSONArray("weather_code")
            val sr = d.optJSONArray("sunrise"); val ss = d.optJSONArray("sunset")
            (0 until t.length()).map { i ->
                DailyPoint(t.getString(i), mx?.dblAt(i), mn?.dblAt(i), pr?.dblAt(i), wc?.dblAt(i)?.toInt(),
                    sr?.optString(i)?.takeIf { it.isNotBlank() }, ss?.optString(i)?.takeIf { it.isNotBlank() })
            }
        } ?: emptyList()
        return LocalWeather(
            lat = o.optDouble("latitude"), lon = o.optDouble("longitude"),
            timeZone = o.str("timezone"), utcOffsetSeconds = o.int("utc_offset_seconds"),
            current = cur, hourly = hourly, daily = daily, retrievedMs = retrievedMs, fromCache = fromCache,
        )
    }

    /**
     * Multi-location Open-Meteo response (a JSON array, one object per coordinate in request order)
     * merged into the global grid. `chunks` are the raw responses in grid order.
     */
    fun weatherGrid(chunks: List<String>, lats: List<Double>, lons: List<Double>, retrievedMs: Long, fromCache: Boolean): WeatherGrid {
        val temp = ArrayList<Double?>(); val cloud = ArrayList<Double?>(); val precip = ArrayList<Double?>()
        val ws = ArrayList<Double?>(); val wd = ArrayList<Double?>(); val code = ArrayList<Int?>()
        var valid: String? = null
        for (chunk in chunks) {
            val trimmed = chunk.trimStart()
            val arr = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONArray().put(JSONObject(trimmed))
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i).optJSONObject("current")
                temp += c?.dbl("temperature_2m"); cloud += c?.dbl("cloud_cover"); precip += c?.dbl("precipitation")
                ws += c?.dbl("wind_speed_10m"); wd += c?.dbl("wind_direction_10m"); code += c?.int("weather_code")
                if (valid == null) valid = c?.str("time")
            }
        }
        val n = lats.size * lons.size
        require(temp.size == n) { "Grid size mismatch: got ${temp.size}, expected $n" }
        return WeatherGrid(lats, lons, temp, cloud, precip, ws, wd, code, valid, retrievedMs, fromCache)
    }
}
