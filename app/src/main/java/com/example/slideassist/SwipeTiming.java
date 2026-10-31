package com.example.slideassist;

/** Supported, deterministic slide durations shared by practice and real gestures. */
final class SwipeTiming {
    static final int DEFAULT_MILLIS = 250;

    private SwipeTiming() {}

    static int normalize(int value) {
        return value == 100 || value == 150 || value == 200 || value == 450 || value == 700
                ? value : DEFAULT_MILLIS;
    }
}
