package com.saebm.btcnfccustomer.core;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import okhttp3.tls.*;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;
public class TransportTest {
 @Test public void productionClientDisablesRetriesRedirectsCookiesCache() {
  OkHttpClient c=Client.http();assertFalse(c.retryOnConnectionFailure());assertFalse(c.followRedirects());assertFalse(c.followSslRedirects());
  assertEquals(15000,c.callTimeoutMillis());assertEquals(5000,c.connectTimeoutMillis());assertEquals(5000,c.readTimeoutMillis());assertNull(c.cache());assertSame(CookieJar.NO_COOKIES,c.cookieJar());
 }
 @Test public void tlsSlowDripIsBoundedByWholeCallDeadline() throws Exception {
  HeldCertificate certificate=new HeldCertificate.Builder().addSubjectAlternativeName("localhost").build();
  HandshakeCertificates serverTls=new HandshakeCertificates.Builder().heldCertificate(certificate).build();
  HandshakeCertificates clientTls=new HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate()).build();
  try(MockWebServer server=new MockWebServer()) {
   server.useHttps(serverTls.sslSocketFactory(),false);
   server.enqueue(new MockResponse().setHeader("Content-Type","application/json").setBody(ResponseTest.fixture()).throttleBody(1,100,TimeUnit.MILLISECONDS));server.start();
   OkHttpClient http=Client.http().newBuilder().sslSocketFactory(clientTls.sslSocketFactory(),clientTls.trustManager()).callTimeout(500,TimeUnit.MILLISECONDS).build();
   Client client=Client.forCalls(request -> http.newCall(request.newBuilder().url(server.url("/api/tags/00017F80")).build()));
   long start=System.nanoTime();assertThrows(IOException.class,()->client.fetch("00017F80",null));long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
   assertTrue("Deadline exceeded: "+elapsed,elapsed<2500);assertEquals(1,server.getRequestCount());http.connectionPool().evictAll();http.dispatcher().executorService().shutdownNow();
  }
 }
}
