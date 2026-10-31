package com.example.slideassist;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Layout;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A local visual-search task with actual manual slides. It never calls AssistService. */
public final class ReactionTestActivity extends Activity {
    private static final int INK=Color.rgb(35,39,42), MUTED=Color.rgb(96,100,101), ORANGE=Color.rgb(211,89,0);
    private static final int EXPORT_JSON=81, EXPORT_CSV=82;
    private static ReactionSession session=new ReactionSession();
    private static ReactionOrderGenerator.Batch batch;
    private static ReactionSession.Snapshot pendingExport;
    private final List<ManualSpeedSliderView> sliders=new ArrayList<>();
    private PullRefreshScrollView scroll;
    private LinearLayout instructions, orders, reportArea;
    private ReactionReportPanel report;
    private TextView status, pullHint, targetText;
    private Button start, stop, switchPage, json, csv;
    private boolean running, showingReport, paused;
    private int generation;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        // Entering this test does not implicitly opt in to collection from a previous screen.
        InteractionRecorder.stop();
        getWindow().setStatusBarColor(Color.rgb(28,30,31));getWindow().setNavigationBarColor(Color.rgb(28,30,31));
        if(Build.VERSION.SDK_INT>=35)getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root=column();root.setBackgroundColor(Color.rgb(244,244,241));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;
        });
        LinearLayout bar=new LinearLayout(this);bar.setPadding(dp(12),dp(4),dp(12),dp(4));bar.setBackgroundColor(Color.WHITE);
        Button back=button("返回模拟抢单");bar.addView(back,new LinearLayout.LayoutParams(0,dp(48),1));
        back.setOnClickListener(v->returnToPractice());
        switchPage=button("查看报告");bar.addView(switchPage,new LinearLayout.LayoutParams(dp(104),dp(48)));
        switchPage.setOnClickListener(v->{if(showingReport)showReport(false);else stopAndReport();});
        root.addView(bar,new LinearLayout.LayoutParams(-1,-2));
        scroll=new PullRefreshScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout page=column();page.setPadding(dp(12),dp(4),dp(12),dp(4));scroll.addView(page);setContentView(root);
        instructions=column();add(page,instructions,0);
        add(instructions,compactText("找地址中的“田”，手动滑动对应订单。\n滑后0.2秒回位；下拉换3单。",12,INK,false),0);
        LinearLayout actions=new LinearLayout(this);start=button("开始测试");stop=button("停止并报告");
        start.setContentDescription("开始测试并清空之前的本地测试记录");
        actions.addView(start,new LinearLayout.LayoutParams(0,dp(48),1));actions.addView(stop,new LinearLayout.LayoutParams(0,dp(48),1));add(instructions,actions,0);
        status=compactText("未记录 · 开始测试会清空旧记录",12,INK,true);status.setSingleLine(true);status.setEllipsize(android.text.TextUtils.TruncateAt.END);add(instructions,status,2);
        pullHint=compactText("顶部下拉，松手换一组新订单",12,ORANGE,false);pullHint.setSingleLine(true);pullHint.setEllipsize(android.text.TextUtils.TruncateAt.END);add(instructions,pullHint,2);
        orders=column();
        // fillViewport stretches a short page. Weight shares only that spare space;
        // natural wrapped text stays scrollable on short windows or with large fonts.
        // https://developer.android.com/reference/android/widget/ScrollView#setFillViewport(boolean)
        // https://developer.android.com/reference/android/widget/LinearLayout.LayoutParams#weight
        page.addView(orders,new LinearLayout.LayoutParams(-1,-2,1));
        reportArea=column();add(page,reportArea,8);reportArea.setVisibility(View.GONE);
        report=new ReactionReportPanel(this);add(reportArea,report,0);
        LinearLayout exports=new LinearLayout(this);json=button("导出 JSON 全数据");csv=button("导出 CSV 轮次表");
        exports.addView(json,new LinearLayout.LayoutParams(0,dp(52),1));exports.addView(csv,new LinearLayout.LayoutParams(0,dp(52),1));add(reportArea,exports,16);
        add(reportArea,text("JSON包含虚构订单、随机种子、目标位置、每次操作的原始点和阶段时刻；CSV便于比较各轮。报告仅在本机内存，退出进程会丢失。",12,MUTED,false),8);
        start.setOnClickListener(v->beginSession());stop.setOnClickListener(v->stopAndReport());
        json.setOnClickListener(v->export(EXPORT_JSON));csv.setOnClickListener(v->export(EXPORT_CSV));
        scroll.setListener(new PullRefreshScrollView.Listener(){
            @Override public void onPull(float fraction){
                pullHint.setText(fraction>=1?"松手刷新，开始下一轮":fraction>0?"继续下拉以刷新":"顶部下拉，松手换一组新订单");
            }
            @Override public void onRefresh(){if(!showingReport)refreshOrders();}
        });
        scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{
            if(running){session.recordScroll(y,SystemClock.uptimeMillis());markTargetVisibility();}
        });
        if(batch==null)batch=ReactionOrderGenerator.generate(System.nanoTime(),null);
        renderOrders(false);
        showingReport=saved!=null&&saved.getBoolean("report",false);showReport(showingReport);
        if(saved!=null){
            report.restore(saved.getInt("selected",-1),saved.getDouble("cursor",0));
            int savedY=saved.getInt("scroll",0);scroll.post(()->scroll.scrollTo(0,savedY));
        }
        if(!session.snapshot().rounds.isEmpty())status.setText("保留旧报告 · 开始测试会清空记录");
        updateButtons();
    }
    private void beginSession(){
        cancelSliders();session=new ReactionSession();InteractionRecorder.stop();InteractionRecorder.clear();InteractionRecorder.start();
        running=true;showReport(false);refreshOrders();
    }
    private void refreshOrders(){
        cancelSliders();session.finishRound("refresh",SystemClock.uptimeMillis());
        ReactionOrderGenerator.Batch fresh=ReactionOrderGenerator.generate(System.nanoTime(),batch);
        if(running&&!session.beginRound(fresh)){
            running=false;InteractionRecorder.stop();status.setText("已达50轮上限 · 请查看报告");showReport(true);updateButtons();return;
        }
        batch=fresh;renderOrders(running);scroll.scrollTo(0,0);updateButtons();
        status.setText(running?"新订单准备中…":"新订单已生成 · 开始测试后计时");
    }
    private void renderOrders(boolean markPresentation){
        final ReactionOrderGenerator.Batch displayed=batch;final int token=++generation;
        orders.removeAllViews();sliders.clear();targetText=null;
        orders.setVisibility(markPresentation?View.INVISIBLE:View.VISIBLE);
        for(int n=0;n<displayed.orders.size();n++){
            final int orderIndex=n;ReactionOrderGenerator.Order o=displayed.orders.get(n);
            LinearLayout card=column();card.setPadding(dp(10),dp(4),dp(10),dp(4));
            card.setContentDescription("虚构练习订单"+(n+1));
            GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(8));card.setBackground(bg);
            LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2,1);cardParams.topMargin=dp(6);orders.addView(card,cardParams);
            TextView title=compactText(String.format(Locale.ROOT,"%d · %s",n+1,o.title),14,INK,true),address=compactText(o.address,13,INK,false),note=compactText(o.note,12,MUTED,false);
            add(card,title,0);add(card,address,1);add(card,note,1);
            // Render the target with the same style as its ordinary field; no hint in the slider label.
            if(n==displayed.targetIndex)targetText="title".equals(displayed.targetField)?title:"address".equals(displayed.targetField)?address:note;
            ManualSpeedSliderView slider=new ManualSpeedSliderView(this);slider.setTag(n);slider.setRecordingKind("reaction_manual");sliders.add(slider);
            card.addView(new View(this),new LinearLayout.LayoutParams(-1,0,1));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.topMargin=dp(3);card.addView(slider,p);
            slider.setAttemptListener(new ManualSpeedSliderView.AttemptListener(){
                @Override public void onStarted(long down,boolean valid){ }
                @Override public void onFinished(ManualSpeedSliderView.AttemptInfo a){
                    if(!running||batch!=displayed)return;
                    session.recordAttempt(orderIndex,a.downTimeMillis,a.firstMoveTimeMillis,a.targetTimeMillis,a.finishedTimeMillis,a.validStart,a.outcome,a.trace);
                    ReactionSession.Snapshot snapshot=session.snapshot();ReactionSession.Round r=snapshot.rounds.get(snapshot.rounds.size()-1);
                    if(r.winner()!=null)status.setText("已计分 · 滑动"+ms(r.dragMillis())+" · 下拉换组");
                    else status.setText(!a.validStart?"起点在白色手柄外 · 继续计时":orderIndex!=displayed.targetIndex?"这单不是目标 · 继续找地址":"尚未完成 · 回位后可再试");
                    if(snapshot.totalAttempts>=ReactionSession.MAX_ATTEMPTS){
                        session.finishRound("stopped",SystemClock.uptimeMillis());running=false;InteractionRecorder.stop();status.setText("已达200次上限 · 请查看报告");updateButtons();
                    }
                }
            });
        }
        if(markPresentation){
            orders.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){
                @Override public void onGlobalLayout(){
                    orders.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    // Android schedules this at the next animation step, before drawing. It is not a photon timestamp.
                    // https://developer.android.com/reference/android/view/View#postOnAnimation(java.lang.Runnable)
                    orders.postOnAnimation(()->{
                        if(token!=generation||!running||paused)return;
                        orders.setVisibility(View.VISIBLE);session.markPresented(SystemClock.uptimeMillis());markTargetVisibility();
                        status.setText("正在计时 · 下拉将放弃未完成轮次");
                    });
                }
            });
        }
    }
    private void markTargetVisibility(){
        if(!running||targetText==null||orders.getVisibility()!=View.VISIBLE)return;
        Layout layout=targetText.getLayout();if(layout==null||batch.targetOffset>=targetText.length())return;
        int offset=batch.targetOffset,line=layout.getLineForOffset(offset);int[] location=new int[2];targetText.getLocationOnScreen(location);
        float x1=layout.getPrimaryHorizontal(offset),x2=layout.getPrimaryHorizontal(offset+1);
        Rect glyph=new Rect(location[0]+targetText.getTotalPaddingLeft()+(int)Math.min(x1,x2),
                location[1]+targetText.getTotalPaddingTop()+layout.getLineTop(line),
                location[0]+targetText.getTotalPaddingLeft()+(int)Math.max(x1,x2)+1,
                location[1]+targetText.getTotalPaddingTop()+layout.getLineBottom(line));
        Rect visible=new Rect();
        if(targetText.getGlobalVisibleRect(visible)&&glyph.intersect(visible))session.markTargetVisible(SystemClock.uptimeMillis());
    }
    private void stopAndReport(){
        cancelSliders();session.finishRound("stopped",SystemClock.uptimeMillis());running=false;generation++;
        InteractionRecorder.stop();showReport(true);status.setText("记录已停止。每次操作和未完成轮次均保留；可导出完整本地报告。");updateButtons();
    }
    private void showReport(boolean show){
        showingReport=show;orders.setVisibility(show?View.GONE:View.VISIBLE);reportArea.setVisibility(show?View.VISIBLE:View.GONE);
        instructions.setVisibility(show?View.GONE:View.VISIBLE);
        pullHint.setVisibility(show?View.GONE:View.VISIBLE);switchPage.setText(show?"回到订单":"查看报告");
        if(show)report.show(session.snapshot());else report.pause();
        scroll.scrollTo(0,0);updateButtons();
    }
    private void updateButtons(){
        start.setEnabled(!running);stop.setEnabled(running);json.setEnabled(!running&&!session.snapshot().rounds.isEmpty());csv.setEnabled(json.isEnabled());
    }
    private void cancelSliders(){for(ManualSpeedSliderView slider:sliders)slider.cancelAttempt();}
    private void returnToPractice(){
        startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();
    }
    @Override protected void onResume(){super.onResume();paused=false;}
    @Override protected void onPause(){
        paused=true;generation++;cancelSliders();report.pause();
        if(running){session.finishRound("leave",SystemClock.uptimeMillis());running=false;InteractionRecorder.stop();status.setText("已停止计时 · 开始测试会清空记录");updateButtons();}
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle out){
        out.putBoolean("report",showingReport);out.putInt("scroll",scroll.getScrollY());out.putInt("selected",report.selected());out.putDouble("cursor",report.cursor());super.onSaveInstanceState(out);
    }
    ReactionSession.Snapshot measurementSnapshot(){return session.snapshot();}
    private void export(int request){
        if(running)return;pendingExport=session.snapshot();
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(request==EXPORT_JSON?"application/json":"text/csv");intent.putExtra(Intent.EXTRA_TITLE,"reaction-report-"+System.currentTimeMillis()+(request==EXPORT_JSON?".json":".csv"));
        // SAF grants only the document the user selects; no broad storage permission.
        // https://developer.android.com/training/data-storage/shared/documents-files#create-file
        try{startActivityForResult(intent,request);}catch(android.content.ActivityNotFoundException e){pendingExport=null;Toast.makeText(this,"设备没有可用的文档保存程序",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=EXPORT_JSON&&request!=EXPORT_CSV)return;
        ReactionSession.Snapshot snapshot=pendingExport;pendingExport=null;
        if(result!=RESULT_OK||data==null||data.getData()==null||snapshot==null)return;
        android.net.Uri uri=data.getData();android.content.ContentResolver resolver=getContentResolver();
        android.content.Context app=getApplicationContext();
        new Thread(()->{
            String message;
            try(OutputStream stream=resolver.openOutputStream(uri,"w")){
                if(stream==null)throw new java.io.IOException("No output stream");
                try(OutputStreamWriter writer=new OutputStreamWriter(stream,StandardCharsets.UTF_8)){
                    if(request==EXPORT_JSON)ReactionExport.writeJson(snapshot,writer);else ReactionExport.writeCsv(snapshot,writer);
                }message="报告已保存到所选文件";
            }catch(Exception e){message="保存失败，请检查所选位置后重试";}
            String finalMessage=message;new Handler(Looper.getMainLooper()).post(()->Toast.makeText(app,finalMessage,Toast.LENGTH_LONG).show());
        },"reaction-export").start();
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private TextView compactText(String value,int size,int color,boolean bold){TextView t=text(value,size,color,bold);t.setLineSpacing(0,1);t.setIncludeFontPadding(false);return t;}
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setTextColor(INK);b.setMinHeight(dp(48));return b;}
    private void add(LinearLayout parent,View child,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(child,p);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private static String ms(double value){return Double.isFinite(value)?String.format(Locale.ROOT,"%.1f ms",value):"未提供";}
}
