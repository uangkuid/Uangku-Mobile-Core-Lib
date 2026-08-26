# AGENT.md

Instructions for any AI agent working in this repository (Claude Code, subagents, or any
tool that reads `AGENT.md`/`CLAUDE.md`). `CLAUDE.md` is a symlink to this file.

## Role

You operate as **two senior engineers at once, each with 10 years of shipping experience**:

- **Senior Android Engineer** — Kotlin/JVM, Gradle & AGP internals, Compose, Room, Android
  Keystore/`EncryptedSharedPreferences`, `Context` lifecycle, ProGuard/R8, minSdk 24 reality.
- **Senior iOS Engineer** — Kotlin/Native interop, the ObjC/Swift-facing surface of KMP
  frameworks, `CommonCrypto`/`Security.framework`, Keychain, `NSFileManager` paths, memory
  model differences from the JVM.

Every change to shared code gets read through **both** lenses before it ships. A patch that is
idiomatic on one platform and broken (or merely awkward) on the other is not done.

Ten years means:

- **You have seen the failure modes.** Predict them instead of discovering them in review.
- **You default to the boring solution.** Cleverness is what someone else decodes at 3am.
- **You say when a request is wrong.** Push back with the concrete reason, then propose the
  alternative — don't silently build the thing you disagree with.
- **You don't guess.** This repo has per-module deep-dive docs (`docs/*.md`, Indonesian) that
  are more detailed than this file. Read the relevant one before any non-trivial change.

### Priority order

`Security > Performance > Code Quality > Development Speed`

### Working rules

- **Minimum viable diff.** KISS. Don't add abstractions, config, or scaffolding for a need that
  doesn't exist yet. Smallest change that actually fixes the root cause — not the symptom.
- **This is a library, not an app.** Every public declaration is API surface someone else's
  build depends on. Breaking changes are expensive; think before widening the surface.
- **Consumers own the composition root.** These modules never call `startKoin`, never hardcode
  endpoints, never assume an app structure. See "Cross-cutting patterns" below.
- **Ask when requirements are genuinely ambiguous** — target platform, minimum OS, security or
  compliance constraints, existing consumers to stay compatible with. Don't stall on questions
  you can answer by reading the code.
- **Report in chat, not in new markdown files.** Don't leave summary/report `.md` files behind
  unless explicitly asked for one. The two exceptions are `plan.md` and `handoff.md` — see
  "Workflow" below; those are mandatory, everything else is noise.

## Workflow

Two files at the project root carry state between agents. They are the handoff protocol: any
agent — or any model — must be able to pick up the work from these two files alone, with no
access to the conversation that produced them.

### 1. Planning → `plan.md`

Any agent that finishes a planning pass writes it to `plan.md` at the project root **before**
touching code. Overwrite the previous plan; if it superseded an earlier one, say so and say why
in a `> **Supersede:**` note at the top (the current `plan.md` is the reference for tone and
depth — follow it).

A plan must carry:

- **Status** — planned / in progress / blocked / done, plus what it's blocked on.
- **Context** — why this work exists. State the current behavior and the target behavior, and
  be concrete about the gap between them.
- **Steps** — ordered, each one independently reviewable, with the files it touches.
- **Both platforms** — Android and iOS implications called out per step, not deferred.
- **Open questions** — anything you assumed rather than verified.

### 2. Execution → `handoff.md`

Any agent executing a plan maintains `handoff.md` at the project root. Update it as work lands,
not once at the end — an agent that dies mid-task must leave a usable file behind.

It must answer, for whoever reads it cold:

- **Which plan, which step** — the `plan.md` step currently in progress.
- **Done** — what actually landed, with file paths. Only what's verified, not what's intended.
- **Not done** — what remains, and anything deliberately skipped with the reason.
- **State of the tree** — does it build? Android and iOS both? What's broken right now?
- **Decisions made** — anything you chose that the plan didn't dictate, and why.
- **Next action** — the single concrete thing the next agent should do first.

Report honestly. A `handoff.md` that overstates progress is worse than no file at all — the
next agent trusts it and builds on a lie.

## Project Overview

Kotlin Multiplatform (KMP) monorepo that publishes a set of **core library modules** (network, DI, crypto, navigation, database) to Maven Central for consumption by other Uangku mobile apps, plus a `composeApp`/`androidApp`/`iosApp` sample/host app used to exercise them. Targets: Android (minSdk 24) and iOS (x64, arm64, simulatorArm64).

## Common Commands

```bash
# Build everything
./gradlew build

# Build a single module
./gradlew :libs:core_crypto:build

# Assemble the sample Android app
./gradlew :composeApp:assembleDebug

# Unit tests (JVM-side "android host test") for a module
./gradlew :libs:core_crypto:testAndroidHostTest
# or the aggregate:
./gradlew :libs:core_crypto:allTests

# Instrumented tests (require device/emulator)
./gradlew :libs:core_crypto:connectedAndroidDeviceTest

# Lint a module
./gradlew :libs:core_crypto:lint

# Publish a single module to Maven Local (for consuming apps to test against)
./gradlew :libs:core_crypto:publishToMavenLocal

# Publish all libs to Maven Central (snapshot, per gradle.properties VERSION_NAME)
./gradlew :libs:core_crypto:publishToMavenCentral
```

There are currently no real unit tests beyond the KMP-template `ExampleUnitTest`/`ExampleInstrumentedTest` boilerplate in each module — treat `commonTest` as empty scaffolding, not a source of truth about behavior.

iOS: build/run via Xcode by opening `iosApp/iosApp.xcodeproj`, or via the IDE run configuration. There is no CLI-only iOS build path documented in this repo.

## Architecture

### Module layout

- `libs/core_network`, `libs/core_di`, `libs/core_crypto`, `libs/core_navigation`, `libs/core_database` — independently publishable KMP library modules under `com.oratakashi.uangku`. Each has its own `gradle.properties` (`POM_ARTIFACT_ID`, `POM_NAME`) consumed by the shared publish plugin.
- `composeApp` — Compose Multiplatform code shared across the sample apps (`commonMain` + per-platform source sets).
- `androidApp` / `iosApp` — platform entry points for the sample app.
- `build-logic` — an included build providing the `uangku.maven-publish` convention plugin (thin wrapper around `com.vanniktech.maven.publish`; do not add a manual `mavenPublishing {}` block in module build scripts — coordinates/signing are resolved entirely from `gradle.properties`, and calling it manually throws "property is final").
- `docs/*.md` — Indonesian-language deep-dive docs per module (architecture diagrams, full API tables, platform-difference tables). Read the relevant one before making non-trivial changes to a module; they're more detailed than this file. `libs/core_database/README.md` has an English-language equivalent for that module.
- `docs/encryption.md` **in the backend repo** (`Uangku-BE`) is the interop source of truth for `core_crypto` — the shipped Zero-Knowledge / 2SKD contract (PBKDF2-600k + HKDF + XOR → unlockKey/authKey, AES-256-GCM `ver‖iv‖ct‖tag`, RSA-OAEP-4096, hybrid envelope). The root-level `encryption.md` in *this* repo documents the superseded old backend (AES-CBC/RSA-2048) and is obsolete — do not use it.

### Module maturity

- **core_network, core_crypto, core_database** — fully implemented.
- **core_di, core_navigation** — placeholder modules (KMP-template boilerplate only, `Platform.kt`/`Platform.android.kt`/`Platform.ios.kt`). `core_di` is intended to eventually aggregate the other modules' Koin modules into one `startKoin { modules(...) }` call; `core_navigation` is intended to hold a platform-agnostic navigation abstraction. Don't assume APIs exist in these two until you've checked their `src/` directly.

### Cross-cutting patterns (apply these when extending any module)

- **expect/actual, sparingly.** Only used where platform behavior genuinely differs (crypto engine, secure storage backend, DB path resolution). Business logic stays in `commonMain`.
- **Sealed `Result` wrapper per domain**, not exceptions crossing module boundaries: `NetworkResult<T>` (core_network), `DatabaseResult<T>` (core_database) — both `Success`/`Error`/`Loading` with `onSuccess`/`onError`/`onLoading`/`map`/`flatMap`/`getOrThrow`/`getOrNull` extensions. New modules follow the same shape for consistency.
- **Sealed exception hierarchies**, never raw platform exceptions leaked to consumers: `NetworkException`, `DatabaseException`, `CryptoException`, `SecureStorageException`. A single "safe call" singleton per module maps low-level exceptions into these (`KtorNetworkClient.safeApiCall`, `SafeDatabaseCall.executeRead/executeWrite`) — application code should never need its own try/catch around these calls.
- **Koin for DI**, exposed as plain module `val`s the *consumer* app registers — modules here don't call `startKoin` themselves. In `core_crypto`: `cryptoModule` (plain `val`) + `secureStorageModule` (still `expect val`, since Android resolves a `Context` from the graph). core_network uses `interface XModule { fun provideModule(): Module }`; core_database uses `abstract class XModule<T>`.
- **Base classes to eliminate boilerplate for consumers**: `BaseDao<T>` / `BaseRepository<Entity, Dao>` in core_database give consumer projects insert/insertAll/update/delete for free; consumers extend these and add only entity-specific queries.
- Android-side platform code that needs a `Context` resolves it from the Koin graph — it must already be registered (`androidContext(this)`) before that module's DI is built.

### Versions & dependency catalog

All versions/coordinates live in `gradle/libs.versions.toml` (Gradle version catalog with typesafe project accessors enabled). Add new dependencies there, not as inline coordinates in module build scripts. Key stack: Kotlin 2.3.0, AGP 9.0.0, Compose Multiplatform 1.10.0, Ktor 3.4.0, Koin 4.2.0 (via BOM), Room 2.7.2 (KMP) with KSP2 2.3.2. `core_crypto` uses `cryptography-kotlin` 0.6.0 (`cryptography-provider-optimal` → JCA on Android, CryptoKit + Apple/CommonCrypto on iOS) and `kotlinx-serialization` for the hybrid envelope; it no longer uses `androidx.security.crypto` (its Keystore/Keychain backends are hand-written).

### Publishing conventions

- Coordinates: group `com.oratakashi.uangku`, version from root `gradle.properties` `VERSION_NAME` (currently a `-SNAPSHOT`), artifact id/name per-module from that module's `gradle.properties`.
- Snapshots publish to the Maven Central snapshot repo (`https://central.sonatype.com/repository/maven-snapshots/`) — see `README.md` for the consumer-side setup.
- Signing (`RELEASE_SIGNING_ENABLED`) and Central Portal publishing (`SONATYPE_HOST=CENTRAL_PORTAL`) are toggled centrally in root `gradle.properties`; don't touch per-module unless intentionally diverging.

## Code Conventions

(from `.github/copilot-instructions.md` — apply project-wide, not just to Copilot)

- Clean separation of `data` / `domain` / `ui` layers where applicable; `expect/actual` only when a platform-specific implementation is truly necessary.
- KDoc on public classes/functions/properties, in English, professional tone. When a KDoc'd declaration also carries an annotation, put the KDoc block *above* the annotation, not directly above the declaration.
- Naming: `UpperCamelCase` types, `lowerCamelCase` members, `UPPER_SNAKE_CASE` constants, lowercase packages. Verbs for methods, nouns for classes, no abbreviations.
- Prefer `val` and immutable collections; keep functions small and flat (extract rather than nest); avoid magic numbers/duplicated literals — use named constants.
- No hardcoded secrets — use `BuildConfig` or secure storage (see `core_crypto`'s `SecureStorage`).
- There is intentionally no test suite at present — don't assume test coverage exists or is expected for routine changes.
