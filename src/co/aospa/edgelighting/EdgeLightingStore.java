/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.edgelighting;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.io.File;

/** Where the toggle and the cutout live, shared by the settings screen and the provider. */
final class EdgeLightingStore {

    static final String AUTHORITY = "co.aospa.edgelighting.provider";
    static final Uri STATE_URI = Uri.parse("content://" + AUTHORITY + "/state");

    private static final String PREFS = "edge_lighting";
    private static final String KEY_ENABLED = "enabled";
    private static final String CUTOUT_FILE = "cutout.png";

    private EdgeLightingStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        notifyChanged(context);
    }

    static File cutoutFile(Context context) {
        return new File(context.createDeviceProtectedStorageContext().getFilesDir(), CUTOUT_FILE);
    }

    static void notifyChanged(Context context) {
        context.getContentResolver().notifyChange(STATE_URI, null);
    }
}
