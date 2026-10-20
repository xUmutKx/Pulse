package com.umutk.pulse.extras.stickynote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.umutk.pulse.AppLookGuard
import com.umutk.pulse.Cfg
import com.umutk.pulse.ExtraLookControl
import com.umutk.pulse.LocalExtraLook
import com.umutk.pulse.Look
import com.umutk.pulse.LookHost
import com.umutk.pulse.Opt
import com.umutk.pulse.PTheme
import com.umutk.pulse.ToolLook

/** The look store of the sticky note editor: Material until the user picks another (the widget opens this editor, not a launcher entry). */
val StickyLook = ToolLook("sticky_look")

/** The sticky note editor, opened by a tap on a sticky note widget. Its own look; Pulse's look comes back on stop. */
class StickyNoteActivity : ComponentActivity() {

    // same process as Pulse: keep this editor's resolved look across pause / resume, as ExtraToolActivity does
    private var ownResolved: PTheme? = null
    override fun onPause() { super.onPause(); ownResolved = Look.resolved }
    override fun onResume() { enter(); super.onResume(); Look.resolved = ownResolved }
    override fun onStop() { super.onStop(); AppLookGuard.leave(this) }
    override fun onDestroy() { super.onDestroy(); AppLookGuard.leave(this) }
    private fun enter() = StickyLook.apply(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Cfg / Opt are process-wide; load the saved ones before the first frame
        Cfg.load(this); Opt.load(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enter()
        setContent {
            var look by remember { mutableStateOf(StickyLook.get(this@StickyNoteActivity)) }
            val control = remember(look) { ExtraLookControl(look) { look = it; StickyLook.set(this@StickyNoteActivity, it) } }
            CompositionLocalProvider(LocalExtraLook provides control) {
                LookHost(lookId = look) { th ->
                    Box(Modifier.fillMaxSize().background(th.bg).statusBarsPadding().navigationBarsPadding()) {
                        StickyNoteScreen { finish() }
                    }
                }
            }
        }
    }
}
