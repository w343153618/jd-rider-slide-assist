package com.example.slideassist;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only presentation of bounded reaction trials. Playback never emits touch input. */
final class ReactionReportPanel extends LinearLayout {
    private static final int INK = Color.rgb(35,39,42), MUTED = Color.rgb(96,100,101);
    private final TextView summary, distributions, comparison, roundTable, details, raw;
    private final ReactionTrendView trend;
    private final Spinner selection;
    private final TouchReplayView replay;
    private final List<ReactionSession.Attempt> attempts = new ArrayList<>();
    private final List<ReactionSession.Round> attemptRounds = new ArrayList<>();
    private int selected = -1;
    private boolean updating;

    ReactionReportPanel(Context context) {
        super(context); setOrientation(VERTICAL);
        add(text("反应与滑动报告",22,true),0);
        summary = text("点击开始测试后生成本地报告。",14,false); add(summary,8);
        trend = new ReactionTrendView(context); add(trend,16);
        add(text("分阶段统计（毫秒）",17,true),12);
        distributions = mono(); add(scroller(distributions),8);
        comparison = text("",13,false); add(comparison,12);
        add(text("每轮对照",17,true),16); roundTable = mono(); add(scroller(roundTable),8);
        add(text("选择一次操作查看轨迹",17,true),20);
        selection = new Spinner(context); selection.setContentDescription("选择一轮中的手动滑动记录"); add(selection,4);
        TouchReportView chart = new TouchReportView(context);
        replay = new TouchReplayView(context,chart); add(replay,8); add(chart,8);
        details = text("",12,false); add(details,12);
        Button toggle = new Button(context); toggle.setText("展开逐点原始数据"); toggle.setMinHeight(dp(48)); add(toggle,8);
        raw = mono(); HorizontalScrollView rawScroll = scroller(raw); rawScroll.setVisibility(GONE); add(rawScroll,4);
        toggle.setOnClickListener(v -> {
            boolean open = rawScroll.getVisibility() != VISIBLE;
            rawScroll.setVisibility(open ? VISIBLE : GONE); toggle.setText(open ? "收起逐点原始数据" : "展开逐点原始数据");
        });
        selection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p,View v,int position,long id) {
                if (!updating && position != selected) { selected = position; select(); }
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
    }
    void show(ReactionSession.Snapshot snapshot) {
        ReactionStats s = ReactionStats.of(snapshot);
        long points = 0, dropped = 0; int missing = 0;
        for (ReactionSession.Round r : snapshot.rounds) for (ReactionSession.Attempt a : r.attempts) {
            if (a.trace == null) missing++; else { points += a.trace.samples.size(); dropped += a.trace.droppedSamples; }
        }
        summary.setText("计分成功 " + s.successfulRounds + " / " + s.presentedRounds + " 轮 · " + percent(s.successRate)
                + "\n错单 " + s.wrongAttempts + " / " + s.eligibleAttempts + " 次 · " + percent(s.errorRate)
                + " · 外起手 " + s.invalidStarts + " · 取消 " + s.cancelledAttempts);
        trend.setSession(snapshot);
        StringBuilder d = new StringBuilder("阶段          n    平均     中位     P95      最快     最慢     标准差\n");
        distribution(d,"搜索起手",s.search); distribution(d,"滑至终点",s.drag); distribution(d,"总完成",s.total);
        distribution(d,"起手停顿",s.initiation); distribution(d,"终点停留",s.hold); distributions.setText(d.toString());
        comparison.setText("每轮只计首次目标成功，之后的重复滑动只作练习；刷新放弃、暂停或停止的轮次保留。错单率以计分前时序有效的全部操作为分母。"
                + "\n保存 " + points + " 个原始点；已知未保存 " + dropped + " 点；无原始轨迹 " + missing + " 次。"
                + "上限50轮 / 200次；未保存轮次 " + snapshot.droppedRounds + "、操作 " + snapshot.droppedAttempts
                + "。每条轨迹最多1024点，总点上限20000。"
                + "\n搜索起手 = 呈现帧回调→首次正确订单有效按下；滑至终点 = 本轮计分成功那次按下→首次到98%行程。总完成 = 呈现→计分终点，包含之前的错单和重试，所以不能简单把搜索与滑动相加。"
                + "\n起手停顿 = 成功那次按下→首个移动；终点停留 = 到98%→抬手。表中只使用有计分成功的轮次；P95用最近秩法，标准差为总体标准差。"
                + "\n最初" + s.comparisonWindowCount + "个成功平均总耗时 " + ms(s.first5TotalMean) + "；最近" + s.comparisonWindowCount
                + "个 " + ms(s.last5TotalMean) + "。不足10个成功时两组可能重叠，变化只作描述，不能推断疲劳或训练效果。"
                + "\n" + grouped(snapshot));
        StringBuilder rows = new StringBuilder("轮  结果      目标  搜索ms   滑动ms   总计ms   错单/重试  滚动px/回转\n");
        attempts.clear(); attemptRounds.clear(); List<String> labels = new ArrayList<>();
        for (int i = 0; i < snapshot.rounds.size(); i++) {
            ReactionSession.Round r = snapshot.rounds.get(i);
            rows.append(String.format(Locale.ROOT,"%-3d %-8s %-3d %-8s %-8s %-8s %d/%d      %d/%d\n",i+1,
                    status(r.status),r.batch.targetIndex+1,n(r.searchMillis()),n(r.dragMillis()),n(r.totalMillis()),
                    r.wrongAttempts,r.retryCount,r.scrollDistancePx,r.directionChanges));
            for (ReactionSession.Attempt a : r.attempts) {
                attempts.add(a); attemptRounds.add(r);
                labels.add("第" + (i+1) + "轮 · 操作" + a.index + " · 单" + (a.orderIndex+1) + " · "
                        + (a.practice ? "重复练习" : a.scored ? "计分成功" : a.correctOrder ? "目标单" : "非目标单") + " · " + status(a.outcome));
            }
        }
        roundTable.setText(rows.toString()); updating = true;
        selection.setAdapter(new ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,labels));
        selected = Math.max(0,Math.min(selected < 0 ? labels.size()-1 : selected,labels.size()-1));
        if (!labels.isEmpty()) selection.setSelection(selected); updating = false; select();
    }
    private void select() {
        if (attempts.isEmpty() || selected < 0 || selected >= attempts.size()) {
            replay.setTrace(null); details.setText("尚无操作记录。先开始测试，在订单的白色手柄中按下并向右拖动。"); raw.setText(""); return;
        }
        ReactionSession.Attempt a = attempts.get(selected); ReactionSession.Round r = attemptRounds.get(selected);
        replay.setTrace(a.trace);
        StringBuilder b = new StringBuilder("本轮状态：").append(status(r.status)).append(" · 目标在第").append(r.batch.targetIndex+1)
                .append("单的").append(r.batch.targetField).append("，字符偏移").append(r.batch.targetOffset).append("（从0开始）。\n")
                .append("呈现→首次有效起手 ").append(ms(ReactionSession.duration(r.presentedTime,r.firstValidDownTime)))
                .append("；呈现→目标字符首次可见 ").append(ms(ReactionSession.duration(r.presentedTime,r.targetVisibleTime)))
                .append("；目标可见→首次正确起手 ").append(ms(r.visibleToDownMillis())).append("。可见仅指字符与视口相交的布局估计，不是看见或注视。\n")
                .append("滚动累计 ").append(r.scrollDistancePx).append(" px，最大深度 ").append(r.maxScrollYPx).append(" px，方向改变 ")
                .append(r.directionChanges).append(" 次，滚动回调 ").append(r.scrollEvents).append(" 次。\n")
                .append("这次起手→首移 ").append(ms(ReactionSession.duration(a.downTime,a.firstMoveTime)))
                .append("；起手→终点 ").append(ms(ReactionSession.duration(a.downTime,a.targetTime)))
                .append("；起手→结束 ").append(ms(ReactionSession.duration(a.downTime,a.finishedTime)))
                .append("。有效起点 ").append(a.validStart ? "是" : "否").append("；时序有效 ").append(a.timingValid ? "是" : "否")
                .append("；计分 ").append(a.scored ? "是" : "否").append("。\n");
        if (a.trace == null) b.append("此操作没有保存原始轨迹（例如已达到记录上限），阶段时刻仍独立保留。\n");
        else {
            ReportStats.TraceMetrics m = ReportStats.metrics(a.trace); ReactionStats.PathMetrics p = ReactionStats.metrics(a.trace);
            b.append("路径 ").append(n(m.pathDp)).append(" dp；均速 / 峰速 ").append(n(m.meanSpeedDpPerSecond)).append(" / ").append(n(m.maxSpeedDpPerSecond))
                    .append(" dp/s；直接度 ").append(percent(p.directness)).append("；向左回退 ").append(n(p.backtrackingDp)).append(" dp。\n")
                    .append("垂直跨度 ").append(n(p.verticalRangeDp)).append(" dp；相对起点最大垂直偏移 ").append(n(p.maxVerticalDeviationDp)).append(" dp。\n")
                    .append("采样间隔 中位 / P95 ").append(ms(m.sampleGapMedianMillis)).append(" / ").append(ms(m.sampleGapP95Millis))
                    .append("；应用接收滞后 中位 / P95 ").append(ms(m.receiptLagMedianMillis)).append(" / ").append(ms(m.receiptLagP95Millis)).append("。\n")
                    .append("原始 pressure 最小 / 平均 / 最大 / 标准差 ").append(n(p.pressureMin)).append(" / ").append(n(p.pressureMean)).append(" / ")
                    .append(n(p.pressureMax)).append(" / ").append(n(p.pressureStddev)).append("；size均值 ").append(n(m.meanSize)).append("。\n")
                    .append("保存 ").append(a.trace.samples.size()).append(" 点；丢点 ").append(a.trace.droppedSamples).append("；重复省略 ")
                    .append(a.trace.duplicateSamples).append("；逆序舍弃 ").append(a.trace.outOfOrderSamples).append("；未保存阶段 ").append(a.trace.droppedStages)
                    .append("。坐标为当前滑条局部px，密度 ").append(n(a.trace.density)).append(" px/dp。\n");
        }
        b.append("全部数据来自本工具的手动测试。原始点是Android交付样本，系统可能已重采样，不等于芯片未处理数据。pressure等可能固定或估算，不能当作真实物理按压力；人体电流Android未提供。接收滞后不是硬件或网络延迟，采样间隔不是触控扫描频率。回放光点只作插值显示，原始点不补造。");
        details.setText(b.toString());
        StringBuilder points = new StringBuilder("#  t(ms)  lag(ms)   x(px)   y(px)  pressure  size  major  minor  angle  tool/pointer/action\n");
        if (a.trace != null) for (int i = 0; i < a.trace.samples.size(); i++) {
            InteractionRecorder.Sample p = a.trace.samples.get(i);
            points.append(i+1).append("  ").append(p.eventTimeMillis-a.downTime).append("  ").append(p.receivedUptimeMillis-p.eventTimeMillis)
                    .append("  ").append(n(p.x)).append("  ").append(n(p.y)).append("  ").append(n(p.pressure)).append("  ").append(n(p.size))
                    .append("  ").append(n(p.touchMajor)).append("  ").append(n(p.touchMinor)).append("  ").append(n(p.orientation))
                    .append("  ").append(p.toolType).append('/').append(p.pointerId).append('/').append(p.action).append('\n');
        }
        raw.setText(points.toString());
    }
    private String grouped(ReactionSession.Snapshot snapshot) {
        StringBuilder b = new StringBuilder("按目标位次分组的成功总耗时均值：");
        for (int slot = 0; slot < ReactionOrderGenerator.TARGET_CANDIDATE_COUNT; slot++) {
            double sum = 0; int count = 0;
            for (ReactionSession.Round r : snapshot.rounds) if (r.batch.targetIndex == slot && r.winner() != null) { sum += r.totalMillis(); count++; }
            b.append(slot == 0 ? "" : "；").append(slot+1).append("单 ").append(count == 0 ? "无样本" : ms(sum/count)+"（n="+count+"）");
        } return b.toString();
    }
    private void distribution(StringBuilder b,String name,ReactionStats.Distribution d) {
        b.append(String.format(Locale.ROOT,"%-10s %-4d %-8s %-8s %-8s %-8s %-8s %s\n",name,d.count,n(d.mean),n(d.median),n(d.p95),n(d.min),n(d.max),n(d.stddev)));
    }
    void pause() { replay.pause(); }
    int selected() { return selected; }
    double cursor() { return replay.getElapsedMillis(); }
    void restore(int index,double cursor) { selected=index; select(); replay.seekTo(cursor); }
    private TextView text(String value,int size,boolean bold) {
        TextView t=new TextView(getContext()); t.setText(value); t.setTextSize(size); t.setTextColor(bold?INK:MUTED); t.setLineSpacing(dp(3),1);
        if (bold) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t;
    }
    private TextView mono() { TextView t=text("",11,false); t.setTypeface(Typeface.MONOSPACE); return t; }
    private HorizontalScrollView scroller(TextView t) { HorizontalScrollView s=new HorizontalScrollView(getContext());s.addView(t);return s; }
    private void add(View child,int top) { LayoutParams p=new LayoutParams(-1,-2);p.topMargin=dp(top);addView(child,p); }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private static String n(double v) { return Double.isFinite(v)?String.format(Locale.ROOT,"%.1f",v):"—"; }
    private static String ms(double v) { return Double.isFinite(v)?n(v)+" ms":"未提供"; }
    private static String percent(double v) { return Double.isFinite(v)?String.format(Locale.ROOT,"%.1f%%",v*100):"未提供"; }
    private static String status(String value) {
        switch(value) {
            case "success":return "成功";case "incomplete":return "未到终点";case "invalid_start":return "外起手";
            case "invalid_multitouch":return "多指无效";case "cancelled":return "取消";case "refresh":case "refreshed":return "刷新放弃";
            case "leave":return "离开中断";case "stopped":return "停止";case "active":return "进行中";default:return value;
        }
    }
}
