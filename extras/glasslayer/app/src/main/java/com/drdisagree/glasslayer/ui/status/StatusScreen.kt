package com.drdisagree.glasslayer.ui.status

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drdisagree.glasslayer.R
import com.drdisagree.glasslayer.domain.model.ModReport
import com.drdisagree.glasslayer.ui.components.GlassContainer
import com.drdisagree.glasslayer.ui.components.GlassIconButton
import com.drdisagree.glasslayer.ui.components.GlassScene
import com.drdisagree.glasslayer.ui.components.PageScaffold
import com.drdisagree.glasslayer.ui.components.PillButton
import com.drdisagree.glasslayer.ui.components.StatusGlyph
import com.drdisagree.glasslayer.ui.components.StatusTone
import com.drdisagree.glasslayer.ui.components.rememberSpin
import com.drdisagree.glasslayer.ui.settings.RowDivider
import com.drdisagree.glasslayer.ui.settings.SectionLabel
import com.drdisagree.glasslayer.ui.theme.Accent
import com.drdisagree.glasslayer.ui.theme.Negative
import com.drdisagree.glasslayer.ui.theme.OnInk
import com.drdisagree.glasslayer.ui.theme.OnInkFaint
import com.drdisagree.glasslayer.ui.theme.OnInkMuted
import com.drdisagree.glasslayer.ui.theme.Warning
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusScreen(viewModel: StatusViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val steps = statusSteps(state)
    val overall = when {
        steps.all { it.state == StepState.OK } -> StepState.OK
        steps.any { it.state == StepState.FAILED } -> StepState.FAILED
        else -> StepState.WAITING
    }
    val currentState by rememberUpdatedState(state)
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(REPORT_MIME)) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.onSaveReport(uri.toString(), StatusReportFormatter.format(context, currentState))
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    PageScaffold(
        stringResource(R.string.status_title),
        onBack,
        actions = { RefreshButton(state.refresh, viewModel::refresh) }
    ) {
        item(key = "hero") { Hero(steps, overall) }
        item(key = "checks_label") { SectionLabel(stringResource(R.string.section_checks), icon = R.drawable.ic_section_checks) }
        item(key = "checks") {
            GlassContainer(CardShape, Modifier.fillMaxWidth()) {
                steps.forEachIndexed { index, step ->
                    if (index > 0) RowDivider()
                    CheckRow(step, state.restarting, viewModel::onRestartSystemUi)
                }
            }
        }
        val report = state.report
        if (report != null && report.mods.isNotEmpty()) {
            item(key = "mods_label") { SectionLabel(stringResource(R.string.section_surfaces), Modifier.animateItem(), R.drawable.ic_section_surfaces) }
            item(key = "mods") {
                GlassContainer(CardShape, Modifier.fillMaxWidth().animateItem()) {
                    report.mods.forEachIndexed { index, mod ->
                        if (index > 0) RowDivider()
                        SurfaceRow(mod)
                    }
                }
            }
        }
        if (report != null && report.errors.isNotEmpty()) {
            item(key = "errors_label") { SectionLabel(stringResource(R.string.section_errors), Modifier.animateItem(), R.drawable.ic_alert) }
            item(key = "errors") {
                GlassContainer(CardShape, Modifier.fillMaxWidth().animateItem()) {
                    report.errors.take(MAX_SHOWN_ERRORS).forEachIndexed { index, error ->
                        if (index > 0) RowDivider()
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnInkMuted,
                            maxLines = ERROR_LINES,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                        )
                    }
                }
            }
        }
        if (report != null) {
            item(key = "system_label") { SectionLabel(stringResource(R.string.section_system), Modifier.animateItem(), R.drawable.ic_info) }
            item(key = "system") {
                GlassContainer(CardShape, Modifier.fillMaxWidth().animateItem()) {
                    InfoRow(stringResource(R.string.status_systemui_version), report.systemUiVersion)
                    RowDivider()
                    InfoRow(stringResource(R.string.status_build), report.buildId)
                    RowDivider()
                    InfoRow(
                        stringResource(R.string.status_api),
                        state.framework?.apiVersion?.toString() ?: stringResource(R.string.not_detected)
                    )
                    RowDivider()
                    InfoRow(stringResource(R.string.status_updated), DateUtils.getRelativeTimeSpanString(report.timestamp).toString())
                }
            }
        }
        item(key = "report") {
            GlassContainer(CardShape, Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Row(
                    Modifier.padding(start = 20.dp, end = 14.dp, top = 18.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.report_help_title), style = MaterialTheme.typography.titleSmall, color = OnInk)
                        Text(stringResource(R.string.report_help_body), style = MaterialTheme.typography.bodySmall, color = OnInkMuted)
                    }
                    Spacer(Modifier.width(12.dp))
                    PillButton(
                        stringResource(
                            when (state.save) {
                                SaveState.IDLE -> R.string.action_save
                                SaveState.SAVING -> R.string.action_saving
                                SaveState.SAVED -> R.string.action_saved
                                SaveState.FAILED -> R.string.action_save_failed
                            }
                        ),
                        if (state.save == SaveState.FAILED) Warning else Accent,
                        { saveLauncher.launch(reportFileName()) },
                        icon = R.drawable.ic_save,
                        spinning = state.save == SaveState.SAVING
                    )
                }
            }
        }
    }
}

@Composable
private fun statusSteps(state: StatusUiState): List<StatusStep> {
    val framework = state.framework
    val report = state.report
    val steps = mutableListOf<StatusStep>()
    var blocked = false
    fun add(title: String, value: String, ok: Boolean, soft: Boolean = false, action: StepAction? = null) {
        val stepState = when {
            blocked -> StepState.PENDING
            ok -> StepState.OK
            soft -> StepState.WAITING
            else -> StepState.FAILED
        }
        steps += StatusStep(title, value, stepState, action.takeIf { stepState != StepState.OK && stepState != StepState.PENDING })
        if (!ok) blocked = true
    }
    add(
        stringResource(R.string.status_framework),
        framework?.let { stringResource(R.string.framework_label, it.name, it.version.substringBefore(' ')) }
            ?: stringResource(R.string.not_detected),
        framework != null
    )
    add(
        stringResource(R.string.status_scope),
        stringResource(if (state.isScoped) R.string.status_scope_ok else R.string.status_scope_missing),
        state.isScoped
    )
    add(
        stringResource(R.string.status_step_systemui),
        report?.systemUiVersion?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.status_no_report_summary),
        report != null,
        action = StepAction.RESTART
    )
    add(
        stringResource(R.string.status_step_update),
        stringResource(if (state.isStale) R.string.status_update_stale else R.string.status_update_ok),
        !state.isStale,
        soft = true,
        action = StepAction.RESTART
    )
    add(
        stringResource(R.string.status_step_capture),
        stringResource(if (report?.captureAvailable == true) R.string.status_capture_ok else R.string.status_capture_missing),
        report?.captureAvailable == true
    )
    return steps
}

@Composable
private fun Hero(steps: List<StatusStep>, overall: StepState) {
    val scene = rememberLayerBackdrop()
    val passed = steps.count { it.state == StepState.OK }
    GlassContainer(CardShape, Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(HERO_HEIGHT)
                .padding(8.dp)
                .clip(InnerShape),
            contentAlignment = Alignment.Center
        ) {
            GlassScene(
                Modifier
                    .matchParentSize()
                    .layerBackdrop(scene),
                accent = sceneAccent(overall)
            )
            Column(
                Modifier
                    .padding(horizontal = 18.dp)
                    .fillMaxWidth()
                    .drawBackdrop(
                        backdrop = scene,
                        shape = { PanelShape },
                        effects = {
                            vibrancy()
                            blur(PANEL_BLUR.toPx())
                            lens(PANEL_LENS.toPx(), PANEL_LENS.toPx() * 2f, depthEffect = true)
                        },
                        highlight = { Highlight.Default.copy(alpha = RIM_ALPHA) },
                        onDrawSurface = { drawRect(PANEL_FILL) }
                    )
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedContent(
                    targetState = overall,
                    transitionSpec = { fadeIn(tween(300, delayMillis = 80)) togetherWith fadeOut(tween(160)) },
                    label = "heroTitle"
                ) { target ->
                    Text(
                        stringResource(
                            when (target) {
                                StepState.OK -> R.string.status_hero_ok
                                StepState.FAILED -> R.string.status_hero_failed
                                else -> R.string.status_hero_waiting
                            }
                        ),
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    pluralStringResource(R.plurals.status_ring_summary, steps.size, passed, steps.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = MUTED_WHITE),
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(
                    Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    steps.forEach { step ->
                        val color by animateColorAsState(beadColor(step.state), tween(500), label = "bead")
                        Box(
                            Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(Capsule())
                                .background(color)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(step: StatusStep, restarting: Boolean, onRestart: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusGlyph(toneOf(step.state), size = 18.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(step.title, style = MaterialTheme.typography.titleSmall, color = if (step.state == StepState.PENDING) OnInkMuted else OnInk)
            AnimatedContent(
                targetState = step.value,
                transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(140)) },
                label = "stepValue"
            ) { value ->
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (step.state == StepState.PENDING) OnInkFaint else OnInkMuted
                )
            }
        }
        if (step.action == StepAction.RESTART) {
            Spacer(Modifier.width(12.dp))
            PillButton(
                stringResource(if (restarting) R.string.action_restarting else R.string.action_restart),
                Negative,
                onRestart,
                spinning = restarting,
                enabled = !restarting
            )
        }
    }
}

@Composable
private fun SurfaceRow(mod: ModReport) {
    val tone = when {
        mod.hooks == 0 -> StatusTone.ERROR
        mod.broken -> StatusTone.WARNING
        else -> StatusTone.OK
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusGlyph(tone, size = 18.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(modLabel(mod.name)?.let { stringResource(it) } ?: mod.name, style = MaterialTheme.typography.titleSmall, color = OnInk)
            Text(
                when (tone) {
                    StatusTone.ERROR -> stringResource(R.string.mod_report_unsupported)
                    StatusTone.WARNING -> stringResource(R.string.mod_report_paused)
                    else -> pluralStringResource(R.plurals.mod_report_hooks, mod.hooks, mod.hooks)
                },
                style = MaterialTheme.typography.bodySmall,
                color = OnInkMuted
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnInkMuted, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value.ifEmpty { "-" }, style = MaterialTheme.typography.titleSmall, color = OnInk, textAlign = TextAlign.End, maxLines = 2)
    }
}

@Composable
private fun RefreshButton(refresh: RefreshState, onRefresh: () -> Unit) {
    val angle by rememberSpin(refresh == RefreshState.CHECKING, SPIN_MS)
    val iconTint by animateColorAsState(
        when (refresh) {
            RefreshState.NO_RESPONSE -> Warning
            RefreshState.UPDATED -> Accent
            else -> OnInk
        },
        tween(300),
        label = "refreshTint"
    )
    GlassIconButton(
        R.drawable.ic_refresh,
        onRefresh,
        description = stringResource(R.string.action_refresh),
        iconTint = iconTint,
        iconModifier = Modifier.graphicsLayer { rotationZ = angle }
    )
}

private fun toneOf(state: StepState): StatusTone = when (state) {
    StepState.OK -> StatusTone.OK
    StepState.FAILED -> StatusTone.ERROR
    StepState.WAITING -> StatusTone.WARNING
    StepState.PENDING -> StatusTone.IDLE
}

private fun beadColor(state: StepState): Color = when (state) {
    StepState.OK -> Color.White
    StepState.FAILED -> Negative
    StepState.WAITING -> Warning
    StepState.PENDING -> Color.White.copy(alpha = PENDING_BEAD)
}

private fun sceneAccent(state: StepState): Color = when (state) {
    StepState.OK -> HEALTHY
    StepState.FAILED -> FAILING
    else -> WAITING
}

private fun reportFileName(): String =
    REPORT_PREFIX + SimpleDateFormat(REPORT_STAMP, Locale.US).format(Date()) + REPORT_EXTENSION

private val CardShape = RoundedCornerShape(28.dp)
private val InnerShape = RoundedCornerShape(22.dp)
private val PanelShape = RoundedCornerShape(26.dp)
private val HERO_HEIGHT = 236.dp
private val PANEL_BLUR = 14.dp
private val PANEL_LENS = 18.dp
private val PANEL_FILL = Color.Black.copy(alpha = 0.16f)
private const val RIM_ALPHA = 0.6f
private const val MUTED_WHITE = 0.78f
private const val PENDING_BEAD = 0.25f
private val HEALTHY = Color(0xFF34C759)
private val WAITING = Color(0xFFFFB020)
private val FAILING = Color(0xFFFF453A)
private const val SPIN_MS = 800
private const val MAX_SHOWN_ERRORS = 8
private const val ERROR_LINES = 4
private const val REPORT_MIME = "application/zip"
private const val REPORT_PREFIX = "glasslayer-report-"
private const val REPORT_STAMP = "yyyyMMdd-HHmmss"
private const val REPORT_EXTENSION = ".zip"
