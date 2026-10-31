package com.example.slideassist;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Controller-level native checks in the synthetic preview; not kernel input or rider-app acceptance. */
public final class V18EdgeCollapseChecks {
    private static final String SIZE_PREF="bubble_size_dp", OPACITY_PREF="bubble_opacity_percent";

    public static void run(Instrumentation i,Bundle result) {
        check("com.example.slideassist.fixture".equals(BuildConfig.TARGET_PACKAGE),"use the isolated fixture build");
        SharedPreferences prefs=i.getTargetContext().getSharedPreferences("assist_settings",0);
        boolean hadSize=prefs.contains(SIZE_PREF),hadOpacity=prefs.contains(OPACITY_PREF);
        int originalSize=prefs.getInt(SIZE_PREF,64),originalOpacity=prefs.getInt(OPACITY_PREF,10);
        Activity[] opened={null};
        try {
            check(prefs.edit().putInt(SIZE_PREF,64).putInt(OPACITY_PREF,55).commit(),"cannot initialize local preview settings");
            Activity a=i.startActivitySync(new Intent(i.getTargetContext(),MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            opened[0]=a;i.waitForIdleSync();
            StatusBubbleView bubble=find(a.getWindow().getDecorView(),StatusBubbleView.class);
            check(bubble!=null,"Main status control missing");
            ui(i,()->shape(bubble,64,64,1));
            boolean initial=active(bubble);
            check(initial,"preview must start enabled for timer continuity checks");
            SystemClock.sleep(1100);i.waitForIdleSync();
            long epoch=elapsed(i,bubble);
            drag(i,bubble,.22f,false);
            // This literal geometry assertion intentionally precedes the new reflective API:
            // the previous implementation compiles and fails because its edge handle is only4dp wide.
            ui(i,()->shape(bubble,20,64,0));
            check(collapsed(bubble),"release must enter collapsed state");
            check(active(bubble)==initial,"dragging must preserve assistance state");
            retained(i,bubble,epoch);
            long hiddenStart=elapsed(i,bubble);
            SystemClock.sleep(1100);i.waitForIdleSync();
            check(elapsed(i,bubble)>=hiddenStart+1000,"active elapsed clock must keep advancing while the strip is collapsed");
            expand(i,bubble,64,0);
            tripleIsolation(i,bubble);
            drag(i,bubble,.78f,false);ui(i,()->shape(bubble,20,64,1));
            SeekBar size=openSizeMenu(i,a);
            for(int value:new int[]{32,120}){
                long before=elapsed(i,bubble);
                ui(i,()->size.setProgress(value-32));i.waitForIdleSync();
                ui(i,()->shape(bubble,20,value,1));
                check(collapsed(bubble),"size changes must preserve collapse");retained(i,bubble,before);
                check(prefs.getInt(SIZE_PREF,-1)==value,"size selection must persist while collapsed");
                expand(i,bubble,value,1);tripleIsolation(i,bubble);
                drag(i,bubble,.78f,false);ui(i,()->shape(bubble,20,value,1));
            }
            expand(i,bubble,120,1);
            interruptions(i,bubble,120,false);
            drag(i,bubble,.22f,false);ui(i,()->shape(bubble,20,120,0));
            interruptions(i,bubble,120,true);
            check(prefs.getInt(OPACITY_PREF,-1)==55,"collapse/expansion must not change opacity preference");
            result.putString("result","PASS native Main edge collapse: initial64dp square,20dp left/right release strips and scoped Back exclusion, first tap expands without toggle or triple count,32/120dp size preservation, same-view active clock and55percent opacity, cancelled drags retain prior state and multiple pointers cannot toggle/expand/collapse; controller events only");
        } finally {
            try {if(opened[0]!=null)ui(i,opened[0]::finish);}
            finally {
                SharedPreferences.Editor restore=prefs.edit();
                if(hadSize)restore.putInt(SIZE_PREF,originalSize);else restore.remove(SIZE_PREF);
                if(hadOpacity)restore.putInt(OPACITY_PREF,originalOpacity);else restore.remove(OPACITY_PREF);
                check(restore.commit(),"cannot restore preview settings");
            }
        }
    }

    public static void runService(Instrumentation i,Bundle result) throws Exception {
        check("com.example.slideassist.fixture".equals(BuildConfig.TARGET_PACKAGE),"use the isolated fixture build");
        for(int n=0;n<200&&!AssistService.isConnected();n++)SystemClock.sleep(50);
        check(AssistService.isConnected(),"fixture accessibility service must already be enabled");
        Object service=field(null,"instance");
        SharedPreferences prefs=i.getTargetContext().getSharedPreferences("assist_settings",0);
        boolean hadSize=prefs.contains(SIZE_PREF),hadOpacity=prefs.contains(OPACITY_PREF);
        int originalSize=prefs.getInt(SIZE_PREF,64),originalOpacity=prefs.getInt(OPACITY_PREF,10);
        try{
            check(prefs.edit().putInt(SIZE_PREF,64).putInt(OPACITY_PREF,55).commit(),"cannot initialize service preferences");
            Intent receiver=i.getTargetContext().getPackageManager().getLaunchIntentForPackage(BuildConfig.TARGET_PACKAGE);
            check(receiver!=null,"separate fixture receiver not installed");
            receiver.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);i.getTargetContext().startActivity(receiver);i.waitForIdleSync();
            ui(i,AssistService::stopAssist);
            StatusBubbleView[] value={null};
            for(int n=0;n<100&&value[0]==null;n++){
                ui(i,()->value[0]=(StatusBubbleView)field(service,"statusBubble"));
                if(value[0]==null)SystemClock.sleep(50);
            }
            check(value[0]!=null,"target-foreground service control missing");
            StatusBubbleView bubble=value[0];
            if(collapsed(bubble))tap(i,bubble);
            for(int n=0;n<3;n++){SystemClock.sleep(60);tap(i,bubble);}
            check(AssistService.isAssistArmed(),"three service taps must enable assistance");
            SystemClock.sleep(1100);i.waitForIdleSync();
            long before=elapsed(i,bubble);String gestureStatus=AssistService.getLastGestureStatus();
            int token=(Integer)field(service,"lastGestureToken");
            ui(i,()->{
                if(Build.VERSION.SDK_INT>=34)check(!((WindowManager.LayoutParams)field(service,"bubbleLayout")).canPlayMoveAnimation(),
                        "status window must disable the system position animation so edge visual/touch bounds settle together");
            });
            serviceDrag(i,service,bubble,.22f,false);
            serviceShape(i,service,bubble,20,64,0);retained(i,bubble,before);
            tap(i,bubble);settle(i,bubble);
            serviceShape(i,service,bubble,64,64,0);
            check(AssistService.isAssistArmed(),"first service strip tap only expands");
            tripleIsolation(i,bubble);
            serviceDrag(i,service,bubble,.78f,false);serviceShape(i,service,bubble,20,64,1);
            for(int height:new int[]{32,120}){
                long epoch=elapsed(i,bubble);
                prefs.edit().putInt(SIZE_PREF,height).commit();
                ui(i,()->invoke(service,"updateStatusBubble"));settle(i,bubble);
                serviceShape(i,service,bubble,20,height,1);retained(i,bubble,epoch);
                check(status(service)==bubble,"service resizing must reuse the same status View");
            }
            serviceDrag(i,service,bubble,.22f,true);serviceShape(i,service,bubble,20,120,1);
            tap(i,bubble);settle(i,bubble);serviceShape(i,service,bubble,120,120,1);
            serviceDrag(i,service,bubble,.78f,true);serviceShape(i,service,bubble,120,120,-1);
            check(AssistService.isAssistArmed(),"cancel and expand must preserve service ON state");
            check(gestureStatus.equals(AssistService.getLastGestureStatus())
                            && token==(Integer)field(service,"lastGestureToken")
                            && !(Boolean)field(service,"injecting") && !(Boolean)field(service,"gestureInFlight"),
                    "status-control actions must never dispatch an order gesture");
            result.putString("result","PASS native service-window edge collapse: actual20dp WindowManager and measured View widths at both edges, scoped Back exclusion, first strip tap expands and isolates triple sequence,32/120dp collapsed resizing reuses View/clock/opacity, cancellation preserves expanded/collapsed state, no order gesture; direct controller events only");
        }finally{
            try{ui(i,AssistService::stopAssist);}
            finally{
                SharedPreferences.Editor restore=prefs.edit();
                if(hadSize)restore.putInt(SIZE_PREF,originalSize);else restore.remove(SIZE_PREF);
                if(hadOpacity)restore.putInt(OPACITY_PREF,originalOpacity);else restore.remove(OPACITY_PREF);
                check(restore.commit(),"cannot restore service preferences");
                ui(i,()->invoke(service,"updateStatusBubble"));
            }
        }
    }

    private static void expand(Instrumentation i,StatusBubbleView bubble,int size,int side){
        boolean before=active(bubble);long epoch=elapsed(i,bubble);
        tap(i,bubble);i.waitForIdleSync();
        ui(i,()->shape(bubble,size,size,side));
        check(!collapsed(bubble) && active(bubble)==before,"first strip tap expands without toggling assistance");
        ui(i,()->{
            String earliest=bubble.getTimerText(),shown=bubble.getText().toString(),latest=bubble.getTimerText();
            check(!before || (!shown.isEmpty() && (shown.equals(earliest)||shown.equals(latest))),
                    "expanded actual TextView must display the continued elapsed clock");
        });
        retained(i,bubble,epoch);SystemClock.sleep(60);
    }
    private static void tripleIsolation(Instrumentation i,StatusBubbleView bubble){
        boolean before=active(bubble);
        for(int n=0;n<3;n++){
            SystemClock.sleep(60);tap(i,bubble);
            check(active(bubble)==(n<2?before:!before),"expansion must not count as one of the three later taps: "+(n+1));
        }
        for(int n=0;n<3;n++){SystemClock.sleep(60);tap(i,bubble);}
        check(active(bubble)==before,"a second full triple restores assistance state");
    }
    private static void interruptions(Instrumentation i,StatusBubbleView bubble,int size,boolean wasCollapsed){
        boolean before=active(bubble);
        int[] originalSide={0};ui(i,()->originalSide[0]=bubble.getX()<((View)bubble.getParent()).getWidth()/2f?0:1);
        int expectedSide=wasCollapsed?originalSide[0]:-1;
        drag(i,bubble,.78f,true);
        ui(i,()->shape(bubble,wasCollapsed?20:size,size,expectedSide));
        check(collapsed(bubble)==wasCollapsed && active(bubble)==before,"cancel must snap while preserving prior presentation and assistance state");
        float[] p=center(i,bubble);long down=SystemClock.uptimeMillis();
        event(i,bubble,down,MotionEvent.ACTION_DOWN,p[0],p[1]);
        multi(i,bubble,down,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),p[0],p[1]);
        multi(i,bubble,down,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),p[0],p[1]);
        event(i,bubble,down,MotionEvent.ACTION_UP,p[0],p[1]);i.waitForIdleSync();
        ui(i,()->shape(bubble,wasCollapsed?20:size,size,expectedSide));
        check(collapsed(bubble)==wasCollapsed && active(bubble)==before,"multi-pointer input must not expand/collapse or toggle");
    }
    private static SeekBar openSizeMenu(Instrumentation i,Activity a){
        Button menu=button(a.getWindow().getDecorView(),"浮标设置");check(menu!=null,"bubble menu missing");
        if(!menu.isShown()){Button expand=button(a.getWindow().getDecorView(),"展开设置");check(expand!=null,"settings inaccessible");ui(i,expand::performClick);}
        ui(i,menu::performClick);i.waitForIdleSync();
        View size=a.getWindow().getDecorView().findViewWithTag("bubble_size");
        check(size instanceof SeekBar&&size.isShown(),"size slider missing from opened menu");return (SeekBar)size;
    }
    private static Button button(View root,String prefix){
        if(root instanceof Button&&((Button)root).getText().toString().startsWith(prefix))return (Button)root;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int n=0;n<group.getChildCount();n++){Button value=button(group.getChildAt(n),prefix);if(value!=null)return value;}}
        return null;
    }
    private static long elapsed(Instrumentation i,StatusBubbleView bubble){long[] value={0};ui(i,()->value[0]=bubble.getElapsedActiveMillis());return value[0];}
    private static void retained(Instrumentation i,StatusBubbleView bubble,long epoch){
        ui(i,()->{
            check(bubble.getElapsedActiveMillis()>=epoch,"presentation changes must preserve the active clock epoch");
            check(Math.abs(bubble.getAlpha()-.55f)<.001f,"presentation changes must preserve55percent opacity");
            if(collapsed(bubble))check(bubble.getText().length()==0,"collapsed strip must not render timer text");
        });
    }
    private static void serviceDrag(Instrumentation i,Object service,StatusBubbleView bubble,float fraction,boolean cancel){
        if(!cancel){
            WindowManager.LayoutParams lp=(WindowManager.LayoutParams)field(service,"bubbleLayout");int[] screen=(int[])invoke(service,"screenSize");
            float threshold=24*bubble.getResources().getDisplayMetrics().density;
            if(fraction<.5f?lp.x<=threshold:screen[0]-lp.x-lp.width<=threshold){
                float[] from=center(i,bubble);long down=SystemClock.uptimeMillis();
                event(i,bubble,down,MotionEvent.ACTION_DOWN,from[0],from[1]);event(i,bubble,down,MotionEvent.ACTION_MOVE,screen[0]/2f,from[1]);event(i,bubble,down,MotionEvent.ACTION_UP,screen[0]/2f,from[1]);settle(i,bubble);
            }
        }
        float[] start=center(i,bubble),target=new float[2];
        ui(i,()->{int[] screen=(int[])invoke(service,"screenSize");float size=bubble.getHeight(),edge=4*bubble.getResources().getDisplayMetrics().density;target[0]=cancel?screen[0]*fraction:fraction<.5f?size/2+edge:screen[0]-size/2-edge;target[1]=screen[1]*.55f;});
        long down=SystemClock.uptimeMillis();event(i,bubble,down,MotionEvent.ACTION_DOWN,start[0],start[1]);
        event(i,bubble,down,MotionEvent.ACTION_MOVE,target[0],target[1]);
        event(i,bubble,down,cancel?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP,target[0],target[1]);settle(i,bubble);
    }
    private static void serviceShape(Instrumentation i,Object service,StatusBubbleView bubble,int widthDp,int heightDp,int side){
        ui(i,()->{
            WindowManager.LayoutParams lp=(WindowManager.LayoutParams)field(service,"bubbleLayout");
            int[] screen=(int[])invoke(service,"screenSize");float density=bubble.getResources().getDisplayMetrics().density;
            int w=Math.round(widthDp*density),h=Math.round(heightDp*density);
            check(status(service)==bubble&&lp.width==w&&lp.height==h&&bubble.getWidth()==w&&bubble.getHeight()==h,"service must use the actual visible20dp window or expanded selected square");
            check((side<0||lp.x==(side==0?0:screen[0]-w))&&lp.x>=0&&lp.x+w<=screen[0]&&lp.y>=0&&lp.y+h<=screen[1],"service visible bounds must dock inside the physical screen");
            if(Build.VERSION.SDK_INT>=29)check(bubble.getSystemGestureExclusionRects().size()==1&&bubble.getSystemGestureExclusionRects().contains(new Rect(0,0,w,h)),"service Back exclusion must cover only visible bounds");
            check(collapsed(bubble)==(widthDp==20),"service presentation state must agree with visible window width");
        });
    }
    private static void settle(Instrumentation i,View view){
        CountDownLatch frames=new CountDownLatch(1);ui(i,()->view.postOnAnimation(()->view.postOnAnimation(frames::countDown)));
        try{check(frames.await(2,TimeUnit.SECONDS),"status-window layout did not settle");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
        i.waitForIdleSync();
    }
    private static StatusBubbleView status(Object service){return (StatusBubbleView)field(service,"statusBubble");}
    private static Object field(Object service,String name){try{Field value=AssistService.class.getDeclaredField(name);value.setAccessible(true);return value.get(service);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static Object invoke(Object service,String name){try{Method value=AssistService.class.getDeclaredMethod(name);value.setAccessible(true);return value.invoke(service);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}

    private static void drag(Instrumentation i,StatusBubbleView bubble,float fraction,boolean cancel) {
        if(!cancel){
            boolean[] sameEdge={false};float[] middle=new float[1];
            ui(i,()->{View parent=(View)bubble.getParent();float threshold=24*bubble.getResources().getDisplayMetrics().density;sameEdge[0]=fraction<.5f?bubble.getX()<=threshold:parent.getWidth()-bubble.getX()-bubble.getWidth()<=threshold;int[] origin=new int[2];parent.getLocationOnScreen(origin);middle[0]=origin[0]+parent.getWidth()/2f;});
            if(sameEdge[0]){float[] from=center(i,bubble);long down=SystemClock.uptimeMillis();event(i,bubble,down,MotionEvent.ACTION_DOWN,from[0],from[1]);event(i,bubble,down,MotionEvent.ACTION_MOVE,middle[0],from[1]);event(i,bubble,down,MotionEvent.ACTION_UP,middle[0],from[1]);i.waitForIdleSync();}
        }
        float[] start=center(i,bubble),target=new float[2];
        ui(i,()->{
            View parent=(View)bubble.getParent();int[] origin=new int[2];parent.getLocationOnScreen(origin);
            float size=bubble.getHeight(),edge=4*bubble.getResources().getDisplayMetrics().density;
            target[0]=origin[0]+(cancel?parent.getWidth()*fraction:fraction<.5f?size/2+edge:parent.getWidth()-size/2-edge);target[1]=origin[1]+parent.getHeight()*.6f;
        });
        long down=SystemClock.uptimeMillis();
        event(i,bubble,down,MotionEvent.ACTION_DOWN,start[0],start[1]);
        event(i,bubble,down,MotionEvent.ACTION_MOVE,target[0],target[1]);
        event(i,bubble,down,cancel?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP,target[0],target[1]);
        i.waitForIdleSync();
    }

    private static void shape(StatusBubbleView bubble,int widthDp,int heightDp,int side) {
        float density=bubble.getResources().getDisplayMetrics().density;
        int width=Math.round(widthDp*density),height=Math.round(heightDp*density);
        check(bubble.getWidth()==width && bubble.getHeight()==height,
                "expected edge control "+widthDp+"x"+heightDp+"dp, received "+bubble.getWidth()/density+"x"+bubble.getHeight()/density);
        View parent=(View)bubble.getParent();
        if(side>=0)near(side==0?0:parent.getWidth()-width,bubble.getX(),"physical dock must use only the visible control width");
        check(bubble.getY()>=0 && bubble.getY()+height<=parent.getHeight()+1,"edge control must remain vertically in its viewport");
        if(Build.VERSION.SDK_INT>=29){
            List<Rect> exclusions=bubble.getSystemGestureExclusionRects();
            check(exclusions.size()==1 && exclusions.contains(new Rect(0,0,width,height)),
                    "Back exclusion must cover only the actual visible edge control");
        }
    }

    private static boolean collapsed(StatusBubbleView bubble) {
        try{return (Boolean)StatusBubbleView.class.getMethod("isCollapsed").invoke(bubble);}
        catch(ReflectiveOperationException e){throw new AssertionError("status control must expose its collapsed state",e);}
    }
    private static boolean active(StatusBubbleView bubble){return String.valueOf(bubble.getContentDescription()).contains("辅助已开启");}
    private static float[] center(Instrumentation i,View view){
        float[] point=new float[2];ui(i,()->{int[] origin=new int[2];view.getLocationOnScreen(origin);point[0]=origin[0]+view.getWidth()/2f;point[1]=origin[1]+view.getHeight()/2f;});return point;
    }
    private static void event(Instrumentation i,View view,long down,int action,float x,float y){
        ui(i,()->{
            MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            int[] origin=new int[2];view.getLocationOnScreen(origin);event.offsetLocation(-origin[0],-origin[1]);
            try{view.dispatchTouchEvent(event);}finally{event.recycle();}
        });
    }
    private static void tap(Instrumentation i,View view){float[] point=center(i,view);long down=SystemClock.uptimeMillis();event(i,view,down,MotionEvent.ACTION_DOWN,point[0],point[1]);event(i,view,down,MotionEvent.ACTION_UP,point[0],point[1]);i.waitForIdleSync();}
    private static void multi(Instrumentation i,View view,long down,int action,float x,float y){
        ui(i,()->{
            MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[2];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[2];
            for(int n=0;n<2;n++){properties[n]=new MotionEvent.PointerProperties();properties[n].id=n;properties[n].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[n]=new MotionEvent.PointerCoords();coords[n].x=x+n;coords[n].y=y;coords[n].pressure=1;coords[n].size=1;}
            MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,2,properties,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
            int[] origin=new int[2];view.getLocationOnScreen(origin);event.offsetLocation(-origin[0],-origin[1]);
            try{view.dispatchTouchEvent(event);}finally{event.recycle();}
        });
    }
    private static <T> T find(View view,Class<T> type){
        if(type.isInstance(view))return type.cast(view);
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++){T found=find(group.getChildAt(n),type);if(found!=null)return found;}}
        return null;
    }
    private static void ui(Instrumentation i,Runnable run){
        Throwable[] failure={null};i.runOnMainSync(()->{try{run.run();}catch(Throwable e){failure[0]=e;}});
        if(failure[0]!=null)throw new AssertionError(failure[0]);
    }
    private static void near(float expected,float actual,String message){check(Math.abs(expected-actual)<=1,message+": "+actual+" vs "+expected);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
