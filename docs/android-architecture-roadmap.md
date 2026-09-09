# Nexus Android 技术基线与推进路线

状态：Android 本地基线已落地，生产外部依赖待联调
更新时间：2026-09-02

功能增量：2026-09-09 已实现 Android 0.2.0 群聊提及、TEXT 富文本实体与链接交互，详见 [0.2.0 版本说明](releases/0.2.0.md)。数据库版本提升到 10，保留远端消息和本地待发送消息中的实体信息。

功能增量：2026-09-09 已实现 Android 0.3.0 Agent 命令面板、会话内 Mini App 入口与本地消息搜索分页/定位体验，详见 [0.3.0 版本说明](releases/0.3.0.md)。沿用现有协议及数据库版本，不新增业务消息类型。

体验修复：2026-09-09 Android 0.3.1 补齐列表实时排序、队列状态预览、陈旧响应保护、会话/历史分页与持久化草稿，详见 [0.3.1 版本说明](releases/0.3.1.md)。Room 升级到 11，仅新增草稿存储，不改变公开协议。

## 技术栈结论

现有原生技术方向合理，不需要跨平台重写，也不需要把 Connect/Protobuf 改成 REST。当前基线为 JDK 21、Gradle 9.5.1、AGP 9.3.2、Kotlin 2.4.10、KSP 2.3.10、Compose BOM 2026.06.01、Hilt 2.60.1、Room 2.8.4、DataStore 1.2.1、WorkManager 2.11.2、Connect-Kotlin 0.9.0、OkHttp 5.4.0 和 Firebase BOM 34.18.0。

`compileSdk` / `targetSdk` 保持 36。Google Play 当前发布要求已满足，而 API 37 仍处于 Preview；待其稳定后再独立完成行为变更、CI 与真机回归，不在当前基线中冒险追 Preview。Compose、Navigation、Lifecycle、Core、Hilt Navigation、Activity、Coil 与 OkHttp 因此固定在最后一组通过 API 36 AAR 元数据门禁的稳定版本，而不是直接采用已要求 API 37 的最新版。

保留以下架构：

- Kotlin、Jetpack Compose、Material 3、Hilt、Coroutines / Flow。
- Protobuf、Connect-Kotlin、OkHttp 与 WebSocket；通过 manager/repository 边界隔离生成代码。
- `app` + `protocol` 两模块；在业务边界稳定前不做过度模块化。
- Room 作为本地事实来源，DataStore 保存非敏感设置，Android Keystore + AES-GCM 保存凭据。
- WorkManager 负责持久发送与恢复同步，前台 WebSocket 提供实时性，后台由 FCM 唤醒有界同步。

## 与协议及桌面端的概念边界

`nexus-proto` 是跨端业务语义和线上数据格式的唯一事实来源。Android 不应自行定义会话类型、群成员角色、媒体用途等业务枚举；这些字段在 API、manager 和 UI 层直接使用生成的 `ConversationType`、`MemberRole`、`MediaPurpose` 等类型。SQLite 只能以整数持久化枚举时，转换集中在 Room 存储边界完成，未知值映射为协议的 `UNSPECIFIED`，业务代码不比较魔法数字。

Room 不是 Nexus 业务概念，也不是 Android 自创的“房间”。它是 Android 官方的 SQLite 持久化层，作用与桌面端 SQLite repository 相同。以下本地模型仍有保留价值：

- Room Entity：数据库表结构、索引和迁移。
- UI/domain model：离线缓存、组合展示字段和状态不可变性。
- `LocalMessageData` / `MessageSendState`：协议中不存在的本地乐观发送、失败重试状态。

生成的 protobuf 对象不直接贯穿数据库和 Compose 生命周期，避免把 wire presence、builder 和 schema 迁移细节泄漏到 UI；但所有跨端可观察的术语、枚举值、权限和行为必须与协议及桌面端一致。平台差异只允许出现在存储和生命周期实现上，不能改变业务含义。

2026-09-02 的对齐审计已修正 Android 将协议 `MEMBER_ROLE_MEMBER = 2` 错误显示为“管理员”的问题，并移除会话类型、成员角色和媒体用途主链路中的裸整数判断。

## 已落地数据流

```text
Compose UI
    ↓ immutable StateFlow / UI events
ViewModel
    ↓ suspend / Flow
Repository / manager
    ├── Room DAO（本地单一事实来源）
    ├── Connect API
    └── WorkManager（持久发送与恢复同步）
             ↑
WebSocket（前台） / FCM（后台唤醒）
             ↓
        Sync coordinator
             ↓ transaction + SN policy
           Room DB
```

## 协议生成决策

过渡方案已经完成：Android 从相邻 `nexus-proto` 读取 schema，使用固定版本的本地 protoc 与 Connect 生成器，Gradle 声明输入输出，CI 执行协议 lint 和生成；schema 不上传远程生成服务。

长期方案仍是由 `nexus-proto` CI 发布带 proto commit 映射的版本化 Android protocol AAR/Maven artifact，Android 只消费固定版本，本地联调通过 composite build 或本地 Maven 覆盖。这个工作需要跨仓发布基础设施，不阻塞当前 Android 开发。

## 里程碑状态

### M0：可重复构建基线

- [x] 本地固定版本的协议生成过渡方案。
- [x] `compileSdk` / `targetSdk` 36。
- [x] `generateProtocol`、单测、Lint、Debug APK、Release AAB 门禁。
- [x] GitHub Actions 构建产物与 API 26/33/36 模拟器矩阵。
- [x] macOS/Linux 运行脚本、环境诊断、JDK/Gradle/AGP/Buf 版本说明。

### M1：数据与协程基线

- [x] 会话、消息、联系人、群组、用户等缓存迁移到 Room 7 号 schema。
- [x] 生产 manager/API 移除 `runBlocking`，使用 `suspend` / Flow。
- [x] Repository 以 Room Flow 驱动 UI，缩小全局事件总线职责。
- [x] SN gap、重复 update、事务失败、冷启动恢复单测。
- [x] 缓存绑定当前账号，切换账号清理远端同步数据并保留待发送本地消息。

### M2：消息可靠性与前后台

- [x] WorkManager 持久发送队列与 client message id 幂等语义。
- [x] FCM Installation ID、data-only 通知、去重、点击路由和同步唤醒已完成；未配置 Firebase 项目时不初始化 Firebase。
- [x] 前台 WebSocket、后台 FCM、周期/即时差量同步职责划分。
- [x] 大文件流式分块上传。
- [~] 已覆盖重启、SN gap、重复回调、待发送恢复；真实断网、FID 失效和生产 FCM 仍需端到端环境。

### M3：功能闭环

- [x] 授权媒体 URL 解析、缓存、图片预览与失败回退。
- [x] 文件/音视频外部打开、相机与系统选择器发送。
- [x] Adaptive Card submit action 与 Mini App action。
- [x] 密码登录/设置、设备退出、会话 mute/delete 等账户能力。
- [~] 群组、联系人、Agent、Mini App 主流程已接入；复杂权限和服务端错误矩阵仍需联调验收。

### M4：质量与发布

- [~] 中英文资源已大幅收敛；完整无障碍审计仍待真机人工验收。
- [x] Compose 组件 instrumentation 与感知哈希截图回归。
- [~] 具备启动耗时检查、数据库/消息压力单测；尚未建立 Macrobenchmark 与线上基线。
- [~] 签名注入、AAB、R8、隐私说明和 CI 已完成；生产崩溃监控与商店密钥属于外部配置。
- [~] API 26/33/36 CI 模拟器矩阵已完成；ARM64 主流真机矩阵仍待设备。

## 下一推进方向

1. 将 protocol AAR 发布能力移到 `nexus-proto` CI，解除 Android 对相邻仓库布局的长期依赖。
2. 接入选定的崩溃/性能平台与生产密钥，增加 Macrobenchmark、Baseline Profile 和消息长列表基准。
3. 以 ARM64 真机覆盖 API 26、33、36 的账号切换、弱网、杀进程、媒体、Mini App 和 Keystore 场景。
4. API 37 稳定后，单独建立升级分支执行行为变更与发布回归。
5. 完成真实 Firebase 项目端到端验收：后台/杀进程通知、免打扰、点击跳转、重复通知抑制和失效 FID 清理。

## 当前不做

- 不重写为 Flutter、React Native 或 KMP UI。
- 不把 Connect/Protobuf 改为 REST。
- 不在可靠性基线前拆分大量 Gradle feature module。
- 不为追求版本数字切换到 Preview SDK。

## 官方依据

- Android Gradle Plugin 9.3: https://developer.android.com/build/releases/agp-9-3-0-release-notes
- AGP 内置 Kotlin 迁移: https://developer.android.com/build/migrate-to-built-in-kotlin
- Google Play target API requirements: https://support.google.com/googleplay/android-developer/answer/11926878
- Android 17 SDK（当前 Preview）: https://developer.android.com/about/versions/17/setup-sdk
- Android architecture recommendations: https://developer.android.com/topic/architecture/recommendations
- Compose BOM: https://developer.android.com/develop/ui/compose/bom
- Room: https://developer.android.com/training/data-storage/room
- WorkManager: https://developer.android.com/develop/background-work/background-tasks/persistent
- DataStore: https://developer.android.com/topic/libraries/architecture/datastore
- Firebase Messaging: https://firebase.google.com/docs/reference/android/com/google/firebase/messaging/FirebaseMessaging
- Buf local plugin configuration: https://buf.build/docs/configuration/v2/buf-gen-yaml/
- Connect-Kotlin: https://connectrpc.com/docs/kotlin/getting-started/
