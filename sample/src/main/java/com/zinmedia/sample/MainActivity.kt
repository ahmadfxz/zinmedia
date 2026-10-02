package com.zinmedia.sample

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.zinmedia.photoeditor.ImageEditorActivity
import com.zinmedia.videoeditor.VideoEditorActivity
import java.io.File

/**
 * Demo editor zinmedia.
 *
 * Buka langsung dengan media contoh lewat adb:
 *   adb shell am start -n com.zinmedia.sample/.MainActivity --es open photo
 *   adb shell am start -n com.zinmedia.sample/.MainActivity --es open video
 */
class MainActivity : ComponentActivity() {

    private lateinit var resultView: TextView

    private val editorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            resultView.text = if (result.resultCode == RESULT_OK) {
                "Hasil: ${data?.data}\nKeterangan: ${data?.getStringExtra("keterangan")}"
            } else {
                "Dibatalkan"
            }
        }

    private val pickPhoto =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { openEditor(ImageEditorActivity::class.java, it) }
        }

    private val pickVideo =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { openEditor(VideoEditorActivity::class.java, it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        resultView = TextView(this).apply { setPadding(0, 48, 0, 0) }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            addView(button("Foto contoh") { openSample("photo") })
            addView(button("Video contoh") { openSample("video") })
            addView(button("Pilih foto") {
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
            addView(button("Pilih video") {
                pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            })
            addView(resultView)
        })

        if (savedInstanceState == null) intent.getStringExtra("open")?.let(::openSample)
    }

    private fun button(label: String, onClick: () -> Unit) =
        Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }

    private fun openSample(type: String) {
        val (asset, editor) = when (type) {
            "video" -> "sample.mp4" to VideoEditorActivity::class.java
            else -> "sample.jpg" to ImageEditorActivity::class.java
        }
        val file = File(cacheDir, asset)
        if (!file.exists()) {
            assets.open(asset).use { input -> file.outputStream().use { input.copyTo(it) } }
        }
        openEditor(editor, Uri.fromFile(file))
    }

    private fun openEditor(editor: Class<*>, uri: Uri) {
        editorLauncher.launch(Intent(this, editor).setData(uri))
    }
}
