package com.example.slideassist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicitly enabled, process-local measurements of this app's own controls only. */
public final class InteractionRecorder {
    public static final int MAX_TRACES = 200, MAX_SAMPLES = 1024, MAX_TOTAL_SAMPLES = 20000;
    private static final int MAX_STAGES = 16, MAX_LABEL_LENGTH = 64;
    private static final Object LOCK = new Object();
    private static final List<Trace> TRACES = new ArrayList<>();
    private static boolean recording;
    private static int totalSamples;
    private static long droppedTraces, droppedTotalSamples;
    private InteractionRecorder() { }

    /** Starts or resumes collection; use clear() to discard the current session. */
    public static void start() { synchronized (LOCK) { recording = true; } }
    public static boolean isRecording() { synchronized (LOCK) { return recording; } }
    public static void stop() {
        synchronized (LOCK) {
            recording = false;
            for (Trace trace : TRACES) if (trace.active) { trace.active = false; trace.outcome = "stopped"; }
        }
    }
    public static void clear() {
        synchronized (LOCK) {
            for (Trace trace : TRACES) trace.active = false;
            TRACES.clear(); totalSamples = 0; droppedTraces = droppedTotalSamples = 0;
        }
    }
    public static Trace begin(String kind, long eventTimeMillis, long receivedUptimeMillis, float density) {
        synchronized (LOCK) {
            if (!recording) return null;
            if (!"manual".equals(kind) && !"demo_assist".equals(kind) && !"service_assist".equals(kind))
                throw new IllegalArgumentException("Unknown measurement kind");
            if (eventTimeMillis < 0 || receivedUptimeMillis < 0 || !Float.isFinite(density) || density <= 0)
                throw new IllegalArgumentException("Invalid measurement clock or density");
            if (TRACES.size() == MAX_TRACES) { droppedTraces++; return null; }
            Trace trace = new Trace(kind, eventTimeMillis, receivedUptimeMillis, density);
            TRACES.add(trace); return trace;
        }
    }
    public static Session snapshot() {
        synchronized (LOCK) {
            List<TraceSnapshot> traces = new ArrayList<>();
            for (Trace trace : TRACES) traces.add(new TraceSnapshot(trace));
            return new Session(recording, traces);
        }
    }

    public static final class Sample {
        public final long eventTimeMillis, receivedUptimeMillis;
        public final float x, y, pressure, size, touchMajor, touchMinor, orientation;
        public final int toolType, action, pointerId;
        public Sample(long eventTimeMillis, long receivedUptimeMillis, float x, float y,
                      float pressure, float size, float touchMajor, float touchMinor, float orientation,
                      int toolType, int action, int pointerId) {
            this.eventTimeMillis = eventTimeMillis; this.receivedUptimeMillis = receivedUptimeMillis;
            this.x = x; this.y = y; this.pressure = pressure; this.size = size;
            this.touchMajor = touchMajor; this.touchMinor = touchMinor; this.orientation = orientation;
            this.toolType = toolType; this.action = action; this.pointerId = pointerId;
        }
        private boolean samePoint(Sample other) {
            // Receipt time changes on redelivery, but distinct boundary actions must survive.
            return eventTimeMillis == other.eventTimeMillis && pointerId == other.pointerId
                    && action == other.action
                    && x == other.x && y == other.y && pressure == other.pressure && size == other.size
                    && touchMajor == other.touchMajor && touchMinor == other.touchMinor
                    && orientation == other.orientation && toolType == other.toolType;
        }
    }

    public static final class Trace {
        private final String kind;
        private final long downEventTimeMillis, receivedUptimeMillis;
        private final float density;
        private final List<Sample> samples = new ArrayList<>();
        private final Map<String, Long> stages = new LinkedHashMap<>();
        private String outcome = "recording";
        private long finishedUptimeMillis = -1, droppedSamples, duplicateSamples, outOfOrderSamples, droppedStages;
        private boolean active = true;
        private Trace(String kind, long down, long received, float density) {
            this.kind = kind; downEventTimeMillis = down; receivedUptimeMillis = received; this.density = density;
        }
        public void add(Sample sample) {
            synchronized (LOCK) {
                if (!active || !recording || sample == null) return;
                for (int i = samples.size() - 1; i >= 0; i--) {
                    Sample previous = samples.get(i);
                    if (previous.eventTimeMillis < sample.eventTimeMillis) break;
                    if (previous.samePoint(sample)) { duplicateSamples++; return; }
                }
                if (sample.eventTimeMillis < downEventTimeMillis || (!samples.isEmpty()
                        && sample.eventTimeMillis < samples.get(samples.size() - 1).eventTimeMillis)) {
                    outOfOrderSamples++; return;
                }
                if (totalSamples == MAX_TOTAL_SAMPLES) { droppedTotalSamples++; droppedSamples++; return; }
                if (samples.size() == MAX_SAMPLES) { droppedSamples++; return; }
                samples.add(sample); totalSamples++;
            }
        }
        public void mark(String stage, long uptimeMillis) {
            synchronized (LOCK) {
                if (!active || !recording || stages.containsKey(stage)) return;
                if (!validLabel(stage) || uptimeMillis < downEventTimeMillis || stages.size() == MAX_STAGES) {
                    droppedStages++; return;
                }
                stages.put(stage, uptimeMillis);
            }
        }
        public void finish(String outcome, long uptimeMillis) {
            synchronized (LOCK) {
                if (!active || !recording) return;
                if (!validLabel(outcome) || uptimeMillis < downEventTimeMillis)
                    throw new IllegalArgumentException("Invalid measurement outcome or clock");
                this.outcome = outcome; finishedUptimeMillis = uptimeMillis; active = false;
            }
        }
    }

    public static final class Session {
        public final boolean recording;
        public final List<TraceSnapshot> traces;
        public final int totalSamples;
        public final long droppedTraces, droppedTotalSamples;
        private Session(boolean recording, List<TraceSnapshot> traces) {
            this.recording = recording; this.traces = Collections.unmodifiableList(traces);
            totalSamples = InteractionRecorder.totalSamples; droppedTraces = InteractionRecorder.droppedTraces;
            droppedTotalSamples = InteractionRecorder.droppedTotalSamples;
        }
    }
    public static final class TraceSnapshot {
        public final String kind, outcome;
        public final long downEventTimeMillis, receivedUptimeMillis, finishedUptimeMillis;
        public final float density;
        public final List<Sample> samples;
        public final Map<String, Long> stages;
        public final long droppedSamples, duplicateSamples, outOfOrderSamples, droppedStages;
        private TraceSnapshot(Trace trace) {
            kind = trace.kind; outcome = trace.outcome; downEventTimeMillis = trace.downEventTimeMillis;
            receivedUptimeMillis = trace.receivedUptimeMillis; finishedUptimeMillis = trace.finishedUptimeMillis;
            density = trace.density; samples = Collections.unmodifiableList(new ArrayList<>(trace.samples));
            stages = Collections.unmodifiableMap(new LinkedHashMap<>(trace.stages));
            droppedSamples = trace.droppedSamples; duplicateSamples = trace.duplicateSamples;
            outOfOrderSamples = trace.outOfOrderSamples; droppedStages = trace.droppedStages;
        }
    }
    private static boolean validLabel(String label) { return label != null && !label.isEmpty() && label.length() <= MAX_LABEL_LENGTH; }
}
