package com.earthx.explorer.engine

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.webkit.WebViewAssetLoader
import com.earthx.explorer.data.repositories.ImageryProxy

private const val HOST = "appassets.androidplatform.net"

/**
 * Hosts the Three.js engine. Assets are served from the APK through WebViewAssetLoader on a
 * secure https origin; /net/gibs requests are answered by the native validating imagery proxy.
 * No other network access or navigation is possible from the page.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun GlobeView(
    url: String,
    bridge: GlobeBridge,
    controller: GlobeController,
    imagery: ImageryProxy,
    onRenderProcessGone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val assetLoader = remember {
        WebViewAssetLoader.Builder()
            .setDomain(HOST)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }

    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(Color.BLACK)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            with(settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mediaPlaybackRequiresUserGesture = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                setGeolocationEnabled(false)
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                offscreenPreRaster = true
            }
            addJavascriptInterface(bridge, "AndroidBridge")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val u: Uri = request.url
                    if (u.host != HOST) return WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), null)
                    if (u.path?.startsWith("/net/gibs") == true) return imagery.handle(u)
                    return assetLoader.shouldInterceptRequest(u)
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.host != HOST

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    // The renderer crashed or was killed to reclaim memory: rebuild instead of crashing the app.
                    controller.detach()
                    (view.parent as? android.view.ViewGroup)?.removeView(view)
                    view.destroy()
                    onRenderProcessGone()
                    return true
                }
            }
            controller.attach(this)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> { controller.send("pause"); webView.onPause() }
                Lifecycle.Event.ON_RESUME -> { webView.onResume(); controller.send("resume") }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    DisposableEffect(webView) {
        onDispose {
            controller.detach()
            webView.stopLoading()
            webView.removeJavascriptInterface("AndroidBridge")
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier,
        update = { w ->
            if (w.tag != url) {
                w.tag = url
                controller.resetPage()
                controller.attach(w)
                w.loadUrl(url)
            }
        },
    )
}
