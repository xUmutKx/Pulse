# Pulse Watch: the plan

The watch app today is stand-alone: 16 small apps for a round screen, about 1 MB. The plan makes it a companion of the phone app, in steps that each ship on their own.

## 0. Ground (one step)
- `wear/` gets the Wear Data Layer (`play-services-wearable` is not used: the foss build must stay free of it; the link is a local Bluetooth RFCOMM socket opened by the two apps, with a pairing code shown once).
- The phone side is a small foreground service in Pulse (`WatchLinkService`) that answers requests; the watch side is `PhoneLink` (connect, request, stream).
- One message format: a line of JSON, a type and a body; files go as length-prefixed chunks.

## 1. Remote (first value)
- Now playing and play / pause / next / previous / volume for the phone's music (Pulse music, YTXTube, any app through the notification media session).
- The same for the video player of Pulse.
- A round UI: a ring that fills with the position, the title curved along the top edge.

## 2. Phone files and music
- Browse the phone's folders (read-only first), play a song from the list on the phone, send a file to the watch.
- A music queue page: the next ten songs, tap to jump.

## 3. Health
- Heart-rate history, steps by day, sleep from the watch's own sensors (Health Services), shown as rings and small graphs; the numbers are sent to Pulse's Health page.

## 4. Apps and processes
- A list of the watch's apps with sizes; install an APK sent from the phone (the watch asks, the person confirms).
- Processes: the watch's own (Android shows no others without root).

## 5. YouTube and Game Hub on the wrist
- YTXTube: the queue and the search results from the phone, a tap plays on the phone or on the watch's speaker.
- Game Hub: the list of games, a tap starts the game on the phone.

## Order of work and checks
1. Step 0 and 1 first: the link and the music remote (the smallest thing that is useful).
2. Each step: a PNG of the round screens (emulated round window in a test), a test of the message format on the build machine, and one device check by the owner.
3. The watch build stays separate and may fail without stopping the phone builds.
