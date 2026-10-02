package com.zinmedia.videoeditor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import androidx.core.content.FileProvider
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
        val isDarkMode =
            (resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES

        enableEdgeToEdge(
            navigationBarStyle = if (isDarkMode) {
                SystemBarStyle.dark(
                    scrim = Color.Transparent.toArgb()
                )
            } else {
                SystemBarStyle.light(
                    scrim = Color.Transparent.toArgb(),
                    darkScrim = Color.Transparent.toArgb()
                )
            }
        )

        // Ambil URI yang dikirim aplikasi utama
        val inputUri = intent?.data
            ?: intent?.getParcelableExtra<Uri>("video_uri")

        //  val inputUri = getVideoUriFromAssets(this, "sample.mp4")

        if (inputUri == null) {
            finish()
            return
        }
        // setupBackPressHandler()
        val imageLoader = createCustomImageLoader(applicationContext)
        setContent {
            CompositionLocalProvider(
                LocalImageLoader provides imageLoader
            ) { }
            MarketplaceTheme {
                //  Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                VideoEditorScreen(
                    videoUri = inputUri,
                    onExportFinished = { finalUri, keterangan ->
                        val intent = Intent().apply {
                            data = finalUri
                            putExtra("keterangan", keterangan)
                            putExtra("media_type", "video")
                        }
                        setResult(RESULT_OK, intent)
                        finish()
                    },
                    onDismiss = {
                        finish()
                    },
                    //     modifier = Modifier.padding(innerPadding)
                    //    }
                )
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
}


private fun getVideoUriFromAssets(context: Context, assetName: String): Uri {
    val file = File(context.cacheDir, assetName)

    // Copy hanya jika belum ada
    if (!file.exists()) {
        context.assets.open(assetName).use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
    }

    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )
}

