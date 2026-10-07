import subprocess, re, json
want = {
 "processes":["Apps","App Generic"],"performance":["Pulse"],"history":["History"],"startup":["Rocket"],
 "users":["People"],"details":["Text Bullet List"],"services":["Puzzle Piece"],"settings":["Settings"],
 "menu":["Navigation"],"cpu":["Developer Board","Chip"],"memory":["Ram","Storage"],"disk":["Hard Drive","Storage"],
 "wifi":["Wifi 2","Globe"],"gpu":["Video Card","Games"],"battery":["Battery 8","Battery 10"],"thermal":["Temperature"],
 "search":["Search"],"close":["Dismiss"],"play":["Play"],"phone":["Phone"],"chevron":["Chevron Down"],"info":["Info"],
 "run":["Add Square"],"palette":["Color"],"speed":["Timer"],"table":["Table"],
}
out = {}
for k, names in want.items():
    for n in names:
        f = "ic_fluent_%s_24_regular.svg" % n.lower().replace(" ", "_")
        r = subprocess.run(["gh", "api", "repos/microsoft/fluentui-system-icons/contents/assets/%s/SVG/%s" % (n.replace(" ", "%20"), f), "-H", "Accept: application/vnd.github.raw"], capture_output=True, text=True)
        if r.returncode == 0 and "<path" in r.stdout:
            out[k] = re.findall(r' d="([^"]+)"', r.stdout)
            print(k, n, len(out[k]))
            break
    else:
        print("MISSING", k)
kt = "package com.umutk.pulse\n\n/** Fluent System Icons (MIT, Microsoft) 24px regular outlines, fetched by tools/fetch_icons.py */\nobject FluentPaths {\n    val map: Map<String, List<String>> = mapOf(\n"
for k, v in out.items():
    kt += '        "%s" to listOf(%s),\n' % (k, ", ".join(json.dumps(p) for p in v))
kt += "    )\n}\n"
open("/sdcard/Download/projects/Pulse/app/src/main/java/com/umutk/pulse/FluentPaths.kt", "w").write(kt)
