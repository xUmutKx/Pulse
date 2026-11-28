#!/usr/bin/env python3
"""Makes every standalone app of Pulse (built with -PpulseApp=<id>): one for each extra app's launcher entry in the manifest, plus Pulse Launcher
and Pulse Core (the main app: the task manager, the settings and the toolkit, with no extra apps). Writes app/src/standalone/<id>/ and
scripts/standalone_apps.txt. Run it again after an extra app is added: python3 scripts/gen_standalone.py"""
import os, re, shutil, xml.dom.minidom as minidom

A = "http://schemas.android.com/apk/res/android"
J = "app/src/main/java/com/umutk/pulse/"
# the code of each app in Pulse (copied into its own repository); the id is the alias without "Launcher", in lower case
SRC = {"files": J + "features/Explorer*.kt," + J + "files," + J + "FilesActivity.kt", "photos": J + "Photos*.kt," + J + "ImageViewer.kt", "calendar": J + "Calendar*.kt", "contacts": J + "Contacts*.kt," + J + "Ct*.kt",
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
# ---- what each app may keep of the sensitive rights ------------------------------------------------------------------------------------------------------------
# An app on its own asks the system only for what it does: every right of this list that an app does not need is taken out of its manifest (with the services
# and receivers that belong to it), so an SMS right is not in a calculator and a scanner reads each app for what it is. The setup screen (StandaloneSetup.kt) lists
# only the rights an app keeps.
P = "android.permission."
GROUPS = {
    "storage": [P + x for x in ("READ_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES", "READ_MEDIA_AUDIO", "READ_MEDIA_VIDEO", "MANAGE_EXTERNAL_STORAGE")],
    "sms": [P + x for x in ("READ_SMS", "SEND_SMS", "RECEIVE_SMS", "RECEIVE_MMS", "RECEIVE_WAP_PUSH", "WRITE_SMS")],
    "call": [P + "CALL_PHONE", P + "READ_CALL_LOG"],
    "contacts": [P + "READ_CONTACTS", P + "WRITE_CONTACTS"],
    "calendar": [P + "READ_CALENDAR", P + "WRITE_CALENDAR"],
    "location": [P + "ACCESS_FINE_LOCATION", P + "ACCESS_COARSE_LOCATION"],
    "mic": [P + "RECORD_AUDIO"], "camera": [P + "CAMERA"],
    "bluetooth": [P + "BLUETOOTH_CONNECT", P + "BLUETOOTH_SCAN"], "activity": [P + "ACTIVITY_RECOGNITION"],
    "overlay": [P + "SYSTEM_ALERT_WINDOW"], "install": [P + "REQUEST_INSTALL_PACKAGES", P + "REQUEST_DELETE_PACKAGES"],
    "secure": [P + "WRITE_SECURE_SETTINGS", P + "WRITE_SETTINGS"], "usage": [P + "PACKAGE_USAGE_STATS"], "packages": [P + "QUERY_ALL_PACKAGES"],
    "shizuku": ["moe.shizuku.manager.permission.API_V23", "com.termux.permission.RUN_COMMAND"],
    "wallpaper": [P + "SET_WALLPAPER"], "dnd": [P + "ACCESS_NOTIFICATION_POLICY"],
}
# the components that belong to a group of rights (removed with it)
COMPONENTS = {
    "sms": [("receiver", "com.umutk.pulse.extras.messages.SmsDeliverReceiver"), ("receiver", "com.umutk.pulse.extras.messages.SmsReceivedReceiver"),
            ("receiver", "com.android.mms.transaction.PushReceiver"), ("receiver", "com.umutk.pulse.extras.messages.MmsInReceiver")],
    "shizuku": [("provider", "rikka.shizuku.ShizukuProvider")],
    "a11y_autoclick": [("service", "com.umutk.pulse.features.AutoClickService")], "a11y_cover": [("service", "com.umutk.pulse.features.CoverAccessibilityService")],
    "a11y_cache": [("service", "com.umutk.pulse.applock.CacheCleanService")], "a11y_lock": [("service", "com.umutk.pulse.applock.LockService")],
    "a11y_blackout": [("service", "com.umutk.pulse.extras.blackout.AppWatch")], "a11y_keyboard": [("service", "com.umutk.pulse.extras.keyboard.AccessKeyboardService")],
    "listen_island": [("service", "com.umutk.pulse.extras.island.IslandListenerService")], "listen_count": [("service", "com.umutk.pulse.features.NoteCounter")],
    "listen_lyrics": [("service", "com.umutk.pulse.extras.lyrics.LyricsListenerService")], "listen_hist": [("service", "com.umutk.pulse.extras.notifhist.NotifHistListener")],
}
ALL_SENSITIVE = set(GROUPS) | set(COMPONENTS)
KEEP = {  # the groups each app keeps (everything else of the lists above is taken out)
    "files": {"storage", "install", "packages", "shizuku", "usage"}, "photos": {"storage", "wallpaper"}, "calendar": {"calendar"}, "contacts": {"contacts", "call"},
    "messages": {"sms", "contacts"}, "music": {"storage", "bluetooth", "listen_lyrics"}, "video": {"storage", "overlay"}, "ytx": {"install"},
    "code": {"storage"}, "terminal": {"storage", "shizuku"}, "ebook": {"storage"}, "office": {"storage"}, "paint": {"storage"}, "palette": {"storage", "wallpaper"},
    "weather": {"location"}, "voice": {"mic", "storage"}, "screenrec": {"mic", "overlay", "storage"}, "whitenoise": set(), "notes": set(), "clock": {"overlay"},
    "keyboard": {"mic", "camera", "storage", "contacts", "a11y_keyboard", "listen_count"},
    "launcher": {"overlay", "usage", "packages", "contacts", "calendar", "storage", "listen_count", "listen_island", "secure", "dnd", "bluetooth", "location", "shizuku", "call"},
    "island": {"overlay", "listen_island", "bluetooth"}, "notifhist": {"listen_hist"}, "gamehub": {"usage", "overlay", "packages", "shizuku", "storage"},
    "health": {"activity", "location", "bluetooth"}, "localsend": {"storage", "location", "camera"}, "ai": {"mic", "storage"}, "browser": {"storage"}, "tempmail": set(),
    "extrastore": {"install", "packages"}, "calculator": set(), "blackout": {"a11y_blackout", "usage", "shizuku", "overlay", "packages", "secure", "dnd"},
    # Pulse Core: the main app (task manager, settings, toolkit, App lock, cleaner) without the extra apps: no messages, calls, contacts or calendar
    "core": ALL_SENSITIVE - {"sms", "call", "contacts", "calendar"},
}

# ---- the components of the other apps ------------------------------------------------------------------------------------------------------------------------------
# An app on its own has only its own launcher entries and the components of its own folders in its manifest: the code and the pictures that only the others used
# are then no longer kept by the manifest, and the shrinker can drop them.
ALIAS_OF = {i: alias for i, alias, name in apps if i not in ("core", "launcher")}
PKG_OWNER = {}
for i in [a[0] for a in apps]:
    for part in SRC.get(i, "").split(","):
        m = re.search(r"/extras/([a-z0-9]+)$", part)
        if m: PKG_OWNER[m.group(1)] = i
CLASS_OWNER = {"CalendarActivity": "calendar", "ContactsActivity": "contacts", "ImageViewActivity": "photos", "FilesActivity": "files"}
def owner_of(tag, name):
    base = name.split(".")[-1]
    if tag == "activity-alias":
        for i, alias in ALIAS_OF.items():
            if base.endswith(alias): return i
        if base == "ArtCraftLauncher" or base.endswith("ArtCraftLauncher"): return "artcraft"
        return None
    m = re.match(r"com\.umutk\.pulse\.extras\.([a-z0-9]+)\.", name)
    if m and m.group(1) in PKG_OWNER: return PKG_OWNER[m.group(1)]
    if name.startswith("com.umutk.pulse.extras.launcher."): return "launcher"
    if "Weather" in base and "extras" not in name: return "weather"
    return CLASS_OWNER.get(base)
ALL_COMPONENTS = []
for tag in ("activity", "activity-alias", "service", "receiver", "provider"):
    for e in doc.getElementsByTagName(tag):
        n = e.getAttributeNS(A, "name")
        if n: ALL_COMPONENTS.append((tag, "com.umutk.pulse" + n if n.startswith(".") else n))

def others(i, already):
    out = ""
    for tag, n in ALL_COMPONENTS:
        o = owner_of(tag, n)
        if o is None or n in already: continue
        # Pulse Core keeps no app of its own; an app keeps its own components; the launcher app keeps its own folder
        if i == "core" or o != i:
            out += f'        <{tag} android:name="{n}" tools:node="remove" />\n'
    return out

def trimmed(i):
    keep = KEEP.get(i, ALL_SENSITIVE)
    out = ""
    for g in sorted(ALL_SENSITIVE - keep):
        for pm in GROUPS.get(g, []):
            out += f'    <uses-permission android:name="{pm}" tools:node="remove" />\n'
    comps = ""
    for g in sorted(ALL_SENSITIVE - keep):
        for tag, n in COMPONENTS.get(g, []):
            comps += f'        <{tag} android:name="{n}" tools:node="remove" />\n'
    already = {n for g in ALL_SENSITIVE - keep for _, n in COMPONENTS.get(g, [])}
    comps += others(i, already)
    return out, comps

shutil.rmtree("app/src/standalone", ignore_errors=True)
for i, alias, name in apps:
    d = f"app/src/standalone/{i}"; os.makedirs(d, exist_ok=True)
    if i == "core":
        perms, comps = trimmed(i)
        body = perms + f'    <application android:label="{name}" tools:replace="android:label">\n' + comps + '    </application>\n'
    elif i == "launcher":
        # the launcher is a home screen: an icon of its own opens it (and makes it easy to set as the home app)
        perms, comps = trimmed(i)
        body = perms + f'''    <application android:label="{name}" tools:replace="android:label">
{comps}        <activity-alias android:name="com.umutk.pulse.IconDefault" android:enabled="false" tools:replace="android:enabled" />
        <activity-alias android:name="com.umutk.pulse.PulseLauncherIcon" android:targetActivity="com.umutk.pulse.extras.launcher.LauncherActivity" android:exported="true"
            android:label="{name}" android:icon="@mipmap/ic_ti_11" android:roundIcon="@mipmap/ic_ti_11">
            <intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent-filter>
        </activity-alias>
    </application>
'''
    else:
        perms, comps = trimmed(i)
        body = perms + f'''    <application android:label="{name}" tools:replace="android:label">
{comps}        <activity-alias android:name="com.umutk.pulse.IconDefault" android:enabled="false" tools:replace="android:enabled" />
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
# ---- the catalog of Pulse Core's "Extra apps" page (app/src/main/java/com/umutk/pulse/extras/store/PulseAppsCatalog.kt) --------------------------------------------
# id: (what it is in English, in Turkish, [(feature in English, in Turkish) x3])
D = {
 "files": ("A file manager with its own search, bulk rename, zip and an app manager.", "Kendi aramalı, toplu yeniden adlandırmalı, zip ve uygulama yöneticili dosya yöneticisi.",
           [("Fast search of the whole phone", "Tüm telefonda hızlı arama"), ("Rename many files at once", "Çok dosyayı birden yeniden adlandırma"), ("Zip, APK and type lists", "Zip, APK ve tür listeleri")]),
 "calendar": ("A calendar with events and reminders, in every look.", "Her görünümde etkinlik ve hatırlatmalı takvim.",
           [("Month, week and day views", "Ay, hafta ve gün görünümü"), ("Events and reminders", "Etkinlikler ve hatırlatmalar"), ("All the looks of Pulse", "Pulse'un tüm görünümleri")]),
 "contacts": ("Your contacts and the call history.", "Kişilerin ve arama geçmişin.",
           [("Search and edit contacts", "Kişi arama ve düzenleme"), ("Call history", "Arama geçmişi"), ("Call backgrounds", "Arama arka planları")]),
 "photos": ("A gallery for the pictures and videos of the phone.", "Telefonun resim ve videoları için galeri.",
           [("Albums and a timeline", "Albümler ve zaman çizelgesi"), ("A search by what is in a picture", "Resmin içindekine göre arama"), ("Edit and share", "Düzenle ve paylaş")]),
 "ytx": ("A YouTube client without ads, with downloads and background playback.", "Reklamsız, indirme ve arka planda oynatmalı YouTube istemcisi.",
           [("No ads", "Reklam yok"), ("Download and play in the background", "İndirme ve arka planda oynatma"), ("YouTube Music mode", "YouTube Music modu")]),
 "island": ("A pill over the camera that shows calls, music and timers.", "Kameranın üstünde arama, müzik ve sayaçları gösteren hap.",
           [("Calls, music and timers in one place", "Arama, müzik ve sayaç tek yerde"), ("Tap to open the app", "Dokununca uygulamayı açar"), ("Looks like the phones that have one", "Olanların görünümünde")]),
 "music": ("A music player with Poweramp and Winamp looks, lyrics and widgets.", "Poweramp ve Winamp görünümlü, sözlü ve widget'lı müzik çalar.",
           [("Poweramp, Winamp and more skins", "Poweramp, Winamp ve daha çok görünüm"), ("Lyrics and the equalizer", "Sözler ve ekolayzır"), ("YouTube Music and Spotify in the search", "Aramada YouTube Music ve Spotify")]),
 "video": ("A video player with subtitle search, a small window and gestures.", "Altyazı arama, küçük pencere ve hareketli video oynatıcı.",
           [("Search subtitles from the player", "Oynatıcıdan altyazı arama"), ("A small window you can throw", "Fırlatılabilen küçük pencere"), ("Brightness and volume gestures", "Parlaklık ve ses hareketleri")]),
 "whitenoise": ("Calm sounds to mix for sleep and focus.", "Uyku ve odaklanma için karıştırılabilen sakin sesler.",
           [("Mix several sounds", "Birkaç sesi karıştır"), ("A timer to stop", "Durdurmak için sayaç"), ("Plays in the background", "Arka planda çalar")]),
 "voice": ("A voice recorder.", "Ses kaydedici.", [("Record and play back", "Kaydet ve dinle"), ("Trim and rename", "Kırp ve yeniden adlandır"), ("Keeps the list of recordings", "Kayıt listesini tutar")]),
 "notes": ("Notes, with lists and code.", "Liste ve kod destekli notlar.", [("Notes and lists", "Notlar ve listeler"), ("Code notes with colours", "Renkli kod notları"), ("Shows in the keyboard", "Klavyede görünür")]),
 "paint": ("A drawing app.", "Çizim uygulaması.", [("Brushes and layers", "Fırçalar ve katmanlar"), ("Open and save pictures", "Resim aç ve kaydet"), ("All the looks of Pulse", "Pulse'un tüm görünümleri")]),
 "browser": ("A small web browser.", "Küçük bir web tarayıcısı.", [("Tabs and a download manager", "Sekmeler ve indirme yöneticisi"), ("Hands video links to the player", "Video bağlantılarını oynatıcıya verir"), ("In every look", "Her görünümde")]),
 "office": ("Word, Excel, PowerPoint and PDF files, open and edit.", "Word, Excel, PowerPoint ve PDF dosyalarını açma ve düzenleme.",
           [("Word, Excel and PowerPoint", "Word, Excel ve PowerPoint"), ("PDF tools", "PDF araçları"), ("Find and replace", "Bul ve değiştir")]),
 "code": ("A code editor with colours, folding, a project tree and Pulse AI.", "Renklendirme, katlama, proje ağacı ve Pulse AI'lı kod düzenleyici.",
           [("Colours for many languages", "Çok dilde renklendirme"), ("A project from a folder or a zip", "Klasör ya da zipten proje"), ("Pulse AI edits your code on the phone", "Pulse AI kodunu telefonda düzenler")]),
 "terminal": ("A terminal.", "Terminal.", [("A shell on the phone", "Telefonda kabuk"), ("Root and Shizuku when you have them", "Varsa root ve Shizuku"), ("Colours of the looks", "Görünümlerin renkleri")]),
 "calculator": ("A calculator in every look.", "Her görünümde hesap makinesi.", [("Standard and scientific", "Standart ve bilimsel"), ("History", "Geçmiş"), ("All the looks of Pulse", "Pulse'un tüm görünümleri")]),
 "clock": ("Alarms, a timer, a stopwatch and a world clock.", "Alarm, sayaç, kronometre ve dünya saati.",
           [("Alarms with a wake-up check", "Uyanıklık kontrollü alarmlar"), ("Timer and stopwatch", "Sayaç ve kronometre"), ("World clock", "Dünya saati")]),
 "ebook": ("A reader for your books, and free books from Project Gutenberg.", "Kitap okuyucu, Project Gutenberg'den ücretsiz kitaplar.",
           [("Reads EPUB and more", "EPUB ve daha fazlasını okur"), ("Free books to download", "İndirilecek ücretsiz kitaplar"), ("Shelves and collections", "Raflar ve koleksiyonlar")]),
 "tempmail": ("Throw-away e-mail addresses.", "Tek kullanımlık e-posta adresleri.", [("A new address in one tap", "Tek dokunuşla yeni adres"), ("Read the mail that arrives", "Gelen postayı oku"), ("Nothing kept after", "Sonra hiçbir şey saklanmaz")]),
 "keyboard": ("A keyboard with swipe typing, many themes, clipboard, AI and tools.", "Kaydırarak yazma, çok tema, pano, yapay zekâ ve araçlı klavye.",
           [("Swipe typing and word suggestions", "Kaydırarak yazma ve kelime önerileri"), ("Themes, sounds and press effects", "Temalar, sesler ve basma efektleri"), ("Clipboard, one-time codes and AI fix", "Pano, tek kullanımlık kodlar ve AI düzeltme")]),
 "ai": ("Chat and speech to text that run on the phone.", "Telefonda çalışan sohbet ve konuşmadan metne.", [("A language model on the phone", "Telefonda dil modeli"), ("Speech to text", "Konuşmadan metne"), ("Nothing leaves the phone", "Hiçbir şey telefondan çıkmaz")]),
 "weather": ("The weather from several sources combined.", "Birden çok kaynağın birleşik hava durumu.", [("Daily and hourly forecast", "Günlük ve saatlik tahmin"), ("Humidity and wind", "Nem ve rüzgâr"), ("The last forecast when offline", "Çevrimdışıyken son tahmin")]),
 "messages": ("A text messages app.", "Kısa mesaj uygulaması.", [("Texts and groups", "Mesajlar ve gruplar"), ("Can be the default messages app", "Varsayılan mesaj uygulaması olabilir"), ("In every look", "Her görünümde")]),
 "screenrec": ("A screen recorder.", "Ekran kaydedici.", [("Record the screen with sound", "Sesli ekran kaydı"), ("A small control over the screen", "Ekranda küçük kontrol"), ("A tile in the quick panel", "Hızlı panelde kutucuk")]),
 "notifhist": ("A history of the notifications you received.", "Aldığın bildirimlerin geçmişi.", [("Saves every notification", "Her bildirimi kaydeder"), ("Search the history", "Geçmişte arama"), ("Per app", "Uygulamaya göre")]),
 "extrastore": ("The store of extra packs and apps.", "Ekstra paket ve uygulama mağazası.", [("Downloads packs", "Paket indirir"), ("Updates them", "Günceller"), ("Lists what is installed", "Kurulanları listeler")]),
 "localsend": ("Send files to nearby phones and computers over Wi-Fi.", "Wi-Fi ile yakındaki telefon ve bilgisayarlara dosya gönder.", [("Nothing goes through the internet", "İnternetten geçmez"), ("Send files and text", "Dosya ve metin gönder"), ("A quick tile", "Hızlı kutucuk")]),
 "palette": ("Wallpapers, icon packs and colours.", "Duvar kâğıtları, simge paketleri ve renkler.", [("A live wallpaper", "Canlı duvar kâğıdı"), ("Icon packs", "Simge paketleri"), ("Colours from a picture", "Resimden renkler")]),
 "gamehub": ("A shelf for your games, with covers, play time and a menu over the game.", "Oyunların için raf: kapaklar, oynama süresi ve oyunun üstünde menü.",
           [("A 3D shelf of cases, or a CD rack", "3B kutu rafı ya da CD rafı"), ("Play time of each game", "Her oyunun oynama süresi"), ("HTML games too", "HTML oyunlar da")]),
 "health": ("Steps, workouts and a food diary.", "Adımlar, antrenmanlar ve yemek günlüğü.", [("Workout log with sets", "Setli antrenman kaydı"), ("Rest timer", "Dinlenme sayacı"), ("Body measures and food", "Vücut ölçüleri ve yemek")]),
 "blackout": ("Switch off the screen's colour while a game or an app runs.", "Bir oyun ya da uygulama çalışırken ekranı karartma.", [("Per app rules", "Uygulamaya göre kurallar"), ("Saves battery on AMOLED", "AMOLED'de pil kazandırır"), ("Quick control", "Hızlı kontrol")]),
 "launcher": ("A home screen in the style of Windows: taskbar, start menu, desktop.", "Windows tarzı ana ekran: görev çubuğu, başlat menüsü, masaüstü.",
           [("Windows 95 to 11 looks", "Windows 95'ten 11'e görünümler"), ("Task manager and widgets", "Görev yöneticisi ve widget'lar"), ("Notification panel", "Bildirim paneli")]),
}
import json as _json
rows = []
for i, alias, name in apps:
    if i == "core": continue
    en, trk, feats = D.get(i, (name, name, []))
    rows.append((i, alias, name, en, trk, feats))
def q(t): return _json.dumps(t, ensure_ascii=False).replace("$", "\\$")
with open("app/src/main/java/com/umutk/pulse/extras/store/PulseAppsCatalog.kt", "w", encoding="utf-8") as f:
    f.write("package com.umutk.pulse.extras.store\n\n// made by scripts/gen_standalone.py: do not edit by hand\n\n/** One app of Pulse that is also an app of its own: what it is, what it does, and the repository that holds it. */\n")
    f.write("class PulseApp(val id: String, val name: String, val about: String, val aboutTr: String, val features: List<Pair<String, String>>, val repo: String)\n\n")
    f.write("object PulseAppsCatalog {\n    val all = listOf(\n")
    repo_of = {l.split("|")[0]: l.split("|")[3] for l in open("scripts/standalone_apps.txt", encoding="utf-8") if "|" in l and not l.startswith("#")}
    for i, alias, name, en, trk, feats in rows:
        fe = ", ".join(f"{q(a)} to {q(b)}" for a, b in feats)
        f.write(f"        PulseApp({q(i)}, {q(name)}, {q(en)}, {q(trk)}, listOf({fe}), {q(repo_of[i])}),\n")
    f.write("    )\n}\n")
# ---- what an app brings over from Pulse (app/src/main/java/com/umutk/pulse/features/share/PulseShareCatalog.kt) -------------------------------------------------------
# the preferences files an app uses are found in its own code (every getSharedPreferences("name") and FILE = "name" of its folder); the files are named here
import glob as _glob
FILES = {
    "keyboard": ["clips", "kb_font", "kb_sound", "kb_picture.img", "kb_space.img", "kb_walker.img", "kbd"] + [f"kbd_{n}" for n in ("banned.txt", "clips.json", "confusion.txt", "forms.txt", "keys.txt", "pairs.txt", "rejects.txt", "touch.txt", "typo_seen.txt", "typos.txt", "user.txt")],
    "music": ["playlists", "resume.m3u8", "skins", "music_playlog.tsv", "music_library.tsv"], "notes": ["notes"], "video": ["subs"], "ebook": ["covers"],
}
def prefs_of(i):
    names = set()
    for part in SRC.get(i, "").split(","):
        for path in _glob.glob(part):
            files = [path] if os.path.isfile(path) else [os.path.join(r, f) for r, _, fs in os.walk(path) for f in fs if f.endswith(".kt")]
            for fp in files:
                t = open(fp, encoding="utf-8", errors="ignore").read()
                names.update(re.findall(r'getSharedPreferences\("([A-Za-z0-9_]+)"', t))
                names.update(re.findall(r'\bFILE\s*=\s*"([A-Za-z0-9_]+)"', t))
    return sorted(names)
with open("app/src/main/java/com/umutk/pulse/features/share/PulseShareCatalog.kt", "w", encoding="utf-8") as f:
    f.write("package com.umutk.pulse.features.share\n\n// made by scripts/gen_standalone.py: do not edit by hand\n\n")
    f.write("/** What one app of Pulse brings over to the same app on its own: the names of its preferences files and the files / folders of Pulse's files folder. */\n")
    f.write("class ShareSpec(val prefs: List<String>, val files: List<String>)\n\nobject PulseShareCatalog {\n    val specs = mapOf(\n")
    for i, alias, name in apps:
        if i == "core": continue
        pf = ", ".join(f'"{n}"' for n in prefs_of(i)); fl = ", ".join(f'"{n}"' for n in FILES.get(i, []))
        f.write(f'        "{i}" to ShareSpec(listOf({pf}), listOf({fl})),\n')
    f.write("    )\n}\n")
print(len(apps), "apps:", " ".join(a[0] for a in apps))
