package com.zinmedia.effects

/** Model resmi MediaPipe Face Landmarker (sekitar 3,6 MB). */
public const val DEFAULT_FACE_MODEL_URL: String =
    "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"

/**
 * Satu efek wajah, otomatis pas di kepala siapa pun (tanpa pengaturan ukuran/posisi), ikut menoleh,
 * dan bagian yang tertutup wajah/kepala saat menoleh ikut tertutup. Dua jenis, dari [imageUrl]:
 *
 * - **Gambar** (`.png`, latar transparan) di peta UV wajah standar MediaPipe
 *   (`canonical_face_model.obj`): dibungkuskan ke permukaan wajah, seperti topeng, riasan, cat
 *   wajah, kumis; ikut berekspresi. Contoh: `tools/make_face_effects.py`.
 * - **Model 3D** (`.glb`, low-poly) di ruang kepala standar MediaPipe (cm; +Y atas, +Z depan):
 *   kacamata, topi, mahkota, telinga. Contoh penempatan aset: `tools/fit_face_props.py`.
 *
 * @param imageUrl `file:///android_asset/…`, http/https, `content://`, `file://`, atau path berkas.
 *   Contoh siap pakai & templat: `tools/templat/` (panduan UV, kepala standar untuk Blender).
 */
public data class FaceEffect(
    val name: String,
    val imageUrl: String,
    /** Gambar kecil untuk daftar pilihan efek (mis. di kamera zinmedia); `null` = tanpa gambar. */
    val iconUrl: String? = null,
) {
    /** `true` bila [imageUrl] model 3D (`.glb`). */
    public val isModel: Boolean get() = imageUrl.substringBefore('?').endsWith(".glb", ignoreCase = true)
}
