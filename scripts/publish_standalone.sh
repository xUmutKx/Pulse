#!/usr/bin/env bash
# Publishes each standalone app (files/Pulse-<id>-<ver>.apk) to a repository of its own (scripts/standalone_apps.txt): its code, a README and a release with the APK.
# Needs APPS_TOKEN (a token that can write to those repositories); the repositories must exist (create them empty on github.com first).
set -u
VER="$1"
while IFS='|' read -r ID ALIAS NAME REPO SRC; do
  case "$ID" in ''|'#'*) continue ;; esac
  APK="files/Pulse-$ID-$VER.apk"
  [ -f "$APK" ] || { echo "no $APK"; continue; }
  if ! gh api "repos/xUmutKx/$REPO" >/dev/null 2>&1; then echo "::warning::xUmutKx/$REPO does not exist: create it on github.com"; continue; fi
  W=$(mktemp -d)
  git clone -q "https://x-access-token:$GH_TOKEN@github.com/xUmutKx/$REPO.git" "$W" 2>/dev/null || git -C "$W" init -q
  ( cd "$W" && git rm -rq --ignore-unmatch . >/dev/null 2>&1 || true )
  mkdir -p "$W/src"
  for p in ${SRC//,/ }; do cp -r $p "$W/src/" 2>/dev/null; done
  cp LICENSE "$W/" 2>/dev/null
  cat > "$W/README.md" <<R
# $NAME

$NAME is one of the apps of [Pulse](https://github.com/xUmutKx/Pulse), here as an app of its own: it installs beside Pulse (its own package, \`com.umutk.pulse.$ID\`) and opens straight into $NAME, with no launcher and no other apps.

## Download
The APK of each version is on the [Releases](https://github.com/xUmutKx/$REPO/releases) page: \`Pulse-$ID-<version>.apk\`.

## Looks
Material by default; Windows 95, XP, 7, 10, 11, AMOLED and the others can be picked in its settings, as in Pulse.

## Code
\`src/\` holds the code of $NAME as it is in Pulse. The app is built from the Pulse repository (it shares Pulse's looks, settings and helpers), with \`-PpulseApp=$ID\`; issues and changes go to [Pulse](https://github.com/xUmutKx/Pulse).
R
  ( cd "$W" && git add -A && git -c user.name=xUmutKx -c user.email=xUmutKx@users.noreply.github.com commit -qm "$NAME $VER" && git branch -M main && git push -q "https://x-access-token:$GH_TOKEN@github.com/xUmutKx/$REPO.git" main ) || echo "::warning::could not push $REPO"
  gh release create "v$VER" "$APK" --repo "xUmutKx/$REPO" --title "$NAME $VER" --notes "$NAME $VER, built from Pulse $VER. Android 5 and newer." || echo "::warning::no release on $REPO"
  rm -rf "$W"
done < scripts/standalone_apps.txt
