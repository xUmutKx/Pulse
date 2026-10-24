#!/usr/bin/env python3
"""Builds app/src/main/assets/kbd/bi_tr.txt and bi_en.txt (word pairs "prev next count") from the Universal Dependencies treebanks (all CC BY-SA 4.0):
Turkish BOUN, Kenet, Penn and English EWT. Usage: python3 tools/gen_bigrams.py <folder with the .conllu files>"""
import re, sys, glob, collections
src = sys.argv[1]
SETS = {"tr": ["tr_boun*", "tr_kenet*", "tr_penn*"], "en": ["en_ewt*"]}
def low(s, lang):
    if lang == "tr": s = s.replace("İ", "i").replace("I", "ı")
    return s.lower()
for lang, pats in SETS.items():
    cnt = collections.Counter(); uni = collections.Counter()
    for pat in pats:
        for f in glob.glob(f"{src}/{pat}"):
            for line in open(f, encoding="utf-8"):
                if not line.startswith("# text = "): continue
                words = re.findall(r"[^\W\d_]+(?:['’][^\W\d_]+)?", low(line[9:], lang))
                for a, b in zip(words, words[1:]): cnt[(a, b)] += 1
                uni.update(words)
    rows = [(a, b, n) for (a, b), n in cnt.items() if n >= 2 and len(a) >= 1 and len(b) >= 1]
    rows.sort(key=lambda r: -r[2])
    rows = rows[:90000]
    open(f"app/src/main/assets/kbd/bi_{lang}.txt", "w", encoding="utf-8").write("\n".join(f"{a} {b} {n}" for a, b, n in rows) + "\n")
    print(lang, len(rows), "pairs from", sum(uni.values()), "words")
