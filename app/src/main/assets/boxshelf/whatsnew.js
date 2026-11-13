// What's new: each version's notes, shipped with the app (so the card after an update works offline, and on the Thor).
// The shelf shows a version's highlights once, the first time it starts; Settings → System → What's new lists the
// items (a short headline per row; the side card says more). scripts/release-notes.js prints the same notes as Markdown
// for the GitHub release, so the app and the release page always say the same thing.
// { version (Windows / Linux), android (the Android build with the same changes, if any), date, title, highlights[],
//   sections: [{ h, items: [[headline, more]] }] }; newest first.
window.BS_WHATSNEW = [
  {
    // (android: the APK published with beta.12 was built a day later and calls itself 1.82.0-beta.13)
    version: '1.82.0-beta.12', android: '1.82.0-beta.13', date: '2026-11-11', title: 'Find any game, manuals you can read, and a new Thor bottom screen',
    highlights: [
      "Find a game: search every shelf by name, by keyboard, controller or touch",
      "Manuals and guides you can read on any screen: zoom, move around the page, one page at a time",
      "A new Details page built around the case, with the trailer's sound and full-screen screenshots",
      "The shelf always slides with every effect on, and achievements as a wall of badges",
      "Every art source one tap away in the Art Studio; Thor: a new bottom screen with Apps, Consoles and Screen off"
    ],
    platforms: [
      "Windows is the main version: everything under Everywhere and Windows.",
      "Android (AYN Thor, phones and tablets) is still a work in progress: expect rough edges, especially on the Thor's bottom screen.",
      "Linux / Steam Deck is still a work in progress: an early build, not yet tried on many Steam Decks."
    ],
    sections: [
      { h: "Everywhere", items: [
        ["Find a game", "Search every shelf by title: the Search button beside Menu, / or Ctrl+F, hold the left stick's click, or the quick menu's first row. Results show as you type, best match first; pick one and the shelf goes to it, on whichever console it's on."],
        ["Manuals you can read", "Zoom in and move around a page with the controller (L2 / R2, right stick), the keyboard (+ / -, arrows), the mouse (Ctrl + wheel) or your fingers (pinch, double tap, drag). Small screens show one page at a time."],
        ["A new achievements screen", "A game's achievements as a wall of badges, with a card beside the one you're on: its full title and description, points, how many players have it, when you unlocked it (and whether in hardcore), and whether it's Story, Beats the game or Missable. Your unlocks sit on a trophy shelf at the top, newest first; the rest are grouped into Story, Missable and Everything else. Rare badges (violet, under 10% of players) and ultra-rare ones (gold, under 3%) get a rim, and missable ones a dot. Y sorts by order, rarity or points."],
        ["RomM", "Play games from your RomM server."],
        ["Every art source at a tap", "The Art Studio ends each row with a card for each source not set up yet, saying what it brings: LaunchBox art (one download, right there), ScreenScraper (every part), SteamGridDB (fronts in HD) and IGDB. Connect one and its versions fill the row."],
        ["A faster Art Studio", "Versions show at once instead of waiting for the slowest source, and the Spine page is readable."],
        ["ScreenScraper", "Sign in with your own free account under Settings → Accounts; its previews in the Art Studio load fast."],
        ["Smoother moments", "A case set down from Inspect lands smoothly, and the other cases stay 3D while Details is open."],
        ["Settings from the quick menu", "Settings is one press away in the quick menu."],
        ["The right word for every console", "Cartridge, UMD or floppy disk wherever the case holds one, in Details, the museum and the Art Studio."],
        ["Every effect stays on", "The automatic performance level no longer turns the shelf's effects off."],
        ["Mona Sans", "Boxshelf's interface has a new, clearer font."],
        ["Fixed", "A cover you chose stays chosen. \"Set up for me\" Undo never wipes settings you saved since. A RetroArch system folder with core subfolders is read."]
      ] },
      { h: "Windows", items: [
        ["Details, redesigned", "Built around the case: Play first, the trailer beside it with its sound and volume, screenshots that open full screen, and LB / RB to jump between sections."],
        ["Seamless play", "Steam and store games are watched while they run, so Boxshelf is back the moment you quit; pick the screen mode, or Go to desktop from the Home menu."],
        ["RocketLauncher", "Games can start through RocketLauncher, HyperSpin's launcher, with its fade screens, bezels and pause menu."],
        ["Custom games", "A Settings page for recomps, PC ports and any other program, on their own Custom shelf."],
        ["Steam", "Trailers play with their sound; fixes for controllers and for games that close early. A on Details' trailer pauses and plays it there."],
        ["Attract mode", "It keeps going when Boxshelf's window isn't in focus, with more attract settings."],
        ["Animations", "The shelf slides with every effect on whatever Windows' own animation setting says. Reduce motion is now in Settings → Performance (Off by default)."],
        ["Controllers", "Switching a controller on with its Guide button no longer opens the Home menu, and its first presses are never lost. Minus / View opens Details (held: the museum)."],
        ["Fixed", "The quick menu no longer opens or slides away by itself."]
      ] },
      { h: "Android (work in progress)", items: [
        ["A new Thor bottom screen", "Apps first (every app on the device, plus Android home, Settings, Wi-Fi and Bluetooth), then a calmer card for the game you're on, Trophies, Consoles (tap one to jump to its shelf) and Shortcuts (up to twelve, in pages, with Add)."],
        ["Hold an app for its menu", "On the bottom screen's Apps tab (or an app in Shortcuts), hold an app to open it on the top or the bottom screen, add it to Shortcuts or open its App info. An app on the top screen leaves Boxshelf's bottom screen up (DS and 3DS emulators get both screens); an app on the bottom screen leaves the shelf live on top, with a button there to bring Boxshelf's bottom screen back."],
        ["Typing in Settings by touch", "A finger on a setting you type into (an API key, an account) opens Boxshelf's own keyboard, with a Paste key for a key copied in your browser."],
        ["The bottom screen helps with what's on top", "While you read a manual, search or look at Details, it shows big buttons for that. Find a game types on the bottom screen, so the top keeps the results."],
        ["It stays up while you play", "A game that doesn't use the second screen (GameCube, PS2…) leaves Boxshelf's bottom screen up. DS and 3DS games still get the bottom screen for themselves."],
        ["Screen off", "A Shortcut that turns the bottom screen dark until you tap it."],
        ["Touch everywhere", "Double-tap a case to pick it up, swipe up for Details and down to close them, tap the manual or the disc in an open case. A one-time tip shows the gestures."],
        ["Manuals sized for the Thor", "A manual opens at the page's width, its print near real size and sharp. Each one starts fitted again, the way you last chose (the whole page or its width)."],
        ["Smoother scrolling", "Racing through a shelf no longer competes with the bottom screen or the moving backdrop. Settings → System → Smoothness test shows its results on screen."],
        ["Box art made clear", "The Thor's Art Studio has a card for each art source: LaunchBox art downloads right there with its progress; ScreenScraper, SteamGridDB and IGDB connect and come back."],
        ["Readable on the Thor", "A Text size setting (Automatic by default, up to Largest), with bigger labels on the shelf, in Details and on the bottom screen's buttons."],
        ["Phones and tablets", "They turn upright with the screen; the Thor stays landscape."],
        ["Fixed", "Boxshelf no longer freezes after you make it your Home app. DS and other cartridge games show the cartridge's own picture, whole, and call it a cartridge."]
      ] },
      { h: "Linux / Steam Deck (work in progress)", items: [
        ["Details, redesigned", "As on Windows."],
        ["Emulators as Flatpaks", "They are found, EmuDeck and RetroDECK included."],
        ["Steam games are watched", "While they run, so Boxshelf is back when you quit."],
        ["Leaving a game, explained", "A \"Leaving a game\" card after setup: hold View + Menu to come back. Steam's own Exit game closes Boxshelf too, and the tour says so."],
        ["Sized for the Deck", "A Steam Deck starts at Interface size 115% for its 7\" screen (a size you choose later is kept)."],
        ["Power and start-up", "Sleep, Restart and Shut down in the quick menu; Settings → Start Boxshelf at login."],
        ["Game Mode", "A controller-friendly file browser, updates that keep your Steam shortcut working, and Boxshelf's own artwork in your Steam library."],
        ["Touch on the Deck's screen", "Double-tap a case to pick it up, swipe for Details, tap the manual in an open case to read it."]
      ] }
    ]
  },
  {
    version: '1.82.0-beta.9', android: '1.82.0-beta.9', date: '2026-11-06', title: 'Smoother, better manuals, and covers for every custom game',
    highlights: [
      'Smoother: background work no longer makes the shelf hitch, and the Art Studio opens faster',
      'Manuals: the right manual for the right game, scan collections for 26 consoles, cleaner pages',
      'Connect your accounts during setup, with Paste buttons (right-click Paste works too)',
      'Covers for all your custom games at once: recomps, ports and programs you added',
      'Thor: video previews, ports and custom APKs, six more consoles; Steam Deck: hold Select + Start to close a game'
    ],
    platforms: [
      'Windows: everything above but the Thor parts.',
      'Android (AYN Thor): video previews in Details, ports and custom APKs, six more consoles.',
      'Linux / Steam Deck (experimental): hold Select + Start to close a game; covers for every custom game.'
    ],
    sections: [
      { h: 'New', items: [
        ['Covers for every custom game', 'Settings → Custom games → Covers: go through every recomp, port and program without a cover (SteamGridDB\'s covers for each, A uses one, Skip moves on), or let Boxshelf pick the best match for each in one go, with Undo. Needs a free SteamGridDB key.'],
        ['Thor: video previews', 'Videos beside your games (a "videos", "media/videos" or "snap" folder in a console\'s games folder) play behind the case in Details, and YouTube trailers in the Media tab. Off until you switch them on: Settings → Shelf & museum → Video previews.'],
        ['Thor: ports and custom APKs', 'Settings → Games → Ports & custom APKs: switch on the apps that are games (ports, recomps, a custom APK) and they get a Custom shelf of their own, off the Apps shelf.'],
        ['Thor: six more consoles', 'Sega CD, 32X, Atari 2600, Lynx, WonderSwan and DOS, through RetroArch (MD.emu for Sega CD too), with their box art, manuals and achievements.'],
        ['Steam Deck: leave any game', 'Hold Select + Start for a moment during a game and Boxshelf closes it, whatever the emulator.'],
        ['Accounts in setup', 'The first-run setup has an optional Accounts page: RetroAchievements, SteamGridDB, ScreenScraper and fanart.tv, each with a link to its free key, a Paste button and a Test. With a controller, an on-screen keyboard. Skip it and nothing changes.'],
        ['Manuals for many more consoles', 'Scan collections for PlayStation, NES, SNES, N64, Game Boy, DS, 3DS, Sega, Atari, TurboGrafx-16 and Neo Geo games, plus Nintendo\'s high-resolution manuals for Wii, GameCube, N64, DS and 3DS.']
      ] },
      { h: 'Better', items: [
        ['Smoother everywhere', 'Box art, manual and library work in the background takes turns with the shelf instead of freezing it, games folders are read once instead of over and over, and coming back from a game no longer sets off a full rescan.'],
        ['The right manual for the right game', 'Stricter matching: no more sequels\' or other consoles\' manuals, no promotional flyers. Manuals picked before that don\'t match are set aside (kept in the backup folder) and looked up again. Games with no manual anywhere say "no manual found online".'],
        ['Cleaner manual pages', 'No more thumbnail "pages", huge scans made a sensible size, and digital manuals and box wraps are no longer cut in half.'],
        ['Fix a manual', 'In the manual reader, Fix manual (Y / W) offers Wrong game (set it aside and get the right one, from the scan collections first) or Repair (the same manual with its cover, pages and sizes put right). Right away, for that game only.'],
        ['Art Studio shows the whole box', 'The case in the Art Studio takes the shape of the game\'s box, so 3DS, PSP, Switch, Blu-ray and cartridge boxes show whole instead of cropped.'],
        ['Wii U manuals open on the box', 'Manuals made from a Wii U game\'s own e-manual open on the box front again, wherever Boxshelf runs from.'],
        ['Right-click Paste', 'Text boxes have the usual Cut / Copy / Paste menu.'],
        ['Settings opens the right page', 'Links to a page of its own (from a card or the bottom screen) now land on that page, on every platform.']
      ] }
    ]
  },
  {
    version: '1.82.0-beta.8', android: '1.82.0-beta.8', date: '2026-11-06', title: 'Video previews, a new Details page and easier setup',
    highlights: [
      'Video previews: rest on a game and its trailer plays, on the shelf and in Details, console games too',
      'A new Details page: the game\'s logo, play stats, tabs and one big Play button',
      'Add many games at once: pick several, scan a folder, or import from Playnite (experimental)',
      'Ten more consoles, including Saturn, Neo Geo, Arcade and DOS',
      'Set up for me: one switch sets up RetroArch, Dolphin, DuckStation, PCSX2 and PPSSPP'
    ],
    // (scripts/release-notes.js prints these under the notes: where each part works)
    platforms: [
      'Windows: everything above.',
      'Linux / Steam Deck (experimental): everything except Set up for me, console trailers, scanning a folder of games and the Playnite import. On a Steam Deck, add Boxshelf to Steam (it offers to) and play from Game Mode.',
      'Android (AYN Thor): the new Details page and the magazines fix; video previews, Add games and the new consoles come to Android later.'
    ],
    sections: [
      { h: 'New', items: [
        ['Video previews', 'Rest on a game and its preview plays, softly behind the shelf and behind the case in Details: your own videos (ES-DE, EmuDeck, RetroBat, LaunchBox or a folder of your own), Steam\'s own trailers for Steam games, and console trailers from YouTube. Settings → Video previews, which also sets how soon they start (half a second to 15 seconds).'],
        ['Console trailers', 'A console game\'s YouTube trailer is kept as a short clip (about 1 MB) the first time you rest on it, so it plays on the shelf just like a Steam trailer. It needs a free helper (yt-dlp), fetched once with one press: Boxshelf asks you on the shelf, or Settings → Video previews.'],
        ['A new Details page', 'The game\'s logo, time played and last played, one Play button with Manual, Inspect and More beside it, and tabs: Overview, Media and Your copy (LB / RB, or Q / E). The game\'s art or its preview fills the background.'],
        ['Add many games at once', 'Settings → Custom games → Add games, or the Add games tile at the end of the Custom shelf: pick several programs at once, or point Boxshelf at a folder of games and tick the ones to add.'],
        ['Import from Playnite (experimental)', 'Bring your Playnite library over in one go: games you added by hand, with their covers, and your emulated games\' folders. You see what will be added first, and one press removes it all again. Tell us how it goes.'],
        ['Steam Deck: Game Mode and Add to Steam', 'Boxshelf now starts from Steam and in Game Mode by itself. On a Steam Deck in Desktop Mode it offers to add itself to Steam, so your controller works as a gamepad in games (outside Steam, B is the Esc key, which quits most emulators). Any time: Settings → System → Add Boxshelf to Steam.'],
        ['Switch: Citron and Ryujinx', 'Found and started as well as Eden, so EmuDeck\'s Switch emulator works without setting anything up.'],
        ['Ten more consoles', 'Saturn, TurboGrafx-16, Neo Geo, Arcade, Sega CD, 32X, Atari 2600, Lynx, WonderSwan and DOS, through RetroArch, each with its own case and colours. Arcade games show their real names.'],
        ['Set up for me', 'Settings → a console → Set up for me: your controller mapped, the Home menu\'s Save and Load keys, one shared BIOS folder and graphics that suit your PC. Your files are backed up first, and Undo puts them back. The first run can do it for you.'],
        ['RetroArch cores and BIOS files', 'A missing RetroArch core downloads by itself the first time you play, and Settings checks the BIOS files each console needs.'],
        ['More PC stores', 'GOG, Ubisoft Connect, EA app and Xbox / Game Pass games join Steam and Epic on the PC shelf.'],
        ['Library tools', 'Filter by decade, genre, players or co-op, and make collections: shelves of your own, from Details → More → Collections.'],
        ['Patch notes in the app', 'A card on the shelf when a new version is out, with its highlights; What\'s new after you update; and every version\'s notes in Settings → System → What\'s new.']
      ] },
      { h: 'Better', items: [
        ['A tidier first run', 'Only the consoles you have games for come ticked; the rest fold away, grouped by maker.'],
        ['Easier to find', 'Video previews has its own place in Settings, and Add games its own page, so the main pages stay short.']
      ] },
      { h: 'Fixed', items: [
        ['Trailers play again', 'YouTube stopped playing trailers opened on their own. They now play in Boxshelf\'s own player.'],
        ['Android: magazines and guides always show', 'The reading corner\'s list could stay empty on some devices; it now always loads.'],
        ['Your changes stay put', 'A game you renamed, or a finished "Set up for me", could be undone by changing another setting in the same visit.'],
        ['No accidental holds', 'Holding A on a disc, or holding Restart / Shut down, now stops if you switch away or pull down the notification shade, instead of finishing on its own.'],
        ['Covers and the museum', 'A Wi-Fi sign-in page could be saved as a game\'s cover, and the museum\'s camera jumped when you touched the controls after leaving it idle.'],
        ['Android: taps after a quick swipe', 'After flicking the shelf or the console bar, the first tap on a game or console did nothing. It works first time now.']
      ] }
    ]
  },  {
    version: '1.82.0-beta.6', android: '0.4.0-beta.1', date: '2026-11-05', title: 'Now on Android and Linux, and clean game transitions',
    highlights: [
      'Boxshelf on Android (AYN Thor) and Linux / Steam Deck',
      'Starting and quitting a game shows nothing but the game',
      'Twelve retro consoles, from PlayStation to Dreamcast'
    ],
    sections: [
      { h: 'New', items: [
        ['Android and Linux', 'The AYN Thor gets Boxshelf with its bottom screen; Linux and the Steam Deck get an AppImage (experimental).'],
        ['Retro consoles', 'PlayStation, NES, SNES, Nintendo 64, Game Boy, Game Boy Color, Game Boy Advance, DS, Mega Drive, Master System, Game Gear and Dreamcast.'],
        ['Rename and change covers', 'Give any game the name you want, and pick its cover from every one found.']
      ] },
      { h: 'Better', items: [
        ['Clean game transitions', 'No taskbar, no desktop and no gaps while a game starts or closes: it feels like a console.']
      ] }
    ]
  }
];
