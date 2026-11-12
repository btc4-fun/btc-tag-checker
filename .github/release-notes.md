# یادداشت نسخهٔ برنامهٔ مشتری

نسخه و کد نصب:

```text
1.1.10
versionCode: 16
```

این نسخه دو تغییر دارد: هم‌خوان‌شدن جدول هزینه‌ها با قرارداد تازهٔ سایت، و پایدارکردن خواندن برچسب.

هزینه‌ها: مواد پاکت یازده سنت (مهر سه، کاغذ یک، چاپ یک و پاکت شش سنت)، پست هفتاد و پنج سنت و برچسب دوازده سنت. حداقل بسته از پانصد و یک ساتوشی شروع می‌شود و سقف جدول سه میلیون تومان است. برنامه پاسخ نرخ را پیش از نمایش اعتبارسنجی می‌کند: نام کلیدهای هزینه (`materials_sats`، `post_sats` و `operator_toman`)، تعداد ردیف‌ها، و برابری هر ردیف با محاسبهٔ همان نرخ. پاسخ ناسازگار کنار گذاشته می‌شود و هیچ قیمت صفر یا جایگزینی ساخته نمی‌شود.

`operator_toman` درآمد گرداننده به تومان است: پانزده درصدِ اصل و هزینه‌ها، تبدیل‌شده به تومان و گردشده به بالا. برنامه آن را از `operator_sats` و نرخ همان لحظه خودش حساب می‌کند و با مقدار سرور مقایسه می‌کند؛ ردیف بدون این کلید یا با مقدار متفاوت پذیرفته نمی‌شود.

خواندن برچسب: حالت خوانندهٔ NFC پس از خواندن روشن می‌ماند و فقط با خروج از صفحه آزاد می‌شود. اگر میدان در همان لحظه که برچسب روی گوشی است آزاد شود، اندروید لینک ذخیره‌شده روی برچسب را به مرورگر می‌دهد، این صفحه متوقف می‌شود و نتیجهٔ خواندن از دست می‌رود. هر خواندن نشانهٔ یکتای خودش را دارد و پاسخ دیررسِ خواندن قدیمی کنار گذاشته می‌شود؛ نتیجهٔ تازه را بازنویسی نمی‌کند و درخواستی را که کاربر لغو کرده دوباره فعال نمی‌کند. گارد این رفتار در `core` آزمون رفتاری دارد، نه فقط آزمون متن.

The customer app now matches the site's new cost contract: envelope materials 0.11 USD (0.03 seal, 0.01 paper, 0.01 print, 0.06 envelope), postage 0.75 USD and tag 0.12 USD. The smallest package starts at 501 satoshis and the table ceiling is 3,000,000 toman. Every market response is validated before it is shown — the cost key names (`materials_sats`, `post_sats`), the row count, and each row against the same rates — and a mismatching response is dropped instead of being rendered with a zero or invented price.

NFC reader mode now stays enabled after a read and is released only when the user leaves the screen. Releasing the field while the tag is still against the phone hands the tag's stored link to Android, which opens a browser, pauses this screen and loses the scan. Each scan carries its own immutable token, so a late callback from an older scan is dropped instead of overwriting a newer result or re-enabling a request the user cancelled. That guard lives in `core` and has behavioural unit tests, not only source-text assertions.

این یادداشت فقط تغییرات و شرط‌های بررسی نسخهٔ ۱.۱.۱۰ را ثبت می‌کند و به معنی ساخته‌شدن، امضاشدن یا انتشار فایل APK جدید نیست.

آزمون سیاست پنجره در همان دروازهٔ شبیه‌ساز میزبانی‌شده اجرا می‌شود. این آزمون روی پنجرهٔ واقعی شبیه‌ساز سیاست انتشار و راه‌اندازی فعالیت آزمایشی را بررسی می‌کند؛ اثبات اجرای بسته روی گوشی نیست.

## شرط انتشار و مرز شواهد

اجرای دستی امضاشده باید پیش از ساخت برچسب عمومی موفق شود. آزمون میزبان و بررسی ایستا و وابستگی سخت‌گیرانه در هر دو نوع ساخت اجرا می‌شوند. شناسهٔ بسته و گواهی ثابت تغییر نکرده‌اند. گزارش اجرای واقعی و فایل دانلودشده معیار پذیرش‌اند؛ این یادداشت به‌تنهایی موفقیت آن‌ها را اعلام نمی‌کند.

گوشی و تگ فیزیکی، ارتقای نصب تولیدی و حداقل نسخهٔ اندروید در این دور آزموده نشده‌اند. خواندن فیزیکی برچسب و رفتار میدان NFC روی گوشی واقعی تأیید نشده است؛ آزمون خودکار فقط منطق گارد را می‌سنجد. آزمون شبیه‌ساز با آزمون گوشی واقعی تفاوت دارد. نبود کلید معتبر باعث توقف می‌شود و هیچ کلیدی ساخته یا به دستگاه محلی منتقل نمی‌شود.
