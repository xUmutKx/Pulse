"""
Bring the newer parts of the old branches into the working tree (which starts as main), file by file:
  * the branch's file is a later version of main's file          -> take the branch's file
  * both changed it (different places)                           -> 3-way merge from the last version both have seen; clean merges are written, conflicting ones are kept as main's and reported
Files that exist only on the branch are not imported (they were moved or dropped on purpose, and they stay on the archived branch).
"""
import subprocess, sys, os, json, collections, tempfile
REPO = '/home/user/pulse-main'
ORDER = ['backup-before-rewrite', 'ytx', 'branch-acc262', 'ytx-integration', 'batch-1.0.166', 'work', 'pending-work-2026-10-21', 'size-and-compat', 'backup-2026-10-22']
TEXT_EXT = ('.kt', '.kts', '.java', '.xml', '.md', '.json', '.py', '.yml', '.yaml', '.gradle', '.pro', '.txt', '.toml', '.properties', '.js', '.html', '.css', '.rs', '.cpp', '.h', '.c', '.cmake', '.sh')

def git(*a, text=True, check=True):
    return subprocess.run(['git', '-C', REPO, *a], capture_output=True, text=text, check=check).stdout

def blobs_by_path(ref):
    out = git('log', '--format=tformat:C', '--raw', '--no-abbrev', '--no-renames', ref)
    d = collections.defaultdict(list)
    for line in out.splitlines():
        if line.startswith(':'):
            meta, path = line.split('\t', 1)
            parts = meta.split(); new, status = parts[3], parts[4]
            if status != 'D': d[path].append(new)
    return d

def tree(ref):
    t = {}
    for line in git('ls-tree', '-r', '--full-tree', ref).splitlines():
        meta, path = line.split('\t', 1); t[path] = meta.split()[2]
    return t

def blob_bytes(b): return subprocess.run(['git', '-C', REPO, 'cat-file', 'blob', b], capture_output=True, check=True).stdout
def hash_file(p): return git('hash-object', p).strip()

main_hist = blobs_by_path('origin/main'); main_tree = tree('origin/main')
applied = collections.defaultdict(set)            # path -> branch blobs already taken or merged
report = collections.defaultdict(lambda: collections.defaultdict(list))
for b in ORDER:
    bt = tree('origin/' + b); bh = blobs_by_path('origin/' + b)
    for p, bb in sorted(bt.items()):
        full = os.path.join(REPO, p)
        if not os.path.isfile(full): continue                       # only on the branch: stays on the archive
        cur = hash_file(full)
        if cur == bb or bb in applied[p]: continue
        mh = set(main_hist.get(p, []))
        if bb in mh: continue                                       # main has gone past this version
        text = p.endswith(TEXT_EXT)
        # the branch is simply later: main's current file is one of its earlier versions
        if cur in set(bh.get(p, [])):
            open(full, 'wb').write(blob_bytes(bb)); applied[p].add(bb); report[b]['taken'].append(p); continue
        if not text: report[b]['binary_differs'].append(p); continue
        # no version both lines have seen (main's first snapshot, taken on 20 November, already differs from every earlier one): the base is the version of the
        # branch's file that is nearest to what main started with, the point main took the file from the branch
        m0 = main_hist[p][-1] if main_hist.get(p) else None
        if not m0: report[b]['no_common_base'].append(p); continue
        best = None
        for x in bh.get(p, [])[:80]:
            if x == m0: best = (0, x); break
            ns = git('diff', '--numstat', m0, x, check=False).split()
            if len(ns) >= 2 and ns[0].isdigit() and ns[1].isdigit():
                d = int(ns[0]) + int(ns[1])
                if best is None or d < best[0]: best = (d, x)
        if best is None: report[b]['no_common_base'].append(p); continue
        n_lines = max(1, len(blob_bytes(m0).splitlines()))
        if best[0] > .5 * n_lines: report[b]['base_too_far'].append(p); continue
        common = [best[1]]
        if best[1] == bb: applied[p].add(bb); continue               # main already holds the branch's latest version of this file
        with tempfile.TemporaryDirectory() as td:
            base_p, theirs_p = os.path.join(td, 'base'), os.path.join(td, 'theirs')
            open(base_p, 'wb').write(blob_bytes(common[0])); open(theirs_p, 'wb').write(blob_bytes(bb))
            ours_p = os.path.join(td, 'ours'); open(ours_p, 'wb').write(open(full, 'rb').read())
            r = subprocess.run(['git', 'merge-file', '-p', ours_p, base_p, theirs_p], capture_output=True)
            if r.returncode == 0:
                if r.stdout != open(full, 'rb').read():
                    open(full, 'wb').write(r.stdout); report[b]['merged'].append(p)
                applied[p].add(bb)
            elif r.returncode > 0: report[b]['conflict'].append(p)
            else: report[b]['error'].append(p)
    print(b, {k: len(v) for k, v in report[b].items()}, flush=True)
json.dump({b: dict(v) for b, v in report.items()}, open('merge_report.json', 'w'), indent=1)
