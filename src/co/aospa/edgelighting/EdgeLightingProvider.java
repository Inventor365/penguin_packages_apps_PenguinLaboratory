/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.edgelighting;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Exposes the edge lighting state to SystemUI.
 *
 * <p>{@code /state} has one row: {@code enabled} and {@code version}, the cutout's modification
 * time so callers can tell when to reload it. {@code /image} is the cutout PNG.
 */
public class EdgeLightingProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        if (!"/state".equals(uri.getPath())) return null;
        File cutout = EdgeLightingStore.cutoutFile(getContext());
        MatrixCursor cursor = new MatrixCursor(new String[] {"enabled", "version"});
        cursor.addRow(new Object[] {
                EdgeLightingStore.isEnabled(getContext()) && cutout.exists() ? 1 : 0,
                cutout.exists() ? cutout.lastModified() : 0});
        cursor.setNotificationUri(getContext().getContentResolver(), EdgeLightingStore.STATE_URI);
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"/image".equals(uri.getPath()) || !"r".equals(mode)) {
            throw new FileNotFoundException(uri.toString());
        }
        return ParcelFileDescriptor.open(EdgeLightingStore.cutoutFile(getContext()),
                ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "/image".equals(uri.getPath()) ? "image/png" : null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
