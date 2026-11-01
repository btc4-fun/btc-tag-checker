package com.saebm.btcnfccustomer.core;
import java.io.IOException;
import java.util.concurrent.*;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class MarketClientTest {
 private MarketClient local(MockWebServer server) {return MarketClient.forCalls(request->{assertEquals("https://btc4.fun/api/market",request.url().toString());return Client.http().newCall(request.newBuilder().url(server.url("/api/market")).build());},()->MarketResponseTest.NOW,()->1000L);}
 @Test public void oneExplicitRequestNoUserData() throws Exception {
  try(MockWebServer server=new MockWebServer()) {
   server.enqueue(new MockResponse().setHeader("Content-Type","application/json").setBody(MarketResponseTest.fixture()));server.start();
   assertNotNull(local(server).fetch().option("fast",true));RecordedRequest r=server.takeRequest();
   assertEquals("/api/market",r.getPath());assertEquals("GET",r.getMethod());assertEquals(0,r.getBodySize());assertNull(r.getHeader("Cookie"));assertEquals("no-store",r.getHeader("Cache-Control"));assertEquals(1,server.getRequestCount());
  }
 }
 @Test public void invalidResponsesNoRetries() throws Exception {
  for(MockResponse response:new MockResponse[]{new MockResponse().setResponseCode(302).setHeader("Location","https://untrusted.invalid"),new MockResponse().setResponseCode(503).setHeader("Retry-After","0"),new MockResponse().setHeader("Content-Type","text/html").setBody("{}"),new MockResponse().setHeader("Content-Type","application/json").setBody("{}"),new MockResponse().setHeader("Content-Type","application/json").setChunkedBody("x".repeat(RequestPolicy.MAX_BODY+1),1024)}) {
   try(MockWebServer server=new MockWebServer()){server.enqueue(response);server.start();assertThrows(IOException.class,()->local(server).fetch());assertEquals(1,server.getRequestCount());}
  }
 }
 @Test public void cancelBeforeAndDuringRequest() throws Exception {
  try(MockWebServer server=new MockWebServer()) {
   server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));server.start();
   MarketClient stopped=local(server);stopped.cancel();assertThrows(IOException.class,stopped::fetch);assertEquals(0,server.getRequestCount());
   MarketClient running=local(server);ExecutorService worker=Executors.newSingleThreadExecutor();
   try {Future<?> f=worker.submit(()->assertThrows(IOException.class,running::fetch));assertNotNull(server.takeRequest(2,TimeUnit.SECONDS));running.cancel();f.get(2,TimeUnit.SECONDS);} finally {running.cancel();worker.shutdownNow();}
  }
 }
}
