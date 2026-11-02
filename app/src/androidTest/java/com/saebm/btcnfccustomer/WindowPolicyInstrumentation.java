package com.saebm.btcnfccustomer;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import java.lang.reflect.Method;

/** Real window flags and Activity startup; release policy is not a release APK. */
public final class WindowPolicyInstrumentation extends Instrumentation {
    private static final int SECURE = WindowManager.LayoutParams.FLAG_SECURE;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void callActivityOnCreate(Activity activity, Bundle state) {
        activity.getWindow().addFlags(SECURE);
        super.callActivityOnCreate(activity, state);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    @Override public void onStart() {
        Bundle report = new Bundle();
        Activity activity = null;
        try {
            check(BuildConfig.DEBUG, "This runner requires the debug test artifact");
            activity = startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            Activity target = activity;
            java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
            runOnMainSync(() -> {
                try {
                    Window window = target.getWindow();
                    check((window.getAttributes().flags & SECURE) == 0,
                            "Activity startup must apply the debug screenshot exception");
                    Method apply = MainActivity.class.getDeclaredMethod("applyWindowPolicy", Window.class, boolean.class);
                    apply.setAccessible(true);
                    int unrelated = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
                    window.addFlags(unrelated);
                    apply.invoke(null, window, false);
                    check((window.getAttributes().flags & SECURE) != 0, "Release policy must set FLAG_SECURE");
                    check((window.getAttributes().flags & unrelated) != 0, "Policy must preserve other flags");
                    apply.invoke(null, window, true);
                    check((window.getAttributes().flags & SECURE) == 0, "Debug policy must clear FLAG_SECURE");
                    check((window.getAttributes().flags & unrelated) != 0, "Debug policy preserves other flags");
                } catch (Throwable error) { failure.set(error); }
            });
            if (failure.get() != null) throw failure.get();
            report.putString("stream", "PASS: window policy and actual debug Activity startup\n");
            finish(Activity.RESULT_OK, report);
        } catch (Throwable error) {
            report.putString("stream", "FAIL: " + error + "\n");
            finish(Activity.RESULT_CANCELED, report);
        } finally {
            if (activity != null) { Activity target = activity; runOnMainSync(target::finish); }
        }
    }
}
