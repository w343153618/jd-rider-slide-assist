package com.example.slideassist;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Exposes immutable, digest-named APKs only, using a temporary read URI grant. */
public final class UpdateApkProvider extends ContentProvider {
    static final String MIME = "application/vnd.android.package-archive";
    static File updateFile(android.content.Context c, String digest) {
        if (digest == null || !digest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid digest");
        return new File(new File(c.getFilesDir(), "updates"), digest + ".apk");
    }
    static Uri updateUri(android.content.Context c, String digest) {
        return new Uri.Builder().scheme("content").authority(c.getPackageName() + ".updates").appendPath(digest + ".apk").build();
    }
    @Override public boolean onCreate() { return true; }
    private File allowedFile(Uri uri) {
        if (getContext() == null || !"content".equals(uri.getScheme()) ||
                !(getContext().getPackageName() + ".updates").equals(uri.getAuthority()) ||
                uri.getQuery() != null || uri.getFragment() != null || uri.getPathSegments().size() != 1) return null;
        String name = uri.getPathSegments().get(0);
        if (!name.matches("[0-9a-f]{64}\\.apk")) return null;
        return updateFile(getContext(), name.substring(0, 64));
    }
    @Override public String getType(Uri uri) { return allowedFile(uri) != null ? MIME : null; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = allowedFile(uri);
        if (file == null || !"r".equals(mode) || !file.isFile()) throw new FileNotFoundException("Not available");
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File file = allowedFile(uri);
        if (file == null || !file.isFile()) return null;
        String[] columns = projection == null ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = "update.apk";
            else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = file.length();
        }
        cursor.addRow(row);
        return cursor;
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
