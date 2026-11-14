package com.saebm.btcnfccustomer.core;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.Test;
import static org.junit.Assert.*;
public class QuoteCalculatorTest {
 @Test public void independentTaggedFractionVectors() throws Exception {
  JsonObject fixture=JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream("/tagged_quote_vectors.json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
  int checked=0,rejected=0;
  for(JsonElement element:fixture.getAsJsonArray("cases")) {
   JsonObject c=element.getAsJsonObject(),in=c.getAsJsonObject("inputs");
   boolean budget=c.get("mode").getAsString().equals("budget");
   QuoteCalculator calc=QuoteCalculator.fromRaw(in.get("btc_toman").getAsString(),in.get("usdt_toman").getAsString(),in.get("fee_rate").getAsString(),true);
   String value=in.get(budget?"budget_toman":"principal_sats").getAsString();
   if(c.has("expected_error")) {
    assertThrows(c.get("expected_error").getAsString(),IllegalArgumentException.class,()->calc.calculate(value,budget));
    rejected++; continue;
   }
   JsonObject actual=calc.calculate(value,budget);
   for(java.util.Map.Entry<String,JsonElement> expected:c.getAsJsonObject("expected").entrySet()) {
    if(expected.getValue().isJsonNull()) assertTrue(actual.get(expected.getKey()).isJsonNull());
    else assertEquals(expected.getKey(),expected.getValue().getAsString(),actual.get(expected.getKey()).getAsString());
   }
   if(budget) {
    // The oracle's principal must be the largest that still fits, so one satoshi more must overspend.
    BigInteger principal=actual.get("principal_sats").getAsBigInteger();
    if(principal.compareTo(QuoteCalculator.MAX)<0) assertTrue(calc.quote(principal.add(BigInteger.ONE),null).get("total_toman").getAsBigInteger().compareTo(new BigInteger(value))>0);
   }
   checked++;
  }
  assertTrue(checked>=12);
  assertTrue(rejected>=2);
 }
 @Test public void independentWebsiteVectors() throws Exception {
  JsonObject fixture=JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream("/quote_calculator.json"), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
  int checked=0;
  for(JsonElement element:fixture.getAsJsonArray("cases")) {
   JsonObject c=element.getAsJsonObject(), in=c.getAsJsonObject("inputs");
   boolean budget=c.get("mode").getAsString().equals("budget");
   String value=in.get(budget?"budget_toman":"principal_sats").getAsString();
   QuoteCalculator calculator=QuoteCalculator.fromRaw(in.get("btc_toman").getAsString(),in.get("usdt_toman").getAsString(),in.get("fee_rate").getAsString(),in.has("include_tag")&&in.get("include_tag").getAsBoolean());
   if(c.has("expected_error") || value.length()>32 || value.equals("0")) {
    assertThrows(c.get("id").getAsString(),IllegalArgumentException.class,()->calculator.calculate(value,budget)); continue;
   }
   JsonObject expected=c.getAsJsonObject("expected");
   JsonObject actual=calculator.calculate(value,budget);
   for(String key:new String[]{"principal_sats","btc","network_sats","convert_sats","transfer_sats","materials_sats","envelope_sats","post_sats","tag_sats","operator_sats","operator_toman","total_sats","total_toman","total_rial","remainder_toman"})
    assertEquals(c.get("id")+" "+key,expected.get(key).getAsString(),actual.get(key).getAsString());
   if(budget) {
    BigInteger principal=actual.get("principal_sats").getAsBigInteger();
    if(principal.compareTo(QuoteCalculator.MAX)<0) assertTrue(calculator.quote(principal.add(BigInteger.ONE),null).get("total_toman").getAsBigInteger().compareTo(new BigInteger(value))>0);
   }
   checked++;
  }
  assertTrue(checked>=10);
 }
 @Test public void inputContractMatchesBrowser() {
  QuoteCalculator c=QuoteCalculator.fromRaw("10000000000","100000","1.1",false);
  for(String value:new String[]{"", "0","0500"," 500","500 ","+500","-500","5e2","500.0","۵۰۰","1".repeat(33)}) assertThrows(IllegalArgumentException.class,()->c.calculate(value,false));
  // The package floor is 501 satoshis, matching the site's quote_table and quote_for_principal.
  assertThrows(IllegalArgumentException.class,()->c.calculate("500",false));
  assertThrows(IllegalArgumentException.class,()->c.calculate("499",false));
  assertEquals(BigInteger.valueOf(501),c.calculate("501",false).get("principal_sats").getAsBigInteger());
  assertThrows(IllegalArgumentException.class,()->c.calculate("2100000000000001",false));
  assertEquals(QuoteCalculator.MAX,c.calculate("9".repeat(32),true).get("principal_sats").getAsBigInteger());
 }
 /** The 1% acquisition markup lives in the satoshi rate ONCE; no row charges it again. */
 @Test public void acquisitionMarkupIsAppliedExactlyOnce() {
  // 10,000,000,000 toman per BTC: 1.01x is exactly 101 toman per satoshi. A second markup
  // (1.01^2) or a per-row 1% of the principal would both show up here.
  QuoteCalculator c=QuoteCalculator.fromRaw("10000000000","100000","1.1",false);
  assertEquals(0,new BigDecimal("101").compareTo(c.satoshiToman));
  // The tether rate is used raw, so the dollar costs are divided by the marked-up satoshi price.
  assertEquals(BigInteger.valueOf(50),c.materials);   // ceil(0.05 * 100000 / 101)
  assertEquals(BigInteger.valueOf(20),c.convert);     // ceil(0.02 * 100000 / 101)
  JsonObject row=c.quote(BigInteger.valueOf(1000000),null);
  assertFalse("no separate acquisition row",row.has("acquisition_sats"));
  BigInteger subtotal=row.get("principal_sats").getAsBigInteger().add(c.network).add(c.convert).add(c.transfer)
    .add(c.materials).add(c.envelope).add(c.post).add(c.tag);
  assertEquals(subtotal,row.get("total_sats").getAsBigInteger().subtract(row.get("operator_sats").getAsBigInteger()));
  // Doubling the principal adds exactly the principal again plus the operator's 15% of it,
  // never another 1%.
  JsonObject doubled=c.quote(BigInteger.valueOf(2000000),null);
  BigInteger extra=doubled.get("total_sats").getAsBigInteger().subtract(row.get("total_sats").getAsBigInteger());
  BigInteger principalExtra=BigInteger.valueOf(1000000);
  BigInteger operatorExtra=doubled.get("operator_sats").getAsBigInteger().subtract(row.get("operator_sats").getAsBigInteger());
  assertEquals(principalExtra,extra.subtract(operatorExtra));
  assertTrue(operatorExtra.compareTo(principalExtra.multiply(BigInteger.valueOf(15)).divide(BigInteger.valueOf(100)))>=0);
  assertTrue(operatorExtra.compareTo(principalExtra.multiply(BigInteger.valueOf(16)).divide(BigInteger.valueOf(100)))<=0);
 }
 /** Both exchange fees are fixed: only the network fee follows the speed selector. */
 @Test public void exchangeFeesAreIndependentOfSpeedTagAndDelivery() {
  QuoteCalculator economy=QuoteCalculator.fromRaw("10000000000","100000","1.1",false);
  QuoteCalculator medium=QuoteCalculator.fromRaw("10000000000","100000","2",false);
  QuoteCalculator fast=QuoteCalculator.fromRaw("10000000000","100000","5",false);
  QuoteCalculator tagged=QuoteCalculator.fromRaw("10000000000","100000","5",true,true);
  assertEquals(BigInteger.valueOf(20),economy.convert);
  assertEquals(BigInteger.valueOf(423),economy.transfer);
  for(QuoteCalculator other:new QuoteCalculator[]{medium,fast,tagged}) {
   assertEquals(economy.convert,other.convert);
   assertEquals(economy.transfer,other.transfer);
  }
  assertTrue(economy.network.compareTo(medium.network)<0);
  assertTrue(medium.network.compareTo(fast.network)<0);
  JsonObject slow=economy.quote(BigInteger.valueOf(100000),null),quick=fast.quote(BigInteger.valueOf(100000),null);
  assertEquals(slow.get("convert_sats"),quick.get("convert_sats"));
  assertEquals(slow.get("transfer_sats"),quick.get("transfer_sats"));
  // The whole difference between the two rows is the network fee, nothing else.
  BigInteger slowSubtotal=slow.get("total_sats").getAsBigInteger().subtract(slow.get("operator_sats").getAsBigInteger());
  BigInteger quickSubtotal=quick.get("total_sats").getAsBigInteger().subtract(quick.get("operator_sats").getAsBigInteger());
  assertEquals(fast.network.subtract(economy.network),quickSubtotal.subtract(slowSubtotal));
 }
 /** The older 0.5% model stays readable, and it never publishes the fees it does not charge. */
 @Test public void legacyModelKeepsItsOldShape() {
  QuoteCalculator legacy=QuoteCalculator.fromRaw("10000000000","100000","1.1",false,false,QuoteCalculator.Model.LEGACY);
  assertEquals(0,new BigDecimal("100.5").compareTo(legacy.satoshiToman));
  assertEquals(BigInteger.ZERO,legacy.convert);
  assertEquals(BigInteger.ZERO,legacy.transfer);
  assertEquals(BigInteger.valueOf(50),legacy.materials); // ceil(0.05 * 100500 / 100.5)
  JsonObject row=legacy.quote(BigInteger.valueOf(3000),null);
  assertFalse(row.has("convert_sats"));
  assertFalse(row.has("transfer_sats"));
  assertFalse(row.has("acquisition_sats"));
 }
}
