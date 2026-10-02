package com.jernih.editor.data.manager

import android.content.Context
import android.graphics.Typeface
import com.jernih.editor.domain.model.FontItem


class FontManager(private val context: Context) {
    private val fontMap = mutableMapOf<Int, FontItem>()

    val fonts: List<FontItem>
        get() = fontMap.values.toList()

    init {
        initializeFonts()
    }

    private fun initializeFonts() {
        val fontDefinitions = listOf(
            FontItem(0, "Inter", loadFont("inter_18pt_bold.ttf") ?: Typeface.DEFAULT_BOLD),
            FontItem(1, "Monofett", loadFont("monofett_regular.ttf") ?: Typeface.DEFAULT),
            FontItem(2, "Caveat", loadFont("caveat_regular.ttf") ?: Typeface.SERIF),
            FontItem(3, "Pacifico", loadFont("pacifico_regular.ttf") ?: Typeface.MONOSPACE),
            FontItem(4, "Rampartone", loadFont("rampartone_regular.ttf") ?: Typeface.DEFAULT),
            FontItem(5, "Karla", loadFont("karla_bold.ttf") ?: Typeface.DEFAULT),
        )

        fontDefinitions.forEach { fontItem ->
            fontMap[fontItem.id] = fontItem
        }
    }

    private fun loadFont(fontName: String): Typeface? {
        return try {
            Typeface.createFromAsset(context.assets, fontName)
        } catch (e: Exception) {
            null
        }
    }

    fun getFont(fontId: Int): Typeface {
        return fontMap[fontId]?.typeface ?: Typeface.DEFAULT
    }

    fun getFontOrNull(fontId: Int): Typeface? {
        return fontMap[fontId]?.typeface
    }
}