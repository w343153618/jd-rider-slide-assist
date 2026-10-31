package com.example.slideassist;

/** Host-side behavioral tests; run with tests/run-unit-tests.sh. */
public final class VolumeKeyChordTest {
    private static final int UP = 24;
    private static final int DOWN = 25;

    private static void expect(boolean got, boolean want, String label) {
        if (got != want) throw new AssertionError(label + ": expected " + want + ", got " + got);
    }
    private static boolean event(VolumeKeyChord c, int key, boolean down, int repeat,
                                 boolean canceled, long ms, int device) {
        return c.onKey(key, down, repeat, canceled, ms, device);
    }
    private static void freshChordAndLatch() {
        VolumeKeyChord c = new VolumeKeyChord();
        expect(event(c, UP, true, 0, false, 10, 1), false, "first press");
        expect(event(c, DOWN, true, 0, false, 210, 1), true, "200ms boundary");
        expect(event(c, DOWN, true, 1, false, 211, 1), false, "held repeat");
        expect(event(c, UP, false, 0, false, 212, 1), false, "one release");
        expect(event(c, UP, true, 0, false, 213, 1), false, "latched with one key held");
        event(c, UP, false, 0, false, 214, 1);
        event(c, DOWN, false, 0, false, 215, 1);
        expect(event(c, DOWN, true, 0, false, 216, 1), false, "reverse first");
        expect(event(c, UP, true, 0, false, 217, 1), true, "reverse order after release");
    }
    private static void latePairMustFullyRelease() {
        VolumeKeyChord c = new VolumeKeyChord();
        event(c, UP, true, 0, false, 1, 1);
        expect(event(c, DOWN, true, 0, false, 202, 1), false, "201ms is late");
        event(c, UP, false, 0, false, 203, 1);
        expect(event(c, UP, true, 0, false, 204, 1), false, "late pair remains blocked");
        event(c, UP, false, 0, false, 205, 1);
        event(c, DOWN, false, 0, false, 206, 1);
        event(c, UP, true, 0, false, 207, 1);
        expect(event(c, DOWN, true, 0, false, 208, 1), true, "recovery after late pair");
    }
    private static void differentDevicesMustFullyRelease() {
        VolumeKeyChord c = new VolumeKeyChord();
        event(c, UP, true, 0, false, 1, 1);
        expect(event(c, DOWN, true, 0, false, 2, 2), false, "mixed devices");
        event(c, DOWN, false, 0, false, 3, 2);
        expect(event(c, DOWN, true, 0, false, 4, 1), false, "mixed sequence stays blocked");
        event(c, DOWN, false, 0, false, 5, 1);
        event(c, UP, false, 0, false, 6, 1);
        event(c, UP, true, 0, false, 7, 1);
        expect(event(c, DOWN, true, 0, false, 8, 1), true, "recovery after mixed devices");
    }
    private static void canceledAndMalformedSequences() {
        VolumeKeyChord c = new VolumeKeyChord();
        event(c, UP, true, 0, false, 10, 1);
        event(c, UP, false, 0, true, 11, 1);
        event(c, DOWN, true, 0, false, 12, 1);
        expect(event(c, UP, true, 0, false, 13, 1), true, "fresh chord after canceled key releases");
        c.reset();
        event(c, UP, true, 0, false, 20, 1);
        expect(event(c, DOWN, true, 0, false, 19, 1), false, "reversed time");
        event(c, UP, false, 0, false, 21, 1);
        event(c, DOWN, false, 0, false, 22, 1);
        event(c, UP, true, 0, false, 23, 1);
        expect(event(c, UP, true, 0, false, 24, 1), false, "duplicate down");
        expect(event(c, DOWN, true, 0, false, 25, 1), false, "duplicate down invalidates overlap");
        c.reset();
        expect(event(c, UP, true, 1, false, 30, 1), false, "unmatched repeat");
        expect(event(c, 42, true, 0, false, 31, 1), false, "unrelated key");
        event(c, UP, true, 0, false, 32, 1);
        event(c, UP, false, 0, false, 33, 1);
        expect(event(c, DOWN, true, 0, false, 34, 1), false, "nonoverlapping keys");
    }
    public static void main(String[] args) {
        freshChordAndLatch();
        latePairMustFullyRelease();
        differentDevicesMustFullyRelease();
        canceledAndMalformedSequences();
        System.out.println("VolumeKeyChordTest passed");
    }
}
