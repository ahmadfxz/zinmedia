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
     * Fragment filter: penghalus kulit (`uSmooth`), warna affine (`uColorMatrix`, `uColorOffset`),
     * lalu LUT (`sLut`, `uLutSize`, `uLutMix`). [external] = input tekstur kamera OES, selain itu
     * tekstur 2D biasa.
     */
    public fun filterFragment(external: Boolean): String {
        val header = if (external) {
            "#extension GL_OES_EGL_image_external : require\nprecision mediump float;\nuniform samplerExternalOES sTexture;\n"
        } else {
            "precision mediump float;\nuniform sampler2D sTexture;\n"
        }
        return header + FILTER_BODY
    }

    private const val FILTER_BODY = """
        uniform sampler2D sLut;
        uniform mat3 uColorMatrix;
        uniform vec3 uColorOffset;
        uniform float uLutSize;
        uniform float uLutMix;
        uniform float uSmooth;
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

        // Penghalus kulit sederhana: rata-rata tetangga yang warnanya mirip (bilateral ringan),
        // sehingga tepi tetap tajam.
        vec3 smoothSkin(vec3 c) {
            vec3 sum = c;
            float total = 1.0;
            for (int i = 0; i < 8; i++) {
                float a = float(i) * 0.785398;
                vec2 offset = vec2(cos(a), sin(a)) * uTexel * 3.0;
                vec3 s = texture2D(sTexture, vTexCoord + offset).rgb;
                float w = max(0.0, 1.0 - distance(s, c) * 6.0);
                sum += s * w;
                total += w;
            }
            vec3 blurred = sum / total;
            return mix(c, blurred, uSmooth) + uSmooth * 0.03;
        }

        void main() {
            vec3 c = texture2D(sTexture, vTexCoord).rgb;
            if (uSmooth > 0.0) c = smoothSkin(c);
            c = clamp(uColorMatrix * c + uColorOffset, 0.0, 1.0);
            if (uLutMix > 0.0) c = mix(c, applyLut(c), uLutMix);
            gl_FragColor = vec4(c, 1.0);
        }
    """
}
