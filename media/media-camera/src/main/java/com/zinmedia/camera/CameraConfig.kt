package com.zinmedia.camera

/**
 * Konten kamera yang ditentukan aplikasi. Isi sekali saat aplikasi mulai
 * (mis. di `Application.onCreate()`).
 *
 * ```kotlin
 * CameraConfig.faceEffects = listOf(
 *     FaceEffect("Kacamata", "https://cdn.contoh.com/efek/kacamata.png", FaceAnchor.Eyes),
 * )
 * ```
 */
public object CameraConfig {

    /** Efek wajah (gambar yang menempel di wajah). Kosong = tombol Efek disembunyikan. */
    @Volatile
    public var faceEffects: List<FaceEffect> = emptyList()

    /**
     * Model pendeteksi titik wajah (MediaPipe Face Landmarker). Diunduh saat efek pertama kali
     * dipakai lalu disimpan di perangkat. Bisa diganti ke URL milik aplikasi.
     */
    @Volatile
    public var faceModelUrl: String = DEFAULT_FACE_MODEL_URL

    /**
     * Jumlah wajah maksimal yang diberi efek (1..3). Lebih banyak = lebih berat; 1 paling aman
     * untuk HP murah.
     */
    @Volatile
    public var maxFaces: Int = 1

    /** Model resmi MediaPipe (sekitar 3,6 MB). */
    public const val DEFAULT_FACE_MODEL_URL: String =
        "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
}

/**
 * Satu efek wajah: gambar PNG (sebaiknya latar transparan) yang ditempel di [anchor] dan ikut
 * bergerak, membesar, dan miring mengikuti wajah.
 *
 * @param imageUrl URL atau URI apa pun yang bisa dimuat Coil (termasuk `file:///android_asset/…`).
 * @param scale pengali ukuran terhadap ukuran bawaan titik tempel (1 = bawaan).
 * @param offsetY geser ke atas (+) / bawah (−) dalam satuan tinggi gambar.
 * @param side untuk [FaceAnchor.Ear], [FaceAnchor.Cheek], dan [FaceAnchor.Eye]: kiri, kanan, atau
 *   keduanya (sisi **orangnya**). Diabaikan untuk titik tempel lain.
 */
public data class FaceEffect(
    val name: String,
    val imageUrl: String,
    val anchor: FaceAnchor,
    val scale: Float = 1f,
    val offsetY: Float = 0f,
    val side: FaceSide = FaceSide.Both,
)

/** Sisi efek untuk telinga, pipi, dan mata (sisi orangnya, bukan sisi layar). */
public enum class FaceSide {
    Left,
    Right,
    Both,
}

/**
 * Titik tempel efek wajah. Untuk [Ear], [Cheek], [Eye] pilih sisi lewat [FaceEffect.side]
 * (sisi **orangnya**, tetap benar walau kamera depan di-mirror). Efek di sisi yang membelakangi
 * kamera saat kepala menoleh disembunyikan.
 */
public enum class FaceAnchor {
    /** Di tengah kedua mata, selebar wajah (kacamata, topeng mata). */
    Eyes,

    /** Di atas dahi, sedikit lebih lebar dari wajah (topi, mahkota, telinga hewan). */
    Head,

    /** Di tengah dahi (bindi, bintang, stiker kecil). */
    Forehead,

    /** Di ujung hidung (hidung badut, hidung hewan). */
    Nose,

    /** Di antara hidung dan bibir atas (kumis). */
    Mouth,

    /** Di bawah bibir sampai dagu (janggut). */
    Chin,

    /** Menutupi seluruh wajah (topeng). */
    Face,

    /** Telinga, sesuai [FaceEffect.side] (bunga, anting). */
    Ear,

    /** Pipi, sesuai [FaceEffect.side] (rona, hati, stiker). */
    Cheek,

    /** Masing-masing mata, sesuai [FaceEffect.side] (hati di mata, monokel). Untuk kacamata pakai [Eyes]. */
    Eye,
}
