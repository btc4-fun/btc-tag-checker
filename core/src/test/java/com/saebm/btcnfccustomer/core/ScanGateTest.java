package com.saebm.btcnfccustomer.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class ScanGateTest {
 @Test public void aLateCallbackFromAnOlderScanIsRejected() {
  ScanGate gate = new ScanGate();
  long first = gate.begin();
  assertTrue(gate.accepts(first, true, true, false));
  // The user pressed scan again before the first callback was delivered.
  long second = gate.begin();
  assertFalse("stale token must not overwrite the newer scan", gate.accepts(first, true, true, false));
  assertTrue(gate.accepts(second, true, true, false));
 }
 @Test public void leavingTheScreenInvalidatesEveryOutstandingToken() {
  ScanGate gate = new ScanGate();
  long token = gate.begin();
  gate.stop();
  assertFalse(gate.waiting());
  assertFalse("a callback after leaving the screen must be dropped", gate.accepts(token, true, true, false));
  // Even a token that was current before stop() stays dead after a new scan starts.
  long next = gate.begin();
  assertFalse(gate.accepts(token, true, true, false));
  assertTrue(gate.accepts(next, true, true, false));
 }
 @Test public void oneReadPerScanAndNoReadWhileARequestIsInFlight() {
  ScanGate gate = new ScanGate();
  long token = gate.begin();
  assertTrue(gate.accepts(token, true, true, false));
  gate.consumed();
  assertFalse("a second callback for the same scan must be ignored", gate.accepts(token, true, true, false));
  long again = gate.begin();
  assertFalse("a request in flight blocks a new read", gate.accepts(again, true, true, true));
  assertTrue(gate.accepts(again, true, true, false));
 }
 @Test public void pausedOrNonInquiryScreenNeverAcceptsARead() {
  ScanGate gate = new ScanGate();
  long token = gate.begin();
  assertFalse("paused activity", gate.accepts(token, false, true, false));
  assertFalse("screen no longer asks for a tag", gate.accepts(token, true, false, false));
  assertTrue(gate.accepts(token, true, true, false));
 }
 @Test public void tokensAreNeverReusedAcrossManyScans() {
  ScanGate gate = new ScanGate();
  java.util.Set<Long> seen = new java.util.HashSet<>();
  for (int i = 0; i < 100; i++) {
   long token = gate.begin();
   assertTrue("token " + token + " was reused", seen.add(token));
   assertTrue(gate.accepts(token, true, true, false));
   gate.consumed();
   gate.stop();
  }
 }
}
