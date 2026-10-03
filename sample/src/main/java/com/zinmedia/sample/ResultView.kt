package com.zinmedia.sample

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.format.Formatter
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView

/** Menampilkan hasil editor: tiap media (foto/video) beserta resolusi & ukuran file, lalu keterangan. */
class ResultView(context: Context) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        setPadding(0, 48, 0, 0)
    }

    fun showCancelled() {
        removeAllViews()
        addView(label("Dibatalkan"))
    }

    fun show(items: List<Pair<Uri, String>>, caption: String) {
        removeAllViews()
        addView(label("Hasil (${items.size} media)", bold = true))
        items.forEachIndexed { index, (uri, type) ->
            val isVideo = type == "video"
            val info = mediaInfo(uri, isVideo)
            addView(label("${index + 1}. ${if (isVideo) "Video" else "Foto"} · ${info.text}"))
            addView(if (isVideo) videoView(uri, info) else imageView(uri))
        }
        addView(label("Keterangan: ${caption.ifBlank { "-" }}"))
        // Tampilkan dari awal hasil (VideoView bisa menarik scroll ke dirinya).
        post { (parent?.parent as? android.widget.ScrollView)?.smoothScrollTo(0, top) }
    }

    private class MediaInfo(val width: Int, val height: Int, val text: String)

    private fun label(text: String, bold: Boolean = false) = TextView(context).apply {
        this.text = text
        setPadding(0, 24, 0, 12)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun imageView(uri: Uri) = ImageView(context).apply {
        adjustViewBounds = true
        layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        setImageBitmap(decodeSampled(uri, resources.displayMetrics.widthPixels))
    }

    /**
     * Video diputar berulang; ketuk untuk menampilkan kontrol. Ukurannya diatur dari metadata sejak
     * awal, karena VideoView baru menyiapkan video setelah permukaannya punya ukuran.
     */
    private fun videoView(uri: Uri, info: MediaInfo): FrameLayout {
        val ratio = if (info.width > 0) info.height.toFloat() / info.width else 1f
        val frame = AspectFrame(context, ratio).apply { layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT) }
        val video = VideoView(context).apply {
            setVideoURI(uri)
            setMediaController(MediaController(context).also { it.setAnchorView(frame) })
            setOnPreparedListener { player ->
                player.isLooping = true
                start()
            }
        }
        frame.addView(video, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        return frame
    }

    private fun mediaInfo(uri: Uri, isVideo: Boolean): MediaInfo {
        var width = 0
        var height = 0
        val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        val dimension = if (isVideo) {
            MediaMetadataRetriever().run {
                try {
                    setDataSource(context, uri)
                    val w = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val h = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    val durationMs = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                    val (dw, dh) = if (rotation % 180 == 0) w to h else h to w
                    width = dw?.toIntOrNull() ?: 0
                    height = dh?.toIntOrNull() ?: 0
                    "${dw}×${dh} · ${"%.1f".format(durationMs / 1000f)} dtk"
                } catch (e: RuntimeException) {
                    "?"
                } finally {
                    release()
                }
            }
        } else {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            width = options.outWidth
            height = options.outHeight
            "${options.outWidth}×${options.outHeight}"
        }
        val bytes = if (size >= 0) Formatter.formatShortFileSize(context, size) else "?"
        return MediaInfo(width, height, "$dimension · $bytes")
    }

    private fun decodeSampled(uri: Uri, targetWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }
}

/** Tinggi = lebar × [ratio]. */
private class AspectFrame(context: Context, private val ratio: Float) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((width * ratio).toInt(), MeasureSpec.EXACTLY),
        )
    }
}
