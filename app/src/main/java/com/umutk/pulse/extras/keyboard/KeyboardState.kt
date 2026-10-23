package com.umutk.pulse.extras.keyboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/** What the keyboard window shows: the keys, the bar above them and the pop-ups. The service fills it, the view draws it. */
class KbState {
    /** 0 off, 1 the next letter, 2 caps lock. */
    var shift by mutableIntStateOf(0)
    /** 0 letters, 1 symbols, 2 more symbols. */
    var page by mutableIntStateOf(0)
    /** keys, emoji, menu, clip, resize */
    var panel by mutableStateOf("keys")
    var lang by mutableStateOf("en")
    /** Left, middle and right slot of the bar; the middle one is what the space key puts in. */
    var slots by mutableStateOf(listOf("", "", ""))
    /** The letters typed so far in the word under way (shown as a quoted slot when it differs from the middle one). */
    var enter by mutableStateOf("newline")
    var dark by mutableStateOf(true)
    var incognito by mutableStateOf(false)
    var toolbar by mutableStateOf(false)
    var preview by mutableStateOf<KbPreview?>(null)
    var clips by mutableStateOf(listOf<String>())
    var recents by mutableStateOf(listOf<String>())
    /** The width of the keyboard's window in px, for keeping the pop-ups inside it. */
    var rootW by mutableStateOf(0f)
    /** The field wants no suggestions or learning (password, email, URL). */
    var plain by mutableStateOf(false)
    /** The selection mode of the quick delete is on: the bar shows the selected text length. */
    var selecting by mutableStateOf(false)
}

/** The pop-up over a pressed key: its big label, or the row of long-press characters with the chosen one. */
class KbPreview(val rect: Rect, val label: String, val alts: List<String> = emptyList(), val sel: Int = 0)

/** What the keys do. The service implements it; the settings page draws the keyboard with an empty one. */
interface KbActions {
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
}
