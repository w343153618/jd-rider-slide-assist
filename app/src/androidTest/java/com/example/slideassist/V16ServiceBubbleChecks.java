package com.example.slideassist;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Native service-window/controller checks using direct synthetic events, not human timing. */
public final class V16ServiceBubbleChecks {
    public static void run(Instrumentation i, Bundle result) throws Exception {
        check("com.example.slideassist.fixture".equals(BuildConfig.TARGET_PACKAGE), "isolated fixture build required");
        for (int n = 0; n < 600 && !AssistService.isConnected(); n++) SystemClock.sleep(50);
        check(AssistService.isConnected(), "fixture accessibility service did not connect");
        Object service = field(null, AssistService.class, "instance");
        Intent launch = i.getTargetContext().getPackageManager().getLaunchIntentForPackage(BuildConfig.TARGET_PACKAGE);
        check(launch != null, "independent receiver not installed");
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); i.getTargetContext().startActivity(launch);
        i.waitForIdleSync();
        onMain(i, AssistService::stopAssist);
        StatusBubbleView[] bubble = {null};
        for (int n = 0; n < 100 && bubble[0] == null; n++) {
            onMain(i, () -> bubble[0] = (StatusBubbleView) field(service, AssistService.class, "statusBubble"));
            if (bubble[0] == null) SystemClock.sleep(50);
        }
        check(bubble[0] != null, "target-foreground status window missing");
        SharedPreferences preferences = i.getTargetContext().getSharedPreferences("assist_settings", 0);
        boolean hadOpacity = preferences.contains(BubbleSettings.OPACITY_PREF);
        int originalOpacity = preferences.getInt(BubbleSettings.OPACITY_PREF, 10);
        int[] oldGeometry = new int[3];
        boolean[] oldInitialized = {false};
        onMain(i, () -> {
            oldGeometry[0] = (int) field(service, AssistService.class, "x");
            oldGeometry[1] = (int) field(service, AssistService.class, "y");
            oldGeometry[2] = (int) field(service, AssistService.class, "width");
            oldInitialized[0] = (boolean) field(service, AssistService.class, "geometryInitialized");
        });
        try {
            for (int opacity : new int[]{10, 55, 100}) {
                preferences.edit().putInt(BubbleSettings.OPACITY_PREF, opacity).commit();
                onMain(i, () -> {
                    invoke(service, "updateStatusBubble");
                    StatusBubbleView v = status(service);
                    WindowManager.LayoutParams lp = bubbleLayout(service);
                    float density = v.getResources().getDisplayMetrics().density;
                    check(lp.width == Math.round(28 * density) && lp.height == Math.round(28 * density),
                            "real status window must measure28x28dp");
                    check(Math.abs(v.getAlpha() - opacity / 100f) < .001f, "live service must apply persisted opacity");
                });
            }
            onMain(i, () -> check(!AssistService.isAssistArmed(), "test starts with assistance off"));
            tap(i, service); check(!AssistService.isAssistArmed(), "one status tap cannot arm");
            SystemClock.sleep(60); tap(i, service); check(!AssistService.isAssistArmed(), "two status taps cannot arm");
            SystemClock.sleep(60); tap(i, service); check(AssistService.isAssistArmed(), "third status tap must arm");
            i.waitForIdleSync();
            SystemClock.sleep(1250); i.waitForIdleSync();
            onMain(i, () -> {
                StatusBubbleView v = status(service); long elapsed = v.getElapsedActiveMillis();
                String shown = v.getText().toString();
                check(elapsed >= 1000 && !"00:00".equals(shown)
                                && shown.matches("(?:\\d{2}:\\d{2}|\\d+:\\d{2}:\\d{2})"),
                        "attached service must visibly count elapsed active time");
                preferences.edit().putInt(BubbleSettings.OPACITY_PREF, 55).commit();
                invoke(service, "updateStatusBubble");
                check(v.getElapsedActiveMillis() >= elapsed && Math.abs(v.getAlpha() - .55f) < .001f,
                        "same-ON service polling and opacity update must not restart the timer");
            });
            String statusBeforeMovement = AssistService.getLastGestureStatus();
            int[] tokenBeforeMovement = {-1};
            onMain(i, () -> tokenBeforeMovement[0] = (int) field(service, AssistService.class, "lastGestureToken"));
            SystemClock.sleep(60); tap(i, service);
            SystemClock.sleep(60); tap(i, service);
            check(AssistService.isAssistArmed(), "two pending taps before drag must not switch off");
            onMain(i, () -> {
                int[] screen = (int[]) invoke(service, "screenSize");
                drag(service, screen[0] / 4, screen[1] / 2, true);
                check(bubbleLayout(service).x == 0, "left-half release must dock at physical left");
                drag(service, screen[0] * 3 / 4, screen[1] / 2, true);
                WindowManager.LayoutParams lp = bubbleLayout(service);
                check(lp.x == screen[0] - lp.width, "right-half release must dock at physical right");
                drag(service, screen[0] / 4, screen[1] / 2, true);

                // A strip on the right cannot displace a left-docked control vertically.
                WindowManager.LayoutParams guide = (WindowManager.LayoutParams) field(service, AssistService.class, "layout");
                int stripTop = guide.y + Math.round(38 * status(service).getResources().getDisplayMetrics().density);
                int y = stripTop + 3;
                set(service, "bubbleY", y); lp = bubbleLayout(service); lp.y = y;
                WindowManager manager = (WindowManager) field(service, AssistService.class, "windows");
                manager.updateViewLayout(status(service), lp);
                set(service, "width", screen[0] / 2); set(service, "x", screen[0] / 2);
                guide.x = screen[0] / 2; guide.width = screen[0] / 2;
                manager.updateViewLayout((View) field(service, AssistService.class, "overlay"), guide);
                invoke(service, "avoidBubbleOverlap");
                check(bubbleLayout(service).y == y, "left control must ignore horizontally disjoint strip");
                guide.x = 0; guide.width = screen[0]; set(service, "x", 0); set(service, "width", screen[0]);
                manager.updateViewLayout((View) field(service, AssistService.class, "overlay"), guide);
                invoke(service, "avoidBubbleOverlap");
                lp = bubbleLayout(service);
                int stripBottom = stripTop + (int) field(service, AssistService.class, "areaHeight");
                check(lp.y + lp.height <= stripTop || lp.y >= stripBottom, "intersecting strip must be avoided vertically");

                check(statusBeforeMovement.equals(AssistService.getLastGestureStatus())
                                && tokenBeforeMovement[0] == (int) field(service, AssistService.class, "lastGestureToken")
                                && !(boolean) field(service, AssistService.class, "injecting")
                                && !(boolean) field(service, AssistService.class, "gestureInFlight"),
                        "status operations must not dispatch order gestures");
            });
            tap(i, service); check(AssistService.isAssistArmed(), "drag must clear the earlier two pending taps");
            onMain(i, () -> {
                StatusBubbleView v = status(service); WindowManager.LayoutParams lp = bubbleLayout(service);
                long down = SystemClock.uptimeMillis();
                event(v, down, MotionEvent.ACTION_DOWN, lp.x + lp.width / 2f, lp.y + lp.height / 2f);
                event(v, down, MotionEvent.ACTION_CANCEL, lp.x + lp.width / 2f, lp.y + lp.height / 2f);
                check(AssistService.isAssistArmed(), "cancel must not toggle assistance");
            });
            SystemClock.sleep(60); tap(i, service); check(AssistService.isAssistArmed(), "one fresh tap after cancel must not toggle");
            onMain(i, () -> {
                StatusBubbleView v = status(service); WindowManager.LayoutParams lp = bubbleLayout(service);
                long down = SystemClock.uptimeMillis(); float x = lp.x + lp.width / 2f, y = lp.y + lp.height / 2f;
                event(v, down, MotionEvent.ACTION_DOWN, x, y);
                multiEvent(v, down, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), x, y);
                multiEvent(v, down, MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), x, y);
                event(v, down, MotionEvent.ACTION_UP, x, y);
                check(AssistService.isAssistArmed(), "multiple pointers must not toggle assistance");
            });
            SystemClock.sleep(60); tap(i, service); check(AssistService.isAssistArmed(), "multi-pointer sequence must clear prior tap");
            SystemClock.sleep(60); tap(i, service); check(AssistService.isAssistArmed(), "second fresh tap must not toggle");
            SystemClock.sleep(60); tap(i, service); check(!AssistService.isAssistArmed(), "third fresh tap must switch off");
            onMain(i, () -> check(statusBeforeMovement.equals(AssistService.getLastGestureStatus())
                            && tokenBeforeMovement[0] == (int) field(service, AssistService.class, "lastGestureToken")
                            && !(boolean) field(service, AssistService.class, "injecting")
                            && !(boolean) field(service, AssistService.class, "gestureInFlight"),
                    "all control operations must finish without a new order gesture"));
            result.putString("result", "PASS native service bubble: live10/55/100percent preferences,28x28dp window,visibleelapsedcounterpreservedbypolling/opacity,left/right docking,drag freeze,real horizontal overlap bounds,cancel/multiplepointers/no1or2/triple toggles,no order gesture dispatch");
        } finally {
            onMain(i, () -> {
                AssistService.stopAssist(); set(service, "x", oldGeometry[0]);
                set(service, "y", oldGeometry[1]); set(service, "width", oldGeometry[2]);
                set(service, "geometryInitialized", oldInitialized[0]);
            });
            SharedPreferences.Editor restore = preferences.edit();
            if (hadOpacity) restore.putInt(BubbleSettings.OPACITY_PREF, originalOpacity);
            else restore.remove(BubbleSettings.OPACITY_PREF);
            restore.commit();
        }
    }
    private static void drag(Object service, int destinationCenterX, int destinationCenterY, boolean checkFrozen) throws Exception {
        StatusBubbleView v = status(service); WindowManager.LayoutParams lp = bubbleLayout(service);
        float startX = lp.x + lp.width / 2f, startY = lp.y + lp.height / 2f;
        long down = SystemClock.uptimeMillis(); event(v, down, MotionEvent.ACTION_DOWN, startX, startY);
        event(v, down, MotionEvent.ACTION_MOVE, destinationCenterX, destinationCenterY);
        int x = lp.x, y = lp.y;
        if (checkFrozen) {
            invoke(service, "updateStatusBubble");
            check(lp.x == x && lp.y == y, "foreground polling must not override active drag");
        }
        event(v, down, MotionEvent.ACTION_UP, destinationCenterX, destinationCenterY);
        check(AssistService.isAssistArmed(), "drag must preserve assistance state");
    }
    private static void tap(Instrumentation i, Object service) {
        onMain(i, () -> {
            StatusBubbleView v = status(service); WindowManager.LayoutParams lp = bubbleLayout(service);
            long down = SystemClock.uptimeMillis(); float x = lp.x + lp.width / 2f, y = lp.y + lp.height / 2f;
            event(v, down, MotionEvent.ACTION_DOWN, x, y); event(v, down, MotionEvent.ACTION_UP, x, y);
        }); i.waitForIdleSync();
    }
    private static void event(View view, long down, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try { view.dispatchTouchEvent(event); } finally { event.recycle(); }
    }
    private static void multiEvent(View view, long down, int action, float x, float y) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[2];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[2];
        for (int n = 0; n < 2; n++) {
            properties[n] = new MotionEvent.PointerProperties(); properties[n].id = n;
            properties[n].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[n] = new MotionEvent.PointerCoords(); coords[n].x = x + n; coords[n].y = y;
            coords[n].pressure = 1; coords[n].size = 1;
        }
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 2, properties, coords,
                0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try { view.dispatchTouchEvent(event); } finally { event.recycle(); }
    }
    private static StatusBubbleView status(Object service) throws Exception { return (StatusBubbleView) field(service, AssistService.class, "statusBubble"); }
    private static WindowManager.LayoutParams bubbleLayout(Object service) throws Exception { return (WindowManager.LayoutParams) field(service, AssistService.class, "bubbleLayout"); }
    private static Object field(Object value, Class<?> type, String name) throws Exception { Field f = type.getDeclaredField(name); f.setAccessible(true); return f.get(value); }
    private static void set(Object value, String name, Object data) throws Exception { Field f = AssistService.class.getDeclaredField(name); f.setAccessible(true); f.set(value, data); }
    private static Object invoke(Object value, String name) throws Exception { Method m = AssistService.class.getDeclaredMethod(name); m.setAccessible(true); return m.invoke(value); }
    private interface Checked { void run() throws Exception; }
    private static void onMain(Instrumentation i, Checked action) {
        Throwable[] failure = {null}; i.runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure[0] = error; } });
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
