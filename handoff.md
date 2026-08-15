# Handoff: real-backend unit tests for `core_crypto` `SecureStorage`

## Which plan, which step

Plan: `plan.md` isn't the file used this round — the plan for this work lives at
`/home/oratakashi/.claude/plans/coba-kamu-periksa-apakah-breezy-cray.md` (plan-mode plan file,
approved by the user). All four steps of that plan are done.

## Done

- **Step 1 — Android instrumented test**:
  `libs/core_crypto/src/androidDeviceTest/kotlin/com/oratakashi/uangku/core/libs/core_crypto/storage/AndroidKeystoreSecureStorageTest.kt`
  — real `AndroidKeystoreSecureStorage` exercised against the real AndroidKeyStore + SharedPreferences.
  Ports the 6 `SecureStorageContractTest` cases plus two Android-specific ones (`stored_value_is_not_plaintext`,
  `corrupted_ciphertext_throws_read_failure`). Unique `prefsName`/`keyAlias` per test instance (UUID-suffixed),
  `@After` calls `clear()`.
- **Step 2 — iOS test**:
  `libs/core_crypto/src/iosTest/kotlin/com/oratakashi/uangku/core/libs/core_crypto/storage/KeychainSecureStorageTest.kt`
  — real `KeychainSecureStorage` exercised against the real iOS Keychain. Same 6 contract cases,
  unique `service` per instance (random-suffixed), `@AfterTest` calls `clear()`.
- **Step 3 — CI workflow**: `.github/workflows/core-crypto-tests.yml` (new — `.github/workflows/`
  didn't exist before this). Three jobs: `unit-test` (ubuntu, `testAndroidHostTest`),
  `android-instrumented-test` (ubuntu + `reactivecircus/android-emulator-runner@v2.38.0`, pinned to
  the exact latest release tag — verified live via GitHub API at plan time, not guessed —
  `connectedAndroidDeviceTest`), `ios-test` (macos-latest, `iosSimulatorArm64Test`). Triggers: `push`
  and `pull_request`, no branch filter (per explicit user request), scoped via `paths:` to
  `libs/core_crypto/**` and the workflow file itself. Root `build-example.yml` was left untouched and
  inert, used only as a style reference.
- **Step 4 — `build.gradle.kts`**: one dependency addition was required (plan flagged this as a
  "verify, don't assume" open item, and it turned out to be needed): `androidDeviceTest` source set
  didn't have `androidx.test.ext:junit`/`androidx.test:runner` on its compile classpath at all — not
  even the pre-existing untouched `ExampleInstrumentedTest.kt` boilerplate compiled without this
  change (confirmed by compiling before and after). Added:
  ```kotlin
  getByName("androidDeviceTest") {
      dependencies {
          implementation(libs.androidx.testExt.junit)
          implementation(libs.androidx.runner)
      }
  }
  ```
  in `libs/core_crypto/build.gradle.kts`. Both alias entries already existed in
  `gradle/libs.versions.toml` — no version-catalog changes needed.

## Not done

- Nothing from the plan was skipped.
- Not verified: actually running `connectedAndroidDeviceTest` on a real device/emulator, or
  `iosSimulatorArm64Test` on a real simulator — no device/emulator/iOS-runtime available in this
  environment (this is the same pre-existing constraint `docs/core_crypto.md` already documents for
  iOS; it's now also true for the new Android instrumented job). Only compilation was verified locally
  (see below). The CI workflow itself has also only been YAML-syntax-validated, not run — GitHub
  Actions is the only place that can actually prove it green, per the plan's own open question.

## State of the tree

Builds clean. Verified locally in this session:

- `./gradlew :libs:core_crypto:testAndroidHostTest` — **BUILD SUCCESSFUL** (all pre-existing
  `commonTest` content unaffected).
- `./gradlew :libs:core_crypto:compileAndroidDeviceTest` — **BUILD SUCCESSFUL** (compiles both the new
  `AndroidKeystoreSecureStorageTest` and the pre-existing `ExampleInstrumentedTest` — the latter did
  *not* compile before the `build.gradle.kts` dependency fix).
- `./gradlew :libs:core_crypto:compileTestKotlinIosSimulatorArm64` — **BUILD SUCCESSFUL** (compiles
  the new `KeychainSecureStorageTest`; pre-existing compiler warnings in `KeychainSecureStorage.kt`/
  `TextNormalizer.ios.kt` are unrelated, not introduced by this change).
- `./gradlew :libs:core_crypto:allTests :libs:core_crypto:lint` — **BUILD SUCCESSFUL**
  (`iosSimulatorArm64Test`/`iosX64Test` show as `SKIPPED`, not failed — no iOS runtime installed
  locally, matches the known constraint).
- `.github/workflows/core-crypto-tests.yml` — YAML syntax validated with `yaml.safe_load` (Python),
  parses cleanly. Not run through `actionlint` (not installed in this environment) and not run through
  GitHub Actions itself.

Not touched, not affected: `build-example.yml` (root) was already staged in git as a new file before
this session started (per `git ls-files -s`, modified 2026-02-09 — pre-existing user work, not
something this session created or altered). Left as-is.

## Decisions made

- Reused `SecureStorageContractTest`'s exact 6 case names/shapes on both new platform test classes
  rather than inventing new ones, per the plan's explicit "reuse, don't reinvent" instruction.
- Skipped a Keychain corrupted-item test case (plan's own call): would require bypassing
  `KeychainSecureStorage`'s public API with raw `SecItemAdd` interop inside the test, judged not worth
  the invasiveness for the value gained.
- Used `org.junit.Assert.*` (not `kotlin.test`) in the Android instrumented test file, matching the
  existing `ExampleInstrumentedTest.kt` convention already present in `androidDeviceTest` — consistency
  over introducing a second assertion style in the same source set.
- Pinned `reactivecircus/android-emulator-runner` to `v2.38.0` (checked live against the GitHub API at
  plan time — this was the actual latest release, not an assumed/guessed version), per the plan's
  explicit call to avoid a floating tag for a new third-party Action dependency.

## Next action

Push this branch (or open a PR) so `.github/workflows/core-crypto-tests.yml` actually runs on GitHub
and the two new test classes get their first real execution against real Keystore/Keychain backends —
that's the only way left to close the "not done" item above. No further local action is blocking.
