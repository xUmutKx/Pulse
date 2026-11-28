#!/usr/bin/env python3
"""LazyColumn / LazyRow / LazyGrid crash finder (heuristic, lists candidates; add '// lazy-ok' on the line to silence one).
1. keys that can repeat (name, label, title, text, the item itself): 'Key "x" was already used' crashes. Use an id, a path, or distinctBy first.
2. keys that change every time (Random, UUID, time): the list rebuilds on every frame.
3. a Lazy list inside a verticalScroll / scrollable parent with no fixed height: 'measured with an infinity maximum height' crash.
Exit 1 when it finds something."""
import re, sys, pathlib
ROOT = pathlib.Path(__file__).resolve().parent.parent
WEAK = re.compile(r"key\s*=\s*\{[^}]*\b(?:it|\w+)\.(name|label|title|text|first|second|value|ext|type|kind)\b\s*\}")
SELF = re.compile(r"key\s*=\s*\{\s*it\s*\}")
UNSTABLE = re.compile(r"key\s*=\s*\{[^}]*(Random|UUID|currentTimeMillis|nanoTime|System\.)")
LAZY = re.compile(r"\b(LazyColumn|LazyVerticalGrid|LazyVerticalStaggeredGrid)\s*\(")
SIZED = re.compile(r"\.(height|heightIn|weight|fillMaxSize|fillMaxHeight|size|requiredHeight|aspectRatio)\(")
problems = []
for p in sorted((ROOT / "app/src").rglob("*.kt")):
    lines = p.read_text(encoding="utf-8", errors="replace").splitlines()
    rel = p.relative_to(ROOT)
    for n, line in enumerate(lines):
        if "lazy-ok" in line: continue
        code = line.split("//")[0]
        if ("items" in code or "itemsIndexed" in code) and "distinct" not in code:
            if WEAK.search(code) or SELF.search(code):
                problems.append(f"{rel}:{n+1}: key may repeat (name/label/title/the item itself): {code.strip()[:110]}")
            if UNSTABLE.search(code):
                problems.append(f"{rel}:{n+1}: key changes on every pass (Random/UUID/time): {code.strip()[:110]}")
        if LAZY.search(code):
            call = " ".join(lines[n:n + 4])
            sized = SIZED.search(call.split("{")[0] if "{" in call else call)
            # an enclosing scrollable parent: a verticalScroll within the 40 lines above, with smaller indentation
            ind = len(line) - len(line.lstrip())
            for m in range(n - 1, max(-1, n - 40), -1):
                l2 = lines[m]
                if l2.strip() and (len(l2) - len(l2.lstrip())) < ind and ".verticalScroll(" in l2 and not sized:
                    problems.append(f"{rel}:{n+1}: Lazy list inside a verticalScroll (line {m+1}) without a fixed height: infinite-height crash")
                    break
for q in problems:
    print(q)
print(f"{len(problems)} lazy-list candidate(s) found")
sys.exit(1 if problems else 0)
