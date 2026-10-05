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
    public const val DEFAULT_FACE_MODEL_URL: String = com.zinmedia.effects.DEFAULT_FACE_MODEL_URL
}

// Tipe efek wajah berada di media-effects (dipakai bersama kamera & efek siaran).

/** @see com.zinmedia.effects.FaceEffect */
public typealias FaceEffect = com.zinmedia.effects.FaceEffect

/** @see com.zinmedia.effects.FaceSide */
public typealias FaceSide = com.zinmedia.effects.FaceSide

/** @see com.zinmedia.effects.FaceAnchor */
public typealias FaceAnchor = com.zinmedia.effects.FaceAnchor
