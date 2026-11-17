package com.earthx.explorer.engine

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import org.json.JSONObject

/** Typed events coming from the JS engine after validation. */
sealed interface GlobeEvent {
    data object Ready : GlobeEvent
    data object DataReady : GlobeEvent
    data object Interact : GlobeEvent
    data object SpaceTapped : GlobeEvent
    data object ContextLost : GlobeEvent
    data object ContextRestored : GlobeEvent
    data class Progress(val pct: Int) : GlobeEvent
    data class Camera(val lat: Double, val lon: Double, val altKm: Double, val level: Int) : GlobeEvent
    data class LocationTapped(val lat: Double, val lon: Double, val countryId: String?, val stateName: String?, val stateType: String?) : GlobeEvent
    data class CityTapped(val index: Int) : GlobeEvent
    data class PlaceTapped(val name: String, val kind: String, val lat: Double, val lon: Double) : GlobeEvent
    data class QuakeTapped(val id: String) : GlobeEvent
    data class Measure(val aLat: Double, val aLon: Double, val bLat: Double?, val bLon: Double?, val km: Double?, val bearing: Double?) : GlobeEvent
    data class Time(val utcMs: Long, val playing: Boolean, val speed: Double, val live: Boolean) : GlobeEvent
    data class Imagery(val scope: String, val ok: Boolean, val loading: Boolean, val message: String?, val resM: Int?) : GlobeEvent
    data class Screenshot(val dataUrl: String) : GlobeEvent
    data class Fps(val fps: Int, val pixelRatio: Double) : GlobeEvent
    data class Error(val message: String) : GlobeEvent
}

/**
 * The only object exposed to JavaScript. Every message is size-limited, parsed as JSON,
 * matched against a fixed whitelist of types and range-checked before being delivered
 * on the main thread. Unknown or malformed messages are dropped.
 */
class GlobeBridge(private val onEvent: (GlobeEvent) -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    companion object {
        const val MAX_MESSAGE = 64 * 1024
        const val MAX_SCREENSHOT = 48 * 1024 * 1024

        private fun JSONObject.lat(k: String) = optDouble(k).takeIf { !it.isNaN() && it in -90.0..90.0 }
        private fun JSONObject.lon(k: String) = optDouble(k).takeIf { !it.isNaN() && it in -360.0..360.0 }
        private val ID = Regex("^[A-Za-z0-9_\\-.]{1,64}$")

        /** Pure parsing/validation, unit-testable. */
        fun parse(raw: String): GlobeEvent? {
            val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            val type = root.optString("type")
            val p = root.optJSONObject("payload") ?: JSONObject()
            if (type != "screenshot" && raw.length > MAX_MESSAGE) return null
            return when (type) {
                "ready" -> GlobeEvent.Ready
                "dataReady" -> GlobeEvent.DataReady
                "interact" -> GlobeEvent.Interact
                "spaceTapped" -> GlobeEvent.SpaceTapped
                "contextLost" -> GlobeEvent.ContextLost
                "contextRestored" -> GlobeEvent.ContextRestored
                "progress" -> GlobeEvent.Progress(p.optInt("pct").coerceIn(0, 100))
                "camera" -> GlobeEvent.Camera(p.lat("lat") ?: return null, p.lon("lon") ?: return null,
                    p.optDouble("altKm").takeIf { !it.isNaN() && it >= 0 } ?: return null, p.optInt("level", 1).coerceIn(1, 6))
                "locationTapped" -> {
                    val st = p.optJSONObject("state")
                    GlobeEvent.LocationTapped(p.lat("lat") ?: return null, p.lon("lon") ?: return null,
                        p.optString("countryId").takeIf { ID.matches(it) },
                        st?.optString("name")?.take(120)?.takeIf { it.isNotBlank() }, st?.optString("type")?.take(60))
                }
                "cityTapped" -> GlobeEvent.CityTapped(p.optInt("index", -1).takeIf { it >= 0 } ?: return null)
                "placeTapped" -> GlobeEvent.PlaceTapped(p.optString("name").take(120), p.optString("kind").take(20),
                    p.lat("lat") ?: return null, p.lon("lon") ?: return null)
                "quakeTapped" -> GlobeEvent.QuakeTapped(p.optString("id").takeIf { ID.matches(it) } ?: return null)
                "measure" -> {
                    val a = p.optJSONObject("a") ?: return null
                    val b = p.optJSONObject("b")
                    GlobeEvent.Measure(a.lat("lat") ?: return null, a.lon("lon") ?: return null, b?.lat("lat"), b?.lon("lon"),
                        p.optDouble("km").takeIf { !it.isNaN() }, p.optDouble("bearing").takeIf { !it.isNaN() })
                }
                "time" -> GlobeEvent.Time(p.optLong("utcMs"), p.optBoolean("playing"), p.optDouble("speed", 1.0), p.optBoolean("live"))
                "imagery" -> GlobeEvent.Imagery(p.optString("scope").take(20), p.optBoolean("ok"), p.optBoolean("loading"),
                    p.optString("msg").take(200).takeIf { it.isNotBlank() }, if (p.has("res")) p.optInt("res") else null)
                "screenshot" -> {
                    val d = p.optString("dataUrl")
                    if (raw.length > MAX_SCREENSHOT || !d.startsWith("data:image/jpeg;base64,")) null else GlobeEvent.Screenshot(d)
                }
                "fps" -> GlobeEvent.Fps(p.optInt("fps").coerceIn(0, 240), p.optDouble("pixelRatio", 1.0))
                "error" -> GlobeEvent.Error(p.optString("msg").take(300))
                else -> null
            }
        }
    }

    @JavascriptInterface
    fun postMessage(raw: String?) {
        if (raw == null || raw.length > MAX_SCREENSHOT) return
        val ev = parse(raw) ?: return
        main.post { onEvent(ev) }
    }
}
