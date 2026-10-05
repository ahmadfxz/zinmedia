package com.zinmedia.effects

import android.content.Context
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.zinmedia.effects.face.FaceDetector
import com.zinmedia.effects.face.ensureFaceModel
import com.zinmedia.effects.gl.ColorParams
import com.zinmedia.effects.gl.EffectsFilterRender
import com.zinmedia.effects.gl.loadCubeLut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Efek kamera untuk siaran RootEncoder: filter warna (termasuk LUT), penghalus kulit, dan satu efek
 * wajah (MediaPipe) sekaligus. Pasang [filterRender] ke pipeline GL stream; efek tampil di preview
 * dan ikut terkirim ke penonton.
 *
 * ```kotlin
 * val effects = ZinEffects(context)
 * stream.getGlInterface().addFilter(effects.filterRender)
 * effects.setSmoothing(0.6f)
 * scope.launch { effects.setFaceEffect(FaceEffect("Kacamata", url, FaceAnchor.Eyes)) }
 * // selesai:
 * effects.release()
 * ```
 *
 * Aplikasi harus menyertakan RootEncoder sendiri (`com.github.pedroSG94.RootEncoder:library`).
 *
 * @param lutFilters filter LUT `.cube` milik aplikasi, ditambahkan setelah filter bawaan.
 * @param faceModelUrl model MediaPipe Face Landmarker; diunduh sekali lalu disimpan di perangkat.
 * @param maxFaces jumlah wajah yang diberi efek (1..3); 1 paling ringan.
 * @param mirrored `true` bila frame di pipeline adalah gambar cermin (mis. kamera depan yang
 *   di-mirror untuk siaran), agar efek kiri/kanan tetap di sisi yang benar milik orangnya.
 */
public class ZinEffects(
    context: Context,
    lutFilters: List<LutFilter> = emptyList(),
    private val faceModelUrl: String = DEFAULT_FACE_MODEL_URL,
    maxFaces: Int = 1,
    mirrored: Boolean = false,
) {
    private val context = context.applicationContext
    private val maxFaces = maxFaces.coerceIn(1, 3)
    private val render = EffectsFilterRender()
    private val filters = effectFilters(lutFilters)
    private var filterIndex = 0
    private var smoothing = 0f
    private val prepareLock = Mutex()
    private var detector: FaceDetector? = null
    private var released = false

    /**
     * Frame di pipeline adalah gambar cermin. Ubah saat kamera dibalik bila mirror siaran ikut
     * berubah.
     */
    public var mirrored: Boolean
        get() = render.mirrored
        set(value) {
            render.mirrored = value
        }

    init {
        render.mirrored = mirrored
    }

    /** Filter untuk `GlStreamInterface.addFilter(...)`. Satu [ZinEffects] = satu filter. */
    public val filterRender: BaseFilterRender get() = render

    /** Nama filter yang tersedia (indeks 0 = tanpa filter), untuk [setFilter]. */
    public val filterNames: List<String> = filters.map { it.name }

    /** Warna contoh tiap filter (ARGB, urutan sama dengan [filterNames]), untuk daftar pilihan filter. */
    public val filterSwatches: List<Int> = filters.map { it.swatch.toInt() }

    private val _faceModelProgress = MutableStateFlow<Float?>(null)

    /** Progres unduh model wajah (0..1), `null` bila tidak sedang mengunduh. */
    public val faceModelProgress: StateFlow<Float?> = _faceModelProgress.asStateFlow()

    /** Filter berdasarkan indeks [filterNames] (0 = tanpa filter). LUT diunduh dulu bila perlu. */
    public suspend fun setFilter(index: Int) {
        val chosen = index.coerceIn(0, filters.lastIndex)
        filterIndex = chosen
        val filter = filters[chosen]
        val lut = filter.lutUrl?.let { loadCubeLut(it) }
        // Pilihan lain datang selama LUT diunduh: biarkan yang terbaru.
        if (filterIndex != chosen) return
        render.params = ColorParams(filter.matrix, filter.offset, lut, smoothing)
    }

    /**
     * Gambar [source] dengan filter ke-[index] (salinan baru), untuk thumbnail daftar filter.
     * Filter LUT diunduh dulu bila perlu; LUT yang gagal dimuat = hanya warna dasarnya.
     */
    public suspend fun filterPreview(index: Int, source: Bitmap): Bitmap {
        val filter = filters[index.coerceIn(0, filters.lastIndex)]
        val lut = filter.lutUrl?.let { loadCubeLut(it) }
        return withContext(Dispatchers.Default) {
            val pixels = IntArray(source.width * source.height)
            source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            applyFilter(pixels, filter, lut)
            Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888)
        }
    }

    /** Penghalus kulit 0 (mati)..1. */
    public fun setSmoothing(strength: Float) {
        smoothing = strength.coerceIn(0f, 1f)
        render.params = render.params.copy(smoothing = smoothing)
    }

    /** Unduh & siapkan model wajah lebih awal, agar efek pertama tampil tanpa menunggu. */
    public suspend fun prepareFaceEffects() {
        prepare()
    }

    /**
     * Pasang satu efek wajah (menggantikan efek sebelumnya); `null` = lepas. Gagal (model/gambar
     * tidak bisa dimuat) = exception. Panggilan baru sebaiknya membatalkan panggilan sebelumnya.
     */
    public suspend fun setFaceEffect(effect: FaceEffect?) {
        if (effect == null) {
            detector?.effect = null
            render.detector = null
            render.setOverlayImage(null)
            return
        }
        val active = prepare()
        val image = loadImage(effect.imageUrl) ?: error("Gambar efek tidak bisa dimuat: ${effect.imageUrl}")
        check(!released) { "ZinEffects sudah dilepas" }
        active.effect = effect to image.height.toFloat() / image.width.coerceAtLeast(1)
        render.setOverlayImage(image)
        render.detector = active
    }

    /** Lepas efek wajah & model. [filterRender] tetap bisa dilepas dari stream oleh aplikasi. */
    public fun release() {
        released = true
        render.detector = null
        render.setOverlayImage(null)
        detector?.close()
        detector = null
    }

    private suspend fun prepare(): FaceDetector = prepareLock.withLock {
        check(!released) { "ZinEffects sudah dilepas" }
        detector?.let { return it }
        try {
            _faceModelProgress.value = 0f
            val model = ensureFaceModel(context, faceModelUrl) { _faceModelProgress.value = it }
            withContext(Dispatchers.Default) {
                FaceDetector(context, model, maxFaces, ::onCorners)
            }.also { detector = it }
        } finally {
            _faceModelProgress.value = null
        }
    }

    /**
     * Sudut efek (piksel frame deteksi) -> pecahan frame untuk digambar filter. Frame deteksi yang
     * dibalik (gambar cermin) dikembalikan, sehingga gambar efek ikut ter-mirror seperti wajahnya.
     */
    private fun onCorners(corners: List<FloatArray>, tag: Any?) {
        if (render.detector == null) return
        val (width, height, flipped) = tag as FloatArray
        render.quads = corners.map { points ->
            FloatArray(points.size) { i ->
                if (i % 2 == 1) {
                    points[i] / height
                } else {
                    val x = points[i] / width
                    if (flipped > 0f) 1f - x else x
                }
            }
        }
    }

    private suspend fun loadImage(url: String): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        return (result as? SuccessResult)?.image?.toBitmap()
    }
}
