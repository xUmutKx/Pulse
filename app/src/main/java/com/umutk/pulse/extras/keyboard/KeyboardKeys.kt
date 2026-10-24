package com.umutk.pulse.extras.keyboard

/** A key of the extra row (Esc, Tab, Ctrl, Alt, Win, arrows, F-keys, ...): [code] is its Android key code, [mod] marks the modifier keys that wait for the next key. */
class ExtraKeyDef(val id: String, val label: String, val code: Int = 0, val mod: Boolean = false, val repeat: Boolean = false)

object ExtraKeys {
    val all = listOf(
        ExtraKeyDef("esc", "Esc", 111), ExtraKeyDef("tab", "Tab", 61),
        ExtraKeyDef("ctrl", "Ctrl", mod = true), ExtraKeyDef("alt", "Alt", mod = true), ExtraKeyDef("win", "Win", mod = true), ExtraKeyDef("shiftm", "Shift", mod = true),
        ExtraKeyDef("left", "←", 21, repeat = true), ExtraKeyDef("down", "↓", 20, repeat = true), ExtraKeyDef("up", "↑", 19, repeat = true), ExtraKeyDef("right", "→", 22, repeat = true),
        ExtraKeyDef("home", "Home", 122), ExtraKeyDef("end", "End", 123), ExtraKeyDef("pgup", "PgUp", 92, repeat = true), ExtraKeyDef("pgdn", "PgDn", 93, repeat = true),
        ExtraKeyDef("del", "Del", 112, repeat = true), ExtraKeyDef("ins", "Ins", 124),
        ExtraKeyDef("selectAll", "Sel"), ExtraKeyDef("copy", "Copy"), ExtraKeyDef("cut", "Cut"), ExtraKeyDef("paste", "Paste"), ExtraKeyDef("undo", "Undo"), ExtraKeyDef("redo", "Redo"),
    ) + (1..12).map { ExtraKeyDef("f$it", "F$it", 130 + it) }

    fun byId(id: String) = all.firstOrNull { it.id == id }
    const val OLD_DEFAULT = "esc,tab,ctrl,alt,win,left,down,up,right"
    const val DEFAULT = "esc,tab,ctrl,alt,win,shiftm,left,down,up,right"

    /** The tools the bar above the keys can hold: id and name. */
    val toolbar = listOf(
        "clip" to "Clipboard", "emoji" to "Emoji", "voice" to "Voice typing", "translate" to "Translate", "selectAll" to "Select all", "copy" to "Copy", "cut" to "Cut", "paste" to "Paste", "undo" to "Undo", "redo" to "Redo",
        "menu" to "Menu", "resize" to "Resize", "incognito" to "Incognito", "arrows" to "Extra keys", "numbers" to "Numbers", "theme" to "Theme", "settings" to "Settings", "lang" to "Language",
    )
    /** The tiles the menu behind the three dots can hold. */
    val menu = listOf(
        "clip" to "Clipboard", "translate" to "Translate", "voice" to "Voice typing", "emoji" to "Emoji", "settings" to "Settings", "theme" to "Themes", "languages" to "Languages",
        "resize" to "Resize", "incognito" to "Incognito", "autocorrect" to "Autocorrect", "onehand" to "One-handed", "arrows" to "Extra keys", "numbers" to "Numbers",
        "glide" to "Swipe typing", "lang" to "Language",
    )
    const val DEFAULT_MENU = "clip,translate,voice,emoji,settings,theme,languages,resize,incognito,autocorrect,onehand,arrows,numbers,glide"
    const val DEFAULT_TOOLBAR = "clip,emoji,voice,translate,menu"
}
