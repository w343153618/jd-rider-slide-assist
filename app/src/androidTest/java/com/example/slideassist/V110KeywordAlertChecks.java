package com.example.slideassist;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Entry/lifecycle/manual regression, adapted to the user's replacement of full-screen effects with static local markers. */
public final class V110KeywordAlertChecks {
    static final String[] KEYS={"南邮","南航","南理工"};
    static final int[] COLORS={0xffdc2626,0xff15803d,0xff2563eb};
    private static final String PAGE="com.example.slideassist.KeywordTestActivity";
    public static void run(Instrumentation i, Bundle result) {
        check("com.example.slideassist.fixture".equals(BuildConfig.TARGET_PACKAGE),"use isolated fixture build");
        Harness h=new Harness(i);
        try {
            h.start();check(!h.enabled(),"markers must be explicitly enabled on entry");h.expect(false);h.expectPlainStyles();
            check(!InteractionRecorder.isRecording(),"keyword page must not implicitly record touches");
            Seeds seeds=Seeds.find();h.refresh(seeds.schools[0]);h.expect(false);
            h.setEnabled(true);h.expect(true);
            for(long seed:seeds.schools){h.refresh(seed);h.expect(true);}
            h.refresh(seeds.offscreen);h.expect(true);
            ui(i,()->{for(int n=0;n<h.batch().orders.size()*4;n++){
                TextView value=h.root.findViewWithTag("keyword_order_text_"+n);Rect visible=new Rect();
                if(value.getGlobalVisibleRect(visible)&&visible.width()>0&&visible.height()>0)
                    check(mask(value.getText().toString())==0,"offscreen fixture must have no visible order-field keyword");
            }});
            h.scrollBottom();Rect backBounds=new Rect();Button back=button(h.root,"返回模拟抢单");
            ui(i,()->check(back!=null&&back.getGlobalVisibleRect(backBounds),"return control must stay visible at list bottom"));
            h.scrollTop();h.manual(false);h.expectWhiteCard(0);
            Object previous=h.batch();String oldSignature=signature(h.batch());h.pull();
            check(h.batch()!=previous&&!oldSignature.equals(signature(h.batch())),"pull refresh must commit changed order content");
            check(h.batch().orders.size()>=6&&h.batch().orders.size()<=8,"each pull must render6–8orders");
            h.expect(mask(h.batch())!=0);h.refresh(seeds.none);h.expect(false);
            check(mask(allText(h.root))==7,"configured keyword legend must exist for the no-self-trigger check");
            h.setEnabled(false);h.refresh(seeds.schools[1]);h.expect(false);h.expectPlainStyles();
            h.setEnabled(true);h.expect(true);
            Activity speed=i.startActivitySync(new Intent(i.getTargetContext(),SpeedTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            i.waitForIdleSync();h.expect(false);h.expectPlainStyles();ui(i,speed::finish);h.settle();h.expect(true);
            List<View> oldMarkers=h.markers();String retained=signature(h.batch());Activity oldPage=h.page;
            Instrumentation.ActivityMonitor monitor=i.addMonitor(PAGE,null,false);
            try{ui(i,oldPage::recreate);h.page=monitor.waitForActivityWithTimeout(4000);}finally{i.removeMonitor(monitor);}
            check(h.page!=null&&h.page!=oldPage,"keyword page must recreate");h.bind();h.settle();
            check(h.enabled()&&retained.equals(signature(h.batch())),"recreation must retain mode and actual loaded batch");
            for(View marker:oldMarkers)check(!(Boolean)invoke(marker,"isMarked"),"old detached markers must not stay selected");h.expect(true);
            h.setEnabled(false);h.expect(false);h.expectPlainStyles();
            ui(i,()->button(h.root,"返回模拟抢单").performClick());i.waitForIdleSync();
            check(h.page.isFinishing(),"return must finish local practice");h.expect(false);
            result.putString("result","PASS local static keyword practice: explicit entry/opt-in,6–8loaded orders,all3schools/offscreen-only markers,self-label/no-match rejection,no full-screen overlay,white card background,manual tap incomplete/full drag success,pull refresh,off/pause/resume/recreate/return cleanup; local synthetic fixtures only");
        } finally {ui(i,()->{if(h.page!=null&&!h.page.isFinishing())h.page.finish();if(h.main!=null&&!h.main.isFinishing())h.main.finish();});}
    }

    /** Shared device harness; the color suite keeps its own literal color/text expectations. */
    static final class Harness {
        final Instrumentation i;Activity main,page;View root,toggle;ViewGroup orders;PullRefreshScrollView scroll;TextView status;
        Harness(Instrumentation i){this.i=i;}
        void start(){
            main=i.startActivitySync(new Intent(i.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));i.waitForIdleSync();
            View entry=main.getWindow().getDecorView().findViewWithTag("keyword_alert_entry");check(entry!=null,"explicit keyword practice entry missing");
            Instrumentation.ActivityMonitor monitor=i.addMonitor(PAGE,null,false);
            try{ui(i,entry::performClick);page=monitor.waitForActivityWithTimeout(4000);}finally{i.removeMonitor(monitor);}
            check(page!=null,"entry must open keyword practice");bind();settle();
        }
        void bind(){
            root=page.getWindow().getDecorView();toggle=root.findViewWithTag("keyword_alert_toggle");orders=root.findViewWithTag("keyword_orders");
            scroll=root.findViewWithTag("keyword_order_scroll");status=root.findViewWithTag("keyword_alert_status");
            check(root.findViewWithTag("keyword_alert_overlay")==null,"cancelled screen-wide breathing overlay must be absent");
            check(toggle!=null&&orders!=null&&scroll!=null&&status!=null,"keyword controls missing");
            check(manuals(orders).size()>=6&&manuals(orders).size()<=8,"each loaded order needs a manual slider");
            check(find(orders,DemoSliderView.class)==null,"keyword practice must not contain automatic demo sliders");
            for(View marker:markers())check(!marker.isClickable()&&!marker.isFocusable()&&marker.getImportantForAccessibility()==View.IMPORTANT_FOR_ACCESSIBILITY_NO,"local marker must not become an input control");
        }
        KeywordOrderGenerator.Batch batch(){return (KeywordOrderGenerator.Batch)field(page,"batch");}
        boolean enabled(){return (Boolean)field(page,"monitoringEnabled");}
        List<View> markers(){List<View> out=new ArrayList<>();for(int n=0;n<batch().orders.size();n++){View marker=root.findViewWithTag("keyword_order_marker_"+n);check(marker!=null,"local order marker missing");out.add(marker);}return out;}
        void refresh(long seed){ui(i,()->{call(page,"refreshBatch",new Class<?>[]{long.class},seed);scroll.scrollTo(0,0);});settle();}
        void setEnabled(boolean value){if(enabled()!=value)ui(i,toggle::performClick);settle();check(enabled()==value,"explicit marker switch state mismatch");}
        void expect(boolean active){ui(i,()->{
            List<View> markers=markers();
            for(int n=0;n<markers.size();n++){
                boolean wanted=active&&mask(batch().orders.get(n))!=0;View marker=markers.get(n);
                check((Boolean)invoke(marker,"isMarked")==wanted,"each order marker must follow only its own match and foreground switch");
                int color=pixel(i,marker,wanted?.5f/Integer.bitCount(mask(batch().orders.get(n))):.5f);
                check(wanted?Color.alpha(color)==255:Color.alpha(color)==0,"local marker must actually paint opaque color or clear pixels");
            }
            if(active){int wanted=mask(batch());for(int k=0;k<KEYS.length;k++)check(status.getText().toString().contains(KEYS[k])==((wanted&(1<<k))!=0),"status must report only loaded matches");}
        });}
        void expectPlainStyles(){ui(i,()->{
            assertNoColorSpan(status.getText());
            for(int n=0;n<batch().orders.size();n++){
                TextView badge=root.findViewWithTag("keyword_order_matches_"+n);check(badge!=null,"order match labels missing");assertNoColorSpan(badge.getText());
                for(int f=0;f<4;f++){TextView value=root.findViewWithTag("keyword_order_text_"+(n*4+f));assertNoColorSpan(value.getText());}
            }
        });}
        void scrollTop(){ui(i,()->scroll.scrollTo(0,0));settle();}
        void scrollBottom(){ui(i,()->scroll.fullScroll(View.FOCUS_DOWN));settle();}
        void settle(){CountDownLatch frames=new CountDownLatch(1);ui(i,()->root.postOnAnimation(()->root.postOnAnimation(frames::countDown)));try{check(frames.await(2,TimeUnit.SECONDS),"keyword layout did not settle");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}i.waitForIdleSync();}
        void manual(boolean requireNumber){
            int index=0;if(requireNumber){while(index<batch().orders.size()&&mask(batch().orders.get(index))==0)index++;check(index<batch().orders.size(),"a matching order is needed");}
            ViewGroup card=root.findViewWithTag("keyword_order_card_"+index);ManualSpeedSliderView slider=find(card,ManualSpeedSliderView.class);check(slider!=null,"manual slider missing");
            ui(i,()->slider.requestRectangleOnScreen(new Rect(0,0,slider.getWidth(),slider.getHeight()),true));settle();
            float[] p=new float[3];ui(i,()->{int[] origin=new int[2];slider.getLocationOnScreen(origin);SliderGeometry g=new SliderGeometry(slider.getWidth(),slider.getHeight(),slider.getResources().getDisplayMetrics().density);p[0]=origin[0]+g.thumbLeft()+g.thumbWidth()*.35f;p[1]=origin[1]+(g.thumbTop()+g.thumbBottom())/2;p[2]=g.travel();});
            long before=slider.getAttemptCount(),down=SystemClock.uptimeMillis();Object original=batch();
            event(i,page,down,MotionEvent.ACTION_DOWN,p[0],p[1]);SystemClock.sleep(260);check(slider.getAttemptCount()==before,"holding must not auto-complete");
            event(i,page,down,MotionEvent.ACTION_UP,p[0],p[1]);
            check(slider.getAttemptCount()==before+1&&"incomplete".equals(slider.getLastAttempt().outcome)&&slider.getLastAttempt().targetTimeMillis<0,"a plain tap must stay incomplete");
            awaitReady(i,slider);down=SystemClock.uptimeMillis();event(i,page,down,MotionEvent.ACTION_DOWN,p[0],p[1]);
            event(i,page,down,MotionEvent.ACTION_MOVE,p[0]+p[2],p[1]);event(i,page,down,MotionEvent.ACTION_UP,p[0]+p[2],p[1]);
            check(slider.getAttemptCount()==before+2&&"success".equals(slider.getLastAttempt().outcome),"manual full drag must pass local markers and complete");
            check(batch()==original&&slider.getLastAttempt().trace==null&&!InteractionRecorder.isRecording(),"manual practice must not refresh or secretly record");
            awaitReady(i,slider);
            if(requireNumber)check(allText(card).contains("模拟订单 "+(index+1)),"manual completion must retain its visible order number for the summary association");
        }
        void pull(){scrollTop();int[] origin=new int[2];ui(i,()->scroll.getLocationOnScreen(origin));float d=scroll.getResources().getDisplayMetrics().density;long down=SystemClock.uptimeMillis();event(i,page,down,MotionEvent.ACTION_DOWN,origin[0]+100*d,origin[1]+20*d);event(i,page,down,MotionEvent.ACTION_MOVE,origin[0]+100*d,origin[1]+150*d);event(i,page,down,MotionEvent.ACTION_UP,origin[0]+100*d,origin[1]+150*d);settle();}
        void expectWhiteCard(int index){
            View card=root.findViewWithTag("keyword_order_card_"+index);check(card!=null,"card missing");float d=card.getResources().getDisplayMetrics().density;
            ui(i,()->card.requestRectangleOnScreen(new Rect(Math.max(0,card.getWidth()-(int)(4*d)),card.getHeight()/2,card.getWidth()-(int)d,card.getHeight()/2+1),true));settle();
            ui(i,()->{int[] a=new int[2],b=new int[2];card.getLocationOnScreen(a);root.getLocationOnScreen(b);int x=a[0]-b[0]+card.getWidth()-Math.round(3*d),y=a[1]-b[1]+card.getHeight()/2;
                check(x>=0&&x<root.getWidth()&&y>=0&&y<root.getHeight(),"white card sample must be on screen");
                Bitmap bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
                try{root.draw(new Canvas(bitmap));check(bitmap.getPixel(x,y)==Color.WHITE,"card whitespace must stay white instead of receiving a full-screen tint");}finally{bitmap.recycle();}
            });
        }
    }
    private static final class Seeds {
        long none=-1,offscreen=-1;final long[] schools={-1,-1,-1};
        static Seeds find(){Seeds s=new Seeds();for(long seed=0;seed<4096;seed++){
            KeywordOrderGenerator.Batch b=KeywordOrderGenerator.generate(seed,null);int mask=mask(b);if(mask==0&&s.none<0)s.none=seed;
            for(int k=0;k<KEYS.length;k++)if(s.schools[k]<0&&(mask&(1<<k))!=0)s.schools[k]=seed;
            boolean early=false,late=false;for(int n=0;n<b.orders.size();n++)if(mask(b.orders.get(n))!=0){if(n<5)early=true;else late=true;}
            if(!early&&late&&s.offscreen<0)s.offscreen=seed;
            if(s.none>=0&&s.offscreen>=0&&s.schools[0]>=0&&s.schools[1]>=0&&s.schools[2]>=0)return s;
        }throw new AssertionError("generator must offer no-match,each-school and offscreen-only fixtures");}
    }
    static int mask(KeywordOrderGenerator.Batch batch){int out=0;for(KeywordOrderGenerator.Order order:batch.orders)out|=mask(order);return out;}
    static int mask(KeywordOrderGenerator.Order order){return mask(order.title)|mask(order.address)|mask(order.goods)|mask(order.note);}
    static int mask(String value){int out=0;for(int k=0;k<KEYS.length;k++)if(value.contains(KEYS[k]))out|=1<<k;return out;}
    static int pixel(Instrumentation i,View view,float y){int[] color={0};ui(i,()->{check(view.getWidth()>0&&view.getHeight()>0,"marker must be laid out");Bitmap bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);try{Canvas c=new Canvas(bitmap);c.scale(64f/view.getWidth(),64f/view.getHeight());view.draw(c);color[0]=bitmap.getPixel(32,Math.min(62,Math.max(1,(int)(64*y))));}finally{bitmap.recycle();}});return color[0];}
    static String allText(View view){StringBuilder out=new StringBuilder();for(TextView text:texts(view))out.append(text.getText()).append('\n');return out.toString();}
    private static String signature(KeywordOrderGenerator.Batch b){StringBuilder out=new StringBuilder();for(KeywordOrderGenerator.Order o:b.orders)out.append(o.title).append('\0').append(o.address).append('\0').append(o.goods).append('\0').append(o.note).append('\0');return out.toString();}
    private static void assertNoColorSpan(CharSequence text){if(text instanceof Spanned)check(((Spanned)text).getSpans(0,text.length(),ForegroundColorSpan.class).length==0,"disabled/background state must clear active school-color spans");}
    private static void awaitReady(Instrumentation i,ManualSpeedSliderView slider){long until=SystemClock.uptimeMillis()+1500;while(!slider.isReadyForAttempt()&&SystemClock.uptimeMillis()<until)SystemClock.sleep(20);i.waitForIdleSync();check(slider.isReadyForAttempt(),"manual slider must reset200ms after fingers lift");}
    private static void event(Instrumentation i,Activity page,long down,int action,float x,float y){ui(i,()->{int[] origin=new int[2];page.getWindow().getDecorView().getLocationOnScreen(origin);MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x-origin[0],y-origin[1],0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{page.dispatchTouchEvent(e);}finally{e.recycle();}});}
    private static List<ManualSpeedSliderView> manuals(View view){List<ManualSpeedSliderView> out=new ArrayList<>();collect(view,ManualSpeedSliderView.class,out);return out;}
    private static List<TextView> texts(View view){List<TextView> out=new ArrayList<>();collect(view,TextView.class,out);return out;}
    private static <T> void collect(View view,Class<T> type,List<T> out){if(type.isInstance(view))out.add(type.cast(view));if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)collect(((ViewGroup)view).getChildAt(n),type,out);}
    private static <T> T find(View view,Class<T> type){if(view==null)return null;List<T> out=new ArrayList<>();collect(view,type,out);return out.isEmpty()?null:out.get(0);}
    private static Button button(View view,String text){for(TextView value:texts(view))if(value instanceof Button&&text.contentEquals(value.getText()))return (Button)value;return null;}
    static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    static Object invoke(Object o,String name){try{return o.getClass().getMethod(name).invoke(o);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    static void call(Object o,String name,Class<?>[] types,Object...args){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(o,args);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    static void ui(Instrumentation i,Runnable task){if(Looper.myLooper()==Looper.getMainLooper()){task.run();return;}Throwable[] failure={null};i.runOnMainSync(()->{try{task.run();}catch(Throwable t){failure[0]=t;}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
