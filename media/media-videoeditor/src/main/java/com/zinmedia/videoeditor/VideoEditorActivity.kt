package com.zinmedia.videoeditor

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


class VideoEditorActivity : ComponentActivity() {

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        // setupBackPressHandler()
        val imageLoader = createCustomImageLoader(applicationContext)
        setContent {
            CompositionLocalProvider(LocalImageLoader provides imageLoader) {
                MarketplaceTheme {
                    VideoEditorScreen(
                        videoUri = inputUri,
                        recipientLabel = intent.getStringExtra(EXTRA_RECIPIENT_LABEL) ?: DEFAULT_RECIPIENT_LABEL,
                        onExportFinished = { finalUri, keterangan ->
                            val result = Intent().apply {
                                data = finalUri
                                putExtra("keterangan", keterangan)
                                putExtra("media_type", "video")
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

    companion object {
        /** Label penerima di kiri tombol kirim, mis. "Status (Kontak)". Default: "Status". */
        const val EXTRA_RECIPIENT_LABEL = "com.zinmedia.extra.RECIPIENT_LABEL"
        private const val DEFAULT_RECIPIENT_LABEL = "Status"
    }
}
