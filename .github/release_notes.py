import os
import sys

# The release notes are written in English, one short file per version (.github/notes/<version>.md); a release shows its own file only.
# YAPILACAKLAR.md is the author's own work log (Turkish) and never goes into a release.
cur_s, prev_s = sys.argv[1], sys.argv[2]
cur = tuple(int(x) for x in cur_s.split('.'))
prev = tuple(int(x) for x in prev_s.split('.')) if prev_s and all(x.isdigit() for x in prev_s.split('.')) else (0, 0, 0)
entries = []
def _ver(n):
    # a file that is not <version>.md (e.g. play-whatsnew-1.0.400.md) sorts first and is skipped below
    try:
        return tuple(int(x) for x in n[:-3].split('.'))
    except ValueError:
        return (0,)
if os.path.isdir('.github/notes'):
    for name in sorted(os.listdir('.github/notes'), key=_ver):
        if not name.endswith('.md'):
            continue
        try:
            v = tuple(int(x) for x in name[:-3].split('.'))
        except ValueError:
            continue
        # only this version's own notes: older versions are not repeated in a release
        if v == cur:
            text = open(os.path.join('.github/notes', name), encoding='utf-8').read().strip()
            entries.append(text)
print(f'## Pulse {cur_s}\n')
print('\n'.join(entries) if entries else 'Fixes and small changes.')
apps = []
if os.path.isfile('scripts/standalone_apps.txt'):
    for line in open('scripts/standalone_apps.txt', encoding='utf-8'):
        if line.startswith('#') or '|' not in line:
            continue
        i, alias, name, repo, src = line.rstrip('\n').split('|')
        apps.append(f"| {name} | `Pulse-{i}-{cur_s}.apk` | [{repo}](https://github.com/xUmutKx/{repo}) |")
print(f"""
### Which file: Pulse, all in one
| File | What it is |
|---|---|
| `Pulse-{cur_s}.apk` | **Full**: every app and tool, with Pulse AI (chat and speech to text on the phone). Most phones. |
| `Pulse-{cur_s}-lite.apk` | **Lite**: the full app without Pulse AI, smaller. Every other app and tool is in. |
| `Pulse-{cur_s}-mini.apk` | **Mini (safe)**: lite without the tools that need sensitive rights: no root or Shizuku, no SMS / MMS, no accessibility services (App lock, auto clicker, cache cleaner, keyboard helper), no APK installing (ArtCraft, in-app updates), no call log, no secure settings; its launcher names all start with Pulse. Pick this one if a virus scanner or ObtainX / Obtainium warns about Pulse. |
| `Pulse-{cur_s}-32bit.apk` | Lite for older 32-bit phones. |
| `PulseWatch.apk` | Pulse Watch, for Wear OS watches. |

### Which file: each app on its own
Every app of Pulse is also an app of its own: it installs beside Pulse (its own package, `com.umutk.pulse.<name>`), has its own icon and opens straight into that app, with no launcher and no other apps; all the looks are in it (Material by default). **Pulse Launcher** is the home screen (set it as the home app after installing); **Pulse Core** is Pulse's main app (the task manager, the settings and the toolkit) with no extra apps.

| App | File | Its own page |
|---|---|---|
""" + "\n".join(apps) + """

""")
print(f"""### Why a scanner may flag the full and lite apps
Pulse is an all-in-one app, so the full and lite APKs ask for rights that, taken together, look like what banking trojans ask for: it can be your **SMS app** (Messages), it has **accessibility services** (App lock, auto clicker, the cache cleaner that presses "Clear cache" in each app's settings), optional **root / Shizuku** tools, **APK installing**, the **call log** and **drawing over other apps**. A scanner sees only that list, not what the code does: Pulse sends none of your data anywhere and never touches other apps' data, and all the code is in this repository. If that worries you, use the **mini** APK: it has none of those rights.

Android 5 and newer (Pulse AI needs Android 9 and a 64-bit phone). Updating keeps your settings (mini, lite and full are the same app: one can replace another). Not every phone has been tested: a crash report can be made in Settings > Report > Create crash report.

[README and videos](https://github.com/xUmutKx/Pulse#readme)""")
