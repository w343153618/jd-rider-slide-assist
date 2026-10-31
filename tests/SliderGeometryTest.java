package com.example.slideassist;

public final class SliderGeometryTest {
    public static void main(String[] args) {
        SliderGeometry g = new SliderGeometry(320, 62, 1);
        equal(4, g.thumbLeft(), "white handle left");
        equal(4, g.thumbTop(), "white handle top");
        equal(54, g.thumbWidth(), "short handle follows height");
        equal(258, g.travel(), "full thumb travel");
        check(g.inHandle(20, 15), "white interior accepted");
        check(!g.inHandle(100, 31), "orange strip never activates");
        check(!g.inHandle(2, 31), "left inset never activates");
        check(!g.inHandle(20, 2), "top inset never activates");
        check(!g.inHandle(4, 4), "rounded white corner excluded");
        check(!g.inHandle(Float.NaN, 31), "NaN never activates");
        for (float x : new float[] {5, 12, 31, 57}) {
            SliderGeometry.Swipe swipe = g.fromLocal(x, 31, 10, 100);
            equal(10 + x, swipe.startX, "start is original DOWN x");
            equal(131, swipe.startY, "start is original DOWN y");
            equal(10 + x + 258, swipe.endX, "translation retains grab offset");
            equal(swipe.startY, swipe.endY, "y stays at original DOWN");
            check(swipe.endX < 10 + 320 - 4, "end remains inside right inset");
        }
        boolean rejected = false;
        try { g.fromLocal(200, 31, 0, 0); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "orange start cannot construct an assisted path");
        SliderGeometry dense = new SliderGeometry(800, 160, 2);
        equal(112, dense.thumbWidth(), "thumb capped at 56dp");
        equal(672, dense.travel(), "scaled full travel");
        check(dense.inHandle(65, 60), "scaled interior");
        SliderGeometry tiny = new SliderGeometry(5, 6, 1);
        check(!tiny.inHandle(4, 4), "undersized geometry rejects touch");
        equal(0, tiny.travel(), "undersized travel");
        SliderGeometry invalid = new SliderGeometry(Float.NaN, 62, 1);
        check(!invalid.inHandle(10, 20), "invalid geometry rejects touch");
        check(!g.inHandle(g.thumbRight(), 31), "handle right boundary is outside");
        check(!g.inHandle(20, g.thumbBottom()), "handle bottom boundary is outside");
        check(!new SliderGeometry(320, 62, 0).inHandle(20, 31), "zero density rejects touch");
        SliderGeometry.Swipe offCenter = g.fromLocal(17, 13, 400, 700);
        equal(417, offCenter.startX, "off-center physical x retained");
        equal(713, offCenter.startY, "off-center physical y retained");
        equal(675, offCenter.endX, "off-center end uses full travel");
        equal(713, offCenter.endY, "off-center y never snaps to center");
        System.out.println("SliderGeometryTest passed");
    }

    private static void equal(float expected, float actual, String message) {
        if (!Float.isFinite(actual) || Math.abs(expected - actual) > .001f) throw new AssertionError(message + ": " + actual + " != " + expected);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
