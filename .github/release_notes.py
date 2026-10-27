import re
import sys

cur_s, prev_s = sys.argv[1], sys.argv[2]
cur = tuple(int(x) for x in cur_s.split('.'))
prev = tuple(int(x) for x in prev_s.split('.')) if prev_s else (0, 0, 0)
entries = []
for line in open('YAPILACAKLAR.md', encoding='utf-8'):
    m = re.match(r'- (1\.0\.\d+) (.*)', line.strip())
    if not m:
        continue
    v = tuple(int(x) for x in m.group(1).split('.'))
    if prev < v <= cur:
        text = re.sub(r'\s*\((derlendi|derlenmedi)\)\s*$', '', m.group(2))
        entries.append(f'- {m.group(1)} {text}')
print(f'## Pulse {cur_s}\n')
print('\n'.join(entries) if entries else 'Fixes and small changes.')
print(f'\n**Which file:** `Pulse-{cur_s}.apk` is for Android 12 and newer. `Pulse-{cur_s}-android5.apk` is for older phones (Android 5 and newer). Both are signed with the fixed key in `ci/pulse.keystore`, so every release installs over the last one. Neither has been tested on every phone, so please report bugs.')
