package com.umutk.pulse.extras.gamehub

import android.app.Activity
import android.os.Bundle
import com.umutk.pulse.extras.blackout.Privilege

/**
 * The target of a game's home-screen shortcut: a see-through window that starts the game and closes. A game hidden with `pm hide` is shown for its start
 * (Game Hub hides it again the next time it opens), so the shortcut works for hidden games like for the others.
 */
class GameLaunch : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg")
        if (pkg.isNullOrBlank()) { finish(); return }
        val ctx = applicationContext
        Thread {
            // a new process has not looked for root yet; the start of a hidden game needs it
            if (Privilege.mode == Privilege.Mode.None) runCatching { Privilege.detect() }
            runOnUiThread { openGame(ctx, pkg); finish() }
        }.start()
    }
}
