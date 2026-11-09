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

    class B(private val key: String, private val def: Boolean) {
        val flow = MutableStateFlow(sp.getBoolean(key, def))
        var value: Boolean
            get() = flow.value
            set(v) { flow.value = v; sp.edit().putBoolean(key, v).apply() }
    }

    lateinit var bashColor: S
    lateinit var black: B
    lateinit var blackBattery: B
    lateinit var blackClock: B
    lateinit var blackDate: B
    lateinit var blackDim: S
    lateinit var blackFont: S
    lateinit var blackLast: B
    lateinit var blackMascot: B
    lateinit var blackSize: S
    lateinit var blackStatus: B
    lateinit var blackStyle: S
    lateinit var blackText: S
    lateinit var bubbleX: S
    lateinit var bubbleY: S
    lateinit var buddyBlanket: S
    lateinit var buddyBounce: S
    lateinit var buddyBubble: S
    lateinit var buddyBubbleHold: S
    lateinit var buddyBubbleLines: S
    lateinit var buddyBubbleOpacity: S
    lateinit var buddyBubbleShape: S
    lateinit var buddyBubbleSize: S
    lateinit var buddyBubbleTone: S
    lateinit var buddyBubbleWidth: S
    lateinit var buddyBubbleWork: S
    lateinit var buddyGlow: S
    lateinit var buddyGravity: S
    lateinit var buddyHideAsleep: S
    lateinit var buddyIdle: S
    lateinit var buddyOn: B
    lateinit var buddyOpacity: S
    lateinit var buddySize: S
    lateinit var buddySleepAfter: S
    lateinit var buddySnap: S
    lateinit var buddyTail: S
    lateinit var buddyTap: S
    lateinit var buddyX: S
    lateinit var buddyY: S
    lateinit var buildColor: S
    lateinit var danceMode: S
    lateinit var doneColor: S
    lateinit var mascotHome: S
    lateinit var mascotPose: S
    lateinit var mascotSkin: S
    lateinit var overlay: S
    lateinit var pillAlpha: S
    lateinit var pillBg: S
    lateinit var pillBubble: S
    lateinit var pillColor: S
    lateinit var pillEvents: S
    lateinit var pillExtra: S
    lateinit var pillFill: S
    lateinit var pillGap: S
    lateinit var pillGlow: S
    lateinit var pillHandle: S
    lateinit var pillHold: S
    lateinit var pillSleepHide: S
    lateinit var ringStyle: S
    lateinit var sceneDim: S
    lateinit var sceneInPill: S
    lateinit var screenOn: B
    lateinit var thinkColor: S

    @Synchronized fun init(c: Context) {
        if (this::buddyX.isInitialized) return
        sp = c.applicationContext.getSharedPreferences("ai_buddy", Context.MODE_PRIVATE)
        bashColor = S("bashColor", "current")
        black = B("black", false)
        blackBattery = B("blackBattery", true)
        blackClock = B("blackClock", true)
        blackDate = B("blackDate", true)
        blackDim = S("blackDim", "15")
        blackFont = S("blackFont", "outfit")
        blackLast = B("blackLast", true)
        blackMascot = B("blackMascot", true)
        blackSize = S("blackSize", "72")
        blackStatus = B("blackStatus", true)
        blackStyle = S("blackStyle", "digital")
        blackText = S("blackText", "")
        bubbleX = S("bubbleX", "")
        bubbleY = S("bubbleY", "")
        buddyBlanket = S("buddyBlanket", "1")
        buddyBounce = S("buddyBounce", "65")
        buddyBubble = S("buddyBubble", "1")
        buddyBubbleHold = S("buddyBubbleHold", "12")
        buddyBubbleLines = S("buddyBubbleLines", "8")
        buddyBubbleOpacity = S("buddyBubbleOpacity", "96")
        buddyBubbleShape = S("buddyBubbleShape", "pill")
        buddyBubbleSize = S("buddyBubbleSize", "13")
        buddyBubbleTone = S("buddyBubbleTone", "light")
        buddyBubbleWidth = S("buddyBubbleWidth", "260")
        buddyBubbleWork = S("buddyBubbleWork", "1")
        buddyGlow = S("buddyGlow", "none")
        buddyGravity = S("buddyGravity", "0")
        buddyHideAsleep = S("buddyHideAsleep", "0")
        buddyIdle = S("buddyIdle", "1")
        buddyOn = B("buddyOn", false)
        buddyOpacity = S("buddyOpacity", "100")
        buddySize = S("buddySize", "76")
        buddySleepAfter = S("buddySleepAfter", "60")
        buddySnap = S("buddySnap", "0")
        buddyTail = S("buddyTail", "1")
        buddyTap = S("buddyTap", "bubble")
        buddyX = S("buddyX", "")
        buddyY = S("buddyY", "")
        buildColor = S("buildColor", "current")
        danceMode = S("danceMode", "steps")
        doneColor = S("doneColor", "current")
        mascotHome = S("mascotHome", "pill")
        mascotPose = S("mascotPose", "idle")
        mascotSkin = S("mascotSkin", "orange")
        overlay = S("overlay", "pill")
        pillAlpha = S("pillAlpha", "100")
        pillBg = S("pillBg", "none")
        pillBubble = S("pillBubble", "1")
        pillColor = S("pillColor", "black")
        pillEvents = S("pillEvents", "0")
        pillExtra = S("pillExtra", "0")
        pillFill = S("pillFill", "100")
        pillGap = S("pillGap", "1")
        pillGlow = S("pillGlow", "none")
        pillHandle = S("pillHandle", "1")
        pillHold = S("pillHold", "4")
        pillSleepHide = S("pillSleepSecs", "10")
        ringStyle = S("ringStyle", "new")
        sceneDim = S("sceneDim", "35")
        sceneInPill = S("sceneInPill", "0")
        screenOn = B("screenOn", true)
        thinkColor = S("thinkColor", "current")
    }
}

/** The windows are plain app overlays (the permission "display over other apps"). */
object PillAccess {
    fun wm(c: Context): WindowManager = c.getSystemService(WindowManager::class.java)
    fun type() = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
}

object Engine {
    val buildRunning = MutableStateFlow(false)
    val shell = MutableStateFlow(false)
    val demoShell = MutableStateFlow(false)
    fun buildLog() {}
    val buildAlive = false
}
object AppState { val foreground = MutableStateFlow(false) }

object ChatLauncher { fun open(c: Context) { AiLive.openChat(c) } }

/** One orange mascot: no outfit, no body colour, no clothes. */
object Outfit { fun skinMatrix(): FloatArray? = null; fun hat(): Int = 0; fun scene(): Int = 0 }
object Wear { fun worn(@Suppress("UNUSED_PARAMETER") sleep: Boolean): List<Int> = emptyList() }
object Mascots {
    fun r(id: Int): Int = id
    fun pc(@Suppress("UNUSED_PARAMETER") alt: Boolean): List<Int> = listOf(R.drawable.cc_mascot18)
    fun hat(c: Context, id: Int): Drawable? = ContextCompat.getDrawable(c, id)?.mutate()
}

object Poses {
    val ALL = listOf(
        "idle", "jump", "hop", "dance", "spin", "squash", "nod", "sway", "bounce", "flip", "read", "pulse", "breath",
        "shiver", "swing", "slide", "zoom", "fade", "wobble", "rocket", "swim", "parachute", "bow", "whistle", "yawn", "backflip", "moonwalk", "pc")
    fun period(p: String): Int = when (p) { "jump", "squash", "hop", "nod" -> 620; "spin", "flip", "backflip" -> 1400; "rocket" -> 900; "dance" -> 900; "shiver" -> 500; else -> 1300 }
    fun label(p: String): Pair<String, String> = when (p) {
        "idle" -> "Idle" to "Boşta"; "jump" -> "Jump" to "Zıplama"; "hop" -> "Hop" to "Sekme"; "dance" -> "Dance" to "Dans"
        "spin" -> "Spin" to "Dönme"; "squash" -> "Squash" to "Esneme"; "nod" -> "Hello" to "Selam"; "sway" -> "Sway" to "Sallanma"
        "bounce" -> "Bounce" to "Zıplayış"; "flip" -> "Flip" to "Takla"; "read" -> "Read" to "Okuma"; "pulse" -> "Pulse" to "Atım"
        "breath" -> "Breathe" to "Nefes"; "shiver" -> "Shiver" to "Titreme"; "swing" -> "Swing" to "Salıncak"; "slide" -> "Slide" to "Kayma"
        "zoom" -> "Zoom" to "Büyüme"; "fade" -> "Glow" to "Parıltı"; "wobble" -> "Wobble" to "Sallantı"; "rocket" -> "Rocket" to "Roket"
        "swim" -> "Swim" to "Yüzme"; "parachute" -> "Parachute" to "Paraşüt"; "bow" -> "Bow" to "Eğilme"; "whistle" -> "Whistle" to "Islık"
        "yawn" -> "Yawn" to "Esneme 2"; "backflip" -> "Backflip" to "Geri takla"; "moonwalk" -> "Moonwalk" to "Ay yürüyüşü"
        "pc" -> "Laptop" to "Bilgisayar"; else -> p to p
    }
    fun prop(p: String): Int = 0

    fun apply(l: androidx.compose.ui.graphics.GraphicsLayerScope, p: String, t: Float) {
        val s = kotlin.math.sin(2 * Math.PI * t).toFloat(); val c = kotlin.math.cos(2 * Math.PI * t).toFloat()
        val a = kotlin.math.abs(s); val half = kotlin.math.abs(kotlin.math.sin(Math.PI * t)).toFloat()
        val px = { v: Float -> v * l.density }
        when (p) {
            "jump" -> l.translationY = -a * px(14f)
            "hop" -> { l.translationY = -half * px(10f);}
            "dance" -> { l.translationX = s * px(8f); l.translationY = -a * px(6f); l.rotationZ = s * 8f }
            "spin" -> l.rotationY = t * 360f
            "squash" -> { l.translationY = px(4f) * a }
            "nod" -> { l.translationY = s * px(3f);}
            "sway" -> { l.translationX = s * px(6f); l.rotationZ = s * 3f }
            "bounce" -> { l.translationY = -a * px(22f);}
            "flip" -> l.rotationX = t * 360f
            "read" -> { l.rotationZ = s * 4f;}
            "pulse" -> {}
            "breath" -> {}
            "shiver" -> l.translationX = kotlin.math.sin(2 * Math.PI * t * 6).toFloat() * px(2f)
            "swing" -> { l.transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f); l.rotationZ = s * 15f }
            "slide" -> l.translationX = s * px(16f)
            "zoom" -> {}
            "fade" -> l.alpha = 0.4f + 0.6f * a
            "wobble" -> { l.rotationZ = s * 6f;}
            "rocket" -> { l.translationY = -t * px(20f); l.translationX = kotlin.math.sin(2 * Math.PI * t * 8).toFloat() * px(1.5f) }
            "swim" -> { l.translationY = s * px(4f); l.translationX = s * px(4f); l.rotationZ = c * 6f }
            "parachute" -> { l.translationY = -half * px(6f); l.rotationZ = s * 5f }
            "bow" -> { l.translationY = px(2f) * kotlin.math.max(0f, s) }
            "whistle" -> { l.translationY = -a * px(3f) }
            "yawn" -> { l.translationY = -a * px(2f) }
            "backflip" -> { l.translationY = -half * px(18f); l.rotationX = -t * 360f }
            "moonwalk" -> l.translationX = ((2 * t) % 1f - 0.5f) * px(8f)
            else -> {}
        }
    }
}

