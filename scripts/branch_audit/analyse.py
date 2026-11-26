"""
For every old branch: which files have a NEWER version in the branch than in main?
 - branch_newer : main's blob of the path appears in the branch's history of that path (main is an ancestor version)  -> take the branch's file
 - main_newer   : the branch's blob appears in main's history of that path                                           -> keep main
 - divergent    : neither contains the other (edited in different places)                                           -> 3-way merge candidate
 - only_branch  : file exists only on the branch tip
Output: report.json
"""
import subprocess, json, sys, collections
REPO = '/home/user/pulse-main'
def git(*a, check=True):
    return subprocess.run(['git', '-C', REPO, *a], capture_output=True, text=True, check=check).stdout

def blobs_by_path(ref):
    """path -> [blob ids over the history of ref] (newest first), one pass over git log."""
    out = git('log', '--format=tformat:C', '--raw', '--no-abbrev', '--no-renames', ref)
    d = collections.defaultdict(list)
    for line in out.splitlines():
        if line.startswith(':'):
            meta, path = line.split('\t', 1)
            parts = meta.split()
            new = parts[3]; status = parts[4]
            if status != 'D': d[path].append(new)
    return d

def tree(ref):
    out = git('ls-tree', '-r', '--full-tree', ref)
    t = {}
    for line in out.splitlines():
        meta, path = line.split('\t', 1)
        t[path] = meta.split()[2]
    return t

main_tree = tree('origin/main'); main_hist = blobs_by_path('origin/main')
branches = sys.argv[1:] or ['backup-before-rewrite', 'ytx', 'branch-acc262', 'ytx-integration', 'icon-glass-game-hub-covers', 'work', 'pending-work-2026-10-21', 'size-and-compat', 'batch-1.0.166', 'backup-2026-10-22']
rep = {}
for b in branches:
    bt = tree('origin/' + b); bh = blobs_by_path('origin/' + b)
    cat = collections.defaultdict(list)
    for p, bb in bt.items():
        mb = main_tree.get(p)
        if mb is None: cat['only_branch'].append(p); continue
        if mb == bb: continue
        if bb in main_hist.get(p, []): cat['main_newer'].append(p)
        elif mb in bh.get(p, []): cat['branch_newer'].append(p)
        else: cat['divergent'].append(p)
    cat['only_main'] = [p for p in main_tree if p not in bt]
    rep[b] = {k: sorted(v) for k, v in cat.items()}
    print(b, {k: len(v) for k, v in cat.items()}, flush=True)
json.dump(rep, open('report.json', 'w'), indent=1)
