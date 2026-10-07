package com.zinmedia.effects

import com.zinmedia.effects.BeautyFeature.Blush
import com.zinmedia.effects.BeautyFeature.Brighten
import com.zinmedia.effects.BeautyFeature.BrightenEyes
import com.zinmedia.effects.BeautyFeature.Cheekbones
import com.zinmedia.effects.BeautyFeature.Chin
import com.zinmedia.effects.BeautyFeature.Contour
import com.zinmedia.effects.BeautyFeature.DarkCircles
import com.zinmedia.effects.BeautyFeature.EnlargeEyes
import com.zinmedia.effects.BeautyFeature.Jaw
import com.zinmedia.effects.BeautyFeature.Lipstick
import com.zinmedia.effects.BeautyFeature.MouthSize
import com.zinmedia.effects.BeautyFeature.Nose
import com.zinmedia.effects.BeautyFeature.Rosy
import com.zinmedia.effects.BeautyFeature.Sharpen
import com.zinmedia.effects.BeautyFeature.SlimFace
import com.zinmedia.effects.BeautyFeature.SmallFace
import com.zinmedia.effects.BeautyFeature.Smile
import com.zinmedia.effects.BeautyFeature.SmileLines
import com.zinmedia.effects.BeautyFeature.Smooth
import com.zinmedia.effects.BeautyFeature.VShape
import com.zinmedia.effects.BeautyFeature.WhitenTeeth

/** Kelompok fitur beauty, untuk menyusun tab/daftar di UI. */
public enum class BeautyGroup(public val label: String) {
    Skin("Kulit"),
    Face("Wajah"),
    Eyes("Mata"),
    Nose("Hidung"),
    Mouth("Mulut"),
    Makeup("Riasan"),
}

/**
 * Katalog fitur beauty. Aplikasi cukup menampilkan [entries] (dikelompokkan per [group]) dengan
 * slider sesuai [range]; nilai disimpan/dikirim lewat [id]. Fitur [bipolar] bernilai −1..1 (mis.
 * dagu lebih pendek ↔ lebih panjang), lainnya 0..1.
 *
 * Kulit & riasan hanya mengenai area wajah yang sesuai (mis. bibir, bawah mata); bentuk wajah
 * menggeser gambar mengikuti titik wajah. Semuanya ikut miring, menoleh, dan berekspresi.
 */
public enum class BeautyFeature(
    public val id: String,
    public val label: String,
    public val group: BeautyGroup,
    public val bipolar: Boolean = false,
) {
    Smooth("smooth", "Halus", BeautyGroup.Skin),
    Brighten("brighten", "Cerah", BeautyGroup.Skin),
    Rosy("rosy", "Merona", BeautyGroup.Skin),
    DarkCircles("darkCircles", "Lingkar Mata", BeautyGroup.Skin),
    SmileLines("smileLines", "Garis Senyum", BeautyGroup.Skin),
    Sharpen("sharpen", "Tajam", BeautyGroup.Skin),

    SlimFace("slimFace", "Tirus", BeautyGroup.Face),
    VShape("vShape", "V-Line", BeautyGroup.Face),
    NarrowFace("narrowFace", "Wajah Ramping", BeautyGroup.Face),
    SmallFace("smallFace", "Wajah Kecil", BeautyGroup.Face),
    Cheekbones("cheekbones", "Tulang Pipi", BeautyGroup.Face),
    Jaw("jaw", "Rahang", BeautyGroup.Face),
    Chin("chin", "Dagu", BeautyGroup.Face, bipolar = true),
    Forehead("forehead", "Dahi", BeautyGroup.Face, bipolar = true),

    EnlargeEyes("enlargeEyes", "Mata Besar", BeautyGroup.Eyes),
    EyeDistance("eyeDistance", "Jarak Mata", BeautyGroup.Eyes, bipolar = true),
    EyeAngle("eyeAngle", "Sudut Mata", BeautyGroup.Eyes, bipolar = true),
    BrightenEyes("brightenEyes", "Mata Cerah", BeautyGroup.Eyes),

    Nose("nose", "Hidung Ramping", BeautyGroup.Nose),
    NoseLength("noseLength", "Panjang Hidung", BeautyGroup.Nose, bipolar = true),

    MouthSize("mouthSize", "Ukuran Bibir", BeautyGroup.Mouth, bipolar = true),
    Smile("smile", "Senyum", BeautyGroup.Mouth),
    WhitenTeeth("whitenTeeth", "Gigi Putih", BeautyGroup.Mouth),

    Lipstick("lipstick", "Lipstik", BeautyGroup.Makeup),
    Blush("blush", "Perona", BeautyGroup.Makeup),
    Contour("contour", "Kontur", BeautyGroup.Makeup),
    ;

    /** Rentang nilai yang didukung. */
    public val range: ClosedFloatingPointRange<Float> get() = if (bipolar) -1f..1f else 0f..1f

    /** Fitur yang menggeser bentuk (bukan warna/kulit). */
    public val reshapes: Boolean get() = group == BeautyGroup.Face || group == BeautyGroup.Eyes && this != BrightenEyes ||
        group == BeautyGroup.Nose || this == MouthSize || this == Smile

    public companion object {
        /** Fitur berdasarkan [id], atau `null` bila tidak dikenal (mis. dari versi lebih baru). */
        public fun fromId(id: String): BeautyFeature? = entries.firstOrNull { it.id == id }
    }
}

/** Hasil akhir (tekstur) lipstik untuk [BeautyFeature.Lipstick]. */
public enum class LipFinish(public val id: String, public val label: String) {
    /** Tanpa kilau, lembut seperti beludru. */
    Matte("matte", "Matte"),

    /** Sedikit kilau alami dari bibir asli (default). */
    Satin("satin", "Satin"),

    /** Basah & berkilau: pantulan cahaya di bibir diperkuat. */
    Gloss("gloss", "Glossy"),
    ;

    public companion object {
        /** Finish berdasarkan [id], atau `null` bila tidak dikenal. */
        public fun fromId(id: String): LipFinish? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Nilai beauty: satu angka per [BeautyFeature] (default 0 = mati), selalu dalam [BeautyFeature.range],
 * ditambah warna & finish lipstik. Tidak berubah; ubah dengan [with].
 *
 * ```kotlin
 * val beauty = BeautyParams.of(BeautyFeature.Smooth to 0.5f, BeautyFeature.SlimFace to 0.3f)
 * effects.setBeauty(beauty.with(BeautyFeature.EnlargeEyes, 0.2f))
 * // Simpan / dari backend:
 * val json: Map<String, Float> = beauty.toMap()
 * val fromServer = BeautyParams.fromMap(mapOf("smooth" to 0.4, "chin" to -0.2))
 * ```
 */
public class BeautyParams private constructor(
    private val values: FloatArray,
    /** Warna lipstik (ARGB) untuk [BeautyFeature.Lipstick]. */
    public val lipColor: Int = DEFAULT_LIP_COLOR,
    /** Finish lipstik untuk [BeautyFeature.Lipstick]. */
    public val lipFinish: LipFinish = LipFinish.Satin,
) {
    /** Nilai [feature]. */
    public operator fun get(feature: BeautyFeature): Float = values[feature.ordinal]

    /** Salinan dengan [feature] = [value] (dibatasi ke rentangnya). */
    public fun with(feature: BeautyFeature, value: Float): BeautyParams {
        val copy = values.copyOf()
        copy[feature.ordinal] = value.coerceIn(feature.range)
        return BeautyParams(copy, lipColor, lipFinish)
    }

    /** Salinan dengan warna lipstik lain. */
    public fun withLipColor(color: Int): BeautyParams = BeautyParams(values, color, lipFinish)

    /** Salinan dengan finish lipstik lain. */
    public fun withLipFinish(finish: LipFinish): BeautyParams = BeautyParams(values, lipColor, finish)

    /** `true` bila setidaknya satu fitur aktif. */
    public val enabled: Boolean get() = values.any { it != 0f }

    /** `true` bila butuh pelacakan wajah (semua fitur kecuali [BeautyFeature.Sharpen] yang mengenai seluruh gambar). */
    public val needsFace: Boolean get() = BeautyFeature.entries.any { it != BeautyFeature.Sharpen && this[it] != 0f }

    /** `true` bila ada fitur bentuk aktif (butuh warp). */
    public val reshapes: Boolean get() = BeautyFeature.entries.any { it.reshapes && this[it] != 0f }

    /** Fitur yang aktif beserta nilainya. */
    public val active: Map<BeautyFeature, Float> get() = BeautyFeature.entries.filter { this[it] != 0f }.associateWith { this[it] }

    /** Nilai sebagai peta berkunci [BeautyFeature.id] (hanya yang aktif), mis. untuk disimpan sebagai JSON. */
    public fun toMap(): Map<String, Float> = active.mapKeys { it.key.id }

    override fun equals(other: Any?): Boolean =
        other is BeautyParams && values.contentEquals(other.values) && lipColor == other.lipColor &&
            lipFinish == other.lipFinish

    override fun hashCode(): Int = (values.contentHashCode() * 31 + lipColor) * 31 + lipFinish.hashCode()

    override fun toString(): String = "BeautyParams(${toMap()})"

    public companion object {
        /** Merah mawar. */
        public const val DEFAULT_LIP_COLOR: Int = 0xFFC2185B.toInt()

        /** Tanpa beauty. */
        public val None: BeautyParams = BeautyParams(FloatArray(BeautyFeature.entries.size))

        /** Dari pasangan fitur -> nilai. */
        public fun of(vararg values: Pair<BeautyFeature, Float>): BeautyParams =
            values.fold(None) { acc, (feature, value) -> acc.with(feature, value) }

        /**
         * Dari peta [BeautyFeature.id] -> nilai (mis. preset dari backend). Kunci yang tidak ada = 0;
         * kunci tak dikenal diabaikan (aman untuk fitur versi baru); nilai dibatasi ke rentangnya.
         */
        public fun fromMap(
            values: Map<String, Number>,
            lipColor: Int = DEFAULT_LIP_COLOR,
            lipFinish: LipFinish = LipFinish.Satin,
        ): BeautyParams =
            values.entries.fold(None.withLipColor(lipColor).withLipFinish(lipFinish)) { acc, (id, value) ->
                BeautyFeature.fromId(id)?.let { acc.with(it, value.toFloat()) } ?: acc
            }
    }
}

/** Preset yang katalog, nama, dan ikonnya dapat disediakan aplikasi atau backend. */
public data class BeautyPreset(
    val id: String,
    val name: String,
    val iconUrl: String? = null,
    val params: BeautyParams,
)

/** Preset bawaan untuk UI siap pakai; aplikasi dapat menggantinya (mis. `CameraConfig.beautyPresets`). */
public val DefaultBeautyPresets: List<BeautyPreset> = run {
    fun preset(id: String, name: String, lipColor: Int, vararg values: Pair<BeautyFeature, Float>, finish: LipFinish = LipFinish.Satin) =
        BeautyPreset(id, name, params = BeautyParams.of(*values).withLipColor(lipColor).withLipFinish(finish))
    // Prinsip: kulit halus tapi tidak seperti plastik, bentuk wajah tipis (tetap mirip diri sendiri),
    // dan riasan dengan warna & finish yang serasi per gaya.
    listOf(
        preset(
            "natural", "Natural", 0xFFC9787A.toInt(),
            Smooth to 0.35f, Brighten to 0.1f, DarkCircles to 0.35f, SmileLines to 0.2f, SlimFace to 0.1f,
            EnlargeEyes to 0.08f, BrightenEyes to 0.15f, Lipstick to 0.18f, Blush to 0.1f,
        ),
        preset(
            "soft", "Lembut", 0xFFE57373.toInt(),
            Smooth to 0.55f, Brighten to 0.18f, Rosy to 0.12f, DarkCircles to 0.45f, SmileLines to 0.35f,
            VShape to 0.15f, EnlargeEyes to 0.12f, BrightenEyes to 0.2f, Lipstick to 0.22f, Blush to 0.2f,
        ),
        preset(
            "fresh", "Segar", 0xFFF06262.toInt(),
            Smooth to 0.4f, Brighten to 0.22f, Rosy to 0.18f, DarkCircles to 0.4f, BrightenEyes to 0.3f,
            WhitenTeeth to 0.3f, Lipstick to 0.25f, Blush to 0.25f,
            finish = LipFinish.Gloss,
        ),
        preset(
            "glow", "Glowing", 0xFFC97064.toInt(),
            Smooth to 0.5f, Brighten to 0.3f, DarkCircles to 0.45f, Sharpen to 0.1f, Contour to 0.25f,
            SlimFace to 0.12f, BrightenEyes to 0.25f, Lipstick to 0.25f, Blush to 0.12f,
            finish = LipFinish.Gloss,
        ),
        preset(
            "korean", "Korea", 0xFFE5495E.toInt(),
            Smooth to 0.6f, Brighten to 0.28f, Rosy to 0.1f, DarkCircles to 0.5f, SmileLines to 0.3f,
            VShape to 0.25f, SmallFace to 0.1f, EnlargeEyes to 0.18f, Nose to 0.2f, BrightenEyes to 0.25f,
            Lipstick to 0.35f, Blush to 0.2f,
            finish = LipFinish.Gloss,
        ),
        preset(
            "glam", "Glam", BeautyParams.DEFAULT_LIP_COLOR,
            Smooth to 0.5f, Brighten to 0.15f, SlimFace to 0.22f, VShape to 0.2f, Cheekbones to 0.1f,
            EnlargeEyes to 0.2f, Nose to 0.25f, BrightenEyes to 0.35f, WhitenTeeth to 0.4f,
            Lipstick to 0.5f, Blush to 0.25f, Contour to 0.35f,
            finish = LipFinish.Matte,
        ),
        preset(
            "doll", "Boneka", 0xFFF06292.toInt(),
            Smooth to 0.6f, Brighten to 0.2f, Rosy to 0.15f, SmallFace to 0.2f, VShape to 0.25f,
            EnlargeEyes to 0.3f, Nose to 0.25f, Chin to 0.1f, MouthSize to -0.1f, Lipstick to 0.3f, Blush to 0.35f,
            finish = LipFinish.Gloss,
        ),
        preset(
            "sculpt", "Tirus", 0xFFB5776A.toInt(),
            Smooth to 0.3f, SlimFace to 0.3f, VShape to 0.25f, Cheekbones to 0.25f, Jaw to 0.25f,
            Chin to 0.08f, Nose to 0.25f, Contour to 0.45f, Lipstick to 0.2f,
            finish = LipFinish.Matte,
        ),
        preset(
            "party", "Pesta", 0xFFC62828.toInt(),
            Smooth to 0.5f, Brighten to 0.15f, SlimFace to 0.2f, EnlargeEyes to 0.18f, BrightenEyes to 0.4f,
            WhitenTeeth to 0.5f, Sharpen to 0.15f, Lipstick to 0.55f, Blush to 0.25f, Contour to 0.35f,
            finish = LipFinish.Gloss,
        ),
        preset(
            "sweet", "Manis", 0xFFE0607E.toInt(),
            Smooth to 0.5f, Brighten to 0.18f, Rosy to 0.2f, DarkCircles to 0.35f, EnlargeEyes to 0.18f,
            Smile to 0.25f, Lipstick to 0.3f, Blush to 0.4f,
        ),
        preset(
            "nude", "Nude", 0xFFB5776A.toInt(),
            Smooth to 0.45f, Brighten to 0.1f, DarkCircles to 0.4f, SlimFace to 0.15f, BrightenEyes to 0.2f,
            Lipstick to 0.45f, Contour to 0.25f,
            finish = LipFinish.Matte,
        ),
        preset(
            "men", "Pria", BeautyParams.DEFAULT_LIP_COLOR,
            Smooth to 0.25f, DarkCircles to 0.35f, SmileLines to 0.25f, Sharpen to 0.2f,
            Cheekbones to 0.1f, Jaw to 0.1f, BrightenEyes to 0.2f, WhitenTeeth to 0.3f,
        ),
    )
}
