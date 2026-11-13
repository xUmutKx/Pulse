/*
 * Pulse's side of the Boxshelf renderer (GPL-3.0, https://github.com/sassiestrhihno/boxshelf-source). Boxshelf's own screen asks one object,
 * window.boxshelf, for everything; mock.js gives it demo data. This file runs right after mock.js and answers with the games of Pulse's Game Hub
 * instead: one shelf of apps, the icon (or the cover picture) of each, and Play = start the game through Pulse. PulseHost is what the
 * Android side hands in (a JavascriptInterface); without it nothing here changes.
 */
(function () {
  const H = window.PulseHost;
  if (!H || !window.boxshelf) return;
  const api = window.boxshelf;
  // Game Hub's own settings (the keys the Game Hub page uses), read and written where the Compose page keeps them
  api.ghGet = k => { try { return H.ghGet(k); } catch (e) { return ''; } };
  api.ghSet = (k, v) => { try { H.ghSet(k, String(v)); } catch (e) {} };
  const list = () => { try { return JSON.parse(H.games()); } catch (e) { return []; } };
  const entry = g => ({
    kind: 'app', id: 'app-' + g.pkg, appId: g.pkg, title: g.name, path: g.pkg, pulse: true,
    // the Steam screenshots for the back of the box (up to four URLs; none until the covers were looked for)
    shots: Array.isArray(g.shots) ? g.shots : [],
    // the box style of the game's box: its spine says that console
    box: g.box || 'steam',
    hidden: !!g.hidden, minutes: Math.round((g.playMs || 0) / 60000), lastPlayed: g.lastUsed ? g.lastUsed / 1000 : 0,
    // the picture of a game is its cover from the internet (Steam, SteamGridDB, Google Play) or its wide art; the app's icon is never used as the cover
    // appIcon is only for the title line (not 'icon': the shelf builds a cover from that one when there is none)
    art: Object.assign({}, g.cover || g.hero ? { front: g.cover || g.hero } : {}, g.hero ? { hero: g.hero } : {}, g.icon ? { appIcon: g.icon } : {}, g.logo ? { logo: g.logo } : {}),
    rating: g.rating || '',
  });
  // the look of the cases: the id of one of Boxshelf's own case styles (pc = a Steam-like case, custom, ps5, ps4, x360, sw ...), chosen in Game Hub
  const sid = () => { try { return (H.caseStyle && H.caseStyle()) || 'pc'; } catch (e) { return 'pc'; } };
  const shelf = () => [{ id: sid(), kind: 'apps', name: 'Games', games: list().map(entry) }];
  // the heading of the shelf, whatever case style it wears
  window.PULSE_WORDMARKS = Object.fromEntries(['pc', 'custom', 'ps5', 'ps4', 'ps3', 'ps2', 'x360', 'sw', 'sw2', 'gc', 'wii', 'wiiu', 'psp', 'n3ds'].map(k => [k, ['Games', 'On this phone']]));
  api.platform = { os: 'android', caps: {} };
  // one shelf only (all the games): its tab bar has nothing to choose and lay over the boxes; the key hints (Enter, V, Q E...) mean nothing on a touch phone
  const st = document.createElement('style');
  st.textContent = '#tabs,#hints{display:none!important}';
  document.head.appendChild(st);
  // AMOLED dark (Game Hub settings, or Pulse's AMOLED look): a very dark, nearly black screen, no coloured backdrop, glow or floor; only the boxes light it
  try {
    if (H.amoled && H.amoled()) {
      const am = document.createElement('style');
      am.textContent = 'html,body,#app,.bss-root{background:#000!important;background-image:none!important}#app::before,#app::after,body::before,body::after,.theme-canvas,.bg-layer,.theme-fx,.vignette,.sel-halo,.floor,.pv-back,.bss-bg,.bss-mus{display:none!important;background:none!important}';
      document.head.appendChild(am);
    }
  } catch (e) {}
  // the page's sounds start with the first touch (a sound context may only begin on one)
  addEventListener('pointerdown', () => { try { window.Sound && Sound.warm && Sound.warm(); } catch (e) {} }, { once: true, capture: true });
  api.scan = async () => shelf();
  api.cachedLibrary = async () => shelf();
  api.launchApp = async appId => { H.launch(String(appId)); return {}; };
  api.pulseDetails = pkg => H.details(String(pkg));
  // the game's guide for Details (what the game is: Steam's page, or its Google Play page): kept after the first read; it appears a moment after Details opens
  window.__pulseGuide = g => {
    const id = String(g.appId), t = H.guideCached ? H.guideCached(id) : '';
    if (t) { g.guideOn = true; return t; }
    if (H.guideFetch && !g.__gTry) {
      g.__gTry = 1; H.guideFetch(id); let n = 0;
      const iv = setInterval(() => {
        const x = H.guideCached(id);
        if (x || ++n > 25) {
          clearInterval(iv);
          if (x) { g.guideOn = true; const d = document.getElementById('dDesc'); if (d && window.__pulseCur && window.__pulseCur.appId === g.appId) { d.textContent = x; d.hidden = false; const sa = document.getElementById('dSecAbout'); if (sa) sa.hidden = false; } }
        }
      }, 1000);
    }
    return '';
  };
  // the back cover of a box shows a delete button: an app is uninstalled (Android asks first), a built-in game leaves the shelf, a game of your own is deleted
  (function () {
    const css = document.createElement('style');
    css.textContent = '#pulseDel{position:fixed;left:50%;bottom:max(22px,env(safe-area-inset-bottom));transform:translateX(-50%);z-index:8000;display:none;align-items:center;gap:10px;height:48px;padding:0 20px 0 14px;border-radius:24px;border:0;background:rgba(40,12,16,.92);color:#ffb4b4;font:600 15px system-ui,sans-serif;box-shadow:0 6px 22px rgba(0,0,0,.5)}#pulseDel.on{display:flex}#pulseDel svg{width:22px;height:22px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}#pulseAsk{position:fixed;inset:0;z-index:9500;background:rgba(0,0,0,.6);display:flex;align-items:center;justify-content:center;padding:24px}#pulseAsk div{background:#15181f;color:#eef;border-radius:20px;padding:20px;max-width:340px;font:15px system-ui,sans-serif}#pulseAsk p{margin:0 0 6px}#pulseAsk small{color:#9aa}#pulseAsk .row{display:flex;gap:10px;justify-content:flex-end;margin-top:16px}#pulseAsk button{border:0;border-radius:14px;padding:10px 18px;font:600 15px system-ui,sans-serif;background:#2a2f3a;color:#dde}#pulseAsk button.go{background:#c0392b;color:#fff}';
    document.head.appendChild(css);
    const b = document.createElement('button'); b.id = 'pulseDel'; b.type = 'button';
    b.innerHTML = '<svg viewBox="0 0 24 24"><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v6M14 11v6"/></svg><span></span>';
    document.addEventListener('DOMContentLoaded', () => document.body.appendChild(b));
    if (document.body) document.body.appendChild(b);
    const kind = g => { const id = String(g && g.appId || ''); return id.indexOf('mega:') === 0 ? 'mega' : id.indexOf('html:') === 0 ? 'html' : 'app'; };
    const label = k => k === 'app' ? 'Uninstall' : k === 'html' ? 'Delete game' : 'Remove from shelf';
    let last = '';
    setInterval(() => {
      let g = null, on = false;
      try { on = !!(window.__pulseFlipped && window.__pulseFlipped()); g = on && window.__pulseCurGame ? window.__pulseCurGame() : null; } catch (e) {}
      on = !!(on && g && g.pulse);
      const k = on ? kind(g) : '';
      if (k !== last) { last = k; b.classList.toggle('on', !!k); if (k) b.querySelector('span').textContent = label(k); }
    }, 350);
    b.addEventListener('pointerdown', e => e.stopPropagation()); b.addEventListener('click', e => {
      e.stopPropagation();
      const g = window.__pulseCurGame && window.__pulseCurGame(); if (!g || !g.pulse) return;
      const k = kind(g), box = document.createElement('div'); box.id = 'pulseAsk';
      const msg = k === 'app' ? 'Uninstall this app? Android asks once more before it is removed.' : k === 'html' ? 'Delete this game and its files from the phone?' : 'Take this built-in game off the shelf? It can be brought back in the settings.';
      box.innerHTML = '<div><p><b></b></p><p></p><div class="row"><button type="button" class="no">Cancel</button><button type="button" class="go"></button></div></div>';
      box.querySelector('b').textContent = g.title || ''; box.querySelectorAll('p')[1].textContent = msg; box.querySelector('.go').textContent = label(k);
      box.addEventListener('pointerdown', ev => ev.stopPropagation()); box.addEventListener('click', ev => ev.stopPropagation());
      box.querySelector('.no').onclick = () => box.remove();
      box.querySelector('.go').onclick = () => { box.remove(); try { H.removeGame(String(g.appId)); } catch (x) {} };
      document.body.appendChild(box);
    });
  })();
  // a text box of the page that gets the focus asks Android for its keyboard
  document.addEventListener('focusin', e => { const t = e.target; if (t && /^(INPUT|TEXTAREA)$/.test(t.tagName) && t.type !== 'checkbox' && t.type !== 'range') { try { H.showKeyboard && H.showKeyboard(); } catch (x) {} } }, true);
  // the clipboard, for the settings' on-screen keyboard (an API key copied from a browser)
  api.clipRead = async () => { try { return H.clipRead ? String(H.clipRead() || '') : ''; } catch (e) { return ''; } };
  // Settings on the phone: Game Hub's own settings first; the pages that belong to a PC (consoles and emulators, Steam, magazines, the phone companion,
  // RetroAchievements, Windows' start-up) have nothing to do here, so they are left out instead of opening broken
  window.BS_SETTINGS_PATCH = sections => {
    const gone = new Set(['consoles', 'pc', 'custom', 'apps', 'reading', 'acct', 'phone', 'museum', 'previews', 'art']);
    const out = (sections || []).filter(s => s && !gone.has(s.id));
    const gh = out.find(s => s.id === 'gamehub');
    const sys = out.find(s => s.id === 'sys');
    // System keeps only what runs on a phone: About, the report and the update rows
    if (sys && Array.isArray(sys.rows)) sys.rows = sys.rows.filter(r => r && (r.t === 'head' ? false : /^(About|What.?s new|Report a problem)/i.test(String(r.label || ''))));
    const rest = out.filter(s => s !== gh && !(s === sys && (!sys.rows || !sys.rows.length)));
    if (gh) { gh.g = 'Game Hub'; gh.name = 'Game Hub'; }
    return gh ? [gh, ...rest] : rest;
  };
  api.quit = async () => { H.back(); return true; };
  // the platform's own "exited": Pulse tells the page when the game has closed and Boxshelf is on the screen again
  // the desktop's "run quitting" step (the shelf is covered in black, then comes back): Android has no such event, so it runs
  // here, the moment Pulse is in front again, right before the "exited" step that brings the shelf back
  const onQuit = api.onRunQuitting;
  api.onRunQuitting = fn => { window.__bsQuit = fn; return onQuit ? onQuit(fn) : () => {}; };
  window.__pulseExited = () => {
    try { window.__bsQuit && window.__bsQuit(); } catch (e) {}
    try { window.__bsExit && window.__bsExit({ code: 0 }); } catch (e) {}
  };
  // Details > More > Game settings: Pulse's own page of the game (performance, profile, cover)
  // the search button beside Menu (Game Hub only): finds a game by name and brings the shelf to it
  // the search button beside Menu is Boxshelf's own Find a game (find.js)
  // "Find a cover": the covers the internet has for the game (Steam's, and SteamGridDB's with a key), in the shelf's own look; a tap makes one the game's cover.
  // The name can be typed again when the game is listed under another one.
  api.coverPick = g => {
    if (!g || !H.coverSearch) return;
    let box = document.getElementById('pulseCovers'); if (box) box.remove();
    box = document.createElement('div'); box.id = 'pulseCovers';
    box.style.cssText = 'position:fixed;inset:0;z-index:9000;background:rgba(4,6,10,.94);display:flex;flex-direction:column;padding:max(14px,env(safe-area-inset-top)) 14px 14px;font-family:Lexend,system-ui,sans-serif;color:#fff';
    box.innerHTML = '<div style="display:flex;gap:8px;align-items:center;margin-bottom:12px"><input id="pcQ" type="text" style="flex:1;min-width:0;height:44px;border-radius:12px;border:1px solid #39404d;background:#12151b;color:#fff;padding:0 14px;font:inherit;font-size:16px"><button id="pcGo" type="button" class="btn" style="height:44px;padding:0 16px">Search</button><button id="pcX" type="button" class="btn" style="height:44px;padding:0 16px">Close</button></div><div id="pcNote" style="opacity:.7;font-size:14px;margin-bottom:8px"></div><div id="pcGrid" style="flex:1;overflow:auto;display:grid;grid-template-columns:repeat(auto-fill,minmax(108px,1fr));gap:10px;align-content:start"></div>';
    document.body.appendChild(box);
    const q = box.querySelector('#pcQ'), grid = box.querySelector('#pcGrid'), note = box.querySelector('#pcNote');
    const close = () => { box.remove(); window.__pulseCovers = null; };
    const go = () => { grid.innerHTML = ''; grid.style.opacity = ''; note.textContent = 'Searching…'; H.coverSearch(String(g.appId), q.value.trim()); };
    q.value = g.title || '';
    box.querySelector('#pcX').onclick = close; box.querySelector('#pcGo').onclick = go;
    q.addEventListener('keydown', e => { e.stopPropagation(); if (e.key === 'Enter') go(); }); q.addEventListener('keyup', e => e.stopPropagation());
    box.addEventListener('pointerdown', e => e.stopPropagation()); box.addEventListener('click', e => e.stopPropagation());
    window.__pulseCoverFail = () => { note.textContent = 'That picture could not be downloaded'; grid.style.opacity = ''; };
    window.__pulseCovers = (pkg, list) => {
      if (String(pkg) !== String(g.appId) || !document.body.contains(box)) return;
      grid.innerHTML = ''; let shown = 0; note.textContent = list.length ? '' : 'Nothing found';
      list.forEach(it => {
        const b = document.createElement('button'); b.type = 'button';
        b.style.cssText = 'padding:0;border:0;background:#12151b;border-radius:10px;overflow:hidden;aspect-ratio:2/3;position:relative';
        const im = new Image(); im.loading = 'lazy'; im.alt = it.n || ''; im.style.cssText = 'width:100%;height:100%;object-fit:cover;display:block';
        im.onerror = () => { b.remove(); if (!--shown) note.textContent = 'Nothing found'; };
        im.src = it.t || it.u; b.appendChild(im);
        b.onclick = () => { note.textContent = 'Saving…'; grid.style.opacity = '.4'; H.coverSet(String(g.appId), it.u); };
        grid.appendChild(b); shown++;
      });
    };
    go();
  };
  document.addEventListener('click', e => { const b = e.target.closest && e.target.closest('#dCover'); if (b && window.__pulseCur) api.coverPick(window.__pulseCur); }, true);
  document.addEventListener('click', e => { const b = e.target.closest && e.target.closest('#dPulse'); if (b && window.__pulseCur) api.pulseDetails(window.__pulseCur.appId); }, true);
  // Back (Android's back button): on the bare shelf Pulse leaves it; on any other screen (details, a case in hand, a menu, a list) the page goes one step back
  window.__pulseBack = () => {
    const T = window.BS_TOUCH;
    if (!T || !T.screen || T.screen() === 'shelf') { H.back(); return; }
    T.back();
  };
  const onExit = api.onExited;
  api.onExited = fn => { window.__bsExit = fn; return onExit ? onExit(fn) : () => {}; };
})();
