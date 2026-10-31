package com.example.slideassist;

import java.util.Locale;

/** Elapsed duration formatting; this is a counter, never a wall clock or a countdown. */
public final class BubbleTimer {
    private BubbleTimer() { }
    public static String formatElapsedMillis(long elapsedMillis) {
        long seconds = Math.max(0, elapsedMillis) / 1000;
        if (seconds < 3600) return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
        return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
}
