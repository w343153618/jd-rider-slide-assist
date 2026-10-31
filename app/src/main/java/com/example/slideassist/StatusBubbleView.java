package com.example.slideassist;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.TextView;

/** Compact, user-operated status control shared by the preview and the real overlay. */
public final class StatusBubbleView extends TextView {
    public StatusBubbleView(Context context) {
        super(context);
        setGravity(Gravity.CENTER);
        setTextSize(12);
        setTextColor(Color.WHITE);
        setClickable(true);
        setActive(false);
    }

    public void setActive(boolean active) {
        setText(active ? "● 已开启" : "● 已关闭");
        setContentDescription(active ? "辅助已开启，点按关闭" : "辅助已关闭，点按开启");
        GradientDrawable background = new GradientDrawable();
        background.setColor(active ? 0xdd17794b : 0xdd4c5563);
        background.setStroke(dp(1), 0xbbe5e7eb);
        background.setCornerRadius(dp(24));
        setBackground(background);
    }

    private int dp(float value) {
        return (int) (getResources().getDisplayMetrics().density * value + .5f);
    }
}
