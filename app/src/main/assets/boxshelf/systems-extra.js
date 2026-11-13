// Extra shelves (retro and handheld systems people run on Android handhelds and through RetroArch).
// app.js merges these into its STYLE / WORDMARKS / MEDIA tables, so a new system only needs an entry here.
// Case looks: ratio = width / height of the real box, depth = thickness / width.
(function () {
  const S = (id, o) => Object.assign({ id, rc: 0.03, re: 0.2, edge: 0.025, depth: 0.1, spineInk: '#ffffff' }, o);
  const cardboard = { rc: 0.01, re: 0.08, edge: 0.012 };   // printed card boxes: sharp corners
  const jewel = { rc: 0.012, re: 0.06, edge: 0.02, depth: 0.073 }; // CD jewel cases
  const clam = { rc: 0.04, re: 0.2, edge: 0.03, depth: 0.12 };     // Sega plastic clamshells
  window.BS_SYSTEMS_EXTRA = {
    style: {
      nes: S('nes', { ...cardboard, hue: 0, ratio: 0.71, depth: 0.2, caseColor: '#16161a', caseSide: '#0c0c0f', band: { bg: '#111114', fg: '#e8e8ec', label: 'NES' }, spineBand: { bg: '#111114', fg: '#e8e8ec', label: 'NES' }, tint: ['#6b1d1d', '#140606'], glow: '#ff8a8a' }),
      snes: S('snes', { ...cardboard, hue: 270, ratio: 1.38, h: 0.7, depth: 0.2, caseColor: '#d9d9de', caseSide: '#b9b9c1', spineInk: '#3c3c46', band: { bg: '#4b3f8f', fg: '#ffffff', label: 'SUPER NINTENDO' }, spineBand: { bg: '#4b3f8f', fg: '#ffffff', label: 'SNES' }, tint: ['#4b3f8f', '#120f26'], glow: '#b8a8ff' }),
      n64: S('n64', { ...cardboard, hue: 210, ratio: 1.38, h: 0.7, depth: 0.2, caseColor: '#141418', caseSide: '#0a0a0d', band: { bg: '#0d0d10', fg: '#ffffff', label: 'NINTENDO 64' }, spineBand: { bg: '#0d0d10', fg: '#ffffff', label: 'N64' }, tint: ['#1d4fa3', '#07142c'], glow: '#7fb0ff' }),
      gb: S('gb', { ...cardboard, hue: 75, ratio: 0.83, depth: 0.22, caseColor: '#cfd0c4', caseSide: '#aeb0a2', spineInk: '#2f3326', band: { bg: '#8b8c7e', fg: '#ffffff', label: 'GAME BOY' }, spineBand: { bg: '#8b8c7e', fg: '#ffffff', label: 'GB' }, tint: ['#4f6b2a', '#121a08'], glow: '#c8e889' }),
      gbc: S('gbc', { ...cardboard, hue: 290, ratio: 0.83, depth: 0.22, caseColor: '#e9e4f2', caseSide: '#c9c2d8', spineInk: '#3b2f52', band: { bg: '#6d3fa8', fg: '#ffffff', label: 'GAME BOY COLOR' }, spineBand: { bg: '#6d3fa8', fg: '#ffffff', label: 'GBC' }, tint: ['#6d3fa8', '#180c2a'], glow: '#d6b3ff' }),
      gba: S('gba', { ...cardboard, hue: 245, ratio: 0.83, depth: 0.22, caseColor: '#ecedf3', caseSide: '#cdcfd9', spineInk: '#2b2f6b', band: { bg: '#2b2f8f', fg: '#ffffff', label: 'GAME BOY ADVANCE' }, spineBand: { bg: '#2b2f8f', fg: '#ffffff', label: 'GBA' }, tint: ['#2b2f8f', '#0a0b26'], glow: '#a8adff' }),
      nds: S('nds', { rc: 0.05, re: 0.24, hue: 0, ratio: 0.9, depth: 0.1, edge: 0.03, caseColor: '#f4f4f6', caseSide: '#dcdde2', spineInk: '#3a3d45', band: { bg: '#ffffff', fg: '#3a3d45', label: 'NINTENDO DS' }, spineBand: { bg: '#ffffff', fg: '#3a3d45', label: 'DS' }, tint: ['#5a5f6e', '#15161b'], glow: '#d9dde8' }),
      psx: S('psx', { ...jewel, hue: 220, ratio: 1.136, h: 0.7, caseColor: '#121216', caseSide: '#08080a', band: null, spineBand: { bg: '#0a0a0c', fg: '#ffffff', label: 'PlayStation' }, tint: ['#3b3f4f', '#0c0d12'], glow: '#c7cde0' }),
      dreamcast: S('dreamcast', { ...jewel, hue: 22, ratio: 1.136, h: 0.7, caseColor: '#f4f4f4', caseSide: '#d6d6d6', spineInk: '#444', band: null, spineBand: { bg: '#ffffff', fg: '#f37021', label: 'DREAMCAST' }, tint: ['#f37021', '#2b1203'], glow: '#ffb07a' }),
      saturn: S('saturn', { ...jewel, hue: 230, ratio: 0.712, depth: 0.09, caseColor: '#141418', caseSide: '#0b0b0e', band: { bg: '#0b0b0e', fg: '#c9ccd8', label: 'SEGA SATURN' }, spineBand: { bg: '#0b0b0e', fg: '#c9ccd8', label: 'SATURN' }, tint: ['#2a2f6b', '#0a0b1c'], glow: '#9ea6ff' }),
      megadrive: S('megadrive', { ...clam, hue: 0, ratio: 0.72, caseColor: '#16161a', caseSide: '#0c0c0f', band: { bg: '#c8102e', fg: '#ffffff', label: 'SEGA GENESIS' }, spineBand: { bg: '#111114', fg: '#ffffff', label: 'GENESIS' }, tint: ['#7a0b1c', '#1a0306'], glow: '#ff7d8f' }),
      mastersystem: S('mastersystem', { ...clam, hue: 0, ratio: 0.72, caseColor: '#f2f2f2', caseSide: '#d4d4d4', spineInk: '#222', band: { bg: '#111114', fg: '#ffffff', label: 'MASTER SYSTEM' }, spineBand: { bg: '#e2001a', fg: '#ffffff', label: 'SMS' }, tint: ['#6a1018', '#160406'], glow: '#ff9aa3' }),
      gamegear: S('gamegear', { ...cardboard, hue: 210, ratio: 0.83, depth: 0.22, caseColor: '#1a1b20', caseSide: '#0f1013', band: { bg: '#0b62b8', fg: '#ffffff', label: 'GAME GEAR' }, spineBand: { bg: '#0b62b8', fg: '#ffffff', label: 'GG' }, tint: ['#0b4c8a', '#04121f'], glow: '#7fc0ff' }),
      pcengine: S('pcengine', { ...jewel, hue: 30, ratio: 1.0, h: 0.72, caseColor: '#18181c', caseSide: '#0d0d10', band: { bg: '#ff8a00', fg: '#141414', label: 'TURBOGRAFX-16' }, spineBand: { bg: '#141414', fg: '#ff8a00', label: 'TG-16' }, tint: ['#7a4100', '#1c0f00'], glow: '#ffc27a' }),
      neogeo: S('neogeo', { ...cardboard, hue: 45, ratio: 0.71, depth: 0.18, caseColor: '#121214', caseSide: '#09090b', band: { bg: '#0b0b0d', fg: '#e8c14a', label: 'NEO GEO' }, spineBand: { bg: '#0b0b0d', fg: '#e8c14a', label: 'NEO GEO' }, tint: ['#5a4810', '#161104'], glow: '#ffe08a' }),
      arcade: S('arcade', { ...cardboard, hue: 300, ratio: 0.71, depth: 0.16, caseColor: '#16131d', caseSide: '#0c0a10', band: { bg: '#1a1424', fg: '#ff5ad1', label: 'ARCADE' }, spineBand: { bg: '#1a1424', fg: '#ff5ad1', label: 'ARCADE' }, tint: ['#5c1a5a', '#150616'], glow: '#ff9ae6' }),
      // (beta.7) wave 2: Sega CD (its tall black clamshell), 32X, Atari 2600 and Lynx, WonderSwan (card boxes), DOS (the big box)
      segacd: S('segacd', { ...clam, hue: 210, ratio: 0.72, caseColor: '#141418', caseSide: '#0a0a0d', band: { bg: '#0a0a0d', fg: '#ffffff', label: 'SEGA CD' }, spineBand: { bg: '#0a0a0d', fg: '#4aa3ff', label: 'SEGA CD' }, tint: ['#123a6b', '#050e1c'], glow: '#8cc4ff' }),
      sega32x: S('sega32x', { ...clam, hue: 50, ratio: 0.72, caseColor: '#16161a', caseSide: '#0c0c0f', band: { bg: '#f2c200', fg: '#111114', label: 'SEGA 32X' }, spineBand: { bg: '#111114', fg: '#f2c200', label: '32X' }, tint: ['#6b5400', '#191300'], glow: '#ffe27a' }),
      atari2600: S('atari2600', { ...cardboard, hue: 15, ratio: 0.72, depth: 0.2, caseColor: '#1b1b1d', caseSide: '#0e0e10', band: { bg: '#c8331a', fg: '#ffffff', label: 'ATARI 2600' }, spineBand: { bg: '#c8331a', fg: '#ffffff', label: 'ATARI' }, tint: ['#6b1a0c', '#180604'], glow: '#ff9a7a' }),
      atarilynx: S('atarilynx', { ...cardboard, hue: 28, ratio: 0.83, depth: 0.2, caseColor: '#16161a', caseSide: '#0c0c0f', band: { bg: '#f07c1b', fg: '#111114', label: 'ATARI LYNX' }, spineBand: { bg: '#111114', fg: '#f07c1b', label: 'LYNX' }, tint: ['#6b360a', '#180c02'], glow: '#ffc08a' }),
      wonderswan: S('wonderswan', { ...cardboard, hue: 190, ratio: 0.83, depth: 0.2, caseColor: '#eef1f4', caseSide: '#cfd5db', spineInk: '#1f3d4f', band: { bg: '#1f7fa8', fg: '#ffffff', label: 'WONDERSWAN' }, spineBand: { bg: '#1f7fa8', fg: '#ffffff', label: 'WS' }, tint: ['#14506b', '#04121a'], glow: '#8ad8ff' }),
      dos: S('dos', { ...cardboard, hue: 120, ratio: 0.78, depth: 0.26, caseColor: '#141614', caseSide: '#0a0b0a', band: { bg: '#0a0b0a', fg: '#7dff8a', label: 'MS-DOS' }, spineBand: { bg: '#0a0b0a', fg: '#7dff8a', label: 'DOS' }, tint: ['#1f5a24', '#071408'], glow: '#9dffa8' })
    },
    wordmarks: {
      nes: ['NES', 'Home console'], snes: ['SNES', 'Home console'], n64: ['N64', 'Home console'],
      gb: ['Game Boy', 'Handheld'], gbc: ['Game Boy Color', 'Handheld'], gba: ['Game Boy Advance', 'Handheld'], nds: ['DS', 'Handheld'],
      psx: ['PlayStation', 'Home console'], dreamcast: ['Dreamcast', 'Home console'], saturn: ['Saturn', 'Home console'],
      megadrive: ['Genesis', 'Home console'], mastersystem: ['Master System', 'Home console'], gamegear: ['Game Gear', 'Handheld'],
      pcengine: ['TurboGrafx-16', 'Home console'], neogeo: ['Neo Geo', 'Arcade'], arcade: ['Arcade', 'Coin-op'],
      segacd: ['Sega CD', 'Home console'], sega32x: ['32X', 'Home console'], atari2600: ['Atari 2600', 'Home console'], atarilynx: ['Lynx', 'Handheld'],
      wonderswan: ['WonderSwan', 'Handheld'], dos: ['DOS', 'PC classics']
    },
    media: {
      nes: { kind: 'cart', size: 0.5, drive: 'slot' }, snes: { kind: 'cart', size: 0.5, drive: 'slot' }, n64: { kind: 'cart', size: 0.5, drive: 'slot' },
      gb: { kind: 'cart', size: 0.45, drive: 'slot' }, gbc: { kind: 'cart', size: 0.45, drive: 'slot' }, gba: { kind: 'cart', size: 0.42, drive: 'slot' },
      nds: { kind: 'cart', size: 0.42, drive: 'slot' }, megadrive: { kind: 'cart', size: 0.5, drive: 'slot' }, mastersystem: { kind: 'cart', size: 0.5, drive: 'slot' },
      gamegear: { kind: 'cart', size: 0.45, drive: 'slot' }, pcengine: { kind: 'cart', size: 0.4, drive: 'slot' }, neogeo: { kind: 'cart', size: 0.55, drive: 'slot' },
      arcade: { kind: 'cart', size: 0.5, drive: 'slot' },
      psx: { kind: 'disc', size: 0.7, drive: 'top' }, dreamcast: { kind: 'disc', size: 0.7, drive: 'top' }, saturn: { kind: 'disc', size: 0.7, drive: 'top' },
      segacd: { kind: 'disc', size: 0.7, drive: 'top' }, sega32x: { kind: 'cart', size: 0.5, drive: 'slot' }, atari2600: { kind: 'cart', size: 0.5, drive: 'slot' },
      atarilynx: { kind: 'cart', size: 0.42, drive: 'slot' }, wonderswan: { kind: 'cart', size: 0.42, drive: 'slot' }, dos: { kind: 'cart', form: 'floppy', size: 0.5, drive: 'slot' } /* (beta.11) a 3.5" floppy, not a CD: handled like a cartridge (no spin), drawn as a floppy */
    }
  };
  Object.assign(window.BS_SYSTEMS_EXTRA.wordmarks, window.BS_WORDMARK_OVERRIDES || {}); // a platform can rename a shelf (Android: Media → Apps)
})();
