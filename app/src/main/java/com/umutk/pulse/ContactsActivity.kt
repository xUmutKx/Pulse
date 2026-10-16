package com.umutk.pulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat

/** "Pulse Contacts": standalone launcher app (own task), Fossify-Contacts style. Look, accent, language live in CtPrefs. */
class ContactsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cfg.load(this); Opt.load(this)
        CtPrefs.load(this); CtPrefs.apply()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION") overridePendingTransition(0, 0) // standalone start: no open animation
        ctHandle(intent)
        setContent { LookHost(transparent = false, lookId = if (CtPrefs.amoled && CtPrefs.look == "material") "amoled" else CtPrefs.look) { LookFade { CtRoot() } } }
    }

    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); setIntent(intent); ctHandle(intent) }

    private fun enter() = AppLookGuard.enter({ CtPrefs.apply() }, { })
    override fun onStart() { enter(); super.onStart() }
    override fun onResume() { enter(); super.onResume() }
    override fun onStop() { super.onStop(); AppLookGuard.leave(this) }
    override fun onDestroy() { super.onDestroy(); AppLookGuard.leave(this) }
}
