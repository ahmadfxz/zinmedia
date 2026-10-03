package com.zinmedia.photoeditor

import com.zinmedia.photoeditor.engine.PhotoFilter

/**
 * Konten editor foto yang ditentukan aplikasi. Isi sekali saat aplikasi mulai
 * (mis. di `Application.onCreate()`), sebelum editor dibuka.
 *
 * ```kotlin
 * PhotoEditorConfig.stickers = listOf("https://cdn.contoh.com/stiker/1.png")
 * ```
 */
public object PhotoEditorConfig {

    /** Stiker: URL atau URI apa pun yang bisa dimuat Coil. Kosong = tab Stiker disembunyikan. */
    @Volatile
    public var stickers: List<String> = emptyList()

    /** Emoji di tray stiker. Kosong = tab Emoji disembunyikan. */
    @Volatile
    public var emojis: List<String> = DefaultEmojis

    /** Filter foto yang ditampilkan, sesuai urutan. Kosong = fitur filter disembunyikan. */
    @Volatile
    public var filters: List<PhotoFilterOption> = PhotoFilterOption.Defaults

    /**
     * Warna utama (ARGB), mis. tombol kirim, tombol dialog, kursor. Default hijau.
     * Ikon/teks di atasnya otomatis putih atau hitam sesuai terang warna ini.
     */
    @Volatile
    public var accentColor: Int = DEFAULT_ACCENT_COLOR

    /** Warna utama bawaan (hijau). */
    public const val DEFAULT_ACCENT_COLOR: Int = 0xFF21C063.toInt()
}

/** Satu pilihan filter foto bawaan beserta labelnya. */
public data class PhotoFilterOption(
    val filter: PhotoFilter,
    val label: String,
) {
    public companion object {
        /** Semua filter bawaan dengan label Bahasa Indonesia. */
        public val Defaults: List<PhotoFilterOption> = listOf(
            PhotoFilterOption(PhotoFilter.NONE, "Asli"),
            PhotoFilterOption(PhotoFilter.AUTO_FIX, "Auto"),
            PhotoFilterOption(PhotoFilter.BRIGHTNESS, "Cerah"),
            PhotoFilterOption(PhotoFilter.CONTRAST, "Kontras"),
            PhotoFilterOption(PhotoFilter.DOCUMENTARY, "Dokumenter"),
            PhotoFilterOption(PhotoFilter.DUE_TONE, "Dual Tone"),
            PhotoFilterOption(PhotoFilter.FILL_LIGHT, "Fill Light"),
            PhotoFilterOption(PhotoFilter.FISH_EYE, "Fish Eye"),
            PhotoFilterOption(PhotoFilter.GRAIN, "Grain"),
            PhotoFilterOption(PhotoFilter.GRAY_SCALE, "Abu-abu"),
            PhotoFilterOption(PhotoFilter.LOMISH, "Lomo"),
            PhotoFilterOption(PhotoFilter.NEGATIVE, "Negatif"),
            PhotoFilterOption(PhotoFilter.POSTERIZE, "Poster"),
            PhotoFilterOption(PhotoFilter.SATURATE, "Saturasi"),
            PhotoFilterOption(PhotoFilter.SEPIA, "Sepia"),
            PhotoFilterOption(PhotoFilter.SHARPEN, "Tajam"),
            PhotoFilterOption(PhotoFilter.TEMPERATURE, "Hangat"),
            PhotoFilterOption(PhotoFilter.TINT, "Tint"),
            PhotoFilterOption(PhotoFilter.VIGNETTE, "Vignette"),
            PhotoFilterOption(PhotoFilter.CROSS_PROCESS, "Cross"),
            PhotoFilterOption(PhotoFilter.BLACK_WHITE, "Hitam Putih"),
        )
    }
}

/** Emoji bawaan (Unicode, tanpa lisensi pihak ketiga). */
public val DefaultEmojis: List<String> = listOf(
    "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
    "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
    "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩",
    "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
    "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬",
    "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗",
    "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯",
    "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐",
    "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "😈",
    "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽", "👾",
    "🤖", "🎃", "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿",
    "😾", "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤏", "✌️", "🤞",
    "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "👍",
    "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝",
    "🙏", "✍️", "💅", "🤳", "💪", "🦾", "🦵", "🦿", "🦶", "👣",
    "👂", "🦻", "👃", "🧠", "🦷", "🦴", "👀", "👁️", "👅", "👄",
    "💋", "🩸", "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
    "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝",
)
