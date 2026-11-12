package com.saebm.btcnfccustomer.core;
import com.google.gson.*;
import java.io.InputStreamReader;
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
   for(String key:new String[]{"principal_sats","btc","network_sats","materials_sats","post_sats","tag_sats","operator_sats","total_sats","total_toman","total_rial","remainder_toman"})
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
}
