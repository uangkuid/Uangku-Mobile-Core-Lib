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

## Post-merge fix: `ios-test` CI job failure

First actual CI run of `.github/workflows/core-crypto-tests.yml` surfaced a real bug (not a CI
infra issue): all 6 `KeychainSecureStorageTest` cases failed on `iosSimulatorArm64Test`, every one
with `SecureStorageException.DeleteFailure(rootCause=null)` thrown from `@AfterTest tearDown()`'s
`storage.clear()` call — including `get_absent_key_is_null`, which never writes to the Keychain
before teardown runs. Uniform failure regardless of prior state means `SecItemDelete` was returning
some `OSStatus` that's neither `errSecSuccess` nor `errSecItemNotFound` for every query, in every
test.

Root cause: Kotlin/Native's `iosSimulatorArm64Test` task runs the test binary as a bare, unsigned
executable rather than inside a signed `.app` bundle. `SecItem*` calls from that context fail
Keychain-entitlement checks unless the query dictionary explicitly opts into the modern
"data protection keychain" via `kSecUseDataProtectionKeychain`. None of `KeychainSecureStorage`'s
four query builders (`baseQuery()`'s put/get/remove path, and `clear()`'s standalone query) set it.

Fix applied in `libs/core_crypto/src/iosMain/kotlin/.../storage/KeychainSecureStorage.kt`:
added `import platform.Security.kSecUseDataProtectionKeychain` and
`CFDictionaryAddValue(query, kSecUseDataProtectionKeychain, kCFBooleanTrue)` to both query-building
sites. This is a production source fix, not test-only — it changes real Keychain behavior on-device
too (harmlessly; iOS already defaults to the data-protection keychain, so this aligns simulator/CLI
execution with that default rather than changing device semantics). Updated the class doc comment
to explain why the flag is required instead of the old "pending simulator/device verification"
wording, which was no longer accurate. All three iOS test targets (`iosSimulatorArm64`, `iosX64`,
`iosArm64`) recompiled clean after the change; `testAndroidHostTest` re-verified unaffected.

**Not yet confirmed**: this fix has not been re-run through actual CI yet — push and watch the
`ios-test` job to confirm `kSecUseDataProtectionKeychain` fully resolves it. If it doesn't, the next
diagnostic step is to log the actual `OSStatus` value (currently swallowed — `DeleteFailure()` is
thrown without capturing `status` as `rootCause`) rather than guessing further.

## Post-merge fix #2: `ios-test` still red after the `kSecUseDataProtectionKeychain` fix

Re-ran CI after commit `5cf7eee` (the fix above) landed. Same 6 `KeychainSecureStorageTest` cases
failed again, byte-for-byte identical symptom: every test, including `get_absent_key_is_null`
(zero prior writes), throws `DeleteFailure(rootCause=null)` from the `@AfterTest tearDown()`'s
`storage.clear()` call. So `kSecUseDataProtectionKeychain` was necessary but not sufficient —
single-item queries (`baseQuery()`, used by `put`/`get`/`remove`) now work fine; only `clear()`'s
standalone wildcard query (`kSecClass` + `kSecAttrService`, no `kSecAttrAccount`) still fails.

Root cause (second bug, same file): `clear()`'s query is the only `SecItem*` query in the class
that doesn't fully specify a primary key (no `kSecAttrAccount`) *and* doesn't set `kSecMatchLimit`.
Per Apple's Keychain Services docs, `kSecMatchLimit` defaults to `kSecMatchLimitOne` for both
`SecItemCopyMatching` and `SecItemDelete`. A delete query that's structurally a multi-item match
(no account) combined with an implicit "at most one" limit gets rejected by `SecItem*` parameter
validation before the search even runs — which is why it fails identically even against an empty
keychain (validation is on query shape, not match count). This is consistent with every other
query in the file working: they all carry a full class+service+account primary key, so the
"how many could this match" ambiguity never arises for them.

Fix applied in the same file: added `import platform.Security.kSecMatchLimitAll` and
`CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitAll)` to `clear()`'s query, making it an
explicit multi-item delete instead of relying on the (single-item) default.

**Not yet confirmed**: cannot compile-check this locally — this repo has no CLI iOS build path
(Linux host, Apple targets require Xcode/macOS toolchain per the project's own `AGENT.md`). Push
and watch `ios-test` again. If still red, capture the real `OSStatus` from `clear()` (and `remove()`,
to rule it out too) via `DeleteFailure(rootCause = ...)` instead of guessing a third time — two
rounds of blind fixes against un-runnable-locally code is already the limit of what should go out
without that instrumentation.

## Post-merge fix #3: `kSecMatchLimitAll` did not fix it either — instrumenting instead of guessing

Re-ran CI after commit `70571e6` (`kSecMatchLimitAll` fix above) landed. **Identical failure**,
byte-for-byte: same 6 tests, same `DeleteFailure(rootCause=null)`. Verified via the log's checkout
step that this run genuinely built from `70571e6` (not a stale/cached run), so the fix was real but
ineffective.

Re-checked the evidence more carefully before guessing a third specific flag: `put_overwrites`
passes (no `WriteFailure` in the failure list), which only happens if the *single-item* delete
inside `put()`'s upsert step (`deleteItem(key)`, unchecked status) actually succeeds in removing the
prior value — otherwise the second `SecItemAdd` would hit `errSecDuplicateItem` and throw
`WriteFailure`, which never appears. That reconfirms: single-item `SecItemDelete` (used by `put()`
internally and by `remove()`) genuinely works. The failure is specifically and only in `clear()`'s
bulk query (`kSecClass`+`kSecAttrService`, no account) — now confirmed *even with* both
`kSecUseDataProtectionKeychain` and `kSecMatchLimitAll` applied. Since two structurally different,
individually-plausible fixes both failed identically, further static guessing isn't reliable —
`DeleteFailure(rootCause=null)` throws away the actual `OSStatus`, so there's no way to distinguish
between remaining hypotheses (errSecParam, errSecMissingEntitlement, something else entirely)
without seeing the real number.

Applied in the same file: `remove()` and `clear()` now wrap the failing status into
`DeleteFailure(rootCause = IllegalStateException("SecItemDelete failed in $op(): OSStatus=$status"))`
via a small `osStatusDiagnostic()` helper, instead of `DeleteFailure()` with no cause. This is
**diagnostic-only, not a fix** — marked with a `TODO` to remove once the real status is known. Next
CI run's failure output will show the actual `OSStatus` integer in place of `rootCause=null`.

**Next step is data-driven, not another guess**: push this, get the CI log, read the `OSStatus` value
out of the printed `DeleteFailure(rootCause=...)`, look that specific code up, and fix precisely
that. Do not attempt a fourth structural change to the query without that number in hand.

## Post-merge fix #4: the diagnostic channel itself was broken — switched to `println`

Ran CI with the `osStatusDiagnostic()` instrumentation from fix #3 (commit `f43d1f9`). Confirmed via
checkout step this ran against that commit. Failures are still the same 6 tests, but critically the
`OSStatus` still didn't show: the printed line is
`DeleteFailure(rootCause=kotlin.IllegalStateException at .../Exceptions.kt:25` — no colon, no
message text, and **no closing paren** on `DeleteFailure(...)` at all (verified with `cat -A`, no
trailing whitespace/hidden chars — the line genuinely ends there). Also confirmed the message isn't
buried anywhere else in the log (`grep -n OSStatus` on the full log: zero hits).

Conclusion: Kotlin/Native's compact test-failure summary renders a nested exception (here, the
`rootCause` property of the `DeleteFailure` data class) using only its qualified class name — it
does not include `.message`, and apparently doesn't even close out the wrapping data class's
`toString()` normally once a non-null nested exception is involved. `DeleteFailure(rootCause = ...)`
is not a usable channel to get diagnostic text out of this specific CI log format.

Applied in the same file: reverted to plain `DeleteFailure()` (no rootCause), and added
`logOsStatusDiagnostic(op, status)` which does a direct `println("KeychainSecureStorage.$op(): ...
OSStatus=$status")` right before the throw. This goes straight to the K/N test binary's stdout,
which this exact log stream already captures verbatim elsewhere (e.g. `"69 tests completed, 6
failed"` comes from that same binary) — a channel proven to survive into the log, unlike the
exception-property approach.

**Next CI run**: grep the log for `OSStatus=` (not `DeleteFailure` — the message now lives in a
`println`, not the exception). That number is the real diagnostic data point this investigation has
been missing across three prior attempts.

## Post-merge fix #5: the `println` never reached the console either — Gradle wasn't streaming it

Ran CI with the `println`-based `logOsStatusDiagnostic()` from fix #4 (commit `bc54b51`). Confirmed
via checkout step this ran against that commit. Same 6 tests failed, `rootCause=null` again (expected
— fix #4 reverted the exception-property approach), but `grep -n "OSStatus=" job-logs.txt` on the
full log came back with **zero matches**. The `println` call itself never showed up anywhere.

Root cause of *that*: Gradle's test tasks (JVM `Test` and Kotlin/Native's `KotlinNativeTest`, which
extends the same `AbstractTestTask` and inherits the same `testLogging` defaults) capture a test's
stdout/stderr into the task's report files, not the console, unless `testLogging.showStandardStreams`
is explicitly set to `true`. Nothing in `libs/core_crypto/build.gradle.kts` configured this, so every
`println` in `logOsStatusDiagnostic()` across the last CI run was silently swallowed into an HTML
report on the ephemeral `macos-latest` runner — not recoverable after the job ends. (The "69 tests
completed, 6 failed" line that *does* appear in every log is emitted by Gradle's own test-progress
reporter, not the test binary's stdout — that's a different channel and was never evidence that
stdout streams through.)

Applied in `libs/core_crypto/build.gradle.kts`: added
`tasks.withType<AbstractTestTask>().configureEach { testLogging { showStandardStreams = true } }`
at the bottom of the file (fully-qualified, no new import needed at file scope). TODO-marked,
diagnostic-only — this makes ALL test tasks in the module (Android host/device tests too) more
verbose in CI, which is an acceptable, easily-reverted side effect for a temporary diagnostic change,
not something to leave in permanently.

**Next CI run**: `logOsStatusDiagnostic()`'s `println` output should now actually appear in the log.
Grep for `OSStatus=`. If it's *still* not there, the next hypothesis is that `showStandardStreams`
doesn't apply to `KotlinNativeTest` the way it does to the JVM `Test` task (some Kotlin/Native task
types are known to diverge from `AbstractTestTask`'s full contract) — in that case, stop trying to
get stdout out of Gradle's test task entirely and instead have the CI step itself locate and `cat`
the generated `build/reports/tests/iosSimulatorArm64Test/**` (or the raw XML under
`build/test-results/iosSimulatorArm64Test/`) as a separate workflow step after the (failing)
`./gradlew` invocation, using `if: always()` so it still runs on failure.

## Not done

- Nothing from the plan was skipped.
- Not verified: actually running `connectedAndroidDeviceTest` on a real device/emulator — no emulator
  available in this local environment. `iosSimulatorArm64Test` *was* run for real via CI (see above)
  and is pending a re-run to confirm the fix. Only compilation was verified locally for both. The CI
  workflow YAML has been proven to execute (the `ios-test` job ran, just failed on real test logic,
  not YAML/infra) — `android-instrumented-test` and the fixed `ios-test` still need a green run to
  fully close this out.

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

## Post-merge fix #6: the real OSStatus, and the actual fix

CI run after fix #5 (`10a568d`, `showStandardStreams = true`) finally surfaced the diagnostic
`println` in the job log: `KeychainSecureStorage.clear(): SecItemDelete failed with
OSStatus=-25291`. All 6 `KeychainSecureStorageTest` failures trace to the same `@AfterTest
storage.clear()` call — none of the test bodies themselves failed (no `WriteFailure`/`ReadFailure`
anywhere in the log), only the teardown.

`-25291` is `errSecNotAvailable` ("No keychain is available"). Per Apple's own forum guidance this
status is *not* typically an API-usage bug (unlike e.g. `errSecParam`), and there's a documented
precedent (`wix/AppleSimulatorUtils#62`) of bulk/"delete all" Keychain operations misbehaving
specifically on the iOS Simulator's data-protection keychain. That matches the evidence exactly:
`clear()`'s query was the *only* one in the file using a "match everything under this service"
shape (`kSecClass` + `kSecAttrService`, no `kSecAttrAccount`, `kSecMatchLimitAll`) — every other
query (`put`/`get`/`remove`, via `baseQuery()`) includes the full primary key (`+kSecAttrAccount`)
and was working fine in the same test run, same simulator, same process.

Fix applied in `libs/core_crypto/src/iosMain/kotlin/.../storage/KeychainSecureStorage.kt`: rewrote
`clear()` to stop issuing a single bulk `SecItemDelete` with `kSecMatchLimitAll`. It now loops,
deleting one match at a time with `kSecMatchLimitOne` (the exact query shape already proven to work
elsewhere in this file, minus `kSecAttrAccount`), until `SecItemDelete` returns
`errSecItemNotFound`. Removed the now-unused `kSecMatchLimitAll` import.

Not yet done: this fix has **not been confirmed green in CI** — only reasoned from the log evidence
and corroborating reports, not verified by a real run (no macOS/iOS runtime available in this
session's environment). Kept both diagnostic-only changes from fix #4/#5
(`logOsStatusDiagnostic()`'s `println`, and `showStandardStreams = true` in `build.gradle.kts`) in
place deliberately, in case this fix is wrong and another `OSStatus` shows up.

## Post-merge fix #7: fix #6 was wrong — it's not about `kSecMatchLimitAll` vs `kSecMatchLimitOne`

Fix #6 was committed as `342cf65` (reused the stale commit message from `70571e6` — misleading, but
the diff is fix #6's loop-based `kSecMatchLimitOne` delete) and run in CI. **Still red, same 6 tests,
same `OSStatus=-25291` on `clear()`, on the very first delete attempt.** This falsifies the fix #6
hypothesis: it's not `kSecMatchLimitAll` specifically that's broken. Both a `kSecMatchLimitAll`
query *and* a `kSecMatchLimitOne` query fail identically with `-25291` — the common factor between
them (and the thing that differs from every working query in this file) is that **neither includes
`kSecAttrAccount`**. `put`/`get`/`remove`, all via `baseQuery()`, always supply the full primary key
(class+service+account) and have never failed. So the real rule this simulator/OS enforces (runner
was `macos-26-arm64`, a very new image) appears to be: any `SecItemDelete` (at least) against the
data-protection keychain that omits `kSecAttrAccount` fails with `errSecNotAvailable`, regardless of
match limit.

Fix applied: `clear()` no longer issues *any* account-less `SecItemDelete`. It now calls a new
`matchingAccounts()` (a `SecItemCopyMatching` **read**, `kSecReturnAttributes` + `kSecMatchLimitAll`,
also account-less — this is the one open risk, see below) to enumerate the accounts actually present
under `service`, then deletes each individually through the existing `deleteItem()` — the exact
class+service+account query shape already proven reliable. Added `matchingAccounts()` and a small
`accountAttributeKey` val (`(kSecAttrAccount as NSString) as String`, the documented pattern for
reading an immortal CF framework constant as a Kotlin `String` without touching its refcount —
verified against an external source, not guessed) to look up `kSecAttrAccount` in the
`NSDictionary`-bridged-to-`Map` results. Re-added `kSecMatchLimitAll` and added `kSecReturnAttributes`
imports. Compiles clean: `./gradlew :libs:core_crypto:compileKotlinIosSimulatorArm64` — BUILD
SUCCESSFUL (only pre-existing, unrelated `String`⇄`NSString` cast warnings, same class of warning
the file's existing working code already has at lines 61/172 — Kotlin/Native's front-end can't
statically verify these toll-free-bridged casts and warns regardless of correctness, confirmed
against the file's own already-working precedent, not a new risk).

**Known open risk, not yet resolved**: `matchingAccounts()`'s `SecItemCopyMatching` call is *also*
account-less (class+service only). If this simulator's `-25291` restriction turns out to apply to
*any* account-less `SecItem*` call (not `SecItemDelete` specifically), this read will fail the same
way and `clear()` will still be broken. This has NOT been ruled out — there was no prior account-less
*read* in this file to have already proven or disproven it. Diagnostics are in place either way:
failures here log via `logOsStatusDiagnostic("clear-enumerate", status)`, distinguishable in the CI
log from the old `"clear"` tag.

## Next action

Push this change (currently uncommitted in the working tree) and watch the `ios-test` CI job.

- **Green**: revert the diagnostic-only changes — `logOsStatusDiagnostic()` call sites in
  `remove()`/`clear()`/`matchingAccounts()` back to throwing the relevant `SecureStorageException`
  directly, delete the `logOsStatusDiagnostic()` method itself, and the
  `tasks.withType<AbstractTestTask>` block in `build.gradle.kts` — then this investigation is closed.
  Also still pending regardless: a green run of `android-instrumented-test` (untested against a real
  emulator so far, only compiled locally).
- **Still red, log shows `"clear-enumerate"` with `OSStatus=-25291`**: confirms the open risk above —
  account-less reads are *also* blocked on this simulator/OS, not just deletes. In that case, stop
  trying to enumerate the keychain at all; switch to tracking known keys explicitly (e.g. an
  in-memory `MutableSet<String>` of keys added via `put()`/removed via `remove()`, consulted by
  `clear()` to drive per-key `deleteItem()` calls) — accepting that `clear()` then only wipes entries
  touched by the current `KeychainSecureStorage` instance/process lifetime, not any pre-existing
  Keychain state from an earlier process. That's an actual behavior change worth flagging to whoever
  owns this module before landing it, since it narrows `clear()`'s real-world semantics for
  production consumers, not just test cleanup.
- **Still red, log shows `"clear"` (not `"clear-enumerate"`) with some `OSStatus`**: the enumerate
  step worked, but deleting an enumerated account through `deleteItem()` failed — new data, don't
  guess, read the actual status first.
