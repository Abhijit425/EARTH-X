package com.earthx.explorer

import android.app.Application
import com.earthx.explorer.data.database.EarthDatabase
import com.earthx.explorer.data.network.ConnectivityObserver
import com.earthx.explorer.data.network.DiskCache
import com.earthx.explorer.data.network.HttpClient
import com.earthx.explorer.data.repositories.FavoritesRepository
import com.earthx.explorer.data.repositories.GeoRepository
import com.earthx.explorer.data.repositories.ImageryProxy
import com.earthx.explorer.data.repositories.QuakeRepository
import com.earthx.explorer.data.repositories.ScreenshotSaver
import com.earthx.explorer.data.repositories.SettingsRepository
import com.earthx.explorer.data.repositories.WeatherRepository
import java.io.File

/** Simple manual dependency container. */
class AppContainer(app: Application) {
    val db = EarthDatabase.create(app)
    val http = HttpClient("EARTH-X/${BuildConfig.VERSION_NAME} (Android; com.earthx.explorer)")
    val connectivity = ConnectivityObserver(app)
    val dataCache = DiskCache("data", File(app.cacheDir, "data"), db.cache(), maxBytes = 120L * 1024 * 1024)
    val imageCache = DiskCache("imagery", File(app.cacheDir, "imagery"), db.cache(), maxBytes = 400L * 1024 * 1024)
    val geo = GeoRepository(app)
    val weather = WeatherRepository(http, dataCache)
    val quakes = QuakeRepository(http, dataCache)
    val imagery = ImageryProxy(http, imageCache) { connectivity.online.value }
    val settings = SettingsRepository(app)
    val favorites = FavoritesRepository(db)
    val screenshots = ScreenshotSaver(app)
}

class EarthXApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
