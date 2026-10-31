package com.example.slideassist;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;
import java.util.Collections;

/** Compact, user-operated status control shared by the preview and the real overlay. */
public final class StatusBubbleView extends TextView {
    public static final int WIDTH_DP = BubbleSettings.WIDTH_DP, HEIGHT_DP = BubbleSettings.HEIGHT_DP;
    public static final int DEFAULT_OPACITY_PERCENT = BubbleSettings.DEFAULT_OPACITY_PERCENT;
    private final TripleTapDetector taps = new TripleTapDetector();
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path handset = new Path();
    private final GradientDrawable square = new GradientDrawable();
    private boolean active;
    private boolean collapsed;
    private Runnable onExpand;
    private int opacityPercent = DEFAULT_OPACITY_PERCENT;
    private long startedElapsedMillis = -1;
    private boolean tickPosted;
    private final Runnable tick = () -> {
        tickPosted = false;
        if (active && isAttachedToWindow()) refreshTimer();
    };
    public StatusBubbleView(Context context) {
        super(context);
        setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        setTextSize(BubbleSettings.DEFAULT_SIZE_DP * .25f);
        setTextColor(0xff07c160);
        setClickable(true);
        setPadding(0, 0, 0, 0);
        setMinWidth(0); setMinHeight(0);
        setIncludeFontPadding(false);
        iconPaint.setColor(0xff07c160);
        handset.moveTo(5, 2); handset.cubicTo(3, 2, 2, 4, 2, 6);
        handset.cubicTo(2, 14, 10, 22, 18, 22);
        handset.cubicTo(20, 22, 22, 20, 22, 18);
        handset.lineTo(22, 15); handset.lineTo(16, 13); handset.lineTo(13, 16);
        handset.cubicTo(10, 14, 8, 12, 7, 9);
        handset.lineTo(10, 6); handset.lineTo(8, 2); handset.close();
        square.setColor(Color.BLACK); setBackground(square);
        setOpacityPercent(DEFAULT_OPACITY_PERCENT);
        setActive(false);
    }

    public void setOpacityPercent(int value) {
        opacityPercent = BubbleSettings.normalizeOpacityPercent(value);
        setAlpha(opacityPercent / 100f);
    }
    public int getOpacityPercent() { return opacityPercent; }

    public void setCollapsed(boolean value) {
        if (collapsed == value) return;
        collapsed = value; resetTapSequence();
        updateVisualMetrics(getWidth(), getHeight()); refreshTimer();
    }
    public boolean isCollapsed() { return collapsed; }
    public void setOnExpandListener(Runnable listener) { onExpand = listener; }

    public void setActive(boolean active) {
        if (this.active != active) {
            resetTapSequence();
            startedElapsedMillis = active ? SystemClock.elapsedRealtime() : -1;
        }
        this.active = active;
        refreshTimer();
    }

    public long getElapsedActiveMillis() {
        return active && startedElapsedMillis >= 0 ? Math.max(0, SystemClock.elapsedRealtime() - startedElapsedMillis) : 0;
    }
    public String getTimerText() { return active ? BubbleTimer.formatElapsedMillis(getElapsedActiveMillis()) : ""; }

    private void refreshTimer() {
        long elapsed = getElapsedActiveMillis();
        setText(active && !collapsed ? BubbleTimer.formatElapsedMillis(elapsed) : ""); fitTimerText();
        String state = active ? "辅助已开启，已持续" + elapsed / 1000 + "秒" : "辅助已关闭";
        setContentDescription(state + (collapsed ? "，已折叠，轻点展开" : active ? "，连续轻点三次关闭" : "，连续轻点三次开启"));
        invalidate();
        if (!active) stopTick();
        else if (!tickPosted && isAttachedToWindow()) {
            tickPosted = true;
            postDelayed(tick, 1000 - elapsed % 1000);
        }
    }
    private void stopTick() { removeCallbacks(tick); tickPosted = false; }
    private void fitTimerText() {
        if (getWidth() == 0 || getText().length() == 0) return;
        float unscaled = getPaint().measureText(getText().toString()) / Math.max(.01f, getTextScaleX());
        float scale = Math.min(1f, Math.max(1, getWidth() - getPaddingLeft() - getPaddingRight()) / Math.max(1, unscaled));
        if (Math.abs(scale - getTextScaleX()) > .001f) setTextScaleX(scale);
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); refreshTimer(); }
    @Override protected void onDetachedFromWindow() { stopTick(); super.onDetachedFromWindow(); }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateVisualMetrics(w, h);
        if (Build.VERSION.SDK_INT >= 29) {
            // Reserve only this small control so its edge drag does not become Android Back.
            // https://developer.android.com/develop/ui/views/touch-and-input/gestures/gesturenav
            setSystemGestureExclusionRects(w > 0 && h > 0
                    ? Collections.singletonList(new Rect(0, 0, w, h)) : Collections.emptyList());
        }
    }
    private void updateVisualMetrics(int w, int h) {
        float side = Math.min(w, h);
        if (collapsed) {
            setPadding(0, 0, 0, 0); square.setCornerRadius(side / 2f); return;
        }
        setTextSize(TypedValue.COMPLEX_UNIT_PX, side * .25f);
        int horizontal = Math.round(side * .05f);
        setPadding(horizontal, 0, horizontal, Math.round(side * .035f));
        square.setCornerRadius(side * 6f / 64f);
        fitTimerText();
    }
    @Override protected void onDraw(Canvas canvas) {
        if (collapsed) {
            float inset = getHeight() * .2f;
            iconPaint.setStyle(Paint.Style.STROKE); iconPaint.setStrokeCap(Paint.Cap.ROUND);
            iconPaint.setStrokeWidth(getResources().getDisplayMetrics().density);
            canvas.drawLine(getWidth() / 2f, inset, getWidth() / 2f, getHeight() - inset, iconPaint);
            iconPaint.setStyle(Paint.Style.FILL);
            return;
        }
        float side = Math.min(getWidth(), getHeight());
        float size = side * (active ? 15f / 28f : 19f / 28f);
        float top = active ? side * .05f : (getHeight() - size) / 2f;
        int save = canvas.save(); canvas.translate((getWidth() - size) / 2f, top);
        canvas.scale(size / 24f, size / 24f); canvas.drawPath(handset, iconPaint); canvas.restoreToCount(save);
        super.onDraw(canvas);
    }

    public void resetTapSequence() { taps.reset(); }

    @Override public boolean performClick() {
        if (collapsed) {
            setCollapsed(false);
            if (onExpand != null) onExpand.run();
            return true;
        }
        // Only a confirmed triple calls the listener and Android's standard click feedback.
        // https://developer.android.com/reference/android/view/View#performClick()
        return taps.onTap(SystemClock.uptimeMillis()) && super.performClick();
    }

}
