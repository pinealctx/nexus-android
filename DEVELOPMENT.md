# Nexus Android Development

本文记录 Android 原生客户端的本地开发、协议生成和模拟器运行要求。Android 代码、注释、日志和提交信息仍使用英文。

## 必需环境

| 工具 | 要求 | 说明 |
| --- | --- | --- |
| JDK | 21 | 由 `.java-version` 固定；Java/Kotlin 字节码目标仍为 17。 |
| Android SDK | `local.properties` 中配置 `sdk.dir` | 脚本优先读取 `local.properties`，不要求 `adb` 在 PATH。 |
| Gradle / AGP / Kotlin | 9.5.1 / 9.3.2 / 2.4.10 | 使用 AGP 内置 Kotlin，KSP 固定为 2.3.10。 |
| buf CLI | 1.72.0 | 用于从 `../nexus-proto/proto` 生成 Kotlin protobuf 和 Connect 0.9 客户端。 |
| AVD | 默认 `nexus_test` | `task run-debug` 默认启动该 AVD。 |

SDK 需要安装 Android API 36、Build Tools 36.0.0 和 Platform Tools。首次拉取后先执行：

```bash
task doctor
```

当前 `compileSdk` / `targetSdk` 固定为 36。Android API 37 仍处于 Preview 阶段，暂不作为发布目标；进入稳定渠道后再单独做行为变更回归与 target SDK 升级。

## 推荐流程

改动 Android UI/Kotlin 或本地业务代码：

```bash
task build
task run-debug
```

提交前执行完整本地门禁：

```bash
task verify
```

当 `nexus-proto` 的 proto schema 改动后：

```bash
task generate
task build
```

## 打包默认服务器地址

Android 的默认服务器地址随 APK 打包进 `BuildConfig`。未指定时使用仓库默认 dev 地址；打包时可以通过 Gradle property 或环境变量覆盖：

```bash
./gradlew assembleDebug -Pnexus.apiBaseUrl=https://api.example.com -Pnexus.wsUrl=wss://api.example.com/ws
```

```bash
NEXUS_API_BASE_URL=https://api.example.com NEXUS_WS_URL=wss://api.example.com/ws ./gradlew assembleDebug
```

登录页支持隐藏的运行时覆盖：5 秒内点击登录页 logo 5 次，输入 API base URL 后保存。运行时覆盖值保存在加密 SharedPreferences 中；退出登录只清 token，不清服务器地址。自定义 API 地址会自动派生 WebSocket 地址：`http` → `ws`，`https` → `wss`，路径后追加 `/ws`。

## Firebase 配置

FCM 客户端与服务端链路已实现，Firebase 项目配置仍保持可选。未注入下列配置时应用不会初始化 Firebase，日常开发、CI、APK/AAB 构建和 WebSocket/周期同步均不依赖 Google 项目。

FCM 配置按需通过 Gradle property、环境变量或被 Git 忽略的 `local.properties` 注入，不提交 `google-services.json` 或密钥：

```bash
NEXUS_FIREBASE_APPLICATION_ID=... \
NEXUS_FIREBASE_API_KEY=... \
NEXUS_FIREBASE_SENDER_ID=... \
NEXUS_FIREBASE_PROJECT_ID=... \
./gradlew assembleDebug
```

本机长期配置可写入 `local.properties`：

```properties
nexus.firebaseApplicationId=...
nexus.firebaseApiKey=...
nexus.firebaseSenderId=...
nexus.firebaseProjectId=...
```

这四项是与 Android 包名和签名环境绑定的 Firebase 公共 bootstrap identity，需要在应用进程被后台消息唤醒前可用，因此不依赖 Nexus `GetClientConfig`。WebSocket 地址、登录方式等可在应用启动后发现的业务配置仍由 `GetClientConfig` 下发；Firebase Service Account JSON 是服务端私钥，只配置在 Worker，绝不下发到客户端。

新版 Firebase Messaging 使用 Firebase Installation ID（FID）注册。登录后客户端调用 `FirebaseMessaging.register()`，由 `onRegistered` 将 FID 写入 Nexus 设备会话；后端 Worker 已通过 Firebase Admin Go SDK 按 FID 发送 data-only 消息。真实 Firebase 项目完成后台通知、免打扰、点击跳转和失效 FID 清理验收后，才视为生产推送闭环。

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

### protobuf 或 Connect 客户端类型找不到

现象：

```text
Unresolved reference: com.api.v1.*
Unresolved reference: com.shared.v1.*
```

处理：确认 `buf` 已安装，并运行 `task generate` 或直接运行 `./gradlew generateProtocol`。普通构建会依赖 `:protocol:generateNexusProtocol`，但单独打开 IDE 时可能需要先触发一次生成。Gradle 会从 Maven Central 解析固定版本的本地 `protoc` 与 Connect-Kotlin 生成器；生成时不会将仓库协议上传到远程生成服务。

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
