
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
