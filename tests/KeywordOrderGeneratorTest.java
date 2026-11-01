package com.example.slideassist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class KeywordOrderGeneratorTest {
    public static void main(String[] args) {
        Set<Integer> counts = new HashSet<>(), titleLengths = new HashSet<>(), addressLengths = new HashSet<>(), noteLengths = new HashSet<>();
        boolean sawNoKeywords = false, sawAllThree = false, sawLateKeyword = false, sawOnlyLateKeywords = false;
        KeywordOrderGenerator.Batch previous = null;
        for (long seed = 0; seed < 500; seed++) {
            KeywordOrderGenerator.Batch batch = KeywordOrderGenerator.generate(seed, previous);
            check(batch.seed == seed, "batch records the requested seed");
            check(batch.orders.size() >= 6 && batch.orders.size() <= 8, "each refresh must contain six to eight orders");
            counts.add(batch.orders.size());
            List<CharSequence> allText = new ArrayList<>();
            List<CharSequence> earlyAddresses = new ArrayList<>();
            for (int index = 0; index < batch.orders.size(); index++) {
                KeywordOrderGenerator.Order order = batch.orders.get(index);
                check(order.title.startsWith("示例") && order.address.contains("示例"), "all order content stays explicitly synthetic");
                check(!order.goods.isEmpty() && !order.note.isEmpty(), "goods and note remain usable display content");
                check(KeywordMatcher.matchFragments(Arrays.asList(order.title, order.goods, order.note)).isEmpty(),
                        "school keywords are intentionally inserted only into addresses");
                check(KeywordMatcher.matchFragments(Arrays.asList(order.address)).size() <= 1,
                        "one synthetic destination must not combine unrelated school names");
                allText.add(order.title); allText.add(order.address); allText.add(order.goods); allText.add(order.note);
                titleLengths.add(order.title.length()); addressLengths.add(order.address.length()); noteLengths.add(order.note.length());
                if (index >= 5 && !KeywordMatcher.matchFragments(Arrays.asList(order.address)).isEmpty()) sawLateKeyword = true;
                if (index < 5) earlyAddresses.add(order.address);
                if (previous != null && index < previous.orders.size()) assertNewFields(order, previous.orders.get(index));
            }
            List<String> found = KeywordMatcher.matchFragments(allText);
            if (found.isEmpty()) sawNoKeywords = true;
            if (found.equals(Arrays.asList("南邮", "南航", "南理工"))) sawAllThree = true;
            if (!found.isEmpty() && KeywordMatcher.matchFragments(earlyAddresses).isEmpty()) sawOnlyLateKeywords = true;
            previous = batch;
        }
        check(counts.size() == 3, "all six, seven, and eight order counts must occur");
        check(sawNoKeywords && sawAllThree, "random batches must include both no-school and all-three cases");
        check(sawLateKeyword, "matching destinations must also be able to appear in later offscreen orders");
        check(sawOnlyLateKeywords, "some batches must match exclusively in later orders, with no earlier school marker");
        check(titleLengths.size() > 1 && addressLengths.size() > 1 && noteLengths.size() > 1,
                "order fields must vary in length rather than repeat a uniform template size");
        sameSeedRefreshIsStillFresh();
        batchIsImmutable();
        seedReconstructsTheSameInitialBatch();
        System.out.println("KeywordOrderGeneratorTest passed");
    }

    private static void sameSeedRefreshIsStillFresh() {
        KeywordOrderGenerator.Batch a = KeywordOrderGenerator.generate(41, null);
        KeywordOrderGenerator.Batch b = KeywordOrderGenerator.generate(41, a);
        check(a.orders.size() == b.orders.size(), "same-seed freshness check compares matching positions");
        for (int n = 0; n < a.orders.size(); n++) assertNewFields(b.orders.get(n), a.orders.get(n));
    }

    private static void batchIsImmutable() {
        KeywordOrderGenerator.Batch batch = KeywordOrderGenerator.generate(12, null);
        try {
            batch.orders.clear();
            throw new AssertionError("batch order list must be a read-only snapshot");
        } catch (UnsupportedOperationException expected) {
            // Activity/tests may inspect orders but cannot mutate the generated batch.
        }
        check(batch.orders.size() >= 6, "failed mutation leaves the original batch unchanged");
    }

    private static void seedReconstructsTheSameInitialBatch() {
        KeywordOrderGenerator.Batch a = KeywordOrderGenerator.generate(-871, null);
        KeywordOrderGenerator.Batch b = KeywordOrderGenerator.generate(-871, null);
        check(a.orders.size() == b.orders.size(), "same initial seed reconstructs the batch size");
        for (int n = 0; n < a.orders.size(); n++) {
            KeywordOrderGenerator.Order first = a.orders.get(n), second = b.orders.get(n);
            check(first.title.equals(second.title) && first.address.equals(second.address)
                    && first.goods.equals(second.goods) && first.note.equals(second.note),
                    "initial content must be reproducible from its seed");
        }
    }

    private static void assertNewFields(KeywordOrderGenerator.Order order, KeywordOrderGenerator.Order old) {
        check(!stripKeywords(order.title).equals(stripKeywords(old.title)), "refreshed title must change at each retained index");
        check(!stripKeywords(order.address).equals(stripKeywords(old.address)), "address must change beyond adding/removing a school keyword");
        check(!stripKeywords(order.goods).equals(stripKeywords(old.goods)), "refreshed goods must change at each retained index");
        check(!stripKeywords(order.note).equals(stripKeywords(old.note)), "refreshed note must change at each retained index");
    }

    private static String stripKeywords(String value) {
        return value.replace("南邮", "").replace("南航", "").replace("南理工", "");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
