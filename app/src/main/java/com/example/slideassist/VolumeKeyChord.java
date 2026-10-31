package com.example.slideassist;

import java.util.HashSet;
import java.util.Set;

/** Detects a two-volume-key chord without consuming any system key event. */
final class VolumeKeyChord {
    private static final int VOLUME_UP = 24;
    private static final int VOLUME_DOWN = 25;
    private static final long CHORD_WINDOW_MS = 200;
    private final Set<Long> pressed = new HashSet<>();
    private int firstDevice;
    private long firstTime;
    private long lastTime;
    private boolean invalid;
    private boolean latched;

    boolean onKey(int keyCode, boolean down, int repeat, boolean canceled, long eventTime, int deviceId) {
        if (keyCode != VOLUME_UP && keyCode != VOLUME_DOWN) return false;
        long identity = ((long) deviceId << 32) | (keyCode & 0xffffffffL);

        // A cancellation invalidates the whole overlapping sequence until all keys release.
        if (canceled) {
            pressed.remove(identity);
            invalid = true;
            if (pressed.isEmpty()) reset();
            return false;
        }
        if (eventTime < 0 || (!pressed.isEmpty() && eventTime < lastTime) || repeat < 0) invalid = true;
        if (eventTime >= 0) lastTime = eventTime;
        if (down) {
            if (repeat > 0) {
                if (!pressed.contains(identity)) invalid = true;
                if (pressed.isEmpty()) reset();
                return false;
            }
            if (pressed.contains(identity)) {
                invalid = true;
                return false;
            }
            if (pressed.isEmpty()) {
                firstDevice = deviceId;
                firstTime = eventTime;
                // Retain an invalid timestamp, but begin a fresh sequence otherwise.
                invalid = eventTime < 0 || repeat < 0;
                latched = false;
            }
            pressed.add(identity);
            if (pressed.size() > 2 || deviceId != firstDevice) invalid = true;
            if (pressed.size() == 2 && !invalid && !latched) {
                long elapsed = eventTime - firstTime;
                if (elapsed < 0 || elapsed > CHORD_WINDOW_MS) {
                    invalid = true;
                } else {
                    latched = true;
                    return true;
                }
            }
        } else {
            if (repeat != 0 || !pressed.remove(identity)) invalid = true;
            if (pressed.isEmpty()) reset();
        }
        return false;
    }

    boolean isIdle() { return pressed.isEmpty(); }

    void reset() {
        pressed.clear();
        firstDevice = 0;
        firstTime = 0;
        lastTime = 0;
        invalid = false;
        latched = false;
    }
}
