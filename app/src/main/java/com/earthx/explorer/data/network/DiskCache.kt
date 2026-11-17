package com.earthx.explorer.data.network

import com.earthx.explorer.data.database.CacheDao
import com.earthx.explorer.data.database.CacheEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** File-backed cache with Room metadata and LRU-by-age trimming. */
class DiskCache(private val namespace: String, private val dir: File, private val dao: CacheDao, private val maxBytes: Long = 300L * 1024 * 1024) {
    init { dir.mkdirs() }

    data class Hit(val bytes: ByteArray, val retrievedMs: Long)

    private fun fileName(key: String): String {
        val md = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
        return md.joinToString("") { "%02x".format(it) }
    }

    suspend fun get(key: String): Hit? = withContext(Dispatchers.IO) {
        val e = dao.get(key)?.takeIf { it.source == namespace } ?: return@withContext null
        val f = File(dir, e.fileName)
        if (!f.exists()) { dao.remove(key); return@withContext null }
        Hit(f.readBytes(), e.retrievedMs)
    }

    suspend fun put(key: String, bytes: ByteArray, @Suppress("UNUSED_PARAMETER") source: String = namespace, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val name = fileName(key)
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(File(dir, name))
        dao.put(CacheEntry(key, name, now, bytes.size.toLong(), namespace))
        trim()
    }

    private suspend fun trim() {
        var total = dao.totalBytesIn(namespace)
        if (total <= maxBytes) return
        for (e in dao.allIn(namespace)) {
            if (total <= maxBytes * 0.8) break
            File(dir, e.fileName).delete()
            dao.remove(e.key)
            total -= e.bytes
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dao.clearIn(namespace)
        dir.listFiles()?.forEach { it.delete() }
    }

    suspend fun sizeBytes(): Long = dao.totalBytesIn(namespace)
}
