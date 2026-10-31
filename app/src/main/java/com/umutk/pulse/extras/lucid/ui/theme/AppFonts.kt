package com.umutk.pulse.extras.lucid.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.umutk.pulse.R
import com.umutk.pulse.extras.lucid.data.prefs.AppFontStyle

/** The family of an app font: the system's, or one of the fonts bundled with the app. Null for a custom file (read from its path). */
fun appFontFamilyOf(style: AppFontStyle): FontFamily? = when (style) {
    AppFontStyle.SYSTEM_DEFAULT -> FontFamily.SansSerif
    AppFontStyle.SERIF -> FontFamily.Serif
    AppFontStyle.CURSIVE -> FontFamily.Cursive
    AppFontStyle.BODONI -> FontFamily(Font(R.font.sb_bodoni_moda))
    AppFontStyle.DM_SERIF -> FontFamily(Font(R.font.sb_dm_serif_display))
    AppFontStyle.MONTAGU -> FontFamily(Font(R.font.sb_montagu_slab))
    AppFontStyle.JULIETT -> FontFamily(Font(R.font.sb_juliett))
    AppFontStyle.NEONDERTHAW -> FontFamily(Font(R.font.sb_neonderthaw))
    AppFontStyle.EDU -> FontFamily(Font(R.font.sb_edu_sa_beginner))
    AppFontStyle.KODE_MONO -> FontFamily(Font(R.font.sb_kode_mono))
    AppFontStyle.JERSEY -> FontFamily(Font(R.font.sb_jersey10))
    AppFontStyle.LEAGUE_GOTHIC -> FontFamily(Font(R.font.sb_league_gothic))
    AppFontStyle.TOURNEY -> FontFamily(Font(R.font.sb_tourney_regular))
    AppFontStyle.SUPER_POWER -> FontFamily(Font(R.font.sb_super_power))
    AppFontStyle.TILT_NEON -> FontFamily(Font(R.font.sb_tilt_neon))
    AppFontStyle.STICK -> FontFamily(Font(R.font.sb_stick_no_bills))
    AppFontStyle.SEGMENTS -> FontFamily(Font(R.font.sb_segments_7_bold))
    AppFontStyle.CUSTOM_FILE -> null
}

/** The family of an alarm clock font: the same bundled fonts as the app's, so the two lists are one list of cards. */
fun clockFontFamilyOf(font: com.umutk.pulse.extras.lucid.data.prefs.RingingClockFont): FontFamily? =
    appFontFamilyOf(AppFontStyle.valueOf(font.name))

/** Fonts as cards, each drawn in its own font: a sample line and the name under it. The chosen one has a border. */
@androidx.compose.foundation.layout.ExperimentalLayoutApi
@androidx.compose.runtime.Composable
fun <T> FontCards(items: List<T>, selected: T, label: (T) -> String, family: (T) -> FontFamily?, onPick: (T) -> Unit) {
    val mat = androidx.compose.material3.MaterialTheme.colorScheme
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            val on = item == selected
            val shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier
                    .width(124.dp)
                    .clip(shape)
                    .border(if (on) 2.dp else 1.dp, if (on) mat.primary else mat.outline, shape)
                    .clickable { onPick(item) }
                    .padding(10.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                androidx.compose.material3.Text(
                    "Aa 07:30",
                    fontFamily = family(item) ?: FontFamily.SansSerif,
                    fontSize = 22.sp,
                    maxLines = 1,
                    color = mat.onSurface
                )
                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(6.dp))
                androidx.compose.material3.Text(
                    label(item),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    color = mat.onSurfaceVariant
                )
            }
        }
    }
}
