plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

fun String.toBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.pinealctx.nexus"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pinealctx.nexus"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val defaultApiBaseUrl = providers.gradleProperty("nexus.apiBaseUrl")
            .orElse(providers.environmentVariable("NEXUS_API_BASE_URL"))
            .orElse("https://api.nexus-dev.xsyphon.com")
            .get()
        val defaultWsUrl = providers.gradleProperty("nexus.wsUrl")
            .orElse(providers.environmentVariable("NEXUS_WS_URL"))
            .orElse("wss://api.nexus-dev.xsyphon.com/ws")
            .get()

        buildConfigField("String", "NEXUS_API_BASE_URL", defaultApiBaseUrl.toBuildConfigString())
        buildConfigField("String", "NEXUS_WS_URL", defaultWsUrl.toBuildConfigString())
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core-bindings"))

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)

    // Activity & Core
    implementation(libs.activity.compose)
    implementation(libs.appcompat)
    implementation(libs.core.ktx)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.telephoto.zoomable)

    // Coroutines
    implementation(libs.coroutines.android)

    // Security
    implementation(libs.security.crypto)

    // CameraX + ML Kit (QR scanning)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    debugImplementation(libs.compose.test.manifest)
}
