package com.example.slideassist;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import java.util.List;
import java.util.Locale;

/** Canvas-only report. The moving cursor is visual feedback, never a touch injection. */
public final class TouchReportView extends View {
    private static final int INK = Color.rgb(35, 39, 42), MUTED = Color.rgb(96, 100, 101);
    private static final int ACCENT = Color.rgb(211, 89, 0), GRID = Color.rgb(220, 222, 219);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d, sp;
    private InteractionRecorder.TraceSnapshot trace;
    private double cursor;

    public TouchReportView(Context context) {
        super(context);
        setContentDescription("触摸轨迹、阶段时间轴、速度、采样间隔、应用接收滞后和原始压力图表");
        d = getResources().getDisplayMetrics().density;
        sp = getResources().getDisplayMetrics().scaledDensity;
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    public void setTrace(InteractionRecorder.TraceSnapshot value) { trace = value; cursor = 0; invalidate(); }
    public void setCursor(double milliseconds) { cursor = milliseconds; invalidate(); }
    public static long duration(InteractionRecorder.TraceSnapshot value) {
        if (value == null) return 0;
        long last = value.downEventTimeMillis;
        if (!value.samples.isEmpty()) last = Math.max(last, value.samples.get(value.samples.size() - 1).eventTimeMillis);
        last = Math.max(last, value.finishedUptimeMillis);
        for (long stage : value.stages.values()) last = Math.max(last, stage);
        return last - value.downEventTimeMillis;
    }

    @Override protected void onMeasure(int width, int height) {
        setMeasuredDimension(MeasureSpec.getSize(width), (int) (740 * d));
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (trace == null || trace.samples.isEmpty()) { text(c, "此记录没有触摸采样点", 8, 30, INK, 14); return; }
        trajectory(c);
        timeline(c, 202);
        curve(c, 330, "速度 · dp/s", 0);
        curve(c, 430, "采样间隔 · ms", 1);
        curve(c, 530, "应用接收滞后 · ms", 2);
        curve(c, 630, "pressure · Android 原始值", 3);
    }

    private void trajectory(Canvas c) {
        text(c, "实际轨迹 · 等比例 x/y（px）", 8, 20, INK, 14);
        List<InteractionRecorder.Sample> points = trace.samples;
        float minX = Float.POSITIVE_INFINITY, minY = minX, maxX = Float.NEGATIVE_INFINITY, maxY = maxX;
        for (InteractionRecorder.Sample s : points) if (finite(s.x) && finite(s.y)) {
            minX = Math.min(minX, s.x); minY = Math.min(minY, s.y);
            maxX = Math.max(maxX, s.x); maxY = Math.max(maxY, s.y);
        }
        if (!finite(minX)) { text(c, "坐标缺测", 8, 50, MUTED, 12); return; }
        float availableW = Math.max(1, getWidth() - 48 * d), availableH = 120 * d;
        float scale = Math.min(availableW / Math.max(1, maxX - minX), availableH / Math.max(1, maxY - minY));
        float ox = 24 * d + (availableW - (maxX - minX) * scale) / 2 - minX * scale;
        float oy = 48 * d + (availableH - (maxY - minY) * scale) / 2 - minY * scale;
        Path path = new Path(); boolean segment = false; int previousPointer = -1, missingCoordinates = 0;
        for (InteractionRecorder.Sample s : points) {
            if (!finite(s.x) || !finite(s.y)) { segment = false; missingCoordinates++; continue; }
            if (previousPointer != s.pointerId) segment = false;
            if (!segment) path.moveTo(ox + s.x * scale, oy + s.y * scale);
            else path.lineTo(ox + s.x * scale, oy + s.y * scale);
            segment = true; previousPointer = s.pointerId;
        }
        if (missingCoordinates > 0) text(c, "坐标缺测 " + missingCoordinates + " 点，轨迹在缺测处断开", 8, 176, MUTED, 10);
        stroke(c, path, ACCENT, 2);
        InteractionRecorder.Sample first = points.get(0), last = points.get(points.size() - 1);
        marker(c, first, ox, oy, scale, "下", Color.rgb(46, 112, 77));
        marker(c, last, ox, oy, scale, "末", MUTED);
        float[] location = cursorPosition();
        if (location != null) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3 * d); paint.setColor(INK);
            c.drawCircle(ox + location[0] * scale, oy + location[1] * scale, 7 * d, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        text(c, String.format(Locale.ROOT, "范围 x %.1f–%.1f / y %.1f–%.1f", minX, maxX, minY, maxY), 8, 191, MUTED, 11);
    }

    private void marker(Canvas c, InteractionRecorder.Sample s, float ox, float oy, float scale, String label, int color) {
        if (!finite(s.x) || !finite(s.y)) return;
        paint.setColor(color); paint.setStyle(Paint.Style.FILL);
        float x = ox + s.x * scale, y = oy + s.y * scale;
        c.drawCircle(x, y, 4 * d, paint);
        text(c, label, x / d - 3, y / d - 10, color, 11);
    }

    private void timeline(Canvas c, float top) {
        text(c, "阶段时间轴 · 相对按下（ms）", 8, top + 10, INK, 14);
        boolean manual = "manual".equals(trace.kind) || "reaction_manual".equals(trace.kind);
        String[] keys = manual ? new String[]{"down", "first_move", "target", "finish"}
                : "demo_assist".equals(trace.kind) ? new String[]{"down", "trigger", "animation_started", "finish"}
                : new String[]{"down", "trigger", "dispatch", "finish"};
        String[] labels = manual ? new String[]{"按下", "首次位移", "到达98%", "结束"}
                : "demo_assist".equals(trace.kind) ? new String[]{"按下", "触发", "动画开始", "结果"}
                : new String[]{"按下", "触发", "手势派发", "结果"};
        double span = Math.max(1, duration(trace));
        for (int i = 0; i < keys.length; i++) {
            Long absolute;
            if (i == 0) absolute = Long.valueOf(trace.downEventTimeMillis);
            else if (i == 3) absolute = trace.finishedUptimeMillis >= 0 ? Long.valueOf(trace.finishedUptimeMillis) : null;
            else absolute = trace.stages.get(keys[i]);
            float y = top + 33 + i * 21;
            text(c, labels[i], 8, y, MUTED, 11);
            line(c, 84, y - 4, getWidth() / d - 55, y - 4, GRID);
            if (absolute == null) { text(c, "未提供", getWidth() / d - 52, y, MUTED, 10); continue; }
            double time = absolute - trace.downEventTimeMillis;
            float x = 84 + (float) (Math.max(0, Math.min(1, time / span)) * (getWidth() / d - 139));
            line(c, x, y - 9, x, y + 1, ACCENT);
            text(c, String.format(Locale.ROOT, "%.0f", time), getWidth() / d - 52, y, INK, 10);
        }
    }

    private void curve(Canvas c, float top, String title, int metric) {
        text(c, title, 8, top + 12, INK, 13);
        List<InteractionRecorder.Sample> points = trace.samples;
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY; int missing = 0;
        for (int i = 0; i < points.size(); i++) { double v = value(trace, i, metric); if (Double.isFinite(v)) { min = Math.min(min, v); max = Math.max(max, v); } else missing++; }
        if (!Double.isFinite(min)) { text(c, "未提供 / 没有可用间隔", 8, top + 45, MUTED, 11); return; }
        text(c, String.format(Locale.ROOT, "%.2f → %.2f · 未提供/无有效间隔 %d 点", min, max, missing), 8, top + 29, MUTED, 10);
        double range = max - min;
        if (range == 0) { min -= .5; range = 1; }
        float left = 54, right = getWidth() / d - 10, base = top + 77, graphHeight = 42;
        line(c, left, top + 35, left, base, GRID); line(c, left, base, right, base, GRID);
        Path path = new Path(); boolean segment = false;
        double span = Math.max(1, duration(trace));
        for (int i = 0; i < points.size(); i++) {
            double v = value(trace, i, metric);
            if (!Double.isFinite(v)) { segment = false; continue; }
            float x = left + (float) ((points.get(i).eventTimeMillis - trace.downEventTimeMillis) / span * (right - left));
            float y = base - (float) ((v - min) / range * graphHeight);
            if (!segment) path.moveTo(x * d, y * d); else path.lineTo(x * d, y * d);
            segment = true;
        }
        stroke(c, path, ACCENT, 1.6f);
        float cursorX = left + (float) (cursor / span * (right - left));
        line(c, cursorX, top + 35, cursorX, base, INK);
        text(c, "0", left, top + 91, MUTED, 10);
        text(c, duration(trace) + " ms", Math.max(left, right - 55), top + 91, MUTED, 10);
    }

    static double value(InteractionRecorder.TraceSnapshot t, int i, int metric) {
        InteractionRecorder.Sample s = t.samples.get(i);
        if (metric == 2) return s.receivedUptimeMillis - s.eventTimeMillis;
        if (metric == 3) return s.pressure;
        if (i == 0) return Double.NaN;
        InteractionRecorder.Sample previous = t.samples.get(i - 1);
        long dt = s.eventTimeMillis - previous.eventTimeMillis;
        if (dt <= 0 || s.pointerId != previous.pointerId) return Double.NaN;
        return metric == 1 ? dt : Math.hypot(s.x - previous.x, s.y - previous.y) * 1000 / dt / Math.max(.01, t.density);
    }

    private float[] cursorPosition() {
        List<InteractionRecorder.Sample> points = trace.samples;
        InteractionRecorder.Sample previous = points.get(0);
        for (InteractionRecorder.Sample next : points) {
            if (next.eventTimeMillis - trace.downEventTimeMillis >= cursor) {
                if (!finite(next.x) || !finite(next.y) || !finite(previous.x) || !finite(previous.y)) return null;
                long dt = next.eventTimeMillis - previous.eventTimeMillis;
                if (dt <= 0 || next.pointerId != previous.pointerId)
                    return new float[]{next.x, next.y};
                float f = (float) Math.max(0, Math.min(1, (cursor + trace.downEventTimeMillis - previous.eventTimeMillis) / dt));
                return new float[]{previous.x + (next.x - previous.x) * f, previous.y + (next.y - previous.y) * f};
            }
            previous = next;
        }
        return finite(previous.x) && finite(previous.y) ? new float[]{previous.x, previous.y} : null;
    }
    private boolean finite(float n) { return !Float.isNaN(n) && !Float.isInfinite(n); }
    private void text(Canvas c, String label, float x, float y, int color, int size) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); paint.setTextSize(size * sp);
        c.drawText(label, x * d, y * d, paint);
    }
    private void line(Canvas c, float x, float y, float xx, float yy, int color) {
        paint.setStyle(Paint.Style.STROKE); paint.setColor(color); paint.setStrokeWidth(d);
        c.drawLine(x * d, y * d, xx * d, yy * d, paint); paint.setStyle(Paint.Style.FILL);
    }
    private void stroke(Canvas c, Path path, int color, float width) {
        paint.setStyle(Paint.Style.STROKE); paint.setColor(color); paint.setStrokeWidth(width * d);
        c.drawPath(path, paint); paint.setStyle(Paint.Style.FILL);
    }
}
