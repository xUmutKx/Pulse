package com.umutk.pulse.extras.palette.core.icon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Two-tier cache. Memory keeps the visible grid instant; disk survives process
 * death so a 20 000-app theme is generated once and then only diffed.
 * Keys embed the theme render key, so changing any slider invalidates cleanly.
 */
class IconCache(private val ctx: Context) {

    private val maxMem = (Runtime.getRuntime().maxMemory() / 8).toInt()
    private val mem = object : LruCache<String, Bitmap>(maxMem) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private val dir: File by lazy {
        File(ctx.cacheDir, "icons").apply { mkdirs() }
    }

    fun key(app: String, themeKey: Int, size: Int) = "$app@$themeKey@$size"

    fun getMemory(key: String): Bitmap? = mem.get(key)

    suspend fun get(key: String): Bitmap? {
        mem.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = File(dir, key.hashCode().toString() + ".png")
            if (!f.exists()) null
            else BitmapFactory.decodeFile(f.absolutePath)?.also { mem.put(key, it) }
        }
    }

    suspend fun put(key: String, bmp: Bitmap, persist: Boolean = true) {
        mem.put(key, bmp)
        if (!persist) return
        withContext(Dispatchers.IO) {
            runCatching {
                FileOutputStream(File(dir, key.hashCode().toString() + ".png")).use {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }

    fun clearMemory() = mem.evictAll()

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mem.evictAll()
        dir.listFiles()?.forEach { it.delete() }
        Unit
    }

    fun diskBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L
}
