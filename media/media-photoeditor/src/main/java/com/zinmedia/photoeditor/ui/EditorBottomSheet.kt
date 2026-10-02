package com.zinmedia.photoeditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val EditorSheetBackground = Color(0xFF121B22)

/**
 * Bottom sheet seragam untuk editor: flat (tanpa sudut membulat), selalu gelap (termasuk ikon
 * status/navigation bar), drag handle rapat ke atas, dan konten diberi jarak dari navigation bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = EditorSheetBackground,
        contentColor = Color.White,
        shape = RectangleShape,
        dragHandle = { SheetDragHandle() },
        properties = ModalBottomSheetProperties(
            isAppearanceLightStatusBars = false,
            isAppearanceLightNavigationBars = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Scroll di dalam daftar tidak boleh ikut menarik/menutup sheet;
                // sheet hanya bisa ditarik dari area di luar daftar (mis. handle & tab).
                .nestedScroll(KeepScrollInsideSheet)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            content = content,
        )
    }
}

/** Drag handle ringkas: lebih rapat ke tepi atas dibanding bawaan Material (22dp). */
@Composable
private fun SheetDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
        )
    }
}

/** Menahan sisa scroll/fling dari daftar agar tidak diteruskan ke gesture tarik sheet. */
private val KeepScrollInsideSheet = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}
