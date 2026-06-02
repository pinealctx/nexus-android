# Nexus Android Development

本文记录 Android 本地开发、Core 集成和模拟器运行的环境要求。Android 代码、注释、日志和提交信息仍使用英文。

## 必需环境

| 工具 | 要求 | 说明 |
| --- | --- | --- |
| JDK | 17 或更高 | 当前本机使用 JDK 21；Gradle/AGP 可正常运行。 |
| Android SDK | `local.properties` 中配置 `sdk.dir` | 脚本优先读取 `local.properties`，不要求 `adb` 在 PATH。 |
| Android NDK | `ANDROID_NDK_HOME` 或 `NEXUS_ANDROID_NDK_HOME` | 用于从 `nexus-core` 构建 `libnexus_ffi.so`。也可安装到 Android SDK 的 `ndk/` 目录。 |
| Rust targets | `x86_64-linux-android`、`aarch64-linux-android` | 分别对应模拟器和 arm64 真机。 |
| AVD | 默认 `nexus_test` | `task run-debug` 默认启动该 AVD。 |

## 推荐流程

当只改 Android UI/Kotlin：

```bash
task build
task run-debug
```

当 `nexus-core` 的 FFI、schema、sync、message 或 native code 改动后：

```bash
task sync-core-libs
task build
task run-debug
```

当 FFI 方法、record、enum 或 callback 发生变化后，还必须先从 `../nexus-core` 生成 Kotlin binding：

```bash
cd ../nexus-core
task bindgen-kotlin
task sync-android-libs
cd ../nexus-android
task build
task run-debug
```

## 本地运行检查

`task run-debug` 会执行以下步骤：

1. 读取 `local.properties` 的 `sdk.dir`。
2. 启动 `nexus_test` 模拟器，或复用已连接设备。
3. 构建 `app-debug.apk`。
4. 安装并启动 `com.pinealctx.nexus/.MainActivity`。
5. 检查进程是否存在，并扫描启动期 fatal logcat。

快速复用已有 APK：

```bash
task run-debug-fast
```

## 常见问题

### Kotlin binding 与 so 不一致

现象：

```text
undefined symbol: uniffi_nexus_ffi_checksum_*
```

处理：

```bash
cd ../nexus-core
task bindgen-kotlin
task sync-android-libs
cd ../nexus-android
task run-debug
```

### JNA native dispatch 缺失

现象：

```text
Native library ... libjnidispatch.so not found
```

要求：

```kotlin
implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")
```

不要使用普通 jar 依赖；普通 jar 能编译，但不会把 Android ABI 的 `libjnidispatch.so` 打进 APK。

### adb 或 emulator 不在 PATH

不用手工配置 PATH。确保 `local.properties` 有：

```properties
sdk.dir=<path-to-android-sdk>
```

实际路径应以开发者本机 Android SDK 安装位置为准。

然后运行：

```bash
task run-debug
```

### jniLibs 不出现在 git status

`app/src/main/jniLibs/**/*.so` 被 `.gitignore` 忽略，这是预期行为。提交源码时不提交 `.so`；本地运行前用 `task sync-core-libs` 生成并复制。
