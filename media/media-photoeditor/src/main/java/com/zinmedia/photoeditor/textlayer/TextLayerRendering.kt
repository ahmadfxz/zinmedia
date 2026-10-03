package com.zinmedia.photoeditor.textlayer

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Ukuran huruf untuk satu font: tinggi kapital & kedalaman ekor huruf (g, y, p) yang sebenarnya.
 * Dipakai agar teks tepat di tengah kotak latarnya.
 */
internal class GlyphMetrics(
    /** Tinggi huruf tertinggi di atas garis dasar. */
    val capHeight: Float,
    /** Kedalaman ekor huruf (g, y, p) di bawah garis dasar. */
    val descender: Float,
    val naturalLineHeight: Float,
)

internal fun glyphMetrics(typeface: Typeface?, textSizePx: Float): GlyphMetrics {
    val paint = Paint().apply {
        this.typeface = typeface
        textSize = textSizePx
    }
    // Rentang visual huruf: puncak huruf tertinggi (kapital & berbatang) dan dasar ekor huruf.
    val tall = Rect().also { paint.getTextBounds("Hbdhkl", 0, 6, it) }
    val tails = Rect().also { paint.getTextBounds("gjpqy", 0, 5, it) }
    val fm = paint.fontMetrics
    return GlyphMetrics(
        capHeight = -tall.top.toFloat(),
        descender = tails.bottom.toFloat().coerceAtLeast(0f),
        naturalLineHeight = fm.descent - fm.ascent,
    )
}

/** Padding kotak latar, sama di atas (dari puncak huruf) dan di bawah (dari dasar ekor huruf). */
internal fun verticalPadding(density: Density): Float = with(density) { TextLayerMetrics.PaddingVertical.toPx() }

/**
 * Gaya teks lapisan teks. Dipakai apa adanya oleh mode teks dan renderer hasil, sehingga
 * susunan barisnya identik. Jarak baris dilebarkan secukupnya agar kotak latar (dengan padding
 * yang sama di atas & bawah) muat dan tetap ada celah antarbaris.
 */
internal fun textLayerStyle(
    layer: TextLayer,
    fontFamily: FontFamily?,
    glyphs: GlyphMetrics,
    density: Density,
): TextStyle = with(density) {
    val boxHeight = glyphs.capHeight + glyphs.descender + 2 * verticalPadding(density)
    val lineHeightPx = max(glyphs.naturalLineHeight, boxHeight + TextLayerMetrics.LineGap.toPx())
    TextStyle(
        color = Color(layer.textColor),
        fontSize = TextLayerMetrics.FontSize,
        fontFamily = fontFamily,
        textAlign = when (layer.align) {
            TextLayerAlign.Left -> TextAlign.Left
            TextLayerAlign.Center -> TextAlign.Center
            TextLayerAlign.Right -> TextAlign.Right
        },
        lineHeight = lineHeightPx.toSp(),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        lineBreak = LineBreak.Simple,
        hyphens = Hyphens.None,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
}

/**
 * Latar teks: satu kotak membulat per baris (selebar isi baris). Tingginya mencakup seluruh rentang
 * huruf font, dari puncak huruf berbatang (b, d, h, k, l) sampai dasar ekor (g, j, p, q, y), dengan
 * padding sama di atas & bawah, sehingga rentang huruf tepat di tengah. Baris kosong tidak diberi kotak.
 */
internal fun textLayerBackground(layout: TextLayoutResult, glyphs: GlyphMetrics, density: Density): Path =
    with(density) {
        val paddingH = TextLayerMetrics.PaddingHorizontal.toPx()
        val paddingV = verticalPadding(density)
        val maxRadius = TextLayerMetrics.CornerRadius.toPx()
        Path().apply {
            for (i in 0 until layout.lineCount) {
                val left = layout.getLineLeft(i)
                val right = layout.getLineRight(i)
                if (right - left <= 0.5f) continue
                val baseline = layout.getLineBaseline(i)
                val rect = ComposeRect(
                    left - paddingH,
                    baseline - glyphs.capHeight - paddingV,
                    right + paddingH,
                    baseline + glyphs.descender + paddingV,
                )
                val radius = minOf(maxRadius, rect.height / 2f)
                addRoundRect(RoundRect(rect, CornerRadius(radius)))
            }
        }
    }

/** Gambar latar (bila ada) lalu teks; dipakai oleh mode teks dan renderer hasil. */
internal fun DrawScope.drawTextLayer(layer: TextLayer, layout: TextLayoutResult, background: Path) {
    layer.backgroundColor?.let { drawPath(background, Color(it)) }
    drawText(layout)
}

/**
 * Render [layer] menjadi gambar transparan yang memuat teks + latarnya, persis seperti di mode
 * teks (mesin teks & gaya yang sama). Ukuran piksel sesuai layar, ditampilkan 1:1 di editor.
 *
 * @param layoutWidthPx lebar kolom teks di mode teks, agar pemenggalan baris sama.
 */
internal fun renderTextLayer(
    layer: TextLayer,
    fontFamily: FontFamily?,
    typeface: Typeface?,
    measurer: TextMeasurer,
    density: Density,
    layoutWidthPx: Int,
): ImageBitmap {
    val glyphs = glyphMetrics(typeface, with(density) { TextLayerMetrics.FontSize.toPx() })
    val style = textLayerStyle(layer, fontFamily, glyphs, density)
    val layout = measurer.measure(
        text = AnnotatedString(layer.text),
        style = style,
        constraints = Constraints.fixedWidth(layoutWidthPx.coerceAtLeast(1)),
        density = density,
        layoutDirection = LayoutDirection.Ltr,
    )
    val background = textLayerBackground(layout, glyphs, density)

    // Batas gambar: kotak latar + area teks tiap baris (agar huruf dekoratif tidak terpotong).
    val paddingH = with(density) { TextLayerMetrics.PaddingHorizontal.toPx() }
    var bounds = background.getBounds()
    for (i in 0 until layout.lineCount) {
        val line = ComposeRect(
            layout.getLineLeft(i) - paddingH, layout.getLineTop(i),
            layout.getLineRight(i) + paddingH, layout.getLineBottom(i),
        )
        bounds = if (bounds.isEmpty) line else bounds.union(line)
    }
    val left = floor(bounds.left)
    val top = floor(bounds.top)
    val width = ceil(bounds.right - left).toInt().coerceAtLeast(1)
    val height = ceil(bounds.bottom - top).toInt().coerceAtLeast(1)

    val image = ImageBitmap(width, height)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(width.toFloat(), height.toFloat())) {
        translate(-left, -top) { drawTextLayer(layer, layout, background) }
    }
    return image
}

private fun ComposeRect.union(other: ComposeRect): ComposeRect =
    ComposeRect(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))
