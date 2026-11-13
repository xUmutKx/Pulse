// (beta.12, Linux Game Mode) Boxshelf's own file and folder browser. In Steam's Game Mode the desktop's file dialog can't be
// used with a controller (and may not appear at all), so main.js (openDialog) sends 'pick:open' and this answers with the
// chosen path, or nothing when cancelled. Controller, keyboard, mouse and touch:
//   up / down (d-pad, stick, arrows) move · A / Enter opens a folder or picks a file · B / Backspace goes up a folder
//   (at a place's own folder: back to the places) · Y / Space uses the folder you're in (folder picks) · Start / Esc cancels
//   left / right switch between the places and the folder
// While it's open nothing else hears the keys or the controller (window.BS_PICK.isOpen(), checked by the shelf's and
// Settings' controller loops). Its controller loop runs only while it's open; a press is acted on when it's let go of, so
// the screen underneath never gets the end of it.
'use strict';
(() => {
  let api = window.boxshelf || {};
  const S = { open: false, id: 0, folder: false, exts: [], dir: '', parent: '', list: [], places: [], sel: 0, side: 'list', raf: 0, prev: {}, held: {}, rep: 0 };
  const $ = (t, c, p) => { const e = document.createElement(t); if (c) e.className = c; if (p) p.append(e); return e; };
  let el = null;
  function build() {
    if (el) return el;
    el = $('div', 'bsp'); el.setAttribute('role', 'dialog');
    const box = $('div', 'bsp-box', el), head = $('div', 'bsp-head', box);
    $('div', 'bsp-title', head); $('div', 'bsp-path', head);
    const body = $('div', 'bsp-body', box); $('div', 'bsp-places', body); $('div', 'bsp-list', body);
    $('div', 'bsp-hints', box);
    el.addEventListener('click', e => {
      const pl = e.target.closest('.bsp-place'); if (pl) { S.psel = +pl.dataset.i; S.side = 'list'; go(S.places[S.psel].path); return; } // (the folder's list is where you are next)
      const it = e.target.closest('.bsp-item'); if (it) { S.side = 'list'; S.sel = +it.dataset.i; paintSel(); choose(); return; }
      const h = e.target.closest('[data-act]'); if (h) act(h.dataset.act);
    });
    document.body.appendChild(el); return el;
  }
  const want = n => { if (S.folder) return false; if (!S.exts.length || S.exts.includes('exe')) return true; const m = /\.([^.]+)$/.exec(n); return !!m && S.exts.includes(m[1].toLowerCase()); };
  const shown = () => S.list.filter(x => x.d || want(x.n));
  async function go(dir) {
    const id = S.id; let r; try { r = await api.fsList(dir, { folder: S.folder, exts: S.exts }); } catch (e) { r = { dir, parent: '', entries: [], error: String(e && e.message || e) }; }
    if (!S.open || S.id !== id) return; // (closed, or another pick, meanwhile)
    S.dir = r.dir; S.parent = r.parent; S.list = r.entries || []; S.more = +r.more || 0; S.err = r.error || ''; S.sel = 0; paint();
  }
  function paint() {
    const e = build();
    e.querySelector('.bsp-title').textContent = S.title || (S.folder ? 'Choose a folder' : 'Choose a file');
    e.querySelector('.bsp-path').textContent = '‎' + S.dir + '‎'; // (the marks keep the slashes where they belong in the right-aligned path)
    const pl = e.querySelector('.bsp-places'); pl.innerHTML = '';
    let here = -1, hl = -1; S.places.forEach((p, i) => { if ((S.dir === p.path || S.dir.startsWith(p.path.replace(/\/$/, '') + '/')) && p.path.length > hl) { here = i; hl = p.path.length; } }); // (the closest one: Emulation, not also Home)
    S.places.forEach((p, i) => { const b = $('button', 'bsp-place' + (i === here ? ' here' : ''), pl); b.type = 'button'; b.dataset.i = i; b.textContent = p.label; });
    const L = e.querySelector('.bsp-list'); L.innerHTML = '';
    const items = shown();
    if (S.err) { const m = $('div', 'bsp-empty', L); m.textContent = S.err; }
    else if (!items.length) { const m = $('div', 'bsp-empty', L); m.textContent = S.folder ? 'No folders in here.' : 'Nothing to pick in here.'; }
    items.forEach((x, i) => { const b = $('button', 'bsp-item' + (x.d ? ' dir' : ''), L); b.type = 'button'; b.dataset.i = i; b.textContent = x.n; });
    if (S.more) { const m = $('div', 'bsp-empty', L); m.textContent = `${S.more} more not shown: open a folder inside, or pick from Settings on the desktop.`; }
    hints(); paintSel();
  }
  function hints() {
    const h = el.querySelector('.bsp-hints'), pad = document.body.classList.contains('pad-input');
    const k = (p, kb) => pad ? p : kb, rows = [[k('A', 'Enter'), 'Open'], [k('B', 'Backspace'), 'Up a folder']];
    if (S.folder) rows.push([k('Y', 'Space'), 'Use this folder']);
    rows.push([k('Start', 'Esc'), 'Cancel']);
    h.innerHTML = ''; rows.forEach(([key, lab], i) => { const b = $('button', 'bsp-hint', h); b.type = 'button'; b.dataset.act = ['open', 'up', S.folder ? 'use' : 'cancel', 'cancel'][i]; const g = $('span', 'bsp-key', b); g.textContent = key; b.append(' ' + lab); });
  }
  function paintSel() {
    if (!el) return;
    el.querySelectorAll('.bsp-item.sel, .bsp-place.sel').forEach(x => x.classList.remove('sel'));
    const t = S.side === 'list' ? el.querySelectorAll('.bsp-item')[S.sel] : el.querySelectorAll('.bsp-place')[S.psel || 0];
    if (t) { t.classList.add('sel'); t.scrollIntoView({ block: 'nearest' }); }
  }
  function move(d) {
    if (S.side === 'list') { const n = shown().length; if (!n) return; S.sel = Math.max(0, Math.min(n - 1, S.sel + d)); }
    else S.psel = Math.max(0, Math.min(S.places.length - 1, (S.psel || 0) + d));
    paintSel();
  }
  function choose() {
    if (S.side === 'places') { const p = S.places[S.psel || 0]; if (p) { S.side = 'list'; go(p.path); } return; }
    const x = shown()[S.sel]; if (!x) return;
    const full = (S.dir === '/' ? '' : S.dir) + '/' + x.n;
    if (x.d) go(full); else done(full);
  }
  function up() { const atPlace = S.places.some(p => p.path === S.dir); if (S.parent && !atPlace) go(S.parent); else { S.side = 'places'; paintSel(); } }
  function act(a) { if (a === 'open') choose(); else if (a === 'up') up(); else if (a === 'use') { if (S.folder && S.dir) done(S.dir); } else if (a === 'cancel') done(''); }
  function done(p) {
    if (!S.open) return; S.open = false; cancelAnimationFrame(S.raf); S.raf = 0;
    if (el) el.classList.remove('on');
    api.pickDone(S.id, p || '');
  }
  // the controller, only while open: acted on when a button is let go of (nothing underneath sees its end)
  const confirmB = () => (window.BS_PAD && Number.isInteger(BS_PAD.confirm) ? BS_PAD.confirm : 0), backB = () => (window.BS_PAD && Number.isInteger(BS_PAD.back) ? BS_PAD.back : 1);
  function poll(t) {
    S.raf = 0; if (!S.open) return; S.raf = requestAnimationFrame(poll);
    const pads = navigator.getGamepads ? [...navigator.getGamepads()].filter(Boolean) : []; if (!pads.length) return;
    const B = i => pads.some(g => g.buttons[i] && g.buttons[i].pressed), ax = (i, s) => pads.some(g => (g.axes[i] || 0) * s > .6);
    const now = { a: B(confirmB()), b: B(backB()), y: B(3), st: B(9), u: B(12) || ax(1, -1), d: B(13) || ax(1, 1), l: B(14) || ax(0, -1), r: B(15) || ax(0, 1), lb: B(4), rb: B(5) };
    if (Object.values(now).some(Boolean)) document.body.classList.add('pad-input');
    for (const k in now) if (now[k] && !S.prev[k]) S.down[k] = true; // (a press that began while it's open)
    const rel = k => { if (S.down[k] && S.prev[k] && !now[k]) { S.down[k] = false; return true; } return false; }, dirKey = k => { if (now[k] && !S.prev[k]) { S.held[k] = t + 380; return true; } if (now[k] && t > (S.held[k] || 0)) { S.held[k] = t + 90; return true; } return false; };
    if (dirKey('u')) move(-1); if (dirKey('d')) move(1); if (dirKey('lb')) move(-10); if (dirKey('rb')) move(10);
    if (now.l && !S.prev.l) { S.side = 'places'; paintSel(); } if (now.r && !S.prev.r && S.side === 'places') { S.side = 'list'; paintSel(); }
    if (rel('a')) choose(); else if (rel('b')) up(); else if (rel('y')) act('use'); else if (rel('st')) done('');
    S.prev = now;
  }
  // the keyboard: ahead of everything (this file loads right after perf.js), and nothing else hears it while open
  addEventListener('keydown', e => {
    if (!S.open) return; e.preventDefault(); e.stopImmediatePropagation(); document.body.classList.remove('pad-input');
    const k = e.key;
    if (k === 'ArrowUp') move(-1); else if (k === 'ArrowDown') move(1); else if (k === 'PageUp') move(-10); else if (k === 'PageDown') move(10);
    else if (k === 'ArrowLeft') { S.side = 'places'; paintSel(); } else if (k === 'ArrowRight') { S.side = 'list'; paintSel(); }
    else if (k === 'Enter' && !e.repeat) choose(); else if (k === 'Backspace') up(); else if (k === ' ' && !e.repeat) act('use'); else if (k === 'Escape' && !e.repeat) done('');
  }, true);
  addEventListener('keyup', e => { if (S.open) { e.preventDefault(); e.stopImmediatePropagation(); } }, true);
  const hook = () => { api = window.boxshelf || api; if (api.onPickOpen && !hook.done) { hook.done = true; api.onPickOpen(openPick); } };
  if (document.readyState === 'loading') addEventListener('DOMContentLoaded', hook, { once: true }); else hook();
  hook(); // (Electron's preload: already there)
  async function openPick(d) {
    if (!d) return; if (S.open) done(''); // (one at a time: a new ask ends the old one)
    Object.assign(S, { open: true, id: d.id, folder: !!d.folder, exts: Array.isArray(d.exts) ? d.exts : [], title: d.title || '', side: 'list', psel: 0, prev: { a: true, b: true, y: true, st: true }, down: {}, held: {} }); // (a button acts only when pressed and let go while it's open: the press that opened it isn't taken)
    const id = d.id; let places = []; try { places = await api.fsPlaces(); } catch {}
    if (!S.open || S.id !== id) return; // (cancelled, or another pick asked, while the places came: nothing left showing)
    S.places = places; build().classList.add('on');
    let start = d.start || ''; if (start && !d.folder && /\.[^/]+$/.test(start)) start = start.replace(/\/[^/]*$/, '');
    await go(start || (S.places[0] && S.places[0].path) || '');
    if (!S.raf) S.raf = requestAnimationFrame(poll);
  }
  window.BS_PICK = { isOpen: () => S.open };
})();
