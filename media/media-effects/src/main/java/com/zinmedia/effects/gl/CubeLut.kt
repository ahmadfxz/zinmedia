package com.zinmedia.effects.gl

import android.util.Log
import androidx.annotation.RestrictTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

/**
 * LUT 3D berukuran [size]³, disusun sebagai tekstur 2D: lebar `size × size` (irisan biru
 * berderet), tinggi `size`. Piksel (x = b·size + r, y = g) = warna hasil untuk (r, g, b).
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class CubeLut(public val size: Int, public val rgba: ByteBuffer) {
    public val width: Int get() = size * size
    public val height: Int get() = size
}

/** Baca file `.cube` (urutan data: merah tercepat, lalu hijau, lalu biru). */
internal fun parseCube(input: InputStream): CubeLut? {
    var size = 0
    val values = ArrayList<Float>()
    input.bufferedReader().forEachLine { raw ->
        val line = raw.trim()
        when {
            line.isEmpty() || line.startsWith("#") -> Unit
            line.startsWith("LUT_3D_SIZE") -> size = line.substringAfter("LUT_3D_SIZE").trim().toIntOrNull() ?: 0
            line.first().isDigit() || line.first() == '-' || line.first() == '.' -> {
                val parts = line.split(Whitespace)
                if (parts.size >= 3) parts.take(3).forEach { values += it.toFloatOrNull() ?: 0f }
            }
        }
    }
    if (size < 2 || values.size != size * size * size * 3) return null
    val buffer = ByteBuffer.allocateDirect(size * size * size * 4).order(ByteOrder.nativeOrder())
    for (g in 0 until size) {
        for (b in 0 until size) {
            for (r in 0 until size) {
                val i = (r + g * size + b * size * size) * 3
                buffer.put(channel(values[i])).put(channel(values[i + 1])).put(channel(values[i + 2])).put(255.toByte())
            }
        }
    }
    buffer.rewind()
    return CubeLut(size, buffer)
}

private fun channel(v: Float): Byte = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()

private val Whitespace = Regex("\\s+")

private val cache = ConcurrentHashMap<String, CubeLut>()

/** Unduh & baca LUT (disimpan di memori setelah dimuat sekali); `null` bila gagal. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public suspend fun loadCubeLut(url: String): CubeLut? {
    cache[url]?.let { return it }
    return withContext(Dispatchers.IO) {
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                connection.inputStream.use { parseCube(it) }?.also { cache[url] = it }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.w("CubeLut", "Gagal memuat LUT: $url", e)
            null
        }
    }
}
