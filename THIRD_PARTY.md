
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

## Keyboard neural language model (assets/kb/lm_tr.bin, lm_en.bin)

Pulse's own character-level GRU models (capitals and punctuation included), trained by the Pulse author for this app. Training text: Leipzig Corpora Collection
(Universität Leipzig), corpora `tur_wikipedia_2021_300K`, `tur_news_2020_300K`, `tur-tr_web_2015_300K`, `tur-tr_web_2019_300K`, `eng_wikipedia_2016_300K`,
`eng-simple_wikipedia_2021_300K`, `eng_news_2020_300K`, `eng_newscrawl-public_2018_300K`, `eng-com_web-public_2018_300K`, licensed CC BY 4.0 (https://wortschatz.uni-leipzig.de/en/download). D. Goldhahn, T. Eckart, U. Quasthoff: Building Large Monolingual
Dictionaries at the Leipzig Corpora Collection: From 100 to 200 Languages. LREC 2012. Everyday sentences from Tatoeba (tatoeba.org), CC BY 2.0 FR. Only the trained weights ship; no corpus text is included.

## Pulse AI (localai/, aistt/)

local chat and speech to text: llama.cpp and whisper.cpp (MIT, ggml authors), fetched at build time; llama.cpp's Android example JNI (MIT) is in localai. See localai/NOTICE.md.

## Sonic look of the music player

The Sonic look (extras/music/cassette/SonicPlayer.kt) follows the design of [Sonic](https://github.com/Abhiroop1122/Sonic) by Abhiroop1122 (MIT licence): the deck in glowing lines, the LED windows, the crate of sleeves. The code is written for Pulse in 2D; no file of Sonic is copied (its font and 3D model are not used).

- **Boxshelf** (GPL-3.0, sassiestrhihno/boxshelf-source v1.82.0-beta.10): the shelf screen of Game Hub is Boxshelf's own web screen (the `renderer/` folder and its Android touch extras), run in a WebView from `app/src/main/assets/boxshelf/`. Changes made for Pulse are listed in `assets/boxshelf/NOTICE.txt` (a new `pulse-api.js`, a few lines in `index.html` and `app.js`; the PDF reader, Wii U manual page, image tools, phone companion page and 3D-model optimiser were left out). It carries three.js (MIT) and Fontsource fonts (SIL OFL 1.1).

## Unexpected Keyboard (layout data)

The symbols on the corners of the letter keys (Settings > Keyboard > Corner swipes, `extras/keyboard/KeyboardCorners.kt`) are taken from the QWERTY (US) layout of
[Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard) by Julow (`srcs/layouts/latn_qwerty_us.xml`). Its layout definitions are released under
CC0 1.0 (public domain); the rest of that project is GPL-3.0 and none of its code is used.

## Photo search (assets/photoai)

The gallery search names what is in a picture on the phone itself. It uses [TensorFlow Lite](https://www.tensorflow.org/lite) (Apache 2.0) and the EfficientNet-Lite0 image classifier
(Apache 2.0, from the [MediaPipe models](https://ai.google.dev/edge/mediapipe/solutions/vision/image_classifier), int8, 1000 ImageNet classes), fetched by the app the first time it is needed (it is not inside the APK). The English names of the classes are the ImageNet class list
(ILSVRC 2012); the Turkish words in `labels.tsv` were written for Pulse. Faces are found with Android's own `android.media.FaceDetector`. Nothing is sent anywhere.

## YTXTube (extras/ytx)

The YouTube extra app is [YTXTube](https://github.com/Koustubh12345/YTXTube) 2.2.0 by Koustubh12345, GPL-3.0, changed for Pulse (Pulse's own looks, AMOLED by default,
a light glass stand-in instead of the Kyant backdrop shaders, songs saved to Music with their tags and cover, no self-updater). It uses
[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0), [SponsorBlock](https://sponsor.ajay.app) data (CC BY-NC-SA 4.0),
[OkHttp](https://square.github.io/okhttp/) and [Coil](https://coil-kt.github.io/coil/) (Apache 2.0).


## Free Exercise DB (exercise photos)

The two-photo exercise loops of the gym (Extra store pack "gym-anim") come from the Free Exercise DB, github.com/yuhonas/free-exercise-db, released into the public domain (Unlicense).

- **Unexpected Keyboard icon font** (`app/src/main/res/font/kb_special.ttf`, `special_font.ttf` of https://github.com/Julow/Unexpected-Keyboard): GPL-3.0; its glyphs come from IcoMoon-Free and Font Awesome Free (CC BY 4.0), Material Design Icons (Apache 2.0 / OFL) and Roboto/DejaVu (Apache 2.0 / free). Used for the function symbols on the corners of the keyboard's keys.
- **libadb-android** 3.1.1 (https://github.com/MuntashirAkon/libadb-android): GPL-3.0-or-later OR Apache-2.0. The watch manager's ADB client.

## Code editor (Files and Notes, extras/code)
- Sora Editor (https://github.com/Rosemoe/sora-editor), LGPL-2.1, with its TextMate language module (tm4e, joni, jcodings, snakeyaml-engine).
- The TextMate grammars and the two editor themes in `app/src/main/assets/textmate` are taken from Xed-Editor (https://github.com/Xed-Editor/Xed-Editor, GPL-3.0); the grammars come from VS Code and its extensions (MIT and similar licences, see each folder).
