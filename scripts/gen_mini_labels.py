#!/usr/bin/env python3
"""Writes the labels of the mini (safe) app into app/src/mini/AndroidManifest.xml, between the BEGIN / END lines.
Scanners flag one APK with some thirty launcher entries named like system apps (Messages, Contacts, Calendar, Store ...) and widgets named after
Windows and iOS as a "FakeApp". In mini every launcher entry is "Pulse <name>" and the widgets carry no other company's name.
Run it after a launcher entry or a widget is added: python3 scripts/gen_mini_labels.py"""
import re, xml.dom.minidom as minidom

A = "http://schemas.android.com/apk/res/android"
main = minidom.parse("app/src/main/AndroidManifest.xml")
BRANDS = [("Windows Media skin", "Media skin"), ("Windows media player", "Media player"), ("Windows 95", "Classic"), ("Windows XP", "Luna"),
          ("Windows 7", "Aero"), ("Windows 10", "Flat"), ("Windows 11", "Fluent"), ("Windows", "Desktop"), ("iOS", "Rounded"), ("YTXTube", "Pulse Tube"),
          ("Winamp", "Classic player"), ("Poweramp", "Player"), ("Office", "Docs"), ("Spotify", "Music"), ("YouTube", "Tube")]
def scrub(l):
    for a, b in BRANDS: l = l.replace(a, b)
    return l
# what the mini manifest removes is left out (a removed element cannot have its label replaced as well)
_mini = open("app/src/mini/AndroidManifest.xml", encoding="utf-8").read()
removed = set(re.findall(r'android:name="([^"]+)" tools:node="remove"', _mini))
out = []
for tag in ("activity", "activity-alias", "receiver", "service"):
    for e in main.getElementsByTagName(tag):
        name = e.getAttributeNS(A, "name"); label = e.getAttributeNS(A, "label")
        if not name or not label or label.startswith("@"): continue
        is_app = tag in ("activity", "activity-alias") and any(c.getAttribute("android:name") == "android.intent.category.LAUNCHER" for c in e.getElementsByTagName("category"))
        new = scrub(label)
        if is_app and not new.startswith("Pulse"): new = "Pulse " + new
        if new == label: continue
        full = "com.umutk.pulse" + name if name.startswith(".") else name
        if full in removed: continue
        out.append(f'        <{tag} android:name="{full}" android:label="{new}" tools:replace="android:label" />')
p = "app/src/mini/AndroidManifest.xml"
s = open(p, encoding="utf-8").read()
block = "        <!-- BEGIN mini labels (scripts/gen_mini_labels.py) -->\n" + "\n".join(out) + "\n        <!-- END mini labels -->\n"
if "BEGIN mini labels" in s:
    s = re.sub(r"        <!-- BEGIN mini labels.*?<!-- END mini labels -->\n", lambda m: block, s, flags=re.S)
else:
    s = s.replace("    </application>", block + "    </application>")
open(p, "w", encoding="utf-8").write(s)
print(len(out), "labels")
