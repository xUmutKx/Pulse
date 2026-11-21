import os
import sys

# The release notes are written in English, one short file per version (.github/notes/<version>.md); a release shows its own file only.
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
        # only this version's own notes: older versions are not repeated in a release
        if v == cur:
            text = open(os.path.join('.github/notes', name), encoding='utf-8').read().strip()
            entries.append(text)
print(f'## Pulse {cur_s}\n')
print('\n'.join(entries) if entries else 'Fixes and small changes.')
print(f'\n### Which file\n- `Pulse-{cur_s}.apk`: the full app, with Pulse AI (chat and speech to text on the phone). Most phones.\n- `Pulse-{cur_s}-lite.apk`: the same app without Pulse AI, smaller.\n- `Pulse-{cur_s}-32bit.apk`: for older 32-bit phones.\n- `Pulse-{cur_s}-play.aab`: the Google Play bundle (not for installing by hand).\n\nAndroid 5 and newer (Pulse AI needs Android 9 and a 64-bit phone). Updating keeps your settings. Not every phone has been tested: a crash report can be made in Settings > Report > Create crash report.\n\n[README and videos](https://github.com/xUmutKx/Pulse#readme)')
