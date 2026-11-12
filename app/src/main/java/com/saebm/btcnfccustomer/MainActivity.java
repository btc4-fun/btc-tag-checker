package com.saebm.btcnfccustomer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.Manifest;
import android.content.pm.PackageManager;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.saebm.btcnfccustomer.core.QrPayload;
import com.saebm.btcnfccustomer.core.BitcoinAddress;
import com.saebm.btcnfccustomer.core.LookupClient;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
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
import com.saebm.btcnfccustomer.core.MarketClient;
import com.saebm.btcnfccustomer.core.MarketResponse;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import android.widget.EditText;
import com.saebm.btcnfccustomer.core.Latest;
import com.saebm.btcnfccustomer.core.RequestPolicy;
import com.saebm.btcnfccustomer.core.Response;
import com.saebm.btcnfccustomer.core.ScanGate;
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
    private static final String PUBLIC_SITE = "https://btc.saeb.id.ir/";
    /** Single source of truth for offline public pages: guide, terms, payment, security, costs. */
    private static final String[] PUBLIC_PAGES = {"guide", "terms", "payment", "security", "costs"};
    /** Human labels for the same pages, kept index-aligned so no screen invents its own title. */
    private static final String[] PUBLIC_TITLES = {"راهنمای پاکت", "شرایط و مسئولیت‌ها", "اطلاعات پرداخت", "امنیت و نگهداری", "راهنمای هزینه‌ها"};
    private static final String HOME_TITLE = "بررسی پاکت";
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
    private String uid, cursor, qrAddress, qrUrl;
    private boolean active, busy, inquiry = true;
    /** True while this activity holds the NFC field, so Android never hands the tag's link to a browser. */
    private boolean readerOn;
    /** Owns the per-scan token and the "waiting for a tag" state; see ScanGate. */
    private final ScanGate scanGate = new ScanGate();
    private long retryUntil;
    private Runnable countdown;
    private AlertDialog consent;
    private final Latest marketGate = new Latest();
    private final Latest lookupGate = new Latest();
    private LookupClient lookupClient;
    /** Which field the in-flight lookup came from, so a 404 names the right source. */
    private boolean lookupFromSerial;
    private EditText serialInput, addressInput;
    private Button serialSubmit, addressSubmit;
    private MarketClient marketClient;
    private MarketResponse market;
    private boolean costsPage, includeTag, budgetMode;
    private String speed = "economy";
    private LinearLayout marketTable, customResult;
    private TextView marketStatus;
    private EditText amount;
    private Button marketFetch, calculate;
    private Runnable ageTick;
    private java.util.function.LongSupplier marketClock = SystemClock::elapsedRealtime;
    private java.util.function.Supplier<MarketClient> marketFactory = () -> new MarketClient(marketClock);
    private long marketRetryUntil;

    static void applyWindowPolicy(android.view.Window window, boolean debug) {
        if (debug) window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        else window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        applyWindowPolicy(getWindow(), BuildConfig.DEBUG);
        nfc = NfcAdapter.getDefaultAdapter(this);
        showInquiry();
    }
    @Override public void onResume() {
        super.onResume(); active = true;
        if (inquiry && status != null && uid == null) status.setText(nfc == null ? "این گوشی نمی‌تواند برچسب را بخواند. راهنماها را می‌توانید بدون اینترنت بخوانید." : !nfc.isEnabled() ? "خواندن برچسب خاموش است. در تنظیمات گوشی، گزینهٔ ارتباط نزدیک را روشن کنید. نام این گزینه در راهنمای پایین آمده است." : "دکمهٔ خواندن برچسب را بزنید. چیزی خودکار ارسال نمی‌شود.");
        updateButtons();
        if (costsPage) startMarketAge();
    }
    @Override public void onPause() {
        active = false;
        stopReading();
        if (consent != null) { consent.dismiss(); consent = null; }
        cancelRequest();
        if (costsPage && market == null && marketStatus != null) marketStatus.setText("دریافت متوقف شد. برای تلاش دوباره دکمهٔ دریافت نرخ را بزنید.");
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
        costsPage = false;
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
        TextView subtitle = text(name, "btc4fun · btc.saeb.id.ir", true);
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
    /** Group entry: same 48dp helper, fixed label, and a spoken suffix that names the action. */
    private Button groupButton(LinearLayout parent, String tag, String label, Runnable action) {
        Button value = button(parent, label, action, false);
        value.setTag(tag);
        value.setContentDescription(label + "؛ باز کردن فهرست");
        return value;
    }
    /** System back from any inner screen returns home; home itself falls through to the system. */
    private boolean backToHome() {
        if (inquiry) return false;
        showInquiry();
        return true;
    }
    @Override public void onBackPressed() {
        if (!backToHome()) super.onBackPressed();
    }
    private void showInquiry() {
        cancelRequest(); stopReading(); inquiry = true; uid = null; cursor = null; cursors.clear();
        screen(HOME_TITLE);
        TextView deck = text(content, "اگر تازه شروع کرده‌اید، از همین‌جا خواندن برچسب را شروع کنید. بعد، با اجازهٔ شما اطلاعات عمومی از اینترنت دریافت می‌شود.", false);
        deck.setTextColor(color(R.color.muted));
        LinearLayout scanSection = section("scan-section", "خواندن برچسب", true);
        text(scanSection, "تراشهٔ کوچک روی پاکت را با پشت گوشی لمس کنید. فقط شناسه خوانده می‌شود؛ چیزی روی برچسب نوشته یا خودکار ارسال نمی‌شود.", false).setTextColor(color(R.color.muted));
        text(scanSection, "اگر دکمه خاموش بود، ارتباط نزدیک را در تنظیمات گوشی روشن کنید. نام این گزینه:", false).setTextColor(color(R.color.muted));
        text(scanSection, "NFC", true).setTextColor(color(R.color.muted));
        scan = button(scanSection, "خواندن برچسب", this::beginScan, true);
        Button qrScan = button(scanSection, "اسکن آدرس عمومی یا لینک سایت", this::startQrScanner, false);
        qrScan.setTag("qr-scan");
        request = button(scanSection, "دیدن موجودی و تراکنش‌ها", () -> confirmRequest(null), false);
        request.setTag("request-button");
        status = text(scanSection, "هنوز برچسبی خوانده نشده است.", false);
        status.setTextSize(13); status.setTextColor(color(R.color.muted));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout resultSection = section("result-section", "موجودی و تراکنش‌ها", true);
        result = new LinearLayout(this); result.setOrientation(LinearLayout.VERTICAL); resultSection.addView(result);
        text(result, "هنوز نتیجه‌ای دریافت نشده است.", false).setTextColor(color(R.color.muted));
        next = button(resultSection, "دیدن تراکنش‌های بیشتر", () -> confirmRequest(cursor), false);
        LinearLayout lookup = section("lookup-section", "استعلام بدون برچسب", true);
        text(lookup, "اگر پاکت را در دست دارید، می‌توانید شمارهٔ سریال چاپ‌شده یا آدرس عمومی روی آن را خودتان وارد کنید. فقط همین یک مقدار به سایت فرستاده می‌شود و موجودی آن کیف پول را نشان می‌دهد. چیزی خودکار ارسال نمی‌شود.", false).setTextColor(color(R.color.muted));
        text(lookup, "شمارهٔ سریال چاپ‌شده روی پاکت", false);
        serialInput = input(lookup, "serial-input", "مثلاً 26b1z00001");
        serialSubmit = button(lookup, "استعلام با شمارهٔ سریال", () -> submitLookup(true), false);
        serialSubmit.setTag("serial-submit");
        text(lookup, "آدرس عمومی بیت‌کوین", false);
        addressInput = input(lookup, "address-input", "bc1…");
        addressSubmit = button(lookup, "استعلام با آدرس عمومی", () -> submitLookup(false), false);
        addressSubmit.setTag("address-submit");
        // Short home warning only; the full sentences live once in showLearnGroup.
        LinearLayout safety = section("privacy-section", "پیش از دریافت", false);
        text(safety, "کلید خصوصی و کلمات بازیابی ارسال نمی‌شوند و چیزی خودکار فرستاده نمی‌شود.", false);
        text(safety, "این بررسی خصوصی نیست؛ جزئیات در «امنیت، آموزش و شرایط».", false).setTextColor(color(R.color.muted));
        // Two group entry points only: no direct page links on the home screen.
        LinearLayout groups = section("group-section", "راهنما و ارتباطات", false);
        groupButton(groups, "group-learn-button", "امنیت، آموزش و شرایط", this::showLearnGroup);
        groupButton(groups, "group-contact-button", "اطلاعات پرداخت و تماس", this::showContactGroup);
        TextView footer = text(content, "برای یاد گرفتن و تجربه کردن؛ نه وعدهٔ ثروتمند شدن.", false);
        footer.setTextSize(12); footer.setTextColor(color(R.color.muted)); footer.setPadding(0, dp(32), 0, 0);
        updateButtons();
    }
    /** One-line LTR field; serial and address are Latin-only, so no RTL mixing can occur. */
    private EditText input(LinearLayout parent, String tag, String hint) {
        EditText field = new EditText(this);
        field.setTag(tag); field.setHint(hint);
        field.setTextDirection(View.TEXT_DIRECTION_LTR); field.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        field.setGravity(Gravity.START); field.setSingleLine(true);
        field.setTextSize(15); field.setSaveEnabled(false);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setTypeface(getResources().getFont(R.font.sahel_regular));
        field.setTextColor(color(R.color.ink)); field.setHintTextColor(color(R.color.muted));
        field.setBackground(surface(R.color.paper, R.color.line));
        field.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(8);
        parent.addView(field, params);
        return field;
    }
    private void updateButtons() {
        if (!inquiry || scan == null) return;
        boolean scanning = scanGate.waiting();
        scan.setEnabled(active && !busy && nfc != null && nfc.isEnabled());
        request.setEnabled(active && uid != null && !busy && !scanning);
        next.setEnabled(active && uid != null && cursor != null && !busy && !scanning);
        if (serialSubmit != null) serialSubmit.setEnabled(active && !busy && !scanning);
        if (addressSubmit != null) addressSubmit.setEnabled(active && !busy && !scanning);
    }
    private void beginScan() {
        cancelRequest(); uid = null; cursor = null; cursors.clear(); result.removeAllViews();
        if (nfc == null || !nfc.isEnabled()) { status.setText("گوشی نمی‌تواند برچسب را بخواند. تنظیمات ارتباط نزدیک را بررسی کنید."); return; }
        final long token = scanGate.begin();
        status.setText("پشت گوشی را نزدیک برچسب نگه دارید. فقط شمارهٔ آن خوانده می‌شود. هنوز چیزی به اینترنت ارسال نشده است.");
        updateButtons();
        try {
            nfc.enableReaderMode(this, tag -> onTagRead(tag, token),
                NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B | NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
            readerOn = true;
        } catch (IllegalStateException | SecurityException ex) { scanGate.stop(); readerOn = false; status.setText("خواندن برچسب شروع نشد. تنظیمات گوشی را بررسی کنید."); updateButtons(); }
    }
    /**
     * Reader mode deliberately stays on after a read. Releasing it while the tag is still against the
     * phone hands the tag's stored link to Android, which opens a browser, pauses this screen and
     * loses the scan. The field is released only when the user leaves the screen.
     *
     * The token is captured per scan and never read from a shared field: a callback that arrives
     * after a newer scan started, or after the screen was left, must be dropped instead of
     * overwriting the newer result. ScanGate owns that decision and is unit-tested in core.
     */
    private void onTagRead(Tag tag, long token) {
        String value;
        try { value = Uid.format(tag.getId()); }
        catch (IllegalArgumentException ex) { value = null; }
        final String scanned = value;
        main.post(() -> {
            if (!scanGate.accepts(token, active, inquiry, busy)) return;
            scanGate.consumed(); uid = scanned;
            status.setText(scanned == null ? "این نوع برچسب قابل خواندن نیست. چیزی ارسال نشد." : "برچسب خوانده شد. برای ادامه، دکمهٔ دیدن موجودی و تراکنش‌ها را بزنید.");
            updateButtons();
        });
    }
    private void startQrScanner() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 210);
            return;
        }
        new IntentIntegrator(this).setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setCaptureActivity(SecureCaptureActivity.class)
            .setPrompt("آدرس عمومی یا لینک سایت را اسکن کنید")
            .setBarcodeImageEnabled(false).setBeepEnabled(false).setOrientationLocked(false).initiateScan();
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code == 210 && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) startQrScanner();
        else if (code == 210 && status != null) status.setText("مجوز دوربین رد شد؛ برای اسکن، مجوز را در تنظیمات فعال کنید. هیچ داده‌ای ذخیره نشد.");
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult scanned = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (scanned == null || status == null) return;
        if (scanned.getContents() == null) { status.setText("اسکن لغو شد؛ هیچ داده‌ای ذخیره نشد."); return; }
        QrPayload.Result parsed = QrPayload.classify(scanned.getContents());
        switch (parsed.kind) {
            case BITCOIN_ADDRESS:
            case BITCOIN_URI:
                qrAddress = parsed.value; qrUrl = null;
                status.setText("آدرس عمومی خوانده شد. برای استعلام عمومی یا باز کردن لینک، اجازهٔ جداگانه لازم است.");
                confirmQrAddress();
                break;
            case ALLOWLISTED_HTTPS_URL:
                qrUrl = parsed.value; qrAddress = null;
                status.setText("لینک سایت مجاز شناسایی شد. خودکار باز نمی‌شود؛ برای باز کردن باید جداگانه تأیید کنید.");
                confirmQrUrl();
                break;
            case UNTRUSTED:
                status.setText("این لینک قابل اعتماد نیست و باز نمی‌شود.");
                break;
            default:
                status.setText("محتوای QR آدرس یا لینک معتبر نیست؛ هیچ داده‌ای ذخیره نشد.");
        }
    }
    private void confirmQrAddress() {
        if (qrAddress == null) return;
        confirmLookup(false, qrAddress);
    }
    /** Reads the typed value, rejects an invalid shape before any network use, then asks for consent. */
    private void submitLookup(boolean bySerial) {
        EditText field = bySerial ? serialInput : addressInput;
        if (field == null || busy || !active) return;
        String value = field.getText().toString().trim();
        if (bySerial ? !RequestPolicy.validSerial(value) : !BitcoinAddress.isValid(value)) {
            status.setText(bySerial ? "شمارهٔ سریال معتبر نیست. همان شماره‌ای را وارد کنید که روی پاکت چاپ شده است." : "آدرس عمومی معتبر نیست. آدرس کامل بیت‌کوین را وارد کنید.");
            return;
        }
        confirmLookup(bySerial, value);
    }
    /** One consent dialog naming the exact source; one request after it. Nothing fires automatically. */
    private void confirmLookup(boolean bySerial, String value) {
        String source = bySerial ? "شمارهٔ سریال" : "آدرس عمومی";
        consent = new AlertDialog.Builder(this)
            .setTitle(bySerial ? "استعلام با شمارهٔ سریال" : "استعلام با آدرس عمومی")
            .setMessage("همین " + source + " به سایت بیت‌کوین برای سرگرمی فرستاده می‌شود تا موجودی و اطلاعات عمومی آن کیف پول دریافت شود. سایت آدرس عمومی کیف پول را به بلاک‌استریم می‌فرستد. این سرویس آدرس و درخواست سایت را می‌بیند. کلید خصوصی و کلمات بازیابی ارسال نمی‌شوند. برنامه خودکار بررسی یا دوباره تلاش نمی‌کند.")
            .setNegativeButton("لغو", (dialog, which) -> consent = null)
            .setPositiveButton("فقط همین بار ارسال کن", (dialog, which) -> { consent = null; fetchLookup(bySerial, value); }).create();
        consent.show();
        styleDialog(consent);
    }
    /** Single lookup path for manual entry, QR and NFC: clears the card, cancels the old call, one generation. */
    private void fetchLookup(boolean bySerial, String value) {
        if (!active) return;
        cancelRequest(); result.removeAllViews();
        final long generation = lookupGate.begin();
        lookupFromSerial = bySerial;
        final LookupClient transport = LookupClient.production(System::nanoTime); lookupClient = transport;
        busy = true; status.setText("در حال استعلام از سایت… نتیجهٔ تازه هنوز نرسیده است."); updateButtons();
        WeakReference<MainActivity> owner = new WeakReference<>(this);
        work = worker.submit(() -> {
            LookupClient.Lookup lookup = null; Exception error = null;
            try { lookup = bySerial ? transport.fetchBySerial(value, true) : transport.fetchByAddress(value, true); }
            catch (Exception ex) { error = ex; }
            LookupClient.Lookup delivered = lookup; Exception failed = error;
            new Handler(Looper.getMainLooper()).post(() -> {
                MainActivity activity = owner.get();
                if (activity == null || !activity.active || !activity.inquiry || !activity.lookupGate.accepts(generation)) return;
                activity.deliverLookup(delivered, failed);
            });
        });
    }
    private void deliverLookup(LookupClient.Lookup lookup, Exception error) {
        busy = false; lookupClient = null; work = null;
        result.removeAllViews();
        if (error != null) {
            if (error instanceof LookupClient.BalanceUnavailable) {
                // The site gave a healthy mapping but no balance: show what we have and never imply zero.
                renderLookup(((LookupClient.BalanceUnavailable) error).lookup);
                status.setText("سایت فعلاً موجودی را نمی‌دهد. این پیام به معنی صفر نیست.");
            } else if (error instanceof Client.Failure) {
                Client.Failure failure = (Client.Failure) error;
                switch (failure.status) {
                    case 400: status.setText("ورودی معتبر نیست"); break;
                    case 404: status.setText(lookupFromSerial ? "سریالی با این شماره پیدا نشد" : "آدرس عمومی نامعتبر است؛ این آدرس در پایگاه‌داده ما ثبت نشده است."); break;
                    case 429: startCountdown(failure.retrySeconds); break;
                    case 503: status.setText("سایت فعلاً اطلاعات نمی‌دهد"); break;
                    default: status.setText("سایت پاسخ نداد. نتیجهٔ تازه‌ای دریافت نشد.");
                }
            } else status.setText("پاسخ نرسید یا دیر شد");
            updateButtons(); return;
        }
        renderLookup(lookup);
        status.setText(lookup.balance.freshness.fresh(System.nanoTime() / 1_000_000L) ? "موجودی رسید." : "موجودی رسید، اما زمان آن در سایت قدیمی است. برای عدد تازه دوباره استعلام کنید.");
        updateButtons();
    }
    /** Public card: mapping first, balance only if present, then the optional seller fields. */
    private void renderLookup(LookupClient.Lookup lookup) {
        text(result, "شمارهٔ سریال", false); text(result, lookup.serial, true);
        text(result, "آدرس عمومی کیف پول — همهٔ حروف و عددها را با آدرس روی پاکت مقایسه کنید", false); text(result, lookup.address, true);
        if (lookup.balance != null) {
            text(result, "موجودی تأییدشده، به ساتوشی", false); text(result, Long.toString(lookup.balance.confirmedSats), true);
            text(result, "تغییر موجودی در انتظار تأیید، به ساتوشی", false); text(result, Response.signed(lookup.balance.pendingDeltaSats), true);
            text(result, "منبع اطلاعات، به گفتهٔ سایت", false); text(result, lookup.balance.source, true);
            text(result, "زمان شروع دریافت اطلاعات در سایت", false); text(result, lookup.balance.fetchedAt, true);
            text(result, "این زمان را سایت می‌دهد، نه منبع اطلاعات. این عدد تضمین نمی‌کند که همهٔ پول همین حالا قابل خرج کردن است.", false);
        }
        LookupClient.PublicInfo info = lookup.publicInfo;
        if (info != null && !info.empty()) {
            if (info.notes != null) { text(result, "یادداشت عمومی فروشنده", false); text(result, info.notes, false); }
            if (info.siteKey != null) {
                text(result, "سایت فروشنده", false); text(result, info.siteKey, true);
                Button site = button(result, "باز کردن سایت فروشنده", () -> confirmOpen(info.siteUrl, "سایت فروشنده", "سایت فروشنده در مرورگر باز می‌شود. آن سایت اطلاعات اتصال شما را می‌بیند."), false);
                site.setTag("site-open");
            }
            if (info.imageUrl != null) {
                text(result, "نشانی تصویر پاکت", false); text(result, info.imageUrl, true);
                Button image = button(result, "باز کردن تصویر پاکت", () -> confirmOpen(info.imageUrl, "تصویر پاکت", "تصویر پاکت در مرورگر باز می‌شود. برنامه خودش تصویر را بارگذاری نمی‌کند."), false);
                image.setTag("image-open");
            }
            if (!info.links.isEmpty()) {
                text(result, "شبکه‌های اجتماعی و راه‌های تماس فروشنده", false);
                for (LookupClient.Link link : info.links) {
                    text(result, link.key, true);
                    Button open = button(result, "باز کردن " + link.key,
                            () -> confirmOpen(link.url, link.key, "این نشانی در مرورگر باز می‌شود. آن سایت اطلاعات اتصال شما را می‌بیند."), false);
                    open.setTag("link-open:" + link.key);
                }
            }
        }
        text(result, "صفحهٔ عمومی این کیف پول", false); text(result, lookup.lookupUrl, true);
        Button page = button(result, "باز کردن صفحهٔ عمومی در سایت", () -> confirmOpen(lookup.lookupUrl, "صفحهٔ عمومی", "صفحهٔ عمومی این کیف پول در مرورگر باز می‌شود."), false);
        page.setTag("lookup-open");
        Button explorer = button(result, "باز کردن آدرس در کاوشگر بلاک‌چین", () -> confirmOpen(LookupClient.explorerUrl(lookup.address), "کاوشگر بلاک‌چین", "این آدرس در کاوشگر عمومی بلاک‌چین باز می‌شود. کاوشگر اطلاعات اتصال شما را می‌بیند."), false);
        explorer.setTag("explorer-open");
    }
    /** Every external link goes through one consent dialog; nothing opens on its own. */
    private void confirmOpen(String url, String title, String message) {
        if (url == null) return;
        Uri uri = Uri.parse(url);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null) return;
        consent = new AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setNegativeButton("لغو", (d, w) -> consent = null)
            .setPositiveButton("باز کردن", (d, w) -> {
                consent = null;
                Intent intent = new Intent(Intent.ACTION_VIEW, uri); intent.addCategory(Intent.CATEGORY_BROWSABLE);
                try { startActivity(intent); } catch (ActivityNotFoundException ignored) { status.setText("مرورگری برای باز کردن این لینک پیدا نشد."); }
            }).create();
        consent.show();
        styleDialog(consent);
    }
    private void confirmQrUrl() {
        if (qrUrl == null) return;
        new AlertDialog.Builder(this).setTitle("باز کردن لینک مجاز")
            .setMessage("این لینک در مرورگر باز می‌شود و مقصد، اطلاعات اتصال شما را می‌بیند. لینک خودکار باز نشده است.")
            .setNegativeButton("لغو", null).setPositiveButton("باز کردن", (d,w) -> { Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(qrUrl)); i.addCategory(Intent.CATEGORY_BROWSABLE); try { startActivity(i); } catch(ActivityNotFoundException ignored) { status.setText("مرورگری برای باز کردن لینک پیدا نشد."); } }).show();
    }
    private void stopReading() {
        scanGate.stop();
        if (nfc != null && readerOn) {
            try { nfc.disableReaderMode(this); } catch (IllegalStateException | SecurityException ignored) { }
        }
        readerOn = false;
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
        marketGate.invalidate();
        lookupGate.invalidate();
        if (lookupClient != null) { lookupClient.cancel(); lookupClient = null; }
        if (marketClient != null) { marketClient.cancel(); marketClient = null; }
        if (ageTick != null) { main.removeCallbacks(ageTick); ageTick = null; }
        if (marketFetch != null) marketFetch.setEnabled(true);
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
    /** The one allowlist every public screen and every group reads from. */
    private static boolean isPublicPage(String page) { return Arrays.asList(PUBLIC_PAGES).contains(page); }
    private static String publicTitle(String page) {
        int index = Arrays.asList(PUBLIC_PAGES).indexOf(page);
        return index < 0 ? "" : PUBLIC_TITLES[index];
    }
    /** Shared prologue for inner pages: cancels work, leaves inquiry mode, and rebuilds the screen. */
    private void innerScreen(String title) {
        cancelRequest(); stopReading(); inquiry = false; uid = null; cursor = null;
        screen(title);
    }
    private void showLearnGroup() {
        innerScreen("امنیت، آموزش و شرایط");
        // Owner order: terms first, then security, then the guide. The group
        // explanation lives above the first control but is not focusable, so
        // the first control a reader reaches really is the terms page.
        text(content, "همهٔ این صفحه‌ها بدون اینترنت باز می‌شوند و نسخهٔ تازه در سایت است:", false).setTextColor(color(R.color.muted));
        text(content, "btc.saeb.id.ir", true).setTextColor(color(R.color.accent));
        pageButton("learn-terms", "terms");
        pageButton("learn-security", "security");
        pageButton("learn-guide", "guide", "آموزش؛ پاکت و بیت‌کوین چیست");
        LinearLayout safety = section("privacy-section", "پیش از دریافت", false);
        text(safety, "کلید خصوصی و کلمات بازیابی ارسال نمی‌شوند. شناسهٔ برچسب را می‌توان کپی کرد؛ فقط با اجازهٔ شما فرستاده می‌شود. این بررسی خصوصی نیست و اصل بودن یا مالکیت را ثابت نمی‌کند.", false);
        text(safety, "دیدن موجودی یا شناسهٔ برچسب تضمین نمی‌کند که فقط شما به پول دسترسی دارید.", false);
        text(safety, "برنامه خودکار بررسی یا دوباره تلاش نمی‌کند. سایت آدرس عمومی را برای دریافت اطلاعات به بلاک‌استریم می‌فرستد.", false).setTextColor(color(R.color.muted));
        text(safety, "btc4.fun → Blockstream", true).setTextColor(color(R.color.accent));
        button("بازگشت به بررسی پاکت", this::showInquiry).setTag("learn-back");
        button("دیدن نسخهٔ تازه در سایت", () -> openPublic("")).setTag("learn-site");
        updateButtons();
    }
    private void showContactGroup() {
        innerScreen("اطلاعات پرداخت و تماس");
        text(content, "هر بخش جدا باز می‌شود؛ خروج از این فهرست با دکمهٔ پایین یا دکمهٔ بازگشت گوشی است.", false).setTextColor(color(R.color.muted));
        pageButton("contact-payment", "payment");
        button("قیمت و هزینه‌ها", this::showCosts).setTag("contact-costs");
        button("ارتباط با ما", this::showContact).setTag("contact-support");
        button("بازگشت به بررسی پاکت", this::showInquiry).setTag("contact-back");
        updateButtons();
    }
    /** One offline page button per allowed page; the label can be overridden, never the target. */
    private Button pageButton(String tag, String page) { return pageButton(tag, page, publicTitle(page)); }
    private Button pageButton(String tag, String page, String label) {
        if (!isPublicPage(page)) return null;
        Button value = button(label, () -> showPublic(page, label));
        value.setTag(tag);
        return value;
    }
    private void showPublic(String page, String title) {
        if (!isPublicPage(page)) return;
        innerScreen(title);
        button("بازگشت", this::showContactGroup);
        try (InputStream input = getAssets().open("public/" + page + ".txt")) {
            String body = RequestPolicy.read(input, RequestPolicy.MAX_BODY, System.nanoTime() + 2_000_000_000L);
            // Each paragraph has its own direction; account numbers and paths stay LTR.
            for (String paragraph : body.split("\\n\\s*\\n")) {
                String trimmed = paragraph.trim();
                if (!trimmed.isEmpty()) text(content, trimmed, !trimmed.matches("(?s).*[\\u0600-\\u06FF].*"));
            }
        } catch (Exception ex) { text(content, "متن راهنما باز نشد. می‌توانید نسخهٔ تازهٔ آن را در سایت بخوانید.", false); }
        button("دیدن نسخهٔ تازه در سایت", () -> openPublic(page.equals("payment") ? "costs" : page));
        button("بازگشت", this::showContactGroup);
    }
    private void showContact() {
        innerScreen("ارتباط با ما");
        button("بازگشت", this::showContactGroup);
        text(content, "فقط پرسش عمومی بفرستید. کلید خصوصی، کلمات بازیابی، عبارت عبور، فایل پشتیبان و عکس داخل پاکت را نفرستید. پاسخ فوری تضمین نمی‌شود.", false);
        text(content, "فرم پیام در مرورگر باز می‌شود. برنامه پیام ارسال نمی‌کند و شمارهٔ برچسب یا اطلاعات کیف پول را به مقصد اضافه نمی‌کند.", false);
        try (InputStream in = getAssets().open("public/contact-links.json")) {
            JsonObject links = JsonParser.parseString(RequestPolicy.read(in, 8192, System.nanoTime()+2_000_000_000L)).getAsJsonObject();
            String[] keys = {"email", "website", "form", "telegram", "instagram", "x"};
            String[] labels = {"ایمیل", "سایت", "فرم پرسش عمومی در سایت", "تلگرام", "اینستاگرام", "ایکس، توییتر سابق"};
            for (int i=0; i<keys.length; i++) {
                String url = links.get(keys[i]).getAsString();
                text(content, url, true);
                Button contact = button(labels[i], () -> openContact(url));
                contact.setTag("contact-" + keys[i]);
            }
        } catch (Exception ex) { text(content, "نشانی‌های ارتباط باز نشدند. لطفاً سایت را بررسی کنید.", false); }
    }
    private void openContact(String url) {
        // Only bundled public destinations, never user or tag-derived URI components.
        if (!url.matches("mailto:[A-Za-z0-9_.+-]+@btc4\\.fun|https://btc\\.saeb\\.id\\.ir/(?:contact)?|https://t\\.me/[A-Za-z0-9_]+|https://www\\.instagram\\.com/[A-Za-z0-9_.]+/|https://x\\.com/[A-Za-z0-9_]+")) return;
        consent = new AlertDialog.Builder(this).setTitle("باز کردن راه ارتباط")
            .setMessage("برنامهٔ ایمیل یا مرورگر باز می‌شود. مقصد اطلاعات اتصال شما را می‌بیند. شمارهٔ برچسب و اطلاعات کیف پول ارسال نمی‌شوند. هیچ راز کیف پولی نفرستید.")
            .setNegativeButton("لغو", (d,w) -> consent=null)
            .setPositiveButton("باز کردن", (d,w) -> {
                consent=null;
                Intent intent=new Intent(url.startsWith("mailto:")?Intent.ACTION_SENDTO:Intent.ACTION_VIEW,Uri.parse(url));
                if(!url.startsWith("mailto:")) intent.addCategory(Intent.CATEGORY_BROWSABLE);
                try { startActivity(intent); }
                catch(ActivityNotFoundException ex) { AlertDialog missing=new AlertDialog.Builder(this).setMessage("برنامه‌ای برای باز کردن این راه ارتباط پیدا نشد.").setPositiveButton("بستن",null).show();styleDialog(missing); }
            }).show();
        styleDialog(consent);
    }
    private void showCosts() {
        cancelRequest(); stopReading(); inquiry=false; uid=null; cursor=null; market=null;
        speed="economy"; includeTag=false; budgetMode=false;
        screen("قیمت و هزینه‌ها"); costsPage=true;
        button("بازگشت",this::showContactGroup);
        text(content,"نرخ فقط با دکمهٔ زیر از سایت دریافت می‌شود. مبلغ و بودجه ارسال نمی‌شوند. تغییر سرعت و برچسب، درخواست تازه‌ای نمی‌فرستد. این برآورد سفارش یا تعهد پرداخت نیست.",false);
        marketFetch=button("دریافت نرخ از سایت",this::fetchMarket);
        marketStatus=text(content,"هنوز نرخی دریافت نشده است.",false);
        marketStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout choices=section("quote-options","انتخاب هزینه‌ها",false);
        String[] speeds={"economy","medium","fast"}, labels={"اقتصادی","متوسط","سریع"};
        for(int i=0;i<speeds.length;i++) {
            final String selected=speeds[i];
            Button b=button(choices,labels[i],()->{speed=selected;renderMarket();},false);b.setTag("speed-"+selected);
        }
        button(choices,"بدون برچسب؛ تغییر انتخاب",()->{includeTag=!includeTag;renderMarket();},false).setTag("tag-choice");
        text(choices,"سرعت‌ها فقط اولویت پیشنهادی‌اند، نه تضمین زمان تأیید. مواد پاکت یازده سنت (مهر سه، کاغذ یک، چاپ یک و پاکت شش سنت)، پست هفتاد و پنج سنت و برچسب دوازده سنت با فرض هر تتر برابر یک دلار برآورد می‌شوند. کارمزد گرداننده پانزده درصدِ اصل و هزینه‌هاست. نرخ صرافی نیم درصد تعدیل دارد.",false);
        marketTable=section("market-table","جدول برآورد",false);
        LinearLayout custom=section("custom-quote","محاسبهٔ مبلغ دلخواه",true);
        text(custom,"عدد صحیح مثبت با ارقام انگلیسی، بدون فاصله، جداکننده، اعشار یا صفر ابتدایی؛ حداکثر سی‌ودو رقم. اصل مبلغ از پانصد و یک ساتوشی تا سقف بیست‌ویک میلیون بیت‌کوین است.",false);
        button(custom,"اصل ساتوشی؛ تغییر حالت",()->{budgetMode=!budgetMode;clearCustom();},false).setTag("budget-choice");
        amount=new EditText(this); amount.setTag("quote-amount");amount.setSaveEnabled(false);
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);amount.setTextDirection(View.TEXT_DIRECTION_LTR);
        amount.setTypeface(getResources().getFont(R.font.sahel_regular));amount.setTextColor(color(R.color.ink));
        amount.setContentDescription("مبلغ دلخواه؛ عدد صحیح با ارقام انگلیسی");custom.addView(amount,new LinearLayout.LayoutParams(-1,-2));
        amount.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) {clearCustom();}
            public void afterTextChanged(android.text.Editable s) {}
        });
        calculate=button(custom,"محاسبه با همین نرخ",this::calculateQuote,false);
        customResult=new LinearLayout(this);customResult.setOrientation(LinearLayout.VERTICAL);custom.addView(customResult);
        renderMarket();startMarketAge();
        button("راهنمای هزینه‌ها",()->showPublic("costs","راهنمای هزینه‌ها"));
    }
    private void clearCustom() {
        Button mode=content.findViewWithTag("budget-choice");
        if(mode!=null) mode.setText(budgetMode?"بودجهٔ تومان؛ تغییر به اصل ساتوشی":"اصل ساتوشی؛ تغییر به بودجهٔ تومان");
        if(customResult==null) return;
        customResult.removeAllViews();
        text(customResult,budgetMode?"حالت انتخاب‌شده: بودجه به تومان":"حالت انتخاب‌شده: اصل مبلغ به ساتوشی",false);
    }
    private void fetchMarket() {
        if(!active || !costsPage || marketClock.getAsLong()<marketRetryUntil) return;
        cancelRequest();market=null;renderMarket();marketFetch.setEnabled(false);
        marketStatus.setText("در حال دریافت نرخ؛ چیزی از برچسب یا مبلغ شما ارسال نمی‌شود…");
        final long generation=marketGate.begin();
        final MarketClient transport=marketFactory.get();marketClient=transport;
        WeakReference<MainActivity> owner=new WeakReference<>(this);
        work=worker.submit(()->{
            MarketResponse response=null;
            int delay=0;
            try {response=transport.fetch();}
            catch(Client.Failure failure) {if(failure.status==429) delay=failure.retrySeconds;}
            catch(Exception ignored) { /* Public error only. */ }
            final int retry=delay;
            final MarketResponse delivered=response;
            new Handler(Looper.getMainLooper()).post(()->{
                MainActivity activity=owner.get();
                if(activity==null||!activity.active||!activity.costsPage||!activity.marketGate.accepts(generation)) return;
                activity.marketClient=null;activity.work=null;activity.marketFetch.setEnabled(true);activity.market=delivered;
                if(retry>0) activity.marketRetryUntil=activity.marketClock.getAsLong()+retry*1000L;
                activity.renderMarket();
                if(delivered==null) activity.marketStatus.setText("نرخ معتبر دریافت نشد؛ اینترنت، پاسخ نامعتبر یا قدیمی می‌تواند علت باشد. هیچ قیمت صفر یا جایگزینی فرض نشده است. برای تلاش دوباره دکمهٔ دریافت نرخ را بزنید.");
                activity.startMarketAge();
            });
        });
    }
    private void startMarketAge() {
        if(ageTick!=null) main.removeCallbacks(ageTick);
        ageTick=new Runnable(){public void run(){
            if(!active||!costsPage) return;
            updateMarketAge();main.postDelayed(this,1000);
        }};ageTick.run();
    }
    private void updateMarketAge() {
        long now=marketClock.getAsLong();
        if(now<marketRetryUntil) {
            marketFetch.setEnabled(false);
            marketStatus.setText("درخواست‌ها زیاد شده است؛ تا پایان زمان پیشنهادی سایت صبر کنید. ثانیهٔ باقی‌مانده:\n\n"+((marketRetryUntil-now+999)/1000)+"\n\nبرنامه خودکار تلاش نمی‌کند.");
        } else if(marketClient==null) {
            marketFetch.setEnabled(true);
            if(marketRetryUntil!=0) {marketRetryUntil=0;marketStatus.setText("زمان پیشنهادی تمام شد؛ برای تلاش دوباره دکمهٔ دریافت نرخ را بزنید. موفقیت تضمین نمی‌شود.");}
        }
        if(market==null) return;
        if(!market.usable(now)) {
            calculate.setEnabled(false);marketTable.removeAllViews();clearCustom();
            marketStatus.setText("نرخ بیش از پنج دقیقه عمر دارد؛ محاسبه متوقف شد. دوباره دکمهٔ دریافت نرخ را بزنید.");
        } else marketStatus.setText(market.fresh(now)?"برآورد با دادهٔ دریافت‌شده؛ نرخ نهایی تضمین نمی‌شود.":"نرخ قدیمی است؛ این برآورد قیمت تازه نیست. پیش از پرداخت نرخ را دوباره دریافت کنید.");
    }
    private void renderMarket() {
        if(!costsPage) return;
        marketTable.removeAllViews();clearCustom();
        boolean usable=market!=null&&market.usable(marketClock.getAsLong());
        for(String s:new String[]{"economy","medium","fast"}) {
            View b=content.findViewWithTag("speed-"+s);b.setEnabled(usable&&market.option(s,false)!=null);
            ((Button)b).setSelected(s.equals(speed));
            String label=s.equals("economy")?"اقتصادی":s.equals("medium")?"متوسط":"سریع";
            ((Button)b).setText(label+(s.equals(speed)?"؛ انتخاب‌شده":"؛ انتخاب"));
            b.setBackground(surface(s.equals(speed)?R.color.surface:R.color.paper,s.equals(speed)?R.color.accent:R.color.line));
        }
        ((Button)content.findViewWithTag("tag-choice")).setText(includeTag?"با برچسب؛ تغییر به بدون برچسب":"بدون برچسب؛ تغییر به با برچسب");
        MarketResponse.Option option=usable?market.option(speed,includeTag):null;
        calculate.setEnabled(option!=null);
        text(marketTable,"انتخاب سرعت: "+(speed.equals("economy")?"اقتصادی":speed.equals("medium")?"متوسط":"سریع")+(includeTag?"؛ با برچسب":"؛ بدون برچسب"),false);
        if(option==null) {text(marketTable,"نرخ معتبر برای این انتخاب در دسترس نیست.",false);updateMarketAge();return;}
        text(marketTable,"زمان دریافت در سایت",false);text(marketTable,market.fetchedAt,true);
        text(marketTable,"منبع قیمت و کارمزد، به گفتهٔ سایت",false);text(marketTable,MarketResponse.PRICES,true);text(marketTable,MarketResponse.FEES,true);
        text(marketTable,"نرخ تعدیل‌شدهٔ بیت‌کوین؛ تومان",false);text(marketTable,option.btcToman,true);
        text(marketTable,"نرخ تعدیل‌شدهٔ تتر؛ تومان",false);text(marketTable,option.usdtToman,true);
        text(marketTable,"هزینهٔ شبکه برای هر بایت مجازی؛ ساتوشی — اندازهٔ فرضی تراکنش صد‌وچهل‌ویک بایت مجازی است.",false);text(marketTable,option.feeRate,true);
        for(com.google.gson.JsonElement element:option.rows()) {
            JsonObject row=element.getAsJsonObject();
            text(marketTable,"اصل مبلغ؛ ساتوشی",false);text(marketTable,row.get("principal_sats").getAsString(),true);
            text(marketTable,"جمع پرداختی؛ تومان",false);text(marketTable,row.get("total_toman").getAsString(),true);
            LinearLayout details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);details.setVisibility(View.GONE);
            String rowLabel="ریز هزینهٔ اصل ساتوشی:\n"+row.get("principal_sats").getAsString();
            Button disclosure=button(marketTable,"نمایش "+rowLabel,()->{},false);
            disclosure.setOnClickListener(v->{boolean open=details.getVisibility()==View.GONE;details.setVisibility(open?View.VISIBLE:View.GONE);disclosure.setText((open?"بستن ":"نمایش ")+rowLabel);});
            marketTable.addView(details);renderQuote(details,row);
        }
        updateMarketAge();
    }
    private void calculateQuote() {
        clearCustom();updateMarketAge();
        if(market==null||!market.usable(marketClock.getAsLong())) return;
        MarketResponse.Option option=market.option(speed,includeTag);if(option==null) return;
        try {
            JsonObject row=option.calculator.calculate(amount.getText().toString(),budgetMode);
            text(customResult,"برآورد با همان نرخ جدول؛ ورودی ارسال نشد. نرخ نهایی تضمین نمی‌شود. زمان دریافت:",false);
            text(customResult,market.fetchedAt,true);renderQuote(customResult,row);
        } catch(IllegalArgumentException invalid) {text(customResult,"مقدار معتبر نیست یا بودجه برای حداقل بسته کافی نیست. محدودیت ارقام و اصل مبلغ را بررسی کنید.",false);}
    }
    private void renderQuote(LinearLayout parent,JsonObject row) {
        String[] fields={"principal_sats","btc","network_sats","materials_sats","post_sats","tag_sats","operator_sats","total_sats","total_toman","total_rial","budget_toman","remainder_toman"};
        String[] labels={"اصل؛ ساتوشی","معادل بیت‌کوین","شبکه؛ ساتوشی","مواد پاکت؛ ساتوشی","پست؛ ساتوشی","برچسب؛ ساتوشی","گرداننده؛ ساتوشی","جمع؛ ساتوشی","جمع پرداختی؛ تومان","معادل ریال","بودجه؛ تومان","باقی‌مانده؛ تومان — جزو موجودی پاکت نیست"};
        for(int i=0;i<fields.length;i++) if(!row.get(fields[i]).isJsonNull()) {text(parent,labels[i],false);text(parent,row.get(fields[i]).getAsString(),true);}
    }
    private void openPublic(String page) {
        if (!page.isEmpty() && !isPublicPage(page)) return;
        AlertDialog external = new AlertDialog.Builder(this).setTitle("باز کردن سایت")
            .setMessage("این صفحه در مرورگر گوشی باز می‌شود و اینترنت لازم دارد. سایت اطلاعات اتصال شما را می‌بیند. شمارهٔ برچسب و آدرس کیف پول به لینک اضافه نمی‌شوند.")
            .setNegativeButton("لغو", null).setPositiveButton("باز کردن", (dialog, which) -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(PUBLIC_SITE + page));
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                try { startActivity(intent); } catch (ActivityNotFoundException ex) {
                    AlertDialog missing = new AlertDialog.Builder(this).setMessage("مرورگری برای باز کردن سایت پیدا نشد.").setPositiveButton("بستن", null).show();
                    styleDialog(missing);
                }
            }).show();
        styleDialog(external);
    }
}
