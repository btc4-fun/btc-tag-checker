# قرارداد API استعلام موجودی — وضعیت نهایی

این سند جای تصمیم موقت قبلی را می‌گیرد. قرارداد backend حالا تعریف و مستقر شده و اپ ۱.۱.۶ بر پایهٔ آن منتشر شده است.

## مسیرها

```text
GET https://btc4.fun/api/addresses/{address}
GET https://btc4.fun/api/addresses/{address}?balance=1
GET https://btc4.fun/api/wallets/{serial}
GET https://btc4.fun/api/wallets/{serial}?balance=1
```

- بدون پرچم `balance=1` پاسخ دقیقاً سه کلید دارد: `address`, `serial`, `lookup_url`. این شکل برای سازگاری با نسخهٔ ۱.۱.۵ تغییر نکرده و نباید کلید اضافه بگیرد.
- با `balance=1` کلیدهای مجاز پاسخ این‌ها هستند: `address`, `serial`, `lookup_url`, `balance`, `public`. اپ هر کلید دیگری را رد می‌کند.
- بلوک `balance` دقیقاً چهار کلید دارد: `address`, `confirmed_sats`, `pending_delta_sats`, `source`, `fetched_at`. این شکل در کلاینت سخت‌گیرانه بررسی می‌شود.
- سرور در پاسخ `balance=1` کلید `explorer_url` را هم می‌فرستد، ولی اپ آن را نمی‌خواند و در فهرست کلیدهای مجاز نیست؛ پیوند explorer را خود اپ از آدرس می‌سازد.
- وقتی بالادست در دسترس نباشد، سرور بلوک `balance` را با `balance_error` جایگزین و کد 503 می‌دهد؛ اپ این حالت را «سایت فعلاً موجودی را نمی‌دهد» نشان می‌دهد، نه صفر.
- بلوک اختیاری `public` فقط وقتی پر می‌شود که دست‌کم یکی از سه فیلد عمومی مقدار داشته باشد: `notes`, `site{key,url}`, `image_url`. مقدار خالی به معنی حذف کلید است، نه رشتهٔ خالی.

## خطاها

```text
400  ورودی نامعتبر
404  not_found  → در اپ «آدرس ثبت‌نشده» یا «سریالی با این شماره پیدا نشد»
429  محدودیت نرخ با Retry-After
503  balance_unavailable یا unavailable
```

هیچ خطایی به موجودی صفر تبدیل نمی‌شود. پاسخ دیرهنگام پس از لغو استفاده نمی‌شود و تکرار خودکار وجود ندارد.

## امنیت و محدودیت

- سهمیهٔ نرخ بین مسیرهای آدرس و سریال مشترک است و خودِ اپ هیچ درخواست خودکاری نمی‌فرستد.
- سرور فقط ستون‌های عمومی را از جدول کیف‌پول‌ها می‌خواند؛ ستون‌های خصوصی تماس هرگز در پاسخ نیستند و نقش وب حتی گرنت خواندن آن‌ها را ندارد.
- نشانی تصویر و سایت فقط به‌صورت پیوند متنی با تأیید و allowlist نمایش داده می‌شوند؛ بارگذاری درون‌برنامه‌ای انجام نمی‌شود.

## شواهد

```text
core/src/main/java/com/saebm/btcnfccustomer/core/RequestPolicy.java
core/src/test/java/com/saebm/btcnfccustomer/core/RequestPolicyTest.java
core/src/test/java/com/saebm/btcnfccustomer/core/LookupClientTest.java
tests/test_public_address_contract.py
```

قرارداد سرور در مخزن سایت مستند شده است:

```text
/home/saeb/PycharmProjects/btc4fun/btc_envelope_maker/docs/address-serial-api.md
```
