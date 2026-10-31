package com.example.slideassist;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/** User-armed, foreground-package-limited gesture assistance. */
public class AssistService extends AccessibilityService {
    private static final String PREFS = "assist_settings";
    private static AssistService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final VolumeKeyChord volumeKeyChord = new VolumeKeyChord();
    private int chordStartTargetWindowId = -1;
    private WindowManager windows;
    private FrameLayout overlay;
    private View touchArea;
    private StatusBubbleView statusBubble;
    private WindowManager.LayoutParams bubbleLayout;
    private int bubbleY = -1;
    private boolean serviceStopping;
    private boolean gestureInFlight;
    private int gestureInFlightToken = -1;
    private WindowManager.LayoutParams layout;
    private final String target = TargetApp.PACKAGE_NAME;
    private volatile String lastGestureStatus = "idle";
    private int lastGestureToken;
    private boolean armed;
    private boolean seenTarget;
    private boolean injecting;
    private boolean gestureDispatched;
    private int generation;
    private int areaHeight;
    private int width;
    private int x;
    private int y;
    private boolean geometryInitialized;
    private int targetWindowId = -1;
    private final Runnable armTimeout = () -> {
        if (armed && !seenTarget) { stopInternal(false); toast("等待目标应用超时，辅助已关闭"); }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                volumeKeyChord.reset();
                stopInternal(false);
                removeStatusBubble();
            }
        }
    };

    public static boolean isConnected() { return instance != null; }

    static boolean isAssistArmed() { return instance != null && instance.armed; }

    /** Arms once, without acting until the fixed target is foreground and the user gestures. */
    public static boolean startAssist() {
        AssistService s = instance;
        if (s == null || s.gestureInFlight || s.serviceStopping) return false;
        s.stopInternal(false);
        s.lastGestureStatus = "idle";
        s.lastGestureToken = -1;
        if (!TargetApp.getStatus(s).launchable) return false;
        s.armed = true;
        s.seenTarget = false;
        s.updateStatusBubble();
        s.checkForeground();
        if (s.armed && !s.seenTarget) s.handler.postDelayed(s.armTimeout, 60000);
        return true;
    }

    /** Process-local diagnostics only: never includes window content or order information. */
    static String getLastGestureStatus() {
        AssistService s = instance;
        return s == null ? "idle" : s.lastGestureStatus;
    }

    public static boolean stopAssist() {
        if (instance == null) return false;
        instance.stopInternal(false);
        return true;
    }

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        volumeKeyChord.reset();
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenReceiver, filter);
        handler.post(foregroundCheck);
    }

    /** Observe without consuming: Android and the foreground app retain the key stream. */
    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;
        int action = event.getAction();
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
                volumeKeyChord.onKey(event.getKeyCode(), false, event.getRepeatCount(), true,
                        event.getEventTime(), event.getDeviceId());
            }
            return false;
        }
        boolean volume = event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN;
        if (volume && action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 && volumeKeyChord.isIdle()) {
            chordStartTargetWindowId = !armed && !injecting && isUnlockedAndInteractive() ? focusedTargetWindowId() : -1;
        }
        boolean chord = volumeKeyChord.onKey(event.getKeyCode(), action == KeyEvent.ACTION_DOWN,
                event.getRepeatCount(), event.isCanceled(), event.getEventTime(), event.getDeviceId());
        if (chord) toggleByVolumeChord();
        return false;
    }

    private void toggleByVolumeChord() {
        if (armed || injecting) {
            boolean wasDispatched = gestureDispatched;
            stopInternal(false);
            toast(wasDispatched ? "辅助已关闭；已开始的滑动可能继续，请查看订单" : "辅助已关闭");
            return;
        }
        if (!isUnlockedAndInteractive()) {
            toast("手机已锁定，无法开启辅助");
            return;
        }
        if (chordStartTargetWindowId < 0 || focusedTargetWindowId() != chordStartTargetWindowId) return;
        if (startAssist()) toast("辅助已开启，请先对齐抢单滑条");
        else toast("无法开启辅助，请检查目标应用和无障碍服务");
    }

    private boolean isUnlockedAndInteractive() {
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        return keyguard != null && power != null && power.isInteractive() &&
                !keyguard.isKeyguardLocked() && !keyguard.isDeviceLocked();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || serviceStopping) return;
        int type = event.getEventType();
        if (armed && type == AccessibilityEvent.TYPE_VIEW_SCROLLED && seenTarget && !gestureDispatched
                && event.getPackageName() != null && target.contentEquals(event.getPackageName())) {
            stopInternal(false);
            toast("页面已滚动，辅助已关闭，请重新对齐");
            return;
        }
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            handler.removeCallbacks(foregroundCheck);
            handler.postDelayed(foregroundCheck, 100);
        }
    }

    private final Runnable foregroundCheck = this::checkForeground;

    private int focusedTargetWindowId() {
        // Accessibility overlays can be active during touch without taking app focus.
        List<AccessibilityWindowInfo> list = getWindows();
        try {
            for (AccessibilityWindowInfo window : list) {
                if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION || !window.isFocused()) continue;
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) return -1;
                try {
                    CharSequence name = root.getPackageName();
                    return name != null && target.equals(name.toString()) ? window.getId() : -1;
                } finally { root.recycle(); }
            }
            return -1;
        } finally {
            for (AccessibilityWindowInfo window : list) window.recycle();
        }
    }


    private boolean targetIsForeground() {
        int id = isUnlockedAndInteractive() ? focusedTargetWindowId() : -1;
        return id >= 0 && (!seenTarget || id == targetWindowId);
    }

    private void checkForeground() {
        if (serviceStopping) return;
        int id = isUnlockedAndInteractive() ? focusedTargetWindowId() : -1;
        if (id < 0) {
            if (armed && seenTarget) {
                stopInternal(false);
                toast("目标页面已变化，辅助已关闭，请重新对齐");
            }
            removeStatusBubble();
            return;
        }
        if (armed) {
            if (seenTarget && id != targetWindowId) {
                stopInternal(false);
                toast("目标页面已变化，辅助已关闭，请重新对齐");
            } else {
                if (!seenTarget) {
                    seenTarget = true;
                    targetWindowId = id;
                    handler.removeCallbacks(armTimeout);
                }
                if (overlay == null && !injecting) showOverlay();
            }
        }
        updateStatusBubble();
    }

    private int dp(float value) { return (int) (getResources().getDisplayMetrics().density * value + .5f); }
    private GradientDrawable shape(int color, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setStroke(dp(2), stroke); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView label(String text, int size) {
        TextView v = new TextView(this);
        v.setText(text); v.setTextSize(size); v.setTextColor(Color.WHITE); v.setGravity(Gravity.CENTER);
        return v;
    }
    private int[] screenSize() {
        DisplayMetrics m = new DisplayMetrics();
        windows.getDefaultDisplay().getRealMetrics(m);
        return new int[] {m.widthPixels, m.heightPixels};
    }

    /** Shows a compact state control only while the selected application is foreground. */
    private void updateStatusBubble() {
        if (windows == null) return;
        if (serviceStopping || instance != this || injecting || gestureInFlight ||
                !isUnlockedAndInteractive() || focusedTargetWindowId() < 0) {
            removeStatusBubble();
            return;
        }
        if (statusBubble == null) showStatusBubble();
        if (statusBubble != null) {
            statusBubble.setActive(armed);
            avoidBubbleOverlap();
        }
    }

    private void showStatusBubble() {
        if (statusBubble != null || serviceStopping) return;
        int[] screen = screenSize();
        if (bubbleY < 0) bubbleY = screen[1] / 3;
        bubbleY = clampBubbleY(bubbleY);
        statusBubble = new StatusBubbleView(this);
        statusBubble.setActive(armed);
        bubbleLayout = new WindowManager.LayoutParams(dp(80), dp(48),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        bubbleLayout.gravity = Gravity.TOP | Gravity.RIGHT;
        bubbleLayout.x = 0;
        bubbleLayout.y = bubbleY;
        statusBubble.setOnClickListener(v -> toggleByStatusBubble());
        statusBubble.setOnTouchListener(new BubbleTouch());
        try { windows.addView(statusBubble, bubbleLayout); }
        catch (RuntimeException e) { statusBubble = null; bubbleLayout = null; }
    }

    private int clampBubbleY(int value) {
        int screenHeight = screenSize()[1];
        int top = Math.min(dp(30), Math.max(0, screenHeight - dp(48)));
        int bottom = Math.max(top, screenHeight - dp(48) - dp(72));
        return Math.max(top, Math.min(bottom, value));
    }

    private final class BubbleTouch implements View.OnTouchListener {
        private float downX, downY;
        private int originalY;
        private boolean moved;
        @Override public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX(); downY = event.getRawY();
                    originalY = bubbleY; moved = false; return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    moved = true; return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                    int slop = ViewConfiguration.get(AssistService.this).getScaledTouchSlop();
                    if (dx * dx + dy * dy > slop * slop) moved = true;
                    if (moved && statusBubble != null && bubbleLayout != null) {
                        bubbleY = clampBubbleY(originalY + Math.round(dy));
                        bubbleLayout.y = bubbleY;
                        try { windows.updateViewLayout(statusBubble, bubbleLayout); }
                        catch (RuntimeException e) { removeStatusBubble(); }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (moved) avoidBubbleOverlap(); else view.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return true;
            }
        }
    }

    private void toggleByStatusBubble() {
        if (armed || injecting) {
            boolean wasDispatched = gestureDispatched;
            stopInternal(false);
            toast(wasDispatched ? "辅助已关闭；已开始的滑动可能继续，请查看订单" : "辅助已关闭");
        } else if (!isUnlockedAndInteractive() || focusedTargetWindowId() < 0) {
            removeStatusBubble();
        } else if (startAssist()) {
            toast("辅助已开启，请先对齐抢单滑条");
        } else {
            toast("无法开启辅助，请检查目标应用和无障碍服务");
        }
    }

    /** Keep the edge control out of the active touch strip when possible. */
    private void avoidBubbleOverlap() {
        if (statusBubble == null || bubbleLayout == null || overlay == null || layout == null) return;
        int screenWidth = screenSize()[0];
        int bubbleLeft = screenWidth - bubbleLayout.width;
        int stripTop = layout.y + dp(38);
        int stripBottom = stripTop + areaHeight;
        if (layout.x + width <= bubbleLeft || bubbleY >= stripBottom || bubbleY + dp(48) <= stripTop) return;
        int above = clampBubbleY(stripTop - dp(48));
        int below = clampBubbleY(stripBottom);
        int destination = above + dp(48) <= stripTop ? above : below;
        if (destination == bubbleY) return;
        bubbleY = destination;
        bubbleLayout.y = bubbleY;
        try { windows.updateViewLayout(statusBubble, bubbleLayout); }
        catch (RuntimeException e) { removeStatusBubble(); }
    }

    private boolean removeStatusBubble() {
        if (statusBubble == null) return true;
        try { windows.removeViewImmediate(statusBubble); }
        catch (RuntimeException e) { if (statusBubble.isAttachedToWindow()) return false; }
        statusBubble = null;
        bubbleLayout = null;
        return true;
    }

    private void showOverlay() {
        if (!armed || overlay != null || !targetIsForeground()) return;
        int[] screen = screenSize();
        if (!geometryInitialized) {
            width = Math.min(screen[0] - dp(16), Math.max(dp(200), screen[0] * 9 / 10));
            areaHeight = dp(62);
            x = (screen[0] - width) / 2;
            y = Math.max(0, screen[1] / 2 - dp(50));
            geometryInitialized = true;
        }
        overlay = new FrameLayout(this);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        overlay.addView(column);
        LinearLayout header = new LinearLayout(this);
        header.setBackgroundColor(0xff7a4100);
        TextView move = label("按住移动 · 对齐抢单条", 13);
        header.addView(move, new LinearLayout.LayoutParams(0, dp(38), 1));
        TextView close = label("关闭 ×", 14);
        header.addView(close, new LinearLayout.LayoutParams(dp(66), dp(38)));
        column.addView(header);
        touchArea = new View(this);
        touchArea.setBackground(shape(0x448e5900, 0xffffa000, 5));
        String mode = getSharedPreferences(PREFS, MODE_PRIVATE).getString("trigger_mode", "immediate");
        touchArea.setContentDescription("gestures".equals(mode) ? "手势辅助区域：双击或双指单击"
                : "release".equals(mode) ? "手势辅助区域：按下后抬起滑动" : "手势辅助区域：按下立即滑动");
        column.addView(touchArea, new LinearLayout.LayoutParams(-1, areaHeight));
        TextView resize = label("↘ 按住调整宽度与高度", 12);
        resize.setBackgroundColor(0xff7a4100);
        column.addView(resize, new LinearLayout.LayoutParams(-1, dp(30)));
        layout = new WindowManager.LayoutParams(width, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        layout.gravity = Gravity.TOP | Gravity.LEFT;
        layout.x = x; layout.y = y;
        move.setOnTouchListener(new TransformTouch(false));
        resize.setOnTouchListener(new TransformTouch(true));
        close.setOnClickListener(v -> { stopInternal(false); toast("辅助已关闭"); });
        touchArea.setOnTouchListener(new ActivationTouch());
        try {
            windows.addView(overlay, layout);
            avoidBubbleOverlap();
        }
        catch (RuntimeException e) { overlay = null; touchArea = null; stopInternal(false); toast("无法显示辅助区域，请重新开启无障碍服务"); }
    }

    private final class TransformTouch implements View.OnTouchListener {
        private final boolean resize;
        private float startX, startY;
        private int originalX, originalY, originalWidth, originalHeight;
        TransformTouch(boolean resize) { this.resize = resize; }
        @Override public boolean onTouch(View view, MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                startX = e.getRawX(); startY = e.getRawY();
                originalX = x; originalY = y; originalWidth = width; originalHeight = areaHeight;
                return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_MOVE && overlay != null) {
                int dx = Math.round(e.getRawX() - startX), dy = Math.round(e.getRawY() - startY);
                int[] screen = screenSize();
                if (resize) {
                    width = Math.max(dp(140), Math.min(screen[0] - x, originalWidth + dx));
                    areaHeight = Math.max(dp(32), Math.min(dp(160), originalHeight + dy));
                    LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) touchArea.getLayoutParams();
                    p.height = areaHeight; touchArea.setLayoutParams(p); layout.width = width;
                } else {
                    x = Math.max(0, Math.min(screen[0] - width, originalX + dx));
                    y = Math.max(0, Math.min(screen[1] - dp(68) - areaHeight, originalY + dy));
                    layout.x = x; layout.y = y;
                }
                try {
                    windows.updateViewLayout(overlay, layout);
                    avoidBubbleOverlap();
                } catch (RuntimeException ignored) { stopInternal(false); }
                return true;
            }
            return true;
        }
    }

    private final class ActivationTouch implements View.OnTouchListener {
        private long downTime, previousTap;
        private float previousX, previousY;
        private final int[] ids = {-1, -1};
        private final float[] xs = new float[2], ys = new float[2];
        private int maxPointers;
        private boolean valid;
        private boolean releaseMode;
        private boolean releasePending;

        private boolean inside(View v, float x, float y) {
            return x >= 0 && y >= 0 && x < v.getWidth() && y < v.getHeight();
        }
        private void checkPointers(View v, MotionEvent e) {
            int slop = ViewConfiguration.get(AssistService.this).getScaledTouchSlop() * 2;
            for (int i = 0; i < e.getPointerCount(); i++) {
                int id = e.getPointerId(i);
                int slot = id == ids[0] ? 0 : (id == ids[1] ? 1 : -1);
                float x = e.getX(i), y = e.getY(i);
                if (slot < 0 || !inside(v, x, y)) { valid = false; continue; }
                float dx = x - xs[slot], dy = y - ys[slot];
                if (dx * dx + dy * dy > slop * slop) valid = false;
            }
            if (!valid) previousTap = 0;
        }
        @Override public boolean onTouch(View view, MotionEvent e) {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                downTime = e.getEventTime(); maxPointers = 1;
                ids[0] = e.getPointerId(0); ids[1] = -1;
                xs[0] = e.getX(0); ys[0] = e.getY(0);
                valid = inside(view, xs[0], ys[0]);
                if (!valid) previousTap = 0;
                String mode = getSharedPreferences(PREFS, MODE_PRIVATE).getString("trigger_mode", "immediate");
                releaseMode = "release".equals(mode);
                releasePending = releaseMode && valid;
                if (valid && !releaseMode && !"gestures".equals(mode)) {
                    previousTap = 0;
                    injectSwipe();
                }
                return true;
            }
            if (releaseMode) {
                if (action == MotionEvent.ACTION_CANCEL) {
                    releasePending = false;
                } else if ((action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP)
                        && e.getPointerId(e.getActionIndex()) == ids[0] && releasePending) {
                    releasePending = false;
                    injectSwipe();
                }
                return true;
            }
            if (action == MotionEvent.ACTION_POINTER_DOWN) {
                maxPointers++;
                int i = e.getActionIndex();
                if (maxPointers != 2 || e.getEventTime() - downTime > 180) valid = false;
                else { ids[1] = e.getPointerId(i); xs[1] = e.getX(i); ys[1] = e.getY(i); }
                previousTap = 0;
            }
            if (action == MotionEvent.ACTION_CANCEL) { valid = false; previousTap = 0; return true; }
            checkPointers(view, e); // Includes pointer-down, each movement and each lift.
            if (action == MotionEvent.ACTION_UP) {
                long now = e.getEventTime();
                if (!valid || now - downTime > 350) { previousTap = 0; return true; }
                if (maxPointers == 2) { previousTap = 0; injectSwipe(); }
                else if (maxPointers == 1) {
                    float dx = xs[0] - previousX, dy = ys[0] - previousY;
                    int slop = ViewConfiguration.get(AssistService.this).getScaledDoubleTapSlop();
                    if (previousTap != 0 && downTime >= previousTap &&
                            downTime - previousTap <= ViewConfiguration.getDoubleTapTimeout()
                            && dx * dx + dy * dy <= slop * slop) {
                        previousTap = 0; injectSwipe();
                    } else { previousTap = now; previousX = xs[0]; previousY = ys[0]; }
                }
            }
            return true;
        }
    }

    private void injectSwipe() {
        if (injecting) return;
        int token = ++generation;
        lastGestureToken = token;
        lastGestureStatus = "rejected"; // Remains rejected unless Android accepts the dispatch.
        if (!armed || overlay == null || touchArea == null || !targetIsForeground()) {
            stopInternal(false); toast("目标应用不可见，辅助已关闭"); return;
        }
        int[] location = new int[2]; touchArea.getLocationOnScreen(location);
        Rect bounds = new Rect(location[0], location[1], location[0] + touchArea.getWidth(), location[1] + touchArea.getHeight());
        int[] screen = screenSize();
        if (bounds.left < 0 || bounds.top < 0 || bounds.right > screen[0] || bounds.bottom > screen[1]
                || bounds.width() < dp(140) || bounds.height() < dp(32)) {
            toast("请将辅助区域完整放在屏幕内并对齐滑条"); return;
        }
        View removedView = overlay;
        View removedBubble = statusBubble;
        injecting = true;
        gestureDispatched = false;
        boolean areaRemoved = removeOverlay();
        boolean bubbleRemoved = removeStatusBubble();
        if (!areaRemoved || !bubbleRemoved) {
            stopInternal(false); toast("无法移除辅助窗口，未执行滑动"); return;
        }
        // removeViewImmediate detaches the local view, but input dispatch may lag behind.
        // Await two display frames and verify detachment before injecting. Some Android
        // builds omit an app's own accessibility overlay from getWindows().
        waitForOverlayRemoval(token, bounds, removedView, removedBubble, SystemClock.uptimeMillis() + 600, false);
    }

    private void waitForOverlayRemoval(int token, Rect bounds, View removedView, View removedBubble, long deadline, boolean detachedOnPreviousFrame) {
        Choreographer.getInstance().postFrameCallback(frameTime -> {
            if (!armed || token != generation) return;
            if (!targetIsForeground()) {
                stopInternal(false); toast("目标页面已变化，未执行滑动"); return;
            }
            if (SystemClock.uptimeMillis() > deadline) {
                stopInternal(false); toast("辅助窗口未及时关闭，未执行滑动"); return;
            }
            boolean detached = !removedView.isAttachedToWindow() && (removedBubble == null || !removedBubble.isAttachedToWindow());
            if (!detached || !detachedOnPreviousFrame) {
                waitForOverlayRemoval(token, bounds, removedView, removedBubble, deadline, detached);
                return;
            }
            dispatchSwipe(token, bounds);
        });
    }

    private void dispatchSwipe(int token, Rect bounds) {
        if (!armed || token != generation || !targetIsForeground()) {
            stopInternal(false); toast("目标应用已离开，未执行滑动"); return;
        }
        float inset = Math.min(bounds.height() / 2f, bounds.width() * .09f);
        float center = bounds.exactCenterY();
        Path path = new Path(); path.moveTo(bounds.left + inset, center); path.lineTo(bounds.right - inset, center);
        GestureDescription g = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0, SwipeTiming.normalize(
                getSharedPreferences(PREFS, MODE_PRIVATE).getInt("swipe_duration_ms", 250)))).build();
        try {
            gestureDispatched = true;
            gestureInFlight = true;
            gestureInFlightToken = token;
            boolean accepted = dispatchGesture(g, new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription gesture) { finishSwipe(token, "completed"); }
                @Override public void onCancelled(GestureDescription gesture) { finishSwipe(token, "cancelled"); }
            }, handler);
            if (accepted) {
                lastGestureStatus = "accepted";
                handler.postDelayed(() -> {
                    if (armed && generation == token && lastGestureToken == token &&
                            "accepted".equals(lastGestureStatus)) {
                        lastGestureStatus = "timeout";
                        lastGestureToken = -1;
                        stopInternal(false);
                        toast("未收到系统结果，请核对订单并重新开启无障碍服务");
                    }
                }, 2000);
            } else finishSwipe(token, "rejected");
        } catch (RuntimeException e) {
            finishSwipe(token, "rejected");
        }
    }

    private void finishSwipe(int token, String status) {
        if (token == lastGestureToken) lastGestureStatus = status;
        if (token == gestureInFlightToken) {
            gestureInFlight = false;
            gestureInFlightToken = -1;
        }
        if (token != generation || !armed) { updateStatusBubble(); return; }
        stopInternal(false); // One user gesture permits one injection; re-arm for each order.
        toast("completed".equals(status) ? "已执行滑动，请查看订单结果；辅助已关闭" : "滑动未完成，辅助已关闭，请重新对齐后重试");
    }

    private boolean removeOverlay() {
        if (overlay == null) return true;
        try { windows.removeViewImmediate(overlay); }
        catch (RuntimeException e) { if (overlay.isAttachedToWindow()) return false; }
        overlay = null; touchArea = null; layout = null;
        return true;
    }

    private void stopInternal(boolean notify) {
        generation++; armed = false; seenTarget = false; injecting = false; gestureDispatched = false; geometryInitialized = false; targetWindowId = -1;
        handler.removeCallbacks(foregroundCheck); handler.removeCallbacks(armTimeout); removeOverlay();
        updateStatusBubble();
        if (notify) toast("辅助已关闭");
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    @Override public void onInterrupt() { volumeKeyChord.reset(); stopInternal(false); removeStatusBubble(); }
    @Override public void onConfigurationChanged(Configuration config) { super.onConfigurationChanged(config); volumeKeyChord.reset(); bubbleY = -1; stopInternal(false); removeStatusBubble(); handler.post(foregroundCheck); toast("屏幕状态变化，辅助已关闭"); }
    @Override public void onDestroy() {
        serviceStopping = true; volumeKeyChord.reset(); stopInternal(false); removeStatusBubble(); unregisterReceiver(screenReceiver); if (instance == this) instance = null; super.onDestroy();
    }
}
