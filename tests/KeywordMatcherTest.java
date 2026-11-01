package com.example.slideassist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class KeywordMatcherTest {
    public static void main(String[] args) {
        matchesEachRequestedAbbreviation();
        deduplicatesInStableCanonicalOrder();
        ignoresNullAndUnrelatedText();
        neverJoinsDifferentTextFragments();
        keepsLiteralMatchingExplicit();
        acceptsCharSequencesAndReturnsImmutableResults();
        canonicalKeywordListIsReadOnly();
        System.out.println("KeywordMatcherTest passed");
    }

    private static void matchesEachRequestedAbbreviation() {
        expect(Arrays.asList("南邮"), match("送至南邮仙林校区东门"), "南邮 address");
        expect(Arrays.asList("南航"), match("南航江宁校区 · 食堂门口"), "南航 address");
        expect(Arrays.asList("南理工"), match("备注：放在南理工快递点"), "南理工 note");
        expect(Arrays.asList("南航"), match("[南航] 🚀"), "literal substring with punctuation and emoji");
    }

    private static void deduplicatesInStableCanonicalOrder() {
        expect(Arrays.asList("南邮", "南航", "南理工"),
                KeywordMatcher.matchFragments(Arrays.asList("南理工 南航", "南邮南邮", "南理工", "南航")),
                "all matches are returned once in canonical order, independent of view order");
        List<String> repeats = new ArrayList<>();
        for (int n = 0; n < 1000; n++) repeats.add(n % 2 == 0 ? "南航门口" : "南理工门口");
        expect(Arrays.asList("南航", "南理工"), KeywordMatcher.matchFragments(repeats),
                "repeated visible entries cannot inflate the result count");
    }

    private static void ignoresNullAndUnrelatedText() {
        expect(Collections.emptyList(), KeywordMatcher.matchFragments(null), "null collection");
        expect(Collections.emptyList(), KeywordMatcher.matchFragments(Collections.emptyList()), "empty collection");
        expect(Collections.emptyList(), KeywordMatcher.matchFragments(Arrays.asList(null, "", "示例便利店", "北京西路88号")),
                "null, empty, and unrelated fragments");
        expect(Arrays.asList("南邮"), KeywordMatcher.matchFragments(Arrays.asList(null, "南邮校门", null)),
                "null neighboring fragments do not hide a valid match");
    }

    private static void neverJoinsDifferentTextFragments() {
        expect(Collections.emptyList(), KeywordMatcher.matchFragments(Arrays.asList("南", "邮", "航", "南理", "工")),
                "characters in separate visible views must not be concatenated into a keyword");
        expect(Arrays.asList("南航"), KeywordMatcher.matchFragments(Arrays.asList("南", "邮", "南航")),
                "a genuine fragment still matches beside separated lookalikes");
    }

    private static void keepsLiteralMatchingExplicit() {
        expect(Collections.emptyList(), match("南京邮电大学 南京航空航天大学 南京理工大学"),
                "full school names are not implicit aliases for the three requested literal abbreviations");
        expect(Collections.emptyList(), match("南 邮 / 南\n航 / 南理 工 / 南郵"),
                "spaces, newlines, and alternate characters are not silently normalized");
        expect(Collections.emptyList(), match("南\u200b邮 南\u200d航 南理\u200b工"),
                "zero-width characters do not create an undocumented fuzzy match");
        expect(Arrays.asList("南航"), match("  南航  "), "ordinary surrounding whitespace does not prevent a literal match");
    }

    private static void acceptsCharSequencesAndReturnsImmutableResults() {
        StringBuilder address = new StringBuilder("南邮取货点");
        List<String> result = KeywordMatcher.matchFragments(Arrays.<CharSequence>asList(address));
        address.setLength(0);
        expect(Arrays.asList("南邮"), result, "later mutation of caller text does not mutate result strings");
        try {
            result.add("南航");
            throw new AssertionError("caller must not mutate the returned result list");
        } catch (UnsupportedOperationException expected) {
            // The caller receives a read-only snapshot, not matcher-owned mutable state.
        }
        try {
            result.set(0, "南航");
            throw new AssertionError("caller must not replace a returned match");
        } catch (UnsupportedOperationException expected) {
            // Replacements must be rejected as well as appends.
        }
        expect(Arrays.asList("南航"), match("南航"), "subsequent calls are independent");
    }

    private static List<String> match(CharSequence text) {
        return KeywordMatcher.matchFragments(Collections.singletonList(text));
    }

    private static void canonicalKeywordListIsReadOnly() {
        expect(Arrays.asList("南邮", "南航", "南理工"), KeywordMatcher.keywords(), "canonical keyword source");
        try {
            KeywordMatcher.keywords().clear();
            throw new AssertionError("callers must not change the canonical keyword list");
        } catch (UnsupportedOperationException expected) {
            // Displaying the rules must not allow the caller to mutate matching behavior.
        }
        expect(Arrays.asList("南邮"), match("南邮"), "failed canonical-list mutation leaves matching intact");
    }

    private static void expect(List<String> expected, List<String> actual, String description) {
        if (!expected.equals(actual)) throw new AssertionError(description + ": " + actual + " vs " + expected);
    }
}
