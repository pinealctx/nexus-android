# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Nexus Android is the native Android client for Nexus IM, built with Jetpack Compose and Kotlin. Core business logic (sync, network, storage) lives in the shared Rust library (`nexus-core`), accessed via UniFFI-generated Kotlin bindings.

## Commands

```bash
./gradlew assembleDebug       # Build debug APK
./gradlew assembleRelease     # Build release APK
./gradlew test                # Run unit tests
./gradlew connectedAndroidTest # Run instrumented tests
./gradlew lint                # Run Android lint
```

Or via Taskfile:
```bash
task build
task build-release
task test
task lint
task sync-core-libs    # Build current nexus-core Android .so files into app/src/main/jniLibs
task run-debug         # Build, install, and launch on local emulator
task run-debug-fast    # Install and launch existing debug APK
```

## Architecture

```
┌─────────────────────────────────────────────────┐
│  Jetpack Compose UI (Screens + Components)      │
├─────────────────────────────────────────────────┤
│  ViewModels (Hilt-injected, expose StateFlow)   │
├─────────────────────────────────────────────────┤
│  core/managers/ (Domain Managers)               │
│  core/ (NexusClientProvider, EventBridge,       │
│         SyncManager, SecureStorage)             │
├─────────────────────────────────────────────────┤
│  core-bindings/ (UniFFI-generated Kotlin)       │
├─────────────────────────────────────────────────┤
│  libnexus_ffi.so (Rust shared core)             │
└─────────────────────────────────────────────────┘
```

### Domain Managers

ViewModels inject domain-specific managers instead of a monolithic wrapper:

| Manager | Responsibility |
|---------|---------------|
| `AuthManager` | Login, logout, verify, password, session restore |
| `ConversationManager` | List, fetch, mark-read, mute, delete conversations |
| `MessageManager` | Send/edit/recall/delete messages |
| `ContactManager` | Contacts, friend requests, block |
| `GroupManager` | Group CRUD, members, invites |
| `MediaManager` | Upload, chunked upload, URL resolution |
| `AgentManager` | Featured agents, mini apps |
| `SearchManager` | Message and user search |
| `UserManager` | Profile, devices, username |
| `PushManager` | FCM token registration |
| `SyncBridge` | Start/stop sync, cold start, local data |

## Tech Stack

- Kotlin 2.1, Jetpack Compose (BOM 2025.01), Material 3
- Hilt for dependency injection
- Compose Navigation for routing
- Coil 3 for image loading
- Firebase Messaging for push notifications
- EncryptedSharedPreferences for secure token storage
- Rust core via UniFFI (JNI)

## Module Structure

| Module | Purpose |
|--------|---------|
| `app` | Main application module (UI, DI, services) |
| `core-bindings` | UniFFI-generated Kotlin bindings for nexus-core |

## Key Directories

- `app/src/main/java/com/pinealctx/nexus/ui/` — Compose UI (screens, theme, navigation, components)
- `app/src/main/java/com/pinealctx/nexus/core/` — Rust bridge layer (NexusClientProvider, EventBridge, SyncManager, Models)
- `app/src/main/java/com/pinealctx/nexus/core/managers/` — Domain managers (Auth, Message, Contact, etc.)
- `app/src/main/java/com/pinealctx/nexus/di/` — Hilt modules (AppModule, ManagerModule)
- `app/src/main/java/com/pinealctx/nexus/service/` — Android services (foreground, FCM)

## Rust Integration

The app loads `libnexus_ffi.so` at startup via `System.loadLibrary("nexus_ffi")`. UniFFI generates Kotlin bindings in the `core-bindings` module. `NexusClientProvider` manages the `NexusClient` lifecycle (initialization and shutdown). Domain managers receive `NexusClientProvider` via Hilt and delegate to the underlying `NexusClient`. `EventBridge` implements the callback interface for Rust-to-Kotlin events.

### Core/Android Integration Rules

- `core-bindings/src/main/java/uniffi/nexus_ffi/nexus_ffi.kt` and `app/src/main/jniLibs/**/libnexus_ffi.so` must come from the same `nexus-core` commit. If the FFI interface changes, regenerate Kotlin bindings and rebuild/copy the Android `.so` files before running the app.
- `app/src/main/jniLibs/**/*.so` is intentionally ignored by git. Local emulator/device runs still require fresh `.so` files; use `task sync-core-libs` from this repo or `task sync-android-libs` from `../nexus-core`.
- The UniFFI Kotlin binding uses JNA. Android must depend on the JNA AAR variant (`net.java.dev.jna:jna:<version>@aar`) so `libjnidispatch.so` is packaged into the APK. Using the plain jar compiles but crashes on startup with `libjnidispatch.so` missing.
- Use `task run-debug` for local smoke tests. It reads `local.properties` `sdk.dir`, starts the `nexus_test` AVD when needed, installs the APK, launches `MainActivity`, and fails if logcat reports a fatal startup crash.
- Native library ABI coverage currently targets `x86_64` for emulator and `arm64-v8a` for physical Android devices. Keep both refreshed when changing `nexus-core`.

Common failure signatures:
- `undefined symbol: uniffi_nexus_ffi_checksum_*`: Kotlin binding and `libnexus_ffi.so` are out of sync.
- `Native library ... libjnidispatch.so not found`: JNA is packaged as jar instead of AAR, or native packaging was stripped incorrectly.
- `adb is not recognized`: use `task run-debug`; do not rely on `adb` being in PATH.

## Code Standards

- Follow Kotlin coding conventions and Android best practices
- All code, comments, and identifiers in English
- Use Compose for all UI (no XML layouts)
- ViewModels expose `StateFlow` for UI state
- Use `Dispatchers.IO` for Rust FFI calls (they may block)

## Language Policy

Conversational replies and spec documents in Simplified Chinese. All code, comments, logs, commits, and identifiers in English.
