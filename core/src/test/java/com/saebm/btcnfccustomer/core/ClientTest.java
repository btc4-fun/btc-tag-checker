package com.saebm.btcnfccustomer.core;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ClientTest {
 private Client local(MockWebServer server) { return Client.forCalls(request -> Client.http().newCall(request.newBuilder().url(server.url(request.url().encodedPath() + (request.url().encodedQuery()==null ? "" : "?"+request.url().encodedQuery()))).build())); }
 @Test public void oneRequestBoundedAndNoRedirectsOrCaches() throws Exception {
  try(MockWebServer server=new MockWebServer()) {
   server.enqueue(new MockResponse().setHeader("Content-Type","application/json").setBody(ResponseTest.fixture()));server.start();
   assertEquals(-42,local(server).fetch("00017F80",null).pendingDeltaSats);
   RecordedRequest request=server.takeRequest();assertEquals("/api/tags/00017F80",request.getPath());assertEquals("GET",request.getMethod());
   assertEquals("identity",request.getHeader("Accept-Encoding"));assertEquals("no-store",request.getHeader("Cache-Control"));assertNull(request.getHeader("Cookie"));assertEquals(1,server.getRequestCount());
  }
 }
 @Test public void errorsNeverParseOrRetry() throws Exception {
  for(int status:new int[]{400,404,429,503,302,500}) {
   try(MockWebServer server=new MockWebServer()) {
    server.enqueue(new MockResponse().setResponseCode(status).setHeader("Retry-After","20").setHeader("Location","https://untrusted.invalid/").setBody("not a success"));server.start();
    Client.Failure failure=assertThrows(Client.Failure.class,()->local(server).fetch("00017F80",null));
    assertEquals(status,failure.status);assertEquals(1,server.getRequestCount());if(status==429) assertEquals(20,failure.retrySeconds);
   }
  }
 }
 @Test public void serviceUnavailableWithZeroRetryAfterIsNotRetried() throws Exception {
  try(MockWebServer server=new MockWebServer()) {
   server.enqueue(new MockResponse().setResponseCode(503).setHeader("Retry-After","0"));
   server.enqueue(new MockResponse().setHeader("Content-Type","application/json").setBody(ResponseTest.fixture()));server.start();
   Client.Failure failure=assertThrows(Client.Failure.class,()->local(server).fetch("00017F80",null));
   assertEquals(503,failure.status);assertEquals(1,server.getRequestCount());
  }
 }
 @Test public void cancelledRequestCannotStart() {
  AtomicInteger calls=new AtomicInteger();Client client=Client.forCalls(request->{calls.incrementAndGet();return Client.http().newCall(request);});
  client.cancel();assertThrows(IOException.class,()->client.fetch("00017F80",null));assertEquals(0,calls.get());
 }
 @Test public void wrongContentTypeAndOversizeFailClosed() throws Exception {
  try(MockWebServer server=new MockWebServer()) {
   server.enqueue(new MockResponse().setHeader("Content-Type","text/html").setBody(ResponseTest.fixture()));
   server.enqueue(new MockResponse().setHeader("Content-Type","application/json").setBody("x".repeat(RequestPolicy.MAX_BODY+1)));
   server.enqueue(new MockResponse().setHeader("Content-Type","application/json").setChunkedBody("x".repeat(RequestPolicy.MAX_BODY+1),1024));server.start();
   for(int i=0;i<3;i++) assertThrows(IOException.class,()->local(server).fetch("00017F80",null));
  }
 }
 @Test public void cancellationInterruptsRunningCall() throws Exception {
  try(MockWebServer server=new MockWebServer()) {
   server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));server.start();
   Client client=local(server);java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
   try {
    java.util.concurrent.Future<?> task=worker.submit(()->assertThrows(IOException.class,()->client.fetch("00017F80",null)));
    assertNotNull(server.takeRequest(2,java.util.concurrent.TimeUnit.SECONDS));client.cancel();task.get(2,java.util.concurrent.TimeUnit.SECONDS);
    assertEquals(1,server.getRequestCount());
   } finally {client.cancel();worker.shutdownNow();}
  }
 }
 @Test public void generationFenceRejectsOldAndStoppedResponses() {
  Latest gate=new Latest();long old=gate.begin(),fresh=gate.begin();assertFalse(gate.accepts(old));assertTrue(gate.accepts(fresh));gate.invalidate();assertFalse(gate.accepts(fresh));
 }
}
