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
        if (inquiry && status != null && uid == null) status.setText(nfc == null ? "این گوشی نمی‌تواند برچسب را بخواند. راهنماها را می‌توانید بدون اینترنت بخوانید." : !nfc.isEnabled() ? "خواندن برچسب خاموش است. در تنظیمات گوشی، گزینهٔ ارتباط نزدیک را روشن کنید. نام این گزینه در راهنمای پایین آمده است." : "دکمهٔ خواندن برچسب را بزنید. چیزی خودکار ارسال نمی‌شود.");
        updateButtons();
    }
    @Override public void onPause() {
        active = false;
        stopReading();
        if (consent != null) { consent.dismiss(); consent = null; }
        cancelRequest();
        if (inquiry && status != null) status.setText("با خروج از این صفحه، دریافت اطلاعات متوقف شد. برای اطلاعات تازه، دوباره دکمهٔ دیدن موجودی را بزنید.");
        super.onPause();
    }
    @Override public void onDestroy() {
        cancelRequest(); worker.shutdownNow(); main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int color(int resource) { return getColor(resource); }
    private android.graphics.drawable.GradientDrawable surface(int fill, int border) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(color(fill)); shape.setCornerRadius(dp(5)); shape.setStroke(dp(1), color(border));
        return shape;
    }
    private void screen(String title) {
        // Own edge-to-edge consistently: safe bars/cutouts outside, generous reading padding inside.
        getWindow().getDecorView(); // Materialize decor before requesting its insets controller.
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().getInsetsController().setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        ScrollView scroll = new ScrollView(this); scroll.setTag("site-scroll");
        scroll.setFillViewport(true); scroll.setClipToPadding(true);
        scroll.setBackgroundColor(color(R.color.paper));
        android.widget.FrameLayout shell = new android.widget.FrameLayout(this);
        content = new LinearLayout(this) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int width = Math.min(View.MeasureSpec.getSize(widthSpec), dp(720));
                super.onMeasure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), heightSpec);
            }
        };
        content.setTag("site-content"); content.setSaveEnabled(false);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        content.setPadding(dp(20), dp(28), dp(20), dp(40));
        android.widget.FrameLayout.LayoutParams centered = new android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        shell.addView(content, centered); scroll.addView(shell);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                int left = insets.getSystemWindowInsetLeft(), top = insets.getSystemWindowInsetTop();
                int right = insets.getSystemWindowInsetRight(), bottom = insets.getSystemWindowInsetBottom();
                if (android.os.Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                    android.view.DisplayCutout cutout = insets.getDisplayCutout();
                    left = Math.max(left, cutout.getSafeInsetLeft()); top = Math.max(top, cutout.getSafeInsetTop());
                    right = Math.max(right, cutout.getSafeInsetRight()); bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                }
                view.setPadding(left, top, right, bottom);
            }
            return insets;
        });
        setContentView(scroll); scroll.requestApplyInsets();
        LinearLayout brand = new LinearLayout(this); brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding(0, 0, 0, dp(24)); content.addView(brand);
        android.widget.ImageView mark = new android.widget.ImageView(this);
        mark.setTag("brand-mark"); mark.setImageResource(R.drawable.ic_customer);
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        brand.addView(mark, new LinearLayout.LayoutParams(dp(43), dp(43)));
        LinearLayout name = new LinearLayout(this); name.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1); nameParams.setMarginStart(dp(13));
        brand.addView(name, nameParams);
        TextView brandName = text(name, "بیت‌کوین برای سرگرمی", false);
        brandName.setTextSize(18); brandName.setPadding(0, 0, 0, 0); bold(brandName);
        TextView subtitle = text(name, "btc4fun · btc4.fun", true);
        subtitle.setTextSize(12); subtitle.setTextColor(color(R.color.muted));
        subtitle.setGravity(Gravity.END); subtitle.setPadding(0, dp(2), 0, 0);
        divider(content);
        TextView eyebrow = text(content, "یک پاکت، یک شروع کنجکاوانه.", false);
        eyebrow.setTextSize(12); eyebrow.setTextColor(color(R.color.accent)); eyebrow.setPadding(0, dp(24), 0, dp(6));
        TextView heading = text(content, title, false); heading.setTextSize(28); bold(heading);
        heading.setPadding(0, 0, 0, dp(16));
    }
    private void bold(TextView view) { view.setTypeface(getResources().getFont(R.font.sahel_bold)); }
    private void divider(LinearLayout parent) {
        View line = new View(this); line.setBackgroundColor(color(R.color.line));
        parent.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }
    private LinearLayout section(String tag, String title, boolean boxed) {
        LinearLayout section = new LinearLayout(this); section.setTag(tag);
        section.setOrientation(LinearLayout.VERTICAL); section.setSaveEnabled(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(24); content.addView(section, params);
        if (boxed) { section.setBackground(surface(R.color.surface, R.color.line)); section.setPadding(dp(20), dp(16), dp(20), dp(20)); }
        else { divider(section); section.setPadding(0, 0, 0, dp(8)); }
        TextView heading = text(section, title, false); heading.setTextSize(19); bold(heading);
        heading.setPadding(0, dp(16), 0, dp(8));
        return section;
    }
    private TextView text(LinearLayout parent, String value, boolean ltr) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(15); view.setTextColor(color(R.color.ink));
        view.setTypeface(getResources().getFont(R.font.sahel_regular));
        view.setLineSpacing(dp(3), 1.25f);
        view.setTextDirection(ltr ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
        view.setLayoutDirection(ltr ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
        view.setGravity(Gravity.START);
        view.setPadding(0, dp(6), 0, dp(6));
        view.setTextIsSelectable(true);
        // Never let Android restore a previous UID/address or response into a new activity.
        view.setSaveEnabled(false);
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }
    private Button button(String label, Runnable action) { return button(content, label, action, false); }
    private Button button(LinearLayout parent, String label, Runnable action, boolean primary) {
        Button value = new Button(this); value.setText(label); value.setAllCaps(false);
        value.setTypeface(getResources().getFont(R.font.sahel_bold)); value.setTextSize(14);
        value.setTextDirection(View.TEXT_DIRECTION_RTL); value.setSaveEnabled(false);
        value.setMinHeight(dp(48)); value.setMinimumHeight(dp(48));
        value.setPadding(dp(16), dp(12), dp(16), dp(12));
        value.setStateListAnimator(null); value.setElevation(0);
        android.graphics.drawable.StateListDrawable backgrounds = new android.graphics.drawable.StateListDrawable();
        backgrounds.addState(new int[]{-android.R.attr.state_enabled}, surface(R.color.soft, R.color.line));
        backgrounds.addState(new int[]{}, surface(primary ? R.color.ink : R.color.paper, R.color.ink));
        value.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(color(R.color.line)), backgrounds, null));
        value.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}}, new int[]{color(R.color.muted), color(primary ? R.color.paper : R.color.ink)}));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(12);
        value.setOnClickListener(view -> action.run()); parent.addView(value, params); return value;
    }
    private void showInquiry() {
        cancelRequest(); stopReading(); inquiry = true; uid = null; cursor = null; cursors.clear();
        screen("موجودی پاکت");
        TextView deck = text(content, "اول برچسب پاکت را بخوانید. بعد، با اجازهٔ شما موجودی و تراکنش‌ها از اینترنت دریافت می‌شود.", false);
        deck.setTextColor(color(R.color.muted));
        LinearLayout scanSection = section("scan-section", "خواندن برچسب", false);
        text(scanSection, "برچسب همان تراشهٔ کوچک روی پاکت است. خواندن برچسب یعنی نزدیک کردن پشت گوشی به آن. فقط شمارهٔ شناسایی برچسب خوانده می‌شود. چیزی روی برچسب نوشته یا خودکار ارسال نمی‌شود.", false).setTextColor(color(R.color.muted));
        text(scanSection, "نام گزینهٔ ارتباط نزدیک در تنظیمات گوشی:", false).setTextColor(color(R.color.muted));
        text(scanSection, "NFC", true).setTextColor(color(R.color.muted));
        scan = button(scanSection, "خواندن برچسب", this::beginScan, true);
        request = button(scanSection, "دیدن موجودی و تراکنش‌ها", () -> confirmRequest(null), false);
        status = text(scanSection, "هنوز برچسبی خوانده نشده است.", false);
        status.setTextSize(13); status.setTextColor(color(R.color.muted));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout resultSection = section("result-section", "موجودی و تراکنش‌ها", true);
        result = new LinearLayout(this); result.setOrientation(LinearLayout.VERTICAL); resultSection.addView(result);
        text(result, "هنوز نتیجه‌ای دریافت نشده است.", false).setTextColor(color(R.color.muted));
        next = button(resultSection, "دیدن تراکنش‌های بیشتر", () -> confirmRequest(cursor), false);
        LinearLayout safety = section("privacy-section", "قبل از دیدن موجودی", false);
        text(safety, "کلید خصوصی یا کلمات بازیابی را هرگز اینجا وارد نکنید. شمارهٔ برچسب را می‌توان کپی کرد. این نتیجه اصل بودن پاکت یا مالکیت شما را ثابت نمی‌کند. تضمین نمی‌کند که فقط شما به پول دسترسی دارید.", false);
        text(safety, "فقط با اجازهٔ شما، شمارهٔ برچسب به سایت فرستاده می‌شود. سایت آدرس عمومی کیف پول را به بلاک‌استریم می‌فرستد. این سرویس آدرس و درخواست سایت را می‌بیند. برنامه خودکار بررسی یا دوباره تلاش نمی‌کند.", false).setTextColor(color(R.color.muted));
        text(safety, "btc4.fun → Blockstream", true).setTextColor(color(R.color.accent));
        LinearLayout links = section("public-section", "راهنماهای پاکت", false);
        text(links, "راهنماهای زیر بدون اینترنت باز می‌شوند.", false).setTextColor(color(R.color.muted));
        button(links, "راهنمای پاکت", () -> showPublic("guide", "راهنمای پاکت"), false);
        button(links, "شرایط و مسئولیت‌ها", () -> showPublic("terms", "شرایط و مسئولیت‌ها"), false);
        button(links, "اطلاعات پرداخت", () -> showPublic("payment", "اطلاعات پرداخت"), false);
        button(links, "امنیت و نگهداری", () -> showPublic("security", "امنیت و نگهداری"), false);
        button(links, "قیمت و هزینه‌ها", () -> showPublic("costs", "قیمت و هزینه‌ها"), false);
        TextView footer = text(content, "برای یاد گرفتن و تجربه کردن؛ نه وعدهٔ ثروتمند شدن.", false);
        footer.setTextSize(12); footer.setTextColor(color(R.color.muted)); footer.setPadding(0, dp(32), 0, 0);
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
        if (nfc == null || !nfc.isEnabled()) { status.setText("گوشی نمی‌تواند برچسب را بخواند. تنظیمات ارتباط نزدیک را بررسی کنید."); return; }
        final long scanToken = ++scanGeneration;
        scanning = true; status.setText("پشت گوشی را نزدیک برچسب نگه دارید. فقط شمارهٔ آن خوانده می‌شود. هنوز چیزی به اینترنت ارسال نشده است.");
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
                    status.setText(scanned == null ? "این نوع برچسب قابل خواندن نیست. چیزی ارسال نشد." : "برچسب خوانده شد. برای ادامه، دکمهٔ دیدن موجودی و تراکنش‌ها را بزنید.");
                    updateButtons();
                });
            }, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B | NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
        } catch (IllegalStateException | SecurityException ex) { scanning = false; status.setText("خواندن برچسب شروع نشد. تنظیمات گوشی را بررسی کنید."); updateButtons(); }
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
            .setTitle("اجازه می‌دهید؟")
            .setMessage("برای دیدن موجودی یا تراکنش‌های بیشتر، شمارهٔ برچسب به سایت بیت‌کوین برای سرگرمی فرستاده می‌شود. سایت آدرس عمومی کیف پول را به بلاک‌استریم می‌فرستد. این سرویس آدرس و درخواست سایت را می‌بیند. پس این بررسی کاملاً خصوصی نیست. کلید خصوصی و کلمات بازیابی ارسال نمی‌شوند. برنامه خودکار بررسی یا دوباره تلاش نمی‌کند.")
            .setNegativeButton("لغو", (dialog, which) -> consent = null)
            .setPositiveButton("فقط همین بار ارسال کن", (dialog, which) -> { consent = null; fetch(after); }).create();
        consent.show();
        styleDialog(consent);
    }
    private void styleDialog(AlertDialog dialog) {
        View decor = dialog.getWindow().getDecorView();
        decor.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        styleDialogText(decor);
    }
    private void styleDialogText(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            boolean strong = view instanceof Button || (text.getTypeface() != null && text.getTypeface().isBold());
            text.setTypeface(getResources().getFont(strong ? R.font.sahel_bold : R.font.sahel_regular));
            text.setTextDirection(View.TEXT_DIRECTION_RTL);
            text.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            if (!(text instanceof Button)) text.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            text.setSaveEnabled(false);
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) styleDialogText(group.getChildAt(i));
        }
    }
    private void fetch(String after) {
        if (!active || uid == null) return;
        cancelRequest();
        final long generation = gate.begin();
        final String requestedUid = uid;
        final Client transport = new Client(); client = transport; busy = true;
        status.setText("در حال دریافت اطلاعات… اگر نتیجهٔ قبلی را می‌بینید، هنوز تازه نشده است."); updateButtons();
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
                    case 400: status.setText("سایت شمارهٔ برچسب یا درخواست را نپذیرفت. نتیجهٔ تازه‌ای نداریم."); break;
                    case 404: status.setText("برچسب پیدا نشد یا بخش بررسی سایت هنوز آماده نیست. این پیام به معنی موجودی صفر نیست."); break;
                    case 429: startCountdown(failure.retrySeconds); break;
                    case 503: status.setText("سایت فعلاً نمی‌تواند اطلاعات بدهد. این پیام به معنی موجودی صفر نیست."); break;
                    default: status.setText("سایت پاسخ نداد. نتیجهٔ تازه‌ای دریافت نشد.");
                }
            } else status.setText("اطلاعات دریافت نشد. ممکن است اینترنت قطع باشد، پاسخ دیر رسیده باشد یا قابل خواندن نباشد. نتیجهٔ تازه‌ای نداریم. برنامه خودکار دوباره درخواست نمی‌فرستد.");
            updateButtons(); return;
        }
        if (after == null) cursors.clear(); else cursors.add(after);
        cursor = response.nextAfterTxid;
        boolean repeated = cursor != null && cursors.contains(cursor);
        if (repeated) cursor = null;
        result.removeAllViews();
        text(result, "شمارهٔ سریال", false); text(result, response.serial, true);
        text(result, "آدرس عمومی کیف پول — همهٔ حروف و عددها را با آدرس روی پاکت مقایسه کنید", false); text(result, response.address, true);
        text(result, "ساتوشی واحد کوچک بیت‌کوین است. تأییدشده یعنی شبکهٔ بیت‌کوین آن را ثبت کرده است.", false);
        text(result, "موجودی تأییدشده، به ساتوشی", false); text(result, Long.toString(response.confirmedSats), true);
        text(result, "این عدد تضمین نمی‌کند که همهٔ پول همین حالا قابل خرج کردن است.", false);
        text(result, "در انتظار تأیید یعنی هنوز قطعی نشده است. این عدد جدا از موجودی تأییدشده است. علامت مثبت یعنی افزایش و علامت منفی یعنی کاهش.", false);
        text(result, "تغییر موجودی در انتظار تأیید، به ساتوشی", false); text(result, Response.signed(response.pendingDeltaSats), true);
        text(result, "منبع یعنی سرویسی که سایت اطلاعات را از آن گرفته است.", false);
        text(result, "منبع اطلاعات، به گفتهٔ سایت", false); text(result, response.source, true);
        text(result, "زمان شروع دریافت اطلاعات در سایت", false); text(result, response.fetchedAt, true);
        text(result, "این زمان را سایت می‌دهد، نه منبع اطلاعات. این زمان تضمین نمی‌کند که اطلاعات همین حالا تازه است.", false);
        text(result, "موجودی و تراکنش‌ها ممکن است در دو زمان متفاوت دریافت شده باشند. فهرست زیر فقط تراکنش‌های تأییدشده را نشان می‌دهد. تراکنش‌های در انتظار، فقط در عدد تغییر موجودی بالا آمده‌اند.", false);
        text(result, "تراکنش‌های تأییدشده در این صفحه. مبلغ هر ردیف فقط تغییر پول این آدرس است، نه کل مبلغ تراکنش.", false);
        if (response.transactions.isEmpty()) text(result, "در این صفحه تراکنش تأییدشده‌ای نیست. ممکن است تراکنش‌های دیگری وجود داشته باشد.", false);
        for (Response.Transaction tx : response.transactions) {
            text(result, "شمارهٔ پیگیری تراکنش تأییدشده", false); text(result, tx.txid, true);
            text(result, "تغییر پول این آدرس، به ساتوشی", false); text(result, Response.signed(tx.deltaSats), true);
            text(result, "شمارهٔ بخش ثبت‌شده در شبکه (بلوک)", false); text(result, Long.toString(tx.blockHeight), true);
            text(result, "زمان ثبت در شبکه — ثانیه از ابتدای سال ۱۹۷۰ میلادی", false); text(result, Long.toString(tx.blockTime), true);
        }
        status.setText(repeated ? "اطلاعات رسید، اما نشانی ادامهٔ فهرست تکراری بود. ادامه متوقف شد. برای شروع دوباره، دکمهٔ دیدن موجودی را بزنید." : cursor == null ? "اطلاعات رسید. سایت صفحهٔ دیگری معرفی نکرد. اطلاعات خودکار تازه نمی‌شود." : "اطلاعات رسید. برای ادامه، دکمهٔ دیدن تراکنش‌های بیشتر را بزنید. صفحهٔ بعد ممکن است خالی باشد.");
        updateButtons();
    }
    private void startCountdown(int seconds) {
        retryUntil = SystemClock.elapsedRealtime() + seconds * 1000L;
        countdown = new Runnable() {
            @Override public void run() {
                if (!active || !inquiry) return;
                long remaining = Math.max(0, (retryUntil - SystemClock.elapsedRealtime() + 999) / 1000);
                status.setText("درخواست‌ها زیاد شده است. سایت پیشنهاد می‌کند کمی صبر کنید. زمان باقی‌مانده به ثانیه:\n\n" + remaining + "\n\nاین شمارنده فقط راهنماست. تمام شدن آن تضمین نمی‌کند که سایت آماده باشد یا محدودیت را بردارد. برنامه خودکار دوباره درخواست نمی‌فرستد.");
                if (remaining > 0) main.postDelayed(this, 1000);
            }
        };
        countdown.run();
    }
    private void showPublic(String page, String title) {
        if (!Arrays.asList("guide", "terms", "payment", "security", "costs").contains(page)) return;
        cancelRequest(); stopReading(); inquiry = false; uid = null; cursor = null;
        screen(title);
        button("بازگشت به موجودی پاکت", this::showInquiry);
        try (InputStream input = getAssets().open("public/" + page + ".txt")) {
            String body = RequestPolicy.read(input, RequestPolicy.MAX_BODY, System.nanoTime() + 2_000_000_000L);
            // Each paragraph has its own direction; account numbers and paths stay LTR.
            for (String paragraph : body.split("\\n\\s*\\n")) {
                String trimmed = paragraph.trim();
                if (!trimmed.isEmpty()) text(content, trimmed, !trimmed.matches("(?s).*[\\u0600-\\u06FF].*"));
            }
        } catch (Exception ex) { text(content, "متن راهنما باز نشد. می‌توانید نسخهٔ تازهٔ آن را در سایت بخوانید.", false); }
        button("دیدن نسخهٔ تازه در سایت", () -> openPublic(page.equals("payment") ? "costs" : page));
        button("بازگشت به موجودی پاکت", this::showInquiry);
    }
    private void openPublic(String page) {
        if (!Arrays.asList("guide", "terms", "costs", "security").contains(page)) return;
        AlertDialog external = new AlertDialog.Builder(this).setTitle("باز کردن سایت")
            .setMessage("این صفحه در مرورگر گوشی باز می‌شود و اینترنت لازم دارد. سایت اطلاعات اتصال شما را می‌بیند. شمارهٔ برچسب و آدرس کیف پول به لینک اضافه نمی‌شوند.")
            .setNegativeButton("لغو", null).setPositiveButton("باز کردن", (dialog, which) -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://btc4.fun/" + page));
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                try { startActivity(intent); } catch (ActivityNotFoundException ex) {
                    AlertDialog missing = new AlertDialog.Builder(this).setMessage("مرورگری برای باز کردن سایت پیدا نشد.").setPositiveButton("بستن", null).show();
                    styleDialog(missing);
                }
            }).show();
        styleDialog(external);
    }
}
