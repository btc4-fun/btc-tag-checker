package com.saebm.btcnfccustomer.core;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;
public class MarketResponseTest {
 @Test public void serverAgeConsumesRemainingLifetime() throws Exception {
  MarketResponse r=MarketResponse.parse(fixture(),NOW+59000,1000);
  assertTrue(r.fresh(1999));assertFalse(r.fresh(2000));
  assertTrue(r.usable(241999));assertFalse(r.usable(242000));
  assertFalse(r.usable(999));
 }
 static String fixture() throws Exception {try(java.io.InputStream in=MarketResponseTest.class.getResourceAsStream("/market.json")){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
 static final long NOW=1793491200000L;
 @Test public void validatesSixOptionsAndAgeBoundaries() throws Exception {
  MarketResponse r=MarketResponse.parse(fixture(),NOW,1000);
  for(String speed:new String[]{"economy","medium","fast"}) for(boolean tag:new boolean[]{false,true}) assertNotNull(r.option(speed,tag));
  assertTrue(r.fresh(1000));assertFalse(r.fresh(61000));assertTrue(r.usable(300999));assertFalse(r.usable(301000));
  JsonObject old=JsonParser.parseString(fixture()).getAsJsonObject();old.addProperty("status","stale");
  assertFalse(MarketResponse.parse(old.toString(),NOW,1000).fresh(1000));
  assertThrows(IllegalArgumentException.class,()->MarketResponse.parse(fixture(),NOW+300000,1000));
  assertThrows(IllegalArgumentException.class,()->MarketResponse.parse(fixture(),NOW-1000,1000));
 }
 @Test public void invalidRatesAndRowsCannotBecomeZeroPrices() throws Exception {
  for(String field:new String[]{"btc_toman","usdt_toman"}) for(String bad:new String[]{"0","-1","NaN","1e3","1.0000000000000000001"}) {
   JsonObject obj=JsonParser.parseString(fixture()).getAsJsonObject();obj.getAsJsonObject("raw_rates").addProperty(field,bad);
   assertThrows(IllegalArgumentException.class,()->MarketResponse.parse(obj.toString(),NOW,0));
  }
  JsonObject obj=JsonParser.parseString(fixture()).getAsJsonObject();obj.getAsJsonObject("quote_options").getAsJsonObject("economy").getAsJsonObject("no_tag").getAsJsonArray("rows").get(0).getAsJsonObject().addProperty("total_toman",0);
  assertThrows(IllegalArgumentException.class,()->MarketResponse.parse(obj.toString(),NOW,0));
 }
 @Test public void absentPriorityNeverBorrowsEconomy() throws Exception {
  JsonObject obj=JsonParser.parseString(fixture()).getAsJsonObject();obj.getAsJsonObject("quote_options").add("medium",JsonNull.INSTANCE);
  MarketResponse r=MarketResponse.parse(obj.toString(),NOW,0);assertNull(r.option("medium",false));assertNotNull(r.option("economy",true));
 }
}
