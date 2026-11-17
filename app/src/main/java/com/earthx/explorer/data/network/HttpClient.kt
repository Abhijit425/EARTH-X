package com.earthx.explorer.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

class HttpException(val code: Int, message: String) : IOException(message)

/**
 * Minimal native HTTPS client (HttpURLConnection). Handles gzip, timeouts and
 * HTTP 429 / 503 with exponential back-off (respecting Retry-After when present).
 * Only https URLs on an explicit host allow-list are accepted.
 */
class HttpClient(private val userAgent: String) {

    companion object {
        val ALLOWED_HOSTS = setOf(
            "api.open-meteo.com",
            "earthquake.usgs.gov",
            "gibs.earthdata.nasa.gov",
        )
        const val MAX_BYTES = 40L * 1024 * 1024
    }

    suspend fun getBytes(url: String, maxAttempts: Int = 3, timeoutMs: Int = 20_000): ByteArray = withContext(Dispatchers.IO) {
        val u = URL(url)
        require(u.protocol == "https") { "Only HTTPS is allowed" }
        require(u.host in ALLOWED_HOSTS) { "Host not allowed: ${u.host}" }
        var attempt = 0
        var wait = 1500L
        while (true) {
            attempt++
            val conn = (u.openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs * 2
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept-Encoding", "gzip")
                instanceFollowRedirects = true
            }
            try {
                val code = conn.responseCode
                if (code == 429 || code == 503) {
                    val retryAfter = conn.getHeaderField("Retry-After")?.toLongOrNull()?.times(1000)
                    if (attempt >= maxAttempts) throw HttpException(code, if (code == 429) "Rate limited by server" else "Service unavailable")
                    delay(retryAfter?.coerceAtMost(30_000) ?: wait)
                    wait *= 2
                    continue
                }
                if (code !in 200..299) {
                    val err = runCatching { conn.errorStream?.bufferedReader()?.readText()?.take(300) }.getOrNull()
                    throw HttpException(code, "HTTP $code${err?.let { ": $it" } ?: ""}")
                }
                val raw = conn.inputStream
                val stream = if ("gzip".equals(conn.contentEncoding, true)) GZIPInputStream(raw) else raw
                return@withContext stream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw IOException("Response too large")
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
            } catch (e: IOException) {
                if (e is HttpException || attempt >= maxAttempts) throw e
                delay(wait); wait *= 2
            } finally {
                conn.disconnect()
            }
        }
        ByteArray(0)
    }

    suspend fun getString(url: String): String = String(getBytes(url), Charsets.UTF_8)
}
