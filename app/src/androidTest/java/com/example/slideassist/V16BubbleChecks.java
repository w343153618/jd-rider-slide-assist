package com.example.slideassist;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Synthetic native preview checks; no rider app or cross-app gesture is invoked. */
public final class V16BubbleChecks {
    private static final String PREF = "bubble_opacity_percent", SIZE_PREF = "bubble_size_dp";
    public static void run(Instrumentation i, Bundle result) {
        check("com.example.slideassist.fixture".equals(BuildConfig.TARGET_PACKAGE), "use the isolated fixture build");
        SharedPreferences preferences = i.getTargetContext().getSharedPreferences("assist_settings",0);
        boolean hadOpacity = preferences.contains(PREF); int originalOpacity = preferences.getInt(PREF,10);
        boolean hadSize = preferences.contains(SIZE_PREF); int originalSize = preferences.getInt(SIZE_PREF,64);
        AudioManager audio = (AudioManager)i.getTargetContext().getSystemService(android.content.Context.AUDIO_SERVICE);
        check(audio != null, "audio manager unavailable before key tests");
        int[] streams = {AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING, AudioManager.STREAM_SYSTEM,
                AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_ALARM, AudioManager.STREAM_VOICE_CALL};
        int[] levels = new int[streams.length];
        for (int n = 0; n < streams.length; n++) levels[n] = audio.getStreamVolume(streams[n]);
        List<Activity> opened = new ArrayList<>();
        preferences.edit().remove(PREF).remove(SIZE_PREF).commit();
        try {
            Activity a = launch(i); opened.add(a);
            StatusBubbleView bubble = find(a.getWindow().getDecorView(),StatusBubbleView.class);
            check(bubble != null, "Main must display its own status control");
            ui(i, () -> {
                float density = a.getResources().getDisplayMetrics().density;
                check(bubble.getWidth() == Math.round(64 * density) && bubble.getHeight() == Math.round(64 * density),
                        "Main status control must measure 64 by 64 dp, received "
                                + bubble.getWidth()/density + " by " + bubble.getHeight()/density);
                if (Build.VERSION.SDK_INT >= 29) {
                    List<Rect> exclusions = bubble.getSystemGestureExclusionRects();
                    Rect ownBounds = new Rect(0,0,Math.round(64*density),Math.round(64*density));
                    check(exclusions.size()==1 && exclusions.contains(ownBounds),
                            "edge dragging must exclude Back only inside the status control's own square");
                }
                alpha(bubble,.1f,"Main defaults to ten percent opacity");
                ViewGroup parent = (ViewGroup)bubble.getParent();
                near(parent.getWidth()-bubble.getWidth(),bubble.getX(),"initial status control is at right edge");
            });
            SeekBar opacity = openMenu(i,a);
            check(opacity.getMax() == 90 && opacity.getProgress() == 0, "visibility slider must map zero to ten percent and ninety to one hundred");
            changeOpacity(i,opacity,bubble,preferences,90,100);
            changeOpacity(i,opacity,bubble,preferences,45,55);
            sizeControls(i,a,bubble,preferences);
            boolean before = active(bubble);
            tap(i,bubble); check(active(bubble)==before,"single tap must not change Main assistance");
            SystemClock.sleep(60); tap(i,bubble); check(active(bubble)==before,"double tap must not change Main assistance");
            SystemClock.sleep(60); tap(i,bubble); check(active(bubble)!=before,"third tap must toggle Main assistance");
            ui(i,()->alpha(bubble,.55f,"triple tap must preserve visibility setting"));
            before=active(bubble);
            volumeChord(i,a);
            check(active(bubble)!=before,"volume up/down chord must still toggle Main assistance");
            ui(i,()->alpha(bubble,.55f,"volume shortcut must preserve visibility setting"));
            interruptedTapSequences(i,bubble);
            dragToEdge(i,bubble,.24f,0);
            dragToEdge(i,bubble,.78f,1);
            changeOpacity(i,opacity,bubble,preferences,0,10);
            changeOpacity(i,opacity,bubble,preferences,45,55);
            ui(i,a::finish); i.waitForIdleSync();
            Activity reopened=launch(i); opened.add(reopened);
            StatusBubbleView restored=find(reopened.getWindow().getDecorView(),StatusBubbleView.class);
            check(restored!=null,"reopened Main status control missing");
            ui(i,()->alpha(restored,.55f,"visibility setting must survive reopening"));
            check(openMenu(i,reopened).getProgress()==45,"reopened visibility slider must show saved value");
            SeekBar restoredSize=(SeekBar)reopened.getWindow().getDecorView().findViewWithTag("bubble_size");
            check(restoredSize!=null && restoredSize.getProgress()==88,"reopened size slider must restore the selected120dp");
            ui(i,()->bounds(restored,120,1));
            componentChecks(i,reopened);
            result.putString("result","PASS compact bubble: actual Main64dp default and32/64/120dp size control with persistence,10% opacity default, menu 10/55/100% with persistence, exact triple taps and volume chord, cancellation/multi-pointer reset, free drag and left/right release docking, black phone control, enabled elapsed counter and opacity/state preservation with full accessible labels");
        } finally {
            try {
                ui(i,()->{for(Activity activity:opened)if(!activity.isFinishing())activity.finish();});
            } finally {
                try {
                    SharedPreferences.Editor restore=preferences.edit();
                    if(hadOpacity)restore.putInt(PREF,originalOpacity);else restore.remove(PREF);
                    if(hadSize)restore.putInt(SIZE_PREF,originalSize);else restore.remove(SIZE_PREF);
                    restore.commit();
                } finally {
                    restoreAudio(audio,streams,levels);
                }
            }
        }
    }
    private static Activity launch(Instrumentation i) {
        Activity a=i.startActivitySync(new Intent(i.getTargetContext(),MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        i.waitForIdleSync();return a;
    }
    private static SeekBar openMenu(Instrumentation i,Activity a) {
        Button menu=button(a.getWindow().getDecorView(),"浮标设置");
        check(menu!=null,"Main must expose a bubble settings menu");
        if(!menu.isShown()) {
            Button expand=button(a.getWindow().getDecorView(),"展开设置");
            check(expand!=null,"bubble settings must be reachable");ui(i,expand::performClick);
        }
        ui(i,menu::performClick);i.waitForIdleSync();
        View tagged=a.getWindow().getDecorView().findViewWithTag("bubble_opacity");
        check(tagged instanceof SeekBar,"visibility slider with stable control tag missing");
        SeekBar value=(SeekBar)tagged;
        check(value.isShown() && textPrefix(a.getWindow().getDecorView(),"浮标可见度")!=null,
                "opened menu must display its visibility label and control");
        return value;
    }
    private static void changeOpacity(Instrumentation i,SeekBar slider,StatusBubbleView bubble,
                                      SharedPreferences preferences,int progress,int expected) {
        ui(i,()->slider.setProgress(progress));i.waitForIdleSync();
        ui(i,()->alpha(bubble,expected/100f,"visibility menu must immediately update preview"));
        check(preferences.getInt(PREF,-1)==expected,"visibility menu must save selected percentage");
    }
    private static void sizeControls(Instrumentation i,Activity a,StatusBubbleView bubble,SharedPreferences preferences) {
        View tagged=a.getWindow().getDecorView().findViewWithTag("bubble_size");
        check(tagged instanceof SeekBar,"size slider with stable control tag missing");
        SeekBar size=(SeekBar)tagged;
        check(size.isShown() && size.getMax()==88 && size.getProgress()==32,
                "size control must expose32–120dp and default64dp");
        SystemClock.sleep(1100);i.waitForIdleSync();
        check(active(bubble),"size test must begin with the counter running");
        changeSize(i,size,bubble,preferences,0,32,1);
        triplePair(i,bubble);dragToEdge(i,bubble,.24f,0);
        changeSize(i,size,bubble,preferences,88,120,0);
        triplePair(i,bubble);dragToEdge(i,bubble,.78f,1);
        changeSize(i,size,bubble,preferences,32,64,1);
        changeSize(i,size,bubble,preferences,88,120,1);
    }
    private static void changeSize(Instrumentation i,SeekBar slider,StatusBubbleView bubble,
                                   SharedPreferences preferences,int progress,int expected,int side) {
        long[] before={0};ui(i,()->before[0]=elapsed(bubble));
        ui(i,()->slider.setProgress(progress));i.waitForIdleSync();
        ui(i,()->{
            bounds(bubble,expected,side);
            check(active(bubble) && elapsed(bubble)>=before[0],"live size changes must preserve the active timer epoch");
            alpha(bubble,.55f,"size control must not modify opacity");
            if(bubble.getLayout()!=null && bubble.getLayout().getLineCount()>0){
                int line=bubble.getLayout().getLineCount()-1;
                check(bubble.getLayout().getLineBottom(line)<=bubble.getHeight()-bubble.getTotalPaddingTop()-bubble.getTotalPaddingBottom(),
                        "resized counter must remain vertically inside the phone control");
            }
        });
        check(preferences.getInt(SIZE_PREF,-1)==expected,"size setting must save32/default64/maximum120dp");
        check(preferences.getInt(PREF,-1)==55,"size setting must leave opacity preference unchanged");
    }
    private static void bounds(StatusBubbleView bubble,int expectedDp,int side) {
        int size=Math.round(expectedDp*bubble.getResources().getDisplayMetrics().density);
        check(bubble.getWidth()==size && bubble.getHeight()==size,"phone control size must immediately match selected dp");
        View parent=(View)bubble.getParent();
        near(side==0?0:parent.getWidth()-size,bubble.getX(),"resizing must preserve the chosen docked edge");
        check(bubble.getY()>=0 && bubble.getY()+size<=parent.getHeight()+1,"resized phone control must remain on screen");
        if(Build.VERSION.SDK_INT>=29){
            List<Rect> exclusions=bubble.getSystemGestureExclusionRects();
            check(exclusions.size()==1 && exclusions.contains(new Rect(0,0,size,size)),
                    "Back exclusion must follow only the resized phone control");
        }
    }
    private static void triplePair(Instrumentation i,StatusBubbleView bubble) {
        ui(i,bubble::resetTapSequence);boolean before=active(bubble);
        for(int n=0;n<6;n++){
            if(n>0)SystemClock.sleep(60);tap(i,bubble);
            if(n==2)check(active(bubble)!=before,"third tap must toggle at the selected size");
        }
        check(active(bubble)==before,"two complete triples restore the original state at min/max size");
    }
    private static void interruptedTapSequences(Instrumentation i,StatusBubbleView bubble) {
        ui(i,bubble::resetTapSequence); boolean before=active(bubble);
        tap(i,bubble);SystemClock.sleep(60);tap(i,bubble);
        float[] center=center(i,bubble);long down=SystemClock.uptimeMillis();
        event(i,bubble,down,MotionEvent.ACTION_DOWN,center[0],center[1]);
        event(i,bubble,down,MotionEvent.ACTION_CANCEL,center[0],center[1]);
        SystemClock.sleep(60);tap(i,bubble);
        check(active(bubble)==before,"cancelled touch must reset the two earlier taps");
        ui(i,bubble::resetTapSequence);tap(i,bubble);SystemClock.sleep(60);tap(i,bubble);
        center=center(i,bubble);down=SystemClock.uptimeMillis();
        event(i,bubble,down,MotionEvent.ACTION_DOWN,center[0],center[1]);
        multi(i,bubble,down,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),center[0],center[1]);
        multi(i,bubble,down,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),center[0],center[1]);
        event(i,bubble,down,MotionEvent.ACTION_UP,center[0],center[1]);
        SystemClock.sleep(60);tap(i,bubble);
        check(active(bubble)==before,"multi-pointer touch must not toggle or retain earlier taps");
        ui(i,bubble::resetTapSequence);
    }
    private static void dragToEdge(Instrumentation i,StatusBubbleView bubble,float targetFraction,int edge) {
        ui(i,bubble::resetTapSequence);boolean before=active(bubble);
        tap(i,bubble);SystemClock.sleep(60);tap(i,bubble);
        float[] start=center(i,bubble), target=new float[2];
        ui(i,()->{
            View parent=(View)bubble.getParent();int[] origin=new int[2];parent.getLocationOnScreen(origin);
            target[0]=origin[0]+parent.getWidth()*targetFraction;
            target[1]=origin[1]+parent.getHeight()*.58f;
        });
        long down=SystemClock.uptimeMillis();event(i,bubble,down,MotionEvent.ACTION_DOWN,start[0],start[1]);
        event(i,bubble,down,MotionEvent.ACTION_MOVE,target[0],target[1]);
        ui(i,()->{
            View parent=(View)bubble.getParent();float right=parent.getWidth()-bubble.getWidth();
            check(bubble.getX()>1 && bubble.getX()<right-1,"dragged control must be movable away from screen edges before release");
        });
        event(i,bubble,down,MotionEvent.ACTION_UP,target[0],target[1]);i.waitForIdleSync();
        ui(i,()->{
            View parent=(View)bubble.getParent();
            int strip=Math.round(4*bubble.getResources().getDisplayMetrics().density);
            check(bubble.getWidth()==strip && bubble.isCollapsed(),"drag release must leave only a4dp visible strip");
            near(edge==0?0:parent.getWidth()-bubble.getWidth(),bubble.getX(),"release must snap to nearest physical side");
            check(bubble.getY()>=0 && bubble.getY()+bubble.getHeight()<=parent.getHeight()+1,"docked bubble must remain vertically on screen");
        });
        check(active(bubble)==before,"drag after two taps must never become the third tap");
        tap(i,bubble);i.waitForIdleSync();
        ui(i,()->{
            check(!bubble.isCollapsed() && bubble.getWidth()==bubble.getHeight(),"one strip tap must restore the selected full control");
            View parent=(View)bubble.getParent();
            near(edge==0?0:parent.getWidth()-bubble.getWidth(),bubble.getX(),"expansion must retain the selected physical side");
        });
        check(active(bubble)==before,"expansion after drag must not count as the third toggle tap");
    }
    private static void componentChecks(Instrumentation i,Activity a) {
        StatusBubbleView[] control=new StatusBubbleView[1];int[] toggles={0};
        ui(i,()->{
            FrameLayout host=new FrameLayout(a);a.setContentView(host);
            StatusBubbleView bubble=new StatusBubbleView(a);control[0]=bubble;
            float density=a.getResources().getDisplayMetrics().density;
            host.addView(bubble,new FrameLayout.LayoutParams(Math.round(64*density),Math.round(64*density)));
            bubble.setOnClickListener(v->toggles[0]++);alpha(bubble,.1f,"new status control defaults to ten percent");
            for(int opacity:new int[]{10,35,100}){
                bubble.setOpacityPercent(opacity);
                for(boolean active:new boolean[]{false,true,false}){
                    bubble.setActive(active);alpha(bubble,opacity/100f,"state changes must preserve selected opacity");
                    check(bubble.getOpacityPercent()==opacity,"selected opacity can be read back");
                    check((active ? "00:00" : "").contentEquals(bubble.getText()),"only enabled state displays an elapsed counter");
                    check(bubble.getContentDescription().toString().contains(active?"辅助已开启":"辅助已关闭"),"accessible state description stays complete");
                    GradientDrawable background=(GradientDrawable)bubble.getBackground();
                    check(background.getColor()!=null && background.getColor().getDefaultColor()==0xff000000,"status control must use an opaque black square before view opacity");
                }
            }
        });i.waitForIdleSync();
        timerChecks(i,control[0]);
        ui(i,()->{float density=a.getResources().getDisplayMetrics().density;check(control[0].getWidth()==Math.round(64*density) && control[0].getHeight()==Math.round(64*density),"standalone phone control follows the larger default geometry");});
        ui(i,()->check(!control[0].performClick() && toggles[0]==0,"one tap must not toggle"));SystemClock.sleep(60);
        ui(i,()->check(!control[0].performClick() && toggles[0]==0,"two taps must not toggle"));SystemClock.sleep(60);
        ui(i,()->check(control[0].performClick() && toggles[0]==1,"third tap toggles once"));
    }
    private static void volumeChord(Instrumentation i,Activity a) {
        long t=SystemClock.uptimeMillis();
        ui(i,()->{
            a.dispatchKeyEvent(new KeyEvent(t,t,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_UP,0,0,7,0));
            a.dispatchKeyEvent(new KeyEvent(t,t+40,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_DOWN,0,0,7,0));
            a.dispatchKeyEvent(new KeyEvent(t,t+50,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_VOLUME_DOWN,0,0,7,0));
            a.dispatchKeyEvent(new KeyEvent(t,t+60,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_VOLUME_UP,0,0,7,0));
        });
    }
    private static void timerChecks(Instrumentation i,StatusBubbleView bubble) {
        ui(i,()->{
            bubble.setActive(false);
            check(timerText(bubble).isEmpty(),"disabled phone control must not display a counter");
            bubble.setActive(true);
            check("00:00".equals(timerText(bubble)),"enabling starts a count-up counter at zero");
        });
        SystemClock.sleep(1250);i.waitForIdleSync();
        long[] elapsed = {0};
        ui(i,()->{
            elapsed[0]=elapsed(bubble);
            check(elapsed[0]>=1000,"enabled counter must measure real elapsed duration");
            check(timerText(bubble).matches("[0-9]{2,}:[0-9]{2}") && !"00:00".equals(timerText(bubble)),
                    "enabled counter diagnostic must advance after a second");
            check(String.valueOf(bubble.getText()).matches("[0-9]{2,}:[0-9]{2}")
                            && !"00:00".contentEquals(bubble.getText()),
                    "the actually displayed counter must advance, not only its clock getter");
            bubble.setOpacityPercent(35);bubble.setActive(true);
            check(elapsed(bubble)>=elapsed[0] && !"00:00".equals(timerText(bubble)),
                    "opacity or repeated same-state updates must not restart the counter");
            bubble.setActive(false);
            check(timerText(bubble).isEmpty() && bubble.getText().length()==0,"turning off removes the timer text");
            elapsed[0]=elapsed(bubble);
        });
        SystemClock.sleep(1100);i.waitForIdleSync();
        ui(i,()->{
            check(timerText(bubble).isEmpty() && bubble.getText().length()==0
                    && elapsed(bubble)==elapsed[0],"disabled counter must remain stopped");
            bubble.setActive(true);
            check(elapsed(bubble)<250 && "00:00".equals(timerText(bubble)),"a new enabled interval starts from zero");
            bubble.setActive(false);
        });
    }
    private static String timerText(StatusBubbleView b){return String.valueOf(read(b,"getTimerText"));}
    private static long elapsed(StatusBubbleView b){return ((Number)read(b,"getElapsedActiveMillis")).longValue();}
    private static Object read(StatusBubbleView b,String method){
        try{return StatusBubbleView.class.getMethod(method).invoke(b);}
        catch(Exception e){throw new AssertionError("status control must expose elapsed-counter diagnostics: "+method,e);}
    }
    private static void restoreAudio(AudioManager audio,int[] streams,int[] before) {
        RuntimeException failure = null;
        for (int n = 0; n < streams.length; n++) {
            try { audio.setStreamVolume(streams[n],before[n],0); }
            catch (RuntimeException error) { if (failure == null) failure = error; }
        }
        for (int n = 0; n < streams.length; n++) {
            int after = audio.getStreamVolume(streams[n]);
            android.util.Log.i("VolumeRestore","V16 stream=" + streams[n] + " before=" + before[n] + " after=" + after);
            if (after != before[n] && failure == null)
                failure = new IllegalStateException("could not restore audio stream " + streams[n]);
        }
        if (failure != null) throw new AssertionError("audio restoration failed",failure);
    }
    private static boolean active(StatusBubbleView b){return String.valueOf(b.getContentDescription()).contains("辅助已开启");}
    private static void tap(Instrumentation i,StatusBubbleView b){float[] p=center(i,b);long t=SystemClock.uptimeMillis();event(i,b,t,MotionEvent.ACTION_DOWN,p[0],p[1]);event(i,b,t,MotionEvent.ACTION_UP,p[0],p[1]);}
    private static float[] center(Instrumentation i,View b){float[] p=new float[2];ui(i,()->{int[] origin=new int[2];b.getLocationOnScreen(origin);p[0]=origin[0]+b.getWidth()/2f;p[1]=origin[1]+b.getHeight()/2f;});return p;}
    private static void event(Instrumentation i,View v,long down,int action,float globalX,float globalY){
        ui(i,()->{MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,globalX,globalY,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);dispatchGlobal(v,e);});
    }
    private static void multi(Instrumentation i,View v,long down,int action,float x,float y){
        ui(i,()->{
            MotionEvent.PointerProperties[] properties={new MotionEvent.PointerProperties(),new MotionEvent.PointerProperties()};
            MotionEvent.PointerCoords[] points={new MotionEvent.PointerCoords(),new MotionEvent.PointerCoords()};
            for(int n=0;n<2;n++){properties[n].id=n;properties[n].toolType=MotionEvent.TOOL_TYPE_FINGER;points[n].x=x+n*2;points[n].y=y+n*2;points[n].pressure=1;points[n].size=1;}
            MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,2,properties,points,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);dispatchGlobal(v,e);
        });
    }
    private static void dispatchGlobal(View v,MotionEvent e){
        // Preserve global raw coordinates while making X/Y local to the current translated View.
        int[] origin=new int[2];v.getLocationOnScreen(origin);e.offsetLocation(-origin[0],-origin[1]);
        try{v.dispatchTouchEvent(e);}finally{e.recycle();}
    }
    private static void ui(Instrumentation i,Runnable run){Throwable[] failure={null};i.runOnMainSync(()->{try{run.run();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
    private static <T>T find(View v,Class<T> type){if(type.isInstance(v))return type.cast(v);if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){T f=find(((ViewGroup)v).getChildAt(n),type);if(f!=null)return f;}return null;}
    private static Button button(View v,String prefix){if(v instanceof Button&&String.valueOf(((Button)v).getText()).startsWith(prefix))return (Button)v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){Button f=button(((ViewGroup)v).getChildAt(n),prefix);if(f!=null)return f;}return null;}
    private static TextView textPrefix(View v,String prefix){if(v instanceof TextView&&String.valueOf(((TextView)v).getText()).startsWith(prefix))return (TextView)v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){TextView f=textPrefix(((ViewGroup)v).getChildAt(n),prefix);if(f!=null)return f;}return null;}
    private static void alpha(View v,float expected,String message){check(Math.abs(v.getAlpha()-expected)<.001f,message+": "+v.getAlpha());}
    private static void near(float expected,float actual,String message){check(Math.abs(expected-actual)<1.1f,message+": "+actual+" expected "+expected);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
