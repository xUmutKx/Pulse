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
