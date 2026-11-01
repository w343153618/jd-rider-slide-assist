package com.example.slideassist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The three requested school colors; selection uses the matcher's canonical keyword order. */
public final class KeywordAlertPalette {
    private KeywordAlertPalette() { }

    public static int colorFor(String keyword) {
        if ("南邮".equals(keyword)) return 0xffdc2626;
        if ("南航".equals(keyword)) return 0xff15803d;
        if ("南理工".equals(keyword)) return 0xff2563eb;
        throw new IllegalArgumentException("Unknown school keyword: " + keyword);
    }

    public static String colorLabel(String keyword) {
        if ("南邮".equals(keyword)) return "红";
        if ("南航".equals(keyword)) return "绿";
        if ("南理工".equals(keyword)) return "蓝";
        throw new IllegalArgumentException("Unknown school keyword: " + keyword);
    }

    /** Darker matching hues keep keyword text readable on the order's white or gray surfaces. */
    public static int textColorFor(String keyword) {
        if ("南邮".equals(keyword)) return 0xff7f1d1d;
        if ("南航".equals(keyword)) return 0xff14532d;
        if ("南理工".equals(keyword)) return 0xff1e3a8a;
        throw new IllegalArgumentException("Unknown school keyword: " + keyword);
    }

    /** Null/unknown hits are ignored; each known school contributes at most one read-only color. */
    public static List<Integer> colorsFor(Iterable<String> hits) {
        if (hits == null) return Collections.emptyList();
        List<String> keywords = KeywordMatcher.keywords();
        boolean[] selected = new boolean[keywords.size()];
        for (String hit : hits) {
            int index = keywords.indexOf(hit);
            if (index >= 0) selected[index] = true;
        }
        List<Integer> colors = new ArrayList<>(keywords.size());
        for (int index = 0; index < keywords.size(); index++)
            if (selected[index]) colors.add(colorFor(keywords.get(index)));
        return Collections.unmodifiableList(colors);
    }
}
