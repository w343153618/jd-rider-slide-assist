package com.example.slideassist;

import java.util.HashSet;
import java.util.Set;

public final class ReactionModelTest {
    public static void main(String[] args) {
        generatedOrdersAreNewAndExactlyOneTarget();
        scoresOnlyFirstTargetSuccessAndPreservesRetries();
        excludesUnpresentedAndInvalidClocks();
        boundsSnapshotsAndScrollMetrics();
        actualSamplePathMetrics();
        retryMeansAttemptAfterTheFirstValidAttempt();
        System.out.println("ReactionModelTest passed");
    }

    private static void retryMeansAttemptAfterTheFirstValidAttempt() {
        ReactionSession session = new ReactionSession();
        ReactionOrderGenerator.Batch batch = ReactionOrderGenerator.generate(8, null);
        session.beginRound(batch); session.markPresented(100);
        session.recordAttempt(batch.targetIndex, 200, 210, -1, 250, true, "incomplete", null);
        check(session.snapshot().rounds.get(0).retryCount == 0, "first attempt is not a retry");
        session.recordAttempt(batch.targetIndex, 300, 310, -1, 350, false, "invalid_start", null);
        session.recordAttempt(batch.targetIndex, 400, 410, -1, 450, true, "incomplete", null);
        check(session.snapshot().rounds.get(0).retryCount == 1, "next valid attempt is one retry");
    }

    private static void actualSamplePathMetrics() {
        InteractionRecorder.clear(); InteractionRecorder.start();
        InteractionRecorder.Trace trace = InteractionRecorder.begin("manual", 100, 100, 2);
        trace.add(point(100, 0, 0, .2f)); trace.add(point(110, 6, 8, .4f));
        trace.add(point(120, 2, 8, .6f)); trace.add(point(130, 20, 0, .8f));
        trace.finish("success", 140);
        ReactionStats.PathMetrics metrics = ReactionStats.metrics(InteractionRecorder.snapshot().traces.get(0));
        close(2, metrics.backtrackingDp, "negative horizontal travel");
        close(4, metrics.maxVerticalDeviationDp, "vertical deviation from first point");
        close(4, metrics.verticalRangeDp, "vertical range");
        close(10 / (7 + Math.sqrt(97)), metrics.directness, "straight displacement / travelled path");
        close(.5, metrics.pressureMean, "raw pressure mean");
        close(Math.sqrt(.05), metrics.pressureStddev, "raw pressure spread");
        InteractionRecorder.stop(); InteractionRecorder.clear();
        check(Double.isNaN(ReactionStats.metrics(null).directness), "missing path explicitly unavailable");
    }
    private static InteractionRecorder.Sample point(long time, float x, float y, float pressure) {
        return new InteractionRecorder.Sample(time, time, x, y, pressure, .3f, 10, 8, 0, 1, 2, 0);
    }

    private static void scoresOnlyFirstTargetSuccessAndPreservesRetries() {
        ReactionSession session = new ReactionSession();
        ReactionOrderGenerator.Batch batch = ReactionOrderGenerator.generate(4, null);
        session.beginRound(batch); session.markPresented(100);
        int correct = batch.targetIndex, wrong = (correct + 1) % 6;
        session.recordAttempt(wrong, 200, 210, 250, 270, true, "success", null);
        session.recordAttempt(correct, 350, 360, -1, 400, false, "invalid_start", null);
        session.recordAttempt(correct, 500, 510, -1, 540, true, "incomplete", null);
        session.recordAttempt(correct, 600, 610, 650, 670, true, "success", null);
        session.recordAttempt(wrong, 800, 810, 850, 870, true, "success", null);
        ReactionSession.Round round = session.snapshot().rounds.get(0);
        check("success".equals(round.status) && round.attempts.size() == 5, "single scored success");
        check(round.wrongAttempts == 1 && round.invalidStarts == 1 && round.retryCount == 2, "wrong/invalid/retry distinct");
        check(round.attempts.get(3).scored && round.attempts.get(4).practice, "later practice cannot rescore");
        close(400, round.searchMillis(), "search ends at first correct valid DOWN");
        close(50, round.dragMillis(), "winning drag"); close(550, round.totalMillis(), "total includes previous errors");
        close(10, round.initiationMillis(), "first movement hesitation"); close(20, round.holdMillis(), "hold after target");
        ReactionStats stats = ReactionStats.of(session.snapshot());
        check(stats.successfulRounds == 1 && stats.eligibleAttempts == 4 && stats.wrongAttempts == 1, "practice excluded from stats");
        close(1, stats.successRate, "round success rate"); close(.25, stats.errorRate, "wrong-order / all eligible attempts");
        check(!session.recordAttempt(correct, 600, 610, 650, 670, true, "success", null), "duplicate callback rejected");
    }

    private static void excludesUnpresentedAndInvalidClocks() {
        ReactionSession session = new ReactionSession();
        ReactionOrderGenerator.Batch batch = ReactionOrderGenerator.generate(1, null);
        session.beginRound(batch);
        session.recordAttempt(batch.targetIndex, 10, 20, 30, 40, true, "success", null);
        session.markPresented(100);
        session.recordAttempt(batch.targetIndex, 90, 100, 110, 120, true, "success", null);
        session.recordAttempt(batch.targetIndex, 200, 220, 250, 240, true, "success", null);
        ReactionSession.Round round = session.snapshot().rounds.get(0);
        check(round.winner() == null && round.firstCorrectDownTime == -1, "unpresented and broken time cannot score");
        check(ReactionStats.of(session.snapshot()).total.count == 0, "invalid values never enter distributions");
        session.finishRound("refreshed", 300);
        check("refreshed".equals(session.snapshot().rounds.get(0).status), "abandoned reason preserved");
        check(!session.recordAttempt(batch.targetIndex, 310, 320, 330, 340, true, "success", null), "late detached callbacks rejected");
    }

    private static void boundsSnapshotsAndScrollMetrics() {
        ReactionSession session = new ReactionSession();
        ReactionOrderGenerator.Batch batch = ReactionOrderGenerator.generate(3, null);
        session.beginRound(batch); session.markPresented(100);
        session.recordScroll(50, 120); session.recordScroll(100, 130); session.recordScroll(70, 140);
        session.recordScroll(70, 145); session.markTargetVisible(140); session.markTargetVisible(150);
        ReactionSession.Snapshot frozen = session.snapshot();
        ReactionSession.Round round = frozen.rounds.get(0);
        check(round.scrollDistancePx == 130 && round.maxScrollYPx == 100 && round.directionChanges == 1, "scroll path metrics");
        check(round.targetVisibleTime == 140, "first marker visibility only");
        for (int i = 0; i < ReactionSession.MAX_ATTEMPTS + 1; i++)
            session.recordAttempt(batch.targetIndex, 200 + i * 10, -1, -1, 205 + i * 10, true, "incomplete", null);
        check(session.snapshot().totalAttempts == ReactionSession.MAX_ATTEMPTS && session.snapshot().droppedAttempts == 1, "bounded attempts");
        check(frozen.rounds.get(0).attempts.isEmpty(), "frozen snapshot detached");
        try { frozen.rounds.clear(); throw new AssertionError("mutable rounds"); } catch (UnsupportedOperationException expected) { }
        for (int i = 1; i < ReactionSession.MAX_ROUNDS; i++) { session.finishRound("refreshed", 3000 + i); session.beginRound(batch); }
        session.finishRound("stopped", 4000);
        check(!session.beginRound(batch) && session.snapshot().droppedRounds == 1, "bounded rounds");
        ReactionStats.Distribution d = ReactionStats.distribution(new double[]{10, 20, 30, Double.NaN, -1});
        check(d.count == 3, "only valid durations"); close(20, d.mean, "distribution mean"); close(20, d.median, "distribution median");
        close(30, d.p95, "nearest rank p95"); close(Math.sqrt(200.0 / 3), d.stddev, "population stddev");
    }

    private static void generatedOrdersAreNewAndExactlyOneTarget() {
        ReactionOrderGenerator.Batch previous = null;
        Set<Integer> lengths = new HashSet<>(), positions = new HashSet<>();
        for (int seed = 0; seed < 500; seed++) {
            ReactionOrderGenerator.Batch batch = ReactionOrderGenerator.generate(seed, previous);
            check(batch.orders.size() == 6, "six orders");
            int targets = 0;
            for (int i = 0; i < batch.orders.size(); i++) {
                ReactionOrderGenerator.Order order = batch.orders.get(i);
                for (String value : new String[]{order.title, order.address, order.note}) {
                    targets += occurrences(value, '田'); lengths.add(value.length());
                }
                if (previous != null) for (ReactionOrderGenerator.Order old : previous.orders) {
                    check(!order.title.replace("田", "").equals(old.title.replace("田", "")), "new title");
                    check(!order.address.replace("田", "").equals(old.address.replace("田", "")), "new address");
                    check(!order.note.replace("田", "").equals(old.note.replace("田", "")), "new note");
                }
            }
            check(targets == 1, "exactly one target character");
            ReactionOrderGenerator.Order target = batch.orders.get(batch.targetIndex);
            String field = "title".equals(batch.targetField) ? target.title
                    : "address".equals(batch.targetField) ? target.address : target.note;
            check(field.charAt(batch.targetOffset) == '田', "target metadata matches text");
            positions.add(batch.targetIndex);
            previous = batch;
        }
        check(lengths.size() > 15 && positions.size() == 6, "varied lengths and target positions");
        ReactionOrderGenerator.Batch first = ReactionOrderGenerator.generate(42, null);
        ReactionOrderGenerator.Batch same = ReactionOrderGenerator.generate(42, null);
        check(first.orders.get(0).title.equals(same.orders.get(0).title), "reproducible seed");
        ReactionOrderGenerator.Batch different = ReactionOrderGenerator.generate(42, first);
        check(!first.orders.get(0).title.replace("田", "").equals(different.orders.get(0).title.replace("田", "")), "same seed refresh still new");
        try { first.orders.clear(); throw new AssertionError("mutable orders"); }
        catch (UnsupportedOperationException expected) { }
    }

    private static int occurrences(String value, char character) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) == character) count++;
        return count;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void close(double expected, double actual, String message) { check(Math.abs(expected - actual) < .0001, message + ": " + actual); }
}
