package com.zinmedia.effects.gl

import androidx.annotation.RestrictTo

/** Shader GLSL ES 2.0 untuk filter warna, LUT, penghalus kulit, dan gambar efek wajah. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object EffectShaders {

    /** Vertex: posisi apa adanya, koordinat tekstur dikali `uTexMatrix`. */
    public const val VERTEX: String = """
        attribute vec4 aPosition;
        attribute vec4 aTexCoord;
        uniform mat4 uTexMatrix;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = (uTexMatrix * aTexCoord).xy;
        }
    """

    /**
     * Fragment filter: beauty kulit & riasan dari masker wajah ([BeautyUniforms]; bentuk wajah sudah
     * diterapkan pada frame oleh [BeautyPass]), warna affine (`uColorMatrix`, `uColorOffset`), lalu
     * LUT (`sLut`, `uLutSize`, `uLutMix`). [external] = input tekstur kamera OES, selain itu
     * tekstur 2D biasa.
     */
    public fun filterFragment(external: Boolean): String {
        val header = if (external) {
            "#extension GL_OES_EGL_image_external : require\n$PRECISION\nuniform samplerExternalOES sTexture;\n"
        } else {
            "$PRECISION\nuniform sampler2D sTexture;\n"
        }
        return header + FILTER_BODY
    }

    /** Koordinat wajah dalam piksel butuh presisi tinggi; mediump hanya bila GPU tidak mendukung. */
    private const val PRECISION = "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif"

    private const val FILTER_BODY = """
        uniform sampler2D sLut;
        uniform mat3 uColorMatrix;
        uniform vec3 uColorOffset;
        uniform float uLutSize;
        uniform float uLutMix;
        // Masker beauty (lihat BeautyPass), koordinat sama dengan sTexture:
        // sMask0 = kulit, bawah mata, garis senyum, bibir; sMask1 = gigi, mata, perona, highlight;
        // sMask2.r = kontur.
        uniform sampler2D sMask0;
        uniform sampler2D sMask1;
        uniform sampler2D sMask2;
        // halus, cerah, merona, tajam
        uniform vec4 uSkin;
        // lingkar mata, garis senyum, gigi putih, mata cerah
        uniform vec4 uAreas;
        // lipstik, perona, kontur, masker aktif (1/0)
        uniform vec4 uMakeup;
        uniform vec3 uLipColor;
        uniform vec2 uTexel;
        varying vec2 vTexCoord;

        vec3 applyLut(vec3 c) {
            float n = uLutSize;
            float blue = c.b * (n - 1.0);
            float b0 = floor(blue);
            float b1 = min(b0 + 1.0, n - 1.0);
            float x = c.r * (n - 1.0) + 0.5;
            float y = (c.g * (n - 1.0) + 0.5) / n;
            vec3 c0 = texture2D(sLut, vec2((b0 * n + x) / (n * n), y)).rgb;
            vec3 c1 = texture2D(sLut, vec2((b1 * n + x) / (n * n), y)).rgb;
            return mix(c0, c1, blue - b0);
        }

        // Bibir dengan tepi lembut: rata-rata masker di sekitar piksel, sedikit menyusut ke dalam
        // (garis bibir dari jaring wajah cenderung sedikit di luar bibir asli).
        float lipMask(float center) {
            vec2 r = uTexel * 4.0;
            float sum = center
                + texture2D(sMask0, vTexCoord + vec2(r.x, 0.0)).a + texture2D(sMask0, vTexCoord - vec2(r.x, 0.0)).a
                + texture2D(sMask0, vTexCoord + vec2(0.0, r.y)).a + texture2D(sMask0, vTexCoord - vec2(0.0, r.y)).a
                + texture2D(sMask0, vTexCoord + r * 0.7).a + texture2D(sMask0, vTexCoord - r * 0.7).a
                + texture2D(sMask0, vTexCoord + vec2(r.x, -r.y) * 0.7).a + texture2D(sMask0, vTexCoord - vec2(r.x, -r.y) * 0.7).a;
            return smoothstep(0.3, 0.95, sum / 9.0);
        }

        float luma(vec3 c) {
            return dot(c, vec3(0.299, 0.587, 0.114));
        }

        // Satu tetangga bilateral: warna yang jauh berbeda tidak ikut diratakan (tepi tetap tajam).
        vec3 tap(vec3 c, vec2 offset, inout float total) {
            vec3 s = texture2D(sTexture, vTexCoord + offset).rgb;
            float w = max(0.0, 1.0 - distance(s, c) * 5.0);
            total += w;
            return s * w;
        }

        // Bilateral 2 cincin × 8 arah (arah tetap, tanpa sin/cos per piksel).
        vec3 smoothSkin(vec3 c, float amount) {
            vec2 scale = uTexel * mix(2.0, 4.5, amount);
            vec3 sum = c;
            float total = 1.0;
            sum += tap(c, vec2(0.9239, 0.3827) * scale, total);
            sum += tap(c, vec2(0.3827, 0.9239) * scale, total);
            sum += tap(c, vec2(-0.3827, 0.9239) * scale, total);
            sum += tap(c, vec2(-0.9239, 0.3827) * scale, total);
            sum += tap(c, vec2(-0.9239, -0.3827) * scale, total);
            sum += tap(c, vec2(-0.3827, -0.9239) * scale, total);
            sum += tap(c, vec2(0.3827, -0.9239) * scale, total);
            sum += tap(c, vec2(0.9239, -0.3827) * scale, total);
            sum += tap(c, vec2(1.4142, 1.4142) * scale, total);
            sum += tap(c, vec2(0.0000, 2.0000) * scale, total);
            sum += tap(c, vec2(-1.4142, 1.4142) * scale, total);
            sum += tap(c, vec2(-2.0000, 0.0000) * scale, total);
            sum += tap(c, vec2(-1.4142, -1.4142) * scale, total);
            sum += tap(c, vec2(-0.0000, -2.0000) * scale, total);
            sum += tap(c, vec2(1.4142, -1.4142) * scale, total);
            sum += tap(c, vec2(2.0000, -0.0000) * scale, total);
            return sum / total;
        }

        void main() {
            vec3 original = texture2D(sTexture, vTexCoord).rgb;
            vec3 c = original;
            if (uMakeup.w > 0.5) {
                vec4 m0 = texture2D(sMask0, vTexCoord);
                vec4 m1 = texture2D(sMask1, vTexCoord);
                float contour = texture2D(sMask2, vTexCoord).r;
                float skin = m0.r;
                float under = m0.g;
                float smile = m0.b;
                float teeth = m1.r;
                float lips = uMakeup.x > 0.0 ? lipMask(m0.a) : 0.0;
                float eyes = m1.g;
                float cheeks = m1.b;
                float highlight = m1.a;

                // Halus: kulit, ditambah garis senyum & bawah mata yang lebih kuat.
                float smooth = clamp(skin * uSkin.x + smile * uAreas.y * 0.9 + under * uAreas.x * 0.6, 0.0, 1.0);
                if (smooth > 0.001) c = mix(c, smoothSkin(c, smooth), smooth * 0.88);
                // Lingkar mata: angkat bayangan & kurangi kebiruan.
                c += (1.0 - c) * under * uAreas.x * 0.22;
                c = mix(c, vec3(luma(c)) + (c - vec3(luma(c))) * 0.8 + vec3(0.02, 0.01, -0.01), under * uAreas.x * 0.5);
                // Cerah & merona: kulit.
                c += (1.0 - c) * skin * uSkin.y * 0.22;
                c = mix(c, vec3(c.r + 0.10, c.g - 0.015, c.b + 0.015), skin * uSkin.z * 0.5);
                // Gigi putih: hanya piksel terang di dalam mulut, kuning dikurangi.
                float bright = smoothstep(0.28, 0.55, luma(c));
                c = mix(c, vec3(luma(c) * 1.1 + 0.05), teeth * uAreas.z * bright * 0.75);
                // Mata cerah: putih mata & iris lebih terang dan kontras.
                c = mix(c, clamp((c - 0.5) * 1.18 + 0.56, 0.0, 1.0), eyes * uAreas.w * 0.7);
                // Lipstik: rona & kepekatan dari warna lipstik; kecerahan ikut terang-gelap bibir
                // asli (diredam, agar tekstur tetap ada tanpa membuat warna gelap jadi menyala).
                float gain = pow(luma(c) / max(luma(uLipColor), 0.05), 0.6);
                vec3 lip = clamp(uLipColor * gain, 0.0, 1.0);
                c = mix(c, lip, lips * uMakeup.x * 0.85);
                // Perona: merah muda lembut di apel pipi.
                c = mix(c, c * vec3(1.0, 0.86, 0.88) + vec3(0.07, 0.0, 0.02), cheeks * uMakeup.y * 0.7);
                // Kontur & highlight.
                c *= 1.0 - contour * uMakeup.z * 0.2;
                c += (1.0 - c) * highlight * uMakeup.z * 0.18;
            }
            // Tajam: seluruh gambar (unsharp ringan).
            if (uSkin.w > 0.0) {
                vec3 blur = (texture2D(sTexture, vTexCoord + vec2(uTexel.x, 0.0)).rgb + texture2D(sTexture, vTexCoord - vec2(uTexel.x, 0.0)).rgb
                    + texture2D(sTexture, vTexCoord + vec2(0.0, uTexel.y)).rgb + texture2D(sTexture, vTexCoord - vec2(0.0, uTexel.y)).rgb) * 0.25;
                c += (original - blur) * uSkin.w * 1.2;
            }
            c = clamp(uColorMatrix * c + uColorOffset, 0.0, 1.0);
            if (uLutMix > 0.0) c = mix(c, applyLut(c), uLutMix);
            gl_FragColor = vec4(c, 1.0);
        }
    """
}
