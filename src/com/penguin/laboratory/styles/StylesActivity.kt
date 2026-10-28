/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.penguin.laboratory.styles

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.penguin.laboratory.R
import com.penguin.laboratory.ui.ConnectedChoice
import com.penguin.laboratory.ui.LabTheme
import com.penguin.laboratory.ui.RadioItem
import com.penguin.laboratory.ui.SectionHeader
import com.penguin.laboratory.ui.SegmentedBlock
import com.penguin.laboratory.ui.SegmentedGroup
import com.penguin.laboratory.ui.SwitchItem

/** The looks PenguinSystemUI can draw its panels in. All in Settings.Secure, read by SystemUI. */
object StylesStore {
    const val QS_PANEL = "qs_panel_style"
    const val QS_FOLDER = "qs_connectivity_folder"
    const val QS_ONE_UI = "qs_oneui_experimental"
    const val VOLUME_PANEL = "volume_panel_style"
    const val POWER_MENU = "power_menu_style"

    /** A style with the value SystemUI reads and its label. */
    class Choice(val value: Int, @StringRes val label: Int)

    val qsPanel =
        listOf(
            Choice(1, R.string.styles_qs_penguin),
            Choice(2, R.string.styles_qs_myui),
            Choice(3, R.string.styles_qs_harmony),
            Choice(6, R.string.styles_qs_stock),
            Choice(5, R.string.styles_qs_coloros),
        )
    val volumePanel =
        listOf(
            Choice(1, R.string.styles_volume_expandable),
            Choice(2, R.string.styles_volume_oneui),
            Choice(3, R.string.styles_volume_myui),
        )
    val powerMenu =
        listOf(Choice(0, R.string.styles_power_penguin), Choice(1, R.string.styles_power_ios))

    fun get(context: Context, key: String, default: Int) =
        Settings.Secure.getInt(context.contentResolver, key, default)

    fun put(context: Context, key: String, value: Int) {
        Settings.Secure.putInt(context.contentResolver, key, value)
    }

    /** How many styles are in use other than AOSP's, for the Laboratory's list. */
    fun customised(context: Context) =
        listOf(
                get(context, QS_PANEL, 1) != 0,
                get(context, VOLUME_PANEL, 0) != 0,
                get(context, POWER_MENU, 0) != 0,
            )
            .count { it }
}

class StylesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { LabTheme { Styles() } }
    }

    @Composable
    private fun Styles() {
        val context = this
        var generation by remember { mutableIntStateOf(0) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { generation++ }
        val oneUi = remember(generation) { StylesStore.get(context, StylesStore.QS_ONE_UI, 0) != 0 }
        var qs by remember(generation) { mutableStateOf(StylesStore.get(context, StylesStore.QS_PANEL, 1)) }
        var folder by
            remember(generation) {
                mutableStateOf(StylesStore.get(context, StylesStore.QS_FOLDER, 1) != 0)
            }
        var volume by
            remember(generation) { mutableStateOf(StylesStore.get(context, StylesStore.VOLUME_PANEL, 0)) }
        var power by
            remember(generation) { mutableStateOf(StylesStore.get(context, StylesStore.POWER_MENU, 0)) }

        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {
                MediumFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.styles_title)) },
                    subtitle = { Text(stringResource(R.string.styles_entry_summary)) },
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
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    )
            ) {
                item { SectionHeader(stringResource(R.string.styles_qs_title)) }
                item {
                    // Six styles don't fit a row of buttons without cutting their names to a
                    // couple of letters, so list them, with the folder switch closing the group.
                    val styles =
                        listOf(StylesStore.Choice(0, R.string.styles_default)) +
                            StylesStore.qsPanel
                    val count = styles.size + 1
                    SegmentedGroup {
                        styles.forEachIndexed { index, choice ->
                            RadioItem(
                                title = stringResource(choice.label),
                                selected = qs == choice.value,
                                enabled = !oneUi,
                                onSelect = {
                                    StylesStore.put(context, StylesStore.QS_PANEL, choice.value)
                                    qs = choice.value
                                },
                                supporting =
                                    stringResource(R.string.styles_qs_oneui_active)
                                        .takeIf { oneUi && index == 0 },
                                index = index,
                                count = count,
                            )
                        }
                        SwitchItem(
                            title = stringResource(R.string.styles_qs_folder),
                            supporting = stringResource(R.string.styles_qs_folder_summary),
                            checked = folder,
                            onCheckedChange = {
                                StylesStore.put(context, StylesStore.QS_FOLDER, if (it) 1 else 0)
                                folder = it
                            },
                            index = count - 1,
                            count = count,
                            icon = R.drawable.materialsymbols_ic_grid_view_rounded_filled,
                        )
                    }
                }
                item { SectionHeader(stringResource(R.string.styles_volume_title)) }
                item {
                    SegmentedBlock(index = 0, count = 1) {
                        StyleChoice(
                            choices = StylesStore.volumePanel,
                            selected = volume,
                            // AOSP's own volume panel, for when none of these is wanted.
                            default = Pair(0, R.string.styles_default),
                        ) {
                            StylesStore.put(context, StylesStore.VOLUME_PANEL, it)
                            volume = it
                        }
                    }
                }
                item { SectionHeader(stringResource(R.string.styles_power_title)) }
                item {
                    SegmentedBlock(index = 0, count = 1) {
                        StyleChoice(choices = StylesStore.powerMenu, selected = power) {
                            StylesStore.put(context, StylesStore.POWER_MENU, it)
                            power = it
                        }
                    }
                }
            }
        }
    }

    /** One button per style, [default] first when AOSP's look is one of the options. */
    @Composable
    private fun StyleChoice(
        choices: List<StylesStore.Choice>,
        selected: Int,
        enabled: Boolean = true,
        default: Pair<Int, Int>? = null,
        onSelect: (Int) -> Unit,
    ) {
        val options = listOfNotNull(default?.first) + choices.map { it.value }
        val labels = (listOfNotNull(default) + choices.map { it.value to it.label }).toMap()
        ConnectedChoice(
            options = options,
            selected = selected,
            onSelect = { if (enabled) onSelect(it) },
            label = { stringResource(labels.getValue(it)) },
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}
