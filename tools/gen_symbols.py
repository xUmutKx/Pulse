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
COMBOS_HAND = [
 "˖ ᡣ𐭩 ⊹ ࣪ ౨ৎ˚₊", "⋆˚࿔ ⊹ ˖", "₊˚⊹ ♡ ⊹˚₊", "˚₊‧꒰ა ♡ ໒꒱ ‧₊˚", "ᡣ𐭩 ౨ৎ ˚ ₊", "⋆｡°✩ ₊˚", "｡ﾟ☆: *.☽ .* :☆ﾟ.", "⋆.˚ ✮ ⋆.˚", "✧˖°.", "𓂃 ࣪ ִֶָ ☾.", "ᯓ★", "ᯓ ✈︎",
 "꒰ ᵔ ᵕ ᵔ ꒱", "₍ᐢ. .ᐢ₎", "૮ ˶ᵔ ᵕ ᵔ˶ ა", "(˶ᵔ ᵕ ᵔ˶)", "ฅ^•ﻌ•^ฅ", "ʕ•ᴥ•ʔ", "ᓚᘏᗢ", "૮₍ ˶•⤙•˶ ₎ა", "૮ ˘ᴥ˘ ა", "₍⸍⸌̣ʷ̣̫⸍̣⸌₎",
 "‧₊˚ ☁︎ ⋅ ♡ 𓂃", "⋆⁺₊⋆ ☾ ⋆⁺₊⋆", "⋆ ˚｡⋆୨୧˚", "୨୧ ┈┈┈┈┈┈┈┈ ୨୧", "⌒⌒⌒⌒⌒", "⸜(｡˃ ᵕ ˂ )⸝♡", "ʚ ♡ ɞ", "ʚ♡⃛ɞ", "ꕤ ⋆ ꕤ", "✦ ━━━━━━ ✦",
 "▬▬▬▬▬▬▬▬▬▬", "═════ ❖ ═════", "╰┈➤", "┈┈┈┈┈┈┈┈┈┈┈┈", "──────⋆⋅☆⋅⋆──────", "•·.·´¯`·.·• •·.·´¯`·.·•", "｡･:*:･ﾟ★,｡･:*:･ﾟ☆", "☆*:.｡. o(≧▽≦)o .｡.:*☆",
 "『 』", "「 」", "【 】", "꧁ ꧂", "༺ ༻", "【・】", "⟦ ⟧", "❝ ❞", "❛ ❜", "〝 〞",
 "☆ ミ", "•☽────✧˖°", "☾ ˖ ✦", "ˏˋ ˎˊ", "°˖✧", "⊹ ˚ ₊", "♡ ⸜ ⸝", "୨ৎ", "ꪜ", "ᰔ", "𓆩♡𓆪", "𓆩⟡𓆪", "𓆸", "𖤐", "𖧷", "𑁍", "ꕥ", "⚝", "⛤", "ꗃ",
]
# presets by design: a centre sign with the same decoration on both sides, the right side the mirror of the left (wings, lines, brackets)
_MIRROR = {"꒰": "꒱", "꒱": "꒰", "⸜": "⸝", "⸝": "⸜", "༺": "༻", "༻": "༺", "꧁": "꧂", "꧂": "꧁", "❨": "❩", "❩": "❨", "❪": "❫", "❫": "❪", "‹": "›", "›": "‹", "«": "»", "»": "«",
           "⊱": "⊰", "⊰": "⊱", "『": "』", "』": "『", "「": "」", "」": "「", "【": "】", "】": "【", "◖": "◗", "◗": "◖", "⟨": "⟩", "⟩": "⟨", "〈": "〉", "〉": "〈", "《": "》", "》": "《",
           "(": ")", ")": "(", "[": "]", "]": "[", "{": "}", "}": "{", "<": ">", ">": "<", "❮": "❯", "❯": "❮", "⁽": "⁾", "⁾": "⁽", "₍": "₎", "₎": "₍", "〔": "〕", "〕": "〔",
           "ᕙ": "ᕗ", "ᕗ": "ᕙ", "𓆩": "𓆪", "𓆪": "𓆩", "⸢": "⸣", "⸣": "⸢", "◜": "◝", "◝": "◜", "╰": "╯", "╯": "╰", "≪": "≫", "≫": "≪", "﹝": "﹞", "﹞": "﹝"}
_FRAMES = [
 ["━━"], ["──"], ["────"], ["┈┈┈"], ["═══"], ["⋆", "｡", "˚"], ["˚", "₊", "‧"], ["୨", "୧", "┈┈"], ["꒰"], ["⸜"], ["༺"], ["꧁"], ["⊱"], ["『"], ["【"], ["𓆩"], ["⟨"], ["❨"],
 ["⋆", "˚", "｡"], ["⊹", "࣪", "˖"], ["·", "˚", "༘"], ["◦", "•", "●"], ["⋰", "⋱"], ["≪"], ["﹝"], ["╰", "┈", "➤"][:2] , ["˖", "⁺"], ["୨", "ৎ"], ["ᡣ𐭩", "˖"], ["✧", "˖", "°"], ["·.", "·´¯", "`·.·"],
 ["⋆", "⁺", "₊", "⋆"], ["┈┈", "✧", "┈┈"], ["━", "✦", "━"], ["∘", "◦", "˚"], ["༻", "━"], ["﹏", "﹏"], ["︶", "꒦", "꒷"],
]
_CENTRES = "✿ ❀ ❁ ❃ ❋ ⚘ ❦ ❧ ♡ ♥ ❥ ღ ✦ ✧ ★ ☆ ✩ ✪ ☾ ☽ ⛤ ⚝ ꕤ ✶ ✷ ❖ ◈ ◇ ⟡ ⁂ ✺ ❂ ☁ ✈ ♪ ♫".split()
def kind(fr):
    j = "".join(fr)
    if any(c in j for c in "━─┈═"): return "Lines"
    if any(c in _MIRROR for c in j): return "Brackets"
    return "Wings"
GENERATED = []
_C = "✿ ♡ ✦ ❀ ☾ ❁ ★ ❥ ✧ ⚘ ღ ✩".split()
def _right(fr):
    return "".join("".join(_MIRROR.get(ch, ch) for ch in t[::-1]) for t in reversed(fr))
for i, fr in enumerate(_FRAMES):
    for c in (_C[(i * 3 + k) % len(_C)] for k in range(3)):
        GENERATED.append((f"{''.join(fr)} {c} {_right(fr)}", kind(fr)))
# long ones: an outer layer around an inner one
_OUT = [["⋆", "｡", "˚"], ["˚", "₊", "‧"], ["୨", "୧", "┈┈"], ["⊹", "࣪", "˖"], ["━━"], ["·", "˚", "༘"], ["✧", "˖", "°"], ["⋆", "⁺", "₊", "⋆"]]
_IN = [["꒰"], ["⸜"], ["༺"], ["꧁"], ["⊱"], ["『"], ["𓆩"], ["【"]]
_n = 0
for o in _OUT:
    for k in range(3):
        inn = _IN[(_n + k * 3) % len(_IN)]; c = _C[(_n * 2 + k) % len(_C)]
        GENERATED.append((f"{''.join(o)} {''.join(inn)} {c} {_right(inn)} {_right(o)}", "Long")); _n += 1
FAM = [
 ("Arrows", [(0x2190,0x21FF,"Arrows"),(0x27F0,0x27FF,"Arrows A"),(0x2900,0x297F,"Arrows B")]),
 ("Shapes", [(0x25A0,0x25FF,"Geometric"),(0x2500,0x257F,"Box drawing"),(0x2580,0x259F,"Blocks"),(0x1F780,0x1F7FF,"Geometric 2")]),
 ("Music", [(0x2669,0x266F,"Notes"),(0x1D100,0x1D1FF,"Musical symbols")]),
 ("Symbols", [(0x2600,0x26FF,"Misc symbols"),(0x2700,0x27BF,"Dingbats"),(0x2300,0x23FF,"Technical"),(0x2B00,0x2BFF,"Symbols and arrows"),(0xA7,0xA7,"Latin"),(0xA9,0xAE,"Latin"),(0xB0,0xB0,"Latin"),(0xB6,0xB6,"Latin")]),
 ("Numbers", [(0xB2,0xB3,"Small"),(0xB9,0xBC,"Small"),(0xBD,0xBE,"Small"),(0x2070,0x209F,"Super and subscripts"),(0x2150,0x218F,"Fractions"),(0x2460,0x24FF,"Circled"),(0x1F100,0x1F1E5,"Enclosed")]),
 ("Punctuation", [(0xA1,0xA1,"Latin"),(0xBF,0xBF,"Latin"),(0xAB,0xAB,"Latin"),(0xBB,0xBB,"Latin"),(0x2010,0x2064,"General"),(0x2E00,0x2E7F,"Supplemental"),(0x3001,0x303F,"CJK")]),
 ("Currency", [(0x24,0x24,"Signs"),(0xA2,0xA5,"Signs"),(0x20A0,0x20CF,"Signs")]),
 ("Math", [(0xB1,0xB1,"Basic"),(0xD7,0xD7,"Basic"),(0xF7,0xF7,"Basic"),(0x2200,0x22FF,"Operators"),(0x27C0,0x27EF,"Misc A"),(0x2980,0x29FF,"Misc B"),(0x2A00,0x2AFF,"Operators 2"),(0x2100,0x214F,"Letterlike")]),
 ("Braille", [(0x2800,0x28FF,"Braille")]),
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
def emit(name, groups):
    out.append("#" + name)
    for sub, items in groups:
        if not items: continue
        if sub: out.append("##" + sub)
        out.extend(f"{c}\t{n}" for c, n in items)
# presets: the hand-made ones, then the designed ones by kind
groups = {"Classics": [], "Lines": [], "Wings": [], "Brackets": [], "Long": []}
for it in COMBOS_HAND:
    it = it.strip()
    if it and not is_emoji(it) and it not in seen: groups["Classics"].append((it, "pattern " + nm(it))); seen.add(it)
for pr, sub in GENERATED:
    if pr not in seen: groups[sub].append((pr, "pattern " + nm(pr))); seen.add(pr)
emit("Patterns", list(groups.items()))
for name, items in CURATED:
    rows = []
    for it in items:
        if it in seen or is_emoji(it): continue
        rows.append((it, nm(it))); seen.add(it)
    emit(name, [("", rows)])
for name, ranges in FAM:
    groups2 = {}
    for a, b, sub in ranges:
        for cp in range(a, b + 1):
            if cp in SKIP_REGIONAL: continue
            ch = chr(cp); cat = ud.category(ch)
            if not (cat[0] in "SP" or cat in ("No", "Nl")): continue
            try: n = ud.name(ch).lower()
            except ValueError: continue
            if ch in seen or is_emoji(ch): continue
            groups2.setdefault(sub, []).append((ch, n)); seen.add(ch)
    emit(name, list(groups2.items()))
open("app/src/main/assets/kbd/symbols.txt", "w", encoding="utf-8").write("\n".join(out) + "\n")
print(sum(1 for l in out if not l.startswith("#")), "signs")
