package com.zinmedia.effects.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.RestrictTo
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.zinmedia.effects.FaceEffect
import com.zinmedia.effects.R
import com.zinmedia.effects.face.BeautyMesh
import com.zinmedia.effects.face.parseBeautyMesh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Memuat [FaceEffect] (gambar peta UV atau model .glb) menjadi [ActiveFaceEffect] siap gambar. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object FaceEffectLoader {
    @Volatile
    private var faceMesh: FaceMeshData? = null

    /** Muat [effect]; gagal (berkas tidak ada/rusak) = exception. */
    public suspend fun load(context: Context, effect: FaceEffect): ActiveFaceEffect {
        val app = context.applicationContext
        val mesh = faceMesh(app)
        return if (effect.isModel) {
            val bytes = loadBytes(app, effect.imageUrl)
            withContext(Dispatchers.Default) {
                val model = parseGlb(bytes)
                ActiveFaceEffect.Model(mesh, model, model.parts.map { part ->
                    part.textureBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                })
            }
        } else {
            ActiveFaceEffect.Paint(mesh, loadImage(app, effect.imageUrl) ?: error("Gambar efek tidak bisa dimuat: ${effect.imageUrl}"))
        }
    }

    @Volatile
    private var beautyMesh: BeautyMesh? = null

    /** Siapkan data beauty (jaring warp & bobot masker) untuk [engine]; sekali baca dari resource. */
    public suspend fun prepareBeauty(context: Context, engine: FaceEffectEngine) {
        engine.beautyMesh = beautyMesh ?: withContext(Dispatchers.IO) {
            parseBeautyMesh(context.applicationContext.resources.openRawResource(R.raw.zm_beauty_mesh).use { it.readBytes() })
        }.also { beautyMesh = it }
    }

    /** Segitiga, UV, dan kepala standar jaring wajah (sekali baca dari resource library). */
    private suspend fun faceMesh(context: Context): FaceMeshData = faceMesh ?: withContext(Dispatchers.IO) {
        parseFaceMesh(context.resources.openRawResource(R.raw.zm_face_mesh).use { it.readBytes() })
    }.also { faceMesh = it }

    /** Isi berkas [url]: aset (`file:///android_asset/…`), http/https, `content://`/`file://`, atau path. */
    private suspend fun loadBytes(context: Context, url: String): ByteArray = withContext(Dispatchers.IO) {
        when {
            url.startsWith(ASSET_PREFIX) -> context.assets.open(url.removePrefix(ASSET_PREFIX)).use { it.readBytes() }
            url.startsWith("http://") || url.startsWith("https://") -> {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                try {
                    check(connection.responseCode == HttpURLConnection.HTTP_OK) { "Efek tidak bisa diunduh: ${connection.responseCode}" }
                    connection.inputStream.use { it.readBytes() }
                } finally {
                    connection.disconnect()
                }
            }
            url.startsWith("content://") || url.startsWith("file://") ->
                requireNotNull(context.contentResolver.openInputStream(Uri.parse(url))) { "Efek tidak ditemukan: $url" }.use { it.readBytes() }
            else -> File(url).readBytes()
        }
    }

    private suspend fun loadImage(context: Context, url: String): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        return (result as? SuccessResult)?.image?.toBitmap()
    }

    private const val ASSET_PREFIX = "file:///android_asset/"
}
