package com.example.slideassist;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.Locale;

/** Playback uses recorded event timestamps. It only moves chart cursors. */
public final class TouchReplayView extends LinearLayout {
    private final TouchReportView chart;
    private final Button play;
    private final SeekBar seek;
    private final TextView values;
    private InteractionRecorder.TraceSnapshot trace;
    private ValueAnimator animator;
    private double elapsed, speed = .1;
    private boolean updatingSeek;

    public TouchReplayView(Context context, TouchReportView chart) {
        super(context);
        this.chart = chart;
        setOrientation(VERTICAL);
        LinearLayout controls = new LinearLayout(context);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        play = new Button(context); play.setText("播放轨迹"); play.setMinHeight(dp(48));
        controls.addView(play, new LayoutParams(0, dp(48), 1));
        Spinner rates = new Spinner(context);
        rates.setContentDescription("轨迹回放速度");
        rates.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"0.1× 慢放", "0.25×", "0.5×", "1× 原速"}));
        controls.addView(rates, new LayoutParams(0, dp(48), 1));
        addView(controls);
        seek = new SeekBar(context); seek.setMax(1000); seek.setContentDescription("轨迹回放进度");
        addView(seek, new LayoutParams(-1, dp(48)));
        values = new TextView(context); values.setTextSize(12); values.setTextColor(Color.rgb(55, 59, 60));
        values.setLineSpacing(dp(3), 1); addView(values);
        play.setOnClickListener(v -> {
            if (animator != null && animator.isRunning()) pause(); else start();
        });
        rates.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean running = animator != null && animator.isRunning(); pause();
                speed = new double[]{.1, .25, .5, 1}[position]; update(elapsed);
                if (running) start();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && !updatingSeek) update(TouchReportView.duration(trace) * progress / 1000.0);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { pause(); }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
    }

    public void setTrace(InteractionRecorder.TraceSnapshot value) {
        pause(); trace = value; chart.setTrace(value); update(0);
        boolean hasPoints = value != null && !value.samples.isEmpty();
        play.setEnabled(hasPoints && TouchReportView.duration(value) > 0); seek.setEnabled(hasPoints);
    }
    public double getElapsedMillis() { return elapsed; }
    public void seekTo(double milliseconds) { pause(); update(milliseconds); }
    public void pause() {
        if (animator != null) { animator.cancel(); animator.removeAllUpdateListeners(); animator = null; }
        play.setText("播放轨迹");
    }
    private void start() {
        if (trace == null || trace.samples.isEmpty()) return;
        long duration = TouchReportView.duration(trace); if (duration <= 0) return;
        if (elapsed >= duration) update(0);
        animator = ValueAnimator.ofFloat((float) elapsed, duration);
        animator.setDuration(Math.max(1, Math.round((duration - elapsed) / speed)));
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> {
            update((float) value.getAnimatedValue());
            if (elapsed >= duration) play.setText("重新播放");
        });
        play.setText("暂停回放"); animator.start();
    }
    private void update(double time) {
        long duration = TouchReportView.duration(trace);
        elapsed = Math.max(0, Math.min(duration, time)); chart.setCursor(elapsed);
        updatingSeek = true; seek.setProgress(duration == 0 ? 0 : (int) (elapsed / duration * 1000)); updatingSeek = false;
        if (trace == null || trace.samples.isEmpty()) { values.setText("此记录没有触摸采样，不能回放。"); return; }
        int index = 0;
        for (int i = 1; i < trace.samples.size(); i++) {
            if (trace.samples.get(i).eventTimeMillis - trace.downEventTimeMillis > elapsed) break;
            index = i;
        }
        InteractionRecorder.Sample s = trace.samples.get(index);
        values.setText(String.format(Locale.ROOT,
                "原始事件 / 阶段时间 %.1f / %d ms · 回放 %.2f×\n最近原始点 #%d：x %s / y %s px\n速度 %s dp/s · 接收滞后 %s ms · pressure %s\n光点仅作可视化插值；末段无触摸采样时停在最后位置。数值取最近已发生的原始点。",
                elapsed, duration, speed, index + 1, number(s.x), number(s.y),
                number(TouchReportView.value(trace, index, 0)), number(TouchReportView.value(trace, index, 2)), number(s.pressure)));
    }
    private String number(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.2f", value) : "未提供"; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onDetachedFromWindow() { pause(); super.onDetachedFromWindow(); }
}
