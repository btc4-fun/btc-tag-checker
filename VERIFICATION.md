# btc4fun — local verification

## Current artifact

- Path: `app/build/outputs/apk/debug/btc4fun-debug.apk`
- Size: 1,592,920 bytes.
- SHA-256: `5bab13764c5233e2ffabdd9c618ff76f1e11f9138d91e0279b2a0b72decffc23`
- Package: `com.saebm.btcnfccustomer.debug`; versionCode 3; versionName `0.1.2-test-only`; minimum API 26; target API 35.
- Display label: `btc4fun (Debug)`. Release configuration uses exact lowercase `btc4fun`, package `com.saebm.btcnfccustomer`, version `0.1.2`, output `btc4fun-release.apk`.
- Repository remains `btc-tag-checker`. Signing configuration, package IDs, site palette/logo, Sahel font bytes and system-bar/cutout handling are preserved.
- ZIP integrity, Gradle metadata, display label, public snapshot bytes and APK Signature Scheme v2 passed the actual artifact verifier.
- Android Debug signer certificate SHA-256: `348ff59439b64016ce70fdb166a84bb2343c4147f135f90d624885682517fd43`.
- Permissions remain only INTERNET and NFC. This is not a user-key-signed release.

## Executed gates on final source and snapshot

Toolchain: checked-in Gradle 8.9 wrapper, JDK 17, AGP 8.7.0, platform 35, build tools 36.0.0. Local JDK: `/usr/lib/jvm/java-17-amazon-corretto`; SDK: `/home/saeb/.local/share/android-sdk-refinery-pulse`. No machine paths were added to build configuration.

```sh
python3 -m unittest discover -s tests -v
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --offline --no-daemon --rerun-tasks --console=plain
python3 tools/verify_apk.py debug
```

- Python: 16 tests passed. Rename/version/workflow/verifier contracts were observed failing before implementation. Two new customer-copy contracts failed against the original text, then passed after the rewrite. Logs retain RED evidence.
- Final Gradle run: BUILD SUCCESSFUL, all 75 actionable tasks executed. Fresh JUnit XML: 20 tests, zero failures/errors/skips (19 core, one Android variant wiring test).
- Android lint: zero issues. Java retains the pre-existing deprecated inset API compile note.
- Workflow tests preserve push-to-main, PR and manual triggers; immutable action pins; read-only permissions; opt-in main-only release dependent on debug gates; step-scoped secrets; cleanup; and stable certificate verification. Four missing-secret shell cases fail closed.
- Actual `:app:assembleRelease` with all signing variables removed failed at the explicit signing guard. No replacement key was created or signing material read. Release build/lint/tests cannot run without the stable signing prerequisites; this guard was not bypassed.
- APK verifier ran again after the parent's final public snapshot refresh, not against an earlier build.

## Public content provenance

Parent refreshed `app/src/main/assets/public` at `2026-10-31T18:43:59+00:00`. Source digest:

`46f0083fb527f8ce055ea5c772d2520a31426bd351f85c61414a33d6d5c5b216`

Every packaged public file exactly matches this checked-in snapshot. The native-copy work did not edit `tools/sync_public_content.py` or regenerate the public assets; parent owns those files. This independent repository is the authoritative Android project. Ordinary build/tests require no former parent checkout.

## Emulator evidence

- Existing disposable API 35 x86_64 AVD was used, not a personal device.
- Final application and instrumentation APKs were installed. Both were pulled back and SHA-256 matched to the final local artifacts; evidence: `.verification/btc4fun/installed-artifacts.json`.
- 156 native-view assertions passed at normal and 1.3 font scale: Sahel, safe margins, centered reading width, offline payment paragraph direction, disabled pre-scan requests, consent title/button and cancellation without a network client.
- Fresh portrait and large-font consent screenshots were visually inspected. Persian text and permission disclosures fit; buttons and content clear system bars. The initial emulator System UI non-response overlay was dismissed and captures repeated; obstructed captures are not acceptance evidence.
- Screenshots and logs: ignored `.verification/btc4fun/`, including `portrait-1.0.png`, `consent-1.3.png`, `ui-1.0.log`, `ui-1.3.log`, `python-tests.log`, `gradle.log`, `apk.log`, `identity-red.log`, `copy-red.log`, `release-refused.log`.
- Historical landscape/cutout design tests belong to code 2 and remain explicitly historical in `DESIGN-VERIFICATION.md`; they were not rerun for this copy revision.

## Remaining boundaries

No commit, push, hosted workflow run or publication was performed by this workstream. Parent owns publication. No physical NFC scan, live customer-wallet request, deployed-backend inquiry, physical-device accessibility review or signer-compatible production upgrade was tested. Emulator/source/unit checks do not establish these gates. Signed release remains blocked on the existing user key; no unsigned/debug-key fallback is allowed.

Initial SDK discovery selected an SDK containing build tools 34 rather than 36; switching to the already-installed documented SDK resolved the build without downloads or project changes. Final build was offline with cached dependencies, not evidence of direct/proxy network availability.
