package com.earthx.explorer.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    const val EARTH_RADIUS_KM = 6371.0088
    private const val D2R = Math.PI / 180.0
    private const val R2D = 180.0 / Math.PI

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = (lat2 - lat1) * D2R
        val dLon = (lon2 - lon1) * D2R
        val a = sin(dLat / 2).let { it * it } + cos(lat1 * D2R) * cos(lat2 * D2R) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(a)))
    }

    fun initialBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = lat1 * D2R; val p2 = lat2 * D2R; val dl = (lon2 - lon1) * D2R
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (atan2(y, x) * R2D + 360.0) % 360.0
    }

    fun wrapLon(lon: Double): Double {
        var l = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        if (l == -180.0) l = 180.0
        return l
    }

    /** Sub-solar point (same low-precision algorithm as the JS engine). */
    fun subsolarPoint(utcMs: Long): Pair<Double, Double> {
        val jd = utcMs / 86_400_000.0 + 2440587.5
        val d = jd - 2451545.0
        val g = (357.529 + 0.98560028 * d) * D2R
        val q = 280.459 + 0.98564736 * d
        val l = (q + 1.915 * sin(g) + 0.020 * sin(2 * g)) * D2R
        val e = (23.439 - 0.00000036 * d) * D2R
        val ra = atan2(cos(e) * sin(l), cos(l))
        val dec = asin(sin(e) * sin(l))
        val gmst = ((18.697374558 + 24.06570982441908 * d) % 24.0 + 24.0) % 24.0
        return dec * R2D to wrapLon(ra * R2D - gmst * 15.0)
    }

    /** Solar elevation angle in degrees (positive = sun above horizon). */
    fun solarElevation(lat: Double, lon: Double, utcMs: Long): Double {
        val (sLat, sLon) = subsolarPoint(utcMs)
        val cosZ = sin(lat * D2R) * sin(sLat * D2R) + cos(lat * D2R) * cos(sLat * D2R) * cos((lon - sLon) * D2R)
        return 90.0 - Math.acos(cosZ.coerceIn(-1.0, 1.0)) * R2D
    }

    fun daylightLabel(elev: Double) = when {
        elev > 6 -> "Daylight"
        elev > -0.833 -> "Sun near horizon"
        elev > -6 -> "Civil twilight"
        elev > -12 -> "Nautical twilight"
        elev > -18 -> "Astronomical twilight"
        else -> "Night"
    }

    fun zoomLevel(altKm: Double) = when {
        altKm > 12000 -> 1; altKm > 5000 -> 2; altKm > 1500 -> 3; altKm > 450 -> 4; altKm > 90 -> 5; else -> 6
    }

    val LEVEL_NAMES = listOf("", "Earth from space", "Continents", "Countries", "States & provinces", "Cities", "Regional detail")

    /** Approximate solar (mean) time offset from longitude, used only when no IANA zone is known. */
    fun solarOffsetSeconds(lon: Double): Int = (lon / 15.0 * 3600).roundToInt()

    fun localTimeText(utcMs: Long, zone: String?, lon: Double): Pair<String, Boolean> {
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.getDefault())
        val zid = zone?.takeIf { it.isNotBlank() }?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        return if (zid != null) {
            Instant.ofEpochMilli(utcMs).atZone(zid).format(fmt) + " (" + zid.id + ")" to true
        } else {
            val off = ZoneOffset.ofTotalSeconds(solarOffsetSeconds(lon).coerceIn(-18 * 3600, 18 * 3600))
            "≈ " + Instant.ofEpochMilli(utcMs).atOffset(off).format(fmt) + " (solar time)" to false
        }
    }

    fun formatLat(lat: Double) = String.format(Locale.US, "%.3f° %s", abs(lat), if (lat >= 0) "N" else "S")
    fun formatLon(lon: Double) = String.format(Locale.US, "%.3f° %s", abs(lon), if (lon >= 0) "E" else "W")
    fun formatCoords(lat: Double, lon: Double) = "${formatLat(lat)}, ${formatLon(lon)}"

    fun formatPopulation(p: Long?): String = when {
        p == null || p <= 0 -> "—"
        p >= 1_000_000_000 -> String.format(Locale.US, "%.2f billion", p / 1e9)
        p >= 1_000_000 -> String.format(Locale.US, "%.1f million", p / 1e6)
        else -> String.format(Locale.US, "%,d", p)
    }

    fun formatArea(a: Double?): String = if (a == null || a <= 0) "—" else String.format(Locale.US, "%,.0f km²", a)

    fun formatDistance(km: Double): String = when {
        km < 1 -> String.format(Locale.US, "%.0f m", km * 1000)
        km < 100 -> String.format(Locale.US, "%.1f km (%.1f mi)", km, km * 0.621371)
        else -> String.format(Locale.US, "%,.0f km (%,.0f mi)", km, km * 0.621371)
    }
}
