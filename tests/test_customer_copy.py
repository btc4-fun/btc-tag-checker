"""Native-copy contract: plain guidance without weakening privacy or status meaning."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java/com/saebm/btcnfccustomer/MainActivity.java'


class CustomerCopy(unittest.TestCase):
    def test_scan_explains_the_action_and_requires_separate_permission(self):
        source = SOURCE.read_text()
        for phrase in [
            'اگر تازه شروع کرده‌اید، از همین‌جا خواندن برچسب را شروع کنید.',
            'تراشهٔ کوچک روی پاکت را با پشت گوشی لمس کنید.',
            'چیزی روی برچسب نوشته یا خودکار ارسال نمی‌شود.',
            '۲. دیدن موجودی و تراکنش‌ها',
            'اجازه می‌دهید؟',
            'فقط همین بار ارسال کن',
            'برنامه خودکار بررسی یا دوباره تلاش نمی‌کند.',
            'سایت آدرس عمومی را برای دریافت اطلاعات به بلاک‌استریم می‌فرستد.',
            'کلید خصوصی و کلمات بازیابی ارسال نمی‌شوند.',
            'این بررسی خصوصی نیست و اصل بودن یا مالکیت را ثابت نمی‌کند.',
        ]:
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, source)

    def test_result_explains_units_confirmation_source_and_errors(self):
        source = SOURCE.read_text()
        for phrase in [
            'ساتوشی واحد کوچک بیت‌کوین است.',
            'تأییدشده یعنی شبکهٔ بیت‌کوین آن را ثبت کرده است.',
            'در انتظار تأیید یعنی هنوز قطعی نشده است.',
            'علامت مثبت یعنی افزایش و علامت منفی یعنی کاهش.',
            'منبع یعنی سرویسی که سایت اطلاعات را از آن گرفته است.',
            'این زمان تضمین نمی‌کند که اطلاعات همین حالا تازه است.',
            'این عدد تضمین نمی‌کند که همهٔ پول همین حالا قابل خرج کردن است.',
            'این پیام به معنی موجودی صفر نیست.',
            'برنامه خودکار دوباره درخواست نمی‌فرستد.',
            'مبلغ هر ردیف فقط تغییر پول این آدرس است، نه کل مبلغ تراکنش.',
        ]:
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, source)
        for jargon in ['اتمی', 'مبدأ یونیکس', 'استنباط', 'نشانگر صفحه']:
            with self.subTest(jargon=jargon):
                self.assertNotIn(jargon, source)

    def test_public_reference_uses_site_without_changing_api_contract(self):
        source = SOURCE.read_text()
        self.assertIn('https://btc.saeb.id.ir/', source)
        self.assertIn('PUBLIC_SITE + page', source)
        self.assertIn('https://btc4.fun/api', (ROOT / 'core/src/main/java/com/saebm/btcnfccustomer/core/RequestPolicy.java').read_text())


if __name__ == '__main__':
    unittest.main()
