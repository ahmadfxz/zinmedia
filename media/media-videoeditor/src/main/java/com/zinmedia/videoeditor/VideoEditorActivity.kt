package com.zinmedia.videoeditor

import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.media3.common.util.UnstableApi
import com.zinmedia.videoeditor.ui.theme.MarketplaceTheme
import com.zinmedia.videoeditor.ui.LocalImageLoader
import com.zinmedia.videoeditor.ui.createCustomImageLoader
import java.io.File
import java.io.FileOutputStream

public class VideoEditorActivity : ComponentActivity() {

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hapus hasil edit lama dari sesi sebelumnya.
        lifecycleScope.launch(Dispatchers.IO) {
            VideoEditorFileProvider.deleteOldOutputs(applicationContext)
        }
        // Editor selalu bertema gelap: ikon status & navigation bar terang di atas latar hitam.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
        )

        // Ambil URI yang dikirim aplikasi utama
        val inputUri = intent?.data
            ?: intent?.getParcelableExtra<Uri>("video_uri")

        if (inputUri == null) {
            finish()
            return
        }
        val imageLoader = createCustomImageLoader(applicationContext)
        setContent {
            CompositionLocalProvider(LocalImageLoader provides imageLoader) {
                MarketplaceTheme {
                    VideoEditorScreen(
                        videoUri = inputUri,
                        recipientLabel = intent.getStringExtra(EXTRA_RECIPIENT_LABEL) ?: getString(R.string.zm_recipient_default),
                        showCaption = intent.getBooleanExtra(EXTRA_SHOW_CAPTION, true),
                        onExportFinished = { finalUri, keterangan ->
                            val result = Intent().apply {
                                data = finalUri
                                putExtra(EXTRA_CAPTION, keterangan)
                                putExtra(EXTRA_MEDIA_TYPE, "video")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            setResult(RESULT_OK, result)
                            finish()
                        },
                        onDismiss = { finish() },
                    )
                }
            }
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) { // PERBAIKAN
                override fun handleOnBackPressed() {
                    handleSystemBack()
                }
            })
    }

    private fun handleSystemBack() {
        finish()
    }

    public companion object {
        /** Hasil: keterangan yang diketik pengguna. */
        public const val EXTRA_CAPTION: String = "keterangan"

        /** Hasil: jenis media, `"image"` atau `"video"`. */
        public const val EXTRA_MEDIA_TYPE: String = "media_type"

        /** Label penerima di kiri tombol kirim, mis. "Status (Kontak)". Default: "Status". */
        public const val EXTRA_RECIPIENT_LABEL: String = "com.zinmedia.extra.RECIPIENT_LABEL"

        /** Input: `false` = tanpa kolom keterangan (hasil [EXTRA_CAPTION] kosong). Default `true`. */
        public const val EXTRA_SHOW_CAPTION: String = "com.zinmedia.extra.SHOW_CAPTION"
    }
}
