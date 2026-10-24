## Pulse 1.0.4

Changes since 1.0: a weather and clock widget with no background (tap to change the place); the keyboard's emoji page (search on top, category bar below) and symbols page in the phone keyboard's layout; autocorrect no longer repeats a correction you undid and treats a word you undo twice as yours; more of your own words are known from the start. The full 1.0 description follows.

## Pulse 1.0

The first complete release. Pulse is an all-in-one Android toolbox that can dress up as Windows 95 / XP / 7 / 10 / 11, Material or AMOLED, with its own launcher, music player, keyboard, file manager, messages app and more. Free and open source (GPL-3.0).

**Which file:** `Pulse-1.0.4.apk` is for Android 12 and newer. `Pulse-1.0.4-android5.apk` is the build for older phones (Android 5 and newer, "low API"). Builds are signed with the fixed key in `ci/pulse.keystore`, so every release installs over the last one. Both builds were compiled in CI; neither has been tested on every phone, so please report bugs.

### What is new since 0.10.x
- **Keyboard (new)**: Pulse's own keyboard with themes, one-handed and resizable layouts, RGB light, brightness, swipe typing, an extra key row (Esc, Tab, Ctrl, Alt, Win, Shift, arrows, F keys) with optional icons, a number pad for number / phone / date fields, emoji with search and kaomoji, a clipboard history, in-keyboard translation and a password-manager chip row.
- **Autocorrect that learns**: a touch model that learns where your fingers land, Turkish word-ending repair, your own typos, words you banned, a similarity setting (default 80%) and a typing practice page in the keyboard settings.
- **Messages**: a full SMS / MMS app (default-SMS capable) with reply, read and copy-code actions in notifications, bubbles, mute, pin, block, an undo-send delay, a signature and automatic spam blocking.
- **Galaxy Buds manager**: noise control, touch controls, equalizer, ambient volume, 360 audio, voice detect, find my earbuds, wear detection, and now noise control by movement (ambient sound while you walk, noise cancelling when you stand still).
- **Music player**: Poweramp backup import (playlists, ratings on a 5-star scale, play counts, covers) and export, favourites with star counts and sorting, genre equalizer, lyrics in the notification and on the cover, album-cover search.
- **Launcher**: home links to apps, files, pictures and folders, a New menu, launcher sounds on the media volume, Windows 11 landscape wallpaper.
- **Files**: home page with file types, recents and sizes, a More menu with media tools and the converter, cache cleaner rules from SD Maid SE, compress with quality slider and preview.
- **Screen cover**: closes with two quick taps anywhere.

### Everything Pulse does
**Task manager**: live CPU, memory, storage, network, GPU, battery and temperature graphs; processes with End task; app history; startup apps and services (root or Shizuku); app manager and debloater with Safe / Expert / Unsafe marks.

**Looks**: Windows 95, XP, 7, 10 and 11 (with dark versions), Material You and AMOLED. Every screen, dialog, icon and animation is drawn for each look; the extra apps keep their own look.

**Launcher**: a home screen for every look (Windows taskbars and Start menus, Material, Windows Phone tiles), framed Windows pages (This PC, Recycle Bin, Paint, Task Manager, Control Panel), widgets, folders, wallpapers with dimming and blur, boot screens and sounds.

**Music**: library by song, album, artist, genre and folder; playlists (including `.m3u`); mixes by mood; lyrics (synced, search by text); waveform seek bar; Winamp, Poweramp and Material looks with matching home-screen widgets; sleep timer; equalizer; white noise; Bluetooth auto-play.

**Video player** with online subtitle search. **Photos**, **Calendar**, **Contacts**, **Notes**, **Weather**, **Paint**, **Voice recorder**, **Screen recorder**, **Notification history**, **White noise**, **ArtCraft launcher**.

**Files**: archives (zip, 7z, rar) browse, extract and compress; folder tree; large and duplicate finders; type breakdown; cache cleaner; root browsing; media tools (GIF maker, converter, compressor).

**Installer**: APK, APKS, APKM, XAPK and zip bundles, dialog / background / root / Shizuku modes. **Downloads** with several connections that survive in the background.

**Tools**: compass, level, flashlight, lux meter, calculator (a key layout per look), stopwatch, timer, clipboard, terminal with quick commands, network usage per app, Private DNS, watchdog, screen cover with six clock styles, cache cleaner, folder sizes, temperature graph.

**Widgets**: more than twenty system widgets, Windows 7 gadgets, XP / 95 classics, Windows 11 tiles and ten music-player skins.

**Languages**: English and Turkish.

### Notes
- Root and Shizuku are optional; most features work without them.
- Win + D / L / A / N and Alt + Tab on the keyboard use Pulse's accessibility helper; they do nothing while it is off.
- It is a hobby project: expect rough edges.
