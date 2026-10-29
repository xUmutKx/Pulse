package com.umutk.pulse.extras.keyboard

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Settings of the Pulse keyboard. One process, so the keyboard service and its settings page read the same state. */
object KeyboardPrefs {
    const val FILE = "pulse_keyboard"
    internal var loaded = false

    // look
    var theme by mutableStateOf("amoled")
    /** Custom theme colours (ARGB): background, key, key text, special key, accent, toolbar menu. */
    var cBg by mutableStateOf(0xFF000000.toInt())
    var cKey by mutableStateOf(0xFF1F1F1F.toInt())
    var cText by mutableStateOf(0xFFFFFFFF.toInt())
    var cSpecial by mutableStateOf(0xFF151515.toInt())
    var cAccent by mutableStateOf(0xFF4FC3F7.toInt())
    var cMenu by mutableStateOf(0xFF000000.toInt())
    var cRadius by mutableStateOf(10)

    // size
    var heightPct by mutableStateOf(110)
    /** A typed word this much like a known word (percent, letters that match) is corrected on its own. */
    var autoRatio by mutableStateOf(80)
    /** How far an automatic correction may reach: 0 modest, 1 normal, 2 aggressive, 3 very aggressive. */
    var autoLevel by mutableStateOf(1)
    var sidePad by mutableStateOf(2)
    var bottomPad by mutableStateOf(0)
    var keyGap by mutableStateOf(3)
    /** off, left, right: a narrower keyboard pushed to one side. */
    var oneHand by mutableStateOf("off")

    // layout
    var langs by mutableStateOf("tr,en")
    var numberRow by mutableStateOf(false)
    var arrows by mutableStateOf(false)
    var emojiKey by mutableStateOf(true)
    /** The key beside ?123: "menu" (the three-dot menu), "emoji" or "none". */
    var bottomKey by mutableStateOf("emoji")
    var largeText by mutableStateOf(false)
    var keyPopups by mutableStateOf(true)
    var longPressSymbols by mutableStateOf(true)
    var keyFx by mutableStateOf(0)
    var boardFx by mutableStateOf(0)
    var motionFx by mutableStateOf(0)
    var soundKind by mutableStateOf(0)
    var spaceLabel by mutableStateOf(true)

    // typing
    var suggestions by mutableStateOf(true)
    var autocorrect by mutableStateOf(true)
    var undoOnBackspace by mutableStateOf(true)
    var quickPrediction by mutableStateOf(false)
    var quickPeriod by mutableStateOf(true)
    var autoCap by mutableStateOf(true)
    var autoSpace by mutableStateOf(true)
    var cursorControl by mutableStateOf(true)
    var quickDelete by mutableStateOf(true)
    var emojiPredictions by mutableStateOf(true)
    var learn by mutableStateOf(true)

    // swipes, extra keys and the toolbar
    var glide by mutableStateOf(true)
    var flick by mutableStateOf(true)
    var anim by mutableStateOf(true)
    var proRow by mutableStateOf(true)
    var proKeys by mutableStateOf(ExtraKeys.DEFAULT)
    /** A second extra row under the first: its own keys (Fn, Home, End, Page keys, editing keys by default). */
    var proRow2 by mutableStateOf(false)
    var proKeys2 by mutableStateOf("fn,home,end,pgup,pgdn,del,ins,selectAll,copy,paste")
    /** The extra row sits under the space row instead of above the letters. */
    var proBottom by mutableStateOf(true)
    var proIcons by mutableStateOf(true)
    /** A gap in the middle of the letter rows, so each thumb has its half (tablets, landscape). */
    var split by mutableStateOf(false)
    /** Floating: a narrower keyboard (70 percent of the width) that slides sideways by a handle on top; [floatX] is its place, 0 left to 1 right. */
    var floating by mutableStateOf(false)
    var floatX by mutableStateOf(.5f)
    private var appCtx: Context? = null
    fun saveFloat() { appCtx?.let { save(it) } }
    /** The typing practice of the settings is open: nothing is corrected, so the slips stay visible to be learned. */
    @Volatile var practicing = false
    var menuItems by mutableStateOf(ExtraKeys.DEFAULT_MENU)
    var toolbarItems by mutableStateOf(ExtraKeys.DEFAULT_TOOLBAR)
    /** The size of the gamepad panel in percent (60..150): it follows the screen, this scales it. */
    var padScale by mutableStateOf(130)
    /** The long-press action of the comma, the period and the key beside ?123 (ExtraKeys.hold ids). */
    var commaHold by mutableStateOf("voice")
    /** The gamepad sticks: the dead zone (percent of a stick's reach before it sends a key) and how far up the sticks sit (dp at 1 percent of the panel). */
    var padDead by mutableStateOf(28)
    var padLift by mutableStateOf(0)
    var periodHold by mutableStateOf("none")
    var bottomHold by mutableStateOf("none")

    // feel
    var haptic by mutableStateOf(true)
    /** How bright the keyboard is (30..100), and the space row with the extra keys on its own. */
    var bright by mutableStateOf(100)
    var bottomBright by mutableStateOf(100)
    var topBright by mutableStateOf(100)
    var bottomShift by mutableStateOf(false)
    /** The direction keyboard: nine big keys, a swipe towards a direction types the character shown there. */
    var dirLayout by mutableStateOf(false)
    /** Moving colour light: 0 off, 1 around the keyboard, 2 around every key (a rainbow that runs along the keys). */
    var rgb by mutableStateOf(0)
    /** How long an unpinned clipboard item is kept, in hours (1 to 72). */
    var clipHours by mutableStateOf(168)  // hours the unpinned items stay (one week by default); 0 = for ever
    /** A comma straight before a letter inside a word is a slip: it is taken out and the word goes on. */
    var commaSlip by mutableStateOf(false)
    var hapticMs by mutableStateOf(8)   // strength 1..40
    var sound by mutableStateOf(false)

    fun langList(): List<String> = langs.split(',').filter { it in KeyboardLayouts.languages }.ifEmpty { listOf("en") }

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        // once: the extra keys (arrows, Ctrl, ...) sit under the space row from now on
        if (p.getInt("ver", 0) < 2) p.edit().putBoolean("proRow", true).putBoolean("proBottom", true).putInt("ver", 2).apply()
        theme = p.getString("theme", theme) ?: theme
        cBg = p.getInt("cBg", cBg); cKey = p.getInt("cKey", cKey); cText = p.getInt("cText", cText); cSpecial = p.getInt("cSpecial", cSpecial)
        cAccent = p.getInt("cAccent", cAccent); cMenu = p.getInt("cMenu", cMenu); cRadius = p.getInt("cRadius", cRadius)
        heightPct = p.getInt("heightPct", heightPct); autoRatio = p.getInt("autoRatio", autoRatio); autoLevel = p.getInt("autoLevel", autoLevel); sidePad = p.getInt("sidePad", sidePad); bottomPad = p.getInt("bottomPad", bottomPad)
        keyGap = p.getInt("keyGap", keyGap); oneHand = p.getString("oneHand", oneHand) ?: oneHand
        langs = p.getString("langs", langs) ?: langs
        numberRow = p.getBoolean("numberRow", numberRow); arrows = p.getBoolean("arrows", arrows); emojiKey = p.getBoolean("emojiKey", emojiKey); bottomKey = p.getString("bottomKey2", null) ?: "emoji"
        largeText = p.getBoolean("largeText", largeText); keyPopups = p.getBoolean("keyPopups", keyPopups)
        longPressSymbols = p.getBoolean("longPressSymbols", longPressSymbols); spaceLabel = p.getBoolean("spaceLabel", spaceLabel)
        suggestions = p.getBoolean("suggestions", suggestions); autocorrect = p.getBoolean("autocorrect", autocorrect)
        undoOnBackspace = p.getBoolean("undoOnBackspace", undoOnBackspace); quickPrediction = p.getBoolean("quickPrediction", quickPrediction)
        quickPeriod = p.getBoolean("quickPeriod", quickPeriod); autoCap = p.getBoolean("autoCap", autoCap); autoSpace = p.getBoolean("autoSpace", autoSpace)
        cursorControl = p.getBoolean("cursorControl", cursorControl); quickDelete = p.getBoolean("quickDelete", quickDelete)
        emojiPredictions = p.getBoolean("emojiPredictions", emojiPredictions); learn = p.getBoolean("learn", learn)
        glide = p.getBoolean("glide", glide); flick = p.getBoolean("flick", flick); anim = p.getBoolean("anim", anim); proRow = p.getBoolean("proRow", proRow)
        proKeys = p.getString("proKeys", proKeys) ?: proKeys; proRow2 = p.getBoolean("proRow2", proRow2); proKeys2 = p.getString("proKeys2", proKeys2) ?: proKeys2; proBottom = p.getBoolean("proBottom", proBottom); proIcons = p.getBoolean("proIcons", proIcons); split = p.getBoolean("split", split); floating = p.getBoolean("floating", false); floatX = p.getFloat("floatX", .5f); appCtx = c.applicationContext; menuItems = p.getString("menuItems", menuItems) ?: menuItems; toolbarItems = p.getString("toolbarItems", toolbarItems) ?: toolbarItems
        // once: Shift joins the default extra keys (only for those who never changed the row)
        if (p.getInt("ratioVer", 0) < 1) { autoRatio = 80; p.edit().putInt("autoRatio", 80).putInt("ratioVer", 1).apply() }
        if (p.getInt("proVer", 0) < 1) { if (proKeys == ExtraKeys.OLD_DEFAULT) proKeys = ExtraKeys.DEFAULT; p.edit().putInt("proVer", 1).apply() }
        // the menu and bar of older versions held cut / copy / paste / undo: they are replaced by the new default once
        // the menu gets the separate "Extra keys" row switch and the "Key set" panel (F keys, Shift, ...) once, whatever it held
        if (p.getInt("menuVer", 0) < 3) { menuItems = (menuItems.split(',') + listOf("proRow", "keyset")).distinct().joinToString(","); p.edit().putInt("menuVer", 3).putString("menuItems", menuItems).apply() }
        if (p.getInt("menuVer", 0) < 1) { menuItems = ExtraKeys.DEFAULT_MENU; toolbarItems = ExtraKeys.DEFAULT_TOOLBAR; p.edit().putInt("menuVer", 1).apply() }
        // the emoji key lives beside ?123 again; the bar keeps the three-dot menu in its place
        if (p.getInt("menuVer", 0) < 4) { toolbarItems = toolbarItems.split(',').map { if (it == "emoji") "menu" else it }.distinct().joinToString(","); p.edit().putInt("menuVer", 4).putString("toolbarItems", toolbarItems).apply() }
        // the key bar (Fn, Home/End, Page keys, ...) gets a quick toggle beside the mic, next to voice
        if (p.getInt("menuVer", 0) < 5) {
            val items = toolbarItems.split(',').toMutableList()
            if ("arrows" !in items) { val i = items.indexOf("voice"); if (i >= 0) items.add(i + 1, "arrows") else items.add("arrows") }
            toolbarItems = items.joinToString(","); p.edit().putInt("menuVer", 5).putString("toolbarItems", toolbarItems).apply()
        }
        // the Layouts picker (Numbers / Game) gets a place in the three-dot menu
        if (p.getInt("menuVer", 0) < 6) {
            if ("layout" !in menuItems.split(',')) { menuItems = "$menuItems,layout"; p.edit().putInt("menuVer", 6).putString("menuItems", menuItems).apply() }
            else p.edit().putInt("menuVer", 6).apply()
        }
        // the bar's first slots are the clipboard and the gamepad now; the mic left the bar (the comma key's long press already starts voice typing), the menu gets the gamepad too
        if (p.getInt("menuVer", 0) < 7) {
            val t = toolbarItems.split(',').filter { it != "voice" && it != "gamepad" && it != "clip" }.toMutableList()
            t.add(0, "gamepad"); t.add(0, "clip")
            toolbarItems = t.joinToString(",")
            if ("gamepad" !in menuItems.split(',')) menuItems = menuItems.split(',').toMutableList().also { l -> val i = l.indexOf("clip"); l.add(if (i >= 0) i + 1 else 0, "gamepad") }.joinToString(",")
            p.edit().putInt("menuVer", 7).putString("toolbarItems", toolbarItems).putString("menuItems", menuItems).apply()
        }
        padScale = p.getInt("padScale", 130); padDead = p.getInt("padDead", 28); padLift = p.getInt("padLift", 0)
        commaHold = p.getString("commaHold", "voice") ?: "voice"; periodHold = p.getString("periodHold", "none") ?: "none"; bottomHold = p.getString("bottomHold", "none") ?: "none"
        // the clipboard and the gamepad trade places in the bar: the gamepad is the first key now
        if (p.getInt("menuVer", 0) < 8) {
            val t = toolbarItems.split(',').toMutableList()
            val i = t.indexOf("clip"); val g = t.indexOf("gamepad")
            if (i >= 0 && g == i + 1) { t[i] = "gamepad"; t[g] = "clip" }
            toolbarItems = t.joinToString(","); p.edit().putInt("menuVer", 8).putString("toolbarItems", toolbarItems).apply()
        }
        haptic = p.getBoolean("haptic", haptic); bright = p.getInt("bright", 100); bottomBright = p.getInt("bottomBright", 100); topBright = p.getInt("topBright", 100); bottomShift = p.getBoolean("bottomShift", false); dirLayout = p.getBoolean("dirLayout", false); rgb = p.getInt("rgb", 0); clipHours = p.getInt("clipKeep", clipHours); commaSlip = p.getBoolean("commaSlip2", false); keyFx = p.getInt("keyFx", 0); boardFx = p.getInt("boardFx", 0); motionFx = p.getInt("motionFx", 0); soundKind = p.getInt("soundKind", 0); hapticMs = p.getInt("hapticMs", hapticMs); sound = p.getBoolean("sound", sound)
    }

    fun save(c: Context) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("theme", theme)
            .putInt("cBg", cBg).putInt("cKey", cKey).putInt("cText", cText).putInt("cSpecial", cSpecial).putInt("cAccent", cAccent).putInt("cMenu", cMenu).putInt("cRadius", cRadius)
            .putInt("heightPct", heightPct).putInt("autoRatio", autoRatio).putInt("autoLevel", autoLevel).putInt("sidePad", sidePad).putInt("bottomPad", bottomPad).putInt("keyGap", keyGap).putString("oneHand", oneHand)
            .putString("langs", langs).putInt("padScale", padScale).putInt("padDead", padDead).putInt("padLift", padLift).putString("commaHold", commaHold).putString("periodHold", periodHold).putString("bottomHold", bottomHold)
            .putBoolean("numberRow", numberRow).putBoolean("arrows", arrows).putBoolean("emojiKey", emojiKey).putString("bottomKey2", bottomKey).putBoolean("largeText", largeText)
            .putBoolean("keyPopups", keyPopups).putBoolean("longPressSymbols", longPressSymbols).putBoolean("spaceLabel", spaceLabel)
            .putBoolean("suggestions", suggestions).putBoolean("autocorrect", autocorrect).putBoolean("undoOnBackspace", undoOnBackspace)
            .putBoolean("quickPrediction", quickPrediction).putBoolean("quickPeriod", quickPeriod).putBoolean("autoCap", autoCap).putBoolean("autoSpace", autoSpace)
            .putBoolean("cursorControl", cursorControl).putBoolean("quickDelete", quickDelete).putBoolean("emojiPredictions", emojiPredictions).putBoolean("learn", learn)
            .putBoolean("glide", glide).putBoolean("flick", flick).putBoolean("anim", anim).putBoolean("proRow", proRow).putInt("ver", 2).putString("proKeys", proKeys).putBoolean("proRow2", proRow2).putString("proKeys2", proKeys2).putBoolean("proBottom", proBottom).putBoolean("proIcons", proIcons).putBoolean("split", split).putBoolean("floating", floating).putFloat("floatX", floatX).putString("menuItems", menuItems).putString("toolbarItems", toolbarItems)
            .putBoolean("haptic", haptic).putInt("bright", bright).putInt("bottomBright", bottomBright).putInt("topBright", topBright).putBoolean("bottomShift", bottomShift).putBoolean("dirLayout", dirLayout).putInt("rgb", rgb).putInt("clipKeep", clipHours).putBoolean("commaSlip2", commaSlip).putInt("keyFx", keyFx).putInt("boardFx", boardFx).putInt("motionFx", motionFx).putInt("soundKind", soundKind).putInt("hapticMs", hapticMs).putBoolean("sound", sound)
            .apply()
    }

    /** The theme colours only: the custom theme goes back to its first values. */
    fun resetTheme(c: Context) {
        theme = "amoled"
        cBg = 0xFF000000.toInt(); cKey = 0xFF1F1F1F.toInt(); cText = 0xFFFFFFFF.toInt(); cSpecial = 0xFF151515.toInt(); cAccent = 0xFF4FC3F7.toInt(); cMenu = 0xFF000000.toInt(); cRadius = 10
        save(c)
    }

    /** Everything back to the first values, and the learned words forgotten. */
    fun factoryReset(c: Context) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
        loaded = false
        theme = "amoled"; heightPct = 110; autoRatio = 80; autoLevel = 1; sidePad = 2; bottomPad = 0; keyGap = 3; oneHand = "off"; langs = "tr,en"
        numberRow = false; arrows = false; emojiKey = true; bottomKey = "emoji"; largeText = false; keyPopups = true; longPressSymbols = true; spaceLabel = true
        suggestions = true; autocorrect = true; undoOnBackspace = true; quickPrediction = false; quickPeriod = true; autoCap = true; autoSpace = true
        cursorControl = true; quickDelete = true; emojiPredictions = true; learn = true; glide = true; flick = true; anim = true; proRow = true; proKeys = ExtraKeys.DEFAULT; proBottom = true; proIcons = true; split = false; floating = false; floatX = .5f; menuItems = ExtraKeys.DEFAULT_MENU; toolbarItems = ExtraKeys.DEFAULT_TOOLBAR; haptic = true; bright = 100; bottomBright = 100; topBright = 100; bottomShift = false; dirLayout = false; keyFx = 0; boardFx = 0; motionFx = 0; soundKind = 0; rgb = 0; hapticMs = 8; sound = false
        resetTheme(c)
        KeyboardDictionary.forgetUser(c)
        loaded = true
    }
}
