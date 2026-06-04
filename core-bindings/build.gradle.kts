plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.pinealctx.nexus.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        disable += "NewApi"
    }
}

dependencies {
    implementation(libs.coroutines.android)
    implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")
}
