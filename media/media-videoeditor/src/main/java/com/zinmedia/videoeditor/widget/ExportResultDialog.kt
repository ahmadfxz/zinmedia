package com.zinmedia.videoeditor.widget

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File


@OptIn(UnstableApi::class)
@Composable
fun ExportResultScreen(
    exportedPath: String,
    onClose: () -> Unit,
    onShare: (Uri) -> Unit
) {
    val context = LocalContext.current
    val previewUri = File(exportedPath).toUri()

    val previewPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(previewUri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { previewPlayer.release() }
    }

   Column(modifier = Modifier.fillMaxWidth()) {
                Text("Hasil video:")
                Spacer(modifier = Modifier.height(8.dp))

                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = true
                            player = previewPlayer
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row {
                    Button(onClick = { onShare(previewUri) }) {
                        Text("Share")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onClose) {
                        Text("Tutup")
                    }
                }
            }
}

