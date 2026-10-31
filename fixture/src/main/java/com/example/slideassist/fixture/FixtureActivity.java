package com.example.slideassist.fixture;

import android.app.Activity;
import android.graphics.Color;
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

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
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

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                moves = 0;
                startX = event.getX(); startY = event.getY();
                downTime = event.getEventTime();
                Log.i("SlideFixture", "DOWN");

                break;
            case MotionEvent.ACTION_MOVE:
                moves++;
                break;
            case MotionEvent.ACTION_UP:
                int distance = Math.round(event.getX() - startX);
                long duration = event.getEventTime() - downTime;
                if (moves > 0 && distance > 100) completedSwipes++;
                Log.i("SlideFixture", "UP count=" + completedSwipes + " moves=" + moves + " dx=" + distance + " duration=" + duration + " sx=" + startX + " sy=" + startY + " ex=" + event.getX() + " ey=" + event.getY());
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
}
