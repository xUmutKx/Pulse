package com.umutk.pulse.extras.keyboard

import java.util.Locale

enum class KeyKind { CHAR, SHIFT, DELETE, ENTER, SPACE, MODE, EMOJI, COMMA, PERIOD, LANG, SYMBOLS2 }

/** One key: its [label] (what it shows and types), the characters of its long press ([alts]) and its width ([weight]). */
class KbKey(val label: String, val kind: KeyKind = KeyKind.CHAR, val weight: Float = 1f, val alts: List<String> = emptyList())

object KeyboardLayouts {
    /** English and Turkish (QWERTY) only. Language code -> its name, rows of letters and the long-press characters. */
    class Lang(val code: String, val name: String, val rows: List<String>, val locale: Locale, val alts: Map<Char, String>)

    private const val DIGITS = "1234567890"

    private val en = Lang("en", "English", listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"), Locale.ENGLISH, mapOf(
        'a' to "àáâäãåāæ", 'e' to "èéêëēę", 'i' to "ìíîïī", 'o' to "òóôöõøœō", 'u' to "ùúûüū", 'c' to "çćč", 'n' to "ñń", 's' to "śšß", 'y' to "ýÿ", 'z' to "žźż", 'l' to "ł", 'd' to "ð"))
    private val tr = Lang("tr", "Türkçe", listOf("qwertyuıopğü", "asdfghjklşi", "zxcvbnmöç"), Locale("tr", "TR"), mapOf(
        'a' to "âáà", 'e' to "éêè", 'i' to "îíı", 'ı' to "î", 'o' to "ôó", 'u' to "ûú", 'c' to "ç", 'g' to "ğ", 's' to "ş", 'n' to "ñ"))
    // the Turkish F keyboard: the letters in the order of Turkish, the common ones under the fingers
    private val trf = Lang("trf", "Türkçe F", listOf("fgğıodrnhpqw", "uieaütkmlyş", "jövcçzsbx"), Locale("tr", "TR"), tr.alts)
    private val de = Lang("de", "Deutsch", listOf("qwertzuiop", "asdfghjkl", "yxcvbnm"), Locale.GERMAN, mapOf(
        'a' to "äàáâã", 'o' to "öòóôõ", 'u' to "üùúû", 's' to "ß", 'e' to "éèêë", 'i' to "íìîï", 'c' to "ç", 'n' to "ñ"))
    private val fr = Lang("fr", "Français", listOf("azertyuiop", "qsdfghjklm", "wxcvbn"), Locale.FRENCH, mapOf(
        'a' to "àâæáä", 'e' to "éèêë", 'i' to "îïíì", 'o' to "ôœòó", 'u' to "ùûüú", 'c' to "ç", 'y' to "ÿ", 'n' to "ñ"))
    private val es = Lang("es", "Español", listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm"), Locale("es", "ES"), mapOf(
        'a' to "áàäâ", 'e' to "éèëê", 'i' to "íìïî", 'o' to "óòöô", 'u' to "úùüû", 'n' to "ñ", 'c' to "ç"))

    val all = listOf(en, tr)
    val languages: Set<String> = all.map { it.code }.toSet()
    /** The word list a layout uses: both Turkish layouts share one. */
    fun dict(code: String) = if (code == "trf") "tr" else code

    fun lang(code: String): Lang = all.firstOrNull { it.code == code } ?: en

    /** The letter rows of [code] as keys; [upper] gives capital letters. Without the number row the top letters carry their digit on long press. */
    fun letterRows(code: String, upper: Boolean, digitsOnPress: Boolean): List<List<KbKey>> {
        val l = lang(code)
        return l.rows.mapIndexed { r, row ->
            row.mapIndexed { i, ch ->
                val alts = buildList {
                    if (digitsOnPress && r == 0 && i < DIGITS.length) add(DIGITS[i].toString())
                    l.alts[ch]?.forEach { add(it.toString()) }
                }
                KbKey(cap(ch.toString(), l, upper), KeyKind.CHAR, 1f, if (upper) alts.map { cap(it, l, true) } else alts)
            }
        }
    }

    fun cap(s: String, l: Lang, upper: Boolean) = if (upper) s.uppercase(l.locale) else s

    /** The symbol pages: the first with digits and the common signs, the second with the rest. Rows of single characters. */
    val symbols1 = listOf("1234567890", "@#$&_-()=%", "\"*':/!?+")
    val symbols2 = listOf("~`|•√π÷×¶∆", "£¢€¥^°={}\\", "%©®™✓[]<>")

    val popupPeriod = listOf(",", "?", "!", ":", ";", "-", "'", "\"", "@", "/", "…")
    val popupComma = listOf(";", ":", "'", "\"")
    val symbolAlts = mapOf(
        '-' to listOf("–", "—", "•"), '\'' to listOf("‘", "’"), '"' to listOf("“", "”", "«", "»"), '?' to listOf("¿"), '!' to listOf("¡"),
        '$' to listOf("€", "£", "¥"), '€' to listOf("$", "£", "¥"), '0' to listOf("°", "⁰", ")"), '(' to listOf("[", "{", "<"), ')' to listOf("]", "}", ">"),
        '1' to listOf("¹", "!", "½"), '2' to listOf("²", "@", "⅔"), '3' to listOf("³", "#", "¾"), '4' to listOf("⁴", "$", "¼"), '5' to listOf("⁵", "%"), '6' to listOf("⁶", "^"), '7' to listOf("⁷", "&"), '8' to listOf("⁸", "*"), '9' to listOf("⁹", "("),
        '&' to listOf("§"), '%' to listOf("‰", "€", "$", "£", "¥"), '=' to listOf("≠", "≈"), '+' to listOf("±"), '/' to listOf("\\"), '*' to listOf("†", "‡"),
    )

    /** Emoji panel groups: the first character group is the emoji of the group's tab. */
    val emoji: List<Pair<String, List<String>>> = listOf(
        "😀" to "😀 😃 😄 😁 😆 😅 😂 🤣 🥲 ☺️ 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🥸 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 ☹️ 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕".split(' '),
        "👍" to "👍 👎 👌 🤌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐️ 🖖 👋 🤝 🙏 ✍️ 💪 🦾 🙌 👏 🤲 🫶 👐 🤜 🤛 ✊ 👊".split(' '),
        "❤️" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❤️‍🔥 💕 💞 💓 💗 💖 💘 💝 💟 ♥️ 💋 💯 ✨ ⭐ 🌟 💫 🔥 💥 🎉 🎊".split(' '),
        "🐶" to "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🦋 🐌 🐞 🐢 🐍 🐙 🦀 🐠 🐬 🐳 🦈 🌸 🌹 🌻 🌲 🍀".split(' '),
        "🍕" to "🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑 🌽 🥕 🍞 🧀 🍗 🍖 🍔 🍟 🍕 🌭 🌮 🌯 🍝 🍜 🍣 🍤 🍩 🍪 🎂 🍰 🍫 🍬 🍭 ☕ 🍵 🍺 🍷 🥂".split(' '),
        "⚽" to "⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🥊 🎯 🎮 🎲 🎸 🎹 🎧 🎤 🎬 🚗 🚕 🚌 🚓 🚑 🚒 ✈️ 🚀 🚁 ⛵ 🚲 🏠 🏝️ 🌍 🌙 ☀️ ⛅ ☔ ⚡ ❄️".split(' '),
        "💡" to "📱 💻 ⌨️ 🖥️ 🖨️ 📷 🎥 📺 📻 ⏰ 💡 🔋 🔌 💰 💳 📦 📌 📎 ✂️ 🔒 🔑 🔨 🔧 ⚙️ 🧲 📚 ✏️ 📝 📅 ✅ ❌ ❓ ❗ ⚠️ 🚫 ♻️ 🏳️ 🏴".split(' '),
    )
}
