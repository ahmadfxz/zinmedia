package com.zinmedia.composer

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.relocation.BringIntoViewModifierNode

/**
 * Menahan permintaan "bring into view" (mis. kursor kolom teks di mode teks) agar tidak
 * diteruskan ke pager. Tanpa ini, mengetik di halaman lain membuat pager menggulir sendiri.
 */
internal fun Modifier.stopBringIntoView(): Modifier = this then StopBringIntoViewElement

private object StopBringIntoViewElement : ModifierNodeElement<StopBringIntoViewNode>() {
    override fun create() = StopBringIntoViewNode()
    override fun update(node: StopBringIntoViewNode) = Unit
    override fun hashCode(): Int = javaClass.hashCode()
    override fun equals(other: Any?): Boolean = other === this
    override fun InspectorInfo.inspectableProperties() {
        name = "stopBringIntoView"
    }
}

private class StopBringIntoViewNode : Modifier.Node(), BringIntoViewModifierNode {
    override suspend fun bringIntoView(childCoordinates: LayoutCoordinates, boundsProvider: () -> Rect?) = Unit
}
