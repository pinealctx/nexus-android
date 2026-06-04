# Nexus Android Development

本文记录 Android 原生客户端的本地开发、协议生成和模拟器运行要求。Android 代码、注释、日志和提交信息仍使用英文。

## 必需环境

| 工具 | 要求 | 说明 |
| --- | --- | --- |
| JDK | 17 或更高 | 当前本机使用 JDK 21；Gradle/AGP 可正常运行。 |
| Android SDK | `local.properties` 中配置 `sdk.dir` | 脚本优先读取 `local.properties`，不要求 `adb` 在 PATH。 |
| buf CLI | 可执行 `buf` | 用于从 `../nexus-proto/proto` 生成 Kotlin protobuf 和 Connect 客户端。 |
| AVD | 默认 `nexus_test` | `task run-debug` 默认启动该 AVD。 |

## 推荐流程

改动 Android UI/Kotlin 或本地业务代码：

```bash
task build
task run-debug
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

处理：确认 `buf` 已安装，并运行 `task generate` 或直接运行 `./gradlew generateProtocol`。普通构建会依赖 `:protocol:generateNexusProtocol`，但单独打开 IDE 时可能需要先触发一次生成。

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
