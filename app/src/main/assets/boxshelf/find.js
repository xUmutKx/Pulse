// (beta.12, every platform) Find a game: search every shelf by title. Opens from the shelf with / or Ctrl+F, the Search
// button beside Menu, the left stick's click held for a moment, or the quick menu's first row. A full screen in Settings'
// look: the title field, and under (or beside) it the matches as you type, best first: titles that start with what you
// typed, then a word that starts with it, then anywhere in the title (capitals, accents and punctuation don't matter).
// With nothing typed yet it lists what you played last. Choosing one closes this and takes the shelf to that game on
// its own console's shelf; Esc / B closes without moving.
//   keyboard: type · up / down choose · Enter goes to the game · Esc closes
//   controller: a letter grid (d-pad / stick, A types, X deletes, Y space), LB / RB or left / right between the letters
//     and the results, A on a result goes to it, B closes
//   touch / mouse: the field is a real one (Android's own keyboard opens), a tap or click on a result goes to it
// While it's up the shelf is in its own mode ("find"): app.js pollBody hands the controller here and nothing else reads
// it, and the keys are caught before the shelf's own handlers (this file loads right after perf.js). It costs nothing
// while closed: the page elements are made once in a quiet moment, there's no drawing loop, and the list of games is
// only read when it opens (never while the shelf is still sliding).
'use strict';
(() => {
  const B = () => (window.Shelf && Shelf._find) || null;
  const ANDROID = () => !!(window.bsPlatform && bsPlatform.os === 'android');
  const LINUX = () => !!((window.bsPlatform && bsPlatform.os === 'linux') || (window.boxshelf && boxshelf.platform && boxshelf.platform.os === 'linux'));
  const S = { open: false, pad: false, zone: 'keys', kr: 0, kc: 0, sel: 0, q: '', list: null, res: [], total: 0, rows: new Map(), prev: null, rep: {}, eat: '', ptrAt: -1e9, gen: 0,
    folT: 0, here: null, from: null, moved: false, eatClick: -1e9 };
  const E = {};
  let el = null;
  const $ = (t, c, p) => { const e = document.createElement(t); if (c) e.className = c; if (p) p.append(e); return e; };
  const MAX = 40, RECENT = 12;
  const I_FIND = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="11" cy="11" r="6.5"/><path d="M16 16l4.5 4.5"/></svg>';
  const I_X = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg>';
  const I_BACK = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 6h10a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H9l-6-6z"/><path d="M12 10l4 4M16 10l-4 4"/></svg>';

  // ---------- the letter grid (a controller's keyboard): 8 across, the last row ends in Space (two wide), Delete, Clear ----------
  const GRID = ['ABCDEFGH', 'IJKLMNOP', 'QRSTUVWX', 'YZ012345'].map(r => [...r]);
  const LAST = ['6', '7', '8', '9', 'space', 'space', 'back', 'clear'];
  const keyAt = (r, c) => (r < GRID.length ? GRID[r][c] : LAST[c]);
  const ROWS = GRID.length + 1, COLS = 8;

  function build() {
    if (el) return el;
    el = $('div', 'fg'); el.id = 'bsFind'; el.setAttribute('role', 'dialog'); el.setAttribute('aria-label', 'Find a game'); el.setAttribute('aria-hidden', 'true');
    const box = $('div', 'fg-box', el), head = $('div', 'fg-head', box);
    $('div', 'fg-title', head).textContent = 'Find a game';
    E.count = $('div', 'fg-count', head);
    E.x = $('button', 'fg-x', head); E.x.type = 'button'; E.x.setAttribute('aria-label', 'Close'); E.x.title = 'Close (Esc)'; E.x.innerHTML = I_X;
    const main = $('div', 'fg-main', box), side = $('div', 'fg-side', main);
    E.field = $('div', 'fg-field', side); E.field.innerHTML = I_FIND;
    E.in = $('input', 'fg-in', E.field); E.in.type = 'text'; E.in.placeholder = 'Type a title'; E.in.maxLength = 60; E.in.autocomplete = 'off'; E.in.spellcheck = false;
    E.in.setAttribute('autocapitalize', 'off'); E.in.setAttribute('autocorrect', 'off'); E.in.setAttribute('enterkeyhint', 'search'); E.in.setAttribute('aria-label', 'Game title');
    E.clr = $('button', 'fg-clr', E.field); E.clr.type = 'button'; E.clr.setAttribute('aria-label', 'Clear'); E.clr.innerHTML = I_X; E.clr.hidden = true;
    E.keys = $('div', 'fg-keys', side); E.keys.setAttribute('aria-hidden', 'true'); E.key = {};
    const key = (k, r, c) => { const b = $('button', 'fg-key', E.keys); b.type = 'button'; b.tabIndex = -1; b.dataset.k = k; b.dataset.r = r; b.dataset.c = c; E.key[k] = b; return b; };
    GRID.forEach((row, r) => row.forEach((k, c) => { key(k, r, c).textContent = k; }));
    LAST.forEach((k, c) => { if (E.key[k]) return; const b = key(k, GRID.length, c);
      if (k === 'space') { b.classList.add('wide'); b.textContent = 'Space'; } else if (k === 'back') { b.innerHTML = I_BACK; b.title = 'Delete'; } else if (k === 'clear') { b.classList.add('word'); b.textContent = 'Clear'; } else b.textContent = k; });
    const res = $('div', 'fg-res', main);
    E.cap = $('div', 'fg-cap', res); E.list = $('div', 'fg-list', res); E.empty = $('div', 'fg-empty', res);
    E.hints = $('div', 'fg-hints', box);

    el.addEventListener('click', e => {
      const t = e.target; if (!t || !t.closest) return;
      const row = t.closest('.fg-row'); if (row) { choose(+row.dataset.i); return; }
      const k = t.closest('.fg-key'); if (k) { S.zone = 'keys'; S.kr = +k.dataset.r; S.kc = +k.dataset.c; type(k.dataset.k); paintKeys(); paintSel(); hints(); return; }
      if (t.closest('.fg-x')) { close(); return; }
      if (t.closest('.fg-clr')) { setQ(''); if (!S.pad) focusIn(); return; }
      if (t.closest('.fg-field')) focusIn();
    });
    // (a tap on a letter mustn't take the field's focus: Android's keyboard would fold away and back)
    E.keys.addEventListener('pointerdown', e => e.preventDefault());
    E.in.addEventListener('input', () => { S.q = E.in.value; update(); });
    // the field in use (a tap, a click): the keyboard's layout, the letter grid steps aside
    E.in.addEventListener('focus', () => { if (S.open && S.pad) setPad(false); });
    document.body.appendChild(el); return el;
  }

  // ---------- matching ----------
  // lower case, accents off, anything that isn't a letter or a digit is a space ("Pokémon: Red" = "pokemon red")
  const norm = s => String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/&/g, ' and ').replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
  function search(q) {
    const L = S.list || [], n = norm(q);
    if (!n) { const r = L.filter(g => g.last > 0).sort((a, b) => b.last - a.last).slice(0, RECENT); S.total = r.length; return r; }
    const c = n.replace(/ /g, ''), toks = n.split(' '), out = [];
    for (const g of L) {
      const t = g.n; let r = -1;
      if (t === n) r = 0; else if (t.startsWith(n)) r = 1; else if ((' ' + t).includes(' ' + n)) r = 2; else if (t.includes(n)) r = 3;
      else if (g.c.startsWith(c)) r = 1; else if (g.c.includes(c)) r = 3; // ("spiderman" finds "Spider-Man")
      else if (toks.length > 1 && toks.every(k => (' ' + t).includes(' ' + k))) r = 4; // (every word typed starts a word, in any order)
      else if (toks.length > 1 && toks.every(k => t.includes(k))) r = 5;
      if (r >= 0) out.push({ g, r });
    }
    out.sort((a, b) => a.r - b.r || (a.g.n < b.g.n ? -1 : a.g.n > b.g.n ? 1 : 0) || (a.g.sys < b.g.sys ? -1 : 1));
    S.total = out.length; return out.slice(0, MAX).map(x => x.g);
  }
  // every game, read once per open; never while the shelf is still sliding (it waits for it to rest, a second at most)
  function load(gen) {
    const run = n => {
      if (!S.open || S.gen !== gen) return;
      if (window.BS_BUSY && BS_BUSY() && n < 20) { setTimeout(() => run(n + 1), 50); return; }
      let L = []; try { L = B().games() || []; } catch {}
      for (const g of L) { g.n = norm(g.title); g.c = g.n.replace(/ /g, ''); }
      S.list = L; E.count.textContent = L.length ? L.length + (L.length === 1 ? ' game' : ' games') + ' on your shelves' : '';
      update();
    };
    run(0);
  }

  // ---------- the results ----------
  function row(g) {
    const b = $('button', 'fg-row'); b.type = 'button'; b.tabIndex = -1;
    const th = $('span', 'fg-th', b); th.style.setProperty('--h', String(Number.isFinite(+g.hue) ? +g.hue : 220)); th.textContent = (g.title.trim()[0] || '?').toUpperCase();
    if (g.art) { let u = ''; try { u = B().thumb(g.art) || g.art; } catch { u = g.art; }
      // (decoded off the main thread, and only once the row is near the part of the list in view)
      const im = new Image(); im.decoding = 'async'; im.loading = 'lazy'; im.alt = ''; im.draggable = false;
      im.onload = () => { th.classList.add('has'); }; im.onerror = () => { im.remove(); }; im.src = u; th.append(im); }
    $('span', 'fg-t', b).textContent = g.title; $('span', 'fg-s', b).textContent = g.sys;
    return b;
  }
  function render() {
    const out = [];
    S.res.forEach((g, i) => { let r = S.rows.get(g.id); if (!r) { r = row(g); S.rows.set(g.id, r); } r.dataset.i = i; out.push(r); });
    E.list.replaceChildren(...out); E.list.scrollTop = 0; E.list.scrollLeft = 0;
    const typed = !!norm(S.q), n = S.res.length;
    E.cap.textContent = !S.list ? '' : !typed ? (n ? 'Recently played' : '') : !n ? '' : S.total > n ? `The first ${n} of ${S.total} matches` : n === 1 ? '1 game' : n + ' games';
    E.empty.textContent = !S.list ? '' : n ? '' : typed ? `Nothing called “${S.q.trim()}” on your shelves.` : S.list.length ? 'Type a few letters of a title. Every shelf is searched.' : 'There are no games on your shelves yet.';
    E.empty.hidden = !!n || !S.list; E.clr.hidden = !S.q;
    paintSel();
  }
  function update() { S.res = S.list ? search(S.q) : []; S.sel = 0; if (S.zone === 'list' && !S.res.length) S.zone = 'keys'; render(); paintKeys(); hints(); ctx(); if (norm(S.q)) followSoon(); }
  // (beta.12, Android) the Thor's bottom screen shows its own keyboard and the first matches while this is up
  const ctx = () => { try { if (window.Shelf && Shelf._ctxSoon) Shelf._ctxSoon(); } catch {} };
  function setQ(q) { S.q = String(q).slice(0, 60); if (E.in.value !== S.q) E.in.value = S.q; update(); }
  function paintSel(show) {
    const on = !S.pad || S.zone === 'list' || S.bottomKb, kids = E.list.children;
    for (let i = 0; i < kids.length; i++) kids[i].classList.toggle('cur', on && i === S.sel);
    if (show && kids[S.sel]) kids[S.sel].scrollIntoView({ block: 'nearest' });
  }
  function paintKeys() {
    const k = S.pad && S.zone === 'keys' ? keyAt(S.kr, S.kc) : '';
    if (E.kcur && E.kcur !== E.key[k]) E.kcur.classList.remove('cur');
    E.kcur = k ? E.key[k] : null; if (E.kcur) E.kcur.classList.add('cur');
  }
  const snd = (n, o) => { try { B().snd(n, o); } catch {} };
  const focusIn = () => { try { E.in.focus({ preventScroll: true }); } catch {} };

  // ---------- moving and typing ----------
  function listMove(d) { const n = S.res.length; if (!n) return; const i = Math.max(0, Math.min(n - 1, S.sel + d)); if (i === S.sel) return; S.sel = i; paintSel(true); snd('tick', { dir: d > 0 ? 1 : -1 }); followSoon(); }
  function setZone(z) { if (z === 'list' && !S.res.length) return; if (S.zone === z) return; S.zone = z; paintKeys(); paintSel(true); hints(); snd('tick', { dir: z === 'list' ? 1 : -1 }); }
  function move(dx, dy) {
    if (S.zone === 'list') { if (dy) listMove(dy); else if (dx < 0) setZone('keys'); return; }
    const r = S.kr + dy; let c = S.kc + dx;
    if (r < 0 || r >= ROWS) return;
    if (dx && r === ROWS - 1 && c >= 0 && c < COLS && keyAt(r, c) === keyAt(r, S.kc)) c += dx; // (Space is two wide: one press leaves it)
    if (c >= COLS) { setZone('list'); return; }
    if (c < 0) return;
    S.kr = r; S.kc = c; paintKeys(); snd('tick', { dir: dx + dy > 0 ? 1 : -1 });
  }
  function type(k) {
    let q = S.q;
    if (k === 'back') q = q.slice(0, -1); else if (k === 'clear') q = '';
    else if (k === 'space') { if (q && !q.endsWith(' ')) q += ' '; }
    else q += !q || q.endsWith(' ') ? k : k.toLowerCase();
    if (q === S.q) return; setQ(q); snd('tick', { dir: k === 'back' || k === 'clear' ? -1 : 1 });
  }
  function act() { if (S.zone === 'list') choose(S.sel); else type(keyAt(S.kr, S.kc)); }
  function setPad(on, zone) {
    S.pad = !!on; el.classList.toggle('pad', S.pad);
    if (S.pad) { S.zone = zone || 'keys'; if (S.zone === 'list' && !S.res.length) S.zone = 'keys'; if (document.activeElement === E.in) E.in.blur(); } else focusIn();
    paintKeys(); paintSel(); hints();
  }
  function hints() {
    const b = B(); if (!b || !el) return; let rows = [];
    const cap = t => `<span class="fg-kc">${t}</span>`, G = k => { try { return b.glyph(k); } catch { return cap(k); } };
    // (the slim bar: the bottom screen types, a controller picks; nothing to say to a finger)
    if (S.bottomKb) { if (S.padSeen || b.pad()) rows = [[G('↔'), 'Choose'], [G('x'), 'Go to game'], [G('o'), 'Close']]; }
    else if (S.pad) rows = [[G('x'), S.zone === 'list' ? 'Go to game' : 'Type'], [G('sq'), 'Delete'], [G('tri'), 'Space'], [G('L1') + G('R1'), 'Letters · Results'], [G('o'), 'Close']];
    else if (!ANDROID()) rows = [[cap('↑') + cap('↓'), 'Choose'], [cap('Enter'), 'Go to game'], [cap('Esc'), 'Close']];
    const h = rows.map(([k, l]) => `<span class="fg-hint"><span class="fg-hk">${k}</span>${l}</span>`).join('');
    if (E.hints._h !== h) { E.hints._h = h; E.hints.innerHTML = h; }
  }

  // ---------- (Android, the bottom screen as the keyboard) the shelf under the slim bar shows the highlighted match ----------
  // A moment after the highlight settles, and never while the shelf is still sliding, the shelf steps to that game when it's
  // on the shelf already in view: one ordinary slide. A match on another console's shelf leaves the shelf where it is
  // (going there rebuilds the shelf, its music and its art: that waits for A). Closed without choosing, the shelf goes back.
  function followSoon() { if (!S.open || !S.bottomKb) return; clearTimeout(S.folT); S.folT = setTimeout(follow, 300); }
  function follow() {
    S.folT = 0; if (!S.open || !S.bottomKb) return;
    if (window.BS_SLIDING && BS_SLIDING()) { S.folT = setTimeout(follow, 120); return; }
    const g = S.res[S.sel]; if (!g) return;
    // (the shelf in view, read once per open: its games, and its name as the matches give it; Home has none, so it never follows there)
    if (!S.here) { let f = null, c = null, now = null; try { f = Shelf.artFocus(true); c = (Shelf.bottomShelves() || []).find(s => s.cur); now = Shelf._lib.cur(); } catch {}
      S.here = { ids: new Set(((f && f.ids) || []).map(String)), name: c && f && c.id === String(f.sys) ? c.name : null }; S.from = now ? now.id : null; }
    if (!S.here.name || g.sys !== S.here.name || !S.here.ids.has(String(g.id))) return;
    try { Shelf.setMode('browse'); if (quietGo(g.id)) S.moved = true; } finally { try { if (S.open) Shelf.setMode('find'); } catch {} }
  }
  // the shelf's own tick muted: Find's tick has already said the highlight moved
  function quietGo(id) { const so = window.Sound, P = so && so.play; let ok = false; try { if (P) so.play = () => {}; ok = !!B().go(id); } catch (e) { console.warn('find', e); } finally { if (P) so.play = P; } return ok; }

  // ---------- opening, choosing, closing ----------
  function open(how) {
    const b = B(); if (S.open || !b) return false;
    try { if (!b.can() || (window.BS_STUDIO && BS_STUDIO.isOpen && BS_STUDIO.isOpen())) return false; } catch { return false; }
    build();
    // a finger or the mouse just now (the Search button, a row of the quick menu): the field; else whatever the shelf's prompts show
    // (Linux, beta.12: a finger on the Steam Deck in Game Mode gets no on-screen keyboard for the field, so a touch opens it
    // on the letter grid, whose keys a finger taps)
    const ptr = performance.now() - S.ptrAt < 900, deckTouch = ptr && S.ptrType === 'touch' && LINUX();
    // (the Thor with its bottom screen on: the bottom screen is the keyboard. Here only the field and the matches, never
    // the letter grid nor Android's keyboard; the controller moves through the matches)
    let bottomKb = false; try { const bs = ANDROID() && window.boxshelf && boxshelf.bottomScreen; const st = bs && bs.status(); bottomKb = !!(st && st.available && st.enabled); } catch {}
    S.bottomKb = bottomKb;
    const pad = !bottomKb && (how === 'pad' || deckTouch || (how !== 'kbd' && !ptr && !!b.pad()));
    Shelf.setMode('find');
    clearTimeout(S.folT);
    Object.assign(S, { open: true, pad, zone: 'keys', kr: 0, kc: 0, sel: 0, q: '', list: null, res: [], total: 0, prev: null, rep: {}, gen: S.gen + 1, folT: 0, here: null, from: null, moved: false, padSeen: false });
    S.rows.clear(); E.in.value = ''; E.count.textContent = '';
    el.classList.toggle('pad', pad); el.classList.toggle('bottomkb', bottomKb); el.classList.add('on'); el.setAttribute('aria-hidden', 'false');
    if (bottomKb) document.body.classList.add('find-slim'); // (the shelf's own hint bar steps aside under the slim bar: its keys go to Find meanwhile)
    E.in.placeholder = bottomKb ? 'Type on the bottom screen' : 'Type a title';
    render(); paintKeys(); hints();
    if (!pad && !bottomKb) focusIn();
    snd('panel'); load(S.gen);
    return true;
  }
  // (stay: a match was chosen, so the shelf isn't taken back to where it was before the highlight moved it)
  function close(quiet, stay) {
    if (!S.open) return; S.open = false; S.gen++;
    clearTimeout(S.folT); S.folT = 0;
    el.classList.remove('on'); el.setAttribute('aria-hidden', 'true'); document.body.classList.remove('find-slim');
    if (document.activeElement === E.in) E.in.blur();
    try { if (Shelf.getMode() === 'find') Shelf.setMode('browse'); Shelf._wake(); } catch {}
    if (S.moved && !stay && S.from != null) quietGo(S.from);
    S.moved = false;
    if (!quiet) snd('back');
    ctx();
  }
  function choose(i) {
    const g = S.res[i]; if (!g) return;
    close(true, true);
    let ok = false; try { ok = B().go(g.id); } catch (e) { console.warn('find', e); }
    if (ok) snd('open'); else { try { let busy = false; try { busy = !B().can(); } catch {} Shelf.toast(busy ? 'One moment: the shelf is still changing. Try again' : `${g.title} isn’t on the shelf right now`); } catch {} }
  }

  // ---------- the controller (app.js pollBody hands it over while this is up; A / B already swapped for a Nintendo-style
  // confirm). Only presses made while it's open count, never what was held as it opened ----------
  function pad(gp, t) {
    if (!S.open || !gp) return;
    const P = i => !!(gp.buttons[i] && gp.buttons[i].pressed), ax = gp.axes || [];
    const now = { a: P(0), b: P(1), x: P(2), y: P(3), lb: P(4), rb: P(5), u: P(12) || ax[1] < -.6, d: P(13) || ax[1] > .6, l: P(14) || ax[0] < -.6, r: P(15) || ax[0] > .6 };
    const was = S.prev; S.prev = now; if (!was) return;
    const edge = k => now[k] && !was[k];
    // a direction (and Delete) held: again after a moment, then steadily
    const rep = k => { if (!now[k]) { S.rep[k] = 0; return false; } if (!was[k]) { S.rep[k] = t + 380; return true; } if (S.rep[k] && t > S.rep[k]) { S.rep[k] = t + 95; return true; } return false; };
    if (edge('b')) { close(); return; }
    // (the bottom screen is the keyboard: up / down through the matches, A goes there, X deletes a letter)
    // (the matches are one row there: left / right step through them too)
    if (S.bottomKb) {
      if (!S.padSeen) { for (const k in now) if (edge(k)) { S.padSeen = true; hints(); break; } }
      const back = rep('u') | rep('l'), fwd = rep('d') | rep('r');
      if (back) listMove(-1); else if (fwd) listMove(1); if (edge('a')) choose(S.sel); if (rep('x')) type('back'); return; }
    // the first press after the keyboard or a finger: the letter grid comes back (on the results when something is typed)
    // (that press only brings the grid: it doesn't also type a letter; on the results it acts as usual)
    if (!S.pad) { let any = false; for (const k in now) if (edge(k)) any = true; if (!any) return; setPad(true, S.q && S.res.length ? 'list' : 'keys'); if (S.zone === 'keys') return; }
    if (rep('u')) move(0, -1); else if (rep('d')) move(0, 1); else if (rep('l')) move(-1, 0); else if (rep('r')) move(1, 0);
    if (edge('a')) { act(); if (!S.open) return; }
    if (rep('x')) type('back'); if (edge('y')) type('space');
    if (edge('lb')) setZone('keys'); else if (edge('rb')) setZone('list');
  }

  // ---------- the keyboard: ahead of the shelf's own handler, which never hears a key while this is up ----------
  const typing = t => !!(t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT' || t.isContentEditable));
  addEventListener('keydown', e => {
    const k = e.key;
    // (the key that closed it or chose a game, still held: its repeats don't reach the shelf)
    if (S.eat) { if (k === S.eat && e.repeat) { e.preventDefault(); e.stopImmediatePropagation(); return; } S.eat = ''; }
    if (window.BS_TOUR && BS_TOUR.on()) return; // (the tour has the keys, this screen included)
    if (!S.open) {
      const slash = k === '/' && !e.ctrlKey && !e.altKey && !e.metaKey, ctrlF = (e.ctrlKey || e.metaKey) && !e.altKey && !e.shiftKey && (k === 'f' || k === 'F');
      if (!slash && !ctrlF) return;
      if (typing(e.target) || !window.Shelf || !Shelf.getMode || Shelf.getMode() !== 'browse' || (Shelf.quickMenuOpen && Shelf.quickMenuOpen())) return;
      e.preventDefault(); e.stopImmediatePropagation(); if (!e.repeat) open('kbd');
      return;
    }
    if (k === 'F11' || k === 'F12' || (e.ctrlKey && e.shiftKey && (k === 'I' || k === 'i'))) return; // (full screen, DevTools; no other Ctrl+Shift key reaches the shelf behind)
    e.stopImmediatePropagation();
    if (k === 'Escape') { e.preventDefault(); if (!e.repeat) { S.eat = k; close(); } return; }
    if (k === 'Enter') { e.preventDefault(); if (e.repeat || e.isComposing) return; S.eat = k; if (S.pad) act(); else choose(S.sel); return; }
    if (k === 'ArrowDown' || k === 'ArrowUp') { e.preventDefault(); const d = k === 'ArrowDown' ? 1 : -1; if (S.pad) move(0, d); else listMove(d); return; }
    if (k === 'PageDown' || k === 'PageUp') { e.preventDefault(); if (!S.pad || S.zone === 'list') listMove(k === 'PageDown' ? 5 : -5); return; }
    if (k === 'ArrowLeft' || k === 'ArrowRight') { if (S.pad) { e.preventDefault(); move(k === 'ArrowRight' ? 1 : -1, 0); } else if (S.bottomKb && document.activeElement !== E.in) { e.preventDefault(); listMove(k === 'ArrowRight' ? 1 : -1); } return; } // (in the field they're the caret's; the slim bar's one row of matches steps with them)
    if (k === 'Tab') { e.preventDefault(); if (S.pad) setZone(S.zone === 'keys' ? 'list' : 'keys'); return; }
    if ((e.ctrlKey || e.metaKey) && (k === 'f' || k === 'F')) { e.preventDefault(); if (S.pad) setPad(false); else focusIn(); E.in.select(); return; }
    // anything else is typing: it goes into the field (the letter grid gives way to a real keyboard)
    if (S.pad) setPad(false); else if (document.activeElement !== E.in) focusIn();
  }, true);
  addEventListener('keyup', e => { if (S.open && !(window.BS_TOUR && BS_TOUR.on())) e.stopImmediatePropagation(); }, true);
  // (when the last finger or click was: a row of the quick menu or a hint pressed that way opens on the field, not the grid)
  addEventListener('pointerdown', e => { S.ptrAt = performance.now(); S.ptrType = e.pointerType || ''; }, { capture: true, passive: true });
  // (the slim bar over the shelf, Android: a touch on the shelf under it closes Find, and that touch does nothing else, as
  // with the quick menu)
  addEventListener('pointerdown', e => {
    if (!S.open || !S.bottomKb || !el || (window.BS_TOUR && BS_TOUR.on())) return;
    const t = e.target; if (!t || el.contains(t) || (t.closest && t.closest('#toast'))) return;
    e.stopImmediatePropagation(); S.eatClick = performance.now(); close();
  }, true);
  addEventListener('click', e => { if (performance.now() - S.eatClick < 700) { S.eatClick = -1e9; e.stopImmediatePropagation(); e.preventDefault(); } }, true);

  // the Search button beside Menu; the page elements made in a quiet moment so the first open has nothing to build
  function ready() {
    const b = document.getElementById('findBtn');
    if (b) b.addEventListener('click', e => { e.stopPropagation(); if (S.open) close(); else open('ptr'); });
    const warm = () => { try { build(); } catch {} };
    if (window.requestIdleCallback) requestIdleCallback(warm, { timeout: 10000 }); else setTimeout(warm, 4000);
  }
  if (document.readyState === 'loading') addEventListener('DOMContentLoaded', ready, { once: true }); else ready();

  // (beta.12) the bottom screen's keyboard: a letter (k + the letter), back, space, clear; pick a match (its place in the
  // list); close. Typed there, the letter grid here steps aside without bringing up Android's keyboard on this screen
  function remote(k, v) {
    if (!S.open) return false;
    if (S.pad) { S.pad = false; el.classList.remove('pad'); paintKeys(); paintSel(); hints(); }
    if (document.activeElement === E.in) E.in.blur();
    if (k === 'k') { const ch = String(v == null ? '' : v).slice(0, 1); if (!/^[\p{L}\p{N}]$/u.test(ch)) return false; type(ch.toUpperCase()); }
    else if (k === 'back' || k === 'space' || k === 'clear') type(k);
    else if (k === 'pick') choose(Math.max(0, +v | 0));
    else if (k === 'close') close();
    else return false;
    return true;
  }
  window.BS_FIND = { isOpen: () => S.open, open, close: () => close(), pad, remote, top: n => S.res.slice(0, n).map((g, i) => ({ i, title: g.title, sys: g.sys })),
    _st: () => ({ open: S.open, pad: S.pad, zone: S.zone, q: S.q, sel: S.sel, key: keyAt(S.kr, S.kc), total: S.total, loaded: !!S.list, res: S.res.map(g => g.title + ' · ' + g.sys) }) };
})();
