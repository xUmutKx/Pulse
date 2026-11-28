#!/usr/bin/env python3
"""Pre-build sanity check: flags Kotlin files that use a common Compose symbol without importing it
(the most frequent cause of failed Pulse builds).
Run from anywhere: python3 scripts/check_imports.py  (exit 1 when something is found)."""
import re, sys, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/java"
# symbol -> package it lives in (only symbols that are not in the file's own package)
SYMBOLS = {
    "collectAsState": "androidx.compose.runtime",
    "mutableLongStateOf": "androidx.compose.runtime",
    "mutableIntStateOf": "androidx.compose.runtime",
    "mutableStateOf": "androidx.compose.runtime",
    "mutableStateListOf": "androidx.compose.runtime",
    "produceState": "androidx.compose.runtime",
    "rememberCoroutineScope": "androidx.compose.runtime",
    "rememberUpdatedState": "androidx.compose.runtime",
    "derivedStateOf": "androidx.compose.runtime",
    "LaunchedEffect": "androidx.compose.runtime",
    "DisposableEffect": "androidx.compose.runtime",
    "withFrameNanos": "androidx.compose.runtime",
    "LocalContext": "androidx.compose.ui.platform",
    "LocalConfiguration": "androidx.compose.ui.platform",
    "rememberLazyListState": "androidx.compose.foundation.lazy",
    "rememberPagerState": "androidx.compose.foundation.pager",
    "animateFloatAsState": "androidx.compose.animation.core",
    "infiniteRepeatable": "androidx.compose.animation.core",
}
DECL = re.compile(r"^(\s*)(?:private\s+|internal\s+)?(?:val|var)\s+([A-Za-z_]\w*)\b")

def package_of(text):
    m = re.search(r"^package\s+([\w.]+)", text, re.M)
    return m.group(1) if m else ""

problems = []
for path in sorted(ROOT.rglob("*.kt")):
    text = path.read_text(encoding="utf-8", errors="replace")
    code = re.sub(r"//.*", "", text)  # drop line comments for usage detection
    imports = set(re.findall(r"^import\s+([\w.*]+)", text, re.M))
    star = {i[:-2] for i in imports if i.endswith(".*")}
    pkg = package_of(text)
    for sym, sp in SYMBOLS.items():
        if re.search(r"(?<![\w.@])" + sym + r"\b", code) and f"{sp}.{sym}" not in imports and sp not in star and pkg != sp:
            problems.append(f"{path.relative_to(ROOT)}: uses '{sym}' without 'import {sp}.{sym}'")

for p in problems:
    print(p)
print(f"{len(problems)} problem(s) found")
import subprocess
api = subprocess.run([sys.executable, str(pathlib.Path(__file__).with_name("check_api.py"))]).returncode  # calls missing on older Androids
lazy = subprocess.run([sys.executable, str(pathlib.Path(__file__).with_name("check_lazy.py"))]).returncode  # list keys / infinite height
sys.exit(1 if problems or api else 0)  # (the lazy check only lists candidates: it does not fail the commit)
