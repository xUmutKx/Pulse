import os
import sys

# The release notes are written in English, one short file per version (.github/notes/<version>.md).
# YAPILACAKLAR.md is the author's own work log (Turkish) and never goes into a release.
cur_s, prev_s = sys.argv[1], sys.argv[2]
cur = tuple(int(x) for x in cur_s.split('.'))
prev = tuple(int(x) for x in prev_s.split('.')) if prev_s else (0, 0, 0)
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
        if prev < v <= cur:
            text = open(os.path.join('.github/notes', name), encoding='utf-8').read().strip()
            entries.append(f'- {name[:-3]} {text}')
print(f'## Pulse {cur_s}\n')
print('\n'.join(entries) if entries else 'Fixes and small changes.')
print(f'\n**Which file:** `Pulse-{cur_s}.apk` is for Android 12 and newer. `Pulse-{cur_s}-android5.apk` is for older phones (Android 5 and newer). Neither has been tested on every phone, so please report bugs.')
