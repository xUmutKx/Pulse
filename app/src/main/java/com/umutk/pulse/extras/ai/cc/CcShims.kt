package com.umutk.pulse.extras.ai.cc

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.umutk.pulse.R
import com.umutk.pulse.extras.ai.AiLive
import kotlinx.coroutines.flow.MutableStateFlow

/*
 * What Buddy.kt (copied from Claude Chat, xUmutKx/ClaudeChat) asks of the rest of that app, answered for Pulse AI: the settings the buddy reads (kept in
 * the "ai_buddy" preferences, with Claude Chat's own defaults), the state of the chat, and the one orange mascot (no hats, no clothes, no body colour).
 */

enum class Status { Idle, Working, Done, Error, Offline, Background }
val Status.running get() = this == Status.Working || this == Status.Background

object Dance {
    const val FRAME_MS = 280L
    val ROT = floatArrayOf(0f, -12f, 0f, 12f)
    val UP = floatArrayOf(1f, 0f, 1f, 0f)
}

object Prefs {
    private lateinit var sp: SharedPreferences

    class S(private val key: String, private val def: String) {
        val flow = MutableStateFlow(sp.getString(key, def) ?: def)
        var value: String
            get() = flow.value
            set(v) { flow.value = v; sp.edit().putString(key, v).apply() }
    }

    lateinit var buddyGlow: S; lateinit var buddySize: S; lateinit var buddyOpacity: S; lateinit var buddyBubble: S; lateinit var buddyBubbleWork: S
    lateinit var buddyBubbleSize: S; lateinit var buddyBubbleWidth: S; lateinit var buddyBubbleLines: S; lateinit var buddyBubbleHold: S; lateinit var buddyBubbleOpacity: S
    lateinit var buddyBubbleShape: S; lateinit var buddyBubbleTone: S; lateinit var buddyTail: S; lateinit var buddyGravity: S; lateinit var buddyBounce: S
    lateinit var buddySleepAfter: S; lateinit var buddyBlanket: S; lateinit var buddyIdle: S; lateinit var buddyTap: S; lateinit var buddySnap: S
    lateinit var buddyX: S; lateinit var buddyY: S; lateinit var buddyHideAsleep: S

    @Synchronized fun init(c: Context) {
        if (this::buddyX.isInitialized) return
        sp = c.applicationContext.getSharedPreferences("ai_buddy", Context.MODE_PRIVATE)
        buddyGlow = S("buddyGlow", "none"); buddySize = S("buddySize", "76"); buddyOpacity = S("buddyOpacity", "100"); buddyBubble = S("buddyBubble", "1")
        buddyBubbleWork = S("buddyBubbleWork", "1"); buddyBubbleSize = S("buddyBubbleSize", "13"); buddyBubbleWidth = S("buddyBubbleWidth", "260")
        buddyBubbleLines = S("buddyBubbleLines", "8"); buddyBubbleHold = S("buddyBubbleHold", "12"); buddyBubbleOpacity = S("buddyBubbleOpacity", "96")
        buddyBubbleShape = S("buddyBubbleShape", "pill"); buddyBubbleTone = S("buddyBubbleTone", "light"); buddyTail = S("buddyTail", "1")
        buddyGravity = S("buddyGravity", "0"); buddyBounce = S("buddyBounce", "65"); buddySleepAfter = S("buddySleepAfter", "60"); buddyBlanket = S("buddyBlanket", "1")
        buddyIdle = S("buddyIdle", "1"); buddyTap = S("buddyTap", "bubble"); buddySnap = S("buddySnap", "0"); buddyX = S("buddyX", ""); buddyY = S("buddyY", "")
        buddyHideAsleep = S("buddyHideAsleep", "0")
    }
}

/** The windows are plain app overlays (the permission "display over other apps"). */
object PillAccess {
    fun wm(c: Context): WindowManager = c.getSystemService(WindowManager::class.java)
    fun type() = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
}

object Engine { val buildRunning = MutableStateFlow(false) }

object ChatLauncher { fun open(c: Context) { AiLive.openChat(c) } }

/** One orange mascot: no outfit, no body colour, no clothes. */
object Outfit { fun skinMatrix(): FloatArray? = null; fun hat(): Int = 0 }
object Wear { fun worn(@Suppress("UNUSED_PARAMETER") sleep: Boolean): List<Int> = emptyList() }
object Mascots {
    fun r(id: Int): Int = id
    fun pc(@Suppress("UNUSED_PARAMETER") alt: Boolean): List<Int> = listOf(R.drawable.cc_mascot18)
    fun hat(c: Context, id: Int): Drawable? = ContextCompat.getDrawable(c, id)?.mutate()
}
