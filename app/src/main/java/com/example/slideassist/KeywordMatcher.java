package com.example.slideassist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Literal local-text matching. The caller decides which loaded text fragments to supply. */
public final class KeywordMatcher {
    private static final List<String> KEYWORDS = Collections.unmodifiableList(Arrays.asList("南邮", "南航", "南理工"));

    private KeywordMatcher() { }

    public static List<String> keywords() { return KEYWORDS; }

    /** Never joins separate fragments, expands school aliases, or silently normalizes characters. */
    public static List<String> matchFragments(Iterable<? extends CharSequence> fragments) {
        if (fragments == null) return Collections.emptyList();
        boolean[] found = new boolean[KEYWORDS.size()];
        int count = 0;
        for (CharSequence fragment : fragments) {
            if (fragment == null) continue;
            String text = fragment.toString();
            for (int index = 0; index < KEYWORDS.size(); index++) {
                if (!found[index] && text.contains(KEYWORDS.get(index))) {
                    found[index] = true;
                    count++;
                }
            }
            if (count == KEYWORDS.size()) break;
        }
        if (count == 0) return Collections.emptyList();
        List<String> matches = new ArrayList<>(count);
        for (int index = 0; index < KEYWORDS.size(); index++) if (found[index]) matches.add(KEYWORDS.get(index));
        return Collections.unmodifiableList(matches);
    }
}
