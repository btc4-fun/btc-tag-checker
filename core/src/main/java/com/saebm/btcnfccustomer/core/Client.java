package com.saebm.btcnfccustomer.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.CookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;

/** One explicitly requested GET. No redirects, retries, credentials, cookies, disk cache or logs. */
public final class Client {
    public static final class Failure extends IOException {
        public final int status, retrySeconds;
        public Failure(int status, int seconds) { super("HTTP request rejected"); this.status = status; this.retrySeconds = seconds; }
    }
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES).cache(null)
        // OkHttp otherwise follows 503 + Retry-After: 0 even with connection retries disabled.
        // A network interceptor prevents that internal follow-up; 429's display header is untouched.
        .addNetworkInterceptor(chain -> {
            okhttp3.Response response = chain.proceed(chain.request());
            return response.code() == 503 ? response.newBuilder().header("Retry-After", "1").build() : response;
        })
        .connectTimeout(RequestPolicy.CONNECT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(RequestPolicy.READ_MS, TimeUnit.MILLISECONDS)
        .callTimeout(RequestPolicy.TOTAL_MS, TimeUnit.MILLISECONDS).build();
    private final Call.Factory calls;
    private volatile Call running;
    private volatile boolean cancelled;
    public static OkHttpClient http() { return HTTP; }
    public static Client forCalls(Call.Factory calls) { return new Client(calls); }
    public Client() { this(HTTP); }
    private Client(Call.Factory calls) { this.calls = calls; }
    public Response fetch(String uid, String cursor) throws IOException {
        Request request = new Request.Builder().url(RequestPolicy.url(uid, cursor)).get()
            .header("Accept", "application/json").header("Accept-Encoding", "identity")
            .header("Cache-Control", "no-store").build();
        if (cancelled) throw new IOException("Cancelled");
        Call call = calls.newCall(request); running = call;
        if (cancelled) { call.cancel(); throw new IOException("Cancelled"); }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RequestPolicy.TOTAL_MS);
        try (okhttp3.Response response = call.execute()) {
            if (response.code() != 200) throw new Failure(response.code(), response.code() == 429 ? RequestPolicy.retrySeconds(response.header("Retry-After")) : 0);
            String type = response.header("Content-Type");
            if (type == null || !type.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) throw new IOException("Unexpected content type");
            String encoding = response.header("Content-Encoding");
            if (encoding != null && !encoding.equalsIgnoreCase("identity")) throw new IOException("Unexpected encoding");
            ResponseBody body = response.body();
            if (body == null || body.contentLength() > RequestPolicy.MAX_BODY) throw new IOException("Invalid body size");
            try (InputStream in = body.byteStream()) {
                String json = RequestPolicy.read(in, RequestPolicy.MAX_BODY, deadline);
                if (cancelled) throw new IOException("Cancelled");
                try { return Response.parse(json); } catch (IllegalArgumentException ex) { throw new IOException("Invalid response"); }
            }
        } finally { running = null; }
    }
    public void cancel() { cancelled = true; Call call = running; if (call != null) call.cancel(); }
}
