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
        ExtraKeyDef("email", "@mail"), ExtraKeyDef("fn", "Fn"), ExtraKeyDef("selectAll", "Sel"), ExtraKeyDef("copy", "Copy"), ExtraKeyDef("cut", "Cut"), ExtraKeyDef("paste", "Paste"), ExtraKeyDef("undo", "Undo"), ExtraKeyDef("redo", "Redo"),
        // the Game layout's keys: real key codes (KeyEvent.KEYCODE_*), not typed letters, so a game reads them as the keyboard keys they are
        ExtraKeyDef("gW", "W", 51), ExtraKeyDef("gA", "A", 29), ExtraKeyDef("gS", "S", 47), ExtraKeyDef("gD", "D", 32),
        ExtraKeyDef("gQ", "Q", 45), ExtraKeyDef("gE", "E", 33), ExtraKeyDef("gSpace", "Space", 62), ExtraKeyDef("gShift", "Shift", 59),
        ExtraKeyDef("gCtrl", "Ctrl", 113), ExtraKeyDef("gEnter", "↵", 66),
    ) + (1..12).map { ExtraKeyDef("f$it", "F$it", 130 + it) }

    fun byId(id: String) = all.firstOrNull { it.id == id }
    const val OLD_DEFAULT = "esc,tab,ctrl,alt,win,left,down,up,right"
    const val DEFAULT = "esc,tab,ctrl,alt,win,shiftm,left,down,up,right"

    /** The tools the bar above the keys can hold: id and name. */
    val toolbar = listOf(
        "auth" to "Quick auth", "clip" to "Clipboard", "gamepad" to "Gamepad", "layout" to "Key layout", "emoji" to "Emoji", "translate" to "Translate", "selectAll" to "Select all", "copy" to "Copy", "cut" to "Cut", "paste" to "Paste", "undo" to "Undo", "redo" to "Redo",
        "menu" to "Menu", "email" to "E-mail @", "resize" to "Resize", "incognito" to "Incognito", "proRow" to "Extra keys", "keyset" to "Key set", "numbers" to "Numbers", "theme" to "Theme", "settings" to "Settings", "lang" to "Language",
    )
    /** The tiles the menu behind the three dots can hold. */
    val menu = listOf(
        "auth" to "Quick auth", "clip" to "Clipboard", "gamepad" to "Gamepad", "translate" to "Translate", "emoji" to "Emoji", "settings" to "Settings", "theme" to "Themes", "languages" to "Languages",
        "resize" to "Resize", "incognito" to "Incognito", "autocorrect" to "Autocorrect", "onehand" to "One-handed", "arrows" to "Key bar", "proRow" to "Extra keys", "keyset" to "Key set", "numbers" to "Numbers",
        "glide" to "Swipe typing", "lang" to "Language", "layout" to "Key layout",
    )
    /** What a long press on the comma, the period or the key beside ?123 can do (Settings > Keyboard): id and name; "none" keeps the key's own pop-up characters. */
    val hold = listOf(
        "none" to "None", "email" to "E-mail @", "voice" to "Voice typing", "clip" to "Clipboard", "gamepad" to "Gamepad", "layout" to "Key layout", "translate" to "Translate", "emoji" to "Emoji",
        "lang" to "Language", "menu" to "Menu", "settings" to "Settings", "theme" to "Theme", "resize" to "Resize", "incognito" to "Incognito",
    )
    const val DEFAULT_MENU = "gamepad,emoji,settings,theme,languages,resize,incognito,autocorrect,onehand,arrows,proRow,keyset,numbers,glide,layout"
    const val DEFAULT_TOOLBAR = "layout,clip,menu,arrows,translate,settings"
}
