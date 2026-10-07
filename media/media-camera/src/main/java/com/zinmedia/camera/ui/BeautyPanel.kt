package com.zinmedia.camera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zinmedia.camera.CameraState
import com.zinmedia.effects.BeautyFeature
import com.zinmedia.effects.BeautyGroup
import com.zinmedia.effects.BeautyThumbnails
import com.zinmedia.effects.LipFinish
import com.zinmedia.videoeditor.VideoEditorConfig
import kotlin.math.roundToInt

private val Accent: Color get() = Color(VideoEditorConfig.accentColor)
private val SheetColor = Color(0xD9101010)

/** Warna lipstik siap pilih (ARGB). */
private val LipColors = listOf(
    0xFFC2185B, 0xFFE53935, 0xFFAD1457, 0xFFD81B60, 0xFFF06292, 0xFFBF6F5A, 0xFF8E2C48, 0xFF6A1B9A,
).map { it.toInt() }

/** Tab panel: preset, lalu grup fitur dari katalog [BeautyFeature]. */
private sealed interface BeautyTab {
    data object Presets : BeautyTab
    data class Group(val group: BeautyGroup) : BeautyTab
}

/**
 * Panel Percantik ala TikTok: slider di atas (angka di atas kenop), tab kategori, deretan tombol
 * bulat berikon dengan nama, dan Reset. Fitur & grup langsung dari katalog [BeautyFeature], jadi
 * fitur baru di library otomatis tampil.
 */
@Composable
internal fun BeautyPanel(state: CameraState) {
    var tab by remember { mutableStateOf<BeautyTab>(BeautyTab.Presets) }
    var selected by remember { mutableStateOf<BeautyFeature?>(null) }
    val feature = selected?.takeIf { tab is BeautyTab.Group && (tab as BeautyTab.Group).group == it.group }

    Column(Modifier.fillMaxWidth()) {
        // Slider melayang di atas panel (hanya saat sebuah fitur dipilih).
        if (feature != null) {
            if (feature == BeautyFeature.Lipstick) {
                LipFinishRow(selected = state.beauty.lipFinish, onSelect = state::setLipFinish)
                Spacer(Modifier.height(10.dp))
                LipColorRow(selected = state.beauty.lipColor, onSelect = state::setLipColor)
                Spacer(Modifier.height(10.dp))
            }
            ValueSlider(
                value = state.beauty[feature],
                bipolar = feature.bipolar,
                onValueChange = { state.setBeauty(feature, it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(SheetColor)
                .padding(top = 12.dp, bottom = 14.dp),
        ) {
            TabRow(tab, state) { next ->
                tab = next
                selected = (next as? BeautyTab.Group)?.let { g -> BeautyFeature.entries.first { it.group == g.group } }
            }
            Spacer(Modifier.height(14.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    ItemButton("Reset", selected = false, active = false, onClick = {
                        state.resetBeauty()
                    }) { color -> drawReset(color) }
                }
                item { Box(Modifier.padding(top = 14.dp).width(1.dp).height(28.dp).background(Color.White.copy(alpha = 0.2f))) }
                when (val current = tab) {
                    BeautyTab.Presets -> {
                        items(state.beautyPresets.indices.toList()) { index ->
                            val preset = state.beautyPresets[index]
                            val sizePx = with(LocalDensity.current) { 52.dp.roundToPx() }
                            // Tanpa iconUrl: ilustrasi otomatis dari nilai preset.
                            val thumbnail = remember(preset.params, sizePx) {
                                if (preset.iconUrl == null) BeautyThumbnails.draw(preset.params, sizePx).asImageBitmap() else null
                            }
                            ItemButton(
                                preset.name, selected = state.beautyPresetIndex == index, active = false,
                                iconUrl = preset.iconUrl, image = thumbnail, onClick = { state.selectBeautyPreset(index) },
                            ) { color -> drawSparkle(color) }
                        }
                    }
                    is BeautyTab.Group -> {
                        items(BeautyFeature.entries.filter { it.group == current.group }) { item ->
                            ItemButton(
                                item.label, selected = item == feature, active = state.beauty[item] != 0f,
                                onClick = { selected = item },
                            ) { color -> drawFeature(item, color) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabRow(tab: BeautyTab, state: CameraState, onSelect: (BeautyTab) -> Unit) {
    val tabs = listOf<BeautyTab>(BeautyTab.Presets) + BeautyGroup.entries.map { BeautyTab.Group(it) }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(tabs) { item ->
            val isSelected = item == tab
            val label = when (item) {
                BeautyTab.Presets -> "Preset"
                is BeautyTab.Group -> item.group.label
            }
            val active = item is BeautyTab.Group && BeautyFeature.entries.any { it.group == item.group && state.beauty[it] != 0f }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(role = Role.Tab) { onSelect(item) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                    if (active) {
                        Spacer(Modifier.width(3.dp))
                        Box(Modifier.size(4.dp).clip(CircleShape).background(Accent))
                    }
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .size(width = 16.dp, height = 2.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSelected) Color.White else Color.Transparent),
                )
            }
        }
    }
}

/** Tombol bulat berikon dengan nama di bawahnya; titik = fitur aktif, cincin = dipilih. */
@Composable
private fun ItemButton(
    label: String,
    selected: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    iconUrl: String? = null,
    image: ImageBitmap? = null,
    icon: DrawScope.(Color) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(62.dp)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (selected) 0.2f else 0.1f))
                    .border(if (selected) 2.dp else 0.dp, if (selected) Accent else Color.Transparent, CircleShape),
            ) {
                if (iconUrl != null) {
                    coil3.compose.AsyncImage(iconUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else if (image != null) {
                    Image(image, null, modifier = Modifier.fillMaxSize())
                } else {
                    val tint = if (selected) Accent else Color.White
                    Canvas(Modifier.size(26.dp)) { icon(tint) }
                }
            }
            if (active) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-2).dp, y = 2.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Accent)
                        .border(1.5.dp, SheetColor, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LipFinishRow(selected: LipFinish, onSelect: (LipFinish) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth(),
    ) {
        LipFinish.entries.forEach { finish ->
            val isSelected = finish == selected
            Text(
                finish.label,
                color = if (isSelected) Color.Black else Color.White,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White else Color.Black.copy(alpha = 0.45f))
                    .clickable(role = Role.Button) { onSelect(finish) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun LipColorRow(selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(LipColors) { color ->
            val isSelected = color == selected
            Box(
                Modifier
                    .size(30.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(if (isSelected) 2.5.dp else 1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.4f), CircleShape)
                    .clickable(role = Role.Button) { onSelect(color) },
            )
        }
    }
}

/**
 * Slider ala TikTok: garis tipis, kenop putih, angka di atas kenop. [bipolar]: −100..100, terisi
 * dari tengah (dengan penanda tengah). Geser atau ketuk di mana saja.
 */
@Composable
private fun ValueSlider(value: Float, bipolar: Boolean, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val onChange by rememberUpdatedState(onValueChange)
    val density = LocalDensity.current
    val min = if (bipolar) -1f else 0f
    BoxWithConstraints(
        modifier
            .height(46.dp)
            .pointerInput(bipolar) {
                val knob = with(density) { KnobSize.toPx() }
                fun valueAt(x: Float): Float {
                    val t = ((x - knob / 2) / (size.width - knob)).coerceIn(0f, 1f)
                    val v = min + t * (1f - min)
                    // Lengket di tengah untuk fitur dua arah.
                    return if (bipolar && kotlin.math.abs(v) < 0.04f) 0f else v
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onChange(valueAt(down.position.x))
                    drag(down.id) { change ->
                        change.consume()
                        onChange(valueAt(change.position.x))
                    }
                }
            },
    ) {
        val travel = maxWidth - KnobSize
        val t = (value - min) / (1f - min)
        val knobX = travel * t
        val zeroX = travel * ((0f - min) / (1f - min))
        Text(
            "${(value * 100).roundToInt()}",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 6f),
            ),
            modifier = Modifier
                .width(KnobSize + 24.dp)
                .offset(x = knobX - 12.dp),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = (KnobSize - TrackHeight) / 2 + 2.dp)
                .fillMaxWidth()
                .height(TrackHeight)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.35f)),
        )
        // Bagian terisi: dari 0 (kiri, atau tengah untuk dua arah) sampai kenop.
        val fillStart = minOf(zeroX, knobX) + KnobSize / 2
        val fillWidth = if (!bipolar) knobX else if (knobX > zeroX) knobX - zeroX else zeroX - knobX
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = (KnobSize - TrackHeight) / 2 + 2.dp)
                .offset(x = if (bipolar) fillStart else 0.dp)
                .width(if (bipolar) fillWidth else fillWidth + KnobSize / 2)
                .height(TrackHeight)
                .clip(RoundedCornerShape(50))
                .background(Color.White),
        )
        if (bipolar) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = (KnobSize - 10.dp) / 2 + 2.dp)
                    .offset(x = zeroX + KnobSize / 2 - 1.dp)
                    .size(width = 2.dp, height = 10.dp)
                    .background(Color.White.copy(alpha = 0.8f)),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 2.dp)
                .offset(x = knobX)
                .size(KnobSize)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

private val KnobSize = 20.dp
private val TrackHeight = 3.dp

// ---------------- ikon garis sederhana (26dp) ----------------

private fun DrawScope.line(color: Color) = Stroke(width = size.minDimension * 0.075f, cap = StrokeCap.Round)

private fun DrawScope.at(x: Float, y: Float) = Offset(size.width * x, size.height * y)

private fun DrawScope.drawReset(color: Color) {
    drawArc(color, 40f, 290f, false, topLeft = at(0.15f, 0.15f), size = Size(size.width * 0.7f, size.height * 0.7f), style = line(color))
    val tip = at(0.78f, 0.28f)
    drawLine(color, tip, at(0.86f, 0.1f), strokeWidth = line(color).width, cap = StrokeCap.Round)
    drawLine(color, tip, at(0.62f, 0.2f), strokeWidth = line(color).width, cap = StrokeCap.Round)
}

private fun DrawScope.drawSparkle(color: Color) {
    val p = Path().apply {
        moveTo(size.width * 0.5f, size.height * 0.08f)
        quadraticTo(size.width * 0.55f, size.height * 0.45f, size.width * 0.92f, size.height * 0.5f)
        quadraticTo(size.width * 0.55f, size.height * 0.55f, size.width * 0.5f, size.height * 0.92f)
        quadraticTo(size.width * 0.45f, size.height * 0.55f, size.width * 0.08f, size.height * 0.5f)
        quadraticTo(size.width * 0.45f, size.height * 0.45f, size.width * 0.5f, size.height * 0.08f)
    }
    drawPath(p, color, style = line(color))
}

/** Kontur wajah (oval meruncing ke dagu). */
private fun DrawScope.faceOutline(color: Color, narrow: Float = 0f) {
    val l = 0.2f + narrow
    val r = 0.8f - narrow
    val p = Path().apply {
        moveTo(size.width * 0.5f, size.height * 0.06f)
        cubicTo(size.width * (r + 0.08f), size.height * 0.06f, size.width * (r + 0.04f), size.height * 0.5f, size.width * r, size.height * 0.62f)
        quadraticTo(size.width * 0.62f, size.height * 0.94f, size.width * 0.5f, size.height * 0.94f)
        quadraticTo(size.width * 0.38f, size.height * 0.94f, size.width * l, size.height * 0.62f)
        cubicTo(size.width * (l - 0.04f), size.height * 0.5f, size.width * (l - 0.08f), size.height * 0.06f, size.width * 0.5f, size.height * 0.06f)
    }
    drawPath(p, color, style = line(color))
}

private fun DrawScope.arrow(color: Color, from: Offset, to: Offset) {
    val w = line(color).width
    drawLine(color, from, to, strokeWidth = w, cap = StrokeCap.Round)
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
    val ux = dx / len
    val uy = dy / len
    val head = size.minDimension * 0.14f
    drawLine(color, to, Offset(to.x - (ux - uy) * head * 0.7f, to.y - (uy + ux) * head * 0.7f), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(color, to, Offset(to.x - (ux + uy) * head * 0.7f, to.y - (uy - ux) * head * 0.7f), strokeWidth = w, cap = StrokeCap.Round)
}

private fun DrawScope.eye(color: Color, cx: Float, cy: Float, w: Float) {
    val p = Path().apply {
        moveTo(size.width * (cx - w), size.height * cy)
        quadraticTo(size.width * cx, size.height * (cy - w * 0.9f), size.width * (cx + w), size.height * cy)
        quadraticTo(size.width * cx, size.height * (cy + w * 0.9f), size.width * (cx - w), size.height * cy)
    }
    drawPath(p, color, style = line(color))
    drawCircle(color, radius = size.minDimension * w * 0.28f, center = at(cx, cy))
}

private fun DrawScope.lips(color: Color, w: Float = 0.34f, fill: Boolean = false) {
    val p = Path().apply {
        moveTo(size.width * (0.5f - w), size.height * 0.52f)
        quadraticTo(size.width * 0.38f, size.height * 0.3f, size.width * 0.5f, size.height * 0.42f)
        quadraticTo(size.width * 0.62f, size.height * 0.3f, size.width * (0.5f + w), size.height * 0.52f)
        quadraticTo(size.width * 0.5f, size.height * 0.82f, size.width * (0.5f - w), size.height * 0.52f)
    }
    if (fill) drawPath(p, color) else drawPath(p, color, style = line(color))
}

private fun DrawScope.nose(color: Color) {
    val p = Path().apply {
        moveTo(size.width * 0.5f, size.height * 0.1f)
        lineTo(size.width * 0.44f, size.height * 0.62f)
        quadraticTo(size.width * 0.3f, size.height * 0.72f, size.width * 0.38f, size.height * 0.82f)
        quadraticTo(size.width * 0.5f, size.height * 0.86f, size.width * 0.62f, size.height * 0.82f)
        quadraticTo(size.width * 0.7f, size.height * 0.72f, size.width * 0.56f, size.height * 0.62f)
    }
    drawPath(p, color, style = line(color))
}

private fun DrawScope.drawFeature(feature: BeautyFeature, c: Color) {
    val w = line(c).width
    when (feature) {
        BeautyFeature.Smooth -> {
            drawSparkle(c)
        }
        BeautyFeature.Brighten -> {
            drawCircle(c, radius = size.minDimension * 0.2f, center = center, style = line(c))
            for (k in 0 until 8) {
                val a = Math.toRadians(k * 45.0)
                val r0 = size.minDimension * 0.32f
                val r1 = size.minDimension * 0.44f
                drawLine(
                    c, Offset(center.x + r0 * kotlin.math.cos(a).toFloat(), center.y + r0 * kotlin.math.sin(a).toFloat()),
                    Offset(center.x + r1 * kotlin.math.cos(a).toFloat(), center.y + r1 * kotlin.math.sin(a).toFloat()),
                    strokeWidth = w, cap = StrokeCap.Round,
                )
            }
        }
        BeautyFeature.Rosy, BeautyFeature.Blush -> {
            faceOutline(c)
            drawCircle(c.copy(alpha = 0.7f), radius = size.minDimension * 0.09f, center = at(0.33f, 0.58f))
            drawCircle(c.copy(alpha = 0.7f), radius = size.minDimension * 0.09f, center = at(0.67f, 0.58f))
        }
        BeautyFeature.DarkCircles -> {
            eye(c, 0.5f, 0.4f, 0.32f)
            drawArc(c, 20f, 140f, false, topLeft = at(0.2f, 0.42f), size = Size(size.width * 0.6f, size.height * 0.3f), style = line(c))
        }
        BeautyFeature.SmileLines -> {
            faceOutline(c)
            drawArc(c, 100f, 70f, false, topLeft = at(0.28f, 0.38f), size = Size(size.width * 0.3f, size.height * 0.36f), style = line(c))
            drawArc(c, 10f, 70f, false, topLeft = at(0.42f, 0.38f), size = Size(size.width * 0.3f, size.height * 0.36f), style = line(c))
        }
        BeautyFeature.Sharpen -> {
            val p = Path().apply {
                moveTo(size.width * 0.5f, size.height * 0.1f)
                lineTo(size.width * 0.88f, size.height * 0.5f)
                lineTo(size.width * 0.5f, size.height * 0.9f)
                lineTo(size.width * 0.12f, size.height * 0.5f)
                close()
            }
            drawPath(p, c, style = line(c))
            drawLine(c, at(0.5f, 0.1f), at(0.5f, 0.9f), strokeWidth = w)
        }
        BeautyFeature.SlimFace, BeautyFeature.NarrowFace, BeautyFeature.Cheekbones -> {
            faceOutline(c, narrow = 0.06f)
            val y = when (feature) {
                BeautyFeature.Cheekbones -> 0.42f
                BeautyFeature.NarrowFace -> 0.5f
                else -> 0.6f
            }
            arrow(c, at(0.02f, y), at(0.16f, y))
            arrow(c, at(0.98f, y), at(0.84f, y))
        }
        BeautyFeature.VShape, BeautyFeature.Jaw -> {
            faceOutline(c, narrow = 0.04f)
            arrow(c, at(0.06f, 0.82f), at(0.24f, 0.7f))
            arrow(c, at(0.94f, 0.82f), at(0.76f, 0.7f))
        }
        BeautyFeature.SmallFace -> {
            faceOutline(c, narrow = 0.08f)
            arrow(c, at(0.04f, 0.04f), at(0.18f, 0.18f))
            arrow(c, at(0.96f, 0.96f), at(0.82f, 0.82f))
        }
        BeautyFeature.Chin -> {
            faceOutline(c)
            arrow(c, at(0.5f, 0.72f), at(0.5f, 0.98f))
        }
        BeautyFeature.Forehead -> {
            faceOutline(c)
            arrow(c, at(0.5f, 0.3f), at(0.5f, 0.02f))
        }
        BeautyFeature.EnlargeEyes -> {
            eye(c, 0.5f, 0.5f, 0.3f)
            arrow(c, at(0.12f, 0.2f), at(0.02f, 0.06f))
            arrow(c, at(0.88f, 0.8f), at(0.98f, 0.94f))
        }
        BeautyFeature.EyeDistance -> {
            eye(c, 0.26f, 0.5f, 0.18f)
            eye(c, 0.74f, 0.5f, 0.18f)
            arrow(c, at(0.42f, 0.84f), at(0.1f, 0.84f))
            arrow(c, at(0.58f, 0.84f), at(0.9f, 0.84f))
        }
        BeautyFeature.EyeAngle -> {
            eye(c, 0.5f, 0.56f, 0.3f)
            arrow(c, at(0.72f, 0.3f), at(0.9f, 0.12f))
        }
        BeautyFeature.BrightenEyes -> {
            eye(c, 0.5f, 0.56f, 0.32f)
            drawLine(c, at(0.5f, 0.04f), at(0.5f, 0.16f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(c, at(0.24f, 0.12f), at(0.3f, 0.22f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(c, at(0.76f, 0.12f), at(0.7f, 0.22f), strokeWidth = w, cap = StrokeCap.Round)
        }
        BeautyFeature.Nose -> {
            nose(c)
            arrow(c, at(0.08f, 0.74f), at(0.24f, 0.74f))
            arrow(c, at(0.92f, 0.74f), at(0.76f, 0.74f))
        }
        BeautyFeature.NoseLength -> {
            nose(c)
            arrow(c, at(0.86f, 0.3f), at(0.86f, 0.9f))
        }
        BeautyFeature.MouthSize -> {
            lips(c)
            arrow(c, at(0.1f, 0.16f), at(0.02f, 0.06f))
            arrow(c, at(0.9f, 0.88f), at(0.98f, 0.96f))
        }
        BeautyFeature.Smile -> {
            drawArc(c, 20f, 140f, false, topLeft = at(0.14f, 0.1f), size = Size(size.width * 0.72f, size.height * 0.6f), style = line(c))
            arrow(c, at(0.14f, 0.5f), at(0.1f, 0.3f))
            arrow(c, at(0.86f, 0.5f), at(0.9f, 0.3f))
        }
        BeautyFeature.WhitenTeeth -> {
            val p = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.2f)
                quadraticTo(size.width * 0.5f, size.height * 0.08f, size.width * 0.72f, size.height * 0.2f)
                quadraticTo(size.width * 0.78f, size.height * 0.5f, size.width * 0.66f, size.height * 0.88f)
                quadraticTo(size.width * 0.58f, size.height * 0.62f, size.width * 0.5f, size.height * 0.6f)
                quadraticTo(size.width * 0.42f, size.height * 0.62f, size.width * 0.34f, size.height * 0.88f)
                quadraticTo(size.width * 0.22f, size.height * 0.5f, size.width * 0.28f, size.height * 0.2f)
            }
            drawPath(p, c, style = line(c))
        }
        BeautyFeature.Lipstick -> {
            lips(c, fill = true)
        }
        BeautyFeature.Contour -> {
            faceOutline(c)
            drawLine(c.copy(alpha = 0.7f), at(0.22f, 0.5f), at(0.36f, 0.72f), strokeWidth = w * 1.6f, cap = StrokeCap.Round)
            drawLine(c.copy(alpha = 0.7f), at(0.78f, 0.5f), at(0.64f, 0.72f), strokeWidth = w * 1.6f, cap = StrokeCap.Round)
        }
    }
}
