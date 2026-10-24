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
        'a' to "àáâäãåāæąă", 'e' to "èéêëēęėě", 'i' to "ìíîïīįı", 'o' to "òóôöõøœōő", 'u' to "ùúûüūůűų", 'c' to "çćčĉ", 'n' to "ñńň", 's' to "śšßşŝ", 'y' to "ýÿŷ", 'z' to "žźż",
        'l' to "łľĺ", 'd' to "ðďđ", 'g' to "ğĝ", 't' to "ťþţ", 'r' to "řŕ", 'w' to "ŵ", 'h' to "ĥħ", 'j' to "ĵ"))
    private val tr = Lang("tr", "Türkçe", listOf("qwertyuıopğü", "asdfghjklşi", "zxcvbnmöç"), Locale("tr", "TR"), mapOf(
        'a' to "âáàäãåā", 'e' to "éêèëēę", 'i' to "îíìïī", 'ı' to "î", 'o' to "ôóòõøœ", 'u' to "ûúùūů", 'c' to "çćč", 'g' to "ğĝ", 's' to "şśšß", 'n' to "ñń", 't' to "ţť", 'z' to "žźż", 'y' to "ýÿ", 'l' to "ł", 'd' to "đð", 'r' to "ř"))
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
                // the element symbols of the letter, with their capital first letter, join the capital letter's long press
                val chem = if (upper && KeyboardPrefs.chemKeys) chemical[ch.toString()].orEmpty() else emptyList()
                KbKey(cap(ch.toString(), l, upper), KeyKind.CHAR, 1f, (if (upper) alts.map { cap(it, l, true) } else alts) + chem)
            }
        }
    }

    /** Chemical element symbols by first letter (kept in their own case, whatever Shift does). */
    private val chemical: Map<String, List<String>> = mapOf(
        "a" to "Ac Ag Al Am Ar As At Au", "b" to "B Ba Be Bh Bi Bk Br", "c" to "C Ca Cd Ce Cf Cl Cm Cn Co Cr Cs Cu", "d" to "Db Ds Dy", "e" to "Er Es Eu", "f" to "F Fe Fl Fm Fr",
        "g" to "Ga Gd Ge", "h" to "H He Hf Hg Ho Hs", "i" to "I In Ir", "k" to "K Kr", "l" to "La Li Lr Lu Lv", "m" to "Mc Md Mg Mn Mo Mt", "n" to "N Na Nb Nd Ne Nh Ni No Np",
        "o" to "O Og Os", "p" to "P Pa Pb Pd Pm Po Pr Pt Pu", "r" to "Ra Rb Re Rf Rg Rh Rn Ru", "s" to "S Sb Sc Se Sg Si Sm Sn Sr", "t" to "Ta Tb Tc Te Th Ti Tl Tm Ts",
        "u" to "U", "v" to "V", "w" to "W", "x" to "Xe", "y" to "Y Yb", "z" to "Zn Zr",
    ).mapValues { it.value.split(' ') }

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
