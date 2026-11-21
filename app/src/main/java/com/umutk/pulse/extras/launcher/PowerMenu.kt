package com.umutk.pulse.extras.launcher

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.umutk.pulse.PButton
import com.umutk.pulse.Shell
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr

/**
 * The ways out of a Windows start menu: sleep and lock (the lock screen, through the accessibility service), shut down and restart (with root or Shizuku they really do it; without,
 * the phone's own power menu opens) and sign out (back to Pulse itself).
 */
internal fun powerAction(ctx: Context, what: String) {
    when (what) {
        // on a Windows desktop Lock shows its lock screen (only the launcher is locked); Sleep locks the phone itself
        "lock" -> if (LauncherPrefs.look in com.umutk.pulse.loginLooks) LauncherSession.lockOpen = true else globalAction(ctx, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        "sleep" -> globalAction(ctx, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        // the launcher is the computer here: Shut down closes it, Restart starts it again with its boot screens, Sign out shows the sign-in screen (the phone itself is not touched)
        "off" -> if (LauncherPrefs.look in com.umutk.pulse.loginLooks) LauncherSession.powerScreen = "off" else (ctx as? android.app.Activity)?.finishAndRemoveTask()
        "restart" -> if (LauncherPrefs.look in com.umutk.pulse.loginLooks) LauncherSession.powerScreen = "restart" else (ctx as? android.app.Activity)?.let { LauncherSession.forceBoot = 1; LauncherSession.booted = false; it.recreate() }
        "signout" -> (ctx as? android.app.Activity)?.let { LauncherSession.forceBoot = 2; LauncherSession.booted = false; it.recreate() } ?: logOff(ctx)
    }
}

/** The list of the ways out, drawn in the look of the launcher (a window of that Windows). */
@Composable
internal fun PowerDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    WinAlert(onDismissRequest = onDismiss,
        title = { Text(tr("Shut down", "Kapat")) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("sleep" to tr("Sleep", "Uyku"), "lock" to tr("Lock", "Kilitle"), "off" to tr("Shut down", "Kapat"), "restart" to tr("Restart", "Yeniden başlat"), "signout" to tr("Sign out", "Oturumu kapat"))
                    .forEach { (k, label) -> PButton(label, Modifier.fillMaxWidth()) { onDone(); powerAction(ctx, k) } }
            }
        },
        confirmButton = { PButton(tr("Cancel", "Vazgeç"), onClick = onDismiss) })
}

/** A power button of a start menu: a press shows all the ways out in a small panel right above the button (no dialog over the whole screen). */
@Composable
internal fun PowerClick(onDone: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    Box(modifier.clickable { open = true }, contentAlignment = Alignment.Center) {
        content()
        if (open) androidx.compose.ui.window.Popup(
            popupPositionProvider = object : androidx.compose.ui.window.PopupPositionProvider {
                override fun calculatePosition(anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: androidx.compose.ui.unit.IntSize, layoutDirection: androidx.compose.ui.unit.LayoutDirection, popupContentSize: androidx.compose.ui.unit.IntSize): androidx.compose.ui.unit.IntOffset {
                    val x = (anchorBounds.left).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                    val y = (anchorBounds.top - popupContentSize.height - 6).coerceAtLeast(0)
                    return androidx.compose.ui.unit.IntOffset(x, y)
                }
            },
            onDismissRequest = { open = false },
        ) {
            // the panel grows out of the button and fades in (it does not just appear); each way out is a row with its icon, as in Windows;
            // on Windows 11 the panel is see-through (Mica-like) with round corners, the older Windows stay opaque and square
            val t = com.umutk.pulse.T
            val w11 = t.id == "win11"
            val grow = remember { androidx.compose.animation.core.Animatable(0f) }
            androidx.compose.runtime.LaunchedEffect(Unit) { grow.animateTo(1f, androidx.compose.animation.core.tween(170, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
            val shape = androidx.compose.foundation.shape.RoundedCornerShape(if (w11) 8.dp else 0.dp)
            Column(Modifier.width(200.dp)
                .graphicsLayer { alpha = grow.value; scaleY = .85f + .15f * grow.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f) }
                .clip(shape).background(t.panel.copy(alpha = if (w11) .80f else 1f)).border(1.dp, t.sub.copy(alpha = if (w11) .25f else .4f), shape).padding(4.dp)) {
                listOf("sleep" to tr("Sleep", "Uyku"), "lock" to tr("Lock", "Kilitle"), "power" to tr("Shut down", "Kapat"), "restart" to tr("Restart", "Yeniden başlat"), "signout" to tr("Sign out", "Oturumu kapat"))
                    .forEach { (k, label) ->
                        Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(if (w11) 5.dp else 0.dp))
                            .clickable { open = false; onDone(); powerAction(ctx, if (k == "power") "off" else k) }.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            com.umutk.pulse.PIcon(if (k == "restart") "refresh" else k, t.text, 18.dp)
                            Text(label, color = t.text, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
            }
        }
    }
}
