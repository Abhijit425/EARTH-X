package com.earthx.explorer.data.repositories

import android.net.Uri
import android.webkit.WebResourceResponse
import com.earthx.explorer.data.model.GIBS_LAYERS
import com.earthx.explorer.data.network.DiskCache
import com.earthx.explorer.data.network.HttpClient
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream

/**
 * Native, validating proxy for NASA GIBS WMS (EPSG:4326) requests coming from the WebView engine.
 * The WebView asks for https://appassets.androidplatform.net/net/gibs?<WMS params>; only documented
 * parameters with sane values are forwarded to https://gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi.
 * Responses are cached on disk so previously viewed imagery works offline.
 */
class ImageryProxy(private val http: HttpClient, private val cache: DiskCache, private val isOnline: () -> Boolean) {

    companion object {
        const val GIBS_WMS = "https://gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi"
        private val LAYER_IDS = GIBS_LAYERS.map { it.id }.toSet()
        private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

        /** Returns the upstream URL, or null if the request is not acceptable. */
        fun buildUpstream(q: Map<String, String>): String? {
            val layer = q["LAYERS"] ?: return null
            if (layer !in LAYER_IDS) return null
            val bbox = q["BBOX"]?.split(',')?.mapNotNull { it.toDoubleOrNull() } ?: return null
            if (bbox.size != 4) return null
            val (w, s, e, n) = bbox
            if (w < -180.0 || e > 180.0 || s < -90.0 || n > 90.0 || w >= e || s >= n) return null
            val width = q["WIDTH"]?.toIntOrNull() ?: return null
            val height = q["HEIGHT"]?.toIntOrNull() ?: return null
            if (width !in 16..4096 || height !in 16..4096) return null
            val format = q["FORMAT"] ?: "image/jpeg"
            if (format != "image/jpeg" && format != "image/png") return null
            val time = q["TIME"]
            if (time != null && !DATE.matches(time)) return null
            val b = Uri.parse(GIBS_WMS).buildUpon()
                .appendQueryParameter("SERVICE", "WMS").appendQueryParameter("REQUEST", "GetMap")
                .appendQueryParameter("VERSION", "1.1.1").appendQueryParameter("LAYERS", layer)
                .appendQueryParameter("STYLES", "").appendQueryParameter("SRS", "EPSG:4326")
                .appendQueryParameter("BBOX", bbox.joinToString(",") { "%.5f".format(java.util.Locale.US, it) })
                .appendQueryParameter("WIDTH", width.toString()).appendQueryParameter("HEIGHT", height.toString())
                .appendQueryParameter("FORMAT", format).appendQueryParameter("TRANSPARENT", "FALSE")
            if (time != null) b.appendQueryParameter("TIME", time)
            return b.build().toString()
        }

        fun isImage(bytes: ByteArray): Boolean =
            bytes.size > 4 && ((bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) ||
                (bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte()))
    }

    fun handle(uri: Uri): WebResourceResponse {
        val q = uri.queryParameterNames.associateWith { uri.getQueryParameter(it) ?: "" }
        val upstream = buildUpstream(q) ?: return error(400, "Rejected imagery request")
        val mime = q["FORMAT"] ?: "image/jpeg"
        return runBlocking {
            val hit = cache.get(upstream)
            if (hit != null) return@runBlocking ok(hit.bytes, mime)
            if (!isOnline()) return@runBlocking error(503, "Offline")
            try {
                val bytes = http.getBytes(upstream, maxAttempts = 2, timeoutMs = 25_000)
                if (!isImage(bytes)) return@runBlocking error(502, "GIBS returned no image (layer/date unavailable)")
                cache.put(upstream, bytes, "gibs")
                ok(bytes, mime)
            } catch (e: Exception) {
                error(502, e.message ?: "Upstream error")
            }
        }
    }

    private fun headers() = mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "max-age=86400")
    private fun ok(b: ByteArray, mime: String) = WebResourceResponse(mime, null, 200, "OK", headers(), ByteArrayInputStream(b))
    private fun error(code: Int, msg: String) = WebResourceResponse("text/plain", "utf-8", code, msg.take(60).ifBlank { "Error" }, headers(), ByteArrayInputStream(msg.toByteArray()))
}
