package com.dailyapps.poolshot;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Minimal provider so the camera app can write a full-size photo into our cache. */
public class PhotoProvider extends ContentProvider {
    static final String AUTHORITY = "com.dailyapps.poolshot.photos";

    static Uri captureUri() {
        return Uri.parse("content://" + AUTHORITY + "/capture.jpg");
    }

    static File captureFile(android.content.Context c) {
        return new File(c.getCacheDir(), "capture.jpg");
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = captureFile(getContext());
        int m = ParcelFileDescriptor.parseMode(mode);
        return ParcelFileDescriptor.open(f, m | ParcelFileDescriptor.MODE_CREATE);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File f = captureFile(getContext());
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{"capture.jpg", f.length()});
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return "image/jpeg";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
