package com.umutk.pulse.extras.keyboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** What the keyboard window shows: the keys, the bar above them and the pop-ups. The service fills it, the view draws it. */
class KbState {
    /** 0 off, 1 the next letter, 2 caps lock. */
    var shift by mutableIntStateOf(0)
    /** 0 letters, 1 symbols, 2 more symbols. */
    var page by mutableIntStateOf(0)
    /** keys, emoji, menu, clip, resize */
    var panel by mutableStateOf("keys")
    /** The height in pixels of the key panel as it is on screen: the emoji panel takes the same, so the keyboard keeps its size. */
    var keysH by mutableStateOf(0)
    /** The text typed in the emoji search (the keys type into it instead of the field); null when the search is not open. */
    var emojiQuery by mutableStateOf<String?>(null)
    var lang by mutableStateOf("en")
    /** Left, middle and right slot of the bar; the middle one is what the space key puts in. */
    var slots by mutableStateOf(listOf("", "", ""))
    // the translation bar: it takes the top bar, translates what is typed from one language to the other
    var trans by mutableStateOf(false)
    // naming a shortcut for a clipboard item: the keys type into the name
    var clipName by mutableStateOf<String?>(null)
    var clipNameFor by mutableStateOf(0L)
    var transFrom by mutableStateOf("tr")
    var transTo by mutableStateOf("en")
    var transOut by mutableStateOf("")
    var transBusy by mutableStateOf(false)
    /** The password manager's suggestions (autofill), drawn by the system inside the bar; empty when there are none. */
    var inline by mutableStateOf(listOf<android.view.View>())
    /** The letters typed so far in the word under way (shown as a quoted slot when it differs from the middle one). */
    var enter by mutableStateOf("newline")
    var dark by mutableStateOf(true)
    var incognito by mutableStateOf(false)
    var toolbar by mutableStateOf(false)
    var preview by mutableStateOf<KbPreview?>(null)
    /** The suggestion slot (0..2) asked "never suggest this again", waiting for the second tap; -1 none. */
    var banAsk by mutableStateOf(-1)
    var clips by mutableStateOf(listOf<String>())
    /** The kept clipboard: texts and pictures, pinned first. */
    var clipItems by mutableStateOf(listOf<ClipItem>())
    var recents by mutableStateOf(listOf<String>())
    /** The width of the keyboard's window in px, for keeping the pop-ups inside it. */
    var rootW by mutableStateOf(0f)
    /** The field wants no suggestions or learning (password, email, URL). */
    var plain by mutableStateOf(false)
    /** A number, phone, date or numeric-password field: only the number pad shows. */
    var numpad by mutableStateOf(false)
    /** The selection mode of the quick delete is on: the bar shows the selected text length. */
    var selecting by mutableStateOf(false)
    /** The modifier keys of the extra row: 0 off, 1 for the next key, 2 held until pressed again. */
    var ctrl by mutableIntStateOf(0)
    var alt by mutableIntStateOf(0)
    var win by mutableIntStateOf(0)
    var shiftMod by mutableIntStateOf(0)
    /** The finger's path while a word is swiped over the letters, in the window's pixels. */
    /** The finger of this touch swiped down to close the keyboard: the key under it must not type. */
    @Volatile var swiped = false
    var trail by mutableStateOf(listOf<Offset>())
    /** Where each letter key sits (window pixels), for following a swipe over the keys. */
    val letterRects = HashMap<String, Rect>()
    /** Where the finger last touched a letter key (screen coordinates): the touch model reads it when the letter is typed. */
    @Volatile var touch: Offset? = null
}

/** The pop-up over a pressed key: its big label, or the row of long-press characters with the chosen one. */
class KbPreview(val rect: Rect, val label: String, val alts: List<String> = emptyList(), val sel: Int = 0)

/** What the keys do. The service implements it; the settings page draws the keyboard with an empty one. */
interface KbActions {
    /** The translation bar: open, close, swap, from, to, apply. */
    fun transOp(op: String) {}
    /** Clipboard shortcuts: start naming one for an item, save it, cancel. */
    fun clipShortcut(id: Long) {}
    fun clipShortcutSave() {}
    fun clipShortcutCancel() {}
    fun type(s: String)
    fun backspace()
    fun enter()
    fun space()
    fun shift()
    fun page(p: Int)
    fun emojiPanel()
    fun lang()
    fun slot(i: Int)
    fun arrow(code: Int)
    fun edit(id: String)
    fun moveCursor(delta: Int)
    fun quickDeleteStart()
    fun quickDelete(chars: Int)
    fun quickDeleteEnd(apply: Boolean)
    fun menu(id: String)
    fun feedback()
    fun emoji(e: String)
    fun paste(s: String)
    fun glide(letters: String)
    fun extraKey(id: String)
    fun hide()
    fun clipPin(id: Long) {}
    fun clipDelete(ids: Set<Long>) {}
    fun clipClear() {}
    fun pasteClip(item: ClipItem) {}
    /** A long press on a suggestion: it turns into the "never suggest again" button. */
    fun slotLong(i: Int) {}
    fun banSlot(i: Int) {}
    /** Deletes the word before the cursor (the held delete key, after the first letters). */
    fun deleteWord() {}
    /** Voice typing of the device (the Google voice input). */
    fun voice()
}

/** Does nothing: the preview in the settings. */
object NoKbActions : KbActions {
    override fun type(s: String) {}
    override fun backspace() {}
    override fun enter() {}
    override fun space() {}
    override fun shift() {}
    override fun page(p: Int) {}
    override fun emojiPanel() {}
    override fun lang() {}
    override fun slot(i: Int) {}
    override fun arrow(code: Int) {}
    override fun edit(id: String) {}
    override fun moveCursor(delta: Int) {}
    override fun quickDeleteStart() {}
    override fun quickDelete(chars: Int) {}
    override fun quickDeleteEnd(apply: Boolean) {}
    override fun menu(id: String) {}
    override fun feedback() {}
    override fun emoji(e: String) {}
    override fun paste(s: String) {}
    override fun glide(letters: String) {}
    override fun extraKey(id: String) {}
    override fun hide() {}
    override fun voice() {}
}

/** The keyboard of the settings page: the panels open and close so a change of the menu or the keys can be seen at once. */
class PreviewKbActions(private val st: KbState) : KbActions by NoKbActions {
    override fun menu(id: String) { when (id) { "menu", "clip", "resize" -> st.panel = if (st.panel == id) "keys" else id; "back" -> st.panel = "keys"; "emoji" -> st.panel = "emoji" } }
    override fun emojiPanel() { st.panel = if (st.panel == "emoji") "keys" else "emoji" }
    override fun page(p: Int) { st.page = p; st.panel = "keys" }
    override fun shift() { st.shift = (st.shift + 1) % 3 }
}
