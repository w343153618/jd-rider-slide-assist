package com.example.slideassist;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.RadioButton;
import java.util.ArrayList;
import java.util.List;

/** Device-side integration checks using actual injected touchscreen events, no test libraries. */
public final class GestureInstrumentation extends Instrumentation {
    private final List<DemoSliderView> sliders = new ArrayList<>();
    private ScrollView scroll;
    private RadioButton gestureMode, releaseMode;
    private boolean serviceTest, emulatorHardware;

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); serviceTest = arguments.getString("service") != null; emulatorHardware = "hardware".equals(arguments.getString("service")); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (serviceTest) { testService(result); return; }
            getTargetContext().getSharedPreferences("assist_settings", 0).edit().remove("trigger_mode").commit();
            Intent intent = new Intent(getTargetContext(), MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            Activity activity = startActivitySync(intent);
            waitForIdleSync();
            runOnMainSync(() -> {
                View root = activity.getWindow().getDecorView();
                findViews(root);
            });
            check(sliders.size() >= 2 && scroll != null, "expected multiple demo orders");
            DemoSliderView one = sliders.get(0), two = sliders.get(1);
            select(one); outsideTap(one); pause(); check(!done(one), "touch outside slider must not complete");
            select(one); outsideDragIn(one); pause(); check(!done(one), "outside start dragged into slider must not complete");
            select(one); downHeld(one); check(done(one), "default mode must complete on down before release");
            reset(one);
            runOnMainSync(() -> { if (releaseMode == null) throw new AssertionError("release mode option missing"); releaseMode.performClick(); });
            waitForIdleSync();
            select(one); releaseHeld(one); pause(); check(done(one), "release mode must complete after up");
            reset(one); select(one); outsideDragIn(one); pause(); check(!done(one), "outside start must not trigger release mode");
            reset(one); select(one); releaseOutside(one); pause(); check(done(one), "release outside must complete when first down inside");
            reset(one); select(one); releaseFirstFinger(one); pause(); check(done(one), "release first finger must complete even if second finger stays down");
            reset(one);
            runOnMainSync(() -> { if (gestureMode == null) throw new AssertionError("gesture mode option missing"); gestureMode.performClick(); });
            waitForIdleSync();
            select(one); tap(one); pause(); check(!done(one), "gesture mode single tap must not complete");
            reset(one); select(one); doubleTap(one); pause(); check(done(one), "single-finger double tap");
            check(!done(two), "first order completion must not complete second");
            reset(one); select(one); twoFingerTap(one); pause(); check(done(one), "two-finger tap");
            reset(one); select(one); move(one); pause(); check(!done(one), "movement must not complete");
            reset(one); select(one); threeFingerTap(one); pause(); check(!done(one), "three-finger tap must not complete");
            select(two); doubleTap(two); pause(); check(done(two), "second order double tap");
            check(!done(one), "second order completion must not complete first");
            result.putString("result", "PASS: immediate on down while held, outside touch and drag, release on up not down, release outside, first finger release, gesture mode single tap, double tap, two-finger tap, movement, three-finger tap, independent orders");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable e) {
            result.putString("result", "FAIL: " + e);
            result.putString("stack", android.util.Log.getStackTraceString(e));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private Object serviceField(String name) throws Exception {
        java.lang.reflect.Field instance = AssistService.class.getDeclaredField("instance");
        instance.setAccessible(true); Object service = instance.get(null);
        if (service == null) return null;
        java.lang.reflect.Field field = AssistService.class.getDeclaredField(name);
        field.setAccessible(true); return field.get(service);
    }
    private boolean armed() throws Exception { return Boolean.TRUE.equals(serviceField("armed")); }
    private View awaitArea() throws Exception {
        for (int i=0; i<100; i++) {
            View v=(View) serviceField("touchArea");
            if (v != null && v.getWidth()>0 && v.getHeight()>0) return v;
            SystemClock.sleep(50);
        }
        throw new AssertionError("service overlay did not appear");
    }
    private void testService(Bundle result) throws Exception {
        for (int i=0; i<600 && !AssistService.isConnected(); i++) SystemClock.sleep(50);
        check(AssistService.isConnected(), "accessibility service is not connected");
        if (emulatorHardware) check("ranchu".equals(android.os.Build.HARDWARE) && android.os.Build.PRODUCT.startsWith("sdk_gphone"), "kernel touch tests are restricted to an emulator");
        check("com.example.slideassist.fixture".equals(BuildConfig.TARGET_PACKAGE), "service test must run with isolated testFixture build");
        Intent fixture = getTargetContext().getPackageManager().getLaunchIntentForPackage(BuildConfig.TARGET_PACKAGE);
        check(fixture != null, "install the separate fixture app first");
        fixture.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getTargetContext().startActivity(fixture);
        SystemClock.sleep(1200);
        serviceCase("release", true);
        serviceCase("immediate", true);
        serviceCase("immediate", false);
        serviceCase("gestures", false);
        runOnMainSync(AssistService::stopAssist);
        result.putString("result", "PASS service: immediate quick up, immediate held, release held then up, double tap; callback completed and fixture received DOWN/MOVE/UP");
        finish(Activity.RESULT_OK, result);
    }
    private void serviceCase(String mode, boolean hold) throws Exception {
        getTargetContext().getSharedPreferences("assist_settings", 0).edit().putString("trigger_mode", mode).commit();
        runOnMainSync(() -> {
            if (!AssistService.startAssist())
                throw new AssertionError("cannot arm service");
        });
        View area=awaitArea();
        android.app.UiAutomation automation = getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        java.lang.reflect.Field instance = AssistService.class.getDeclaredField("instance"); instance.setAccessible(true);
        AssistService service = (AssistService) instance.get(null);
        for (android.view.accessibility.AccessibilityWindowInfo window : service.getWindows()) {
            android.view.accessibility.AccessibilityNodeInfo windowRoot = window.getRoot();
            android.util.Log.i("SlideTestWindows", "type=" + window.getType() + " id=" + window.getId()
                    + " package=" + (windowRoot == null ? "null" : windowRoot.getPackageName()));
            if (windowRoot != null) windowRoot.recycle(); window.recycle();
        }
        int before=fixtureResult()[0];
        float[] c=new float[2];
        runOnMainSync(() -> { int[] p=new int[2]; area.getLocationOnScreen(p); c[0]=p[0]+area.getWidth()/2f; c[1]=p[1]+area.getHeight()/2f; });
        long t=SystemClock.uptimeMillis();
        if (emulatorHardware) sendHardware(c, true); else sendGlobal(t,t,MotionEvent.ACTION_DOWN,c, false);
        if (hold) {
            SystemClock.sleep(650);
            if (mode.equals("release")) check(armed(), "release mode triggered before up");
            else check(!armed(), "immediate mode did not finish while pointer held");
        } else SystemClock.sleep(50);
        if (emulatorHardware) sendHardware(c, false); else sendGlobal(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,c, mode.equals("immediate"));
        if ("gestures".equals(mode)) {
            SystemClock.sleep(35);
            long second = SystemClock.uptimeMillis();
            if (emulatorHardware) { sendHardware(c, true); sendHardware(c, false); }
            else { sendGlobal(second,second,MotionEvent.ACTION_DOWN,c,false); sendGlobal(second,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,c,false); }
        }
        for (int i=0; i<100 && armed(); i++) SystemClock.sleep(25);
        check("completed".equals(AssistService.getLastGestureStatus()), mode + " callback was " + AssistService.getLastGestureStatus());
        check(!armed(), mode+" service still armed after expected swipe");
        int[] result = fixtureResult();
        for (int i=0; i<40 && result[0] == before; i++) { SystemClock.sleep(25); result = fixtureResult(); }
        SystemClock.sleep(100); result = fixtureResult();
        check(result[0] == before + 1 && result[1] > 0 && result[2] > 100,
                "fixture did not receive exactly one complete new rightward swipe; before=" + before + " after=" + java.util.Arrays.toString(result));
    }

    private int[] fixtureResult() {
        android.view.accessibility.AccessibilityNodeInfo root = getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).getRootInActiveWindow();
        check(root != null, "fixture accessibility root missing");
        try {
            java.util.List<android.view.accessibility.AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("完成滑动总数");
            check(nodes != null && !nodes.isEmpty(), "fixture status not found");
            String value = nodes.get(0).getText().toString();
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("完成滑动总数 (\\d+)，最近移动 (\\d+) 次，水平位移 (-?\\d+) 像素").matcher(value);
            check(matcher.find(), "could not parse fixture result: " + value);
            return new int[]{ Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)) };
        } finally { root.recycle(); }
    }

    private void sendHardware(float[] point, boolean down) throws Exception {
        android.util.DisplayMetrics metrics = getTargetContext().getResources().getDisplayMetrics();
        int rawX = Math.max(0, Math.min(32767, Math.round(point[0] * 32767f / metrics.widthPixels)));
        int rawY = Math.max(0, Math.min(32767, Math.round(point[1] * 32767f / metrics.heightPixels)));
        android.util.Log.i("SlideHardware", "point=" + point[0] + "," + point[1] + " display=" + metrics.widthPixels + "x" + metrics.heightPixels + " raw=" + rawX + "," + rawY + " down=" + down);
        String device = "/dev/input/event1";
        if (down) {
            runHardware("3 47 0"); runHardware("3 57 123");
            runHardware("3 53 " + rawX); runHardware("3 54 " + rawY); runHardware("3 58 512");
        } else runHardware("3 57 -1");
        runHardware("0 0 0");
    }
    private void runHardware(String values) throws Exception {
        android.os.ParcelFileDescriptor fd = getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                .executeShellCommand("su 0 sendevent /dev/input/event1 " + values);
        try (java.io.InputStream in = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] bytes = new byte[256]; int n; while ((n = in.read(bytes)) != -1) output.write(bytes, 0, n);
            check(output.size() == 0, "hardware command failed: " + output.toString("UTF-8"));
        }
    }

    private void sendGlobal(long down, long when, int action, float[] point, boolean allowCancelledStream) {
        MotionEvent event=MotionEvent.obtain(down,when,action,point[0],point[1],0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            boolean sent = getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                    .injectInputEvent(event, true);
            check(sent || allowCancelledStream, "global event injection failed");
        } finally { event.recycle(); }
    }

    private void findViews(View v) {
        if (v instanceof ScrollView) scroll = (ScrollView) v;
        if (v instanceof RadioButton && ((RadioButton) v).getText().toString().contains("抬起手指")) releaseMode = (RadioButton) v;
        if (v instanceof RadioButton && ((RadioButton) v).getText().toString().contains("单指双击")) gestureMode = (RadioButton) v;
        if (v instanceof DemoSliderView) sliders.add((DemoSliderView) v);
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) findViews(group.getChildAt(i));
        }
    }
    private void select(DemoSliderView v) {
        runOnMainSync(() -> {
            Rect r = new Rect(); v.getDrawingRect(r);
            scroll.offsetDescendantRectToMyCoords(v, r);
            scroll.scrollTo(0, Math.max(0, r.top - scroll.getHeight() / 2));
        });
        waitForIdleSync(); SystemClock.sleep(150);
    }
    private void reset(DemoSliderView v) { runOnMainSync(v::reset); waitForIdleSync(); }
    private boolean done(DemoSliderView v) {
        final boolean[] result = {false};
        runOnMainSync(() -> result[0] = v.getContentDescription().toString().contains("模拟抢单成功"));
        return result[0];
    }
    private void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private void pause() { SystemClock.sleep(500); waitForIdleSync(); }
    private float[] center(DemoSliderView v) {
        float[] c = new float[2];
        runOnMainSync(() -> { int[] p = new int[2]; v.getLocationOnScreen(p); c[0] = p[0] + v.getWidth()/2f; c[1] = p[1] + v.getHeight()/2f; });
        return c;
    }
    private void send(long down, long when, int action, float[][] points) {
        int[] ids = new int[points.length]; for (int i = 0; i < ids.length; i++) ids[i] = i;
        sendWithIds(down, when, action, points, ids);
    }
    private void sendWithIds(long down, long when, int action, float[][] points, int[] ids) {
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[points.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[points.length];
        for (int i = 0; i < points.length; i++) {
            props[i] = new MotionEvent.PointerProperties(); props[i].id = ids[i]; props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords(); coords[i].x = points[i][0]; coords[i].y = points[i][1]; coords[i].pressure = 1; coords[i].size = 1;
        }
        MotionEvent event = MotionEvent.obtain(down, when, action, points.length, props, coords, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try { sendPointerSync(event); } finally { event.recycle(); }
    }
    private void tap(DemoSliderView v) {
        float[] c = center(v); long t = SystemClock.uptimeMillis();
        send(t,t,MotionEvent.ACTION_DOWN,new float[][]{c}); SystemClock.sleep(50);
        send(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,new float[][]{c});
    }
    private void outsideTap(DemoSliderView v) {
        float[] c = center(v); float[] p = { c[0] - v.getWidth() / 2f - 10, c[1] }; long t = SystemClock.uptimeMillis();
        send(t,t,MotionEvent.ACTION_DOWN,new float[][]{p}); send(t,t+50,MotionEvent.ACTION_UP,new float[][]{p});
    }
    private void downHeld(DemoSliderView v) {
        float[] c = center(v); long t = SystemClock.uptimeMillis();
        send(t,t,MotionEvent.ACTION_DOWN,new float[][]{c});
        pause();
        check(done(v), "default mode should finish while pointer is still down");
        send(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,new float[][]{c});
    }
    private void outsideDragIn(DemoSliderView v) {
        float[] c = center(v), p = { c[0] - v.getWidth() / 2f - 10, c[1] }; long t = SystemClock.uptimeMillis();
        send(t,t,MotionEvent.ACTION_DOWN,new float[][]{p});
        send(t,t+80,MotionEvent.ACTION_MOVE,new float[][]{c});
        send(t,t+140,MotionEvent.ACTION_UP,new float[][]{c});
    }
    private void releaseHeld(DemoSliderView v) {
        float[] c = center(v); long t = SystemClock.uptimeMillis();
        send(t,t,MotionEvent.ACTION_DOWN,new float[][]{c});
        SystemClock.sleep(400); waitForIdleSync(); check(!done(v), "release mode must not complete while held");
        send(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,new float[][]{c});
    }
    private void releaseOutside(DemoSliderView v) {
        float[] c = center(v), p = { c[0] - v.getWidth() / 2f - 10, c[1] }; long t = SystemClock.uptimeMillis();
        send(t,t,MotionEvent.ACTION_DOWN,new float[][]{c});
        send(t,t+50,MotionEvent.ACTION_MOVE,new float[][]{p});
        send(t,t+90,MotionEvent.ACTION_UP,new float[][]{p});
    }
    private void releaseFirstFinger(DemoSliderView v) {
        float[] c=center(v), a={c[0]-20,c[1]}, b={c[0]+20,c[1]}; long t=SystemClock.uptimeMillis();
        send(t,t,MotionEvent.ACTION_DOWN,new float[][]{a});
        send(t,t+20,MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[][]{a,b});
        send(t,t+50,MotionEvent.ACTION_POINTER_UP,new float[][]{a,b});
        SystemClock.sleep(400); waitForIdleSync(); check(done(v), "first pointer release must trigger while second stays down");
        sendWithIds(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,new float[][]{b},new int[]{1});
    }
    private void doubleTap(DemoSliderView v) { tap(v); SystemClock.sleep(80); tap(v); }
    private void twoFingerTap(DemoSliderView v) {
        float[] c = center(v), a = {c[0]-20,c[1]}, b = {c[0]+20,c[1]};
        long t = SystemClock.uptimeMillis();
        send(t,t,MotionEvent.ACTION_DOWN,new float[][]{a});
        send(t,t+20,MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[][]{a,b});
        send(t,t+50,MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[][]{a,b});
        send(t,t+70,MotionEvent.ACTION_UP,new float[][]{a});
    }
    private void move(DemoSliderView v) {
        float[] a = center(v), b = {a[0]+80,a[1]}; long t = SystemClock.uptimeMillis();
        send(t,t,MotionEvent.ACTION_DOWN,new float[][]{a});
        send(t,t+50,MotionEvent.ACTION_MOVE,new float[][]{b});
        send(t,t+100,MotionEvent.ACTION_UP,new float[][]{b});
    }
    private void threeFingerTap(DemoSliderView v) {
        float[] c = center(v), a={c[0]-30,c[1]}, b={c[0],c[1]}, d={c[0]+30,c[1]}; long t=SystemClock.uptimeMillis();
        send(t,t,MotionEvent.ACTION_DOWN,new float[][]{a});
        send(t,t+10,MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[][]{a,b});
        send(t,t+20,MotionEvent.ACTION_POINTER_DOWN | (2 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[][]{a,b,d});
        send(t,t+30,MotionEvent.ACTION_POINTER_UP | (2 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[][]{a,b,d});
        send(t,t+40,MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[][]{a,b});
        send(t,t+50,MotionEvent.ACTION_UP,new float[][]{a});
    }
}
