#!/usr/bin/env python3
"""Makes every standalone app of Pulse (built with -PpulseApp=<id>): one for each extra app's launcher entry in the manifest, plus Pulse Launcher
and Pulse Core (the main app: the task manager, the settings and the toolkit, with no extra apps). Writes app/src/standalone/<id>/ and
scripts/standalone_apps.txt. Run it again after an extra app is added: python3 scripts/gen_standalone.py"""
import os, re, shutil, xml.dom.minidom as minidom

A = "http://schemas.android.com/apk/res/android"
J = "app/src/main/java/com/umutk/pulse/"
# the code of each app in Pulse (copied into its own repository); the id is the alias without "Launcher", in lower case
SRC = {"files": J + "features/Explorer*.kt," + J + "files", "photos": J + "photos", "calendar": J + "calendar", "contacts": J + "contacts",
       "paint": J + "extras/paint", "calculator": J + "extras/calculator", "code": J + "extras/code", "terminal": J + "features/TerminalPage.kt",
       "clock": J + "extras/lucid", "ebook": J + "extras/ebook", "browser": J + "extras/browser", "office": J + "Office*.kt", "music": J + "extras/music",
       "video": J + "extras/video", "whitenoise": J + "extras/whitenoise", "voice": J + "extras/voice", "notes": J + "extras/notes", "weather": J + "extras/weather",
       "ai": J + "extras/ai", "messages": J + "extras/messages", "screenrec": J + "extras/screenrec", "keyboard": J + "extras/keyboard", "palette": J + "extras/palette",
       "blackout": J + "extras/blackout", "island": J + "extras/island", "notifhist": J + "extras/notifhist", "gamehub": J + "extras/gamehub", "health": J + "health",
       "ytx": J + "extras/ytx", "localsend": J + "extras/localsend", "tempmail": J + "extras/tempmail", "extrastore": J + "extras/store",
       "launcher": J + "extras/launcher", "core": J + "*.kt"}
NAMES = {"ytx": "YTXTube", "ebook": "Pulse Books", "extrastore": "Pulse Store", "notifhist": "Pulse Notification history", "screenrec": "Pulse Screen recorder",
         "whitenoise": "Pulse White Noise", "ai": "Pulse AI", "launcher": "Pulse Launcher", "core": "Pulse Core"}
SKIP = {"artcraft"}  # another project's app (it installs APKs)
doc = minidom.parse("app/src/main/AndroidManifest.xml")
apps = []
for e in doc.getElementsByTagName("activity-alias"):
    n = e.getAttributeNS(A, "name").lstrip(".")
    if not n.endswith("Launcher") or n.startswith("Glass") or n.startswith("Icon"): continue
    if not any(c.getAttribute("android:name") == "android.intent.category.LAUNCHER" for c in e.getElementsByTagName("category")): continue
    i = n[:-len("Launcher")].lower()
    if i in SKIP: continue
    label = e.getAttributeNS(A, "label")
    if label.startswith("@string/"): label = {"files_name": "Files", "photos_name": "Photos", "contacts_name": "Contacts"}.get(label[8:], label[8:])
    apps.append((i, n, NAMES.get(i, "Pulse " + label)))
apps.append(("launcher", "PulseLauncherIcon", NAMES["launcher"]))
apps.append(("core", "IconDefault", NAMES["core"]))
shutil.rmtree("app/src/standalone", ignore_errors=True)
for i, alias, name in apps:
    d = f"app/src/standalone/{i}"; os.makedirs(d, exist_ok=True)
    if i == "core":
        body = f'    <application android:label="{name}" tools:replace="android:label" />\n'
    elif i == "launcher":
        # the launcher is a home screen: an icon of its own opens it (and makes it easy to set as the home app)
        body = f'''    <application android:label="{name}" tools:replace="android:label">
        <activity-alias android:name="com.umutk.pulse.IconDefault" android:enabled="false" tools:replace="android:enabled" />
        <activity-alias android:name="com.umutk.pulse.PulseLauncherIcon" android:targetActivity="com.umutk.pulse.extras.launcher.LauncherActivity" android:exported="true"
            android:label="{name}" android:icon="@mipmap/ic_ti_11" android:roundIcon="@mipmap/ic_ti_11">
            <intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent-filter>
        </activity-alias>
    </application>
'''
    else:
        body = f'''    <application android:label="{name}" tools:replace="android:label">
        <activity-alias android:name="com.umutk.pulse.IconDefault" android:enabled="false" tools:replace="android:enabled" />
        <activity-alias android:name="com.umutk.pulse.{alias}" android:enabled="true" tools:replace="android:enabled" />
    </application>
'''
    open(d + "/AndroidManifest.xml", "w").write(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {name} as an app of its own (-PpulseApp={i}), made by scripts/gen_standalone.py: do not edit by hand. -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">
{body}</manifest>
''')
with open("scripts/standalone_apps.txt", "w") as f:
    f.write("# id | launcher alias | name | repository (github.com/xUmutKx/<repo>) | the app's own code in Pulse (made by scripts/gen_standalone.py)\n")
    for i, alias, name in apps:
        repo = "Pulse-" + "".join(w[:1].upper() + w[1:] for w in re.split(r"[^A-Za-z0-9]+", name.replace("Pulse ", "")) if w)
        f.write(f"{i}|{alias}|{name}|{repo}|{SRC.get(i, J + 'extras/' + i)}\n")
print(len(apps), "apps:", " ".join(a[0] for a in apps))
