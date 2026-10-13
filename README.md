# Pulse

Pulse is a task manager for Android that looks like the Windows one. It shows live CPU, memory, storage, network, battery and temperature, and it has tools for the rest of the phone: a file explorer, an APK installer, a downloads manager, a terminal and a toolkit of small utilities.

Pulse needs Android 10 or newer, and it is built for arm64 phones only. The APK is about 11 MB.

## First start

The first time you open Pulse, a welcome screen lists what the app does and the open-source parts it is built from. Tap **Start** to go on; it does not come back. The language is set in Settings > Language (phone language, English or Türkçe).

## Features

### Task manager

- **Processes**: apps and background processes in groups, with CPU and memory per process and an End task button.
- **Performance**: live graphs for CPU, memory, storage, network, GPU, battery and temperature.
- **App history**: foreground time per app today, screen unlocks and a notification count, as a list or a pie chart. Needs usage access; the notification count needs notification access.
- **Startup apps**: boot receivers, which you can switch off with root or Shizuku.
- **Services**: running services (root only).
- **Details** and **Apps**: search, filter, sort by name, size, install date or last used, open, app settings, uninstall, force stop, and extract the APK.
- **Guardians**: a detail page each for Thermal, Battery, Storage and Memory, with a verdict and 30-minute graphs.

### Monitoring

- **Floating monitor** over other apps, live numbers in a notification, and alerts for temperature, battery and memory with limits you set.
- **Quick Settings tiles** for the monitor, Private DNS and seven toolkit tools.
- **20 home-screen widgets**: battery, memory, storage, network, temperature, clock, downloads and more.
- **Device**: system, CPU and per-core frequency and governors, memory, storage, battery, network with a latency test, thermal zones, display, camera, sensors, media and DRM, system properties and a short benchmark.
- **Health**: step counter, heart rate from the camera flash, and location tracking. Each is asked for only when you turn it on.

### Files and install

- **Files**: a file explorer with a trash folder. Deleted files go to `.pulse_trash` until the next delete, so Undo can bring them back.
- **Installer**: installs APK, APKS, APKM, XAPK and zip bundles. Modes are dialog, background, auto and silent. Silent install uses root or Shizuku. The installer detects Xposed modules and has an LSPosed button. You can require biometrics before each install.
- **Downloads**: several connections for files the server can split, and downloads keep running in the background. Paste a link, or share a link from your browser to Pulse.
- **Extract APK**: copies an installed app to `Downloads/Pulse/extract`. Split apps are saved as one `.apks` file.

### Tools

- **Toolkit**: 20 tools, including compass, level, flashlight, lux meter, dice, coin flip, clipboard, screenshot (root), volume, stopwatch, timer, calculator, screen scale and a finder.
- **Terminal**: runs commands on the phone's shell, with ADB-style quick commands (model, battery, packages, logcat and more). A Run in Termux button sends a command to Termux instead.
- **RAM**: memory history and a Free memory button that stops background apps (root).
- **Network usage**: data use per app, split into mobile and Wi-Fi.
- **DNS**: Private DNS as Auto, Off or Manual with a host name. Needs root or Shizuku.
- **Watchdog**: every 15 minutes it restarts the monitor and any running downloads that Android has stopped.
- **Cache cleaner**, **folder sizes**, **type breakdown** and **temperature graph**, from the Tools page.

## Themes

Pulse has six looks:

- Windows 11, with a dark version
- Windows 10, with a dark version
- Windows 7
- Windows XP
- Windows 95
- Material You, which uses the wallpaper colours on Android 12 and newer

You can choose light, dark or the system setting. The Windows 11, Windows 10 and Material looks have an accent colour choice. The sidebar looks have a backdrop effect: None, Mica, Mica Alt, Acrylic or Glass. Pop-up windows, dialogs and menus can use the Windows, Material or AMOLED black look.

## Permissions

Pulse asks for these. Most are optional and only used by the feature named.

- **Usage access**: app history and per-app network use.
- **All files access**: the folder, cache and file tools. Granted from the Tools page.
- **Display over other apps**: the floating monitor.
- **Notifications**: the live notification and alerts.
- **Notification access**: the notification count in app history.
- **Install unknown apps**: the installer.
- **Internet**: downloads and the latency test.
- **Biometrics**: optional install confirmation.
- **Camera**: heart rate on the Health page.
- **Activity recognition and location**: the Health page step counter and location tracking.
- **Termux run command**: the Run in Termux button.
- **Query all packages**: the app list.

## Root and Shizuku

Pulse works without root. Root or Shizuku unlock more.

**Root** (the `su` command) unlocks:

- the full process list, kernel data and temperature zones
- services
- startup app switches
- the Free memory button in RAM
- cache trimming in the cache cleaner
- the screenshot tool and the root tab in Files
- silent install, downgrades that keep app data, and restoring app data

**Shizuku** runs the same shell commands as root for startup switches, killing and force-stopping apps, and the terminal. It also does silent installs and sets Private DNS.

Without root or Shizuku you still get memory, battery, storage, network, device info, sensors, the dialog installer, downloads, files, the toolkit and the floating monitor.

## Build

Pulse is written in Kotlin with Jetpack Compose. The project has no Gradle wrapper, so build with Android Studio, or with a Gradle 8.9 install.

You need JDK 17 or newer and the Android SDK with platform 34. Put the SDK path in `local.properties`, which is not committed.

```
gradle :app:assembleRelease
```

The APK is written to `app/build/outputs/apk/release/`. Release builds are currently signed with the debug key, so change the signing config before you publish one.

## Licences

- **Microsoft Fluent System Icons** (MIT). The icons in the Windows themes are outlines taken from them by `tools/fetch_icons.py` into `FluentPaths.kt`.
- **Shizuku API** (MIT, copyright RikkaW). The licence text is in `app/src/main/assets/licenses/`.
- **Windows XP icon pack** by Matt Pettit (CC0 1.0, public domain dedication). The XP theme's Apps, Download, Heart, Phone and Startup icons come from it, resized to 48 px. The licence text is in `app/src/main/assets/licenses/xp-icons-LICENSE.txt`.
- **Windows logos, wallpapers and other images** in `app/src/main/res/drawable-nodpi/`. The repository has no licence file for these yet, and their sources are not recorded here.

Pulse's own code has no licence file yet.

## Screenshots

Screenshots go in `docs/screenshots/`, with the names below.

### Processes

![Processes](docs/screenshots/01-processes.png)

### Performance

![Performance](docs/screenshots/02-performance.png)

### App history

![App history](docs/screenshots/03-app-history.png)

### Installer

![Installer](docs/screenshots/04-installer.png)

### Toolkit

![Toolkit](docs/screenshots/05-toolkit.png)

### Authenticator

![Authenticator](docs/screenshots/06-authenticator.png)

### Troubleshoot

![Troubleshoot](docs/screenshots/07-troubleshoot.png)

### Settings

![Settings](docs/screenshots/08-settings.png)
