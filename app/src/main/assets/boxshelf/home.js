// The Home menu overlay, used two ways:
//  • in a game: Resume, Save/Load state, Screenshot, Power, Quit to shelf (only what that emulator supports)
//  • on the shelf: Home shelf, Museum, Settings, (beta.12, Seamless play) Go to desktop, Power
// Power opens a sub-list in place (Sleep, Restart, Shut down, Quit Boxshelf). The main process opens the menu,
// forwards controller buttons (from the XInput helper) and carries out whatever is chosen here.
(() => {
  // fonts are bundled (fonts/fonts.css, linked from home.html): no font CDN request
  const api = window.home || { onOpen() {}, onButton() {}, onToast() {}, act() {} };
  const $ = id => document.getElementById(id);
  const P = (d, fill) => fill ? `<path d="${d}" fill="currentColor"/>` : `<path d="${d}" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>`;
  const ICON = {
    resume: P('M8 5.5v13l11-6.5z', true),
    save: P('M12 4v10m0 0-4-4m4 4 4-4M5 15v3a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-3'),
    load: P('M12 15V5m0 0-4 4m4-4 4 4M5 15v3a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-3'),
    shot: P('M4 8.5A2.5 2.5 0 0 1 6.5 6h1.7l1.3-2h5l1.3 2h1.7A2.5 2.5 0 0 1 20 8.5v8a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 16.5zM12 9.1a3.4 3.4 0 1 0 0 6.8 3.4 3.4 0 0 0 0-6.8z'),
    quit: P('M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 8l-4 4 4 4M6 12h10'),
    power: P('M12 3v8M6.3 6.8a8 8 0 1 0 11.4 0'),
    shelfhome: P('M4 11l8-7 8 7v8a1 1 0 0 1-1 1h-5v-6h-4v6H5a1 1 0 0 1-1-1z'),
    museum: P('M3 9l9-5 9 5M5 10v8M9.5 10v8M14.5 10v8M19 10v8M3 20h18'),
    settings: P('M12 9.2a2.8 2.8 0 1 0 0 5.6 2.8 2.8 0 0 0 0-5.6zM19.4 13.5l1.6 1.2-1.8 3.1-1.9-.6a7.4 7.4 0 0 1-1.6.9L15.3 20h-3.6l-.4-1.9a7.4 7.4 0 0 1-1.6-.9l-1.9.6-1.8-3.1 1.6-1.2a7 7 0 0 1 0-1.9L6 10.4l1.8-3.1 1.9.6c.5-.4 1-.7 1.6-.9L11.7 5h3.6l.4 1.9c.6.2 1.1.5 1.6.9l1.9-.6 1.8 3.1-1.6 1.2a7 7 0 0 1 0 1.9z'),
    sleep: P('M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z'),
    restart: P('M20 11a8 8 0 1 0-2.3 5.7M20 4v7h-7'),
    shutdown: P('M12 3v8M6.3 6.8a8 8 0 1 0 11.4 0'),
    quitapp: P('M6 6l12 12M18 6L6 18'),
    desktop: P('M3 5h18v11H3zM8 20h8M12 16v4'), // (beta.12) Seamless play: Go to desktop
    back: P('M15 5l-7 7 7 7')
  };
  const LABEL = { resume: 'Resume', save: 'Save state', load: 'Load state', shot: 'Screenshot', quit: 'Quit to shelf', power: 'Power',
    shelfhome: 'Home shelf', museum: 'Museum', settings: 'Settings', desktop: 'Go to desktop',
    sleep: 'Sleep', restart: 'Restart', shutdown: 'Shut down', quitapp: 'Quit Boxshelf', back: 'Back' };
  const HOLD = new Set(['quit', 'restart', 'shutdown', 'quitapp']); // these need A held, so a stray press never does them
  const SEP_BEFORE = new Set(['power', 'quit']);
  let main = [], power = [], items = [], inPower = false, sel = 0, mainSel = 0, open = false, info = null, holdT = 0, holdStart = 0, toastT = 0, clockT = 0;
  const HOLD_MS = 900;

  function fmtFor(ms) { const m = Math.max(1, Math.round(ms / 60000)); return m < 60 ? `${m} min` : `${Math.floor(m / 60)} h ${m % 60 ? (m % 60) + ' min' : ''}`.trim(); }
  function tick() {
    $('clock').textContent = new Date().toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
    if (info && info.startedAt) $('time').textContent = `Playing for ${fmtFor(Date.now() - info.startedAt)}`;
  }
  // Settings → Controller → Confirm button 'b' (Nintendo style): the main process passes info.confirmButton; A and B swap
  const swapAB = () => !!(info && info.confirmButton === 'b');
  // which: 'ok' / 'back' -> the face button that does it (bottom, or right when swapped), labelled as that pad prints it
  function glyph(which) { const g = info && info.glyphs, bottom = (which === 'ok') !== swapAB();
    return g === 'ps' ? (bottom ? '✕' : '○') : g === 'nintendo' ? (bottom ? 'B' : 'A') : (bottom ? 'A' : 'B'); }
  function subFor(id) {
    if ((id === 'restart' || id === 'shutdown') && info && info.inGame) return `closes the game · hold ${glyph('ok')}`;
    if (HOLD.has(id)) return `hold ${glyph('ok')}`;
    if ((id === 'save' || id === 'load') && info && info.slot) return `slot ${info.slot}`;
    if (id === 'sleep' && info && info.inGame) return info.canSave ? 'saves, then sleeps' : info.paused ? 'game stays paused' : 'game keeps running';
    if (id === 'resume' && info && info.inGame && !info.paused) return 'game is still running';
    if (id === 'power') return '›';
    return '';
  }
  function render() {
    const nav = $('items'); nav.innerHTML = '';
    document.body.classList.toggle('in-power', inPower);
    if (inPower) { const h = document.createElement('div'); h.className = 'subhead'; h.textContent = 'Power'; nav.append(h); }
    items.forEach((id, i) => {
      if (!inPower && SEP_BEFORE.has(id) && i > 0) nav.append(Object.assign(document.createElement('div'), { className: 'sep' }));
      const el = document.createElement('div'); el.className = 'item' + (i === sel ? ' sel' : '') + (HOLD.has(id) ? ' danger' : ''); el.setAttribute('role', 'menuitem');
      const sub = subFor(id);
      el.innerHTML = `<svg viewBox="0 0 24 24">${ICON[id] || ''}</svg><span></span>`; el.lastChild.textContent = LABEL[id] || id; // text parts as text: sub can carry values from the main process
      if (sub) { const sp = document.createElement('span'); sp.className = 'sub'; sp.textContent = sub; el.append(sp); }
      if (HOLD.has(id)) { const h = document.createElement('span'); h.className = 'hold'; el.append(h); }
      nav.append(el);
    });
    const backLabel = inPower ? 'Back' : info && info.inGame ? 'Resume' : 'Close';
    $('foot').innerHTML = `<span><b>${glyph('ok')}</b>Select</span><span><b>${glyph('back')}</b>${backLabel}</span><span><b>↕</b>Move</span><span><b class="guide">${info && info.glyphs === 'nintendo' ? 'Home' : 'Guide'}</b>${info && info.inGame ? 'Resume' : 'Close'}</span>`;
  }
  // the shelf's own sounds, in a game too (the shelf's audio is asleep while a game runs; this small window has its own)
  const cue = (name, o) => { if (!info || !info.inGame || !window.Sound) return; try { Sound.play(name, o); } catch {} };
  function move(d) { if (!items.length) return; sel = (sel + d + items.length) % items.length; cancelHold(); render(); cue('tick', { dir: d > 0 ? 1 : -1 }); }
  let holdShown = 0, holdFull = false;
  function cancelHold() { cancelAnimationFrame(holdT); holdT = 0; holdShown = 0; holdFull = false; const h = document.querySelector('.item.sel .hold'); if (h) h.style.width = '0'; }
  function holdLoop() {
    // the bar is the timer: it can't jump more than a step per frame (a busy game can make frames late), and the action
    // only fires once a full bar has actually been on screen, so it never goes off before the bar fills
    if (holdFull) { holdT = 0; holdFull = false; choose(items[sel]); return; }
    const p = Math.min(1, (performance.now() - holdStart) / HOLD_MS, holdShown + .08); holdShown = p;
    const h = document.querySelector('.item.sel .hold'); if (h) h.style.width = (p * 100) + '%';
    if (p >= 1) holdFull = true; holdT = requestAnimationFrame(holdLoop);
  }
  function enterPower() { mainSel = sel; inPower = true; items = power.concat(['back']); sel = 0; render(); cue('open'); }
  function leavePower() { inPower = false; items = main; sel = mainSel; render(); cue('back'); }
  function choose(id) {
    if (!open) return; cancelHold();
    if (id === 'power') return enterPower();
    if (id === 'back') return leavePower();
    close(id);
  }
  // once the closing cue has played, the menu's audio fades and sleeps (it ran for the whole game otherwise); a cue wakes it
  let sndT = 0;
  function close(action) { if (!open) return; cancelHold(); cue(!action || action === 'resume' || action === 'quit' ? 'back' : 'open'); open = false; inPower = false; document.body.classList.remove('open'); clearInterval(clockT); api.act(action || 'resume');
    clearTimeout(sndT); sndT = setTimeout(() => { if (!open && window.Sound && Sound.fadeOut) { try { Sound.fadeOut(400); } catch {} } }, 900); }

  api.onOpen(d => {
    info = d || {}; main = (info.actions && info.actions.length ? info.actions : ['resume', 'quit']); power = info.power || ['sleep', 'restart', 'shutdown'];
    items = main; inPower = false; sel = 0; cancelHold(); // a hold still filling from the last time must not fire in this one
    document.documentElement.style.setProperty('--accent', info.accent || '#9d8cff');
    document.body.classList.toggle('layout-center', info.layout === 'center'); document.body.classList.toggle('layout-side', info.layout !== 'center');
    document.body.classList.toggle('is-paused', !!info.paused); document.body.classList.toggle('shelf', !info.inGame);
    { let rm = false; try { rm = info.reduceMotion === true || (info.reduceMotion === 'system' && matchMedia('(prefers-reduced-motion: reduce)').matches); } catch {} document.body.classList.toggle('reduce-motion', rm); } // (beta.12) Boxshelf's own Reduce motion
    $('cover').src = info.cover || ''; $('cover').parentNode.style.display = info.cover ? '' : 'none';
    $('sys').textContent = info.system || ''; $('title').textContent = info.title || (info.inGame ? 'Now playing' : 'Home'); $('time').textContent = info.subtitle || '';
    render(); tick(); clearInterval(clockT); clockT = setInterval(tick, 15000);
    if (window.Sound && Sound.setSink && typeof info.audioOut === 'string') { try { Sound.setSink(info.audioOut); } catch {} } // Settings → Sound → Output device, when passed
    if (info.inGame && info.sound && window.Sound) { try { Sound.set({ enabled: info.sound.enabled !== false, theme: info.sound.theme, volume: info.sound.volume, boot: info.sound.boot }); } catch {} }
    cue('panel');
    open = true; requestAnimationFrame(() => document.body.classList.add('open'));
  });
  // controller buttons from the main process (XInput helper): up/down/left/right/a/b/start/home, with down/up for A.
  // 'ok' / 'cancel' are the same as a / b but never swapped (keyboard Enter / Esc: they mean select / back on any setting).
  api.onButton(({ btn, down }) => {
    if (!open) return;
    if (swapAB()) btn = btn === 'a' ? 'b' : btn === 'b' ? 'a' : btn;
    press(btn === 'ok' ? 'a' : btn === 'cancel' ? 'b' : btn, down);
  });
  // The window only has the keyboard while it's open over a media app (the menu takes the focus there): Enter / Esc and the
  // arrows work like on the shelf, instead of nothing at all
  const KEY = { ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right', Enter: 'a', ' ': 'a', Escape: 'b', Backspace: 'b', Home: 'home' };
  addEventListener('keydown', e => { const b = KEY[e.key]; if (!b || !open) return; e.preventDefault(); if (e.repeat && !/^Arrow/.test(e.key)) return; press(b, true); });
  addEventListener('keyup', e => { const b = KEY[e.key]; if (b === 'a' && open) press(b, false); });
  function press(btn, down) {
    const centre = document.body.classList.contains('layout-center') && !inPower;
    if (down && (btn === 'up' || (centre && btn === 'left'))) move(centre && btn === 'up' ? -3 : -1);
    else if (down && (btn === 'down' || (centre && btn === 'right'))) move(centre && btn === 'down' ? 3 : 1);
    else if (btn === 'a') {
      const id = items[sel];
      if (HOLD.has(id)) { if (down) { holdStart = performance.now(); cancelAnimationFrame(holdT); holdT = requestAnimationFrame(holdLoop); } else cancelHold(); }
      else if (down) choose(id);
    }
    else if (down && btn === 'b') { if (inPower) leavePower(); else close('resume'); }
    else if (down && (btn === 'home' || btn === 'start')) close('resume');
  }
  if (api.onPaused) api.onPaused(p => { if (!info || !info.inGame) return; info.paused = !!p; document.body.classList.toggle('is-paused', !!p); if (open) render(); }); /* the pause lands a moment after the menu appears */
  api.onToast(text => { const t = $('toast'); t.textContent = text; t.classList.add('on'); clearTimeout(toastT); toastT = setTimeout(() => t.classList.remove('on'), 1800); });
})();
