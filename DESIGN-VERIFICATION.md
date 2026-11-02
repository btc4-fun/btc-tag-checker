# Website-matched native design

## حفاظت تصویری انتشار

نسخهٔ انتشار از ثبت تصویر و نمایش در سطح ناامن جلوگیری می‌کند. فقط نسخهٔ آزمایشی با شناسهٔ بستهٔ جداگانه برای اثبات تصویری رابط از این کنترل مستثناست. این استثنا مجوز استفاده از دادهٔ واقعی یا محرمانه در نسخهٔ آزمایشی نیست. قواعد پشتیبان‌گیری ابری و انتقال دستگاه، همهٔ حوزه‌های دادهٔ برنامه را حذف می‌کنند.

```text
FLAG_SECURE
BuildConfig.DEBUG
```


This is the historical code 2 / 0.1.1 design evidence, not a claim about the latest artifact. Current identity, artifact and checks are in `VERIFICATION.md`.

The customer app follows the existing public website, not a separate Material visual system:

- continuous paper `#f7f4eb`, ink `#292922`, muted `#68665c`, burnt-orange `#b94e2d`, surface `#fffcf5`, fine border `#dcd7c9`;
- the website's outlined circular Bitcoin mark, counterclockwise 12-degree tilt, with Persian brand and a separate LTR subtitle; the same vector is the launcher icon;
- original Sahel v3.4.0 regular/bold TTFs, locally bundled with OFL license and pinned provenance;
- flat sections and restrained rules, one white result panel matching `.lookup-result`, no elevation;
- 20dp horizontal reading gutters, 28dp top and 40dp bottom padding **in addition to** system-bar/cutout insets; centered content capped at 720dp for landscape/tablets;
- native buttons, text, and dialogs explicitly receive Sahel; Persian paragraphs and LTR numbers/addresses remain separate.

The website reference files were read-only. Public content snapshots, request transport, NFC callbacks, generation fences, consent gates, and release signing configuration remain unchanged. Only version identity advances to code 2 / 0.1.1. Debug retains its separate package and test-only suffix.

## Font provenance

`app/src/main/assets/licenses/Sahel-SOURCE.txt` records the official v3.4.0 ZIP hash and the exact extracted TTF hashes. `Sahel-LICENSE.txt` is the upstream license. No font is fetched at runtime.

## Checks

```sh
JAVA_HOME=/path/to/jdk17 python3 -m unittest discover -s tests -v
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --offline
```

The new Python palette/layout contracts were observed failing against the old resources/UI before implementation. The font hash test independently pins the bundled bytes. Existing behavioral tests remain intact (the version expectation was updated).

The suspected Java doubled-escape defect is **not a bug**: Java string escaping correctly produces regex newline/whitespace and Unicode-range escapes. A runnable Java probe using the expressions extracted from the actual Activity proves blank-line splitting and Persian/LTR direction. The original expressions were retained; the new test records coverage rather than claiming a bug fix.

## Emulator-only proof

The framework-only `VisualInstrumentation` adds no test library dependency. It checks real native views, font identity, insets and reading width, offline payment paragraph direction, disabled pre-scan request, and consent cancellation without creating a network client. Its synthetic UID is test-only and is never submitted. Screenshot capture also lives only in the test APK, not in the distributable app.

```sh
adb -s emulator-5556 install -r app/build/outputs/apk/debug/btc4fun-debug.apk
adb -s emulator-5556 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5556 shell am instrument -w com.saebm.btcnfccustomer.debug.test/com.saebm.btcnfccustomer.VisualInstrumentation
# Optional capture values: portrait, footer, payment, consent, landscape.
adb -s emulator-5556 shell am instrument -w -e capture portrait com.saebm.btcnfccustomer.debug.test/com.saebm.btcnfccustomer.VisualInstrumentation
```

This is not a real-device NFC scan or a live-server inquiry test. Release signing and publication are intentionally left to the parent workflow; no local release keystore is read.

## Executed results

- JDK 17 / Gradle wrapper: final offline `--rerun-tasks` build succeeded; all 75 tasks executed.
- JUnit: 20 tests, zero failures/errors/skips. Python: 14 tests passed. Android lint: no issues.
- API 35 x86_64 emulator: 140 native-view assertions passed separately in portrait, landscape, 1.3 font-scale, and with the tall-cutout overlay enabled. This proves insets against Android's reported values; it is not proof on every physical notch shape.
- Real emulator screenshots are in ignored `.verification/design/`: `portrait.png`, `footer.png`, `payment.png`, `consent.png`, `landscape.png`, `large-font.png`, `cutout.png`. Captures were visually inspected; a transient blank large-font capture was replaced with a settled fresh launch. RTL dialog title alignment was caught visually, reproduced with a failing instrumentation assertion, fixed, and rechecked.
- APK and instrumentation APK were installed, then each installed APK was read back and SHA-256 matched to its local artifact (`installed-artifacts.json`).
- APK ZIP integrity, signature, version identity, public-content snapshot hashes, and both packaged Sahel TTF byte identities passed.
- Debug APK: `app/build/outputs/apk/debug/BTC-Tag-Checker-debug.apk`, 1,591,396 bytes.
- Debug APK SHA-256: `9dc62d94c71d2a85ff51d3f953c77ea15fe51b3c5ab0c4da6a21e108ae749eff`.
- Signing configuration and stable signing material were not changed. No commit or push was performed.

The initial SDK manager direct download failed; a command-scoped local HTTP proxy recovered the official emulator/image installation. No proxy was added to project configuration. The emulator is a disposable app-specific AVD under `.verification/design/avd-home`, not a personal device. Java reports the existing deprecated-API compile note; Android lint itself is clean.
