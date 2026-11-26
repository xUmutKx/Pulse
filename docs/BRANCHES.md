# Branches: what is where

`main` is the line that ships (2.0.x). Its history starts on 20 November 2026 with the 2.0.9 snapshot; everything before it lives on the old branches below, which have **no common history** with `main`. No old branch is deleted: each one stays as it is and is the archive of its time.

| Branch | Last commit | What it is | Newer than `main`? |
|---|---|---|---|
| `backup-before-rewrite` | 2026-11-18 | the 1.0.x line right before `main` was started (closest to `main`) | no: 147 files are older versions of what `main` has, 85 differ in other places (checked one by one, see below) |
| `ytx`, `branch-acc262` | 2026-11-17 | the YTX work (same tree) with NextPlayer under `third_party/` | one file (`FilesHome.kt`, the "Received files" card) |
| `ytx-integration`, `icon-glass-game-hub-covers` | 2026-11-10 | glass icons and Game Hub covers | no |
| `batch-1.0.166` | 2026-11-07 | 1.0.166 batch | no |
| `work`, `pending-work-2026-10-21`, `size-and-compat`, `backup-2026-10-22` | October | early lines | no |
| `copilot/update-color-scheme` | 2026-11-21 | merged into `main` (PR 19) | no |
| `cautious-engine` | 2026-11-22 | drafts of the Reddit post (`REDDIT_POST.md`) | docs only |

## How it was checked
For every file that differs between a branch tip and `main`, the history of that file on both sides is compared:
- the branch's version is an older version of `main`'s file: `main` is newer, nothing to bring;
- `main`'s file is an older version of the branch's: the branch is newer, its file is taken;
- both changed it in different places: a three-way merge from the version of the branch's file that is nearest to what `main` started with; a clean merge is written, a conflict keeps `main`'s file.

Result over all branches: one file was newer on a branch (`FilesHome.kt`) and was brought over. Two clean merges (a Log off row in `Win11LauncherLook.kt` and a "Floating keys" tool in `Toolkit.kt`) were **not** kept: `main` already has the Log off row in a newer form, and the tool needs a service (`FloatingKeysService`) that `main` no longer has. The other 134 files that a machine could not merge (conflicts, or no version near enough to serve as a base) were then read through by their line counts and, where the branch had more lines than `main`, by their diffs. Every one turned out to be `main` being newer or being different on purpose:
- `assets/skins/index.json` (+544 lines on `ytx`): 68 community Winamp skins whose licence "is not stated by the author"; `main` ships 6 skins and left the rest out on purpose.
- `LocalSendBubble.kt`: `main` replaced the floating bubble with a notification.
- `WeatherVisuals.kt`, `WeatherScene.kt`, `WeatherHub.kt`: `main` has the real moon picture, the fixed list keys and the emoji switch that the branches lack.
- `Duplicates.kt`: `main` is drawn with Pulse's own look controls (PButton, PCard), the branch with plain Material.
- `debloat/uad_lists.json`: the same data, written on one line on `main` and over 43,000 lines on the branch.
- the files where `main` has hundreds more lines (`ExtraIcons.kt`, `LauncherSettings.kt`, `ChargeAnim.kt`, `BoxshelfWeb.kt`, `MegaGames.kt` ...): `main` has gone on from them.

The 12 branches are also kept as `archive/<name>` refs (the original names stay too; `batch-1.0.166` is the branch GitHub Pages serves). A git tag could not be pushed through this environment's proxy, a branch ref could.

Files that exist only on a branch (1,984 on `ytx`: old file layout, `third_party/nextplayer`, `extras/lockart`) are not imported; they stay on the archived branch.
