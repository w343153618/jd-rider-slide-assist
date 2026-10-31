package com.example.slideassist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Descriptive reaction-training statistics. Missing durations never become zero. */
public final class ReactionStats {
    public final int presentedRounds, successfulRounds, eligibleAttempts, wrongAttempts, invalidStarts, cancelledAttempts;
    public final int comparisonWindowCount;
    public final double successRate, errorRate, first5TotalMean, last5TotalMean;
    public final Distribution search, drag, total, initiation, hold;

    private ReactionStats(ReactionSession.Snapshot snapshot) {
        int presented = 0, succeeded = 0, attempts = 0, wrong = 0, invalid = 0, cancelled = 0;
        List<Double> searches = new ArrayList<>(), drags = new ArrayList<>(), totals = new ArrayList<>();
        List<Double> initiations = new ArrayList<>(), holds = new ArrayList<>();
        for (ReactionSession.Round round : snapshot.rounds) {
            if (round.presentedTime >= 0) presented++;
            wrong += round.wrongAttempts; invalid += round.invalidStarts; cancelled += round.cancelledAttempts;
            for (ReactionSession.Attempt attempt : round.attempts) if (attempt.eligible) attempts++;
            if (round.winner() != null) {
                succeeded++; add(searches, round.searchMillis()); add(drags, round.dragMillis());
                add(totals, round.totalMillis()); add(initiations, round.initiationMillis()); add(holds, round.holdMillis());
            }
        }
        presentedRounds = presented; successfulRounds = succeeded; eligibleAttempts = attempts;
        wrongAttempts = wrong; invalidStarts = invalid; cancelledAttempts = cancelled;
        successRate = presented == 0 ? Double.NaN : (double) succeeded / presented;
        errorRate = attempts == 0 ? Double.NaN : (double) wrong / attempts;
        comparisonWindowCount = Math.min(5, totals.size());
        first5TotalMean = mean(totals.subList(0, comparisonWindowCount));
        last5TotalMean = mean(totals.subList(totals.size() - comparisonWindowCount, totals.size()));
        search = new Distribution(searches); drag = new Distribution(drags); total = new Distribution(totals);
        initiation = new Distribution(initiations); hold = new Distribution(holds);
    }

    public static ReactionStats of(ReactionSession.Snapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("Missing reaction snapshot");
        return new ReactionStats(snapshot);
    }

    public static Distribution distribution(double[] values) {
        List<Double> list = new ArrayList<>();
        if (values != null) for (double value : values) add(list, value);
        return new Distribution(list);
    }
    public static PathMetrics metrics(InteractionRecorder.TraceSnapshot trace) { return new PathMetrics(trace); }
    private static void add(List<Double> values, double value) { if (Double.isFinite(value) && value >= 0) values.add(value); }
    private static double mean(List<Double> values) {
        double sum = 0;
        for (double value : values) sum += value;
        return values.isEmpty() ? Double.NaN : sum / values.size();
    }

    /** Geometry of retained primary-pointer samples only; missing segments are never interpolated. */
    public static final class PathMetrics {
        public final double directness, backtrackingDp, verticalRangeDp, maxVerticalDeviationDp;
        public final double pressureMin, pressureMax, pressureMean, pressureStddev;
        public final int validPointCount;
        private PathMetrics(InteractionRecorder.TraceSnapshot trace) {
            double path = 0, backtracking = 0, minY = Double.NaN, maxY = Double.NaN, deviation = 0;
            InteractionRecorder.Sample first = null, previous = null, last = null;
            List<Double> pressures = new ArrayList<>();
            boolean broken = false;
            int count = 0, primary = trace == null || trace.samples.isEmpty() ? -1 : trace.samples.get(0).pointerId;
            if (trace != null) for (InteractionRecorder.Sample sample : trace.samples) {
                if (sample.pointerId != primary) { previous = null; broken = true; continue; }
                if (Float.isFinite(sample.pressure)) pressures.add((double) sample.pressure);
                if (!Float.isFinite(sample.x) || !Float.isFinite(sample.y)) { previous = null; broken = true; continue; }
                if (first == null) { first = sample; minY = maxY = sample.y; }
                minY = Math.min(minY, sample.y); maxY = Math.max(maxY, sample.y);
                deviation = Math.max(deviation, Math.abs((double) sample.y - first.y));
                if (previous != null) {
                    path += Math.hypot((double) sample.x - previous.x, (double) sample.y - previous.y);
                    backtracking += Math.max(0, (double) previous.x - sample.x);
                }
                previous = last = sample; count++;
            }
            double density = trace == null ? 1 : trace.density;
            validPointCount = count;
            directness = first == null || last == null || broken || path <= 0 ? Double.NaN
                    : Math.min(1, Math.hypot((double) last.x - first.x, (double) last.y - first.y) / path);
            backtrackingDp = count < 2 ? Double.NaN : backtracking / density;
            verticalRangeDp = count == 0 ? Double.NaN : (maxY - minY) / density;
            maxVerticalDeviationDp = count == 0 ? Double.NaN : deviation / density;
            Distribution pressure = new Distribution(pressures);
            pressureMin = pressure.min; pressureMax = pressure.max;
            pressureMean = pressure.mean; pressureStddev = pressure.stddev;
        }
    }

    public static final class Distribution {
        public final int count;
        public final double mean, median, p95, min, max, stddev;
        private Distribution(List<Double> values) {
            count = values.size(); mean = ReactionStats.mean(values);
            List<Double> sorted = new ArrayList<>(values); Collections.sort(sorted);
            min = count == 0 ? Double.NaN : sorted.get(0); max = count == 0 ? Double.NaN : sorted.get(count - 1);
            median = count == 0 ? Double.NaN : count % 2 == 0
                    ? (sorted.get(count / 2 - 1) + sorted.get(count / 2)) / 2 : sorted.get(count / 2);
            p95 = count == 0 ? Double.NaN : sorted.get((int) Math.ceil(count * .95) - 1);
            double variance = 0;
            for (double value : values) variance += (value - mean) * (value - mean);
            stddev = count == 0 ? Double.NaN : Math.sqrt(variance / count);
        }
    }
}
