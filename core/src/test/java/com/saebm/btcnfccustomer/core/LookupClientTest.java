package com.saebm.btcnfccustomer.core;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Call;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.Test;
import static org.junit.Assert.*;

public class LookupClientTest {
    static final String ADDRESS = "bc1qqqqsyqcyq5rqwzqfpg9scrgwpugpzysn4v0345";
    static final String SERIAL = "26b1z00001";
    static final long WALL = 1793491200000L; // 2026-11-01T00:00:00Z
    static final long MONO = 1000L;

    static String fields() {
        return "{\"address\":\"" + ADDRESS + "\",\"serial\":\"" + SERIAL + "\",\"lookup_url\":\"https://btc4.fun/s/" + SERIAL + "\"";
    }
    static String mapping() { return fields() + "}"; }
    static String full() {
        return fields() + ",\"balance\":{\"address\":\"" + ADDRESS + "\",\"confirmed_sats\":500,\"pending_delta_sats\":-50,"
            + "\"source\":\"https://blockstream.info/api\",\"fetched_at\":\"2026-10-31T23:59:30+00:00\"},"
            + "\"public\":{\"notes\":\"یادداشت نمونه\",\"site\":{\"key\":\"example-shop\",\"url\":\"https://example.com/\"},"
            + "\"image_url\":\"https://btc4.fun/public-images/1.png\","
            + "\"links\":{\"instagram\":\"https://instagram.com/example\",\"telegram\":\"https://t.me/example\"}}}";
    }
    static LookupClient local(MockWebServer server) {
        return LookupClient.forCalls(request -> Client.http().newCall(request.newBuilder().url(server.url(request.url().encodedPath() + (request.url().encodedQuery() == null ? "" : "?" + request.url().encodedQuery()))).build()), "https://btc4.fun/", () -> WALL, () -> MONO);
    }
    static MockResponse json(String body) { return new MockResponse().setHeader("Content-Type", "application/json").setBody(body); }

    @Test public void addressLookupWithoutFlagKeepsTheThreeFieldContract() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(json(mapping())); server.start();
            LookupClient.Lookup lookup = local(server).fetchByAddress(ADDRESS, false);
            assertEquals(ADDRESS, lookup.address); assertEquals(SERIAL, lookup.serial);
            assertEquals("https://btc4.fun/s/" + SERIAL, lookup.lookupUrl);
            assertNull(lookup.balance); assertNull(lookup.publicInfo);
            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            assertEquals("/api/addresses/" + ADDRESS, request.getPath()); assertEquals("GET", request.getMethod());
            assertEquals("identity", request.getHeader("Accept-Encoding")); assertEquals("no-store", request.getHeader("Cache-Control"));
            assertNull(request.getHeader("Cookie")); assertEquals(1, server.getRequestCount());
        }
    }
    @Test public void addressLookupWithFlagParsesBalanceAndPublicFields() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(json(full())); server.start();
            LookupClient.Lookup lookup = local(server).fetchByAddress(ADDRESS, true);
            assertEquals("/api/addresses/" + ADDRESS + "?balance=1", server.takeRequest(1, TimeUnit.SECONDS).getPath());
            assertEquals(500, lookup.balance.confirmedSats); assertEquals(-50, lookup.balance.pendingDeltaSats);
            assertEquals(ADDRESS, lookup.balance.address); assertEquals("https://blockstream.info/api", lookup.balance.source);
            assertEquals("2026-10-31T23:59:30+00:00", lookup.balance.fetchedAt);
            // fetched_at is 30s before WALL, so it is fresh for 30s more and usable for 270s more.
            assertTrue(lookup.balance.freshness.fresh(MONO + 29999)); assertFalse(lookup.balance.freshness.fresh(MONO + 30000));
            assertTrue(lookup.balance.freshness.usable(MONO + 269999)); assertFalse(lookup.balance.freshness.usable(MONO + 270000));
            assertEquals("یادداشت نمونه", lookup.publicInfo.notes); assertEquals("example-shop", lookup.publicInfo.siteKey);
            assertEquals("https://example.com/", lookup.publicInfo.siteUrl); assertEquals("https://btc4.fun/public-images/1.png", lookup.publicInfo.imageUrl);
            assertEquals(2, lookup.publicInfo.links.size());
            assertEquals("instagram", lookup.publicInfo.links.get(0).key);
            assertEquals("https://instagram.com/example", lookup.publicInfo.links.get(0).url);
            assertEquals("telegram", lookup.publicInfo.links.get(1).key);
            assertEquals("https://t.me/example", lookup.publicInfo.links.get(1).url);
        }
    }
    /** A payload whose public block is null or absent: the card still loads with no extra fields. */
    @Test public void publicPayloadWithoutLinksStillParses() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(json(fields() + ",\"balance\":{\"address\":\"" + ADDRESS + "\",\"confirmed_sats\":0,\"pending_delta_sats\":0,\"source\":\"x\",\"fetched_at\":\"2026-10-31T23:59:30Z\"},\"public\":{\"notes\":null,\"site\":{\"key\":null,\"url\":null},\"image_url\":null}}"));
            server.start();
            LookupClient.Lookup lookup = local(server).fetchByAddress(ADDRESS, true);
            assertTrue(lookup.publicInfo.empty());
            assertTrue(lookup.publicInfo.links.isEmpty());
        }
    }

    /** One bad entry is dropped; the rest of the card and the good links survive. */
    @Test public void badLinkIsDroppedWithoutLosingTheCard() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(json(fields() + ",\"balance\":{\"address\":\"" + ADDRESS + "\",\"confirmed_sats\":0,\"pending_delta_sats\":0,\"source\":\"x\",\"fetched_at\":\"2026-10-31T23:59:30Z\"},"
                + "\"public\":{\"notes\":\"n\",\"site\":{\"key\":null,\"url\":null},\"image_url\":null,\"links\":{"
                + "\"Instagram\":\"https://instagram.com/example\","
                + "\"bad key\":\"https://t.me/x\","
                + "\"tel\":\"http://insecure.example/\","
                + "\"mail\":\"https://mail.example/contact\"}}}"));
            server.start();
            LookupClient.Lookup lookup = local(server).fetchByAddress(ADDRESS, true);
            assertEquals("n", lookup.publicInfo.notes);
            assertEquals(2, lookup.publicInfo.links.size());
            assertEquals("instagram", lookup.publicInfo.links.get(0).key);
            assertEquals("mail", lookup.publicInfo.links.get(1).key);
        }
    }

    @Test public void serialLookupUsesWalletPathAndFlag() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(json(full())); server.start();
            LookupClient.Lookup lookup = local(server).fetchBySerial(SERIAL, true);
            assertEquals("/api/wallets/" + SERIAL + "?balance=1", server.takeRequest(1, TimeUnit.SECONDS).getPath());
            assertEquals(SERIAL, lookup.serial); assertEquals(500, lookup.balance.confirmedSats);
        }
    }
    @Test public void serialLookupWithoutFlagHasNoBalance() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(json(mapping())); server.start();
            assertNull(local(server).fetchBySerial(SERIAL, false).balance);
            assertEquals("/api/wallets/" + SERIAL, server.takeRequest(1, TimeUnit.SECONDS).getPath());
        }
    }
    @Test public void notFoundIsAStatusFailureForBothSources() throws Exception {
        for (boolean byAddress : new boolean[]{true, false}) {
            try (MockWebServer server = new MockWebServer()) {
                server.enqueue(new MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json").setBody("{\"error\":\"not_found\"}")); server.start();
                LookupClient client = local(server);
                Client.Failure failure = assertThrows(Client.Failure.class, () -> {
                    if (byAddress) client.fetchByAddress(ADDRESS, true); else client.fetchBySerial(SERIAL, true);
                });
                assertEquals(404, failure.status); assertEquals(1, server.getRequestCount());
            }
        }
    }
    @Test public void rateLimitCarriesRetryAfterAndIsNeverRetried() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "43").setHeader("Content-Type", "application/json").setBody("{\"error\":\"rate_limited\",\"retry_after\":43}"));
            server.enqueue(json(full())); server.start();
            Client.Failure failure = assertThrows(Client.Failure.class, () -> local(server).fetchByAddress(ADDRESS, true));
            assertEquals(429, failure.status); assertEquals(43, failure.retrySeconds); assertEquals(1, server.getRequestCount());
        }
    }
    @Test public void balanceUnavailableKeepsMappingButNeverABalance() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(503).setHeader("Content-Type", "application/json")
                .setBody(fields() + ",\"error\":\"balance_unavailable\"}")); server.start();
            LookupClient.BalanceUnavailable failure = assertThrows(LookupClient.BalanceUnavailable.class, () -> local(server).fetchByAddress(ADDRESS, true));
            assertEquals(ADDRESS, failure.lookup.address); assertEquals(SERIAL, failure.lookup.serial); assertNull(failure.lookup.balance);
            assertEquals(1, server.getRequestCount());
        }
    }
    @Test public void plainServiceUnavailableIsNotTreatedAsAMapping() throws Exception {
        for (String body : new String[]{"{\"error\":\"unavailable\"}", "{\"error\":\"balance_unavailable\"}", "not json", fields() + ",\"error\":\"balance_unavailable\",\"balance\":{\"address\":\"" + ADDRESS + "\",\"confirmed_sats\":0,\"pending_delta_sats\":0,\"source\":\"x\",\"fetched_at\":\"2026-10-31T23:59:30Z\"}}"}) {
            try (MockWebServer server = new MockWebServer()) {
                server.enqueue(new MockResponse().setResponseCode(503).setHeader("Content-Type", "application/json").setBody(body)); server.start();
                Client.Failure failure = assertThrows(Client.Failure.class, () -> local(server).fetchByAddress(ADDRESS, true));
                assertEquals(503, failure.status);
            }
        }
    }
    @Test public void rejectsExtraDuplicateAndBadTypeKeys() throws Exception {
        String[] bodies = {
            fields() + ",\"extra\":1}",
            fields() + ",\"serial\":\"" + SERIAL + "\"}",
            fields().replace("\"serial\":\"" + SERIAL + "\"", "\"serial\":1") + "}",
            fields() + ",\"address\":\"" + ADDRESS + "\"}",
            full().replace("\"confirmed_sats\":500", "\"confirmed_sats\":\"500\""),
            full().replace("\"confirmed_sats\":500", "\"confirmed_sats\":1.5"),
            full().replace("\"confirmed_sats\":500", "\"confirmed_sats\":-1"),
            full().replace("\"pending_delta_sats\":-50", "\"pending_delta_sats\":null"),
            full().replace("2026-10-31T23:59:30+00:00", "yesterday"),
            full().replace("2026-10-31T23:59:30+00:00", "2026-10-01T00:00:00Z"),
            full() + " trailing",
            // no flag, but the site returned a balance anyway: not our contract, refuse it.
            "{\"error\":\"invalid_lookup_request\"}",
        };
        for (String body : bodies) {
            try (MockWebServer server = new MockWebServer()) {
                server.enqueue(json(body)); server.start();
                assertThrows(IOException.class, () -> local(server).fetchByAddress(ADDRESS, true));
            }
        }
        // The un-flagged request must reject a balance-carrying body, never silently accept it.
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(json(full())); server.start();
            assertThrows(IOException.class, () -> local(server).fetchByAddress(ADDRESS, false));
        }
    }
    @Test public void rejectsMismatchedAddressSerialAndLookupUrl() throws Exception {
        String other = "bc1qzmtrqsfuaf6l6kkcsseumq26ukaphfj9skkug6";
        String[] bodies = {
            mapping().replace("\"address\":\"" + ADDRESS + "\"", "\"address\":\"" + other + "\""),
            full().replace("\"balance\":{\"address\":\"" + ADDRESS + "\"", "\"balance\":{\"address\":\"" + other + "\""),
            mapping().replace("https://btc4.fun/s/" + SERIAL, "https://evil.example/s/" + SERIAL),
            mapping().replace("https://btc4.fun/s/" + SERIAL, "http://btc4.fun/s/" + SERIAL),
            mapping().replace("https://btc4.fun/s/" + SERIAL, "https://btc4.fun/s/26b1z00002"),
            mapping().replace("\"serial\":\"" + SERIAL + "\"", "\"serial\":\"TEST-ONLY\""),
        };
        for (String body : bodies) {
            try (MockWebServer server = new MockWebServer()) {
                server.enqueue(json(body)); server.start();
                assertThrows(IOException.class, () -> local(server).fetchByAddress(ADDRESS, true));
            }
        }
        try (MockWebServer server = new MockWebServer()) {
            // Site echoes a different serial than the one requested.
            server.enqueue(json(mapping().replace("\"serial\":\"" + SERIAL + "\"", "\"serial\":\"26b1z00002\"").replace("https://btc4.fun/s/" + SERIAL, "https://btc4.fun/s/26b1z00002"))); server.start();
            assertThrows(IOException.class, () -> local(server).fetchBySerial(SERIAL, true));
        }
    }
    @Test public void rejectsUnsafePublicFields() throws Exception {
        String[] bodies = {
            full().replace("https://example.com/", "http://example.com/"),
            full().replace("https://example.com/", "https://user@example.com/"),
            full().replace("https://example.com/", "https://example.com:8443/"),
            full().replace("\"key\":\"example-shop\"", "\"key\":\"bad key\""),
            full().replace("\"key\":\"example-shop\",", ""),
            full().replace("\"notes\":\"یادداشت نمونه\"", "\"notes\":\"bad\\u0007note\""),
            full().replace("\"notes\":\"یادداشت نمونه\"", "\"notes\":\"" + "x".repeat(281) + "\""),
            full().replace("\"notes\":\"یادداشت نمونه\"", "\"notes\":\"bad\\u200cnote\""),
        };
        for (String body : bodies) {
            try (MockWebServer server = new MockWebServer()) {
                server.enqueue(json(body)); server.start();
                assertThrows(IOException.class, () -> local(server).fetchByAddress(ADDRESS, true));
            }
        }
    }
    @Test public void foreignImageHostIsDroppedNotShown() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(json(full().replace("https://btc4.fun/public-images/1.png", "https://cdn.example/1.png"))); server.start();
            LookupClient.Lookup lookup = local(server).fetchByAddress(ADDRESS, true);
            assertNull(lookup.publicInfo.imageUrl); assertEquals("example-shop", lookup.publicInfo.siteKey);
        }
    }
    @Test public void emptyPublicFieldsAreOmitted() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(json(fields() + ",\"balance\":{\"address\":\"" + ADDRESS + "\",\"confirmed_sats\":0,\"pending_delta_sats\":0,\"source\":\"x\",\"fetched_at\":\"2026-10-31T23:59:30Z\"},\"public\":{\"notes\":null,\"site\":{\"key\":null,\"url\":null},\"image_url\":null}}")); server.start();
            LookupClient.Lookup lookup = local(server).fetchByAddress(ADDRESS, true);
            assertEquals(0, lookup.balance.confirmedSats); assertTrue(lookup.publicInfo.empty());
        }
    }
    @Test public void wrongContentTypeAndOversizeFailClosed() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setHeader("Content-Type", "text/html").setBody(full()));
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("x".repeat(RequestPolicy.MAX_BODY + 1)));
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setChunkedBody("x".repeat(RequestPolicy.MAX_BODY + 1), 1024)); server.start();
            for (int i = 0; i < 3; i++) assertThrows(IOException.class, () -> local(server).fetchByAddress(ADDRESS, true));
        }
    }
    @Test public void invalidInputNeverReachesTheNetwork() {
        AtomicInteger calls = new AtomicInteger();
        LookupClient client = LookupClient.forCalls(request -> { calls.incrementAndGet(); return Client.http().newCall(request); }, "https://btc4.fun/");
        assertThrows(IllegalArgumentException.class, () -> client.fetchByAddress("bc1qtestonly", true));
        assertThrows(IllegalArgumentException.class, () -> client.fetchBySerial("TEST-ONLY", true));
        assertThrows(IllegalArgumentException.class, () -> client.fetchBySerial("26b1z00001/", true));
        assertEquals(0, calls.get());
    }
    @Test public void cancelBeforeAndDuringRequest() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)); server.start();
            LookupClient stopped = local(server); stopped.cancel();
            assertThrows(IOException.class, () -> stopped.fetchByAddress(ADDRESS, true)); assertEquals(0, server.getRequestCount());
            LookupClient running = local(server);
            java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor();
            try {
                Future<?> task = worker.submit(() -> assertThrows(IOException.class, () -> running.fetchByAddress(ADDRESS, true)));
                assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)); running.cancel(); task.get(2, TimeUnit.SECONDS);
                assertEquals(1, server.getRequestCount());
            } finally { running.cancel(); worker.shutdownNow(); }
        }
    }
    @Test public void explorerLinkIsBuiltLocally() {
        assertEquals("https://mempool.space/address/" + ADDRESS, LookupClient.explorerUrl(ADDRESS));
        assertThrows(IllegalArgumentException.class, () -> LookupClient.explorerUrl("https://evil.example/"));
    }
}
