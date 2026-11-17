package com.earthx.explorer.data.repositories

import com.earthx.explorer.data.model.QuakeFeed
import com.earthx.explorer.data.model.QuakeRange
import com.earthx.explorer.data.network.DiskCache
import com.earthx.explorer.data.network.HttpClient
import com.earthx.explorer.data.parse.Parsers

/** USGS real-time GeoJSON summary feeds (public domain). Updated by USGS every minute. */
class QuakeRepository(private val http: HttpClient, private val cache: DiskCache) {

    companion object {
        fun url(range: QuakeRange) = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/${range.feed}.geojson"
        fun ttl(range: QuakeRange) = when (range) {
            QuakeRange.HOUR -> 60_000L; QuakeRange.DAY -> 5 * 60_000L; QuakeRange.WEEK -> 15 * 60_000L; QuakeRange.MONTH -> 30 * 60_000L
        }
    }

    suspend fun feed(range: QuakeRange, online: Boolean, force: Boolean = false): QuakeFeed {
        val key = "usgs:${range.feed}"
        val hit = cache.get(key)
        val now = System.currentTimeMillis()
        if (hit != null && (!online || (!force && now - hit.retrievedMs < ttl(range)))) {
            val (gen, ev) = Parsers.usgs(String(hit.bytes))
            return QuakeFeed(range, ev, gen, hit.retrievedMs, true)
        }
        if (!online) throw IllegalStateException("Offline and no cached earthquake feed")
        return try {
            val body = http.getBytes(url(range), timeoutMs = 30_000)
            val (gen, ev) = Parsers.usgs(String(body))
            cache.put(key, body, "usgs")
            QuakeFeed(range, ev, gen, now, false)
        } catch (e: Exception) {
            if (hit != null) {
                val (gen, ev) = Parsers.usgs(String(hit.bytes))
                QuakeFeed(range, ev, gen, hit.retrievedMs, true)
            } else throw e
        }
    }
}
