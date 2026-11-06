package com.saebm.btcnfccustomer.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.ResponseBody;

/**
 * One explicitly requested public lookup by printed serial or public address.
 * Strict JSON (unique keys, exact types), fixed origin, no redirects, retries, cookies or cache.
 */
public final class LookupClient {
    /** The only image host the customer app accepts; anything else is dropped, not shown. */
    public static final String IMAGE_HOST = "btc4.fun";
    public static final String LOOKUP_PREFIX = "https://btc4.fun/s/";
    /** The most public links the card will show; the site caps the same way. */
    public static final int MAX_LINKS = 20;

    public static final class Balance {
        public final String address, source, fetchedAt;
        public final long confirmedSats, pendingDeltaSats;
        public final Freshness freshness;
        private Balance(String address, long confirmed, long pending, String source, String fetchedAt, Freshness freshness) {
            this.address = address; this.confirmedSats = confirmed; this.pendingDeltaSats = pending;
            this.source = source; this.fetchedAt = fetchedAt; this.freshness = freshness;
        }
    }

    /**
     * Optional seller fields. Each is null when absent or rejected; the card omits nulls.
     * {@code links} is an ordered list of seller-chosen key/value pairs (instagram, telegram, ...);
     * it is empty when the site sends none or every entry is rejected.
     */
    public static final class PublicInfo {
        public final String notes, siteKey, siteUrl, imageUrl;
        public final java.util.List<Link> links;
        private PublicInfo(String notes, String siteKey, String siteUrl, String imageUrl, java.util.List<Link> links) {
            this.notes = notes; this.siteKey = siteKey; this.siteUrl = siteUrl; this.imageUrl = imageUrl;
            this.links = java.util.Collections.unmodifiableList(links);
        }
        public boolean empty() {
            return notes == null && siteKey == null && imageUrl == null && links.isEmpty();
        }
    }

    /** One seller-chosen public link: a short label and an https address. */
    public static final class Link {
        public final String key, url;
        private Link(String key, String url) { this.key = key; this.url = url; }
    }

    public static final class Lookup {
        public final String address, serial, lookupUrl;
        /** Null when the site answered without a balance (no flag, or 503 balance_unavailable). */
        public final Balance balance;
        public final PublicInfo publicInfo;
        private Lookup(String address, String serial, String lookupUrl, Balance balance, PublicInfo publicInfo) {
            this.address = address; this.serial = serial; this.lookupUrl = lookupUrl;
            this.balance = balance; this.publicInfo = publicInfo;
        }
    }

    /** 503 with a healthy mapping: the card may show the mapping and public fields, never a balance. */
    public static final class BalanceUnavailable extends IOException {
        public final Lookup lookup;
        public BalanceUnavailable(Lookup lookup) { super("Balance unavailable"); this.lookup = lookup; }
    }

    private final Call.Factory calls;
    private final String base;
    private final LongSupplier wall, monotonic;
    private volatile Call running;
    private volatile boolean cancelled;

    public LookupClient() { this(Client.http(), "https://btc4.fun/", System::currentTimeMillis, System::nanoTime); }
    /** Production instance for callers that must not see the transport types (okhttp is core-only). */
    public static LookupClient production() { return new LookupClient(); }
    /** Instance whose clock is the Android monotonic clock, still on the fixed origin. */
    public static LookupClient production(LongSupplier monotonic) { return new LookupClient(Client.http(), "https://btc4.fun/", System::currentTimeMillis, monotonic); }
    public static LookupClient forCalls(Call.Factory calls, String base) {
        return new LookupClient(calls, base, System::currentTimeMillis, System::nanoTime);
    }
    public static LookupClient forCalls(Call.Factory calls, String base, LongSupplier wall, LongSupplier monotonic) {
        return new LookupClient(calls, base, wall, monotonic);
    }
    private LookupClient(Call.Factory calls, String base, LongSupplier wall, LongSupplier monotonic) {
        this.calls = calls; this.base = base.endsWith("/") ? base : base + "/";
        this.wall = wall; this.monotonic = monotonic;
    }

    public Lookup fetchBySerial(String serial, boolean balance) throws IOException {
        return fetch(RequestPolicy.walletUrl(serial, balance), serial, null, balance);
    }
    public Lookup fetchByAddress(String address, boolean balance) throws IOException {
        return fetch(RequestPolicy.addressUrl(address, balance), null, address, balance);
    }

    private Lookup fetch(java.net.URL url, String serial, String address, boolean balance) throws IOException {
        Request request = new Request.Builder().url(base + url.getPath().substring(1) + (url.getQuery() == null ? "" : "?" + url.getQuery())).get()
            .header("Accept", "application/json").header("Accept-Encoding", "identity").header("Cache-Control", "no-store").build();
        if (cancelled) throw new IOException("Cancelled");
        Call call = calls.newCall(request); running = call;
        if (cancelled) { call.cancel(); throw new IOException("Cancelled"); }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RequestPolicy.TOTAL_MS);
        try (okhttp3.Response response = call.execute()) {
            if (response.code() != 200) {
                int retry = response.code() == 429 ? RequestPolicy.retrySeconds(response.header("Retry-After")) : 0;
                if (response.code() == 503) {
                    Lookup mapping = balanceUnavailable(response, serial, address);
                    if (mapping != null) throw new BalanceUnavailable(mapping);
                }
                throw new Client.Failure(response.code(), retry);
            }
            String type = response.header("Content-Type");
            if (type == null || !type.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) throw new IOException("Unexpected content type");
            String encoding = response.header("Content-Encoding");
            if (encoding != null && !encoding.equalsIgnoreCase("identity")) throw new IOException("Unexpected encoding");
            ResponseBody body = response.body();
            if (body == null || body.contentLength() > RequestPolicy.MAX_BODY) throw new IOException("Invalid body size");
            try (InputStream in = body.byteStream()) {
                String json = RequestPolicy.read(in, RequestPolicy.MAX_BODY, deadline);
                if (cancelled) throw new IOException("Cancelled");
                try { return parse(json, serial, address, balance, wall.getAsLong(), monotonic.getAsLong()); }
                catch (IllegalArgumentException ex) { throw new IOException("Invalid response"); }
            }
        } finally { running = null; }
    }

    /** A 503 body is only trusted when it carries the full healthy mapping; otherwise it is a plain failure. */
    private Lookup balanceUnavailable(okhttp3.Response response, String serial, String address) {
        try {
            ResponseBody body = response.body();
            if (body == null || body.contentLength() > RequestPolicy.MAX_BODY) return null;
            String json = RequestPolicy.read(body.byteStream(), RequestPolicy.MAX_BODY, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RequestPolicy.TOTAL_MS));
            JsonObject root = unique(json);
            if (!"balance_unavailable".equals(string(root, "error", 32))) return null;
            // Validate the healthy mapping without the error marker and never accept a balance in a 503.
            root.remove("error");
            if (root.has("balance") || root.has("public")) return null;
            return mapping(root, serial, address, false, wall.getAsLong(), monotonic.getAsLong());
        } catch (IOException | RuntimeException ex) { return null; }
    }

    public void cancel() { cancelled = true; Call call = running; if (call != null) call.cancel(); }

    static Lookup parse(String json, String serial, String address, boolean balance, long wallNow, long monotonicNow) {
        if (json.length() > RequestPolicy.MAX_BODY) throw new IllegalArgumentException();
        try { return mapping(unique(json), serial, address, balance, wallNow, monotonicNow); }
        catch (java.time.format.DateTimeParseException ex) { throw new IllegalArgumentException(); }
    }

    private static Lookup mapping(JsonObject root, String serial, String address, boolean balance, long wallNow, long monotonicNow) {
        // Only the agreed keys are allowed; balance and the optional public block travel together with the flag.
        Set<String> allowed = balance ? new LinkedHashSet<>(Arrays.asList("address", "serial", "lookup_url", "balance", "public"))
            : new LinkedHashSet<>(Arrays.asList("address", "serial", "lookup_url"));
        if (!allowed.containsAll(root.keySet())) throw new IllegalArgumentException();
        String returnedAddress = string(root, "address", 128), returnedSerial = string(root, "serial", 128);
        String lookupUrl = string(root, "lookup_url", 512);
        if (!BitcoinAddress.isValid(returnedAddress) || !RequestPolicy.validSerial(returnedSerial)) throw new IllegalArgumentException();
        if (address != null && !address.equals(returnedAddress)) throw new IllegalArgumentException();
        if (serial != null && !serial.equals(returnedSerial)) throw new IllegalArgumentException();
        if (!lookupUrl.equals(LOOKUP_PREFIX + returnedSerial)) throw new IllegalArgumentException();
        Balance parsed = root.has("balance") ? balance(root.get("balance"), returnedAddress, wallNow, monotonicNow) : null;
        PublicInfo info = root.has("public") ? publicInfo(root.get("public")) : null;
        return new Lookup(returnedAddress, returnedSerial, lookupUrl, parsed, info);
    }

    private static Balance balance(JsonElement element, String address, long wallNow, long monotonicNow) {
        JsonObject row = object(element);
        if (!row.keySet().equals(new HashSet<>(Arrays.asList("address", "confirmed_sats", "pending_delta_sats", "source", "fetched_at")))) throw new IllegalArgumentException();
        String returned = string(row, "address", 128);
        if (!returned.equals(address)) throw new IllegalArgumentException();
        long confirmed = integer(row, "confirmed_sats"), pending = integer(row, "pending_delta_sats");
        if (confirmed < 0) throw new IllegalArgumentException();
        String source = string(row, "source", 128), fetchedAt = string(row, "fetched_at", 64);
        return new Balance(returned, confirmed, pending, source, fetchedAt, Freshness.at(fetchedAt, wallNow, monotonicNow));
    }

    private static PublicInfo publicInfo(JsonElement element) {
        JsonObject row = object(element);
        // "links" is newer than the first app builds: it is optional so old and new payloads both parse,
        // while any other unknown key still fails closed.
        Set<String> keys = row.keySet();
        if (!new HashSet<>(Arrays.asList("notes", "site", "image_url", "links")).containsAll(keys)) throw new IllegalArgumentException();
        String notes = optionalText(row, "notes", 280);
        String siteKey = null, siteUrl = null;
        if (row.has("site")) {
            JsonObject site = object(row.get("site"));
            if (!site.keySet().equals(new HashSet<>(Arrays.asList("key", "url")))) throw new IllegalArgumentException();
            siteKey = optionalText(site, "key", 64);
            siteUrl = optionalText(site, "url", 512);
            if (siteKey != null && !siteKey.matches("[A-Za-z0-9._-]{1,64}")) throw new IllegalArgumentException();
            if (siteUrl != null && !httpsUrl(siteUrl, false)) throw new IllegalArgumentException();
            if ((siteKey == null) != (siteUrl == null)) throw new IllegalArgumentException();
        }
        String imageUrl = optionalText(row, "image_url", 512);
        if (imageUrl != null && !httpsUrl(imageUrl, true)) imageUrl = null;
        return new PublicInfo(notes, siteKey, siteUrl, imageUrl, links(row.get("links")));
    }

    /**
     * Seller-chosen public links. A single bad entry is dropped on its own, never the whole card.
     * Keys are lowercase labels (a-z 0-9 . _ -, 1-32) and values are absolute https URLs.
     */
    private static java.util.List<Link> links(JsonElement element) {
        java.util.List<Link> out = new java.util.ArrayList<>();
        if (element == null || element.isJsonNull()) return out;
        if (!element.isJsonObject()) return out;
        for (java.util.Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (out.size() >= MAX_LINKS) break;
            String key = entry.getKey().trim().toLowerCase(Locale.ROOT);
            if (!key.matches("[a-z0-9][a-z0-9._-]{0,31}")) continue;
            JsonElement value = entry.getValue();
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) continue;
            String url = value.getAsString().trim();
            if (url.isEmpty() || url.length() > 512 || !httpsUrl(url, false) || !cleanText(url)) continue;
            out.add(new Link(key, url));
        }
        return out;
    }

    /** Text with no control or bidi/format characters, the same admission rule as the site. */
    private static boolean cleanText(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || (c >= 0x7f && c <= 0x9f) || (c >= 0x200b && c <= 0x200f)
                    || (c >= 0x202a && c <= 0x202e) || (c >= 0x2060 && c <= 0x206f) || c == 0xfeff) return false;
        }
        return true;
    }

    /** https only, no userinfo, no port, no query/fragment; image host must be the approved one. */
    private static boolean httpsUrl(String value, boolean image) {
        try {
            URI uri = new URI(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getPort() != -1
                    || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) return false;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return !image || host.equals(IMAGE_HOST);
        } catch (java.net.URISyntaxException ex) { return false; }
    }

    private static JsonObject unique(String json) {
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setStrictness(Strictness.STRICT);
            JsonElement root = uniqueJson(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT || !root.isJsonObject()) throw new IllegalArgumentException();
            return root.getAsJsonObject();
        } catch (IOException ex) { throw new IllegalArgumentException(); }
    }

    private static JsonElement uniqueJson(JsonReader reader, int depth) throws IOException {
        if (depth > 8) throw new IllegalArgumentException();
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                JsonObject object = new JsonObject(); reader.beginObject();
                while (reader.hasNext()) { String key = reader.nextName(); if (object.has(key)) throw new IllegalArgumentException(); object.add(key, uniqueJson(reader, depth + 1)); }
                reader.endObject(); return object;
            case BEGIN_ARRAY:
                JsonArray array = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) { if (array.size() >= 16) throw new IllegalArgumentException(); array.add(uniqueJson(reader, depth + 1)); }
                reader.endArray(); return array;
            case STRING: return new JsonPrimitive(reader.nextString());
            case NUMBER: return com.google.gson.JsonParser.parseString(reader.nextString());
            case BOOLEAN: return new JsonPrimitive(reader.nextBoolean());
            case NULL: reader.nextNull(); return JsonNull.INSTANCE;
            default: throw new IllegalArgumentException();
        }
    }

    private static JsonObject object(JsonElement element) {
        if (element == null || !element.isJsonObject()) throw new IllegalArgumentException();
        return element.getAsJsonObject();
    }

    private static String string(JsonObject row, String key, int max) {
        JsonElement element = row.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
        String value = element.getAsString();
        if (value.isEmpty() || value.length() > max || unsafe(value)) throw new IllegalArgumentException();
        return value;
    }

    /** Absent, null or empty means "not provided"; a present value must still be clean text. */
    private static String optionalText(JsonObject row, String key, int max) {
        JsonElement element = row.get(key);
        if (element == null || element.isJsonNull()) return null;
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
        String value = element.getAsString();
        if (value.isEmpty()) return null;
        if (value.length() > max || unsafe(value)) throw new IllegalArgumentException();
        return value;
    }

    private static boolean unsafe(String value) {
        return value.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT);
    }

    private static long integer(JsonObject row, String key) {
        JsonElement element = row.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
        String value = element.getAsString();
        if (!value.matches("-?(0|[1-9][0-9]{0,18})")) throw new IllegalArgumentException();
        return Long.parseLong(value);
    }

    /** Explorer link is built locally from the validated address, never taken from the site. */
    public static String explorerUrl(String address) {
        if (!BitcoinAddress.isValid(address)) throw new IllegalArgumentException("Invalid address");
        return "https://mempool.space/address/" + address;
    }
}
