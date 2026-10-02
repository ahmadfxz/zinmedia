plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.zinmedia.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zinmedia.sample"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":media:media-photoeditor"))
    implementation(project(":media:media-videoeditor"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
}
