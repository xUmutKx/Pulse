package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.PButton
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.frame
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Time and date at the top of the home screen; ticks every second so the minute is never late. */
@Composable
fun ClockHeader(modifier: Modifier) {
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(1000); value = System.currentTimeMillis() } }
    val d = Date(now)
    val edge = LocalLauncherEdge.current
    Column(modifier.padding(horizontal = maxOf(20.dp, edge), vertical = 12.dp)) {
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(d), color = T.text, fontSize = 44.sp, fontWeight = FontWeight.Light)
        Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(d), color = T.text, fontSize = 14.sp)
    }
}

/** One dot per home page; the current one is larger and stronger. */
@Composable
fun PageDots(current: Int, count: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val on = i == current
            Box(Modifier.size(if (on) 7.dp else 5.dp).clip(CircleShape).background(T.text.copy(alpha = if (on) .9f else .4f)))
        }
    }
}

/** A round icon button over the home screen (settings, the apps list when the dock is hidden). */
@Composable
fun HomeChip(icon: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(T.panel.copy(alpha = .7f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        PIcon(icon, T.text, 22.dp)
    }
}

/** Long press on empty home space opens the home sheet; the grid below still gets its taps and drags. */
fun Modifier.homeLongPress(onLong: () -> Unit): Modifier = composed {
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(onLong) {
            detectTapGestures(
                onLongPress = { if (LauncherPrefs.haptic) view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); LauncherSession.pressAt = origin + it; onLong() },
                onDoubleTap = { if (LauncherPrefs.gestureLock) globalAction(ctx, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) },
            )
        }
        .pointerInput(Unit) {
            // a long swipe on the home: down opens the notification shade, up opens the apps list (each is a setting)
            var total = 0f
            detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = {
                if (total > 140f && LauncherPrefs.swipeDownShade) globalAction(ctx, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                else if (total > 140f && LauncherPrefs.swipeDownSearch) LauncherSession.searchOpen = true
                else if (total < -140f && LauncherPrefs.swipeUpApps) LauncherSession.startOpen = true
            }) { _, d -> total += d }
        }
}

/** Asks Pulse's accessibility service (it lives in the main process, the launcher is a process of its own) to do a system action: lock the screen, open the shade. */
fun globalAction(ctx: Context, action: Int) {
    ctx.sendBroadcast(Intent("com.umutk.pulse.action.GLOBAL").setPackage(ctx.packageName).putExtra("g", action))
}

/** A small menu beside the finger, like a right-click menu: to the right of the press, or to its left when there is no room. */
@Composable
fun ContextPopup(menuW: androidx.compose.ui.unit.Dp, menuH: androidx.compose.ui.unit.Dp, onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val w = LocalWindowLook.current
    val d = androidx.compose.ui.platform.LocalDensity.current.density
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val at = LauncherSession.pressAt
    val sw = cfg.screenWidthDp * d; val sh = cfg.screenHeightDp * d
    val mw = menuW.value * d; val mh = menuH.value * d; val gap = 10f * d
    val x = (if (at.x + gap + mw <= sw) at.x + gap else at.x - gap - mw).coerceIn(0f, (sw - mw).coerceAtLeast(0f))
    val y = (at.y - mh / 3f).coerceIn(0f, (sh - mh).coerceAtLeast(0f))
    androidx.compose.ui.window.Popup(
        alignment = Alignment.TopStart, offset = androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt()), onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
        Column(Modifier.width(menuW).shadow(8.dp).background(w.panel).border(1.dp, w.line).padding(horizontal = 14.dp, vertical = 6.dp), content = content)
    }
}

/** Search field at the top of the apps list; it filters the names, nothing else is read. */
@Composable
fun DrawerSearch(query: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(40.dp).background(T.panel).frame(T).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        PIcon("search", T.sub, 20.dp)
        Spacer(Modifier.width(8.dp))
        val fr = remember { androidx.compose.ui.focus.FocusRequester() }
        androidx.compose.runtime.LaunchedEffect(Unit) { if (LauncherPrefs.searchFocus) runCatching { fr.requestFocus() } }
        BasicTextField(query, onChange, singleLine = true, textStyle = TextStyle(color = T.text, fontSize = 15.sp),
            cursorBrush = SolidColor(Look.accent), modifier = Modifier.weight(1f).focusRequester(fr),
            decorationBox = { inner ->
                if (query.isEmpty()) Text(tr("Search apps", "Uygulama ara"), color = T.sub, fontSize = 15.sp)
                inner()
            })
    }
}

/** Dock pin list: the pinned apps, or the automatic first three when nothing is pinned. Max 5 pinned. */
fun togglePin(ctx: Context, pkg: String, auto: List<String>) {
    val base = LauncherPrefs.dock.ifEmpty { auto }.toMutableList()
    if (pkg in base) base.remove(pkg) else if (base.size < 5) base.add(pkg)
    LauncherPrefs.dock = base
    LauncherPrefs.save(ctx)
}

/** The menu of one app (long press): info, uninstall for user apps, hide, pin to the dock. A small popup at the finger, like a right-click menu. */
@Composable
fun AppActionDialog(app: LaunchApp, auto: List<String>, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val w = LocalWindowLook.current
    val pinned = app.pkg in LauncherPrefs.dock
    fun act(f: () -> Unit) { f(); onDismiss() }
    val tiles = LauncherPrefs.look == "winphone" && LauncherPrefs.phoneTiles
    if (isFolder(app)) { ContextPopup(230.dp, 250.dp, onDismiss) { FolderActionRows(app, onDismiss) }; return }
    val inFolder = LauncherFolders.of(app.pkg) != null
    ContextPopup(230.dp, if (tiles) 380.dp else 250.dp, onDismiss) {
        Text(app.label, color = w.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 6.dp))
        // Windows Phone: the size of this app's tile
        if (tiles) listOf("s" to tr("Small tile", "Küçük kutucuk"), "m" to tr("Medium tile", "Orta kutucuk"), "w" to tr("Wide tile", "Geniş kutucuk")).forEach { (k, n) ->
            ActionRow("gridView", n, w.text) { act { LauncherPrefs.tileSizes = LauncherPrefs.tileSizes + (app.pkg to k); LauncherPrefs.save(ctx) } }
        }
        ActionRow("info", tr("App info", "Uygulama bilgisi"), w.text) {
            act { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.pkg, null))) }
        }
        if (!app.system) ActionRow("trash", tr("Uninstall", "Kaldır"), w.text) {
            act { ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.pkg, null))) }
        }
        ActionRow("eyeOff", tr("Hide from launcher", "Ana ekranda gizle"), w.text) {
            act { LauncherPrefs.hidden = LauncherPrefs.hidden + app.pkg; LauncherPrefs.save(ctx) }
        }
        ActionRow("folder", tr("Add to folder", "Klasöre ekle"), w.text) { act { FolderUi.pick = app } }
        if (inFolder) ActionRow("close", tr("Remove from folder", "Klasörden çıkar"), w.text) { act { LauncherFolders.remove(ctx, app.pkg) } }
        ActionRow("pin", if (pinned) tr("Unpin from dock", "Dock'tan çıkar") else tr("Pin to dock", "Dock'a sabitle"), w.text) {
            act { togglePin(ctx, app.pkg, auto) }
        }
    }
}

@Composable
internal fun ActionRow(icon: String, label: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        PIcon(icon, tint, 22.dp)
        Spacer(Modifier.width(12.dp))
        Text(label, color = tint, fontSize = 15.sp)
    }
}
