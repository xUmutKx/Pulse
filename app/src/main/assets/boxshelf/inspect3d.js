/* Inspect in real 3D: a lit, reflective game case you can turn in your hands, open, and take the disc
   (or cartridge / UMD) out of. Built with three.js; falls back to the CSS version if WebGL isn't there.
   Optional models: themes\<system>\cart.glb (Switch) or disc.glb / umd.glb replace the built-in media. */
window.Inspect3D = (() => {
  const T = () => window.BS3D && window.BS3D.THREE;
  let renderer, scene, camera, canvas, env, rot, caseG, lidPivot, media, mediaG, manualPlane, spineM = null;
  let active = false, raf = 0, failed = false, tex = new Map(), cur = null, customMedia = null, customUrl = null;
  const st = { state: 0, lid: 0, lidT: 0, out: 0, outT: 0, bump: 0, spin: 0, rx: 0, ry: 0, zoom: 1, px: 0, py: 0, in: 0, lid0: 0, out0: 0, flip: 0, base: 0 };
  // The hand-off between the shelf and this canvas. phase: 'idle' | 'wait' (built, art decoding: the shelf box still shows) |
  // 'in' (flying out of the slot) | 'shown' | 'out' (flying back into the slot) | 'fade' (a quick cross-fade out instead).
  // tok tags each open so late promises from an earlier one do nothing; pend = an open waiting for the last case to fade.
  let phase = 'idle', tok = 0, pend = null, ready = false, fadeT0 = 0, fadeFrom = 1, target = null, onDone = null, cvRect = null, coverMat = null, standInTex = null;
  const FADE_MS = 140;
  const DEG = Math.PI / 180;

  function available() { return !failed && !!T() && !!ensure(); }
  function ensure() {
    if (renderer) return true;
    if (failed) return false;
    const THREE = T(); if (!THREE) return false;
    try {
      canvas = document.createElement('canvas'); canvas.className = 'isp-gl';
      renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true, powerPreference: 'high-performance' });
    } catch (e) { failed = true; setTimeout(() => { failed = false; }, 30000); return false; } /* (beta.12: tried again in 30 s, not off until a restart) */
    // Neutral tone mapping keeps the cover's colours as printed (ACES lifted and desaturated them into a milky wash);
    // only real highlights get compressed. Lights are balanced so a white sleeve facing you sits at ~1.0.
    renderer.outputColorSpace = THREE.SRGBColorSpace; renderer.toneMapping = THREE.NeutralToneMapping; renderer.toneMappingExposure = 1;
    scene = new THREE.Scene();
    const pm = new THREE.PMREMGenerator(renderer); env = pm.fromScene(new window.BS3D.RoomEnvironment(), .04).texture; pm.dispose(); scene.environment = env; scene.environmentIntensity = .45;
    camera = new THREE.PerspectiveCamera(30, 1, .2, 12); /* a tight depth range: more depth precision for the case's thin walls */ camera.position.set(0, 0, 3.9); camera.lookAt(0, 0, 0);
    const key = new THREE.DirectionalLight(0xffffff, 1.55); key.position.set(2, 3, 4); scene.add(key);
    const rim = new THREE.DirectionalLight(0xb9c6ff, .55); rim.position.set(-3, 1, -2); scene.add(rim);
    const fill = new THREE.DirectionalLight(0xfff3ea, .6); fill.position.set(-2, -1, 3); scene.add(fill);
    rot = new THREE.Group(); scene.add(rot);
    caseG = new THREE.Group(); rot.add(caseG);
    mediaG = new THREE.Group(); rot.add(mediaG);
    const stage = document.querySelector('#inspect .isp-stage'); if (stage) stage.appendChild(canvas);
    // If the GPU drops our context (it can while a game runs fullscreen), throw everything away and rebuild on the next open.
    canvas.addEventListener('webglcontextlost', e => { e.preventDefault(); lost = true; });
    return true;
  }
  let lost = false;
  function teardown() {
    try { cancelAnimationFrame(raf); raf = 0; if (renderer) { renderer.dispose(); renderer.forceContextLoss && renderer.forceContextLoss(); } } catch {}
    try { canvas && canvas.remove(); } catch {}
    tex.forEach(t => { try { t.dispose(); } catch {} }); tex = new Map(); mediaSeq++;
    genTex.forEach(t => { try { t.dispose(); } catch {} }); genTex.clear();
    renderer = scene = camera = canvas = env = rot = caseG = lidPivot = media = mediaG = manualPlane = null; cur = null; customMedia = null; customUrl = null; lost = false;
    coverMat = null; if (standInTex) { try { standInTex.dispose(); } catch {} standInTex = null; } phase = 'idle'; pend = null; fadeT0 = 0; target = null; tok++;
    const cb = onDone; onDone = null; if (cb) try { cb(true); } catch {} // a box still hidden for a flight that can't finish now comes back
  }
  function resize() {
    const w = innerWidth, h = innerHeight;
    const pr0 = Math.min((devicePixelRatio || 1) * (document.body.classList.contains('fx') ? 1.5 : 1), (document.body.classList.contains('fx') ? 2 : 1.5) * (window.BS_AUTOZOOM || 1), (window.Perf ? Perf.get().inspectPr : 2) * (window.BS_AUTOZOOM || 1));
    // the supersampling has a budget (a 1080p screen's 1.5x): on an ultrawide or 4K screen it would draw 11+ million pixels a
    // frame, up to 240 times a second. Never below the screen's own pixels, so the art stays as sharp as the panel shows it
    renderer.setPixelRatio(Math.max(Math.min(pr0, devicePixelRatio || 1), Math.min(pr0, Math.sqrt(1920 * 1080 * 2.25 / Math.max(1, w * h))))); /* with effects on, a little supersampling: the case's glossy edges and light glints stop shimmering as it turns */ // one object on screen: render at the panel's real pixels so the art stays sharp
    renderer.setSize(w, h, false); camera.aspect = w / h; camera.updateProjectionMatrix();
    cvRect = canvas.classList.contains('on') ? canvas.getBoundingClientRect() : null;
  }
  // a point on screen (client px, e.g. a shelf box's rect) in the canvas's own px: the canvas sits inside #app, which
  // Fit to TV scales, so the two differ when the picture is shrunk
  function local(f) { const r = cvRect, kx = r && r.width ? innerWidth / r.width : 1, ky = r && r.height ? innerHeight / r.height : 1;
    return { x: (f.x - (r ? r.left : 0)) * kx, y: (f.y - (r ? r.top : 0)) * ky, h: f.h * ky, ry: f.ry || 0 }; }
  function loadTex(url) {
    if (!url) return null;
    if (tex.has(url)) { const t = tex.get(url); tex.delete(url); tex.set(url, t); return t; } // used again: last in line to be freed
    const THREE = T(), t = new THREE.Texture();
    t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = renderer.capabilities.getMaxAnisotropy(); t.userData.cached = true;
    // decoded off the main thread (img.decode) before three gets it, so its upload never stops to decode a 1536px scan
    // mid-animation; userData.p resolves (true = it has its picture) once that's done, userData.ok says so afterwards
    t.addEventListener('dispose', () => { t.userData.gone = true; });
    t.userData.p = new Promise(res => { const im = new Image(); im.crossOrigin = 'anonymous'; im.decoding = 'async';
      im.onload = () => Promise.resolve(im.decode && im.decode()).catch(() => {}).then(() => { if (t.userData.gone) return res(false); t.image = im; t.needsUpdate = true; t.userData.ok = true; res(true); });
      im.onerror = () => res(false); im.src = url; });
    tex.set(url, t);
    if (tex.size > 14) { const [k, old] = tex.entries().next().value; tex.delete(k); try { old.dispose(); } catch {} } // keep the last few games' art on the GPU, not everything ever opened
    return t;
  }
  // Spine text drawn into a texture when there's no spine art
  function spineTexture(title, bg, ink) {
    const THREE = T(); const c = document.createElement('canvas'); c.width = 128; c.height = 1024; const x = c.getContext('2d');
    x.fillStyle = bg; x.fillRect(0, 0, 128, 1024);
    x.save(); x.translate(64, 512); x.rotate(-Math.PI / 2); x.fillStyle = ink; x.font = '600 56px Lexend, system-ui, sans-serif'; x.textAlign = 'center'; x.textBaseline = 'middle';
    let s = title; while (x.measureText(s).width > 900 && s.length > 4) s = s.slice(0, -2); if (s !== title) s += '…'; x.fillText(s, 0, 0); x.restore();
    const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; return t;
  }
  // The inside of the case, drawn from how real moulded keep cases look: a matte, faintly grainy plastic; a shallow
  // round well the disc sits in, with a raised rim and two finger notches either side for lifting the disc out; the
  // hub in the middle (built in 3D, see build); a small recycling mark moulded near the bottom. A Switch case has a
  // recessed cartridge cradle with a thumb notch instead, a PSP case an oval UMD well, a GameCube case a little
  // memory-card holder under its mini-disc.
  function interiorTexture(bg, kind, w, h, R, sys) {
    // willReadFrequently (Android): the plate stays a CPU canvas (it used to be read back for the grain; the grain tile below
    // now has its own read-back canvas, so nothing reads this one back on any platform)
    const THREE = T(), H = 1024, W = Math.round(H * (w || .71) / (h || 1)), c = document.createElement('canvas'); c.width = W; c.height = H; const x = c.getContext('2d', window.bsPlatform && bsPlatform.os === 'android' ? { willReadFrequently: true } : undefined); // Android: read back without a GPU stall
    const px = v => v / (h || 1) * H, cx = W / 2, cy = H / 2 - px(discLift(kind, sys));
    // black / very dark plastic still reads as dark grey under light, so the moulding shows; lift the base a little
    { const cc = new THREE.Color(bg), l = cc.r * .3 + cc.g * .59 + cc.b * .11; if (l < .12) cc.lerp(new THREE.Color(0x2c2d33), .75); bg = '#' + cc.getHexString(); }
    x.fillStyle = bg; x.fillRect(0, 0, W, H);
    // moulded-plastic grain and a soft falloff toward the edges
    // (beta.10) the grain is a small tile (read back on its own CPU canvas) repeated over the plate: the whole-plate noise loop cost ~13 ms mid-lift
    { const N = 128, tc = document.createElement('canvas'); tc.width = tc.height = N; const tx = tc.getContext('2d', { willReadFrequently: true }); tx.fillStyle = bg; tx.fillRect(0, 0, N, N);
      const img = tx.getImageData(0, 0, N, N), d = img.data; for (let i = 0; i < d.length; i += 4) { const n = (Math.random() - .5) * 10; d[i] += n; d[i + 1] += n; d[i + 2] += n; } tx.putImageData(img, 0, 0);
      const pat = x.createPattern(tc, 'repeat'); if (pat) { x.fillStyle = pat; x.fillRect(0, 0, W, H); } }
    const g = x.createRadialGradient(cx, cy, 40, cx, cy, H * .62); g.addColorStop(0, 'rgba(255,255,255,.05)'); g.addColorStop(1, 'rgba(0,0,0,.3)'); x.fillStyle = g; x.fillRect(0, 0, W, H);
    const emboss = (path, depth) => { x.save(); x.lineWidth = 3; x.strokeStyle = `rgba(255,255,255,${.16 * depth})`; x.translate(-1.5, -1.5); path(); x.stroke(); x.restore(); x.save(); x.lineWidth = 3; x.strokeStyle = `rgba(0,0,0,${.35 * depth})`; x.translate(1.5, 1.5); path(); x.stroke(); x.restore(); };
    if (kind === 'cart') {
      const cw = px(R * 2 * .9 * 1.06), ch = cw * 1.1, ty = cy - ch / 2; // the cradle, a touch bigger than the cartridge
      x.fillStyle = 'rgba(0,0,0,.22)'; x.beginPath(); x.roundRect(cx - cw / 2, ty, cw, ch, 14); x.fill();
      emboss(() => { x.beginPath(); x.roundRect(cx - cw / 2, ty, cw, ch, 14); }, 1);
      x.fillStyle = 'rgba(0,0,0,.28)'; x.beginPath(); x.ellipse(cx, ty + ch + 6, cw * .22, 26, 0, Math.PI, 0, true); x.fill(); // thumb notch
    } else if (kind === 'umd') {
      const rw = px(R * 1.25), rh = px(R * 1.32);
      x.fillStyle = 'rgba(0,0,0,.2)'; x.beginPath(); x.ellipse(cx, cy, rw, rh, 0, 0, Math.PI * 2); x.fill(); emboss(() => { x.beginPath(); x.ellipse(cx, cy, rw, rh, 0, 0, Math.PI * 2); }, 1);
    } else {
      const rw = px(R * 1.035);
      x.fillStyle = 'rgba(0,0,0,.16)'; x.beginPath(); x.arc(cx, cy, rw, 0, Math.PI * 2); x.fill();
      emboss(() => { x.beginPath(); x.arc(cx, cy, rw, 0, Math.PI * 2); }, 1);
      // finger notches at the well's left and right
      [-1, 1].forEach(sd => { x.fillStyle = 'rgba(0,0,0,.3)'; x.beginPath(); x.ellipse(cx + sd * rw, cy, 34, 58, 0, 0, Math.PI * 2); x.fill(); emboss(() => { x.beginPath(); x.ellipse(cx + sd * rw, cy, 34, 58, 0, sd > 0 ? -Math.PI / 2 : Math.PI / 2, sd > 0 ? Math.PI / 2 : Math.PI * 1.5); }, .7); });
      // a faint ring of pads the disc's rim rests on
      x.strokeStyle = 'rgba(255,255,255,.06)'; x.lineWidth = 2; x.beginPath(); x.arc(cx, cy, px(R * .92), 0, Math.PI * 2); x.stroke();
      // GameCube: the disc sits high, and under it a moulded holder for a memory card (a card-sized pocket with a
      // retaining lip at the top and a notch at the bottom to push the card out)
      if (sys === 'gc') { const mw = px(.2), mh = px(.235), my = cy + rw + px(.035);
        x.fillStyle = 'rgba(0,0,0,.4)'; x.beginPath(); x.roundRect(cx - mw / 2, my, mw, mh, 8); x.fill(); emboss(() => { x.beginPath(); x.roundRect(cx - mw / 2, my, mw, mh, 8); }, 1.4);
        x.fillStyle = 'rgba(255,255,255,.1)'; x.fillRect(cx - mw / 2 + 6, my + 4, mw - 12, px(.012)); // retaining lip
        x.fillStyle = 'rgba(0,0,0,.45)'; x.beginPath(); x.ellipse(cx, my + mh, mw * .2, px(.02), 0, 0, Math.PI); x.fill(); // push-out notch
        x.fillStyle = 'rgba(255,255,255,.22)'; x.font = `600 ${Math.round(px(.016))}px sans-serif`; x.textAlign = 'center'; x.fillText('MEMORY CARD', cx, my + px(.04)); }
    }
    // the moulded recycling mark
    { const mx = sys === 'gc' && kind === 'disc' ? W - px(.08) : W / 2, my = H - px(.06), s = px(.022); x.save(); x.strokeStyle = 'rgba(255,255,255,.14)'; x.lineWidth = 2; x.beginPath(); x.moveTo(mx, my - s); x.lineTo(mx + s, my + s * .8); x.lineTo(mx - s, my + s * .8); x.closePath(); x.stroke();
      x.fillStyle = 'rgba(255,255,255,.14)'; x.font = `700 ${Math.round(s * .9)}px sans-serif`; x.textAlign = 'center'; x.fillText('5', mx, my + s * .55); x.font = `600 ${Math.round(s * .7)}px sans-serif`; x.fillText('PP', mx, my + s * 2); x.restore(); }
    const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 4; return t;
  }

  // Where the front cover is on a manual's first page, as a texture rectangle {u0,u1,v0,v1,aspect} (v up). Scans come
  // in three shapes: the front cover alone (use it all); the booklet's outside, back + front side by side (use the right
  // half); or a whole print sheet, the cover spread on top and inside pages below (find the dense, colourful block,
  // then its right half). flipped: the picture is stored upside down (an ImageBitmap decoded with flipY).
  function coverRect(img, flipped) {
    const iw = img && (img.naturalWidth || img.width), ih = img && (img.naturalHeight || img.height); if (!iw || !ih) return null;
    const W = 160, H = Math.max(8, Math.round(W * ih / iw)), c = document.createElement('canvas'); c.width = W; c.height = H; const x = c.getContext('2d', { willReadFrequently: true });
    try { x.drawImage(img, 0, 0, W, H); } catch { return null; } let d; try { d = x.getImageData(0, 0, W, H).data; } catch { return null; }
    const px = (X, Y) => { const k = ((flipped ? H - 1 - Y : Y) * W + X) * 4; return [d[k], d[k + 1], d[k + 2]]; };
    // background: the commonest of the four corners' colours
    const cs = [px(1, 1), px(W - 2, 1), px(1, H - 2), px(W - 2, H - 2)], bg = cs.map(a => [a, cs.filter(b => Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]) < 40).length]).sort((a, b) => b[1] - a[1])[0][0];
    const ink = new Uint8Array(W * H); for (let Y = 0; Y < H; Y++) for (let X = 0; X < W; X++) { const p = px(X, Y); ink[Y * W + X] = Math.abs(p[0] - bg[0]) + Math.abs(p[1] - bg[1]) + Math.abs(p[2] - bg[2]) > 48 ? 1 : 0; }
    const rowInk = Y => { let n = 0; for (let X = 0; X < W; X++) n += ink[Y * W + X]; return n / W; };
    // blocks of rows, split only at clear blank bands (at least 4% of the height)
    const segs = []; let s0 = -1, gap = 0; const minGap = Math.max(2, Math.round(H * .04));
    for (let Y = 0; Y <= H; Y++) { const on = Y < H && rowInk(Y) > .02; if (on) { if (s0 < 0) s0 = Y; gap = 0; } else if (s0 >= 0 && (++gap >= minGap || Y === H)) { segs.push([s0, Y - gap + 1]); s0 = -1; gap = 0; } }
    const blocks = segs.filter(([a, b]) => b - a >= H * .12).map(([a, b]) => { let n = 0; for (let Y = a; Y < b; Y++) for (let X = 0; X < W; X++) n += ink[Y * W + X]; return { a, b, n }; });
    if (!blocks.length) return { u0: 0, u1: 1, v0: 0, v1: 1, aspect: iw / ih };
    const blk = blocks.sort((p, q) => q.n - p.n)[0]; // the most printed-on block: the cover, not a page of text
    // its columns: the widest run of printed columns (thin specks and scanner edges off to the side don't count)
    let x0 = 0, x1 = W; { const on = []; for (let X = 0; X < W; X++) { let n = 0; for (let Y = blk.a; Y < blk.b; Y++) n += ink[Y * W + X]; on.push(n / (blk.b - blk.a) > .02); }
      const runs = []; let r0 = -1, g = 0; const mg = Math.max(2, Math.round(W * .03)); for (let X = 0; X <= W; X++) { if (X < W && on[X]) { if (r0 < 0) r0 = X; g = 0; } else if (r0 >= 0 && (++g >= mg || X === W)) { runs.push([r0, X - g + 1]); r0 = -1; g = 0; } }
      const best = runs.sort((p, q) => (q[1] - q[0]) - (p[1] - p[0]))[0]; if (best && best[1] - best[0] > W * .1) { x0 = best[0]; x1 = best[1]; } }
    let y0 = blk.a, y1 = blk.b; const bw = (x1 - x0) * iw / W, bh = (y1 - y0) * ih / H;
    if (bw / bh > 1.02) x0 = Math.round((x0 + x1) / 2); // back + front side by side: the front is the right half
    // tiny blocks are noise: then show the whole page
    if ((x1 - x0) * (y1 - y0) < W * H * .08) return { u0: 0, u1: 1, v0: 0, v1: 1, aspect: iw / ih };
    // keep the full page when the block is nearly all of it anyway (a full-bleed cover)
    const r = { u0: x0 / W, u1: x1 / W, v0: 1 - y1 / H, v1: 1 - y0 / H }; if (r.u0 < .03 && r.u1 > .97 && r.v0 < .03 && r.v1 > .97) Object.assign(r, { u0: 0, u1: 1, v0: 0, v1: 1 });
    r.aspect = (r.u1 - r.u0) * iw / ((r.v1 - r.v0) * ih); return r;
  }
  // The front cover of a manual's first page as its own picture (an object URL), for the booklet readers: null when
  // the page already is just the cover. Cached per page.
  const crops = new Map();
  function coverCrop(url) {
    if (!url) return Promise.resolve(null); if (crops.has(url)) return crops.get(url);
    const p = new Promise(res => { const im = new Image(); im.crossOrigin = 'anonymous'; im.decoding = 'async'; let late = false; const to = setTimeout(() => { late = true; crops.delete(url); res(null); }, 8000); /* slow: give up now, try again next time */
      im.onload = () => { clearTimeout(to); if (late) return; try { const r = coverRect(im, false); if (!r || (r.u0 === 0 && r.u1 === 1 && r.v0 === 0 && r.v1 === 1)) return res(null);
        const iw = im.naturalWidth, ih = im.naturalHeight, sx = r.u0 * iw, sy = (1 - r.v1) * ih, sw = (r.u1 - r.u0) * iw, sh = (r.v1 - r.v0) * ih, k = Math.min(1, 1800 / sh);
        const c = document.createElement('canvas'); c.width = Math.round(sw * k); c.height = Math.round(sh * k); c.getContext('2d', { willReadFrequently: true }).drawImage(im, sx, sy, sw, sh, 0, 0, c.width, c.height);
        c.toBlob(b => { c.width = c.height = 0; res(b ? URL.createObjectURL(b) : null); }, 'image/jpeg', .9); } catch { res(null); } };
      im.onerror = () => { clearTimeout(to); res(null); }; im.src = url; });
    // (the crop is read back as a JPEG: a canvas kept in memory, not on the GPU. The oldest crop's picture is let go a
    // minute after it leaves the cache, so a reader still showing it keeps it)
    crops.set(url, p);
    if (crops.size > 40) { const k0 = crops.keys().next().value, p0 = crops.get(k0); crops.delete(k0); p0.then(u => { if (u) setTimeout(() => URL.revokeObjectURL(u), 60000); }); }
    return p;
  }
  // put that rectangle on a manual plane, sized to fit maxW x maxH without stretching
  function fitCover(mesh, r, maxW, maxH) { if (!r) return; const uv = mesh.geometry.attributes.uv; for (let i = 0; i < uv.count; i++) uv.setXY(i, Math.round(uv.getX(i)) ? r.u1 : r.u0, Math.round(uv.getY(i)) ? r.v1 : r.v0); uv.needsUpdate = true;
    const k = r.aspect > maxW / maxH ? [1, maxW / r.aspect / maxH] : [r.aspect * maxH / maxW, 1]; mesh.scale.set(k[0], k[1], 1); }
  // how far above the middle the disc sits (case heights = 1): a GameCube disc rides high to leave room for the card holder
  function discLift(kind, sys) { return sys === 'gc' && kind !== 'cart' && kind !== 'umd' ? .1 : 0; }
  // The disc's underside, by format: a clear outer rim; the data area, a mirror with fine concentric grooves (the
  // rainbow comes from the material's iridescence as you turn it) and a slightly different sheen where the data
  // ends; the mirror band near the middle with a tiny moulded ID code; the clear stacking ring and the hole.
  function discBackTexture(fmt, code) {
    const THREE = T(), S = 1024, c = document.createElement('canvas'); c.width = c.height = S; const x = c.getContext('2d'), m = S / 2;
    const base = fmt === 'bd' ? [196, 200, 232] : fmt === 'gc' ? [214, 210, 226] : [226, 218, 196]; // Blu-ray cooler and violet, DVD warmer and golden
    const col = (k, a = 1) => `rgba(${base.map(v => Math.max(0, Math.min(255, Math.round(v * k)))).join(',')},${a})`;
    x.fillStyle = col(1); x.fillRect(0, 0, S, S);
    for (let r = m * .36; r < m * .985; r += 1.5) { x.strokeStyle = col(.9 + .12 * Math.sin(r * .9) + (Math.random() - .5) * .05, .35); x.lineWidth = 1; x.beginPath(); x.arc(m, m, r, 0, Math.PI * 2); x.stroke(); }
    const end = m * (.82 + Math.random() * .12); x.fillStyle = col(.86, .55); x.beginPath(); x.arc(m, m, m * .985, 0, Math.PI * 2); x.arc(m, m, end, 0, Math.PI * 2, true); x.fill(); // past the end of the data
    x.strokeStyle = col(.7, .6); x.lineWidth = 2; x.beginPath(); x.arc(m, m, end, 0, Math.PI * 2); x.stroke();
    // mirror band with the moulded ID code running round it
    x.fillStyle = col(1.12); x.beginPath(); x.arc(m, m, m * .36, 0, Math.PI * 2); x.fill();
    x.save(); x.translate(m, m); x.fillStyle = col(.62, .9); x.font = `600 ${Math.round(S * .012)}px monospace`; const txt = (code + '   ').repeat(3); for (let i = 0; i < txt.length; i++) { x.save(); x.rotate(i / txt.length * Math.PI * 2); x.fillText(txt[i], 0, -m * .3); x.restore(); } x.restore();
    x.strokeStyle = col(.75, .8); x.lineWidth = 3; x.beginPath(); x.arc(m, m, m * .36, 0, Math.PI * 2); x.stroke();
    const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 8; return t;
  }
  // The drawn textures (case interior, disc underside) are the same every time for the same inputs: keep the last few
  // instead of redrawing a 1024px canvas and re-uploading it on each open. Marked cached so disposeGroup leaves them.
  // (kit.* hands out fresh, caller-owned ones, as before.)
  const genTex = new Map();
  function genCached(key, make) {
    let t = genTex.get(key);
    if (t) { genTex.delete(key); genTex.set(key, t); return t; }
    t = make(); t.userData.cached = true; genTex.set(key, t);
    if (genTex.size > 8) { const [k, old] = genTex.entries().next().value; genTex.delete(k); try { old.dispose(); } catch {} }
    return t;
  }
  const interiorCached = (...a) => genCached('in|' + a.join('|'), () => interiorTexture(...a));
  const discBackCached = (fmt, code) => genCached('db|' + fmt + '|' + code, () => discBackTexture(fmt, code));
  // Bounding box of an object in its own local space, whatever its parents are doing
  function localBox(obj) {
    const THREE = T(); obj.updateMatrixWorld(true);
    const inv = new THREE.Matrix4().copy(obj.matrixWorld).invert(), box = new THREE.Box3(), m = new THREE.Matrix4();
    obj.traverse(n => { if (!n.isMesh || !n.geometry) return; for (let q = n; q && q !== obj.parent; q = q.parent) if (q.visible === false) return; if (!n.geometry.boundingBox) n.geometry.computeBoundingBox(); m.multiplyMatrices(inv, n.matrixWorld); box.union(n.geometry.boundingBox.clone().applyMatrix4(m)); });
    return box;
  }
  // (beta.10) frees every texture a material holds (a cart.glb brings normal / roughness maps, and the label's original), not only .map; cached art stays
  function disposeGroup(g) { if (!g) return; g.traverse(n => { if (n.geometry) n.geometry.dispose(); if (n.material) (Array.isArray(n.material) ? n.material : [n.material]).forEach(m => { if (!m) return; for (const k in m) { const v = m[k]; if (v && v.isTexture && !(v.userData && v.userData.cached)) v.dispose(); } const lo = m.userData && m.userData.labelOrig; if (lo && lo.isTexture && !(lo.userData && lo.userData.cached)) lo.dispose(); m.dispose(); }); }); while (g.children.length) g.remove(g.children[0]); }

  // Build the case for a game. o = {ratio, depth, caseColor, caseSide, spineInk, front, back, spine, disc, manual, kind:'disc'|'cart'|'umd', size, title, modelUrl}
  function build(o) {
    const THREE = T();
    if (customMedia && customMedia.parent === mediaG) mediaG.remove(customMedia);
    disposeGroup(caseG); disposeGroup(mediaG); cur = o;
    const h = 1, w = o.ratio, lt = Math.max(.014, o.depth * .18), r = Math.min(.016, lt * .9);
    const plastic = (color, extra) => new THREE.MeshPhysicalMaterial(Object.assign({ color, roughness: .42, metalness: .02, clearcoat: .35, clearcoatRoughness: .4 }, extra || {}));
    // printed sleeve under clear plastic: the paper itself is matte (no broad haze over the art), the shine is a
    // tight clearcoat highlight that moves as you turn the case
    const sleeve = map => new THREE.MeshPhysicalMaterial({ map, color: 0xffffff, roughness: .62, metalness: 0, clearcoat: .55, clearcoatRoughness: .22, reflectivity: .3, envMapIntensity: .6 });
    const side = plastic(o.caseSide), inner = plastic(o.caseSide, { roughness: .55 });
    // Media first, so the tray is exactly deep enough for it (a cartridge needs more room than a disc)
    buildMedia(o, 0);
    // measure the media in its own space (the whole scene may be turned at this point; a world-space box would be far too deep)
    const mb = localBox(media), mDepth = mb.isEmpty() ? .01 : (mb.max.z - mb.min.z);
    const tb = .012, D = Math.max(.045, o.depth * .55, mDepth + tb + .014); // tray depth: back plate + media + a little air
    // Hollow tray: back plate, a spine wall along the hinge edge and thin top/bottom walls — the lid closes flush on them
    const spineMap = o.spine ? loadTex(o.spine) : spineTexture(o.title, o.caseColor, o.spineInk);
    // The case is built like the real moulded thing: the front and back are rounded rectangles with each console's
    // own corner radius (Blu-ray and Switch cases are much rounder than a GameCube's), softly bevelled at every edge,
    // and the walls are one continuous rounded ring, so there are no square corners poking out past the curves.
    const rc = Math.max(.012, Math.min(.09, (o.rc || .04) * w)), bev = .0028;
    const rr = (W, H, R) => { const sh = new THREE.Shape(), x = -W / 2, y = -H / 2; R = Math.min(R, W / 2 - .001, H / 2 - .001);
      sh.moveTo(x + R, y); sh.lineTo(x + W - R, y); sh.quadraticCurveTo(x + W, y, x + W, y + R); sh.lineTo(x + W, y + H - R); sh.quadraticCurveTo(x + W, y + H, x + W - R, y + H);
      sh.lineTo(x + R, y + H); sh.quadraticCurveTo(x, y + H, x, y + H - R); sh.lineTo(x, y + R); sh.quadraticCurveTo(x, y, x + R, y); return sh; };
    // a printed face that sits on a rounded slab: UVs spread 0..1 over the whole rectangle so the art fills it
    // like the shelf's cover pictures (object-fit: cover): once the scan has loaded, crop it to the face's shape
    // instead of stretching it
    const cropTo = (mesh, t, W, H) => { if (!t || !t.userData || !t.userData.p) return; t.userData.p.then(ok => { const im = t.image; if (!ok || !im || !mesh.geometry || !mesh.geometry.attributes) return;
      const ia = (im.naturalWidth || im.width) / (im.naturalHeight || im.height), pa = W / H; if (!isFinite(ia) || !ia) return;
      let su = 1, sv = 1; if (ia > pa) su = pa / ia; else sv = ia / pa; const u0 = (1 - su) / 2, v0 = (1 - sv) / 2, uv = mesh.geometry.attributes.uv, pos = mesh.geometry.attributes.position;
      for (let i = 0; i < pos.count; i++) uv.setXY(i, u0 + (pos.getX(i) / W + .5) * su, v0 + (pos.getY(i) / H + .5) * sv); uv.needsUpdate = true; }); };
    const face = (W, H, R, mat) => { const g = new THREE.ShapeGeometry(rr(W, H, R), 12), uv = g.attributes.uv, pos = g.attributes.position; for (let i = 0; i < pos.count; i++) uv.setXY(i, pos.getX(i) / W + .5, pos.getY(i) / H + .5); uv.needsUpdate = true; return new THREE.Mesh(g, mat); };
    const slab = (W, H, R, T, mat) => { const g = new THREE.ExtrudeGeometry(rr(W - 2 * bev, H - 2 * bev, Math.max(.002, R - bev)), { depth: Math.max(.0005, T - 2 * bev), bevelEnabled: true, bevelThickness: bev, bevelSize: bev, bevelSegments: 3, curveSegments: 12 }); g.translate(0, 0, -(T - 2 * bev) / 2); return new THREE.Mesh(g, mat); };
    const back = slab(w, h, rc, tb, side); back.position.z = -D / 2 + tb / 2; caseG.add(back);
    { const inFace = face(w - .02, h - .02, rc * .8, plastic(0xffffff, { map: interiorCached(o.caseSide, o.kind, w - .02, h - .02, o.ratio * o.size / 2, o.sys), roughness: .7, clearcoat: .1 })); inFace.position.z = -D / 2 + tb + .0004; caseG.add(inFace);
      const eb = o.back ? Math.max(0, Math.min(.08, o.edge || 0)) * w : 0; if (eb > .0015) { const rimB = face(w - .004, h - .004, rc, plastic(o.caseColor)); rimB.rotation.y = Math.PI; rimB.position.z = -D / 2 - .0003; caseG.add(rimB); }
      // (beta.11) o.wrap (Xbox 360): no border on the spine side, the sheet runs on round the spine
      const bw2 = w - .004 - (o.wrap ? eb : 2 * eb);
      const outFace = face(bw2, h - .004 - 2 * eb, Math.max(.003, rc - eb * .6), o.back ? sleeve(loadTex(o.back)) : plastic(o.caseColor)); outFace.rotation.y = Math.PI; outFace.position.set(o.wrap ? -eb / 2 : 0, 0, -D / 2 - .0006); if (o.back) cropTo(outFace, outFace.material.map, bw2, h - .004 - 2 * eb); caseG.add(outFace); }
    // the walls: one rounded ring (outer = the case outline, inner = a wall's thickness in), standing on the back plate
    const wall = .011, Dw = D - tb, zw = tb / 2;
    { const ringS = rr(w, h, rc), hole = rr(w - 2 * wall, h - 2 * wall, Math.max(.003, rc - wall)); ringS.holes.push(new THREE.Path(hole.getPoints(48)));
      const rg = new THREE.ExtrudeGeometry(ringS, { depth: Dw, bevelEnabled: false, curveSegments: 16 }); rg.translate(0, 0, -Dw / 2);
      const ring = new THREE.Mesh(rg, [inner, side]); ring.position.z = zw; caseG.add(ring); }
    // the spine: the printed spine wraps the hinge side, full height between the rounded corners, curving at its edges
    // (beta.11) wrap: the spine as tall as the covers it joins
    const spine = new THREE.Mesh(new THREE.PlaneGeometry(D + lt - .004, o.wrap ? h - .004 - 2 * Math.max(0, Math.min(.08, o.edge || 0)) * w : h - rc * 1.1), sleeve(spineMap)); spine.rotation.y = -Math.PI / 2; spine.position.set(-w / 2 - .0008, 0, lt / 2); caseG.add(spine); spineM = spine;
    // hub / cradle on the back plate
    const floorZ = -D / 2 + tb;
    // the hub (a "rosette"): a raised collar, a ring of springy fingers that grip the disc's hole, and a push button in
    // the middle that releases it, moulded in the case's own plastic
    if (o.kind === 'disc') { const hubM = plastic(new THREE.Color(o.caseSide).lerp(new THREE.Color(0xffffff), .12), { roughness: .5 }), btnM = plastic(new THREE.Color(o.caseSide).lerp(new THREE.Color(0x000000), .25), { roughness: .35 });
      const hr = o.ratio * o.size / 2 * .135, hub = new THREE.Group(); hub.position.set(0, discLift(o.kind, o.sys), floorZ); caseG.add(hub);
      const collar = new THREE.Mesh(new THREE.CylinderGeometry(hr * 1.45, hr * 1.6, .0036, 48), hubM); collar.rotation.x = Math.PI / 2; collar.position.z = .0018; hub.add(collar);
      for (let k = 0; k < 10; k++) { const a = k / 10 * Math.PI * 2, f = new THREE.Mesh(new THREE.BoxGeometry(hr * .4, hr * .32, .011), hubM); f.position.set(Math.cos(a) * hr * .7, Math.sin(a) * hr * .7, .0105); f.rotation.z = a + Math.PI / 2; f.rotation.x = .25; hub.add(f); }
      const btn = new THREE.Mesh(new THREE.CylinderGeometry(hr * .45, hr * .5, .016, 32), btnM); btn.rotation.x = Math.PI / 2; btn.position.z = .01; hub.add(btn);
      const cap = new THREE.Mesh(new THREE.SphereGeometry(hr * .45, 24, 12, 0, Math.PI * 2, 0, Math.PI / 2), btnM); cap.rotation.x = Math.PI / 2; cap.scale.set(1, .35, 1); cap.position.z = .018; hub.add(cap); }
    else if (o.kind === 'umd') { const pegM = plastic(new THREE.Color(o.caseSide).lerp(new THREE.Color(0xffffff), .12), { roughness: .5 }); [[-1, 1], [1, 1], [-1, -1], [1, -1]].forEach(([sx, sy]) => { const peg = new THREE.Mesh(new THREE.BoxGeometry(.03, .03, .014), pegM); const R0 = o.ratio * o.size / 2; peg.position.set(sx * R0 * 1.1, sy * R0 * 1.2, floorZ + .007); caseG.add(peg); }); }
    // lid hinged at the spine edge, flush on the walls
    lidPivot = new THREE.Group(); lidPivot.position.set(-w / 2, 0, D / 2 + lt / 2); caseG.add(lidPivot);
    const ft = o.front ? loadTex(o.front) : null;
    const lidMats = [side, side, side, side, o.front ? sleeve(ft.userData.ok || !standInTex ? ft : standInTex) : plastic(o.caseColor), plastic(o.caseSide, { roughness: .5 })];
    coverMat = o.front ? lidMats[4] : null;
    // the lid: a rounded slab like the back, with the cover on a face just above it (under the clear-sleeve finish)
    const lid = slab(w, h, rc, lt, side); lid.position.set(w / 2, 0, 0); lidPivot.add(lid);
    // the printed cover sits inside a border of the case's own colour, the same width as on the shelf (style edge), so
    // the case looks the same in your hands as on the shelf
    const ed = o.front ? Math.max(0, Math.min(.08, o.edge || 0)) * w : 0;
    { if (ed > .0015) { const rim = face(w - .004, h - .004, rc, plastic(o.caseColor)); rim.position.set(w / 2, 0, lt / 2 + .0003); lidPivot.add(rim); }
      const cw2 = w - .004 - (o.wrap ? ed : 2 * ed); // (beta.11) wrap: no border on the hinge side
      const cover = face(cw2, h - .004 - 2 * ed, Math.max(.003, rc - ed * .6), lidMats[4]); cover.position.set(o.wrap ? w / 2 - ed / 2 : w / 2, 0, lt / 2 + .0006); lidPivot.add(cover); if (ft) cropTo(cover, ft, cw2, h - .004 - 2 * ed);
      const under = face(w - .01, h - .01, rc * .9, lidMats[5]); under.rotation.y = Math.PI; under.position.set(w / 2, 0, -lt / 2 - .0004); lidPivot.add(under); }
    // the manual sits in clips on the inside of the lid
    const man = new THREE.Mesh(new THREE.PlaneGeometry(w * .8, h * .86), new THREE.MeshStandardMaterial({ map: o.manual ? loadTex(o.manual) : null, color: o.manual ? 0xffffff : 0xe8e4dc, roughness: .75 }));
    man.position.set(w / 2 + .01, 0, -lt / 2 - .002); man.rotation.y = Math.PI; lidPivot.add(man); manualPlane = man;
    // the front cover only (see coverRect), once the scan has loaded
    if (o.manual) { const mt = man.material.map; let n = 0; const chk = () => { if (manualPlane !== man) return; const img = mt.image; if (!img || !(img.naturalWidth || img.width)) { if (n++ < 100) setTimeout(chk, 100); return; } fitCover(man, coverRect(img, false), w * .8, h * .86); }; chk(); }
    [[-.4, .34], [-.4, -.34]].forEach(([x, y]) => { const clip = new THREE.Mesh(new THREE.BoxGeometry(w * .07, h * .09, .006), plastic(o.caseColor)); clip.position.set(w / 2 + x * w + w * .035, y * h, -lt / 2 - .006); lidPivot.add(clip); });
    // seat the media on the back plate
    const zIn = floorZ + .004 - mb.min.z; media.position.set(0, discLift(o.kind, o.sys), zIn); media.userData.zIn = zIn; media.userData.y0 = discLift(o.kind, o.sys);
    st.lid = st.lidT = 0; st.out = st.outT = 0; st.spin = 0;
    applyState(true);
  }
  function buildMedia(o, t) {
    const THREE = T(), RB = window.BS3D.RoundedBoxGeometry;
    const labelMap = loadTex(o.disc || o.front);
    if (customMedia && customUrl === o.modelUrl) { mediaG.add(customMedia); media = customMedia; media.position.set(0, 0, 0); media.scale.set(1, 1, 1); media.updateMatrixWorld(true); return; }
    if (o.kind === 'cart' && o.form === 'floppy') {
      // (beta.11) a 3.5" floppy disk (DOS): a 90 x 94 mm dark plastic shell 3.3 mm thick, a sliding metal shutter across the
      // top with its window, the game's label below it and a write-protect tab in the corner
      const fw = o.ratio * o.size * .9, fh = fw * 94 / 90, fd = fw * 3.3 / 90;
      const shellM = new THREE.MeshPhysicalMaterial({ color: 0x1d1e22, roughness: .6, clearcoat: .15 });
      media = new THREE.Group();
      media.add(new THREE.Mesh(new RB(fw, fh, fd, 2, fw * .02), shellM));
      const metal = new THREE.MeshStandardMaterial({ color: 0xc4c8cf, metalness: .9, roughness: .28 });
      const sw = fw * .6, sh = fh * .34, sx = fw * .04;
      [1, -1].forEach(side => { const sh2 = new THREE.Mesh(new THREE.BoxGeometry(sw, sh, .0012), metal); sh2.position.set(sx, fh / 2 - sh / 2, side * (fd / 2 + .0006)); media.add(sh2);
        const win = new THREE.Mesh(new THREE.PlaneGeometry(sw * .28, sh * .62), new THREE.MeshStandardMaterial({ color: 0x3a2a1c, roughness: .9 })); win.position.set(sx + sw * .1, fh / 2 - sh / 2 - sh * .06, side * (fd / 2 + .0013)); if (side < 0) win.rotation.y = Math.PI; media.add(win); });
      const lbl = new THREE.Mesh(new THREE.PlaneGeometry(fw * .82, fh * .56), new THREE.MeshPhysicalMaterial({ map: labelMap, roughness: .7 }));
      lbl.position.set(0, -fh / 2 + fh * .05 + fh * .28, fd / 2 + .0007); media.add(lbl);
      const tab = new THREE.Mesh(new THREE.BoxGeometry(fw * .07, fw * .07, .0012), new THREE.MeshStandardMaterial({ color: 0x0c0c0e, roughness: .8 })); tab.position.set(-fw / 2 + fw * .09, -fh / 2 + fw * .09, -fd / 2 - .0006); media.add(tab);
    } else if (o.kind === 'cart') {
      const cw = o.ratio * o.size * .9, ch = cw * 1.1, cd = .035;
      const shell = new THREE.MeshPhysicalMaterial({ color: 0x24252b, roughness: .55, clearcoat: .2 });
      const cart = new THREE.Mesh(new RB(cw, ch, cd, 3, .008), [shell, shell, shell, shell, new THREE.MeshPhysicalMaterial({ map: labelMap, roughness: .45, clearcoat: .3 }), shell]);
      media = new THREE.Group(); media.add(cart);
      const pins = new THREE.Mesh(new THREE.BoxGeometry(cw * .7, ch * .12, cd + .002), new THREE.MeshStandardMaterial({ color: 0xc9a24a, metalness: .8, roughness: .35 })); pins.position.y = -ch / 2 + ch * .08; media.add(pins);
    } else {
      const R = o.ratio * o.size / 2;
      const label = new THREE.MeshPhysicalMaterial({ map: labelMap, roughness: .3, clearcoat: .6, clearcoatRoughness: .15 });
      const fmt = o.sys === 'ps3' || o.sys === 'ps4' || o.sys === 'ps5' || o.sys === 'wiiu' ? 'bd' : o.sys === 'gc' ? 'gc' : 'dvd';
      let hcode = 0; for (const ch of (o.title || 'disc')) hcode = (hcode * 31 + ch.charCodeAt(0)) >>> 0;
      const code = `${(o.sys || 'DSC').toUpperCase()}-${(hcode % 9000 + 1000)}-${String.fromCharCode(65 + hcode % 26)}${String.fromCharCode(65 + (hcode >> 5) % 26)}  ${(hcode >> 9) % 90 + 10}`;
      const data = new THREE.MeshPhysicalMaterial({ color: 0xffffff, map: discBackCached(fmt, code), metalness: .92, roughness: .1, iridescence: 1, iridescenceIOR: fmt === 'bd' ? 1.8 : 1.6, iridescenceThicknessRange: fmt === 'bd' ? [200, 560] : [120, 480], clearcoat: .6, clearcoatRoughness: .05 });
      media = new THREE.Group();
      // glass: real transmission renders the whole scene once more each frame to see through it; the Handheld level (phones)
      // draws the clear plastic as plain see-through instead, which looks nearly the same on a small screen
      const glass = !window.Perf || Perf.get().glass !== false;
      const TH = .0045, clear = new THREE.MeshPhysicalMaterial({ color: 0xffffff, transparent: true, opacity: glass ? .35 : .45, roughness: .05, transmission: glass ? .6 : 0, thickness: .01, clearcoat: 1 });
      const top = new THREE.Mesh(new THREE.RingGeometry(R * .22, R, 128), label); top.position.z = TH / 2; media.add(top);
      // underside: planar UVs so the round texture lands on the ring as drawn; a clear outer rim beyond the data
      const bg = new THREE.RingGeometry(R * .2, R * .985, 128), bp = bg.attributes.position, bu = bg.attributes.uv; for (let i = 0; i < bp.count; i++) bu.setXY(i, .5 + bp.getX(i) / (2 * R), .5 + bp.getY(i) / (2 * R)); bu.needsUpdate = true;
      const bottom = new THREE.Mesh(bg, data); bottom.rotation.y = Math.PI; bottom.position.z = -TH / 2; media.add(bottom);
      const rim = new THREE.Mesh(new THREE.RingGeometry(R * .985, R, 128), clear); rim.rotation.y = Math.PI; rim.position.z = -TH / 2; media.add(rim);
      const hubT = new THREE.Mesh(new THREE.RingGeometry(R * .14, R * .22, 64), clear); hubT.position.z = TH / 2; media.add(hubT);
      const hubB = new THREE.Mesh(new THREE.RingGeometry(R * .14, R * .2, 64), clear); hubB.rotation.y = Math.PI; hubB.position.z = -TH / 2; media.add(hubB);
      const edge = new THREE.Mesh(new THREE.CylinderGeometry(R, R, TH, 128, 1, true), clear); edge.rotation.x = Math.PI / 2; media.add(edge);
      const hole = new THREE.Mesh(new THREE.CylinderGeometry(R * .14, R * .14, TH, 48, 1, true), clear); hole.rotation.x = Math.PI / 2; hole.material.side = THREE.BackSide; media.add(hole);
      if (o.kind === 'umd') {
        const shell = new THREE.Mesh(new RB(R * 2.3, R * 2.5, .05, 3, .02), new THREE.MeshPhysicalMaterial({ color: 0xdfe6ff, transparent: true, opacity: glass ? .22 : .3, roughness: .1, transmission: glass ? .3 : 0, clearcoat: 1 }));
        shell.position.set(0, -R * .1, 0); media.add(shell);
      }
    }
    media.position.set(0, 0, 0); media.scale.set(1, 1, 1); mediaG.add(media); media.updateMatrixWorld(true);
  }
  // Optional user model for the media (e.g. themes\sw\cart.glb). Files often hold several copies of the object
  // (a row of cartridges): keep the first copy that has a picture on it, hide the rest, and put the game's own
  // label where that picture was.
  // each call takes a ticket: a slow earlier load that finishes after a newer one is dropped, not swapped in
  let mediaSeq = 0;
  async function loadMediaModel(url, labelUrl, opts) {
    const seq = ++mediaSeq;
    if (!url) { customMedia = null; customUrl = null; return; }
    if (customUrl === url && customMedia) { relabel(labelUrl); return; }
    try {
      const THREE = T(); const gltf = await new window.BS3D.GLTFLoader().loadAsync(url);
      if (seq !== mediaSeq || !renderer) { try { disposeGroup(gltf.scene); } catch {} return; }
      let m = gltf.scene; m.updateMatrixWorld(true);
      // step down single-child wrappers to the level where the copies live
      let lvl = m; while (lvl.children.length === 1 && !lvl.children[0].isMesh) lvl = lvl.children[0];
      const kids = lvl.children.slice();
      if (kids.length > 1) {
        const boxes = kids.map(k => new THREE.Box3().setFromObject(k));
        const groups = []; const used = new Set();
        kids.forEach((k, i) => { if (used.has(i)) return; const g = [i]; used.add(i); let grew = true; const acc = boxes[i].clone();
          while (grew) { grew = false; kids.forEach((k2, j) => { if (used.has(j)) return; const b = boxes[j].clone().expandByScalar(acc.getSize(new THREE.Vector3()).length() * .02); if (b.intersectsBox(acc)) { used.add(j); g.push(j); acc.union(boxes[j]); grew = true; } }); }
          groups.push({ idx: g, box: acc }); });
        const hasPic = g => g.idx.some(i => { let f = false; kids[i].traverse(n => { if (n.isMesh && n.material && (Array.isArray(n.material) ? n.material : [n.material]).some(mt => mt.map)) f = true; }); return f; });
        let pick = groups.find(hasPic) || groups.sort((a, b) => b.idx.length - a.idx.length)[0];
        groups.forEach(g => { if (g !== pick) g.idx.forEach(i => { kids[i].visible = false; }); });
      }
      const vis = new THREE.Box3(); m.traverse(n => { if (n.isMesh) { let v = true; for (let q = n; q; q = q.parent) if (q.visible === false) v = false; if (v) vis.expandByObject(n); } });
      let size = vis.getSize(new THREE.Vector3()), c = vis.getCenter(new THREE.Vector3());
      const g = new THREE.Group(); const k = .42 / Math.max(size.x, size.y, size.z); m.scale.setScalar(k); m.position.set(-c.x * k, -c.y * k, -c.z * k);
      // stand it up facing you: the thin axis becomes depth, the long axis becomes height (cart.json yaw/pitch/roll adjust from there)
      const hold = new THREE.Group(); hold.add(m);
      const thin = size.x <= size.y && size.x <= size.z ? 'x' : size.y <= size.z ? 'y' : 'z';
      if (thin === 'y') hold.rotation.x = -Math.PI / 2; else if (thin === 'x') hold.rotation.y = Math.PI / 2;
      hold.updateMatrixWorld(true);
      const visBox = obj => { const b = new THREE.Box3(); obj.updateMatrixWorld(true); obj.traverse(n => { if (n.isMesh) { let v = true; for (let q = n; q; q = q.parent) if (q.visible === false) v = false; if (v) b.expandByObject(n); } }); return b; };
      const s2 = visBox(hold).getSize(new THREE.Vector3());
      const spin = new THREE.Group(); spin.add(hold); if (s2.x > s2.y) spin.rotation.z = Math.PI / 2;
      // picture side toward the camera: the label sits on the front surface, so its centre is in front of the body's centre
      spin.updateMatrixWorld(true);
      const isVis = n => { for (let q = n; q; q = q.parent) if (q.visible === false) return false; return true; };
      const matsOf = n => Array.isArray(n.material) ? n.material : [n.material];
      const centerOf = pred => { const b = new THREE.Box3(); m.traverse(n => { if (n.isMesh && isVis(n) && pred(n)) b.expandByObject(n); }); return b.isEmpty() ? null : b.getCenter(new THREE.Vector3()); };
      // look at it from the front and from the back: whichever side shows the picture first is the front
      const meshes = []; m.traverse(n => { if (n.isMesh && isVis(n)) meshes.push(n); });
      const bodyC = visBox(spin).getCenter(new THREE.Vector3());
      const hitHasMap = dirZ => { const rc = new THREE.Raycaster(new THREE.Vector3(bodyC.x, bodyC.y, bodyC.z - dirZ * 5), new THREE.Vector3(0, 0, dirZ)); const hits = rc.intersectObjects(meshes, false); return hits.length ? matsOf(hits[0].object).some(mt => mt.map) : null; };
      const frontPic = hitHasMap(-1), backPic = hitHasMap(1);
      if (!frontPic && backPic) spin.rotation.y += Math.PI;
      else if (frontPic === null && backPic === null) { const labelC = centerOf(n => matsOf(n).some(mt => mt.map)); if (labelC && labelC.z < bodyC.z) spin.rotation.y += Math.PI; }
      spin.updateMatrixWorld(true);
      // contacts at the bottom: the most metallic material marks the pin end
      let pinC = centerOf(n => matsOf(n).some(mt => /metal|pin|contact|connector/i.test(mt.name || '')));
      if (!pinC) { let best = 0; m.traverse(n => { if (n.isMesh && isVis(n)) matsOf(n).forEach(mt => { if ((mt.metalness || 0) > best && !mt.map) best = mt.metalness; }); }); if (best > .5) pinC = centerOf(n => matsOf(n).some(mt => mt.metalness === best)); }
      const bodyC2 = visBox(spin).getCenter(new THREE.Vector3());
      if (pinC && pinC.y > bodyC2.y + 1e-4) { spin.rotation.z += Math.PI; }
      const o = opts || {}; const adj = new THREE.Group(); adj.add(spin);
      adj.rotation.set((o.pitch || 0) * DEG, (o.yaw || 0) * DEG, (o.roll || 0) * DEG); if (o.scale) adj.scale.setScalar(o.scale);
      g.add(adj);
      // label materials = the ones with a picture; clone them so each game gets its own
      g.userData.labels = [];
      m.traverse(n => { if (!n.isMesh || !n.material) return; let v = true; for (let q = n; q; q = q.parent) if (q.visible === false) v = false; if (!v) return;
        const arr = Array.isArray(n.material) ? n.material : [n.material];
        arr.forEach((mt, i) => { if (mt.map) { const cl = mt.clone(); cl.userData.labelOrig = mt.map; if (Array.isArray(n.material)) n.material[i] = cl; else n.material = cl; g.userData.labels.push(cl); } }); });
      if (customMedia && customMedia !== g) { const old = customMedia; try { if (old.parent) old.parent.remove(old); if (old.userData.labelTex) old.userData.labelTex.dispose(); disposeGroup(old); } catch {} } // swapping cart.glb ↔ umd.glb: free the old one
      customMedia = g; customUrl = url; relabel(labelUrl);
      if (active && cur && cur.modelUrl === url) { const keep = { ...st }; build(cur); st.state = keep.state; applyState(true); }
    } catch (e) { if (seq !== mediaSeq) return; console.warn('media model', e); customMedia = null; customUrl = null; }
  }
  function relabel(labelUrl) {
    if (customMedia) customMedia.userData.want = labelUrl; // (beta.10) a ticket: a late decode of the last game's label mustn't land on this game's cart
    if (!customMedia || !labelUrl) return;
    const src = loadTex(labelUrl), cm = customMedia; if (!src.userData.ok) { src.userData.p.then(ok => { if (ok && customMedia === cm && cm.userData.want === labelUrl && renderer) relabel(labelUrl); }); return; } // the copy needs the picture itself
    const THREE = T(); const tx = src.clone(); tx.flipY = false; tx.needsUpdate = true; tx.colorSpace = THREE.SRGBColorSpace;
    const prev = customMedia.userData.labelTex; if (prev && prev !== tx) { try { prev.dispose(); } catch {} } customMedia.userData.labelTex = tx;
    customMedia.userData.labels.forEach(mt => { mt.map = tx; mt.needsUpdate = true; });
  }

  function applyState(snap) {
    st.lidT = st.state >= 1 ? 1 : 0; st.outT = st.state >= 2 ? 1 : 0;
    if (snap) { st.lid = st.lidT; st.out = st.outT; }
  }
  const sm = (a, b, k) => a + (b - a) * k;
  let lastT = 0, paused = false;
  function frame(now) {
    if (!cur || paused) { raf = 0; lastT = 0; return; } // (beta.10) paused (a manual is open) or nothing shown: the loop stops; setPaused(false) / open() start it again
    raf = requestAnimationFrame(frame);
    const dt = Math.min(.05, lastT ? (now - lastT) / 1000 : .016); lastT = now;
    // the quick cross-fade: the canvas keeps its last picture, only its opacity moves (no re-render)
    if (fadeT0) {
      const k = Math.min(1, (now - fadeT0) / FADE_MS); canvas.style.opacity = (fadeFrom * (1 - easeIO(k))).toFixed(3);
      if (k < 1) return;
      fadeT0 = 0; canvas.style.opacity = '0';
      if (pend) { start(pend); return; } // the next game, now that the last one has gone
      finish(); return;
    }
    if (phase === 'idle') return;
    if (phase === 'wait') { if (!ready) return; begin(); } // art decoded (or the wait ran out): take over from the shelf box this frame
    if (standInTex && coverMat && cur.front) { const ft = tex.get(cur.front); if (ft && ft.userData.ok) { coverMat.map = ft; coverMat.needsUpdate = true; try { standInTex.dispose(); } catch {} standInTex = null; } }
    const reduce = document.body.classList.contains('reduce-motion'), leaving = phase === 'out';
    // entrance / exit run on the clock with an ease-in-out curve, so they start and land softly at any frame rate
    if (phase === 'in' || leaving) {
      st.in = Math.max(0, Math.min(1, st.in + (leaving ? -dt : dt) / (reduce ? .18 : leaving ? (cur.from ? .44 : .32) : (cur.from ? .56 : .5))));
      // the box may still be sliding (or the scene moving for Details): fly to where it is now; if it's gone (another
      // game picked, another shelf, a game launching), cross-fade instead of landing somewhere it no longer is
      if (leaving && target) { let f = null; try { f = target(); } catch {} if (!f) { crossfade(now); return; } cur.from = local(f); }
      if (leaving && st.in <= 0) { finish(); return; } // landed: the box shows again in this same frame, the canvas goes
      if (phase === 'in' && st.in >= 1) { phase = 'shown'; canvas.style.transition = ''; canvas.style.opacity = ''; } // hand opacity back to the stylesheet (the reader's dim)
    }
    const kl = 1 - Math.exp(-dt * 4.2), ko = 1 - Math.exp(-dt * 4);
    if (leaving) { const k = easeIO(st.in); st.lid = st.lid0 * k; st.out = st.out0 * k; } // shut on the way back: it lands closed, like the box on the shelf
    else { st.lid = sm(st.lid, st.lidT, kl); st.out = sm(st.out, st.outT, ko); }
    st.bump *= Math.exp(-dt * 9);
    lidPivot.rotation.y = -165 * DEG * easeIO(st.lid);
    const o = easeIO(st.out);
    // disc out: the case slides away off-screen and the disc alone sits in your hands; "put back" brings the case in again
    caseG.position.set(-2.6 * o, -.3 * o, -.6 * o); caseG.rotation.y = 30 * DEG * o; caseG.visible = o < .995;
    media.position.x = 0; media.position.y = (media.userData.y0 || 0) * (1 - o); media.position.z = media.userData.zIn * (1 - o) + .25 * o + st.bump;
    const ms = 1 + (cur.kind === 'cart' ? .9 : .55) * o; media.scale.set(ms, ms, ms);
    media.rotation.set(0, 0, 0);
    // A disc (or UMD) out of its case spins about its own centre, like in a player, and only tilts gently; it doesn't swing round
    // the case's middle. So as it comes out, the turn the controls make (st.ry, counted from where it left the case: st.base)
    // moves off the whole scene and onto the disc's own face axis, and up / down becomes a softer tilt of the disc alone.
    // rxE / ryE: what's left for the scene (the case), squared up to face you once the disc is out. dw: the spin, wrapped to half a turn, so taking it in or out only ever
    // unwinds less than half a turn (whole turns are invisible on a round disc and on the off-screen case).
    let rxE = st.rx, ryE = st.ry;
    if (cur.kind !== 'cart' && !st.own) { const idle = now - lastMove > 1500; st.spinV = sm(st.spinV || 0, (st.out > .95 && idle) ? .22 : 0, 1 - Math.exp(-dt * 2)); st.spin += dt * st.spinV; media.rotation.z = st.spin; } // Windows: as before
    else if (cur.kind !== 'cart') {
      const idle = now - lastMove > 1500; st.spinV = sm(st.spinV || 0, (st.out > .95 && idle) ? .22 : 0, 1 - Math.exp(-dt * 2)); st.spin += dt * st.spinV;
      const d = st.ry - st.base, dw = d - 360 * Math.round(d / 360), whole = d - dw, front = 360 * Math.round(st.base / 360);
      ryE = st.base + whole + dw * (1 - o) + (front - st.base) * o; rxE = st.rx * (1 - o); // out: the scene squares up, so the disc faces you
      // (0.3.1) the turn moves the disc in 3D about its own centre: side to side turns it over (both faces), up / down
      // tilts it right round; it still spins in its own plane when left alone, like in a player (Euler XYZ: the spin is
      // applied in the disc's plane first, then the turn, then the tilt)
      media.rotation.set(-st.rx * o * DEG, (st.flip + dw) * o * DEG, st.spin);
    }
    const perPx = (2 * 3.9 * Math.tan(camera.fov * DEG / 2)) / innerHeight;
    const e = easeIO(st.in);
    if (cur.from && !reduce) {
      // "pull it off the shelf": start exactly where the selected box sits on screen (position, size and the angle the
      // shelf turns it to), glide to the middle while turning to the inspect angle; leaving plays the path back into
      // the box's current place. Size: the box's rect is as tall as its nearer edge, so match that edge, not the middle.
      const f = cur.from, fx = (f.x - innerWidth / 2) * perPx, fy = -(f.y - innerHeight / 2) * perPx, H = f.h * perPx, a = Math.abs(Math.sin(f.ry * DEG)) * cur.ratio / 2, fs = 3.9 * H / (3.9 + a * H);
      rot.rotation.set(-rxE * e * DEG, (f.ry * (1 - e) + ryE * e) * DEG, 0);
      const s = fs + (st.zoom - fs) * e; rot.scale.set(s, s, s);
      rot.position.set(fx + (st.px * perPx - fx) * e, fy + (-st.py * perPx - fy) * e, .35 * Math.sin(Math.PI * e) * (leaving ? .4 : 1));
    } else {
      // no box to come from (over the museum), or reduce motion: the case comes forward out of the dark and fades in;
      // leaving, it keeps coming toward you and fades out (reduce motion: a plain fade in place)
      if (phase !== 'shown') canvas.style.opacity = (leaving ? Math.max(0, e * 1.2 - .2) : Math.min(1, e * 1.05)).toFixed(3);
      const m = reduce ? 0 : 1 - e, back = leaving ? -m * 1.1 : -m * 1.6;
      rot.rotation.set(-rxE * DEG, (ryE + m * 22) * DEG, 0);
      const s = st.zoom * (leaving ? 1 + m * .35 : .8 + .2 * (1 - m)); rot.scale.set(s, s, s);
      rot.position.set(st.px * perPx, -st.py * perPx - m * .12, leaving ? -back : back);
    }
    renderer.render(scene, camera);
  }
  const easeIO = t => t <= 0 ? 0 : t >= 1 ? 1 : (t < .5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);

  const sameGame = (a, b) => a && b && a.front === b.front && a.back === b.back && a.sys === b.sys && !!a.from === !!b.from;
  // o: as build, plus from {x,y,h,ry} (the shelf box on screen, client px; null = no box), standIn (the box's own,
  // already-loaded cover <img>: shown on the case if the big scan isn't ready in time), onStart() (called in the frame
  // the 3D case takes over: hide the box then)
  function open(o) {
    if (lost || (renderer && renderer.getContext().isContextLost())) teardown();
    if (!ensure()) return false;
    tok++;
    canvas.classList.add('on'); active = true;
    // still flying back into its box: the same game just turns round and comes out again
    if (phase === 'out' && sameGame(cur, o)) { target = null; onDone = null; phase = 'in'; cur.onStart = o.onStart; if (o.onStart) o.onStart(); return true; }
    // another case still on screen: it fades out quickly while this one's art loads; this one starts once it's gone
    if (phase === 'in' || phase === 'shown' || phase === 'out') crossfade(performance.now());
    if (fadeT0) { pend = o; [o.front, o.back, o.spine].forEach(loadTex); } else start(o);
    if (!raf) { lastT = 0; raf = requestAnimationFrame(frame); }
    return true;
  }
  function start(o) {
    pend = null; const my = tok;
    if (standInTex) { try { standInTex.dispose(); } catch {} standInTex = null; }
    canvas.style.transition = 'none'; canvas.style.opacity = '0';
    resize(); build(o); st.state = 0; applyState(true); st.in = 0; phase = 'wait'; ready = false; target = null; onDone = null;
    if (o.from) cur.from = local(o.from);
    if (o.modelUrl) loadMediaModel(o.modelUrl, o.disc || o.front, o.modelOpts);
    // wait (at most a quarter second) for the outside art to be decoded and on the GPU and the materials compiled, so
    // the case never flies out blank or half-drawn; the shelf box stays on screen meanwhile
    const ps = [o.front, o.back, o.spine].map(u => { const t = loadTex(u); return t && (t.userData.ok ? null : t.userData.p.then(ok => { if (ok && tok === my && renderer) try { renderer.initTexture(t); } catch {} })); }).filter(Boolean);
    if (renderer.compileAsync && renderer.extensions.has('KHR_parallel_shader_compile')) ps.push(renderer.compileAsync(scene, camera).catch(() => {})); // first open: shaders compile off the main thread
    Promise.race([Promise.all(ps), new Promise(r => setTimeout(r, 250))]).then(() => { if (tok === my) ready = true; });
  }
  function begin() {
    phase = 'in';
    // re-read where the box is now (the shelf may have settled during the wait) and the canvas's own place (its parent
    // only drops the CSS case's scale once the overlay is in 3D mode), so the first frame sits exactly on the box
    cvRect = canvas.getBoundingClientRect(); { let f = null; try { f = cur.where && cur.where(); } catch {} if (f) cur.from = local(f); }
    // the big scan isn't here yet: wear the shelf box's own cover (already decoded) until it is, rather than a blank sleeve
    const ft = cur.front && tex.get(cur.front), si = cur.standIn;
    if (coverMat && ft && !ft.userData.ok && si && si.complete && si.naturalWidth && untainted(si)) {
      const THREE = T(); standInTex = new THREE.Texture(si); standInTex.colorSpace = THREE.SRGBColorSpace; standInTex.needsUpdate = true; coverMat.map = standInTex; coverMat.needsUpdate = true; }
    if (cur.from && !document.body.classList.contains('reduce-motion')) canvas.style.opacity = '1';
    if (cur.onStart) try { cur.onStart(); } catch (e) { console.warn(e); }
  }
  // a picture from this page's own origin (or data: / blob:) can't taint WebGL: no test draw (which decodes the whole picture
  // on the main thread just to read one pixel back) is needed for those
  function untainted(im) { try { const u = new URL(im.currentSrc || im.src, location.href); if (u.protocol === 'data:' || u.protocol === 'blob:' || (u.origin !== 'null' && u.origin === location.origin)) return true; } catch {}
    try { const c = document.createElement('canvas'); c.width = c.height = 1; const x = c.getContext('2d', { willReadFrequently: true }); x.drawImage(im, 0, 0, 1, 1); x.getImageData(0, 0, 1, 1); return true; } catch { return false; } }
  function crossfade(now) {
    const v = parseFloat(canvas.style.opacity); fadeFrom = isNaN(v) ? 1 : v; fadeT0 = now || performance.now(); phase = 'fade'; canvas.style.transition = 'none';
    target = null; const cb = onDone; onDone = null; if (cb) try { cb(true); } catch (e) { console.warn(e); } // the box fades back in as the case fades out
  }
  function finish() { phase = 'idle'; target = null; const cb = onDone; onDone = null; if (canvas) canvas.style.opacity = '0'; close(); if (cb) try { cb(false); } catch (e) { console.warn(e); } }
  // tgt(): where the box is now ({x,y,h,ry} client px) or null when it's no longer the place to land; done(fade): show
  // the box again (fade = cross-fading, not landing)
  function leave(tgt, done) {
    if (!active) { if (done) done(false); return; }
    tok++; // a pending start or late art from this open does nothing now
    if (pend || phase === 'wait' || phase === 'idle') { pend = null; if (!fadeT0) finish(); if (done) done(true); return; } // never got as far as the screen
    if (phase === 'fade') { if (done) done(true); return; }
    cvRect = canvas.getBoundingClientRect();
    st.lid0 = st.lid; st.out0 = st.out; canvas.style.transition = 'none';
    // the flight home goes the short way: st.ry is the shelf's running angle (every turn you gave it), and the path
    // eases from it to the box's angle, so two full turns used to be spun back on the way into the slot
    { const fr = cur && cur.from ? cur.from.ry || 0 : 0; st.ry = fr + (((st.ry - fr) % 360) + 540) % 360 - 180; }
    if (document.body.classList.contains('reduce-motion') && cur.from) { onDone = done; crossfade(performance.now()); return; }
    if (phase === 'shown') canvas.style.opacity = cur.from ? '1' : '1';
    phase = 'out'; target = cur.from ? tgt : null; onDone = done;
  }
  // closed: only the shown game's art stays on the GPU (reopening it is instant); every other cached texture is freed
  function close() { active = false; cancelAnimationFrame(raf); raf = 0; phase = 'idle'; pend = null; fadeT0 = 0; if (canvas) canvas.classList.remove('on');
    const keep = new Set(cur ? [cur.front, cur.back, cur.spine, cur.disc, cur.manual, cur.disc || cur.front] : []);
    tex.forEach((t, k) => { if (keep.has(k)) return; tex.delete(k); try { t.dispose(); } catch {} }); }
  let lastMove = 0;
  // flip: the disc turned over (degrees, eased by the caller); base: the turn (ry) it had when it came out of the case
  function setPose(rx, ry, zoom, px, py, busy, flip, base) { if (busy) lastMove = performance.now(); st.rx = rx; st.ry = ry; st.zoom = zoom; st.px = px; st.py = py; st.flip = flip || 0; st.own = base != null; st.base = base || 0; } // (base null: Windows, the disc turns with the case as before)
  function setState(n) { st.state = n; applyState(false); }
  function bump() { st.bump = .12; }
  // (beta.12, touch) what's under a finger on the screen: 'manual' (the booklet in the lid), 'disc' (the disc or
  // cartridge), or null (the case itself, or nothing). Whatever is nearest along the line of sight wins, so a case wall in
  // front of the manual keeps it. Once per tap, never per frame
  function hitAt(x, y) {
    if (!active || !renderer || !camera || !canvas || !scene) return null;
    const THREE = T(), r = canvas.getBoundingClientRect(); if (!r.width || !r.height) return null;
    const rc = new THREE.Raycaster(); rc.setFromCamera(new THREE.Vector2((x - r.left) / r.width * 2 - 1, -((y - r.top) / r.height) * 2 + 1), camera);
    scene.updateMatrixWorld(true);
    const hits = rc.intersectObjects([caseG, mediaG].filter(Boolean), true).filter(h => h.object.visible !== false);
    for (let o = hits.length ? hits[0].object : null; o; o = o.parent) { if (o === manualPlane) return 'manual'; if (media && o === media) return 'disc'; }
    return null;
  }
  function setPaused(p) { paused = !!p; if (!paused && active && !raf && renderer) { lastT = 0; raf = requestAnimationFrame(frame); } }
  addEventListener('resize', () => { if (renderer && active) resize(); });
  // the lid's manual, when it arrives late (Boxshelf's own booklet is drawn after the case opens)
  function setManual(url) { if (!manualPlane || !url || (cur && cur.manual)) return; const m = manualPlane.material, t = loadTex(url); m.map = t; m.color.set(0xffffff); m.needsUpdate = true; if (cur) cur.manual = url; }
  return { setManual, kit: { interiorTexture, discBackTexture, discLift, coverRect, fitCover, coverCrop }, available, open, close, leave, setPose, setState, bump, hitAt, setPaused, isActive: () => active,
    busy: () => !paused && (phase === 'out' || phase === 'fade' || phase === 'wait' || phase === 'in'), _st: () => ({ phase, in: st.in, ry: st.ry, rotY: rot ? Math.round(rot.rotation.y / DEG) : null, ready, fade: !!fadeT0, pend: !!pend, op: canvas && canvas.style.opacity, on: !!(canvas && canvas.classList.contains('on')), from: cur && cur.from, standIn: !!standInTex, tok, disc: discPose(), manual: manualXY(), spine: !!(spineM && spineM.parent && spineM.parent.parent) }) }; // (beta.11 spine: the printed spine is on the case)
  // (test hook) where on the screen the middle of the lid's manual is, in page pixels (null when it isn't drawn)
  function manualXY() { if (!manualPlane || !camera || !canvas) return null; const THREE = T(); scene.updateMatrixWorld(true); const p = manualPlane.getWorldPosition(new THREE.Vector3()).project(camera), r = canvas.getBoundingClientRect();
    return { x: Math.round(r.left + (p.x + 1) / 2 * r.width), y: Math.round(r.top + (1 - p.y) / 2 * r.height) }; }
  // (test hook) where the disc / cartridge is in the scene, and which way its face points
  function discPose() { if (!media) return null; const THREE = T(); media.updateWorldMatrix(true, false); const p = media.getWorldPosition(new THREE.Vector3()), n = new THREE.Vector3(0, 0, 1).transformDirection(media.matrixWorld); return { x: +p.x.toFixed(3), y: +p.y.toFixed(3), z: +p.z.toFixed(3), nz: +n.z.toFixed(3) }; }
})();
