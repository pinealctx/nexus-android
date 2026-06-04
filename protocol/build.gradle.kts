plugins {
    alias(libs.plugins.android.library)
}

val generatedProtocolDir = layout.buildDirectory.dir("generated/source/buf/main")
val protoRoot = rootProject.projectDir.parentFile.resolve("nexus-proto/proto")

android {
    namespace = "com.pinealctx.nexus.protocol"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val generatedPath = generatedProtocolDir.get().asFile.absolutePath
        variant.sources.java?.addStaticSourceDirectory(generatedPath)
        variant.sources.kotlin?.addStaticSourceDirectory(generatedPath)
    }
}

tasks.register<Exec>("generateNexusProtocol") {
    group = "code generation"
    description = "Generate Nexus protobuf messages and Connect-Kotlin clients."
    workingDir = protoRoot
    outputs.dir(generatedProtocolDir)
    doFirst {
        delete(generatedProtocolDir)
    }
    commandLine(
        "buf",
        "generate",
        "--template",
        rootProject.projectDir.resolve("buf.gen.kotlin.yaml").absolutePath,
        "--output",
        generatedProtocolDir.get().asFile.absolutePath
    )
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn("generateNexusProtocol")
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn("generateNexusProtocol")
}

dependencies {
    api(libs.protobuf.javalite)
    api(libs.protobuf.kotlinlite)
    api(libs.connect.kotlin)
    api(libs.connect.kotlin.okhttp)
    api(libs.connect.kotlin.google.javalite)
    api(libs.okhttp)
    implementation(libs.coroutines.core)
}
