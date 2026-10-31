package com.example.slideassist;

/** Physical screen-edge placement shared by the preview and accessibility overlay. */
public final class BubblePlacement {
    private BubblePlacement() { }
    public static int clampX(int x, int screenWidth, int bubbleWidth) {
        int right = Math.max(0, Math.max(0, screenWidth) - Math.max(0, bubbleWidth));
        return Math.max(0, Math.min(right, x));
    }
    public static int snapX(int x, int screenWidth, int bubbleWidth) {
        int right = clampX(Integer.MAX_VALUE, screenWidth, bubbleWidth);
        return (long) clampX(x, screenWidth, bubbleWidth) * 2 < right ? 0 : right;
    }
    public static int dockedX(boolean left, int screenWidth, int bubbleWidth) {
        return left ? 0 : clampX(Integer.MAX_VALUE, screenWidth, bubbleWidth);
    }
    public static boolean shouldExpandFromEdge(boolean left, float dx, float dy, float threshold) {
        if (!Float.isFinite(dx) || !Float.isFinite(dy) || !Float.isFinite(threshold) || threshold < 0) return false;
        float inward = left ? dx : -dx;
        return inward >= threshold && inward > Math.abs(dy);
    }
    /** Returns no fold(-1), left(0) or right(1); ordinary interior motion remains free. */
    public static int collapseSide(int x, int screenWidth, int bubbleWidth, float dx, float dy, float edgeThreshold, float slop) {
        if (screenWidth <= 0 || bubbleWidth <= 0 || !Float.isFinite(dx) || !Float.isFinite(dy)
                || !Float.isFinite(edgeThreshold) || !Float.isFinite(slop) || edgeThreshold < 0 || slop < 0) return -1;
        if (x <= edgeThreshold && dx < -slop && -dx > Math.abs(dy)) return 0;
        long rightGap = (long) screenWidth - bubbleWidth - x;
        if (rightGap <= edgeThreshold && dx > slop && dx > Math.abs(dy)) return 1;
        return -1;
    }
    public static int resizedX(int oldX, int screenWidth, int oldWidth, int newWidth) {
        if (oldX <= 0) return 0;
        int oldRight = clampX(Integer.MAX_VALUE, screenWidth, oldWidth);
        if (oldX >= oldRight) return dockedX(false, screenWidth, newWidth);
        return clampX(oldX, screenWidth, newWidth);
    }
    public static boolean overlapsHorizontal(int bubbleLeft, int bubbleWidth, int stripLeft, int stripWidth) {
        return bubbleWidth > 0 && stripWidth > 0 && (long) bubbleLeft + bubbleWidth > stripLeft
                && (long) stripLeft + stripWidth > bubbleLeft;
    }
}
