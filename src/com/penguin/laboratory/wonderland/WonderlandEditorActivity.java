/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.penguin.laboratory.wonderland;

import com.penguin.laboratory.R;
import com.penguin.laboratory.edgelighting.Cutouts;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Builds a Wonderland wallpaper out of a background and layers that drift with the tilt. */
public class WonderlandEditorActivity extends Activity implements SceneView.Callback {

    public static final String EXTRA_PROJECT = "project";

    private static final String TAG = "Wonderland";
    private static final int REQUEST_BACKGROUND = 1;
    private static final int REQUEST_PHOTO = 2;
    private static final int REQUEST_SUBJECT = 3;
    // Big enough for a sharp full screen background with room to drift
    private static final int MAX_BACKGROUND_PX = 2560;
    private static final int MAX_LAYER_PX = 1600;
    private static final int THUMBNAIL_PX = 270;
    private static final long SAVE_DELAY_MS = 400;

    private static final int[] COLORS = {
            0xFFFFFFFF, 0xFF101828, 0xFF000000, 0xFFFF5C8A, 0xFFFF8A3D, 0xFFFFD43B, 0xFF69DB7C,
            0xFF38D9A9, 0xFF4DABF7, 0xFF9775FA, 0xFFFFC9DE};
    private static final int PETAL_PINK = 0xFFFFC9DE;
    private static final String[] PARTICLES = {
            Scene.PARTICLES_SNOW, Scene.PARTICLES_RAIN, Scene.PARTICLES_BUBBLES,
            Scene.PARTICLES_STARS, Scene.PARTICLES_FIREFLIES, Scene.PARTICLES_PETALS};
    private static final int[] PARTICLE_LABELS = {
            R.string.wonderland_particles_snow, R.string.wonderland_particles_rain,
            R.string.wonderland_particles_bubbles, R.string.wonderland_particles_stars,
            R.string.wonderland_particles_fireflies, R.string.wonderland_particles_petals};
    private static final String[] FONTS = {
            Scene.FONT_SANS, Scene.FONT_SERIF, Scene.FONT_CONDENSED, Scene.FONT_MONO};
    private static final int[] FONT_LABELS = {
            R.string.wonderland_font_sans, R.string.wonderland_font_serif,
            R.string.wonderland_font_condensed, R.string.wonderland_font_mono};

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mSave = this::saveNow;

    private String mProject;
    private Scene mScene;
    private String mSelected;

    private SceneView mSceneView;
    private LinearLayout mLayers;
    private LinearLayout mControls;
    private ProgressBar mProgress;
    private Switch mPreviewMotion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mProject = getIntent().getStringExtra(EXTRA_PROJECT);
        mScene = WonderlandStore.load(this, mProject);
        if (mScene == null) {
            finish();
            return;
        }
        setContentView(R.layout.wonderland_editor);
        mSceneView = findViewById(R.id.scene);
        mLayers = findViewById(R.id.layers);
        mControls = findViewById(R.id.controls);
        mProgress = findViewById(R.id.progress);
        mPreviewMotion = findViewById(R.id.preview_motion);

        mSceneView.setScene(mProject, mScene);
        mSceneView.setCallback(this);
        mPreviewMotion.setOnCheckedChangeListener((v, checked) -> {
            mSceneView.setPreviewMotion(checked);
        });
        findViewById(R.id.apply).setOnClickListener(v -> apply());
        if (savedInstanceState != null) mSelected = savedInstanceState.getString("selected");
        refresh();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("selected", mSelected);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mScene != null && mHandler.hasCallbacks(mSave)) {
            mHandler.removeCallbacks(mSave);
            saveNow();
        }
    }

    @Override
    protected void onDestroy() {
        mExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    public void onLayerSelected(String id) {
        mSelected = id;
        refresh();
    }

    @Override
    public void onSceneEdited() {
        scheduleSave();
    }

    private void scheduleSave() {
        mHandler.removeCallbacks(mSave);
        mHandler.postDelayed(mSave, SAVE_DELAY_MS);
    }

    private void saveNow() {
        String json;
        Scene copy;
        try {
            json = mScene.toJson();
            copy = Scene.fromJson(json);
        } catch (org.json.JSONException e) {
            Log.w(TAG, "Couldn't save", e);
            return;
        }
        Bitmap thumbnail = mSceneView.snapshot(THUMBNAIL_PX);
        mExecutor.execute(() -> {
            try {
                WonderlandStore.save(this, mProject, copy, thumbnail);
            } catch (IOException e) {
                Log.w(TAG, "Couldn't save " + mProject, e);
            }
        });
    }

    private void edited() {
        mSceneView.invalidate();
        scheduleSave();
    }

    private void refresh() {
        if (mSelected != null && mScene.find(mSelected) == null) mSelected = null;
        mSceneView.setScene(mProject, mScene);
        mSceneView.setSelected(mSelected);
        buildLayers();
        buildControls();
    }

    // Layers strip

    private void buildLayers() {
        mLayers.removeAllViews();
        TextView add = chip(getString(R.string.wonderland_add_layer), false);
        add.setOnClickListener(v -> addLayer());
        mLayers.addView(add);
        TextView scene = chip(getString(R.string.wonderland_scene), mSelected == null);
        scene.setOnClickListener(v -> onLayerSelected(null));
        mLayers.addView(scene);
        // Top layer first, as they stack
        for (int i = mScene.layers.size() - 1; i >= 0; i--) {
            Scene.Layer l = mScene.layers.get(i);
            TextView chip = chip(label(l), l.id.equals(mSelected));
            if (l.hidden) chip.setAlpha(0.5f);
            chip.setOnClickListener(v -> onLayerSelected(l.id));
            mLayers.addView(chip);
        }
    }

    private String label(Scene.Layer l) {
        return switch (l.type) {
            case Scene.TYPE_BACKGROUND -> getString(R.string.wonderland_layer_background);
            case Scene.TYPE_TEXT -> l.text != null && !l.text.isEmpty()
                    ? "“" + firstLine(l.text) + "”" : getString(R.string.wonderland_layer_text);
            case Scene.TYPE_PARTICLES -> getString(PARTICLE_LABELS[indexOf(PARTICLES, l.particles)]);
            default -> getString(R.string.wonderland_layer_image);
        };
    }

    private static String firstLine(String text) {
        String line = text.split("\n")[0];
        return line.length() > 12 ? line.substring(0, 12) + "…" : line;
    }

    private TextView chip(String text, boolean selected) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setGravity(Gravity.CENTER);
        chip.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Widget_Button);
        chip.setAllCaps(false);
        chip.setBackgroundResource(R.drawable.chip_background);
        chip.setTextColor(getColorStateList(R.color.chip_text));
        chip.setSelected(selected);
        chip.setPadding(dp(16), 0, dp(16), 0);
        chip.setMinHeight(dp(40));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        lp.setMarginEnd(dp(8));
        chip.setLayoutParams(lp);
        return chip;
    }

    private void addLayer() {
        String[] items = {
                getString(R.string.wonderland_add_background),
                getString(R.string.wonderland_add_subject),
                getString(R.string.wonderland_add_photo),
                getString(R.string.wonderland_add_text),
                getString(R.string.wonderland_add_particles)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.wonderland_add_layer)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0 -> pick(REQUEST_BACKGROUND);
                        case 1 -> pick(REQUEST_SUBJECT);
                        case 2 -> pick(REQUEST_PHOTO);
                        case 3 -> editText(null);
                        case 4 -> addParticles();
                    }
                })
                .show();
    }

    private void pick(int request) {
        startActivityForResult(new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*"),
                request);
    }

    private Scene.Layer newLayer(String type) {
        Scene.Layer l = new Scene.Layer();
        l.id = Scene.newId("layer");
        l.type = type;
        return l;
    }

    private void addOnTop(Scene.Layer l) {
        mScene.layers.add(l);
        mSelected = l.id;
        refresh();
        scheduleSave();
    }

    private void addParticles() {
        Scene.Layer l = newLayer(Scene.TYPE_PARTICLES);
        l.particles = Scene.PARTICLES_SNOW;
        l.motionX = l.motionY = 0.5f;
        addOnTop(l);
    }

    private void editText(Scene.Layer existing) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint(R.string.wonderland_text_hint);
        if (existing != null) input.setText(existing.text);
        LinearLayout frame = new LinearLayout(this);
        frame.setPadding(dp(24), dp(8), dp(24), 0);
        frame.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle(existing == null ? R.string.wonderland_add_text
                        : R.string.wonderland_edit_text)
                .setView(frame)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) return;
                    if (existing != null) {
                        existing.text = text;
                        refresh();
                        scheduleSave();
                        return;
                    }
                    Scene.Layer l = newLayer(Scene.TYPE_TEXT);
                    l.text = text;
                    l.y = 0.3f;
                    l.scale = 0.5f;
                    l.motionX = l.motionY = 0.3f;
                    addOnTop(l);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        setBusy(true, requestCode == REQUEST_SUBJECT
                ? R.string.wonderland_cutting_out : R.string.wonderland_loading);
        mExecutor.execute(() -> {
            Scene.Layer layer = null;
            int error = 0;
            try {
                layer = importImage(requestCode, uri);
            } catch (Exception e) {
                Log.w(TAG, "Couldn't import " + uri, e);
                error = requestCode == REQUEST_SUBJECT
                        ? R.string.wonderland_cutout_failed : R.string.wonderland_load_failed;
            }
            Scene.Layer finalLayer = layer;
            int finalError = error;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                setBusy(false, 0);
                if (finalError != 0) {
                    Toast.makeText(this, finalError, Toast.LENGTH_LONG).show();
                    return;
                }
                if (Scene.TYPE_BACKGROUND.equals(finalLayer.type)) {
                    // One background, always at the bottom
                    Scene.Layer old = mScene.background();
                    if (old != null) mScene.layers.remove(old);
                    mScene.layers.add(0, finalLayer);
                    mSelected = finalLayer.id;
                    refresh();
                    scheduleSave();
                } else {
                    addOnTop(finalLayer);
                }
            });
        });
    }

    /** Copies the picked photo into the project as a new layer. Runs off the main thread. */
    private Scene.Layer importImage(int request, Uri uri) throws Exception {
        Scene.Layer l;
        Bitmap bitmap;
        String name;
        switch (request) {
            case REQUEST_BACKGROUND -> {
                l = newLayer(Scene.TYPE_BACKGROUND);
                l.scale = 1f;
                l.motionX = l.motionY = 0.3f;
                bitmap = Images.decode(this, uri, MAX_BACKGROUND_PX);
                l.source = uri.toString();
                name = l.id + ".jpg";
            }
            case REQUEST_SUBJECT -> {
                l = newLayer(Scene.TYPE_IMAGE);
                l.y = 0.62f;
                l.scale = 0.8f;
                l.motionX = l.motionY = -0.2f;
                RectF bounds = new RectF();
                bitmap = Cutouts.subject(this, uri, () -> runOnUiThread(
                        () -> setBusy(true, R.string.wonderland_downloading)), bounds);
                l.source = uri.toString();
                l.anchored = true;
                l.cropLeft = bounds.left;
                l.cropTop = bounds.top;
                l.cropRight = bounds.right;
                l.cropBottom = bounds.bottom;
                bitmap = Cutouts.scaleDown(bitmap, MAX_LAYER_PX);
                name = l.id + ".png";
            }
            default -> {
                l = newLayer(Scene.TYPE_IMAGE);
                l.scale = 0.6f;
                l.motionX = l.motionY = -0.3f;
                bitmap = Images.decode(this, uri, MAX_LAYER_PX);
                name = l.id + ".png";
            }
        }
        l.file = WonderlandStore.writeImage(this, mProject, name, bitmap);
        return l;
    }

    private void setBusy(boolean busy, int message) {
        mProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        TextView status = findViewById(R.id.status);
        status.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (message != 0) status.setText(message);
    }

    // Controls for the selection

    private void buildControls() {
        mControls.removeAllViews();
        Scene.Layer l = mSelected != null ? mScene.find(mSelected) : null;
        if (l == null) {
            buildSceneControls();
            return;
        }
        switch (l.type) {
            case Scene.TYPE_TEXT -> {
                Button edit = button(R.string.wonderland_edit_text);
                edit.setOnClickListener(v -> editText(l));
                mControls.addView(edit);
                header(R.string.wonderland_font);
                choices(FONTS, FONT_LABELS, l.font, font -> {
                    l.font = font;
                    edited();
                });
                header(R.string.wonderland_color);
                colors(l.color, color -> {
                    l.color = color;
                    edited();
                });
            }
            case Scene.TYPE_PARTICLES -> {
                header(R.string.wonderland_particles);
                choices(PARTICLES, PARTICLE_LABELS, l.particles, kind -> {
                    if (Scene.PARTICLES_PETALS.equals(kind) && l.color == 0xFFFFFFFF) {
                        l.color = PETAL_PINK;
                    }
                    l.particles = kind;
                    refresh();
                    scheduleSave();
                });
                level(R.string.wonderland_amount, l.amount, v -> {
                    l.amount = v;
                    edited();
                });
                level(R.string.wonderland_size, l.size, v -> {
                    l.size = v;
                    edited();
                });
                level(R.string.wonderland_speed, l.speed, v -> {
                    l.speed = v;
                    edited();
                });
                header(R.string.wonderland_color);
                colors(l.color, color -> {
                    l.color = color;
                    edited();
                });
            }
            case Scene.TYPE_BACKGROUND -> {
                Button change = button(R.string.wonderland_change_background);
                change.setOnClickListener(v -> pick(REQUEST_BACKGROUND));
                mControls.addView(change);
            }
        }

        header(R.string.wonderland_transparency);
        slider(0, 100, Math.round((1f - l.alpha) * 100), v -> {
            l.alpha = 1f - v / 100f;
            edited();
        });
        header(R.string.wonderland_motion);
        TextView motionHint = new TextView(this);
        motionHint.setText(R.string.wonderland_motion_description);
        motionHint.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
        motionHint.setPadding(dp(4), 0, dp(4), dp(8));
        mControls.addView(motionHint);
        motion(R.string.wonderland_motion_x, l.motionX, v -> {
            l.motionX = v;
            edited();
        });
        motion(R.string.wonderland_motion_y, l.motionY, v -> {
            l.motionY = v;
            edited();
        });
        motion(R.string.wonderland_motion_z, l.motionZ, v -> {
            l.motionZ = v;
            edited();
        });

        LinearLayout actions = row();
        Button visibility = button(l.hidden ? R.string.wonderland_show : R.string.wonderland_hide);
        visibility.setOnClickListener(v -> {
            l.hidden = !l.hidden;
            refresh();
            scheduleSave();
        });
        actions.addView(visibility, weighted());
        if (!Scene.TYPE_BACKGROUND.equals(l.type)) {
            Button forward = button(R.string.wonderland_forward);
            forward.setOnClickListener(v -> move(l, 1));
            actions.addView(forward, weighted());
            Button backward = button(R.string.wonderland_backward);
            backward.setOnClickListener(v -> move(l, -1));
            actions.addView(backward, weighted());
        }
        mControls.addView(actions);
        LinearLayout more = row();
        if (!Scene.TYPE_BACKGROUND.equals(l.type)) {
            Button duplicate = button(R.string.wonderland_duplicate);
            duplicate.setOnClickListener(v -> {
                Scene.Layer copy = l.copy();
                copy.id = Scene.newId("layer");
                copy.x = Math.min(1f, copy.x + 0.05f);
                copy.y = Math.min(1f, copy.y + 0.05f);
                addOnTop(copy);
            });
            more.addView(duplicate, weighted());
        }
        Button delete = button(R.string.wonderland_delete_layer);
        delete.setOnClickListener(v -> {
            mScene.layers.remove(l);
            mSelected = null;
            refresh();
            scheduleSave();
        });
        more.addView(delete, weighted());
        mControls.addView(more);
    }

    private void buildSceneControls() {
        header(R.string.wonderland_sensitivity);
        slider(0, 100, Math.round(mScene.sensitivity * 100), v -> {
            mScene.sensitivity = v / 100f;
            edited();
        });
        header(R.string.wonderland_background_color);
        colors(mScene.backgroundColor, color -> {
            mScene.backgroundColor = color;
            edited();
        });
        TextView hint = new TextView(this);
        hint.setText(R.string.wonderland_scene_description);
        hint.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
        hint.setPadding(dp(4), dp(16), dp(4), 0);
        mControls.addView(hint);
    }

    /** Moves a layer up or down the stack, never under the background. */
    private void move(Scene.Layer l, int by) {
        int from = mScene.layers.indexOf(l);
        int to = from + by;
        int bottom = mScene.background() != null ? 1 : 0;
        if (to < bottom || to >= mScene.layers.size()) return;
        mScene.layers.remove(from);
        mScene.layers.add(to, l);
        refresh();
        scheduleSave();
    }

    private interface IntSetter {
        void set(int value);
    }

    private interface FloatSetter {
        void set(float value);
    }

    private interface StringSetter {
        void set(String value);
    }

    private void header(int text) {
        TextView header = new TextView(this);
        header.setText(text);
        header.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
        header.setTextColor(getColor(android.R.color.system_accent1_400));
        header.setPadding(dp(4), dp(16), dp(4), dp(4));
        mControls.addView(header);
    }

    private SeekBar slider(int min, int max, int value, IntSetter setter) {
        SeekBar bar = new SeekBar(this);
        bar.setMin(min);
        bar.setMax(max);
        bar.setProgress(value);
        bar.setPadding(dp(16), dp(8), dp(16), dp(8));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) setter.set(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        mControls.addView(bar);
        return bar;
    }

    private void level(int label, int value, IntSetter setter) {
        header(label);
        slider(0, 2, value, setter);
    }

    /** A slider from -100 to 100 that snaps to 0 near the middle. */
    private void motion(int label, float value, FloatSetter setter) {
        TextView title = new TextView(this);
        title.setText(label);
        title.setPadding(dp(4), dp(8), dp(4), 0);
        mControls.addView(title);
        slider(-100, 100, Math.round(value * 100), v -> {
            setter.set(Math.abs(v) < 6 ? 0f : v / 100f);
        });
    }

    private void choices(String[] values, int[] labels, String current, StringSetter setter) {
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        for (int i = 0; i < values.length; i++) {
            RadioButton button = new RadioButton(this);
            button.setId(View.generateViewId());
            button.setText(labels[i]);
            button.setChecked(values[i].equals(current));
            String value = values[i];
            button.setOnClickListener(v -> setter.set(value));
            group.addView(button);
        }
        mControls.addView(group);
    }

    private void colors(int current, IntSetter setter) {
        LinearLayout row = row();
        int size = dp(32);
        for (int color : COLORS) {
            View swatch = new View(this);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(color);
            dot.setStroke(dp(color == current ? 3 : 1),
                    color == current ? getColor(android.R.color.system_accent1_400) : 0x40808080);
            swatch.setBackground(dot);
            swatch.setOnClickListener(v -> {
                setter.set(color);
                buildControls();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(dp(6));
            row.addView(swatch, lp);
        }
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.addView(row);
        mControls.addView(scroller);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, 0);
        return row;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(dp(6));
        return lp;
    }

    private Button button(int text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        return button;
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return i;
        return 0;
    }

    // Applying

    private void apply() {
        mHandler.removeCallbacks(mSave);
        saveNow();
        WonderlandStore.setActive(this, mProject);
        ComponentName component = new ComponentName(this, WonderlandWallpaperService.class);
        WallpaperManager wm = WallpaperManager.getInstance(this);
        boolean home = isOurs(wm.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM), component);
        // Without its own wallpaper the lock screen shows the home screen's
        boolean lock = wm.getWallpaperId(WallpaperManager.FLAG_LOCK) < 0 ? home
                : isOurs(wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK), component);
        if (home && lock) {
            Toast.makeText(this, R.string.wonderland_applied, Toast.LENGTH_SHORT).show();
            return;
        }
        // Lets the user choose the home screen, lock screen or both
        startActivity(new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component));
    }

    private static boolean isOurs(WallpaperInfo info, ComponentName component) {
        return info != null && component.equals(info.getComponent());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
