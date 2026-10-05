package com.zinmedia.sample

import android.Manifest
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.encoder.utils.gl.AspectRatioMode
import com.pedro.library.rtmp.RtmpStream
import com.zinmedia.effects.FaceEffect
import com.zinmedia.effects.ZinEffects
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Contoh media-effects pada pipeline RootEncoder milik aplikasi (kamera, siaran, dan UI milik
 * aplikasi; zinmedia hanya filter efeknya). Susunannya sama dengan aplikasi live pada umumnya:
 * RtmpStream + Camera2Source + SurfaceView.
 */
class EffectsDemoActivity : ComponentActivity(), ConnectChecker {

    private lateinit var stream: RtmpStream
    private lateinit var effects: ZinEffects
    private lateinit var status: TextView
    private var filter = 0
    private var smoothing = false
    private var giftJob: Job? = null

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.all { ok -> ok }) setUp() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        permissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    private fun setUp() {
        val url = intent.getStringExtra("url") ?: "rtmp://127.0.0.1:1935/live/test"
        val camera = Camera2Source(this).apply { switchCamera() }
        stream = RtmpStream(this, this, camera, com.pedro.encoder.input.sources.audio.MicrophoneSource())
        stream.prepareVideo(1280, 720, 2_000_000, fps = 30, iFrameInterval = 2, rotation = 90)
        stream.prepareAudio(44_100, true, 128_000)
        stream.getGlInterface().setAspectRatioMode(AspectRatioMode.Fill)

        // Kamera depan RootEncoder dikirim sebagai gambar cermin: beri tahu efek agar kiri/kanan benar.
        effects = ZinEffects(this, mirrored = true)
        stream.getGlInterface().addFilter(effects.filterRender)
        lifecycleScope.launch { runCatching { effects.prepareFaceEffects() } }

        val surface = SurfaceView(this)
        surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (!stream.isOnPreview) stream.startPreview(surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                stream.getGlInterface().setPreviewResolution(width, height)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                if (stream.isOnPreview) stream.stopPreview()
            }
        })

        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            setPadding(24, 12, 24, 12)
            text = "Siap"
        }
        fun button(label: String, onClick: (Button) -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick(this) }
        }
        val row1 = LinearLayout(this).apply {
            addView(button("Balik") {
                camera.switchCamera()
                effects.mirrored = camera.getCameraFacing() == CameraHelper.Facing.FRONT
            })
            addView(button(effects.filterNames[0]) { b ->
                filter = (filter + 1) % effects.filterNames.size
                b.text = effects.filterNames[filter]
                lifecycleScope.launch { effects.setFilter(filter) }
            })
            addView(button("Halus: mati") { b ->
                smoothing = !smoothing
                b.text = if (smoothing) "Halus: nyala" else "Halus: mati"
                effects.setSmoothing(if (smoothing) 0.6f else 0f)
            })
        }
        // Efek wajah: gambar peta UV dibungkuskan ke wajah, otomatis pas di wajah siapa pun.
        // Model 3D (.glb) di ruang kepala standar: kacamata, topi, mahkota, telinga.
        fun asset(folder: String, name: String, ext: String) =
            FaceEffect(name, "file:///android_asset/$folder/${name.lowercase().replace(' ', '_')}.$ext")
        val faceEffects = listOf("Cat Wajah", "Topeng", "Kucing", "Kumis", "Pipi Merah", "Badut", "Tengkorak", "Bintang").map { asset("face_mesh", it, "png") } +
            listOf("Kacamata Sport", "Kacamata Hitam", "Helm Pilot", "Helm Scifi", "Topi Nelayan", "Masker Gas").map { asset("face_3d", it, "glb") }
        val row2 = HorizontalScrollView(this).apply {
            addView(LinearLayout(this@EffectsDemoActivity).apply {
                fun choose(effect: FaceEffect?) {
                    giftJob?.cancel()
                    giftJob = lifecycleScope.launch { effects.setFaceEffect(effect) }
                }
                addView(button("Tanpa") { choose(null) })
                faceEffects.forEach { effect -> addView(button(effect.name) { choose(effect) }) }
            })
        }
        val live = button("Mulai Live") { b ->
            if (stream.isStreaming) {
                stream.stopStream()
                b.text = "Mulai Live"
                status.text = "Siap"
            } else {
                status.text = "Menghubungkan…"
                stream.startStream(url)
                b.text = "Akhiri"
            }
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 96)
            addView(row1)
            addView(row2)
            addView(live)
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 120
                leftMargin = 32
            })
            addView(controls, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        })
    }

    override fun onDestroy() {
        if (::stream.isInitialized) {
            effects.release()
            stream.release()
        }
        super.onDestroy()
    }

    override fun onConnectionStarted(url: String) = Unit
    override fun onConnectionSuccess() {
        runOnUiThread { status.text = "LIVE" }
    }

    override fun onConnectionFailed(reason: String) {
        runOnUiThread {
            status.text = "Gagal: $reason"
            stream.stopStream()
        }
    }

    override fun onDisconnect() = Unit
    override fun onAuthError() = Unit
    override fun onAuthSuccess() = Unit
}
