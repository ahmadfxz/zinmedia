package com.zinmedia.videoeditor

/**
 * Konten editor video yang ditentukan aplikasi. Isi sekali saat aplikasi mulai
 * (mis. di `Application.onCreate()`), sebelum editor dibuka.
 */
public object VideoEditorConfig {

    /** Stiker: URL atau URI apa pun yang bisa dimuat Coil. Kosong = tab Stiker disembunyikan. */
    @Volatile
    public var stickers: List<String> = emptyList()

    /** Emoji di tray stiker. Kosong = tab Emoji disembunyikan. */
    @Volatile
    public var emojis: List<String> = DefaultVideoEmojis

    /** Filter warna (LUT `.cube`), sesuai urutan. Kosong = fitur filter disembunyikan. */
    @Volatile
    public var filters: List<VideoFilterOption> = emptyList()

    /**
     * Warna utama (ARGB), mis. tombol kirim, tombol dialog, kursor. Default hijau.
     * Ikon/teks di atasnya otomatis putih atau hitam sesuai terang warna ini.
     */
    @Volatile
    public var accentColor: Int = DEFAULT_ACCENT_COLOR

    /** Warna utama bawaan (hijau). */
    public const val DEFAULT_ACCENT_COLOR: Int = 0xFF21C063.toInt()
}

/**
 * Satu filter video berbasis LUT 3D.
 *
 * @param name label di bawah thumbnail.
 * @param cubeUrl URL file `.cube`.
 * @param thumbnailUrl gambar contoh hasil filter.
 */
public data class VideoFilterOption(
    val name: String,
    val cubeUrl: String,
    val thumbnailUrl: String,
)

/** Emoji bawaan (Unicode, tanpa lisensi pihak ketiga). */
internal val DefaultVideoEmojis: List<String> = listOf(
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
