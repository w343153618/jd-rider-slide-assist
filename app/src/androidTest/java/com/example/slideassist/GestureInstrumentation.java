package com.example.slideassist;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.RadioButton;
import java.util.ArrayList;
import java.util.List;

/** Device-side integration checks using actual injected touchscreen events, no test libraries. */
public final class GestureInstrumentation extends Instrumentation {
    private final List<DemoSliderView> sliders = new ArrayList<>();
    private ScrollView scroll;
    private RadioButton gestureMode, releaseMode, immediateMode, slowDuration;
    private final java.util.Map<Integer, RadioButton> durationOptions = new java.util.HashMap<>();
    private android.widget.Button expandSettings;
    private View demoBubble;
    private boolean serviceTest, emulatorHardware, volumeTest, bubbleTest, updateTest, publishTest;
    private String pendingTest;

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); publishTest = arguments.getString("publish") != null; pendingTest = arguments.getString("pending"); updateTest = arguments.getString("update") != null; volumeTest = arguments.getString("volume") != null; bubbleTest = arguments.getString("bubble") != null; serviceTest = arguments.getString("service") != null; emulatorHardware = "hardware".equals(arguments.getString("service")) || "hardware".equals(arguments.getString("bubble")); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (publishTest) { testPublish(result); return; }
            if (pendingTest != null) { testPending(result); return; }
            if (updateTest) { testUpdate(result); return; }
            if (volumeTest) { testVolume(result); return; }
            if (bubbleTest) { testBubble(result); return; }
            if (serviceTest) { testService(result); return; }
            getTargetContext().getSharedPreferences("assist_settings", 0).edit().remove("trigger_mode").remove("swipe_duration_ms").commit();
            Intent intent = new Intent(getTargetContext(), MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            Activity activity = startActivitySync(intent);
            waitForIdleSync();
            runOnMainSync(() -> {
                View root = activity.getWindow().getDecorView();
                findViews(root);
            });
            check(sliders.size() >= 2 && scroll != null, "expected multiple demo orders");
            for (int millis : new int[]{100,150,200,250,450,700})
                check(durationOptions.containsKey(millis), "duration option missing: " + millis);
            check(durationOptions.get(250).isChecked(), "250ms must be selected by default");
            DemoSliderView one = sliders.get(0), two = sliders.get(1);
            select(one); outsideTap(one); pause(); check(!done(one), "touch outside slider must not complete");
            select(one); outsideDragIn(one); pause(); check(!done(one), "outside start dragged into slider must not complete");
            select(one); downHeld(one); check(done(one), "default mode must complete on down before release");
            check(demoBubble != null && String.valueOf(demoBubble.getContentDescription()).contains("辅助已关闭"), "demo bubble should turn off after success");
            runOnMainSync(one::reset); select(one); tap(one); pause(); check(!done(one), "OFF demo bubble must prevent assisted tap");
            runOnMainSync(() -> demoBubble.performClick());
            check(String.valueOf(demoBubble.getContentDescription()).contains("辅助已开启"), "demo bubble should turn on when clicked");
            reset(one);
            runOnMainSync(() -> { if (releaseMode == null) throw new AssertionError("release mode option missing"); releaseMode.performClick(); });
            waitForIdleSync();
            select(one); releaseHeld(one); pause(); check(done(one), "release mode must complete after up");
            reset(one); select(one); outsideDragIn(one); pause(); check(!done(one), "outside start must not trigger release mode");
            reset(one); select(one); releaseOutside(one); pause(); check(done(one), "release outside must complete when first down inside");
            reset(one); select(one); releaseFirstFinger(one); pause(); check(done(one), "release first finger must complete even if second finger stays down");
            reset(one);
            runOnMainSync(() -> { if (gestureMode == null) throw new AssertionError("gesture mode option missing"); gestureMode.performClick(); });
            waitForIdleSync();
            select(one); tap(one); pause(); check(!done(one), "gesture mode single tap must not complete");
            reset(one); select(one); doubleTap(one); pause(); check(done(one), "single-finger double tap");
            check(!done(two), "first order completion must not complete second");
            reset(one); select(one); twoFingerTap(one); pause(); check(done(one), "two-finger tap");
            reset(one); select(one); move(one); pause(); check(!done(one), "movement must not complete");
            reset(one); select(one); threeFingerTap(one); pause(); check(!done(one), "three-finger tap must not complete");
            select(two); doubleTap(two); pause(); check(done(two), "second order double tap");
            check(!done(one), "second order completion must not complete first");
            reset(two); reset(one); select(one);
            runOnMainSync(() -> {
                check(expandSettings != null && immediateMode != null && slowDuration != null, "settings controls missing");
                expandSettings.performClick(); immediateMode.performClick();
                for (int millis : new int[]{100,150,200}) {
                    durationOptions.get(millis).performClick();
                    check(getTargetContext().getSharedPreferences("assist_settings",0).getInt("swipe_duration_ms",0)==millis,
                            "duration selection not saved: " + millis);
                }
                slowDuration.performClick();
            });
            check(getTargetContext().getSharedPreferences("assist_settings",0).getInt("swipe_duration_ms",0)==700,
                    "duration setting must persist in preferences");
            select(one); float[] durationPoint=center(one); long durationDown=SystemClock.uptimeMillis();
            send(durationDown,durationDown,MotionEvent.ACTION_DOWN,new float[][]{durationPoint});
            check(String.valueOf(demoBubble.getContentDescription()).contains("辅助已关闭"), "first slide must close assist immediately");
            runOnMainSync(() -> {
                long now=SystemClock.uptimeMillis(); float x=two.getWidth()/2f, y=two.getHeight()/2f;
                MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0);
                MotionEvent up=MotionEvent.obtain(now,now+40,MotionEvent.ACTION_UP,x,y,0);
                try { two.dispatchTouchEvent(down); two.dispatchTouchEvent(up); } finally { down.recycle(); up.recycle(); }
            });
            SystemClock.sleep(350); check(!done(one), "700ms demo should still animate after 350ms");
            SystemClock.sleep(500); waitForIdleSync(); check(done(one), "700ms demo must complete after animation");
            check(!done(two), "touching another order while first animates must not start another assisted swipe");
            send(durationDown,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,new float[][]{durationPoint});
            runOnMainSync(activity::finish); waitForIdleSync();
            sliders.clear(); durationOptions.clear(); scroll=null; slowDuration=null;
            Activity reopened=startActivitySync(intent); waitForIdleSync();
            runOnMainSync(() -> findViews(reopened.getWindow().getDecorView()));
            check(slowDuration != null && slowDuration.isChecked(), "700ms choice must remain selected after reopening");
            result.putString("result", "PASS: immediate on down while held, outside touch and drag, release on up not down, release outside, first finger release, gesture mode single tap, double tap, two-finger tap, movement, three-finger tap, independent orders, demo bubble auto-OFF and tap to enable, second-order race prevented, all six duration options and faster selections, 700ms demo timing and selection after reopening");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable e) {
            result.putString("result", "FAIL: " + e);
            result.putString("stack", android.util.Log.getStackTraceString(e));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private interface ThrowingAction { void run() throws Exception; }
    private void rejects(ThrowingAction action, String message) throws Exception {
        boolean rejected=false; try { action.run(); } catch (Exception expected) { rejected=true; }
        check(rejected,message);
    }
    private void testPublish(Bundle result) throws Exception {
        android.content.Context c=getTargetContext();
        byte[] data=("fixture payload " + java.util.UUID.randomUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String digest=UpdateManager.hex(java.security.MessageDigest.getInstance("SHA-256").digest(data));
        java.io.File target=UpdateApkProvider.updateFile(c,digest), dir=target.getParentFile();
        check(dir.isDirectory() || dir.mkdirs(),"cannot create fixture update directory");
        check(!target.exists(),"fixture digest unexpectedly exists");
        java.io.File first=java.io.File.createTempFile("publish-first-", ".apk",dir);
        java.io.File second=null;
        try {
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(first)){out.write(data);}
            // Caller verification is exercised separately; this test isolates filesystem publish.
            UpdateManager.publishVerified(c,first,target,digest,"v9.9.9");
            check(!first.exists() && target.isFile(),"new target must be published by rename");
            try(android.os.ParcelFileDescriptor fd=c.getContentResolver().openFileDescriptor(UpdateApkProvider.updateUri(c,digest),"r");
                java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) {
                java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream(); byte[] buf=new byte[128]; int n;
                while((n=in.read(buf))!=-1) output.write(buf,0,n);
                check(java.util.Arrays.equals(output.toByteArray(),data),"provider must read exactly published bytes");
            }
            byte[] existing="existing tampered bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(target)){out.write(existing);}
            second=java.io.File.createTempFile("publish-second-", ".apk",dir);
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(second)){out.write(data);}
            final java.io.File next=second;
            rejects(() -> UpdateManager.publishVerified(c,next,target,digest,"v9.9.9"),"existing wrong digest must be rejected");
            byte[] after=new byte[(int)target.length()];
            try(java.io.FileInputStream in=new java.io.FileInputStream(target)) {
                int offset=0, count; while(offset<after.length && (count=in.read(after,offset,after.length-offset))>0) offset+=count;
                check(offset==after.length,"cannot read complete existing bytes");
            }
            check(java.util.Arrays.equals(after,existing),"existing file must never be overwritten");
            check(second.exists(),"rejected temporary file must not be renamed over existing target");
            result.putString("result","PASS verified file publication: new same-directory rename and read-only provider; existing wrong digest rejected without overwrite");
        } finally {
            if(first.exists()) first.delete(); if(second!=null && second.exists()) second.delete(); if(target.exists()) target.delete();
        }
        finish(Activity.RESULT_OK,result);
    }

    private void testUpdate(Bundle result) throws Exception {
        check(UpdateManager.compare(UpdateManager.parseVersion("v1.2.0"),UpdateManager.parseVersion("1.1.9")) > 0, "version order");
        check(UpdateManager.compare(UpdateManager.parseVersion("1.2.0"),UpdateManager.parseVersion("v1.2.0")) == 0, "tag equivalence");
        rejects(() -> UpdateManager.parseVersion("v1.2.0-rc1"),"prerelease version must be rejected");
        rejects(() -> UpdateManager.parseVersion("v1.2"),"incomplete version must be rejected");
        rejects(() -> UpdateManager.requireHost(new java.net.URL("http://github.com/example")),"http host must be rejected");
        rejects(() -> UpdateManager.requireHost(new java.net.URL("https://github.com.example.invalid/")),"host suffix spoof must be rejected");
        rejects(() -> UpdateManager.requireHost(new java.net.URL("https://github.com@evil.invalid/")),"URL user info must be rejected");
        rejects(() -> UpdateManager.requireHost(new java.net.URL("https://github.com:444/")),"unexpected port must be rejected");
        UpdateManager.requireHost(new java.net.URL("https://github.com/"));
        String asset="https://github.com/w343153618/jd-rider-slide-assist/releases/download/v1.2.0/jd-rider-slide-assist.apk";
        testUpdateMetadata(asset);
        UpdateManager.requireAssetUrl(asset,"v1.2.0","jd-rider-slide-assist.apk");
        rejects(() -> UpdateManager.requireAssetUrl(asset.replace("v1.2.0","v1.2.1"),"v1.2.0","jd-rider-slide-assist.apk"),"mismatched release tag must be rejected");
        java.lang.reflect.Field hashField=UpdateManager.class.getDeclaredField("SHA_PATTERN"); hashField.setAccessible(true);
        java.util.regex.Pattern hashPattern=(java.util.regex.Pattern)hashField.get(null);
        String digest="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        check(hashPattern.matcher(digest).matches(),"plain checksum accepted");
        check(hashPattern.matcher(digest+"  jd-rider-slide-assist.apk\n").matches(),"named checksum accepted");
        check(!hashPattern.matcher(digest+"  other.apk").matches(),"wrong checksum filename rejected");
        android.content.Context c=getTargetContext();
        java.io.File file=UpdateApkProvider.updateFile(c,digest); java.io.File parent=file.getParentFile();
        check(parent.isDirectory() || parent.mkdirs(),"cannot create test update directory");
        check(file.createNewFile(),"test digest file already exists; refusing to overwrite");
        try {
            try (java.io.FileOutputStream out=new java.io.FileOutputStream(file)) { out.write(new byte[]{1,2,3}); }
            android.net.Uri uri=UpdateApkProvider.updateUri(c,digest);
            try (android.os.ParcelFileDescriptor pfd=c.getContentResolver().openFileDescriptor(uri,"r")) { check(pfd != null,"provider read should succeed"); }
            rejects(() -> c.getContentResolver().openFileDescriptor(uri,"w"),"provider must reject writes");
            android.net.Uri invalid=uri.buildUpon().appendQueryParameter("x","1").build();
            rejects(() -> c.getContentResolver().openFileDescriptor(invalid,"r"),"provider must reject unexpected query");
            rejects(() -> UpdateApkProvider.updateFile(c,"../invalid"),"invalid digest rejected");
        } finally { check(file.delete(),"test digest cleanup failed"); }
        java.lang.reflect.Method archive=UpdateManager.class.getDeclaredMethod("verifyArchive",android.content.Context.class,java.io.File.class,String.class);
        archive.setAccessible(true);
        String ownVersion=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;
        java.io.File ownApk=new java.io.File(c.getApplicationInfo().sourceDir);
        rejects(() -> archive.invoke(null,c,ownApk,ownVersion),"same version must be rejected");
        java.io.File foreignApk=new java.io.File(c.getPackageManager().getApplicationInfo(BuildConfig.TARGET_PACKAGE,0).sourceDir);
        rejects(() -> archive.invoke(null,c,foreignApk,ownVersion),"different application ID must be rejected");
        java.io.File wrongSigner=java.io.File.createTempFile("wrong-signer-", ".apk", c.getCacheDir());
        try {
            android.os.ParcelFileDescriptor fd=getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                    .executeShellCommand("cat /data/local/tmp/slide-wrong-signer.apk");
            try (java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd);
                 java.io.OutputStream out=new java.io.FileOutputStream(wrongSigner)) {
                byte[] buf=new byte[1024]; int n; while ((n=in.read(buf))!=-1) out.write(buf,0,n);
            }
            check(wrongSigner.length()>0,"prepare wrong-signer fixture APK first");
            try {
                archive.invoke(null,c,wrongSigner,"9.9.9");
                throw new AssertionError("wrong signing key must be rejected");
            } catch (java.lang.reflect.InvocationTargetException e) {
                check(String.valueOf(e.getCause().getMessage()).contains("签名"), "wrong signer must fail specifically on signature: "+e.getCause());
            }
        } finally { check(wrongSigner.delete(),"wrong signer test cleanup"); }
        result.putString("result","PASS updater: metadata parsing, versions, hosts, asset URL, checksum format, provider read-only/URI, same-version, foreign-package and wrong-signer rejection");
        finish(Activity.RESULT_OK,result);
    }

    private void testUpdateMetadata(String asset) throws Exception {
        org.json.JSONObject apk=new org.json.JSONObject().put("name","jd-rider-slide-assist.apk").put("browser_download_url",asset);
        org.json.JSONObject sha=new org.json.JSONObject().put("name","jd-rider-slide-assist.apk.sha256").put("browser_download_url",asset+".sha256");
        org.json.JSONObject valid=new org.json.JSONObject().put("tag_name","v1.2.0").put("draft",false).put("prerelease",false)
                .put("body","release notes").put("assets",new org.json.JSONArray().put(apk).put(sha));
        UpdateManager.ReleaseMetadata release=UpdateManager.parseMetadata(valid.toString());
        check("v1.2.0".equals(release.tag) && asset.equals(release.apkUrl) && (asset+".sha256").equals(release.shaUrl),"valid metadata must parse");
        org.json.JSONObject draft=new org.json.JSONObject(valid.toString()).put("draft",true);
        rejects(() -> UpdateManager.parseMetadata(draft.toString()),"draft releases rejected");
        org.json.JSONObject pre=new org.json.JSONObject(valid.toString()).put("prerelease",true);
        rejects(() -> UpdateManager.parseMetadata(pre.toString()),"prereleases rejected");
        org.json.JSONObject missing=new org.json.JSONObject(valid.toString()); missing.remove("draft");
        rejects(() -> UpdateManager.parseMetadata(missing.toString()),"missing publication status rejected");
        org.json.JSONObject wrongType=new org.json.JSONObject(valid.toString()).put("draft","false");
        rejects(() -> UpdateManager.parseMetadata(wrongType.toString()),"publication status must be a boolean");
        org.json.JSONObject duplicate=new org.json.JSONObject(valid.toString()).put("assets",new org.json.JSONArray().put(apk).put(apk).put(sha));
        rejects(() -> UpdateManager.parseMetadata(duplicate.toString()),"duplicate asset rejected");
        org.json.JSONObject absent=new org.json.JSONObject(valid.toString()).put("assets",new org.json.JSONArray().put(apk));
        rejects(() -> UpdateManager.parseMetadata(absent.toString()),"missing checksum asset rejected");
        org.json.JSONObject badUrl=new org.json.JSONObject(valid.toString()).put("assets",new org.json.JSONArray().put(
                new org.json.JSONObject().put("name","jd-rider-slide-assist.apk").put("browser_download_url","https://evil.invalid/payload.apk")).put(sha));
        rejects(() -> UpdateManager.parseMetadata(badUrl.toString()),"untrusted asset URL rejected");
        StringBuilder longNotes=new StringBuilder(); for(int i=0; i<2000; i++) longNotes.append('x');
        org.json.JSONObject longBody=new org.json.JSONObject(valid.toString()).put("body",longNotes.toString());
        String notes=UpdateManager.parseMetadata(longBody.toString()).notes;
        check(notes.length()==1601 && notes.endsWith("…"),"release notes must be truncated for display");
    }

    private void testPending(Bundle result) throws Exception {
        android.content.Context c=getTargetContext();
        boolean allowed="allowed".equals(pendingTest);
        check(allowed || "denied".equals(pendingTest), "invalid pending test mode");
        check(c.getPackageManager().canRequestPackageInstalls()==allowed, "set test app install permission before starting instrumentation");
        String digest="fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";
        java.io.File file=UpdateApkProvider.updateFile(c,digest);
        check(file.getParentFile().isDirectory() || file.getParentFile().mkdirs(),"cannot create pending update directory");
        check(file.createNewFile(),"pending-test file already exists");
        try {
            try (java.io.FileOutputStream out=new java.io.FileOutputStream(file)) { out.write(new byte[]{1,2,3}); }
            android.content.SharedPreferences pending=c.getSharedPreferences("pending_update",0);
            pending.edit().putBoolean("waiting",true).putString("digest",digest).putString("tag","v9.9.9").commit();
            Activity activity=startUpdateActivity();
            waitForDialog(allowed ? "安装包校验失败" : "尚未允许安装");
            check(!pending.getBoolean("waiting",false),"resume should consume pending state without installing");
            getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                    .performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
            runOnMainSync(activity::finish); waitForIdleSync();
            result.putString("result","PASS pending update recreation, install permission " + pendingTest + "; no installation launched");
        } finally {
            c.getSharedPreferences("pending_update",0).edit().clear().commit();
            check(file.delete(),"pending test cleanup failed");
        }
        finish(Activity.RESULT_OK,result);
    }
    private Activity startUpdateActivity() {
        Intent intent=new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity=startActivitySync(intent); waitForIdleSync(); return activity;
    }
    private void waitForDialog(String text) {
        for (int i=0; i<80; i++) {
            android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).getRootInActiveWindow();
            if (root!=null) {
                try {
                    if (getTargetContext().getPackageName().contentEquals(root.getPackageName()) && !root.findAccessibilityNodeInfosByText(text).isEmpty()) return;
                } finally { root.recycle(); }
            }
            SystemClock.sleep(50);
        }
        throw new AssertionError("expected own-app dialog not found: " + text);
    }

    private void testBubble(Bundle result) throws Exception {
        check(emulatorHardware && "ranchu".equals(android.os.Build.HARDWARE) && android.os.Build.PRODUCT.startsWith("sdk_gphone"),
                "bubble integration test requires an isolated emulator");
        check("com.example.slideassist.fixture".equals(BuildConfig.TARGET_PACKAGE), "bubble test requires fixture build");
        for (int i=0; i<600 && !AssistService.isConnected(); i++) SystemClock.sleep(50);
        check(AssistService.isConnected(), "accessibility service not connected");
        boolean screenOff=false;
        try {
            runOnMainSync(AssistService::stopAssist);
            Intent fixture=getTargetContext().getPackageManager().getLaunchIntentForPackage(BuildConfig.TARGET_PACKAGE);
            check(fixture != null, "fixture not installed"); fixture.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getTargetContext().startActivity(fixture); SystemClock.sleep(1000);
            getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            View bubble=awaitBubble(true);
            check(!uiArmed() && bubbleDescription().contains("辅助已关闭"), "target must show OFF bubble while disarmed");
            int before=fixtureResult()[0];
            tapBubble(bubble); awaitArmed(true,"bubble should enable assist");
            check(bubbleDescription().contains("辅助已开启"), "bubble must show ON after activation");
            SystemClock.sleep(300); check(fixtureResult()[0]==before, "enabling bubble must not dispatch a swipe");
            bubble=awaitBubble(true); tapBubble(bubble); awaitArmed(false,"bubble should disable assist");
            check(bubbleDescription().contains("辅助已关闭"), "bubble must show OFF after disabling");
            check(fixtureResult()[0]==before, "bubble toggle must not dispatch a swipe");
            serviceCase("release",true);
            awaitBubble(true); check(!uiArmed() && bubbleDescription().contains("辅助已关闭"), "after one swipe bubble must return OFF");
            Intent other=new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getTargetContext().startActivity(other); awaitBubble(false);
            getTargetContext().startActivity(fixture); awaitBubble(true);
            shellCommand("input keyevent KEYCODE_SLEEP"); screenOff=true;
            awaitBubble(false);
            result.putString("result", "PASS bubble: OFF/ON, taps never swipe, OFF after gesture, hidden outside target and on screen off");
        } finally {
            if (screenOff) { shellCommand("input keyevent KEYCODE_WAKEUP"); shellCommand("wm dismiss-keyguard"); }
            runOnMainSync(AssistService::stopAssist);
        }
        finish(Activity.RESULT_OK,result);
    }
    private View awaitBubble(boolean present) throws Exception {
        for (int i=0; i<80; i++) {
            View view=(View) serviceField("statusBubble");
            boolean[] attached={false};
            if (view!=null) runOnMainSync(() -> attached[0]=view.isAttachedToWindow());
            if (present && view!=null && attached[0]) return view;
            if (!present && (view==null || !attached[0])) return null;
            SystemClock.sleep(50);
        }
        throw new AssertionError("status bubble not " + (present ? "visible" : "hidden"));
    }
    private String bubbleDescription() throws Exception {
        View view=awaitBubble(true); String[] value={""};
        runOnMainSync(() -> value[0]=String.valueOf(view.getContentDescription())); return value[0];
    }
    private void tapBubble(View view) throws Exception {
        float[] center=new float[2]; runOnMainSync(() -> { int[] p=new int[2]; view.getLocationOnScreen(p); center[0]=p[0]+view.getWidth()/2f; center[1]=p[1]+view.getHeight()/2f; });
        long t=SystemClock.uptimeMillis(); sendGlobal(t,t,MotionEvent.ACTION_DOWN,center,false);
        sendGlobal(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,center,false);
    }
    private void shellCommand(String command) throws Exception {
        android.os.ParcelFileDescriptor fd=getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeShellCommand(command);
        try (java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) { while (in.read()!=-1) {} }
    }

    private void testVolume(Bundle result) throws Exception {
        check("com.example.slideassist.fixture".equals(BuildConfig.TARGET_PACKAGE), "volume test requires fixture build");
        for (int i=0; i<600 && !AssistService.isConnected(); i++) SystemClock.sleep(50);
        check(AssistService.isConnected(), "accessibility service is not connected");
        android.media.AudioManager audio=(android.media.AudioManager) getTargetContext().getSystemService(android.content.Context.AUDIO_SERVICE);
        int[] streams={android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.STREAM_RING,
                android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.STREAM_NOTIFICATION,
                android.media.AudioManager.STREAM_ALARM, android.media.AudioManager.STREAM_VOICE_CALL};
        int[] levels=new int[streams.length];
        for (int i=0; i<streams.length; i++) levels[i]=audio.getStreamVolume(streams[i]);
        try {
            runOnMainSync(AssistService::stopAssist);
            Intent fixture=getTargetContext().getPackageManager().getLaunchIntentForPackage(BuildConfig.TARGET_PACKAGE);
            check(fixture != null, "install isolated fixture first");
            fixture.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); getTargetContext().startActivity(fixture);
            SystemClock.sleep(900);
            getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            int before=fixtureResult()[0];
            check(!uiArmed(), "initially disarmed");

            runKeyFixture("single");
            SystemClock.sleep(150); check(!uiArmed(), "single volume key must not arm");
            runKeyFixture("slow");
            SystemClock.sleep(150); check(!uiArmed(), "keys separated by more than 200ms must not arm");

            java.io.BufferedReader held=startKeyFixture("hold");
            awaitArmed(true, "physical-source chord must enable overlay in target app");
            SystemClock.sleep(350); check(uiArmed(), "repeated keydown while held must not toggle twice");
            finishKeyFixture(held);
            SystemClock.sleep(150); check(uiArmed(), "releasing keys must not toggle");
            runKeyFixture("reverse");
            awaitArmed(false, "new reversed chord after both releases must disable");
            check(fixtureResult()[0] == before, "volume chords must not dispatch a swipe");
            check("idle".equals(AssistService.getLastGestureStatus()), "volume chord must not send a gesture");

            Intent settings=new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getTargetContext().startActivity(settings); SystemClock.sleep(900);
            runKeyFixture("reverse");
            SystemClock.sleep(300); check(!uiArmed(), "chord outside target must not activate");
            result.putString("result", "PASS volume key filter: single and slow keys do not arm, chord enables without swipe, repeats do not retoggle, release plus new chord disables, other app cannot activate");
        } finally {
            runOnMainSync(AssistService::stopAssist);
            for (int i=0; i<streams.length; i++) audio.setStreamVolume(streams[i],levels[i],0);
            for (int i=0; i<streams.length; i++) {
                int restored=audio.getStreamVolume(streams[i]);
                android.util.Log.i("VolumeRestore", "stream=" + streams[i] + " before=" + levels[i] + " after=" + restored);
                check(restored == levels[i], "could not restore emulator audio stream " + streams[i]);
            }
        }
        finish(Activity.RESULT_OK,result);
    }
    private boolean uiArmed() {
        boolean[] result={false}; runOnMainSync(() -> result[0]=AssistService.isAssistArmed()); return result[0];
    }
    private void awaitArmed(boolean expected, String message) {
        for (int i=0; i<30 && uiArmed()!=expected; i++) SystemClock.sleep(50);
        check(uiArmed()==expected, message);
    }
    private java.io.BufferedReader startKeyFixture(String mode) throws Exception {
        check("ranchu".equals(android.os.Build.HARDWARE) && android.os.Build.PRODUCT.startsWith("sdk_gphone"),
                "uinput key tests are restricted to an emulator");
        android.os.ParcelFileDescriptor fd=getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                .executeShellCommand("su 0 /data/local/tmp/slide-volume-uinput " + mode);
        java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)));
        String ready=reader.readLine();
        check("READY".equals(ready), "could not start emulator key fixture: " + ready);
        return reader;
    }
    private void finishKeyFixture(java.io.BufferedReader reader) throws Exception {
        try { check("DONE".equals(reader.readLine()), "key fixture did not complete"); } finally { reader.close(); }
    }
    private void runKeyFixture(String mode) throws Exception { finishKeyFixture(startKeyFixture(mode)); }

    private Object serviceField(String name) throws Exception {
        java.lang.reflect.Field instance = AssistService.class.getDeclaredField("instance");
        instance.setAccessible(true); Object service = instance.get(null);
        if (service == null) return null;
        java.lang.reflect.Field field = AssistService.class.getDeclaredField(name);
        field.setAccessible(true); return field.get(service);
    }
    private boolean armed() throws Exception { return Boolean.TRUE.equals(serviceField("armed")); }
    private View awaitArea() throws Exception {
        for (int i=0; i<100; i++) {
            View v=(View) serviceField("touchArea");
            if (v != null && v.getWidth()>0 && v.getHeight()>0) return v;
            SystemClock.sleep(50);
        }
        throw new AssertionError("service overlay did not appear");
    }
    private void testService(Bundle result) throws Exception {
        for (int i=0; i<600 && !AssistService.isConnected(); i++) SystemClock.sleep(50);
        check(AssistService.isConnected(), "accessibility service is not connected");
        if (emulatorHardware) check("ranchu".equals(android.os.Build.HARDWARE) && android.os.Build.PRODUCT.startsWith("sdk_gphone"), "kernel touch tests are restricted to an emulator");
        check("com.example.slideassist.fixture".equals(BuildConfig.TARGET_PACKAGE), "service test must run with isolated testFixture build");
        Intent fixture = getTargetContext().getPackageManager().getLaunchIntentForPackage(BuildConfig.TARGET_PACKAGE);
        check(fixture != null, "install the separate fixture app first");
        fixture.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getTargetContext().startActivity(fixture);
        SystemClock.sleep(1200);
        serviceCase("release", true, 0);
        serviceCase("release", true, 100);
        serviceCase("release", true, 150);
        serviceCase("release", true, 200);
        serviceCase("release", true, 450);
        serviceCase("release", true, 700);
        serviceCase("immediate", true);
        serviceCase("immediate", false);
        serviceCase("gestures", false);
        runOnMainSync(AssistService::stopAssist);
        result.putString("result", "PASS service: immediate quick up, immediate held, release held then up at 100/150/200/250/450/700ms, double tap; callback completed and fixture received DOWN/MOVE/UP");
        finish(Activity.RESULT_OK, result);
    }
    private void serviceCase(String mode, boolean hold) throws Exception { serviceCase(mode, hold, 0); }
    private void serviceCase(String mode, boolean hold, int selectedDuration) throws Exception {
        android.content.SharedPreferences.Editor prefs=getTargetContext().getSharedPreferences("assist_settings", 0).edit().putString("trigger_mode", mode);
        if (selectedDuration == 0) prefs.remove("swipe_duration_ms"); else prefs.putInt("swipe_duration_ms", selectedDuration);
        prefs.commit();
        runOnMainSync(() -> {
            if (!AssistService.startAssist())
                throw new AssertionError("cannot arm service");
        });
        View area=awaitArea();
        android.app.UiAutomation automation = getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        java.lang.reflect.Field instance = AssistService.class.getDeclaredField("instance"); instance.setAccessible(true);
        AssistService service = (AssistService) instance.get(null);
        for (android.view.accessibility.AccessibilityWindowInfo window : service.getWindows()) {
            android.view.accessibility.AccessibilityNodeInfo windowRoot = window.getRoot();
            android.util.Log.i("SlideTestWindows", "type=" + window.getType() + " id=" + window.getId()
                    + " package=" + (windowRoot == null ? "null" : windowRoot.getPackageName()));
            if (windowRoot != null) windowRoot.recycle(); window.recycle();
        }
        int before=fixtureResult()[0];
        float[] c=new float[2];
        runOnMainSync(() -> { int[] p=new int[2]; area.getLocationOnScreen(p); c[0]=p[0]+area.getWidth()/2f; c[1]=p[1]+area.getHeight()/2f; });
        long t=SystemClock.uptimeMillis();
        if (emulatorHardware) sendHardware(c, true); else sendGlobal(t,t,MotionEvent.ACTION_DOWN,c, false);
        if (hold) {
            SystemClock.sleep(650);
            if (mode.equals("release")) check(armed(), "release mode triggered before up");
            else check(!armed(), "immediate mode did not finish while pointer held");
        } else SystemClock.sleep(50);
        if (emulatorHardware) sendHardware(c, false); else sendGlobal(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,c, mode.equals("immediate"));
        if ("gestures".equals(mode)) {
            SystemClock.sleep(35);
            long second = SystemClock.uptimeMillis();
            if (emulatorHardware) { sendHardware(c, true); sendHardware(c, false); }
            else { sendGlobal(second,second,MotionEvent.ACTION_DOWN,c,false); sendGlobal(second,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,c,false); }
        }
        for (int i=0; i<100 && armed(); i++) SystemClock.sleep(25);
        check("completed".equals(AssistService.getLastGestureStatus()), mode + " callback was " + AssistService.getLastGestureStatus());
        check(!armed(), mode+" service still armed after expected swipe");
        int[] result = fixtureResult();
        for (int i=0; i<40 && result[0] == before; i++) { SystemClock.sleep(25); result = fixtureResult(); }
        SystemClock.sleep(100); result = fixtureResult();
        check(result[0] == before + 1 && result[1] > 0 && result[2] > 100,
                "fixture did not receive exactly one complete new rightward swipe; before=" + before + " after=" + java.util.Arrays.toString(result));
        int duration=fixtureDurationFor(result[0]); int expected=selectedDuration == 0 ? 250 : selectedDuration;
        check(Math.abs(duration-expected) <= 10, "fixture duration " + duration + "ms; expected " + expected + "ms");
    }

    private int fixtureDurationFor(int count) throws Exception {
        android.os.ParcelFileDescriptor fd=getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                .executeShellCommand("logcat -d -s SlideFixture:I");
        java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();
        try (java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) {
            byte[] buf=new byte[1024]; int n; while ((n=in.read(buf))!=-1) output.write(buf,0,n);
        }
        java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("UP count="+count+" moves=\\d+ dx=-?\\d+ duration=(\\d+)")
                .matcher(output.toString("UTF-8"));
        int duration=-1; while (matcher.find()) duration=Integer.parseInt(matcher.group(1));
        check(duration >= 0,"missing isolated fixture duration for count " + count);
        return duration;
    }

    private int[] fixtureResult() {
        android.view.accessibility.AccessibilityNodeInfo root = getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).getRootInActiveWindow();
        check(root != null, "fixture accessibility root missing");
        try {
            java.util.List<android.view.accessibility.AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("完成滑动总数");
            check(nodes != null && !nodes.isEmpty(), "fixture status not found");
            String value = nodes.get(0).getText().toString();
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("完成滑动总数 (\\d+)，最近移动 (\\d+) 次，水平位移 (-?\\d+) 像素").matcher(value);
            check(matcher.find(), "could not parse fixture result: " + value);
            return new int[]{ Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)) };
        } finally { root.recycle(); }
    }

    private void sendHardware(float[] point, boolean down) throws Exception {
        android.util.DisplayMetrics metrics = getTargetContext().getResources().getDisplayMetrics();
        int rawX = Math.max(0, Math.min(32767, Math.round(point[0] * 32767f / metrics.widthPixels)));
        int rawY = Math.max(0, Math.min(32767, Math.round(point[1] * 32767f / metrics.heightPixels)));
        android.util.Log.i("SlideHardware", "point=" + point[0] + "," + point[1] + " display=" + metrics.widthPixels + "x" + metrics.heightPixels + " raw=" + rawX + "," + rawY + " down=" + down);
        String device = "/dev/input/event1";
        if (down) {
            runHardware("3 47 0"); runHardware("3 57 123");
            runHardware("3 53 " + rawX); runHardware("3 54 " + rawY); runHardware("3 58 512");
        } else runHardware("3 57 -1");
        runHardware("0 0 0");
    }
    private void runHardware(String values) throws Exception {
        android.os.ParcelFileDescriptor fd = getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                .executeShellCommand("su 0 sendevent /dev/input/event1 " + values);
        try (java.io.InputStream in = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] bytes = new byte[256]; int n; while ((n = in.read(bytes)) != -1) output.write(bytes, 0, n);
            check(output.size() == 0, "hardware command failed: " + output.toString("UTF-8"));
        }
    }

    private void sendGlobal(long down, long when, int action, float[] point, boolean allowCancelledStream) {
        MotionEvent event=MotionEvent.obtain(down,when,action,point[0],point[1],0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            boolean sent = getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                    .injectInputEvent(event, true);
            check(sent || allowCancelledStream, "global event injection failed");
        } finally { event.recycle(); }
    }

    private void findViews(View v) {
        if (v instanceof ScrollView) scroll = (ScrollView) v;
        if (v instanceof RadioButton && v.getTag() instanceof Integer) durationOptions.put((Integer)v.getTag(), (RadioButton)v);
        if (v instanceof StatusBubbleView) demoBubble=v;
        if (v instanceof android.widget.Button && ((android.widget.Button) v).getText().toString().contains("展开设置")) expandSettings=(android.widget.Button)v;
        if (v instanceof RadioButton && ((RadioButton) v).getText().toString().contains("按下立即")) immediateMode=(RadioButton)v;
        if (v instanceof RadioButton && ((RadioButton) v).getText().toString().contains("700 毫秒")) slowDuration=(RadioButton)v;
        if (v instanceof RadioButton && ((RadioButton) v).getText().toString().contains("抬起手指")) releaseMode = (RadioButton) v;
        if (v instanceof RadioButton && ((RadioButton) v).getText().toString().contains("单指双击")) gestureMode = (RadioButton) v;
        if (v instanceof DemoSliderView) sliders.add((DemoSliderView) v);
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) findViews(group.getChildAt(i));
        }
    }
    private void select(DemoSliderView v) {
        runOnMainSync(() -> {
            Rect r = new Rect(); v.getDrawingRect(r);
            scroll.offsetDescendantRectToMyCoords(v, r);
            scroll.scrollTo(0, Math.max(0, r.top - scroll.getHeight() / 2));
        });
        waitForIdleSync(); SystemClock.sleep(150);
    }
    private void reset(DemoSliderView v) {
        runOnMainSync(() -> {
            v.reset();
            if (demoBubble != null && String.valueOf(demoBubble.getContentDescription()).contains("辅助已关闭")) demoBubble.performClick();
        });
        waitForIdleSync();
    }
    private boolean done(DemoSliderView v) {
        final boolean[] result = {false};
        runOnMainSync(() -> result[0] = v.getContentDescription().toString().contains("模拟抢单成功"));
        return result[0];
    }
    private void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private void pause() { SystemClock.sleep(500); waitForIdleSync(); }
    private float[] center(DemoSliderView v) {
        float[] c = new float[2];
        runOnMainSync(() -> { int[] p = new int[2]; v.getLocationOnScreen(p); c[0] = p[0] + v.getWidth()/2f; c[1] = p[1] + v.getHeight()/2f; });
        return c;
    }
    private void send(long down, long when, int action, float[][] points) {
        int[] ids = new int[points.length]; for (int i = 0; i < ids.length; i++) ids[i] = i;
        sendWithIds(down, when, action, points, ids);
    }
    private void sendWithIds(long down, long when, int action, float[][] points, int[] ids) {
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[points.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[points.length];
        for (int i = 0; i < points.length; i++) {
            props[i] = new MotionEvent.PointerProperties(); props[i].id = ids[i]; props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords(); coords[i].x = points[i][0]; coords[i].y = points[i][1]; coords[i].pressure = 1; coords[i].size = 1;
        }
        MotionEvent event = MotionEvent.obtain(down, when, action, points.length, props, coords, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try { sendPointerSync(event); } finally { event.recycle(); }
    }
    private void tap(DemoSliderView v) {
        float[] c = center(v); long t = SystemClock.uptimeMillis();
        send(t,t,MotionEvent.ACTION_DOWN,new float[][]{c}); SystemClock.sleep(50);
        send(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,new float[][]{c});
    }
    private void outsideTap(DemoSliderView v) {
        float[] c = center(v); float[] p = { c[0] - v.getWidth() / 2f - 10, c[1] }; long t = SystemClock.uptimeMillis();
        send(t,t,MotionEvent.ACTION_DOWN,new float[][]{p}); send(t,t+50,MotionEvent.ACTION_UP,new float[][]{p});
    }
    private void downHeld(DemoSliderView v) {
        float[] c = center(v); long t = SystemClock.uptimeMillis();
        send(t,t,MotionEvent.ACTION_DOWN,new float[][]{c});
        pause();
        check(done(v), "default mode should finish while pointer is still down");
        send(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,new float[][]{c});
    }
    private void outsideDragIn(DemoSliderView v) {
        float[] c = center(v), p = { c[0] - v.getWidth() / 2f - 10, c[1] }; long t = SystemClock.uptimeMillis();
        send(t,t,MotionEvent.ACTION_DOWN,new float[][]{p});
        send(t,t+80,MotionEvent.ACTION_MOVE,new float[][]{c});
        send(t,t+140,MotionEvent.ACTION_UP,new float[][]{c});
    }
    private void releaseHeld(DemoSliderView v) {
        float[] c = center(v); long t = SystemClock.uptimeMillis();
        send(t,t,MotionEvent.ACTION_DOWN,new float[][]{c});
        SystemClock.sleep(400); waitForIdleSync(); check(!done(v), "release mode must not complete while held");
        send(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,new float[][]{c});
    }
    private void releaseOutside(DemoSliderView v) {
        float[] c = center(v), p = { c[0] - v.getWidth() / 2f - 10, c[1] }; long t = SystemClock.uptimeMillis();
        send(t,t,MotionEvent.ACTION_DOWN,new float[][]{c});
        send(t,t+50,MotionEvent.ACTION_MOVE,new float[][]{p});
        send(t,t+90,MotionEvent.ACTION_UP,new float[][]{p});
    }
    private void releaseFirstFinger(DemoSliderView v) {
        float[] c=center(v), a={c[0]-20,c[1]}, b={c[0]+20,c[1]}; long t=SystemClock.uptimeMillis();
        send(t,t,MotionEvent.ACTION_DOWN,new float[][]{a});
        send(t,t+20,MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[][]{a,b});
        send(t,t+50,MotionEvent.ACTION_POINTER_UP,new float[][]{a,b});
        SystemClock.sleep(400); waitForIdleSync(); check(done(v), "first pointer release must trigger while second stays down");
        sendWithIds(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,new float[][]{b},new int[]{1});
    }
    private void doubleTap(DemoSliderView v) { tap(v); SystemClock.sleep(80); tap(v); }
    private void twoFingerTap(DemoSliderView v) {
        float[] c = center(v), a = {c[0]-20,c[1]}, b = {c[0]+20,c[1]};
        long t = SystemClock.uptimeMillis();
        send(t,t,MotionEvent.ACTION_DOWN,new float[][]{a});
        send(t,t+20,MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[][]{a,b});
        send(t,t+50,MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[][]{a,b});
        send(t,t+70,MotionEvent.ACTION_UP,new float[][]{a});
    }
    private void move(DemoSliderView v) {
        float[] a = center(v), b = {a[0]+80,a[1]}; long t = SystemClock.uptimeMillis();
        send(t,t,MotionEvent.ACTION_DOWN,new float[][]{a});
        send(t,t+50,MotionEvent.ACTION_MOVE,new float[][]{b});
        send(t,t+100,MotionEvent.ACTION_UP,new float[][]{b});
    }
    private void threeFingerTap(DemoSliderView v) {
        float[] c = center(v), a={c[0]-30,c[1]}, b={c[0],c[1]}, d={c[0]+30,c[1]}; long t=SystemClock.uptimeMillis();
        send(t,t,MotionEvent.ACTION_DOWN,new float[][]{a});
        send(t,t+10,MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[][]{a,b});
        send(t,t+20,MotionEvent.ACTION_POINTER_DOWN | (2 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[][]{a,b,d});
        send(t,t+30,MotionEvent.ACTION_POINTER_UP | (2 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[][]{a,b,d});
        send(t,t+40,MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[][]{a,b});
        send(t,t+50,MotionEvent.ACTION_UP,new float[][]{a});
    }
}
