package com.example.slideassist;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Recorded round timing only. Gaps represent abandoned or unsuccessful rounds. */
public final class ReactionTrendView extends View {
    private static final int SEARCH = Color.rgb(40, 108, 167), DRAG = Color.rgb(211, 89, 0);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<ReactionSession.Round> rounds = Collections.emptyList();
    private final float density;
    public ReactionTrendView(Context context) {
        super(context); density = getResources().getDisplayMetrics().density;
        setMinimumHeight((int)(240 * density));
    }
    public void setSession(ReactionSession.Snapshot snapshot) {
        rounds = snapshot.rounds;
        StringBuilder description = new StringBuilder("各轮搜索和滑动耗时，单位毫秒。");
        for (int i = 0; i < rounds.size(); i++) {
            ReactionSession.Round r = rounds.get(i);
            description.append("第").append(i + 1).append("轮：");
            if (r.winner() == null) description.append("无计分成绩；");
            else description.append(String.format(Locale.ROOT, "搜索%.1f，滑动%.1f；", r.searchMillis(), r.dragMillis()));
        }
        setContentDescription(description.toString()); invalidate();
    }
    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), resolveSize(Math.round(240 * density), h));
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        paint.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
        paint.setStrokeWidth(2 * density); paint.setColor(Color.rgb(55, 60, 62));
        c.drawText("逐轮独立耗时对比", 0, 18 * density, paint);
        paint.setColor(SEARCH); c.drawText("● 搜索", 0, 40 * density, paint);
        paint.setColor(DRAG); c.drawText("● 滑动", 92 * density, 40 * density, paint);
        paint.setColor(Color.rgb(65, 76, 80)); c.drawText("● 总计", 184 * density, 40 * density, paint);
        float left = 58 * density, right = getWidth() - 12 * density;
        float top = 62 * density, bottom = getHeight() - 34 * density;
        double max = 1;
        for (ReactionSession.Round r : rounds) if (r.winner() != null) max = Math.max(max, r.totalMillis());
        paint.setColor(Color.rgb(115, 120, 122));
        for (int row = 0; row <= 3; row++) {
            float y = bottom - (bottom - top) * row / 3f;
            paint.setColor(Color.rgb(223, 226, 225)); c.drawLine(left, y, right, y, paint);
            paint.setColor(Color.rgb(96, 100, 101));
            c.drawText(String.format(Locale.ROOT, "%.0f", max * row / 3), 0, y + 4 * density, paint);
        }
        if (rounds.isEmpty()) { c.drawText("完成一轮后显示实测对比", left, (top + bottom) / 2, paint); return; }
        float slot = (right - left) / rounds.size();
        for (int i = 0; i < rounds.size(); i++) {
            ReactionSession.Round r = rounds.get(i); float x = left + slot * (i + .5f);
            if (r.winner() != null) {
                float offset = Math.min(8 * density, slot * .22f);
                paint.setStrokeWidth(Math.max(density, offset * .7f));
                double[] values = {r.searchMillis(), r.dragMillis(), r.totalMillis()};
                int[] colors = {SEARCH, DRAG, Color.rgb(65, 76, 80)};
                for (int j = 0; j < 3; j++) {
                    float y = bottom - (float)(values[j] / max) * (bottom - top);
                    float bx = x + (j - 1) * offset;
                    paint.setColor(colors[j]); c.drawLine(bx, bottom, bx, y, paint);
                }
            } else {
                paint.setColor(Color.GRAY); paint.setStrokeWidth(density);
                c.drawLine(x - 3 * density, bottom - 6 * density, x + 3 * density, bottom, paint);
                c.drawLine(x + 3 * density, bottom - 6 * density, x - 3 * density, bottom, paint);
            }
            if (rounds.size() <= 12 || i == 0 || i == rounds.size() - 1 || (i + 1) % 5 == 0) {
                paint.setTextAlign(Paint.Align.CENTER); paint.setColor(Color.rgb(96, 100, 101));
                c.drawText(Integer.toString(i + 1), x, bottom + 20 * density, paint);
                paint.setTextAlign(Paint.Align.LEFT);
            }
        }
    }
}
