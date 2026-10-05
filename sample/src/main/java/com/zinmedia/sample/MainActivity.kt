package com.zinmedia.sample

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.zinmedia.camera.CameraActivity
import com.zinmedia.camera.CameraConfig
import com.zinmedia.camera.FaceEffect
import com.zinmedia.composer.AllowedMedia
import com.zinmedia.composer.MediaComposer
import com.zinmedia.composer.MediaComposerActivity
import com.zinmedia.photoeditor.ImageEditorActivity
import com.zinmedia.videoeditor.VideoEditorActivity
import java.io.File

/**
 * Demo editor zinmedia.
 *
 * Buka langsung dengan media contoh lewat adb:
 *   adb shell am start -n com.zinmedia.sample/.MainActivity --es open camera
 *   adb shell am start -n com.zinmedia.sample/.MainActivity --es open live   # RTMP lokal, lihat di bawah
 *   adb shell am start -n com.zinmedia.sample/.MainActivity --es open effects   # efek di RootEncoder milik aplikasi
 *   adb shell am start -n com.zinmedia.sample/.MainActivity --es open photo
 *   adb shell am start -n com.zinmedia.sample/.MainActivity --es open video
 *   adb shell am start -n com.zinmedia.sample/.MainActivity --es open mixed
 *   adb shell am start -n com.zinmedia.sample/.MainActivity --es open single
 *   adb shell am start -n com.zinmedia.sample/.MainActivity --es open nocaption
 *   adb shell am start -n com.zinmedia.sample/.MainActivity --es open videoonly   # atau: photoonly
 *   adb shell am start -n com.zinmedia.sample/.MainActivity --es path /sdcard/Android/data/com.zinmedia.sample/files/a.jpg
 *   adb shell am start -n com.zinmedia.sample/.MainActivity --es open video_rotated
 */
class MainActivity : ComponentActivity() {

    private lateinit var resultView: ResultView

    private val editorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode != RESULT_OK || data == null) {
                resultView.showCancelled()
                return@registerForActivityResult
            }
            val uris = data.clipData?.let { clip -> (0 until clip.itemCount).map { clip.getItemAt(it).uri } }
                ?: listOfNotNull(data.data)
            // Composer memberi jenis per item; editor tunggal memberi satu jenis.
            val types = data.getStringArrayListExtra(MediaComposerActivity.EXTRA_RESULT_TYPES)
                ?: List(uris.size) { data.getStringExtra(MediaComposerActivity.EXTRA_MEDIA_TYPE) ?: "image" }
            resultView.show(
                items = uris.zip(types),
                caption = data.getStringExtra(MediaComposerActivity.EXTRA_CAPTION).orEmpty(),
            )
        }

    private val pickPhoto =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { openEditor(ImageEditorActivity::class.java, it) }
        }

    private val pickVideo =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { openEditor(VideoEditorActivity::class.java, it) }
        }

    private val pickMany =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MediaComposerActivity.MAX_ITEMS)) { uris ->
            if (uris.isNotEmpty()) openComposer(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Konten editor ditentukan aplikasi. Di aplikasi sungguhan, panggil di Application.onCreate().
        MediaComposer.configure(
            stickers = listOf("heart", "star", "wow").map { "file:///android_asset/stickers/$it.png" },
            // Warna utama milik aplikasi (tombol kirim, dialog, dll).
            accentColor = 0xFF1E88E5.toInt(),
        )
        // Efek wajah kamera: gambar peta UV (.png) dan model 3D (.glb, dengan ikon), otomatis pas di
        // wajah siapa pun. Aset contoh: assets/face_mesh & assets/face_3d (lihat CREDITS.txt).
        val paint = listOf("Cat Wajah", "Topeng", "Kucing", "Kumis", "Pipi Merah", "Badut", "Tengkorak", "Bintang")
        val models = listOf("Kacamata Sport", "Kacamata Hitam", "Helm Pilot", "Helm Scifi", "Topi Nelayan", "Masker Gas")
        fun file(name: String) = name.lowercase().replace(' ', '_')
        CameraConfig.faceEffects = paint.map { FaceEffect(it, "file:///android_asset/face_mesh/${file(it)}.png") } +
            models.map {
                FaceEffect(it, "file:///android_asset/face_3d/${file(it)}.glb", iconUrl = "file:///android_asset/face_3d/${file(it)}_ikon.png")
            }

        resultView = ResultView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(64, 64, 64, 64)
            addView(button("Kamera") { openSample("camera") })
            addView(button("Live (RTMP lokal)") { openSample("live") })
            addView(button("Efek siaran (RootEncoder aplikasi)") { openSample("effects") })
            addView(button("Foto contoh") { openSample("photo") })
            addView(button("Video contoh") { openSample("video") })
            addView(button("Gabungan contoh (2 foto + video)") { openSample("mixed") })
            addView(button("Pilih beberapa media") {
                pickMany.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            })
            addView(button("Pilih foto") {
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
            addView(button("Pilih video") {
                pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            })
            addView(resultView)
        }
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(content)
        })

        if (savedInstanceState == null) {
            intent.getStringExtra("open")?.let(::openSample)
            // Uji media sendiri: file di folder app (adb push ke /sdcard/Android/data/<pkg>/files/).
            intent.getStringExtra("path")?.let { path ->
                editorLauncher.launch(MediaComposerActivity.intent(this, listOf(Uri.fromFile(File(path)))))
            }
        }
    }

    private fun button(label: String, onClick: () -> Unit) =
        Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }

    private fun sampleUri(asset: String): Uri {
        val file = File(cacheDir, asset)
        if (!file.exists()) {
            assets.open(asset).use { input -> file.outputStream().use { input.copyTo(it) } }
        }
        return Uri.fromFile(file)
    }

    private fun openSample(type: String) {
        if (type == "live" || type == "effects") {
            // Server RTMP lokal di komputer (mis. MediaMTX), lewat: adb reverse tcp:1935 tcp:1935
            val url = intent.getStringExtra("url") ?: "rtmp://127.0.0.1:1935/live/test"
            val screen = if (type == "live") LiveDemoActivity::class.java else EffectsDemoActivity::class.java
            startActivity(Intent(this, screen).putExtra("url", url))
            return
        }
        if (type == "camera") {
            editorLauncher.launch(Intent(this, CameraActivity::class.java))
            return
        }
        if (type == "videoonly" || type == "photoonly") {
            // Kamera & editor hanya untuk satu jenis media.
            val allowed = if (type == "videoonly") AllowedMedia.Video else AllowedMedia.Image
            editorLauncher.launch(CameraActivity.intent(this, allowedMedia = allowed))
            return
        }
        if (type == "mixed") {
            openComposer(listOf(sampleUri("sample.jpg"), sampleUri("sample.mp4"), sampleUri("sample2.jpg")))
            return
        }
        if (type == "single") {
            // Mode satu media: tanpa deretan thumbnail & tombol tambah.
            editorLauncher.launch(MediaComposerActivity.intent(this, listOf(sampleUri("sample.jpg")), maxItems = 1))
            return
        }
        if (type == "nocaption") {
            // Tanpa kolom keterangan.
            editorLauncher.launch(MediaComposerActivity.intent(this, listOf(sampleUri("sample.jpg")), showCaption = false))
            return
        }
        if (type == "photos") {
            openComposer(listOf("sample.jpg", "sample2.jpg", "sample.jpg", "sample2.jpg", "sample.mp4").map(::sampleUri))
            return
        }
        val (asset, editor) = when (type) {
            "video" -> "sample.mp4" to VideoEditorActivity::class.java
            // Seperti video kamera HP: disimpan landscape + metadata rotasi 90° (tampil portrait).
            "video_rotated" -> "sample_rotated.mp4" to VideoEditorActivity::class.java
            else -> "sample.jpg" to ImageEditorActivity::class.java
        }
        val file = File(cacheDir, asset)
        if (!file.exists()) {
            assets.open(asset).use { input -> file.outputStream().use { input.copyTo(it) } }
        }
        openEditor(editor, Uri.fromFile(file))
    }

    private fun openComposer(uris: List<Uri>) {
        editorLauncher.launch(
            Intent(this, MediaComposerActivity::class.java)
                .putParcelableArrayListExtra(MediaComposerActivity.EXTRA_MEDIA_URIS, ArrayList(uris))
        )
    }

    private fun openEditor(editor: Class<*>, uri: Uri) {
        editorLauncher.launch(Intent(this, editor).setData(uri))
    }
}
