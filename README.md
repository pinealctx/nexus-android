# Nexus Android

Native Android IM client for Nexus, built with Jetpack Compose + Kotlin.

The app is implemented as a native Android client. It talks to Nexus backend services through generated Connect-Kotlin clients and keeps a local SQLite cache for conversations, messages, contacts, groups, agents, and media metadata.

## Requirements

- Android Studio Ladybug or later
- JDK 17
- Android SDK 35
- `buf` CLI for protobuf generation

## Commands

```bash
./gradlew generateProtocol
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

## Architecture

- **UI**: Jetpack Compose + Material 3
- **DI**: Hilt
- **Navigation**: Compose Navigation
- **Protocol**: Protobuf + Connect-Kotlin generated from `../nexus-proto/proto`
- **Network**: Connect RPC over OkHttp + WebSocket gateway
- **Storage**: Android SQLite through `SQLiteOpenHelper`
- **Push**: Android notification infrastructure; Firebase Messaging integration is still pending

## Modules

| Module | Purpose |
| --- | --- |
| `app` | Android application, UI, DI, network wrappers, sync, and local storage |
| `protocol` | Generated protobuf messages and Connect-Kotlin clients |
