package com.zinmedia.sample

import android.Manifest
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zinmedia.camera.FaceAnchor
import com.zinmedia.camera.FaceEffect
import com.zinmedia.live.LiveCamera
import com.zinmedia.live.LivePhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Contoh layar live buatan aplikasi: zinmedia hanya menyediakan isi kamera ([LiveCamera.Preview])
 * dan fungsi; semua tombol (termasuk "gift" yang memasang efek) milik aplikasi.
 */
class LiveDemoActivity : ComponentActivity() {

    private lateinit var live: LiveCamera
    private var granted by mutableStateOf(false)

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = it.values.all { ok -> ok }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        live = LiveCamera(this, this)
        val url = intent.getStringExtra("url") ?: "rtmp://127.0.0.1:1935/live/test"
        permissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        setContent { if (granted) LiveDemo(url) }
    }

    override fun onDestroy() {
        live.release()
        super.onDestroy()
    }

    @Composable
    private fun LiveDemo(url: String) {
        val state by live.state.collectAsState()
        val scope = rememberCoroutineScope()
        var muted by remember { mutableStateOf(false) }
        var filter by remember { mutableIntStateOf(0) }
        var giftJob by remember { mutableStateOf<Job?>(null) }

        // Unduh model wajah di awal agar gift pertama langsung tampil.
        LaunchedEffect(Unit) { runCatching { live.prepareFaceEffects() } }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            live.Preview(
                Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
            )

            // Status (dari LiveState).
            Text(
                text = statusText(state.phase, state.liveSinceMs, state.errorMessage),
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .background(if (state.phase == LivePhase.Live) Color(0xFFFE2C55) else Color(0x99000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { live.flipCamera() }) { Text("Balik", color = Color.White) }
                    OutlinedButton(onClick = { muted = !muted; live.setMicMuted(muted) }) {
                        Text(if (muted) "Mic: mati" else "Mic: nyala", color = Color.White)
                    }
                    OutlinedButton(onClick = {
                        filter = (filter + 1) % live.filterNames.size
                        scope.launch { live.setFilter(filter) }
                    }) { Text(live.filterNames[filter], color = Color.White) }
                }
                // Simulasi gift dari penonton: pasang kacamata 10 detik.
                OutlinedButton(onClick = {
                    giftJob?.cancel()
                    giftJob = scope.launch {
                        live.setFaceEffect(FaceEffect("Kacamata", "file:///android_asset/face/glasses.png", FaceAnchor.Eyes))
                        delay(10_000)
                        live.setFaceEffect(null)
                    }
                }) { Text("Gift kacamata (10 detik)", color = Color.White) }
                Button(
                    onClick = { if (state.isActive) live.stop() else live.start(url) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (state.isActive) Color.White else Color(0xFFFE2C55)),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.isActive) "Akhiri" else "Mulai Live", color = if (state.isActive) Color.Black else Color.White) }
            }
        }
    }

    @Composable
    private fun statusText(phase: LivePhase, since: Long, error: String?): String {
        var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
        LaunchedEffect(phase) {
            while (phase == LivePhase.Live) {
                now = SystemClock.elapsedRealtime()
                delay(500)
            }
        }
        return when (phase) {
            LivePhase.Idle -> "Siap"
            LivePhase.Connecting -> "Menghubungkan…"
            LivePhase.Reconnecting -> "Menyambung ulang…"
            LivePhase.Error -> error ?: "Galat"
            LivePhase.Live -> {
                val s = ((now - since) / 1000).coerceAtLeast(0)
                "LIVE %02d:%02d".format(s / 60, s % 60)
            }
        }
    }
}
