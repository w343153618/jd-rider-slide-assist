package com.example.slideassist;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.TextView;

/** Compact, user-operated status control shared by the preview and the real overlay. */
public final class StatusBubbleView extends TextView {
    private final TripleTapDetector taps = new TripleTapDetector();
    private boolean active;
    public StatusBubbleView(Context context) {
        super(context);
        setGravity(Gravity.CENTER);
        setTextSize(12);
        setTextColor(Color.WHITE);
        setClickable(true);
        setActive(false);
    }

    public void setActive(boolean active) {
        if (this.active != active) resetTapSequence();
        this.active = active;
        setText(active ? "● 已开启" : "● 已关闭");
        setContentDescription(active ? "辅助已开启，连续轻点三次关闭" : "辅助已关闭，连续轻点三次开启");
        GradientDrawable background = new GradientDrawable();
        background.setColor(active ? 0xdd17794b : 0xdd4c5563);
        background.setStroke(dp(1), 0xbbe5e7eb);
        background.setCornerRadius(dp(24));
        setBackground(background);
    }

    public void resetTapSequence() { taps.reset(); }

    @Override public boolean performClick() {
        // Only a confirmed triple calls the listener and Android's standard click feedback.
        // https://developer.android.com/reference/android/view/View#performClick()
        return taps.onTap(SystemClock.uptimeMillis()) && super.performClick();
    }

    private int dp(float value) {
        return (int) (getResources().getDisplayMetrics().density * value + .5f);
    }
}
