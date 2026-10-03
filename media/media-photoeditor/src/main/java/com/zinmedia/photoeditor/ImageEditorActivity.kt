package com.zinmedia.photoeditor

import kotlinx.coroutines.Dispatchers
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.lifecycleScope
import com.zinmedia.photoeditor.ui.DiscardChangesDialog
import com.zinmedia.photoeditor.ui.EditorCaptionBar
import com.zinmedia.photoeditor.ui.theme.MarketplaceTheme
import kotlinx.coroutines.launch

/**
 * Editor satu foto. Kirim URI gambar lewat `intent.data`; hasil dikembalikan lewat `data`
 * beserta extra `keterangan` dan `media_type` = "image".
 */
public class ImageEditorActivity : ComponentActivity() {

    private lateinit var state: PhotoEditorState
    private var caption by mutableStateOf("")
    private var showDiscardDialog by mutableStateOf(false)
    private var sending by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hapus hasil edit lama dari sesi sebelumnya.
        lifecycleScope.launch(Dispatchers.IO) {
            PhotoEditorFileProvider.deleteOldOutputs(applicationContext)
        }

        val imageUri = intent.data
        if (imageUri == null) {
            Log.e(TAG, "ImageEditorActivity dibuka tanpa URI gambar")
            finish()
            return
        }
        state = PhotoEditorState(
            context = this,
            sourceUri = imageUri,
            pinchTextScalable = intent.getBooleanExtra(PINCH_TEXT_SCALABLE_INTENT_KEY, true),
        )
        val recipientLabel = intent.getStringExtra(EXTRA_RECIPIENT_LABEL) ?: getString(R.string.zm_recipient_default)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })

        // Editor selalu bertema gelap: ikon status & navigation bar terang di atas latar hitam.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
        )
        setContent {
            MarketplaceTheme {
                LaunchedEffect(state.loadFailed) { if (state.loadFailed) finish() }

                PhotoEditorContent(
                    state = state,
                    onClose = { onBackPressedDispatcher.onBackPressed() },
                    bottomContent = {
                        EditorCaptionBar(
                            caption = caption,
                            onCaptionChange = { caption = it },
                            recipientLabel = recipientLabel,
                            sendEnabled = state.isLoaded && !sending,
                            onSend = ::publish,
                        )
                    },
                )

                if (showDiscardDialog) {
                    DiscardChangesDialog(
                        onDiscard = { finish() },
                        onDismiss = { showDiscardDialog = false },
                    )
                }
            }
        }
    }

    private fun handleBack() {
        when {
            state.handleBack() -> Unit
            state.hasEdits() || caption.isNotBlank() -> showDiscardDialog = true
            else -> finish()
        }
    }

    private fun publish() {
        sending = true
        state.isLoading = true
        lifecycleScope.launch {
            try {
                val uri = state.exportToUri()
                val result = Intent().apply {
                    data = uri
                    putExtra(EXTRA_CAPTION, caption)
                    putExtra(EXTRA_MEDIA_TYPE, "image")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                setResult(RESULT_OK, result)
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menyimpan gambar", e)
                state.snackbarMessage = e.message
            } finally {
                sending = false
                state.isLoading = false
            }
        }
    }

    public companion object {
        /** Hasil: keterangan yang diketik pengguna. */
        public const val EXTRA_CAPTION: String = "keterangan"

        /** Hasil: jenis media, `"image"` atau `"video"`. */
        public const val EXTRA_MEDIA_TYPE: String = "media_type"

        public const val PINCH_TEXT_SCALABLE_INTENT_KEY: String = "PINCH_TEXT_SCALABLE"

        /** Label penerima di kiri tombol kirim, mis. "Status (Kontak)". Default: "Status". */
        public const val EXTRA_RECIPIENT_LABEL: String = "com.zinmedia.extra.RECIPIENT_LABEL"
        private const val TAG = "ImageEditorActivity"
    }
}
