import re, sys, collections
n = int(sys.argv[1])
by = collections.OrderedDict()
try:
    for l in open('app/lint-newapi.txt', encoding='utf8', errors='ignore'):
        m = re.match(r'.*/umutk/pulse/(.*?\.kt):(\d+): Error: Call requires API level (\d+).*?: ([\w.#$]+) \[NewApi\]', l)
        if m: by.setdefault(m.group(1), []).append(f"{m.group(2)}={m.group(3)}:{m.group(4).split('.')[-1]}")
        else:
            m = re.match(r'.*/umutk/pulse/(.*?\.kt):(\d+): Error: (.*) \[NewApi\]', l)
            if m: by.setdefault(m.group(1), []).append(f"{m.group(2)}:{m.group(3)[:60]}")
except FileNotFoundError: pass
rows = [f"{k} {' '.join(v)}"[:600] for k, v in by.items()]
for r in rows[n*10:(n+1)*10]: print("::warning::" + r)
if n == 0: print(f"::warning::TOTAL files={len(rows)} hits={sum(len(v) for v in by.values())}")
