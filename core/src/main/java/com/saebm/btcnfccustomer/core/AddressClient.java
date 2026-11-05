package com.saebm.btcnfccustomer.core;

import okhttp3.*;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/** Public address-to-serial lookup. It never requests balances or customer data. */
public final class AddressClient {
    public static final class Mapping {
        public final String address, serial, lookupUrl;
        private Mapping(String address, String serial, String lookupUrl) { this.address=address; this.serial=serial; this.lookupUrl=lookupUrl; }
    }
    private final Call.Factory calls; private final String base;
    public static AddressClient forCalls(Call.Factory calls, String base) { return new AddressClient(calls, base); }
    public AddressClient() { this(Client.http(), "https://btc4.fun/"); }
    private AddressClient(Call.Factory calls, String base) { this.calls=calls; this.base=base.endsWith("/")?base:base+"/"; }
    public Mapping fetch(String address) throws IOException {
        if (!BitcoinAddress.isValid(address)) throw new IllegalArgumentException("Invalid address");
        Request request = new Request.Builder().url(base + "api/addresses/" + address).get()
            .header("Accept", "application/json").header("Accept-Encoding", "identity").header("Cache-Control", "no-store").build();
        try (okhttp3.Response response=calls.newCall(request).execute()) {
            if (response.code()!=200) throw new Client.Failure(response.code(), response.code()==429 ? RequestPolicy.retrySeconds(response.header("Retry-After")) : 0);
            String type=response.header("Content-Type"); if(type==null || !type.split(";",2)[0].trim().equalsIgnoreCase("application/json")) throw new IOException("Unexpected content type");
            ResponseBody body=response.body(); if(body==null || body.contentLength()>RequestPolicy.MAX_BODY) throw new IOException("Invalid body size");
            String json=RequestPolicy.read(body.byteStream(), RequestPolicy.MAX_BODY, System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(RequestPolicy.TOTAL_MS));
            try { return parse(json, address); } catch (RuntimeException ex) { throw new IOException("Invalid response"); }
        }
    }
    private static Mapping parse(String json, String requested) {
        com.google.gson.JsonObject root=com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        if(root.size()!=3 || !root.has("address") || !root.has("serial") || !root.has("lookup_url")) throw new IllegalArgumentException();
        String address=text(root,"address",128), serial=text(root,"serial",128), lookup=text(root,"lookup_url",512);
        try { URL url=new URL(lookup); if(!requested.equals(address) || !serial.matches("[A-Za-z0-9_-]{1,64}") || !lookup.startsWith("https://btc4.fun/s/") || !url.getHost().equals("btc4.fun") || url.getPort()!=-1) throw new IllegalArgumentException(); } catch (java.net.MalformedURLException ex) { throw new IllegalArgumentException(); }
        return new Mapping(address,serial,lookup);
    }
    private static String text(com.google.gson.JsonObject o,String key,int max) { com.google.gson.JsonElement e=o.get(key); if(e==null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(); String v=e.getAsString(); if(v.isEmpty()||v.length()>max||v.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT)) throw new IllegalArgumentException(); return v; }
}
