<!-- Copy the title and the body below into a new Reddit post. Screenshots are in docs/screenshots; upload the ones you like. -->

# Title

Pulse: an open-source Android task manager that dresses up as Windows 95 / XP / 7 / 10 / 11 (or Material / AMOLED), with a file manager, installer, music player, Office, launcher, widgets and more (GPL-3.0)

# Body

Hi everyone! I have been building **Pulse** for a long time and it has grown into a big all-in-one app, so I want to show it to you and hear what you think. It is free, open source (GPL-3.0) and has no ads or account.

**What it is**

Pulse started as a Windows-style task manager for Android: live CPU, memory, storage, network, battery and temperature, with processes, app history, startup apps and services. Then it kept growing. Everything in it is drawn in the look you choose: **Windows 11, 10, 7, XP, 95**, **Material You** and **AMOLED**. Each look has its own windows, menus, buttons, dialogs, icons and animations, not just a colour swap. Windows 11 even has three icon styles (the Windows 11 icon pack, Fluent colour pictures, or thin line glyphs).

**Everything in it, quickly**

*System*
- **Task manager**: processes, performance graphs (CPU, memory, GPU, storage, network, battery, temperature), app history, startup apps, services, a detailed device page, a floating monitor, alerts and Quick Settings tiles.
- **App manager and debloater**: filter and sort apps, extract APKs, remove system apps with a Safe / Expert / Unsafe rating (Universal Android Debloater list), undo, reinstall.
- **Installer**: APK, APKS, APKM, XAPK and zip bundles (a zip with several apps installs them one by one), dialog / background / silent modes, root or Shizuku, biometric confirmation, Xposed module detection.
- **Downloads** with several connections, running in the background; share a link from your browser.
- **App lock** with pattern, PIN, password or fingerprint, also over bubbles and floating windows.
- **Tools**: toolkit (compass, level, lux meter, flashlight and more), terminal, network usage per app, DNS switch, cache cleaner, folder tree and file-type breakdown (measured once and saved), health page, screen cover with six clock styles and nine positions.

*Files*
- File manager with archives (zip, 7z, rar: browse, extract, compress, paste into a zip), a trash with undo, large and duplicate file finders, and root browsing of `/data/data` with every app's own icon. Zip files from any other app can be opened with Pulse. Progress notifications for long copies, deletes and compressions.

*Media*
- **Music player** with four looks: Material, Poweramp, Winamp (real skins, lyrics drawn inside the skin) and Windows Media Player. Library with albums / artists / genres, m3u playlists, mood mixes, search through songs and lyrics, a scrolling waveform seek bar, sound quality (format, kHz, bit, kbps) under the buttons, album art and tags from the internet (iTunes, no key), synced lyrics, ratings, sleep timer, white noise and bird sounds.
- **Video player** that searches subtitles online, like VLC.
- **Office**: Word, Excel and PowerPoint drawn like the old (95 / 2003) and new (2013 / 365) Office, and a Notepad-style text editor per look (with an AMOLED black option).
- **Photos, Calendar, Contacts, Messages (SMS/MMS), Notes, Weather, Voice and Screen recorder, notification history** and a **Paint** app in the style of MS Paint. Each is its own launcher entry with its own look, and none of them changes the main app's look.

*Home screen*
- **Launcher**: Pulse can be your home screen. In the Windows looks it is a desktop (This PC, Recycle Bin, File Explorer, Paint, Command Prompt, Task Manager) with windowed apps; the other looks keep a clean home with a clock, widgets and a dock. Apps are grouped by category.
- **Widgets in every look**: Windows 7 gadget clocks, CPU meter, XP / 95 classics, Windows 11 tiles, Material You and AMOLED tiles, and music widgets (Winamp with real skins, Winamp visualizer, Poweramp, a plain player with an opacity setting).

**Settings worth knowing**
- One **Apps** card lists every optional app, tool and page, and each one can be placed in the top-right menu, the left menu, or only in Settings.
- English and Türkçe, light / dark / system, accent colours, Mica / Acrylic backdrops, wallpapers per look.

**Requirements**

Android 12 or newer, arm64. Root and Shizuku are optional and unlock more (silent install, services, startup switches, the debloater, `/data/data`). Most of it works without either.

**Honest notes**

- It is a hobby project that grew a lot, so expect rough edges and bugs. Bug reports and ideas are very welcome.
- Release builds are signed with a public key kept in the repo (so updates install over each other); use your own key if you publish one.
- Some features (online playlists, subtitles, lyrics, covers) depend on public servers that come and go.

**Link**

GitHub (source, builds and screenshots): https://github.com/xUmutKx/Pulse

If you try it, tell me which look you picked and what you would add or fix next!
