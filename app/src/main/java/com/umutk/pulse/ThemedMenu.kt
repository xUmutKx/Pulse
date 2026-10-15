package com.umutk.pulse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * How a pop-up menu is drawn in one look: its panel, text and icon colours, corners, edge and the size of a row.
 * Each look builds its own in its own file: MenuStyle95.kt, MenuStyleXp.kt, MenuStyle7.kt, MenuStyle10.kt, MenuStyle11.kt, MenuStyleMaterial.kt.
 */
class MenuStyle(
    val bg: Color,
    val ink: Color,
    val sub: Color,
    val shape: Shape,
    /** Extra modifier on the menu panel, for example the 95 bevel. Applied after the border. */
    val edge: Modifier = Modifier,
    val border: BorderStroke? = null,
    val rowV: Dp = 8.dp,
    val rowH: Dp = 12.dp,
    val font: TextUnit = 14.sp,
    val iconSize: Dp = 18.dp,
    /** Smallest height of a row; the classic looks are far tighter than Material's 48 dp. */
    val rowMin: Dp = 48.dp,
    /** Background and text of the row under the finger; Unspecified keeps the plain row. */
    val hiBg: Color = Color.Unspecified,
    val hiInk: Color = Color.Unspecified,
)

val LocalMenuStyle = compositionLocalOf { menuStyleMaterial(Themes[0]) }

/** The menu style of a look; the Material and AMOLED windows (and anything unknown) take the Material one. */
fun menuStyle(w: PTheme): MenuStyle = if (!Opt.oldPopups && w.id != "material" && w.id != "amoled") menuStyleCurrent(w) else when (w.id) {
    "win95" -> menuStyle95(w)
    "xp" -> menuStyleXp(w)
    "win7" -> menuStyle7(w)
    "win10" -> menuStyle10(w)
    "win11" -> menuStyle11(w)
    else -> menuStyleMaterial(w)
}

/** "New designs" in Settings: the menu as Pulse drew it before the classic looks, one rounded tonal panel for every Windows look. */
private fun menuStyleCurrent(w: PTheme) = MenuStyle(bg = w.panel, ink = w.text, sub = w.sub, shape = RoundedCornerShape(w.radius), border = BorderStroke(1.dp, w.line), rowV = 8.dp, rowH = 12.dp, font = 14.sp, iconSize = 18.dp)

/** A drop-down menu in the look of the pop-up windows (LocalWindowLook): every look has its own panel, edge and rows. */
@Composable
fun ThemedMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val s = menuStyle(LocalWindowLook.current)
    CompositionLocalProvider(LocalMenuStyle provides s) {
        DropdownMenu(
            expanded, onDismiss,
            modifier = (if (s.border != null) Modifier.border(s.border, s.shape) else Modifier).then(s.edge),
            shape = s.shape, containerColor = s.bg, tonalElevation = 0.dp, content = content,
        )
    }
}

/**
 * One row of a [ThemedMenu]. The icon comes from [leading], else from the look's icon pack by [iconKey] (95 / XP / 7 icon sets, Fluent, Material Symbols), else [icon].
 * [checked] shows a tick in front: null = no tick column at all.
 */
@Composable
fun ThemedMenuItem(label: String, onClick: () -> Unit, iconKey: String? = null, icon: ImageVector? = null, checked: Boolean? = null, leading: (@Composable () -> Unit)? = null) {
    val s = LocalMenuStyle.current
    val press = remember { MutableInteractionSource() }
    val down by press.collectIsPressedAsState()
    val lit = down && s.hiBg != Color.Unspecified
    val ink = if (lit && s.hiInk != Color.Unspecified) s.hiInk else s.ink
    // a plain row, not Material's DropdownMenuItem, so the classic looks can be as tight as the real menus
    Row(
        Modifier.fillMaxWidth().heightIn(min = s.rowMin).background(if (lit) s.hiBg else Color.Transparent)
            .clickable(interactionSource = press, indication = if (s.hiBg == Color.Unspecified) LocalIndication.current else null, onClick = onClick)
            .padding(horizontal = s.rowH, vertical = s.rowV),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        when {
            leading != null -> leading()
            iconKey != null -> PIcon(iconKey, if (lit) ink else s.sub, s.iconSize)
            icon != null -> Icon(icon, null, Modifier.size(s.iconSize), tint = if (lit) ink else s.sub)
        }
        if (leading != null || iconKey != null || icon != null) Spacer(Modifier.width(10.dp))
        if (checked != null) androidx.compose.foundation.layout.Box(Modifier.width(18.dp)) { if (checked) Text("\u2713", color = if (lit) ink else Look.accent, fontSize = s.font) }
        Text(label, color = ink, fontSize = s.font, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The icon beside the text of a message box, in the look's own drawing: [warn] is the warning glyph, otherwise the information one. */
@Composable
fun ThemeMsgIcon(id: String, warn: Boolean) = if (!Opt.oldPopups) MsgIconDefault(warn) else when (id) {
    "win95" -> MsgIcon95(warn)
    "xp" -> MsgIconXp(warn)
    "win7" -> MsgIcon7(warn)
    "win10" -> MsgIcon10(warn)
    "win11" -> MsgIcon11(warn)
    else -> MsgIconDefault(warn)
}
