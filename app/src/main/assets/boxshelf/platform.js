// Which platform the shelf is running on, for the rest of the renderer.
// window.bsPlatform = { os: 'windows' | 'android' | 'web', caps: {…}, has(cap) }
// The page also gets class platform-<os>, plus no-<cap> for every capability the platform lacks, and any element
// marked data-needs="cap" (space-separated) is hidden when a cap is missing. Windows has every cap, so nothing changes there.
(function () {
  const src = (window.boxshelf && window.boxshelf.platform) || { os: 'web', caps: {} };
  const os = String(src.os || 'web');
  const caps = Object.freeze(Object.assign({}, src.caps));
  // Settings → which pages and rows need a capability (anything not listed is kept everywhere)
  const PAGE_NEEDS = { pc: 'steam', apps: 'steam', custom: 'steam', phone: 'companionServer' } /* (beta.4) custom: the Custom games page (Windows programs) */;
  const ROW_NEEDS = { 'Start Boxshelf with Windows': 'autostart', 'App folder': 'folders', 'Restore emulator settings': 'emuSettings', 'Home menu': 'homeMenu', 'Home menu layout': 'homeMenu', 'Hide the taskbar during games': 'winMode' /* (beta.6) Windows only */ };
  const okRow = r => !(r && typeof r === 'object' && ROW_NEEDS[r.label] && !caps[ROW_NEEDS[r.label]]);
  const pruneRows = rows => Array.isArray(rows) ? rows.filter(okRow).map(r => {
    if (r && typeof r === 'object') for (const k of Object.keys(r)) if (Array.isArray(r[k]) && r[k].some(x => x && typeof x === 'object' && 'label' in x)) r[k] = pruneRows(r[k]);
    return r; }) : rows;
  const filterSections0 = list => (list || []).filter(s => !(s && PAGE_NEEDS[s.id] && !caps[PAGE_NEEDS[s.id]])).map(s => (s && Array.isArray(s.rows) ? Object.assign(s, { rows: pruneRows(s.rows) }) : s));
  // a platform adapter may reword or swap a few rows for what it does differently (Android's box art); Windows sets none
  // (helpers: Settings' own row makers, e.g. bind(path, default) for a switch stored in the config)
  const filterSections = (list, helpers) => { const out = filterSections0(list);
    // (beta.2) only Android's own adapter: the Android build's demo page also runs as Windows (?platform=windows, the
    // device test), and must show Windows' Settings there, not the handheld's regrouped one
    if (typeof window.BS_SETTINGS_PATCH !== 'function' || os !== 'android') return out;
    try { return window.BS_SETTINGS_PATCH(out, helpers || {}) || out; } catch (e) { console.error('settings patch', e); return out; } };
  window.bsPlatform = Object.freeze({ os, caps, has: k => !!caps[k], filterSections });
  const root = document.documentElement, missing = Object.keys(caps).filter(k => !caps[k]);
  root.classList.add('platform-' + os);
  for (const k of missing) root.classList.add('no-' + k);
  if (missing.length) {
    const st = document.createElement('style');
    st.textContent = missing.map(k => `[data-needs~="${k}"]`).join(',') + '{display:none!important}';
    document.head.appendChild(st);
  }
})();
