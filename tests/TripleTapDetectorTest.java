package com.example.slideassist;

public final class TripleTapDetectorTest {
    public static void main(String[] args) {
        TripleTapDetector detector = new TripleTapDetector();
        expect(false, detector.onTap(100), "single does not toggle");
        expect(false, detector.onTap(200), "double does not toggle");
        expect(true, detector.onTap(300), "third toggles once");
        expect(false, detector.onTap(400), "fourth starts new sequence without toggling");
        detector.reset();
        expect(false, detector.onTap(0), "zero uptime is valid");
        expect(false, detector.onTap(350), "350ms interval accepted");
        expect(true, detector.onTap(700), "boundary triple accepted");
        detector.reset();
        detector.onTap(100); detector.onTap(200);
        expect(false, detector.onTap(551), "slow third cannot toggle");
        expect(false, detector.onTap(600), "late tap becomes fresh first");
        expect(true, detector.onTap(650), "fresh triple after timeout");
        detector.onTap(1000); detector.onTap(1100); detector.reset();
        expect(false, detector.onTap(1200), "drag or cancellation clears two taps");
        expect(false, detector.onTap(1300), "two fresh taps after drag");
        expect(true, detector.onTap(1400), "three fresh taps after drag");
        detector.onTap(2000); detector.onTap(2100);
        expect(false, detector.onTap(2100), "duplicate time clears sequence");
        expect(false, detector.onTap(2200), "duplicate cannot complete old triple");
        expect(false, detector.onTap(2199), "reversed time clears sequence");
        expect(false, detector.onTap(-1), "negative time rejected");
        expect(false, detector.onTap(2300), "negative cannot seed a sequence");
        expect(false, detector.onTap(2400), "new double after invalid clocks");
        expect(true, detector.onTap(2500), "recover from invalid clocks");
        System.out.println("TripleTapDetectorTest passed");
    }
    private static void expect(boolean expected, boolean actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual);
    }
}
