# Flow and YTXTube: a comparison, and what was taken

Flow (https://github.com/Flow-Tube/Flow, GPL-3.0, read on 26 November 2026, commit `e064fbf`) is a YouTube and YouTube Music client for Android. Pulse is GPL-3.0 too, so parts of it may be used with credit (see `THIRD_PARTY.md`). This page is what a read of its source showed; nothing was measured on a phone.

## Size and stack

| | Flow | YTXTube in Pulse |
|---|---|---|
| Kotlin in the YouTube part | `innertube` 164 files / 19,100 lines, `data` 526 files / 81,000 lines, `player` 255 files / 36,600 lines, `service` 23 files / 4,300 lines | `extras/ytx` 72 files / 33,300 lines |
| Learning engine | `data/recommendation` (about 12,100 lines: tokenizer, topic catalogue, clusters, neighbours, specificity, Snowball stemmers, 672 KB of word-frequency lists) | `Neuro.kt`, `Taste.kt`, `YourTaste.kt` (about 560 lines) |
| Libraries | Hilt, Room, Ktor client **and server**, Media3 (ExoPlayer, HLS, DASH, muxer, inspector), Coil, CameraX, DataStore, Glance, BouncyCastle, BIP-39 (profile sync) | Media3, Coil, the app's own storage |
| YouTube access | its own InnerTube client (`innertube` package) | the `InnerTube` object (`Account.kt`) and `YouTube.kt`, with a proof-of-origin token provider |

Flow is about five times the size of YTXTube and rests on Hilt, Room and a Ktor server. It cannot be dropped into Pulse (about 50 MB for the whole app) and replacing YTXTube with it would mean rewriting every screen. What can be taken are separate algorithms, one at a time.

## The music radio (what the user asked about)

Flow's `Media3MusicService.startRadio`:
1. asks `next` for the playlist `RDAMVM<id>` (YouTube Music's automix of the song);
2. when that has one entry or none, asks again for the playlist id the answer itself names;
3. when still nothing, asks `next` for the song alone, and if even that is empty falls back to the related songs;
4. orders the songs (`musicBrain.rankTracks`, `sequenceRadioBatch`) and keeps a continuation to ask for more pages;
5. tops the queue up when it runs short (`maybeExtendRadio`).

What Pulse's music player did before: it played the picked song, then added the rest of the list shown, then guessed with plain searches ("artist title radio mix"). It never wrote what was played anywhere.

## What was taken (2.0.23)

- Steps 1, 2 and 3 above (`ytx/MusicRadio.kt`), and step 5 in a simpler form: when fewer than four songs are left the queue is filled again from the radio of the last queued song (`OnlineMusic.startRadio`). It stops by itself when a song of the phone starts.
- From Flow's `MusicRadioPlanner` (`player/MusicRadioPlanner.kt`, 149 lines, no Hilt): **no artist more than twice** in the queue, and a refill starts from the **anchors** (the seed and the songs YouTube placed closest to it, eight of them) and not from the newest song, which drifts away from the seed's genre after a few hops.
- A history of the online songs played in the music player (`OnlineHistory`, newest first, 400 kept on the phone), used by a **For you** list in the browse page: the radio of the latest played songs and of the liked songs, mixed, without what was played already.

## What was not taken, and why

- **Ranking of the radio by taste** (`musicBrain.rankTracks`) and the **continuation** pages: they depend on Flow's learning engine and its Room database. The anchors and the artist limit above stand in for them.
- **The learning engine itself** (`Neuro*`, 12,100 lines): the part that matters for a radio is the ranking; a port of the whole is a project of its own, with the Snowball stemmers and 672 KB of language data. It was not started.
- **Flow's player, downloads, sync, comments, shorts and profiles**: no gain for what was asked, and each rests on Room and Hilt.
- **Replacing the YouTube access (InnerTube) with Flow's**: not done. The slowness YTXTube shows was not traced to it; a replacement would need the same proof-of-origin token handling and would change every call. A profile of the home page and the player on the phone has to come first.

## Still open from the request

- Finding what makes YTXTube slow ("takip"): needs a trace on the phone (the Performance log in Settings > Report records it). Without a measured cause a rewrite would be a guess.
- Local music on the phone through the same radio, and Spotify songs through it: Spotify has closed its recommendation endpoint to new apps, so the Spotify suggestions have to be built from the YouTube Music radio of the matching song.
