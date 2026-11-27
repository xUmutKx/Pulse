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
print(f"""
### Which file
| File | What it is |
|---|---|
| `Pulse-{cur_s}.apk` | **Full**: every tool, with Pulse AI (chat and speech to text on the phone). Most phones. |
| `Pulse-{cur_s}-lite.apk` | **Lite**: the full app without Pulse AI, smaller. Every other tool is in. |
| `Pulse-{cur_s}-mini.apk` | **Mini (safe)**: lite without the tools that need sensitive rights: no root or Shizuku, no SMS / MMS, no accessibility services (App lock, auto clicker, cache cleaner, keyboard helper), no APK installing (ArtCraft, in-app updates), no call log, no secure settings. Pick this one if a virus scanner or ObtainX / Obtainium warns about Pulse. |
| `Pulse-{cur_s}-32bit.apk` | Lite for older 32-bit phones. |
| `Pulse-{cur_s}-play.aab` | The Google Play bundle (not for installing by hand). |

### Why a scanner may flag the full and lite apps
Pulse is an all-in-one app, so the full and lite APKs ask for rights that, taken together, look like what banking trojans ask for: it can be your **SMS app** (Messages), it has **accessibility services** (App lock, auto clicker, the cache cleaner that presses "Clear cache" in each app's settings), optional **root / Shizuku** tools, **APK installing**, the **call log** and **drawing over other apps**. A scanner sees only that list, not what the code does: Pulse sends none of your data anywhere and never touches other apps' data, and all the code is in this repository. If that worries you, use the **mini** APK: it has none of those rights.

Android 5 and newer (Pulse AI needs Android 9 and a 64-bit phone). Updating keeps your settings (mini, lite and full are the same app: one can replace another). Not every phone has been tested: a crash report can be made in Settings > Report > Create crash report.

[README and videos](https://github.com/xUmutKx/Pulse#readme)""")
