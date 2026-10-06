package com.zinmedia.camera

/**
 * Konten kamera yang ditentukan aplikasi. Isi sekali saat aplikasi mulai
 * (mis. di `Application.onCreate()`).
 *
 * ```kotlin
 * CameraConfig.faceEffects = listOf(
 *     FaceEffect("Topeng", "file:///android_asset/efek/topeng.png", iconUrl = "…/ikon_topeng.png"),
 *     FaceEffect("Helm", "https://cdn.contoh.com/efek/helm.glb", iconUrl = "…/ikon_helm.png"),
 * )
 * ```
 */
public object CameraConfig {

    /** Preset beauty pada panel kamera; dapat diganti dari respons backend sebelum kamera dibuka. */
    @Volatile
    public var beautyPresets: List<BeautyPreset> = com.zinmedia.effects.DefaultBeautyPresets

    /**
     * Efek wajah: gambar peta UV (.png) atau model 3D (.glb), otomatis pas di wajah siapa pun.
     * Kosong = tombol Efek disembunyikan.
     */
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

/** Efek wajah (gambar peta UV atau model 3D .glb); lihat [com.zinmedia.effects.FaceEffect]. */
public typealias FaceEffect = com.zinmedia.effects.FaceEffect

/** Preset beauty dinamis; lihat [com.zinmedia.effects.BeautyPreset]. */
public typealias BeautyPreset = com.zinmedia.effects.BeautyPreset
