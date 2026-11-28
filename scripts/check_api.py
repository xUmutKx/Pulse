#!/usr/bin/env python3
"""Finds calls that only exist on newer Androids / Java 11+ (NoSuchMethodError on older phones, e.g. InputStream.readNBytes -> Android 13).
Run before every commit (check_imports.py runs it too). Use the helper in Compat.kt instead, or add '// api-ok' to a line that is guarded by a Build.VERSION check."""
import re, sys, pathlib
ROOT = pathlib.Path(__file__).resolve().parent.parent
BAD = {
    r"\.readNBytes\(": "InputStream.readNBytes (Android 13+): use readUpTo() from Compat.kt",
    r"\.readAllBytes\(": "InputStream.readAllBytes (Android 13+): use readBytes()",
    r"\.transferTo\(": "InputStream.transferTo (Android 13+): use copyTo()",
    r"\bFiles\.(readString|writeString|readAllBytes|lines|walk|list|find)\(": "java.nio.file.Files helper (Android 8+/13+): use File.readText / readBytes",
    r"\bPath\.of\(": "Path.of (Java 11): use File",
    r"\b(List|Set|Map)\.of\(": "List/Set/Map.of (Android 11+): use listOf/setOf/mapOf",
    r"\b(List|Set|Map)\.copyOf\(": "copyOf (Android 11+): use toList()/toSet()/toMap()",
    r"\.toUnmodifiable\w*\(": "Collectors.toUnmodifiable (Android 11+)",
    r"\bHttpClient\.newHttpClient|java\.net\.http\.": "java.net.http (not on Android)",
    r"\bString\.valueOf\(.*\)\.strip\(|\.stripLeading\(|\.stripTrailing\(": "String.strip* (Android 11+): use trim()",
    r"\.lines\(\)\.(toList|collect)": "String.lines() stream (Android 11+): use lines() Kotlin ext (no stream)",
    r"\bOptional\w*\.isEmpty\(": "Optional.isEmpty (Android 11+)",
    r"\.orElseThrow\(\)": "Optional.orElseThrow() (Android 10+)",
    r"\.toArray\(\w+\[\]::new\)": "Collection.toArray(IntFunction) (Android 13+)",
}
problems = []
for d in ("app/src", "lean/src", "wear/src"):
    for p in sorted((ROOT / d).rglob("*.kt")) if (ROOT / d).exists() else []:
        for n, line in enumerate(p.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            code = line.split("//")[0] if "api-ok" not in line else ""
            for rx, msg in BAD.items():
                if re.search(rx, code):
                    problems.append(f"{p.relative_to(ROOT)}:{n}: {msg}")
for q in problems:
    print(q)
print(f"{len(problems)} api problem(s) found")
sys.exit(1 if problems else 0)
