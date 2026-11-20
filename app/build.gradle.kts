plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// GitHub Actions passes -Pvc=<run number>, so the version bumps on every build
val vc = (project.findProperty("vc") as String?)?.toIntOrNull() ?: 1
val releaseKeystore = file("release.jks")

android {
    namespace = "com.savekoro.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.savekoro.app"
        minSdk = 26
        targetSdk = 35
        versionCode = vc
        versionName = "1.0.$vc"
    }

    // Permanent signing key: comes from GitHub Secrets, never stored in the repo
    signingConfigs {
        create("release") {
            if (releaseKeystore.exists()) {
                storeFile = releaseKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = "savekoro"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    // One small APK per CPU type + one universal APK that runs everywhere
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            // yt-dlp library uses reflection, so minify is off
            isMinifyEnabled = false
            // Release key if keystore exists, otherwise debug key (local testing)
            signingConfig = if (releaseKeystore.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // Required by youtubedl-android: native libs (python, ffmpeg) must be extracted
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    val ytdl = "0.17.4"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdl")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdl")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("io.coil-kt:coil-compose:2.7.0")
}
