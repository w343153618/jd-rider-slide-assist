package com.example.slideassist.fixture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A stand-alone test receiver: it performs no order, account, or network actions. */
public final class FixtureActivity extends Activity {
    private TextView status;
    private int moves, completedSwipes;
    private float startX, startY;
    private long downTime;
    private final MotionEvent.PointerCoords coordinates = new MotionEvent.PointerCoords();
    private boolean unbuffered, streamUnbuffered;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        unbuffered = getIntent().getBooleanExtra("unbuffered", false);
        LinearLayout screen = new LinearLayout(this);
        screen.setGravity(Gravity.CENTER);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(Color.rgb(250, 247, 239));
        TextView title = new TextView(this);
        title.setText("独立滑动测试靶场\n不连接订单或网络");
        title.setGravity(Gravity.CENTER);
        title.setTextSize(24);
        screen.addView(title);
        status = new TextView(this);
        status.setText("完成滑动总数 0，最近移动 0 次，水平位移 0 像素");
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        status.setPadding(20, 30, 20, 30);
        screen.addView(status);
        setContentView(screen);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        unbuffered = intent.getBooleanExtra("unbuffered", false);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                streamUnbuffered = unbuffered;
                if (streamUnbuffered) {
                    // Optional test-receiver mode only; default input delivery remains buffered.
                    // https://developer.android.com/reference/android/view/View#requestUnbufferedDispatch(android.view.MotionEvent)
                    getWindow().getDecorView().requestUnbufferedDispatch(event);
                }
                moves = 0;
                startX = event.getX(); startY = event.getY();
                downTime = event.getEventTime();
                Log.i("SlideFixture", "DOWN resampled=" + resampled(event) + " unbuffered=" + streamUnbuffered);

                break;
            case MotionEvent.ACTION_MOVE:
                moves++;
                Log.i("SlideFixture", "MOVE t=" + (event.getEventTime() - downTime)
                        + " x=" + event.getX() + " y=" + event.getY()
                        + " rawX=" + event.getRawX() + " rawY=" + event.getRawY()
                        + " flags=" + event.getFlags() + " history=" + event.getHistorySize()
                        + " resampled=" + resampled(event) + " unbuffered=" + streamUnbuffered);
                break;
            case MotionEvent.ACTION_UP:
                int distance = Math.round(event.getX() - startX);
                long duration = event.getEventTime() - downTime;
                if (moves > 0 && distance > 100) completedSwipes++;
                Log.i("SlideFixture", "UP count=" + completedSwipes + " moves=" + moves + " dx=" + distance + " duration=" + duration + " sx=" + startX + " sy=" + startY + " ex=" + event.getX() + " ey=" + event.getY() + " resampled=" + resampled(event) + " unbuffered=" + streamUnbuffered);
                status.setText("完成滑动总数 " + completedSwipes + "，最近移动 " + moves + " 次，水平位移 " + distance + " 像素");
                break;
            case MotionEvent.ACTION_CANCEL:
                Log.i("SlideFixture", "CANCEL");

                break;
            default:
                break;
        }
        super.dispatchTouchEvent(event);
        return true;
    }

    private String resampled(MotionEvent event) {
        // Public since API35. Observe the delivered coordinate without changing input batching.
        // https://developer.android.com/reference/android/view/MotionEvent.PointerCoords#isResampled()
        if (Build.VERSION.SDK_INT < 35) return "unavailable";
        event.getPointerCoords(0, coordinates);
        return Boolean.toString(coordinates.isResampled());
    }
}
