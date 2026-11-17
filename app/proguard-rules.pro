# Keep the JavaScript bridge methods callable from the WebView
-keepclassmembers class com.earthx.explorer.engine.GlobeBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
