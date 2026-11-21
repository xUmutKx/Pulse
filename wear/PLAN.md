# Pulse Watch: the plan

The watch app today is stand-alone: small apps for a round screen, about 1 MB. This plan turns it into a companion of the phone app.
Nothing here is built yet. Each part ships on its own, in the order below, and each one ends with round-screen PNGs, a test of the
message format on the build machine and one check on a real watch.

## 0. The link (needed by everything below)

- **No Google Play services**: the foss build must stay free of `play-services-wearable`. The phone and the watch talk over a
  **Bluetooth RFCOMM socket** opened by the two apps (the watch is already paired with the phone), with a 6-digit code shown once on
  both screens. When both are on the same Wi-Fi, a local TCP socket is used for big transfers (files, video), found through NSD (mDNS).
- **Phone side**: `WatchLinkService`, a small foreground service in Pulse (notification "Watch connected"), started when the watch asks
  and stopped after 2 minutes of silence. **Watch side**: `PhoneLink` (connect, request / answer, stream, reconnect with backoff).
- **One message format**: a line of JSON `{ "t": type, "id": n, "b": body }`; files go as length-prefixed chunks of 64 KB with a CRC,
  resumable after a drop. A version number in the first message; an old side shows "Update Pulse" instead of failing.
- **Settings** (phone, Device manager > Watch): link on / off, which parts the watch may use (files, media, remote, notifications),
  forget the watch.

## The eight parts

### 1. File manager (first: parts 2, 3 and 6 stand on it)
- **On the watch**: two roots, "Watch" (its own storage) and "Phone" (through the link). A round list that bends to the circle,
  folders first, size and date under each name, the crown scrolls.
- **Actions**: open (music and video go to parts 2 / 3, pictures to a round viewer, text to a reader), copy phone to watch and watch
  to phone with a progress ring, delete (asks first), new folder, rename with the watch keyboard or voice.
- **Storage ring**: used / free of the watch; a "Clean up" page lists the biggest files.
- **Phone side**: answers list / stat / read / write in the shared storage it can already reach (all-files access or the folders the
  person picked); never the app-private folders.
- **Done when**: a 200 MB file copies both ways over Wi-Fi in under a minute, and over Bluetooth it resumes after the watch sleeps.

### 2. Music player on the watch
- Plays files that are on the watch (sent with part 1) through the watch speaker or Bluetooth earbuds, so a run needs no phone.
- **Screens**: now playing (cover in the middle, title curved along the top edge, a ring that fills with the position, crown = volume),
  queue, library (albums, artists, folders, playlists copied from Pulse's music player).
- **Sync**: "Keep on the watch" on a playlist in Pulse's music player copies it over Wi-Fi while the watch charges.
- Media3 `MediaSessionService` on the watch, so the watch's media controls and the earbuds' buttons work; synced lyrics (.lrc next to
  the file) a line at a time.

### 3. Video player on the watch
- Plays the video files on the watch (part 1) and short clips sent from the phone; Media3 ExoPlayer, the picture fitted into the
  circle (crop or fit, a tap switches), crown = seek 10 s, double tap = play / pause.
- "Send to watch" in Pulse's video player makes a small copy (480p, H.264) on the phone first, so any watch can play it.
- Subtitles (.srt next to the file) in one short line at the bottom of the circle.

### 4. Media remote (the phone's music and video)
- Now playing of **any app** on the phone through its media session (Pulse music, YTXTube, Spotify ...), read by the phone side with
  the notification access Pulse already asks for.
- **Controls**: play / pause, next / previous, seek, the phone's volume (crown), like, shuffle / repeat, the queue (next ten), the
  output (phone speaker, earbuds, a Bluetooth speaker).
- Cover art sent small (200 px), the lyrics of Pulse's player line by line, the Winamp / Poweramp look of the phone's player as a choice.
- A **tile** and a **complication** for the current song.

### 5. Phone remote control
- **Find my phone**: rings at full volume even when silent, the flashlight blinks, until stopped on the watch or the phone.
- **Camera shutter** with a live preview (small JPEG frames over Wi-Fi), a 3 s timer, front / back.
- **Toggles**: Wi-Fi, Bluetooth, mobile data, Do not disturb, flashlight, ringer mode, rotation, battery saver (the same Shizuku /
  root / WRITE_SETTINGS paths as the launcher's Settings pages).
- **Phone status**: battery and charging, signal, storage, temperature; an alert on the watch when the phone is left behind (the link
  drops for more than a minute after it was close).
- **Pulse shortcuts**: lock the phone, a screenshot, start the screen recorder, open an app on the phone, run a Pulse automation.
- **Presenter mode**: next / previous slide (Office in Pulse), and a touch-pad mode for the Windows launcher's mouse pointer.

### 6. YouTube and YouTube Music
- Through YTXTube on the phone (no Google account on the watch): **search** by voice or keyboard, **home / subscriptions / history**,
  a tap plays on the phone, or **on the watch** as audio (the phone hands over the audio stream; the watch plays it on its speaker or
  earbuds).
- **YouTube Music**: liked songs, playlists, mixes; "Download to watch" saves the audio (m4a) for offline runs (part 2 plays it).
- Video on the watch: the 144p / 240p stream for a short clip; long videos open on the phone.
- Comments and descriptions are left out (too small a screen).

### 7. Watch faces, tiles and complications
- Faces in Pulse's looks: **Windows 11** (taskbar along the bottom arc), **Windows 95** (grey clock on teal), **XP** (Luna),
  **Windows 7** (the Aero gadget clock), **AMOLED** and **Material**; the phone's Screen cover clocks (Analog, Word clock,
  Clock of clocks, Striking calendar) as faces too.
- Watch Face Format (Wear OS 4+), so no watch-face app is needed; the phone's look can push the same face to the watch.
- **Tiles**: phone battery, now playing, timers, steps, the next calendar event. **Complications**: phone battery, now playing,
  the phone's temperature, data used today.

### 8. Notifications, messages and lyrics on the wrist
- The phone's notifications through Pulse's own notification history (so the watch also gets those the system bridge hides),
  grouped by app, with **reply** (voice, keyboard, quick answers), **mark as read**, **mute this app on the watch**.
- The live lyrics of the playing song (Pulse's lyrics engine) as a running line.
- Calls: who is calling, answer / reject on the phone, a quick message.

## Order of work
1. Part 0 and part 1 (link and file manager): everything else stands on them.
2. Part 4 (media remote) and part 5 (phone remote): the most useful every day, small on the watch.
3. Part 2 and part 3 (music and video on the watch), then part 6 (YouTube / YouTube Music).
4. Part 7 (faces, tiles), then part 8 (notifications).

## Checks for every part
- Round-screen PNGs of each new page (an emulated round window in a test) for the review.
- A unit test of the message format and of chunked transfer with a dropped connection, run on the build machine.
- The watch build stays separate (`PulseWatch.apk`) and may fail without stopping the phone builds.
- English first, Turkish through `tr(en, tr)`; every page in the watch's black look.
