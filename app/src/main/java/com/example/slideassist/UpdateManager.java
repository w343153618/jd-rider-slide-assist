package com.example.slideassist;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.widget.ProgressBar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Explicit, user-initiated GitHub release update. Android still confirms installation. */
public final class UpdateManager {
    private static final String REPO = "w343153618/jd-rider-slide-assist";
    static final String METADATA_URL = "https://github.com/" + REPO + "/releases/latest/download/update.json";
    private static final String APK = "jd-rider-slide-assist.apk";
    private static final String SHA = APK + ".sha256";
    private static final int JSON_LIMIT = 512 * 1024, SHA_LIMIT = 4096, APK_LIMIT = 20 * 1024 * 1024;
    private static final Pattern VERSION = Pattern.compile("v?([0-9]{1,9})\\.([0-9]{1,9})\\.([0-9]{1,9})");
    private static final Pattern SHA_PATTERN = Pattern.compile("\\A\\s*([a-fA-F0-9]{64})(?:\\s+\\*?" + Pattern.quote(APK) + ")?\\s*\\z");
    private final WeakReference<Activity> activity;
    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed;
    private volatile int generation;
    private boolean busy, waitingForPermission, verifiedReady;
    private String verifiedDigest, verifiedTag;
    private AlertDialog progress;

    public UpdateManager(Activity activity) {
        this.activity = new WeakReference<>(activity);
        this.app = activity.getApplicationContext();
        android.content.SharedPreferences pending = app.getSharedPreferences("pending_update", Context.MODE_PRIVATE);
        waitingForPermission = pending.getBoolean("waiting", false);
        if (waitingForPermission) {
            verifiedDigest = pending.getString("digest", "");
            verifiedTag = pending.getString("tag", "");
            verifiedReady = verifiedDigest != null && verifiedTag != null && verifiedDigest.matches("[0-9a-f]{64}") && VERSION.matcher(verifiedTag).matches()
                    && UpdateApkProvider.updateFile(app, verifiedDigest).isFile();
        }
    }

    public void checkForUpdate() {
        if (destroyed) return;
        if (busy) { showMessage("正在处理更新", "请稍候，或先取消当前操作。"); return; }
        final int token = begin("正在检查更新");
        executor.execute(() -> {
            try {
                String raw = new String(readBytes(new URL(METADATA_URL), JSON_LIMIT, token), StandardCharsets.UTF_8);
                ReleaseMetadata release = parseMetadata(raw);
                String tag = release.tag;
                PackageInfo own = installedPackage(app);
                if (compare(parseVersion(tag), parseVersion(own.versionName)) <= 0) {
                    post(token, () -> { end(); showMessage("当前已是最新版本", "当前版本：" + own.versionName + "\n最新发布：" + tag); });
                    return;
                }
                final String finalApk = release.apkUrl, finalSha = release.shaUrl, finalNotes = release.notes;
                post(token, () -> {
                    end();
                    Activity a = liveActivity(); if (a == null) return;
                    new AlertDialog.Builder(a).setTitle("发现新版本 " + tag)
                            .setMessage("当前版本：" + own.versionName + "\n\n" + (finalNotes.isEmpty() ? "可下载更新。" : finalNotes)
                                    + "\n\n下载后将校验文件与签名，再由系统征求安装确认。")
                            .setNegativeButton("稍后", null)
                            .setPositiveButton("下载更新", (d, w) -> download(finalApk, finalSha, tag)).show();
                });
            } catch (Exception e) { fail(token, "检查更新失败", e); }
        });
    }

    static final class ReleaseMetadata {
        final String tag, apkUrl, shaUrl, notes;
        ReleaseMetadata(String tag, String apkUrl, String shaUrl, String notes) {
            this.tag = tag; this.apkUrl = apkUrl; this.shaUrl = shaUrl; this.notes = notes;
        }
    }

    /** Parse an untrusted fixed release asset without accepting arbitrary source URLs. */
    static ReleaseMetadata parseMetadata(String raw) throws Exception {
        JSONObject json = new JSONObject(raw);
        String tag = json.getString("tag_name");
        parseVersion(tag);
        Object draft = json.get("draft"), prerelease = json.get("prerelease");
        if (!(draft instanceof Boolean) || !(prerelease instanceof Boolean)) throw new IOException("发布状态格式无效");
        if ((Boolean) draft || (Boolean) prerelease) throw new IOException("该版本尚未正式发布");
        String apkUrl = null, shaUrl = null;
        JSONArray assets = json.getJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String name = asset.getString("name");
            if (APK.equals(name)) { if (apkUrl != null) throw new IOException("发布文件重复"); apkUrl = asset.getString("browser_download_url"); }
            if (SHA.equals(name)) { if (shaUrl != null) throw new IOException("校验文件重复"); shaUrl = asset.getString("browser_download_url"); }
        }
        if (apkUrl == null || shaUrl == null) throw new IOException("该版本缺少安装包或校验文件");
        requireAssetUrl(apkUrl, tag, APK);
        requireAssetUrl(shaUrl, tag, SHA);
        String notes = json.optString("body", "").trim();
        if (notes.length() > 1600) notes = notes.substring(0, 1600) + "…";
        return new ReleaseMetadata(tag, apkUrl, shaUrl, notes);
    }

    private void download(String apkUrl, String shaUrl, String tag) {
        if (destroyed || busy) return;
        verifiedReady = false;
        waitingForPermission = false;
        app.getSharedPreferences("pending_update", Context.MODE_PRIVATE).edit().clear().apply();
        final int token = begin("正在下载并校验更新");
        executor.execute(() -> {
            File tmp = null;
            try {
                String digestText = new String(readBytes(new URL(shaUrl), SHA_LIMIT, token), StandardCharsets.UTF_8);
                Matcher match = SHA_PATTERN.matcher(digestText);
                if (!match.matches()) throw new IOException("校验文件格式无效");
                String expected = match.group(1).toLowerCase(Locale.ROOT);
                File dir = new File(app.getFilesDir(), "updates");
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("无法创建更新目录");
                tmp = File.createTempFile("update-", ".apk", dir);
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                downloadFile(new URL(apkUrl), tmp, md, token);
                if (!hex(md.digest()).equals(expected)) throw new IOException("安装包校验值不一致");
                verifyArchive(app, tmp, tag);
                check(token);
                // Link without replacement: an installer grant must always refer to the same bytes.
                File immutable = UpdateApkProvider.updateFile(app, expected);
                try {
                    android.system.Os.link(tmp.getAbsolutePath(), immutable.getAbsolutePath());
                } catch (android.system.ErrnoException e) {
                    if (e.errno != android.system.OsConstants.EEXIST || !expected.equals(fileSha256(immutable)))
                        throw new IOException("无法安全保存安装包");
                    verifyArchive(app, immutable, tag);
                }
                if (!tmp.delete()) throw new IOException("无法清除临时文件");
                tmp = null;
                post(token, () -> { end(); verifiedDigest = expected; verifiedTag = tag; verifiedReady = true; showInstallReady(); });
            } catch (Exception e) { fail(token, "更新下载或校验失败", e); }
            finally { if (tmp != null) tmp.delete(); }
        });
    }

    private void showInstallReady() {
        Activity a = liveActivity();
        if (a == null || !verifiedReady) return;
        new AlertDialog.Builder(a).setTitle("更新已准备好")
                .setMessage("版本 " + verifiedTag + "：已核对文件摘要、应用包名、发布版本与签名证书；安装包版本高于当前版本。Android 将在安装时验证 APK 签名，并请您确认。")
                .setNegativeButton("稍后", null).setPositiveButton("安装更新", (d, w) -> requestInstall()).show();
    }

    private void requestInstall() {
        Activity a = liveActivity();
        if (a == null || !verifiedReady) return;
        if (Build.VERSION.SDK_INT >= 26 && !app.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(a).setTitle("允许安装更新")
                    .setMessage("请在系统设置中允许本应用安装更新，返回后再确认安装。")
                    .setNegativeButton("取消", null).setPositiveButton("打开设置", (d, w) -> {
                        try {
                            waitingForPermission = true;
                            if (!app.getSharedPreferences("pending_update", Context.MODE_PRIVATE).edit()
                                    .putBoolean("waiting", true).putString("digest", verifiedDigest).putString("tag", verifiedTag).commit()) {
                                waitingForPermission = false;
                                showMessage("无法保存安装状态", "请重新检查更新后再试。");
                                return;
                            }
                            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + app.getPackageName())));
                        } catch (RuntimeException ex) {
                            waitingForPermission = false;
                            app.getSharedPreferences("pending_update", Context.MODE_PRIVATE).edit().clear().apply();
                            showMessage("无法打开设置", "请在系统设置中允许本应用安装未知来源应用。");
                        }
                    }).show();
            return;
        }
        verifyReadyThen(() -> {
            Activity current = liveActivity();
            if (current == null) return;
            try {
                Uri uri = UpdateApkProvider.updateUri(app, verifiedDigest);
                Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, UpdateApkProvider.MIME)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setClipData(ClipData.newRawUri("update", uri));
                app.getSharedPreferences("pending_update", Context.MODE_PRIVATE).edit().clear().apply();
                current.startActivity(intent);
                verifiedReady = false;
            } catch (RuntimeException ex) { showMessage("无法打开安装界面", "请重新检查更新并重试。"); }
        });
    }

    /** Call on Activity resume; returning from settings never installs without another tap. */
    public void resumePendingInstall() {
        if (!waitingForPermission || destroyed) return;
        waitingForPermission = false;
        app.getSharedPreferences("pending_update", Context.MODE_PRIVATE).edit().clear().apply();
        if (!verifiedReady) { showMessage("安装包已失效", "请重新检查并下载更新。"); return; }
        if (Build.VERSION.SDK_INT < 26 || app.getPackageManager().canRequestPackageInstalls()) verifyReadyThen(this::showInstallReady);
        else showMessage("尚未允许安装", "需要系统安装授权后，才能继续安装更新。");
    }

    private void verifyReadyThen(Runnable next) {
        if (!verifiedReady || busy || destroyed) return;
        final String digest = verifiedDigest, tag = verifiedTag;
        final int token = begin("正在核验安装包");
        executor.execute(() -> {
            try {
                File file = UpdateApkProvider.updateFile(app, digest);
                if (!digest.equals(fileSha256(file))) throw new IOException("安装包校验值不一致");
                verifyArchive(app, file, tag);
                check(token);
                post(token, () -> { end(); next.run(); });
            } catch (Exception e) {
                post(token, () -> { verifiedReady = false; end(); showMessage("安装包校验失败", "请重新检查并下载更新。"); });
            }
        });
    }

    public void destroy() {
        destroyed = true; generation++; busy = false;
        if (progress != null) { progress.dismiss(); progress = null; }
        executor.shutdownNow();
        activity.clear();
    }

    private int begin(String title) {
        int token = ++generation; busy = true;
        Activity a = liveActivity();
        if (a != null) {
            progress = new AlertDialog.Builder(a).setTitle(title).setView(new ProgressBar(a))
                    .setNegativeButton("取消", (d, w) -> cancel(token))
                    .setOnCancelListener(d -> cancel(token)).show();
        }
        return token;
    }
    private void cancel(int token) { if (token == generation) { generation++; busy = false; progress = null; } }
    private void end() { busy = false; if (progress != null) { progress.dismiss(); progress = null; } }
    private Activity liveActivity() { Activity a = activity.get(); return destroyed || a == null || a.isFinishing() || a.isDestroyed() ? null : a; }
    private void post(int token, Runnable action) { main.post(() -> { if (!destroyed && token == generation) action.run(); }); }
    private void fail(int token, String title, Exception e) {
        String detail = e instanceof IOException ? e.getMessage() : "请检查网络或稍后重试";
        post(token, () -> { end(); showMessage(title, detail == null ? "请稍后重试" : detail); });
    }
    private void showMessage(String title, String message) {
        Activity a = liveActivity(); if (a != null) new AlertDialog.Builder(a).setTitle(title).setMessage(message).setPositiveButton("知道了", null).show();
    }
    private void check(int token) throws IOException {
        if (destroyed || token != generation || Thread.currentThread().isInterrupted()) throw new IOException("操作已取消");
    }

    private byte[] readBytes(URL url, int limit, int token) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transfer(url, limit, token, (bytes, count) -> out.write(bytes, 0, count));
        return out.toByteArray();
    }
    private void downloadFile(URL url, File output, MessageDigest digest, int token) throws IOException {
        try (FileOutputStream out = new FileOutputStream(output)) {
            transfer(url, APK_LIMIT, token, (bytes, count) -> { digest.update(bytes, 0, count); out.write(bytes, 0, count); });
            out.getFD().sync();
        }
    }
    private interface Sink { void write(byte[] bytes, int count) throws IOException; }
    private void transfer(URL initial, int limit, int token, Sink sink) throws IOException {
        URL url = initial;
        for (int redirects = 0; redirects <= 5; redirects++) {
            check(token); requireHost(url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(12000); conn.setReadTimeout(15000); conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "RiderSlideAssist-Update");
            conn.setRequestProperty("Accept", "application/octet-stream");
            try {
                int status = conn.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = conn.getHeaderField("Location");
                    if (location == null || redirects == 5) throw new IOException("更新下载跳转无效");
                    url = new URL(url, location); continue;
                }
                if (status != 200) throw new IOException(status == 404 ? "未找到更新文件，请稍后重试" : "服务器返回错误（" + status + "）");
                long length = conn.getContentLengthLong();
                if (length > limit) throw new IOException("更新文件超过大小限制");
                try (InputStream in = conn.getInputStream()) {
                    byte[] buf = new byte[8192]; int n, total = 0;
                    while ((n = in.read(buf)) != -1) {
                        check(token); total += n;
                        if (total > limit) throw new IOException("更新文件超过大小限制");
                        sink.write(buf, n);
                    }
                }
                return;
            } finally { conn.disconnect(); }
        }
        throw new IOException("更新下载跳转过多");
    }
    static void requireHost(URL u) throws IOException {
        String h = u.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(u.getProtocol()) || u.getUserInfo() != null || u.getRef() != null ||
                (u.getPort() != -1 && u.getPort() != 443) || !(h.equals("github.com") ||
                h.equals("objects.githubusercontent.com") || h.equals("release-assets.githubusercontent.com") || h.equals("github-releases.githubusercontent.com")))
            throw new IOException("不允许的更新地址");
    }
    static void requireAssetUrl(String url, String tag, String filename) throws IOException {
        if (!url.equals("https://github.com/" + REPO + "/releases/download/" + tag + "/" + filename))
            throw new IOException("发布文件地址不匹配");
    }
    static long[] parseVersion(String value) throws IOException {
        if (value == null) throw new IOException("版本号无效");
        Matcher m = VERSION.matcher(value);
        if (!m.matches()) throw new IOException("版本号格式无效");
        return new long[] {Long.parseLong(m.group(1)), Long.parseLong(m.group(2)), Long.parseLong(m.group(3))};
    }
    static int compare(long[] a, long[] b) {
        for (int i = 0; i < 3; i++) { int c = Long.compare(a[i], b[i]); if (c != 0) return c; }
        return 0;
    }
    static String hex(byte[] bytes) {
        StringBuilder b = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) b.append(String.format(Locale.ROOT, "%02x", value & 255));
        return b.toString();
    }
    private static PackageInfo installedPackage(Context c) throws PackageManager.NameNotFoundException {
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        return c.getPackageManager().getPackageInfo(c.getPackageName(), flags);
    }
    private static String fileSha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        if (!file.isFile() || file.length() > APK_LIMIT) throw new IOException("保存的安装包无效");
        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        return hex(digest.digest());
    }
    private static void verifyArchive(Context c, File file, String expectedTag) throws Exception {
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo installed = installedPackage(c);
        PackageInfo archive = c.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), flags);
        if (archive == null || !c.getPackageName().equals(archive.packageName)) throw new IOException("安装包应用标识不匹配");
        if (compare(parseVersion(archive.versionName), parseVersion(expectedTag)) != 0)
            throw new IOException("安装包版本与发布版本不匹配");
        long current = Build.VERSION.SDK_INT >= 28 ? installed.getLongVersionCode() : installed.versionCode;
        long next = Build.VERSION.SDK_INT >= 28 ? archive.getLongVersionCode() : archive.versionCode;
        if (next <= current) throw new IOException("安装包版本不是更新版本");
        Set<String> installedSigners = signerSet(installed), archiveSigners = signerSet(archive);
        if (installedSigners.isEmpty() || !installedSigners.equals(archiveSigners)) throw new IOException("安装包签名与当前应用不匹配");
    }
    private static Set<String> signerSet(PackageInfo info) {
        Signature[] signatures = Build.VERSION.SDK_INT >= 28
                ? (info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners()) : info.signatures;
        Set<String> result = new HashSet<>();
        if (signatures != null) for (Signature sig : signatures) result.add(Base64.encodeToString(sig.toByteArray(), Base64.NO_WRAP));
        return result;
    }
}
