import sys
# The notes of a release of Pulse's standalone apps: what each file is, and where each app has its own page.
ver = sys.argv[1]
rows = []
for line in open("scripts/standalone_apps.txt", encoding="utf-8"):
    if line.startswith("#") or "|" not in line:
        continue
    i, alias, name, repo, src = line.rstrip("\n").split("|")
    rows.append(f"| {name} | `Pulse-{i}-{ver}.apk` | [{repo}](https://github.com/xUmutKx/{repo}) |")
print(f"""## Pulse's apps on their own ({ver})

Every app of Pulse is also an app of its own: it installs beside Pulse (its own package, `com.umutk.pulse.<name>`), has its own icon and opens straight into that app, with no launcher and no other apps. The looks are all there (Material by default; Windows 95, XP, 7, 10, 11, AMOLED and the others in its settings).

Want everything in one app? Pulse itself (full, lite or mini) has all of them: see the [latest Pulse release](https://github.com/xUmutKx/Pulse/releases/latest).

| App | File | Its own page |
|---|---|---|
""" + "\n".join(rows) + """
| Pulse Watch (Wear OS) | `PulseWatch.apk` | (in Pulse) |

**Pulse Launcher** is the home screen (set it as the home app after installing). **Pulse Core** is Pulse's main app: the task manager, the settings and the toolkit, with no extra apps.

These are built from the same code as Pulse; they ask only for the rights their own app needs, through the same screens as in Pulse. Not every phone has been tested.""")
