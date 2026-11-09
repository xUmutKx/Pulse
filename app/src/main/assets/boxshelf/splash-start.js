// Hands the start-up logo to its own thread (see splash-worker.js). Until that thread has drawn it, the page's own
// copy (same look, standing still) shows, so there's never a blank moment. Without OffscreenCanvas the page copy stays.
(function () {
  try {
    const sp = document.getElementById('splash'); if (!sp || document.documentElement.classList.contains('no-splash')) return;
    const c = document.createElement('canvas'); if (!c.transferControlToOffscreen || !window.Worker) return;
    const w = Math.min(1100, innerWidth * .92), h = 260, fs = Math.max(34, Math.min(70, innerWidth * .042));
    c.className = 'sp-canvas'; c.style.width = w + 'px'; c.style.height = h + 'px'; sp.appendChild(c);
    const off = c.transferControlToOffscreen(), wk = new Worker('splash-worker.js');
    window.__splash = { worker: wk, ready: false, done: null, onDone: null };
    wk.onmessage = e => { const d = e.data; if (d.ready) { window.__splash.ready = true; sp.classList.add('cv'); } if (d.done) { window.__splash.done = d; if (window.__splash.onDone) window.__splash.onDone(d); } };
    wk.postMessage({ canvas: off, w, h, fs, dpr: Math.min(2, devicePixelRatio || 1) }, [off]);
  } catch (e) {}
})();
