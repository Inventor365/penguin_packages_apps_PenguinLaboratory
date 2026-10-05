/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.penguin.laboratory.wonderland;

import com.penguin.laboratory.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** Lists the Wonderland wallpapers made so far. */
public class WonderlandActivity extends Activity implements WonderlandStore.Listener {

    private static final String TAG = "Wonderland";
    private static final int COLUMNS = 2;

    private LinearLayout mGrid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wonderland);
        mGrid = findViewById(R.id.projects);
        findViewById(R.id.create).setOnClickListener(v -> {
            try {
                open(WonderlandStore.create(this));
            } catch (IOException e) {
                Log.w(TAG, "Couldn't create a project", e);
            }
        });
        WonderlandStore.addListener(this);
    }

    @Override
    protected void onDestroy() {
        WonderlandStore.removeListener(this);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onChanged() {
        if (!isDestroyed()) refresh();
    }

    private void open(String project) {
        startActivity(new Intent(this, WonderlandEditorActivity.class)
                .putExtra(WonderlandEditorActivity.EXTRA_PROJECT, project));
    }

    private void refresh() {
        mGrid.removeAllViews();
        List<String> projects = WonderlandStore.projects(this);
        findViewById(R.id.empty).setVisibility(projects.isEmpty() ? View.VISIBLE : View.GONE);
        String active = inUse() ? WonderlandStore.active(this) : null;
        LinearLayout row = null;
        for (int i = 0; i < projects.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                mGrid.addView(row);
            }
            row.addView(card(projects.get(i), projects.get(i).equals(active)));
        }
        // Keep the last card half width
        if (row != null && projects.size() % COLUMNS != 0) {
            row.addView(new View(this), cardParams());
        }
    }

    /** Whether the home or lock screen shows Wonderland. */
    private boolean inUse() {
        ComponentName component = new ComponentName(this, WonderlandWallpaperService.class);
        WallpaperManager wm = WallpaperManager.getInstance(this);
        for (int which : new int[] {WallpaperManager.FLAG_SYSTEM, WallpaperManager.FLAG_LOCK}) {
            WallpaperInfo info = wm.getWallpaperInfo(which);
            if (info != null && component.equals(info.getComponent())) return true;
        }
        return false;
    }

    private View card(String project, boolean applied) {
        FrameLayout card = new FrameLayout(this);
        card.setBackgroundResource(R.drawable.preview_background);
        card.setClipToOutline(true);
        card.setForeground(getDrawable(android.R.drawable.list_selector_background));
        ImageView thumbnail = new ImageView(this);
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        File file = WonderlandStore.file(this, project, WonderlandStore.THUMBNAIL);
        if (file.exists()) thumbnail.setImageBitmap(BitmapFactory.decodeFile(file.getPath()));
        card.addView(thumbnail, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (applied) {
            TextView badge = new TextView(this);
            badge.setText(R.string.wonderland_in_use);
            badge.setBackgroundResource(R.drawable.badge_background);
            badge.setTextColor(0xFFFFFFFF);
            badge.setPadding(dp(12), dp(4), dp(12), dp(4));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.START);
            lp.setMargins(dp(10), dp(10), 0, 0);
            card.addView(badge, lp);
        }
        card.setOnClickListener(v -> open(project));
        card.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.wonderland_delete_project)
                    .setPositiveButton(R.string.wonderland_delete, (d, w) ->
                            WonderlandStore.delete(this, project))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        });
        card.setLayoutParams(cardParams());
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        int width = (getResources().getDisplayMetrics().widthPixels - dp(40 + 12)) / COLUMNS;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                Math.round(width * 2.1f), 1f);
        lp.setMargins(dp(6), dp(6), dp(6), dp(6));
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
