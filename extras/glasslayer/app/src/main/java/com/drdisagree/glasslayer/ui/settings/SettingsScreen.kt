package com.drdisagree.glasslayer.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drdisagree.glasslayer.R
import com.drdisagree.glasslayer.domain.model.GlassToggle
import com.drdisagree.glasslayer.ui.components.GlassContainer
import com.drdisagree.glasslayer.ui.components.PageScaffold

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpen: (GlassPage) -> Unit,
    onOpenStatus: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    val enabled = state.settings.isOn(GlassToggle.ENABLED)
    val issue = state.status.issueColor()
    var menuOpen by remember { mutableStateOf(false) }
    val dim by animateFloatAsState(if (enabled) 1f else DIMMED_ALPHA, tween(DIM_MS), label = "masterDim")
    val dimmed = Modifier
        .fillMaxWidth()
        .graphicsLayer { alpha = dim }

    PageScaffold(
        title = stringResource(R.string.app_name),
        overlay = {
            HomeMenu(
                visible = menuOpen,
                issue = issue,
                onStatus = onOpenStatus,
                onAbout = onOpenAbout,
                onDismiss = { menuOpen = false }
            )
        },
        actions = {
            MenuButton(issue, onClick = { menuOpen = true })
        }
    ) {
        if (state.status.restartNeeded || state.status.safeMode) {
            item(key = "restart") { RestartBanner(state.restart, state.status.safeMode, viewModel::onRestart, Modifier.animateItem()) }
        }
        if (!state.loaded) return@PageScaffold
        item(key = "master") {
            MasterCard(enabled) { viewModel.onToggle(GlassToggle.ENABLED, it) }
        }
        item(key = "tiles") {
            HomeTiles(
                settings = state.settings,
                enabled = enabled,
                onOpenStyle = { onOpen(GlassPage.STYLE) },
                onOpenPerformance = { onOpen(GlassPage.PERFORMANCE) },
                modifier = dimmed.animateItem()
            )
        }
        ModGroup.entries.forEach { group ->
            item(key = "label_${group.name}") { SectionLabel(stringResource(group.title), Modifier.animateItem(), group.icon) }
            item(key = "group_${group.name}") {
                GlassContainer(CardShape, dimmed.animateItem(), enabled = enabled) {
                    group.pages.forEachIndexed { index, page ->
                        if (index > 0) RowDivider()
                        val toggle = page.toggle ?: return@forEachIndexed
                        if (page.hasSettings) {
                            SplitToggleRow(
                                stringResource(page.title),
                                stringResource(page.summary),
                                state.settings.isOn(toggle),
                                { viewModel.onToggle(toggle, it) }
                            ) { onOpen(page) }
                        } else {
                            ToggleRow(
                                stringResource(page.title),
                                stringResource(page.summary),
                                state.settings.isOn(toggle)
                            ) { viewModel.onToggle(toggle, it) }
                        }
                    }
                }
            }
        }
    }
}

private const val DIM_MS = 300
