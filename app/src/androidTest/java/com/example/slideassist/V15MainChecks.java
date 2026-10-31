package com.example.slideassist;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Spinner;

/** Exercises user-facing timing selection and the pinned return path on a real Activity. */
public final class V15MainChecks {
    public static void run(Instrumentation i, Bundle result) {
        Activity main = i.startActivitySync(new Intent(i.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        i.waitForIdleSync();
        Spinner duration = find(main.getWindow().getDecorView(), Spinner.class);
        check(duration != null, "simulation duration selector missing");
        check(duration.getAdapter().getCount() == 11, "all eleven simulation durations required");
        int real = i.getTargetContext().getSharedPreferences("assist_settings",0).getInt("swipe_duration_ms",250);
        for (int index = 0; index < 5; index++) {
            int position = index;
            i.runOnMainSync(() -> duration.setSelection(position)); i.waitForIdleSync();
            int expected = 50 + index * 10;
            check(i.getTargetContext().getSharedPreferences("assist_settings",0).getInt("demo_swipe_duration_ms",0)==expected,
                    "fast simulation duration not saved: " + expected);
            check(i.getTargetContext().getSharedPreferences("assist_settings",0).getInt("swipe_duration_ms",250)==real,
                    "practice timing must not change real assist setting");
        }
        Activity speed = i.startActivitySync(new Intent(i.getTargetContext(), SpeedTestActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); i.waitForIdleSync();
        Button back = button(speed.getWindow().getDecorView(), "返回模拟抢单");
        check(back != null, "speed page must expose a visible return button");
        ScrollView scroll = find(speed.getWindow().getDecorView(), ScrollView.class);
        i.runOnMainSync(() -> scroll.fullScroll(View.FOCUS_DOWN)); i.waitForIdleSync();
        Rect bounds = new Rect();
        i.runOnMainSync(() -> check(back.getGlobalVisibleRect(bounds) && bounds.height() >= 100,
                "return button must remain visible after scrolling"));
        Instrumentation.ActivityMonitor monitor = i.addMonitor(MainActivity.class.getName(),null,false);
        i.runOnMainSync(back::performClick);
        Activity returned = monitor.waitForActivityWithTimeout(4000); i.removeMonitor(monitor);
        i.waitForIdleSync();
        check(returned != null && speed.isFinishing(), "return button must close speed screen and show simulation");
        i.runOnMainSync(returned::finish);
        result.putString("result","PASS: 50/60/70/80/90ms separately saved, all eleven simulation choices, real duration isolated, pinned return stays visible at scroll bottom and returns directly");
    }
    private static Button button(View view,String text) {
        if(view instanceof Button && String.valueOf(((Button)view).getText()).contains(text))return (Button)view;
        if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){
            Button found=button(((ViewGroup)view).getChildAt(n),text);if(found!=null)return found;
        }
        return null;
    }
    private static <T> T find(View view,Class<T> type) {
        if(type.isInstance(view))return type.cast(view);
        if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){
            T found=find(((ViewGroup)view).getChildAt(n),type);if(found!=null)return found;
        }
        return null;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
