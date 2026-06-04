# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Nexus Android is the native Android client for Nexus IM. It is built with Jetpack Compose and Kotlin, uses generated Connect-Kotlin clients for business RPC, maintains a WebSocket gateway for push updates, and stores local cache data in Android SQLite.

The goal is to keep Android moving toward feature parity with `nexus-desktop` while preserving native Android quality: clear layering, coroutine-first APIs, testable business logic, reliable sync, and high unit-test coverage.

## Commands

```bash
./gradlew generateProtocol      # Generate protobuf and Connect-Kotlin clients from ../nexus-proto/proto
./gradlew assembleDebug         # Build debug APK
./gradlew assembleRelease       # Build release APK
./gradlew test                  # Run unit tests
./gradlew connectedAndroidTest  # Run instrumented tests
./gradlew lint                  # Run Android lint
```

Or via Taskfile:

```bash
task generate
task build
task build-release
task test
task lint
task run-debug
task run-debug-fast
```

## Architecture

```text
Jetpack Compose UI (screens + components)
        ↓
ViewModels (Hilt-injected, expose StateFlow)
        ↓
core/managers/ (domain use-case facade)
        ↓
client/ (Connect RPC, WebSocket gateway, sync engine)
        ↓
protocol/ (generated protobuf + Connect clients)

Local cache: local/LocalDataStore (SQLiteOpenHelper)
Secure state: core/SecureStorage
App events: core/AppEventBus
```

## Module Structure

| Module | Purpose |
| --- | --- |
| `app` | Android app, UI, DI, network wrappers, sync, local storage, services |
| `protocol` | Generated protobuf messages and Connect-Kotlin clients |

## Key Directories

- `app/src/main/java/com/pinealctx/nexus/client/` — RPC clients, WebSocket gateway, sync engine, proto mappers.
- `app/src/main/java/com/pinealctx/nexus/core/` — app models, secure storage, events, sync/session coordination.
- `app/src/main/java/com/pinealctx/nexus/core/managers/` — domain managers used by ViewModels.
- `app/src/main/java/com/pinealctx/nexus/local/` — SQLite local cache.
- `app/src/main/java/com/pinealctx/nexus/ui/` — Compose UI, routes, screens, components, theme.
- `app/src/main/java/com/pinealctx/nexus/service/` — Android foreground/push services.
- `protocol/` — codegen module that reads `../nexus-proto/proto`.

## Feature Alignment Target

Use `nexus-desktop` as the behavioral reference. Android should progressively match:

- Auth/session restore and server endpoint override.
- Conversation, message, contact, group, user profile, device, blocked-user, and agent workflows.
- Message types: text, image, file, audio, video, markdown, card, reply, edit, recall, delete.
- Sync behavior: cold start, gap fetch, push updates, reconnect difference fetch, idempotent local writes.
- Mini App behavior: launch data, bridge events, theme, QR scanner, permissions, and card actions.
- Notifications: message alerts, foreground service behavior, push registration, badge clearing.

## Code Standards

- Follow Kotlin coding conventions and Android best practices.
- All code, comments, logs, commits, and identifiers in English.
- Use Compose for all UI; no XML layouts.
- ViewModels expose immutable `StateFlow`.
- Prefer suspend functions and structured concurrency over `runBlocking`.
- Keep pure logic outside Android framework classes when practical, and cover it with JVM unit tests.
- Local storage changes need tests for round trips, pagination, deletion, migration, and sync update application.

## Language Policy

Conversational replies and spec documents in Simplified Chinese. All code, comments, logs, commits, and identifiers in English.
