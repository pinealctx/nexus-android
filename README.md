# Nexus Android

Native Android IM client for Nexus, built with Jetpack Compose + Kotlin.

Core business logic (sync engine, network protocol, local storage) is provided by the shared Rust library (`nexus-core`) via UniFFI-generated Kotlin bindings.

## Requirements

- Android Studio Ladybug or later
- JDK 17
- Android SDK 35
- NDK (for Rust .so integration)

## Build

```bash
./gradlew assembleDebug
```

## Architecture

- **UI**: Jetpack Compose + Material 3
- **DI**: Hilt
- **Navigation**: Compose Navigation
- **Core**: Rust shared library via UniFFI (JNI)
- **Push**: Firebase Cloud Messaging
