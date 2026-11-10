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
    hidden: !!g.hidden, minutes: Math.round((g.playMs || 0) / 60000), lastPlayed: g.lastUsed ? g.lastUsed / 1000 : 0,
    // the picture of a game is its cover from the internet (Steam, SteamGridDB, Google Play) or its wide art; the app's icon is never used as the cover
    // appIcon is only for the title line (not 'icon': the shelf builds a cover from that one when there is none)
    art: Object.assign({}, g.cover || g.hero ? { front: g.cover || g.hero } : {}, g.hero ? { hero: g.hero } : {}, g.icon ? { appIcon: g.icon } : {}),
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
      am.textContent = 'html,body,#app{background:#0d1017!important;background-image:none!important}#app::before,#app::after,body::before,body::after,.theme-canvas,.bg-layer,.theme-fx,.vignette,.sel-halo,.floor,.pv-back{display:none!important;background:none!important}';
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
  api.quit = async () => { H.back(); return true; };
  // the platform's own "exited": Pulse tells the page when the game has closed and Boxshelf is on the screen again
  window.__pulseExited = () => { try { window.__bsExit && window.__bsExit({ code: 0 }); } catch (e) {} };
  // Details > More > Game settings: Pulse's own page of the game (performance, profile, cover)
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
