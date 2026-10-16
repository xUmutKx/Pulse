package com.umutk.pulse

import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/** One UI (Samsung) and ColorOS (OPPO): own switch, dialog, rail item and page title instead of plain Material. */
val oneUi: Boolean get() = T.id == "oneui"
val colorOs: Boolean get() = T.id == "coloros"
val oem: Boolean get() = oneUi || colorOs

/** One UI: pill with a white thumb; ColorOS: narrower capsule, thumb shrinks to a dot when off. */
@Composable
fun OemSwitch(on: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean) {
    if (colorOs) { ColorOsSwitch(on, onChange, enabled); return }
    val acc = Look.accent.copy(alpha = 1f)
    val one = oneUi
    val w = if (one) 52.dp else 44.dp; val h = if (one) 30.dp else 26.dp
    val thumb = if (one) 24.dp else if (on) 20.dp else 14.dp
    val off = if (T.dark) Color(0xFF48484A) else Color(0xFFCFCFD4)
    Box(Modifier.size(w, h).alpha2(enabled).background(if (on) acc else off, CircleShape)
        .then(if (enabled) Modifier.clickable { onChange(!on) } else Modifier)) {
        Box(Modifier.align(if (on) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = if (one) 3.dp else 4.dp).size(thumb).background(Color.White, CircleShape))
    }
}

private fun Modifier.alpha2(enabled: Boolean) = if (enabled) this else this.alpha(.45f)

/** True inside OemDialog: its button boxes already draw the pill, so a PButton there stays bare. */
val InOemDialog = androidx.compose.runtime.compositionLocalOf { false }

/** A rail entry: One UI a wide rounded pill around the icon, ColorOS a round solid accent disc; the label sits under it. */
@Composable
fun OemRailItem(icon: String, label: String, on: Boolean, onClick: () -> Unit) {
    if (colorOs) { ColorOsNavItem(icon, label, on, onClick); return }
    val t = T
    val acc = Look.accent.copy(alpha = 1f)
    Column(Modifier.padding(vertical = 4.dp).width(76.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        if (oneUi) Box(Modifier.size(60.dp, 34.dp).background(if (on) acc.copy(alpha = .2f) else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) { PNavIcon(icon, if (on) acc else t.sub, 22.dp) }
        else Box(Modifier.size(42.dp).background(if (on) acc else t.text.copy(alpha = .06f), CircleShape), contentAlignment = Alignment.Center) { PNavIcon(icon, if (on) onAccent(acc) else t.sub, 22.dp) }
        Text(label, color = if (on) (if (oneUi) acc else t.text) else t.sub, fontSize = 11.sp, maxLines = 1, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** Page title: One UI a big airy header with a lot of room above, ColorOS a bold left-aligned one. */
@Composable
fun OemTitle(text: String, trailing: @Composable () -> Unit = {}) {
    if (colorOs) { ColorOsTitle(text, trailing); return }
    val t = T
    if (oneUi) Column(Modifier.fillMaxWidth().padding(top = 44.dp, bottom = 14.dp, start = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(text, color = t.text, fontSize = 34.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)); trailing() }
    } else Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = t.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); trailing()
    }
}

/** The pop-up of One UI (a rounded card pinned to the bottom, side-by-side pill buttons) and ColorOS (a centred soft card, capsule buttons). */
@Composable
fun OemDialog(w: PTheme, onDismiss: () -> Unit, title: (@Composable () -> Unit)?, text: (@Composable () -> Unit)?, confirm: @Composable () -> Unit, dismissBtn: (@Composable () -> Unit)?, containerColor: Color?) {
    val one = w.id == "oneui"
    if (!one) { ColorOsDialog(w, onDismiss, title, text, confirm, dismissBtn, containerColor); return }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        if (one) SideEffect { (view.parent as? DialogWindowProvider)?.window?.setGravity(Gravity.BOTTOM) }
        val acc = Look.accent.copy(alpha = 1f)
        val shape = RoundedCornerShape(if (one) 32.dp else 22.dp)
        Column(Modifier.padding(if (one) 12.dp else 24.dp).widthIn(max = 480.dp).fillMaxWidth()
            .background(containerColor ?: w.panel, shape).then(if (one) Modifier else Modifier.border(1.dp, w.line, shape))
            .padding(horizontal = 24.dp, vertical = 26.dp), horizontalAlignment = if (one) Alignment.CenterHorizontally else Alignment.Start) {
            if (title != null) CompositionLocalProvider(LocalContentColor provides w.text) { androidx.compose.material3.ProvideTextStyle(TextStyle(color = w.text, fontSize = if (one) 22.sp else 20.sp, fontWeight = if (one) FontWeight.Medium else FontWeight.Bold)) { title() } }
            if (text != null) { Spacer(Modifier.height(12.dp)); CompositionLocalProvider(LocalContentColor provides w.sub) { androidx.compose.material3.ProvideTextStyle(TextStyle(color = w.sub, fontSize = 15.sp)) { text() } } }
            Spacer(Modifier.height(24.dp))
            CompositionLocalProvider(InOemDialog provides true) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (dismissBtn != null) Box(Modifier.weight(1f).heightIn(min = 48.dp).background(w.text.copy(alpha = .07f), CircleShape), contentAlignment = Alignment.Center) { dismissBtn() }
                Box(Modifier.weight(1f).heightIn(min = 48.dp).background(if (one) acc.copy(alpha = .16f) else acc, CircleShape), contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(LocalContentColor provides if (one) acc else onAccent(acc)) { confirm() }
                }
            } }
        }
    }
}
