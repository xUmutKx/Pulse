# Pulse — project rules

Android app (Kotlin, Compose, package `com.umutk.pulse`, sources in `app/src/main/java/com/umutk/pulse/`).
Work log: `/sdcard/Download/projects/YAPILACAKLAR.md` (append with `printf >>`, mark "derlenmedi" until built).

## Looks (themes) — the central rule

Pulse has one look system (`Look`, `PTheme`, `LookHost`, `LocalWindowLook`, `PIcon`, `PSwitch`/`PCheck`/`PRadio`).
Looks: Windows 95, XP, 7, 10, 11, Material, AMOLED (+ One UI, ColorOS where they exist).

- **Every feature, screen, dialog, menu, button, switch, icon and animation must be drawn for EVERY look.**
  A new composable that only has a Material version is unfinished. Use the per-look controls and the
  per-look icon mapping (`PIcon` / `LIc` / `CtGlyph` / `CalIc`), never a hard-coded Material `Icon` in a Windows look.
- Windows 365/2017 Office looks are FLAT: no gradients, gloss or shadows. Gradients only in 95/2003.
- Win11 is the only look that is translucent (Mica/blur). 95/XP/7/10 stay opaque.
- Dark/AMOLED: pure `#000000` background, light on-colours. Never leave default `LocalContentColor`
  (black) outside a Material `Surface`; provide the look's text colour at the root.
- When the look changes, everything re-resolves immediately (key remembered painters/colours on the look id).
- Material is the DEFAULT look of every extra app; the user can change it in that app's settings.
- Do not tell the user how to use something (no "long press / drag / tap" captions). Icons over words, short text.
- English default, Turkish via `tr(en, tr)`.

## Extra apps (Files, Photos, Calendar, Contacts, …)

- Standalone launcher entries (activity-aliases) with their own look store (`FilesLook`, `PhotosPrefs`, `CalPrefs`, `CtPrefs`).
- They must never mix looks with the main Pulse app: apply the app's own look before the first frame
  (`AppLookGuard.enter`), restore Pulse's saved look on stop (`AppLookGuard.leave`), and never write Pulse's
  `Cfg`/`Opt` from an extra app's settings.
- No opening/splash animation when an extra app starts standalone.
- Reachable from the top-right ⋮ menu under "Extra apps" (Menus.kt) and Settings > Extra apps (launcher switch, set default).
- Settings screens: collapsible sections with chevrons, full-width rows (icon, title, value) that open radio dialogs,
  no pill chips, themed switches. Scroll-edge fading only on the theme preview row.

## Building

- Never run Gradle or build unless the user says "derle". One Gradle at a time (7 GB RAM).
- Kotlin check: `/sdcard/Download/projects/_scripts/compile_check.sh Pulse` (~5 min).
  Full build: `_scripts/build_one.sh Pulse <version>` (bump `versionName`/`versionCode` in `app/build.gradle.kts`).
  Run long jobs with `nohup setsid sh … &`; wait for `=== Pulse <ver> OK|FAIL` as the LAST line of `_logs/build_seq.log`.
- Build cache is off globally (`org.gradle.caching=false`).
- Agents split by file ownership; they never start Gradle.

## Pitfalls seen

- `FilesActivity` must not depend on process-global `Look`/`Cfg`/`Opt`; use its own provider.
- Concurrent agents can drop spaces in `AndroidManifest.xml`: validate with `python3 -I -c "import xml.dom.minidom as m; m.parse(path)"`.
- A translucent launch theme can make a launcher open an activity "behind"; keep launch themes opaque unless the look is Win11.
