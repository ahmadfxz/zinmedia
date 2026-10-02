# zinmedia

Library Android (Jetpack Compose) untuk mengedit foto dan video sebelum diunggah.

| Modul | Isi |
|---|---|
| `media-photoeditor` | Editor foto: `com.zinmedia.photoeditor.ImageEditorActivity` |
| `media-videoeditor` | Editor video: `com.zinmedia.videoeditor.VideoEditorActivity` (ekspor lewat Media3 Transformer) |

Persyaratan: `minSdk` 23, `compileSdk` 36 atau lebih baru.

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
zinmedia = "2.0.0"

[libraries]
zinmedia-photoeditor = { module = "com.github.ahmadfxz.zinmedia:media-photoeditor", version.ref = "zinmedia" }
zinmedia-videoeditor = { module = "com.github.ahmadfxz.zinmedia:media-videoeditor", version.ref = "zinmedia" }
```

**app/build.gradle.kts**

```kotlin
dependencies {
    implementation(libs.zinmedia.photoeditor)
    implementation(libs.zinmedia.videoeditor)
}
```

## Setup di aplikasi

Library ini tidak mendaftarkan activity atau FileProvider sendiri. Aplikasi harus menambahkannya.

### 1. AndroidManifest.xml

```xml
<application ...>

    <activity
        android:name="com.zinmedia.photoeditor.ImageEditorActivity"
        android:hardwareAccelerated="true"
        android:windowSoftInputMode="adjustResize"
        android:theme="@style/Theme.AppCompat.DayNight.NoActionBar" />

    <activity
        android:name="com.zinmedia.videoeditor.VideoEditorActivity"
        android:hardwareAccelerated="true"
        android:windowSoftInputMode="adjustResize"
        android:theme="@style/Theme.AppCompat.DayNight.NoActionBar" />

    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.fileprovider"
        android:grantUriPermissions="true"
        android:exported="false">
        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/file_paths" />
    </provider>

</application>
```

Authority **harus** `${applicationId}.fileprovider`, karena hasil edit dibagikan lewat authority itu. `@xml/file_paths` sudah disediakan oleh `media-videoeditor` (folder cache). Kalau aplikasi punya `file_paths.xml` sendiri, pastikan di dalamnya ada `<cache-path name="cache" path="." />`.

### 2. Font

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
        val keterangan = result.data?.getStringExtra("keterangan") // caption dari editor
        val mediaType = result.data?.getStringExtra("media_type")  // "image" atau "video"
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
