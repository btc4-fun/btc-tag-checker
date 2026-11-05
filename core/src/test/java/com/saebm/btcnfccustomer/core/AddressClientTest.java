package com.saebm.btcnfccustomer.core;

import org.junit.Test;
import okhttp3.mockwebserver.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class AddressClientTest {
    private static final String ADDRESS = "bc1qqqqsyqcyq5rqwzqfpg9scrgwpugpzysn4v0345";
    @Test public void fetchesAndExposesOnlyPublicMapping() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
            .setBody("{\"address\":\"" + ADDRESS + "\",\"serial\":\"26b1z00001\",\"lookup_url\":\"https://btc4.fun/s/26b1z00001\"}"));
        server.start();
        AddressClient client = AddressClient.forCalls(Client.http(), server.url("/").url().toString());
        AddressClient.Mapping mapping = client.fetch(ADDRESS);
        assertEquals(ADDRESS, mapping.address); assertEquals("26b1z00001", mapping.serial);
        assertEquals("https://btc4.fun/s/26b1z00001", mapping.lookupUrl);
        assertEquals("/api/addresses/" + ADDRESS, server.takeRequest(1, TimeUnit.SECONDS).getPath());
        server.shutdown();
    }
    @Test public void rejectsExtraOrUnsafeResponseFields() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
            .setBody("{\"address\":\"" + ADDRESS + "\",\"serial\":\"26b1z00001\",\"lookup_url\":\"http://evil\",\"balance\":1}"));
        server.start();
        AddressClient client = AddressClient.forCalls(Client.http(), server.url("/").url().toString());
        try { client.fetch(ADDRESS); fail(); } catch (java.io.IOException expected) { }
        server.shutdown();
    }
}
