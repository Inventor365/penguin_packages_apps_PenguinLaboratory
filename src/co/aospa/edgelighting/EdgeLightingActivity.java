/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.edgelighting;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Turns edge lighting on and picks the photo whose subject rains down on notifications. */
public class EdgeLightingActivity extends Activity {

    private static final String TAG = "EdgeLighting";
    private static final int REQUEST_PICK = 1;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private Switch mEnabled;
    private ImageView mPreview;
    private TextView mStatus;
    private ProgressBar mProgress;
    private Button mChoose;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edge_lighting);
        mEnabled = findViewById(R.id.enabled);
        mPreview = findViewById(R.id.preview);
        mStatus = findViewById(R.id.status);
        mProgress = findViewById(R.id.progress);
        mChoose = findViewById(R.id.choose);

        mEnabled.setChecked(EdgeLightingStore.isEnabled(this));
        mEnabled.setOnCheckedChangeListener((v, checked) -> {
            EdgeLightingStore.setEnabled(this, checked);
            updateStatus();
        });
        mChoose.setOnClickListener(v -> startActivityForResult(
                new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*"), REQUEST_PICK));
        showCutout();
    }

    @Override
    protected void onDestroy() {
        mExecutor.shutdownNow();
        super.onDestroy();
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
                Cutouts.create(this, uri);
            } catch (Exception e) {
                Log.w(TAG, "Couldn't cut out " + uri, e);
                error = getString(R.string.edge_lighting_cutout_failed);
            }
            String finalError = error;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                setBusy(false);
                showCutout();
                if (finalError != null) mStatus.setText(finalError);
            });
        });
    }

    private void setBusy(boolean busy) {
        mProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        mChoose.setEnabled(!busy);
        if (busy) mStatus.setText(R.string.edge_lighting_cutting_out);
    }

    private void showCutout() {
        File cutout = EdgeLightingStore.cutoutFile(this);
        mPreview.setImageBitmap(cutout.exists()
                ? BitmapFactory.decodeFile(cutout.getPath()) : null);
        updateStatus();
    }

    private void updateStatus() {
        boolean hasCutout = EdgeLightingStore.cutoutFile(this).exists();
        mStatus.setText(!hasCutout ? R.string.edge_lighting_no_image
                : mEnabled.isChecked() ? R.string.edge_lighting_ready
                : R.string.edge_lighting_off);
    }
}
