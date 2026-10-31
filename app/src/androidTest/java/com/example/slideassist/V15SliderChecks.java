package com.example.slideassist;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/** Generated input checks the local sliders; these timings are not human benchmarks. */
public final class V15SliderChecks {
    public static void run(Instrumentation i, Bundle result) throws Exception {
        InteractionRecorder.stop(); InteractionRecorder.clear();
        Activity a = i.startActivitySync(new Intent(i.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        i.waitForIdleSync();
        ManualSpeedSliderView[] manual = new ManualSpeedSliderView[1];
        DemoSliderView[] demo = new DemoSliderView[1];
        FrameLayout[] host = new FrameLayout[1];
        i.runOnMainSync(() -> {
            host[0] = new FrameLayout(a);
            a.setContentView(host[0]);
            manual[0] = new ManualSpeedSliderView(a);
            demo[0] = new DemoSliderView(a);
            demo[0].setSwipeDurationMillis(100);
            FrameLayout.LayoutParams top = new FrameLayout.LayoutParams(900, 180);
            top.topMargin = 100;
            host[0].addView(manual[0], top);
            FrameLayout.LayoutParams bottom = new FrameLayout.LayoutParams(900, 180);
            bottom.topMargin = 400;
            host[0].addView(demo[0], bottom);
        });
        i.waitForIdleSync();
        long down = SystemClock.uptimeMillis();
        event(i, manual[0], down, MotionEvent.ACTION_DOWN, 45, 85);
        event(i, manual[0], down, MotionEvent.ACTION_MOVE, 850, 90);
        event(i, manual[0], down, MotionEvent.ACTION_UP, 850, 90);
        check(!ready(i, manual[0]), "manual result must remain briefly visible");
        SystemClock.sleep(120);
        check(!ready(i, manual[0]), "manual reset must not precede 200ms");
        SystemClock.sleep(160); i.waitForIdleSync();
        check(ready(i, manual[0]), "manual slider must reset without a reset button");
        i.runOnMainSync(() -> {
            ManualSpeedSliderView.AttemptInfo attempt = manual[0].getLastAttempt();
            check("success".equals(attempt.outcome) && attempt.validStart,
                    "automatic reset must retain successful attempt");
            check(attempt.firstMoveTimeMillis >= attempt.downTimeMillis
                    && attempt.targetTimeMillis >= attempt.firstMoveTimeMillis
                    && attempt.finishedTimeMillis >= attempt.targetTimeMillis,
                    "manual timing must exist without raw recording");
            check(attempt.trace == null && manual[0].getAttemptCount() == 1,
                    "opt-out attempt must have no raw trace and one result");
        });

        // Holding a finger after assisted completion must not re-arm the same physical sequence.
        down = SystemClock.uptimeMillis();
        event(i, demo[0], down, MotionEvent.ACTION_DOWN, 45, 85);
        SystemClock.sleep(420); i.waitForIdleSync();
        check(!ready(i, demo[0]), "held finger must postpone assisted reset");
        event(i, demo[0], down, MotionEvent.ACTION_UP, 45, 85);
        SystemClock.sleep(280); i.waitForIdleSync();
        check(ready(i, demo[0]), "assisted slider must reset after release plus 200ms");
        check(counter(i, demo[0], "getCompletionCount") == 1, "reset must preserve completion count");
        down = SystemClock.uptimeMillis();
        event(i, demo[0], down, MotionEvent.ACTION_DOWN, 45, 85);
        event(i, demo[0], down, MotionEvent.ACTION_UP, 45, 85);
        SystemClock.sleep(420); i.waitForIdleSync();
        check(ready(i, demo[0]), "second assisted attempt must reset independently");
        check(counter(i, demo[0], "getCompletionCount") == 2, "second physical trigger must permit another attempt");
        i.runOnMainSync(() -> check(demo[0].getLastAssistedPath() != null, "automatic reset must retain last measured path"));
        check(InteractionRecorder.snapshot().traces.isEmpty(), "automatic reset must not enable recording");

        InteractionRecorder.start();
        int[] starts = {0}, finishes = {0};
        i.runOnMainSync(() -> {
            manual[0].setRecordingKind("reaction_manual");
            manual[0].setAttemptListener(new ManualSpeedSliderView.AttemptListener() {
                @Override public void onStarted(long time, boolean valid) { starts[0]++; }
                @Override public void onFinished(ManualSpeedSliderView.AttemptInfo info) { finishes[0]++; }
            });
        });
        down = SystemClock.uptimeMillis();
        event(i, manual[0], down, MotionEvent.ACTION_DOWN, 45, 85);
        SystemClock.sleep(15);
        event(i, manual[0], down, MotionEvent.ACTION_MOVE, 400, 95);
        event(i, manual[0], down, MotionEvent.ACTION_UP, 400, 95);
        SystemClock.sleep(280); i.waitForIdleSync();
        check(ready(i, manual[0]), "incomplete manual attempt must auto-reset");
        i.runOnMainSync(() -> {
            ManualSpeedSliderView.AttemptInfo attempt = manual[0].getLastAttempt();
            check("incomplete".equals(attempt.outcome) && attempt.targetTimeMillis == -1,
                    "incomplete attempt must not invent target timestamp");
            check(attempt.trace != null && "reaction_manual".equals(attempt.trace.kind),
                    "reaction callback must retain its separately classified raw trace");
        });
        // A second pointer invalidates the attempt; first-pointer release cannot re-arm under it.
        down = SystemClock.uptimeMillis();
        event(i, manual[0], down, MotionEvent.ACTION_DOWN, 45, 85);
        multiEvent(i, manual[0], down, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT));
        multiEvent(i, manual[0], down, MotionEvent.ACTION_POINTER_UP);
        SystemClock.sleep(280); i.waitForIdleSync();
        check(!ready(i, manual[0]), "remaining physical pointer must postpone manual reset");
        event(i, manual[0], down, MotionEvent.ACTION_UP, 90, 90, 1);
        SystemClock.sleep(280); i.waitForIdleSync();
        check(ready(i, manual[0]), "manual reset must follow final pointer release");
        i.runOnMainSync(() -> check("invalid_multitouch".equals(manual[0].getLastAttempt().outcome),
                "multi-pointer attempt must stay invalid after reset"));
        check(starts[0] == 2 && finishes[0] == 2, "one finished callback required per physical attempt");
        down = SystemClock.uptimeMillis();
        event(i, manual[0], down, MotionEvent.ACTION_DOWN, 45, 85);
        i.runOnMainSync(() -> host[0].removeView(manual[0]));
        SystemClock.sleep(280); i.waitForIdleSync();
        check(finishes[0] == 3 && starts[0] == 3, "detach must cancel active attempt only once");
        i.runOnMainSync(() -> {
            check("cancelled".equals(manual[0].getLastAttempt().outcome), "detach must seal cancelled result");
            check(manual[0].isReadyForAttempt(), "detached slider must have no retained pointer or reset callback");
        });
        check(ReportStats.of(InteractionRecorder.snapshot()).manualAttemptCount == 0,
                "reaction attempts must not count as standalone hand-speed scores");
        InteractionRecorder.stop(); InteractionRecorder.clear();
        InteractionRecorder.start();
        long fillTime = SystemClock.uptimeMillis();
        for (int n = 0; n < InteractionRecorder.MAX_TRACES; n++) {
            InteractionRecorder.Trace capped = InteractionRecorder.begin("reaction_manual", fillTime, fillTime, 1);
            capped.finish("incomplete", fillTime);
        }
        i.runOnMainSync(() -> host[0].addView(manual[0], new FrameLayout.LayoutParams(900, 180)));
        i.waitForIdleSync();
        down = SystemClock.uptimeMillis();
        event(i, manual[0], down, MotionEvent.ACTION_DOWN, 45, 85);
        SystemClock.sleep(15);
        event(i, manual[0], down, MotionEvent.ACTION_MOVE, 850, 90);
        event(i, manual[0], down, MotionEvent.ACTION_UP, 850, 90);
        SystemClock.sleep(280); i.waitForIdleSync();
        i.runOnMainSync(() -> {
            ManualSpeedSliderView.AttemptInfo attempt = manual[0].getLastAttempt();
            check("success".equals(attempt.outcome) && attempt.targetTimeMillis >= attempt.downTimeMillis,
                    "raw trace cap must not remove current logical attempt timestamps");
            check(attempt.trace == null, "trace cap must remain explicit rather than fabricate raw data");
        });
        check(InteractionRecorder.snapshot().droppedTraces == 1, "bounded recorder must report omitted trace");
        InteractionRecorder.stop(); InteractionRecorder.clear();
        i.runOnMainSync(a::finish); i.waitForIdleSync();
        result.putString("result", "PASS v1.5 slider reset: 200ms result hold, no reset button, held-finger and multi-pointer guards, repeated independent assistance, preserved counts/path/timestamps, opt-in raw reaction trace, incomplete/cancel/detach, separate hand-speed scores");
    }

    private static boolean ready(Instrumentation i, View view) {
        boolean[] answer = new boolean[1];
        i.runOnMainSync(() -> {
            try { answer[0] = (boolean) view.getClass().getMethod("isReadyForAttempt").invoke(view); }
            catch (NoSuchMethodException baseline) {
                // The old release has no readiness API; inspect its terminal state for the RED run.
                try {
                    java.lang.reflect.Field terminal = view.getClass().getDeclaredField(
                            view instanceof ManualSpeedSliderView ? "finished" : "completed");
                    terminal.setAccessible(true); answer[0] = !terminal.getBoolean(view);
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            }
            catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        return answer[0];
    }
    private static long counter(Instrumentation i, View view, String method) {
        long[] answer = new long[1];
        i.runOnMainSync(() -> {
            try { answer[0] = (long) view.getClass().getMethod(method).invoke(view); }
            catch (ReflectiveOperationException e) { throw new AssertionError("attempt counter missing", e); }
        });
        return answer[0];
    }
    private static void event(Instrumentation i, View view, long down, int action, float x, float y) {
        event(i, view, down, action, x, y, 0);
    }
    private static void event(Instrumentation i, View view, long down, int action, float x, float y, int pointerId) {
        i.runOnMainSync(() -> {
            MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
            properties.id = pointerId; properties.toolType = MotionEvent.TOOL_TYPE_FINGER;
            MotionEvent.PointerCoords coordinates = new MotionEvent.PointerCoords();
            coordinates.x = x; coordinates.y = y; coordinates.pressure = .5f;
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
                    new MotionEvent.PointerProperties[]{properties}, new MotionEvent.PointerCoords[]{coordinates},
                    0, 0, 1, 1, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0);
            view.dispatchTouchEvent(event); event.recycle();
        });
    }
    private static void multiEvent(Instrumentation i, View view, long down, int action) {
        i.runOnMainSync(() -> {
            MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[2];
            MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[2];
            for (int n = 0; n < 2; n++) {
                properties[n] = new MotionEvent.PointerProperties(); properties[n].id = n;
                properties[n].toolType = MotionEvent.TOOL_TYPE_FINGER;
                coordinates[n] = new MotionEvent.PointerCoords(); coordinates[n].x = 45 + 45 * n;
                coordinates[n].y = 85; coordinates[n].pressure = .5f;
            }
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 2,
                    properties, coordinates, 0, 0, 1, 1, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0);
            view.dispatchTouchEvent(event); event.recycle();
        });
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
