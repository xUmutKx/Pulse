package com.umutk.pulse.extras.ytx

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.extras.ytx.glass.Backdrop
import kotlin.math.abs

/** YouTube's exact speed ladder, now including the 2.25x and 2.5x presets. */
private val YouTubeSpeeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.25f, 2.5f)

@Composable
fun SpeedSheet(state: PlayerState, backdrop: Backdrop, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    // any speed between the ends (the slider gives 1.17 as it is); the buttons and the chips go by their own steps
    var value by remember { mutableFloatStateOf(PlaybackSpeed.free(state.speed).coerceIn(0.25f, 2.5f)) }
    LaunchedEffect(state.speed) { value = PlaybackSpeed.free(state.speed).coerceIn(0.25f, 2.5f) }

    fun apply(next: Float) {
        value = PlaybackSpeed.free(next.coerceIn(0.25f, 2.5f))
        state.commitSpeed(value)
    }

    PlayerGlassSheet("Playback speed", backdrop, onDismiss) {
        // the speed in the middle; "Remember" is a small button at the top right (it was a whole row)
        Box(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Speed, null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
                Text(PlaybackSpeed.label(value) + "×", fontFamily = RoundedHeavy, fontWeight = FontWeight.Black, fontSize = 30.sp)
            }
            val on = PlaybackSpeed.remember
            Row(
                Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(50))
                    .background(if (on) c.primary else c.surfaceContainerHighest)
                    .pressable { state.toggleRememberSpeed() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (on) { Icon(androidx.compose.material.icons.Icons.Rounded.Check, null, tint = c.onPrimary, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)) }
                Text("Remember", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (on) c.onPrimary else c.onSurface)
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SpeedStepButton(Icons.Rounded.Remove, "Slower") { apply(value - 0.25f) }
            Slider(
                value = value,
                // free: 1.17 is a speed; the video follows the slider while it moves
                onValueChange = { value = PlaybackSpeed.free(it.coerceIn(0.25f, 2.5f)); state.previewSpeed(value) },
                onValueChangeFinished = { state.commitSpeed(value) },
                valueRange = 0.25f..2.5f,
                colors = SliderDefaults.colors(
                    thumbColor = c.onSurface,
                    activeTrackColor = c.onSurface,
                    inactiveTrackColor = c.surfaceContainerHighest,
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            SpeedStepButton(Icons.Rounded.Add, "Faster") { apply(value + 0.25f) }
        }

        Spacer(Modifier.height(10.dp))
        // one row, 0.25 at the left end and 2.5 at the right end; on a narrow screen the row scrolls
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fits = maxWidth / YouTubeSpeeds.size >= 54.dp
            val chips: @Composable (Boolean) -> Unit = { fill ->
                YouTubeSpeeds.forEach { speed ->
                    SpeedChip(
                        label = PlaybackSpeed.label(speed) + "×",
                        selected = abs(value - speed) < 0.001f,
                        modifier = if (fill) Modifier.weight(1f) else Modifier.width(58.dp),
                    ) { apply(speed) }
                }
            }
            if (fits) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { chips(true) }
            else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) { chips(false) }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SpeedStepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(40.dp)
            .pressable(pressedScale = 0.9f, onClick = onClick)
            .clip(CircleShape)
            .background(c.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = c.onSurface, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun AudioTrackSheet(state: PlayerState, backdrop: Backdrop, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { state.refreshTracks() }
    val activeKey = state.audioOptions.firstOrNull { it.key == AudioTrackPreference.key }?.key
    PlayerGlassSheet("Audio track", backdrop, onDismiss) {
        Text(
            "Original audio is the default for every show, so dubs never take over.",
            color = c.onSurfaceVariant,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
        )

        TrackRow(
            title = "Original",
            detail = "The creator's own audio track",
            selected = activeKey == null,
            icon = Icons.Rounded.Headphones,
        ) {
            state.setAudioTrack(null)
            onDismiss()
        }

        state.audioOptions.forEach { option ->
            val selected = activeKey != null && activeKey == option.key
            TrackRow(
                title = option.label,
                detail = option.detail,
                selected = selected,
                icon = null,
            ) {
                state.setAudioTrack(option)
                onDismiss()
            }
        }

        if (state.audioOptions.isEmpty()) {
            Text(
                "No alternate audio tracks for this video.",
                color = c.onSurfaceVariant,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp),
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(c.surfaceContainerHigh.copy(alpha = 0.5f))
                .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Subtitles, null, tint = c.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Captions", fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        state.selectedSubtitleLanguage != null -> "Showing captions"
                        else -> "Turn on captions for this video"
                    },
                    color = c.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Switch(
                checked = state.selectedSubtitleLanguage != null,
                onCheckedChange = { state.setCaptions(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = c.onPrimary,
                    checkedTrackColor = c.primary,
                    uncheckedThumbColor = c.onSurfaceVariant,
                    uncheckedTrackColor = c.surfaceContainerHighest,
                ),
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SpeedChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    caption: String? = null,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        targetValue = if (selected) c.secondaryContainer else c.surfaceContainerHigh.copy(alpha = 0.55f),
        animationSpec = tween(180),
        label = "speedChipFill",
    )
    Column(modifier.pressable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (selected) c.onSecondaryContainer else c.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                maxLines = 1,
            )
        }
        if (caption != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                caption,
                color = c.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TrackRow(
    title: String,
    detail: String?,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) c.primary.copy(alpha = 0.14f) else c.surfaceContainerHigh.copy(alpha = 0.45f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) c.primary else c.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    detail,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(c.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, null, tint = c.onPrimary, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun PlayerGlassSheet(
    title: String,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = MaterialTheme.colorScheme
    BackHandler(onBack = onDismiss)
    val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val spec = glassSpec(
        shape = shape,
        tint = glassFill(0.62f),
        blur = 26.dp,
        refractionHeight = 18.dp,
        refractionAmount = 24.dp,
    )
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.46f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .liquidGlass(backdrop, spec)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(c.onSurfaceVariant.copy(alpha = 0.35f)),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f),
                )
                CircleAction(Icons.Rounded.Close, "Close", onDismiss)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                content = content,
            )
        }
    }
}
