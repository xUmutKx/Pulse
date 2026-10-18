package com.umutk.pulse.extras.watchdog

import android.content.Context
import android.content.Intent
import com.umutk.pulse.tr

/**
 * Data behind the Quick panel tile "last played". Players call [save] when a track or mix starts.
 * The tile shows [label] and sends [resumeIntent] on tap; the receiver calls [load] to know what to resume.
 * Tile class, icon drawable and manifest entry are wired in the shared Quick panel (see .scratch/extra-apps/19-quicktile-contract.md).
 */
object QuickTileContract {
    const val ACTION_RESUME = "com.umutk.pulse.action.RESUME_LAST"

    /** Drawable the tile uses; it must exist in res/drawable before the tile is registered. */
    const val ICON_RES_NAME = "ic_qs_last_played"

    private const val PREFS = "quick_tile_last"

    enum class Kind { TRACK, MIX }

    /** [ref] is what the player needs to start again (a file path for a track); empty when unknown. */
    data class LastPlayed(val kind: Kind, val name: String, val ref: String = "")

    fun save(c: Context, last: LastPlayed) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("kind", last.kind.name).putString("name", last.name).putString("ref", last.ref).apply()
    }

    fun load(c: Context): LastPlayed? {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val kind = p.getString("kind", null)?.let { runCatching { Kind.valueOf(it) }.getOrNull() } ?: return null
        val name = p.getString("name", null) ?: return null
        return LastPlayed(kind, name, p.getString("ref", "").orEmpty())
    }

    /** Tile text: the last name, or a fallback when nothing has been played yet. */
    fun label(c: Context): String = load(c)?.name ?: tr("Last played", "Son çalınan")

    /** Tap intent. [target] is the activity or receiver that resumes playback. */
    fun resumeIntent(c: Context, target: Class<*>): Intent = Intent(c, target).setAction(ACTION_RESUME)
}
