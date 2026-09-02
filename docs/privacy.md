# Android 隐私与本地数据说明

- 登录 Token 使用 Android Keystore 管理的 AES-GCM 密钥加密后保存在应用私有目录。
- 语言和通知偏好保存在 Preferences DataStore，不包含 Token。
- 会话、消息、联系人、群组和媒体元数据保存在应用私有 Room 数据库；退出登录会清理同步数据。
- 相册和文件通过系统选择器按次授权；大文件以分块流式上传，不在应用内复制完整文件。
- 相机、麦克风和通知权限只在对应功能需要时请求。拒绝权限不应阻止基础聊天功能。
- FCM 仅在提供 Firebase 项目配置后启用，Firebase Installation ID 会注册到 Nexus 推送服务。
