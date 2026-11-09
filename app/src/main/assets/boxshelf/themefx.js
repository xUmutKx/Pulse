/* Moving backgrounds for the themes, drawn on one canvas behind the shelf.
   - Spectrum: slow ribbons of light and drifting dust in the console's colour
   - Keys: a field of dots rolling like a wave; every note the menu plays sends a ripple across it, coloured by the song
   - Arcade: a pixel starfield over a neon mountain range, with scanlines; notes make stars flare
   - Aurora: curtains of northern lights folding over a starry sky
   - Midnight: true black with a single line of light under the shelf and dust drifting through it
   Drawn at reduced resolution, paused whenever something covers the shelf (a game, the museum, Settings…). */
window.ThemeFx = (() => {
  const app = document.getElementById('app'), cv = document.createElement('canvas');
  cv.className = 'theme-canvas'; cv.setAttribute('aria-hidden', 'true');
  const anchor = document.getElementById('themeFx'); if (anchor) anchor.after(cv); else app.prepend(cv);
  const g = cv.getContext('2d', { alpha: true }), off = document.createElement('canvas'), o = off.getContext('2d');
  let drawMs = 0, id = null, hue = 220, hueT = 220, songHue = 200, W = 0, H = 0, scale = .5, raf = 0, last = 0, t = 0, floorY = .62, lite = false;
  let fdt = 0; const ripples = [], flares = [], MAX_PX = 1920 * 1080 * 1.02;
  const rnd = (a, b) => a + Math.random() * (b - a);
  const hsl = (h, s, l, a = 1) => `hsla(${((h % 360) + 360) % 360},${s}%,${l}%,${a})`;

  function size() {
    lite = !document.body.classList.contains('fx');
    scale = Math.min(1.25, (window.devicePixelRatio || 1)) * (lite ? .7 : 1);
    // Handheld (Android): a phone GPU on a 6" screen. The soft ribbons and dust look the same drawn at about half size and
    // stretched, and it's ~3x fewer pixels to draw and hand to the graphics chip every frame
    if (document.body.classList.contains('perf-handheld')) scale *= .6;
    // a pixel budget (a 1080p screen's worth): on an ultrawide or 4K screen the canvas is drawn smaller and stretched, as its
    // soft ribbons and dust lose nothing by it; at 3440x1440 that's 2.4x fewer pixels to draw and hand over 60 times a second
    const px = innerWidth * innerHeight * scale * scale; if (px > MAX_PX) scale *= Math.sqrt(MAX_PX / px);
    W = Math.max(320, Math.round(innerWidth * scale)); H = Math.max(180, Math.round(innerHeight * scale));
    if (cv.width !== W || cv.height !== H) { cv.width = W; cv.height = H; }
    const f = document.querySelector('.floor'); if (f) { const r = f.getBoundingClientRect(); if (r.height) floorY = Math.min(.8, Math.max(.45, r.top / innerHeight)); }
    seed();
  }
  // per-theme particles, made once per size
  let stars = [], dust = [], peaks = [];
  function seed() {
    stars = Array.from({ length: lite ? 120 : 220 }, () => ({ x: Math.random(), y: Math.random(), z: Math.random(), p: Math.random() * 6.28 }));
    dust = Array.from({ length: lite ? 40 : 80 }, () => ({ x: Math.random(), y: Math.random(), s: rnd(.4, 1.6), v: rnd(.004, .016), p: Math.random() * 6.28 }));
    // a stepped pixel mountain range for Arcade, generated once (two layers)
    peaks = [0, 1].map(() => { const out = []; let y = .4; for (let i = 0; i < 96; i++) { y += rnd(-.16, .16) + (i % 16 < 8 ? .04 : -.04); y = Math.max(.08, Math.min(1, y)); out.push(y); } return out; });
  }

  // ---------------- Spectrum ----------------
  function spectrum() {
    g.globalCompositeOperation = 'lighter';
    for (let k = 0; k < 3; k++) {
      const yb = H * (.2 + k * .14), hh = hue + (k - 1) * 28;
      g.beginPath();
      for (let x = 0; x <= W; x += 8) { const y = yb + Math.sin(x / W * 5 + t * (.16 + k * .05) + k * 2) * H * .06 + Math.sin(x / W * 11 - t * .21 + k) * H * .02; x ? g.lineTo(x, y) : g.moveTo(x, y); }
      g.strokeStyle = hsl(hh, 90, 62, lite ? .07 : .06); g.lineWidth = H * .09; g.stroke();
      g.strokeStyle = hsl(hh, 95, 75, .12); g.lineWidth = H * .012; g.stroke();
      g.strokeStyle = hsl(hh + 10, 100, 88, .18); g.lineWidth = Math.max(1, H * .0025); g.stroke();
    }
    for (const d of dust) {
      d.y -= d.v * fdt; if (d.y < -.02) { d.y = 1.02; d.x = Math.random(); }
      const a = .25 + .25 * Math.sin(t * 1.3 + d.p);
      g.fillStyle = hsl(hue + 20, 90, 80, a * .7); g.beginPath(); g.arc(d.x * W + Math.sin(t * .4 + d.p) * 6, d.y * H, d.s * scale * 2.2, 0, 6.28); g.fill();
    }
  }

  // ---------------- Keys ----------------
  const COLS = 64, ROWS = 24;
  function keys() {
    const hy = H * (floorY - .1);
    // horizon glow in the song's colour
    const gl = g.createLinearGradient(0, hy - H * .25, 0, hy + H * .1); gl.addColorStop(0, hsl(songHue, 90, 50, 0)); gl.addColorStop(.75, hsl(songHue, 90, 55, .28)); gl.addColorStop(1, hsl(songHue, 90, 55, 0));
    g.fillStyle = gl; g.fillRect(0, hy - H * .25, W, H * .35);
    g.globalCompositeOperation = 'lighter';
    const now = t;
    for (let i = ripples.length - 1; i >= 0; i--) if (now - ripples[i].t0 > 3.2) ripples.splice(i, 1);
    for (let r = 0; r < ROWS; r++) {
      const z = r / (ROWS - 1), persp = .18 + z * z * .95, rowY = hy + (H * 1.08 - hy) * Math.pow(z, 1.6);
      const span = W * (.75 + persp * 1.1), x0 = (W - span) / 2;
      for (let c = 0; c < COLS; c++) {
        const u = c / (COLS - 1);
        let h = Math.sin(u * 9 + now * .9 + z * 3.2) * .6 + Math.exp(-Math.pow(((u * 1.6 - now * .08) % 1.6 + 1.6) % 1.6 - .8, 2) * 30) * .5 + Math.sin(u * 3.4 - now * .55 + z * 1.4) * .35 + Math.sin((u + z) * 15 + now * 1.6) * .08, lit = 0, rh = 0;
        for (const rp of ripples) { const dx = (u - rp.u) * 3.2, dz = (z - .55) * 1.6, d = Math.sqrt(dx * dx + dz * dz), age = now - rp.t0, front = age * .9, e = Math.exp(-((d - front) ** 2) * 40) * rp.v * Math.max(0, 1 - age / 3.2); h += e * 1.6; lit += e; rh = rp.hue; }
        const y = rowY - h * H * .04 * persp * 2.2, s = Math.min(H * .011, Math.max(.8, (1 + persp * 2.2 + lit * 1.4) * scale * 1.5));
        const clear = z > .35 ? 1 - .75 * Math.exp(-Math.pow((u - .5) / .16, 2)) : 1; // keep the game's name readable
        const hh = (lit > .05 ? rh : songHue) + (u - .5) * 90 + h * 16, l = 52 + h * 10 + lit * 18, a = Math.min(.95, ((.3 + z * .5) * (.7 + h * .3) + lit * .6) * clear);
        if (a < .03) continue;
        g.globalAlpha = a; g.fillStyle = keyCol(hh, Math.min(74, l)); g.beginPath(); g.arc(x0 + u * span, y, s / 2, 0, 6.28); g.fill();
      }
    }
    g.globalAlpha = 1;
  }
  // the dots' colours, by whole degree and percent (alpha goes through globalAlpha), so no new string per dot per frame
  const keyCols = new Map();
  function keyCol(h, l) {
    const k = ((Math.round(h) % 360) + 360) % 360 * 128 + Math.max(0, Math.min(127, Math.round(l)));
    let c = keyCols.get(k); if (!c) { c = `hsl(${k >> 7},90%,${k & 127}%)`; keyCols.set(k, c); } return c;
  }
  function note(f, v, h) {
    if ((window.Perf && !Perf.get().themeFps) || document.body.classList.contains('reduce-motion')) return; // still picture (Low / Reduce motion): a ripple would only freeze in place
    const u = Math.max(0, Math.min(1, Math.log2(f / 110) / 5));
    ripples.push({ u, t0: t, v: Math.min(1, .45 + v), hue: h != null ? h + (u - .5) * 60 : songHue }); if (ripples.length > 14) ripples.shift();
    flares.push({ x: Math.random(), y: rnd(.04, .5), t0: t, h: h != null ? h : hue }); if (flares.length > 10) flares.shift();
  }

  // ---------------- Arcade ----------------
  function arcade() {
    const hy = H * (floorY - .04), px = Math.max(2, Math.round(H / 180));
    g.globalCompositeOperation = 'lighter';
    for (const s of stars) {
      s.x -= (.004 + s.z * .02) * fdt; if (s.x < 0) { s.x = 1; s.y = Math.random(); }
      if (s.y * H > hy - px * 4) continue;
      const tw = .5 + .5 * Math.sin(t * (2 + s.z * 4) + s.p), sz = px * (s.z > .85 ? 2 : 1);
      g.fillStyle = s.z > .9 ? hsl(s.p > 3 ? 190 : 315, 100, 78, .5 + tw * .5) : `rgba(235,240,255,${.2 + tw * .55 * s.z})`;
      g.fillRect(Math.round(s.x * W / px) * px, Math.round(s.y * H / px) * px, sz, sz);
    }
    for (let i = flares.length - 1; i >= 0; i--) { const f = flares[i], a = 1 - (t - f.t0) / .6; if (a <= 0) { flares.splice(i, 1); continue; } const cx = Math.round(f.x * W / px) * px, cy = Math.round(f.y * (hy - px * 10) / px) * px, r = px * (1 + (1 - a) * 3);
      g.fillStyle = hsl(f.x > .5 ? 190 : 315, 100, 80, a); g.fillRect(cx - r, cy - px / 2, r * 2 + px, px); g.fillRect(cx, cy - r, px, r * 2 + px); }
    g.globalCompositeOperation = 'source-over';
    // two pixel mountain ranges drifting at different speeds: dark violet, outlined in magenta (near) and cyan (far)
    [[1, .09, 5, 275, 190], [0, .15, 2.5, 285, 315]].forEach(([layer, amp0, speed, fillH, lineH]) => {
      const pk = peaks[layer], col = Math.max(px * 2, Math.round(W / 110 / px) * px), n = Math.ceil(W / col) + 2, amp = H * amp0, shift = t * speed, first = Math.floor(shift / col), off = shift % col;
      g.beginPath(); g.moveTo(0, hy);
      for (let i = 0; i < n; i++) { const x = i * col - off, v = pk[(first + i) % pk.length], y = hy - Math.round(v * amp / px) * px; g.lineTo(x, y); g.lineTo(x + col, y); }
      g.lineTo(W, hy); g.closePath();
      const fg = g.createLinearGradient(0, hy - amp, 0, hy); fg.addColorStop(0, hsl(fillH, 55, layer ? 14 : 10)); fg.addColorStop(1, hsl(fillH, 60, layer ? 6 : 4));
      g.fillStyle = fg; g.fill(); g.strokeStyle = hsl(lineH, 100, 65, layer ? .45 : .8); g.lineWidth = px; g.stroke();
    });
    // neon horizon
    const gl = g.createLinearGradient(0, hy - px * 2, 0, hy + H * .1); gl.addColorStop(0, hsl(315, 100, 65, .45)); gl.addColorStop(1, hsl(315, 100, 50, 0));
    g.fillStyle = gl; g.fillRect(0, hy - px, W, H * .1);
  }

  // ---------------- Aurora ----------------
  function aurora() {
    g.globalCompositeOperation = 'lighter';
    for (const s of stars) { if (s.y > .62) continue; const tw = .45 + .55 * Math.sin(t * (1 + s.z * 2) + s.p); g.fillStyle = `rgba(220,235,255,${(.15 + s.z * .5) * tw})`; g.fillRect(s.x * W, s.y * H, s.z > .9 ? 2 : 1, s.z > .9 ? 2 : 1); }
    // the curtains are drawn small and stretched up: the upscale softens them like real aurora, for free
    const ow = Math.max(80, W >> 2), oh = Math.max(45, H >> 2); if (off.width !== ow || off.height !== oh) { off.width = ow; off.height = oh; }
    o.setTransform(1, 0, 0, 1, 0, 0); o.globalCompositeOperation = 'source-over'; o.clearRect(0, 0, ow, oh); o.globalCompositeOperation = 'lighter';
    // the curtains take the console's colour (green on Xbox, red on Switch…), with a lighter and a complementary band
    [[hue, .16, 1.1, 0], [hue + 25, .22, .9, 2.1], [hue + 120, .1, .85, 4.2]].forEach(([h0, yb, amp, ph]) => {
      const gr = o.createLinearGradient(0, 0, 0, 1);
      gr.addColorStop(0, hsl(h0 + 90, 80, 60, 0)); gr.addColorStop(.35, hsl(h0 + 40, 85, 60, .7)); gr.addColorStop(.85, hsl(h0, 95, 58, 1)); gr.addColorStop(1, hsl(h0, 95, 58, 0));
      o.fillStyle = gr;
      for (let x = 0; x < ow; x++) {
        const u = x / ow, fold = Math.sin(u * 6 + t * .12 + ph) * .5 + Math.sin(u * 13 - t * .19 + ph * 2) * .25;
        const top = oh * (yb + fold * .05), len = oh * (.16 + .1 * Math.sin(u * 4 + t * .09 + ph)) * amp;
        const ray = Math.pow(.5 + .5 * Math.sin(u * 55 + t * .5 + ph * 3 + Math.sin(u * 17 + t * .25) * 3), 3);
        o.globalAlpha = Math.max(0, (.1 + ray * .22) * amp * (.6 + .4 * Math.sin(t * .23 + u * 3 + ph)));
        o.setTransform(1, 0, 0, len, x, top); o.fillRect(0, 0, 1, 1);
      }
      o.setTransform(1, 0, 0, 1, 0, 0); o.globalAlpha = 1;
    });
    g.imageSmoothingEnabled = true; g.imageSmoothingQuality = 'high'; g.drawImage(off, 0, 0, W, H);
    const gl = g.createLinearGradient(0, H * (floorY - .08), 0, H * floorY + H * .05); gl.addColorStop(0, hsl(hue, 100, 60, 0)); gl.addColorStop(1, hsl(hue, 100, 60, .07)); g.fillStyle = gl; g.fillRect(0, H * (floorY - .08), W, H * .13);
  }

  // ---------------- Midnight ----------------
  function midnight() {
    const ly = H * floorY;
    g.globalCompositeOperation = 'lighter';
    const breathe = .85 + .15 * Math.sin(t * .5);
    const gw = g.createRadialGradient(W / 2, ly, 0, W / 2, ly, W * .5); gw.addColorStop(0, hsl(hue, 90, 55, .22 * breathe)); gw.addColorStop(1, hsl(hue, 90, 40, 0));
    g.save(); g.translate(W / 2, ly); g.scale(1, .16); g.translate(-W / 2, -ly); g.fillStyle = gw; g.fillRect(0, ly - W * .5, W, W); g.restore();
    const ln = g.createLinearGradient(0, 0, W, 0); ln.addColorStop(0, hsl(hue, 90, 60, 0)); ln.addColorStop(.5, hsl(hue, 80, 85, .9 * breathe)); ln.addColorStop(1, hsl(hue, 90, 60, 0));
    g.fillStyle = ln; g.fillRect(W * .08, ly - Math.max(1, H * .0018), W * .84, Math.max(1.2, H * .0035));
    // dust: brightest as it passes through the light
    for (const d of dust) {
      d.y -= d.v * fdt * .625; d.x += Math.sin(t * .3 + d.p) * .0048 * fdt; if (d.y < .25) { d.y = .98; d.x = Math.random(); }
      const near = Math.exp(-Math.pow((d.y - floorY) * 9, 2)), cen = 1 - Math.abs(d.x - .5) * 1.6;
      const a = Math.max(0, (.06 + near * .7) * cen); if (a < .02) continue;
      g.fillStyle = hsl(hue, 70, 85, a); g.beginPath(); g.arc(d.x * W, d.y * H, d.s * scale * 1.6, 0, 6.28); g.fill();
    }
  }

  const DRAW = { spectrum, keys, arcade, aurora, midnight };
  // Android's extra themes (android-themes.js; none on Windows) bring their own frame, drawn on this canvas with this
  // layer's clock and pauses. At the Handheld level they hold one still frame (redrawn only when the theme, colour or size
  // changes), so on the Thor they never keep a frame loop going
  const XFX = {}; let xStill = false, xKey = '';
  for (const x of (window.BS_EXTRA_THEMES || [])) if (x && x.id && typeof x.fx === 'function' && !DRAW[x.id]) {
    XFX[x.id] = x; DRAW[x.id] = () => x.fx({ g, W, H, t, dt: fdt, hue, scale, floorY, lite, still: xStill });
  }
  const setupEl = document.getElementById('setup');
  const covered = () => app.classList.contains('launching') || app.classList.contains('inspecting') || app.classList.contains('museum-open') || app.classList.contains('attract')
    || app.classList.contains('details-open') || app.classList.contains('settings-open')
    || document.body.classList.contains('idle-bg') || document.body.classList.contains('bs-still') || document.body.classList.contains('dimmed') || (document.body.classList.contains('bss-cover') && !document.body.classList.contains('bss-peek')) || app.classList.contains('bg-art') || document.hidden || !!(setupEl && setupEl.classList.contains('on'));
  // while covered the loop stops altogether (no polling); a class change or the window showing again starts it back up.
  // `still`: the picture changed (theme switch, resize) while covered, so draw it once, then hold
  let still = false;
  const ANDROID = !!(window.bsPlatform && window.bsPlatform.os === 'android');
  function draw(dt) {
    t += dt; fdt = dt; hue += (hueT - hue) * Math.min(1, dt * 1.5); // fdt: the particles move per second, whatever the draw rate (60 on High, 30 on Balanced)
    g.globalCompositeOperation = 'source-over'; g.clearRect(0, 0, W, H);
    const d0 = performance.now(); try { DRAW[id](); } catch (e) { console.warn('themefx', e); id = null; } drawMs = drawMs * .9 + (performance.now() - d0) * .1;
    g.globalCompositeOperation = 'source-over'; g.globalAlpha = 1;
  }
  function frame(ms) {
    raf = 0;
    if (!id || !DRAW[id]) return;
    if (XFX[id]) { xStill = covered() || document.body.classList.contains('perf-handheld') || document.body.classList.contains('reduce-motion') || !!(window.Perf && !Perf.get().themeFps);
      if (xStill) { last = ms; hue = hueT; const k = id + '|' + hueT + '|' + W + 'x' + H; if (still || k !== xKey) { still = false; xKey = k; draw(0); } return; } xKey = ''; }
    if (covered()) { if (still) { still = false; draw(0); } last = ms; return; }
    const fps = document.body.classList.contains('reduce-motion') ? 0 : Math.min(lite ? 30 : 60, window.Perf ? Perf.get().themeFps : 60); // Reduce motion: a still picture, like the Low level
    if (!fps) { last = ms; hue = hueT; draw(0); return; } // Low performance level: a still picture, drawn again only when the theme, colour or size changes
    // Android at 30 fps: after a drawn frame the next one is asked for from a timer that waits out the frame in between, so
    // only the frames it draws in are requested. Asking for every frame and skipping every other one made the page restyle
    // and commit a frame 60 times a second (a whole frame of the page's work for nothing, every other vsync)
    if (ANDROID && fps < 60 && ms - last >= 1000 / fps - 2) {
      const dt = Math.min(.1, (ms - last) / 1000); last = ms; still = false; draw(dt);
      raf = -1; setTimeout(() => { if (raf === -1) raf = requestAnimationFrame(frame); }, Math.max(0, ms + 1000 / fps - 1000 / 60 + 4 - performance.now())); return; // (asked for just after the vsync in between has gone by, so it lands on the one after)
    }
    raf = requestAnimationFrame(frame); if (ms - last < 1000 / fps - 2) return;
    const dt = Math.min(.1, (ms - last) / 1000); last = ms; still = false; draw(dt);
  }
  const wake = () => { if (id && !raf && !covered()) raf = requestAnimationFrame(frame); };
  const mo = new MutationObserver(wake);
  mo.observe(app, { attributes: true, attributeFilter: ['class'] }); mo.observe(document.body, { attributes: true, attributeFilter: ['class'] });
  if (setupEl) mo.observe(setupEl, { attributes: true, attributeFilter: ['class'] });
  document.addEventListener('visibilitychange', wake);
  function set(theme, o = {}) {
    if (o.hue != null) hueT = o.hue; const was = id; id = DRAW[theme] ? theme : null;
    cv.style.display = id ? 'block' : 'none'; cv.dataset.theme = id || '';
    if (id && !was) { hue = hueT; size(); }
    if (id && id !== was) still = true;
    if (id && !raf) raf = requestAnimationFrame(frame);
    if (!id && raf) { cancelAnimationFrame(raf); raf = 0; g.clearRect(0, 0, W, H); }
  }
  addEventListener('resize', () => { if (id) { size(); still = true; if (!raf) raf = requestAnimationFrame(frame); } });
  addEventListener('bs-perf', () => { if (id) { size(); still = true; if (!raf) raf = requestAnimationFrame(frame); } }); // the canvas size follows the Performance level (Handheld draws it smaller)
  addEventListener('bs-note', e => { if (id === 'keys' || id === 'arcade') { const d = e.detail || {}; note(d.f || 440, d.v || .3, d.hue); } });
  addEventListener('bs-song', e => { const d = e.detail || {}; if (d.hue != null) { songHue = d.hue; wake(); } });
  return { set, refresh: () => { size(); still = true; if (id && !raf) raf = requestAnimationFrame(frame); }, _state: () => ({ id, W, H, ripples: ripples.length, t, ms: +drawMs.toFixed(2) }) };
})();
