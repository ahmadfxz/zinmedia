package com.zinmedia.composer

import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import android.content.ClipData
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.zinmedia.photoeditor.PhotoEditorContent
import com.zinmedia.photoeditor.PhotoEditorFileProvider
import com.zinmedia.photoeditor.PhotoEditorState
import com.zinmedia.photoeditor.ui.DiscardChangesDialog
import com.zinmedia.photoeditor.ui.EditorCaptionBar
import com.zinmedia.photoeditor.ui.EditorColors
import com.zinmedia.photoeditor.ui.theme.MarketplaceTheme
import com.zinmedia.videoeditor.VideoEditorFileProvider
import com.zinmedia.videoeditor.VideoEditorScreen
import com.zinmedia.videoeditor.VideoEditorSessions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Editor foto & video: satu media, atau beberapa sekaligus (maks. [MAX_ITEMS]). Geser untuk
 * berpindah media; tiap media diedit terpisah, keterangan dipakai bersama. Buat intent dengan [intent].
 *
 * Input: [EXTRA_MEDIA_URIS] (`ArrayList<Uri>`), atau `clipData` / `data`. [EXTRA_MAX_ITEMS] = batas
 * jumlah media; `1` = mode satu media (tanpa deretan thumbnail & tombol tambah).
 * Hasil: `clipData` + [EXTRA_RESULT_URIS] berisi semua URI hasil (urutan sama dengan input),
 * [EXTRA_RESULT_TYPES] ("image"/"video" per item), `data` = media pertama, dan extra `keterangan`.
 */
public class MediaComposerActivity : ComponentActivity() {

    private val items = mutableStateListOf<ComposerItem>()
    private var caption by mutableStateOf("")
    private var sending by mutableStateOf(false)
    private var sendingIndex by mutableIntStateOf(0)
    private var showDiscardDialog by mutableStateOf(false)
    /** Batas jumlah media untuk sesi ini (1 = mode satu media). */
    private var maxItems = MAX_ITEMS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hapus hasil edit lama dari sesi sebelumnya.
        lifecycleScope.launch(Dispatchers.IO) {
            PhotoEditorFileProvider.deleteOldOutputs(applicationContext)
            VideoEditorFileProvider.deleteOldOutputs(applicationContext)
        }

        maxItems = intent.getIntExtra(EXTRA_MAX_ITEMS, MAX_ITEMS).coerceIn(1, MAX_ITEMS)
        addMedia(readInputUris())
        if (items.isEmpty()) {
            Log.e(TAG, "MediaComposerActivity dibuka tanpa foto/video")
            finish()
            return
        }
        val recipientLabel = intent.getStringExtra(EXTRA_RECIPIENT_LABEL)
            ?: getString(com.zinmedia.photoeditor.R.string.zm_recipient_default)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
        )
        setContent {
            MarketplaceTheme {
                ComposerScreen(recipientLabel)
            }
        }
    }

    @Composable
    private fun ComposerScreen(recipientLabel: String) {
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        val pagerState = rememberPagerState { items.size }
        val videoToolActive = remember { mutableStateMapOf<String, Boolean>() }
        var bottomBarHeight by remember { mutableStateOf(0.dp) }

        // Muat semua foto di awal (maks. 5), agar lompat ke media mana pun tidak menampilkan layar kosong.
        LaunchedEffect(items.size) {
            items.forEach { it.photo?.load() }
        }

        val activeItem = items.getOrNull(pagerState.currentPage)
        val toolActive = activeItem?.photo?.isToolActive == true || videoToolActive[activeItem?.id] == true

        // Picker hanya dipakai bila boleh lebih dari satu media (PickMultiple butuh batas >= 2).
        val pickMedia = rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(maxItems.coerceAtLeast(2))
        ) { uris ->
            val before = items.size
            addMedia(uris)
            if (items.size > before) scope.launch { pagerState.animateScrollToPage(before) }
        }

        BackHandler(enabled = !sending) {
            if (activeItem?.photo?.handleBack() != true) requestClose()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EditorColors.Background)
        ) {
            HorizontalPager(
                state = pagerState,
                key = { items[it].id },
                userScrollEnabled = !toolActive && !sending,
                // Siapkan halaman tetangga lebih dulu (frame video/foto sudah termuat saat digeser).
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                Box(
                    Modifier
                        .fillMaxSize()
                        // Lapisan yang digeser ke tepi tidak boleh tampil di halaman tetangga.
                        .clipToBounds()
                        .stopBringIntoView()
                ) {
                    ComposerPage(
                        item = items[page],
                        active = page == pagerState.settledPage,
                        bottomBarHeight = bottomBarHeight,
                        onToolActiveChange = { active -> videoToolActive[items[page].id] = active },
                    )
                }
            }

            if (!toolActive) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { bottomBarHeight = with(density) { it.height.toDp() } },
                ) {
                    if (maxItems > 1) {
                        ThumbnailStrip(
                            items = items,
                            selected = pagerState.currentPage,
                            canAdd = items.size < maxItems && !sending,
                            onSelect = { scope.launch { pagerState.scrollToPage(it) } },
                            onRemove = { index -> items.removeAt(index) },
                            onAdd = {
                                pickMedia.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                        )
                    }
                    EditorCaptionBar(
                        caption = caption,
                        onCaptionChange = { caption = it },
                        recipientLabel = recipientLabel,
                        sendEnabled = !sending,
                        onSend = { send(pagerState::scrollToPage) { pagerState.settledPage } },
                    )
                }
            }

            if (sending) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .background(EditorColors.Dialog, RoundedCornerShape(20.dp))
                            .padding(horizontal = 28.dp, vertical = 24.dp),
                    ) {
                        CircularProgressIndicator(color = EditorColors.Accent)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.zm_composer_processing, sendingIndex + 1, items.size),
                            color = Color.White,
                            fontSize = 15.sp,
                        )
                    }
                }
            }

            if (showDiscardDialog) {
                DiscardChangesDialog(
                    onDiscard = { finish() },
                    onDismiss = { showDiscardDialog = false },
                )
            }
        }
    }

    /** Satu halaman pager: editor foto atau video untuk [item]. */
    @Composable
    private fun ComposerPage(
        item: ComposerItem,
        active: Boolean,
        bottomBarHeight: Dp,
        onToolActiveChange: (Boolean) -> Unit,
    ) {
        // Ruang kosong setinggi bar bawah milik layar ini (strip media + keterangan).
        val bottomSpace: @Composable () -> Unit = {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(bottomBarHeight)
            )
        }
        when (item.type) {
            MediaType.Image -> PhotoEditorContent(
                state = item.photo!!,
                onClose = ::requestClose,
                bottomContent = bottomSpace,
            )

            MediaType.Video -> VideoEditorScreen(
                videoUri = item.uri,
                onExportFinished = { _, _ -> },
                onDismiss = ::requestClose,
                sessionKey = item.id,
                active = active,
                standalone = false,
                bottomContent = bottomSpace,
                onToolActiveChange = onToolActiveChange,
            )
        }
    }

    private fun requestClose() {
        val hasChanges = caption.isNotBlank() || items.any { item ->
            item.photo?.hasEdits() ?: VideoEditorSessions.hasEdits(this, item.id)
        }
        if (hasChanges) showDiscardDialog = true else finish()
    }

    /**
     * Proses semua media berurutan. Foto yang diedit perlu tampil di layar agar bisa dirender,
     * jadi pager dipindah ke foto tersebut dulu.
     */
    private fun send(scrollToPage: suspend (Int) -> Unit, settledPage: () -> Int) {
        if (sending) return
        sending = true
        lifecycleScope.launch {
            try {
                val results = ArrayList<Uri>()
                val types = ArrayList<String>()
                items.forEachIndexed { index, item ->
                    sendingIndex = index
                    val uri = when (item.type) {
                        MediaType.Image -> {
                            val photo = item.photo!!
                            if (photo.hasEdits()) {
                                scrollToPage(index)
                                snapshotFlow { settledPage() }.first { it == index }
                                delay(RENDER_SETTLE_MS)
                                photo.exportToUri()
                            } else {
                                item.uri
                            }
                        }

                        MediaType.Video ->
                            VideoEditorSessions.exportIfEdited(this@MediaComposerActivity, item.id, this@MediaComposerActivity)
                                ?: item.uri
                    }
                    results += shareable(uri, item.type)
                    types += item.type.resultName
                }
                setResult(RESULT_OK, buildResult(results, types))
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "Gagal memproses media", e)
                Toast.makeText(this@MediaComposerActivity, R.string.zm_composer_failed, Toast.LENGTH_SHORT).show()
                sending = false
            }
        }
    }

    /**
     * URI `file://` tidak boleh keluar lewat Intent (FileUriExposedException), jadi media yang
     * tidak diedit dan berasal dari file disalin dulu ke folder FileProvider library.
     */
    private suspend fun shareable(uri: Uri, type: MediaType): Uri {
        if (uri.scheme != ContentResolver.SCHEME_FILE) return uri
        return withContext(Dispatchers.IO) {
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString()).ifEmpty { "bin" }
            val dir = when (type) {
                MediaType.Image -> PhotoEditorFileProvider.outputDir(this@MediaComposerActivity)
                MediaType.Video -> VideoEditorFileProvider.outputDir(this@MediaComposerActivity)
            }
            val copy = File.createTempFile("input_", ".$extension", dir)
            contentResolver.openInputStream(uri)!!.use { input -> copy.outputStream().use { input.copyTo(it) } }
            when (type) {
                MediaType.Image -> PhotoEditorFileProvider.uriFor(this@MediaComposerActivity, copy)
                MediaType.Video -> VideoEditorFileProvider.uriFor(this@MediaComposerActivity, copy)
            }
        }
    }

    private fun buildResult(uris: ArrayList<Uri>, types: ArrayList<String>): Intent {
        val clip = ClipData.newRawUri("media", uris.first())
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        return Intent().apply {
            data = uris.first()
            clipData = clip
            putParcelableArrayListExtra(EXTRA_RESULT_URIS, uris)
            putStringArrayListExtra(EXTRA_RESULT_TYPES, types)
            putExtra(EXTRA_CAPTION, caption)
            putExtra(EXTRA_MEDIA_TYPE, types.first())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun readInputUris(): List<Uri> {
        val fromExtra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_MEDIA_URIS, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_MEDIA_URIS)
        }
        if (!fromExtra.isNullOrEmpty()) return fromExtra
        intent.clipData?.let { clip -> return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }
        return listOfNotNull(intent.data)
    }

    private fun addMedia(uris: List<Uri>) {
        for (uri in uris) {
            if (items.size >= maxItems) break
            val type = mediaTypeOf(uri) ?: continue
            val photo = if (type == MediaType.Image) PhotoEditorState(this, uri) else null
            items += ComposerItem(uri, type, photo)
        }
    }

    public companion object {
        /** Hasil: keterangan yang diketik pengguna. */
        public const val EXTRA_CAPTION: String = "keterangan"

        /** Hasil: jenis media pertama, `"image"` atau `"video"` (lihat juga [EXTRA_RESULT_TYPES]). */
        public const val EXTRA_MEDIA_TYPE: String = "media_type"

        /** Maksimal media sekali kirim. */
        public const val MAX_ITEMS: Int = 5

        /** Input: batas jumlah media (`Int`, 1..[MAX_ITEMS], default [MAX_ITEMS]). `1` = mode satu media. */
        public const val EXTRA_MAX_ITEMS: String = "com.zinmedia.extra.MAX_ITEMS"

        /**
         * Intent untuk membuka editor.
         *
         * @param uris media awal (foto/video).
         * @param maxItems batas jumlah media; `1` = mode satu media, lebih dari 1 = daftar
         *   (pengguna bisa menambah media hingga batas ini).
         * @param recipientLabel label penerima di kiri tombol kirim; `null` = "Status".
         */
        @JvmStatic
        @JvmOverloads
        public fun intent(
            context: Context,
            uris: List<Uri>,
            maxItems: Int = MAX_ITEMS,
            recipientLabel: String? = null,
        ): Intent = Intent(context, MediaComposerActivity::class.java)
            .putParcelableArrayListExtra(EXTRA_MEDIA_URIS, ArrayList(uris))
            .putExtra(EXTRA_MAX_ITEMS, maxItems.coerceIn(1, MAX_ITEMS))
            .apply { if (recipientLabel != null) putExtra(EXTRA_RECIPIENT_LABEL, recipientLabel) }

        /** Input: `ArrayList<Uri>` foto/video. */
        public const val EXTRA_MEDIA_URIS: String = "com.zinmedia.extra.MEDIA_URIS"

        /** Label penerima di kiri tombol kirim, mis. "Status (Kontak)". Default: "Status". */
        public const val EXTRA_RECIPIENT_LABEL: String = "com.zinmedia.extra.RECIPIENT_LABEL"

        /** Hasil: `ArrayList<Uri>` media yang dikirim. */
        public const val EXTRA_RESULT_URIS: String = "com.zinmedia.extra.RESULT_URIS"

        /** Hasil: `ArrayList<String>` tipe tiap media ("image"/"video"). */
        public const val EXTRA_RESULT_TYPES: String = "com.zinmedia.extra.RESULT_TYPES"

        private const val RENDER_SETTLE_MS = 250L
        private const val TAG = "MediaComposerActivity"
    }
}
