plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.savekoro.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.savekoro.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // প্রতিটা ফোন-টাইপের জন্য আলাদা ছোট APK + একটা universal APK (সব ফোনে চলে)
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
            // yt-dlp লাইব্রেরি reflection ব্যবহার করে, তাই minify বন্ধ
            isMinifyEnabled = false
            // শুধু নিজে ইনস্টল/টেস্টের জন্য debug key দিয়ে সাইন। বিক্রির আগে নিজের keystore বানান।
            signingConfig = signingConfigs.getByName("debug")
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

    // youtubedl-android এর জন্য জরুরি: native lib (python, ffmpeg) extract করা লাগে
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
