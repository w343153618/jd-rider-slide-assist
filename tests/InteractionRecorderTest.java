package com.example.slideassist;

public final class InteractionRecorderTest {
    public static void main(String[] args) {
        optInAndFrozenSnapshots();
        boundedAndOrderedSamples();
        cancelledAndSeparateKinds();
        statisticalMath();
        rawActionsAndMissingValues();
        stageAndConcurrentSnapshotSafety();
        reactionTraceIsDetachedAndExcludedFromManualRanking();
        System.out.println("InteractionRecorderTest passed");
    }

    private static void reactionTraceIsDetachedAndExcludedFromManualRanking() {
        InteractionRecorder.clear(); InteractionRecorder.start();
        InteractionRecorder.Trace reaction = InteractionRecorder.begin("reaction_manual", 100, 103, 1);
        reaction.add(point(100, 103, 0, 0));
        InteractionRecorder.TraceSnapshot detached = reaction.snapshot();
        reaction.add(point(110, 112, 30, 0));
        reaction.mark("target", 110); reaction.finish("success", 120);
        check(detached.samples.size() == 1 && "recording".equals(detached.outcome), "individual snapshot is detached");
        expectImmutable(() -> detached.samples.clear());
        check(ReportStats.of(InteractionRecorder.snapshot()).manualAttemptCount == 0, "reaction timing excluded from standalone hand ranking");
    }

    private static void optInAndFrozenSnapshots() {
        InteractionRecorder.stop(); InteractionRecorder.clear();
        check(InteractionRecorder.begin("manual", 100, 103, 2) == null, "off by default");
        InteractionRecorder.start();
        InteractionRecorder.Trace trace = InteractionRecorder.begin("manual", 100, 103, 2);
        trace.add(point(100, 103, 0, 0));
        InteractionRecorder.Session before = InteractionRecorder.snapshot();
        trace.add(point(110, 114, 20, 0));
        check(before.traces.get(0).samples.size() == 1, "snapshot is detached");
        expectImmutable(() -> before.traces.clear());
        expectImmutable(() -> before.traces.get(0).stages.put("fake", 1L));
        InteractionRecorder.stop();
        trace.add(point(120, 124, 30, 0)); trace.finish("success", 130);
        check("stopped".equals(InteractionRecorder.snapshot().traces.get(0).outcome), "stop seals active trace");
        InteractionRecorder.start(); trace.add(point(140, 144, 40, 0));
        check(InteractionRecorder.snapshot().traces.get(0).samples.size() == 2, "old trace stays sealed after restart");
        InteractionRecorder.clear(); trace.mark("target", 150);
        check(InteractionRecorder.snapshot().traces.isEmpty(), "clear detaches stale handles");
    }

    private static void boundedAndOrderedSamples() {
        InteractionRecorder.clear();
        InteractionRecorder.Trace trace = InteractionRecorder.begin("manual", 100, 103, 1);
        trace.add(point(100, 103, 0, 0)); trace.add(point(110, 115, 10, 0));
        trace.add(point(100, 119, 0, 0)); // A repeated historical point must not inflate travel.
        trace.add(point(105, 119, 5, 0));
        trace.add(point(110, 119, 11, 0)); // Distinct locations sharing a millisecond remain visible.
        InteractionRecorder.TraceSnapshot snap = InteractionRecorder.snapshot().traces.get(0);
        check(snap.samples.size() == 3 && snap.duplicateSamples == 1 && snap.outOfOrderSamples == 1, "dedupe and monotonic order");
        for (int i = 0; i < 1100; i++) trace.add(point(120 + i, 120 + i, i, 0));
        snap = InteractionRecorder.snapshot().traces.get(0);
        check(snap.samples.size() == 1024 && snap.droppedSamples == 79, "per-trace bound is visible");
        for (int i = 0; i < 20; i++) trace.mark("stage" + i, 100 + i);
        check(InteractionRecorder.snapshot().traces.get(0).droppedStages == 4, "bounded stages");
        InteractionRecorder.clear();
        for (int n = 0; n < 25; n++) {
            InteractionRecorder.Trace next = InteractionRecorder.begin("manual", 0, 0, 1);
            for (int i = 0; i < 1000; i++) next.add(point(i, i, i, 0));
        }
        check(InteractionRecorder.snapshot().totalSamples == 20000, "global sample bound");
        check(InteractionRecorder.snapshot().droppedTotalSamples == 5000, "global drops visible");
        InteractionRecorder.clear();
        for (int n = 0; n < 201; n++) InteractionRecorder.begin("manual", n, n, 1);
        check(InteractionRecorder.snapshot().traces.size() == 200 && InteractionRecorder.snapshot().droppedTraces == 1, "trace cap");
    }

    private static void cancelledAndSeparateKinds() {
        InteractionRecorder.clear();
        completed("manual", "cancelled", 1);
        completed("demo_assist", "success", 1);
        completed("service_assist", "completed", 1);
        InteractionRecorder.Trace good = completed("manual", "success", 120);
        good.mark("target", 1); good.finish("success", 1);
        ReportStats stats = ReportStats.of(InteractionRecorder.snapshot());
        check(stats.manualSuccessCount == 1 && stats.manualAttemptCount == 2, "only successful manual attempts count");
        close(120, stats.fastestManualMillis, "auxiliary/cancelled cannot win fastest");
        close(120, stats.meanManualMillis, "auxiliary/cancelled cannot affect manual mean");
        check(InteractionRecorder.snapshot().traces.get(3).stages.get("target") == 220, "finished trace immutable");
    }

    private static void statisticalMath() {
        InteractionRecorder.clear();
        for (int i = 1; i <= 20; i++) completed("manual", "success", i * 10);
        ReportStats stats = ReportStats.of(InteractionRecorder.snapshot());
        close(10, stats.fastestManualMillis, "fastest"); close(105, stats.medianManualMillis, "even median");
        close(190, stats.p95ManualMillis, "nearest-rank p95");
        InteractionRecorder.clear();
        completed("manual", "success", 100); completed("manual", "success", 200); completed("manual", "success", 900);
        close(400, ReportStats.of(InteractionRecorder.snapshot()).meanManualMillis, "arithmetic mean differs from median");
        InteractionRecorder.clear();
        InteractionRecorder.Trace trace = InteractionRecorder.begin("manual", 100, 103, 2);
        trace.add(point(100, 103, 0, 0)); trace.add(point(110, 115, 6, 8));
        trace.add(point(110, 117, 6, 8)); trace.add(point(130, 138, 6, 28));
        trace.mark("target", 130); trace.finish("success", 150);
        ReportStats.TraceMetrics metrics = ReportStats.metrics(InteractionRecorder.snapshot().traces.get(0));
        close(15, metrics.pathDp, "density-normalized path");
        close(500, metrics.maxSpeedDpPerSecond, "instantaneous positive-delta speed");
        close(15, metrics.sampleGapMedianMillis, "sample gaps exclude duplicate time");
        close(5, metrics.receiptLagMedianMillis, "receipt lag uses event/uptime clock");
    }

    private static void rawActionsAndMissingValues() {
        InteractionRecorder.clear();
        InteractionRecorder.Trace trace = InteractionRecorder.begin("manual", 100, 103, 1);
        trace.add(point(100, 103, 0, 0));
        trace.add(new InteractionRecorder.Sample(100, 103, 0, 0, .5f, .2f, 10, 8, 0, 1, 1, 0));
        check(InteractionRecorder.snapshot().traces.get(0).samples.size() == 2, "same-time UP remains distinct from MOVE");
        ReportStats.TraceMetrics metrics = ReportStats.metrics(InteractionRecorder.snapshot().traces.get(0));
        check(Double.isNaN(metrics.maxSpeedDpPerSecond), "speed unknown without positive time delta");
        trace.add(new InteractionRecorder.Sample(110, 113, Float.NaN, 0, Float.NaN, Float.POSITIVE_INFINITY, 0, 0, 0, 1, 2, 0));
        trace.add(point(140, 143, 10, 0)); trace.add(point(150, 153, 10, 0));
        metrics = ReportStats.metrics(InteractionRecorder.snapshot().traces.get(0));
        close(0, metrics.pathDp, "invalid coordinates break path instead of bridging the missing point");
        close(0, metrics.maxSpeedDpPerSecond, "stationary samples over positive time have zero speed");
        close(.5, metrics.meanPressure, "nonfinite pressure excluded from its own finite count");
        close(.2, metrics.meanSize, "nonfinite size excluded from its own finite count");
        close(10, metrics.sampleGapMedianMillis, "sampling cadence remains measurable across missing coordinates");
        close(30, metrics.sampleGapP95Millis, "missing coordinates do not conceal long sample gap");
    }

    private static void stageAndConcurrentSnapshotSafety() {
        InteractionRecorder.clear();
        check(Double.isNaN(ReportStats.of(InteractionRecorder.snapshot()).fastestManualMillis), "empty fastest unknown");
        check(Double.isNaN(ReportStats.of(InteractionRecorder.snapshot()).meanManualMillis), "empty mean unknown");
        InteractionRecorder.Trace trace = InteractionRecorder.begin("manual", 100, 100, 1);
        trace.mark("target", 140); trace.mark("target", 101); trace.mark("before_down", 99);
        InteractionRecorder.TraceSnapshot snapshot = InteractionRecorder.snapshot().traces.get(0);
        check(snapshot.stages.get("target") == 140 && snapshot.droppedStages == 1, "first mark wins and bad clock counted");
        check(ReportStats.of(InteractionRecorder.snapshot()).manualSuccessCount == 0, "active trace not a result");
        trace.finish("success", 130);
        check(ReportStats.of(InteractionRecorder.snapshot()).manualSuccessCount == 0, "target after finish is invalid");
        InteractionRecorder.clear();
        completed("manual", "success", 10); completed("manual", "success", 20); completed("manual", "success", 30);
        close(20, ReportStats.of(InteractionRecorder.snapshot()).medianManualMillis, "odd median");
        InteractionRecorder.clear();
        InteractionRecorder.Trace live = InteractionRecorder.begin("manual", 0, 0, 1);
        Thread writer = new Thread(() -> { for (int i = 0; i < 1000; i++) live.add(point(i, i, i, 0)); });
        writer.start();
        for (int i = 0; i < 100; i++) {
            InteractionRecorder.Session reading = InteractionRecorder.snapshot();
            check(reading.totalSamples == reading.traces.get(0).samples.size(), "snapshot is atomic");
        }
        try { writer.join(); } catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
        check(InteractionRecorder.snapshot().totalSamples == 1000, "concurrent writes retained");
    }

    private static InteractionRecorder.Trace completed(String kind, String outcome, long duration) {
        InteractionRecorder.Trace trace = InteractionRecorder.begin(kind, 100, 103, 1);
        trace.mark("target", 100 + duration); trace.finish(outcome, 2000); return trace;
    }
    private static InteractionRecorder.Sample point(long time, long received, float x, float y) {
        return new InteractionRecorder.Sample(time, received, x, y, .5f, .2f, 10, 8, 0, 1, 2, 0);
    }
    private static void expectImmutable(Runnable action) {
        try { action.run(); throw new AssertionError("snapshot mutable"); } catch (UnsupportedOperationException expected) { }
    }
    private static void close(double expected, double actual, String message) {
        check(Math.abs(expected - actual) < .0001, message + ": " + actual);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
