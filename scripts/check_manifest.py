#!/usr/bin/env python3
"""Checks the merged AndroidManifest.xml of a build: every activity-alias must point at an activity that is in the same manifest.
An alias whose target is gone (removed by a flavour's manifest) makes the whole package fail to install on every phone, with no message
in the Play Store but "Can't install". Exit code 1 lists the broken aliases."""
import sys
import xml.dom.minidom as minidom

A = "http://schemas.android.com/apk/res/android"
bad = []
for path in sys.argv[1:]:
    doc = minidom.parse(path)
    pkg = doc.documentElement.getAttribute("package")
    def full(n):
        return pkg + n if n.startswith(".") else n
    acts = {full(a.getAttributeNS(A, "name")) for a in doc.getElementsByTagName("activity")}
    for al in doc.getElementsByTagName("activity-alias"):
        t = full(al.getAttributeNS(A, "targetActivity"))
        if t not in acts:
            bad.append(f"{path}: {full(al.getAttributeNS(A, 'name'))} -> {t} (no such activity)")
for b in bad:
    print(b)
print(f"{len(bad)} broken alias(es)")
sys.exit(1 if bad else 0)
