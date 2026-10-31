# btc4fun

Java/native Android views, Persian RTL. **Test-only debug artifact**, not a wallet, not an authenticity verifier, and not a device-tested release. Application identity: `com.saebm.btcnfccustomer`; debug identity: `com.saebm.btcnfccustomer.debug`. Android 8/API 26 minimum; target/compile API 35.

The native instructions use short Persian sentences, explain the tag/scan action and satoshi, confirmed/pending amounts and source freshness, and keep the data-sharing and no-authenticity warnings. Website colors, logo, Sahel fonts and safe-area layout are preserved.

## Behavior and privacy

- Explicitly arm NFC reader mode; read only `Tag.getId()` raw bytes, in original order, uppercase contiguous hex. Only 4/7/10-byte identifiers accepted; leading zeroes preserved. Skip NDEF; no tag writing, keys, recovery words, camera or storage permissions.
- Scanning does **not** request the network. A separate Persian consent dialog is required for each initial request and each manual history page. Disclosure: the UID goes to `btc4.fun`; that server sends the associated public Bitcoin address to Blockstream. UID matching does not prove ownership, authenticity, payment or exclusive custody.
- Only `GET https://btc4.fun/api/tags/{UID}` with optional validated `after_txid` is constructed. Default system TLS/hostname checks; no redirects, cookies, credentials, cache, logging interceptor, automatic polling or retries. An explicit interceptor blocks OkHttp's otherwise automatic `503 + Retry-After: 0` follow-up; this has a regression test.
- Connect/read timeout 5 seconds; whole-call timeout 15 seconds; body limit 262144 bytes, strict UTF-8 and JSON. JSON nesting, arrays and field strings bounded; duplicate keys, floats, numeric strings, overflow, wrong scope, unconfirmed history and invalid/repeated cursors fail closed. Integer satoshi amounts never pass through floating point.
- Requests run off the UI thread. Leaving foreground or navigating cancels work and invalidates a generation fence. A late result cannot overwrite current state. Rotation deliberately discards inquiry state rather than restoring identifiers or auto-requesting. Only one history page is retained; each next page replaces the previous page. Starting initial inquiry again resets pagination.
- Confirmed sats and signed pending delta are separate. Confirmed history shows net address delta, not whole transaction value. Source and conservative server request-start timestamp are shown with non-atomic/freshness warnings. No spendability/coinbase maturity claim. A returned continuation is only a candidate; the next page can be empty.
- 400/404/429/503 and malformed/timeout/network responses never become a fake zero balance. Disabled backend (`503 tag_lookup_disabled`) is displayed as unavailable without parsing error bodies. 429 countdown is a local display of suggested waiting, not server enforcement, and never schedules a request.
- No inquiry persistence or raw UID/body application logging. Backup, device transfer content and cleartext disabled. No WebView. Public pages open only from an explicit fixed allowlist in an external browser, with no UID/address appended.

## Bundled offline content

Guide, terms, payment, security and price-link pages are native scrollable text. Snapshot metadata and a verify-current-payment disclaimer appear in each page. The exact public card, numeric sheba and holder are copied from the site's trusted payment partial; **no stale price/rate is bundled**. Prices are only linked to the current `/costs` page. No images, scripts, forms or secret examples are packaged. Hidden interactive questionnaire controls are omitted: offline security is guidance, not an interactive risk score.

The checked-in snapshot is sufficient: building and testing require **no parent checkout** and no Jinja2. Verify it with:

```sh
python3 tools/sync_public_content.py --check
python3 -m unittest discover -s tests -v
```

For an intentional refresh only, install Jinja2 3.1.6 in a separate Python environment and run `python3 tools/sync_public_content.py --source-root /path/to/btc-envelope-maker`. With no argument the tool verifies, rather than regenerates, the bundle. The generator renders a fixed public-template whitelist with `StrictUndefined` and a dictionary loader, then uses stdlib HTMLParser. It never imports the backend app, invokes dynamic prices, queries wallets, downloads pages or reads secret configuration. `app/src/main/assets/public/manifest.json` records template and output SHA-256 digests and UTC snapshot time. Snapshot changes require rebuilding; the APK verifier compares every packaged page with the checked-in snapshot.

## Local build

Use the checked-in, verified Gradle 8.9 wrapper and JDK 17. AGP 8.7.0, platform 35, build tools 36.0.0. No committed `local.properties`; choose an installed SDK via environment.

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
python3 -m unittest discover -s tests -v
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --rerun-tasks --console=plain
```

After dependencies are cached, `--offline` works. Offline is **not** appropriate on a fresh GitHub runner. Runtime dependencies are Gson 2.11.0 and OkHttp 4.12.0; JUnit 4.13.2 and MockWebServer/TLS 4.12.0 are test-only. Pure core tests run on the host JVM; Android variant tests test core wiring, not a running activity. Python tests check manifest/source/CI/content contracts, not rendered UI.

Output: `app/build/outputs/apk/debug/btc4fun-debug.apk` (debug-signed, test-only). Its display label is `btc4fun (Debug)`; the release label is exactly `btc4fun`. Package names and repository `btc-tag-checker` are unchanged. Version code is 3 and version name is `0.1.2` (`0.1.2-test-only` for debug); `app/build.gradle` is their single source. Verify:

```sh
python3 tools/verify_apk.py debug
```

### Wrapper provenance

Generated using the installed official Gradle 8.9 distribution, then compared with the official checksum endpoint. Wrapper JAR SHA-256:

`498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17`

The distribution SHA-256 is pinned in `gradle/wrapper/gradle-wrapper.properties`:

`258e722ec21e955201e31447b0aed14201765a3bfbae296a46cf60b70e66db70`

Sources: `https://services.gradle.org/distributions/gradle-8.9-wrapper.jar.sha256` and `https://services.gradle.org/distributions/gradle-8.9-all.zip.sha256`.

## GitHub Actions

The independent `.github/workflows/android.yml` runs tests, strict lint, debug build and APK validation on pushes to `main`, pull requests and manual runs. All actions are pinned to verified immutable commits. Default permissions are read-only; checkout does not persist credentials. APKs upload only after successful validation; reports upload even after failure, with explicit 14-day retention and missing-file errors. No release publication or deployment is automated. Debug signing certificates on ephemeral runners may differ between runs; update compatibility is not promised.

Manual `signed_release=true` runs a dependent release job **only on main after tests succeed**, using these repository secrets:

- `ANDROID_RELEASE_KEYSTORE_BASE64`: base64 bytes of the existing user signing keystore.
- `ANDROID_RELEASE_STORE_PASSWORD`
- `ANDROID_RELEASE_KEY_ALIAS`
- `ANDROID_RELEASE_KEY_PASSWORD`

The user must set the existing values themselves at https://github.com/btc4-fun/btc-tag-checker/settings/secrets/actions . Stored GitHub secret values cannot be read or copied by API. Never send passwords or key contents in chat. No local release key has been searched for, read, copied or generated during this extraction.

Secrets are available only in the signing step, never job-wide or to pull requests. That step writes the supplied keystore exclusively in the runner temporary directory with mode 0600 and removes it with exit/signal traps. A separate `always()` cleanup is the fallback; abrupt machine termination ultimately relies on ephemeral hosted runner destruction. Missing/invalid values fail closed. Signed output is `app/build/outputs/apk/release/btc4fun-release.apk`. Gradle caching is disabled for the release job, and the signing invocation uses a single-use daemon.

Release verification also requires the certificate SHA-256 below, independently established from the public SMS app release, not inferred from secret names:

`cdee481eac2d81904d0bed6281b956fe56e4f67ca37c5479592f4c89a18912f4`

Reference: https://github.com/sae13/android-sms-to-ntfy/releases/download/android-native-5ce03990e38533b545e665dbc6ffa74029effca6/sms-ntfy-android-native-arm64-v8a.apk

Until a signed customer artifact passes that comparison, reuse of the user's key remains **unverified**. Existing installed customer versions/signers were not inspected; version code advances to 3, but signer-compatible upgrades have not been tested. Never uninstall to bypass a certificate mismatch without the user's explicit data-loss approval.

### Local release gate

For an authorized local release, use a secret manager to supply `ANDROID_RELEASE_KEYSTORE` (an existing keystore path) and the three password/alias variables above, then run `./gradlew :app:assembleRelease`. Do not put values in source, command history or chat. Every requested release task fails without all variables and an existing file. There is no unsigned/debug-key fallback. Missing-credential refusal has been executed; a user-signed release remains unverified. See `VERIFICATION.md` for actual local evidence and remaining hosted/device gates.

## Verification boundaries / next gate

Unit tests include leading-zero UID vectors, exact integer parsing, bounded bodies, JSON errors, fixed-origin/cursor validation, status handling, local TLS slow-drip timeout, cancellation, generation fencing and the no-503-retry case. Synthetic fixtures live exclusively in test sources; none is a live screen fallback. Real sockets are loopback test servers only; no customer wallet was queried.

Prior emulator-only design checks are recorded in `DESIGN-VERIFICATION.md`; current rename/copy checks are recorded in `VERIFICATION.md`. No physical-device NFC scan was performed. Remaining physical-device acceptance: RTL rendering/font scaling/insets, NFC toggled off/on, rapid scans, consent cancellation, rotation/background cancellation, manual pagination and screen-reader behavior. Backend is intentionally gated off until the parent enables/deploys it. Do not claim end-to-end service operation from these local tests. Java compilation emits a compatibility deprecation note for the older platform inset API; strict Android lint passes.
