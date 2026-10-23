package com.umutk.pulse.extras.keyboard

import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.delay
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
    private var prevWord2: String? = null
    private val touchPts = ArrayList<androidx.compose.ui.geometry.Offset?>()
    private var emailField = false
    private var lastEmail: String? = null
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
        // everything copied is kept (a password field's copy and a sensitive one are not), texts and pictures
        val clip = clipboard?.primaryClip?.takeIf { it.itemCount > 0 } ?: return@OnPrimaryClipChangedListener
        if (st.incognito || st.plain) return@OnPrimaryClipChangedListener
        if (android.os.Build.VERSION.SDK_INT >= 24 && clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return@OnPrimaryClipChangedListener
        val item = clip.getItemAt(0)
        val uri = item.uri
        if (uri != null && clip.description.hasMimeType("image/*")) {
            scope.launch(Dispatchers.IO) { val l = ClipStore.addImage(this@PulseKeyboardService, uri); withContext(Dispatchers.Main) { st.clipItems = l } }
        } else {
            val t = item.coerceToText(this)?.toString()?.trim()
            if (!t.isNullOrEmpty()) { st.clipItems = ClipStore.addText(this, t); if (t.length <= 600) st.clips = (listOf(t) + st.clips.filter { it != t }).take(20) }
        }
    }

    override fun clipPin(id: Long) { st.clipItems = ClipStore.togglePin(this, id) }
    override fun clipDelete(ids: Set<Long>) { st.clipItems = ClipStore.delete(this, ids) }
    override fun clipClear() { st.clipItems = ClipStore.clear(this) }

    /** A kept item into the field: a text is typed, a picture is handed over as content when the field takes pictures. */
    override fun pasteClip(item: ClipItem) {
        val c = currentInputConnection ?: return
        finishWord("", false)
        val path = item.image
        if (path == null) { c.commitText(item.text, 1); st.panel = "keys"; afterText(); return }
        val info = currentInputEditorInfo
        val types = if (info != null) androidx.core.view.inputmethod.EditorInfoCompat.getContentMimeTypes(info) else emptyArray()
        val ok = types.any { it.startsWith("image/") || it == "*/*" }
        if (!ok) { android.widget.Toast.makeText(this, com.umutk.pulse.tr("This field takes no pictures", "Bu alan resim kabul etmiyor"), android.widget.Toast.LENGTH_SHORT).show(); return }
        runCatching {
            val f = java.io.File(path)
            val u = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", f)
            val mime = if (f.inputStream().use { it.read() } == 0x89) "image/png" else "image/jpeg"
            val content = androidx.core.view.inputmethod.InputContentInfoCompat(u, android.content.ClipDescription("clip", arrayOf(mime)), null)
            androidx.core.view.inputmethod.InputConnectionCompat.commitContent(c, info!!, content, androidx.core.view.inputmethod.InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
            st.panel = "keys"
        }
    }

    private val lang get() = KeyboardLayouts.lang(st.lang)
    private var seenPrimary = ""

    /** The word lists used together: the layout's own, then those of the other languages in use (a Turkish keyboard that also corrects English). */
    private fun dictCodes(code: String): List<String> = (listOf(KeyboardLayouts.dict(code)) + KeyboardPrefs.langList().map { KeyboardLayouts.dict(it) }).distinct()

    override fun onCreate() {
        super.onCreate()
        KeyboardPrefs.load(this)
        owner = StandByOwner().also { it.start() }
        st.lang = KeyboardPrefs.langList().first()
        st.recents = getSharedPreferences("pulse_keyboard", Context.MODE_PRIVATE).getString("recents", "")?.let { r -> if ('\n' in r) r.split('\n') else r.split(' ') }?.filter { it.isNotEmpty() } ?: emptyList()
        scope.launch(Dispatchers.IO) {
            KeyboardPrefs.langList().forEach { code ->
                // a language without a word list fetches its own (when there is a connection)
                if (!KeyboardDictionary.hasList(this@PulseKeyboardService, code)) KeyboardDictionary.download(this@PulseKeyboardService, code)
                KeyboardDictionary.load(this@PulseKeyboardService, code)
                // the small bundled list is replaced once by the full one (inflected forms, names), when there is a connection
                runCatching { KeyboardDictionary.upgradeOnce(this@PulseKeyboardService, code) }
            }
            withContext(Dispatchers.Main) { updateSuggestions() }
        }
        st.clipItems = ClipStore.load(this)
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
        st.inline = emptyList(); st.emojiQuery = null
        st.clipItems = ClipStore.load(this)
        // the settings may have changed the language since the last field
        KeyboardPrefs.load(this)
        val primary = KeyboardPrefs.langList().first()
        if (primary != seenPrimary) { seenPrimary = primary; st.lang = primary }
        if (st.lang !in KeyboardPrefs.langList()) st.lang = primary
        st.dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val v = info.inputType and InputType.TYPE_MASK_VARIATION
        st.plain = cls != InputType.TYPE_CLASS_TEXT || (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0 ||
            v in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI, InputType.TYPE_TEXT_VARIATION_FILTER)
        emailField = cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || v == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS); lastEmail = null
        st.page = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME) 1 else 0
        st.panel = "keys"; st.toolbar = false
        st.enter = when {
            (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 -> "newline"
            else -> when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_SEND -> "send"; EditorInfo.IME_ACTION_SEARCH -> "search"; EditorInfo.IME_ACTION_GO -> "go"
                EditorInfo.IME_ACTION_DONE -> "done"; EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_PREVIOUS -> "next"; else -> "newline"
            }
        }
        word.clear(); touchPts.clear(); lastCorrection = null; prevWord = null; prevWord2 = null; lastWasSpace = false; punctTyped = false
        st.shift = 0
        updateCaps(); updateSuggestions()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        st.inline = emptyList()
        lastEmail?.let { e -> getSharedPreferences("pulse_kb_emails", MODE_PRIVATE).let { p -> p.edit().putString("list", (listOf(e) + p.getString("list", "").orEmpty().split('\n').filter { it.isNotBlank() && it != e }).take(5).joinToString("\n")).apply() } }
        lastEmail = null
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
        // the emoji search is open: the keys type into its box
        st.emojiQuery?.let { if (st.panel == "emoji") { st.emojiQuery = it + s; return } }
        if (s.codePointCount(0, s.length) > 1 || Character.isSurrogate(s[0])) { finishWord("", false); c.commitText(s, 1); afterText(); return }
        lastGlide = null
        if (s.length == 1 && (st.ctrl > 0 || st.alt > 0 || st.win > 0 || st.shiftMod > 0) && sendChord(s[0])) return
        val ch = s[0]
        val letter = ch.isLetter() || (ch == '\'' && word.isNotEmpty())
        if (letter) {
            // a comma right before a letter, behind a word: a slip of the finger; the comma goes and the word is reopened
            if (punctTyped && ch.isLetter() && KeyboardPrefs.commaSlip && !st.plain && word.isEmpty()) {
                val before = c.getTextBeforeCursor(40, 0)?.toString().orEmpty()
                if (before.length >= 3 && before.last() == ',' && before[before.length - 2].isLetter()) {
                    var n = 1
                    while (n < before.length && before[before.length - 1 - n].isLetter()) n++
                    val stem = before.substring(before.length - n, before.length - 1)
                    c.deleteSurroundingText(n, 0); word.append(stem); punctTyped = false
                }
            }
            if (punctTyped && KeyboardPrefs.autoSpace && !st.plain && ch.isLetter()) c.commitText(" ", 1)
            punctTyped = false; lastWasSpace = false
            word.append(ch)
            // the touch model: where this letter was touched, and the finger's drift from the key's centre is learned
            val tp = st.touch; st.touch = null
            touchPts.add(tp); if (touchPts.size > 48) touchPts.removeAt(0)
            if (tp != null) st.letterRects[ch.lowercase()]?.let { r -> KeyboardDictionary.learnTouch(ch.lowercaseChar(), tp, r.center, r.width) }
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
        if (out == typed && KeyboardPrefs.learn && !st.plain && !st.incognito) KeyboardDictionary.learn(this, prevWord2, prevWord, typed)
        prevWord2 = prevWord; prevWord = out
        word.clear(); target = null
    }

    private fun afterText() { updateCaps(); updateSuggestions() }

    override fun space() {
        val c = currentInputConnection ?: return
        st.emojiQuery?.let { if (st.panel == "emoji") { if (it.isNotEmpty() && !it.endsWith(" ")) st.emojiQuery = "$it "; return } }
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
        st.emojiQuery?.let { if (st.panel == "emoji") { st.emojiQuery = it.dropLast(1); return } }
        lastWasSpace = false
        lastGlide?.let { g ->
            val before = c.getTextBeforeCursor(g.length + 1, 0)?.toString()
            lastGlide = null
            if (before == "$g ") { c.deleteSurroundingText(g.length + 1, 0); afterText(); return }
        }
        if (word.isNotEmpty()) {
            word.deleteCharAt(word.length - 1)
            if (touchPts.isNotEmpty()) touchPts.removeAt(touchPts.size - 1)
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

    override fun emojiPanel() { finishWord("", false); st.emojiQuery = null; st.panel = if (st.panel == "emoji") "keys" else "emoji"; st.toolbar = false }

    override fun lang() {
        val l = KeyboardPrefs.langList()
        finishWord("", false)
        st.lang = l[(l.indexOf(st.lang) + 1).mod(l.size)]
        scope.launch(Dispatchers.IO) { KeyboardDictionary.load(this@PulseKeyboardService, st.lang); withContext(Dispatchers.Main) { updateSuggestions() } }
    }

    // ---- autofill inside the bar (Android 11+): the password manager's suggestions, drawn by the system -----------------------------------------------

    @androidx.annotation.RequiresApi(30)
    override fun onCreateInlineSuggestionsRequest(uiExtras: android.os.Bundle): android.view.inputmethod.InlineSuggestionsRequest? {
        val d = resources.displayMetrics.density
        val styles = androidx.autofill.inline.UiVersions.newStylesBuilder().addStyle(androidx.autofill.inline.v1.InlineSuggestionUi.newStyleBuilder().build()).build()
        val spec = android.widget.inline.InlinePresentationSpec.Builder(android.util.Size((100 * d).toInt(), (38 * d).toInt()), android.util.Size((260 * d).toInt(), (42 * d).toInt())).setStyle(styles).build()
        return android.view.inputmethod.InlineSuggestionsRequest.Builder(listOf(spec)).setMaxSuggestionCount(4).build()
    }

    @androidx.annotation.RequiresApi(30)
    override fun onInlineSuggestionsResponse(response: android.view.inputmethod.InlineSuggestionsResponse): Boolean {
        val list = response.inlineSuggestions
        if (list.isEmpty()) { st.inline = emptyList(); return false }
        val d = resources.displayMetrics.density
        val size = android.util.Size((260 * d).toInt(), (42 * d).toInt())
        val views = arrayOfNulls<android.view.View>(list.size)
        var left = list.size
        val main = ContextCompat.getMainExecutor(this)
        list.forEachIndexed { i, sug ->
            sug.inflate(this, size, main) { v ->
                views[i] = v
                if (--left == 0) st.inline = views.filterNotNull()
            }
        }
        return true
    }

    override fun slotLong(i: Int) {
        val w = slotWords.getOrNull(i).orEmpty()
        if (w.isEmpty() || w.startsWith("\"") || !w[0].isLetter()) return
        st.banAsk = i
        scope.launch { delay(3000); if (st.banAsk == i) st.banAsk = -1 }
    }

    override fun banSlot(i: Int) {
        val w = slotWords.getOrNull(i).orEmpty()
        st.banAsk = -1
        if (w.isEmpty()) return
        KeyboardDictionary.ban(this, w)
        android.widget.Toast.makeText(this, com.umutk.pulse.tr("Will not suggest \"$w\" again", "\"$w\" bir daha önerilmeyecek"), android.widget.Toast.LENGTH_SHORT).show()
        updateSuggestions()
    }

    override fun slot(i: Int) {
        st.banAsk = -1
        val c = currentInputConnection ?: return
        val w = slotWords.getOrNull(i).orEmpty()
        if (w.isEmpty()) return
        if (emailField) {
            val before = c.getTextBeforeCursor(100, 0)?.toString().orEmpty()
            if (w.startsWith("@") && '@' in before) c.deleteSurroundingText(before.length - before.lastIndexOf('@'), 0)
            c.commitText(w, 1); afterText(); return
        }
        val isEmoji = !w[0].isLetterOrDigit()
        val g = lastGlide
        if (word.isEmpty() && g != null && w in glideAlts) {
            c.deleteSurroundingText(g.length + 1, 0); c.commitText("$w ", 1); lastGlide = w; prevWord = w; return
        }
        if (word.isNotEmpty()) {
            val typed = word.toString()
            word.clear(); target = null
            // a tapped suggestion that is not a completion: this typo means that word from now on
            if (!isEmoji && KeyboardPrefs.learn && !st.plain && !st.incognito) KeyboardDictionary.learnTypo(this, typed.lowercase(lang.locale), w.lowercase(lang.locale))
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

    override fun deleteWord() {
        val c = currentInputConnection ?: return
        finishWord("", false); lastGlide = null; lastCorrection = null
        val before = c.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        if (before.isEmpty()) return
        var n = 0
        while (n < before.length && before[before.length - 1 - n].isWhitespace()) n++
        val wordStart = n
        while (n < before.length && (before[before.length - 1 - n].isLetterOrDigit() || before[before.length - 1 - n] == '\'')) n++
        if (n == wordStart) n++   // punctuation: one mark
        c.deleteSurroundingText(n, 0)
        afterText()
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
            "menu" -> st.panel = if (st.panel == "menu") "keys" else "menu"
            "clip" -> st.panel = if (st.panel == "clip") "keys" else "clip"
            "lang" -> lang()
            "voice" -> { st.panel = "keys"; voice() }
            "translate" -> { st.panel = "keys"; translate() }
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

    /** Hands the text of the field (the selection, else what is before the cursor) to the translator: the Google Translate app, or its page. */
    private fun translate() {
        val c = currentInputConnection ?: return
        val text = (c.getSelectedText(0)?.toString()?.takeIf { it.isNotBlank() } ?: c.getTextBeforeCursor(500, 0)?.toString().orEmpty()).trim()
        val url = "https://translate.google.com/?sl=auto&tl=${java.util.Locale.getDefault().language}&op=translate&text=" + android.net.Uri.encode(text)
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun hide() { finishWord("", false); requestHideSelf(0) }

    /** Hands the field over to the device's own voice typing (Google); the keyboard comes back when it is done. */
    override fun voice() {
        finishWord("", false)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        val voice = imm.enabledInputMethodList.firstOrNull { ime ->
            ime.packageName != packageName && (0 until ime.subtypeCount).any { ime.getSubtypeAt(it).mode == "voice" }
        } ?: imm.enabledInputMethodList.firstOrNull { it.id.contains("voicesearch", true) || it.id.contains("voice", true) && it.packageName != packageName }
        if (voice == null) { android.widget.Toast.makeText(this, if (java.util.Locale.getDefault().language == "tr") "Sesle yazma açık değil" else "Voice typing is off", android.widget.Toast.LENGTH_SHORT).show(); return }
        runCatching { switchInputMethod(voice.id) }
    }

    override fun feedback() {
        if (KeyboardPrefs.haptic) {
            // the strength slider: a short, soft pulse of its own instead of the system's key tap, which is strong on many phones
            val ms = KeyboardPrefs.hapticMs.coerceIn(1, 40)
            val vib = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (vib != null && vib.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= 26) vib.vibrate(android.os.VibrationEffect.createOneShot((4 + ms / 3).toLong(), if (vib.hasAmplitudeControl()) (12 + ms * 5).coerceAtMost(255) else android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                else @Suppress("DEPRECATION") vib.vibrate((4 + ms / 3).toLong())
            } else window.window?.decorView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (KeyboardPrefs.sound) (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1f)
    }

    override fun emoji(e: String) {
        val c = currentInputConnection ?: return
        finishWord("", false)
        c.commitText(e, 1)
        st.recents = (listOf(e) + st.recents.filter { it != e }).take(30)
        getSharedPreferences("pulse_keyboard", Context.MODE_PRIVATE).edit().putString("recents", st.recents.joinToString("\n")).apply()
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
        val found = dictCodes(st.lang).flatMap { KeyboardDictionary.glide(it, letters, 4) }.distinct().take(4)
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
        // Ctrl with A, C, X, V, Z, Y: the editor's own actions (a raw key chord is not understood by every text field, and some typed the letter after it)
        if (st.ctrl > 0 && st.alt == 0 && st.win == 0) {
            val act = when (ch.lowercaseChar()) {
                'a' -> "selectAll"; 'c' -> "copy"; 'x' -> "cut"; 'v' -> "paste"; 'y' -> "redo"
                'z' -> if (ch.isUpperCase() || st.shiftMod > 0) "redo" else "undo"
                else -> null
            }
            if (act != null) { edit(act); useModifiers(); if (st.shift == 1) st.shift = 0; return true }
        }
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

    private fun cased(w: String, typed: String): String {
        // a word that is English and not Turkish is capitalised the English way (if -> If, not İf)
        val loc = if (lang.locale.language == "tr" && w.none { it in "çğıöşü" } && KeyboardDictionary.isWord("en", w) && !KeyboardDictionary.isWord("tr", w)) Locale.ENGLISH else lang.locale
        return when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> w.uppercase(loc)
            typed.isNotEmpty() && typed[0].isUpperCase() -> w.replaceFirstChar { it.titlecase(loc) }
            else -> w
        }
    }

    private val domains = listOf("@gmail.com", "@outlook.com", "@hotmail.com", "@yahoo.com", "@icloud.com", "@yandex.com")
    private val emailRe = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

    /** An e-mail field: the addresses typed here before when it is empty, the usual domains once there is a name or an "@". */
    private fun emailSuggestions() {
        val before = currentInputConnection?.getTextBeforeCursor(100, 0)?.toString().orEmpty()
        if (emailRe.matches(before)) lastEmail = before
        val list = when {
            before.isEmpty() -> getSharedPreferences("pulse_kb_emails", MODE_PRIVATE).getString("list", "").orEmpty().split('\n').filter { it.isNotBlank() }.take(3)
            '@' in before -> domains.filter { it.startsWith("@" + before.substringAfterLast('@'), true) && it.length > before.substringAfterLast('@').length + 1 }.take(3)
            else -> domains.take(3)
        }
        slotWords = arrayOf(list.getOrElse(0) { "" }, list.getOrElse(1) { "" }, list.getOrElse(2) { "" })
        st.slots = slotWords.toList()
    }

    private fun updateSuggestions() {
        job?.cancel()
        if (emailField) { emailSuggestions(); return }
        if (!KeyboardPrefs.suggestions || st.plain) { st.slots = listOf("", "", ""); slotWords = arrayOf("", "", ""); target = null; return }
        val typed = word.toString(); val code = st.lang; val l = lang; val prev = prevWord; val prev2 = prevWord2; val up = st.shift > 0
        // the touch model needs the touch of every letter and the key positions; a word that was not touched letter by letter is judged without it
        val tc: KeyboardDictionary.TouchCtx? = if (typed.isNotEmpty() && touchPts.size >= typed.length && st.letterRects.isNotEmpty()) {
            val centers = HashMap<Char, androidx.compose.ui.geometry.Offset>()
            var w = 0f
            st.letterRects.entries.toList().forEach { (k, r) -> if (k.length == 1) { centers[k[0]] = r.center; w = r.width } }
            if (w > 0f) KeyboardDictionary.TouchCtx(touchPts.takeLast(typed.length), centers, w) else null
        } else null
        // the start of the field or of a sentence: the words a sentence begins with, before anything has been typed
        if (typed.isEmpty()) {
            val tb = currentInputConnection?.getTextBeforeCursor(3, 0)?.toString().orEmpty()
            if (tb.isEmpty() || tb.trimEnd(' ').let { it.isEmpty() || it.last() in ".!?\n" }) {
                val starts = if (KeyboardLayouts.dict(code) == "tr") listOf("Merhaba", "Ben", "Evet") else listOf("I", "The", "Hello")
                slotWords = arrayOf(starts[0], starts[1], starts[2]); st.slots = starts; target = null; return
            }
        }
        job = scope.launch {
            val res = withContext(Dispatchers.Default) { compute(code, l.locale, typed, prev, up, prev2, tc) }
            if (word.toString() == typed) { target = res.third; slotWords = res.second; st.slots = res.first }
        }
    }

    /** The three slots of the bar (shown and as words), and the word the space key would correct [typed] to. */
    private fun compute(code: String, loc: Locale, typed: String, prev: String?, up: Boolean, prev2: String? = null, tc: KeyboardDictionary.TouchCtx? = null): Triple<List<String>, Array<String>, String?> {
        if (typed.isEmpty()) {
            val n = KeyboardDictionary.next(code, prev, 6).filter { !KeyboardDictionary.isBanned(it) }.take(3).map { if (up) it.replaceFirstChar { c -> c.titlecase(loc) } else it }
            val w = arrayOf(n.getOrElse(0) { "" }, n.getOrElse(1) { "" }, n.getOrElse(2) { "" })
            return Triple(w.toList(), w, null)
        }
        val lower = typed.lowercase(loc)
        val codes = dictCodes(code)
        val comp = codes.flatMap { KeyboardDictionary.complete(it, lower, 6) }.sortedByDescending { it.score + KeyboardDictionary.pairBoost(prev, it.word, prev2) }.take(5)
        val maxCost = if (KeyboardLayouts.dict(code) == "tr") (if (lower.length >= 5) .8f else .65f) else if (lower.length >= 4) 1.3f else .7f
        val learned = KeyboardDictionary.typoFix(this, lower)
        val tr = codes.any { KeyboardDictionary.dictOf(it) == "tr" }
        // long words may hold more slips; Turkish words are also mended as a base word plus its endings
        // a word that is the chosen share alike (90% by default) to a known word is mended on its own: 12 letters allow one slip, 20 letters two
        val ratioLimit = ((100 - KeyboardPrefs.autoRatio) / 100f * maxOf(lower.length, 4)).coerceAtMost(1.2f)
        val limit = maxOf(if (lower.length >= 8) maxCost + .5f else maxCost, ratioLimit)
        val corr = if (lower.length >= 2) (codes.flatMap { KeyboardDictionary.correct(it, lower, limit, 5, tc) } + (if (tr) KeyboardDictionary.trCorrect(lower, 4, tc) else emptyList()) +
            (if (learned != null) listOf(KeyboardDictionary.Cand(learned, 40f, 0f)) else emptyList())).sortedByDescending { it.score + KeyboardDictionary.pairBoost(prev, it.word, prev2) }.distinctBy { it.word }.take(6) else emptyList()
        val valid = learned == null && codes.any { KeyboardDictionary.isWord(it, lower) }
        // the word the space key puts in: a near word, unless the typed one is already a word or too short to guess
        val fix = corr.firstOrNull { it.word != lower && !KeyboardDictionary.isBanned(it.word) }?.takeIf { lower.length >= 3 && it.cost <= limit && (!valid || it.score - (corr.firstOrNull { c -> c.word == lower }?.score ?: 0f) > 5f) }
        val others = (comp.map { it.word } + corr.map { it.word }).distinct().filter { it != lower && it != fix?.word && !KeyboardDictionary.isBanned(it) }
        val emoji = if (KeyboardPrefs.emojiPredictions) KeyboardDictionary.emoji(lower) else null
        val mid: String; val left: String; val right: String
        if (fix != null) { mid = cased(fix.word, typed); left = "\"$typed\""; right = emoji ?: others.firstOrNull()?.let { cased(it, typed) }.orEmpty() }
        else { mid = typed; left = others.getOrNull(0)?.let { cased(it, typed) }.orEmpty(); right = emoji ?: others.getOrNull(1)?.let { cased(it, typed) }.orEmpty() }
        val words = arrayOf(if (fix != null) typed else left, mid, right)
        return Triple(listOf(left, mid, right), words, fix?.let { cased(it.word, typed) })
    }
}
