package com.saebm.btcnfccustomer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Framework-only emulator assertions: no new runtime/test library or network dependency. */
@android.annotation.TargetApi(30)
public final class VisualInstrumentation extends Instrumentation {
    private Activity activity;
    private int assertions;
    private String capture;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments); capture = arguments.getString("capture"); start();
    }
    private void screenshot(String name) throws Exception {
        waitForIdleSync();
        android.graphics.Bitmap image = getUiAutomation().takeScreenshot();
        if (image == null) throw new AssertionError("emulator screenshot unavailable");
        java.io.File directory = new java.io.File(getTargetContext().getExternalFilesDir(null), "visual-proof");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("screenshot directory");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(directory, name + ".png"))) {
            if (!image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)) throw new java.io.IOException("screenshot write");
        }
        image.recycle();
    }
    private void check(boolean pass, String message) {
        assertions++;
        if (!pass) throw new AssertionError(message);
    }
    private List<View> views(View root) {
        List<View> all = new ArrayList<>(); all.add(root);
        if (root instanceof ViewGroup) for (int i=0;i<((ViewGroup)root).getChildCount();i++) all.addAll(views(((ViewGroup)root).getChildAt(i)));
        return all;
    }
    private View tagged(String tag) {
        return activity.getWindow().getDecorView().findViewWithTag(tag);
    }
    private void invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method=MainActivity.class.getDeclaredMethod(name,types); method.setAccessible(true); method.invoke(activity,args);
    }
    private Object field(String name) throws Exception {
        Field field=MainActivity.class.getDeclaredField(name); field.setAccessible(true); return field.get(activity);
    }
    private void verifyFonts(View root) {
        Typeface regular=activity.getResources().getFont(R.font.sahel_regular);
        Typeface bold=activity.getResources().getFont(R.font.sahel_bold);
        for(View v:views(root)) if(v instanceof TextView) {
            TextView t=(TextView)v;
            check(t.getTypeface().equals(regular)||t.getTypeface().equals(bold),"all native text uses real Sahel: "+t.getText());
            check(!t.isSaveEnabled(),"text must not persist into saved state");
        }
    }
    private void setField(String name,Object value) throws Exception {
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);
    }
    private void toolsChecks() throws Exception {
        invoke("showContact",new Class<?>[]{});verifyFonts(tagged("site-content"));
        check(field("marketClient")==null,"contact must not fetch");
        invoke("openContact",new Class<?>[]{String.class},"https://btc4.fun/contact");
        AlertDialog dialog=(AlertDialog)field("consent");check(dialog!=null&&dialog.isShowing(),"contact explicit confirmation");
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        invoke("showCosts",new Class<?>[]{});
        check(field("marketClient")==null,"opening costs must not fetch");
        check(!((Button)field("calculate")).isEnabled(),"no quote without rates");
        String json;
        try(java.io.InputStream in=getContext().getAssets().open("market.json")) {
            json=com.saebm.btcnfccustomer.core.RequestPolicy.read(in,262144,System.nanoTime()+2_000_000_000L);
        }
        long wall=java.time.OffsetDateTime.parse("2026-11-01T00:00:00+00:00").toInstant().toEpochMilli();
        long mono=android.os.SystemClock.elapsedRealtime();
        com.saebm.btcnfccustomer.core.MarketResponse data=com.saebm.btcnfccustomer.core.MarketResponse.parse(json,wall,mono);
        setField("market",data);invoke("renderMarket",new Class<?>[]{});
        for(String speed:new String[]{"economy","medium","fast"}) for(boolean tag:new boolean[]{false,true}) {
            setField("speed",speed);setField("includeTag",tag);invoke("renderMarket",new Class<?>[]{});
            check(((Button)tagged("speed-"+speed)).getText().toString().contains("انتخاب‌شده"),"speed has visible selected label");
            check(((Button)tagged("tag-choice")).getText().toString().contains(tag?"با برچسب":"بدون برچسب"),"tag control exposes state");
            for(boolean budget:new boolean[]{false,true}) {
                setField("budgetMode",budget);
                ((android.widget.EditText)field("amount")).setText(budget?"500000":"3000");
                invoke("calculateQuote",new Class<?>[]{});
                String expected=data.option(speed,tag).calculator.calculate(budget?"500000":"3000",budget).get("total_toman").getAsString();
                boolean found=false;
                for(View v:views((View)field("customResult"))) if(v instanceof TextView && ((TextView)v).getText().toString().equals(expected)) found=true;
                check(found,"exact quote shown for each option/mode");
            }
            check(field("marketClient")==null,"local changes must not fetch");
        }
        verifyFonts(tagged("site-content"));
        com.saebm.btcnfccustomer.core.Latest gate=(com.saebm.btcnfccustomer.core.Latest)field("marketGate");long token=gate.begin();
        invoke("showContact",new Class<?>[]{});check(!gate.accepts(token),"leaving rejects late market response");
        invoke("showCosts",new Class<?>[]{});
        java.util.concurrent.atomic.AtomicLong clock=new java.util.concurrent.atomic.AtomicLong(1000);
        setField("marketClock",(java.util.function.LongSupplier)clock::get);
        setField("market",com.saebm.btcnfccustomer.core.MarketResponse.parse(json,wall+299000,1000));
        invoke("renderMarket",new Class<?>[]{});
        check(((Button)field("calculate")).isEnabled(),"pre-expiry calculator enabled");
        ((android.widget.EditText)field("amount")).setText("3000");
        invoke("calculateQuote",new Class<?>[]{});
        check(((ViewGroup)field("customResult")).getChildCount()>1,"pre-expiry result populated");
        clock.set(2000);
        invoke("updateMarketAge",new Class<?>[]{});check(!((Button)field("calculate")).isEnabled(),"expired rate blocks calculation");
        check(((ViewGroup)field("customResult")).getChildCount()==1,"expiry removes result");
        check(((ViewGroup)field("marketTable")).getChildCount()==0,"expiry removes table");
        setField("marketClock",(java.util.function.LongSupplier)android.os.SystemClock::elapsedRealtime);
        invoke("showInquiry",new Class<?>[]{});
    }
    @Override public void onStart() {
        Bundle report=new Bundle();
        try {
            Intent intent=new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=startActivitySync(intent); waitForIdleSync();
            if (capture != null) {
                java.util.concurrent.atomic.AtomicReference<Throwable> captureFailure = new java.util.concurrent.atomic.AtomicReference<>();
                runOnMainSync(() -> {
                    try {
                        if (capture.equals("contact")) invoke("showContact",new Class<?>[]{});
                        if (capture.equals("costs")) invoke("showCosts",new Class<?>[]{});
                        if (capture.equals("payment")) invoke("showPublic", new Class<?>[]{String.class,String.class}, "payment", "اطلاعات پرداخت");
                        if (capture.equals("consent")) {
                            Field uid=MainActivity.class.getDeclaredField("uid"); uid.setAccessible(true); uid.set(activity,"00000001");
                            invoke("confirmRequest",new Class<?>[]{String.class},(Object)null);
                        }
                        if (capture.equals("footer")) ((android.widget.ScrollView)tagged("site-scroll")).fullScroll(View.FOCUS_DOWN);
                    } catch(Throwable e) { captureFailure.set(e); }
                });
                if(captureFailure.get()!=null) throw captureFailure.get();
                waitForIdleSync(); android.os.SystemClock.sleep(500);
                screenshot(capture); report.putString("stream","Captured actual emulator screen: "+capture+"\n");
                finish(Activity.RESULT_OK,report); return;
            }
            java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
            runOnMainSync(()->{
                try {
                    View content=tagged("site-content");
                    check(content!=null,"centered website content shell missing");
                    float density=activity.getResources().getDisplayMetrics().density;
                    check(content.getPaddingTop()>=24*density,"deliberate top breathing room beyond insets");
                    check(content.getPaddingBottom()>=32*density,"deliberate bottom breathing room beyond insets");
                    check(content.getWidth()<=Math.round(720*density),"reading width capped in landscape");
                    View scroll=tagged("site-scroll");
                    android.graphics.Insets safe=scroll.getRootWindowInsets().getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
                    check(scroll.getPaddingTop()>=safe.top && scroll.getPaddingBottom()>=safe.bottom && scroll.getPaddingLeft()>=safe.left && scroll.getPaddingRight()>=safe.right,"system bars and cutouts outside content padding");
                    int[] location=new int[2]; content.getLocationOnScreen(location);
                    check(location[1]+content.getPaddingTop()>=safe.top+24*density,"header below safe inset with breathing room");
                    check(tagged("brand-mark")!=null,"website Bitcoin mark missing");
                    check(tagged("scan-section")!=null && tagged("result-section")!=null && tagged("public-section")!=null,"section hierarchy missing");
                    Button requestButton=(Button)tagged("request-button");
                    check(requestButton!=null,"request button semantic locator missing");
                    check(requestButton.getText().toString().equals("۲. دیدن موجودی و تراکنش‌ها"),"request button label matches visible numbered contract");
                    check(!requestButton.isEnabled(),"no request without scanned UID");
                    check(field("client")==null,"opening screen must not create network client");
                    verifyFonts(content);
                    invoke("showPublic",new Class<?>[]{String.class,String.class},"payment","اطلاعات پرداخت");
                    View page=tagged("site-content"); verifyFonts(page);
                    boolean account=false, rtl=false;
                    for(View v:views(page)) if(v instanceof TextView) {
                        TextView t=(TextView)v; String value=t.getText().toString();
                        if(value.equals("6219861819062158")) { account=true; check(t.getTextDirection()==View.TEXT_DIRECTION_LTR,"account number LTR"); }
                        if(value.contains("صائب ملایی")) { rtl=true; check(t.getTextDirection()==View.TEXT_DIRECTION_RTL,"Persian paragraphs RTL"); }
                    }
                    check(account&&rtl,"paragraph splitting preserves separate payment blocks");
                    invoke("showInquiry",new Class<?>[]{});
                    Field uid=MainActivity.class.getDeclaredField("uid"); uid.setAccessible(true); uid.set(activity,"00000001");
                    invoke("confirmRequest",new Class<?>[]{String.class},(Object)null);
                    AlertDialog dialog=(AlertDialog)field("consent");
                    check(dialog!=null && dialog.isShowing(),"explicit consent required");
                    verifyFonts(dialog.getWindow().getDecorView());
                    boolean titleFound=false;
                    for(View v:views(dialog.getWindow().getDecorView())) if(v instanceof TextView && ((TextView)v).getText().toString().equals("اجازه می‌دهید؟")) {
                        titleFound=true;
                        TextView title=(TextView)v;
                        check(title.getTextDirection()==View.TEXT_DIRECTION_RTL && title.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL && (title.getGravity() & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)==Gravity.START,"dialog title aligns with RTL body");
                    }
                    check(titleFound,"plain consent title exists");
                    check(dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString().equals("فقط همین بار ارسال کن"),"consent is for one request only");
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                    check(field("client")==null,"cancel consent must not create network client");
                    toolsChecks();
                    invoke("showInquiry",new Class<?>[]{});
                } catch(Throwable e) { failure.set(e); }
            });
            if (failure.get()!=null) throw failure.get();
            new MarketInteractionChecks(this, activity).run();
            report.putString("stream","PASS: "+assertions+" UI assertions; layout, Sahel, offline paragraphs, consent/no-network\n");
            finish(Activity.RESULT_OK,report);
        } catch(Throwable e) {
            report.putString("stream","FAIL after "+assertions+" assertions: "+e+"\n"); finish(Activity.RESULT_CANCELED,report);
        } finally { if(activity!=null) runOnMainSync(()->activity.finish()); }
    }
}
