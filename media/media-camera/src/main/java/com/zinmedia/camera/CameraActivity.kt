package com.zinmedia.camera

import android.Manifest
import android.content.Context
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
import androidx.compose.ui.graphics.luminance
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
import com.zinmedia.composer.AllowedMedia
import com.zinmedia.composer.MediaComposerActivity
import com.zinmedia.videoeditor.VideoEditorConfig
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
 * Input opsional: [EXTRA_MAX_ITEMS] (1 = satu media), [EXTRA_ALLOWED_MEDIA], [EXTRA_SHOW_CAPTION], [EXTRA_RECIPIENT_LABEL];
 * buat dengan [intent]. Hasil (`RESULT_OK`) sama dengan [MediaComposerActivity]:
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

    /** Batas jumlah media (1 = satu media), dari [EXTRA_MAX_ITEMS]. */
    private var maxItems = MediaComposerActivity.MAX_ITEMS
    /** Jenis media yang boleh dipakai, dari [EXTRA_ALLOWED_MEDIA]. */
    private var allowedMedia = AllowedMedia.All

    /** Galeri: pilih satu media, atau beberapa sesuai [maxItems]. Didaftarkan di onCreate. */
    private lateinit var openGallery: () -> Unit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
        )
        lifecycleScope.launch(Dispatchers.IO) { deleteOldCameraFiles(applicationContext) }

        maxItems = intent.getIntExtra(EXTRA_MAX_ITEMS, MediaComposerActivity.MAX_ITEMS)
            .coerceIn(1, MediaComposerActivity.MAX_ITEMS)
        allowedMedia = AllowedMedia.from(intent)
        val galleryRequest = PickVisualMediaRequest(
            when (allowedMedia) {
                AllowedMedia.All -> ActivityResultContracts.PickVisualMedia.ImageAndVideo
                AllowedMedia.Image -> ActivityResultContracts.PickVisualMedia.ImageOnly
                AllowedMedia.Video -> ActivityResultContracts.PickVisualMedia.VideoOnly
            }
        )
        openGallery = if (maxItems == 1) {
            val single = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
                if (uri != null) openComposer(listOf(uri))
            }
            val launch: () -> Unit = { single.launch(galleryRequest) }
            launch
        } else {
            val multiple = registerForActivityResult(
                ActivityResultContracts.PickMultipleVisualMedia(maxItems)
            ) { uris: List<Uri> ->
                if (uris.isNotEmpty()) openComposer(uris)
            }
            val launch: () -> Unit = { multiple.launch(galleryRequest) }
            launch
        }

        cameraGranted = hasPermission(Manifest.permission.CAMERA)
        if (!cameraGranted || (needsAudio && !hasPermission(Manifest.permission.RECORD_AUDIO))) requestPermissions()

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
                allowedMedia = allowedMedia,
                maxPhotos = maxItems,
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
            onGallery = { openGallery() },
        )
    }

    private fun openComposer(uris: List<Uri>) {
        composerLauncher.launch(
            MediaComposerActivity.intent(
                context = this,
                uris = uris,
                maxItems = maxItems,
                recipientLabel = intent.getStringExtra(EXTRA_RECIPIENT_LABEL),
                showCaption = intent.getBooleanExtra(EXTRA_SHOW_CAPTION, true),
                allowedMedia = allowedMedia,
            )
        )
    }

    /** Mikrofon hanya dibutuhkan bila video boleh direkam. */
    private val needsAudio: Boolean get() = allowedMedia != AllowedMedia.Image

    private fun requestPermissions() {
        val permissions = if (needsAudio) {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        } else {
            arrayOf(Manifest.permission.CAMERA)
        }
        permissionLauncher.launch(permissions)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    public companion object {
        /** Input: label penerima di editor, mis. "Status (Kontak)". */
        public const val EXTRA_RECIPIENT_LABEL: String = MediaComposerActivity.EXTRA_RECIPIENT_LABEL

        /** Input: batas jumlah media (1..[MediaComposerActivity.MAX_ITEMS]); `1` = satu media. */
        public const val EXTRA_MAX_ITEMS: String = MediaComposerActivity.EXTRA_MAX_ITEMS

        /** Input: `false` = editor tanpa kolom keterangan. Default `true`. */
        public const val EXTRA_SHOW_CAPTION: String = MediaComposerActivity.EXTRA_SHOW_CAPTION

        /** Input: nama [AllowedMedia]; tidak diisi = foto & video. */
        public const val EXTRA_ALLOWED_MEDIA: String = MediaComposerActivity.EXTRA_ALLOWED_MEDIA

        /**
         * Intent untuk membuka kamera.
         *
         * @param maxItems batas jumlah media di galeri & editor; `1` = satu media.
         * @param recipientLabel label penerima di editor; `null` = "Status".
         * @param showCaption `false` = editor tanpa kolom keterangan.
         * @param allowedMedia foto saja (mode Foto), video saja (mode 15d/1m/30d), atau keduanya (default).
         */
        @JvmStatic
        @JvmOverloads
        public fun intent(
            context: Context,
            maxItems: Int = MediaComposerActivity.MAX_ITEMS,
            recipientLabel: String? = null,
            showCaption: Boolean = true,
            allowedMedia: AllowedMedia = AllowedMedia.All,
        ): Intent = Intent(context, CameraActivity::class.java)
            .putExtra(EXTRA_MAX_ITEMS, maxItems.coerceIn(1, MediaComposerActivity.MAX_ITEMS))
            .putExtra(EXTRA_SHOW_CAPTION, showCaption)
            .putExtra(EXTRA_ALLOWED_MEDIA, allowedMedia.name)
            .apply { if (recipientLabel != null) putExtra(EXTRA_RECIPIENT_LABEL, recipientLabel) }
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
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(VideoEditorConfig.accentColor),
                contentColor = if (Color(VideoEditorConfig.accentColor).luminance() > 0.6f) Color.Black else Color.White,
            ),
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
