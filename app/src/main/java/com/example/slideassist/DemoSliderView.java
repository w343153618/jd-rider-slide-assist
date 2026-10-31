package com.example.slideassist;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;

/** A self-contained practice control. It never communicates with an order service. */
public final class DemoSliderView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final float slop;
    private float progress;
    private float downX, downY, secondX, secondY;
    private int firstId, secondId = -1;
    private long downTime, previousTapUp;
    private float previousTapX, previousTapY;
    private boolean moved, dragging, hadSecond, invalid, completed;
    private boolean immediate = true, release;
    private int releasePointer = -1;
    private ValueAnimator animator;
    private Runnable onComplete;

    public DemoSliderView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
        setFocusable(true);
        updateDescription();
    }

    public void setTriggerMode(String mode) {
        immediate = !"gestures".equals(mode) && !"release".equals(mode);
        release = "release".equals(mode);
        releasePointer = -1;
        previousTapUp = 0;
        updateDescription();
    }

    private void updateDescription() {
        setContentDescription(completed ? "模拟抢单成功，可点击重置练习再次尝试" :
                immediate ? "模拟抢单滑条。单指按下立即模拟抢单" :
                release ? "模拟抢单滑条。按下后抬起手指模拟抢单" : "模拟抢单滑条。双击或双指轻点，或从左侧向右滑动");
    }

    public void setOnComplete(Runnable callback) { onComplete = callback; }

    public void reset() {
        if (animator != null) animator.cancel();
        completed = false;
        progress = 0;
        previousTapUp = 0;
        releasePointer = -1;
        updateDescription();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float h = getHeight(), w = getWidth(), radius = 12 * density;
        paint.setColor(completed ? Color.rgb(50, 126, 83) : Color.rgb(244, 109, 0));
        canvas.drawRoundRect(new RectF(0, 0, w, h), radius, radius, paint);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(18 * getResources().getDisplayMetrics().scaledDensity);
        paint.setFakeBoldText(true);
        String label = completed ? "模拟成功" : "抢单（演示）";
        canvas.drawText(label, w / 2, h / 2 - (paint.ascent() + paint.descent()) / 2, paint);
        if (!completed) {
            float inset = 4 * density;
            float thumb = Math.min(56 * density, h - inset * 2);
            float left = inset + progress * Math.max(0, w - thumb - inset * 2);
            paint.setColor(Color.WHITE);
            canvas.drawRoundRect(new RectF(left, inset, left + thumb, h - inset), 8 * density, 8 * density, paint);
            paint.setColor(Color.rgb(225, 99, 0));
            paint.setTextSize(29 * getResources().getDisplayMetrics().scaledDensity);
            canvas.drawText("››", left + thumb / 2, h / 2 - (paint.ascent() + paint.descent()) / 2 - density, paint);
        }
        paint.setFakeBoldText(false);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (completed || (animator != null && animator.isRunning())) return true;
        if (release) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                releasePointer = inside(event.getX(), event.getY()) ? event.getPointerId(0) : -1;
            } else if (action == MotionEvent.ACTION_CANCEL) {
                releasePointer = -1;
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                if (releasePointer != -1 && event.getPointerId(event.getActionIndex()) == releasePointer) {
                    releasePointer = -1;
                    complete();
                }
            }
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (immediate) {
                    if (inside(event.getX(), event.getY())) complete();
                    return true;
                }
                firstId = event.getPointerId(0);
                downX = event.getX(); downY = event.getY(); downTime = event.getEventTime();
                moved = dragging = hadSecond = invalid = false;
                secondId = -1;
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() != 2 || hadSecond || moved || event.getEventTime() - downTime > 250) {
                    invalid = true;
                    hadSecond = true;
                    previousTapUp = 0;
                    return true;
                }
                int i = event.getActionIndex();
                secondId = event.getPointerId(i);
                secondX = event.getX(i); secondY = event.getY(i);
                hadSecond = true;
                previousTapUp = 0;
                if (!inside(secondX, secondY)) invalid = true;
                return true;
            case MotionEvent.ACTION_MOVE:
                int first = event.findPointerIndex(firstId);
                checkPositions(event);
                if (moved) previousTapUp = 0;
                if (hadSecond && moved) invalid = true;
                if (!hadSecond && first >= 0 && downX <= 64 * density && moved) {
                    dragging = true;
                    previousTapUp = 0;
                    float span = Math.max(1, getWidth() - Math.min(56 * density, getHeight() - 8 * density) - 8 * density);
                    progress = Math.max(0, Math.min(1, (event.getX(first) - downX) / span));
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                checkPositions(event);
                if (event.getPointerCount() != 2 || moved) invalid = true;
                return true;
            case MotionEvent.ACTION_UP:
                checkPositions(event);
                long duration = event.getEventTime() - downTime;
                if (hadSecond) {
                    if (!invalid && !moved && duration <= 500 && inside(event.getX(), event.getY())) complete();
                    else if (progress > 0) animateTo(0, false);
                } else if (dragging) {
                    if (progress >= 0.85f) complete(); else animateTo(0, false);
                } else if (!invalid && !moved && duration <= 350 && inside(event.getX(), event.getY())) {
                    long sincePrevious = downTime - previousTapUp;
                    if (previousTapUp != 0 && sincePrevious >= 0 && sincePrevious <= ViewConfiguration.getDoubleTapTimeout()
                            && distance(downX - previousTapX, downY - previousTapY) <= 48 * density) {
                        previousTapUp = 0;
                        complete();
                    } else {
                        previousTapUp = event.getEventTime(); previousTapX = downX; previousTapY = downY;
                    }
                } else {
                    previousTapUp = 0;
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                previousTapUp = 0;
                if (progress > 0) animateTo(0, false);
                return true;
            default: return true;
        }
    }

    private void checkPositions(MotionEvent event) {
        for (int i = 0; i < event.getPointerCount(); i++) {
            int id = event.getPointerId(i);
            float x = event.getX(i), y = event.getY(i);
            if (!inside(x, y)) invalid = true;
            if (id == firstId && distance(x - downX, y - downY) > slop) moved = true;
            if (id == secondId && distance(x - secondX, y - secondY) > slop) moved = true;
        }
    }
    private boolean inside(float x, float y) { return x >= 0 && y >= 0 && x < getWidth() && y < getHeight(); }
    private float distance(float x, float y) { return (float) Math.hypot(x, y); }
    private void complete() { animateTo(1, true); }
    private void animateTo(float end, boolean finish) {
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(progress, end);
        animator.setDuration(280);
        animator.addUpdateListener(animation -> { progress = (float) animation.getAnimatedValue(); invalidate(); });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (finish && !cancelled) {
                    completed = true;
                    setContentDescription("模拟抢单成功，可点击重置练习再次尝试");
                    sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
                    invalidate();
                    if (onComplete != null) onComplete.run();
                }
            }
        });
        animator.start();
    }

    @Override public boolean performClick() {
        super.performClick();
        if (!completed) complete();
        return true;
    }
}
