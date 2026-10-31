package com.example.slideassist;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int INK = Color.rgb(35, 39, 42);
    private static final int MUTED = Color.rgb(96, 100, 101);
    private static final int ORANGE = Color.rgb(211, 89, 0);
    private TextView connection, target, demoHint, gestureStatus;
    private final List<DemoSliderView> sliders = new ArrayList<>();
    private StatusBubbleView demoBubble;
    private boolean demoAssistEnabled = true;
    private final VolumeKeyChord demoKeys = new VolumeKeyChord();
    private UpdateManager updater;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(28, 30, 31));
        getWindow().setNavigationBarColor(Color.rgb(28, 30, 31));
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(244, 244, 241));
        // Android 15+ enforces edge-to-edge for target 35; keep controls outside system bars.
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(22), dp(16), dp(28));
        scroll.addView(page);
        FrameLayout content = new FrameLayout(this);
        content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(content);
        updater = new UpdateManager(this);

        TextView eyebrow = text("手动决定 · 辅助滑动", 13, ORANGE, true);
        page.addView(eyebrow);
        TextView title = text("滑动辅助", 28, INK, true);
        addTop(page, title, 5);
        TextView intro = text("京东秒送骑士专用 · 请先在模拟订单中练习", 15, MUTED, false);
        intro.setLineSpacing(dp(3), 1f);
        addTop(page, intro, 7);
        LinearLayout versionRow = row();
        versionRow.addView(text("版本 " + BuildConfig.VERSION_NAME, 13, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        Button upgrade = button("检查更新 / 升级", false);
        upgrade.setOnClickListener(v -> updater.checkForUpdate());
        versionRow.addView(upgrade, new LinearLayout.LayoutParams(dp(142), dp(48)));
        addTop(page, versionRow, 10);

        LinearLayout setup = card();
        addTop(page, setup, 20);
        LinearLayout setupHeader = row();
        setup.addView(setupHeader);
        TextView setupTitle = text("真实页面辅助", 18, INK, true);
        setupHeader.addView(setupTitle, new LinearLayout.LayoutParams(0, -2, 1));
        Button expand = button("展开设置 ▾", false);
        setupHeader.addView(expand, new LinearLayout.LayoutParams(dp(112), dp(48)));
        target = text("", 14, MUTED, false);
        addTop(setup, target, 5);
        LinearLayout setupDetails = new LinearLayout(this);
        setupDetails.setOrientation(LinearLayout.VERTICAL);
        setupDetails.setVisibility(View.GONE);
        addTop(setup, setupDetails, 0);
        expand.setOnClickListener(v -> {
            boolean show = setupDetails.getVisibility() != View.VISIBLE;
            setupDetails.setVisibility(show ? View.VISIBLE : View.GONE);
            expand.setText(show ? "收起设置 ▴" : "展开设置 ▾");
        });
        connection = text("", 14, MUTED, false);
        addTop(setupDetails, connection, 8);
        gestureStatus = text("", 14, MUTED, false);
        gestureStatus.setLineSpacing(dp(2), 1f);
        addTop(setupDetails, gestureStatus, 6);
        addTop(setupDetails, text("触发方式", 15, INK, true), 14);
        RadioGroup modes = new RadioGroup(this);
        modes.setOrientation(RadioGroup.VERTICAL);
        RadioButton immediate = new RadioButton(this);
        immediate.setId(View.generateViewId());
        immediate.setText("按下立即触发（默认）");
        immediate.setTextColor(INK);
        immediate.setMinHeight(dp(48));
        RadioButton release = new RadioButton(this);
        release.setId(View.generateViewId());
        release.setText("抬起手指时触发");
        release.setTextColor(INK);
        release.setMinHeight(dp(48));
        RadioButton gestures = new RadioButton(this);
        gestures.setId(View.generateViewId());
        gestures.setText("单指双击 / 双指单击");
        gestures.setTextColor(INK);
        gestures.setMinHeight(dp(48));
        modes.addView(immediate);
        modes.addView(release);
        modes.addView(gestures);
        addTop(setupDetails, modes, 3);
        String savedMode = getSharedPreferences("assist_settings", MODE_PRIVATE).getString("trigger_mode", "immediate");
        modes.check("gestures".equals(savedMode) ? gestures.getId() :
                "release".equals(savedMode) ? release.getId() : immediate.getId());
        modes.setOnCheckedChangeListener((group, checkedId) -> {
            String mode = checkedId == gestures.getId() ? "gestures" :
                    checkedId == release.getId() ? "release" : "immediate";
            getSharedPreferences("assist_settings", MODE_PRIVATE).edit().putString("trigger_mode", mode).apply();
            AssistService.stopAssist();
            for (DemoSliderView slider : sliders) slider.setTriggerMode(mode);
            updateDemoHint(mode);
            message("触发方式已更改，辅助已停止；请重新启动并对齐");
            refreshStatus();
        });
        addTop(setupDetails, text("滑动时长", 15, INK, true), 14);
        RadioGroup durations = new RadioGroup(this);
        durations.setOrientation(RadioGroup.VERTICAL);
        int savedDuration = SwipeTiming.normalize(getSharedPreferences("assist_settings", MODE_PRIVATE)
                .getInt("swipe_duration_ms", SwipeTiming.DEFAULT_MILLIS));
        for (int duration : new int[] {100, 150, 200, 250, 450, 700}) {
            RadioButton option = new RadioButton(this);
            option.setId(View.generateViewId());
            option.setTag(duration);
            option.setText(duration + " 毫秒" + (duration == 250 ? " · 默认"
                    : duration == 450 ? " · 适中" : duration == 700 ? " · 较慢" : ""));
            option.setTextColor(INK);
            option.setMinHeight(dp(48));
            durations.addView(option);
            if (duration == savedDuration) durations.check(option.getId());
        }
        addTop(setupDetails, durations, 3);
        durations.setOnCheckedChangeListener((group, id) -> {
            View checked = group.findViewById(id);
            if (checked == null) return;
            int duration = (Integer) checked.getTag();
            getSharedPreferences("assist_settings", MODE_PRIVATE).edit().putInt("swipe_duration_ms", duration).apply();
            AssistService.stopAssist();
            for (DemoSliderView slider : sliders) slider.setSwipeDurationMillis(duration);
            message("滑动时长已设为 " + duration + " 毫秒；请重新启动并对齐辅助区");
            refreshStatus();
        });
        addTop(setupDetails, text("时长控制拖动过程，不增加触发前的等待。模拟订单也采用所选时长。", 14, MUTED, false), 4);
        addTop(setupDetails, text("音量键快捷开关", 15, INK, true), 14);
        TextView shortcut = text("先开启无障碍服务。在骑士页面，同时短按音量＋和音量－，开启或关闭辅助。也可点按贴边状态球切换，或上下拖动状态球。开启后仍需对齐滑条并亲自触发；每次滑动结束会自动关闭。两键全部松开后才能再次切换。\n正常音量可能变化；请勿长按，以免触发系统快捷方式。若手机无法接收组合键，可使用状态球或下方按钮。", 14, MUTED, false);
        shortcut.setLineSpacing(dp(2), 1f);
        addTop(setupDetails, shortcut, 6);
        TextView setupHint = text("开启无障碍服务后，启动辅助会打开京东秒送骑士。将浮层移动、调整至完整对齐所选订单的抢单条，浮层会遮挡其下触摸。按下模式一接触就执行；抬起模式在最初的手指抬起时执行。滚动列表请从区域外起手。每次触发、离开应用或滚动列表后辅助会关闭，需重新启动和对齐。", 14, MUTED, false);
        setupHint.setLineSpacing(dp(2), 1f);
        addTop(setupDetails, setupHint, 10);
        LinearLayout row1 = row();
        addTop(setupDetails, row1, 12);
        Button settings = button("无障碍设置", false);
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        addWeighted(row1, settings, false);
        Button stop = button("停止辅助", false);
        stop.setOnClickListener(v -> {
            AssistService.stopAssist();
            message("辅助已停止");
            refreshStatus();
        });
        addWeighted(row1, stop, true);
        Button start = button("启动辅助并打开骑士应用", true);
        addTop(setupDetails, start, 8);
        start.setOnClickListener(v -> {
            TargetApp.Status status = TargetApp.getStatus(this);
            if (!status.installed) { message("未检测到京东秒送骑士，请先安装官方版"); refreshStatus(); return; }
            if (!status.launchable) { message("无法打开骑士应用，请检查是否已停用或重新安装官方版"); refreshStatus(); return; }
            if (!AssistService.isConnected()) { message("请先在无障碍设置中开启滑动辅助"); return; }
            if (!AssistService.startAssist()) { message("无法启动辅助，请检查无障碍服务状态"); return; }
            if (!TargetApp.launch(this)) {
                AssistService.stopAssist();
                message("无法打开骑士应用，辅助已停止");
            }
            refreshStatus();
        });

        TextView heading = text("模拟订单", 21, INK, true);
        addTop(page, heading, 24);
        demoHint = text("", 14, MUTED, false);
        updateDemoHint(savedMode);
        demoHint.setLineSpacing(dp(2), 1f);
        addTop(page, demoHint, 7);
        String[][] orders = {
                {"示例便利店 · 中山路店", "取货后送至：幸福小区 3 号楼", "约 1.2 公里 · 2 件", "请放在门口置物架"},
                {"示例鲜花店", "取货后送至：明珠花园东门附近的社区服务中心", "约 2.5 公里 · 1 件", "鲜花请轻拿轻放，送达前请联系收件人"},
                {"示例药房 · 城南店", "取货后送至：锦绣家园 8 栋", "约 800 米 · 1 件", ""},
                {"示例超市 · 公园店", "取货后送至：春和路 88 号办公楼 B 座，进入大厅后交给前台", "约 3.1 公里 · 5 件", "商品较多，请核对件数"},
                {"示例面包坊", "取货后送至：望江小区北门", "约 1.7 公里 · 3 件", "无需餐具"},
                {"示例水果店 · 中心店", "取货后送至：云景苑 12 号楼一单元", "约 2.2 公里 · 2 件", "若电话未接通，请放在门卫处"}
        };
        for (int i = 0; i < orders.length; i++) addOrder(page, orders[i], i + 1);
        demoBubble = new StatusBubbleView(this);
        FrameLayout.LayoutParams bubblePosition = new FrameLayout.LayoutParams(dp(84), dp(48), Gravity.END | Gravity.CENTER_VERTICAL);
        content.addView(demoBubble, bubblePosition);
        demoBubble.setActive(demoAssistEnabled);
        demoBubble.setOnClickListener(v -> setDemoAssistEnabled(!demoAssistEnabled));
        demoBubble.setOnTouchListener(new View.OnTouchListener() {
            float startY, initialY;
            boolean dragged;
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    startY = event.getRawY(); initialY = view.getY(); dragged = false; return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    float delta = event.getRawY() - startY;
                    if (Math.abs(delta) > ViewConfiguration.get(MainActivity.this).getScaledTouchSlop()) dragged = true;
                    if (dragged) view.setY(Math.max(dp(48), Math.min(content.getHeight() - dp(80), initialY + delta)));
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP && !dragged) view.performClick();
                return true;
            }
        });
        refreshStatus();
    }

    private void addOrder(LinearLayout page, String[] order, int number) {
        LinearLayout card = card();
        addTop(page, card, 12);
        LinearLayout header = row();
        card.addView(header);
        TextView label = text("模拟订单 " + number, 13, ORANGE, true);
        header.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView state = text("待选择", 13, MUTED, false);
        header.addView(state);
        addTop(card, text(order[0], 19, INK, true), 12);
        TextView address = text(order[1], 16, INK, false);
        address.setLineSpacing(dp(3), 1f);
        addTop(card, address, 8);
        addTop(card, text(order[2], 14, MUTED, false), 10);
        if (!order[3].isEmpty()) {
            TextView note = text("备注：" + order[3], 14, Color.rgb(103, 77, 48), false);
            note.setPadding(dp(10), dp(9), dp(10), dp(9));
            note.setBackground(round(Color.rgb(255, 245, 229), 8));
            addTop(card, note, 10);
        }
        DemoSliderView slider = new DemoSliderView(this);
        slider.setTriggerMode(getSharedPreferences("assist_settings", MODE_PRIVATE).getString("trigger_mode", "immediate"));
        slider.setSwipeDurationMillis(getSharedPreferences("assist_settings", MODE_PRIVATE)
                .getInt("swipe_duration_ms", SwipeTiming.DEFAULT_MILLIS));
        sliders.add(slider);
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(-1, dp(62));
        sliderParams.topMargin = dp(15);
        card.addView(slider, sliderParams);
        Button reset = button("重置本条练习", false);
        reset.setVisibility(View.GONE);
        addTop(card, reset, 8);
        slider.setOnComplete(() -> {
            state.setText("模拟成功"); state.setTextColor(Color.rgb(41, 118, 75));
            reset.setVisibility(View.VISIBLE);
        });
        reset.setOnClickListener(v -> {
            slider.reset(); state.setText("待选择"); state.setTextColor(MUTED);
            reset.setVisibility(View.GONE);
        });
    }

    private void updateDemoHint(String mode) {
        if (demoHint == null) return;
        if (!demoAssistEnabled) {
            demoHint.setText("练习辅助已关闭。点击贴边状态球或同时短按音量加减键开启；关闭时仍可从左侧手动拖动。状态球仅控制本页练习，不操作真实订单。");
            return;
        }
        String instruction = "gestures".equals(mode)
                ? "以下均为虚构订单，不会产生真实接单。在橙色区域单指双击、双指轻点，或从左侧向右滑动。"
                : "release".equals(mode)
                ? "以下均为虚构订单，不会产生真实接单。在橙色区域按下，最初的手指抬起时即模拟抢单；滚动列表请从橙色区域外起手。"
                : "以下均为虚构订单，不会产生真实接单。手指一按下橙色区域就会模拟抢单；滚动列表请从橙色区域外起手。";
        demoHint.setText("练习辅助已开启。点按贴边状态球可关闭，按住可上下移动。完成一条后可继续练习其他订单；同一条可点“重置本条练习”再次练习。状态球仅控制本页练习。\n" + instruction);
    }

    void setDemoAssistEnabled(boolean enabled) {
        demoAssistEnabled = enabled;
        for (DemoSliderView slider : sliders) slider.setAssistanceEnabled(enabled);
        if (demoBubble != null) demoBubble.setActive(enabled);
        updateDemoHint(getSharedPreferences("assist_settings", MODE_PRIVATE).getString("trigger_mode", "immediate"));
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP) {
            if (demoKeys.onKey(event.getKeyCode(), event.getAction() == KeyEvent.ACTION_DOWN,
                    event.getRepeatCount(), event.isCanceled(), event.getEventTime(), event.getDeviceId())) {
                setDemoAssistEnabled(!demoAssistEnabled);
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onResume() {
        super.onResume();
        AssistService.stopAssist();
        refreshStatus();
        if (updater != null) updater.resumePendingInstall();
    }
    @Override protected void onPause() { demoKeys.reset(); super.onPause(); }
    @Override protected void onDestroy() { if (updater != null) updater.destroy(); super.onDestroy(); }

    private void refreshStatus() {
        if (connection == null || target == null) return;
        connection.setText(AssistService.isConnected()
                ? "无障碍服务：已开启 · 辅助" + (AssistService.isAssistArmed() ? "已激活" : "已关闭")
                : "无障碍服务：未开启（快捷键暂不可用）");
        String last = AssistService.getLastGestureStatus();
        String result;
        switch (last) {
            case "accepted": result = "系统已接受手势，等待执行结果"; break;
            case "completed": result = "系统手势已完成，请查看订单实际结果"; break;
            case "cancelled": result = "系统手势已取消"; break;
            case "rejected": result = "手势未派发或被系统拒绝"; break;
            case "timeout": result = "未及时收到系统结果，辅助已关闭，请核对订单"; break;
            default: result = "尚无手势记录";
        }
        gestureStatus.setText("最近一次手势：" + result);
        TargetApp.Status status = TargetApp.getStatus(this);
        if (!status.installed) {
            target.setText("京东秒送骑士：未安装或不可见，请安装官方版");
        } else if (!status.launchable) {
            target.setText("京东秒送骑士：已安装，但无法打开 · " + status.versionName + "（" + status.versionCode + "）");
        } else {
            target.setText("京东秒送骑士：已安装 · " + status.versionName + "（" + status.versionCode + "）\n版本兼容性尚待实机验证");
        }
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setBackground(round(Color.WHITE, 12));
        return c;
    }
    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label); b.setTextSize(14); b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : INK);
        b.setBackground(round(primary ? Color.rgb(211, 89, 0) : Color.rgb(239, 239, 235), 8));
        b.setMinHeight(dp(48));
        return b;
    }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private void addTop(LinearLayout parent, View child, int margin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(margin); parent.addView(child, p);
    }
    private void addWeighted(LinearLayout row, View child, boolean margin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
        if (margin) p.leftMargin = dp(8);
        row.addView(child, p);
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void message(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
}
