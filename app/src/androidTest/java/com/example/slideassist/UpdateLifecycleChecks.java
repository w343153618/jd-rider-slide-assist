package com.example.slideassist;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.List;

/** Offline regression for a verified update surviving Activity recreation. */
public final class UpdateLifecycleChecks {
    private final Instrumentation instrumentation;
    private final Context context;

    private UpdateLifecycleChecks(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;
        context = instrumentation.getTargetContext();
    }

    public static void run(Instrumentation instrumentation, Bundle result) throws Exception {
        new UpdateLifecycleChecks(instrumentation).run(result);
    }

    private void run(Bundle result) throws Exception {
        check("com.example.slideassist.testfixture".equals(context.getPackageName()), "fixture build required");
        check("ranchu".equals(android.os.Build.HARDWARE), "isolated emulator required");
        boolean installationAllowed = context.getPackageManager().canRequestPackageInstalls();
        SharedPreferences pending = context.getSharedPreferences("pending_update", Context.MODE_PRIVATE);
        check(pending.getAll().isEmpty(), "pending update must be empty before this test");
        File source = File.createTempFile("lifecycle-source-", ".apk", context.getCacheDir());
        File target = null;
        boolean ownsTarget = false;
        Activity activity = null;
        try {
            try (ParcelFileDescriptor descriptor = automation().executeShellCommand("cat /data/local/tmp/slide-valid-update.apk");
                 InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
                copy(input, source);
            }
            check(source.length() > 0, "prepare a higher-version, same-signer fixture APK first");
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new FileInputStream(source)) {
                byte[] bytes = new byte[8192]; int count;
                while ((count = input.read(bytes)) != -1) sha.update(bytes, 0, count);
            }
            String digest = UpdateManager.hex(sha.digest());
            target = UpdateApkProvider.updateFile(context, digest);
            check(target.getParentFile().isDirectory() || target.getParentFile().mkdirs(), "cannot create update directory");
            check(target.createNewFile(), "test digest already exists; refusing to overwrite");
            ownsTarget = true;
            try (InputStream input = new FileInputStream(source)) { copy(input, target); }
            check(pending.edit().putBoolean("waiting", true).putString("digest", digest).putString("tag", "v9.9.9").commit(), "cannot seed legacy pending state");
            activity = startActivity();
            waitForDialog("更新已准备好");
            check(digest.equals(pending.getString("digest", "")), "verified ready update must remain persisted after permission return");
            if (!installationAllowed) {
                clickDialog("安装更新");
                waitForDialog("允许安装更新");
                check(digest.equals(pending.getString("digest", "")), "requesting permission must preserve the ready update");
                clickDialog("取消");
                check(pending.getAll().isEmpty(), "explicit permission cancellation must clear pending state");
                result.putString("result", "PASS: valid higher-version same-signer APK is reverified while installation permission is denied; install tap opens only the app permission prompt; explicit cancellation clears pending state; no installer opened");
                return;
            }

            Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(MainActivity.class.getName(), null, false);
            try {
                Activity original = activity;
                instrumentation.runOnMainSync(original::recreate);
                activity = monitor.waitForActivityWithTimeout(5000);
                check(activity != null && activity != original, "Activity recreation did not complete");
            } finally { instrumentation.removeMonitor(monitor); }
            instrumentation.waitForIdleSync();
            waitForDialog("更新已准备好");
            check(digest.equals(pending.getString("digest", "")), "ready update must survive recreation without opening an installer");
            clickDialog("稍后");
            check(pending.getAll().isEmpty(), "explicit later action must clear pending update state");
            Activity ready = activity;
            instrumentation.runOnMainSync(ready::finish);
            instrumentation.waitForIdleSync();
            activity = null;

            // A persisted ready record must be checked again, even without waiting for permission.
            try (FileOutputStream output = new FileOutputStream(target)) { output.write(new byte[]{1, 2, 3}); }
            check(pending.edit().putBoolean("waiting", false).putString("digest", digest).putString("tag", "v9.9.9").commit(), "cannot seed ready state");
            activity = startActivity();
            waitForDialog("安装包校验失败");
            check(pending.getAll().isEmpty(), "failed verification must clear pending update state");
            clickDialog("知道了");
            result.putString("result", "PASS: valid same-signer higher-version APK stays pending after permission return and Activity recreation; explicit later clears it; restored ready record rechecks SHA and rejects changed bytes; no installer opened");
        } finally {
            if (activity != null) {
                Activity current = activity;
                instrumentation.runOnMainSync(current::finish);
                instrumentation.waitForIdleSync();
            }
            pending.edit().clear().commit();
            source.delete();
            if (ownsTarget) target.delete();
        }
    }

    private Activity startActivity() {
        Activity activity = instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        instrumentation.waitForIdleSync();
        return activity;
    }

    private UiAutomation automation() {
        return instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    }

    private void waitForDialog(String text) {
        for (int attempt = 0; attempt < 100; attempt++) {
            AccessibilityNodeInfo root = automation().getRootInActiveWindow();
            if (root != null) {
                try {
                    if (context.getPackageName().contentEquals(root.getPackageName()) && !root.findAccessibilityNodeInfosByText(text).isEmpty()) return;
                } finally { root.recycle(); }
            }
            SystemClock.sleep(50);
        }
        throw new AssertionError("expected own-app dialog not found: " + text);
    }

    private void clickDialog(String text) {
        AccessibilityNodeInfo root = automation().getRootInActiveWindow();
        check(root != null && context.getPackageName().contentEquals(root.getPackageName()), "installer must not open automatically");
        try {
            List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(text);
            check(!matches.isEmpty() && matches.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK), "cannot click dialog action: " + text);
            for (AccessibilityNodeInfo match : matches) match.recycle();
        } finally { root.recycle(); }
        instrumentation.waitForIdleSync();
    }

    private static void copy(InputStream input, File output) throws Exception {
        try (FileOutputStream stream = new FileOutputStream(output)) {
            byte[] bytes = new byte[8192]; int count;
            while ((count = input.read(bytes)) != -1) stream.write(bytes, 0, count);
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
