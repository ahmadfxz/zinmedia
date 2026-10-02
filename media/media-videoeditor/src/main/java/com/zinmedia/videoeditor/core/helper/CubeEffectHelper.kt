package com.zinmedia.videoeditor.core.helper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

//
//fun parseCubeToLutCube(inputStream: InputStream): Array<Array<IntArray>>? {
//    val reader = BufferedReader(InputStreamReader(inputStream))
//    val colorValues = mutableListOf<Float>()
//    var lutSize = 0
//
//    var line: String?
//
//    try {
//        while (reader.readLine().also { line = it } != null) {
//            val trimmed = line!!.trim()
//
//            if (trimmed.isEmpty() ||
//                trimmed.startsWith("#") ||
//                trimmed.startsWith("TITLE") ||
//                trimmed.startsWith("DOMAIN_")
//            ) continue
//
//            // Get LUT size
//            if (trimmed.startsWith("LUT_3D_SIZE")) {
//                lutSize = trimmed.substringAfter("LUT_3D_SIZE").trim().toInt()
//                continue
//            }
//
//            // RGB values (float)
//            val parts = trimmed.split(Regex("\\s+"))
//            if (parts.size == 3) {
//                colorValues.add(parts[0].toFloat())
//                colorValues.add(parts[1].toFloat())
//                colorValues.add(parts[2].toFloat())
//            }
//        }
//    } catch (e: Exception) {
//        e.printStackTrace()
//        return null
//    }
//
//    // Validate
//    if (lutSize == 0 ||
//        colorValues.size != lutSize * lutSize * lutSize * 3
//    ) {
//        return null
//    }
//
//    // Convert to 3D LUT cube
//    val lutCube = Array(lutSize) {
//        Array(lutSize) {
//            IntArray(lutSize)
//        }
//    }
//
//    var idx = 0
//    for (r in 0 until lutSize) {
//        for (g in 0 until lutSize) {
//            for (b in 0 until lutSize) {
//
//                val rr = (colorValues[idx++] * 255f).toInt().coerceIn(0, 255)
//                val gg = (colorValues[idx++] * 255f).toInt().coerceIn(0, 255)
//                val bb = (colorValues[idx++] * 255f).toInt().coerceIn(0, 255)
//
//                val argb = (255 shl 24) or (rr shl 16) or (gg shl 8) or bb
//
//                lutCube[r][g][b] = argb
//            }
//        }
//    }
//
//    return lutCube
//}

fun parseCubeToLutCube(inputStream: InputStream): Array<Array<IntArray>>? {
    val reader = BufferedReader(InputStreamReader(inputStream))
    val colorValues = mutableListOf<Float>()
    var lutSize = 0

    try {
        reader.forEachLine { raw ->
            val line = raw.trim()

            if (line.isEmpty() ||
                line.startsWith("#") ||
                line.startsWith("TITLE") ||
                line.startsWith("DOMAIN_")
            ) return@forEachLine

            if (line.startsWith("LUT_3D_SIZE")) {
                lutSize = line.substringAfter("LUT_3D_SIZE").trim().toInt()
                return@forEachLine
            }

            val parts = line.split(Regex("\\s+"))
            if (parts.size == 3) {
                colorValues.add(parts[0].toFloat())
                colorValues.add(parts[1].toFloat())
                colorValues.add(parts[2].toFloat())
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }

    // validasi ukuran
    if (lutSize == 0 || colorValues.size != lutSize * lutSize * lutSize * 3) {
        return null
    }

    // buat 3D cube
    val lutCube = Array(lutSize) {
        Array(lutSize) { IntArray(lutSize) }
    }

    var idx = 0

    // ============================
    //   KOREKSI PENTING!
    //   Urutan file .cube:
    //   BLUE → GREEN → RED
    // ============================
    for (b in 0 until lutSize) {
        for (g in 0 until lutSize) {
            for (r in 0 until lutSize) {

                val rr = (colorValues[idx++] * 255f).toInt().coerceIn(0, 255)
                val gg = (colorValues[idx++] * 255f).toInt().coerceIn(0, 255)
                val bb = (colorValues[idx++] * 255f).toInt().coerceIn(0, 255)

                val argb = (255 shl 24) or (rr shl 16) or (gg shl 8) or bb

                lutCube[r][g][b] = argb
            }
        }
    }

    return lutCube
}


suspend fun loadLutCubeFromUrl(
    url: String
): Array<Array<IntArray>>? = withContext(Dispatchers.IO) {

    var connection: HttpURLConnection? = null
    var input: InputStream? = null

    return@withContext try {
        connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
            doInput = true
            connect()
        }

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            null
        } else {
            input = connection.inputStream
            parseCubeToLutCube(input!!)
        }

    } catch (e: Exception) {
        e.printStackTrace()
        null
    } finally {
        try { input?.close() } catch (_: Exception) {}
        connection?.disconnect()
    }
}


suspend fun loadLutCubeFromUrlSuspend(url: String): Array<Array<IntArray>>? {
    return withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.connect()

            if (connection.responseCode != 200) {
                Log.e("LUT", "Failed to fetch LUT. Response code: ${connection.responseCode}")
                return@withContext null
            }

            // Baca seluruh file terlebih dahulu
            val data = connection.inputStream.use { it.readBytes() }

            // Parsing LUT
            val lut = parseCubeToLutCube(ByteArrayInputStream(data))
            lut
        } catch (e: Exception) {
            Log.e("LUT", "Exception while loading LUT from URL: $url", e)
            null
        }
    }
}

fun createSimpleLutCube(): Array<Array<IntArray>> {
    val size = 2
    val lutCube = Array(size) {
        Array(size) { IntArray(size) }
    }

    for (r in 0 until size) {
        for (g in 0 until size) {
            for (b in 0 until size) {
                val rr = (r * 255f / (size - 1)).toInt()
                val gg = (g * 255f / (size - 1)).toInt()
                val bb = (b * 255f / (size - 1)).toInt()
                lutCube[r][g][b] = (255 shl 24) or (rr shl 16) or (gg shl 8) or bb
            }
        }
    }
    return lutCube
}


