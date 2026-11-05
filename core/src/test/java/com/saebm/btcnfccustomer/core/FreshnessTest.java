package com.saebm.btcnfccustomer.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class FreshnessTest {
 @Test public void sixtySecondsFreshThreeHundredUsable() {
  long wall = java.time.Instant.parse("2026-11-01T00:00:00Z").toEpochMilli(), mono = 1000L;
  Freshness fresh = Freshness.at("2026-11-01T00:00:00Z", wall, mono);
  assertTrue(fresh.fresh(mono)); assertTrue(fresh.fresh(mono + 59999)); assertFalse(fresh.fresh(mono + 60000));
  assertTrue(fresh.usable(mono + 299999)); assertFalse(fresh.usable(mono + 300000));
  assertEquals(Long.MAX_VALUE, fresh.age(mono - 1));
  Freshness old = Freshness.at("2026-10-31T23:58:00Z", wall, mono);
  assertFalse(old.fresh(mono)); assertTrue(old.usable(mono));
  // Data already past the usable window at arrival is refused outright, not handed back as usable.
  IllegalArgumentException expired = assertThrows(IllegalArgumentException.class, () -> Freshness.at("2026-10-31T23:54:59Z", wall, mono));
  assertEquals("Expired data", expired.getMessage());
  // A future stamp is clamped to age zero, never negative.
  Freshness future = Freshness.at("2026-11-02T00:00:00Z", wall, mono);
  assertEquals(0, future.age(mono)); assertTrue(future.fresh(mono));
  assertThrows(IllegalArgumentException.class, () -> Freshness.at("yesterday", wall, mono));
 }
}
