package com.example.slideassist;

/** Compact shared dimensions and the user-selected visibility range. */
public final class BubbleSettings {
    public static final int MIN_SIZE_DP = 32, DEFAULT_SIZE_DP = 64, MAX_SIZE_DP = 120;
    public static final int WIDTH_DP = DEFAULT_SIZE_DP, HEIGHT_DP = DEFAULT_SIZE_DP;
    public static final int MIN_OPACITY_PERCENT = 10, DEFAULT_OPACITY_PERCENT = 10, MAX_OPACITY_PERCENT = 100;
    public static final String OPACITY_PREF = "bubble_opacity_percent";
    public static final String SIZE_PREF = "bubble_size_dp";
    private BubbleSettings() { }
    public static int normalizeOpacityPercent(int value) {
        return Math.max(MIN_OPACITY_PERCENT, Math.min(MAX_OPACITY_PERCENT, value));
    }
    public static int normalizeSizeDp(int value) {
        return Math.max(MIN_SIZE_DP, Math.min(MAX_SIZE_DP, value));
    }
}
