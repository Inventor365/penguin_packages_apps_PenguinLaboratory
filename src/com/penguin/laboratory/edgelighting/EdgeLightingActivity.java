/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.penguin.laboratory.edgelighting;

import com.penguin.laboratory.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.BroadcastOptions;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Edits the edge lighting style: image, colour, motion and when it plays. */
public class EdgeLightingActivity extends Activity {

    private static final String TAG = "EdgeLighting";
    private static final int REQUEST_PICK = 1;
    private static final String SYSTEMUI = "com.android.systemui";

    private static final int[] COLORS = {
            0xFFFF5C8A, 0xFFFF8A3D, 0xFFFFD43B, 0xFF69DB7C, 0xFF38D9A9, 0xFF4DABF7, 0xFF9775FA,
            0xFFFFFFFF};
    private static final String[] EFFECTS = {
            EdgeLightingStore.EFFECT_RAIN, EdgeLightingStore.EFFECT_SNOW,
            EdgeLightingStore.EFFECT_BUBBLES, EdgeLightingStore.EFFECT_BURST};
    private static final int[] EFFECT_LABELS = {
            R.string.edge_lighting_effect_rain, R.string.edge_lighting_effect_snow,
            R.string.edge_lighting_effect_bubbles, R.string.edge_lighting_effect_burst};

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private Switch mEnabled;
    private ImageView mPreview;
    private TextView mStatus;
    private ProgressBar mProgress;
    private Button mPlay;
    private LinearLayout mImages;
    private LinearLayout mColors;
    private LinearLayout mKeywords;
    private boolean mBusy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edge_lighting);
        mEnabled = findViewById(R.id.enabled);
        mPreview = findViewById(R.id.preview);
        mStatus = findViewById(R.id.status);
        mProgress = findViewById(R.id.progress);
        mPlay = findViewById(R.id.play);
        mImages = findViewById(R.id.images);
        mColors = findViewById(R.id.colors);
        mKeywords = findViewById(R.id.keywords);

        bindSwitch(mEnabled, EdgeLightingStore.KEY_ENABLED, false);
        bindSwitch(findViewById(R.id.app_icon), EdgeLightingStore.KEY_APP_ICON, false);
        bindSwitch(findViewById(R.id.screen_off_only), EdgeLightingStore.KEY_SCREEN_OFF_ONLY, true);
        bindSlider(findViewById(R.id.size), EdgeLightingStore.KEY_SIZE, 2);
        bindSlider(findViewById(R.id.amount), EdgeLightingStore.KEY_AMOUNT, 1);
        bindSlider(findViewById(R.id.duration), EdgeLightingStore.KEY_DURATION, 1);
        bindSlider(findViewById(R.id.rotation), EdgeLightingStore.KEY_ROTATION, 1);
        buildEffects();
        buildColors();

        mPlay.setOnClickListener(v -> sendBroadcast(
                new Intent(EdgeLightingStore.ACTION_PREVIEW).setPackage(SYSTEMUI), null,
                BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()));
        findViewById(R.id.add_keyword).setOnClickListener(v -> addKeyword());
        refresh();
    }

    @Override
    protected void onDestroy() {
        mExecutor.shutdownNow();
        super.onDestroy();
    }

    private void bindSwitch(Switch view, String key, boolean def) {
        view.setChecked(EdgeLightingStore.flag(this, key, def));
        view.setOnCheckedChangeListener((v, checked) -> {
            EdgeLightingStore.put(this, key, checked);
            updateStatus();
        });
    }

    private void bindSlider(SeekBar view, String key, int def) {
        view.setProgress(EdgeLightingStore.level(this, key, def));
        view.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) EdgeLightingStore.put(EdgeLightingActivity.this, key, progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void buildEffects() {
        RadioGroup group = findViewById(R.id.effects);
        String current = EdgeLightingStore.effect(this);
        for (int i = 0; i < EFFECTS.length; i++) {
            RadioButton button = new RadioButton(this);
            button.setId(View.generateViewId());
            button.setText(EFFECT_LABELS[i]);
            button.setChecked(EFFECTS[i].equals(current));
            String effect = EFFECTS[i];
            button.setOnClickListener(v ->
                    EdgeLightingStore.put(this, EdgeLightingStore.KEY_EFFECT, effect));
            group.addView(button, new RadioGroup.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    private void buildColors() {
        int size = dp(36);
        for (int color : COLORS) {
            View swatch = new View(this);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(color);
            swatch.setBackground(dot);
            swatch.setOnClickListener(v -> {
                EdgeLightingStore.put(this, EdgeLightingStore.KEY_COLOR, color);
                run(() -> Stickers.recolor(this, color));
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, size, 1f);
            lp.setMarginEnd(dp(8));
            mColors.addView(swatch, lp);
        }
    }

    /** Rebuilds everything that depends on the images and keywords. */
    private void refresh() {
        String selected = EdgeLightingStore.selected(this);
        File file = EdgeLightingStore.image(this, selected);
        mPreview.setImageBitmap(file != null ? BitmapFactory.decodeFile(file.getPath()) : null);
        buildImages(selected);
        buildKeywords();
        updateStatus();
    }

    private void buildImages(String selected) {
        mImages.removeAllViews();
        ImageView add = tile(null);
        add.setImageResource(R.drawable.ic_add);
        add.setScaleType(ImageView.ScaleType.CENTER);
        add.setContentDescription(getString(R.string.edge_lighting_add_photo));
        add.setOnClickListener(v -> startActivityForResult(
                new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*"), REQUEST_PICK));
        mImages.addView(add);

        for (String id : EdgeLightingStore.cutouts(this)) {
            ImageView tile = tile(id.equals(selected));
            tile.setImageBitmap(BitmapFactory.decodeFile(
                    EdgeLightingStore.image(this, id).getPath()));
            tile.setOnClickListener(v -> {
                EdgeLightingStore.put(this, EdgeLightingStore.KEY_SELECTED, id);
                refresh();
            });
            tile.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setMessage(R.string.edge_lighting_delete_image)
                        .setPositiveButton(R.string.edge_lighting_delete, (d, w) -> {
                            EdgeLightingStore.delete(this, id);
                            refresh();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            });
            mImages.addView(tile);
        }

        int color = EdgeLightingStore.color(this);
        for (int i = 0; i < Stickers.NAMES.length; i++) {
            ImageView tile = tile(Stickers.id(Stickers.NAMES[i]).equals(selected));
            tile.setImageResource(Stickers.DRAWABLES[i]);
            tile.setImageTintList(ColorStateList.valueOf(color));
            int index = i;
            tile.setOnClickListener(v -> run(() -> Stickers.select(this, index,
                    EdgeLightingStore.color(this))));
            mImages.addView(tile);
        }
    }

    private ImageView tile(Boolean selected) {
        ImageView tile = new ImageView(this);
        tile.setBackgroundResource(R.drawable.tile_background);
        tile.setPadding(dp(10), dp(10), dp(10), dp(10));
        tile.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.setSelected(Boolean.TRUE.equals(selected));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(76), dp(76));
        lp.setMarginEnd(dp(10));
        tile.setLayoutParams(lp);
        return tile;
    }

    private void buildKeywords() {
        mKeywords.removeAllViews();
        List<EdgeLightingStore.Keyword> keywords = EdgeLightingStore.keywords(this);
        for (EdgeLightingStore.Keyword k : keywords) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(6), dp(4), dp(6));
            ImageView thumb = new ImageView(this);
            File file = EdgeLightingStore.image(this, k.imageId);
            if (file.exists()) thumb.setImageBitmap(BitmapFactory.decodeFile(file.getPath()));
            row.addView(thumb, new LinearLayout.LayoutParams(dp(40), dp(40)));
            TextView word = new TextView(this);
            word.setText(k.word);
            word.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            word.setPadding(dp(16), 0, 0, 0);
            row.addView(word);
            row.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setMessage(R.string.edge_lighting_remove_keyword)
                    .setPositiveButton(R.string.edge_lighting_remove, (d, w) -> {
                        List<EdgeLightingStore.Keyword> list = EdgeLightingStore.keywords(this);
                        list.removeIf(x -> x.word.equals(k.word));
                        EdgeLightingStore.setKeywords(this, list);
                        buildKeywords();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show());
            mKeywords.addView(row);
        }
    }

    private void addKeyword() {
        String image = EdgeLightingStore.selected(this);
        if (image == null) {
            mStatus.setText(R.string.edge_lighting_keyword_needs_image);
            return;
        }
        EditText input = new EditText(this);
        input.setHint(R.string.edge_lighting_keyword_hint);
        input.setSingleLine();
        new AlertDialog.Builder(this)
                .setTitle(R.string.edge_lighting_add_keyword)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String word = input.getText().toString().trim();
                    if (word.isEmpty()) return;
                    List<EdgeLightingStore.Keyword> list = EdgeLightingStore.keywords(this);
                    list.removeIf(x -> x.word.equalsIgnoreCase(word));
                    list.add(new EdgeLightingStore.Keyword(word, image));
                    EdgeLightingStore.setKeywords(this, list);
                    buildKeywords();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Runs a store edit off the main thread, then redraws. */
    private void run(IoAction action) {
        mExecutor.execute(() -> {
            try {
                action.run();
            } catch (IOException e) {
                Log.w(TAG, "Couldn't update the image", e);
            }
            runOnUiThread(() -> {
                if (!isDestroyed()) refresh();
            });
        });
    }

    private interface IoAction {
        void run() throws IOException;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        setBusy(true);
        mExecutor.execute(() -> {
            String error = null;
            try {
                Cutouts.create(this, uri, () -> runOnUiThread(
                        () -> mStatus.setText(R.string.edge_lighting_downloading)));
            } catch (Exception e) {
                Log.w(TAG, "Couldn't cut out " + uri, e);
                error = getString(R.string.edge_lighting_cutout_failed);
            }
            String finalError = error;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                setBusy(false);
                refresh();
                if (finalError != null) mStatus.setText(finalError);
            });
        });
    }

    private void setBusy(boolean busy) {
        mBusy = busy;
        mProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        mImages.setEnabled(!busy);
        if (busy) mStatus.setText(R.string.edge_lighting_cutting_out);
        updateStatus();
    }

    private void updateStatus() {
        boolean hasImage = EdgeLightingStore.selected(this) != null;
        mPlay.setEnabled(!mBusy && hasImage);
        if (mBusy) return;
        mStatus.setText(!hasImage ? R.string.edge_lighting_no_image
                : mEnabled.isChecked() ? R.string.edge_lighting_ready
                : R.string.edge_lighting_off);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
