package com.saebm.btcnfccustomer.core;
import com.google.gson.*;
import java.math.BigInteger;
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
 @Test public void operatorTomanMustMatchTheLocalComputation() throws Exception {
  // The site's row carries the operator's income in toman; a missing or faked value must fail closed
  // instead of being rendered as a zero or an invented amount. Economy is the required table, so a
  // bad economy row rejects the whole payload; medium and fast are optional and are dropped.
  for(String speed:new String[]{"economy","medium","fast"}) for(String tag:new String[]{"no_tag","with_tag"}) {
   boolean required=speed.equals("economy");
   JsonObject missing=JsonParser.parseString(fixture()).getAsJsonObject();
   missing.getAsJsonObject("quote_options").getAsJsonObject(speed).getAsJsonObject(tag).getAsJsonArray("rows").get(0).getAsJsonObject().remove("operator_toman");
   if(required) assertThrows(speed+"/"+tag+" missing",IllegalArgumentException.class,()->MarketResponse.parse(missing.toString(),NOW,0));
   else assertNull(speed+"/"+tag+" missing",MarketResponse.parse(missing.toString(),NOW,0).option(speed,tag.equals("with_tag")));
   JsonObject faked=JsonParser.parseString(fixture()).getAsJsonObject();
   JsonObject row=faked.getAsJsonObject("quote_options").getAsJsonObject(speed).getAsJsonObject(tag).getAsJsonArray("rows").get(0).getAsJsonObject();
   row.addProperty("operator_toman",row.get("operator_toman").getAsBigInteger().add(BigInteger.ONE));
   if(required) assertThrows(speed+"/"+tag+" faked",IllegalArgumentException.class,()->MarketResponse.parse(faked.toString(),NOW,0));
   else assertNull(speed+"/"+tag+" faked",MarketResponse.parse(faked.toString(),NOW,0).option(speed,tag.equals("with_tag")));
  }
 }
 @Test public void operatorTomanIsCeilingOfOperatorSatsTimesSatoshiToman() throws Exception {
  MarketResponse r=MarketResponse.parse(fixture(),NOW,0);
  for(String speed:new String[]{"economy","medium","fast"}) for(boolean tag:new boolean[]{false,true}) {
   QuoteCalculator calculator=r.option(speed,tag).calculator;
   for(JsonElement element:r.option(speed,tag).rows()) {
    JsonObject row=element.getAsJsonObject();
    BigInteger expected=calculator.quote(row.get("principal_sats").getAsBigInteger(),null).get("operator_toman").getAsBigInteger();
    assertEquals(speed+"/"+tag,expected,row.get("operator_toman").getAsBigInteger());
   }
  }
 }
}
