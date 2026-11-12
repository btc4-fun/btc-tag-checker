package com.saebm.btcnfccustomer.core;

/**
 * Guard for the NFC reader callback.
 *
 * Reader mode deliberately stays enabled after a read, so the callback can fire again at any
 * time: after the user started a newer scan, after the screen was left, or while a request is
 * already in flight. Each scan gets its own immutable token, and a callback is accepted only
 * when its token is still the current one and the screen is still waiting for a read. A late
 * callback must never overwrite a newer result, re-enable a request the user cancelled, or
 * report a tag the user is no longer waiting for.
 *
 * The token is returned to the caller instead of being read back from a shared field: a field
 * would be overwritten by the next scan and the stale callback would then compare equal.
 */
public final class ScanGate {
    private long generation;
    private boolean waiting;

    /** Start a scan and return the token its callback must present. */
    public synchronized long begin() {
        waiting = true;
        return ++generation;
    }

    /** Leave the screen or cancel: every outstanding token becomes stale. */
    public synchronized void stop() {
        generation++;
        waiting = false;
    }

    /** True while a scan is waiting for a tag. */
    public synchronized boolean waiting() {
        return waiting;
    }

    /** Accept a callback only for the current scan, on a live screen, with no request in flight. */
    public synchronized boolean accepts(long token, boolean active, boolean inquiry, boolean busy) {
        return active && inquiry && !busy && waiting && token == generation;
    }

    /** Consume the accepted read so a second callback for the same scan is ignored. */
    public synchronized void consumed() {
        waiting = false;
    }
}
