package com.saebm.btcnfccustomer.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class ResponseTest {
 static final String TX="a".repeat(64);
 static String fixture() { return "{\"serial\":\"TEST-ONLY\",\"address\":\"bc1qtestonly\",\"confirmed_sats\":9007199254740993,\"pending_delta_sats\":-42,\"history_scope\":\"confirmed_only\",\"mempool_scope\":\"balance_only\",\"consistency\":\"non_atomic\",\"source\":\"blockstream\",\"fetched_at\":\"2026-10-31T00:00:00Z\",\"next_after_txid\":\""+TX+"\",\"transactions\":[{\"txid\":\""+TX+"\",\"confirmed\":true,\"block_height\":1,\"block_time\":123,\"delta_sats\":-5}]}"; }
 @Test public void preservesExactIntegerAmountsAndConfirmedHistory() {
  Response p=Response.parse(fixture()); assertEquals(9007199254740993L,p.confirmedSats); assertEquals(-42,p.pendingDeltaSats);
  assertEquals(1,p.transactions.size()); assertEquals(-5,p.transactions.get(0).deltaSats); assertEquals(TX,p.nextAfterTxid);
  assertEquals("+0", Response.signed(0)); assertEquals("+5",Response.signed(5)); assertEquals("-5",Response.signed(-5));
 }
 @Test public void rejectsNonIntegralAndOverflowAmounts() {
  for(String value : new String[]{"1.0","1e3","\"1\"","true","9223372036854775808","-1"}) assertThrows(IllegalArgumentException.class, () -> Response.parse(fixture().replace("9007199254740993",value)));
 }
 @Test public void failsClosedOnContractMismatch() {
  for(String value : new String[]{fixture().replace("confirmed_only","all"),fixture().replace("balance_only","all"),fixture().replace("non_atomic","atomic"),fixture().replace("true","false"),fixture().replace(TX,"../"),fixture().replace("\"serial\":\"TEST-ONLY\",",""),fixture()+" trailing",fixture().replace("\"pending_delta_sats\":-42","\"pending_delta_sats\":null"), fixture().replace("2026-10-31T00:00:00Z","yesterday")}) assertThrows(IllegalArgumentException.class, () -> Response.parse(value));
 }
 @Test public void rejectsDuplicateJsonKeysAndInconsistentContinuation() {
  assertThrows(IllegalArgumentException.class, () -> Response.parse(fixture().replace("\"confirmed_sats\":", "\"confirmed_sats\":0,\"confirmed_sats\":")));
  assertThrows(IllegalArgumentException.class, () -> Response.parse(fixture().replace("\"next_after_txid\":\""+TX, "\"next_after_txid\":\""+"b".repeat(64))));
 }
 @Test public void nullableOrAbsentCursorIsEndOfHistory() {
  assertNull(Response.parse(fixture().replace("\"next_after_txid\":\""+TX+"\",", "")).nextAfterTxid);
  assertNull(Response.parse(fixture().replace("\"next_after_txid\":\""+TX+"\"", "\"next_after_txid\":null")).nextAfterTxid);
 }
}
