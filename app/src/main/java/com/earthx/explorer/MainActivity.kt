package com.earthx.explorer

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.earthx.explorer.ui.EarthXScreen
import com.earthx.explorer.ui.theme.EarthXTheme
import com.earthx.explorer.viewmodels.EarthViewModel

class MainActivity : ComponentActivity() {
    private val vm: EarthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        setContent {
            EarthXTheme { EarthXScreen(vm) }
        }
    }
}
