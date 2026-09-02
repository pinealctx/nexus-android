# Nexus Android

Native Android IM client for Nexus, built with Jetpack Compose + Kotlin.

The app talks to Nexus backend services through generated Connect-Kotlin clients. Room is the local source of truth for conversations, messages, contacts, groups, agents, and media metadata; DataStore holds non-sensitive preferences, and Android Keystore protects credentials.

## Requirements

- Android Studio with Android Gradle Plugin 9.3 support
- JDK 21 (pinned by `.java-version`; AGP requires at least JDK 17)
- Android SDK 36 and Build Tools 36.0.0
- `buf` CLI for protobuf generation

## Commands

```bash
./gradlew generateProtocol
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

Use `task doctor` to validate the local toolchain, `task verify` for the full
quality gate, and `task run-debug` to build and launch the app on a connected
device or the `nexus_test` AVD. Protocol generation uses pinned local generator
artifacts; Nexus schemas are never sent to a remote code-generation service.

## Architecture

- **UI**: Jetpack Compose + Material 3
- **DI**: Hilt
- **Navigation**: Compose Navigation
- **Protocol**: Protobuf + Connect-Kotlin generated from `../nexus-proto/proto`
- **Network**: Connect RPC over OkHttp + WebSocket gateway
- **Storage**: Room + DataStore + Android Keystore
- **Background reliability**: WorkManager send queue and bounded sync recovery
- **Push**: optional Firebase Messaging (FID registration, data-only notifications, sync wake-up, deduplication, and deep-link routing)

## Modules

| Module | Purpose |
| --- | --- |
| `app` | Android application, UI, DI, network wrappers, sync, and local storage |
| `protocol` | Generated protobuf messages and Connect-Kotlin clients |
