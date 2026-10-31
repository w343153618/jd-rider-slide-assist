package com.example.slideassist;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/** Manual-only measurement surface. It never dispatches a gesture or contacts a service. */
public final class ManualSpeedSliderView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private InteractionRecorder.Trace trace;
    private Runnable listener;
    private int pointer = -1;
    private float startX, startY, progress;
    private boolean validStart, multiTouch, reached, moved, finished, successful;
    private String result = "从白色手柄向右拖动";

    public ManualSpeedSliderView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setFocusable(true);
        setContentDescription("手动测速滑块；从白色手柄开始，拖过全行程的百分之九十八后抬手。不会自动滑动。");
    }

    public void setOnAttemptFinished(Runnable value) { listener = value; }
    public void reset() {
        cancelAttempt();
        pointer = -1;
        progress = 0;
        finished = successful = false;
        result = "从白色手柄向右拖动";
        setContentDescription("手动测速滑块；从白色手柄开始，拖过全行程的百分之九十八后抬手。不会自动滑动。");
        invalidate();
    }
    public void cancelAttempt() {
        if (pointer != -1) finish("cancelled", SystemClock.uptimeMillis());
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        SliderGeometry geometry = geometry();
        paint.setColor(successful
                ? Color.rgb(46, 112, 77) : Color.rgb(211, 89, 0));
        canvas.drawRoundRect(new RectF(0, 0, getWidth(), getHeight()), 12 * density, 12 * density, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(14 * getResources().getDisplayMetrics().scaledDensity);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(result, getWidth() / 2f + 16 * density,
                getHeight() / 2f - (paint.ascent() + paint.descent()) / 2f, paint);
        float left = geometry.thumbLeft() + progress * geometry.travel();
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(new RectF(left, geometry.thumbTop(), left + geometry.thumbWidth(),
                geometry.thumbBottom()), geometry.cornerRadius(), geometry.cornerRadius(), paint);
        paint.setColor(Color.rgb(211, 89, 0));
        paint.setTextSize(26 * getResources().getDisplayMetrics().scaledDensity);
        canvas.drawText("››", left + geometry.thumbWidth() / 2f,
                getHeight() / 2f - (paint.ascent() + paint.descent()) / 2f, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (finished) return true;
            pointer = event.getPointerId(0);
            startX = event.getX(); startY = event.getY();
            validStart = geometry().inHandle(startX, startY);
            multiTouch = reached = moved = false;
            trace = TouchCapture.begin("manual", event, 0, density);
            result = validStart ? "拖到右端后抬手" : "起点在白色手柄外";
            // Android ViewParent contract: keep a valid drag from becoming ScrollView scrolling.
            // https://developer.android.com/reference/android/view/ViewParent#requestDisallowInterceptTouchEvent(boolean)
            if (validStart && getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            invalidate();
            return true;
        }
        if (pointer == -1 || finished) return true;
        int index = event.findPointerIndex(pointer);
        if (action == MotionEvent.ACTION_POINTER_DOWN || event.getPointerCount() > 1) multiTouch = true;
        if (index >= 0) {
            TouchCapture.append(trace, event, index);
            for (int h = 0; h < event.getHistorySize(); h++) {
                position(event.getHistoricalX(index, h), event.getHistoricalY(index, h),
                        event.getHistoricalEventTime(h));
            }
            position(event.getX(index), event.getY(index), event.getEventTime());
        }
        if (action == MotionEvent.ACTION_CANCEL) finish("cancelled", event.getEventTime());
        else if (action == MotionEvent.ACTION_UP) {
            String outcome = !validStart ? "invalid_start" : multiTouch ? "invalid_multitouch"
                    : reached ? "success" : "incomplete";
            finish(outcome, event.getEventTime());
            performClick();
        } else if (action == MotionEvent.ACTION_POINTER_UP
                && event.getPointerId(event.getActionIndex()) == pointer) {
            finish("invalid_multitouch", event.getEventTime());
        }
        invalidate();
        return true;
    }

    private void position(float x, float y, long time) {
        if (!moved && (x != startX || y != startY)) {
            moved = true;
            if (trace != null) trace.mark("first_move", time);
        }
        if (!validStart || multiTouch) return;
        progress = Math.max(0, Math.min(1, (x - startX) / Math.max(1, geometry().travel())));
        if (!reached && progress >= .98f) {
            reached = true;
            if (trace != null) trace.mark("target", time);
            result = "已到终点，抬手结束";
        }
    }

    private void finish(String outcome, long time) {
        if (trace != null) trace.finish(outcome, time);
        trace = null;
        pointer = -1;
        finished = true;
        successful = "success".equals(outcome);
        result = "success".equals(outcome) ? "手动测试成功" : "incomplete".equals(outcome)
                ? "未到终点" : "invalid_start".equals(outcome) ? "起点无效" :
                "invalid_multitouch".equals(outcome) ? "多指无效" : "本次已取消";
        setContentDescription(result + "，点击重置 / 下一次后继续");
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        invalidate();
        if (listener != null) listener.run();
    }

    private SliderGeometry geometry() { return new SliderGeometry(getWidth(), getHeight(), density); }
    @Override public boolean performClick() { super.performClick(); return true; }
}
