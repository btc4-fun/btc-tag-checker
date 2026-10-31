package com.saebm.btcnfccustomer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.saebm.btcnfccustomer.core.Client;
import com.saebm.btcnfccustomer.core.Latest;
import com.saebm.btcnfccustomer.core.RequestPolicy;
import com.saebm.btcnfccustomer.core.Response;
import com.saebm.btcnfccustomer.core.Uid;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Read-only NFC inquiry. All financial data stays in memory and never enters saved state. */
@SuppressLint("SetTextI18n")
public final class MainActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Latest gate = new Latest();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Set<String> cursors = new HashSet<>();
    private NfcAdapter nfc;
    private Client client;
    private Future<?> work;
    private LinearLayout content, result;
    private TextView status;
    private Button scan, request, next;
    private String uid, cursor;
    private boolean active, scanning, busy, inquiry = true;
    private long retryUntil, scanGeneration;
    private Runnable countdown;
    private AlertDialog consent;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        nfc = NfcAdapter.getDefaultAdapter(this);
        showInquiry();
    }
    @Override public void onResume() {
        super.onResume(); active = true;
        if (inquiry && status != null && uid == null) status.setText(nfc == null ? "این دستگاه خوانشگر نزدیک‌برد ندارد؛ راهنماهای آفلاین در دسترس‌اند." : !nfc.isEnabled() ? "خوانشگر نزدیک‌برد خاموش است. آن را در تنظیمات دستگاه روشن کنید." : "برای خواندن شناسه، دکمهٔ اسکن را بزنید. هیچ درخواستی خودکار ارسال نمی‌شود.");
        updateButtons();
    }
    @Override public void onPause() {
        active = false;
        stopReading();
        if (consent != null) { consent.dismiss(); consent = null; }
        cancelRequest();
        if (inquiry && status != null) status.setText("برنامه از پیش‌زمینه خارج شد؛ درخواست جاری لغو شد. برای دریافت اطلاعات تازه دوباره انتخاب کنید.");
        super.onPause();
    }
    @Override public void onDestroy() {
        cancelRequest(); worker.shutdownNow(); main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void screen(String title) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        content.setPadding(dp(20), dp(16), dp(20), dp(24));
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        setContentView(scroll);
        TextView heading = text(content, title, false); heading.setTextSize(25);
    }
    private TextView text(LinearLayout parent, String value, boolean ltr) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(17); view.setTextColor(0xff152b29);
        view.setTextDirection(ltr ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
        view.setLayoutDirection(ltr ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
        view.setGravity(Gravity.START);
        view.setPadding(0, dp(8), 0, dp(8));
        view.setTextIsSelectable(true);
        // Never let Android restore a previous UID/address or response into a new activity.
        view.setSaveEnabled(false);
        parent.addView(view);
        return view;
    }
    private Button button(String label, Runnable action) {
        Button value = new Button(this); value.setText(label); value.setAllCaps(false);
        value.setMinHeight(dp(48)); value.setOnClickListener(view -> action.run()); content.addView(value); return value;
    }
    private void showInquiry() {
        cancelRequest(); stopReading(); inquiry = true; uid = null; cursor = null; cursors.clear();
        screen(getString(R.string.app_name));
        text(content, "این برنامه فقط شناسهٔ عمومی برچسب را می‌خواند؛ چیزی روی برچسب نمی‌نویسد. کلید خصوصی یا کلمات بازیابی را هرگز اینجا وارد نکنید.", false);
        text(content, "شناسهٔ برچسب قابل کپی است؛ تطابق شناسه، اثبات اصالت پاکت، مالکیت یا انحصار دسترسی به دارایی نیست.", false);
        text(content, "پس از رضایت شما، شناسه به سرور سایت ارسال می‌شود. سرور آدرس عمومی مربوط به آن را برای دریافت موجودی و تاریخچه به سرویس زیر می‌فرستد؛ این سرویس می‌تواند آدرس و درخواست سرور را ببیند.", false);
        text(content, "btc4.fun → Blockstream", true);
        scan = button("اسکن برچسب — بدون ارسال خودکار", this::beginScan);
        request = button("رضایت و استعلام همین برچسب", () -> confirmRequest(null));
        status = text(content, "هنوز برچسبی خوانده نشده و نتیجه‌ای دریافت نشده است.", false);
        result = new LinearLayout(this); result.setOrientation(LinearLayout.VERTICAL); content.addView(result);
        next = button("صفحهٔ بعدِ تاریخچه — درخواست دستی", () -> confirmRequest(cursor));
        button("راهنمای آفلاین پاکت", () -> showPublic("guide", "راهنمای پاکت"));
        button("شرایط و مسئولیت‌ها — آفلاین", () -> showPublic("terms", "شرایط و مسئولیت‌ها"));
        button("اطلاعات پرداخت — نسخهٔ آفلاین", () -> showPublic("payment", "اطلاعات پرداخت"));
        button("امنیت و نگهداری — آفلاین", () -> showPublic("security", "امنیت و نگهداری"));
        button("قیمت و هزینه‌ها", () -> showPublic("costs", "قیمت و هزینه‌ها"));
        updateButtons();
    }
    private void updateButtons() {
        if (!inquiry || scan == null) return;
        scan.setEnabled(active && !busy && nfc != null && nfc.isEnabled());
        request.setEnabled(active && uid != null && !busy && !scanning);
        next.setEnabled(active && uid != null && cursor != null && !busy && !scanning);
    }
    private void beginScan() {
        cancelRequest(); uid = null; cursor = null; cursors.clear(); result.removeAllViews();
        if (nfc == null || !nfc.isEnabled()) { status.setText("خوانشگر نزدیک‌برد در دسترس نیست یا خاموش است."); return; }
        final long scanToken = ++scanGeneration;
        scanning = true; status.setText("برچسب را نزدیک دستگاه نگه دارید. فقط شناسه خوانده می‌شود؛ هنوز هیچ درخواست شبکه‌ای ارسال نشده است.");
        updateButtons();
        try {
            nfc.enableReaderMode(this, tag -> {
                String value;
                try { value = Uid.format(tag.getId()); }
                catch (IllegalArgumentException ex) { value = null; }
                final String scanned = value;
                main.post(() -> {
                    if (!active || !scanning || !inquiry || scanGeneration != scanToken) return;
                    stopReading(); uid = scanned;
                    status.setText(scanned == null ? "طول شناسهٔ برچسب پشتیبانی نمی‌شود؛ چیزی ارسال نشد." : "شناسه خوانده شد. برای ارسال آن، دکمهٔ رضایت و استعلام را بزنید.");
                    updateButtons();
                });
            }, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B | NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
        } catch (IllegalStateException | SecurityException ex) { scanning = false; status.setText("فعال‌سازی خوانشگر ممکن نشد."); updateButtons(); }
    }
    private void stopReading() {
        scanGeneration++;
        if (nfc != null && scanning) {
            try { nfc.disableReaderMode(this); } catch (IllegalStateException | SecurityException ignored) { /* No sensitive logs. */ }
        }
        scanning = false;
    }
    private void confirmRequest(String after) {
        if (uid == null || busy || !active) return;
        consent = new AlertDialog.Builder(this)
            .setTitle("اجازهٔ ارسال درخواست عمومی")
            .setMessage("شناسهٔ برچسب به سایت ارسال می‌شود. سرور آدرس عمومی کیف پول را به بلاک‌استریم می‌فرستد. این کار حریم خصوصی آدرس را کاهش می‌دهد. کلید یا کلمات بازیابی ارسال نمی‌شود. هیچ بررسی دوره‌ای یا تلاش مجدد خودکار وجود ندارد. ادامه می‌دهید؟")
            .setNegativeButton("لغو", (dialog, which) -> consent = null)
            .setPositiveButton("ارسال همین درخواست", (dialog, which) -> { consent = null; fetch(after); }).create();
        consent.show();
        consent.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    }
    private void fetch(String after) {
        if (!active || uid == null) return;
        cancelRequest();
        final long generation = gate.begin();
        final String requestedUid = uid;
        final Client transport = new Client(); client = transport; busy = true;
        status.setText("در حال دریافت؛ نتیجهٔ قبلی، اگر نمایش داده شده، تازه نشده است."); updateButtons();
        WeakReference<MainActivity> owner = new WeakReference<>(this);
        work = worker.submit(() -> {
            Response response = null; Exception error = null;
            try { response = transport.fetch(requestedUid, after); } catch (Exception ex) { error = ex; }
            Response delivered = response; Exception failed = error;
            new Handler(Looper.getMainLooper()).post(() -> {
                MainActivity activity = owner.get();
                if (activity == null || !activity.active || !activity.inquiry || !activity.gate.accepts(generation)) return;
                activity.deliver(delivered, failed, after);
            });
        });
    }
    private void cancelRequest() {
        gate.invalidate();
        if (client != null) { client.cancel(); client = null; }
        if (work != null) { work.cancel(true); work = null; }
        if (countdown != null) { main.removeCallbacks(countdown); countdown = null; }
        busy = false;
    }
    private void deliver(Response response, Exception error, String after) {
        busy = false; client = null; work = null;
        if (error != null) {
            if (error instanceof Client.Failure) {
                Client.Failure failure = (Client.Failure) error;
                switch (failure.status) {
                    case 400: status.setText("درخواست یا شناسه از نظر سرور نامعتبر است."); break;
                    case 404: status.setText("برچسب در سرور پیدا نشد؛ یا مسیر سرویس هنوز منتشر نشده است. این پیام به معنی موجودی صفر نیست."); break;
                    case 429: startCountdown(failure.retrySeconds); break;
                    case 503: status.setText("سرویس موقتاً در دسترس نیست. نتیجه یا موجودی صفر استنباط نکنید."); break;
                    default: status.setText("پاسخ ناموفق سرور؛ هیچ نتیجهٔ تازه‌ای دریافت نشد.");
                }
            } else status.setText("دریافت ناموفق: مشکل شبکه، پایان مهلت یا پاسخ نامعتبر. هیچ نتیجهٔ تازه‌ای دریافت نشد؛ تلاش مجدد فقط با انتخاب شماست.");
            updateButtons(); return;
        }
        if (after == null) cursors.clear(); else cursors.add(after);
        cursor = response.nextAfterTxid;
        boolean repeated = cursor != null && cursors.contains(cursor);
        if (repeated) cursor = null;
        result.removeAllViews();
        text(result, "شمارهٔ سریال", false); text(result, response.serial, true);
        text(result, "آدرس عمومی — کامل تطبیق دهید", false); text(result, response.address, true);
        text(result, "موجودی تأییدشده — ساتوشی صحیح", false); text(result, Long.toString(response.confirmedSats), true);
        text(result, "تغییر خالص تأییدنشده — جدا از موجودی، با علامت؛ قطعی نیست", false); text(result, Response.signed(response.pendingDeltaSats), true);
        text(result, "منبع گزارش‌شده توسط سرور", false); text(result, response.source, true);
        text(result, "زمان آغاز دریافت گزارش‌شده توسط سرور؛ نه زمان منبع و نه تضمین تازه‌بودن", false); text(result, response.fetchedAt, true);
        text(result, "داده‌ها هم‌زمان و اتمی نیستند؛ موجودی و تاریخچه ممکن است در زمان‌های متفاوت خوانده شده باشند. تاریخچه فقط تراکنش‌های تأییدشده را نشان می‌دهد؛ تراکنش‌های صف فقط در تغییر خالص موجودی لحاظ شده‌اند.", false);
        text(result, "تاریخچهٔ تأییدشده — فقط همین صفحه؛ مبلغ هر ردیف تغییر خالص آدرس است، نه کل مبلغ تراکنش", false);
        if (response.transactions.isEmpty()) text(result, "این صفحه ردیف تأییدشده‌ای ندارد؛ این به‌تنهایی نبود تمام سابقه را ثابت نمی‌کند.", false);
        for (Response.Transaction tx : response.transactions) {
            text(result, "شناسهٔ تراکنش تأییدشده", false); text(result, tx.txid, true);
            text(result, "تغییر خالص — ساتوشی", false); text(result, Response.signed(tx.deltaSats), true);
            text(result, "ارتفاع بلوک", false); text(result, Long.toString(tx.blockHeight), true);
            text(result, "زمان بلوک — ثانیه از مبدأ یونیکس", false); text(result, Long.toString(tx.blockTime), true);
        }
        status.setText(repeated ? "پاسخ دریافت شد، اما نشانگر صفحه تکراری بود؛ ادامه متوقف شد. برای شروع دوباره، استعلام نخست را انتخاب کنید." : cursor == null ? "پاسخ دریافت شد. سرور نشانگر صفحهٔ بعد نداده است؛ به‌روزرسانی خودکار نداریم." : "پاسخ دریافت شد. صفحهٔ بعد فقط با انتخاب شما دریافت می‌شود.");
        updateButtons();
    }
    private void startCountdown(int seconds) {
        retryUntil = SystemClock.elapsedRealtime() + seconds * 1000L;
        countdown = new Runnable() {
            @Override public void run() {
                if (!active || !inquiry) return;
                long remaining = Math.max(0, (retryUntil - SystemClock.elapsedRealtime() + 999) / 1000);
                status.setText("سرور درخواست‌های زیاد را رد کرد. زمان پیشنهادی انتظار به ثانیه:\n\n" + remaining + "\n\nاین شمارنده فقط نمایش محلی است، نه تضمین رفع یا اجرای محدودیت سرور. ارسال مجدد خودکار انجام نمی‌شود.");
                if (remaining > 0) main.postDelayed(this, 1000);
            }
        };
        countdown.run();
    }
    private void showPublic(String page, String title) {
        if (!Arrays.asList("guide", "terms", "payment", "security", "costs").contains(page)) return;
        cancelRequest(); stopReading(); inquiry = false; uid = null; cursor = null;
        screen(title);
        button("بازگشت به استعلام", this::showInquiry);
        try (InputStream input = getAssets().open("public/" + page + ".txt")) {
            String body = RequestPolicy.read(input, RequestPolicy.MAX_BODY, System.nanoTime() + 2_000_000_000L);
            // Each paragraph has its own direction; account numbers and paths stay LTR.
            for (String paragraph : body.split("\\n\\s*\\n")) {
                String trimmed = paragraph.trim();
                if (!trimmed.isEmpty()) text(content, trimmed, !trimmed.matches("(?s).*[\\u0600-\\u06FF].*"));
            }
        } catch (Exception ex) { text(content, "خواندن متن آفلاین ممکن نشد. نسخهٔ فعلی سایت را می‌توانید به انتخاب خود باز کنید.", false); }
        button("باز کردن نسخهٔ فعلی در مرورگر خارجی", () -> openPublic(page.equals("payment") ? "costs" : page));
        button("بازگشت به استعلام", this::showInquiry);
    }
    private void openPublic(String page) {
        if (!Arrays.asList("guide", "terms", "costs", "security").contains(page)) return;
        new AlertDialog.Builder(this).setTitle("مرورگر خارجی")
            .setMessage("صفحهٔ عمومی سایت در مرورگر باز می‌شود. اطلاعات اتصال شما برای سایت قابل مشاهده است؛ هیچ شناسهٔ برچسب یا آدرس کیف پول به پیوند اضافه نمی‌شود.")
            .setNegativeButton("لغو", null).setPositiveButton("باز کردن", (dialog, which) -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://btc4.fun/" + page));
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                try { startActivity(intent); } catch (ActivityNotFoundException ex) { new AlertDialog.Builder(this).setMessage("مرورگر قابل استفاده یافت نشد.").setPositiveButton("بستن", null).show(); }
            }).show();
    }
}
