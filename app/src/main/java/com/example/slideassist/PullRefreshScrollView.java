package com.example.slideassist;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ScrollView;

/** A top-edge, release-to-refresh gesture; a child slider's interception request wins. */
public final class PullRefreshScrollView extends ScrollView {
    public interface Listener { void onPull(float fraction); void onRefresh(); }
    private final float threshold, slop;
    private Listener listener;
    private float downX, downY, pull;
    private boolean eligible, pulling, childOwnsGesture;

    public PullRefreshScrollView(Context context) {
        super(context);
        threshold = 96 * getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        setFillViewport(true);
    }
    public void setListener(Listener value) { listener = value; }
    @Override public void requestDisallowInterceptTouchEvent(boolean disallow) {
        childOwnsGesture = disallow;
        // A primary POINTER_UP may release interception while another finger is still down.
        // Once a child claimed this sequence, only a fresh DOWN can make it refresh-eligible.
        if (disallow) eligible = false;
        // A valid white-handle drag must never turn into a refresh gesture.
        // https://developer.android.com/reference/android/widget/ScrollView#requestDisallowInterceptTouchEvent(boolean)
        super.requestDisallowInterceptTouchEvent(disallow);
    }
    private void begin(MotionEvent e) {
        downX = e.getX(); downY = e.getY(); pull = 0;
        eligible = getScrollY() <= 0; pulling = childOwnsGesture = false;
    }
    private boolean detect(MotionEvent e) {
        if (!eligible || childOwnsGesture || e.getPointerCount() != 1) return false;
        float dy = e.getY() - downY, dx = Math.abs(e.getX() - downX);
        if (!pulling && dy > slop && dy > dx) pulling = true;
        if (pulling) {
            pull = Math.max(0, dy);
            if (listener != null) listener.onPull(Math.min(1, pull / threshold));
        }
        return pulling;
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) begin(e);
        if (e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) eligible = false;
        if (e.getActionMasked() == MotionEvent.ACTION_MOVE && detect(e)) return true;
        return super.onInterceptTouchEvent(e);
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) begin(e);
        if (action == MotionEvent.ACTION_POINTER_DOWN) { eligible = pulling = false; }
        if (action == MotionEvent.ACTION_MOVE && detect(e)) return true;
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean handled = pulling;
            boolean refresh = action == MotionEvent.ACTION_UP && eligible && pulling && pull >= threshold;
            pulling = eligible = false; pull = 0;
            if (listener != null) {
                listener.onPull(0);
                if (refresh) listener.onRefresh();
            }
            if (handled) return true;
            if (action == MotionEvent.ACTION_UP && Math.abs(e.getX() - downX) <= slop
                    && Math.abs(e.getY() - downY) <= slop) performClick();
        }
        return super.onTouchEvent(e);
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
