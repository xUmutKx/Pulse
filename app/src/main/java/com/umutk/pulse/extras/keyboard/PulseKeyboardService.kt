package com.umutk.pulse.extras.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.SystemClock
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.umutk.pulse.features.StandByOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The Pulse keyboard. It types into the field with a composing word (so a typo can be corrected when the word ends and the correction taken back with backspace),
 * keeps the suggestions of the bar up to date, and lets the settings page (KeyboardSettings) change how it looks and behaves.
 */
class PulseKeyboardService : InputMethodService(), KbActions {
    private val st = KbState()
    private var owner: StandByOwner? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    private val word = StringBuilder()
    private var prevWord: String? = null
    private class Correction(val original: String, val corrected: String, val sep: String)
    private var lastCorrection: Correction? = null
    private var selStart = 0
    private var selEnd = 0
    private var shiftTap = 0L
    private var spaceTap = 0L
    private var lastWasSpace = false
    private var punctTyped = false
    private var slotWords = arrayOf("", "", "")
    private var target: String? = null
    private var anchor = 0
    private var lastGlide: String? = null
    private var glideAlts: List<String> = emptyList()
    private var clipboard: ClipboardManager? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val t = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
        if (!t.isNullOrEmpty() && t.length <= 600 && !st.incognito && !st.plain) st.clips = (listOf(t) + st.clips.filter { it != t }).take(20)
    }

    private val lang get() = KeyboardLayouts.lang(st.lang)

    override fun onCreate() {
        super.onCreate()
        KeyboardPrefs.load(this)
        owner = StandByOwner().also { it.start() }
        st.lang = KeyboardPrefs.langList().first()
        st.recents = getSharedPreferences("pulse_keyboard", Context.MODE_PRIVATE).getString("recents", "")?.split(' ')?.filter { it.isNotEmpty() } ?: emptyList()
        scope.launch(Dispatchers.IO) { KeyboardPrefs.langList().forEach { KeyboardDictionary.load(this@PulseKeyboardService, it) }; withContext(Dispatchers.Main) { updateSuggestions() } }
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        clipboard?.removePrimaryClipChangedListener(clipListener)
        KeyboardDictionary.saveUser(this)
        owner?.stop(); scope.cancel()
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View {
        val o = owner ?: StandByOwner().also { it.start(); owner = it }
        // Compose finds its lifecycle on the root of the window, which for a keyboard is the window's decor view
        window.window?.decorView?.let { it.setViewTreeLifecycleOwner(o); it.setViewTreeSavedStateRegistryOwner(o) }
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(o); setViewTreeSavedStateRegistryOwner(o)
            setContent { KeyboardView(st, this@PulseKeyboardService) }
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        st.dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val v = info.inputType and InputType.TYPE_MASK_VARIATION
        st.plain = cls != InputType.TYPE_CLASS_TEXT || (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0 ||
            v in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI, InputType.TYPE_TEXT_VARIATION_FILTER)
        st.page = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME) 1 else 0
        st.panel = "keys"; st.toolbar = false
        st.enter = when {
            (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 -> "newline"
            else -> when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_SEND -> "send"; EditorInfo.IME_ACTION_SEARCH -> "search"; EditorInfo.IME_ACTION_GO -> "go"
                EditorInfo.IME_ACTION_DONE -> "done"; EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_PREVIOUS -> "next"; else -> "newline"
            }
        }
        word.clear(); lastCorrection = null; prevWord = null; lastWasSpace = false; punctTyped = false
        st.shift = 0
        updateCaps(); updateSuggestions()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        word.clear(); lastCorrection = null
        KeyboardDictionary.saveUser(this)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart; selEnd = newSelEnd
        // the cursor left the word under way (the user tapped elsewhere): the word is finished as it stands
        if (word.isNotEmpty() && (candidatesStart < 0 || newSelStart < candidatesStart || newSelStart > candidatesEnd)) {
            word.clear(); lastCorrection = null; updateSuggestions()
        }
    }

    // ---- typing ------------------------------------------------------------------------------------------------------------------------------------

    override fun type(s: String) {
        val c = currentInputConnection ?: return
        if (s.isEmpty()) return
        if (s.codePointCount(0, s.length) > 1 || Character.isSurrogate(s[0])) { finishWord("", false); c.commitText(s, 1); afterText(); return }
        lastGlide = null
        if (s.length == 1 && (st.ctrl > 0 || st.alt > 0 || st.win > 0 || st.shiftMod > 0) && sendChord(s[0])) return
        val ch = s[0]
        val letter = ch.isLetter() || (ch == '\'' && word.isNotEmpty())
        if (letter) {
            if (punctTyped && KeyboardPrefs.autoSpace && !st.plain && ch.isLetter()) c.commitText(" ", 1)
            punctTyped = false; lastWasSpace = false
            word.append(ch)
            c.setComposingText(word, 1)
            lastCorrection = null
            if (st.shift == 1) st.shift = 0
            updateSuggestions()
        } else {
            val correct = !ch.isDigit()
            finishWord(s, correct)
            c.commitText(s, 1)
            punctTyped = ch in ",;!?:"
            lastWasSpace = false
            if (ch in ".!?") lastCorrection = null
            afterText()
        }
    }

    /** Ends the word under way: commits it, corrected when the correction applies. [sep] is what the user typed after it. */
    private fun finishWord(sep: String, correct: Boolean) {
        val c = currentInputConnection ?: return
        if (word.isEmpty()) return
        val typed = word.toString()
        var out = typed
        val t = target
        if (correct && KeyboardPrefs.autocorrect && !st.plain && t != null && !t.equals(typed, true)) out = t
        c.commitText(out, 1)
        lastCorrection = if (out != typed) Correction(typed, out, sep) else null
        if (out == typed && KeyboardPrefs.learn && !st.plain && !st.incognito) KeyboardDictionary.learn(this, prevWord, typed)
        prevWord = out
        word.clear(); target = null
    }

    private fun afterText() { updateCaps(); updateSuggestions() }

    override fun space() {
        val c = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        if (word.isEmpty() && lastWasSpace && KeyboardPrefs.quickPeriod && !st.plain && now - spaceTap < 600) {
            val before = c.getTextBeforeCursor(2, 0)
            if (before != null && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                c.deleteSurroundingText(1, 0); c.commitText(". ", 1)
                lastWasSpace = false; punctTyped = false; afterText(); return
            }
        }
        if (word.isEmpty() && KeyboardPrefs.quickPrediction && !st.plain && slotWords[1].isNotEmpty() && prevWord != null) {
            c.commitText(slotWords[1] + " ", 1); prevWord = slotWords[1]; lastWasSpace = true; spaceTap = now; afterText(); return
        }
        finishWord(" ", true)
        c.commitText(" ", 1)
        lastWasSpace = true; spaceTap = now; punctTyped = false
        afterText()
    }

    override fun enter() {
        val c = currentInputConnection ?: return
        finishWord("\n", true)
        if (st.enter != "newline") {
            val info = currentInputEditorInfo
            c.performEditorAction(info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_DONE)
        } else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        lastWasSpace = false; punctTyped = false; prevWord = null
        afterText()
    }

    override fun backspace() {
        val c = currentInputConnection ?: return
        lastWasSpace = false
        lastGlide?.let { g ->
            val before = c.getTextBeforeCursor(g.length + 1, 0)?.toString()
            lastGlide = null
            if (before == "$g ") { c.deleteSurroundingText(g.length + 1, 0); afterText(); return }
        }
        if (word.isNotEmpty()) {
            word.deleteCharAt(word.length - 1)
            if (word.isEmpty()) c.commitText("", 1) else c.setComposingText(word, 1)
            lastCorrection = null; target = null
            updateCaps(); updateSuggestions(); return
        }
        val lc = lastCorrection
        if (lc != null && KeyboardPrefs.undoOnBackspace) {
            val n = lc.corrected.length + lc.sep.length
            val txt = c.getTextBeforeCursor(n, 0)?.toString()
            lastCorrection = null
            if (txt == lc.corrected + lc.sep) {
                c.beginBatchEdit(); c.deleteSurroundingText(n, 0); c.setComposingText(lc.original, 1); c.endBatchEdit()
                word.append(lc.original); target = null; updateSuggestions(); return
            }
        }
        if (c.getSelectedText(0)?.isNotEmpty() == true) c.commitText("", 1) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        updateCaps(); updateSuggestions()
    }

    // ---- keys of the keyboard ----------------------------------------------------------------------------------------------------------------------

    override fun shift() {
        val now = SystemClock.uptimeMillis()
        st.shift = when (st.shift) { 0 -> { shiftTap = now; 1 }; 1 -> if (now - shiftTap < 450) 2 else 0; else -> 0 }
    }

    override fun page(p: Int) { finishWord("", false); st.page = p; st.panel = "keys"; if (p == 0) updateCaps() }

    override fun emojiPanel() { finishWord("", false); st.panel = if (st.panel == "emoji") "keys" else "emoji"; st.toolbar = false }

    override fun lang() {
        val l = KeyboardPrefs.langList()
        finishWord("", false)
        st.lang = l[(l.indexOf(st.lang) + 1).mod(l.size)]
        scope.launch(Dispatchers.IO) { KeyboardDictionary.load(this@PulseKeyboardService, st.lang); withContext(Dispatchers.Main) { updateSuggestions() } }
    }

    override fun slot(i: Int) {
        val c = currentInputConnection ?: return
        val w = slotWords.getOrNull(i).orEmpty()
        if (w.isEmpty()) return
        val isEmoji = !w[0].isLetterOrDigit()
        val g = lastGlide
        if (word.isEmpty() && g != null && w in glideAlts) {
            c.deleteSurroundingText(g.length + 1, 0); c.commitText("$w ", 1); lastGlide = w; prevWord = w; return
        }
        if (word.isNotEmpty()) {
            val typed = word.toString()
            word.clear(); target = null
            if (isEmoji) { c.commitText("$typed $w ", 1) } else {
                c.commitText("$w ", 1)
                if (KeyboardPrefs.learn && !st.plain && !st.incognito) KeyboardDictionary.learn(this, prevWord, w)
                prevWord = w
            }
        } else {
            c.commitText(if (isEmoji) "$w " else "$w ", 1)
            if (!isEmoji) prevWord = w
        }
        lastCorrection = null; lastWasSpace = true; spaceTap = SystemClock.uptimeMillis(); punctTyped = false
        afterText()
    }

    override fun arrow(code: Int) { finishWord("", false); sendDownUpKeyEvents(code); lastWasSpace = false }

    override fun edit(id: String) {
        val c = currentInputConnection ?: return
        finishWord("", false)
        when (id) {
            "selectAll" -> c.performContextMenuAction(android.R.id.selectAll)
            "copy" -> c.performContextMenuAction(android.R.id.copy)
            "cut" -> c.performContextMenuAction(android.R.id.cut)
            "paste" -> c.performContextMenuAction(android.R.id.paste)
            "undo" -> c.performContextMenuAction(android.R.id.undo)
            "redo" -> c.performContextMenuAction(android.R.id.redo)
            "capslock" -> st.shift = if (st.shift == 2) 0 else 2
        }
    }

    override fun moveCursor(delta: Int) {
        val c = currentInputConnection ?: return
        finishWord("", false)
        val pos = (selStart + delta).coerceAtLeast(0)
        c.setSelection(pos, pos); selStart = pos; selEnd = pos
    }

    override fun quickDeleteStart() { finishWord("", false); anchor = selEnd; st.selecting = true }

    override fun quickDelete(chars: Int) {
        val c = currentInputConnection ?: return
        val from = (anchor - chars).coerceAtLeast(0)
        c.setSelection(from, anchor)
    }

    override fun quickDeleteEnd(apply: Boolean) {
        val c = currentInputConnection ?: return
        if (apply) c.commitText("", 1)
        st.selecting = false; afterText()
    }

    override fun menu(id: String) {
        when (id) {
            "menu" -> { st.panel = "menu"; st.toolbar = false }
            "clip" -> { st.panel = "clip"; st.toolbar = false }
            "back" -> st.panel = "keys"
            "emoji" -> st.panel = "emoji"
            "resize" -> st.panel = "resize"
            "settings", "languages" -> {
                st.panel = "keys"
                startActivity(Intent().setClassName(this, "com.umutk.pulse.ExtraToolActivity").putExtra("route", "keyboard").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            "theme" -> { val l = KeyboardThemes.ids; KeyboardPrefs.theme = l[(l.indexOf(KeyboardPrefs.theme) + 1).mod(l.size)]; KeyboardPrefs.save(this) }
            "incognito" -> st.incognito = !st.incognito
            "autocorrect" -> { KeyboardPrefs.autocorrect = !KeyboardPrefs.autocorrect; KeyboardPrefs.save(this) }
            "onehand" -> { KeyboardPrefs.oneHand = when (KeyboardPrefs.oneHand) { "off" -> "right"; "right" -> "left"; else -> "off" }; KeyboardPrefs.save(this) }
            "arrows" -> { KeyboardPrefs.arrows = !KeyboardPrefs.arrows; KeyboardPrefs.save(this) }
            "numbers" -> { KeyboardPrefs.numberRow = !KeyboardPrefs.numberRow; KeyboardPrefs.save(this) }
            "proRow" -> { KeyboardPrefs.proRow = !KeyboardPrefs.proRow; KeyboardPrefs.save(this) }
            "glide" -> { KeyboardPrefs.glide = !KeyboardPrefs.glide; KeyboardPrefs.save(this) }
        }
        if (id == "resize") return
        if (st.panel == "keys") KeyboardPrefs.save(this)
    }

    override fun feedback() {
        if (KeyboardPrefs.haptic) window.window?.decorView?.performHapticFeedback(if (KeyboardPrefs.hapticMs >= 20) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP)
        if (KeyboardPrefs.sound) (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1f)
    }

    override fun emoji(e: String) {
        val c = currentInputConnection ?: return
        finishWord("", false)
        c.commitText(e, 1)
        st.recents = (listOf(e) + st.recents.filter { it != e }).take(30)
        getSharedPreferences("pulse_keyboard", Context.MODE_PRIVATE).edit().putString("recents", st.recents.joinToString(" ")).apply()
        lastWasSpace = false
    }

    override fun paste(s: String) {
        val c = currentInputConnection ?: return
        finishWord("", false)
        c.commitText(s, 1); st.panel = "keys"; afterText()
    }

    // ---- swipe typing, extra keys -------------------------------------------------------------------------------------------------------------------

    override fun glide(letters: String) {
        val c = currentInputConnection ?: return
        val found = KeyboardDictionary.glide(st.lang, letters, 4)
        if (found.isEmpty()) return
        finishWord(" ", true)
        // a word swiped right after another is a new word
        val before = c.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        if (before.isNotEmpty() && !before[0].isWhitespace() && before[0] !in "([{\"'") c.commitText(" ", 1)
        val up = st.shift > 0
        fun fix(w: String) = if (up) w.replaceFirstChar { it.titlecase(lang.locale) } else w
        val words = found.map { fix(it) }
        c.commitText(words[0] + " ", 1)
        if (st.shift == 1) st.shift = 0
        if (KeyboardPrefs.learn && !st.plain && !st.incognito) KeyboardDictionary.learn(this, prevWord, words[0])
        prevWord = words[0]; lastGlide = words[0]; glideAlts = words
        lastWasSpace = true; spaceTap = SystemClock.uptimeMillis(); punctTyped = false
        slotWords = arrayOf(words.getOrElse(1) { "" }, words[0], words.getOrElse(2) { "" })
        st.slots = slotWords.toList()
    }

    private fun metaNow(): Int {
        var m = 0
        if (st.ctrl > 0) m = m or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (st.alt > 0) m = m or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (st.win > 0) m = m or KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        if (st.shiftMod > 0) m = m or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        return m
    }

    /** A one-shot modifier is used up by the key it was waiting for. */
    private fun useModifiers() {
        if (st.ctrl == 1) st.ctrl = 0
        if (st.alt == 1) st.alt = 0
        if (st.win == 1) st.win = 0
        if (st.shiftMod == 1) st.shiftMod = 0
    }

    private fun sendKey(code: Int, extraMeta: Int = 0) {
        val c = currentInputConnection ?: return
        val t = SystemClock.uptimeMillis(); val meta = metaNow() or extraMeta
        c.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta))
        c.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta))
        useModifiers()
    }

    /** A letter typed while Ctrl, Alt, Win or Shift waits: the key press with those modifiers (Ctrl+C and so on), not the letter. */
    private fun sendChord(ch: Char): Boolean {
        finishWord("", false)
        val evs = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(charArrayOf(ch.lowercaseChar())) ?: return false
        val shifted = ch.isUpperCase() || st.shift > 0
        evs.filter { it.keyCode !in setOf(59, 60, 57, 58, 113, 114, 117, 118) }.forEach { e -> sendKey(e.keyCode, if (shifted) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0) }
        if (st.shift == 1) st.shift = 0
        return true
    }

    override fun extraKey(id: String) {
        val d = ExtraKeys.byId(id) ?: return
        finishWord("", false); lastGlide = null; lastWasSpace = false
        when (id) {
            "ctrl" -> st.ctrl = (st.ctrl + 1) % 3
            "alt" -> st.alt = (st.alt + 1) % 3
            "win" -> st.win = (st.win + 1) % 3
            "shiftm" -> st.shiftMod = (st.shiftMod + 1) % 3
            "selectAll", "copy", "cut", "paste", "undo", "redo" -> edit(id)
            else -> if (d.code != 0) sendKey(d.code)
        }
    }

    // ---- shift, suggestions ------------------------------------------------------------------------------------------------------------------------

    /** The sentence start puts the shift on, as the field asks. */
    private fun updateCaps() {
        if (!KeyboardPrefs.autoCap || st.plain || st.shift == 2) return
        val c = currentInputConnection ?: return
        val info = currentInputEditorInfo ?: return
        if (info.inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return
        st.shift = if (c.getCursorCapsMode(info.inputType) != 0) 1 else 0
    }

    private fun cased(w: String, typed: String): String = when {
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> w.uppercase(lang.locale)
        typed.isNotEmpty() && typed[0].isUpperCase() -> w.replaceFirstChar { it.titlecase(lang.locale) }
        else -> w
    }

    private fun updateSuggestions() {
        job?.cancel()
        if (!KeyboardPrefs.suggestions || st.plain) { st.slots = listOf("", "", ""); slotWords = arrayOf("", "", ""); target = null; return }
        val typed = word.toString(); val code = st.lang; val l = lang; val prev = prevWord; val up = st.shift > 0
        job = scope.launch {
            val res = withContext(Dispatchers.Default) { compute(code, l.locale, typed, prev, up) }
            if (word.toString() == typed) { target = res.third; slotWords = res.second; st.slots = res.first }
        }
    }

    /** The three slots of the bar (shown and as words), and the word the space key would correct [typed] to. */
    private fun compute(code: String, loc: Locale, typed: String, prev: String?, up: Boolean): Triple<List<String>, Array<String>, String?> {
        if (typed.isEmpty()) {
            val n = KeyboardDictionary.next(code, prev, 3).map { if (up) it.replaceFirstChar { c -> c.titlecase(loc) } else it }
            val w = arrayOf(n.getOrElse(0) { "" }, n.getOrElse(1) { "" }, n.getOrElse(2) { "" })
            return Triple(w.toList(), w, null)
        }
        val lower = typed.lowercase(loc)
        val comp = KeyboardDictionary.complete(code, lower, 4)
        val maxCost = if (code == "tr") (if (lower.length >= 5) .8f else .65f) else if (lower.length >= 4) 1.3f else .7f
        val corr = if (lower.length >= 2) KeyboardDictionary.correct(code, lower, maxCost, 5) else emptyList()
        val valid = KeyboardDictionary.isWord(code, lower)
        // the word the space key puts in: a near word, unless the typed one is already a word or too short to guess
        val fix = corr.firstOrNull { it.word != lower }?.takeIf { lower.length >= 3 && it.cost <= maxCost && (!valid || it.score - (corr.firstOrNull { c -> c.word == lower }?.score ?: 0f) > 5f) }
        val others = (comp.map { it.word } + corr.map { it.word }).distinct().filter { it != lower && it != fix?.word }
        val emoji = if (KeyboardPrefs.emojiPredictions) KeyboardDictionary.emoji(lower) else null
        val mid: String; val left: String; val right: String
        if (fix != null) { mid = cased(fix.word, typed); left = "\"$typed\""; right = emoji ?: others.firstOrNull()?.let { cased(it, typed) }.orEmpty() }
        else { mid = typed; left = others.getOrNull(0)?.let { cased(it, typed) }.orEmpty(); right = emoji ?: others.getOrNull(1)?.let { cased(it, typed) }.orEmpty() }
        val words = arrayOf(if (fix != null) typed else left, mid, right)
        return Triple(listOf(left, mid, right), words, fix?.let { cased(it.word, typed) })
    }
}
