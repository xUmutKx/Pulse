package com.umutk.pulse.extras.launcher

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
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
        "sleep", "lock" -> globalAction(ctx, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        "off" -> if (Shell.root == true || Shell.shizukuOk()) Thread { Shell.run("reboot -p") }.start() else com.umutk.pulse.PowerMenu.show()
        "restart" -> if (Shell.root == true || Shell.shizukuOk()) Thread { Shell.run("reboot") }.start() else com.umutk.pulse.PowerMenu.show()
        "signout" -> logOff(ctx)
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
            Column(Modifier.width(190.dp).background(com.umutk.pulse.T.panel.copy(alpha = 1f)).border(1.dp, com.umutk.pulse.T.sub.copy(alpha = .4f)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("sleep" to tr("Sleep", "Uyku"), "lock" to tr("Lock", "Kilitle"), "off" to tr("Shut down", "Kapat"), "restart" to tr("Restart", "Yeniden başlat"), "signout" to tr("Sign out", "Oturumu kapat"))
                    .forEach { (k, label) -> PButton(label, Modifier.fillMaxWidth()) { open = false; onDone(); powerAction(ctx, k) } }
            }
        }
    }
}
