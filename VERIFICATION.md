# BTC Tag Checker — local verification

This records the independent-project preparation, not a hosted CI run or device test.

## Final artifact

- Path: `app/build/outputs/apk/debug/BTC-Tag-Checker-debug.apk`
- Size: 1491362 bytes.
- SHA-256: `ba74b5b8c91ac2958e53ee2606ad43a000897f5774b2c46ba22e7d8a43ace28d`
- Verified package: `com.saebm.btcnfccustomer.debug`; versionCode 1; versionName `0.1.0-test-only`; minimum API 26; target API 35.
- Verified label: `BTC Tag Checker (Debug)`.
- ZIP integrity and every bundled public-content byte verified against the current checkout.
- APK Signature Scheme v2 verified; one Android Debug signer. Certificate SHA-256: `348ff59439b64016ce70fdb166a84bb2343c4147f135f90d624885682517fd43`.
- Only INTERNET and NFC permissions. This is not a user-key-signed release.

## Executed local gates

- Gradle 8.9 wrapper, JDK 17, AGP 8.7.0, Android platform 35, build tools 36.0.0.
- `python3 -m unittest discover -s tests -v`: 10 tests passed (six original contracts adapted/preserved, four additional independence, artifact, explicit-refresh and signing-step checks).
- `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --rerun-tasks --console=plain`: BUILD SUCCESSFUL; 51 actionable tasks executed.
- Fresh JUnit XML aggregate: 20 tests, zero failures/errors/skips (19 core, one Android variant core-wiring test).
- Strict Android lint XML: zero issues. Java compiler reports a pre-existing deprecated Android API usage; it is not a lint failure.
- Actual `:app:assembleRelease` invocation with all signing variables absent: BUILD FAILED at the explicit signing guard; no release APK created.
- Actual workflow signing shell executed for each of the four absent-secret cases using synthetic values for the others: failed closed and left no temporary keystore. No real release key was read or generated.
- Workflow YAML parsed; all embedded shell scripts passed `bash -n`; no job-level secret environment. Contract tests check immutable action pins, triggers, main-only release dependency, secret scope, cleanup and certificate gate.
- Python independence check copies tools and bundled content into an unrelated temporary directory, verifies successfully without a parent checkout, then confirms tampered content is rejected. Ordinary checks use Python stdlib only. Optional refresh uses Jinja2 3.1.6.
- Actual artifact verifier passed ZIP, Gradle metadata/identity, display label, bundled content and Android signature checks. A deliberately wrong identity or certificate is rejected by regression tests.
- No separate proxy/no-proxy network claim: final Gradle execution used the normal environment and existing dependency cache, without forcing offline mode.

## Transfer and content provenance

39 explicitly allowlisted source/build-wrapper/test/documentation files were copied non-destructively. Every initial destination SHA-256 matched its source. Build directories, Gradle state, bootstrap wrapper duplicates, caches, local properties, logs and signing files were excluded. Original app/backend/seller and the old workflow were not modified or deleted.

Local ignored evidence is under `.verification/`: `transfer-parity.json`, RED/GREEN logs, `gradle-debug.log`, `release-refused.log`, `apk-verification.log`, and `final-summary.json`. The initial hash inventory is retained even for files intentionally adapted afterward. Source files were rechecked against it and remained unchanged by this work.

The public snapshot was intentionally refreshed from the explicit read-only parent template allowlist after detecting existing base/terms changes. Snapshot UTC: `2026-10-31T17:27:33+00:00`. Source digest: `4ba8adad364868eff4461c6ac4f6726915f7d979cfc7cb50f041564ea9a2f3f2`. Full security guidance and current public payment disclaimer are included; no prices, scripts, images, backend configuration or real wallet data were copied. Further upstream edits do not silently change this bundle.

Wrapper JAR SHA-256 is `498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17`; distribution checksum remains pinned in wrapper properties. The wrapper bytes were preserved from the previously verified source.

Action commits resolved through GitHub's commit API during preparation:

| Action | Reference resolved | Immutable commit |
| --- | --- | --- |
| actions/checkout | v4 | `11d5960a326750d5838078e36cf38b85af677262` |
| actions/setup-java | v4 | `cf277c60eb25467037889841efdb72551f06f6c3` |
| android-actions/setup-android | v3 | `9fc6c4e9069bf8d3d10b2204b1fb8f6ef7065407` |
| gradle/actions/setup-gradle | v4 (gradle/actions repository) | `ed408507eac070d1f99cc633dbcf757c94c7933a` |
| actions/upload-artifact | v4 | `ea165f8d65b6e75b540449e92b4886f43607fa02` |

## Remaining boundaries

Local Git was initialized on `main`; this preparation did not commit or push. Hosted runs, remote SHA equality and downloading/verifying an APK from that exact run belong to the publication gate, not this local report. The user must supply the four existing signing secrets; workflow authorization and secret setup are external prerequisites.

The release workflow compares its APK signer against the public SMS release certificate documented in README. No customer release has yet passed that comparison; user-key reuse remains unverified. No installed app/version/signature was inspected, and no upgrade compatibility or version increment is claimed.

No emulator, physical device installation, NFC scan, rendered RTL/UI, wireless connection or deployed backend end-to-end test was performed. Host JVM/source contracts are not Android instrumentation. No live customer wallet was queried. Backend availability is not established by these tests.
