package com.saebm.btcnfccustomer.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class QrPayloadTest {
    private static final String ADDRESS = "bc1qqqqsyqcyq5rqwzqfpg9scrgwpugpzysn4v0345";

    @Test public void classifiesFourExactHttpsDomainsAndReturnsSafeUrl() {
        for (String host : new String[]{"btc.saeb.id.ir", "btc4.fun", "bitcoin4.fun", "b.tc4fun.ir"}) {
            QrPayload.Result result = QrPayload.classify("https://" + host + "/guide");
            assertEquals(QrPayload.Kind.ALLOWLISTED_HTTPS_URL, result.kind);
            assertEquals("https://" + host + "/guide", result.value);
        }
        assertEquals(QrPayload.Kind.UNTRUSTED, QrPayload.classify("https://evil.example/").kind);
        assertNull(QrPayload.classify("https://evil.example/").value);
    }

    @Test public void bitcoinUriAcceptsOnlySafeQueryParameters() {
        QrPayload.Result result = QrPayload.classify("bitcoin:" + ADDRESS + "?amount=0.01&label=Gift%20one&message=hello");
        assertEquals(QrPayload.Kind.BITCOIN_URI, result.kind);
        assertEquals(ADDRESS, result.value);
        for (String query : new String[]{"foo=bar", "amount=1&amount=2", "amount", "amount=-1", "amount=1.2.3", "r=https%3A%2F%2Fevil.example"})
            assertEquals(QrPayload.Kind.INVALID, QrPayload.classify("bitcoin:" + ADDRESS + "?" + query).kind);
    }

    @Test public void rejectsInvalidAddressesAndDangerousSchemes() {
        assertEquals(QrPayload.Kind.INVALID, QrPayload.classify(" ").kind);
        assertEquals(QrPayload.Kind.UNTRUSTED, QrPayload.classify("javascript:alert(1)").kind);
        assertEquals(QrPayload.Kind.INVALID, QrPayload.classify(" ").kind);
        assertEquals(QrPayload.Kind.INVALID, QrPayload.classify(null).kind);
    }
}
