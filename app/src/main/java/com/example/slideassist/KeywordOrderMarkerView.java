package com.example.slideassist;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Static color rail confined to one order card. It never covers text or handles input. */
public final class KeywordOrderMarkerView extends View {
    private final Paint paint = new Paint();
    private List<Integer> colors = Collections.emptyList();
    private boolean marked;

    public KeywordOrderMarkerView(Context context) {
        super(context);
        setClickable(false); setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setColors(List<Integer> values) {
        List<Integer> next = new ArrayList<>(3);
        if (values != null) for (Integer color : values)
            if (color != null && !next.contains(color) && next.size() < 3) next.add(color);
        if (colors.equals(next)) return;
        colors = Collections.unmodifiableList(next);
        invalidate();
    }

    public void setMarked(boolean enabled) {
        if (marked == enabled) return;
        marked = enabled;
        invalidate();
    }

    public boolean isMarked() { return marked && !colors.isEmpty(); }
    @Override public boolean dispatchTouchEvent(MotionEvent event) { return false; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!isMarked()) return;
        for (int index = 0; index < colors.size(); index++) {
            int top = Math.round(index * (float) getHeight() / colors.size());
            int bottom = Math.round((index + 1) * (float) getHeight() / colors.size());
            paint.setColor(colors.get(index));
            canvas.drawRect(0, top, getWidth(), bottom, paint);
        }
    }
}
