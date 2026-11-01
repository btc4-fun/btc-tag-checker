package com.saebm.btcnfccustomer;

import android.app.*;
import android.content.Intent;
import android.view.*;
import android.widget.*;
import com.saebm.btcnfccustomer.core.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import okhttp3.*;

/** Actual Activity callback/consent checks with deterministic in-memory transport. */
final class MarketInteractionChecks {
    private final Instrumentation test;
    private final Activity activity;
    private final AtomicLong clock = new AtomicLong(1000);
    private String json;
    MarketInteractionChecks(Instrumentation test, Activity activity) { this.test=test;this.activity=activity; }
    interface Step { void run() throws Exception; }
    private void ui(Step action) throws Exception {
        AtomicReference<Throwable> failure=new AtomicReference<>();
        test.runOnMainSync(()->{try{action.run();}catch(Throwable e){failure.set(e);}});
        if(failure.get()!=null) throw new AssertionError(failure.get());
    }
    private void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    private Object field(String name) throws Exception {Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void set(String name,Object value) throws Exception {Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    private void invoke(String name) throws Exception {Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}
    private Button button(String name) throws Exception {return (Button)field(name);}
    private void settled() throws Exception {
        ((ExecutorService)field("worker")).submit(()->{}).get(5,TimeUnit.SECONDS);
        test.waitForIdleSync();
    }
    private final class Pending implements Call {
        final CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        volatile boolean cancelled;
        Request request;
        int status=200;
        public Request request(){return request;}
        public okhttp3.Response execute() {
            entered.countDown(); boolean interrupted=false;
            for(;;) {try{release.await();break;}catch(InterruptedException ignored){interrupted=true;}}
            if(interrupted) Thread.currentThread().interrupt();
            return new okhttp3.Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("synthetic")
                .header("Content-Type","application/json").header("Retry-After","2")
                .body(ResponseBody.create(json,MediaType.get("application/json"))).build();
        }
        public void enqueue(Callback callback){throw new AssertionError("sync transport expected");}
        public void cancel(){cancelled=true;}
        public boolean isExecuted(){return entered.getCount()==0;}
        public boolean isCanceled(){return cancelled;}
        public okio.Timeout timeout(){return new okio.Timeout();}
        public Call clone(){throw new AssertionError("no retries");}
    }
    private void supply(Pending pending) throws Exception {
        Supplier<MarketClient> factory=()->MarketClient.forCalls(request->{
            check(request.url().toString().equals("https://btc4.fun/api/market"),"exact public endpoint");
            check(request.body()==null,"no private input body");pending.request=request;return pending;
        },()->1793491200000L,clock::get);
        set("marketFactory",factory);
    }
    private void fetch(Pending pending) throws Exception {
        ui(()->{supply(pending);button("marketFetch").performClick();});
        check(pending.entered.await(3,TimeUnit.SECONDS),"button starts request");
    }
    void run() throws Exception {
        try(java.io.InputStream in=test.getContext().getAssets().open("market.json")) {
            json=RequestPolicy.read(in,262144,System.nanoTime()+2_000_000_000L);
        }
        ui(()->{set("marketClock",(LongSupplier)clock::get);invoke("showCosts");});
        Pending success=new Pending(); fetch(success);success.release.countDown();settled();
        ui(()->check(field("market")!=null&&button("calculate").isEnabled(),"actual successful response renders"));
        Pending cancelled=new Pending();fetch(cancelled);
        ui(()->{test.callActivityOnPause(activity);test.callActivityOnResume(activity);});
        check(cancelled.cancelled,"pause cancels in-flight transport");
        cancelled.release.countDown();settled();
        ui(()->check(field("market")==null,"cancelled request cannot render"));
        // A completed transport has no running Call; only its queued delivery needs fencing.
        Pending paused=new Pending(); fetch(paused);
        ui(()->{paused.release.countDown();((ExecutorService)field("worker")).submit(()->{}).get(3,TimeUnit.SECONDS);test.callActivityOnPause(activity);test.callActivityOnResume(activity);});
        test.waitForIdleSync();

        ui(()->check(field("market")==null&&field("marketClient")==null,"queued response rejected after pause/resume; no automatic fetch"));
        Pending old=new Pending();fetch(old);
        Pending newer=new Pending();
        ui(()->{old.release.countDown();((ExecutorService)field("worker")).submit(()->{}).get(3,TimeUnit.SECONDS);invoke("showContact");invoke("showCosts");supply(newer);button("marketFetch").performClick();});
        check(newer.entered.await(3,TimeUnit.SECONDS),"new request started");test.waitForIdleSync();
        ui(()->check(field("market")==null&&!button("marketFetch").isEnabled(),"old callback cannot replace newer request"));
        newer.release.countDown();settled();
        ui(()->check(field("market")!=null,"new response accepted"));
        Pending limited=new Pending();limited.status=429;fetch(limited);limited.release.countDown();settled();
        ui(()->{check(!button("marketFetch").isEnabled(),"429 cooldown disables fetch");invoke("showContact");invoke("showCosts");check(!button("marketFetch").isEnabled(),"navigation preserves cooldown");});
        clock.addAndGet(2000);
        ui(()->{invoke("updateMarketAge");check(button("marketFetch").isEnabled(),"cooldown expires without retry");check(field("marketClient")==null,"cooldown never auto-fetches");});
        contacts();
        ui(()->{set("marketClock",(LongSupplier)android.os.SystemClock::elapsedRealtime);invoke("showInquiry");});
    }
    private void contacts() throws Exception {
        AtomicReference<Intent> launched=new AtomicReference<>();
        Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor(){
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent){launched.set(intent);return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);}

        };
        test.addMonitor(monitor);
        try {
            for(String[] item:new String[][]{{"ایمیل","mailto:09113458375@btc4.fun",Intent.ACTION_SENDTO},{"فرم پرسش عمومی در سایت","https://btc4.fun/contact",Intent.ACTION_VIEW}}) {
                launched.set(null);
                ui(()->{invoke("showContact");clickLabel(activity.getWindow().getDecorView(),item[0]);check(launched.get()==null,"no launch before consent");((AlertDialog)field("consent")).getButton(AlertDialog.BUTTON_NEGATIVE).performClick();check(launched.get()==null,"no launch on cancel");clickLabel(activity.getWindow().getDecorView(),item[0]);((AlertDialog)field("consent")).getButton(AlertDialog.BUTTON_POSITIVE).performClick();});
                test.waitForIdleSync();
                check(launched.get()!=null,"consent launches destination: "+item[0]+" monitor hits="+monitor.getHits());check(item[1].equals(launched.get().getDataString()),"exact public URI no private data");check(item[2].equals(launched.get().getAction()),"correct email/browser action");
            }
        } finally {test.removeMonitor(monitor);}
    }
    private boolean clickLabel(View v,String label) {
        if(v instanceof Button&&((Button)v).getText().toString().equals(label)){v.performClick();return true;}
        if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++) if(clickLabel(((ViewGroup)v).getChildAt(i),label)) return true;
        return false;
    }
}
