#!/data/data/com.termux/files/usr/bin/bash
# Builds Pulse from scratch in Termux. Run: bash termux-build.sh
set -e
pkg update -y
pkg install -y openjdk-17 git wget unzip aapt2
export JAVA_HOME=$PREFIX/lib/jvm/java-17-openjdk
SDK=$HOME/android-sdk
if [ ! -d "$SDK/cmdline-tools/latest" ]; then
  mkdir -p "$SDK/cmdline-tools"
  wget -q -O $HOME/clt.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q $HOME/clt.zip -d "$SDK/cmdline-tools" && mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
fi
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses >/dev/null || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" "platforms;android-34" "build-tools;34.0.0"
[ -d "$HOME/Pulse" ] || git clone https://github.com/xUmutKx/Pulse "$HOME/Pulse"
cd "$HOME/Pulse" && git pull -q
echo "sdk.dir=$SDK" > local.properties
G=$HOME/gradle-8.9
if [ ! -d "$G" ]; then
  wget -q -O $HOME/g.zip https://services.gradle.org/distributions/gradle-8.9-bin.zip
  unzip -q $HOME/g.zip -d "$HOME" 
fi
"$G/bin/gradle" assembleRelease --no-daemon -Dorg.gradle.jvmargs=-Xmx3g -Pkotlin.daemon.jvmargs=-Xmx3g \
  -Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2 -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease
mkdir -p ~/storage/downloads 2>/dev/null || true
cp "$(ls app/build/outputs/apk/release/app-arm64-v8a-release.apk 2>/dev/null || ls app/build/outputs/apk/release/app-*-release.apk | head -n 1)" /sdcard/Download/Pulse.apk && echo "APK: /sdcard/Download/Pulse.apk"
