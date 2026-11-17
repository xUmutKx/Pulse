package com.drdisagree.glasslayer.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drdisagree.glasslayer.R
import com.drdisagree.glasslayer.domain.model.BatterySaverAction
import com.drdisagree.glasslayer.domain.model.GlassLevel
import com.drdisagree.glasslayer.domain.model.GlassToggle
import com.drdisagree.glasslayer.domain.model.PerformanceMode
import com.drdisagree.glasslayer.domain.model.StyleParam
import com.drdisagree.glasslayer.domain.model.BatterySaverBackground
import com.drdisagree.glasslayer.domain.model.ThermalAction
import com.drdisagree.glasslayer.ui.components.ChoiceDialog
import com.drdisagree.glasslayer.ui.components.ChoiceOption
import com.drdisagree.glasslayer.ui.components.GlassContainer
import com.drdisagree.glasslayer.ui.components.PageScaffold
import com.drdisagree.glasslayer.ui.theme.OnInkMuted

@Composable
fun GlassPageScreen(page: GlassPage, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings
    val toggle = page.toggle
    val surface = page.surface
    val master = settings.isOn(GlassToggle.ENABLED)
    val active = master && (toggle == null || settings.isOn(toggle))
    val dim by animateFloatAsState(if (active) 1f else DIMMED_ALPHA, tween(DIM_MS), label = "pageDim")
    val masterDim by animateFloatAsState(if (master) 1f else DIMMED_ALPHA, tween(DIM_MS), label = "masterDim")
    val dimmed = Modifier
        .fillMaxWidth()
        .graphicsLayer { alpha = dim }
    val styleDrafts = remember(page) { mutableStateMapOf<StyleParam, Float>() }
    val levelDrafts = remember(page) { mutableStateMapOf<GlassLevel, Float>() }

    fun styleValue(param: StyleParam): Float =
        styleDrafts[param] ?: if (surface == null) settings.style(param) else settings.effective(surface, param)

    fun levelValue(level: GlassLevel): Float = levelDrafts[level] ?: settings.level(level)

    var choice by remember { mutableStateOf<PerformanceChoice?>(null) }
    val modeOptions = PerformanceMode.entries.map { ChoiceOption(stringResource(modeTitle(it)), stringResource(modeSummary(it))) }
    val saverOptions = BatterySaverAction.entries.map { ChoiceOption(stringResource(saverTitle(it)), stringResource(saverSummary(it))) }
    val thermalOptions = ThermalAction.entries.map { ChoiceOption(stringResource(thermalTitle(it)), stringResource(thermalSummary(it))) }
    val saverBackgroundOptions = BatterySaverBackground.entries.map {
        ChoiceOption(stringResource(saverBackgroundTitle(it)), stringResource(saverBackgroundSummary(it)))
    }

    PageScaffold(
        title = stringResource(page.pageTitle),
        onBack = onBack,
        overlay = {
            ChoiceDialog(
                visible = choice == PerformanceChoice.MODE,
                title = stringResource(R.string.performance_frame_rate),
                options = modeOptions,
                selected = settings.performance.ordinal,
                onSelect = { viewModel.onPerformance(PerformanceMode.entries[it]) },
                onDismiss = { choice = null }
            )
            ChoiceDialog(
                visible = choice == PerformanceChoice.SAVER,
                title = stringResource(R.string.performance_saver_title),
                options = saverOptions,
                selected = settings.batterySaver.ordinal,
                onSelect = { viewModel.onBatterySaver(BatterySaverAction.entries[it]) },
                onDismiss = { choice = null }
            )
            ChoiceDialog(
                visible = choice == PerformanceChoice.THERMAL,
                title = stringResource(R.string.performance_thermal_title),
                options = thermalOptions,
                selected = settings.thermal.ordinal,
                onSelect = { viewModel.onThermal(ThermalAction.entries[it]) },
                onDismiss = { choice = null }
            )
            ChoiceDialog(
                visible = choice == PerformanceChoice.SAVER_BACKGROUND,
                title = stringResource(R.string.section_battery_saver_background),
                options = saverBackgroundOptions,
                selected = settings.batterySaverBackground.ordinal,
                onSelect = { viewModel.onBatterySaverBackground(BatterySaverBackground.entries[it]) },
                onDismiss = { choice = null }
            )
        }
    ) {
        if (toggle == null) item(key = "summary") { Caption(stringResource(page.summary)) }
        if (!state.loaded) return@PageScaffold
        if (page != GlassPage.PERFORMANCE) item(key = "preview") {
            val tintLevel = page.levels.firstOrNull { it != GlassLevel.BACKGROUND_BLUR }
            GlassPreview(
                blurDp = if (StyleParam.BLUR_RADIUS in page.styleParams) styleValue(StyleParam.BLUR_RADIUS) else 0f,
                refraction = if (StyleParam.REFRACTION in page.styleParams) styleValue(StyleParam.REFRACTION) / PERCENT else 0f,
                reflection = styleValue(StyleParam.REFLECTION) / PERCENT,
                rim = styleValue(StyleParam.RIM) / PERCENT,
                shadow = styleValue(StyleParam.SHADOW) / PERCENT,
                tint = if (page.hasStyle) tintLevel?.let { levelValue(it) / PERCENT } ?: 0f else 0f,
                sceneDim = if (page == GlassPage.BACKGROUND) levelValue(GlassLevel.BACKGROUND_DIM) / PERCENT else 0f,
                sceneBlurDp = if (page == GlassPage.BACKGROUND) levelValue(GlassLevel.BACKGROUND_BLUR) / PERCENT * SCENE_BLUR_MAX else 0f,
                showGlass = page.hasStyle,
                modifier = dimmed.animateItem()
            )
        }
        if (toggle != null) {
            item(key = "toggle") {
                GlassContainer(
                    CardShape,
                    Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .graphicsLayer { alpha = masterDim },
                    enabled = master
                ) {
                    ToggleRow(
                        stringResource(R.string.page_enabled),
                        stringResource(page.summary),
                        settings.isOn(toggle)
                    ) { viewModel.onToggle(toggle, it) }
                }
            }
        }
        if (page.options.isNotEmpty()) {
            item(key = "options") {
                GlassContainer(CardShape, dimmed.animateItem(), enabled = active) {
                    page.options.forEachIndexed { index, option ->
                        if (index > 0) RowDivider()
                        ToggleRow(
                            stringResource(optionTitle(option)),
                            stringResource(optionSummary(option)),
                            settings.isOn(option)
                        ) { viewModel.onToggle(option, it) }
                    }
                }
            }
        }
        if (page.levels.isNotEmpty()) {
            item(key = "tint_label") { SectionLabel(stringResource(R.string.section_tint), Modifier.animateItem(), R.drawable.ic_section_tint) }
            item(key = "tints") {
                GlassContainer(CardShape, dimmed.animateItem(), enabled = active) {
                    page.levels.forEachIndexed { index, level ->
                        if (index > 0) RowDivider()
                        LevelSlider(
                            label = stringResource(levelLabel(level)),
                            value = settings.level(level),
                            max = level.max,
                            percentOfMax = false,
                            modified = settings.level(level).toInt() != state.defaults.level(level).toInt(),
                            onChange = { viewModel.onLevelChange(level, it) },
                            onReset = {
                                levelDrafts.remove(level)
                                viewModel.onLevelReset(level)
                            },
                            onDraft = { levelDrafts[level] = it }
                        )
                    }
                }
            }
        }
        if (page.hasStyle) {
            if (surface != null) {
                item(key = "style_label") { SectionLabel(stringResource(R.string.section_glass), Modifier.animateItem(), R.drawable.ic_section_glass) }
            }
            item(key = "style") {
                GlassContainer(CardShape, dimmed.animateItem(), enabled = active) {
                    page.styleParams.forEachIndexed { index, param ->
                        if (index > 0) RowDivider()
                        val value = if (surface == null) settings.style(param) else settings.effective(surface, param)
                        val modified = if (surface == null) {
                            value.toInt() != state.defaults.style(param).toInt()
                        } else {
                            settings.override(surface, param) != null
                        }
                        LevelSlider(
                            label = stringResource(styleLabel(param)),
                            value = value,
                            max = param.max,
                            percentOfMax = param == StyleParam.BLUR_RADIUS,
                            modified = modified,
                            onChange = { viewModel.onStyleChange(surface, param, it) },
                            onReset = {
                                styleDrafts.remove(param)
                                viewModel.onStyleReset(surface, param)
                            },
                            onDraft = { styleDrafts[param] = it }
                        )
                    }
                }
            }
        }
        if (page == GlassPage.PERFORMANCE) {
            item(key = "performance_hero") {
                PerformanceHero(settings.performance, settings.batterySaver, dimmed.animateItem())
            }
            item(key = "frame_rate_label") {
                SectionLabel(stringResource(R.string.performance_frame_rate), Modifier.animateItem(), R.drawable.ic_section_frame_rate)
            }
            item(key = "performance") {
                PerformanceCard(
                    mode = settings.performance,
                    saver = settings.batterySaver,
                    thermal = settings.thermal,
                    enabled = active,
                    onOpenMode = { choice = PerformanceChoice.MODE },
                    onOpenSaver = { choice = PerformanceChoice.SAVER },
                    onOpenThermal = { choice = PerformanceChoice.THERMAL },
                    modifier = dimmed.animateItem()
                )
            }
            item(key = "battery_saver_label") {
                SectionLabel(stringResource(R.string.section_battery_saver_group), Modifier.animateItem(), R.drawable.ic_section_battery)
            }
            item(key = "battery_saver") {
                GlassContainer(CardShape, dimmed.animateItem(), enabled = active) {
                    ChoiceRow(
                        stringResource(R.string.qs_background),
                        stringResource(saverBackgroundTitle(settings.batterySaverBackground))
                    ) { choice = PerformanceChoice.SAVER_BACKGROUND }
                }
            }
        }
    }
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = OnInkMuted,
        modifier = modifier.padding(start = 4.dp, end = 4.dp)
    )
}

private const val DIM_MS = 300
private const val PERCENT = 100f
private const val SCENE_BLUR_MAX = 24f
