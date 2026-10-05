/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.penguin.laboratory;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.penguin.laboratory.edgelighting.EdgeLightingActivity;
import com.penguin.laboratory.wonderland.WonderlandActivity;

/** Lists the experiments in Penguin Laboratory. */
public class LaboratoryActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.laboratory);
        findViewById(R.id.edge_lighting).setOnClickListener(
                v -> startActivity(new Intent(this, EdgeLightingActivity.class)));
        findViewById(R.id.wonderland).setOnClickListener(
                v -> startActivity(new Intent(this, WonderlandActivity.class)));
    }
}
