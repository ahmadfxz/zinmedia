package com.zinmedia.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import com.zinmedia.camera.ui.CameraScreen
import com.zinmedia.composer.MediaComposerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Kamera foto & video: filter & efek real-time, penghalus kulit, rekam bersegmen (ketuk/tahan),
 * kecepatan 0.3x–3x, timer, flash, zoom, fokus, grid, dan galeri (pilih hingga
 * [MediaComposerActivity.MAX_ITEMS] media). Hasilnya dibuka di [MediaComposerActivity]; kembali dari
 * editor = kembali ke kamera.
 *
 * Input opsional: [EXTRA_RECIPIENT_LABEL]. Hasil (`RESULT_OK`) sama dengan [MediaComposerActivity]:
 * `clipData`, [MediaComposerActivity.EXTRA_RESULT_URIS], [MediaComposerActivity.EXTRA_RESULT_TYPES],
 * `data` (media pertama), dan [MediaComposerActivity.EXTRA_CAPTION].
 */
public class CameraActivity : ComponentActivity() {

    private var cameraGranted by mutableStateOf(false)
    /** Izin pernah ditolak permanen: arahkan ke pengaturan. */
    private var askedOnce = false
    private var state: CameraState? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            askedOnce = true
            cameraGranted = hasPermission(Manifest.permission.CAMERA)
        }

    private val composerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                state?.reset()
                setResult(RESULT_OK, result.data)
                finish()
            }
            // Dibatalkan: tetap di kamera (klip yang sudah direkam masih ada).
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MediaComposerActivity.MAX_ITEMS)) { uris ->
            if (uris.isNotEmpty()) openComposer(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
        )
        lifecycleScope.launch(Dispatchers.IO) { deleteOldCameraFiles(applicationContext) }

        cameraGranted = hasPermission(Manifest.permission.CAMERA)
        if (!cameraGranted || !hasPermission(Manifest.permission.RECORD_AUDIO)) requestPermissions()

        setContent {
            if (cameraGranted) {
                CameraContent()
            } else {
                PermissionScreen(
                    permanentlyDenied = askedOnce &&
                        !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA),
                    onGrant = ::requestPermissions,
                    onOpenSettings = {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
                        )
                    },
                    onClose = ::finish,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Kembali dari pengaturan setelah izin diberikan.
        if (!cameraGranted) cameraGranted = hasPermission(Manifest.permission.CAMERA)
    }

    @Composable
    private fun CameraContent() {
        val scope = rememberCoroutineScope()
        val previewView = remember {
            PreviewView(this).apply {
                // TextureView: frame terfilter bisa diambil sebagai foto (PreviewView.getBitmap).
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }
        val cameraState = remember {
            CameraState(
                context = this,
                session = CameraSession(this, this, previewView),
                scope = scope,
                audioEnabled = { hasPermission(Manifest.permission.RECORD_AUDIO) },
                onCaptured = ::openComposer,
            ).also { state = it }
        }
        LaunchedEffect(Unit) {
            // ViewPort (bingkai 9:16) baru tersedia setelah preview diukur.
            suspendCancellableCoroutine { cont -> previewView.doOnLayout { cont.resume(Unit) } }
            cameraState.session.start()
        }
        DisposableEffect(Unit) {
            onDispose {
                cameraState.release()
                cameraState.session.release()
                state = null
            }
        }
        BackHandler(enabled = cameraState.isRecording || cameraState.countdown != null) {
            cameraState.onShutterHoldEnd()
        }
        CameraScreen(
            state = cameraState,
            onClose = ::finish,
            onGallery = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
        )
    }

    private fun openComposer(uris: List<Uri>) {
        val intent = Intent(this, MediaComposerActivity::class.java)
            .putParcelableArrayListExtra(MediaComposerActivity.EXTRA_MEDIA_URIS, ArrayList(uris))
        // Label penerima dari pemanggil diteruskan ke editor.
        this.intent.getStringExtra(EXTRA_RECIPIENT_LABEL)?.let {
            intent.putExtra(MediaComposerActivity.EXTRA_RECIPIENT_LABEL, it)
        }
        composerLauncher.launch(intent)
    }

    private fun requestPermissions() {
        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    public companion object {
        /** Input: label penerima di editor, mis. "Status (Kontak)". */
        public const val EXTRA_RECIPIENT_LABEL: String = MediaComposerActivity.EXTRA_RECIPIENT_LABEL
    }
}

@Composable
private fun PermissionScreen(
    permanentlyDenied: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.zm_camera_permission_title),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.zm_camera_permission_body),
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = if (permanentlyDenied) onOpenSettings else onGrant,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55)),
        ) {
            Text(
                stringResource(
                    if (permanentlyDenied) R.string.zm_camera_permission_settings else R.string.zm_camera_permission_grant
                ),
            )
        }
        TextButton(onClick = onClose) { Text(stringResource(R.string.zm_camera_close), color = Color.White) }
    }
}
