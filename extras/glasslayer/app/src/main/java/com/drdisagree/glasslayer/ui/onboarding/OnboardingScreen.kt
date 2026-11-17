package com.drdisagree.glasslayer.ui.onboarding

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drdisagree.glasslayer.R
import com.drdisagree.glasslayer.domain.model.FrameworkInfo
import com.drdisagree.glasslayer.ui.components.BackdropScreen
import com.drdisagree.glasslayer.ui.components.GlassContainer
import com.drdisagree.glasslayer.ui.components.LocalBackdrop
import com.drdisagree.glasslayer.ui.components.PillButton
import com.drdisagree.glasslayer.ui.components.StatusGlyph
import com.drdisagree.glasslayer.ui.components.StatusTone
import com.drdisagree.glasslayer.ui.components.liquid.LiquidButton
import com.drdisagree.glasslayer.ui.settings.RowDivider
import com.drdisagree.glasslayer.ui.theme.Accent
import com.drdisagree.glasslayer.ui.theme.GlassLayerTheme
import com.drdisagree.glasslayer.ui.theme.Ink
import com.drdisagree.glasslayer.ui.theme.Negative
import com.drdisagree.glasslayer.ui.theme.OnInk
import com.drdisagree.glasslayer.ui.theme.OnInkFaint
import com.drdisagree.glasslayer.ui.theme.OnInkMuted
import com.drdisagree.glasslayer.ui.theme.onTint

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, onDone: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    OnboardingContent(
        state = state,
        onGrantRoot = viewModel::onGrantRoot,
        onRestart = viewModel::onRestart,
        onContinue = {
            viewModel.onContinue()
            onDone()
        }
    )
}

@Composable
internal fun OnboardingContent(
    state: OnboardingUiState,
    onGrantRoot: () -> Unit,
    onRestart: () -> Unit,
    onContinue: () -> Unit
) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    BackdropScreen(
        content = {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = navBottom + BUTTON_AREA)
            ) {
                OnboardingHero()
                Text(
                    stringResource(R.string.onboarding_welcome),
                    style = MaterialTheme.typography.displaySmall,
                    color = OnInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
                Text(
                    stringResource(R.string.onboarding_summary),
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnInkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp, end = 32.dp, top = 8.dp)
                )
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SetupCard(state, onGrantRoot, onRestart)
                    Text(
                        stringResource(R.string.onboarding_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnInkFaint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    )
                }
            }
        },
        overlay = {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = SCRIM_ALPHA))))
                    .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = navBottom + 16.dp)
            ) {
                LiquidButton(
                    onClick = onContinue,
                    backdrop = LocalBackdrop.current,
                    modifier = Modifier.fillMaxWidth(),
                    tint = Accent,
                    height = 54.dp
                ) {
                    AnimatedContent(
                        targetState = state.isReady,
                        transitionSpec = { fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_MS)) },
                        label = "continueLabel"
                    ) { ready ->
                        Text(
                            stringResource(if (ready) R.string.onboarding_start else R.string.onboarding_skip),
                            style = MaterialTheme.typography.titleSmall,
                            color = onTint(Accent)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun SetupCard(state: OnboardingUiState, onGrantRoot: () -> Unit, onRestart: () -> Unit) {
    val framework = state.framework
    GlassContainer(CardShape, Modifier.fillMaxWidth()) {
        SetupRow(
            stringResource(R.string.onboarding_framework),
            framework?.let { stringResource(R.string.framework_label, it.name, it.version.substringBefore(' ')) }
                ?: stringResource(R.string.onboarding_framework_missing),
            if (framework != null) StatusTone.OK else StatusTone.ERROR
        )
        RowDivider()
        SetupRow(
            stringResource(R.string.status_scope),
            stringResource(if (state.isScoped) R.string.status_scope_ok else R.string.onboarding_scope_missing),
            when {
                framework == null -> StatusTone.IDLE
                state.isScoped -> StatusTone.OK
                else -> StatusTone.ERROR
            }
        )
        RowDivider()
        SetupRow(
            stringResource(R.string.onboarding_root),
            stringResource(
                when (state.root) {
                    RootState.GRANTED -> R.string.onboarding_root_granted
                    RootState.DENIED -> R.string.onboarding_root_denied
                    RootState.CHECKING -> R.string.onboarding_root_checking
                    RootState.UNKNOWN -> R.string.onboarding_root_summary
                }
            ),
            when (state.root) {
                RootState.GRANTED -> StatusTone.OK
                RootState.DENIED -> StatusTone.WARNING
                RootState.CHECKING -> StatusTone.BUSY
                RootState.UNKNOWN -> StatusTone.IDLE
            }
        ) {
            if (state.root != RootState.GRANTED) {
                PillButton(
                    stringResource(R.string.action_grant),
                    Accent,
                    onGrantRoot,
                    spinning = state.root == RootState.CHECKING,
                    enabled = state.root != RootState.CHECKING
                )
            }
        }
        RowDivider()
        SetupRow(
            stringResource(R.string.status_step_systemui),
            stringResource(if (state.isLoaded) R.string.onboarding_loaded else R.string.status_no_report_summary),
            when {
                !state.isScoped -> StatusTone.IDLE
                state.isLoaded -> StatusTone.OK
                else -> StatusTone.WARNING
            }
        ) {
            if (state.isScoped && !state.isLoaded) {
                PillButton(
                    stringResource(if (state.restarting) R.string.action_restarting else R.string.action_restart),
                    Negative,
                    onRestart,
                    spinning = state.restarting,
                    enabled = !state.restarting
                )
            }
        }
    }
}

@Composable
private fun SetupRow(title: String, value: String, tone: StatusTone, action: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusGlyph(tone, size = 18.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (tone == StatusTone.IDLE) OnInkMuted else OnInk)
            AnimatedContent(
                targetState = value,
                transitionSpec = { fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_OUT_MS)) },
                label = "setupValue"
            ) { text ->
                Text(text, style = MaterialTheme.typography.bodySmall, color = if (tone == StatusTone.IDLE) OnInkFaint else OnInkMuted)
            }
        }
        Spacer(Modifier.width(12.dp))
        action()
    }
}

@Preview(showSystemUi = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun OnboardingPreview() {
    GlassLayerTheme(dark = true) {
        OnboardingContent(
            state = OnboardingUiState(
                framework = FrameworkInfo("Vector", "1.10", 102, listOf("com.android.systemui")),
                root = RootState.UNKNOWN
            ),
            onGrantRoot = {},
            onRestart = {},
            onContinue = {}
        )
    }
}

private val CardShape = RoundedCornerShape(28.dp)
private val BUTTON_AREA = 98.dp
private const val SCRIM_ALPHA = 0.85f
private const val FADE_MS = 240
private const val FADE_OUT_MS = 140
