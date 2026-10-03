package com.zinmedia.camera

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Satu potongan rekaman (sekali tahan/ketuk) beserta kecepatannya. */
internal data class Segment(
    val file: File,
    val speed: Float,
    /** Durasi rekaman asli (sebelum kecepatan diterapkan). */
    val recordedMs: Long,
) {
    /** Durasi di video hasil. */
    val outputMs: Long get() = (recordedMs / speed).toLong()
}

/**
 * Gabungkan [segments] menjadi satu video dan terapkan kecepatan tiap segmen. Bila cuma satu
 * segmen berkecepatan normal, file aslinya dipakai langsung (tanpa encode ulang).
 */
@OptIn(UnstableApi::class)
internal suspend fun mergeSegments(
    context: Context,
    segments: List<Segment>,
    hasAudio: Boolean,
    outputDir: File,
    onProgress: (Float) -> Unit,
): Uri {
    require(segments.isNotEmpty())
    val single = segments.singleOrNull()
    if (single != null && single.speed == 1f) return Uri.fromFile(single.file)

    val output = File.createTempFile("camera_", ".mp4", outputDir)
    val items = segments.map { segment ->
        EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(segment.file)))
            .setSpeed(constantSpeed(segment.speed))
            // Segmen kamera depan/belakang bisa beda resolusi; samakan ke ukuran video hasil.
            .setEffects(Effects(emptyList(), listOf(Presentation.createForWidthAndHeight(720, 1280, Presentation.LAYOUT_SCALE_TO_FIT))))
            .build()
    }
    val trackTypes = if (hasAudio) setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO) else setOf(C.TRACK_TYPE_VIDEO)
    val composition = Composition.Builder(EditedMediaItemSequence.Builder(trackTypes).addItems(items).build()).build()

    return withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation: CancellableContinuation<Uri> ->
            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        continuation.resume(Uri.fromFile(output))
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        output.delete()
                        continuation.resumeWithException(exportException)
                    }
                })
                .build()
            transformer.start(composition, output.absolutePath)
            continuation.invokeOnCancellation {
                transformer.cancel()
                output.delete()
            }
            val holder = ProgressHolder()
            kotlinx.coroutines.MainScope().launch {
                while (continuation.isActive) {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress / 100f)
                    }
                    delay(100)
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun constantSpeed(speed: Float): SpeedProvider = object : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float = speed
    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
}
