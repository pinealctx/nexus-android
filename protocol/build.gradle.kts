plugins {
    alias(libs.plugins.android.library)
}

val generatedProtocolDir = layout.buildDirectory.dir("generated/source/buf/main")
val protoRoot = rootProject.projectDir.parentFile.resolve("nexus-proto/proto")
val protocolToolsDir = layout.buildDirectory.dir("protocol-tools")
val generationConfig = layout.buildDirectory.file("protocol-tools/buf.gen.local.yaml")

val hostOS = System.getProperty("os.name").lowercase()
val hostArch = System.getProperty("os.arch").lowercase()
val isWindows = hostOS.contains("windows")
val protocClassifier = when {
    hostOS.contains("mac") && (hostArch == "aarch64" || hostArch == "arm64") -> "osx-aarch_64"
    hostOS.contains("mac") -> "osx-x86_64"
    hostOS.contains("linux") && (hostArch == "aarch64" || hostArch == "arm64") -> "linux-aarch_64"
    hostOS.contains("linux") -> "linux-x86_64"
    isWindows && (hostArch == "amd64" || hostArch == "x86_64") -> "windows-x86_64"
    else -> throw GradleException("Unsupported host for protobuf generation: $hostOS/$hostArch")
}

val protocTool by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val connectKotlinGenerator by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

android {
    namespace = "com.pinealctx.nexus.protocol"
    compileSdk = 36

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

val protocBinary = protocolToolsDir.map {
    it.file(if (isWindows) "protoc.exe" else "protoc")
}
val connectKotlinGeneratorJar = protocolToolsDir.map {
    it.file("protoc-gen-connect-kotlin.jar")
}

val prepareProtocolTools = tasks.register("prepareProtocolTools") {
    group = "code generation"
    description = "Resolve pinned local protobuf and Connect-Kotlin generators."
    inputs.files(protocTool, connectKotlinGenerator)
    outputs.files(protocBinary, connectKotlinGeneratorJar)

    doLast {
        val protocFile = protocBinary.get().asFile
        protocFile.parentFile.mkdirs()
        protocTool.singleFile.copyTo(protocFile, overwrite = true)
        if (!isWindows && !protocFile.setExecutable(true)) {
            throw GradleException("Failed to mark protoc as executable: $protocFile")
        }

        connectKotlinGenerator.singleFile.copyTo(
            connectKotlinGeneratorJar.get().asFile,
            overwrite = true
        )
    }
}

val writeProtocolGenerationConfig = tasks.register("writeProtocolGenerationConfig") {
    group = "code generation"
    description = "Write the Buf template that uses only local generators."
    dependsOn(prepareProtocolTools)
    inputs.files(protocBinary, connectKotlinGeneratorJar)
    outputs.file(generationConfig)

    doLast {
        fun yamlPath(path: String): String = path.replace('\\', '/')

        val configFile = generationConfig.get().asFile
        configFile.parentFile.mkdirs()
        configFile.writeText(
            """
            version: v2
            managed:
              enabled: true
            plugins:
              - protoc_builtin: java
                protoc_path: ${yamlPath(protocBinary.get().asFile.absolutePath)}
                out: .
                opt:
                  - lite
              - protoc_builtin: kotlin
                protoc_path: ${yamlPath(protocBinary.get().asFile.absolutePath)}
                out: .
                opt:
                  - lite
              - local:
                  - java
                  - -jar
                  - ${yamlPath(connectKotlinGeneratorJar.get().asFile.absolutePath)}
                out: .
            """.trimIndent() + "\n"
        )
    }
}

val generateNexusProtocol = tasks.register<Exec>("generateNexusProtocol") {
    group = "code generation"
    description = "Generate Nexus protobuf messages and Connect-Kotlin clients."
    dependsOn(writeProtocolGenerationConfig)
    workingDir = protoRoot
    inputs.files(
        fileTree(protoRoot) {
            include("**/*.proto", "buf.yaml", "buf.lock")
        }
    )
    inputs.file(generationConfig)
    outputs.dir(generatedProtocolDir)
    doFirst {
        delete(generatedProtocolDir)
    }
    commandLine(
        "buf",
        "generate",
        "--template",
        generationConfig.get().asFile.absolutePath,
        "--output",
        generatedProtocolDir.get().asFile.absolutePath
    )
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(generateNexusProtocol)
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(generateNexusProtocol)
}

tasks.matching {
    it.name.startsWith("extract") && it.name.endsWith("Annotations")
}.configureEach {
    dependsOn(generateNexusProtocol)
}

dependencies {
    add(
        protocTool.name,
        "com.google.protobuf:protoc:${libs.versions.protobuf.get()}:$protocClassifier@exe"
    )
    add(
        connectKotlinGenerator.name,
        "com.connectrpc:protoc-gen-connect-kotlin:${libs.versions.connect.kotlin.get()}"
    )

    api(libs.protobuf.javalite)
    api(libs.protobuf.kotlinlite)
    api(libs.connect.kotlin)
    api(libs.connect.kotlin.okhttp)
    api(libs.connect.kotlin.google.javalite)
    api(libs.okhttp)
    implementation(libs.coroutines.core)
}
