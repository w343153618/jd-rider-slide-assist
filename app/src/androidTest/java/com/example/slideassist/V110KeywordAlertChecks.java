package com.example.slideassist;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
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

/** Native local-page checks; injected gestures are synthetic, not rider-app or human-speed evidence. */
public final class V110KeywordAlertChecks {
    private static final String[] KEYS={"南邮","南航","南理工"};
    private static final String PAGE="com.example.slideassist.KeywordTestActivity";
    public static void run(Instrumentation i, Bundle result) {
        check("com.example.slideassist.fixture".equals(BuildConfig.TARGET_PACKAGE),"use the isolated fixture build");
        Harness h=new Harness(i);
        try {
            h.main=i.startActivitySync(new Intent(i.getTargetContext(),MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));i.waitForIdleSync();
            View entry=h.main.getWindow().getDecorView().findViewWithTag("keyword_alert_entry");
            // This baseline-compatible assertion precedes any new Activity or generator API use.
            check(entry!=null,"Main must expose an explicit keyword-alert simulation entry before scanning loaded orders");
            Instrumentation.ActivityMonitor monitor=i.addMonitor(PAGE,null,false);
            try {ui(i,entry::performClick);h.page=monitor.waitForActivityWithTimeout(4000);}
            finally {i.removeMonitor(monitor);}
            check(h.page!=null,"entry must open the dedicated local keyword page");h.bind();h.settle();
            check(!h.enabled()&&!h.alerting()&&!h.pulsing(),"keyword monitoring must be explicitly enabled on entry");
            check(!InteractionRecorder.isRecording(),"keyword page must not implicitly record touches");
            Seeds seeds=Seeds.find();
            h.refresh(seeds.schools[0]);h.expect(false);
            h.setEnabled(true);h.expect(true);
            for(long seed:seeds.schools){h.refresh(seed);h.expect(true);}
            h.refresh(seeds.offscreen);h.expect(true);
            ui(i,()->{
                for(TextView value:texts(h.orders)){
                    Rect visible=new Rect();
                    if(value.getGlobalVisibleRect(visible)&&visible.width()>0&&visible.height()>0)
                        check(matches(Arrays.asList(value.getText().toString())).isEmpty(),
                                "offscreen-only fixture must have no visible keyword in an order TextView");
                }
            });
            h.scrollBottom();h.expect(true);
            Button pinnedBack=button(h.root,"返回模拟抢单");Rect backBounds=new Rect();
            ui(i,()->check(pinnedBack!=null&&pinnedBack.getGlobalVisibleRect(backBounds),"return control must stay visible at list bottom"));
            h.scrollTop();h.manualThroughOverlay();h.pulsePixels();
            Object beforePull=h.batch();String oldSignature=signature((KeywordOrderGenerator.Batch)beforePull);
            h.pull();check(h.batch()!=beforePull,"a physical-style pull must commit a new loaded batch");
            check(!oldSignature.equals(signature(h.batch())),"pull refresh must change synthetic order content");h.expect(!hits(h.batch()).isEmpty());
            check(h.batch().orders.size()>=6&&h.batch().orders.size()<=8,"each pull must render6–8orders");
            h.refresh(seeds.none);h.expect(false);
            check(textsContainAllKeys(h.root),"configured-keyword labels must exist for the self-trigger check");
            h.setEnabled(false);h.refresh(seeds.schools[1]);h.expect(false);
            h.setEnabled(true);h.expect(true);
            Activity speed=i.startActivitySync(new Intent(i.getTargetContext(),SpeedTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            i.waitForIdleSync();check(!h.alerting()&&!h.pulsing(),"leaving keyword page must stop red rendering and animator");
            ui(i,speed::finish);i.waitForIdleSync();h.settle();h.expect(true);
            View oldOverlay=h.overlay;String retained=signature(h.batch());
            monitor=i.addMonitor(PAGE,null,false);Activity oldPage=h.page;
            try {ui(i,oldPage::recreate);h.page=monitor.waitForActivityWithTimeout(4000);}
            finally {i.removeMonitor(monitor);}
            check(h.page!=null&&h.page!=oldPage,"keyword page must recreate");h.bind();h.settle();
            check(h.enabled()&&retained.equals(signature(h.batch())),"rotation must retain the selected mode and loaded batch");
            check(!(Boolean)invoke(oldOverlay,"isPulseRunning"),"old detached overlay must release its animator");h.expect(true);
            h.setEnabled(false);h.expect(false);
            Button returnButton=button(h.root,"返回模拟抢单");check(returnButton!=null,"fixed return button missing");
            ui(i,returnButton::performClick);i.waitForIdleSync();
            check(h.page.isFinishing(),"return must finish keyword simulation");
            check(!h.alerting()&&!h.pulsing(),"return must leave no active red animator");
            result.putString("result","PASS keyword simulation: explicit entry/opt-in,6–8loaded orders,all3schools and offscreen-only matches,self-label/no-match rejection,red pulse/static reduced-motion rendering,manual tap incomplete and manual full drag success through overlay,pull refresh,stop/pause/resume/rotation/return cleanup; local synthetic data only");
        } finally {
            ui(i,()->{if(h.page!=null&&!h.page.isFinishing())h.page.finish();if(h.main!=null&&!h.main.isFinishing())h.main.finish();});
        }
    }

    private static final class Harness {
        final Instrumentation i;Activity main,page;View root,toggle,overlay;ViewGroup orders;PullRefreshScrollView scroll;TextView status;
        Harness(Instrumentation i){this.i=i;}
        void bind(){
            root=page.getWindow().getDecorView();toggle=root.findViewWithTag("keyword_alert_toggle");overlay=root.findViewWithTag("keyword_alert_overlay");
            orders=(ViewGroup)root.findViewWithTag("keyword_orders");scroll=(PullRefreshScrollView)root.findViewWithTag("keyword_order_scroll");status=(TextView)root.findViewWithTag("keyword_alert_status");
            check(toggle!=null&&overlay!=null&&orders!=null&&scroll!=null&&status!=null,"keyword controls and data container missing");
            check(manuals(orders).size()>=6&&manuals(orders).size()<=8,"every generated order needs its own manual slider");
            check(find(orders,DemoSliderView.class)==null,"keyword page must not contain an automatic demo slider");
            check(!overlay.isClickable()&&!overlay.isFocusable()&&overlay.getImportantForAccessibility()==View.IMPORTANT_FOR_ACCESSIBILITY_NO,
                    "red tint must not become an input/focus/accessibility control");
        }
        KeywordOrderGenerator.Batch batch(){return (KeywordOrderGenerator.Batch)field(page,"batch");}
        boolean enabled(){return (Boolean)field(page,"monitoringEnabled");}
        boolean alerting(){return (Boolean)invoke(overlay,"isAlerting");}
        boolean pulsing(){return (Boolean)invoke(overlay,"isPulseRunning");}
        void refresh(long seed){ui(i,()->{invoke(page,"refreshBatch",new Class<?>[]{long.class},seed);scroll.scrollTo(0,0);});settle();}
        void setEnabled(boolean value){if(enabled()!=value)ui(i,toggle::performClick);settle();check(enabled()==value,"explicit reminder switch state mismatch");}
        void expect(boolean expected){ui(i,()->{
            check(alerting()==expected,"red reminder must match mode,foreground and the entire loaded batch");
            if(expected){List<String> wanted=hits(batch());for(String key:KEYS)check(status.getText().toString().contains(key)==wanted.contains(key),"status must report only actual loaded matches: "+key);}
            else check(!pulsing(),"no-match or disabled reminder must stop the actual animator");
        });}
        void scrollTop(){ui(i,()->scroll.scrollTo(0,0));settle();}
        void scrollBottom(){ui(i,()->scroll.fullScroll(View.FOCUS_DOWN));settle();}
        void settle(){CountDownLatch frames=new CountDownLatch(1);ui(i,()->overlay.postOnAnimation(()->overlay.postOnAnimation(frames::countDown)));try{check(frames.await(2,TimeUnit.SECONDS),"keyword layout did not settle");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}i.waitForIdleSync();}
        void manualThroughOverlay(){
            ManualSpeedSliderView slider=manuals(orders).get(0);ui(i,()->slider.requestRectangleOnScreen(new Rect(0,0,slider.getWidth(),slider.getHeight()),true));settle();
            float[] p=new float[3];ui(i,()->{int[] origin=new int[2];slider.getLocationOnScreen(origin);SliderGeometry g=new SliderGeometry(slider.getWidth(),slider.getHeight(),slider.getResources().getDisplayMetrics().density);p[0]=origin[0]+g.thumbLeft()+g.thumbWidth()*.35f;p[1]=origin[1]+(g.thumbTop()+g.thumbBottom())/2;p[2]=g.travel();});
            long before=slider.getAttemptCount(),down=SystemClock.uptimeMillis();Object originalBatch=batch();
            activityEvent(i,page,down,MotionEvent.ACTION_DOWN,p[0],p[1]);SystemClock.sleep(320);
            check(slider.getAttemptCount()==before,"holding inside a manual handle must not auto-complete");
            activityEvent(i,page,down,MotionEvent.ACTION_UP,p[0],p[1]);
            check(slider.getAttemptCount()==before+1&&"incomplete".equals(slider.getLastAttempt().outcome)&&slider.getLastAttempt().targetTimeMillis<0,
                    "a plain handle tap must end incomplete instead of accepting an order");
            awaitReady(i,slider);down=SystemClock.uptimeMillis();
            activityEvent(i,page,down,MotionEvent.ACTION_DOWN,p[0],p[1]);
            activityEvent(i,page,down,MotionEvent.ACTION_MOVE,p[0]+p[2]*.5f,p[1]);
            activityEvent(i,page,down,MotionEvent.ACTION_MOVE,p[0]+p[2],p[1]);
            activityEvent(i,page,down,MotionEvent.ACTION_UP,p[0]+p[2],p[1]);
            check(slider.getAttemptCount()==before+2&&"success".equals(slider.getLastAttempt().outcome),"manual full drag must pass through the red overlay and succeed");
            check(slider.getLastAttempt().trace==null&&!InteractionRecorder.isRecording(),"manual reminder practice must not secretly collect raw traces");
            check(batch()==originalBatch,"a child-owned manual drag must not turn into pull refresh");awaitReady(i,slider);expect(true);
        }
        void pull(){scrollTop();int[] origin=new int[2];ui(i,()->scroll.getLocationOnScreen(origin));float d=scroll.getResources().getDisplayMetrics().density;long down=SystemClock.uptimeMillis();
            activityEvent(i,page,down,MotionEvent.ACTION_DOWN,origin[0]+100*d,origin[1]+20*d);
            activityEvent(i,page,down,MotionEvent.ACTION_MOVE,origin[0]+100*d,origin[1]+150*d);
            activityEvent(i,page,down,MotionEvent.ACTION_UP,origin[0]+100*d,origin[1]+150*d);settle();}
        void pulsePixels(){
            check((Long)staticField(overlay.getClass(),"PULSE_PERIOD_MILLIS")==1200L,"breathing period must remain1200ms");
            int a=tintAlpha(i,overlay);check(a>=8&&a<=58,"center red tint must be visible and translucent");
            if(Build.VERSION.SDK_INT>=26&&!ValueAnimator.areAnimatorsEnabled()){
                check(!pulsing()&&Math.abs(a-31)<=1,"reduced-motion mode must use static12percent red tint");
            }else{
                check(pulsing(),"foreground match should run the red breathing animator");SystemClock.sleep(350);int b=tintAlpha(i,overlay);SystemClock.sleep(350);int c=tintAlpha(i,overlay);
                check(Math.max(a,Math.max(b,c))-Math.min(a,Math.min(b,c))>=5,"actual rendered red alpha must breathe instead of remaining static");
            }
        }
    }

    private static final class Seeds {
        long none=-1,offscreen=-1;final long[] schools={-1,-1,-1};
        static Seeds find(){Seeds s=new Seeds();for(long seed=0;seed<4096;seed++){
            KeywordOrderGenerator.Batch b=KeywordOrderGenerator.generate(seed,null);List<String> keys=hits(b);if(keys.isEmpty()&&s.none<0)s.none=seed;
            for(int k=0;k<KEYS.length;k++)if(s.schools[k]<0&&keys.contains(KEYS[k]))s.schools[k]=seed;
            boolean early=false,late=false;for(int n=0;n<b.orders.size();n++)if(!matches(values(b.orders.get(n))).isEmpty()){if(n<5)early=true;else late=true;}
            if(!early&&late&&s.offscreen<0)s.offscreen=seed;
            if(s.none>=0&&s.offscreen>=0&&s.schools[0]>=0&&s.schools[1]>=0&&s.schools[2]>=0)return s;
        }throw new AssertionError("generator must offer no-match,all3school and offscreen-only fixtures within4096seeds");}
    }
    private static List<String> values(KeywordOrderGenerator.Order o){return Arrays.asList(o.title,o.address,o.goods,o.note);}
    private static List<String> hits(KeywordOrderGenerator.Batch batch){List<String> text=new ArrayList<>();for(KeywordOrderGenerator.Order o:batch.orders)text.addAll(values(o));return matches(text);}
    /** Independent literal expectation; do not call the production matcher from this oracle. */
    private static List<String> matches(List<String> text){List<String> found=new ArrayList<>();for(String key:KEYS){for(String value:text)if(value!=null&&value.contains(key)){found.add(key);break;}}return found;}
    private static String signature(KeywordOrderGenerator.Batch b){StringBuilder s=new StringBuilder();for(KeywordOrderGenerator.Order o:b.orders)for(String v:values(o))s.append(v).append('\u0000');return s.toString();}
    private static boolean textsContainAllKeys(View root){List<String> text=new ArrayList<>();for(TextView t:texts(root))text.add(t.getText().toString());return matches(text).size()==3;}
    private static int tintAlpha(Instrumentation i,View overlay){int[] alpha={0};ui(i,()->{check(overlay.getWidth()>0&&overlay.getHeight()>0,"red overlay must have full laid-out bounds");Bitmap bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);try{Canvas c=new Canvas(bitmap);c.scale(64f/overlay.getWidth(),64f/overlay.getHeight());overlay.draw(c);int color=bitmap.getPixel(32,32);check(Color.red(color)>Color.green(color)&&Color.red(color)>Color.blue(color),"center overlay must be red");alpha[0]=Color.alpha(color);}finally{bitmap.recycle();}});return alpha[0];}
    private static void awaitReady(Instrumentation i,ManualSpeedSliderView slider){long until=SystemClock.uptimeMillis()+1500;while(!slider.isReadyForAttempt()&&SystemClock.uptimeMillis()<until)SystemClock.sleep(20);i.waitForIdleSync();check(slider.isReadyForAttempt(),"manual slider must reset200ms after fingers lift");}
    private static void activityEvent(Instrumentation i,Activity a,long down,int action,float x,float y){ui(i,()->{int[] origin=new int[2];a.getWindow().getDecorView().getLocationOnScreen(origin);MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x-origin[0],y-origin[1],0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{a.dispatchTouchEvent(e);}finally{e.recycle();}});}
    private static List<ManualSpeedSliderView> manuals(View v){List<ManualSpeedSliderView> out=new ArrayList<>();collect(v,ManualSpeedSliderView.class,out);return out;}
    private static List<TextView> texts(View v){List<TextView> out=new ArrayList<>();collect(v,TextView.class,out);return out;}
    private static <T> void collect(View v,Class<T> type,List<T> out){if(type.isInstance(v))out.add(type.cast(v));if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++)collect(((ViewGroup)v).getChildAt(n),type,out);}
    private static <T> T find(View v,Class<T> type){List<T> out=new ArrayList<>();collect(v,type,out);return out.isEmpty()?null:out.get(0);}
    private static Button button(View v,String text){for(TextView value:texts(v))if(value instanceof Button&&text.contentEquals(value.getText()))return (Button)value;return null;}
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static Object staticField(Class<?> type,String name){try{return type.getField(name).get(null);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static Object invoke(Object o,String name){try{return o.getClass().getMethod(name).invoke(o);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void invoke(Object o,String name,Class<?>[] types,Object...args){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(o,args);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void ui(Instrumentation i,Runnable task){if(Looper.myLooper()==Looper.getMainLooper()){task.run();return;}Throwable[] failure={null};i.runOnMainSync(()->{try{task.run();}catch(Throwable t){failure[0]=t;}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
