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
        LinearLayout page=column();page.setPadding(dp(16),dp(16),dp(16),dp(28));scroll.addView(page);setContentView(root);
        instructions=column();add(page,instructions,0);
        add(instructions,text("搜索反应 · 手动滑动",13,ORANGE,true),0);
        add(instructions,text("找到藏在订单里的“田”",24,INK,true),4);
        add(instructions,text("先点开始测试，找出文字中含“田”的订单，再从它的白色手柄手动滑到右端。只有这一单计分；滑条结束0.2秒后自动复原。",14,MUTED,false),8);
        LinearLayout actions=new LinearLayout(this);start=button("开始测试");stop=button("停止并报告");
        actions.addView(start,new LinearLayout.LayoutParams(0,dp(52),1));actions.addView(stop,new LinearLayout.LayoutParams(0,dp(52),1));add(instructions,actions,12);
        status=text("尚未开始记录；可先试滑。开始测试会清空之前这组测试和本工具触摸记录。",13,INK,true);add(instructions,status,8);
        pullHint=text("在列表顶部向下拉，松手换一组全新订单",13,ORANGE,false);add(instructions,pullHint,10);
        orders=column();add(page,orders,8);
        reportArea=column();add(page,reportArea,8);reportArea.setVisibility(View.GONE);
        report=new ReactionReportPanel(this);add(reportArea,report,0);
        LinearLayout exports=new LinearLayout(this);json=button("导出 JSON 全数据");csv=button("导出 CSV 轮次表");
        exports.addView(json,new LinearLayout.LayoutParams(0,dp(52),1));exports.addView(csv,new LinearLayout.LayoutParams(0,dp(52),1));add(reportArea,exports,16);
        add(reportArea,text("JSON包含虚构订单、随机种子、目标位置、每次操作的原始点和阶段时刻；CSV便于比较各轮。报告仅在本机内存，退出进程会丢失。",12,MUTED,false),8);
        start.setOnClickListener(v->beginSession());stop.setOnClickListener(v->stopAndReport());
        json.setOnClickListener(v->export(EXPORT_JSON));csv.setOnClickListener(v->export(EXPORT_CSV));
        scroll.setListener(new PullRefreshScrollView.Listener(){
            @Override public void onPull(float fraction){
                pullHint.setText(fraction>=1?"松手刷新，开始下一轮":fraction>0?"继续下拉以刷新":"在列表顶部向下拉，松手换一组全新订单");
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
        if(!session.snapshot().rounds.isEmpty())status.setText("保留之前的本地测试数据；可查看报告。点击开始测试会清空旧会话并重新计时。");
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
            running=false;InteractionRecorder.stop();status.setText("已达到50轮上限，请查看或导出报告；开始测试可创建新会话。");showReport(true);updateButtons();return;
        }
        batch=fresh;renderOrders(running);scroll.scrollTo(0,0);updateButtons();
        status.setText(running?"新订单准备中…":"新订单已生成；当前没有记录，点击开始测试后计时。");
    }
    private void renderOrders(boolean markPresentation){
        final ReactionOrderGenerator.Batch displayed=batch;final int token=++generation;
        orders.removeAllViews();sliders.clear();targetText=null;
        orders.setVisibility(markPresentation?View.INVISIBLE:View.VISIBLE);
        for(int n=0;n<displayed.orders.size();n++){
            final int orderIndex=n;ReactionOrderGenerator.Order o=displayed.orders.get(n);
            LinearLayout card=column();card.setPadding(dp(14),dp(14),dp(14),dp(14));
            GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(10));card.setBackground(bg);add(orders,card,12);
            add(card,text(String.format(Locale.ROOT,"练习订单 %02d · 虚构",n+1),12,ORANGE,true),0);
            TextView title=text(o.title,18,INK,true),address=text(o.address,15,INK,false),note=text(o.note,14,MUTED,false);
            add(card,title,8);add(card,address,10);add(card,note,10);
            // Render the target with the same style as its ordinary field; no hint in the slider label.
            if(n==displayed.targetIndex)targetText="title".equals(displayed.targetField)?title:"address".equals(displayed.targetField)?address:note;
            ManualSpeedSliderView slider=new ManualSpeedSliderView(this);slider.setTag(n);slider.setRecordingKind("reaction_manual");sliders.add(slider);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(64));p.topMargin=dp(14);card.addView(slider,p);
            slider.setAttemptListener(new ManualSpeedSliderView.AttemptListener(){
                @Override public void onStarted(long down,boolean valid){ }
                @Override public void onFinished(ManualSpeedSliderView.AttemptInfo a){
                    if(!running||batch!=displayed)return;
                    session.recordAttempt(orderIndex,a.downTimeMillis,a.firstMoveTimeMillis,a.targetTimeMillis,a.finishedTimeMillis,a.validStart,a.outcome,a.trace);
                    ReactionSession.Snapshot snapshot=session.snapshot();ReactionSession.Round r=snapshot.rounds.get(snapshot.rounds.size()-1);
                    if(r.winner()!=null)status.setText("本轮已计分：搜索 "+ms(r.searchMillis())+"，手动滑动 "+ms(r.dragMillis())+"，总耗时 "+ms(r.totalMillis())+"。下拉刷新后开始下一轮；本轮再滑只作练习。");
                    else status.setText(!a.validStart?"这次起点在白色手柄外；本轮继续计时。":orderIndex!=displayed.targetIndex?"这次不是目标订单；本轮继续计时。":"尚未完成目标滑动；0.2秒回位后可再试，本轮继续计时。");
                    if(snapshot.totalAttempts>=ReactionSession.MAX_ATTEMPTS){
                        session.finishRound("stopped",SystemClock.uptimeMillis());running=false;InteractionRecorder.stop();status.append("\n已达到200次操作上限，请导出后开始新会话。");updateButtons();
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
                        status.setText("正在计时：找到含“田”的订单并手动滑动。下拉刷新会放弃未完成的这一轮。");
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
        if(running){session.finishRound("leave",SystemClock.uptimeMillis());running=false;InteractionRecorder.stop();status.setText("离开页面已中断本轮，计时已停止；之前的数据仍可查看或导出。");updateButtons();}
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
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setTextColor(INK);b.setMinHeight(dp(48));return b;}
    private void add(LinearLayout parent,View child,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(child,p);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private static String ms(double value){return Double.isFinite(value)?String.format(Locale.ROOT,"%.1f ms",value):"未提供";}
}
