# zinmedia

Library Android (Jetpack Compose) untuk mengedit foto dan video sebelum diunggah.

| Modul | Isi |
|---|---|
| `media-composer` | Editor beberapa foto & video sekaligus (maks. 5, geser antar media): `com.zinmedia.composer.MediaComposerActivity`. Sudah termasuk dua modul di bawah. |
| `media-photoeditor` | Editor foto: `com.zinmedia.photoeditor.ImageEditorActivity` |
| `media-videoeditor` | Editor video: `com.zinmedia.videoeditor.VideoEditorActivity` (ekspor lewat Media3 Transformer) |

Persyaratan: `minSdk` 23, `compileSdk` 37 atau lebih baru, Kotlin 2.3 atau lebih baru.

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
zinmedia = "4.1.0"

[libraries]
zinmedia-photoeditor = { module = "com.github.ahmadfxz.zinmedia:media-photoeditor", version.ref = "zinmedia" }
zinmedia-videoeditor = { module = "com.github.ahmadfxz.zinmedia:media-videoeditor", version.ref = "zinmedia" }
# atau, untuk editor gabungan (sudah termasuk foto & video):
zinmedia-composer = { module = "com.github.ahmadfxz.zinmedia:media-composer", version.ref = "zinmedia" }
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

### Konten: stiker, emoji, dan filter

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

        // Atau, bila memakai media-composer, sekaligus:
        MediaComposer.configure(stickers = …, videoFilters = …)
    }
}
```

| Pengaturan | Isi | Default |
|---|---|---|
| `stickers` | URL/URI gambar (apa pun yang bisa dimuat Coil, termasuk `file:///android_asset/…`) | kosong → tab Stiker disembunyikan |
| `emojis` | daftar emoji | emoji Unicode bawaan |
| `PhotoEditorConfig.filters` | `PhotoFilterOption(filter, label)`: filter foto bawaan yang ditampilkan dan urutannya | semua filter |
| `VideoEditorConfig.filters` | `VideoFilterOption(name, cubeUrl, thumbnailUrl)`: LUT 3D `.cube` | kosong → filter video disembunyikan |

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

`VideoEditorActivity.EXTRA_RECIPIENT_LABEL` berlaku sama untuk editor video.

## Hasil & penyimpanan

- Foto disimpan sebagai **JPEG (kualitas 90)**, video sebagai **MP4** dengan sisi panjang maksimal 1280 px dan orientasi mengikuti video asli.
- File hasil ada di cache aplikasi (`cache/zinmedia/…`) dan dibagikan lewat FileProvider milik library. File yang lebih tua dari 24 jam dihapus otomatis setiap kali editor dibuka, jadi segera salin atau unggah hasilnya.
- Kunci extra hasil tersedia sebagai konstanta: `EXTRA_CAPTION`, `EXTRA_MEDIA_TYPE` (dan untuk editor gabungan `EXTRA_RESULT_URIS`, `EXTRA_RESULT_TYPES`).

## Aplikasi contoh

Modul `sample` (tidak ikut dipublish) membuka editor dengan foto/video contoh atau dari galeri:

```bash
./gradlew :sample:installDebug
adb shell am start -n com.zinmedia.sample/.MainActivity --es open photo   # atau: video
```

## Editor gabungan (beberapa media)

`MediaComposerActivity` menerima hingga 5 foto/video sekaligus. Media digeser kiri-kanan dan masing-masing diedit terpisah. Deretan thumbnail di bawah dipakai untuk berpindah, menghapus (×), atau menambah media (+). Keterangan dipakai bersama untuk semua media.

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

composerLauncher.launch(
    Intent(context, MediaComposerActivity::class.java)
        .putParcelableArrayListExtra(MediaComposerActivity.EXTRA_MEDIA_URIS, ArrayList(pickedUris))
)
```

Urutan hasil sama dengan urutan media. Media yang tidak diedit dikembalikan dengan URI aslinya, tanpa diproses ulang. Hasil juga tersedia lewat `clipData`.

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
