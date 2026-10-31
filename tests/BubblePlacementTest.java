package com.example.slideassist;

public final class BubblePlacementTest {
    public static void main(String[] args) {
        check(BubblePlacement.clampX(-30, 360, 36) == 0, "cannot drag past left edge");
        check(BubblePlacement.clampX(500, 360, 36) == 324, "cannot drag past right edge");
        check(BubblePlacement.clampX(120, 360, 36) == 120, "inside horizontal drag is preserved");
        check(BubblePlacement.snapX(100, 360, 36) == 0, "left half docks to physical left");
        check(BubblePlacement.snapX(250, 360, 36) == 324, "right half docks to physical right");
        check(BubblePlacement.snapX(162, 360, 36) == 324, "equal distances dock right");
        check(BubblePlacement.snapX(-400, 360, 36) == 0, "out of bounds left still docks left");
        check(BubblePlacement.snapX(1000, 360, 36) == 324, "out of bounds right still docks right");
        check(BubblePlacement.clampX(10, 20, 36) == 0 && BubblePlacement.snapX(10, 20, 36) == 0,
                "oversized control cannot get a negative coordinate");
        check(BubblePlacement.snapX(Integer.MAX_VALUE, Integer.MAX_VALUE, 36) == Integer.MAX_VALUE - 36,
                "large dimensions do not overflow midpoint calculation");
        check(BubbleSettings.normalizeOpacityPercent(-20) == 10, "opacity has ten percent lower bound");
        check(BubbleSettings.normalizeOpacityPercent(101) == 100, "opacity has full upper bound");
        check(BubbleSettings.normalizeOpacityPercent(35) == 35, "selected intermediate opacity is preserved");
        check(BubbleSettings.DEFAULT_OPACITY_PERCENT == 10, "default opacity stays ten percent");
        check(BubbleSettings.DEFAULT_SIZE_DP == 64, "phone widget defaults to a readable64dp square");
        check(BubbleSettings.normalizeSizeDp(0) == 32 && BubbleSettings.normalizeSizeDp(31) == 32,
                "phone widget cannot be smaller than32dp");
        check(BubbleSettings.normalizeSizeDp(32) == 32 && BubbleSettings.normalizeSizeDp(75) == 75,
                "valid chosen size is preserved");
        check(BubbleSettings.normalizeSizeDp(120) == 120 && BubbleSettings.normalizeSizeDp(Integer.MAX_VALUE) == 120,
                "phone widget cannot exceed120dp");
        check(BubblePlacement.dockedX(false, 360, 32) == 328
                        && BubblePlacement.dockedX(false, 360, 120) == 240,
                "changing size preserves the physical right edge");
        check(BubblePlacement.dockedX(true, 360, 32) == 0 && BubblePlacement.dockedX(true, 360, 120) == 0,
                "changing size preserves the physical left edge");
        check(!BubblePlacement.overlapsHorizontal(0, 36, 100, 250),
                "left-docked bubble must not avoid a disjoint strip to its right");
        check(!BubblePlacement.overlapsHorizontal(324, 36, 0, 300),
                "right-docked bubble must not avoid a disjoint strip to its left");
        check(BubblePlacement.overlapsHorizontal(0, 36, 20, 300),
                "partial intersection on the left edge must be avoided");
        check(BubblePlacement.overlapsHorizontal(324, 36, 20, 320),
                "partial intersection on the right edge must be avoided");
        System.out.println("BubblePlacementTest passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
