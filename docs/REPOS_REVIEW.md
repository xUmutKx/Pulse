# Repositories read for the keyboard, download and tweak requests (26 November 2026)

Read from a shallow clone of each; nothing was copied into Pulse. Pulse is GPL-3.0.

| Repository | Licence | What it is | Use for Pulse |
|---|---|---|---|
| deniscerri/ytdlnis | GPL-3.0 | YouTube/yt-dlp downloader on `youtubedl-android` (Python + yt-dlp), ffmpeg and aria2c as native packages | The licence allows it, but the three packages add tens of MB per ABI: against the 20 MB aim. What makes it fast is aria2c's several connections per file; Pulse's Download Manager already has ranges, `If-Range`, parts and resume (`features/Downloads.kt`), so a restart from zero is a stream that cannot be resumed (a YouTube link that expired), not a missing feature. |
| dtechterminal/s23-ultra-tweaks | GPL-2.0 | A root module (KernelSU/Magisk) that patches firmware pieces of one phone, the Galaxy S23 Ultra: camera drivers, modem files, kernel modules, XML flags, bound to named firmware builds | Not portable into an app: it edits system files by build, and a wrong patch can leave the camera or modem unusable. GPL-2.0 is not compatible with copying into a GPL-3.0 app. Left out. |
| futo-org/android-keyboard | FUTO Source First 1.1 | FUTO Keyboard | The licence limits changes to non-commercial use and is not GPL: its code and theme files cannot be taken into Pulse. Only the idea (a light, Gboard-like default theme) can be followed with Pulse's own colours. |
| opencut-app/opencut | MIT | A web video editor (TypeScript and Rust) | Not portable to Android. Pulse's Media tools already have Cut (Media3 Transformer); the timeline ideas can be followed there. |
| d4rken-org/sdmaid-se | GPL-3.0 | System cleaner (app cleaner, corpse finder, system cleaner, with a marker database of known leftovers) | Licence fits. Taken as an idea only: the system call that drops every app cache (`pm trim-caches`), now the Trim all action of the Cleaner. Its marker database and automation of the system settings are large and were not ported. |
