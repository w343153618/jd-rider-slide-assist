package com.example.slideassist;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Direct native controller checks, separate from root's actual global-touch evidence. */
public final class V19EdgeDragChecks {
    private static final String SIZE="bubble_size_dp",OPACITY="bubble_opacity_percent";
    public static void run(Instrumentation i,Bundle result,boolean serviceMode){
        check("com.example.slideassist.fixture".equals(BuildConfig.TARGET_PACKAGE),"isolated fixture build required");
        SharedPreferences prefs=i.getTargetContext().getSharedPreferences("assist_settings",0);
        boolean hadSize=prefs.contains(SIZE),hadOpacity=prefs.contains(OPACITY);
        int oldSize=prefs.getInt(SIZE,64),oldOpacity=prefs.getInt(OPACITY,10);
        Harness h=new Harness(i,prefs);h.serviceMode=serviceMode;
        try{
            check(prefs.edit().putInt(SIZE,64).putInt(OPACITY,10).commit(),"cannot initialize fixture settings");
            if(serviceMode)h.openService();else h.openMain();
            if(h.b.isCollapsed())tap(i,h.b);
            check(h.active(),"drag checks require enabled assistance");
            SystemClock.sleep(1100);long epoch=h.b.getElapsedActiveMillis();
            h.collapse(true,64);
            // Literal actual geometry catches the published4dp handle before any new API is needed.
            h.shape(20,64,0);h.retained(epoch,true);
            long down=begin(i,h.b);float[] start=center(i,h.b);float oldX=h.rect()[0];
            move(i,h.b,down,start,12,0);h.settle();
            h.shape(64,64,-1);check(!h.b.isCollapsed(),"12dp horizontal inward motion must expand during MOVE");
            near(oldX+h.dp(12),h.rect()[0],"left drag expansion must preserve the original finger offset");
            finish(i,h.b,down,start,12,0,MotionEvent.ACTION_UP);h.settle();
            check(!h.b.isCollapsed(),"dragging out must remain expanded on UP even near the edge");
            h.retained(epoch,false);
            float freeX=h.rect()[0],freeY=h.rect()[1];
            h.resize(32);h.shape(32,32,-1);near(freeX,h.rect()[0],"resizing a free control must not dock it");
            h.resize(64);h.shape(64,64,-1);near(freeX,h.rect()[0],"restoring full size must preserve its free X position");
            h.retained(epoch,false);
            h.triplePair();near(freeX,h.rect()[0],"clicks on a free control must not snap X");near(freeY,h.rect()[1],"clicks must not change Y");
            gesture(i,h.b,64,6,MotionEvent.ACTION_UP);h.settle();
            h.shape(64,64,-1);check(!h.b.isCollapsed(),"expanded drag released away from an edge must remain full");
            h.collapse(true,64);h.shape(20,64,0);
            float y=h.rect()[1];gesture(i,h.b,12,24,MotionEvent.ACTION_UP);h.settle();
            h.shape(20,64,0);check(h.rect()[1]>y,"vertical handle drag must move Y while remaining collapsed");
            h.interrupt(true,false,64);h.interrupt(true,true,64);
            boolean before=h.active();tap(i,h.b);h.settle();
            h.shape(64,64,0);check(h.active()==before,"one handle tap only expands");h.triplePair();
            h.interrupt(false,false,64);h.interrupt(false,true,64);
            h.collapse(false,64);h.shape(20,64,1);
            float[] rightStart=center(i,h.b);float[] rightRect=h.rect();float oldRight=rightRect[0]+rightRect[2];long rightDown=begin(i,h.b);
            move(i,h.b,rightDown,rightStart,-12,0);h.settle();h.shape(64,64,-1);
            check(!h.b.isCollapsed(),"right12dp inward MOVE must expand before UP");
            near(oldRight-h.dp(12),h.rect()[0]+h.rect()[2],"right expansion must preserve finger distance from the right edge");
            float expandedX=h.rect()[0];finish(i,h.b,rightDown,rightStart,-12,0,MotionEvent.ACTION_UP);h.settle();
            h.shape(64,64,-1);near(expandedX,h.rect()[0],"right drag-out must remain expanded at its free release position");
            for(int size:new int[]{32,120}){
                h.collapse(true,64);long beforeResize=h.b.getElapsedActiveMillis();h.resize(size);h.shape(20,size,0);h.retained(beforeResize,true);
                tap(i,h.b);h.settle();h.shape(size,size,0);h.retained(beforeResize,false);
                if(size==32)h.resize(64);
            }
            check(prefs.getInt(OPACITY,-1)==10,"visibility floor must never rewrite selected opacity");
            h.assertNoGesture();
            result.putString("result","PASS native "+(serviceMode?"service":"Main")+" edge dragging: actual20dp handle at35percent floor with10percent expanded preference,12dp inward horizontal MOVE expansion with continuous left offset and full/free UP,right drag-out,near-edge outward collapse,free clicks/triples,vertical/cancel/multi handling,32/120dp same-view clock and bounds; direct controller events only");
        }finally{
            try{if(h.activity!=null)ui(i,h.activity::finish);if(serviceMode)ui(i,AssistService::stopAssist);}
            finally{
                SharedPreferences.Editor restore=prefs.edit();if(hadSize)restore.putInt(SIZE,oldSize);else restore.remove(SIZE);if(hadOpacity)restore.putInt(OPACITY,oldOpacity);else restore.remove(OPACITY);
                check(restore.commit(),"cannot restore fixture preferences");
                if(serviceMode&&h.service!=null)ui(i,()->invoke(h.service,"updateStatusBubble"));
            }
        }
    }
    private static final class Harness{
        final Instrumentation i;final SharedPreferences prefs;boolean serviceMode;Activity activity;Object service;StatusBubbleView b;String gestureStatus;int gestureToken;
        Harness(Instrumentation i,SharedPreferences prefs){this.i=i;this.prefs=prefs;}
        void openMain(){
            activity=i.startActivitySync(new Intent(i.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));i.waitForIdleSync();
            b=find(activity.getWindow().getDecorView());check(b!=null,"Main status control missing");
        }
        void openService(){
            for(int n=0;n<200&&!AssistService.isConnected();n++)SystemClock.sleep(50);check(AssistService.isConnected(),"fixture accessibility service must already be enabled");service=field(null,"instance");
            Intent receiver=i.getTargetContext().getPackageManager().getLaunchIntentForPackage(BuildConfig.TARGET_PACKAGE);check(receiver!=null,"separate fixture receiver missing");receiver.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);i.getTargetContext().startActivity(receiver);i.waitForIdleSync();ui(i,AssistService::stopAssist);
            for(int n=0;n<100&&b==null;n++){ui(i,()->b=(StatusBubbleView)field(service,"statusBubble"));if(b==null)SystemClock.sleep(50);}check(b!=null,"target-foreground service status window missing");
            if(b.isCollapsed())tap(i,b);for(int n=0;n<3;n++){SystemClock.sleep(60);tap(i,b);}check(AssistService.isAssistArmed(),"three taps must arm service");
            gestureStatus=AssistService.getLastGestureStatus();gestureToken=(Integer)field(service,"lastGestureToken");
        }
        float dp(float value){return value*b.getResources().getDisplayMetrics().density;}
        boolean active(){return String.valueOf(b.getContentDescription()).contains("辅助已开启");}
        float[] rect(){float[] r=new float[4];ui(i,()->{if(serviceMode){WindowManager.LayoutParams lp=(WindowManager.LayoutParams)field(service,"bubbleLayout");r[0]=lp.x;r[1]=lp.y;}else{r[0]=b.getX();r[1]=b.getY();}r[2]=b.getWidth();r[3]=b.getHeight();});return r;}
        int viewport(){int[] w={0};ui(i,()->w[0]=serviceMode?((int[])invoke(service,"screenSize"))[0]:((View)b.getParent()).getWidth());return w[0];}
        int originX(){if(serviceMode)return 0;int[] origin=new int[2];ui(i,()->((View)b.getParent()).getLocationOnScreen(origin));return origin[0];}
        void collapse(boolean left,int size){
            if(b.isCollapsed()){tap(i,b);settle();}
            float[] r=rect();
            if(left?r[0]<=dp(24):viewport()-r[0]-r[2]<=dp(24)){
                float[] from=center(i,b);float target=originX()+viewport()/2f;long down=SystemClock.uptimeMillis();
                event(i,b,down,MotionEvent.ACTION_DOWN,from[0],from[1]);event(i,b,down,MotionEvent.ACTION_MOVE,target,from[1]);event(i,b,down,MotionEvent.ACTION_UP,target,from[1]);settle();
            }
            float[] start=center(i,b);float target=originX()+(left?dp(size)/2+dp(4):viewport()-dp(size)/2-dp(4));
            long down=SystemClock.uptimeMillis();event(i,b,down,MotionEvent.ACTION_DOWN,start[0],start[1]);event(i,b,down,MotionEvent.ACTION_MOVE,target,start[1]);event(i,b,down,MotionEvent.ACTION_UP,target,start[1]);settle();
        }
        void shape(int width,int height,int side){
            ui(i,()->{
                int w=Math.round(dp(width)),h=Math.round(dp(height));check(b.getWidth()==w&&b.getHeight()==h,"expected actual "+width+"x"+height+"dp control, received "+b.getWidth()/dp(1)+"x"+b.getHeight()/dp(1));
                float[] r=rect();if(side>=0)near(side==0?0:viewport()-w,r[0],"control must use its visible width at the selected edge");
                int viewportHeight=serviceMode?((int[])invoke(service,"screenSize"))[1]:((View)b.getParent()).getHeight();check(r[0]>=0&&r[0]+w<=viewport()+1&&r[1]>=0&&r[1]+h<=viewportHeight+1,"visible control must stay inside viewport");
                if(serviceMode){WindowManager.LayoutParams lp=(WindowManager.LayoutParams)field(service,"bubbleLayout");check(lp.width==w&&lp.height==h,"service window must not retain invisible full-size hit bounds");check(field(service,"statusBubble")==b,"service gestures and resize must reuse the same View");}
                if(Build.VERSION.SDK_INT>=29)check(b.getSystemGestureExclusionRects().size()==1&&b.getSystemGestureExclusionRects().contains(new Rect(0,0,w,h)),"Back exclusion must match only measured visible bounds");
            });
        }
        void retained(long epoch,boolean folded){ui(i,()->{check(active()&&b.getElapsedActiveMillis()>=epoch,"presentation changes must preserve ON clock");check(b.getOpacityPercent()==10,"selected opacity must remain10percent");check(Math.abs(b.getAlpha()-(folded?.35f:.1f))<.001f,"collapsed visibility floor and expanded preference must be independent");});}
        void triplePair(){
            boolean before=active();for(int n=0;n<6;n++){SystemClock.sleep(60);assertNoGesture();tap(i,b);check(active()==(n>=2&&n<5?!before:before),"only each third expanded tap may toggle");}
            assertNoGesture();
        }
        void interrupt(boolean folded,boolean multiple,int size){
            check(b.isCollapsed()==folded,"interruption must start in intended presentation");boolean before=active();float[] start=center(i,b);long down=SystemClock.uptimeMillis();
            event(i,b,down,MotionEvent.ACTION_DOWN,start[0],start[1]);move(i,b,down,start,48,0);settle();
            if(multiple){multi(i,b,down,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),start[0]+dp(48),start[1]);multi(i,b,down,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),start[0]+dp(48),start[1]);finish(i,b,down,start,48,0,MotionEvent.ACTION_UP);}
            else finish(i,b,down,start,48,0,MotionEvent.ACTION_CANCEL);
            settle();shape(folded?20:size,size,folded?0:-1);check(b.isCollapsed()==folded&&active()==before,"cancel/multi must restore initial fold state and must never toggle");
        }
        void resize(int size){
            if(serviceMode){check(prefs.edit().putInt(SIZE,size).commit(),"cannot update service size");ui(i,()->invoke(service,"updateStatusBubble"));}
            else{
                View root=activity.getWindow().getDecorView();View tagged=root.findViewWithTag("bubble_size");
                if(tagged==null||!tagged.isShown()){Button menu=button(root,"浮标设置");check(menu!=null,"bubble menu missing");if(!menu.isShown()){Button expand=button(root,"展开设置");check(expand!=null,"settings inaccessible");ui(i,expand::performClick);}ui(i,menu::performClick);i.waitForIdleSync();}
                SeekBar sizeBar=(SeekBar)root.findViewWithTag("bubble_size");check(sizeBar!=null,"size slider missing");ui(i,()->sizeBar.setProgress(size-32));
            }settle();
        }
        void settle(){CountDownLatch frame=new CountDownLatch(1);ui(i,()->b.postOnAnimation(()->b.postOnAnimation(frame::countDown)));try{check(frame.await(2,TimeUnit.SECONDS),"status layout did not settle");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}i.waitForIdleSync();}
        void assertNoGesture(){if(serviceMode)check(gestureStatus.equals(AssistService.getLastGestureStatus())&&gestureToken==(Integer)field(service,"lastGestureToken")&&!(Boolean)field(service,"injecting")&&!(Boolean)field(service,"gestureInFlight"),"status drag must not dispatch any order gesture");}
    }
    private static long begin(Instrumentation i,View v){float[] p=center(i,v);long down=SystemClock.uptimeMillis();event(i,v,down,MotionEvent.ACTION_DOWN,p[0],p[1]);return down;}
    private static void move(Instrumentation i,View v,long down,float[] p,float dx,float dy){float density=v.getResources().getDisplayMetrics().density;event(i,v,down,MotionEvent.ACTION_MOVE,p[0]+dx*density,p[1]+dy*density);}
    private static void finish(Instrumentation i,View v,long down,float[] p,float dx,float dy,int action){float density=v.getResources().getDisplayMetrics().density;event(i,v,down,action,p[0]+dx*density,p[1]+dy*density);}
    private static void gesture(Instrumentation i,View v,float dx,float dy,int action){float[] p=center(i,v);long down=SystemClock.uptimeMillis();event(i,v,down,MotionEvent.ACTION_DOWN,p[0],p[1]);move(i,v,down,p,dx,dy);finish(i,v,down,p,dx,dy,action);}
    private static void tap(Instrumentation i,View v){float[] p=center(i,v);long down=SystemClock.uptimeMillis();event(i,v,down,MotionEvent.ACTION_DOWN,p[0],p[1]);event(i,v,down,MotionEvent.ACTION_UP,p[0],p[1]);i.waitForIdleSync();}
    private static float[] center(Instrumentation i,View v){float[] p=new float[2];ui(i,()->{int[] origin=new int[2];v.getLocationOnScreen(origin);p[0]=origin[0]+v.getWidth()/2f;p[1]=origin[1]+v.getHeight()/2f;});return p;}
    private static void event(Instrumentation i,View v,long down,int action,float x,float y){ui(i,()->{MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);int[] origin=new int[2];v.getLocationOnScreen(origin);e.offsetLocation(-origin[0],-origin[1]);try{v.dispatchTouchEvent(e);}finally{e.recycle();}});}
    private static void multi(Instrumentation i,View v,long down,int action,float x,float y){ui(i,()->{MotionEvent.PointerProperties[] pp=new MotionEvent.PointerProperties[2];MotionEvent.PointerCoords[] pc=new MotionEvent.PointerCoords[2];for(int n=0;n<2;n++){pp[n]=new MotionEvent.PointerProperties();pp[n].id=n;pp[n].toolType=MotionEvent.TOOL_TYPE_FINGER;pc[n]=new MotionEvent.PointerCoords();pc[n].x=x+n;pc[n].y=y;pc[n].pressure=1;pc[n].size=1;}MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,2,pp,pc,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);int[] origin=new int[2];v.getLocationOnScreen(origin);e.offsetLocation(-origin[0],-origin[1]);try{v.dispatchTouchEvent(e);}finally{e.recycle();}});}
    private static StatusBubbleView find(View v){if(v instanceof StatusBubbleView)return (StatusBubbleView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++){StatusBubbleView found=find(g.getChildAt(n));if(found!=null)return found;}}return null;}
    private static Button button(View v,String prefix){if(v instanceof Button&&((Button)v).getText().toString().startsWith(prefix))return (Button)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++){Button found=button(g.getChildAt(n),prefix);if(found!=null)return found;}}return null;}
    private static Object field(Object o,String name){try{Field f=AssistService.class.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static Object invoke(Object o,String name){try{Method m=AssistService.class.getDeclaredMethod(name);m.setAccessible(true);return m.invoke(o);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void ui(Instrumentation i,Runnable action){if(Looper.myLooper()==Looper.getMainLooper()){action.run();return;}Throwable[] failure={null};i.runOnMainSync(()->{try{action.run();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
    private static void near(float expected,float actual,String message){check(Math.abs(expected-actual)<=1,message+": "+actual+" vs "+expected);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
