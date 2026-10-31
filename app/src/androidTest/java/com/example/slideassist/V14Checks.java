package com.example.slideassist;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import java.io.StringWriter;
import org.json.JSONObject;

/** Device checks of the local measurement screen; generated input is test data, not human results. */
public final class V14Checks {
    public static void run(Instrumentation i, Bundle result) throws Exception {
        InteractionRecorder.stop(); InteractionRecorder.clear();
        Intent launch = new Intent(i.getTargetContext(), SpeedTestActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity a = i.startActivitySync(launch); i.waitForIdleSync();
        ManualSpeedSliderView slider = find(a, ManualSpeedSliderView.class);
        check(slider != null, "manual slider missing"); show(i, slider);
        drag(i, slider, false);
        check(InteractionRecorder.snapshot().traces.isEmpty(), "opt-in must precede collection");
        click(i, a, "开始新记录");
        drag(i, slider, true);
        click(i, a, "重置 / 下一次");
        drag(i, slider, false);
        InteractionRecorder.Session measured = InteractionRecorder.snapshot();
        check(measured.traces.size() == 2, "expected invalid and manual-success attempts");
        check("invalid_start".equals(measured.traces.get(0).outcome), "orange start must be invalid");
        InteractionRecorder.TraceSnapshot success = measured.traces.get(1);
        check("success".equals(success.outcome) && success.stages.containsKey("target"), "manual target not measured");
        check(success.samples.size() >= 4, "manual samples incomplete");
        check(success.stages.get("target") > success.downEventTimeMillis, "manual duration must come from input time");
        // Add a single-point auxiliary trace to exercise missing manual-stage chart branches.
        long t = SystemClock.uptimeMillis();
        InteractionRecorder.Trace auxiliary = InteractionRecorder.begin("service_assist", t, t, 1);
        auxiliary.add(new InteractionRecorder.Sample(t,t,25,35,Float.NaN,Float.NaN,0,0,0,1,0,0));
        auxiliary.mark("trigger", t+4); auxiliary.mark("dispatch",t+20); auxiliary.mark("completed",t+120);
        auxiliary.finish("completed",t+120);
        click(i, a, "停止并生成报告");
        InteractionRecorder.Session frozen = InteractionRecorder.snapshot();
        ReportStats stats = ReportStats.of(frozen);
        check(!frozen.recording && stats.manualAttemptCount == 2 && stats.manualSuccessCount == 1,
                "auxiliary and invalid records must not win manual speed");
        check(stats.fastestManualMillis == success.stages.get("target")-success.downEventTimeMillis,
                "fastest time must use target timestamp, not replay/animation");
        StringWriter json = new StringWriter(); SpeedTestActivity.writeJson(frozen, json);
        JSONObject exported = new JSONObject(json.toString());
        check(exported.getJSONArray("traces").length() == 3, "JSON must retain all trace types");
        check(exported.getJSONArray("traces").getJSONObject(2).getJSONArray("samples")
                .getJSONObject(0).isNull("pressure"), "unavailable raw pressure must export null");
        StringWriter csv = new StringWriter(); SpeedTestActivity.writeCsv(frozen,csv);
        check(csv.toString().contains("density") && csv.toString().contains("target"), "CSV lacks measurement context");
        TouchReportView chart = find(a,TouchReportView.class);
        TouchReplayView replay = find(a,TouchReplayView.class);
        check(chart != null && replay != null, "charts or replay missing");
        for (InteractionRecorder.TraceSnapshot trace : frozen.traces) {
            i.runOnMainSync(() -> {
                replay.setTrace(trace); replay.seekTo(TouchReportView.duration(trace)/2.0);
                int width = 900, height = 2200;
                chart.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
                chart.layout(0,0,width,height);
                Bitmap bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
                chart.draw(new Canvas(bitmap)); bitmap.recycle();
            });
        }
        check(TouchReportView.duration(frozen.traces.get(2)) >= 120, "auxiliary stages must extend replay duration");
        // Return to the Activity's selected final trace, then use real replay controls.
        i.runOnMainSync(() -> { replay.setTrace(frozen.traces.get(2)); replay.seekTo(20); });
        click(i,a,"播放轨迹"); SystemClock.sleep(180);
        double[] position = {0}; i.runOnMainSync(() -> { replay.pause(); position[0]=replay.getElapsedMillis(); });
        check(position[0] > 20, "replay cursor must advance by recorded event time");
        i.runOnMainSync(() -> replay.seekTo(35));
        Instrumentation.ActivityMonitor monitor = i.addMonitor(SpeedTestActivity.class.getName(),null,false);
        Activity before = a; i.runOnMainSync(before::recreate);
        a = monitor.waitForActivityWithTimeout(5000); i.removeMonitor(monitor); i.waitForIdleSync();
        check(a != null && a != before, "report Activity did not recreate");
        TouchReplayView restored = find(a,TouchReplayView.class);
        double[] cursor = {0}; i.runOnMainSync(() -> cursor[0]=restored.getElapsedMillis());
        check(Math.abs(cursor[0]-35)<2, "rotation must preserve replay cursor");
        check(InteractionRecorder.snapshot().traces.size()==3, "rotation lost captured session");
        Activity finalActivity = a; i.runOnMainSync(finalActivity::finish); i.waitForIdleSync();
        InteractionRecorder.clear(); InteractionRecorder.stop();
        result.putString("result", "PASS v1.4 measurement: opt-in, white-handle manual success/orange rejection, raw points and target time, manual/aux separation, JSON null and CSV context, all charts including missing stages, playback/seek/rotation, no gesture or installer dispatched");
    }

    private static void drag(Instrumentation i, ManualSpeedSliderView v, boolean invalid) {
        show(i,v); float[] origin = new float[2], point = new float[4];
        i.runOnMainSync(() -> {
            int[] p = new int[2]; v.getLocationOnScreen(p); origin[0]=p[0]; origin[1]=p[1];
            SliderGeometry g=new SliderGeometry(v.getWidth(),v.getHeight(),v.getResources().getDisplayMetrics().density);
            point[0]=invalid ? v.getWidth()*.6f : g.thumbLeft()+g.thumbWidth()*.3f;
            point[1]=(g.thumbTop()+g.thumbBottom())/2f; point[2]=g.travel();
        });
        long down=SystemClock.uptimeMillis(); send(i,down,MotionEvent.ACTION_DOWN,origin[0]+point[0],origin[1]+point[1]);
        if (!invalid) {
            SystemClock.sleep(45); send(i,down,MotionEvent.ACTION_MOVE,origin[0]+point[0]+point[2]*.45f,origin[1]+point[1]-12);
            SystemClock.sleep(50); send(i,down,MotionEvent.ACTION_MOVE,origin[0]+point[0]+point[2],origin[1]+point[1]+8);
        }
        SystemClock.sleep(35); send(i,down,MotionEvent.ACTION_UP,origin[0]+point[0]+(invalid?0:point[2]),origin[1]+point[1]+(invalid?0:8));
        i.waitForIdleSync();
    }
    private static void send(Instrumentation i,long down,int action,float x,float y) {
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try { i.sendPointerSync(event); } finally { event.recycle(); }
    }
    private static void show(Instrumentation i,View view) {
        i.runOnMainSync(() -> {
            android.view.ViewParent p=view.getParent();
            while(p != null && !(p instanceof ScrollView)) p=p.getParent();
            if(p instanceof ScrollView) {
                ScrollView scroll=(ScrollView)p; Rect rect=new Rect(); view.getDrawingRect(rect);
                scroll.offsetDescendantRectToMyCoords(view,rect); scroll.scrollTo(0,Math.max(0,rect.top-40));
            }
        }); i.waitForIdleSync();
    }
    private static void click(Instrumentation i,Activity a,String text) {
        Button button=findButton(a.getWindow().getDecorView(),text); check(button!=null,"missing control: "+text);
        i.runOnMainSync(button::performClick); i.waitForIdleSync();
    }
    private static Button findButton(View v,String text) {
        if(v instanceof Button && text.contentEquals(((Button)v).getText())) return (Button)v;
        if(v instanceof ViewGroup) for(int j=0;j<((ViewGroup)v).getChildCount();j++) {
            Button found=findButton(((ViewGroup)v).getChildAt(j),text); if(found!=null)return found;
        }
        return null;
    }
    private static <T> T find(Activity a,Class<T> type) { return find(a.getWindow().getDecorView(),type); }
    private static <T> T find(View v,Class<T> type) {
        if(type.isInstance(v))return type.cast(v);
        if(v instanceof ViewGroup)for(int j=0;j<((ViewGroup)v).getChildCount();j++){
            T found=find(((ViewGroup)v).getChildAt(j),type);if(found!=null)return found;
        }
        return null;
    }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
