package com.example.slideassist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class KeywordAlertPaletteTest {
    public static void main(String[] args) {
        mapsEachSchoolToItsRequestedOpaqueColor();
        keepsKeywordTextReadableOnOrderSurfaces();
        labelsEveryColorInWords();
        rejectsUnknownIndividualKeywords();
        keepsOnlyKnownHitsInCanonicalOrder();
        returnsReadOnlySnapshots();
        System.out.println("KeywordAlertPaletteTest passed");
    }

    private static void mapsEachSchoolToItsRequestedOpaqueColor() {
        expect(0xffdc2626, KeywordAlertPalette.colorFor("南邮"), "南邮 must be red");
        expect(0xff15803d, KeywordAlertPalette.colorFor("南航"), "南航 must be green");
        expect(0xff2563eb, KeywordAlertPalette.colorFor("南理工"), "南理工 must be blue");
        for (String school : Arrays.asList("南邮", "南航", "南理工"))
            expect(255, KeywordAlertPalette.colorFor(school) >>> 24, "base palette color must be opaque");
    }

    private static void labelsEveryColorInWords() {
        expect("红", KeywordAlertPalette.colorLabel("南邮"), "red label");
        expect("绿", KeywordAlertPalette.colorLabel("南航"), "green label");
        expect("蓝", KeywordAlertPalette.colorLabel("南理工"), "blue label");
    }

    private static void keepsKeywordTextReadableOnOrderSurfaces() {
        String[] schools = {"南邮", "南航", "南理工"};
        int[] expectedText = {0xff7f1d1d, 0xff14532d, 0xff1e3a8a};
        int[] bands = {0xffdc2626, 0xff15803d, 0xff2563eb};
        int[] backgrounds = {0xffffffff, 0xfff4f4f1};
        for (int index = 0; index < schools.length; index++) {
            int text = KeywordAlertPalette.textColorFor(schools[index]);
            expect(expectedText[index], text, "school keyword text needs its darker matching hue");
            for (int band : bands) for (int background : backgrounds)
                // Alpha0 covers the current static page. Light tints are an extra palette guard,
                // not a claim that the order page draws a full-screen reminder layer.
                for (int alpha = 0; alpha <= 56; alpha++) {
                    int visibleText = composite(band, text, alpha);
                    int visibleBackground = composite(band, background, alpha);
                    if (contrast(visibleText, visibleBackground) < 4.5)
                        throw new AssertionError("normal-size keyword text must retain at least4.5:1 contrast: "
                                + schools[index] + " at alpha" + alpha);
                }
        }
    }

    private static void rejectsUnknownIndividualKeywords() {
        for (String unknown : Arrays.asList(null, "", "其他学校", "南 邮", "南郵", "南京邮电大学")) {
            try {
                KeywordAlertPalette.colorFor(unknown);
                throw new AssertionError("unknown keyword must not silently become a red reminder");
            } catch (IllegalArgumentException expected) { }
            try {
                KeywordAlertPalette.colorLabel(unknown);
                throw new AssertionError("unknown keyword must not receive a made-up color label");
            } catch (IllegalArgumentException expected) { }
            try {
                KeywordAlertPalette.textColorFor(unknown);
                throw new AssertionError("unknown keyword must not receive an arbitrary text color");
            } catch (IllegalArgumentException expected) { }
        }
    }

    private static void keepsOnlyKnownHitsInCanonicalOrder() {
        expect(Arrays.asList(0xffdc2626, 0xff15803d, 0xff2563eb),
                KeywordAlertPalette.colorsFor(Arrays.asList("南理工", "南航", "南邮", "南理工", null, "其他")),
                "multiple hits must be deduplicated and ordered red, green, blue");
        expect(Arrays.asList(0xff15803d, 0xff2563eb),
                KeywordAlertPalette.colorsFor(Arrays.asList("南理工", "南航", "南航")),
                "a subset must not introduce red for an absent school");
        expect(Arrays.asList(0xff2563eb), KeywordAlertPalette.colorsFor(Arrays.asList("南理工", "南理工")),
                "repeated school must create exactly one color");
        expect(Collections.emptyList(), KeywordAlertPalette.colorsFor(null), "null hits");
        expect(Collections.emptyList(), KeywordAlertPalette.colorsFor(Collections.emptyList()), "empty hits");
        expect(Collections.emptyList(), KeywordAlertPalette.colorsFor(Arrays.asList(null, "", "其他")), "unknown hits");
    }

    private static void returnsReadOnlySnapshots() {
        List<String> input = new ArrayList<>(Arrays.asList("南航"));
        List<Integer> colors = KeywordAlertPalette.colorsFor(input);
        input.clear();
        expect(Arrays.asList(0xff15803d), colors, "later caller mutation must not change selected colors");
        try {
            colors.add(0xffdc2626);
            throw new AssertionError("caller must not append a color");
        } catch (UnsupportedOperationException expected) { }
        try {
            colors.set(0, 0xffdc2626);
            throw new AssertionError("caller must not replace a color");
        } catch (UnsupportedOperationException expected) { }
        try {
            KeywordAlertPalette.colorsFor(null).add(0xffdc2626);
            throw new AssertionError("caller must not append to an empty result");
        } catch (UnsupportedOperationException expected) { }
        expect(Arrays.asList(0xffdc2626), KeywordAlertPalette.colorsFor(Arrays.asList("南邮")),
                "one result cannot pollute subsequent calls");
    }

    private static void expect(Object expected, Object actual, String description) {
        if (!expected.equals(actual)) throw new AssertionError(description + ": " + actual + " vs " + expected);
    }

    private static int composite(int overlay, int underneath, int alpha) {
        int result = 0xff000000;
        for (int shift : new int[]{16, 8, 0}) {
            int top = (overlay >>> shift) & 255, bottom = (underneath >>> shift) & 255;
            int value = (int) Math.round(top * alpha / 255.0 + bottom * (1 - alpha / 255.0));
            result |= value << shift;
        }
        return result;
    }

    private static double contrast(int first, int second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
    }

    private static double luminance(int color) {
        return .2126 * channel((color >>> 16) & 255) + .7152 * channel((color >>> 8) & 255)
                + .0722 * channel(color & 255);
    }

    private static double channel(int value) {
        double srgb = value / 255.0;
        return srgb <= .04045 ? srgb / 12.92 : Math.pow((srgb + .055) / 1.055, 2.4);
    }
}
