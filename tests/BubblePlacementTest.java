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
        check(BubbleSettings.COLLAPSED_WIDTH_DP == 20, "collapsed control exposes a grabbable twenty dp handle");
        check(BubblePlacement.dockedX(true, 360, BubbleSettings.COLLAPSED_WIDTH_DP) == 0
                        && BubblePlacement.dockedX(false, 360, BubbleSettings.COLLAPSED_WIDTH_DP) == 340,
                "collapsed strip docks at both physical edges with its actual narrow width");
        check(!BubblePlacement.overlapsHorizontal(340, BubbleSettings.COLLAPSED_WIDTH_DP, 0, 330),
                "collapsed strip must not cover the previous full floating square");
        check(BubbleSettings.effectiveOpacityPercent(true, 10) == 35,
                "collapsed low-opacity handles remain visible enough to grab");
        check(BubbleSettings.effectiveOpacityPercent(true, 55) == 55
                        && BubbleSettings.effectiveOpacityPercent(false, 10) == 10,
                "collapsed visibility floor must not replace the expanded user preference");
        check(BubblePlacement.shouldExpandFromEdge(true, 12, 0, 12), "left handle expands at the inward threshold");
        check(BubblePlacement.shouldExpandFromEdge(false, -12, 0, 12), "right handle expands at the inward threshold");
        check(!BubblePlacement.shouldExpandFromEdge(true, 11.9f, 0, 12), "inward jitter below threshold does not expand");
        check(!BubblePlacement.shouldExpandFromEdge(false, 40, 0, 12)
                        && !BubblePlacement.shouldExpandFromEdge(true, -40, 0, 12), "outward pulls do not expand");
        check(!BubblePlacement.shouldExpandFromEdge(true, 20, 21, 12)
                        && !BubblePlacement.shouldExpandFromEdge(false, -20, -21, 12), "vertical drags do not expand");
        check(!BubblePlacement.shouldExpandFromEdge(true, 20, 20, 12), "an exactly diagonal pull is not inward-dominant");
        check(!BubblePlacement.shouldExpandFromEdge(true, Float.NaN, 0, 12)
                        && !BubblePlacement.shouldExpandFromEdge(true, 30, 0, -1), "invalid direction or threshold cannot expand");
        check(BubblePlacement.collapseSide(0, 360, 64, -20, 0, 12, 8) == 0,
                "outward left release at the edge collapses left");
        check(BubblePlacement.collapseSide(296, 360, 64, 20, 0, 12, 8) == 1,
                "outward right release at the edge collapses right");
        check(BubblePlacement.collapseSide(12, 360, 64, -9, 0, 12, 8) == 0
                        && BubblePlacement.collapseSide(13, 360, 64, -20, 0, 12, 8) == -1,
                "collapse requires the inclusive edge-distance boundary");
        check(BubblePlacement.collapseSide(0, 360, 64, -8, 0, 12, 8) == -1,
                "movement must exceed touch slop before folding");
        check(BubblePlacement.collapseSide(0, 360, 64, -20, 20, 12, 8) == -1
                        && BubblePlacement.collapseSide(296, 360, 64, 20, 30, 12, 8) == -1,
                "diagonal and vertical motions do not fold");
        check(BubblePlacement.collapseSide(0, 360, 64, 20, 0, 12, 8) == -1
                        && BubblePlacement.collapseSide(296, 360, 64, -20, 0, 12, 8) == -1,
                "inward pulls do not fold expanded controls");
        check(BubblePlacement.collapseSide(100, 360, 64, -50, 0, 12, 8) == -1,
                "an interior floating position does not snap or collapse");
        check(BubblePlacement.resizedX(0, 360, 64, 120) == 0
                        && BubblePlacement.resizedX(296, 360, 64, 120) == 240,
                "resizing an edge-aligned control preserves that edge");
        check(BubblePlacement.resizedX(100, 360, 64, 120) == 100,
                "resizing a freely placed control preserves its interior position");
        check(BubblePlacement.resizedX(270, 360, 64, 120) == 240,
                "resizing only clamps an interior control when necessary");
        check(BubblePlacement.resizedX(40, 160, 120, 32) == 128,
                "narrow-screen right anchors cannot flip to the left on shrink");
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
