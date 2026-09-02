# Android 发布检查清单

## 自动门禁

- `task verify` 通过：协议生成、单元测试、Lint、Debug APK、Release AAB。
- Room schema JSON 已随数据库变更提交。
- Release 混淆构建无缺失类或反射告警。
- CI 产物中的 APK、AAB、测试和 Lint 报告可下载。

## 签名

正式签名通过以下环境变量注入，禁止提交 keystore 或密码：

- `NEXUS_RELEASE_STORE_FILE`
- `NEXUS_RELEASE_STORE_PASSWORD`
- `NEXUS_RELEASE_KEY_ALIAS`
- `NEXUS_RELEASE_KEY_PASSWORD`

未提供签名时仍可生成用于门禁检查的 unsigned Release AAB。

## Firebase

没有 Firebase 项目配置时应用继续依靠前台 WebSocket 与 WorkManager 周期/恢复同步，但不承诺系统级后台即时通知。需要发布后台即时通知时，Firebase 配置和端到端验收属于发布阻塞项。

FCM 可通过 Gradle property 或对应环境变量配置：

- `nexus.firebaseApplicationId` / `NEXUS_FIREBASE_APPLICATION_ID`
- `nexus.firebaseApiKey` / `NEXUS_FIREBASE_API_KEY`
- `nexus.firebaseSenderId` / `NEXUS_FIREBASE_SENDER_ID`
- `nexus.firebaseProjectId` / `NEXUS_FIREBASE_PROJECT_ID`

发布前验证 Firebase Installation ID 注册、后台通知、通知去重、免打扰、点击跳转和 WorkManager 差量同步。后端已完成 Installation ID 兼容，但仍必须通过真实 Firebase 项目端到端测试；只验证客户端注册接口不算完成生产推送验收。

## 设备矩阵

- API 26 真机或模拟器：最低版本、Keystore、Room、通知渠道。
- API 33：存储选择器、后台限制、通知权限。
- API 36：target SDK 行为、后台同步、边到边布局。
- 至少一台主流 ARM64 真机完成登录、消息、媒体、群组、Mini App 和退出流程。

## 上架前

- 更新 `versionCode` / `versionName` 和发布说明。
- 核对隐私说明、数据安全表单、通知与相机/麦克风用途。
- 在测试轨道验证升级、数据库 schema、账号切换和回滚方案。
- 确认崩溃与性能监控平台的生产配置已注入且无敏感信息泄漏。
- 确认 API 37 已进入稳定渠道后，再决定是否提升 `compileSdk` / `targetSdk`，不得直接以 Preview SDK 发布。
