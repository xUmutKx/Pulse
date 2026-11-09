// Input gate: this file is the first script on the page, so these capture listeners run before any other screen's own
// (the shelf, Settings, the quick menu…). app.js sets window.BS_INPUT_GATE; when it returns true the input is used up
// (the key / click that wakes a dimmed screen, the Undo key after hiding a game) and nothing else sees it.
['keydown', 'keyup', 'pointerdown', 'click', 'wheel'].forEach(t => addEventListener(t, e => {
  const g = window.BS_INPUT_GATE; if (!g) return;
  let used = false; try { used = g(e); } catch {}
  if (used) { e.stopImmediatePropagation(); if (e.cancelable) e.preventDefault(); }
}, { capture: true, passive: false }));
// Idle-time jobs (the museum's shader warm-up, Settings' pre-build, the reverb tail, generated icons…) also wait while
// the shelf is moving: Chromium counts the gaps between animation frames as idle, so a 50–200 ms step used to land
// mid-scroll and drop frames. app.js sets window.BS_BUSY (true while cases are sliding); a job asked for, or about to run,
// then waits for a quiet moment (at most ~15 s, so a controller held down for ages can't starve it).
(() => {
  const ric = window.requestIdleCallback; if (!ric) return;
  const busy = () => { try { return !!(window.BS_BUSY && window.BS_BUSY()); } catch { return false; } };
  const wrap = (cb, o, t0) => dl => { if (busy() && performance.now() - t0 < 15000) { setTimeout(() => ric.call(window, wrap(cb, o, t0), o), 250); return; } cb(dl); };
  window.requestIdleCallback = (cb, o) => ric.call(window, wrap(cb, o, performance.now()), o);
})();
// Performance level: how much the shelf and the museum ask of the graphics card. "auto" looks at the PC once (graphics
// chip, cores, memory) and picks High, Balanced or Low; the museum then also keeps its frame rate by rendering at a
// slightly lower resolution while frames run slow (and back up when there's room). High is the full look.
window.Perf = (() => {
  const LEVELS = {
    //        render scale cap, anti-aliasing, shadows, shadow map, texture filtering (kept at 8 everywhere: lower makes the narrow spines unreadable), museum dust, theme fps, blur, halo pulse, inspect scale cap
    high:     { pr: 1.25, aa: true,  shadows: true,  shadowSize: 1024, aniso: 8, dust: true,  themeFps: 60, blur: true,  halo: true,  inspectPr: 2 },
    balanced: { pr: 1,    aa: true,  shadows: true,  shadowSize: 1024,  aniso: 8, dust: true,  themeFps: 30, blur: true,  halo: true,  inspectPr: 1.5 },
    // handheld (Android, e.g. the AYN Thor): a phone GPU on a small, dense screen. The shelf's effects mode is off (no case
    // reflection, lighter case layers), museum shadows and dust off, theme at 30 fps, Inspect at the screen's real resolution,
    // and the disc's clear plastic drawn as plain see-through (glass: real transmission costs a whole extra render pass per frame)
    handheld: { pr: 1,    aa: true,  shadows: false, shadowSize: 512,  aniso: 8, dust: false, themeFps: 30, blur: false, halo: false, inspectPr: 1.34, glass: false },
    low:      { pr: .8,   aa: false, shadows: false, shadowSize: 512,  aniso: 8, dust: false, themeFps: 0,  blur: false, halo: false, inspectPr: 1 }
  };
  let pref = 'auto', gpu = '', guess = null;

  // what kind of PC this is: read once, from a throwaway WebGL context
  function detect() {
    if (guess) return guess; // once per run: a WebGL context is costly to make and to throw away (Perf.get() is called every frame)
    // Android: Handheld on any real GPU (the desktop tiers assume a PC driving a TV); software rendering still means Low
    const os = (window.bsPlatform && window.bsPlatform.os) || (window.boxshelf && window.boxshelf.platform && window.boxshelf.platform.os) || '';
    let tier = 'high', why = [];
    try {
      const c = document.createElement('canvas'), gl = c.getContext('webgl', { powerPreference: 'high-performance' }) || c.getContext('experimental-webgl'); // the same chip the museum asks for (laptops with two)
      if (gl) { const ext = gl.getExtension('WEBGL_debug_renderer_info'); gpu = String(ext ? gl.getParameter(ext.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER) || ''); const lose = gl.getExtension('WEBGL_lose_context'); if (lose) lose.loseContext(); }
      else { tier = 'low'; why.push('no WebGL'); }
    } catch { tier = 'low'; why.push('no WebGL'); }
    const g = gpu.toLowerCase();
    if (/swiftshader|llvmpipe|softpipe|basic render|microsoft basic|software/.test(g)) { tier = 'low'; why.push('software rendering'); }
    else if (os === 'android' && tier !== 'low') { guess = { tier: 'handheld', why: 'Android handheld', gpu }; return guess; } // Android handhelds start at Handheld: the desktop tiers assume a PC driving a TV
    else if (/vangogh|custom gpu 0(405|932)/.test(g)) { tier = 'balanced'; why.push('Steam Deck graphics'); } // (beta.10) the Deck's APU (LCD 0405, OLED 0932): a PC handheld, so Balanced (Handheld is tuned for Android phones' GPUs)
    else if (/intel/.test(g) && !/iris|arc|\bxe\b/.test(g)) { tier = 'low'; why.push('Intel HD/UHD graphics'); }
    else if (/iris|radeon\(tm\) graphics|radeon graphics|vega \d+ graphics|radeon r[2-7]\b|geforce mx|mx\s?\d{3}|adreno|mali|apple m1(?! (pro|max|ultra))\b|\bxe\b/.test(g)) { tier = 'balanced'; why.push('integrated graphics'); }
    const cores = navigator.hardwareConcurrency || 4, mem = navigator.deviceMemory || 8;
    const cap = t => { if (tier === 'high' || (tier === 'balanced' && t === 'low')) tier = t; };
    if (cores <= 2) { cap('low'); why.push(cores + ' cores'); } else if (cores <= 4) { cap('balanced'); why.push(cores + ' cores'); }
    if (mem <= 2) { cap('low'); why.push(mem + ' GB memory'); } else if (mem <= 4) { cap('balanced'); why.push(mem + ' GB memory'); }
    guess = { tier, why: why.join(', ') || 'dedicated graphics', gpu };
    return guess;
  }
  const level = () => pref === 'auto' ? detect().tier : (LEVELS[pref] ? pref : 'high');
  const get = () => ({ ...LEVELS[level()], level: level(), auto: pref === 'auto' });
  let applied = null;
  function apply() {
    const L = level(), b = document.body; if (!b) return; if (applied === pref + '|' + L) return; applied = pref + '|' + L; // nothing changed: no event (a refresh mustn't reset the museum)
    Object.keys(LEVELS).forEach(k => b.classList.toggle('perf-' + k, k === L));
    window.dispatchEvent(new CustomEvent('bs-perf', { detail: get() }));
  }
  function set(p) { pref = LEVELS[p] || p === 'auto' ? p : 'auto'; apply(); }
  return { set, get, detect, pref: () => pref, levels: Object.keys(LEVELS) };
})();
