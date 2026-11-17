package com.earthx.explorer.engine

import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sends commands to the JS engine (window.EarthX.cmd). Commands issued before the page
 * finished loading are queued and flushed once the engine reports ready.
 */
class GlobeController {
    private var webView: WebView? = null
    private var pageReady = false
    private val queue = ArrayList<String>()

    fun attach(w: WebView) { webView = w }
    fun detach() { webView = null; pageReady = false }

    /** Called for every engine event: the first one proves window.EarthX exists. */
    fun ensureReady() { if (!pageReady && webView != null) onPageReady() }

    /** A new page is being loaded: queue commands until it reports in. */
    fun resetPage() { pageReady = false }

    fun onPageReady() {
        pageReady = true
        val pending = queue.toList(); queue.clear()
        pending.forEach { eval(it) }
    }

    fun send(type: String, payload: JSONObject = JSONObject()) {
        val msg = JSONObject().put("type", type).put("payload", payload).toString()
        if (!pageReady || webView == null) { queue += msg; if (queue.size > 200) queue.removeAt(0); return }
        eval(msg)
    }

    private fun eval(msg: String) {
        // JSONObject.quote produces a safe JS string literal; the engine JSON.parses it.
        webView?.evaluateJavascript("window.EarthX && window.EarthX.cmd(${JSONObject.quote(msg)})", null)
    }

    // ---- convenience API
    fun flyTo(lat: Double, lon: Double, altKm: Double? = null) =
        send("flyTo", JSONObject().put("lat", lat).put("lon", lon).apply { if (altKm != null) put("altKm", altKm) })
    fun flyToBBox(bbox: List<Double>, lat: Double, lon: Double) =
        send("flyToBBox", JSONObject().put("bbox", JSONArray(bbox)).put("lat", lat).put("lon", lon))
    fun selectCountry(id: String?, fly: Boolean) = send("selectCountry", JSONObject().put("id", id ?: JSONObject.NULL).put("fly", fly))
    fun toggle(type: String, on: Boolean) = send(type, JSONObject().put("on", on))
}
