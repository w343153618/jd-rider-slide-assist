package com.example.slideassist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure calculations. Missing measurements are NaN; p95 uses the nearest-rank convention. */
public final class ReportStats {
    public final int manualAttemptCount, manualSuccessCount;
    public final double fastestManualMillis, meanManualMillis, medianManualMillis, p95ManualMillis;
    private ReportStats(int attempts, List<Double> durations) {
        manualAttemptCount = attempts; manualSuccessCount = durations.size();
        Collections.sort(durations);
        fastestManualMillis = durations.isEmpty() ? Double.NaN : durations.get(0);
        double sum = 0;
        for (double duration : durations) sum += duration;
        meanManualMillis = durations.isEmpty() ? Double.NaN : sum / durations.size();
        medianManualMillis = median(durations); p95ManualMillis = percentile95(durations);
    }
    public static ReportStats of(InteractionRecorder.Session session) {
        int attempts = 0; List<Double> durations = new ArrayList<>();
        for (InteractionRecorder.TraceSnapshot trace : session.traces) {
            if (!"manual".equals(trace.kind)) continue;
            attempts++;
            Long target = trace.stages.get("target");
            if ("success".equals(trace.outcome) && target != null && target >= trace.downEventTimeMillis
                    && target <= trace.finishedUptimeMillis) durations.add((double) (target - trace.downEventTimeMillis));
        }
        return new ReportStats(attempts, durations);
    }
    public static TraceMetrics metrics(InteractionRecorder.TraceSnapshot trace) { return new TraceMetrics(trace); }
    public static final class TraceMetrics {
        public final double pathDp, maxSpeedDpPerSecond, meanSpeedDpPerSecond;
        public final double sampleGapMedianMillis, sampleGapP95Millis, receiptLagMedianMillis, receiptLagP95Millis;
        public final double meanPressure, meanSize;
        private TraceMetrics(InteractionRecorder.TraceSnapshot trace) {
            double path = 0, maxSpeed = Double.NaN, pressure = 0, size = 0;
            int pressureCount = 0, sizeCount = 0;
            List<Double> gaps = new ArrayList<>(), lags = new ArrayList<>();
            InteractionRecorder.Sample previous = null;
            for (InteractionRecorder.Sample sample : trace.samples) {
                if (sample.receivedUptimeMillis >= sample.eventTimeMillis)
                    lags.add((double) (sample.receivedUptimeMillis - sample.eventTimeMillis));
                if (Float.isFinite(sample.pressure)) { pressure += sample.pressure; pressureCount++; }
                if (Float.isFinite(sample.size)) { size += sample.size; sizeCount++; }
                if (previous != null && sample.pointerId == previous.pointerId) {
                    long gap = sample.eventTimeMillis - previous.eventTimeMillis;
                    if (gap > 0) gaps.add((double) gap);
                    if (Float.isFinite(previous.x) && Float.isFinite(previous.y)
                            && Float.isFinite(sample.x) && Float.isFinite(sample.y)) {
                        double distance = Math.hypot((double) sample.x - previous.x, (double) sample.y - previous.y) / trace.density;
                        path += distance;
                        if (gap > 0) {
                            double speed = distance * 1000 / gap;
                            maxSpeed = Double.isNaN(maxSpeed) ? speed : Math.max(maxSpeed, speed);
                        }
                    }
                }
                previous = sample;
            }
            Collections.sort(gaps); Collections.sort(lags);
            pathDp = path; maxSpeedDpPerSecond = maxSpeed;
            int count = trace.samples.size();
            long span = count < 2 ? 0 : trace.samples.get(count - 1).eventTimeMillis - trace.samples.get(0).eventTimeMillis;
            meanSpeedDpPerSecond = span > 0 ? path * 1000 / span : Double.NaN;
            meanPressure = pressureCount == 0 ? Double.NaN : pressure / pressureCount;
            meanSize = sizeCount == 0 ? Double.NaN : size / sizeCount;
            sampleGapMedianMillis = median(gaps); sampleGapP95Millis = percentile95(gaps);
            receiptLagMedianMillis = median(lags); receiptLagP95Millis = percentile95(lags);
        }
    }
    private static double median(List<Double> sorted) {
        int n = sorted.size();
        return n == 0 ? Double.NaN : n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }
    private static double percentile95(List<Double> sorted) {
        return sorted.isEmpty() ? Double.NaN : sorted.get((int) Math.ceil(sorted.size() * .95) - 1);
    }
}
