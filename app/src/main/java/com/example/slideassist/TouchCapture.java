package com.example.slideassist;

import android.os.SystemClock;
import android.view.MotionEvent;

/** Raw Android values in the touched control's local pixels; never force/current measurements. */
public final class TouchCapture {
    private TouchCapture() { }
    public static InteractionRecorder.Trace begin(String kind, MotionEvent event, int index, float density) {
        if (index < 0 || index >= event.getPointerCount()) return null;
        long received = SystemClock.uptimeMillis();
        InteractionRecorder.Trace trace = InteractionRecorder.begin(kind, event.getEventTime(), received, density);
        append(trace, event, index, received); return trace;
    }
    public static void append(InteractionRecorder.Trace trace, MotionEvent event, int index) {
        append(trace, event, index, SystemClock.uptimeMillis());
    }
    private static void append(InteractionRecorder.Trace trace, MotionEvent event, int index, long received) {
        if (trace == null || index < 0 || index >= event.getPointerCount()) return;
        // Android batches history chronologically; both clocks use uptime, excluding deep sleep.
        // https://developer.android.com/reference/android/view/MotionEvent#getHistoricalEventTime(int)
        // https://developer.android.com/reference/android/os/SystemClock#uptimeMillis()
        for (int h = 0; h < event.getHistorySize(); h++) {
            trace.add(new InteractionRecorder.Sample(event.getHistoricalEventTime(h), received,
                    event.getHistoricalX(index, h), event.getHistoricalY(index, h),
                    event.getHistoricalPressure(index, h), event.getHistoricalSize(index, h),
                    event.getHistoricalTouchMajor(index, h), event.getHistoricalTouchMinor(index, h),
                    event.getHistoricalOrientation(index, h), event.getToolType(index), MotionEvent.ACTION_MOVE,
                    event.getPointerId(index)));
        }
        trace.add(new InteractionRecorder.Sample(event.getEventTime(), received, event.getX(index), event.getY(index),
                event.getPressure(index), event.getSize(index), event.getTouchMajor(index), event.getTouchMinor(index),
                event.getOrientation(index), event.getToolType(index), event.getActionMasked(), event.getPointerId(index)));
    }
}
