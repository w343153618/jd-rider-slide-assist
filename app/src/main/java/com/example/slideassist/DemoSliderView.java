package com.example.slideassist;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
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
    private boolean assistanceEnabled = true;
    private boolean startedInHandle;
    private int releasePointer = -1;
    private ValueAnimator animator;
    private int swipeDurationMillis = SwipeTiming.DEFAULT_MILLIS;
    private Runnable onComplete;
    private InteractionRecorder.Trace touchTrace;
    private InteractionRecorder.Trace animationTrace;
    private SliderGeometry.Swipe lastAssistedPath;
    private SliderGeometry.Swipe pendingAssistedPath;

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
        invalid = true;
        updateDescription();
    }

    private void updateDescription() {
        setContentDescription(completed ? "模拟抢单成功，可点击重置练习再次尝试" :
                !assistanceEnabled ? "练习辅助已关闭，可从左侧白箭头内手动拖动滑条" :
                immediate ? "模拟抢单滑条。左侧白箭头内单指按下立即模拟抢单" :
                release ? "模拟抢单滑条。左侧白箭头内按下后抬起手指模拟抢单" : "模拟抢单滑条。从左侧白箭头内双击、双指轻点或向右滑动");
    }

    public void setOnComplete(Runnable callback) { onComplete = callback; }

    public void setSwipeDurationMillis(int duration) {
        swipeDurationMillis = SwipeTiming.normalize(duration);
    }

    public void setAssistanceEnabled(boolean enabled) {
        assistanceEnabled = enabled;
        previousTapUp = 0;
        releasePointer = -1;
        invalid = true;
        updateDescription();
    }

    public void reset() {
        if (animator != null) animator.cancel();
        finishTrace(touchTrace, "cancelled"); touchTrace = null;
        completed = false;
        progress = 0;
        lastAssistedPath = null;
        pendingAssistedPath = null;
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
            SliderGeometry g = geometry();
            float thumb = g.thumbWidth();
            float left = g.thumbLeft() + progress * g.travel();
            paint.setColor(Color.WHITE);
            canvas.drawRoundRect(new RectF(left, g.thumbTop(), left + thumb, g.thumbBottom()), g.cornerRadius(), g.cornerRadius(), paint);
            paint.setColor(Color.rgb(225, 99, 0));
            paint.setTextSize(29 * getResources().getDisplayMetrics().scaledDensity);
            canvas.drawText("››", left + thumb / 2, h / 2 - (paint.ascent() + paint.descent()) / 2 - density, paint);
        }
        paint.setFakeBoldText(false);
    }

    private SliderGeometry geometry() { return new SliderGeometry(getWidth(), getHeight(), density); }

    /** Local coordinates of the last assisted path, for fixture diagnostics only. */
    SliderGeometry.Swipe getLastAssistedPath() { return lastAssistedPath; }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (completed || (animator != null && animator.isRunning())) {
            if (action != MotionEvent.ACTION_DOWN) TouchCapture.append(animationTrace, event, event.findPointerIndex(firstId));
            if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && getParent() != null)
                getParent().requestDisallowInterceptTouchEvent(false);
            return true;
        }
        if (action != MotionEvent.ACTION_DOWN) TouchCapture.append(touchTrace, event, event.findPointerIndex(firstId));
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                finishTrace(touchTrace, "cancelled");
                touchTrace = TouchCapture.begin("demo_assist", event, 0, density);
                if (touchTrace != null) touchTrace.mark("down", event.getEventTime());
                firstId = event.getPointerId(0);
                downX = event.getX(0); downY = event.getY(0); downTime = event.getEventTime();
                startedInHandle = geometry().inHandle(downX, downY);
                pendingAssistedPath = startedInHandle ? geometry().fromLocal(downX, downY, 0, 0) : null;
                moved = dragging = hadSecond = false;
                invalid = !startedInHandle;
                secondId = -1;
                releasePointer = assistanceEnabled && release && startedInHandle ? firstId : -1;
                if (!startedInHandle) previousTapUp = 0;
                if (startedInHandle && getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                if (assistanceEnabled && immediate) {
                    if (startedInHandle) completeAssisted();
                    return true;
                }
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (assistanceEnabled && release) return true;
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
                if (assistanceEnabled && (immediate || release)) return true;
                int first = event.findPointerIndex(firstId);
                checkPositions(event);
                if (moved) previousTapUp = 0;
                if (hadSecond && moved) invalid = true;
                if (!hadSecond && first >= 0 && startedInHandle && moved) {
                    dragging = true;
                    previousTapUp = 0;
                    float span = Math.max(1, geometry().travel());
                    progress = Math.max(0, Math.min(1, (event.getX(first) - downX) / span));
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (assistanceEnabled && release) {
                    if (releasePointer != -1 && event.getPointerId(event.getActionIndex()) == releasePointer) {
                        releasePointer = -1;
                        completeAssisted();
                    }
                    return true;
                }
                checkPositions(event);
                if (event.getPointerCount() != 2 || moved) invalid = true;
                return true;
            case MotionEvent.ACTION_UP:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                if (assistanceEnabled && release) {
                    if (releasePointer != -1 && event.getPointerId(event.getActionIndex()) == releasePointer) {
                        releasePointer = -1;
                        completeAssisted();
                    } else finishTouch("ignored");
                    return true;
                }
                if (assistanceEnabled && immediate) { finishTouch("ignored"); return true; }
                checkPositions(event);
                long duration = event.getEventTime() - downTime;
                if (hadSecond) {
                    if (assistanceEnabled && !invalid && !moved && duration <= 500 && inside(event.getX(), event.getY())) completeAssisted();
                    else { finishTouch("ignored"); if (progress > 0) animateTo(0, false, null); }
                } else if (dragging) {
                    if (progress >= 0.85f) completeManual();
                    else { finishTouch("incomplete"); animateTo(0, false, null); }
                } else if (assistanceEnabled && !invalid && !moved && duration <= 350 && inside(event.getX(), event.getY())) {
                    long sincePrevious = downTime - previousTapUp;
                    if (previousTapUp != 0 && sincePrevious >= 0 && sincePrevious <= ViewConfiguration.getDoubleTapTimeout()
                            && distance(downX - previousTapX, downY - previousTapY) <= 48 * density) {
                        previousTapUp = 0;
                        completeAssisted();
                    } else {
                        previousTapUp = event.getEventTime(); previousTapX = downX; previousTapY = downY;
                        finishTouch("tap_wait");
                    }
                } else {
                    previousTapUp = 0;
                    finishTouch("ignored");
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                previousTapUp = 0;
                releasePointer = -1;
                finishTouch("cancelled");
                if (progress > 0) animateTo(0, false, null);
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
    private void completeAssisted() {
        if (!startedInHandle || pendingAssistedPath == null) return;
        // The thumb remains at progress zero; the user's exact grab offset moves by its full travel.
        lastAssistedPath = pendingAssistedPath;
        progress = 0;
        markTrace(touchTrace, "trigger");
        InteractionRecorder.Trace trace = touchTrace; touchTrace = null;
        animateTo(1, true, trace);
    }

    private void completeManual() {
        markTrace(touchTrace, "manual_release");
        InteractionRecorder.Trace trace = touchTrace; touchTrace = null;
        animateTo(1, true, trace);
    }

    private void finishTouch(String outcome) { finishTrace(touchTrace, outcome); touchTrace = null; }
    private void markTrace(InteractionRecorder.Trace trace, String stage) {
        if (trace != null) trace.mark(stage, SystemClock.uptimeMillis());
    }
    private void finishTrace(InteractionRecorder.Trace trace, String outcome) {
        if (trace != null) {
            long now = SystemClock.uptimeMillis();
            trace.mark(outcome, now); trace.finish(outcome, now);
        }
    }

    private void animateTo(float end, boolean finish, InteractionRecorder.Trace trace) {
        if (animator != null) animator.cancel();
        animationTrace = trace;
        markTrace(trace, "animation_started");
        animator = ValueAnimator.ofFloat(progress, end);
        animator.setDuration(finish ? swipeDurationMillis : 280);
        animator.addUpdateListener(animation -> { progress = (float) animation.getAnimatedValue(); invalidate(); });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator animation) {
                cancelled = true; finishTrace(trace, "cancelled");
            }
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (finish && !cancelled) {
                    finishTrace(trace, "animation_completed");
                    if (animationTrace == trace) animationTrace = null;
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
        return true;
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        finishTouch("cancelled");
        super.onDetachedFromWindow();
    }
}
