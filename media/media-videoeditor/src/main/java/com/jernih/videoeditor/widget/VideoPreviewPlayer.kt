package com.jernih.videoeditor.widget

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

//
//@OptIn(UnstableApi::class)
//@Composable
//fun VideoPreviewPlayer(player: ExoPlayer, modifier: Modifier = Modifier) {
//
//        AndroidView(
//            factory = { ctx ->
//                PlayerView(ctx).apply {
//                    useController = false
//                    this.player = player
//                   // this.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
//                    // <-- DISABLE controller
////                layoutParams = FrameLayout.LayoutParams(
////                    ViewGroup.LayoutParams.MATCH_PARENT,
////                    ViewGroup.LayoutParams.MATCH_PARENT
////                )
//                }
//            },
//            modifier = modifier
//                .clip(RoundedCornerShape(16.dp))
//                .clickable {
//                    player.playWhenReady = !player.playWhenReady
//                }
//        )
//
//}

@OptIn(UnstableApi::class)
@Composable
fun VideoPreviewPlayer(
    player: ExoPlayer,
    modifier: Modifier = Modifier
) {
    // RASIO 9:16


    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                this.player = player
              //  this.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
            }
        },
        modifier = modifier
           // .clip(RoundedCornerShape(16.dp))
            .clickable {
                player.playWhenReady = !player.playWhenReady
            }
    )
}
