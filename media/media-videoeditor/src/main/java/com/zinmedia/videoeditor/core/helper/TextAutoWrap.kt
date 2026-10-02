package com.zinmedia.videoeditor.core.helper

fun autoWrap(text: String, maxPerLine: Int = 15): String {
    val result = StringBuilder()
    var count = 0

    for (char in text) {
        if (char == '\n') {
            count = 0
        } else if (count >= maxPerLine) {
            result.append('\n')
            count = 0
        }

        result.append(char)
        count++
    }

    return result.toString()
}
