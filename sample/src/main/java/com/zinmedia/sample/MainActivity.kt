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
 *   adb shell am start -n com.zinmedia.sample/.MainActivity --es open photo
 *   adb shell am start -n com.zinmedia.sample/.MainActivity --es open video
 *   adb shell am start -n com.zinmedia.sample/.MainActivity --es open mixed
 *   adb shell am start -n com.zinmedia.sample/.MainActivity --es open single
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
        )

        resultView = ResultView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(64, 64, 64, 64)
            addView(button("Kamera") { openSample("camera") })
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

        if (savedInstanceState == null) intent.getStringExtra("open")?.let(::openSample)
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
        if (type == "camera") {
            editorLauncher.launch(Intent(this, CameraActivity::class.java))
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
