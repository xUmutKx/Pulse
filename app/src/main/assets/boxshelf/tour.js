// (beta.11, Windows and Linux) The shelf tour: a one-minute look at the basics the first time the shelf opens after setup, and again
// from Settings → Shelf → Show the tour. The real screens are opened in turn (Details, Inspect, the quick menu, the Art
// Studio, Settings) with everything dimmed except the part being explained, and one card in Settings' style beside it.
// A (Enter, →) goes on, B (Esc) ends it for good; it never comes back on its own. Not on Android (the Thor has its own).
// Costs nothing while it isn't up: a few page elements made when it starts, no drawing loop. While it's up the shelf and
// Settings leave the controller to it (app.js pollBody, settings.js poll) and the keyboard is caught before them.
(function () {
  'use strict';
  const OS = () => (window.bsPlatform && bsPlatform.os) || 'web';
  const allowed = () => OS() !== 'android';
  const T = () => (window.Shelf && Shelf._tour) || null;
  const wait = ms => new Promise(r => setTimeout(r, ms));
  const until = async (fn, ms) => { for (let n = 0; n < ms / 100 && !fn(); n++) await wait(100); return !!fn(); };
  const $ = id => document.getElementById(id);
  const shown = e => !!e && e.getBoundingClientRect().width > 0 && e.getBoundingClientRect().height > 0;
  const box = e => { const r = e.getBoundingClientRect(); return { x: r.left, y: r.top, w: r.width, h: r.height }; };
  const union = list => { const L = list.filter(shown).map(box); if (!L.length) return null;
    const x = Math.min(...L.map(r => r.x)), y = Math.min(...L.map(r => r.y)), r = Math.max(...L.map(q => q.x + q.w)), b = Math.max(...L.map(q => q.y + q.h));
    return { x, y, w: r - x, h: b - y }; };
  const settingsOn = () => !!(window.Settings && Settings.isOpen && Settings.isOpen());

  // ---------- where each step points ----------
  // the case in the middle of the shelf
  function centreCase() {
    const vw = innerWidth, L = [...document.querySelectorAll('#track > *')].filter(shown);
    L.sort((a, b) => { const p = box(a), q = box(b); return Math.abs(p.x + p.w / 2 - vw / 2) - Math.abs(q.x + q.w / 2 - vw / 2); });
    return L[0] ? box(L[0]) : null;
  }
  // Inspect draws the case in 3D in the middle of the screen: its place as measured there (1600 x 900: 652, 246, 292 x 420)
  const inspectCase = () => ({ x: innerWidth * .4075, y: innerHeight * .273, w: innerWidth * .1825, h: innerHeight * .467 });
  // the quick menu from its top row (Favorite, Details, Box art, Hide) down to Trophies
  function qmRows() {
    const rows = [...document.querySelectorAll('#quickMenu .qm-row')].filter(shown);
    const k = rows.findIndex(r => /^\s*Trophies/.test(r.textContent));
    // (the top row of buttons isn't a .qm-row: found by its Box art button, whose whole row is taken)
    const art = [...document.querySelectorAll('#quickMenu button, #quickMenu [role="button"], #quickMenu .qm-act')].find(e => shown(e) && /^\s*Box art\s*$/.test(e.textContent));
    return union([...(art && art.parentElement ? [art.parentElement] : []), ...(k >= 0 ? rows.slice(0, k + 1) : rows.slice(0, 6))]) || ($('quickMenu') ? box($('quickMenu')) : null);
  }
  // the Art Studio's part tabs and its first row of versions
  const artTop = () => union([document.querySelector('#bss .bsa-parts'), ...[...document.querySelectorAll('#bss .bsa-card')].slice(0, 4)]);
  // Settings' sidebar: Consoles, PC games and Custom games
  const addRows = () => union([...document.querySelectorAll('#bss .bss-nav')].filter(e => /^(Consoles|PC games|Custom games)\b/.test(e.textContent.trim())));

  // ---------- moving between the screens ----------
  async function toShelf() {
    const t = T(); if (!t) return;
    if (settingsOn()) { try { await Settings.close(); } catch {} await until(() => !settingsOn(), 1500); await wait(250); }
    if (t.qmOn()) { t.qmClose(); await wait(250); }
    if (window.BS_FIND && BS_FIND.isOpen()) { BS_FIND.close(); await until(() => t.mode() === 'browse', 1000); await wait(200); } // (beta.12) Find a game
    if (t.mode() === 'inspect') { t.closeInspect(); await until(() => t.mode() === 'browse', 1500); await wait(200); }
    if (t.mode() === 'details') { t.closeDetails(); await until(() => t.mode() === 'browse', 1500); await wait(250); }
  }
  const hasArt = () => { const t = T(), g = t && t.game(); return !!(g && g.kind === 'rom' && g.file && window.boxshelf && window.boxshelf.artStudio); };

  // keys: [{ pad: [glyph keys as the hint bar names them], kbd: [key caps] }, what it does]
  const STEPS = [
    { k: 'Welcome', t: 'Your shelf, in a minute', p: 'A quick look at where things are. Skip it any time; it won’t come back on its own.', enter: toShelf },
    { k: 'Consoles', t: 'Switch consoles', p: 'Each console has its own shelf. The tabs show which one you’re on.', enter: toShelf, target: () => $('tabs') && shown($('tabs')) ? box($('tabs')) : null,
      keys: [[{ pad: ['L2', 'R2'], kbd: ['↑', '↓'] }, 'Next or previous console'], [{ pad: ['L1', 'R1'], kbd: ['Z', 'C'] }, 'Jump by letter']] },
    { k: 'Your games', t: 'Pick a game', p: 'Move along the shelf with the stick, the d-pad or the arrow keys.', enter: toShelf, target: centreCase,
      keys: [[{ pad: ['x'], kbd: ['Enter'] }, 'Play'], [{ pad: ['tri'], kbd: ['F'] }, 'Flip to the back'], [{ pad: ['tri'], kbd: ['H'] }, { pad: 'Hold to favourite', kbd: 'Favourite' }]] },
    // (beta.12) the search (find.js): opened for this step, closed again as the tour moves on or ends (toShelf)
    { k: 'Find a game', t: 'Search every shelf', p: 'Type a few letters of a title and pick the game from the list. The shelf goes straight to it, whichever console it’s on. The magnifying glass beside Menu opens it too.',
      enter: async () => { await toShelf(); if (window.BS_FIND) { BS_FIND.open(); await until(() => BS_FIND.isOpen(), 1500); await wait(250); } },
      target: () => { const e = document.querySelector('#bsFind.on .fg-side'); return e && shown(e) ? box(e) : null; },
      keys: [[{ pad: ['L3'], kbd: ['/'], padText: 'Hold' }, 'Find a game']] },
    { k: 'Details', t: 'Everything about a game', p: 'The trailer plays at the top. Below it: info, screenshots, the manual and achievements. Box art and Rename are under More.',
      enter: async () => { await toShelf(); T().openDetails(); await until(() => T().mode() === 'details', 1500); }, target: () => shown($('details')) ? box($('details')) : null,
      keys: [[{ pad: ['⧉'], kbd: ['I'] }, 'Open Details'], [{ pad: ['tri'] }, 'Trailer full screen'], [{ pad: ['L1', 'R1'], kbd: ['Q', 'E'] }, 'Switch tabs']] },
    // (spin: the case turns a side every 2.2 s while this step is up, to show what Inspect does)
    // (only a console game has a case to hold: on a Steam or custom game the step is left out, as the Art Studio's is)
    { k: 'Inspect', t: 'Hold the case', p: 'Turn it round, zoom in, and open it to see the disc and the manual.', spin: true,
      skip: () => { const t = T(), g = t && t.game(); return !(g && g.kind === 'rom'); },
      enter: async () => { await toShelf(); T().openInspect(); await until(() => T().mode() === 'inspect', 1500); await wait(900); }, target: inspectCase,
      keys: [[{ pad: ['R3'], kbd: ['V'] }, 'Inspect'], [{ pad: ['x'], kbd: ['Enter'] }, 'Open the case']] },
    { k: 'Quick menu', t: 'Art, looks and trophies', p: 'Box art opens this game in the Art Studio. Customize changes the theme and sounds. Trophies connects RetroAchievements.',
      enter: async () => { await toShelf(); T().qmOpen(); await until(() => T().qmOn(), 1500); }, target: qmRows,
      keys: [[{ pad: ['☰'], kbd: null, kbdText: 'Menu, top right' }, 'Quick menu']] },
    { k: 'Art Studio', t: 'Choose the box art', p: 'Box art in the quick menu opens the game here. Pick a part along the top, then any version of it, and the shelf wears it straight away. Lock this game keeps it as it is.',
      skip: () => !hasArt(), cardAt: 'bottomLeft',
      enter: async () => { await toShelf(); T().artStudio(); await until(() => settingsOn() && !!document.querySelector('#bss .bsa-parts'), 4000); await until(() => document.querySelectorAll('#bss .bsa-card').length > 0, 2500); }, target: artTop,
      keys: [[{ pad: ['←', '→'], kbd: ['←', '→'] }, 'Front, back, spine, disc'], [{ pad: ['x'], kbd: ['Enter'] }, 'Use this version'], [{ pad: ['o'], kbd: ['Esc'] }, 'Back to the shelf']] },
    { k: 'Adding games', t: 'Add more games', p: 'Consoles: point Boxshelf at a games folder. PC games and Custom games: add anything else, one at a time or a whole folder.',
      enter: async () => { await toShelf(); if (window.Setup && Setup.open) Setup.open({ page: 'games' }); await until(() => settingsOn() && !!addRows(), 3000); }, target: addRows,
      keys: [[{ pad: ['☰'], kbd: ['S'] }, { pad: 'Menu, then Settings', kbd: 'Settings' }]] },
    // (beta.12, Linux / Steam Deck only) there's no Home menu during a game there: how to get back, and Steam's layout
    { k: 'Leaving a game', t: 'Back to the shelf', p: 'Hold View and Menu together for a second and a half: the game closes and the shelf comes back. (In Steam’s Game Mode, the Steam button’s Exit game closes Boxshelf too.) If the controller acts oddly, set Boxshelf’s controller layout in Steam to Gamepad with mouse trackpad.',
      skip: () => !(window.bsPlatform && bsPlatform.os === 'linux'), enter: toShelf,
      keys: [[{ pad: ['⧉', '☰'], kbd: null, kbdText: 'In the game: its own quit key', padText: 'Hold' }, 'Close the game']] },
    { k: 'Done', t: 'That’s the basics', p: 'The museum has a short tour of its own the first time you go in. Watch this one again from Settings · Shelf · Show the tour.', enter: toShelf, last: true,
      keys: [[{ pad: ['⧉'], kbd: ['M'], padText: 'Hold' }, 'Museum']] },
  ];

  // ---------- the overlay ----------
  let on = false, i = 0, el = null, busy = false, prev = {}, gen = 0;
  function ensure() {
    if (el) return el;
    el = document.createElement('div'); el.id = 'bsTour'; el.setAttribute('aria-hidden', 'true');
    el.innerHTML = '<div class="bt-spot none"></div><div class="bt-card" role="dialog" aria-modal="true" aria-labelledby="btT">' +
      '<div class="bt-k"><span class="bt-kn"></span><span class="bt-n"></span></div><h2 id="btT"></h2><p class="bt-p"></p><div class="bt-keys"></div>' +
      '<div class="bt-foot"><div class="bt-dots"></div><div class="bt-acts"><button type="button" class="bt-btn bt-skip"><span class="bt-g"></span><span class="bt-l"></span></button>' +
      '<button type="button" class="bt-btn bt-next"><span class="bt-g"></span><span class="bt-l"></span></button></div></div></div>';
    document.body.appendChild(el);
    el.querySelector('.bt-skip').addEventListener('click', () => end());
    el.querySelector('.bt-next').addEventListener('click', () => go(1));
    return el;
  }
  const glyph = k => { const t = T(); return t ? t.glyph(k) : '<span class="g">' + k + '</span>'; };
  const cap = k => '<span class="bt-cap">' + k + '</span>';
  function keyRow(spec, what) {
    const pad = !!(T() && T().pad());
    const label = typeof what === 'string' ? what : (pad ? what.pad : what.kbd);
    let caps;
    if (pad) caps = (spec.padText ? '<span class="bt-word">' + spec.padText + '</span>' : '') + (spec.pad || []).map(glyph).join('');
    else if (!spec.kbd && !spec.kbdText) return ''; // (nothing on the keyboard does it)
    else caps = spec.kbd ? spec.kbd.map(cap).join('') : '<span class="bt-word">' + spec.kbdText + '</span>';
    return '<div class="bt-kr"><span class="bt-caps">' + caps + '</span><span>' + label + '</span></div>';
  }
  const list = () => STEPS.filter(s => !(s.skip && s.skip()));
  function paint(s, n, of) {
    const c = el.querySelector('.bt-card'), pad = !!(T() && T().pad());
    el.querySelector('.bt-kn').textContent = s.k; el.querySelector('.bt-n').textContent = (n + 1) + ' of ' + of;
    el.querySelector('#btT').textContent = s.t; el.querySelector('.bt-p').textContent = s.p;
    el.querySelector('.bt-keys').innerHTML = (s.keys || []).map(r => keyRow(r[0], r[1])).join('');
    el.querySelector('.bt-dots').innerHTML = Array.from({ length: of }, (_, k) => '<i' + (k === n ? ' class="on"' : '') + '></i>').join('');
    el.querySelector('.bt-skip .bt-g').innerHTML = pad ? glyph('o') : cap('Esc'); el.querySelector('.bt-skip .bt-l').textContent = s.last ? 'Close' : 'Skip tour';
    el.querySelector('.bt-next .bt-g').innerHTML = pad ? glyph('x') : cap('Enter'); el.querySelector('.bt-next .bt-l').textContent = s.last ? 'Done' : 'Next';
    c.classList.remove('in'); void c.offsetWidth; c.classList.add('in');
  }
  // the spotlight on the step's part, the card beside it (right, left, below or above: wherever it fits), or both centred
  function place(s) {
    const spot = el.querySelector('.bt-spot'), c = el.querySelector('.bt-card'), W = innerWidth, H = innerHeight, m = Math.max(16, W * .015);
    let r = null; try { r = s.target ? s.target() : null; } catch { r = null; }
    const cw = c.offsetWidth, ch = c.offsetHeight;
    if (!r) { spot.classList.add('none'); c.style.left = Math.round((W - cw) / 2) + 'px'; c.style.top = Math.round((H - ch) / 2) + 'px'; return; }
    const pad = Math.max(8, W * .006); r = { x: r.x - pad, y: r.y - pad, w: r.w + pad * 2, h: r.h + pad * 2 };
    spot.classList.remove('none'); spot.style.left = r.x + 'px'; spot.style.top = r.y + 'px'; spot.style.width = r.w + 'px'; spot.style.height = r.h + 'px';
    const gap = Math.max(18, W * .014), clampX = x => Math.max(m, Math.min(W - cw - m, x)), clampY = y => Math.max(m, Math.min(H - ch - m, y));
    const side = [
      () => r.x + r.w + gap + cw <= W - m && { x: r.x + r.w + gap, y: clampY(r.y + r.h / 2 - ch / 2) },
      () => r.x - gap - cw >= m && { x: r.x - gap - cw, y: clampY(r.y + r.h / 2 - ch / 2) }];
    const under = [
      () => r.y + r.h + gap + ch <= H - m && { x: clampX(r.x + r.w / 2 - cw / 2), y: r.y + r.h + gap },
      () => r.y - gap - ch >= m && { x: clampX(r.x + r.w / 2 - cw / 2), y: r.y - gap - ch }];
    // (a wide, low part such as the console tabs: the card below it reads better than beside it)
    const tries = s.cardAt === 'bottomLeft' ? [() => ({ x: m, y: H - ch - m })] : r.w > r.h * 3 ? [...under, ...side] : [...side, ...under];
    let p = null; for (const f of tries) { p = f(); if (p) break; }
    if (!p) p = { x: clampX(r.x + m), y: clampY(r.y + r.h - ch - m) }; // (the part fills the screen: the card sits on its lower left)
    c.style.left = Math.round(p.x) + 'px'; c.style.top = Math.round(p.y) + 'px';
  }
  // the step's part, measured each frame until it has stopped moving (a panel sliding in, Settings fading up): 4 frames the
  // same, or at most maxMs. Only while a step is changing, never while the tour just sits there
  const frame = () => new Promise(r => requestAnimationFrame(() => r()));
  async function settle(s, maxMs) {
    let last = '', same = 0; const t0 = performance.now();
    while (performance.now() - t0 < maxMs) {
      await frame(); let r = null; try { r = s.target ? s.target() : null; } catch {}
      const k = r ? [r.x, r.y, r.w, r.h].map(Math.round).join() : 'none';
      if (k === last) { if (++same >= 4) return; } else { last = k; same = 0; }
    }
  }
  async function show(n) {
    const L = list(); i = Math.max(0, Math.min(L.length - 1, n)); const s = L[i], my = ++gen;
    // the spotlight closes at once (it used to stay on the last part while the next screen opened, then slide across late)
    busy = true; el.classList.add('moving'); el.querySelector('.bt-spot').classList.add('none'); spinStop();
    const run = entering = Promise.resolve().then(() => s.enter()).catch(e => console.error('tour step', s.k, e));
    await run; if (entering === run) entering = null;
    if (my !== gen || !on) return;
    await settle(s, 1500); if (my !== gen || !on) return;
    paint(s, i, L.length); place(s); el.classList.remove('moving'); busy = false;
    if (s.spin) spinStart(my);
    // (moved since, e.g. a late layout: placed again only if it really did, never a second jump for nothing)
    await wait(450); if (my !== gen || !on) return; await settle(s, 600); if (my === gen && on) place(s);
  }
  let spinT = 0, entering = null;
  function spinStart(my) { spinStop(); spinT = setInterval(() => { const t = T(); if (my !== gen || !on || !t || t.mode() !== 'inspect') { spinStop(); return; } t.inspectTurn(); }, 2200); }
  function spinStop() { if (spinT) { clearInterval(spinT); spinT = 0; } }
  function go(d) { if (!on || busy) return; const L = list(); if (d > 0 && i >= L.length - 1) { end(); return; } if (i + d < 0) return; show(i + d); }
  async function end() {
    if (!on) return; on = false; gen++; busy = false; spinStop();
    removeEventListener('keydown', onKey, true); removeEventListener('resize', onResize);
    el.classList.remove('on'); el.setAttribute('aria-hidden', 'true');
    // closed first: Settings saves its own copy of the settings as it closes, which would undo tourDone saved before it
    await toShelf();
    // ended while a step was still opening its screen (Settings takes a moment to come up): once it's up, it goes too
    const late = entering; entering = null;
    if (late) { await late; if (settingsOn()) { try { await Settings.close(); } catch {} await until(() => !settingsOn(), 1500); } }
    const t = T(); if (t) { t.uiSet('tourDone', true); t.poke(); } // (the idle wait for attract mode starts again from now)
    try { localStorage.removeItem('bs-tour-pending'); } catch {}
    setTimeout(() => { if (!on && el) { el.querySelector('.bt-spot').classList.add('none'); } }, 300);
  }
  function onKey(e) {
    if (!on) return; const k = e.key;
    if (k === 'F12' || (e.ctrlKey && e.shiftKey && (k === 'I' || k === 'i'))) return; // (DevTools)
    e.preventDefault(); e.stopImmediatePropagation(); if (e.repeat) return;
    if (k === 'Enter' || k === ' ' || k === 'ArrowRight') go(1); else if (k === 'ArrowLeft' || k === 'Backspace') go(-1); else if (k === 'Escape') end();
  }
  function onResize() { if (on && !busy) place(list()[i]); }
  // the controller (app.js pollBody hands it over while the tour is up, A / B already swapped for Nintendo-style confirm):
  // A or RB goes on, LB goes back, B ends it. The d-pad and stick don't change step (moving around to look at a menu
  // jumped through the tour): they nudge the Next button instead, so it's clear what goes on. Only presses count, never
  // what was held when the tour began
  function pad(gp) {
    const B = n => !!(gp.buttons[n] && gp.buttons[n].pressed), ax = gp.axes || [];
    const now = { a: B(0), b: B(1), rb: B(5), lb: B(4), dir: B(12) || B(13) || B(14) || B(15) || Math.abs(ax[0] || 0) > .6 || Math.abs(ax[1] || 0) > .6 };
    const was = prev, edge = k => now[k] && !was[k]; prev = now; prev.primed = true;
    if (!was.primed) return;
    if (edge('b')) end(); else if (edge('a') || edge('rb')) go(1); else if (edge('lb')) go(-1); else if (edge('dir')) nudge();
  }
  function nudge() { if (!el || busy) return; const n = el.querySelector('.bt-next'); n.classList.remove('nudge'); void n.offsetWidth; n.classList.add('nudge'); }
  function start() {
    const t = T(); if (on || !t || !allowed() || !t.game()) return false;
    ensure(); on = true; prev = {}; el.classList.add('on'); el.setAttribute('aria-hidden', 'false');
    addEventListener('keydown', onKey, true); addEventListener('resize', onResize);
    show(0); return true;
  }

  // ---------- the first time, on its own ----------
  // A new install (setup not done when Boxshelf started) gets the tour once the shelf is up after setup and still for a
  // moment; an install that was set up before this version never gets it on its own (Settings → Shelf → Show the tour).
  // It's owed from the first start, so closing Boxshelf right after setup brings it on the next start. That's kept in
  // the page's own storage (bs-tour-pending), not the settings: setup saves its own copy of the settings as it finishes.
  // Checked every 2 s until then, never once it has shown or isn't wanted.
  let fresh = null, tries = 0;
  const owed = () => { try { return localStorage.getItem('bs-tour-pending') === '1'; } catch { return false; } };
  function watch() {
    if (!allowed() || on) return; const t = T(), c = t && t.cfg();
    if (!c) { if (++tries < 120) setTimeout(watch, 2000); return; }
    if (fresh === null) { fresh = !c.setupDone || owed(); if (!c.setupDone) { try { localStorage.setItem('bs-tour-pending', '1'); } catch {} } }
    if (!fresh || (c.ui && c.ui.tourDone)) return;
    const ready = c.setupDone && t.mode() === 'browse' && !t.qmOn() && !settingsOn() && !t.busy() && !!t.game() && document.hasFocus() && !document.querySelector('#setup.on');
    if (!ready) { if (++tries < 1800) setTimeout(watch, 2000); return; }
    setTimeout(() => { const t2 = T(); if (!on && t2 && t2.mode() === 'browse' && !settingsOn() && !t2.busy()) start(); else setTimeout(watch, 2000); }, 2500);
  }
  setTimeout(watch, 3000);

  window.BS_TOUR = { on: () => on, start, end, pad, _step: () => (on ? list()[i].k : null), _go: d => go(d) };
})();
