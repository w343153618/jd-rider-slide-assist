package com.example.slideassist;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Local loaded-batch reminders. Every order slider is physical/manual; no OCR is claimed. */
public final class KeywordTestActivity extends Activity {
    private static final int INK = Color.rgb(35, 39, 42), MUTED = Color.rgb(96, 100, 101);
    private final List<ManualSpeedSliderView> sliders = new ArrayList<>();
    private final List<List<String>> matchesByOrder = new ArrayList<>();
    private final List<KeywordOrderMarkerView> markers = new ArrayList<>();
    private final List<TextView> orderTexts = new ArrayList<>();
    private KeywordOrderGenerator.Batch batch;
    private List<String> hits = new ArrayList<>();
    private boolean monitoringEnabled, resumed;
    private int batchNumber;
    private LinearLayout orders;
    private PullRefreshScrollView scroll;
    private TextView status, pullHint;
    private Button toggle;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        AssistService.stopAssist();
        InteractionRecorder.stop();
        getWindow().setStatusBarColor(Color.rgb(244, 244, 241));
        getWindow().setNavigationBarColor(Color.rgb(244, 244, 241));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | (Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0));
        FrameLayout host = new FrameLayout(this);
        host.setBackgroundColor(Color.rgb(244, 244, 241));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(12), 0, dp(12), 0);
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(12) + insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    dp(12) + insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        host.addView(page, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        navigation.addView(text("订单关键词标记", 19, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        Button back = button("返回模拟抢单");
        back.setOnClickListener(v -> finish());
        navigation.addView(back, new LinearLayout.LayoutParams(dp(140), dp(48)));
        page.addView(navigation, new LinearLayout.LayoutParams(-1, dp(52)));
        toggle = button("开启颜色标记");
        toggle.setTag("keyword_alert_toggle");
        toggle.setOnClickListener(v -> {
            monitoringEnabled = !monitoringEnabled;
            updateReminder();
        });
        page.addView(toggle, new LinearLayout.LayoutParams(-1, dp(48)));
        status = text("", 14, INK, true);
        status.setTag("keyword_alert_status");
        status.setMinHeight(dp(46));
        add(page, status, 4);
        add(page, text(highlighted("南邮（红） / 南航（绿） / 南理工（蓝）\n整批含屏幕外；颜色只标对应订单，不闪屏。"), 12, MUTED, false), 2);
        pullHint = text("列表顶部下拉换一批 · 6～8张虚构订单", 12, MUTED, false);
        add(page, pullHint, 4);
        scroll = new PullRefreshScrollView(this);
        scroll.setTag("keyword_order_scroll");
        orders = new LinearLayout(this);
        orders.setOrientation(LinearLayout.VERTICAL);
        orders.setTag("keyword_orders");
        orders.setPadding(0, 0, 0, dp(16));
        scroll.addView(orders);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        scroll.setListener(new PullRefreshScrollView.Listener() {
            @Override public void onPull(float fraction) {
                pullHint.setText(fraction >= 1 ? "松手换一批" : fraction > 0
                        ? "继续下拉，松手换批" : "列表顶部下拉换一批 · 6～8张虚构订单");
            }
            @Override public void onRefresh() { refreshBatch(SystemClock.elapsedRealtimeNanos()); }
        });
        setContentView(host);
        monitoringEnabled = saved != null && saved.getBoolean("monitoring", false);
        batchNumber = saved == null ? 0 : saved.getInt("batchNumber", 1);
        Object retained = getLastNonConfigurationInstance();
        if (retained instanceof KeywordOrderGenerator.Batch) {
            batch = (KeywordOrderGenerator.Batch) retained;
            showBatch();
        } else if (saved != null && saved.containsKey("seed")) {
            batch = KeywordOrderGenerator.generate(saved.getLong("seed"), null);
            showBatch();
        } else refreshBatch(SystemClock.elapsedRealtimeNanos());
    }

    private void refreshBatch(long seed) {
        resetSliders();
        batch = KeywordOrderGenerator.generate(seed, batch);
        batchNumber++;
        showBatch();
        scroll.scrollTo(0, 0);
    }

    private void showBatch() {
        sliders.clear();
        matchesByOrder.clear();
        markers.clear();
        orderTexts.clear();
        orders.removeAllViews();
        List<CharSequence> fragments = new ArrayList<>();
        int textIndex = 0;
        for (int index = 0; index < batch.orders.size(); index++) {
            final int orderNumber = index + 1;
            KeywordOrderGenerator.Order order = batch.orders.get(index);
            FrameLayout cardHost = new FrameLayout(this);
            cardHost.setTag("keyword_order_card_" + index);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(10), dp(12), dp(12));
            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.WHITE); background.setCornerRadius(dp(10));
            cardHost.setBackground(background);
            cardHost.addView(card, new FrameLayout.LayoutParams(-1, -2));
            add(orders, cardHost, 8);
            TextView state = text("模拟订单 " + orderNumber + " · 待手动拖动", 12, MUTED, false);
            card.addView(state);
            String[] values = {order.title, order.address, order.goods, order.note};
            List<String> orderHits = KeywordMatcher.matchFragments(Arrays.asList(values));
            matchesByOrder.add(orderHits);
            KeywordOrderMarkerView marker = new KeywordOrderMarkerView(this);
            marker.setColors(KeywordAlertPalette.colorsFor(orderHits));
            marker.setTag("keyword_order_marker_" + index);
            FrameLayout.LayoutParams markerSize = new FrameLayout.LayoutParams(dp(5), -1, Gravity.LEFT);
            markerSize.topMargin = dp(10); markerSize.bottomMargin = dp(10);
            cardHost.addView(marker, markerSize);
            markers.add(marker);
            List<String> labels = new ArrayList<>();
            for (String key : orderHits) labels.add(key + "（" + KeywordAlertPalette.colorLabel(key) + "）");
            TextView matchLabels = text(labels.isEmpty() ? "本单未命中" : TextUtils.join(" · ", labels), 12, MUTED, true);
            matchLabels.setTag("keyword_order_matches_" + index);
            add(card, matchLabels, 4);
            orderTexts.add(matchLabels);
            for (int field = 0; field < values.length; field++) {
                fragments.add(values[field]);
                TextView value = text((field == 3 ? "备注：" : "") + values[field], field < 2 ? 15 : 12,
                        field < 2 ? INK : MUTED, field == 0);
                value.setTag("keyword_order_text_" + textIndex++);
                add(card, value, field < 2 ? 7 : 4);
                orderTexts.add(value);
            }
            ManualSpeedSliderView slider = new ManualSpeedSliderView(this);
            slider.setTag("keyword_order_slider_" + index);
            int[] completed = {0};
            slider.setOnAttemptFinished(() -> {
                ManualSpeedSliderView.AttemptInfo attempt = slider.getLastAttempt();
                if (attempt != null && "success".equals(attempt.outcome)) completed[0]++;
                state.setText("模拟订单 " + orderNumber + " · 手动完成 " + completed[0] + " 次");
            });
            LinearLayout.LayoutParams sliderSize = new LinearLayout.LayoutParams(-1, dp(56));
            sliderSize.topMargin = dp(10); card.addView(slider, sliderSize);
            sliders.add(slider);
        }
        hits = KeywordMatcher.matchFragments(fragments);
        updateReminder();
    }

    private void updateReminder() {
        if (batch == null) return;
        boolean active = monitoringEnabled && resumed;
        for (KeywordOrderMarkerView marker : markers) marker.setMarked(active);
        for (TextView value : orderTexts) {
            String raw = value.getText().toString();
            value.setText(active ? highlighted(raw) : raw);
        }
        toggle.setText(monitoringEnabled ? "关闭颜色标记" : "开启颜色标记");
        toggle.setContentDescription(monitoringEnabled ? "关闭颜色标记，滑条始终手动操作" : "开启颜色标记，滑条始终手动操作");
        StringBuilder summary = new StringBuilder("第" + batchNumber + "批 · " + batch.orders.size() + "单 · 标记"
                + (monitoringEnabled ? "已开启" : "已关闭"));
        if (hits.isEmpty()) summary.append("\n本批未命中");
        for (String key : hits) {
            summary.append('\n').append(key).append("（").append(KeywordAlertPalette.colorLabel(key)).append("）：第");
            boolean first = true;
            for (int index = 0; index < matchesByOrder.size(); index++) if (matchesByOrder.get(index).contains(key)) {
                if (!first) summary.append('、');
                summary.append(index + 1); first = false;
            }
            summary.append('单');
        }
        status.setText(active ? highlighted(summary.toString()) : summary.toString());
    }

    private void resetSliders() { for (ManualSpeedSliderView slider : sliders) slider.reset(); }
    @Override protected void onResume() { super.onResume(); resumed = true; updateReminder(); }
    @Override protected void onPause() {
        resumed = false;
        updateReminder();
        resetSliders();
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("monitoring", monitoringEnabled);
        state.putInt("batchNumber", batchNumber);
        if (batch != null) state.putLong("seed", batch.seed);
    }
    @Override public Object onRetainNonConfigurationInstance() { return batch; }

    private CharSequence highlighted(String value) {
        SpannableStringBuilder styled = new SpannableStringBuilder(value);
        for (String key : KeywordMatcher.keywords())
            for (int at = value.indexOf(key); at >= 0; at = value.indexOf(key, at + key.length()))
                styled.setSpan(new ForegroundColorSpan(KeywordAlertPalette.textColorFor(key)), at, at + key.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return styled;
    }

    private TextView text(CharSequence value, int size, int color, boolean bold) {
        TextView result = new TextView(this); result.setText(value); result.setTextSize(size); result.setTextColor(color);
        if (bold) result.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return result;
    }
    private Button button(String value) {
        Button result = new Button(this); result.setText(value); result.setTextSize(13); result.setAllCaps(false);
        result.setMinHeight(dp(48)); return result;
    }
    private void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(top); parent.addView(child, params);
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
