package com.saebm.btcnfccustomer.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.ResponseBody;

/** Public market GET only. Reuses the hardened no-retry/no-redirect transport policy. */
public final class MarketClient {
    private final Call.Factory calls;
    private final LongSupplier wall, monotonic;
    private volatile Call running;
    private volatile boolean cancelled;
    public MarketClient(LongSupplier monotonic) { this(Client.http(),System::currentTimeMillis,monotonic); }
    private MarketClient(Call.Factory calls,LongSupplier wall,LongSupplier monotonic) { this.calls=calls;this.wall=wall;this.monotonic=monotonic; }
    public static MarketClient forCalls(Call.Factory calls,LongSupplier wall,LongSupplier monotonic) { return new MarketClient(calls,wall,monotonic); }
    public MarketResponse fetch() throws IOException {
        Request request=new Request.Builder().url("https://btc4.fun/api/market").get()
            .header("Accept","application/json").header("Accept-Encoding","identity").header("Cache-Control","no-store").build();
        if(cancelled) throw new IOException("Cancelled");
        Call call=calls.newCall(request);running=call;
        if(cancelled) {call.cancel();throw new IOException("Cancelled");}
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(RequestPolicy.TOTAL_MS);
        try(okhttp3.Response response=call.execute()) {
            if(response.code()!=200) throw new Client.Failure(response.code(),response.code()==429?RequestPolicy.retrySeconds(response.header("Retry-After")):0);
            String type=response.header("Content-Type"),encoding=response.header("Content-Encoding");
            if(type==null||!type.split(";",2)[0].trim().equalsIgnoreCase("application/json")||encoding!=null&&!encoding.equalsIgnoreCase("identity")) throw new IOException("Unexpected format");
            ResponseBody body=response.body();
            if(body==null||body.contentLength()>RequestPolicy.MAX_BODY) throw new IOException("Invalid size");
            try(InputStream in=body.byteStream()) {
                String json=RequestPolicy.read(in,RequestPolicy.MAX_BODY,deadline);
                if(cancelled) throw new IOException("Cancelled");
                try {return MarketResponse.parse(json,wall.getAsLong(),monotonic.getAsLong());}
                catch(IllegalArgumentException invalid) {throw new IOException("Invalid market response");}
            }
        } finally {running=null;}
    }
    public void cancel() {cancelled=true;Call call=running;if(call!=null) call.cancel();}
}
