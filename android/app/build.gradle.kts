plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "ug.liso.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ug.liso.app"
        // Android 8.0+: covers Tecno Spark / itel phones still in use in Uganda.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Only English and Luganda resources ship in the pilot (Swahili later).
        resourceConfigurations += listOf("en", "lg")
        // Test builds pass -PlisoApiUrl=https://... (see .github/workflows/test-apk.yml); the default is
        // the developer's computer as seen from the Android emulator.
        val apiUrl = (project.findProperty("lisoApiUrl") as String?)?.trimEnd('/') ?: "http://10.0.2.2:8080"
        buildConfigField("String", "API_BASE_URL", "\"$apiUrl\"")
    }

    buildTypes {
        release {
            // Shrinking keeps the download well under the 20 MB budget.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"https://api.liso.ug\"")
        }
    }

    // One APK per CPU type, so each phone downloads only its own native video code.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    // Compress the video SDK's native code inside the APK: a much smaller download
    // (pilot APKs are shared directly), at the cost of a little more space once installed.
    packaging {
        jniLibs.useLegacyPackaging = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Video calls: Amazon IVS Real-Time (the "stages" build of the broadcast SDK).
    implementation("com.amazonaws:ivs-broadcast:1.48.0:stages@aar")
    testImplementation("junit:junit:4.13.2")
}
