package com.earthx.explorer.data.repositories

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saves globe screenshots to Pictures/EARTH-X (MediaStore on Android 10+, app folder before). */
class ScreenshotSaver(private val context: Context) {
    suspend fun save(dataUrl: String): String = withContext(Dispatchers.IO) {
        require(dataUrl.startsWith("data:image/jpeg;base64,")) { "Unexpected screenshot format" }
        val bytes = Base64.decode(dataUrl.substringAfter(","), Base64.DEFAULT)
        require(bytes.size in 1..(60 * 1024 * 1024)) { "Screenshot too large" }
        val name = "EARTH-X_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/EARTH-X")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Could not create image")
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Could not write image")
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "Pictures/EARTH-X/$name"
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "EARTH-X").apply { mkdirs() }
            File(dir, name).writeBytes(bytes)
            dir.absolutePath + "/" + name
        }
    }
}
