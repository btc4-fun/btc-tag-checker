# اعتبارسنجی سند و نقشهٔ اجرای عمومی

## مرز ادعا

در این نوبت هیچ ساخت، آزمون برنامه یا میزبان، نصب وابستگی، ارتباط شبکه، اقدام دستگاه و اجرای انتشار انجام نشده است. خروجی موجود در پوشه‌های ساخت خوانده و به عنوان نتیجهٔ جاری استفاده نشده است. این سند مسیرهای قابل مشاهده در منبع را ثبت می‌کند، نه موفقیت اجرای آن‌ها.

## فرمان‌های عمومی از گردش یکپارچه‌سازی

این فرمان بررسی یکپارچگی داخلی دارایی آفلاین است و بدون مخزن منبع و وابستگی قالب‌ساز کار می‌کند؛ برای تأیید تازگی محتوای سایت کافی نیست. در این نوبت اجرا نشده است.

```sh
python3 tools/sync_public_content.py --check
```

[Source](../tools/sync_public_content.py#L15-L24)

[Source](../tools/sync_public_content.py#L75-L84)

فرمان کشف قراردادهای پایتون و فرمان ساخت و آزمون عادی در گردش عمومی:

```sh
python3 -m unittest discover -s tests -v
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --dependency-verification strict --no-daemon --rerun-tasks --console=plain
```

[Source](../.github/workflows/android.yml#L31-L51)

گردش عمومی پیش از قراردادهای پایتون، وابستگی قالب‌ساز با نسخهٔ ثابت نصب می‌کند؛ این نوبت نصب انجام نشده است. هسته آزمون‌های واحد انتقال، اعتبارسنجی، تجزیه و محاسبه دارد؛ قراردادهای پایتون عمدتاً منبع، دارایی و سیاست ساخت را بررسی می‌کنند و جای رفتار واقعی اندروید نیستند.

[Source](../core/build.gradle#L1-L3)

[Source](../tests/test_public_address_contract.py#L27-L75)

[Source](../tests/test_contact_destination_contract.py#L8-L21)

ساخت آزمون دستگاه، اجرای کنترل تعامل و کنترل پنجره در گردش عمومی پس از آماده‌سازی شبیه‌ساز مشخص آمده است. این‌ها نیازمند دستگاه هدف صریح هستند و در این مأموریت مجاز یا اجراشده نیستند.

```sh
./gradlew :app:assembleDebugAndroidTest --dependency-verification strict --no-daemon --no-parallel --max-workers=1 --console=plain
python3 tools/run_device_checks.py --serial emulator-5554
python3 tools/verify_window_policy.py --serial emulator-5554
python3 tools/verify_apk.py debug
```

[Source](../.github/workflows/android.yml#L52-L68)

## امنیت انتشار

ساخت انتشار بدون تمام مقادیر امضا و فایل کلید موجود عمداً متوقف می‌شود؛ کلید تازه تولید و کلید اشکال‌زدایی جایگزین نمی‌شود. گردش انتشار پس از کنترل عمومی، رویداد مورد اعتماد را پیش از دسترسی به راز بررسی می‌کند و نتیجهٔ امضا را با گواهی مورد انتظار تطبیق می‌دهد. هیچ راز، فایل کلید یا اثر امضاشده در این کار خوانده نشده است. فرمان انتشار برای اجرا پیشنهاد نشده است.

[Source](../app/build.gradle#L2-L3)

[Source](../app/build.gradle#L34-L38)

[Source](../.github/workflows/android.yml#L90-L114)

[Source](../.github/workflows/android.yml#L159-L179)

## کنترل سندهای تازه

کنترل واقعی این نوبت: وجود همهٔ مقصدهای محلی پیوندها، قرارگرفتن شمارهٔ خط در محدودهٔ فایل هدف، نبود خط دارای نثر فارسی و شناسهٔ لاتین، نبود نویسهٔ کنترل جهت در سندهای تازه، نبود مسیر پیوند خروجی از مخزن و نبود تغییر ردیابی‌شدهٔ تازه جز تغییر قبلی نادیده‌گیری. نتیجهٔ شمارش و چکیدهٔ بایت‌ها در گزارش بازگشتی عامل آمده است. این کنترل، اجرای آزمون پروژه نیست.

## محدودیت‌ها و موارد باقی‌مانده

- گردش نصب‌شده، تغییر بخش راهنمای عامل را مقصد اصلی می‌داند؛ به دلیل محدودیت کاربر، آن بخش و دستورهای موجود خوانده یا اصلاح نشده‌اند و جذب زمینه به آن انجام نشده است.
- دانش موجودِ قرارداد فقط به عنوان شاهد تعارض بررسی شد؛ ادعای استقرار آن تأیید نشده است.
- سمت سایت، چاپ و دیگر برنامه‌ها باید به دست عامل والد تطبیق داده شوند؛ این کار مرز نوشتن همان مخزن را رعایت کرده است.
- شکاف واحد زمان، نبود الزام موجودی در تجزیه‌گر، ناهمخوانی پیوند تماس و شکل پاسخ خطا، یافته‌های استاتیک‌اند؛ برای تبدیل به عیب تأییدشده به کار جداگانه و اجرای مجاز نیاز دارند.

[Contracts](cross-repo-contracts.md)

## نوبت اجرا و انتشار ۱.۱.۱۰

این بخش نتیجهٔ اجرای واقعی همین نوبت است و جای بخش‌های بالا را نمی‌گیرد؛ بخش‌های بالا وضعیت پیش از اجرا را ثبت می‌کنند.

فرمان‌های اجراشده و نتیجهٔ واقعی آن‌ها:

```sh
python3 tools/sync_public_content.py --check          # Bundled snapshot verified
python3 -m unittest discover -s tests -q              # Ran 50 tests, OK
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
                                                      # BUILD SUCCESSFUL, 53 tasks
python3 tools/verify_apk.py debug                     # versionCode 16, 1.1.10-test-only
git diff --check                                      # clean
```

شمارش آزمون‌های هسته از گزارش XML: ۶۰ آزمون، صفر شکست. `ScanGateTest` پنج آزمون رفتاری دارد و `QuoteCalculatorTest` سه آزمون. آزمون جهش انجام شد: بازگرداندن حداقل به ۵۰۰ ساتوشی هر سه آزمون `QuoteCalculatorTest` را شکست، پس بردارها واقعاً قرارداد را می‌سنجند.

`tools/generate_tagged_quote_vectors.py --check` بردارهای مستقل را از یک اوراکل `fractions.Fraction` بازتولید می‌کند و کد محصول را وارد نمی‌کند؛ بردارهای بودجه با جست‌وجوی دودویی معکوس خود اوراکل ساخته می‌شوند، نه با گذاشتن بودجه به‌جای اصل مبلغ.

آنچه تأیید نشده است: خواندن فیزیکی برچسب، رفتار میدان NFC روی گوشی واقعی، ارتقای نصب تولیدی و اجرای شبیه‌ساز. این‌ها با آزمون میزبان اثبات نمی‌شوند.

## فراخوانی bmad-help در همین مخزن

کاتالوگ محلی و پیکربندی حل‌شده بررسی شدند:

```text
_bmad/_config/bmad-help.csv
_bmad/scripts/resolve_config.py --project-root .
```

نتیجهٔ ارزیابی وضعیت: در `_bmad-output` فقط سندهای زمینه و قرارداد وجود دارد؛ `planning-artifacts` و `implementation-artifacts` خالی‌اند. یعنی دروازه‌های الزامی کاتالوگ (`bmad-prd`، `bmad-architecture`، `bmad-create-epics-and-stories`، `bmad-sprint-planning`) برای این مخزن اجرا نشده‌اند و کار جاری در مرحلهٔ `ship` به‌صورت موردی پیش رفته است. این وضعیت ثبت می‌شود و ادعای تکمیل برنامه‌ریزی نمی‌شود.

گام بعدی پیشنهادی طبق کاتالوگ، به ترتیب: `bmad-prd` سپس `bmad-architecture` و `bmad-create-epics-and-stories` و `bmad-sprint-planning` تا `bmad-build`؛ و برای همین تغییر منتشرشده `bmad-code-review` یا `bmad-walkthrough` به‌صورت اختیاری.
