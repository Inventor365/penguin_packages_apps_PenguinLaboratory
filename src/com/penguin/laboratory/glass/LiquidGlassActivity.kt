/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.penguin.laboratory.glass

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.penguin.laboratory.R
import com.penguin.laboratory.ui.ConnectedChoice
import com.penguin.laboratory.ui.LabTheme
import com.penguin.laboratory.ui.SectionHeader
import com.penguin.laboratory.ui.SegmentedGroup
import com.penguin.laboratory.ui.SwitchItem

/** Liquid Glass & iOS Frosted Blur: the system's blurred surfaces drawn as glass or vibrant frost. */
object LiquidGlassStore {
    /** Read by the window manager, which hands it to SurfaceFlinger. */
    private const val KEY = "penguin_liquid_glass"
    private const val KEY_BLUR_STYLE = "penguin_blur_style"

    const val STYLE_STANDARD = 0
    const val STYLE_IOS_FROSTED = 1
    const val STYLE_LIQUID_GLASS = 2

    fun isEnabled(context: Context): Boolean {
        val cr = context.contentResolver
        val lg = Settings.Secure.getInt(cr, KEY, 0) != 0
        val style = Settings.Secure.getInt(cr, KEY_BLUR_STYLE, if (lg) STYLE_LIQUID_GLASS else STYLE_IOS_FROSTED)
        return lg || style == STYLE_LIQUID_GLASS
    }

    fun getBlurStyle(context: Context): Int {
        val cr = context.contentResolver
        val lg = Settings.Secure.getInt(cr, KEY, 0) != 0
        val style = Settings.Secure.getInt(cr, KEY_BLUR_STYLE, if (lg) STYLE_LIQUID_GLASS else STYLE_IOS_FROSTED)
        return if (lg) STYLE_LIQUID_GLASS else style.coerceIn(0, 2)
    }

    fun setBlurStyle(context: Context, style: Int) {
        val clamped = style.coerceIn(0, 2)
        val cr = context.contentResolver
        Settings.Secure.putInt(cr, KEY_BLUR_STYLE, clamped)
        Settings.Secure.putInt(cr, KEY, if (clamped == STYLE_LIQUID_GLASS) 1 else 0)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        setBlurStyle(context, if (enabled) STYLE_LIQUID_GLASS else STYLE_IOS_FROSTED)
    }
}

class LiquidGlassActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { LabTheme { LiquidGlass() } }
    }

    @Composable
    private fun LiquidGlass() {
        var blurStyle by remember { mutableStateOf(LiquidGlassStore.getBlurStyle(this)) }
        val enabled = blurStyle == LiquidGlassStore.STYLE_LIQUID_GLASS
        var tabBars by remember { mutableStateOf(TabBarStore.isEnabled(this)) }
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {
                MediumFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.liquid_glass_title)) },
                    subtitle = { Text(stringResource(R.string.liquid_glass_entry_summary)) },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(painterResource(R.drawable.ic_back), contentDescription = null)
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            LazyColumn(
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    )
            ) {
                item {
                    SegmentedGroup {
                        SwitchItem(
                            title = stringResource(R.string.liquid_glass_use),
                            supporting = stringResource(R.string.liquid_glass_use_summary),
                            checked = enabled,
                            onCheckedChange = {
                                val newStyle =
                                    if (it) LiquidGlassStore.STYLE_LIQUID_GLASS
                                    else LiquidGlassStore.STYLE_IOS_FROSTED
                                LiquidGlassStore.setBlurStyle(this@LiquidGlassActivity, newStyle)
                                blurStyle = newStyle
                            },
                            index = 0,
                            count = 1,
                            icon = R.drawable.materialsymbols_ic_water_drop_rounded_filled,
                        )
                    }
                }
                item { SectionHeader(stringResource(R.string.liquid_glass_style_header)) }
                item {
                    ConnectedChoice(
                        options =
                            listOf(
                                LiquidGlassStore.STYLE_LIQUID_GLASS,
                                LiquidGlassStore.STYLE_IOS_FROSTED,
                                LiquidGlassStore.STYLE_STANDARD,
                            ),
                        selected = blurStyle,
                        onSelect = { newStyle ->
                            LiquidGlassStore.setBlurStyle(this@LiquidGlassActivity, newStyle)
                            blurStyle = newStyle
                        },
                        label = { option ->
                            when (option) {
                                LiquidGlassStore.STYLE_LIQUID_GLASS ->
                                    stringResource(R.string.liquid_glass_style_liquid)
                                LiquidGlassStore.STYLE_IOS_FROSTED ->
                                    stringResource(R.string.liquid_glass_style_frosted)
                                else -> stringResource(R.string.liquid_glass_style_standard)
                            }
                        },
                    )
                }
                item { SectionHeader(stringResource(R.string.tab_bars_header)) }
                item {
                    SegmentedGroup {
                        SwitchItem(
                            title = stringResource(R.string.tab_bars_use),
                            supporting = stringResource(R.string.tab_bars_use_summary),
                            checked = tabBars,
                            onCheckedChange = {
                                TabBarStore.setEnabled(this@LiquidGlassActivity, it)
                                tabBars = it
                            },
                            index = 0,
                            count = 2,
                            icon = R.drawable.materialsymbols_ic_dock_to_bottom_rounded_filled,
                        )
                        SegmentedListItem(
                            onClick = {
                                startActivity(
                                    Intent(this@LiquidGlassActivity, TabBarAppsActivity::class.java)
                                )
                            },
                            shapes = ListItemDefaults.segmentedShapes(1, 2),
                            enabled = tabBars,
                            leadingContent = {
                                Icon(
                                    painterResource(R.drawable.materialsymbols_ic_apps_rounded_filled),
                                    contentDescription = null,
                                )
                            },
                            supportingContent = {
                                Text(stringResource(R.string.tab_bars_apps_summary))
                            },
                        ) {
                            Text(stringResource(R.string.tab_bars_apps_title))
                        }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.liquid_glass_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    )
                }
            }
        }
    }
}
