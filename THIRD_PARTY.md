
## Textures

The wood, steel, plastic and leather textures used by the cassette, turntable and iPod looks come from ambientCG (https://ambientcg.com), released under CC0 1.0 (public domain). No attribution is required; it is given here anyway.

## GymMane (gym log in Health)

The exercise catalogue (names, muscles, equipment, steps), the ready-made programs and the food table of Pulse's gym log come from
[GymMane](https://github.com/InlitX/GymMane) by InlitX, code under GPL-3.0. The exercise pictures (first 130 of the catalogue, two frames each, numbers rounded to one
decimal) come through GymMane from [Workout Guide](https://github.com/bryllim/workout-guide) by Bryl Lim and
[Everkinetic](https://github.com/everkinetic/data), licensed under [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/); the copy in
`app/src/main/assets/gym/art/` stays under that license. `tools/gym_import.py` makes the files in `app/src/main/assets/gym/`.

## Atelier ASCII (inspiration for the ASCII art tool)

The ASCII art tool of the Toolkit follows the modes and the dithering of [Atelier ASCII](https://github.com/) by its author (MIT licence): tones, characters, text, 1-bit and Braille, Floyd–Steinberg, Atkinson and Bayer dithering. The code in Pulse is written from scratch for Android.

## Halftone QR (method)

The halftone QR tool uses the "submodule" method described at kloet.net/usr/halftone (a QR module is cut into 3x3 blocks, the middle block keeps the module's colour and the other eight carry the picture). The code is written from scratch; the QR itself is made with ZXing (Apache 2.0).

## YTXTube (carried in the open-source build)

[YTXTube](https://github.com/Koustubh12345/YTXTube) by Koustubh12345, GNU GPL v3: a YouTube and YouTube Music client. Its source is in `extras/ytxtube` of this repository with these changes: Shorts removed from the bottom bar, no AMOLED theme, its own updater switched off, a different application id (`com.umutk.pulse.ytx`) and signed with Pulse's key. It is built by the workflow and installed from Extra apps; it is an app of its own. It uses NewPipeExtractor (GPL v3) and other libraries listed in its `app/build.gradle.kts`. It is not affiliated with or endorsed by Google or YouTube; using it is the user's own responsibility under YouTube's terms. It is left out of the Google Play build.

## Athenaeum (carried in the open-source build)

[Athenaeum](https://github.com/zad1ag/athenaeum) by zad1ag, GNU GPL v3: an offline occult companion (tarot, astrology, natal charts, spirits, spells, staves, runes) with a Rust core and a Compose interface. Its source is in `extras/athenaeum` of this repository with these changes: a different application id (`com.umutk.pulse.athenaeum`), only the arm64-v8a and armeabi-v7a libraries, signed with Pulse's key. It is built by the workflow and installed from Extra apps; it is an app of its own. Its icons for entities and spells are from game-icons.net (CC BY 3.0). It is left out of the Google Play build.
