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
 /** The current contract is the 1% model with both exchange fees, and it is what the fixture is. */
 @Test public void currentModelCarriesBothExchangeFees() throws Exception {
  MarketResponse r=MarketResponse.parse(fixture(),NOW,0);
  for(String speed:new String[]{"economy","medium","fast"}) for(boolean tag:new boolean[]{false,true}) {
   MarketResponse.Option option=r.option(speed,tag);
   assertEquals(QuoteCalculator.Model.CURRENT,option.model);
   assertEquals(BigInteger.valueOf(423),option.calculator.transfer);
   assertTrue(option.calculator.convert.signum()>0);
   for(JsonElement element:option.rows()) {
    JsonObject row=element.getAsJsonObject();
    assertFalse("no separate acquisition row",row.has("acquisition_sats"));
    assertEquals(option.calculator.convert,row.get("convert_sats").getAsBigInteger());
    assertEquals(BigInteger.valueOf(423),row.get("transfer_sats").getAsBigInteger());
   }
  }
 }
 /** A server that still serves the older 0.5% table stays readable, with no invented fees. */
 @Test public void legacyHalfPercentMarketStillParses() throws Exception {
  JsonObject root=JsonParser.parseString(fixture()).getAsJsonObject();
  String btc=root.getAsJsonObject("raw_rates").get("btc_toman").getAsString();
  String usdt=root.getAsJsonObject("raw_rates").get("usdt_toman").getAsString();
  JsonObject legacyRoot=legacyTable(btc,usdt,"1.1",false,false);
  for(String key:new String[]{"rates","assumptions","costs","rows"}) root.add(key,legacyRoot.get(key));
  JsonObject catalog=new JsonObject();
  for(String speed:new String[]{"economy","medium","fast"}) {
   String fee=speed.equals("economy")?"1.1":speed.equals("medium")?"2":"5";
   JsonObject choices=new JsonObject();
   choices.add("no_tag",legacyTable(btc,usdt,fee,false,false));
   choices.add("with_tag",legacyTable(btc,usdt,fee,true,false));
   choices.add("no_tag_in_person",legacyTable(btc,usdt,fee,false,true));
   choices.add("with_tag_in_person",legacyTable(btc,usdt,fee,true,true));
   catalog.add(speed,choices);
  }
  root.add("quote_options",catalog);
  MarketResponse r=MarketResponse.parse(root.toString(),NOW,0);
  for(String speed:new String[]{"economy","medium","fast"}) for(boolean tag:new boolean[]{false,true}) {
   MarketResponse.Option option=r.option(speed,tag);
   assertNotNull(speed+"/"+tag,option);
   assertEquals(QuoteCalculator.Model.LEGACY,option.model);
   assertEquals(BigInteger.ZERO,option.calculator.convert);
   assertEquals(BigInteger.ZERO,option.calculator.transfer);
   for(JsonElement element:option.rows()) {
    JsonObject row=element.getAsJsonObject();
    assertFalse(row.has("convert_sats"));
    assertFalse(row.has("transfer_sats"));
   }
  }
  assertNotNull(r.option("economy",false,true));
 }
 /** A half-published or mislabelled model is rejected instead of being read as a zero cost. */
 @Test public void mixedOrMislabelledModelsAreRejected() throws Exception {
  JsonObject half=JsonParser.parseString(fixture()).getAsJsonObject();
  half.getAsJsonObject("assumptions").remove("transfer_sats");
  assertThrows(IllegalArgumentException.class,()->MarketResponse.parse(half.toString(),NOW,0));
  JsonObject otherHalf=JsonParser.parseString(fixture()).getAsJsonObject();
  otherHalf.getAsJsonObject("assumptions").remove("convert_usd");
  assertThrows(IllegalArgumentException.class,()->MarketResponse.parse(otherHalf.toString(),NOW,0));
  JsonObject mislabelled=JsonParser.parseString(fixture()).getAsJsonObject();
  mislabelled.getAsJsonObject("assumptions").addProperty("exchange_fee_percent","0.5");
  assertThrows(IllegalArgumentException.class,()->MarketResponse.parse(mislabelled.toString(),NOW,0));
  JsonObject wrongFee=JsonParser.parseString(fixture()).getAsJsonObject();
  wrongFee.getAsJsonObject("assumptions").addProperty("convert_usd","0.03");
  assertThrows(IllegalArgumentException.class,()->MarketResponse.parse(wrongFee.toString(),NOW,0));
  JsonObject wrongTransfer=JsonParser.parseString(fixture()).getAsJsonObject();
  wrongTransfer.getAsJsonObject("assumptions").addProperty("transfer_sats",424);
  assertThrows(IllegalArgumentException.class,()->MarketResponse.parse(wrongTransfer.toString(),NOW,0));
  JsonObject missingCost=JsonParser.parseString(fixture()).getAsJsonObject();
  missingCost.getAsJsonObject("costs").remove("convert_sats");
  assertThrows(IllegalArgumentException.class,()->MarketResponse.parse(missingCost.toString(),NOW,0));
  JsonObject legacyWithFees=JsonParser.parseString(fixture()).getAsJsonObject();
  legacyWithFees.getAsJsonObject("assumptions").remove("convert_usd");
  legacyWithFees.getAsJsonObject("assumptions").remove("transfer_sats");
  legacyWithFees.getAsJsonObject("assumptions").addProperty("exchange_fee_percent","0.5");
  assertThrows(IllegalArgumentException.class,()->MarketResponse.parse(legacyWithFees.toString(),NOW,0));
 }
 /** Build the older 0.5% table from the legacy calculator, so the test does not hand-type numbers. */
 static JsonObject legacyTable(String btc,String usdt,String fee,boolean tag,boolean hand) {
  QuoteCalculator c=QuoteCalculator.fromRaw(btc,usdt,fee,tag,hand,QuoteCalculator.Model.LEGACY);
  JsonArray rows=new JsonArray();
  JsonObject first=c.quote(BigInteger.valueOf(501),null);
  rows.add(first);
  BigInteger step=BigInteger.valueOf(100000);
  BigInteger next=first.get("total_toman").getAsBigInteger().divide(step).add(BigInteger.ONE).multiply(step);
  for(BigInteger budget=next;budget.compareTo(BigInteger.valueOf(3000000))<=0;budget=budget.add(step)) rows.add(c.calculate(budget.toString(),true));
  JsonObject rates=new JsonObject();
  rates.addProperty("btc_toman",new java.math.BigDecimal(btc).multiply(new java.math.BigDecimal("1.005")).toPlainString());
  rates.addProperty("usdt_toman",new java.math.BigDecimal(usdt).multiply(new java.math.BigDecimal("1.005")).toPlainString());
  rates.addProperty("satoshi_toman",c.satoshiToman.toPlainString());
  JsonObject assumptions=new JsonObject();
  assumptions.addProperty("exchange_fee_percent","0.5");assumptions.addProperty("operator_fee_percent","15");
  assumptions.addProperty("materials_usd","0.05");assumptions.addProperty("envelope_usd","0.06");
  assumptions.addProperty("post_usd","0.75");assumptions.addProperty("tag_usd","0.12");
  assumptions.addProperty("include_tag",tag);assumptions.addProperty("in_person",hand);
  assumptions.addProperty("transaction_vbytes",141);assumptions.addProperty("network_fee_rate",fee);
  JsonObject costs=new JsonObject();
  costs.addProperty("network_sats",c.network);costs.addProperty("materials_sats",c.materials);
  costs.addProperty("envelope_sats",c.envelope);costs.addProperty("post_sats",c.post);costs.addProperty("tag_sats",c.tag);
  JsonObject table=new JsonObject();
  table.add("rates",rates);table.add("assumptions",assumptions);table.add("costs",costs);table.add("rows",rows);
  return table;
 }
}
