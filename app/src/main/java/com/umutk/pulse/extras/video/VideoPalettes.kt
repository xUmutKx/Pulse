package com.umutk.pulse.extras.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** name, background, text, secondary text, accent; shared by the generated player skins and the widget looks. */
class VPal(val name: String, val bg: Long, val fg: Long, val sub: Long, val accent: Long, val light: Boolean = false)

val videoPalettes = listOf(
    VPal("Ocean", 0xFF06202E, 0xFFE3F4FB, 0xFF86B5C9, 0xFF2EC4F1), VPal("Forest", 0xFF0C1F14, 0xFFE4F2E7, 0xFF8DB59A, 0xFF5CD67F),
    VPal("Sunset", 0xFF2A1210, 0xFFFBE9E0, 0xFFCE9C8A, 0xFFFF7A45), VPal("Grape", 0xFF1D1030, 0xFFEEE5FA, 0xFFAD9BC9, 0xFFB57CFF),
    VPal("Rose", 0xFF2C0F1B, 0xFFFBE4EC, 0xFFC993A8, 0xFFFF5C93), VPal("Mint", 0xFF0A2420, 0xFFDFF7F1, 0xFF86BDB2, 0xFF3DDBB5),
    VPal("Amber", 0xFF2A1E05, 0xFFFBF0D6, 0xFFC9B27A, 0xFFFFB800), VPal("Slate", 0xFF1A1F26, 0xFFE4E8EE, 0xFF9AA4B2, 0xFF7C9CBF),
    VPal("Crimson", 0xFF2B0A0C, 0xFFFAE3E4, 0xFFC98C90, 0xFFE5333D), VPal("Lime", 0xFF16200A, 0xFFEEF8DC, 0xFFA5BA80, 0xFFB5E61D),
    VPal("Indigo", 0xFF0F1233, 0xFFE6E8FB, 0xFF9A9FD1, 0xFF6C7BFF), VPal("Petrol", 0xFF0F2A33, 0xFFE5F3F6, 0xFF8FB4BD, 0xFFFFB74D),
    VPal("Coffee", 0xFF241812, 0xFFF3E7DE, 0xFFB89C88, 0xFFD08A55), VPal("Graphite", 0xFF121212, 0xFFE8E8E8, 0xFF9E9E9E, 0xFFBDBDBD),
    VPal("Gold", 0xFF1C1708, 0xFFF8F0D0, 0xFFBDAE6E, 0xFFE6C84A), VPal("Cherry", 0xFF2A0716, 0xFFFAE0EA, 0xFFC4869E, 0xFFD6246E),
    VPal("Teal Light", 0xFFE3F4F2, 0xFF0E3B3A, 0xFF4A7270, 0xFF00897B, true), VPal("Paper", 0xFFF5F1E8, 0xFF2B2619, 0xFF6B6449, 0xFFB5651D, true),
    VPal("Lavender", 0xFFF1ECFA, 0xFF2A1E48, 0xFF6A5D8F, 0xFF7E57C2, true), VPal("Cloud", 0xFFD9DDE3, 0xFF1E2024, 0xFF595E66, 0xFF5B6BC0, true),
    VPal("Sage", 0xFFEAF1E6, 0xFF1F3322, 0xFF56705B, 0xFF3F8F4F, true), VPal("Sky", 0xFFE6F1FC, 0xFF0F2C4C, 0xFF4C6F93, 0xFF1E88E5, true),
    VPal("Neon Pink", 0xFF0B0010, 0xFFFFE0F7, 0xFFB580A8, 0xFFFF2BD6), VPal("Rose Light", 0xFFFBE9EE, 0xFF3A1020, 0xFF7A4A5C, 0xFFD81B60, true),
    VPal("Neon Blue", 0xFF020818, 0xFFD8E8FF, 0xFF6F8FBF, 0xFF1F51FF), VPal("Cyber", 0xFF0D0221, 0xFFE0F9FF, 0xFF8AA8C0, 0xFF00F0FF),
    VPal("Ember", 0xFF1F0A00, 0xFFFFE6D1, 0xFFBF8A62, 0xFFFF4D00), VPal("Midnight", 0xFF000814, 0xFFDDE7F5, 0xFF7C8CA6, 0xFF3A86FF),
    VPal("Plum", 0xFF210A2B, 0xFFF3DCF9, 0xFFB587C2, 0xFFCE4FE8), VPal("Storm", 0xFF20252B, 0xFFE6E9ED, 0xFF9AA3AD, 0xFFFF6B6B),
    VPal("Olive", 0xFF1A1C0A, 0xFFEEF0D4, 0xFFABB070, 0xFF9AA63A), VPal("Copper", 0xFF241208, 0xFFF8E3D2, 0xFFBA8A68, 0xFFC96F2D),
    VPal("Ice", 0xFF0A1A24, 0xFFE9FAFF, 0xFF8AB4C4, 0xFF9BE7FF), VPal("Blood Moon", 0xFF140000, 0xFFFFD9D9, 0xFFB57070, 0xFFFF1A1A),
    VPal("Jade", 0xFF04201A, 0xFFD9F7EC, 0xFF77B8A0, 0xFF00C98D), VPal("Dusk", 0xFF1A1426, 0xFFE9E3F5, 0xFFA096B8, 0xFFFF9E80),
    VPal("Cream", 0xFFFFF8E7, 0xFF3A2E10, 0xFF7A6A42, 0xFFC2185B, true), VPal("Snow", 0xFFFFFFFF, 0xFF1C1C1C, 0xFF6B6B6B, 0xFF2962FF, true),
    VPal("Mono", 0xFF000000, 0xFFFFFFFF, 0xFF8C8C8C, 0xFFFFFFFF), VPal("Terminal", 0xFF000000, 0xFF33FF33, 0xFF1F991F, 0xFF33FF33),
)

/** One tiny picture of a look (bar, round button, seek line): used by the skin gallery and the widget picker. */
@Composable
fun PalSwatch(p: VPal, round: Int, modifier: Modifier = Modifier) {
    val r = round.dp
    Column(modifier.background(Color(p.bg), RoundedCornerShape(r)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.fillMaxWidth().height(5.dp).background(Color(p.sub).copy(alpha = .5f), CircleShape))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(Color(p.fg).copy(alpha = .35f), CircleShape))
            Box(Modifier.padding(horizontal = 6.dp).size(18.dp).background(Color(p.accent), CircleShape))
            Box(Modifier.size(12.dp).background(Color(p.fg).copy(alpha = .35f), CircleShape))
        }
        Row(Modifier.fillMaxWidth().height(4.dp)) {
            Box(Modifier.weight(.4f).height(4.dp).background(Color(p.accent), CircleShape))
            Box(Modifier.weight(.6f).height(4.dp).background(Color(p.fg).copy(alpha = .2f), CircleShape))
        }
    }
}

/** 50 home-screen widget looks: 25 palettes x (rounded | square) corners. */
class WidgetLook(val name: String, val pal: VPal, val square: Boolean)
val VideoWidgetLooks: List<WidgetLook> = (0 until 50).map { i ->
    val p = videoPalettes[(i % 25).let { if (it >= videoPalettes.size) 0 else it }]
    WidgetLook(p.name + if (i >= 25) " Sq" else "", p, i >= 25)
}
