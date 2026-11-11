# قراردادهای میان مخزن‌ها و ناهمخوانی‌های مشاهده‌شده

## دامنه و اعتبار شواهد

این سند توصیف وضعیت مشاهده‌شده در منبع است؛ نتیجهٔ اجرای برنامه، ساخت، آزمون میزبان، آزمون دستگاه یا تأیید استقرار نیست. دادهٔ مشتری، خروجی خصوصی، فایل تنظیمات محلی و کلید امضا خوانده نشده‌اند. تغییر قبلی فایل نادیده‌گیری دست‌نخورده مانده است. فقط سندهای تازه در پوشهٔ خروجی نوشته شده‌اند.

تاریخ تهیه:

2026-11-11

بازبینی مبنا:

8301cc651703c223032e983dd216125f1d3640d1

## مالکیت و حدود نتیجه

این سند فقط رفتار مصرف‌کنندهٔ اندروید را از منبع همین مخزن ثبت می‌کند. پیاده‌سازی مخزن سایت، تولیدکنندهٔ پاکت و برنامهٔ دیگر اینجا بررسی نشده است. سازگاری دوطرفه و استقرار باید در جمع‌بندی والد با شواهد مستقل تطبیق داده شوند؛ هیچ قرارداد سمت سرور در این نوبت تأیید نشده است.

## شناسه و آدرس

شناسهٔ خام برچسب از بایت‌ها با حروف بزرگ و ترتیب اصلی ساخته می‌شود؛ طول‌های مجاز و مسیر آن مستقل از سریال چاپی‌اند:

```text
UID: 4 / 7 / 10 bytes -> 8 / 14 / 20 uppercase hex characters
GET https://btc4.fun/api/tags/{UID}
GET https://btc4.fun/api/tags/{UID}?after_txid={64-lowercase-hex}
```

[Source](../core/src/main/java/com/saebm/btcnfccustomer/core/Uid.java#L4-L8)

[Source](../core/src/main/java/com/saebm/btcnfccustomer/core/RequestPolicy.java#L15-L18)

سریال چاپی هر رشتهٔ دلخواه نیست: نخستین نویسه حرف یا رقم، طول حداکثر شصت‌وچهار و شکل دارای پسوند تاریخ‌مانند طبق عبارت منظم منبع است. همان عبارت باید با تولیدکننده و سایت تطبیق داده شود؛ شناسهٔ خام برچسب جای آن نیست. مرجع دقیق عبارت به‌جای بازنویسی ناقص:

[Source](../core/src/main/java/com/saebm/btcnfccustomer/core/RequestPolicy.java#L7-L28)

آدرس پذیرفته‌شده فقط شبکهٔ اصلی، حروف کوچک، گواه نسخهٔ صفر و بیست‌بایتی با کنترل جمع معتبر است؛ دو نمونهٔ مشخص نیز صریحاً رد می‌شوند. این اعتبارسنج، پشتیبانی از همهٔ آدرس‌های معتبر بیت‌کوین نیست.

```text
bc1q...
length = 42
```

[Source](../core/src/main/java/com/saebm/btcnfccustomer/core/BitcoinAddress.java#L7-L20)

## نگاشت عمومی و پاسخ موجودی

```text
GET https://btc4.fun/api/wallets/{serial}[?balance=1]
GET https://btc4.fun/api/addresses/{address}[?balance=1]
without flag: address, serial, lookup_url
with flag allowed: address, serial, lookup_url, balance, public
lookup_url = https://btc4.fun/s/{returnedSerial}
balance exact: address, confirmed_sats, pending_delta_sats, source, fetched_at
```

تطبیق مقدار درخواستی با پاسخ، قالب آدرس و سریال، و نشانی دقیق صفحه لازم است. کلید ناشناختهٔ ریشه رد می‌شود؛ برای بلوک موجودی دقیقاً پنج کلید لازم است. اعداد باید عدد صحیح واقعی باشند، نه متن، اعشار یا نمای علمی؛ موجودی تأییدشده منفی نیست. کلید تکراری، دادهٔ پس از سند و عمق بیش از حد رد می‌شوند. در شاخهٔ دارای پرچم، تجزیه‌گر وجود بلوک موجودی را اجباری نمی‌کند، اما رابطِ مسیر موفق به آن دسترسی بی‌قید دارد؛ این مرز نیازمند تطبیق سرور است، نه مجوز حذف بلوک.

[Source](../core/src/main/java/com/saebm/btcnfccustomer/core/LookupClient.java#L166-L218)

[Source](../core/src/main/java/com/saebm/btcnfccustomer/core/LookupClient.java#L309-L371)

[Source](../app/src/main/java/com/saebm/btcnfccustomer/MainActivity.java#L453-L454)

پاسخ خدمت‌ناموجود تنها وقتی به نگاشت قابل نمایش تبدیل می‌شود که نشانگر خطای مشخص و نگاشت سالم داشته باشد؛ وجود بلوک موجودی یا عمومی در آن شاخه رد می‌شود. بنابراین نظر بالای استثنا که از حفظ اطلاعات عمومی سخن می‌گوید با کنترل واقعی یکسان نیست.

```text
HTTP 503
error = balance_unavailable
remaining keys = address, serial, lookup_url
no balance, no public
```

[Source](../core/src/main/java/com/saebm/btcnfccustomer/core/LookupClient.java#L149-L161)

## فرادادهٔ عمومی فروشنده

```text
public allowed: notes, site, image_url, links
notes: <= 280 Java characters
site exact keys: key, url
site.key: [A-Za-z0-9._-]{1,64}
site.url: HTTPS, any parsed host, no port/userinfo/query/fragment
image_url: same URL restrictions, host btc4.fun only
links: object; <= 20 accepted entries; label <= 32; value <= 512
```

یادداشت متن است، نه محتوای فعال. مقدار غایب، تهی یا رشتهٔ خالی برای متن اختیاری حذف می‌شود؛ نویسه‌های کنترلی و قالب‌دهی در آن‌ها مجاز نیست. تصویر نامعتبر حذف می‌شود، اما سایت نامعتبر می‌تواند کل پاسخ را رد کند. تصویر فقط به شکل نشانی و دکمهٔ مرورگر عرضه می‌شود و درون برنامه بارگیری نمی‌شود. پذیرش دامنهٔ دلخواه برای سایت فروشنده را نباید با فهرست ثابت دامنه‌های اسکن یا تصویر اشتباه گرفت.

[Source](../core/src/main/java/com/saebm/btcnfccustomer/core/LookupClient.java#L199-L218)

[Source](../core/src/main/java/com/saebm/btcnfccustomer/core/LookupClient.java#L298-L305)

[Source](../core/src/main/java/com/saebm/btcnfccustomer/core/LookupClient.java#L351-L364)

[Source](../app/src/main/java/com/saebm/btcnfccustomer/MainActivity.java#L468-L495)

پیوندهای فروشنده در تجزیه‌گر، نشانی امن، رایانامه و تلفن را می‌پذیرند و عضو نامعتبر را جدا حذف می‌کنند. اما دکمهٔ همین کارت همگی را به بازکننده‌ای می‌فرستد که فقط نشانی امن دارای میزبان را قبول می‌کند؛ رایانامه، تلفن و بعضی میزبان‌های خاص پذیرفته‌شده در هسته، در رابط باز نمی‌شوند. این شکاف رفتار موجود است، نه قابلیت تکمیل‌شدهٔ تماس. مسیر تماس عمومی برنامه جداست و برای رایانامه از عمل ارسال به استفاده می‌کند؛ پیام را خودکار ارسال و شناسهٔ کیف پول را ضمیمه نمی‌کند.

[Source](../core/src/main/java/com/saebm/btcnfccustomer/core/LookupClient.java#L227-L285)

[Source](../app/src/main/java/com/saebm/btcnfccustomer/MainActivity.java#L481-L507)

[Source](../app/src/main/java/com/saebm/btcnfccustomer/MainActivity.java#L704-L734)

## نرخ و محتوای عمومی

مسیر نرخ از سایت دریافت می‌شود؛ نشانی‌های منبع قیمت و کارمزد در پاسخ باید با ثابت‌های هسته تطبیق کنند. جدول اقتصادی الزامی است؛ انتخاب‌های متوسط و سریع فقط در صورت معتبر بودن پذیرفته می‌شوند. ضریب صرافی، گرداننده، چاپ، برچسب، اندازهٔ فرضی تراکنش و گردکردن باید با سایت و بردارهای مشترک تطبیق شوند؛ تغییر در متن بدون تغییر محاسبه کافی نیست.

```text
GET https://btc4.fun/api/market
exchange adjustment: 1.005
operator: ceil(subtotal * 15 / 100)
print USD: 0.15
optional tag USD: 0.12
network vbytes: 141
```

[Source](../core/src/main/java/com/saebm/btcnfccustomer/core/MarketClient.java#L20-L22)

[Source](../core/src/main/java/com/saebm/btcnfccustomer/core/MarketResponse.java#L51-L101)

[Source](../core/src/main/java/com/saebm/btcnfccustomer/core/QuoteCalculator.java#L24-L54)

قرارداد منبع بستهٔ آفلاین، پوشهٔ قالب عمومی زیر ریشهٔ صریح مخزن سایت است. قالب تماس باید برای هر راه اجتماعی یک مقصد بدون ابهام بدهد. تولید بسته، اقدام جداگانه و تغییردهندهٔ دارایی‌هاست و اینجا انجام نشده است.

```text
{trusted-source-root}/bitcoin4funsite/templates/
ALLOWED: base.html, contact.html, guide.html, terms.html, security.html,
         _wallet_safety.html, _purchase_warning.html, _payment_details.html
output: app/src/main/assets/public/
```

[Source](../tools/sync_public_content.py#L12-L23)

[Source](../tools/sync_public_content.py#L53-L114)

## ناهمخوانی مستند قدیمی؛ نیازمند جمع‌بندی والد

سند موجود دربارهٔ بلوک موجودی می‌گوید «چهار کلید» اما پنج نام می‌آورد. همچنین از ارسال کلید کاوشگر و جایگزینی با خطای موجودی سخن می‌گوید؛ تجزیه‌گر فعلی کلید اضافهٔ کاوشگر و چنین شکل خطایی را نمی‌پذیرد. این‌ها شواهد تعارض سند و مصرف‌کننده‌اند، نه اثبات خروجی فعلی سرور. سند موجود اصلاح نشده است.

```text
explorer_url
balance_error
```

[Source](../docs/API-DECISION.fa.md#L14-L19)

[Source](../core/src/main/java/com/saebm/btcnfccustomer/core/LookupClient.java#L149-L196)

## ریسک‌های استاتیک و تصمیم‌های باز؛ بدون اجرا و بدون تصویب محصول

- واحد زمان استعلام عمومی باید بررسی شود: زمان دریافت به صورت نانوثانیه تزریق می‌شود، ولی زمان ارزیابی تازگی بر حسب میلی‌ثانیه داده می‌شود؛ کلاس تازگی اختلاف را مستقیم مقایسه می‌کند. این مشاهدهٔ منبع است و نتیجهٔ آزمون نیست.
- الزام حضور موجودی در پاسخ موفق دارای پرچم، شکل دقیق خطای بالادست و امکان حفظ فراداده در خطا باید میان مصرف‌کننده و سایت حل شوند.
- رفتار مطلوب رایانامه و تلفن فروشنده، سیاست پذیرش میزبان‌های ویژه و ادعای بسته‌شدن همهٔ پنجره‌های رضایت تصمیم باز هستند؛ این سند مجوز گسترش مقصدها نیست.
- پشتیبانی از گونه‌های بیشتر آدرس، بارگیری درون‌برنامه‌ای تصویر، بازآزمایی خودکار و تبدیل برنامه به کیف پول امضاکننده، نیازمندی تأییدشده نیستند.

[Source](../app/src/main/java/com/saebm/btcnfccustomer/MainActivity.java#L418)

[Source](../app/src/main/java/com/saebm/btcnfccustomer/MainActivity.java#L454)

[Source](../core/src/main/java/com/saebm/btcnfccustomer/core/Freshness.java#L10-L25)
