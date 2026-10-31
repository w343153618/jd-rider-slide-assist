package com.example.slideassist;

import android.util.JsonWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;

/** Streaming local reaction reports: generated orders, explicit clocks and unmodified raw input. */
public final class ReactionExport {
    private ReactionExport() { }
    public static void writeJson(ReactionSession.Snapshot session, Writer output) throws IOException {
        JsonWriter w = new JsonWriter(output); w.setIndent("  ");
        w.beginObject(); w.name("schema").value("reaction-report-1");
        w.name("clock").value("Android uptimeMillis; presentation is a UI frame callback, not physical pixels");
        w.name("bodyCurrent").nullValue();
        w.name("limitations").value("Local synthetic practice only; visibility is layout intersection, not gaze. pressure/size/axes are raw device estimates. Sample gaps are not hardware scan rate; receipt lag is not hardware/network latency. No server outcome is measured.");
        w.name("maxRounds").value(ReactionSession.MAX_ROUNDS); w.name("maxAttempts").value(ReactionSession.MAX_ATTEMPTS);
        w.name("maxSamplesPerTrace").value(InteractionRecorder.MAX_SAMPLES); w.name("maxTotalSamples").value(InteractionRecorder.MAX_TOTAL_SAMPLES);
        w.name("droppedRounds").value(session.droppedRounds); w.name("droppedAttempts").value(session.droppedAttempts);
        w.name("totalAttempts").value(session.totalAttempts);
        ReactionStats stats = ReactionStats.of(session);
        w.name("statistics").beginObject();
        w.name("presentedRounds").value(stats.presentedRounds); w.name("successfulRounds").value(stats.successfulRounds);
        value(w,"successRate",stats.successRate); value(w,"errorRate",stats.errorRate);
        distribution(w,"search",stats.search); distribution(w,"drag",stats.drag); distribution(w,"total",stats.total);
        distribution(w,"initiation",stats.initiation); distribution(w,"hold",stats.hold);
        value(w,"first5TotalMean",stats.first5TotalMean); value(w,"last5TotalMean",stats.last5TotalMean);
        w.name("comparisonWindowCount").value(stats.comparisonWindowCount); w.endObject();
        w.name("rounds").beginArray();
        for (int n = 0; n < session.rounds.size(); n++) {
            ReactionSession.Round r = session.rounds.get(n); ReactionOrderGenerator.Batch b = r.batch;
            w.beginObject(); w.name("round").value(n + 1); w.name("seed").value(b.seed);
            w.name("targetIndex").value(b.targetIndex); w.name("targetField").value(b.targetField); w.name("targetOffset").value(b.targetOffset);
            w.name("orders").beginArray();
            for (ReactionOrderGenerator.Order o : b.orders) {
                w.beginObject(); w.name("title").value(o.title); w.name("address").value(o.address); w.name("note").value(o.note); w.endObject();
            }
            w.endArray(); w.name("status").value(r.status); w.name("closed").value(r.closed);
            clock(w,"presentedTime",r.presentedTime); clock(w,"firstValidDownTime",r.firstValidDownTime);
            clock(w,"firstCorrectDownTime",r.firstCorrectDownTime); clock(w,"targetVisibleTime",r.targetVisibleTime);
            clock(w,"finishedTime",r.finishedTime); clock(w,"firstScrollTime",r.firstScrollTime); clock(w,"lastScrollTime",r.lastScrollTime);
            value(w,"searchMillis",r.searchMillis()); value(w,"dragMillis",r.dragMillis()); value(w,"totalMillis",r.totalMillis());
            value(w,"initiationMillis",r.initiationMillis()); value(w,"holdMillis",r.holdMillis()); value(w,"visibleToDownMillis",r.visibleToDownMillis());
            w.name("scrollDistancePx").value(r.scrollDistancePx); w.name("maxScrollYPx").value(r.maxScrollYPx);
            w.name("scrollEvents").value(r.scrollEvents); w.name("directionChanges").value(r.directionChanges);
            w.name("wrongAttempts").value(r.wrongAttempts); w.name("invalidStarts").value(r.invalidStarts);
            w.name("cancelledAttempts").value(r.cancelledAttempts); w.name("retryCount").value(r.retryCount);
            w.name("attempts").beginArray();
            for (ReactionSession.Attempt a : r.attempts) {
                w.beginObject(); w.name("index").value(a.index); w.name("orderIndex").value(a.orderIndex);
                clock(w,"downTime",a.downTime); clock(w,"firstMoveTime",a.firstMoveTime); clock(w,"targetTime",a.targetTime); clock(w,"finishedTime",a.finishedTime);
                w.name("validStart").value(a.validStart); w.name("correctOrder").value(a.correctOrder); w.name("eligible").value(a.eligible);
                w.name("scored").value(a.scored); w.name("practice").value(a.practice); w.name("timingValid").value(a.timingValid); w.name("outcome").value(a.outcome);
                w.name("trace"); trace(w,a.trace); w.endObject();
            }
            w.endArray(); w.endObject();
        }
        w.endArray(); w.endObject(); w.flush();
    }
    private static void trace(JsonWriter w, InteractionRecorder.TraceSnapshot t) throws IOException {
        if (t == null) { w.nullValue(); return; }
        w.beginObject(); w.name("kind").value(t.kind); w.name("outcome").value(t.outcome); value(w,"density",t.density);
        clock(w,"downEventTimeMillis",t.downEventTimeMillis); clock(w,"receivedUptimeMillis",t.receivedUptimeMillis); clock(w,"finishedUptimeMillis",t.finishedUptimeMillis);
        w.name("droppedSamples").value(t.droppedSamples); w.name("duplicateSamples").value(t.duplicateSamples);
        w.name("outOfOrderSamples").value(t.outOfOrderSamples); w.name("droppedStages").value(t.droppedStages);
        w.name("stages").beginObject(); for (Map.Entry<String, Long> stage : t.stages.entrySet()) w.name(stage.getKey()).value(stage.getValue()); w.endObject();
        w.name("samples").beginArray();
        for (InteractionRecorder.Sample s : t.samples) {
            w.beginObject(); w.name("eventTimeMillis").value(s.eventTimeMillis); w.name("receivedUptimeMillis").value(s.receivedUptimeMillis);
            value(w,"x",s.x); value(w,"y",s.y); value(w,"pressure",s.pressure); value(w,"size",s.size);
            value(w,"touchMajor",s.touchMajor); value(w,"touchMinor",s.touchMinor); value(w,"orientation",s.orientation);
            w.name("toolType").value(s.toolType); w.name("pointerId").value(s.pointerId); w.name("action").value(s.action); w.endObject();
        }
        w.endArray(); w.endObject();
    }
    private static void distribution(JsonWriter w, String name, ReactionStats.Distribution d) throws IOException {
        w.name(name).beginObject(); w.name("count").value(d.count); value(w,"mean",d.mean); value(w,"median",d.median);
        value(w,"p95",d.p95); value(w,"min",d.min); value(w,"max",d.max); value(w,"stddev",d.stddev); w.endObject();
    }
    private static void clock(JsonWriter w, String name, long time) throws IOException {
        w.name(name); if (time < 0) w.nullValue(); else w.value(time);
    }
    private static void value(JsonWriter w, String name, double number) throws IOException {
        w.name(name); if (Double.isFinite(number)) w.value(number); else w.nullValue();
    }
    public static void writeCsv(ReactionSession.Snapshot session, Writer w) throws IOException {
        w.write("round,seed,targetIndex,targetField,targetOffset,status,presentedTime,firstValidDownTime,firstCorrectDownTime,targetVisibleTime,finishedTime,searchMillis,dragMillis,totalMillis,initiationMillis,holdMillis,visibleToDownMillis,attempts,wrongAttempts,invalidStarts,cancelledAttempts,retryCount,scrollDistancePx,maxScrollYPx,scrollEvents,directionChanges\r\n");
        for (int i = 0; i < session.rounds.size(); i++) {
            ReactionSession.Round r = session.rounds.get(i);
            w.write((i + 1) + "," + r.batch.seed + "," + r.batch.targetIndex + "," + r.batch.targetField + "," + r.batch.targetOffset + "," + r.status);
            for (long time : new long[]{r.presentedTime,r.firstValidDownTime,r.firstCorrectDownTime,r.targetVisibleTime,r.finishedTime})
                w.write("," + (time < 0 ? "" : Long.toString(time)));
            for (double n : new double[]{r.searchMillis(),r.dragMillis(),r.totalMillis(),r.initiationMillis(),r.holdMillis(),r.visibleToDownMillis()})
                w.write("," + (Double.isFinite(n) ? Double.toString(n) : ""));
            w.write("," + r.attempts.size() + "," + r.wrongAttempts + "," + r.invalidStarts + "," + r.cancelledAttempts + "," + r.retryCount
                    + "," + r.scrollDistancePx + "," + r.maxScrollYPx + "," + r.scrollEvents + "," + r.directionChanges + "\r\n");
        }
        w.flush();
    }
}
