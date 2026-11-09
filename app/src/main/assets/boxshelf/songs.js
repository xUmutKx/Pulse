/* Songs for the Melody sound. Every move along the shelf plays the next note of the current song, so a song is just
   its tune (one note per step) plus the chord under each bar. The built-in set is original, written for Boxshelf in the
   style of old console soundtracks. MIDI files in the Songs folder are read here too: the busiest melodic part becomes
   the tune and the bass under it becomes the chords. */
window.Songs = (() => {
  // "Chord: notes | Chord: notes …"; chord names like C, Am, F#, Bb, Dm, G7 (only the root and minor/major matter)
  const BUILT_IN = [
    { name: 'Meadow Overture', hue: 120, tune: 'C: C5 E5 G5 E5 F5 G5 | F: A5 G5 F5 E5 D5 C5 | G: D5 E5 F5 G5 A5 B5 | C: C6 G5 E5 C5 | Am: A4 C5 E5 A5 G5 E5 | F: F5 E5 D5 C5 A4 C5 | G: B4 D5 G5 F5 E5 D5 | C: C5 E5 G5 C6' },
    { name: 'Lantern Castle', hue: 280, tune: 'Dm: D5 F5 A5 F5 D5 A4 | Bb: Bb4 D5 F5 E5 D5 C5 | C: C5 E5 G5 F5 E5 C5 | A: C#5 E5 A5 G5 F5 E5 | Dm: D5 A5 F5 D5 | Gm: G4 Bb4 D5 G5 F5 D5 | A: E5 C#5 A4 C#5 E5 G5 | Dm: F5 E5 D5' },
    { name: 'Tide Pool Waltz', hue: 195, tune: 'F: A4 C5 F5 A5 G5 F5 | Dm: D5 F5 A5 G5 F5 D5 | Bb: D5 F5 Bb5 A5 G5 F5 | C: E5 G5 C6 Bb5 A5 G5 | F: F5 A5 C6 A5 F5 C5 | Gm: Bb4 D5 G5 F5 E5 D5 | C: C5 E5 G5 Bb5 A5 G5 | F: F5 C5 A4 F5' },
    { name: 'Turbo Coast', hue: 210, tune: 'Em: E5 G5 B5 G5 E5 B4 E5 G5 | C: C5 E5 G5 C6 B5 G5 | D: D5 F#5 A5 D6 C6 A5 | Em: B5 A5 G5 F#5 E5 | C: E5 G5 C6 E6 D6 C6 | D: A5 F#5 D5 F#5 A5 D6 | B: D#6 B5 F#5 D#5 B4 D#5 | Em: E5 G5 B5 E6' },
    { name: 'Starlight Lullaby', hue: 230, tune: 'G: B4 D5 G5 D5 | Em: B4 E5 G5 E5 | C: C5 E5 G5 A5 G5 E5 | D: F#5 A5 D5 F#5 | G: G5 B5 D6 B5 | C: C6 B5 A5 G5 | D: A5 G5 F#5 E5 D5 | G: G5 D5 B4 G4' },
    { name: 'Snowcap Summit', hue: 185, tune: 'Am: A4 E5 A5 B5 C6 B5 A5 E5 | F: F5 A5 C6 A5 F5 C5 | G: G5 B5 D6 B5 G5 D5 | E: E5 G#5 B5 E6 D6 B5 | Am: C6 A5 E5 C5 A4 C5 | Dm: D5 F5 A5 D6 C6 A5 | E: B5 G#5 E5 G#5 B5 D6 | Am: C6 B5 A5' },
    { name: 'Clockwork Town', hue: 40, tune: 'Bb: Bb4 D5 F5 D5 Bb4 F5 | Eb: G5 Eb5 Bb4 Eb5 G5 Bb5 | F: A5 F5 C5 F5 A5 C6 | Bb: Bb5 F5 D5 Bb4 | Gm: D5 G5 Bb5 G5 D5 Bb4 | Eb: Eb5 G5 Bb5 C6 Bb5 G5 | F: F5 A5 C6 Eb6 D6 C6 | Bb: Bb5 F5 D5 Bb5' },
    { name: 'Ember Depths', hue: 12, tune: 'Cm: C5 Eb5 G5 Eb5 C5 G4 | Ab: Ab4 C5 Eb5 C5 Ab4 Eb5 | Fm: F5 Ab5 C6 Ab5 F5 C5 | G: B4 D5 G5 F5 D5 B4 | Cm: G5 Eb5 C5 Eb5 G5 C6 | Ab: C6 Ab5 Eb5 Ab5 C6 Eb6 | G: D6 B5 G5 F5 D5 B4 | Cm: C5 G4 Eb5 C5' },
  ];
  const PC = { C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 11 };
  const midiOf = n => { const m = /^([A-G])(#|b)?(-?\d)$/.exec(n); if (!m) return null; return 12 * (+m[3] + 1) + PC[m[1]] + (m[2] === '#' ? 1 : m[2] === 'b' ? -1 : 0); };
  const chordOf = c => { const m = /^([A-G])(#|b)?(m(?!aj))?/.exec(c); if (!m) return { root: 0, minor: false }; return { root: (PC[m[1]] + (m[2] === '#' ? 1 : m[2] === 'b' ? -1 : 0) + 12) % 12, minor: !!m[3] }; };
  // → { name, hue, notes: [midi], chords: [{root, minor}] (one per note), bars: [index where each bar starts] }
  function parse(s) {
    const notes = [], chords = [], bars = [];
    for (const bar of s.tune.split('|')) {
      const [c, body] = bar.split(':'); const ch = chordOf(c.trim()); bars.push(notes.length);
      for (const n of body.trim().split(/\s+/)) { const m = midiOf(n); if (m != null) { notes.push(m); chords.push(ch); } }
    }
    return { name: s.name, hue: s.hue, notes, chords, bars, builtIn: true };
  }
  const builtIn = BUILT_IN.map(parse);

  // ---- MIDI ----
  // Standard MIDI file → the top melody line (one note per onset) and the bass under it, as chords.
  function parseMidi(buf, name) {
    const d = new DataView(buf.buffer || buf, buf.byteOffset || 0, buf.byteLength);
    const str = (o, n) => String.fromCharCode(...new Uint8Array(d.buffer, d.byteOffset + o, n));
    if (d.byteLength < 14 || str(0, 4) !== 'MThd') throw new Error('Not a MIDI file.');
    const ntr = d.getUint16(10), div = d.getUint16(12);
    if (div & 0x8000) throw new Error('This MIDI timing format isn\'t supported.');
    let p = 8 + d.getUint32(4); const events = []; // {t (ticks), ch, n, v, tr}
    for (let tr = 0; tr < ntr && p + 8 <= d.byteLength; tr++) {
      if (str(p, 4) !== 'MTrk') break; const len = d.getUint32(p + 4); let q = p + 8; const end = Math.min(d.byteLength, q + len); p = q + len;
      let t = 0, run = 0; const vlq = () => { let v = 0, b; do { b = d.getUint8(q++); v = (v << 7) | (b & 0x7f); } while (b & 0x80 && q < end); return v; };
      while (q < end) {
        t += vlq(); let st = d.getUint8(q);
        if (st < 0x80) st = run; else { q++; if (st < 0xf0) run = st; }
        const hi = st & 0xf0;
        if (st === 0xff) { q++; const l = vlq(); q += l; }
        else if (st === 0xf0 || st === 0xf7) { const l = vlq(); q += l; }
        else if (hi === 0x90 || hi === 0x80) { const n = d.getUint8(q), v = d.getUint8(q + 1); q += 2; if (hi === 0x90 && v > 0) events.push({ t, ch: st & 15, n, v, tr }); }
        else if (hi === 0xc0 || hi === 0xd0) q += 1;
        else if (hi >= 0xa0 && hi <= 0xe0) q += 2;
        else break;
        if (events.length > 60000) break;
      }
    }
    const tones = events.filter(e => e.ch !== 9); // channel 10 is drums
    if (tones.length < 8) throw new Error('No tune found in this MIDI file.');
    // the melody part: the track/channel whose notes sit highest and come most often (a tune, not a pad)
    const parts = new Map(); tones.forEach(e => { const k = e.tr * 16 + e.ch; const a = parts.get(k) || []; a.push(e); parts.set(k, a); });
    let best = null, bestScore = -1;
    for (const [k, a] of parts) { if (a.length < 8) continue; const onsets = new Set(a.map(e => e.t)).size; const mean = a.reduce((s, e) => s + e.n, 0) / a.length; const score = onsets * Math.pow(Math.max(0, mean - 40) / 30, 1.5); if (score > bestScore) { bestScore = score; best = a; } }
    if (!best) throw new Error('No tune found in this MIDI file.');
    // one note per onset: the highest at each moment (chords in the melody part keep their top note)
    const byT = new Map(); best.forEach(e => { const o = byT.get(e.t); if (!o || e.n > o.n) byT.set(e.t, e); });
    const line = [...byT.values()].sort((a, b) => a.t - b.t).slice(0, 600);
    // bass under each note: the lowest other note sounding at or just before it → the chord root
    const inBest = new Set(best), others = tones.filter(e => !inBest.has(e)).sort((a, b) => a.t - b.t);
    let lo = 0, prev = null;
    const chords = line.map(e => { while (lo < others.length && others[lo].t < e.t - div) lo++;
      let low = null; for (let j = lo; j < others.length && others[j].t <= e.t; j++) if (!low || others[j].n < low.n) low = others[j];
      if (low) prev = low; return { root: ((prev ? prev.n : e.n) % 12), minor: false }; });
    // into a comfortable piano range: the middle of the tune near E5
    const mean = line.reduce((s, e) => s + e.n, 0) / line.length, shift = 12 * Math.round((76 - mean) / 12);
    const notes = line.map(e => Math.max(48, Math.min(96, e.n + shift)));
    const bars = []; const barLen = div * 4; let lastBar = -1; line.forEach((e, i) => { const b = Math.floor(e.t / barLen); if (b !== lastBar) { bars.push(i); lastBar = b; } });
    let h = 0; for (const c of name || '') h = (h * 31 + c.charCodeAt(0)) >>> 0;
    return { name: name || 'Song', hue: h % 360, notes, chords, bars, builtIn: false };
  }
  return { builtIn, parseMidi, _parse: parse, _midiOf: midiOf };
})();
