package com.example.slideassist;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

/** Identifies the one application configured at build time. */
public final class TargetApp {
    // Production: https://app.mi.com/details?id=com.dada.mobile.android
    // Compile-time test builds may use a separate, explicitly identified local fixture.
    public static final String PACKAGE_NAME = BuildConfig.TARGET_PACKAGE;

    private TargetApp() { }

    public static boolean isTarget(CharSequence packageName) {
        return packageName != null && PACKAGE_NAME.contentEquals(packageName);
    }

    public static final class Status {
        public final boolean installed;
        public final boolean launchable;
        public final String versionName;
        public final long versionCode;
        private Status(boolean installed, boolean launchable, String versionName, long versionCode) {
            this.installed = installed;
            this.launchable = launchable;
            this.versionName = versionName;
            this.versionCode = versionCode;
        }
    }

    /** Package presence is not verification of publisher signature or app compatibility. */
    public static Status getStatus(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0);
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return new Status(true, getLaunchIntent(context) != null,
                    info.versionName == null ? "" : info.versionName, code);
        } catch (PackageManager.NameNotFoundException | SecurityException e) {
            return new Status(false, false, "", 0);
        }
    }

    // Android documents this API as returning a package's front-door activity:
    // https://developer.android.com/reference/android/content/pm/PackageManager#getLaunchIntentForPackage(java.lang.String)
    public static Intent getLaunchIntent(Context context) {
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(PACKAGE_NAME);
            if (intent == null || intent.getComponent() == null ||
                    !PACKAGE_NAME.equals(intent.getComponent().getPackageName())) return null;
            return intent;
        } catch (SecurityException e) {
            return null;
        }
    }

    public static boolean launch(Context context) {
        Intent intent = getLaunchIntent(context);
        if (intent == null) return false;
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { context.startActivity(intent); return true; }
        catch (ActivityNotFoundException | SecurityException e) { return false; }
    }
}
