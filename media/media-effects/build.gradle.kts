plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.zinmedia.effects"
    compileSdk = 36
    // Semua resource library berawalan zm_ agar tidak bentrok dengan resource aplikasi.
    resourcePrefix = "zm_"

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    // API publik harus ditandai eksplisit; selain itu internal.
    explicitApi()
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Filter siaran (ZinEffects) dibangun di atas RootEncoder milik aplikasi; modul lain
    // (mis. media-camera) memakai inti efek tanpa RootEncoder.
    compileOnly(libs.rootencoder.encoder)
    // Efek wajah (titik wajah per frame).
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.coil.core)
    implementation(libs.coil.network.okhttp)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
