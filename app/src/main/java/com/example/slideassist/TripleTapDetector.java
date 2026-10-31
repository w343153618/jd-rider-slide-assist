package com.example.slideassist;

/** Counts completed light taps only; dragging and cancellation must call reset(). */
public final class TripleTapDetector {
    private static final long MAX_GAP_MILLIS = 350, MAX_SEQUENCE_MILLIS = 800;
    private int count;
    private long first, last;

    public boolean onTap(long uptimeMillis) {
        if (uptimeMillis < 0 || (count > 0 && uptimeMillis <= last)) { reset(); return false; }
        if (count > 0 && (uptimeMillis - last > MAX_GAP_MILLIS
                || uptimeMillis - first > MAX_SEQUENCE_MILLIS)) reset();
        if (count == 0) first = uptimeMillis;
        last = uptimeMillis;
        if (++count < 3) return false;
        reset(); return true;
    }

    public void reset() { count = 0; first = last = 0; }
}
