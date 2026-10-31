package com.example.slideassist;

/** Compact shared dimensions and the user-selected visibility range. */
public final class BubbleSettings {
    public static final int WIDTH_DP = 28, HEIGHT_DP = 28;
    public static final int MIN_OPACITY_PERCENT = 10, DEFAULT_OPACITY_PERCENT = 10, MAX_OPACITY_PERCENT = 100;
    public static final String OPACITY_PREF = "bubble_opacity_percent";
    private BubbleSettings() { }
    public static int normalizeOpacityPercent(int value) {
        return Math.max(MIN_OPACITY_PERCENT, Math.min(MAX_OPACITY_PERCENT, value));
    }
}
