# zinmedia

Library Android (Jetpack Compose) untuk mengedit foto dan video sebelum diunggah.

| Modul | Isi |
|---|---|
| `media-camera` | Kamera foto & video ala aplikasi video pendek: filter & efek real-time, penghalus kulit, rekam bersegmen, kecepatan, timer, flash, zoom, galeri (pilih banyak): `com.zinmedia.camera.CameraActivity`. Hasilnya dibuka di editor gabungan. Sudah termasuk semua modul di bawah. |
| `media-composer` | Editor beberapa foto & video sekaligus (maks. 5, geser antar media): `com.zinmedia.composer.MediaComposerActivity`. Sudah termasuk dua modul di bawah. |
| `media-photoeditor` | Editor foto: `com.zinmedia.photoeditor.ImageEditorActivity` |
| `media-videoeditor` | Editor video: `com.zinmedia.videoeditor.VideoEditorActivity` (ekspor lewat Media3 Transformer) |

Persyaratan: `minSdk` 23, `compileSdk` 36 atau lebih baru, Kotlin 2.3 atau lebih baru.

## Instalasi

Library dibangun oleh [JitPack](https://jitpack.io/#ahmadfxz/zinmedia) dari tag di repo ini.

**settings.gradle.kts**

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

**gradle/libs.versions.toml**

```toml
[versions]
zinmedia = "4.6.0"

[libraries]
zinmedia-photoeditor = { module = "com.github.ahmadfxz.zinmedia:media-photoeditor", version.ref = "zinmedia" }
zinmedia-videoeditor = { module = "com.github.ahmadfxz.zinmedia:media-videoeditor", version.ref = "zinmedia" }
# atau, untuk editor gabungan (sudah termasuk foto & video):
zinmedia-composer = { module = "com.github.ahmadfxz.zinmedia:media-composer", version.ref = "zinmedia" }
# atau, kamera + galeri + editor gabungan (semuanya):
zinmedia-camera = { module = "com.github.ahmadfxz.zinmedia:media-camera", version.ref = "zinmedia" }
```

**app/build.gradle.kts**

```kotlin
dependencies {
    implementation(libs.zinmedia.photoeditor)
    implementation(libs.zinmedia.videoeditor)
}
```

## Setup di aplikasi

Tidak perlu menambahkan apa pun ke `AndroidManifest.xml`. Activity editor dan FileProvider sudah didaftarkan oleh library, lalu otomatis digabung ke manifest aplikasi:

| Modul | Activity | FileProvider authority |
|---|---|---|
| `media-photoeditor` | `com.zinmedia.photoeditor.ImageEditorActivity` | `${applicationId}.zinmedia.photoeditor.fileprovider` |
| `media-videoeditor` | `com.zinmedia.videoeditor.VideoEditorActivity` | `${applicationId}.zinmedia.videoeditor.fileprovider` |

FileProvider library hanya membuka folder `cache/zinmedia/<modul>/`, tempat hasil edit disimpan. Provider ini terpisah dari FileProvider milik aplikasi, jadi tidak ada konflik authority atau `file_paths`.

Untuk mengubah atribut activity (misalnya theme), deklarasikan ulang activity tersebut di manifest aplikasi dengan `tools:replace`:

```xml
<activity
    android:name="com.zinmedia.photoeditor.ImageEditorActivity"
    android:theme="@style/ThemeAplikasi"
    tools:replace="android:theme" />
```

### Konten & warna: stiker, emoji, filter, dan warna utama

Library **tidak** membawa daftar stiker atau filter video. Aplikasi yang menentukannya, sekali saat aplikasi mulai:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Hanya pakai media-photoeditor / media-videoeditor:
        PhotoEditorConfig.stickers = listOf("https://cdn.contoh.com/stiker/1.png")
        VideoEditorConfig.stickers = PhotoEditorConfig.stickers
        VideoEditorConfig.filters = listOf(
            VideoFilterOption("Vintage", cubeUrl = "https://…/vintage.cube", thumbnailUrl = "https://…/vintage.jpg"),
        )

        PhotoEditorConfig.accentColor = 0xFF1E88E5.toInt()
        VideoEditorConfig.accentColor = PhotoEditorConfig.accentColor

        // Atau, bila memakai media-composer / media-camera, sekaligus:
        MediaComposer.configure(stickers = …, videoFilters = …, accentColor = 0xFF1E88E5.toInt())
    }
}
```

| Pengaturan | Isi | Default |
|---|---|---|
| `stickers` | URL/URI gambar (apa pun yang bisa dimuat Coil, termasuk `file:///android_asset/…`) | kosong → tab Stiker disembunyikan |
| `emojis` | daftar emoji | emoji Unicode bawaan |
| `PhotoEditorConfig.filters` | `PhotoFilterOption(filter, label)`: filter foto bawaan yang ditampilkan dan urutannya | semua filter |
| `VideoEditorConfig.filters` | `VideoFilterOption(name, cubeUrl, thumbnailUrl)`: LUT 3D `.cube` | kosong → filter video disembunyikan |
| `accentColor` | Warna utama (ARGB): tombol kirim/ekspor, tombol dialog konfirmasi (mis. keluar), kursor, indikator proses, serta tombol Selesai & izin di kamera. Ikon/teks di atasnya otomatis putih atau hitam sesuai terang warna. Tombol & progress **rekam** di kamera tetap merah. | hijau `0xFF21C063` |

Pastikan lisensi stiker/LUT yang dipakai mengizinkan penggunaannya (mis. atribusi).

### Font

Font untuk fitur teks **tidak** dibundel di library. Taruh file berikut di `app/src/main/assets/` dengan nama persis seperti ini:

```
inter_18pt_bold.ttf
monofett_regular.ttf
caveat_regular.ttf
pacifico_regular.ttf
rampartone_regular.ttf
karla_bold.ttf
```

Kalau ada font yang tidak tersedia, editor memakai font sistem sebagai gantinya dan tidak crash.

## Pemakaian

Kedua editor dibuka dengan `Intent` berisi URI media di `data`, lalu hasilnya diterima lewat `StartActivityForResult`.

```kotlin
val editorLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
        val editedUri = result.data?.data                          // content:// hasil edit
        val keterangan = result.data?.getStringExtra(ImageEditorActivity.EXTRA_CAPTION)
        val mediaType = result.data?.getStringExtra(ImageEditorActivity.EXTRA_MEDIA_TYPE) // "image"/"video"
    }
}

// Foto
editorLauncher.launch(
    Intent(context, ImageEditorActivity::class.java).apply { data = imageUri }
)

// Video
editorLauncher.launch(
    Intent(context, VideoEditorActivity::class.java).apply { data = videoUri }
)
```

Kalau pengguna menutup editor tanpa menyimpan, atau URI tidak diberikan / gambar gagal dimuat, `resultCode` bernilai `RESULT_CANCELED`.

Label penerima di kiri tombol kirim (default `Status`) bisa diganti:

```kotlin
Intent(context, ImageEditorActivity::class.java).apply {
    data = imageUri
    putExtra(ImageEditorActivity.EXTRA_RECIPIENT_LABEL, "Status (Kontak)")
}
```

Kolom keterangan bisa disembunyikan bila aplikasi tidak memakainya:

```kotlin
Intent(context, ImageEditorActivity::class.java).apply {
    data = imageUri
    putExtra(ImageEditorActivity.EXTRA_SHOW_CAPTION, false)
}
```

`VideoEditorActivity.EXTRA_RECIPIENT_LABEL` dan `VideoEditorActivity.EXTRA_SHOW_CAPTION` berlaku sama untuk editor video.

## Hasil & penyimpanan

- Foto disimpan sebagai **JPEG (kualitas 90)** di resolusi asli foto (tidak bergantung ukuran layar), sisi terpanjang maksimal 2048 px; teks ikut dirender di resolusi foto agar tetap tajam. Video disimpan sebagai **MP4** dengan sisi panjang maksimal 1280 px dan orientasi mengikuti video asli; ukurannya dihitung dari resolusi asli video, dan teks/coretan dirender di resolusi hasil, jadi tidak bergantung ukuran layar.
- **Lapisan di luar foto/video** (teks, stiker, emoji, coretan): bila semua lapisan ada di dalam frame, hasil seukuran foto/video asli. Bila ada lapisan yang keluar frame, hasil diperluas memenuhi seluruh area editor (batas maksimal) dengan latar hitam, sehingga sama dengan yang terlihat saat mengedit. Area editor adalah kotak **9:16** di tengah layar (di luar status bar & navigation bar), jadi hasil yang diperluas selalu 9:16 (video 720×1280). Lapisan yang keluar dari area editor terpotong, baik di layar maupun di hasil.
- File hasil ada di cache aplikasi (`cache/zinmedia/…`) dan dibagikan lewat FileProvider milik library. File yang lebih tua dari 24 jam dihapus otomatis setiap kali editor dibuka, jadi segera salin atau unggah hasilnya.
- Kunci extra hasil tersedia sebagai konstanta: `EXTRA_CAPTION`, `EXTRA_MEDIA_TYPE` (dan untuk editor gabungan `EXTRA_RESULT_URIS`, `EXTRA_RESULT_TYPES`).

## Aplikasi contoh

Modul `sample` (tidak ikut dipublish) membuka editor dengan foto/video contoh atau dari galeri:

```bash
./gradlew :sample:installDebug
adb shell am start -n com.zinmedia.sample/.MainActivity --es open camera  # atau: photo, video, mixed
```

## Editor gabungan (satu atau beberapa media)

`MediaComposerActivity` bisa dipakai untuk **satu media** atau **daftar** hingga 5 foto/video, diatur dengan `maxItems`:

| `maxItems` | Tampilan |
|---|---|
| `1` | Mode satu media: tanpa deretan thumbnail dan tanpa tombol tambah (+). |
| `2`–`5` (default `5`) | Mode daftar: pengguna bisa berpindah, menghapus, dan menambah media hingga batas ini. |

```kotlin
// Satu media
MediaComposerActivity.intent(context, listOf(uri), maxItems = 1)
// Daftar (maks. 5)
MediaComposerActivity.intent(context, pickedUris)
```

### Jenis media: foto saja, video saja, atau keduanya

Bila tidak diatur, foto dan video sama-sama didukung. Aplikasi bisa membatasi dengan `allowedMedia`:

| `allowedMedia` | Editor gabungan | Kamera |
|---|---|---|
| `AllowedMedia.All` (default) | foto & video | mode 15d, 1m, 30d, Foto |
| `AllowedMedia.Video` | hanya video; picker (+) hanya video | hanya 15d, 1m & 30d; galeri hanya video |
| `AllowedMedia.Image` | hanya foto; picker (+) hanya foto | hanya mode Foto (tanpa izin mikrofon); galeri hanya foto |

```kotlin
MediaComposerActivity.intent(context, uris, allowedMedia = AllowedMedia.Video)
CameraActivity.intent(context, allowedMedia = AllowedMedia.Image)
```

Media yang jenisnya tidak diizinkan dilewati. Bila tidak ada media yang tersisa, editor ditutup dengan `RESULT_CANCELED`.

### Tanpa keterangan

Kolom keterangan bisa disembunyikan bila aplikasi tidak memakainya (label penerima & tombol kirim tetap ada, hasil `EXTRA_CAPTION` kosong):

```kotlin
MediaComposerActivity.intent(context, uris, showCaption = false)
CameraActivity.intent(context, showCaption = false)
// Editor tunggal: extra EXTRA_SHOW_CAPTION = false pada ImageEditorActivity / VideoEditorActivity
```

Dalam mode daftar, composer menerima hingga 5 foto/video sekaligus. Media digeser kiri-kanan dan masing-masing diedit terpisah. Deretan thumbnail di bawah dipakai untuk berpindah, menghapus (×), atau menambah media (+). Keterangan dipakai bersama untuk semua media.

```kotlin
val composerLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
        val data = result.data ?: return@rememberLauncherForActivityResult
        val uris = IntentCompat.getParcelableArrayListExtra(
            data, MediaComposerActivity.EXTRA_RESULT_URIS, Uri::class.java
        ).orEmpty()
        val types = data.getStringArrayListExtra(MediaComposerActivity.EXTRA_RESULT_TYPES).orEmpty() // "image"/"video"
        val keterangan = data.getStringExtra(MediaComposerActivity.EXTRA_CAPTION)
    }
}

composerLauncher.launch(MediaComposerActivity.intent(context, pickedUris))
```

Urutan hasil sama dengan urutan media. Media yang tidak diedit dikembalikan dengan URI aslinya, tanpa diproses ulang. Hasil juga tersedia lewat `clipData`.

## Kamera

`CameraActivity` adalah kamera layar penuh (potret, preview 9:16):

- **Rekam bersegmen**: ketuk rana untuk mulai/berhenti, atau tahan selama merekam (geser jari ke atas saat menahan untuk zoom). Progress bar menandai tiap klip; klip terakhir bisa dihapus. Pilihan mode: 15d, 1m (default), 30d, atau Foto.
- **Kecepatan** 0.3x, 0.5x, 1x, 2x, 3x per klip. Klip digabung (dan kecepatannya diterapkan) saat menekan Selesai.
- **Filter real-time** (GPU) yang terlihat di preview dan ikut terekam: filter bawaan plus filter LUT `.cube` dari `MediaComposer.configure(videoFilters = …)`. Geser kiri/kanan di preview untuk ganti filter.
- **Halus**: penghalus kulit sederhana.
- **Kontrol kamera**: balik depan/belakang (juga ketuk 2× di preview), flash (senter di kamera belakang, layar putih di kamera depan), cubit untuk zoom, ketuk untuk fokus, timer 3/10 detik, grid.
- **Foto beruntun**: di mode Foto, tiap jepretan ditampung dulu sampai batas `maxItems`. Foto tampil sebagai tumpukan kartu miring (menggantikan tombol galeri) dengan jumlahnya; ketuk tumpukan untuk membuka deretan foto (hapus dengan ×, jumlah mis. `2/5`). Tombol Selesai membuka semuanya di editor; saat batas tercapai editor terbuka otomatis. Dengan `maxItems = 1`, foto langsung dibuka di editor.
- **Galeri**: pilih hingga `maxItems` foto/video sekaligus (default 5).

Hasil foto/video langsung dibuka di `MediaComposerActivity`. Kembali dari editor = kembali ke kamera (klip tetap ada). Setelah dikirim, `CameraActivity` selesai dengan hasil yang **sama persis** dengan editor gabungan (`EXTRA_RESULT_URIS`, `EXTRA_RESULT_TYPES`, `EXTRA_CAPTION`, `clipData`, `data`).

```kotlin
cameraLauncher.launch(CameraActivity.intent(context))                // galeri & editor: daftar (maks. 5)
cameraLauncher.launch(CameraActivity.intent(context, maxItems = 1))  // satu media
```

Izin `CAMERA` dan `RECORD_AUDIO` sudah dideklarasikan library dan diminta oleh `CameraActivity`. Tanpa izin mikrofon, video direkam tanpa suara.

## Rilis versi baru

1. Commit dan push perubahan ke `main`.
2. Buat tag lalu push:
   ```bash
   git tag 1.0.1
   git push origin 1.0.1
   ```
3. Buka `https://jitpack.io/#ahmadfxz/zinmedia` (atau langsung pakai versi barunya). Build pertama butuh beberapa menit.
4. Di aplikasi, naikkan `zinmedia = "1.0.1"` di `libs.versions.toml`.

Untuk mengetes publish secara lokal:

```bash
./gradlew publishToMavenLocal -Pversion=0.0.0-local
```
