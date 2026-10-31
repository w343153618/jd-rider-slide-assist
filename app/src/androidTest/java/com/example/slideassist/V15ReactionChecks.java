package com.example.slideassist;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.text.Layout;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.StringWriter;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONObject;

/** Synthetic native input checks: these measurements are not human benchmark results. */
public final class V15ReactionChecks {
    public static void run(Instrumentation i, Bundle result) throws Exception {
        InteractionRecorder.stop(); InteractionRecorder.clear(); InteractionRecorder.start();
        ReactionTestActivity a = (ReactionTestActivity)i.startActivitySync(new Intent(i.getTargetContext(),
                ReactionTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        i.waitForIdleSync();
        Button back = button(a.getWindow().getDecorView(), "返回模拟抢单");
        check(back != null && back.isShown(), "pinned return missing");
        ManualSpeedSliderView preview = slider(a.getWindow().getDecorView(), 0);
        drag(i, preview);
        check(InteractionRecorder.snapshot().traces.isEmpty(), "preview must not collect before opt-in");
        click(i, a, "开始测试"); i.waitForIdleSync(); SystemClock.sleep(70);
        ReactionSession.Snapshot before = a.measurementSnapshot();
        check(before.rounds.size() == 1 && before.rounds.get(0).presentedTime >= 0, "presentation not marked");
        ReactionOrderGenerator.Batch batch = before.rounds.get(0).batch;
        assertThreeOrdersFitViewport(i, a, batch);
        assertMarkerVisibility(i, a, batch);
        drag(i, slider(a.getWindow().getDecorView(), (batch.targetIndex + 1) % batch.orders.size()));
        assertThreeOrdersFitViewport(i, a, batch);
        drag(i, slider(a.getWindow().getDecorView(), batch.targetIndex));
        ReactionSession.Round completed = a.measurementSnapshot().rounds.get(0);
        check(completed.wrongAttempts == 1 && completed.winner() != null, "wrong / correct scoring broken");
        check(completed.searchMillis() >= 0 && completed.dragMillis() > 0, "separate timing missing");
        assertThreeOrdersFitViewport(i, a, batch);
        SystemClock.sleep(250);
        drag(i, slider(a.getWindow().getDecorView(), batch.targetIndex));
        check(a.measurementSnapshot().rounds.get(0).attempts.get(2).practice,
                "same-batch repeated success must remain unscored practice");
        PullRefreshScrollView scroll = find(a.getWindow().getDecorView(), PullRefreshScrollView.class);
        i.runOnMainSync(() -> scroll.scrollTo(0, 0)); i.waitForIdleSync();
        check(back.isShown() && back.getGlobalVisibleRect(new Rect()), "return must stay visible when scrolling");
        ownedPull(i, scroll);
        check(a.measurementSnapshot().rounds.size() == 1,
                "a child-owned gesture must not refresh after an early interception release");
        pull(i, scroll); i.waitForIdleSync(); SystemClock.sleep(70);
        ReactionSession.Snapshot refreshed = a.measurementSnapshot();
        check(refreshed.rounds.size() == 2, "pull-to-refresh did not begin next round");
        ReactionOrderGenerator.Batch next = refreshed.rounds.get(1).batch;
        assertThreeOrdersFitViewport(i, a, next);
        int markerCount = 0;
        for (int n = 0; n < next.orders.size(); n++) {
            ReactionOrderGenerator.Order old = batch.orders.get(n), fresh = next.orders.get(n);
            check(!old.title.equals(fresh.title) && !old.address.equals(fresh.address) && !old.note.equals(fresh.note),
                    "every generated field must change");
            for (String s : new String[]{fresh.title, fresh.address, fresh.note})
                for (int p = 0; p < s.length(); p++) if (s.charAt(p) == '田') markerCount++;
        }
        check(markerCount == 1, "batch must contain exactly one marker");
        click(i, a, "停止并报告");
        SystemClock.sleep(100);
        // Native view rendering for layout inspection; not a physical display or human timing capture.
        Bitmap[] rendered = {null};
        i.runOnMainSync(() -> {
            View decor = a.getWindow().getDecorView();
            rendered[0] = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
            decor.draw(new Canvas(rendered[0]));
        });
        try (FileOutputStream file = new FileOutputStream(new File(i.getTargetContext().getExternalFilesDir(null), "v15-reaction-report.png"))) {
            rendered[0].compress(Bitmap.CompressFormat.PNG, 100, file);
        } finally { rendered[0].recycle(); }
        StringWriter json = new StringWriter(); ReactionExport.writeJson(a.measurementSnapshot(), json);
        JSONObject parsed = new JSONObject(json.toString());
        check(parsed.getJSONArray("rounds").length() == 2, "round metadata export missing");
        StringWriter csv = new StringWriter(); ReactionExport.writeCsv(a.measurementSnapshot(), csv);
        check(csv.toString().contains("searchMillis") && csv.toString().contains("targetIndex"), "comparison CSV missing");
        exportMissingValues();
        Instrumentation.ActivityMonitor monitor = i.addMonitor(ReactionTestActivity.class.getName(), null, false);
        i.runOnMainSync(a::recreate);
        ReactionTestActivity restored = (ReactionTestActivity)monitor.waitForActivityWithTimeout(5000);
        i.removeMonitor(monitor); i.waitForIdleSync();
        check(restored != null && restored.measurementSnapshot().rounds.size() == 2, "rotation lost local session");
        Instrumentation.ActivityMonitor main = i.addMonitor(MainActivity.class.getName(), null, false);
        click(i, restored, "返回模拟抢单");
        Activity returned = main.waitForActivityWithTimeout(5000); i.removeMonitor(main);
        check(returned != null, "visible return did not open simulation");
        i.runOnMainSync(returned::finish); InteractionRecorder.stop(); InteractionRecorder.clear();
        result.putString("result", "PASS reaction: opt-in, pinned return, wrong/correct/once-per-round scoring, manual timing, pull refresh with changed fields and one marker, JSON/CSV, rotation and return");
    }
    private static void assertThreeOrdersFitViewport(Instrumentation i, ReactionTestActivity a,
                                                    ReactionOrderGenerator.Batch batch) {
        Throwable[] failure = {null};
        i.runOnMainSync(() -> {
            try {
            check(batch.orders.size() == 3, "reaction page must contain exactly three orders");
            check(batch.targetIndex < 2 && "address".equals(batch.targetField),
                    "target must be an address character in the first or second order");
            PullRefreshScrollView scroll = find(a.getWindow().getDecorView(), PullRefreshScrollView.class);
            check(scroll != null && scroll.getScrollY() == 0, "three-order page must begin without scrolling");
            Rect viewport = new Rect(); check(scroll.getGlobalVisibleRect(viewport), "practice viewport missing");
            for (int n = 0; n < 3; n++) {
                ManualSpeedSliderView slider = slider(a.getWindow().getDecorView(), n);
                check(slider != null, "expected manual slider " + n);
                check(slider.getHeight() >= Math.round(48 * slider.getResources().getDisplayMetrics().density),
                        "manual slider must keep a 48dp touch surface");
                View card = (View) slider.getParent();
                int[] origin = new int[2]; card.getLocationOnScreen(origin);
                Rect full = new Rect(origin[0], origin[1], origin[0] + card.getWidth(), origin[1] + card.getHeight());
                check(viewport.contains(full), "all three complete order cards must fit one screen; order="
                        + n + " card=" + full + " viewport=" + viewport);
                TextView address = findText(card, batch.orders.get(n).address);
                check(address != null && address.getLayout() != null, "address must be rendered in full");
                for (int line = 0; line < address.getLayout().getLineCount(); line++)
                    check(address.getLayout().getEllipsisCount(line) == 0, "address or target may not be ellipsized");
                check(address.getHeight() - address.getTotalPaddingTop() - address.getTotalPaddingBottom()
                                >= address.getLayout().getLineBottom(address.getLayout().getLineCount() - 1),
                        "address lines must not be clipped");
                check(address.getTextSize() / address.getResources().getDisplayMetrics().scaledDensity >= 12,
                        "address font must remain readable");
            }
            check(slider(a.getWindow().getDecorView(), 3) == null, "a fourth order must not be created");
            } catch (Throwable error) { failure[0] = error; }
        });
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }
    private static void drag(Instrumentation i, ManualSpeedSliderView slider) {
        check(slider != null, "order slider missing");
        show(i, slider); SystemClock.sleep(220);
        i.runOnMainSync(() -> {
            SliderGeometry g = new SliderGeometry(slider.getWidth(), slider.getHeight(), slider.getResources().getDisplayMetrics().density);
            float x = g.thumbLeft() + g.thumbWidth() * .3f, y = (g.thumbTop() + g.thumbBottom()) / 2;
            long down = SystemClock.uptimeMillis();
            dispatch(slider, down, down, MotionEvent.ACTION_DOWN, x, y);
            dispatch(slider, down, down + 40, MotionEvent.ACTION_MOVE, x + g.travel() * .4f, y - 9);
            dispatch(slider, down, down + 90, MotionEvent.ACTION_MOVE, x + g.travel(), y + 5);
            dispatch(slider, down, down + 120, MotionEvent.ACTION_UP, x + g.travel(), y + 5);
        }); i.waitForIdleSync();
    }
    private static void pull(Instrumentation i, PullRefreshScrollView scroll) {
        i.runOnMainSync(() -> {
            float d = scroll.getResources().getDisplayMetrics().density;
            long t = SystemClock.uptimeMillis();
            dispatch(scroll, t, t, MotionEvent.ACTION_DOWN, 100 * d, 20 * d);
            dispatch(scroll, t, t + 80, MotionEvent.ACTION_MOVE, 100 * d, 150 * d);
            dispatch(scroll, t, t + 160, MotionEvent.ACTION_UP, 100 * d, 150 * d);
        }); i.waitForIdleSync();
    }
    private static void ownedPull(Instrumentation i, PullRefreshScrollView scroll) {
        i.runOnMainSync(() -> {
            float d = scroll.getResources().getDisplayMetrics().density;
            long t = SystemClock.uptimeMillis();
            dispatch(scroll, t, t, MotionEvent.ACTION_DOWN, 100*d, 20*d);
            // A slider can release interception on primary POINTER_UP while another finger remains.
            scroll.requestDisallowInterceptTouchEvent(true);
            scroll.requestDisallowInterceptTouchEvent(false);
            dispatch(scroll, t, t+60, MotionEvent.ACTION_MOVE, 100*d, 150*d);
            dispatch(scroll, t, t+120, MotionEvent.ACTION_UP, 100*d, 150*d);
        }); i.waitForIdleSync();
    }
    private static void assertMarkerVisibility(Instrumentation i, ReactionTestActivity a, ReactionOrderGenerator.Batch batch) {
        ReactionOrderGenerator.Order order = batch.orders.get(batch.targetIndex);
        String value = "title".equals(batch.targetField) ? order.title : "address".equals(batch.targetField) ? order.address : order.note;
        TextView marker = findText(a.getWindow().getDecorView(), value);
        check(marker != null, "target field text not rendered");
        boolean[] initiallyVisible = {false};
        i.runOnMainSync(() -> {
            Layout layout = marker.getLayout(); int line = layout.getLineForOffset(batch.targetOffset);
            int[] origin = new int[2]; marker.getLocationOnScreen(origin);
            float x1 = layout.getPrimaryHorizontal(batch.targetOffset), x2 = layout.getPrimaryHorizontal(batch.targetOffset+1);
            Rect glyph = new Rect(origin[0]+marker.getTotalPaddingLeft()+(int)Math.min(x1,x2),
                    origin[1]+marker.getTotalPaddingTop()+layout.getLineTop(line),
                    origin[0]+marker.getTotalPaddingLeft()+(int)Math.max(x1,x2)+1,
                    origin[1]+marker.getTotalPaddingTop()+layout.getLineBottom(line));
            Rect visible = new Rect(); initiallyVisible[0] = marker.getGlobalVisibleRect(visible) && glyph.intersect(visible);
        });
        if (!initiallyVisible[0]) check(a.measurementSnapshot().rounds.get(0).targetVisibleTime < 0,
                "an offscreen marker must not be counted as visible merely because its card exists");
        show(i, marker);
        check(a.measurementSnapshot().rounds.get(0).targetVisibleTime >= beforePresentation(a), "visible glyph timestamp missing");
    }
    private static long beforePresentation(ReactionTestActivity a) { return a.measurementSnapshot().rounds.get(0).presentedTime; }
    private static void exportMissingValues() throws Exception {
        ReactionSession s = new ReactionSession(); s.beginRound(ReactionOrderGenerator.generate(42,null)); s.markPresented(10);
        InteractionRecorder.clear(); InteractionRecorder.start();
        InteractionRecorder.Trace trace = InteractionRecorder.begin("reaction_manual",20,20,1);
        trace.add(new InteractionRecorder.Sample(20,20,2,3,Float.NaN,Float.NaN,0,0,0,1,0,0)); trace.finish("incomplete",40);
        s.recordAttempt(0,20,-1,-1,40,true,"incomplete",trace.snapshot()); InteractionRecorder.stop();
        StringWriter output = new StringWriter(); ReactionExport.writeJson(s.snapshot(),output);
        JSONObject round = new JSONObject(output.toString()).getJSONArray("rounds").getJSONObject(0);
        check(round.isNull("targetVisibleTime") && round.isNull("totalMillis"), "missing clocks / timing must remain null");
        JSONObject point = round.getJSONArray("attempts").getJSONObject(0).getJSONObject("trace").getJSONArray("samples").getJSONObject(0);
        check(point.isNull("pressure") && point.isNull("size"), "missing raw values must remain null");
    }
    private static void dispatch(View v, long down, long time, int action, float x, float y) {
        MotionEvent e = MotionEvent.obtain(down, time, action, x, y, 0);
        try { v.dispatchTouchEvent(e); } finally { e.recycle(); }
    }
    private static void show(Instrumentation i, View v) {
        i.runOnMainSync(() -> {
            android.view.ViewParent p = v.getParent();
            while (p != null && !(p instanceof ScrollView)) p = p.getParent();
            if (p instanceof ScrollView) {
                ScrollView s = (ScrollView)p; Rect r = new Rect(); v.getDrawingRect(r);
                s.offsetDescendantRectToMyCoords(v, r); s.scrollTo(0, Math.max(0, r.top - 30));
            }
        }); i.waitForIdleSync();
    }
    private static void click(Instrumentation i, Activity a, String label) {
        Button b = button(a.getWindow().getDecorView(), label); check(b != null, "missing " + label);
        i.runOnMainSync(b::performClick); i.waitForIdleSync();
    }
    private static Button button(View v, String label) {
        if (v instanceof Button && label.contentEquals(((Button)v).getText())) return (Button)v;
        if (v instanceof ViewGroup) for (int n = 0; n < ((ViewGroup)v).getChildCount(); n++) {
            Button found = button(((ViewGroup)v).getChildAt(n), label); if (found != null) return found;
        } return null;
    }
    private static TextView findText(View v, String value) {
        if (v instanceof TextView && value.contentEquals(((TextView)v).getText())) return (TextView)v;
        if (v instanceof ViewGroup) for (int n=0;n<((ViewGroup)v).getChildCount();n++) {
            TextView found=findText(((ViewGroup)v).getChildAt(n),value);if(found!=null)return found;
        }return null;
    }
    private static ManualSpeedSliderView slider(View v, int index) {
        if (v instanceof ManualSpeedSliderView && Integer.valueOf(index).equals(v.getTag())) return (ManualSpeedSliderView)v;
        if (v instanceof ViewGroup) for (int n = 0; n < ((ViewGroup)v).getChildCount(); n++) {
            ManualSpeedSliderView found = slider(((ViewGroup)v).getChildAt(n), index); if (found != null) return found;
        } return null;
    }
    private static <T> T find(View v, Class<T> type) {
        if (type.isInstance(v)) return type.cast(v);
        if (v instanceof ViewGroup) for (int n = 0; n < ((ViewGroup)v).getChildCount(); n++) {
            T found = find(((ViewGroup)v).getChildAt(n), type); if (found != null) return found;
        } return null;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
