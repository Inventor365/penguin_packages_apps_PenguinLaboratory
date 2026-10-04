/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.penguin.laboratory.edgelighting;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Size;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

/** Lifts the main subject out of a photo with ML Kit, the way Gallery's "drag to shape" does. */
final class Cutouts {

    // The segmenter is slow and no more accurate on bigger inputs
    private static final int MAX_INPUT_PX = 1600;
    // Plenty for a sprite that is drawn at most a few hundred pixels wide
    private static final int MAX_OUTPUT_PX = 512;
    // Pixels with less coverage than this don't count towards the subject's bounds
    private static final int ALPHA_THRESHOLD = 24;
    // Play services fetches the model on first use; it takes a few seconds on a good connection
    private static final long DOWNLOAD_TIMEOUT_MS = 90_000;
    private static final long DOWNLOAD_POLL_MS = 2_000;

    private Cutouts() {}

    /** Blocks until the subject of {@code source} is saved as the cutout. Call off the main thread. */
    static void create(Context context, Uri source, Runnable onDownloading) throws Exception {
        Bitmap input = decode(context, source);
        SubjectSegmenter segmenter = SubjectSegmentation.getClient(
                new SubjectSegmenterOptions.Builder().enableForegroundBitmap().build());
        Bitmap foreground;
        try {
            foreground = segment(segmenter, InputImage.fromBitmap(input, 0), onDownloading);
        } finally {
            segmenter.close();
        }
        if (foreground == null) throw new IOException("No subject found");

        Bitmap subject = scaleDown(trim(foreground), MAX_OUTPUT_PX);
        String id = "cutout-" + System.currentTimeMillis();
        save(context, subject, id);
        EdgeLightingStore.put(context, EdgeLightingStore.KEY_SELECTED, id);
    }

    /** Writes {@code bitmap} as image {@code id}, replacing it atomically. */
    static void save(Context context, Bitmap bitmap, String id) throws IOException {
        File out = EdgeLightingStore.image(context, id);
        File tmp = new File(out.getPath() + ".tmp");
        try (FileOutputStream os = new FileOutputStream(tmp)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
        }
        if (!tmp.renameTo(out)) throw new IOException("Couldn't save " + id);
    }

    /**
     * Runs the segmenter, waiting out the model download Play services starts on first use.
     */
    private static Bitmap segment(SubjectSegmenter segmenter, InputImage image,
            Runnable onDownloading) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + DOWNLOAD_TIMEOUT_MS;
        boolean notified = false;
        while (true) {
            try {
                Task<SubjectSegmentationResult> task = segmenter.process(image);
                return Tasks.await(task).getForegroundBitmap();
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof MlKitException mlKit)
                        || mlKit.getErrorCode() != MlKitException.UNAVAILABLE
                        || SystemClock.elapsedRealtime() > deadline) {
                    throw e;
                }
                if (!notified) {
                    onDownloading.run();
                    notified = true;
                }
                Thread.sleep(DOWNLOAD_POLL_MS);
            }
        }
    }

    private static Bitmap decode(Context context, Uri uri) throws IOException {
        return ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(context.getContentResolver(), uri),
                (decoder, info, src) -> {
                    Size size = info.getSize();
                    int longest = Math.max(size.getWidth(), size.getHeight());
                    if (longest > MAX_INPUT_PX) {
                        float scale = (float) MAX_INPUT_PX / longest;
                        decoder.setTargetSize(Math.round(size.getWidth() * scale),
                                Math.round(size.getHeight() * scale));
                    }
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
    }

    /** Crops away the transparent margin around the subject. */
    private static Bitmap trim(Bitmap bitmap) throws IOException {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int[] row = new int[w];
        int left = w, top = h, right = -1, bottom = -1;
        for (int y = 0; y < h; y++) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                if ((row[x] >>> 24) > ALPHA_THRESHOLD) {
                    if (x < left) left = x;
                    if (x > right) right = x;
                    if (y < top) top = y;
                    bottom = y;
                }
            }
        }
        if (right < 0) throw new IOException("No subject found");
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1);
    }

    private static Bitmap scaleDown(Bitmap bitmap, int maxPx) {
        int longest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (longest <= maxPx) return bitmap;
        float scale = (float) maxPx / longest;
        return Bitmap.createScaledBitmap(bitmap, Math.round(bitmap.getWidth() * scale),
                Math.round(bitmap.getHeight() * scale), true);
    }
}
