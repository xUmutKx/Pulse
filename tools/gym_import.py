#!/usr/bin/env python3
"""Turns the GymMane catalogue into the compact files of Pulse's gym log (app/src/main/assets/gym).

GymMane: https://github.com/InlitX/GymMane, code GPL-3.0, exercise art CC BY-SA 4.0 (from Workout Guide and Everkinetic, see THIRD_PARTY.md).
Usage: gym_import.py <path to the GymMane clone> <path to Pulse's assets/gym>
"""
import re, sys, os, shutil

src, out = sys.argv[1], sys.argv[2]
ART_COUNT = 130   # the first exercises of the catalogue keep their pictures, so the app stays small
Q = r"'((?:[^'\\]|\\.)*)'"

def unq(s): return s.replace("\\'", "'").replace('\\\\', '\\')

cat = open(os.path.join(src, 'lib/catalog/exercise_catalog.dart'), encoding='utf-8').read()
blocks = re.split(r'\n  Exercise\(', cat)[1:]
rows = []; arts = []
for b in blocks:
    def one(k):
        m = re.search(k + r': ' + Q, b); return unq(m.group(1)) if m else ''
    def lst(k):
        m = re.search(k + r': \[(.*?)\]', b, re.S)
        return [unq(x) for x in re.findall(Q, m.group(1))] if m else []
    art = one('art')
    if art and art not in arts: arts.append(art)
    keep = art in arts[:ART_COUNT]
    steps = [x.replace('|', ',').replace('\t', ' ') for x in lst('steps')]
    rows.append('\t'.join([one('id'), one('name'), one('primary'), ','.join(lst('secondary')), one('equipment'), one('difficulty'), art if keep else '', one('kind'), '|'.join(steps)]))
os.makedirs(out, exist_ok=True)
open(os.path.join(out, 'exercises.tsv'), 'w', encoding='utf-8').write('\n'.join(rows) + '\n')

def red(line):
    return re.sub(r'-?\d+\.\d+', lambda m: ('%.1f' % float(m.group())).rstrip('0').rstrip('.'), line)
shutil.rmtree(os.path.join(out, 'art'), ignore_errors=True); os.makedirs(os.path.join(out, 'art'))
for a in arts[:ART_COUNT]:
    p = os.path.join(src, 'assets/art', a + '.txt')
    if not os.path.exists(p): continue
    L = open(p, encoding='utf-8').read().split('\n')[:2]
    open(os.path.join(out, 'art', a + '.txt'), 'w').write('\n'.join(red(x) for x in L))

prog = open(os.path.join(src, 'lib/catalog/program_templates.dart'), encoding='utf-8').read()
lines = []
for tm in re.finditer(r"ProgramTemplate\('([^']+)', '([^']+)', \[(.*?)\n  \]\),", prog, re.S):
    tid, name, body = tm.groups()
    for dm in re.finditer(r"ProgramDay\('([^']+)', \[(.*?)\](?:, weekday: (\d))?\)", body, re.S):
        dn, exs, wd = dm.groups()
        items = re.findall(r"\('([^']+)', (\d+)\)", exs)
        lines.append('\t'.join([tid, name, dn, wd or '', '|'.join('%s:%s' % (e, n) for e, n in items)]))
open(os.path.join(out, 'programs.tsv'), 'w', encoding='utf-8').write('\n'.join(lines) + '\n')

foods = open(os.path.join(src, 'assets/data/foods.tsv'), encoding='utf-8').read().split('\n')
hdr = foods[0].split('\t'); ix = {h: i for i, h in enumerate(hdr)}
fl = []
for l in foods[1:]:
    c = l.split('\t')
    if len(c) < len(hdr) - 8: continue
    g = lambda k: c[ix[k]] if k in ix and ix[k] < len(c) else ''
    unit = g('g') if g('kind') == 'unit' else ''
    fl.append('\t'.join([g('en'), g('tr') or g('en'), g('k'), g('p'), g('c'), g('f'), unit]))
open(os.path.join(out, 'foods.tsv'), 'w', encoding='utf-8').write('\n'.join(fl) + '\n')
print(len(rows), 'exercises,', len(arts[:ART_COUNT]), 'pictures,', len(lines), 'program days,', len(fl), 'foods')
