package com.example.slideassist;

import android.animation.ValueAnimator;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** A local, non-interactive reminder tint; it never reads input or starts an order action. */
public final class RedBreathingView extends View {
    public static final long PULSE_PERIOD_MILLIS = 1200L;
    private static final int ALERT_RED = 0xffdc2626;
    private static final float STATIC_TINT = .12f;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float borderWidth;
    private boolean alerting;
    private boolean aggregatedVisible = true;
    private long startedUptimeMillis;
    private float pulse = 1f;
    private boolean staticTint;
    private ValueAnimator animator;
    private boolean observingLegacyScale, legacyScaleBlocked;
    private boolean legacyMotionEnabled = true;
    private final ContentObserver legacyScaleObserver = Build.VERSION.SDK_INT < 26
            ? new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override public void onChange(boolean selfChange) {
                    if (!observingLegacyScale) return;
                    legacyMotionEnabled = readLegacyMotionPreference();
                    reconcilePulse();
                }
            } : null;

    public RedBreathingView(Context context) {
        super(context);
        borderWidth = 4 * getResources().getDisplayMetrics().density;
        fill.setColor(ALERT_RED);
        border.setColor(ALERT_RED);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(borderWidth);
        setClickable(false);
        setLongClickable(false);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setAlerting(boolean value) {
        if (alerting == value) {
            if (value) reconcilePulse();
            return;
        }
        alerting = value;
        if (value) {
            // The first matched frame is visible immediately; later frames breathe smoothly.
            startedUptimeMillis = SystemClock.uptimeMillis();
            pulse = 1f;
            reconcilePulse();
        } else {
            stopAnimator();
            staticTint = false;
            updateLegacyObserver(false);
        }
        invalidate();
    }

    public boolean isAlerting() { return alerting; }
    public boolean isPulseRunning() { return animator != null && animator.isRunning(); }

    private boolean motionEnabled() {
        // Android exposes the system animation preference from API26 onward.
        // https://developer.android.com/reference/android/animation/ValueAnimator#areAnimatorsEnabled()
        return Build.VERSION.SDK_INT >= 26 ? ValueAnimator.areAnimatorsEnabled() : legacyMotionEnabled;
    }

    private boolean readLegacyMotionPreference() {
        // Public since API17; do not animate if this setting cannot be read.
        // https://developer.android.com/reference/android/provider/Settings.Global#ANIMATOR_DURATION_SCALE
        try {
            float scale = Settings.Global.getFloat(getContext().getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            return Float.isFinite(scale) && scale > 0;
        } catch (SecurityException denied) { return false; }
    }

    private void updateLegacyObserver(boolean visibleAlert) {
        if (legacyScaleObserver == null) return;
        if (visibleAlert && !observingLegacyScale && !legacyScaleBlocked) {
            try {
                getContext().getContentResolver().registerContentObserver(
                        Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, legacyScaleObserver);
                observingLegacyScale = true;
                legacyMotionEnabled = readLegacyMotionPreference();
            } catch (SecurityException denied) {
                legacyScaleBlocked = true;
                legacyMotionEnabled = false;
            }
        } else if (!visibleAlert && observingLegacyScale) {
            observingLegacyScale = false;
            getContext().getContentResolver().unregisterContentObserver(legacyScaleObserver);
        }
    }

    private void reconcilePulse() {
        boolean visibleAlert = alerting && isAttachedToWindow() && aggregatedVisible
                && isShown() && getWindowVisibility() == VISIBLE;
        updateLegacyObserver(visibleAlert);
        if (!visibleAlert) {
            stopAnimator();
            return;
        }
        if (!motionEnabled()) {
            stopAnimator();
            staticTint = true;
            invalidate();
            return;
        }
        staticTint = false;
        if (animator != null && animator.isStarted()) return;
        stopAnimator();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(PULSE_PERIOD_MILLIS);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(frame -> {
            if (!motionEnabled()) {
                stopAnimator();
                staticTint = true;
            } else {
                // Real uptime keeps the visible cycle at1200ms even if the developer scale is fast.
                long elapsed = Math.max(0, SystemClock.uptimeMillis() - startedUptimeMillis);
                double phase = (elapsed % PULSE_PERIOD_MILLIS) / (double) PULSE_PERIOD_MILLIS;
                pulse = .5f + .5f * (float) Math.cos(2 * Math.PI * phase);
            }
            invalidate();
        });
        animator.start();
    }

    private void stopAnimator() {
        ValueAnimator previous = animator;
        animator = null;
        if (previous != null) {
            previous.removeAllUpdateListeners();
            previous.cancel();
        }
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) { return false; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!alerting) return;
        fill.setAlpha(Math.round(255 * (staticTint ? STATIC_TINT : .04f + .18f * pulse)));
        border.setAlpha(Math.round(255 * (staticTint ? .60f : .45f + .30f * pulse)));
        canvas.drawRect(0, 0, getWidth(), getHeight(), fill);
        float inset = borderWidth / 2f;
        if (getWidth() > borderWidth && getHeight() > borderWidth)
            canvas.drawRect(inset, inset, getWidth() - inset, getHeight() - inset, border);
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); reconcilePulse(); }
    @Override protected void onDetachedFromWindow() {
        stopAnimator(); updateLegacyObserver(false); super.onDetachedFromWindow();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        reconcilePulse();
    }
    @Override public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        aggregatedVisible = isVisible;
        reconcilePulse();
    }
}
