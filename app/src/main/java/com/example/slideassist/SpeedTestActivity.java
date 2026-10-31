package com.example.slideassist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.JsonWriter;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Explicit, process-local manual testing and report export. No rider-app interactions. */
public final class SpeedTestActivity extends Activity {
    private static final int INK = Color.rgb(35, 39, 42), MUTED = Color.rgb(96, 100, 101);
    private static final int ORANGE = Color.rgb(211, 89, 0), EXPORT_JSON = 71, EXPORT_CSV = 72;
    private static InteractionRecorder.Session frozen, pendingExport;
    private ManualSpeedSliderView slider;
    private TouchReplayView replay;
    private LinearLayout report;
    private TextView status, summary, details, table;
    private Spinner selection;
    private Button start, stop, json, csv, tableToggle;
    private int selected;
    private int displayedIndex = -1;
    private long displayedDown = -1;
    private boolean updatingSelection;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(28, 30, 31));
        getWindow().setNavigationBarColor(Color.rgb(28, 30, 31));
        if (Build.VERSION.SDK_INT >= 35) getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        selected = state == null ? -1 : state.getInt("selected", -1);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(244, 244, 241));
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets;
        });
        LinearLayout page = column(); page.setPadding(dp(16), dp(20), dp(16), dp(28));
        scroll.addView(page); setContentView(scroll);
        add(page, text("手动测试 · 本地报告", 13, ORANGE, true), 0);
        add(page, text("测一下你的手速", 26, INK, true), 4);
        add(page, text("只测本应用练习控件，不能代表真实订单接受或服务器处理耗时。手动测速不会触发任何自动辅助。", 14, MUTED, false), 8);
        LinearLayout practice = card(); add(page, practice, 18);
        add(practice, text("从白色手柄开始，手动拖到右端", 17, INK, true), 0);
        add(practice, text("到达 98% 行程后抬手算成功；每次结束后点击“重置 / 下一次”。最快成绩按下→首次到达终点计算。", 13, MUTED, false), 7);
        slider = new ManualSpeedSliderView(this);
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(-1, dp(68)); sliderParams.topMargin = dp(16);
        practice.addView(slider, sliderParams);
        Button reset = button("重置 / 下一次"); add(practice, reset, 8); reset.setOnClickListener(v -> slider.reset());
        status = text("", 14, INK, true); add(practice, status, 12);
        LinearLayout recordActions = new LinearLayout(this);
        start = button("开始新记录"); stop = button("停止并生成报告");
        recordActions.addView(start, new LinearLayout.LayoutParams(0, dp(52), 1));
        recordActions.addView(stop, new LinearLayout.LayoutParams(0, dp(52), 1)); add(practice, recordActions, 6);
        add(practice, text("开始前不采集；开始新记录会清空旧会话。仅在本机内存保存，进程退出会丢失。也可返回首页记录模拟辅助；统计会区分手动、模拟辅助和真实页辅助。", 12, MUTED, false), 8);
        start.setOnClickListener(v -> {
            slider.cancelAttempt(); InteractionRecorder.clear(); InteractionRecorder.start();
            frozen = null; selected = -1; slider.reset(); refresh();
        });
        stop.setOnClickListener(v -> {
            slider.cancelAttempt(); InteractionRecorder.stop(); frozen = InteractionRecorder.snapshot();
            selected = frozen.traces.size() - 1; refresh();
        });
        slider.setOnAttemptFinished(this::refresh);

        report = card(); add(page, report, 18);
        add(report, text("本次报告", 20, INK, true), 0);
        summary = text("", 14, INK, false); add(report, summary, 8);
        selection = new Spinner(this); selection.setContentDescription("选择一次记录查看轨迹和原始数据");
        add(report, selection, 12);
        selection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
                if (!updatingSelection && position != selected) { selected = position; selectTrace(); }
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        details = text("", 12, MUTED, false);
        TouchReportView chart = new TouchReportView(this);
        replay = new TouchReplayView(this, chart); add(report, replay, 12); add(report, chart, 10);
        add(report, details, 12);
        tableToggle = button("展开逐点数据"); add(report, tableToggle, 10);
        HorizontalScrollView tableScroll = new HorizontalScrollView(this);
        table = text("", 11, INK, false); table.setTypeface(Typeface.MONOSPACE);
        tableScroll.addView(table); tableScroll.setVisibility(View.GONE); add(report, tableScroll, 6);
        tableToggle.setOnClickListener(v -> {
            boolean show = tableScroll.getVisibility() != View.VISIBLE;
            tableScroll.setVisibility(show ? View.VISIBLE : View.GONE);
            tableToggle.setText(show ? "收起逐点数据" : "展开逐点数据");
        });
        LinearLayout exports = new LinearLayout(this); json = button("导出 JSON 原始数据"); csv = button("导出 CSV 点表");
        exports.addView(json, new LinearLayout.LayoutParams(0, dp(52), 1));
        exports.addView(csv, new LinearLayout.LayoutParams(0, dp(52), 1)); add(report, exports, 10);
        json.setOnClickListener(v -> export(EXPORT_JSON)); csv.setOnClickListener(v -> export(EXPORT_CSV));
        add(page, text("读数说明：应用接收滞后 = 应用收到事件时刻 − 事件时间，包含批处理、排队及调度，不能当作触控硬件延迟或网络 / 服务器耗时。采样间隔不等于触控硬件扫描频率。pressure 可能为固定或估算值，不能视为真实按压力；size、接触长短轴、角度也是设备原始估算值。人体电流：Android 未提供。", 12, MUTED, false), 16);
        refresh();
        if (state != null) replay.seekTo(state.getDouble("elapsed", 0));
    }

    @Override protected void onResume() { super.onResume(); if (slider != null) refresh(); }
    @Override protected void onPause() { slider.cancelAttempt(); replay.pause(); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("selected", selected); out.putDouble("elapsed", replay.getElapsedMillis()); super.onSaveInstanceState(out);
    }
    private void refresh() {
        boolean recording = InteractionRecorder.isRecording();
        InteractionRecorder.Session session = InteractionRecorder.snapshot();
        if (!recording) frozen = session;
        start.setEnabled(!recording); stop.setEnabled(recording);
        status.setText(recording ? "正在记录 · 已记录 " + session.traces.size() + " 次，" + session.totalSamples + " 个点"
                : "记录已停止 · " + session.traces.size() + " 次");
        report.setVisibility(!recording && !session.traces.isEmpty() ? View.VISIBLE : View.GONE);
        if (recording || session.traces.isEmpty()) { replay.pause(); return; }
        ReportStats stats = ReportStats.of(frozen);
        summary.setText("手动有效成功 " + stats.manualSuccessCount + " / " + stats.manualAttemptCount + " 次 · 成功率 "
                + (stats.manualAttemptCount == 0 ? "未提供" : String.format(Locale.ROOT, "%.1f%%", stats.manualSuccessCount * 100.0 / stats.manualAttemptCount)) + "\n"
                + "最快 " + ms(stats.fastestManualMillis) + " · 平均 " + ms(stats.meanManualMillis) + " · 中位 " + ms(stats.medianManualMillis)
                + " · P95 " + ms(stats.p95ManualMillis) + "\n仅手动成功且到终点时间有效的样本参与；辅助结果单列，不参与手速。\n"
                + "已保存 " + frozen.totalSamples + " 点；达到上限后未保存的次数 " + frozen.droppedTraces
                + "，会话总点数上限丢点 " + frozen.droppedTotalSamples + "。各次截断见详情。\n"
                + Build.MANUFACTURER + " " + Build.MODEL + " · Android API " + Build.VERSION.SDK_INT);
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < frozen.traces.size(); i++) {
            InteractionRecorder.TraceSnapshot t = frozen.traces.get(i);
            labels.add("第 " + (i + 1) + " 次 · " + kind(t.kind) + " · " + outcome(t.outcome));
        }
        selected = Math.max(0, Math.min(selected < 0 ? labels.size() - 1 : selected, labels.size() - 1));
        updatingSelection = true;
        selection.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        selection.setSelection(selected); updatingSelection = false; selectTrace();
    }
    private void selectTrace() {
        if (frozen == null || selected < 0 || selected >= frozen.traces.size()) return;
        InteractionRecorder.TraceSnapshot t = frozen.traces.get(selected);
        ReportStats.TraceMetrics m = ReportStats.metrics(t);
        StringBuilder stages = new StringBuilder();
        for (Map.Entry<String, Long> s : t.stages.entrySet())
            stages.append(s.getKey()).append(" +").append(s.getValue() - t.downEventTimeMillis).append(" ms；");
        details.setText(kind(t.kind) + " · " + outcome(t.outcome) + " · " + t.samples.size() + " 点\n"
                + "路径 " + number(m.pathDp) + " dp · 均速 " + number(m.meanSpeedDpPerSecond) + " dp/s · 峰值 " + number(m.maxSpeedDpPerSecond) + " dp/s\n"
                + "采样间隔 中位 / P95：" + ms(m.sampleGapMedianMillis) + " / " + ms(m.sampleGapP95Millis) + "\n"
                + "应用接收滞后 中位 / P95：" + ms(m.receiptLagMedianMillis) + " / " + ms(m.receiptLagP95Millis) + "\n"
                + "原始 pressure 均值 " + number(m.meanPressure) + " · size 均值 " + number(m.meanSize) + "\n"
                + "截断未保存 " + t.droppedSamples + " 点；重复省略 " + t.duplicateSamples + "；逆序舍弃 " + t.outOfOrderSamples
                + "；未保存阶段 " + t.droppedStages + "。每次最多1024点，会话最多20000点。\n"
                + "坐标为触摸控件内 px，密度 " + number(t.density) + " px/dp；时间为 uptime 相对按下。图和回放仅含已采集的手指输入，不是系统生成手势的运动轨迹。\n"
                + ("service_assist".equals(t.kind) ? "系统手势完成回调不等于订单被接受。\n" : "")
                + "action：0按下 / 1抬起 / 2移动 / 3取消 / 5增指 / 6减指；tool：0未知 / 1手指 / 2笔 / 3鼠标 / 4橡皮。\n阶段：" + (stages.length() == 0 ? "未提供" : stages));
        double elapsed = displayedIndex == selected && displayedDown == t.downEventTimeMillis ? replay.getElapsedMillis() : 0;
        replay.setTrace(t); replay.seekTo(elapsed); displayedIndex = selected; displayedDown = t.downEventTimeMillis;
        StringBuilder points = new StringBuilder("#  t(ms)  lag(ms)  x(px)  y(px)  pressure  size  major(px)  minor(px)  orientation(rad)  tool  pointer  action\n");
        for (int i = 0; i < t.samples.size(); i++) {
            InteractionRecorder.Sample s = t.samples.get(i);
            points.append(i + 1).append("  ").append(s.eventTimeMillis - t.downEventTimeMillis).append("  ")
                    .append(s.receivedUptimeMillis - s.eventTimeMillis).append("  ").append(number(s.x)).append("  ")
                    .append(number(s.y)).append("  ").append(number(s.pressure)).append("  ").append(number(s.size)).append("  ")
                    .append(number(s.touchMajor)).append("  ").append(number(s.touchMinor)).append("  ").append(number(s.orientation))
                    .append("  ").append(s.toolType).append("  ").append(s.pointerId).append("  ").append(s.action).append('\n');
        }
        table.setText(points.toString());
    }

    private void export(int request) {
        if (frozen == null || frozen.recording) return;
        pendingExport = frozen;
        // The system picker grants access only to the URI the user chooses.
        // https://developer.android.com/training/data-storage/shared/documents-files#create-file
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(request == EXPORT_JSON ? "application/json" : "text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "touch-report-" + System.currentTimeMillis() + (request == EXPORT_JSON ? ".json" : ".csv"));
        try { startActivityForResult(intent, request); }
        catch (android.content.ActivityNotFoundException e) { pendingExport = null; Toast.makeText(this, "设备没有可用的文档保存程序", Toast.LENGTH_LONG).show(); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != EXPORT_JSON && request != EXPORT_CSV) return;
        InteractionRecorder.Session payload = pendingExport; pendingExport = null;
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (payload == null) { Toast.makeText(this, "会话已丢失，请重新生成报告后导出", Toast.LENGTH_LONG).show(); return; }
        Context app = getApplicationContext(); android.net.Uri uri = data.getData();
        new Thread(() -> {
            String message;
            try (OutputStream stream = app.getContentResolver().openOutputStream(uri, "w")) {
                if (stream == null) throw new IOException("No output stream");
                try (Writer writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
                    if (request == EXPORT_JSON) writeJson(payload, writer); else writeCsv(payload, writer);
                }
                message = "报告已写入你选择的位置";
            } catch (IOException | RuntimeException e) { message = "导出未完成，请检查所选位置后重试"; }
            final String notification = message;
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(app, notification, Toast.LENGTH_LONG).show());
        }, "touch-report-export").start();
    }

    static void writeJson(InteractionRecorder.Session session, Writer destination) throws IOException {
        JsonWriter w = new JsonWriter(destination); w.setIndent("  "); w.beginObject();
        w.name("schemaVersion").value(1); w.name("appVersion").value(BuildConfig.VERSION_NAME);
        w.name("clock").value("Android uptime milliseconds; relative times subtract downEventTimeMillis");
        w.name("coordinateSpace").value("touch control local pixels");
        w.name("pressureMeaning").value("Android raw estimate; may be constant; not physical force");
        w.name("humanCurrent").nullValue(); w.name("hardwareLatency").nullValue(); w.name("serverLatency").nullValue();
        w.name("receiptLagMeaning").value("receivedUptimeMillis minus eventTimeMillis; includes batching, queueing, scheduling");
        w.name("device").value(Build.MANUFACTURER + " " + Build.MODEL); w.name("androidApi").value(Build.VERSION.SDK_INT);
        w.name("recording").value(session.recording); w.name("droppedTraces").value(session.droppedTraces);
        w.name("droppedTotalSamples").value(session.droppedTotalSamples); w.name("traces").beginArray();
        for (InteractionRecorder.TraceSnapshot t : session.traces) {
            w.beginObject(); w.name("kind").value(t.kind); w.name("outcome").value(t.outcome);
            w.name("downEventTimeMillis").value(t.downEventTimeMillis); w.name("receivedUptimeMillis").value(t.receivedUptimeMillis);
            w.name("finishedUptimeMillis"); if (t.finishedUptimeMillis < 0) w.nullValue(); else w.value(t.finishedUptimeMillis);
            w.name("density").value(t.density); w.name("droppedSamples").value(t.droppedSamples);
            w.name("duplicateSamples").value(t.duplicateSamples); w.name("outOfOrderSamples").value(t.outOfOrderSamples);
            w.name("droppedStages").value(t.droppedStages); w.name("stages").beginObject();
            for (Map.Entry<String, Long> entry : t.stages.entrySet()) w.name(entry.getKey()).value(entry.getValue());
            w.endObject(); w.name("samples").beginArray();
            for (InteractionRecorder.Sample s : t.samples) {
                w.beginObject(); w.name("eventTimeMillis").value(s.eventTimeMillis); w.name("receivedUptimeMillis").value(s.receivedUptimeMillis);
                field(w, "x", s.x); field(w, "y", s.y); field(w, "pressure", s.pressure); field(w, "size", s.size);
                field(w, "touchMajor", s.touchMajor); field(w, "touchMinor", s.touchMinor); field(w, "orientation", s.orientation);
                w.name("toolType").value(s.toolType); w.name("action").value(s.action); w.name("pointerId").value(s.pointerId); w.endObject();
            }
            w.endArray(); w.endObject();
        }
        w.endArray(); w.endObject(); w.flush();
    }
    private static void field(JsonWriter writer, String key, float value) throws IOException {
        writer.name(key); if (Float.isFinite(value)) writer.value(value); else writer.nullValue();
    }
    static void writeCsv(InteractionRecorder.Session session, Writer w) throws IOException {
        w.write("trace,kind,outcome,density,downEventTimeMillis,firstMoveEventTimeMillis,targetEventTimeMillis,finishedUptimeMillis,droppedSamples,point,eventTimeMillis,relativeMillis,receivedUptimeMillis,receiptLagMillis,x,y,pressure,size,touchMajor,touchMinor,orientation,toolType,pointerId,action\r\n");
        for (int i = 0; i < session.traces.size(); i++) {
            InteractionRecorder.TraceSnapshot t = session.traces.get(i);
            for (int p = 0; p < t.samples.size(); p++) {
                InteractionRecorder.Sample s = t.samples.get(p);
                w.write((i + 1) + "," + t.kind + "," + t.outcome + "," + t.density + "," + t.downEventTimeMillis + ","
                        + (t.stages.containsKey("first_move") ? t.stages.get("first_move") : "") + ","
                        + (t.stages.containsKey("target") ? t.stages.get("target") : "") + ","
                        + (t.finishedUptimeMillis >= 0 ? Long.toString(t.finishedUptimeMillis) : "") + "," + t.droppedSamples + "," + (p + 1) + "," + s.eventTimeMillis + ","
                        + (s.eventTimeMillis - t.downEventTimeMillis) + "," + s.receivedUptimeMillis + "," + (s.receivedUptimeMillis - s.eventTimeMillis));
                for (float value : new float[]{s.x, s.y, s.pressure, s.size, s.touchMajor, s.touchMinor, s.orientation})
                    w.write("," + (Float.isFinite(value) ? Float.toString(value) : ""));
                w.write("," + s.toolType + "," + s.pointerId + "," + s.action + "\r\n");
            }
        }
        w.flush();
    }
    private static String kind(String kind) { return "manual".equals(kind) ? "手动" : "demo_assist".equals(kind) ? "模拟辅助" : "真实页辅助"; }
    private static String outcome(String value) {
        switch (value) {
            case "success": return "手动成功";
            case "incomplete": return "未到终点";
            case "invalid_start": return "起点在手柄外";
            case "invalid_multitouch": return "多指无效";
            case "cancelled": return "已取消";
            case "stopped": return "记录中途停止";
            case "recording": return "记录中";
            case "animation_completed": return "模拟动画完成";
            case "completed": return "系统手势回调完成";
            case "rejected": return "手势未派发";
            case "timeout": return "手势回调超时";
            case "ignored": return "未触发辅助";
            case "tap_wait": return "等待后续点击";
            default: return value;
        }
    }
    private static String number(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.2f", value) : "未提供"; }
    private static String ms(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.1f ms", value) : "未提供"; }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout card() {
        LinearLayout v = column(); v.setPadding(dp(14), dp(16), dp(14), dp(16));
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.WHITE); background.setCornerRadius(dp(10)); v.setBackground(background); return v;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); v.setLineSpacing(dp(3), 1);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v;
    }
    private Button button(String label) { Button v = new Button(this); v.setText(label); v.setTextSize(13); v.setTextColor(INK); v.setMinHeight(dp(48)); return v; }
    private void add(LinearLayout parent, View child, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(margin); parent.addView(child, params);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
