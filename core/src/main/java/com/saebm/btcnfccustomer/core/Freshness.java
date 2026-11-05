package com.saebm.btcnfccustomer.core;

import java.time.Instant;

/**
 * In-page age of one server timestamp, measured on the monotonic clock.
 * Same 60s fresh / 300s usable thresholds as the market table; nothing is cached between requests.
 */
public final class Freshness {
    public static final long FRESH_MS = 60000, USABLE_MS = 300000;
    private final long initialAge, received;
    private Freshness(long initialAge, long received) { this.initialAge = initialAge; this.received = received; }
    /** Age of {@code fetchedAt} at receipt, clamped to [0, USABLE_MS); a future stamp counts as age zero. */
    public static Freshness at(String fetchedAt, long wallNow, long monotonicNow) {
        long age;
        try { age = Math.subtractExact(wallNow, Instant.parse(fetchedAt).toEpochMilli()); }
        // An unparseable stamp is refused the same way expired data is: one failure type, never a leaked date error.
        catch (java.time.format.DateTimeParseException | ArithmeticException ex) { throw new IllegalArgumentException("Expired data"); }
        if (age < 0) age = 0;
        if (age >= USABLE_MS) throw new IllegalArgumentException("Expired data");
        return new Freshness(age, monotonicNow);
    }
    public long age(long now) { return now < received ? Long.MAX_VALUE : initialAge + (now - received); }
    public boolean fresh(long now) { return age(now) < FRESH_MS; }
    public boolean usable(long now) { return age(now) < USABLE_MS; }
}
