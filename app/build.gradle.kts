import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val nexusLocalProperties = Properties().apply {
    rootProject.file("local.properties")
        .takeIf { it.isFile }
        ?.inputStream()
        ?.use(::load)
}

fun nexusProperty(gradleName: String, environmentName: String) =
    providers.gradleProperty(gradleName)
        .orElse(providers.environmentVariable(environmentName))
        .let { provider ->
            nexusLocalProperties.getProperty(gradleName)?.let(provider::orElse) ?: provider
        }

fun String.toBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val releaseStoreFile = nexusProperty("nexus.releaseStoreFile", "NEXUS_RELEASE_STORE_FILE")
val releaseStorePassword = nexusProperty("nexus.releaseStorePassword", "NEXUS_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = nexusProperty("nexus.releaseKeyAlias", "NEXUS_RELEASE_KEY_ALIAS")
val releaseKeyPassword = nexusProperty("nexus.releaseKeyPassword", "NEXUS_RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { it.isPresent && it.get().isNotBlank() }

android {
    namespace = "com.pinealctx.nexus"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pinealctx.nexus"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val defaultApiBaseUrl = nexusProperty("nexus.apiBaseUrl", "NEXUS_API_BASE_URL")
            .orElse("https://api.nexus-dev.xsyphon.com")
            .get()
        val defaultWsUrl = nexusProperty("nexus.wsUrl", "NEXUS_WS_URL")
            .orElse("wss://ws.nexus-dev.xsyphon.com/ws")
            .get()

        buildConfigField("String", "NEXUS_API_BASE_URL", defaultApiBaseUrl.toBuildConfigString())
        buildConfigField("String", "NEXUS_WS_URL", defaultWsUrl.toBuildConfigString())

        val firebaseResources = mapOf(
            "google_app_id" to nexusProperty(
                "nexus.firebaseApplicationId",
                "NEXUS_FIREBASE_APPLICATION_ID"
            ),
            "google_api_key" to nexusProperty("nexus.firebaseApiKey", "NEXUS_FIREBASE_API_KEY"),
            "gcm_defaultSenderId" to nexusProperty(
                "nexus.firebaseSenderId",
                "NEXUS_FIREBASE_SENDER_ID"
            ),
            "project_id" to nexusProperty("nexus.firebaseProjectId", "NEXUS_FIREBASE_PROJECT_ID")
        )
        firebaseResources.forEach { (name, value) ->
            value.orNull?.takeIf { it.isNotBlank() }?.let { resValue("string", name, it) }
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
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

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":protocol"))

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
    implementation(libs.markdown.renderer.m3)
    implementation(libs.markdown.renderer.coil3)

    // Audio and video playback
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui.compose.material3)

    // Coroutines
    implementation(libs.coroutines.android)

    // Persistence and background work
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.installations)

    // CameraX + ML Kit (QR scanning)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.work.testing)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.room.testing)
    debugImplementation(libs.compose.test.manifest)
}
