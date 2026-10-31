"""Native-copy contract: plain guidance without weakening privacy or status meaning."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java/com/saebm/btcnfccustomer/MainActivity.java'


class CustomerCopy(unittest.TestCase):
    def test_scan_explains_the_action_and_requires_separate_permission(self):
        source = SOURCE.read_text()
        for phrase in [
            'برچسب همان تراشهٔ کوچک روی پاکت است.',
            'خواندن برچسب یعنی نزدیک کردن پشت گوشی به آن.',
            'چیزی روی برچسب نوشته یا خودکار ارسال نمی‌شود.',
            'دیدن موجودی و تراکنش‌ها',
            'اجازه می‌دهید؟',
            'فقط همین بار ارسال کن',
            'برنامه خودکار بررسی یا دوباره تلاش نمی‌کند.',
            'سایت آدرس عمومی کیف پول را به بلاک‌استریم می‌فرستد.',
            'این سرویس آدرس و درخواست سایت را می‌بیند.',
            'کلید خصوصی و کلمات بازیابی ارسال نمی‌شوند.',
            'این نتیجه اصل بودن پاکت یا مالکیت شما را ثابت نمی‌کند.',
            'تضمین نمی‌کند که فقط شما به پول دسترسی دارید.',
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
        for jargon in ['استعلام', 'اتمی', 'مبدأ یونیکس', 'استنباط', 'نشانگر صفحه']:
            with self.subTest(jargon=jargon):
                self.assertNotIn(jargon, source)


if __name__ == '__main__':
    unittest.main()
