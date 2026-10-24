#!/usr/bin/env python3
"""Builds app/src/main/assets/kbd/symbols.txt: the keyboard's symbol tab. Lines: "#Family" then "char<TAB>unicode name". Run: python3 tools/gen_symbols.py"""
import unicodedata as ud, sys

def w(v): return v.split()
CURATED = [
 ("Flowers", w("✿ ❀ ❁ ❃ ❋ ✾ ✽ ⚘ ☘ ❦ ❧ ⚜ 🌸 🌺 🌼 🌻 🌷 🌹 🥀 🍀 🌿 🍃 🌱 🌾 ꕥ ꕤ 𓆸 𓇼 ⚛ ❂ ✤ ✥ ❊ ❉ ❈ ✣ ✢")),
 ("Hearts", w("♡ ♥ ❤ ❥ ❣ ❦ ❧ ღ ❢ ☙ ♡︎ ❤︎ 💗 💖 💕 💞 💓 💘 💝 🤍 🖤 💜 💙 💚 💛 🧡 ❤️‍🔥 ꒰ა ໒꒱ ⸜♡⸝ 𐙚 ᥫ᭡ 💌")),
 ("Stars", w("✦ ✧ ⋆ ★ ☆ ✩ ✪ ✫ ✬ ✭ ✮ ✯ ✰ ⊹ ˖ ⁺ ˚ ₊ ⟡ ✶ ✷ ✸ ✹ ✺ ❇ ❆ ❅ ❄ ⭐ 🌟 ✨ 💫 ⚝ ⍟ ⛤ ⛥ ⛦ ⛧ ⋆˚ ₊˚ ✧˖ ˙ ･ ｡ ﾟ ‧ ⸜ ⸝")),
 ("Cute", w("ᡣ𐭩 ౨ৎ ʚ ɞ ᰔ ᥫ ᭡ ᯓ 𖦹 ៹ ࿐ ࿔ ᨳ ⌒ ᵎ ꜣ ꩜ ⸙ ⸸ ꕀ ⁂ ※ ⁕ ⁎ ✻ ✼ ꒰ ꒱ ໒ ა ˶ ᵔ ᵕ ݁ ༘ ࣪ 𓂃 𓃠 𓆩 𓆪 ᴗ ⁀ ‿ ︶ ෴ ༻ ༺ ࿙ ࿚ ˳ ˑ ˙ ᵒ ᴥ ₍ ₎ ᐢ ᐟ ᐠ ᐣ ᕱ ᕕ ᕗ ෆ ᰔ ꕁ ꗃ ꕭ ꕮ ꕯ")),
 ("Moon and sky", w("☾ ☽ ☀ ☁ ☂ ☃ ☄ ☼ ⛅ ⛈ ❄ ❅ ❆ 🌙 🌛 🌜 ⚡ ☔ 🌈 🌠 ☮ ☯ ☸ ♨ ꕤ 𓆝 𓆟 𓆞")),
 ("Lines", w("─ ━ ┄ ┅ ┈ ┉ ═ ╌ ╍ ╴ ╶ ⎯ ⏤ ﹏ ﹋ ﹌ ∼ ～ 〜 ෴ ‿ ︶ ⁀ ⏝ ⋆⋅⋆ ꕀ ᯓ ◦•◦ ··· ･ﾟ ⋰ ⋱ ┈┈ ━━ ⎼ ╾╼ ⸺ ⸻ ⌇ ⌁ ⍝")),
 ("Brackets", w("「」 『』 【】 〔〕 〈〉 《》 〖〗 〘〙 〚〛 ⟦⟧ ⦅⦆ ❨❩ ❪❫ ❬❭ ❮❯ ❰❱ ❲❳ ❴❵ ⌈⌉ ⌊⌋ ︽︾ ﹝﹞ ◖◗ ⸢⸣ ⸤⸥ ‹› «» ⟨⟩ ⟪⟫ ꒰꒱ ₍₎ ⁽⁾ ༺༻ ᕙᕗ")),
]
FAM = [
 ("Arrows", [(0x2190,0x21FF),(0x27F0,0x27FF),(0x2900,0x297F)]),
 ("Shapes", [(0x25A0,0x25FF),(0x2500,0x259F),(0x1F780,0x1F7FF)]),
 ("Music", [(0x1D100,0x1D1FF),(0x2669,0x266F)]),
 ("Symbols", [(0x2600,0x27BF),(0x2300,0x23FF),(0x2B00,0x2BFF),(0xA9,0xAE),(0xA7,0xA7),(0xB6,0xB6),(0xB0,0xB0)]),
 ("Numbers", [(0x2070,0x209F),(0x2150,0x218F),(0x2460,0x24FF),(0x1F100,0x1F1E5),(0xB2,0xB3),(0xB9,0xBC),(0xBD,0xBE)]),
 ("Punctuation", [(0x2010,0x2064),(0x2E00,0x2E7F),(0x3001,0x303F),(0xA1,0xA1),(0xBF,0xBF),(0xAB,0xAB),(0xBB,0xBB)]),
 ("Currency", [(0x20A0,0x20CF),(0xA2,0xA5),(0x24,0x24)]),
 ("Math", [(0x2200,0x22FF),(0x27C0,0x27EF),(0x2980,0x29FF),(0x2A00,0x2AFF),(0x2100,0x214F),(0xB1,0xB1),(0xD7,0xD7),(0xF7,0xF7)]),
 ("Braille", [(0x2800,0x28FF)]),
]
SKIP_REGIONAL = range(0x1F1E6, 0x1F200)
# emoji live in the emoji tabs: the symbol tab holds the plain text signs only
EMOJI_LIKE = set("⛅⛈⚡☔⭐⌚⌛✅❌❓❗❤️")
def is_emoji(it):
    return any(0x1F000 <= ord(c) <= 0x1FAFF or c == "\uFE0F" or c == "\u200D" for c in it) or it in EMOJI_LIKE
out = []
seen = set()
def nm(ch):
    try: return ud.name(ch[0]).lower()
    except ValueError: return "decoration"
for name, items in CURATED:
    rows = []
    for it in items:
        # a cell is one sign or a short sequence; emoji stay whole, "ᡣ𐭩" stays a pair
        if it in seen or is_emoji(it): continue
        rows.append((it, nm(it))); seen.add(it)
    out.append("#" + name); out += [f"{c}\t{n}" for c, n in rows]
for name, ranges in FAM:
    rows = []
    for a, b in ranges:
        for cp in range(a, b + 1):
            if cp in SKIP_REGIONAL: continue
            ch = chr(cp); cat = ud.category(ch)
            if not (cat[0] in "SP" or cat in ("No", "Nl")): continue
            try: n = ud.name(ch).lower()
            except ValueError: continue
            if ch in seen or is_emoji(ch): continue
            rows.append((ch, n)); seen.add(ch)
    out.append("#" + name); out += [f"{c}\t{n}" for c, n in rows]
open("app/src/main/assets/kbd/symbols.txt", "w", encoding="utf-8").write("\n".join(out) + "\n")
print(sum(1 for l in out if not l.startswith("#")), "signs")
