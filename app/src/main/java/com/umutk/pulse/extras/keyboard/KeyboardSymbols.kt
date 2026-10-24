package com.umutk.pulse.extras.keyboard

/** The decorative signs of the emoji page's last tab ("cute symbols"), in groups: name in English and Turkish, and the signs. */
object KeyboardSymbols {
    private fun s(v: String) = v.split(' ').filter { it.isNotEmpty() }

    val groups: List<Triple<String, String, List<String>>> = listOf(
        Triple("Sparkles", "Parıltı", s("✦ ✧ ⋆ ★ ☆ ✩ ✪ ✫ ✬ ✭ ✮ ✯ ✰ ⊹ ˖ ⁺ ˚ ₊ ⟡ ✶ ✷ ✸ ✹ ✺ ❂ ❈ ❉ ❊ ❋ ✢ ✣ ✤ ✥ ✱ ✲ ✳ ✴ ✵ ❇ ❆ ❅ ❄ ⋆˚ ₊˚ ✧˖ ˙ ･ ｡ ﾟ ⸜ ⸝ ˚₊ ‧ ₊⊹ ⋆｡˚")),
        Triple("Hearts", "Kalpler", s("♡ ♥ ❤ ❥ ❣ ❦ ❧ ღ ❢ ☙ ♡︎ ❤︎ ♥︎ 𐙚 ꒰ა ໒꒱ ᥫ᭡ ꕤ ꕥ ⸜♡⸝ ♡⃛ ❦ ❧")),
        Triple("Cute marks", "Süs", s("ᡣ𐭩 ౨ৎ ˚ ʚɞ ᰔ ᥫ᭡ ₍ᐢ..ᐢ₎ ࣪ ⟢ ꒰ ꒱ ໒ ა ˶ ᵔ ᵕ ˙ ݁ ༘ ⋆ ˖ ᯓ ᯓ★ ⊹ ࣪ 𖦹 ౨ৎ˚₊ ᡣ𐭩 ៹ ཾ ࿐ ࿔ ᨳ ⌒ ᵎ ꜣ ₊˚ ᴗ ᵔᴥᵔ ꩜ ⸙ ⸸ ꕀ ⁂ ※ ⁕ ⁎ ✻ ✼ ✽ ❃")),
        Triple("Flowers and sky", "Çiçek ve gökyüzü", s("✿ ❀ ❁ ❃ ❋ ✾ ✽ ⚘ ❦ ☘ ⚜ ☾ ☽ ☀ ☁ ☂ ☃ ☄ ☼ ☮ ☯ ☸ ♨ ❄ ❅ ❆ 𓆸 𓆩 𓆪 𓂃 𓃠 𓅪 𓆝 𓇼 ⛅ ⛈ 〰")),
        Triple("Arrows", "Oklar", s("→ ← ↑ ↓ ↔ ↕ ↖ ↗ ↘ ↙ ⇒ ⇐ ⇑ ⇓ ⇔ ➜ ➔ ➤ ➳ ➵ ➸ ➼ ↪ ↩ ↺ ↻ ⟲ ⟳ ⤴ ⤵ ➥ ➦ ➧ ➨ ⇢ ⇠ ⟶ ⟵ ⟷ ↣ ↢ ↝ ↜ ⇝ ⇜ ➙ ➛ ➝ ➞ ➟ ➠ ➡ ⬅ ⬆ ⬇ ⬈ ⬉ ⬊ ⬋")),
        Triple("Brackets", "Parantez", s("「」 『』 【】 〔〕 〈〉 《》 〖〗 〘〙 〚〛 ⟦⟧ ⦅⦆ ❨❩ ❪❫ ❬❭ ❮❯ ❰❱ ❲❳ ❴❵ ⌈⌉ ⌊⌋ ︽︾ ﹝﹞ ◖◗ ⸢⸣ ⸤⸥ ‹› «» ⟨⟩ ⟪⟫ ꒰꒱ ₍₎ ⁽⁾ ༺༻ ᕙᕗ")),
        Triple("Lines", "Çizgi", s("─ ━ ┄ ┅ ┈ ┉ ═ ╌ ╍ ╴ ╶ ⎯ ⏤ ﹏ ﹋ ﹌ ∼ ～ 〜 ෴ ‿ ︶ ⁀ ⏝ ༻ ༺ ⋆⋅⋆ ꕀ ᯓ ࿙ ࿚ ◦•◦ •• ··· ･ﾟ ⋰ ⋱ ┈┈ ━━ ⎼ ╾╼ ⸺ ⸻")),
        Triple("Shapes", "Şekiller", s("● ○ ◉ ◎ ◌ ◍ ◐ ◑ ◒ ◓ ◔ ◕ ■ □ ▢ ▣ ▤ ▥ ▦ ▧ ▨ ▩ ▪ ▫ ▬ ▲ △ ▴ ▵ ▶ ▷ ▸ ▹ ► ▻ ▼ ▽ ▾ ▿ ◀ ◁ ◂ ◃ ◆ ◇ ◈ ◊ ◦ ‣ • ⁃ ⬟ ⬠ ⬡ ⬢ ⬣ ⯁ ⯂")),
        Triple("Music", "Müzik", s("♪ ♫ ♬ ♩ ♭ ♮ ♯ 𝄞 𝄢 ♪♪ ⏮ ⏭ ⏯ ⏺ ⏹ ⏸ ◁◁ ▷▷ ⇄ ⇆ ⟲")),
        Triple("Marks", "İşaretler", s("✓ ✔ ✕ ✖ ✗ ✘ ☑ ☒ ☐ ✂ ✈ ✉ ✎ ✐ ✑ ✒ ☎ ☏ ☠ ☢ ☣ ☺ ☻ ☹ ✌ ✍ ⚠ ⚡ ☔ ⌛ ⌚ ⏰ ⚓ ⚙ ⚖ ⚔ ⚑ ⚐ © ® ™ § ¶ † ‡ ‰ № ℗ ℃ ℉")),
        Triple("Numbers", "Sayılar", s("⁰ ¹ ² ³ ⁴ ⁵ ⁶ ⁷ ⁸ ⁹ ₀ ₁ ₂ ₃ ₄ ₅ ₆ ₇ ₈ ₉ ⁺ ⁻ ⁼ ₊ ₋ ₌ ① ② ③ ④ ⑤ ⑥ ⑦ ⑧ ⑨ ⑩ ❶ ❷ ❸ ❹ ❺ ❻ ❼ ❽ ❾ ❿ ½ ⅓ ¼ ¾ ⅔")),
        Triple("Math", "Matematik", s("± × ÷ ≠ ≈ ≤ ≥ ∞ √ ∑ ∏ π ∆ ° µ Ω ∂ ∫ ∈ ∉ ∩ ∪ ⊂ ⊃ ⊕ ⊗ ∴ ∵ ≡ ≅ ∝ ∠ ⊥ ∥ ¬ ∧ ∨")),
    )
}
