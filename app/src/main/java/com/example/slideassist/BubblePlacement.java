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
    public static boolean overlapsHorizontal(int bubbleLeft, int bubbleWidth, int stripLeft, int stripWidth) {
        return bubbleWidth > 0 && stripWidth > 0 && (long) bubbleLeft + bubbleWidth > stripLeft
                && (long) stripLeft + stripWidth > bubbleLeft;
    }
}
