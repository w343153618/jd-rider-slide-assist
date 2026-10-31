package com.example.slideassist;

import java.io.IOException;
import java.net.URL;

/** Host-side tests for untrusted release metadata and URL policy. */
public final class UpdatePolicyTest {
    interface Attempt { void run() throws Exception; }
    private static void rejects(Attempt attempt, String label) throws Exception {
        try { attempt.run(); }
        catch (IOException expected) { return; }
        throw new AssertionError("must reject " + label);
    }
    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        require(UpdateManager.METADATA_URL.equals("https://github.com/w343153618/jd-rider-slide-assist/releases/latest/download/update.json"), "fixed metadata URL");
        String[] allowed = {"https://github.com/x/y",
                "https://release-assets.githubusercontent.com/file?token=example",
                "https://objects.githubusercontent.com/file", "https://github-releases.githubusercontent.com/file"};
        for (String url : allowed) UpdateManager.requireHost(new URL(url));
        String[] denied = {"https://api.github.com/repos/x/y", "http://github.com/x", "https://github.com.evil.invalid/x",
                "https://evil-github.com/x", "https://github.com@evil.invalid/x",
                "https://evil.invalid@github.com/x", "https://github.com:444/x",
                "https://github.com/x#fragment", "https://127.0.0.1/x"};
        for (String url : denied) rejects(() -> UpdateManager.requireHost(new URL(url)), url);
        String base = "https://github.com/w343153618/jd-rider-slide-assist/releases/download/v1.2.3/";
        String apk = "jd-rider-slide-assist.apk";
        UpdateManager.requireAssetUrl(base + apk, "v1.2.3", apk);
        rejects(() -> UpdateManager.requireAssetUrl(base + apk + "?x=1", "v1.2.3", apk), "asset query");
        rejects(() -> UpdateManager.requireAssetUrl(base.replace("w343153618", "other") + apk, "v1.2.3", apk), "other repo");
        rejects(() -> UpdateManager.requireAssetUrl(base + apk, "v1.2.4", apk), "different tag");
        long[] a = UpdateManager.parseVersion("v1.2.3");
        require(UpdateManager.compare(a, UpdateManager.parseVersion("1.2.2")) > 0, "version ordering");
        require(UpdateManager.compare(a, UpdateManager.parseVersion("1.2.3")) == 0, "version equality");
        require(UpdateManager.compare(a, UpdateManager.parseVersion("1.10.0")) < 0, "numeric ordering");
        for (String bad : new String[]{"", "v1.2", "1.2.3-beta", "1.2.3/evil", "v9999999999.1.1"}) {
            rejects(() -> UpdateManager.parseVersion(bad), "version " + bad);
        }
        System.out.println("UpdatePolicyTest passed");
    }
}
