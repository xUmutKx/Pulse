/* Museum: your whole collection as one tall shelving wall. One shelf row per system, every game as a real-sized
   case standing spine-out (real spine art when you have it), the console (your .glb or an original stand-in) at
   the end of its row with a plaque. Opening: the picture lights come on row by row while the camera pulls back to
   reveal the wall; games added since your last visit slide into their gap. Up/down picks a row, left/right a case
   (it slides out, then turns to face you, with a little card beneath), A inspects it, the console can be picked up
   and turned. three.js; shadows only under the row you're looking at, textures built a few per frame. */
window.Museum = (() => {
  const T = () => window.BS3D && window.BS3D.THREE;
  const DEG = Math.PI / 180;
  let renderer, scene, camera, canvas, env, wall, raf = 0, active = false, failed = false, paused = false, fillL = null; const pauseWhy = new Set();
  let rows = [], rowI = 0, colI = 0, hooks = {}, tex = new Map(), camP = { x: 0, y: 0, z: 8 }, lastT = 0, seen = new Set();
  let spot = null, spotAt = null, spotK = 0, wide = false; // wide: stepped back to see the whole wall (B from a row)
  // Lighting scenes (Room menu / Settings → Museum, or L in the museum). Ids are the old moods' ids, so a saved choice
  // carries over: gallery = Classic (the original look, unchanged), night = Evening (was After hours), neon = Neon night.
  // lamp: picture-light colour and strength; env: how much the room itself glows; spot: the selection beam; hue: each row lit
  // in its console's colour (0…1); amb / moon / rim: the room's fixed lights (only their colour and strength change, never
  // their number, so a scene change never recompiles a shader); bg: the dark beyond the room; wash / strip / caseGlow: the
  // shelf's own glow (the LED line, its wash on the back panel, the cases' faint self-light); tvGlow: the TV's light on the floor.
  const LIGHTS = {
    gallery: { name: 'Classic', lamp: 0xffe0b0, lampI: 16, env: 1, spot: 0xfff1dc, spotI: 5, hue: 0, fill: .28, beam: .07, amb: .12, ambC: 0xaab0c8, moon: .25, rimL: 0xbfd2ff, rimR: 0xbfd2ff, rim: .22, bg: 0x05060a, exp: .95, wash: 1, strip: 1, caseGlow: 1, tvGlow: 1 },
    daylight: { name: 'Daylight', lamp: 0xf4f7ff, lampI: 13, env: 2.6, spot: 0xffffff, spotI: 3, hue: 0, fill: .3, beam: .03, amb: .55, ambC: 0xe4ecff, moon: 1.1, rimL: 0xf2f6ff, rimR: 0xfff4e0, rim: .45, bg: 0x2a3140, exp: 1.08, wash: .5, strip: .6, caseGlow: 1.1, tvGlow: .45 },
    night: { name: 'Evening', lamp: 0xffa860, lampI: 11, env: .4, spot: 0xffd6a0, spotI: 7, hue: 0, fill: .36, beam: .13, amb: .05, ambC: 0x6a5a8a, moon: .2, rimL: 0x5a6cff, rimR: 0xff8a40, rim: .5, bg: 0x0b0612, exp: .95, wash: 1.3, strip: 1, caseGlow: .9, tvGlow: 1.4 },
    neon: { name: 'Neon night', lamp: 0xffffff, lampI: 10, env: .3, spot: 0xffffff, spotI: 4, hue: 1, fill: .3, beam: 0, amb: .04, ambC: 0x7a50ff, moon: .08, rimL: 0xff2fb4, rimR: 0x22d8ff, rim: 1.6, bg: 0x0a0320, exp: 1, wash: 0, strip: 0, caseGlow: 1, tvGlow: 1.6 },
    spotlit: { name: 'Spotlit gallery', lamp: 0xfff0d6, lampI: 26, env: .3, spot: 0xffffff, spotI: 10, hue: 0, fill: .14, beam: .22, amb: .03, ambC: 0xaab0c8, moon: .05, rimL: 0xbfd2ff, rimR: 0xbfd2ff, rim: .06, bg: 0x020203, exp: 1, wash: .3, strip: .4, caseGlow: .5, tvGlow: 1 },
    lightsout: { name: 'Lights out', lamp: 0xffd0a0, lampI: 0, env: .05, spot: 0xffe8cc, spotI: 1.4, hue: 0, fill: .1, beam: 0, amb: .012, ambC: 0x6070a0, moon: .02, rimL: 0x4050a0, rimR: 0x4050a0, rim: .04, bg: 0x010102, exp: 1, wash: 1.7, strip: 1.5, caseGlow: 1.9, tvGlow: 2.4 }
  };
  const LCOL = ['lamp', 'spot', 'ambC', 'rimL', 'rimR', 'bg'], LNUM = Object.keys(LIGHTS.gallery).filter(k => k !== 'name' && !LCOL.includes(k));
  // light: the values in use (THREE.Colors for the colours), eased from lightFrom to the chosen scene over ~1.4 s
  let light = null, lightId = 'gallery', lightFrom = null, lightK = 1, ambL = null, moonL = null, rimLs = [];
  const sceneCols = new Map(); // the chosen scene's colours as THREE.Colors, made once each
  function sceneAt(id) { const THREE = T(), s = LIGHTS[id] || LIGHTS.gallery; if (!sceneCols.has(id)) { const c = {}; LCOL.forEach(k => { c[k] = new THREE.Color(s[k]); }); sceneCols.set(id, c); } return sceneCols.get(id); }
  function lightCopy(id) { const s = LIGHTS[id] || LIGHTS.gallery, o = { name: s.name }; LNUM.forEach(k => { o[k] = s[k]; }); const c = sceneAt(id); LCOL.forEach(k => { o[k] = c[k].clone(); }); return o; }
  function setLighting(id) {
    const was = lightId; lightId = LIGHTS[id] ? id : 'gallery'; const name = LIGHTS[lightId].name; if (!T()) return name; if (light && was === lightId) return name; // already there (or on its way)
    // closed (or not built yet): straight there. Open: a cross-fade, from wherever a fade still running has got to
    if (!light || !active || !rows.length) { light = lightCopy(lightId); lightFrom = null; lightK = 1; applyLight(); applyAtmos(); return name; }
    const f = { name: light.name }; LNUM.forEach(k => { f[k] = light[k]; }); LCOL.forEach(k => { f[k] = light[k].clone(); }); lightFrom = f; lightK = 0; light.name = name; return name;
  }
  function stepLight(dt) {
    if (!lightFrom) return; lightK = Math.min(1, lightK + dt / (reducedMo() ? .35 : 1.4)); const e = ss(lightK), to = LIGHTS[lightId], tc = sceneAt(lightId);
    LNUM.forEach(k => { light[k] = lightFrom[k] + (to[k] - lightFrom[k]) * e; }); LCOL.forEach(k => { light[k].copy(lightFrom[k]).lerp(tc[k], e); });
    applyLight(); applyAtmos(); if (lightK >= 1) lightFrom = null;
  }
  // the room's fixed lights, the dark beyond it and the exposure: plain values, no shader changes
  const AMB_LIFT = () => (window.bsPlatform && window.bsPlatform.os === 'android' ? 1.45 : 1); // (the handheld's pooled lamps: see poolLamps)
  function applyAtmos() { if (!light || !scene) return; if (ambL) { ambL.color.copy(light.ambC); ambL.intensity = light.amb * AMB_LIFT(); } if (moonL) moonL.intensity = light.moon; rimLs.forEach((r, i) => { r.color.copy(i ? light.rimR : light.rimL); r.intensity = light.rim; });
    if (scene.background && scene.background.isColor) scene.background.copy(light.bg); if (scene.fog) scene.fog.color.copy(light.bg); if (renderer) renderer.toneMappingExposure = light.exp; }
  const LAMP = () => light.lampI * (room ? room.bright : 1);
  // warmth: -1 cool daylight … +1 warm tungsten, mixed into every lamp and the spotlight
  const WARM = [0xffa45c, 0xc8dcff].map(h => ({ h, c: null }));
  function warmTint(c) { const THREE = T(), w = room ? room.warmth : 0; WARM.forEach(x => { if (!x.c) x.c = new THREE.Color(x.h); }); if (w > 0) c.lerp(WARM[0].c, w * .45); else if (w < 0) c.lerp(WARM[1].c, -w * .55); return c; }
  function applyLight() {
    const THREE = T(); if (!THREE || !light) return; // (every frame of a cross-fade: no new objects after the first)
    // Neon night: each row is lit in its console's own colour, at full strength; a cross-fade mixes the two
    rows.forEach(rw => { const lc = rw.lc || (rw.lc = new THREE.Color()); lc.copy(light.lamp); if (light.hue > 0 && rw.style && rw.style.glow) { const gc = rw.gc || (rw.gc = new THREE.Color(rw.style.glow).lerp(new THREE.Color(0xffffff), .06)); lc.lerp(gc, light.hue); } warmTint(lc);
      rw.lamp.color.copy(lc); if (rw.strip) rw.strip.material.color.copy(lc).multiplyScalar(1.2 * Math.max(1, light.strip)); if (rw.wash) rw.wash.material.color.copy(lc); if (rw.bulb) rw.bulb.material.emissive.copy(lc); if (rw.bulbs) rw.bulbs.forEach(b => b.mesh.material.emissive.copy(lc)); if (!intro.on && rw.lampOn >= 1) rw.lamp.intensity = LAMP();
      if (rw.led) { rw.led.visible = light.hue > .01; rw.led.children.forEach(m => { m.material.opacity = Math.min(1, light.hue); }); } if (rw.fixture) rw.fixture.visible = false; });
    if (spot) spot.color.copy(warmTint(sceneTmp(light.spot)));
  }
  const sceneTmp = col => { const THREE = T(); return (sceneTmp.c || (sceneTmp.c = new THREE.Color())).copy(col); };
  let wallW = 6, wallH = 4, holding = null, heldCase = null, hold = { rx: 0, ry: 0, trx: 0, try: 0 }, lastInput = 0, shadowRow = -1;
  let intro = { on: false, t: 0, len: 0 }, work = [], card = null, dust = [];
  const H = 1, ROW = 1.34, DEPTH = .8; // a case is 1 unit tall; everything else is relative
  const snd = n => { try { hooks.onSound && hooks.onSound(n); } catch {} };
  // the case card's button line, in the shelf's own prompt style (A / Cross / Nintendo's swapped A, Enter on the keyboard):
  // hooks.okWord is app.js's okWord, read at draw time; without it the line names no button rather than a wrong one
  const cardKeys = () => { let w = ''; try { w = hooks.okWord ? String(hooks.okWord() || '') : ''; } catch {} return w ? `${w}  pick up · again to open` : 'pick it up to open it'; };
  const SV = {}, sv = k => SV[k] || (SV[k] = new (T().Vector3)()); // scratch vectors for the per-frame maths (no garbage every frame)

  function available() { return !failed && !!T(); }
  // the PC's performance level (renderer/perf.js); dynScale: the museum's own render-scale step-down while frames run slow
  const perf = () => (window.Perf ? Perf.get() : { pr: 1.25, aa: true, shadows: true, shadowSize: 1024, aniso: 8, dust: true }); let glGen = 0, dynScale = 1, dynSlow = 0, dynFast = 0, dynWant = 0, dynHold = null, frameEma = 0, dynCeil = 1, dynCeilAt = 0, dynLogN = 0, rendererAA = null, rebuildGL = false;
  const wantShadows = () => !window.__musNoShadows && perf().shadows;
  function ensure() {
    if (renderer) return true; const THREE = T(); if (!THREE) return false;
    try { canvas = document.createElement('canvas'); canvas.className = 'mus-gl'; rendererAA = perf().aa; renderer = new THREE.WebGLRenderer({ canvas, antialias: rendererAA, powerPreference: 'high-performance' }); renderer.debug.checkShaderErrors = false; }
    catch { failed = true; setTimeout(() => { failed = false; }, 30000); return false; } /* (beta.12: tried again in 30 s: a graphics driver restarting used to leave the museum off until Boxshelf restarted) */
    renderer.outputColorSpace = THREE.SRGBColorSpace; renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = .95;
    // shadows follow the Performance level (Settings → Performance: off at Low) for weaker GPUs
    renderer.shadowMap.enabled = wantShadows(); renderer.shadowMap.type = THREE.PCFShadowMap; renderer.shadowMap.autoUpdate = false;
    scene = new THREE.Scene(); scene.background = new THREE.Color(0x05060a); scene.fog = new THREE.Fog(0x05060a, 16, 46);
    scene.environmentIntensity = .22; // the reflections (makeEnv) are made in their own step
    camera = new THREE.PerspectiveCamera(36, 1, .05, 90);
    ambL = new THREE.AmbientLight(0xaab0c8, .12); scene.add(ambL);
    moonL = new THREE.DirectionalLight(0x9fb0ff, .25); moonL.position.set(-6, 6, 8); scene.add(moonL);
    // movie-set lighting on top of the lamps: two rim lights that catch shelf edges and case corners from either side (cool
    // in Classic, magenta and cyan in Neon night), and a soft fill that follows the camera so whatever you're looking at
    // always reads, however dark the scene
    rimLs = [[-7, 5, 4], [9, 5, 4]].map(p => { const rim = new THREE.DirectionalLight(0xbfd2ff, .22); rim.position.set(...p); scene.add(rim); return rim; });
    fillL = new THREE.DirectionalLight(0xfff4e6, 0); scene.add(fillL, fillL.target);
    light = lightCopy(lightId); applyAtmos();

    wall = new THREE.Group(); scene.add(wall);
    document.getElementById('museum').prepend(canvas);
    canvas.addEventListener('webglcontextlost', e => { e.preventDefault(); if (e.target === canvas) lost(); }); // an old surface (after a rebuild) can't tear down the new one
    return true;
  }
  // the soft room reflections everything picks up: a small render of its own, so it gets its own quiet moment in the
  // warm-up (or is made when the museum first opens, if that comes first)
  function makeEnv() { if (env || !renderer) return; const THREE = T(); const pm = new THREE.PMREMGenerator(renderer); env = pm.fromScene(new window.BS3D.RoomEnvironment(), .04).texture; pm.dispose(); scene.environment = env; }
  // pictures still waiting to be read are dropped (and forgotten, so asking again starts a fresh load); nothing is
  // uploaded for a room that's gone
  function dropQueued() { loadQ.forEach(q => { q.t.userData.dead = true; if (tex.get(q.t.userData.url) === q.t) tex.delete(q.t.userData.url); }); loadQ.length = 0; upQ.forEach(upDone); upQ.length = 0; } // decoded but not yet uploaded: marked ready (kept for a reopen, uploaded when first drawn)
  function teardown() { glGen++; try { disposeTV(); } catch {} dropQueued(); tex.forEach(t => { t.userData.dead = true; }); roomTex.forEach(t => { try { t.dispose(); } catch {} }); roomTex = new Map(); roomObj = null; try { cancelAnimationFrame(raf); if (renderer) { renderer.dispose(); if (!renderer.getContext().isContextLost()) renderer.forceContextLoss(); } /* already lost: forcing it again only warned */ } catch {} try { canvas && canvas.remove(); } catch {} tex.forEach(t => { try { t.dispose(); } catch {} }); tex = new Map(); woodT = floorT = null; renderer = scene = camera = canvas = wall = env = null; fillL = ambL = moonL = null; rimLs = []; active = false; warmed = false; warming = null; }
  // the graphics card reset (driver update, sleep / wake, a GPU crash) while you're in the museum: it used to leave a black
  // screen with a wall of dead objects (A on a case then threw). Rebuild the room on a fresh surface, straight to the overview
  // (no walk-in, no opening chord); at most twice a minute, so a card that keeps failing leaves you on a black room B gets out of.
  let lastOpen = null, lostN = 0, lostAt = 0;
  function lost() {
    const re = active && lastOpen; teardown(); rows = []; prep = null; primeKeep = []; spotAt = null; if (hooks.log) hooks.log('museum: graphics context lost' + (re ? ', rebuilding' : ''));
    if (!re) return; const now = performance.now(); if (now - lostAt > 60000) lostN = 0; lostAt = now; if (++lostN > 2) return;
    setTimeout(() => { if (active || lastOpen !== re || failed) return; try { open(Object.assign(re.data, { walkIn: false }), Object.assign({}, re.h, { onShown: null })); } catch (e) { console.warn('museum rebuild', e); } }, 500);
  }
  function resize() { const w = Math.max(1, innerWidth), h = Math.max(1, innerHeight); /* a minimised window reports 0 × 0 */ renderer.setPixelRatio(Math.max(.5, Math.min(devicePixelRatio || 1, perf().pr * (window.BS_AUTOZOOM || 1)) * dynScale)); renderer.setSize(w, h, false); camera.aspect = w / h; camera.updateProjectionMatrix(); }
  // Images decode off the main thread (ImageBitmap) and at most three load at once, so a wall of spines doesn't stutter the frame
  const loadQ = []; let loading = 0;
  // pictures are handed to the graphics card a few per frame (see uploadSome) instead of all on the frame they first show
  const upQ = [];
  const fst = { up: 0, fr: 0, sp: 0, sh: 0, row: 0 }; // what the last frame did (for the hitch note)
  const facc = { n: 0, js: 0, jsMax: 0, up: 0, fr: 0, sp: 0, sh: 0, row: 0, card: 0, trim: 0, rn: 0, rUpMax: 0, rFr: 0 }; // running totals of the same (read by the profiling / device tests through _perf)
  function uploadSome(ms, max = 99) { const t0 = performance.now(); let n = 0; while (upQ.length && n < max && performance.now() - t0 < ms) { const t = upQ.shift(); if (t && t.image && !t.userData.dead) { try { renderer.initTexture(t); n++; fst.up++; } catch {} } upDone(t); } }
  // a picture is handed to its materials only once it's on the graphics card: handed over as soon as it was decoded, the next
  // draw uploaded it itself, in the middle of a camera glide (the quiet frames uploadSome keeps clear)
  function upDone(t) { const f = t && t.userData.onUp; if (f) { t.userData.onUp = null; f(); } }
  const ANDROID_512 = () => !!(window.bsPlatform && bsPlatform.os === 'android'); // read when used (museum.js may load before platform.js has said)
  function pump() {
    const THREE = T();
    while (loading < (intro.on ? 2 : 3) && loadQ.length) {
      const { url, t, kind } = loadQ.shift(); loading++;
      const done = () => { loading--; pump(); };
      // waiters: materials that show the case colour until the picture is really there (an empty texture draws black)
      const settle = ok => { const w = t.userData.waiters || []; t.userData.waiters = null; t.userData.state = ok ? 'ok' : 'bad'; if (!t.userData.dead) w.forEach(f => f[ok ? 0 : 1]()); };
      const fail = () => { settle(false); done(); };
      const put = img => { if (t.userData.dead) { try { img && img.close && img.close(); } catch {} } else if (img) { t.image = img; t.needsUpdate = true; t.userData.onUp = () => settle(true); upQ.push(t); unsleep(); } else settle(false); done(); }; // released while loading: drop the image
      if (/^data:/.test(url)) { new THREE.TextureLoader().load(url, tx => put(tx.image), undefined, fail); continue; }
      try {
        const opts = { imageOrientation: 'flipY', premultiplyAlpha: 'none', resizeQuality: 'high' }; if (kind === 'spine') opts.resizeHeight = 512; else if (kind === 'front') { if (ANDROID_512() && /_capacitor_file_|Boxshelf(%20| )Art/i.test(url)) opts.resizeHeight = 512; else opts.resizeWidth = 512; } // Android's art copies are tall (1024 px since 0.3.2): sized by height they come down to the museum's 512 (by width a tall cover came out taller than 512 for nothing)
        new THREE.ImageBitmapLoader().setOptions(opts).load(url, put, undefined,
          () => { new THREE.TextureLoader().load(url, tx => put(tx.image), undefined, fail); });
      } catch { fail(); }
    }
  }
  // keep the most recent covers' decoded images for a quick reopen; older ones are released completely
  function trimTex(keep) { const drop = tex.size - keep; if (drop <= 0) return; let i = 0; for (const [k, t] of tex) { if (i++ >= drop) break; t.userData.dead = true; try { if (t.image && t.image.close) t.image.close(); t.dispose(); } catch {} tex.delete(k); } } // dead: a load still in flight drops its picture instead of reviving it
  // ask Boxshelf's thumbnailer for a small copy instead of decoding full-size art (spines and fronts are drawn ≤ 512 px)
  // a back cover too (seen up close in your hands, so a bigger copy): a full-size scan was decoded and uploaded as it was (a
  // 3000×4000 scan is ~60 MB of graphics memory with its mipmaps) for every case you turned over
  const small = (url, kind) => /^bsfile:/.test(url) && !/[?&]t=/.test(url) && (kind === 'spine' || kind === 'front' || kind === 'back') ? url + (url.includes('?') ? '&' : '?') + 't=' + (kind === 'spine' ? 512 : kind === 'back' ? 1600 : 768) : url;
  // image textures are shared by url and counted, so a case that leaves the range around the selection can let go of its
  // cover / spine art (GPU copy and decoded image) without pulling it from another case that still shows it
  function loadTex(url, kind) {
    if (!url) return null; url = small(url, kind); if (tex.has(url)) { const t = tex.get(url); t.userData.refs = (t.userData.refs || 0) + 1; return t; } const THREE = T();
    const t = new THREE.Texture(); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = Math.min(perf().aniso, renderer.capabilities.getMaxAnisotropy()); t.userData.url = url; t.userData.refs = 1; tex.set(url, t);
    loadQ.push({ url, t, kind }); pump(); return t;
  }
  // ok() once the texture has its picture (at once if it already has), bad() if the picture can't be loaded
  function whenLoaded(t, ok, bad) { const st = t.userData.state; if (st === 'ok') ok(); else if (st === 'bad') bad(); else (t.userData.waiters = t.userData.waiters || []).push([ok, bad]); }
  function unref(t) {
    if (!t || --t.userData.refs > 0) return;
    t.userData.dead = true; if (tex.get(t.userData.url) === t) tex.delete(t.userData.url);
    const q = loadQ.findIndex(x => x.t === t); if (q >= 0) loadQ.splice(q, 1);
    try { if (t.image && t.image.close) t.image.close(); t.dispose(); } catch {} t.image = null;
  }
  // a drawn texture (spine text) or a shared image one: either way it's freed now, canvas included
  function freeMap(m) { if (!m) return; if (m.userData.url) { unref(m); return; } try { m.dispose(); if (m.image && m.image.getContext) m.image.width = m.image.height = 0; } catch {} }
  function canvasTex(w, h, draw, repeat) { const THREE = T(); const c = document.createElement('canvas'); c.width = w; c.height = h; draw(c.getContext('2d'), w, h); const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 4; if (repeat) { t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(repeat[0], repeat[1]); } return t; }
  // procedural materials: dark walnut (planks, uprights, baseboard) and dark stone tiles (floor)
  // ---- the room: lighting, walls, floor, shelf finish and a few extras, all live-adjustable (Room menu in the museum,
  // Settings → Museum). Textures are drawn once per choice and cached until the museum closes.
  const ROOM_DEF = { bright: 1, warmth: 0, spot: 1, wall: 'plaster', floor: 'stone', shelf: 'walnut', bench: false, plinths: false, ropes: false, posters: false, dust: 1, tvStand: 'cart', tvSize: 'm', tvPos: 'right', tvCh: 'ads', tvCrt: .6, tvNear: false, tvAnim: true };
  let room = Object.assign({}, ROOM_DEF), roomTex = new Map(), roomObj = null, woodT = null, floorT = null;
  const rnd = (a, b) => a + Math.random() * (b - a);
  const SHELF = { walnut: ['#3b2719', '20,10,5', '110,75,45'], oak: ['#86603c', '70,45,20', '200,160,110'], black: ['#141417', '0,0,0', '80,80,90'], white: ['#d6d0c4', '150,140,125', '255,252,246'] };
  const washTex = () => cachedTex('wash', () => canvasTex(256, 128, (x, w, h) => { const g = x.createLinearGradient(0, 0, 0, h); g.addColorStop(0, 'rgba(255,255,255,1)'); g.addColorStop(.25, 'rgba(255,255,255,.55)'); g.addColorStop(.7, 'rgba(255,255,255,.18)'); g.addColorStop(1, 'rgba(255,255,255,.08)'); x.fillStyle = g; x.fillRect(0, 0, w, h);
    x.globalCompositeOperation = 'destination-in'; const e = x.createLinearGradient(0, 0, w, 0); e.addColorStop(0, 'rgba(0,0,0,0)'); e.addColorStop(.06, 'rgba(0,0,0,1)'); e.addColorStop(.94, 'rgba(0,0,0,1)'); e.addColorStop(1, 'rgba(0,0,0,0)'); x.fillStyle = e; x.fillRect(0, 0, w, h); }));
  const beamTex = () => cachedTex('beam', () => canvasTex(64, 256, (x, w, h) => { const g = x.createLinearGradient(0, 0, 0, h); g.addColorStop(0, 'rgba(255,255,255,.9)'); g.addColorStop(.6, 'rgba(255,255,255,.25)'); g.addColorStop(1, 'rgba(255,255,255,0)'); x.fillStyle = g; x.fillRect(0, 0, w, h); const sg = x.createLinearGradient(0, 0, w, 0); sg.addColorStop(0, 'rgba(0,0,0,1)'); sg.addColorStop(.2, 'rgba(0,0,0,0)'); sg.addColorStop(.8, 'rgba(0,0,0,0)'); sg.addColorStop(1, 'rgba(0,0,0,1)'); x.globalCompositeOperation = 'destination-out'; x.fillStyle = sg; x.fillRect(0, 0, w, h); }));
  function cachedTex(key, make) { if (!roomTex.has(key)) roomTex.set(key, make()); return roomTex.get(key); }
  function woodOf(fin) {
    const [base, dark, light2] = SHELF[fin] || SHELF.walnut;
    return cachedTex('wood:' + fin, () => canvasTex(1024, 256, (x, w, h) => {
      x.fillStyle = base; x.fillRect(0, 0, w, h);
      for (let i = 0; i < 260; i++) { const y = Math.random() * h, a = .05 + Math.random() * .12; x.strokeStyle = `rgba(${Math.random() < .5 ? dark : light2},${a})`; x.lineWidth = .6 + Math.random() * 2.2; x.beginPath(); x.moveTo(0, y); for (let px = 0; px <= w; px += 64) x.lineTo(px, y + Math.sin(px / 90 + i) * 2.5); x.stroke(); }
      for (let i = 0; i < 5; i++) { const cx = Math.random() * w, cy = Math.random() * h; const g = x.createRadialGradient(cx, cy, 2, cx, cy, 26); g.addColorStop(0, `rgba(${dark},.35)`); g.addColorStop(1, `rgba(${dark},0)`); x.fillStyle = g; x.fillRect(cx - 30, cy - 30, 60, 60); }
    }, [2, 1]));
  }
  function wood() { woodT = woodOf(room.shelf); return woodT; }
  function floorOf(kind) {
    return cachedTex('floor:' + kind, () => {
      if (kind === 'wood') return canvasTex(512, 512, (x, w, h) => { for (let r = 0; r < 8; r++) { const y = r * 64, off = (r % 2) * 180; x.fillStyle = ['#3a2616', '#33210f', '#402a18'][r % 3]; x.fillRect(0, y, w, 64); for (let i = 0; i < 40; i++) { x.strokeStyle = `rgba(${Math.random() < .5 ? '15,8,3' : '120,85,50'},${rnd(.05, .14)})`; x.lineWidth = rnd(.5, 1.6); const yy = y + rnd(4, 60); x.beginPath(); x.moveTo(0, yy); x.lineTo(w, yy + rnd(-2, 2)); x.stroke(); } x.fillStyle = 'rgba(0,0,0,.55)'; x.fillRect(0, y, w, 2); for (let c = off; c < w + 200; c += 360) x.fillRect(c % w, y, 2, 64); } }, [6, 6]);
      if (kind === 'carpet') return canvasTex(256, 256, (x, w, h) => { x.fillStyle = '#2a1416'; x.fillRect(0, 0, w, h); for (let i = 0; i < 9000; i++) { x.fillStyle = `rgba(${Math.random() < .5 ? '80,30,34' : '10,4,6'},${rnd(.15, .45)})`; x.fillRect(Math.random() * w, Math.random() * h, 1.4, 1.4); } }, [16, 16]);
      if (kind === 'checker') return canvasTex(256, 256, (x, w, h) => { for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) { x.fillStyle = (i + j) % 2 ? '#d8d4cc' : '#16171b'; x.fillRect(i * 64, j * 64, 64, 64); } x.fillStyle = 'rgba(0,0,0,.35)'; for (let i = 0; i <= 4; i++) { x.fillRect(i * 64 - 1, 0, 2, h); x.fillRect(0, i * 64 - 1, w, 2); } }, [10, 10]);
      return canvasTex(512, 512, (x, w, h) => { x.fillStyle = '#121419'; x.fillRect(0, 0, w, h);
        for (let i = 0; i < 2; i++) for (let j = 0; j < 2; j++) { const g = x.createLinearGradient(i * 256, j * 256, i * 256 + 256, j * 256 + 256); g.addColorStop(0, '#171a21'); g.addColorStop(1, '#101217'); x.fillStyle = g; x.fillRect(i * 256 + 3, j * 256 + 3, 250, 250); }
        for (let i = 0; i < 1800; i++) { x.fillStyle = `rgba(255,255,255,${Math.random() * .035})`; x.fillRect(Math.random() * w, Math.random() * h, 1.5, 1.5); } }, [8, 8]);
    });
  }
  function floorTex() { floorT = floorOf(room.floor); return floorT; }
  const FLOOR_LOOK = { stone: { roughness: .32, metalness: .25 }, wood: { roughness: .45, metalness: .05 }, carpet: { roughness: .95, metalness: 0 }, checker: { roughness: .28, metalness: .1 } };
  // walls: a plain colour, or a drawn surface
  const WALL_COL = { plaster: 0x171a24, green: 0x14241b, burgundy: 0x2a1219, navy: 0x101a2e };
  function wallTex(kind) {
    if (kind === 'wood') return cachedTex('wall:wood', () => canvasTex(512, 512, (x, w, h) => { for (let c = 0; c < 8; c++) { x.fillStyle = ['#2b1c12', '#251810', '#2f1f14'][c % 3]; x.fillRect(c * 64, 0, 64, h); for (let i = 0; i < 30; i++) { x.strokeStyle = `rgba(${Math.random() < .5 ? '10,5,2' : '90,62,38'},${rnd(.05, .12)})`; x.lineWidth = rnd(.5, 1.5); const xx = c * 64 + rnd(3, 61); x.beginPath(); x.moveTo(xx, 0); x.lineTo(xx + rnd(-2, 2), h); x.stroke(); } x.fillStyle = 'rgba(0,0,0,.6)'; x.fillRect(c * 64, 0, 2, h); } }, [12, 3]));
    if (kind === 'brick') return cachedTex('wall:brick', () => canvasTex(512, 512, (x, w, h) => { x.fillStyle = '#1a1210'; x.fillRect(0, 0, w, h); for (let r = 0; r < 16; r++) for (let c = -1; c < 5; c++) { const bx = c * 128 + (r % 2) * 64 + 3, by = r * 32 + 3; const v = rnd(.75, 1.05); x.fillStyle = `rgb(${Math.round(92 * v)},${Math.round(40 * v)},${Math.round(32 * v)})`; x.fillRect(bx, by, 122, 26); for (let i = 0; i < 30; i++) { x.fillStyle = `rgba(0,0,0,${rnd(.05, .2)})`; x.fillRect(bx + rnd(0, 120), by + rnd(0, 24), 2, 2); } } }, [10, 4]));
    if (kind === 'wallpaper') return cachedTex('wall:paper', () => canvasTex(256, 256, (x, w, h) => { x.fillStyle = '#0f1c1d'; x.fillRect(0, 0, w, h); x.strokeStyle = 'rgba(200,170,100,.22)'; x.lineWidth = 2; for (const [cx, cy] of [[64, 64], [192, 192], [192, 64], [64, 192]]) { x.beginPath(); x.moveTo(cx, cy - 40); x.quadraticCurveTo(cx + 34, cy, cx, cy + 40); x.quadraticCurveTo(cx - 34, cy, cx, cy - 40); x.stroke(); x.beginPath(); x.arc(cx, cy, 6, 0, Math.PI * 2); x.stroke(); } x.fillStyle = 'rgba(200,170,100,.08)'; for (let i = 0; i < w; i += 32) x.fillRect(i, 0, 1, h); }, [24, 8]));
    return null;
  }
  function applyRoomSurfaces(noExtras) {
    if (!roomObj) return; const THREE = T(), o = roomObj;
    o.woodMat.map = wood(); o.woodMat.needsUpdate = true;
    const wt = wallTex(room.wall); o.bw.material.map = wt; o.bw.material.color.set(wt ? 0xffffff : (WALL_COL[room.wall] || WALL_COL.plaster)); o.bw.material.roughness = room.wall === 'wallpaper' ? .7 : .95; o.bw.material.needsUpdate = true;
    o.floor.material.map = floorTex(); Object.assign(o.floor.material, FLOOR_LOOK[room.floor] || FLOOR_LOOK.stone); o.floor.material.needsUpdate = true;
    if (!noExtras) buildExtras(); shadowDirty = true;
  }
  // extras: a rug in front of the shelves, a potted plant at each end, framed posters of your favourite / most played games
  function buildExtras() {
    if (!roomObj) return; const THREE = T(), o = roomObj;
    // the old set goes only after the new one has taken its pictures, so a poster both share isn't dropped and fetched again
    const old = o.extras, g = new THREE.Group(); o.extras = g; wall.add(g); const fy = o.bottom - .4;
    // the vitrine lights stay in the room (dark when there are no plinths): a change in the number of lights would recompile every material
    if (!o.plinthL) o.plinthL = [0, 1].map(() => { const pt = new THREE.PointLight(0xfff0dc, 0, 1.6, 2); wall.add(pt); return pt; }); o.plinthL.forEach(pt => { pt.intensity = 0; });
    // gallery pieces, kept to the sides and the foreground so they frame the shelves without ever covering a case
    const metal = new THREE.MeshStandardMaterial({ color: 0x2a2c31, metalness: .85, roughness: .3 }), brassM = new THREE.MeshStandardMaterial({ color: 0xb08a48, metalness: .9, roughness: .25 });
    const favs = (o.posterUrls || []).length ? o.posterUrls : [];
    const tvW = tvOn ? tvDims().W + 1.05 : 0, tvS0 = tvSide(), sides = [-2.05 - (tvS0 === 'left' ? tvW : 0), (o.rackEnd || o.maxW) + 2.05 + (tvS0 === 'right' ? tvW : 0)]; // gallery pieces step aside for the TV
    if (room.plinths) sides.forEach((px, k) => {
      // a white plinth with a glass vitrine; inside, one of your favourite games standing under its own small light
      const pl = new THREE.Mesh(new THREE.BoxGeometry(.72, 1.02, .72), new THREE.MeshStandardMaterial({ color: 0xe9e6df, roughness: .85 })); pl.position.set(px, fy + .51, .75); pl.castShadow = pl.receiveShadow = true; g.add(pl);
      const glass = new THREE.Mesh(new THREE.BoxGeometry(.6, .7, .6), new THREE.MeshStandardMaterial({ color: 0xddeeff, roughness: .02, metalness: .1, transparent: true, opacity: .12, depthWrite: false })); glass.position.set(px, fy + 1.02 + .35, .75); g.add(glass);
      const edgeM = new THREE.LineBasicMaterial({ color: 0xffffff, transparent: true, opacity: .35 }); const ed = new THREE.LineSegments(new THREE.EdgesGeometry(glass.geometry), edgeM); ed.position.copy(glass.position); g.add(ed);
      const url = favs[(k + 2) % Math.max(1, favs.length)] || favs[k];
      const caseM = new THREE.MeshStandardMaterial({ color: 0x1a1b20, roughness: .35 }); const cs = new THREE.Mesh(new THREE.BoxGeometry(.04, .55, .39), [caseM, caseM, caseM, caseM, caseM, caseM]); cs.position.set(px, fy + 1.02 + .3, .75); cs.rotation.y = -Math.PI / 2 + (k ? -.35 : .35); cs.castShadow = true; g.add(cs);
      if (url) { const t = loadTex(url, 'front'); g.userData.texs = (g.userData.texs || []).concat(t); const fm = new THREE.MeshStandardMaterial({ color: 0x222222, roughness: .35 }); cs.material = [fm, caseM, caseM, caseM, caseM, caseM]; whenLoaded(t, () => { if (o.extras !== g) return; fm.map = t; fm.color.set(0xffffff); fm.needsUpdate = true; }, () => {}); }
      const pt = o.plinthL[k]; pt.intensity = 1.4; pt.position.set(px, fy + 1.02 + .66, .78);
    });
    if (room.ropes) sides.forEach(px => {
      // brass stanchions with a red velvet rope in front of each side display
      const posts = [px - .62, px + .62].map(x => { const p = new THREE.Mesh(new THREE.CylinderGeometry(.03, .03, .95, 16), brassM); p.position.set(x, fy + .475, 1.55); p.castShadow = true; g.add(p); const top = new THREE.Mesh(new THREE.SphereGeometry(.05, 16, 12), brassM); top.position.set(x, fy + .97, 1.55); g.add(top); const base = new THREE.Mesh(new THREE.CylinderGeometry(.16, .18, .04, 24), brassM); base.position.set(x, fy + .02, 1.55); g.add(base); return x; });
      const curve = new THREE.CatmullRomCurve3([new THREE.Vector3(posts[0], fy + .9, 1.55), new THREE.Vector3(px, fy + .68, 1.55), new THREE.Vector3(posts[1], fy + .9, 1.55)]);
      const rope = new THREE.Mesh(new THREE.TubeGeometry(curve, 24, .022, 10, false), new THREE.MeshStandardMaterial({ color: 0x7a0e18, roughness: .85 })); rope.castShadow = true; g.add(rope);
    });
    if (room.bench) {
      // a museum bench in the foreground, facing the shelves (you see it from the wide view)
      const bz = 4.4, bx = o.maxW / 2, seat = new THREE.Mesh(new THREE.BoxGeometry(1.9, .1, .5), new THREE.MeshStandardMaterial({ color: 0x23201d, roughness: .55 })); seat.position.set(bx, fy + .46, bz); seat.castShadow = seat.receiveShadow = true; g.add(seat);
      const cush = new THREE.Mesh(new THREE.BoxGeometry(1.84, .05, .46), new THREE.MeshStandardMaterial({ color: 0x3a1f1c, roughness: .9 })); cush.position.set(bx, fy + .535, bz); g.add(cush);
      [[-.85, -.2], [-.85, .2], [.85, -.2], [.85, .2]].forEach(([dx, dz]) => { const leg = new THREE.Mesh(new THREE.BoxGeometry(.04, .41, .04), metal); leg.position.set(bx + dx, fy + .205, bz + dz); g.add(leg); });
    }
    if (room.posters) {
      const picks = (o.posterUrls || []).slice(0, 2);
      picks.forEach((url, k) => {
        const px = k ? (o.rackEnd || o.maxW) + 1.75 + (tvS0 === 'right' && room.tvStand === 'wall' ? tvW : 0) : -1.75 - (tvS0 === 'left' && room.tvStand === 'wall' ? tvW : 0), py = o.midY + .35, fw = .95, fh = 1.3;
        const frame = new THREE.Mesh(new THREE.BoxGeometry(fw + .1, fh + .1, .05), new THREE.MeshStandardMaterial({ color: 0x1c140c, roughness: .4, metalness: .2 })); frame.position.set(px, py, -DEPTH / 2 + .03); frame.castShadow = true; g.add(frame);
        const mat = new THREE.Mesh(new THREE.PlaneGeometry(fw, fh), new THREE.MeshStandardMaterial({ color: 0xeee8dc, roughness: .9 })); mat.position.set(px, py, -DEPTH / 2 + .056); g.add(mat);
        const t = loadTex(url, 'front'); const pm = new THREE.MeshStandardMaterial({ color: 0x222222, roughness: .5 }); const pic = new THREE.Mesh(new THREE.PlaneGeometry(fw * .8, fh * .8), pm); pic.position.set(px, py, -DEPTH / 2 + .058); g.add(pic);
        whenLoaded(t, () => { if (o.extras !== g) return; pm.map = t; pm.color.set(0xffffff); pm.needsUpdate = true; }, () => {});
        g.userData.texs = (g.userData.texs || []).concat(t);
      });
    }
    if (old) { old.traverse(n => { if (n.geometry) n.geometry.dispose(); if (n.material) { [].concat(n.material).forEach(m => { const mm = m.map; if (mm && !(mm.userData && mm.userData.url)) mm.dispose(); m.dispose(); }); } }); (old.userData.texs || []).forEach(unref); wall.remove(old); }
  }
  // settings from the shelf: any subset of ROOM_DEF's keys
  function setRoom(o) { const prev = Object.assign({}, room), ch = ks => ks.some(k => room[k] !== prev[k]); Object.assign(room, o || {}); applyLight(); if (ch(['wall', 'floor', 'shelf'])) applyRoomSurfaces(true); if (ch(['bench', 'plinths', 'ropes', 'posters'])) { buildExtras(); shadowDirty = true; } /* only what changed is rebuilt (each Room-menu step used to rebuild the extras) */ if (!roomObj) return;
    // the Retro TV in or out, or a new stand / size / place: rebuilt at once (the reading corner moves for a TV in the middle,
    // the gallery pieces step aside), and a short look at it from the Room menu; its picture settings apply on the next frame
    const tvNow = !!(o && 'tv' in o ? o.tv : tvOn), lay = tvNow !== tvOn || (tvNow && ch(['tvStand', 'tvSize', 'tvPos'])), rackMoves = (tvNow && room.tvPos === 'centre') !== (tvOn && prev.tvPos === 'centre') || (tvNow && room.tvPos === 'centre' && ch(['tvStand', 'tvSize']));
    if (lay) { const wasSel = wide && isTvSel(wideSel); tvOn = tvNow; if (!tvOn && tvView) { tvView = false; wide = true; wideSel = 0; } if (rackMoves && rack) setLibrary(lastLib); else { buildTV(); buildExtras(); } if (!tvOn) tvPeekAt = -1e9; else if (roomView) showTv(); if (wide) wideSel = wasSel && tv ? wideCount() - 1 : Math.min(wideSel, wideCount() - 1); hooks.onSelect && hooks.onSelect(current()); }
  }
  function showTv() { if (tv) { tvPeekAt = performance.now(); lastInput = woke(); } }
  // far rows get a small printed spine (a quarter of the pixels) so every case on the wall always shows its title;
  // the full-size spine and real spine art are kept for the rows around you
  const spineTexLite = (title, style) => canvasTex(48, 384, (x, w, h) => {
    x.fillStyle = style.caseColor; x.fillRect(0, 0, w, h);
    x.fillStyle = style.spineBand.bg; x.fillRect(0, 0, w, 45); x.fillStyle = style.spineBand.fg; x.font = '700 11px Lexend, sans-serif'; x.textAlign = 'center'; x.textBaseline = 'middle'; x.fillText(style.spineBand.label, w / 2, 23);
    x.save(); x.translate(w / 2, 45 + (h - 45) / 2); x.rotate(-Math.PI / 2); x.fillStyle = style.spineInk; x.font = '600 20px Lexend, sans-serif'; x.textAlign = 'center'; x.textBaseline = 'middle';
    let s = title; while (x.measureText(s).width > h - 75 && s.length > 4) s = s.slice(0, -2); if (s !== title) s += '…'; x.fillText(s, 0, 0); x.restore();
  });
  const spineTex = (title, style) => canvasTex(96, 768, (x, w, h) => {
    x.fillStyle = style.caseColor; x.fillRect(0, 0, w, h);
    x.fillStyle = style.spineBand.bg; x.fillRect(0, 0, w, 90); x.fillStyle = style.spineBand.fg; x.font = '700 22px Lexend, sans-serif'; x.textAlign = 'center'; x.textBaseline = 'middle'; x.fillText(style.spineBand.label, w / 2, 45);
    x.save(); x.translate(w / 2, 90 + (h - 90) / 2); x.rotate(-Math.PI / 2); x.fillStyle = style.spineInk; x.font = '600 40px Lexend, sans-serif'; x.textAlign = 'center'; x.textBaseline = 'middle';
    let s = title; while (x.measureText(s).width > h - 150 && s.length > 4) s = s.slice(0, -2); if (s !== title) s += '…'; x.fillText(s, 0, 0); x.restore();
  });
  const plaqueTex = (row) => canvasTex(1024, 384, (x, w, h) => {
    const g0 = x.createLinearGradient(0, 0, w, h); g0.addColorStop(0, '#5a4524'); g0.addColorStop(.5, '#8a6a34'); g0.addColorStop(1, '#4e3a1e'); x.fillStyle = g0; x.fillRect(0, 0, w, h);
    x.fillStyle = '#1c150c'; x.fillRect(16, 16, w - 32, h - 32);
    x.strokeStyle = 'rgba(255,220,160,.55)'; x.lineWidth = 4; x.strokeRect(30, 30, w - 60, h - 60);
    x.fillStyle = '#f3e7c9'; x.textAlign = 'left'; x.font = '700 74px Lexend, sans-serif'; x.fillText(row.name, 64, 124);
    x.font = '400 34px Lexend, sans-serif'; x.fillStyle = 'rgba(243,231,201,.85)';
    const n = row.games.length, lines = [row.stats.played ? `${n} ${n === 1 ? 'game' : 'games'} · ${row.stats.played} played · ${row.stats.hours}` : `${n} ${n === 1 ? 'game' : 'games'} · nothing played yet`];
    if (row.stats.top) lines.push(`Most played: ${row.stats.top}`); if (row.stats.fav) lines.push(`${row.stats.fav} favorite${row.stats.fav === 1 ? '' : 's'}`);
    if (!(row.console && row.console.model)) lines.push(`${hooks.okWord ? hooks.okWord() : 'A'}: add a 3D model of your console`); // (beta.11: A opens Add a 3D model)
    lines.forEach((l, i) => x.fillText(l, 64, 204 + i * 52));
  });
  const CARD_S = 1.16; // the museum card's size on the shelf
  // small museum card shown under a pulled-out case
  // one card picture for the whole visit: each new selection is drawn over the same canvas (t0) instead of a new 1800×880 texture
  const cardTex = (g, row, t0) => { const K = window.Perf && Perf.get().level === 'handheld' ? 1 : 2; /* Handheld: 1× (900×440) is still above what a 6" screen shows of the card, and 4× fewer pixels to draw and upload per case */ const draw = (x, W, Hh) => {
    x.scale(K, K); const w = 900, h = 440; // drawn at 2× so the small print stays sharp up close
    // a gallery wall label: title, maker and year, publisher and genre, rating, then your own history with it
    const pg = x.createLinearGradient(0, 0, 0, h); pg.addColorStop(0, '#f6f1e6'); pg.addColorStop(1, '#e2d9c6'); x.fillStyle = pg; x.fillRect(0, 0, w, h); // paper, lit from above
    x.strokeStyle = 'rgba(120,100,70,.35)'; x.lineWidth = 2; x.strokeRect(14, 14, w - 28, h - 28); x.strokeStyle = 'rgba(255,255,255,.7)'; x.lineWidth = 1; x.strokeRect(16, 16, w - 32, h - 32); // pressed border
    const fit = (t, max) => { if (x.measureText(t).width <= max) return t; while (t.length > 4 && x.measureText(t + '…').width > max) t = t.slice(0, -1); return t + '…'; };
    const badge = g.metacritic ? 92 : 0; x.textAlign = 'left'; x.fillStyle = '#100d0b'; x.font = '700 50px Lexend, sans-serif'; x.fillText(fit(g.title, w - 80 - badge), 40, 74);
    x.font = 'italic 500 30px Lexend, Georgia, serif'; x.fillStyle = '#2e2822';
    const l1 = [g.dev, g.year].filter(Boolean).join(', ') || row.name; x.fillText(fit(l1, w - 80 - badge), 40, 118);
    x.font = '500 26px Lexend, sans-serif'; x.fillStyle = '#463e35';
    const l2 = [g.pub && g.pub !== g.dev ? g.pub : null, g.genre, row.name].filter(Boolean).join('  ·  '); x.fillText(fit(l2, w - 80), 40, 158);
    x.fillStyle = 'rgba(20,17,16,.22)'; x.fillRect(40, 178, w - 80, 2);
    // ratings line
    let y = 228;
    if (g.rating) { const r = Math.max(0, Math.min(5, +g.rating)); x.font = '400 32px Lexend, sans-serif'; for (let i = 0; i < 5; i++) { const f = Math.max(0, Math.min(1, r - i)); x.fillStyle = '#cdbf9f'; x.fillText('★', 40 + i * 34, y); if (f > 0) { x.save(); x.beginPath(); x.rect(40 + i * 34, y - 32, 30 * f, 40); x.clip(); x.fillStyle = '#b8862b'; x.fillText('★', 40 + i * 34, y); x.restore(); } }
      x.font = '600 24px Lexend, sans-serif'; x.fillStyle = '#2e2822'; x.fillText(r.toFixed(1) + ' community', 40 + 5 * 34 + 12, y - 6); }
    if (g.metacritic) { const c = g.metacritic >= 75 ? '#2f7a22' : g.metacritic >= 50 ? '#b0860f' : '#a3301f', bx = w - 40 - 78; x.fillStyle = c; x.fillRect(bx, 32, 78, 58); x.fillStyle = '#fff'; x.font = '700 36px Lexend, sans-serif'; x.textAlign = 'center'; x.fillText(String(g.metacritic), bx + 39, 74); x.font = '600 12px Lexend, sans-serif'; x.fillStyle = '#5a5146'; x.fillText('METASCORE', bx + 39, 106); x.textAlign = 'left'; }
    if (g.rating) y += 46; else y -= 10;
    // your history with it: time, sessions, last played; then since when it has been in the collection
    x.font = '600 28px Lexend, sans-serif'; x.fillStyle = '#16120f';
    const plays = g.plays ? `${g.plays} ${g.plays === 1 ? 'session' : 'sessions'}` : '';
    x.fillText(fit(g.hours ? [g.hours, plays, g.last ? 'last played ' + g.last : ''].filter(Boolean).join('  ·  ') : 'Not played yet', w - 80), 40, y);
    if (g.added) { y += 38; x.font = 'italic 500 25px Lexend, Georgia, serif'; x.fillStyle = '#3f372f'; let d = ''; try { d = new Date(g.added).toLocaleDateString(undefined, { month: 'long', year: 'numeric' }); } catch {} if (d) x.fillText('In your collection since ' + d, 40, y); }
    // what's in the box: owned pieces as filled chips, missing ones as faint outlines; then players and age rating
    y += 24; let cx = 40;
    const chip = (label, on) => { x.font = '600 22px Lexend, sans-serif'; const tw = x.measureText(label).width + 32; if (cx + tw > w - 40) return; const r = 18, ch = 36;
      x.beginPath(); x.moveTo(cx + r, y); x.arcTo(cx + tw, y, cx + tw, y + ch, r); x.arcTo(cx + tw, y + ch, cx, y + ch, r); x.arcTo(cx, y + ch, cx, y, r); x.arcTo(cx, y, cx + tw, y, r); x.closePath();
      if (on) { x.fillStyle = '#2c241b'; x.fill(); x.fillStyle = '#f6eedc'; } else { x.fillStyle = 'rgba(60,50,40,.08)'; x.fill(); x.strokeStyle = 'rgba(60,50,40,.55)'; x.lineWidth = 2; x.stroke(); x.fillStyle = 'rgba(45,38,30,.78)'; }
      x.fillText(label, cx + 16, y + 25); cx += tw + 10; };
    chip(g.manual ? '✓ Manual' : 'No manual', g.manual); chip(g.boxFull ? '✓ Full box art' : 'Box art incomplete', g.boxFull); if (g.media !== 'cart') { const mw = g.media === 'umd' ? 'UMD' : 'Disc'; chip(g.disc ? `✓ ${mw} art` : `No ${mw} art`, !!g.disc); }
    if (g.players) chip(+g.players > 1 ? `1–${g.players} players` : '1 player', true); if (g.esrb) chip(String(g.esrb).replace(/\s*\(.*\)$/, '').replace(/^Everyone 10\+$/i, 'E10+').replace(/^Everyone$/i, 'E').replace(/^Teen$/i, 'T').replace(/^Mature$/i, 'M'), true);
    x.font = '600 23px Lexend, sans-serif'; x.fillStyle = '#4f463b'; x.fillText((g.fav ? '♥ favorite    ' : '') + cardKeys(), 40, h - 28); // what the confirm button does here now (A no longer goes to Inspect, X no longer opens from the shelf)
  }; if (t0 && t0.image && t0.image.getContext && t0.image.width === 900 * K) { const x = t0.image.getContext('2d'); if (x.reset) x.reset(); else { x.setTransform(1, 0, 0, 1, 0, 0); x.clearRect(0, 0, 900 * K, 440 * K); } draw(x, 900 * K, 440 * K); t0.needsUpdate = true; return t0; } const t = canvasTex(900 * K, 440 * K, draw); t.anisotropy = 8; return t; };
  // brass divider plate on the plank's front edge where a new letter / year / genre starts
  function drawTab(x, label, w, h) {
    const g0 = x.createLinearGradient(0, 0, 0, h); g0.addColorStop(0, '#d2b273'); g0.addColorStop(.5, '#8a6a34'); g0.addColorStop(1, '#5e4520'); x.fillStyle = g0; x.fillRect(0, 0, w, h);
    x.fillStyle = 'rgba(255,235,190,.75)'; x.fillRect(0, 0, 6, h); // bright left edge: "the section starts here"
    x.fillStyle = '#1c150c'; x.textAlign = 'left'; x.textBaseline = 'middle'; let fs = 50; x.font = `700 ${fs}px Lexend, sans-serif`;
    while (x.measureText(label).width > w - 22 && fs > 16) { fs -= 2; x.font = `700 ${fs}px Lexend, sans-serif`; } x.fillText(label, 14, h / 2 + 2);
  }


  // original stand-in console: a low rounded slab in the shelf's colours with a lit strip, and a controller pebble
  function standIn(style, w, h, d) {
    const THREE = T(), RB = window.BS3D.RoundedBoxGeometry, g = new THREE.Group();
    const body = new THREE.Mesh(new RB(w, h, d, 4, Math.min(.06, h * .35)), new THREE.MeshPhysicalMaterial({ color: style.caseSide, roughness: .45, metalness: .1, clearcoat: .5 })); body.castShadow = true; g.add(body);
    const strip = new THREE.Mesh(new THREE.BoxGeometry(w * .5, h * .12, .01), new THREE.MeshBasicMaterial({ color: style.glow })); strip.position.set(0, -h * .2, d / 2 + .005); g.add(strip);
    const top = new THREE.Mesh(new RB(w * .9, .01, d * .9, 2, .005), new THREE.MeshPhysicalMaterial({ color: style.caseColor, roughness: .3, clearcoat: .7 })); top.position.y = h / 2; g.add(top);
    const pad = new THREE.Mesh(new RB(w * .32, h * .35, d * .5, 3, .05), new THREE.MeshPhysicalMaterial({ color: style.caseColor, roughness: .6 })); pad.position.set(w * .1, -h / 2 + h * .18, d / 2 + d * .4); pad.castShadow = true; g.add(pad);
    return g;
  }
  /* Your own 3D console (themes\<system>\console.glb), fitted by ConsoleFit: only the console itself counts for the size
     (a bundled controller, disc or cable is split off and hidden unless you switch it on), it turns to face you (the
     guessed front, or your choice saved in console.json), fills the console slot and stands on the shelf. A controller
     (one from the model, or themes\<system>\controller.glb) lies on the shelf in front of the name plate. */
  const modelCache = new Map(); // url -> Promise<{ scene, a }>
  function getModel(url, hide) {
    const k = url + '|' + (hide === false ? '' : hide || '');
    if (!modelCache.has(k)) {
      // a new version of the same file (its ?v= changes when it's replaced or optimised): the old one is never asked for again,
      // so let it go (its geometry and textures were kept for the whole session before)
      const base = String(url).split('?')[0];
      for (const [k2, p2] of modelCache) if (k2 !== k && k2.split('|')[0] !== url && k2.split('|')[0].split('?')[0] === base) { modelCache.delete(k2); p2.then(m => { try { const sc = m && (m.scene || m); if (sc && sc.traverse) sc.traverse(n => { if (n.geometry) n.geometry.dispose(); (Array.isArray(n.material) ? n.material : n.material ? [n.material] : []).forEach(mt => { for (const v of Object.values(mt)) if (v && v.isTexture) v.dispose(); mt.dispose(); }); }); } catch {} }, () => {}); }
    }
    if (!modelCache.has(k)) modelCache.set(k, new window.BS3D.GLTFLoader().loadAsync(url).then(async gltf => { if (!active) await idleStep(); await ConsoleFit.fixMaterials(gltf); if (!active) await idleStep(); /* preloading on the shelf: each step in a quiet moment */ liftMaterials(gltf.scene); gltf.scene.traverse(n => { if (n.isMesh) n.castShadow = true; }); return { scene: gltf.scene, a: ConsoleFit.analyze(gltf.scene, hide) }; }).catch(e => { modelCache.delete(k); throw e; }));
    return modelCache.get(k);
  }
  // read and analyse a console model ahead of time (the shelf does this while you're idle), so the museum opens without the wait
  function preloadModel(url, opts) { if (!url || !ensure()) return Promise.resolve(); const o = opts || {}; const t0 = performance.now(), had = modelCache.has(url + '|' + (o.hide === false ? '' : o.hide || ''));
    return getModel(url, o.hide).then(() => had ? 0 : Math.round(performance.now() - t0), () => null); }
  // The gallery is dimly lit and a console stood in its slot with only the lamp's edge on it, so it read as a dark shape
  // sitting alone. Its materials get a little more of the room's reflections and a faint glow of their own colour (the
  // same lift the cases get), so it reads clearly at any light setting. Parts that already glow (a PC's lights) are kept.
  function liftMaterials(root) { root.traverse(n => { if (!n.isMesh) return; (Array.isArray(n.material) ? n.material : [n.material]).forEach(m => {
    if (!m || m.userData.lifted || !m.isMeshStandardMaterial) return; m.userData.lifted = true;
    m.envMapIntensity = Math.max(m.envMapIntensity == null ? 1 : m.envMapIntensity, 1.6);
    if (m.emissive && m.emissive.getHex() === 0) { if (m.map) { m.emissiveMap = m.map; m.emissive.set(0xffffff); m.emissiveIntensity = .1; } else { m.emissive.copy(m.color).multiplyScalar(.1); m.emissiveIntensity = 1; } }
    m.needsUpdate = true; }); }); }
  const yawOf = (o, a) => o.museumYaw != null && isFinite(+o.museumYaw) ? +o.museumYaw : a.yaw;
  function loadModel(row) {
    const THREE = T(), c0 = row.console; if (!c0 || !c0.model) return; const url = c0.model, gen = row.fitGen = (row.fitGen || 0) + 1; const tL = performance.now(), warmHit = modelCache.has(url + '|' + (((c0.opts || {}).hide === false) ? '' : (c0.opts || {}).hide || ''));
    const o = Object.assign({ yaw: 0, pitch: 0, roll: 0, scale: 1 }, c0.opts || {});
    getModel(url, o.hide).then(({ scene, a }) => {
      if (!row.consoleG || !rows.includes(row) || row.fitGen !== gen) return;
      row.fit = a;
      const m = scene.clone(true); m.traverse(n => { n.userData.shared = true; }); ConsoleFit.show(m, a, Object.assign({}, o, { controller: ctlChoice(row, o, a) }), 'console'); /* the controller on the shelf isn't also drawn on the console */
      const yaw = yawOf(o, a), inner = new THREE.Group(); inner.add(m);
      // the size is worked out with the console at its automatic facing, so turning it (Fit on shelf → Facing) only turns
      // it: it used to be refitted at every angle, which made it grow and shrink as it turned
      inner.rotation.set((o.pitch || 0) * DEG, a.yaw * DEG, (o.roll || 0) * DEG); inner.updateMatrixWorld(true);
      const size = ConsoleFit.visibleBox(inner).getSize(new THREE.Vector3());
      inner.rotation.set((o.pitch || 0) * DEG, yaw * DEG, (o.roll || 0) * DEG); inner.updateMatrixWorld(true);
      const box = ConsoleFit.visibleBox(inner), c = box.getCenter(new THREE.Vector3()), sizeNow = box.getSize(new THREE.Vector3());
      /* fit the shelf space: no wider than the console slot, no taller than the gap under the plank above; a long console
         turned to face you may run deeper than the shelf, so it sits against the back wall and pokes out a little */
      const sc = Math.max(.3, Math.min(2, +o.museumScale || 1));
      const s = Math.min((row.consoleW * .95) / Math.max(size.x, .001), (H * .92) / Math.max(size.y, .001), (DEPTH * .9) / Math.max(size.z, .001)) * sc; /* stays on the shelf; only a bigger Size can make it hang over */
      const holder = new THREE.Group(); holder.add(inner); holder.scale.setScalar(s);
      const zBack = -DEPTH / 2 + .04, z0 = sizeNow.z * s <= DEPTH * .95 ? -c.z * s : zBack - box.min.z * s;
      holder.position.set(-c.x * s, -box.min.y * s, z0);
      if (!row.consoleG.userData.model) row.consoleG.traverse(n => { if (n.geometry) n.geometry.dispose(); if (n.material) (Array.isArray(n.material) ? n.material : [n.material]).forEach(x => x.dispose()); });
      row.consoleG.clear(); row.consoleG.add(holder); row.consoleG.userData.model = true; row.consoleScale = s; shadowDirty = true; if (hooks.log) hooks.log(`museum: ${row.id} console placed in ${Math.round(performance.now() - tL)} ms${warmHit ? ' (ready ahead of time)' : ' (read now)'}`);
      placeController(row, o, a, scene, yaw, s, gen);
    }).catch(() => { row.fitFailed = true; });
  }
  // which controller lies on the shelf: yours if you chose one; otherwise the controller.glb you added, or the one that
  // came in the console model (a GameCube model usually has its pad: it used to stay hidden, leaving the console alone)
  function ctlChoice(row, o, a) { if (o.controller) return o.controller; if (row.console && row.console.controller) return 'file'; return (a && a.controller) || 'none'; }
  function clearController(row) { if (row.ctlG) { row.group.remove(row.ctlG); row.ctlG = null; shadowDirty = true; } }
  function placeController(row, o, a, scene, yaw, s, gen) {
    const THREE = T(), want = ctlChoice(row, o, a);
    const put = (obj, oyaw, scale) => {
      if (!rows.includes(row) || row.fitGen !== gen) return; clearController(row);
      const inner = new THREE.Group(); inner.add(obj); inner.rotation.y = oyaw * DEG; inner.updateMatrixWorld(true);
      const box = ConsoleFit.visibleBox(inner); if (box.isEmpty()) return; const sz = box.getSize(new THREE.Vector3()), c = box.getCenter(new THREE.Vector3());
      let k = scale(sz); k = Math.min(k, .95 / Math.max(sz.x, .001), .5 / Math.max(sz.z, .001), H * .3 / Math.max(sz.y, .001)); /* lies in front of the plate, never towers over it */
      const holder = new THREE.Group(); holder.add(inner); holder.scale.setScalar(k);
      const g = new THREE.Group(); g.add(holder); holder.position.set(-c.x * k, -box.min.y * k, -c.z * k);
      g.position.set(row.consoleX + row.consoleW / 2 + .5, -H / 2 + .002, (DEPTH + .1) / 2 - sz.z * k / 2 - .04); g.rotation.y = -12 * DEG;
      row.group.add(g); row.ctlG = g; shadowDirty = true;
    };
    const cy = o.controllerYaw != null && isFinite(+o.controllerYaw) ? +o.controllerYaw : null;
    if (want === 'file' && row.console.controller) {
      getModel(row.console.controller, false).then(({ scene: cs, a: ca }) => { const m = cs.clone(true); m.traverse(n => { n.userData.shared = true; }); ConsoleFit.show(m, ca, {}, 'console'); put(m, cy != null ? cy : ca.yaw, sz => H * .72 / Math.max(sz.x, sz.z, .001)); }).catch(() => clearController(row));
    } else if (want !== 'none' && a.parts.some(p => p.key === want && !p.main)) {
      const m = scene.clone(true); m.traverse(n => { n.userData.shared = true; }); ConsoleFit.show(m, a, Object.assign({}, o, { controller: want }), 'controller'); put(m, cy != null ? cy : yaw, () => s);
    } else clearController(row);
  }

  const mesh0z = w => -DEPTH / 2 + w / 2 + .02;
  // free GPU resources from the previous wall (image textures in `tex` are kept and reused; canvas textures are per build)
  function disposeWall() {
    // the reading corner (its placeholder / loaded covers), an open box of magazines and the TV (its static, clock and
    // scanline pictures) free their own textures first; whatever is left goes with the wall below
    disposeRack(); closeFan(true); disposeTV();
    const keep = new Set([...tex.values(), ...roomTex.values()]); roomObj = null;
    wall.traverse(o => { if (o.userData.shared) return; /* a 3D console: its geometry, materials and textures belong to the cached model, kept for the next visit */ if (o.geometry) o.geometry.dispose(); const ms = o.material ? (Array.isArray(o.material) ? o.material : [o.material]) : []; ms.forEach(m => { if (m.map && !keep.has(m.map)) m.map.dispose(); if (m.emissiveMap && m.emissiveMap !== m.map && !keep.has(m.emissiveMap)) m.emissiveMap.dispose(); m.dispose(); }); if (o.isLight && o.shadow && o.shadow.map) { o.shadow.map.dispose(); o.shadow.map = null; } });
    wall.clear();
  }
  // ---- mail: new games arrive as one delivery. Their places on the shelf stay closed up, and as the walk-in ends a
  // parcel (a wooden crate for five or more) pops up in front of you. Hold A to open it: each game rises out showing its
  // cover, then flies to its own shelf, where the cases beside its place shuffle apart to let it in. A or B skips to the
  // end. All of it is a handful of plain boxes and two canvas textures, built (and its shaders compiled) on entering. ----
  let mailSet = new Set(), mailAt = {}, cardT = null, owner = ''; // owner: your RetroAchievements name, for the label
  const CRATE_MIN = 5, DV_DIST = 2.05, DV_S = .4;
  let delivery = null, dvAnchor = null, dvV = null; // the delivery: { g, crate, list, mats, ph, w, userData:{pull}, t, shown, open }
  function boxTex() { if (cardT && cardT.image && cardT.image.width) return cardT; cardT = canvasTex(256, 256, (x, w, h) => { x.fillStyle = '#b98b55'; x.fillRect(0, 0, w, h);
      for (let i = 0; i < 900; i++) { const v = Math.random(); x.fillStyle = v > .5 ? `rgba(255,230,190,${Math.random() * .08})` : `rgba(70,40,10,${Math.random() * .08})`; x.fillRect(Math.random() * w, Math.random() * h, 1 + Math.random() * 3, 1); }
      for (let y = 0; y < h; y += 7) { x.fillStyle = 'rgba(80,50,20,.05)'; x.fillRect(0, y, w, 2); } }); cardT.userData.shared = true; return cardT; }
  const dvShelf = list => { const names = [...new Set(list.map(b => (rows[b.userData.ri] || {}).name || ''))]; return names.length === 1 ? names[0] : `${names.length} shelves`; };
  function buildDelivery(list) {
    const THREE = T(), n = list.length, crate = n >= CRATE_MIN, shelf = String(dvShelf(list)).toUpperCase();
    const caseW = Math.max(...list.map(b => (rows[b.userData.ri] || {}).w || H * .7)), ph = H * (crate ? 1.04 : 1.06), pw = crate ? Math.max(H * 1.75, caseW * 2.2) : Math.max(caseW * 1.14, H * Math.min(1.3, .7 + .14 * (n - 1))), pd = crate ? H * .85 : H * .7, t = crate ? .02 : .012;
    const g = new THREE.Group(), mats = [], add = (geo, mat, x, y, z, par) => { const m = new THREE.Mesh(geo, mat); m.position.set(x, y, z); (par || g).add(m); return m; };
    const rec = n === 1 ? (mailAt[String(list[0].userData.g.id)] || { at: Date.now(), no: 0 }) : { at: Date.now(), no: 0 };
    const date = new Date(rec.at).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
    const lines = (x, W, k, v, y, small) => { x.font = '600 12px Lexend, sans-serif'; x.fillStyle = '#6b5a44'; x.fillText(k, 26, y); x.font = `700 ${small ? 18 : 19}px Lexend, sans-serif`; x.fillStyle = '#2a2018'; let s = String(v); while (x.measureText(s).width > W - 52 && s.length > 3) s = s.slice(0, -1); x.fillText(s, 26, y + 21); };
    let lid = null, flaps = null, tapeF = null;
    if (crate) {
      const slat = canvasTex(256, 256, (x, W, Hh) => { x.fillStyle = '#c99b62'; x.fillRect(0, 0, W, Hh); for (let i = 0; i < 4; i++) { const y = i * Hh / 4; x.fillStyle = `rgb(${190 + (i * 13) % 20},${142 + (i * 7) % 14},${88 + (i * 5) % 12})`; x.fillRect(0, y + 3, W, Hh / 4 - 6); x.fillStyle = 'rgba(60,35,10,.55)'; x.fillRect(0, y, W, 3);
          for (let k = 0; k < 40; k++) { x.fillStyle = `rgba(90,55,20,${Math.random() * .12})`; x.fillRect(Math.random() * W, y + 4 + Math.random() * (Hh / 4 - 10), 20 + Math.random() * 60, 1); } x.fillStyle = 'rgba(40,30,20,.7)'; x.fillRect(10, y + Hh / 8, 4, 4); x.fillRect(W - 14, y + Hh / 8, 4, 4); } });
      slat.wrapS = slat.wrapT = THREE.RepeatWrapping; slat.repeat.set(Math.max(1, pw / .55), 1);
      const wood = new THREE.MeshStandardMaterial({ map: slat, roughness: .85, transparent: true }), dark = new THREE.MeshStandardMaterial({ color: 0x3a2814, roughness: 1, transparent: true }); // see-through-capable from the start: no shader rebuild when it fades
      add(new THREE.BoxGeometry(pw, t, pd), wood, 0, -ph / 2 + t / 2, 0); add(new THREE.BoxGeometry(pw, ph, t), dark, 0, 0, -pd / 2 + t / 2);
      [-1, 1].forEach(sx => add(new THREE.BoxGeometry(t, ph, pd), wood, sx * (pw / 2 - t / 2), 0, 0)); add(new THREE.BoxGeometry(pw, ph, t), wood, 0, 0, pd / 2 - t / 2);
      lid = new THREE.Group(); lid.position.set(0, ph / 2, 0); g.add(lid); add(new THREE.BoxGeometry(pw + .02, t * 1.4, pd + .02), wood, 0, t * .7, 0, lid);
      const sh = ph * .26, sten = canvasTex(512, 200, (x, W, Hh) => { x.clearRect(0, 0, W, Hh); x.fillStyle = 'rgba(30,20,10,.82)'; x.textAlign = 'center'; x.font = '900 86px Lexend, sans-serif'; x.fillText('BOXSHELF', W / 2, 96); x.font = '800 30px Lexend, sans-serif'; x.fillText("COLLECTOR'S CRATE", W / 2, 150); x.font = '700 22px Lexend, sans-serif'; x.fillText('↑  THIS SIDE UP  ↑', W / 2, 186); });
      const stenM = new THREE.MeshStandardMaterial({ map: sten, transparent: true, roughness: .9 }); add(new THREE.PlaneGeometry(sh * 512 / 200, sh), stenM, -pw * .17, ph * .14, pd / 2 + .003);
      const lw = H * .36, listT = canvasTex(300, 360, (x, W, Hh) => { x.fillStyle = '#f4efe4'; x.fillRect(0, 0, W, Hh); x.strokeStyle = '#2a2018'; x.lineWidth = 3; x.strokeRect(6, 6, W - 12, Hh - 12); x.fillStyle = '#2a2018'; x.textAlign = 'center'; x.font = '800 24px Lexend, sans-serif'; x.fillText('PACKING LIST', W / 2, 44); x.fillRect(22, 58, W - 44, 2); x.textAlign = 'left';
          lines(x, W, 'CONTENTS', `${n} games`, 90); lines(x, W, 'SHELF', shelf, 140); lines(x, W, 'DELIVERED TO', owner || 'The Collection', 190); lines(x, W, 'CONDITION', 'Factory sealed', 240);
          x.font = '500 12px Lexend, sans-serif'; x.fillStyle = '#6b5a44'; x.fillText('Arrived ' + date, 26, 300); x.fillStyle = '#2a2018'; for (let i = 0; i < 40; i++) x.fillRect(26 + i * 6.2, 312, 1 + (i * 7 % 3), 30); });
      const listM = new THREE.MeshStandardMaterial({ map: listT, transparent: true, roughness: .7 }); add(new THREE.PlaneGeometry(lw, lw * 360 / 300), listM, pw / 2 - lw * .72, -ph * .1, pd / 2 + .004);
      mats.push(wood, dark, stenM, listM);
    } else {
      const card = new THREE.MeshStandardMaterial({ map: boxTex(), roughness: .9, metalness: 0, transparent: true }), inner = new THREE.MeshStandardMaterial({ color: 0x5a3c1c, roughness: 1, transparent: true });
      const tapeM = new THREE.MeshStandardMaterial({ color: 0xd9b27a, roughness: .35, metalness: 0, transparent: true, opacity: .92 });
      add(new THREE.BoxGeometry(pw, t, pd), card, 0, -ph / 2 + t / 2, 0); [-1, 1].forEach(sx => add(new THREE.BoxGeometry(t, ph, pd), card, sx * (pw / 2 - t / 2), 0, 0));
      add(new THREE.BoxGeometry(pw, ph, t), inner, 0, 0, -pd / 2 + t / 2); add(new THREE.BoxGeometry(pw, ph, t), card, 0, 0, pd / 2 - t / 2);
      const label = canvasTex(320, 420, (x, W, Hh) => { x.fillStyle = '#f4efe4'; x.fillRect(0, 0, W, Hh); x.strokeStyle = '#2a2018'; x.lineWidth = 4; x.strokeRect(8, 8, W - 16, Hh - 16);
          x.fillStyle = '#2a2018'; x.textAlign = 'center'; x.font = '800 28px Lexend, sans-serif'; x.fillText('BOXSHELF', W / 2, 50); x.font = '700 13px Lexend, sans-serif'; x.fillText("COLLECTOR'S DELIVERY", W / 2, 70); x.fillRect(24, 84, W - 48, 3); x.textAlign = 'left';
          lines(x, W, 'DELIVERED TO', owner || 'The Collection', 108, 1); lines(x, W, 'SHELF', shelf, 152, 1);
          lines(x, W, n === 1 ? 'ITEM' : 'CONTENTS', n === 1 ? (rec.no ? 'No. ' + String(rec.no).padStart(4, '0') : 'New arrival') : `${n} games`, 196, 1); lines(x, W, 'CONDITION', 'Factory sealed', 240, 1);
          x.font = '500 12px Lexend, sans-serif'; x.fillStyle = '#6b5a44'; x.fillText('Arrived ' + date, 26, 290); x.fillStyle = '#2a2018'; for (let i = 0; i < 46; i++) x.fillRect(26 + i * 5.6, 300, 1 + (i * 7 % 3), 46);
          x.save(); x.translate(W / 2 + 34, 384); x.rotate(-.1); x.strokeStyle = '#b3261e'; x.fillStyle = '#b3261e'; x.lineWidth = 4; x.strokeRect(-104, -22, 208, 42); x.font = '800 19px Lexend, sans-serif'; x.textAlign = 'center'; x.fillText('HANDLE WITH CARE', 0, 7); x.restore(); });
      const labM = new THREE.MeshStandardMaterial({ map: label, roughness: .7, transparent: true }), lh = Math.min(ph * .8, pw * .78 * 420 / 320); add(new THREE.PlaneGeometry(lh * 320 / 420, lh), labM, n === 1 ? 0 : -pw * .18, -ph * .06, pd / 2 + .002);
      flaps = [-1, 1].map(sx => { const hinge = new THREE.Group(); hinge.position.set(sx * pw / 2, ph / 2, 0); g.add(hinge); add(new THREE.BoxGeometry(pw / 2, t, pd), card, -sx * pw / 4, t / 2, 0, hinge); add(new THREE.BoxGeometry(pw * .09, t * .5, pd * 1.02), tapeM, -sx * (pw / 2 - pw * .045), t + .002, 0, hinge); return hinge; });
      tapeF = add(new THREE.BoxGeometry(pw * .18, ph * .22, t * .5), tapeM, 0, ph / 2 - ph * .11, pd / 2 + .004);
      mats.push(card, inner, tapeM, labM);
    }
    // parked out of sight until it's shown (it's in the scene now so its shaders compile with everything else)
    g.position.set(0, -50, 0); g.scale.setScalar(DV_S); scene.add(g);
    if (!dvAnchor) { dvAnchor = new THREE.Object3D(); } scene.add(dvAnchor); if (!dvV) dvV = { a: new THREE.Vector3(), b: new THREE.Vector3(), f: new THREE.Vector3(), qs: new THREE.Quaternion(), qe: new THREE.Quaternion(), qy: new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), -Math.PI / 2) };
    delivery = { g, crate, list, mats, ph, pw, lid, flaps, tapeF, userData: { pull: 0 }, t: 0, shown: false, open: null, n, rot: { x: 0, y: 0, tx: 0, ty: 0 } };
    const mrows = new Set(list.map(b => b.userData.ri)); delivery.rows = [...mrows].map(i => rows[i]);
  }
  // the order and timing of the games coming out: quicker the more there are (about 5 s for a hundred)
  const dvStag = n => Math.min(.34, 3.4 / n), dvS0 = (k, n) => .7 + k * dvStag(n), dvEnd = n => dvS0(n - 1, n) + 1.35;
  let opening = null, dvFrom = null; // the delivery while it's being opened (A or B skips it); the shelf you came to see
  function placeDelivery(lift) { const d = delivery, v = dvV; camera.getWorldDirection(v.f); d.g.position.copy(camera.position).addScaledVector(v.f, DV_DIST); d.g.quaternion.copy(camera.quaternion); d.g.translateY(-.24 + lift); }
  function stepDelivery(dt, now) {
    const d = delivery; if (!d) return; const sm2 = (a, b0, x) => ss(Math.max(0, Math.min(1, (x - a) / (b0 - a))));
    if (!d.shown) { if (intro.on || prep) return; d.shown = true; d.t = 0; snd('slide'); setTimeout(() => snd('caseOpen'), 260); hooks.onSelect && hooks.onSelect(current()); }
    d.t += dt; const pop = Math.min(1, d.t / .6), back = pop < 1 ? 1 + 2.2 * Math.pow(pop - 1, 3) + 1.2 * Math.pow(pop - 1, 2) : 1; // eases in with a small overshoot
    const drop = (1 - ss(Math.min(1, d.t / .45))) * .55, bump = d.t > .45 && d.t < .8 ? Math.sin((d.t - .45) / .35 * Math.PI) * .025 : 0;
    placeDelivery(drop + bump); d.g.scale.setScalar(DV_S * Math.max(.01, back));
    const ro = d.rot, kr = 1 - Math.exp(-dt * (d.open ? 9 : 7)); ro.y = sm(ro.y, ro.ty, kr); ro.x = sm(ro.x, ro.tx, kr); d.g.rotateX(ro.x); d.g.rotateY(ro.y); d.g.updateMatrixWorld(); // tilt towards you, then spin: like turning a case in your hands
    if (!d.open) { const pull = d.userData.pull || 0, wob = Math.max(0, Math.sin(now / 900)) ** 12 * .04; d.g.rotateZ((Math.random() - .5) * .03 * pull + Math.sin(now / 70) * wob); d.g.rotateY(Math.sin(now / 1400) * .05); d.g.updateMatrixWorld(); return; }
    const o = d.open, n = d.n, end = dvEnd(n); o.t = Math.min(end, o.t + dt); const t = o.t, ph = d.ph, v = dvV;
    if (o.t < .55 && (Math.abs(d.rot.y) > .02 || Math.abs(d.rot.x) > .02)) o.t = Math.min(o.t, .02); // it turns back to face you first
    // the lid comes off / the tape tears and the flaps fold back
    if (d.crate) { const l = sm2(0, .5, t); d.lid.position.y = ph / 2 + l * .45; d.lid.rotation.x = -l * .6; d.lid.position.z = -l * .35; d.lid.visible = t < 1.1; }
    else { d.tapeF.scale.y = 1 - sm2(0, .3, t); d.tapeF.visible = d.tapeF.scale.y > .02; const fl = sm2(.15, .7, t) * 2.1; d.flaps[0].rotation.z = fl; d.flaps[1].rotation.z = -fl; }
    if (t > .35 && !o.burst) { o.burst = true; dvAnchor.position.copy(d.g.position); dvAnchor.translateY(0); burst(dvAnchor); snd('caseOpen'); }
    // the shelves: every case after a new game's place sits one place to the left until that game is on its way in
    const op = k => sm2(dvS0(k, n) + .5, dvS0(k, n) + 1.15, t);
    d.rows.forEach(r => { let shift = 0; r.boxes.forEach(bx => { const u = bx.userData; if (u.finalX == null) return; const x = u.finalX - shift * r.pitch; if (u.mail) { u.flyX = x; shift += 1 - op(u.mk); } else if (Math.abs(u.home.x - x) > 1e-5) { u.home.x = x; bx.position.x = x; } }); });
    shadowDirty = true;
    // each game: up out of the box with its cover to you, then off to its own place, turning to stand spine out
    d.list.forEach((bx, k) => { const u = bx.userData, s0 = dvS0(k, n), up = sm2(s0, s0 + .5, t), go = sm2(s0 + .5, s0 + 1.25, t); if (up <= 0) return;
      if (!d.skipping && !u.frontLoaded && !u.frontQ) { u.frontQ = true; loadFront(bx); }
      if (!bx.visible) { bx.visible = true; if (!d.skipping && (k < 10 || k % 5 === 0)) snd('slide'); }
      const cols = Math.min(n, 5), xk = n === 1 ? 0 : ((k % cols) / Math.max(1, cols - 1) - .5) * d.pw * .55;
      const bh = (rows[u.ri] && rows[u.ri].ch) || H; v.a.set(xk, -ph / 2 + bh / 2 + .015 + up * (bh + .1), 0); d.g.localToWorld(v.a); // standing on the box's floor, lifted straight up and out of the top
      const r = rows[u.ri]; v.b.set(u.flyX != null ? u.flyX : u.finalX, u.home.y, u.home.z); r.group.localToWorld(v.b); // its place on the shelf
      v.a.lerp(v.b, go); v.a.y += Math.sin(Math.PI * go) * .3; r.group.worldToLocal(v.a); bx.position.copy(v.a);
      v.qs.copy(d.g.quaternion).multiply(v.qy); v.qe.identity(); bx.quaternion.copy(v.qs).slerp(v.qe, ss(go)); bx.scale.setScalar(DV_S + (1 - DV_S) * go);
      if (go >= 1 && !u.landed) { u.landed = true; if (!d.skipping && (k < 10 || k % 5 === 0)) snd('slideBack'); } });
    // the empty box drops away once the last game is out
    const gone = sm2(dvS0(n - 1, n) + .45, dvS0(n - 1, n) + 1, t); if (gone > 0) { d.mats.forEach((m, i) => { m.opacity = (!d.crate && i === 2 ? .92 : 1) * (1 - gone); m.depthWrite = gone < .5; }); d.g.translateY(-gone * .5); }
    if (t >= end) finishDelivery();
  }
  function finishDelivery() { const d = delivery; if (!d) return;
    let acq = {}; try { acq = JSON.parse(localStorage.getItem('museum-acquired') || '{}') || {}; } catch {}
    d.rows.forEach(r => r.boxes.forEach(bx => { const u = bx.userData; if (u.finalX == null) return; u.home.x = u.finalX; bx.position.copy(u.home); bx.rotation.set(0, 0, 0); bx.scale.setScalar(1); bx.visible = true; delete u.finalX; delete u.flyX; }));
    d.list.forEach(bx => { const u = bx.userData, id = String(u.g.id); u.mail = false; delete u.mk; delete u.landed; delete u.frontQ; u.isNew = true; mailSet.delete(id); acq[id] = { at: Date.now(), no: (mailAt[id] && mailAt[id].no) || 0 }; delete mailAt[id]; });
    try { localStorage.setItem('museum-acquired', JSON.stringify(acq)); localStorage.setItem('museum-mail', JSON.stringify([...mailSet])); localStorage.setItem('museum-mail-at', JSON.stringify(mailAt)); } catch {}
    dropDelivery(); opening = null; shadowDirty = true; selKey = ''; snd('slideBack'); hooks.onSelect && hooks.onSelect(current()); }
  function dropDelivery() { const d = delivery; if (!d) return; delivery = null; if (pullCase === d) pullCase = null; if (d.g.parent) d.g.parent.remove(d.g);
    d.g.traverse(o => { if (o.geometry) o.geometry.dispose(); }); d.mats.forEach(m => { if (m.map && m.map !== cardT) m.map.dispose(); m.dispose(); }); }
  function openDelivery() { const d = delivery; if (!d || d.open || !d.shown) return false; d.open = { t: 0, burst: false }; d.rot.y = Math.atan2(Math.sin(d.rot.y), Math.cos(d.rot.y)); d.rot.ty = 0; d.rot.tx = 0; /* back to face you by the short way (at most half a turn): rounding the target to a whole turn left rot.y settling on 2π, and the lid waits for |rot.y| < .02, so a parcel spun past 180° never opened */ d.userData.pull = 0; opening = d; snd('unlock'); setTimeout(() => snd('caseOpen'), 180); hooks.onSelect && hooks.onSelect(current()); return true; }
  function skipOpening() { const d = delivery; if (!d) { opening = null; return false; } if (!d.shown) { d.shown = true; } if (!d.open) d.open = { t: 0, burst: true }; d.skipping = true; d.open.t = 1e9; stepDelivery(0, performance.now()); return true; }
  // straight to the end, e.g. when the museum was opened to fit a console: and back to the shelf that was asked for
  function skipDelivery(back) { if (!delivery) return false; skipOpening(); if (back && dvFrom && rows[dvFrom.rowI]) { rowI = dvFrom.rowI; colI = Math.min(dvFrom.colI, rows[rowI].boxes.length); selKey = ''; hooks.onSelect && hooks.onSelect(current()); } return true; }
  const selParcel = () => delivery && delivery.shown && !delivery.open && !heldCase ? delivery : null;
  // ---- shrink-wrap: every game you haven't opened in the museum yet is still sealed. X in your hands unwraps it (once,
  // remembered), and now and then a sealed case on your row catches the light in a rainbow shimmer ----
  let unwrapped = new Set(), sleeveM = null, shimTex = null, shimM = null, shim = null, shimNext = 0, unwraps = [];
  // magazines and guides come in a clear bag until you open them (hold A in your hands), remembered like sealed games
  const libKey = d => 'lib:' + d.id, libSealed = d => !!d && !unwrapped.has(libKey(d));
  // the magazine in your hands, or the one facing you in a fanned-out box: what hold-A unwraps
  function libInHand() {
    if (libHeld && libHeld.temp) return { d: libHeld.d, mesh: libHeld.mesh, bag: libHeld.bag, z: (libHeld.d.kind === 'guide' ? .035 : .012) / 2 + .012, it: libHeld }; // (beta.11: picked up out of a fan)
    if (fan && !fan.closing) { const sl = fan.slots.get(fan.i); return sl ? { d: sl.d, mesh: sl.mesh, bag: sl.bag, z: .012, sl } : { d: fan.it.box.issues[fan.i], mesh: null, bag: null, pending: true }; } // (its slot is made on the next frame)
    if (libHeld) return { d: libHeld.d, mesh: libHeld.mesh, bag: libHeld.bag, z: (libHeld.d.kind === 'guide' ? .035 : .012) / 2 + .012, it: libHeld };
    return null; }
  function libWrappedNow() { const h = !heldCase && libInHand(); return !!(h && (h.bag || h.pending) && libSealed(h.d)); }
  function loadUnwrapped() { try { unwrapped = new Set(JSON.parse(localStorage.getItem('museum-unwrapped') || '[]').map(String)); } catch { unwrapped = new Set(); } }
  // a magazine's bag: the sealed-game film, thinner so the cover reads clearly through it (a copy of the same material,
  // so it uses the same shader: nothing new to compile)
  let bagM0 = null; function bagMat() { const s0 = sleeveMat(); if (!bagM0 || bagM0.userData.from !== s0) { if (bagM0) { try { bagM0.dispose(); } catch {} } bagM0 = s0.clone(); bagM0.opacity = .5; bagM0.userData.from = s0; } return bagM0; }
  function sleeveMat() {
    if (sleeveM) return sleeveM; const THREE = T();
    // shrink-wrap you can see: a faint milky film with long glossy streaks and creases, and a bright rim where it
    // folds over the edges (all in one small texture, so it costs nothing extra to draw)
    const film = canvasTex(256, 256, (x, w, h) => {
      x.fillStyle = 'rgba(235,242,255,.32)'; x.fillRect(0, 0, w, h);
      for (let i = 0; i < 9; i++) { const y0 = Math.random() * h * 1.4 - h * .2, a = .18 + Math.random() * .32, wd = 3 + Math.random() * 16; const g = x.createLinearGradient(0, y0, w, y0 - w * .55); g.addColorStop(0, 'rgba(255,255,255,0)'); g.addColorStop(.5, `rgba(255,255,255,${a})`); g.addColorStop(1, 'rgba(255,255,255,0)'); x.strokeStyle = g; x.lineWidth = wd; x.beginPath(); x.moveTo(0, y0); x.lineTo(w, y0 - w * .55); x.stroke(); }
      for (let i = 0; i < 5; i++) { x.strokeStyle = `rgba(255,255,255,${.25 + Math.random() * .2})`; x.lineWidth = 1; x.beginPath(); const sx = Math.random() * w, sy = Math.random() * h; x.moveTo(sx, sy); x.lineTo(sx + (Math.random() - .5) * 60, sy + 20 + Math.random() * 60); x.stroke(); }
      const r = x.createLinearGradient(0, 0, w, 0); r.addColorStop(0, 'rgba(255,255,255,.95)'); r.addColorStop(.06, 'rgba(255,255,255,0)'); r.addColorStop(.94, 'rgba(255,255,255,0)'); r.addColorStop(1, 'rgba(255,255,255,.95)'); x.fillStyle = r; x.fillRect(0, 0, w, h);
      const r2 = x.createLinearGradient(0, 0, 0, h); r2.addColorStop(0, 'rgba(255,255,255,.95)'); r2.addColorStop(.05, 'rgba(255,255,255,0)'); r2.addColorStop(.95, 'rgba(255,255,255,0)'); r2.addColorStop(1, 'rgba(255,255,255,.95)'); x.fillStyle = r2; x.fillRect(0, 0, w, h);
    });
    sleeveM = new THREE.MeshPhysicalMaterial({ color: 0xffffff, map: film, transparent: true, opacity: .78, roughness: .04, metalness: 0, clearcoat: 1, clearcoatRoughness: .03, depthWrite: false, side: THREE.FrontSide });
    return sleeveM;
  }
  function shimMat() {
    const THREE = T();
    if (!shimTex) {
      const c = document.createElement('canvas'); c.width = c.height = 256; const x = c.getContext('2d');
      const gr = x.createLinearGradient(0, 0, 256, 256);
      [[0, 'rgba(0,0,0,0)'], [.38, 'rgba(255,70,160,0)'], [.42, 'rgba(255,70,160,.85)'], [.46, 'rgba(255,220,80,.85)'], [.5, 'rgba(90,255,200,.85)'], [.54, 'rgba(90,140,255,.85)'], [.58, 'rgba(230,90,255,.8)'], [.62, 'rgba(0,0,0,0)'], [1, 'rgba(0,0,0,0)']].forEach(([o, col]) => gr.addColorStop(o, col));
      x.fillStyle = gr; x.fillRect(0, 0, 256, 256);
      for (let i = 0; i < 70; i++) { const px = 8 + Math.random() * 240, py = 8 + Math.random() * 240; x.fillStyle = `rgba(255,255,255,${.4 + Math.random() * .6})`; x.fillRect(px, py, 1.5, 1.5); }
      // a half-size window slides across the texture, carrying the band corner to corner (no edges or tiling ever shown)
      shimTex = new THREE.CanvasTexture(c); shimTex.repeat.set(.5, .5); if (THREE.SRGBColorSpace) shimTex.colorSpace = THREE.SRGBColorSpace;
    }
    // one material for every glint, kept for as long as the room: making and throwing away a new one per glint meant
    // the graphics card had to rebuild its shader each time, a small stall right as you landed on a sealed game
    if (!shimM) shimM = new THREE.MeshBasicMaterial({ map: shimTex, transparent: true, opacity: 0, blending: THREE.AdditiveBlending, depthWrite: false });
    return shimM;
  }
  function startUnwrap(b) {
    const u = b.userData, sl = u.sleeve; if (!sl) return false;
    u.sleeve = null; unwrapped.add(String(u.g.id)); try { localStorage.setItem('museum-unwrapped', JSON.stringify([...unwrapped])); } catch {}
    if (shim && shim.box === b) endShim();
    sl.material = sl.material.clone(); unwraps.push({ sl, b, t: 0, p0: sl.position.clone() }); snd('page'); setTimeout(() => snd('flip'), 260);
    return true;
  }
  // a magazine out of its bag: the bag peels away from the one in your hands; any other copy of it on the rack (the
  // front of its box) simply loses its bag
  function unwrapLib(h) {
    const d = h.d; if (!libSealed(d) || !h.bag || !h.mesh) return false;
    unwrapped.add(libKey(d)); try { localStorage.setItem('museum-unwrapped', JSON.stringify([...unwrapped])); } catch {}
    if (shim) endShim();
    const bag = h.bag; if (bag.material === bagM0) bag.material = bagM0.clone(); unwraps.push({ sl: bag, b: h.mesh, t: 0, p0: bag.position.clone(), lib: true });
    if (h.it) h.it.bag = null; if (h.sl) h.sl.bag = null;
    if (rack) rack.items.forEach(it => { if (it.d.id === d.id && it.bag && it.bag !== bag) { it.mesh.remove(it.bag); it.bag = null; } });
    snd('page'); setTimeout(() => snd('flip'), 260); snd('unlock');
    const m = libShimMesh(h); h.mesh.add(m); shim = { box: h.mesh, mesh: m, t: 0, fast: true }; burst(h.mesh);
    hooks.onSelect && hooks.onSelect(current()); return true;
  }
  // Room → Reseal: everything you've opened goes back in its wrap (games in shrink-wrap, magazines in their bags)
  function resealAll() {
    const n = unwrapped.size; if (!n) return 0; const THREE = T();
    closeFan(true); libHeld = null; endShim(); unwraps.forEach(w => { try { w.b.remove(w.sl); w.sl.material.dispose(); } catch {} }); unwraps = []; libPullBag = null; libPull = 0;
    unwrapped = new Set(); try { localStorage.setItem('museum-unwrapped', '[]'); } catch {}
    rows.forEach(rw => rw.boxes.forEach(b => { const u = b.userData; if (u.sleeve || !rw.sleeveGeo || b === heldCase) return; const sl = new THREE.Mesh(rw.sleeveGeo, sleeveMat()); sl.renderOrder = 3; sl.userData.sleeveGeo = rw.sleeveGeo; b.add(sl); u.sleeve = sl; }));
    if (rack) rack.items.forEach(it => { if (it.bag) return; const bag = new THREE.Mesh(it.d.kind === 'guide' ? rack.bagG : rack.bagM, bagMat()); bag.position.y = RACK_H * .025; bag.renderOrder = 3; bag.userData.keepMat = true; it.mesh.add(bag); it.bag = bag; });
    shadowDirty = true; hooks.onSelect && hooks.onSelect(current()); return n;
  }
  function libShimMesh(h) { const THREE = T(), m = new THREE.Mesh(new THREE.PlaneGeometry(RACK_W, RACK_H), shimMat()); m.position.z = h.z; m.renderOrder = 4; m.userData.ownGeo = true; return m; }
  // holding A on a sealed case: the plastic tightens and starts to glint; let go and it relaxes
  // hold-A tension on the plastic. Let go (p 0), or the thing in your hands changes, and whatever was pulled relaxes
  let pullCase = null;
  function wrapPull(p) { p = Math.max(0, Math.min(1, p || 0)); if (p > 0) nudge();
    if (p <= 0) { libPull = 0; if (pullCase) { pullCase.userData.pull = 0; pullCase = null; } return; }
    const pb = !heldCase && selParcel(); if (pb) { if (pullCase && pullCase !== pb) pullCase.userData.pull = 0; pb.userData.pull = p; pullCase = pb; return; }
    const h = !heldCase && libInHand();
    if (h) { if (h.bag && libSealed(h.d)) { if (libPullBag && libPullBag !== h.bag) relaxBag(libPullBag); if (h.bag.material === bagM0) h.bag.material = bagM0.clone(); libPull = p; libPullBag = h.bag; } return; }
    const b = heldCase, u = b && b.userData, sl = u && u.sleeve; if (!sl) return; if (pullCase && pullCase !== b) pullCase.userData.pull = 0; if (sl.material === sleeveM) sl.material = sleeveM.clone(); u.pull = p; pullCase = b; }
  function relaxBag(bg) { if (!bg) return; const p0 = bg.userData.p0; bg.scale.setScalar(1); if (unwraps.some(w => w.sl === bg)) return; if (p0) bg.position.copy(p0); if (bg.material !== bagM0 && bagM0) { try { bg.material.dispose(); } catch {} bg.material = bagM0; } if (libPullBag === bg) libPullBag = null; }
  function unwrapHeld() {
    if (!heldCase) { const pb = selParcel(); if (pb) { pb.userData.pull = 0; pullCase = null; return openDelivery(); } const h = libInHand(); libPull = 0; return h ? unwrapLib(h) : false; }
    const b = heldCase; if (!b || !b.userData.sleeve) return false; const geo = b.userData.sleeve.userData.sleeveGeo;
    startUnwrap(b); snd('unlock');
    // a rainbow sweep across the bare case as the plastic comes away, and a burst of glitter
    endShim(); const m = shimMesh(b); b.add(m); shim = { box: b, mesh: m, t: 0, fast: true };
    burst(b); return true;
  }
  let sparks = null, sparkM = null, libPull = 0, libPullBag = null;
  function burst(b) {
    const THREE = T(); if (sparks) { try { sparks.parent.remove(sparks); sparks.geometry.dispose();  } catch {} sparks = null; }
    const n = 90, pos = new Float32Array(n * 3), vel = []; for (let i = 0; i < n; i++) { pos.set([0, 0, 0], i * 3); const a = Math.random() * Math.PI * 2, e = Math.random() * .9 - .2; vel.push([Math.cos(a) * .5, .35 + e * .6, Math.sin(a) * .5]); }
    const g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    const cols = new Float32Array(n * 3); for (let i = 0; i < n; i++) { const c = new THREE.Color().setHSL(Math.random(), .9, .7); cols.set([c.r, c.g, c.b], i * 3); } g.setAttribute('color', new THREE.BufferAttribute(cols, 3));
    if (!sparkM) sparkM = new THREE.PointsMaterial({ size: .03, vertexColors: true, transparent: true, opacity: 1, blending: THREE.AdditiveBlending, depthWrite: false }); // kept: no shader rebuild per unwrap
    sparkM.opacity = 1; sparks = new THREE.Points(g, sparkM);
    sparks.userData = { vel, t: 0 }; b.add(sparks);
  }
  function stepSparks(dt) { if (!sparks) return; const u = sparks.userData; u.t += dt; const a = sparks.geometry.attributes.position; for (let i = 0; i < u.vel.length; i++) { const v = u.vel[i]; v[1] -= dt * .9; a.array[i * 3] += v[0] * dt; a.array[i * 3 + 1] += v[1] * dt; a.array[i * 3 + 2] += v[2] * dt; } a.needsUpdate = true; sparks.material.opacity = Math.max(0, 1 - u.t / 1.4); if (u.t > 1.4) { try { sparks.parent.remove(sparks); sparks.geometry.dispose();  } catch {} sparks = null; } }
  // the shimmer sweeps across the cover side only (one clean plane, no seams across the case's other faces)
  function shimMesh(b) { const THREE = T(), g0 = b.geometry; if (!g0.boundingBox) g0.computeBoundingBox(); const sz = g0.boundingBox.getSize(new THREE.Vector3()), d = sz.x, h = sz.y, w = sz.z;
    const m = new THREE.Mesh(new THREE.PlaneGeometry(w, h), shimMat()); m.rotation.y = Math.PI / 2; m.position.x = d / 2 + .003; m.renderOrder = 4; m.userData.ownGeo = true; return m; }
  function endShim() { if (!shim) return; try { shim.box.remove(shim.mesh); if (shim.mesh.material !== shimM) shim.mesh.material.dispose(); if (shim.mesh.userData.ownGeo) shim.mesh.geometry.dispose(); } catch {} shim = null; }
  // Even light along every row without more real lights: each case glows faintly with its own art (emissive = its own
  // picture), so the far ends of a long shelf read as clearly as the middle. Cheap: a few material values, re-checked
  // a few times a second (and every frame during the walk-in).
  // a case material that also glows faintly with its own colour/art, by a row-wide amount (one shared shader, one number per row)
  function fillify(m, u) { m.userData.fill = u; m.customProgramCacheKey = () => 'bs-fill';
    m.onBeforeCompile = sh => { sh.uniforms.uFill = m.userData.fill; sh.fragmentShader = 'uniform float uFill;\n' + sh.fragmentShader.replace('#include <emissivemap_fragment>', '#include <emissivemap_fragment>\n\ttotalEmissiveRadiance += diffuseColor.rgb * uFill;'); }; return m; }
  function evenFill() {
    const k0 = .22 * (room ? room.bright : 1) * (1 - .5 * light.hue) * light.caseGlow * (1 - .6 * dimK);
    rows.forEach(rw => { if (rw.fillU) rw.fillU.value = k0 * (rw.fill == null ? 1 : rw.fill); });
  }
  function stepWrap(dt, now, r) {
    stepSparks(dt);
    // the held sealed case under A: the plastic tightens (a touch bigger, glossier) and trembles
    if (heldCase && heldCase.userData.sleeve) { const u = heldCase.userData, sl = u.sleeve, p = u.pull || 0; sl.scale.setScalar(1 + .025 * p); sl.position.set((Math.random() - .5) * .004 * p, (Math.random() - .5) * .004 * p, 0); if (sl.material !== sleeveM) sl.material.opacity = .78 + .2 * p; }
    // a bagged magazine under A: the bag tightens and trembles the same way; let go and it relaxes
    if (libPullBag) { const bg = libPullBag, p = libPull; if (!bg.userData.p0) bg.userData.p0 = bg.position.clone(); const p0 = bg.userData.p0;
      if (p > 0 && bg.parent) { bg.scale.setScalar(1 + .03 * p); bg.position.set(p0.x + (Math.random() - .5) * .004 * p, p0.y + (Math.random() - .5) * .004 * p, p0.z); if (bg.material !== bagM0) bg.material.opacity = .5 + .3 * p; }
      else relaxBag(bg); }
    // the plastic peeling off: it lifts, stretches and twists away, fading out
    for (let i = unwraps.length - 1; i >= 0; i--) { const w = unwraps[i]; w.t = Math.min(1, w.t + dt / 1.1); const e = w.t * w.t * (3 - 2 * w.t);
      w.sl.position.set((w.p0 ? w.p0.x : 0) + .04 * e, (w.p0 ? w.p0.y : 0) + .22 * e, (w.p0 ? w.p0.z : 0) + .12 * e); w.sl.rotation.set(-.35 * e, .25 * e, .18 * e); w.sl.scale.set(1 + .06 * e, 1 + .12 * e, 1 + .06 * e); w.sl.material.opacity = (w.lib ? .5 : .78) * (1 - e) + .2 * Math.sin(Math.PI * Math.min(1, w.t * 1.6)) * (1 - e);
      if (w.t >= 1) { try { w.b.remove(w.sl); w.sl.material.dispose(); } catch {} unwraps.splice(i, 1); } }
    // the shimmer: every so often, a sealed case near you on this row catches the light (never while you're busy with one)
    if (shim) { shim.t += dt / (shim.fast ? .9 : 1.8); const k = Math.min(1, shim.t); shim.mesh.material.opacity = Math.sin(Math.PI * k) * (shim.fast ? .9 : .85); shimTex.offset.set(.5 - .5 * k, .5 - .5 * k); if (k >= 1) { endShim(); shimNext = now + 3000; } return; }
    if (!r || intro.on || reader || peek) return;
    if (wide || roomView || holding || delivery || paused) return; // only while you're actually looking at one (not the overview, the room menu, a console or the mail)
    if (now - Math.max(lastNav, lastInput, shimNext) < 5000) return; // you've been looking at this sealed game for 5 s
    // a magazine still in its bag catches the light the same way: in your hands, facing you in a box, or on the rack
    const lh = !heldCase && (libInHand() || (() => { const it = !wide && rackItem(); return it && it.bag ? { d: it.d, mesh: it.mesh, bag: it.bag, z: (it.d.kind === 'guide' ? .035 : .012) / 2 + .012 } : null; })());
    if (lh) { if (!lh.bag) return; const m = libShimMesh(lh); lh.mesh.add(m); shim = { box: lh.mesh, mesh: m, t: 0 }; snd('toast'); return; }
    const sel = heldCase || (!fan && r.boxes[colI]), b = sel && sel.userData.sleeve && !sel.userData.mail && (sel === heldCase || sel.userData.p > .9) ? sel : null; // pulled out in front of you, or in your hands
    if (!b) return;
    const m = shimMesh(b); b.add(m); shim = { box: b, mesh: m, t: 0 }; snd('toast');
  }

  // Build the wall. rows: [{id,name,style,games:[{id,title,front,spine,year,hours,fav}],console:{model,opts},stats}]
  function build(data) {
    const THREE = T(), RB = window.BS3D.RoundedBoxGeometry;
    libPending = null; endPeek(); closeFan(true); libHeld = null; if (delivery) dropDelivery(); disposeWall(); opening = null; sleeveM = null; if (shimTex) { try { shimTex.dispose(); } catch {} } if (shimM) { try { shimM.dispose(); } catch {} } shimTex = null; shimM = null; shim = null; unwraps = []; loadUnwrapped(); rack = null; wideFrame = null; rows = []; work = []; dust = []; card = null; wallGen++; spineRow = -1; selKey = '';
    tex.forEach(t => { t.userData.refs = 0; }); // the old wall's cases are gone: nothing holds these any more (kept only for a quick reuse)
    const gap = .012, plankT = .055;
    const PLQ = 1.75; /* room for the name plate beside the console */
    // a case's height: 1 for the usual 190 mm cases; smaller ones (3DS) say so in their style (h) and stand on the same shelf
    const dims = r => { const st = r.style, w = H * (st.h || 1) * st.ratio, d = Math.max(.045, w * st.depth), consoleW = Math.max(1.3, w * 1.7); return { w, d, consoleW, totalW: r.games.length * (d + gap) + (r.games.length >= 3 ? .16 : 0) + consoleW + .5 + PLQ }; };
    let maxW = Math.max(4, ...data.map(r => dims(r).totalW)); const fullW = maxW;
    const woodMat = new THREE.MeshStandardMaterial({ map: wood(), roughness: .55 });
    data.forEach((r, ri) => {
      const st = r.style, ch = H * (st.h || 1), w = ch * st.ratio, d = Math.max(.045, w * st.depth), cy = -(H - ch) / 2;
      const y = -ri * ROW;
      const row = { ...r, y, boxes: [], w, d, ch };
      const { consoleW, totalW } = dims(r); row.consoleW = consoleW;
      const group = new THREE.Group(); group.position.y = y; wall.add(group); row.group = group;
      const span = Math.max(totalW, 4), lx = totalW / 2;
      // warm picture light over the row (off until the opening turns it on); shadows only while this row is the one in view
      const ly = ROW - H / 2 - plankT - .07, lz = -DEPTH / 2 + .3; // just under the shelf above, near the back wall
      // the light itself sits a little forward of the shade; a 58° cone keeps the shadow camera's field narrow enough for a clean map
      const lamp = new THREE.SpotLight(0xffe0b0, 0, 7.5, 58 * DEG, .75, 1.1); lamp.position.set(lx, ly - .02, lz + .55); lamp.target.position.set(lx, -H / 2, 0);
      lamp.castShadow = false; lamp.shadow.mapSize.set(perf().shadowSize, perf().shadowSize); lamp.shadow.bias = -.0015; lamp.shadow.normalBias = .02; lamp.shadow.camera.near = .25; lamp.shadow.camera.far = 6; if (!lampPooled()) group.add(lamp, lamp.target); row.lamp = lamp; /* (Android: copied into the lamp pool instead, see poolLamps) */ row.lampOn = 0; row.lampX0 = lx;
      const brass = new THREE.MeshStandardMaterial({ color: 0x8a6a34, metalness: .85, roughness: .3 });
      const fix = new THREE.Group(); group.add(fix); row.fixture = fix; // the brass lamp: hidden in Neon
      const tube = new THREE.Mesh(new THREE.CylinderGeometry(.03, .03, .62, 16, 1, false, 0, Math.PI), brass); tube.rotation.set(0, 0, Math.PI / 2); tube.rotation.x = -Math.PI / 2 * .35; tube.position.set(lx, ly, lz); fix.add(tube); // half-shade, open toward the shelf
      [-.22, .22].forEach(ax => { const arm = new THREE.Mesh(new THREE.BoxGeometry(.02, .02, .3), brass); arm.position.set(lx + ax, ly + .01, lz - .15); fix.add(arm); const cap = new THREE.Mesh(new THREE.CylinderGeometry(.032, .032, .012, 16), brass); cap.rotation.z = Math.PI / 2; cap.position.set(lx + ax * 1.45, ly, lz); fix.add(cap); });
      const bulb = new THREE.Mesh(new THREE.CylinderGeometry(.014, .014, .5, 10), new THREE.MeshStandardMaterial({ color: 0x332a1a, emissive: 0xffd9a0, emissiveIntensity: 0 })); bulb.rotation.z = Math.PI / 2; bulb.position.set(lx, ly - .012, lz + .012); fix.add(bulb); row.bulb = bulb;
      // Clean gallery lighting, with no extra real lights (they're what made it slow): a slim warm LED line under the
      // shelf above, running the full width; a soft glow it throws on the back panel; and the cases on the row lifted
      // evenly from end to end (see evenFill). The one picture light above still follows what you're looking at, with
      // shadows. On the walk-in each row's line draws itself across, left to right.
      { const stripG = new THREE.BoxGeometry(1, .008, .02); stripG.translate(.5, 0, 0);
        const stripM = new THREE.MeshBasicMaterial({ color: 0xffe2b8, toneMapped: false, transparent: true }); // (see-through from the start: a scene fades it, and changing that later would rebuild its shader)
        const strip = new THREE.Mesh(stripG, stripM); strip.position.set(-.3, ROW - H / 2 - plankT - .006, (DEPTH + .1) / 2 - .05); strip.scale.x = .0001; group.add(strip); row.strip = strip; row.stripW = fullW + .6;
        const washM = new THREE.MeshBasicMaterial({ map: washTex(), transparent: true, opacity: 0, depthWrite: false, blending: THREE.AdditiveBlending, toneMapped: false, color: 0xffd9a8 });
        const wash = new THREE.Mesh(new THREE.PlaneGeometry(fullW + .5, ROW - plankT), washM); wash.position.set(fullW / 2, ROW / 2 - H / 2 - plankT / 2 - .01, -DEPTH / 2 + .03); group.add(wash); row.wash = wash; }
      // a soft beam under the lamp (gallery moods): a faint shaft of light falling onto the cases
      const beam = new THREE.Mesh(new THREE.PlaneGeometry(Math.min(span, 5.2), ROW * .92), new THREE.MeshBasicMaterial({ map: beamTex(), transparent: true, opacity: 0, depthWrite: false, blending: THREE.AdditiveBlending, toneMapped: false, color: 0xffe2b8 })); beam.position.set(lx, ly - ROW * .46, lz + .42); beam.rotation.x = -.12; group.add(beam); row.beam = beam;
      // dust in the lamp cone
      const n = 70, pos = new Float32Array(n * 3), seed = new Float32Array(n);
      for (let i = 0; i < n; i++) { pos[i * 3] = lx + (Math.random() - .5) * Math.min(span, 5); pos[i * 3 + 1] = -H / 2 + Math.random() * H * 1.2; pos[i * 3 + 2] = -.2 + Math.random() * 1.4; seed[i] = Math.random() * 100; }
      const dg = new THREE.BufferGeometry(); dg.setAttribute('position', new THREE.BufferAttribute(pos, 3));
      const dp = new THREE.Points(dg, new THREE.PointsMaterial({ color: 0xffe6c0, size: .012, transparent: true, opacity: 0, depthWrite: false, blending: THREE.AdditiveBlending })); group.add(dp); dust.push({ pts: dp, seed, row });
      // plank
      const plank = new THREE.Mesh(new THREE.BoxGeometry(fullW + .66, plankT, DEPTH + .1), woodMat); plank.receiveShadow = true; plank.castShadow = true;
      plank.position.set(fullW / 2, -H / 2 - plankT / 2, 0); group.add(plank);
      // Neon lighting: an LED strip glowing in the console's colour along the shelf's front edge (hidden in the other moods)
      // two glowing strips (the shelf's front edge, and under the shelf above) and two soft panel lights that wash the
      // whole row in the console's colour, from below and from above
      const ledM = new THREE.MeshBasicMaterial({ color: new THREE.Color(st.glow || '#9d8cff'), transparent: true, opacity: 0 }); // faded in and out with Neon night
      const led = new THREE.Group(); group.add(led); row.led = led; led.visible = light.hue > .01;
      const ledB = new THREE.Mesh(new THREE.BoxGeometry(fullW + .6, plankT * .3, .012), ledM); ledB.position.set(fullW / 2, -H / 2 - plankT / 2, (DEPTH + .1) / 2 + .004); led.add(ledB);
      const ledT = new THREE.Mesh(new THREE.BoxGeometry(fullW + .6, .014, .014), ledM); ledT.position.set(fullW / 2, ROW - H / 2 - plankT - .012, (DEPTH + .1) / 2 - .02); led.add(ledT);

      // cases, spine out (+z), front cover on +x. Spine textures are built later, a few per frame, so entering is quick.
      let x = d / 2; const tabs = []; row.pitch = d + gap; let before = 0;
      // new games still in the mail: their places stay closed up (the cases after them sit one place left for each) until
      // the delivery is opened and they fly in
      const mailRow = r.games.some(g => mailSet.has(String(g.id)));
      row.fillU = { value: 0 }; const plain = fillify(new THREE.MeshStandardMaterial({ color: st.caseColor, roughness: .42 }), row.fillU);
      // rounded like the real case: each console's corner radius (as on the shelf), as far as the case's thickness allows
      const cr = Math.min(w * (st.rc || .04), d * .42), geo = caseGroups(new RB(d, ch, w, 4, cr));
      // the shrink-wrap on games you haven't opened yet: a clear, glossy skin a hair bigger than the case
      const sleeveGeo = new RB(d + .006, ch + .006, w + .006, 4, cr + .003); row.sleeveGeo = sleeveGeo;
      r.games.forEach((g, i) => {
        const front = fillify(new THREE.MeshStandardMaterial({ color: st.caseColor, roughness: .42, metalness: 0 }), row.fillU); /* less glare across the cover art */
        const spine = fillify(new THREE.MeshStandardMaterial({ color: st.caseColor, roughness: .36, metalness: .05 }), row.fillU);
        const mesh = new THREE.Mesh(geo, [front, plain, plain, spine]); /* cover, back, the other plain sides, spine */ mesh.castShadow = true; mesh.receiveShadow = true;
        const mailed = mailSet.has(String(g.id));
        mesh.position.set(x - before * (d + gap), cy, mesh0z(w)); mesh.userData = { g, i, ri, st, home: mesh.position.clone(), front, spine, frontLoaded: false, spineOn: false, isNew: false, slide: 0, p: 0 };
        if (!unwrapped.has(String(g.id))) { const sl = new THREE.Mesh(sleeveGeo, sleeveMat()); sl.renderOrder = 3; sl.userData.sleeveGeo = sleeveGeo; mesh.add(sl); mesh.userData.sleeve = sl; }
        if (mailRow) mesh.userData.finalX = x; if (mailed) { mesh.userData.mail = true; mesh.visible = false; before++; }
        group.add(mesh); row.boxes.push(mesh);
        const tx = x; // section markers go where the cases will finally be
        if (g.group && r.games.length >= 6) { if (i === 0 || r.games[i - 1].group !== g.group) tabs.push({ x0: tx - d / 2, x1: tx + d / 2, label: g.group }); else tabs[tabs.length - 1].x1 = tx + d / 2; }
        x += d + gap;
      });
      // section markers on the plank's front edge: a thin brass rail under exactly the cases of each section (letter, year,
      // genre…) and a small label plate starting at its first case, so it's clear where each section begins and ends.
      // Labels that would collide with the previous one are left off (the rail still shows the split). One mesh + one
      // texture per row for the plates, one for the rails.
      if (tabs.length) {
        const LH = .036, z = (DEPTH + .1) / 2 + .002, top = -H / 2, PXH = 80, PXU = PXH / LH; let lastEnd = -1;
        const shown = tabs.filter(t => { const w = Math.max(.07, Math.min(.26, .03 + t.label.length * .019)); t.w = Math.min(w, Math.max(.07, t.x1 - t.x0)); if (t.x0 < lastEnd + .006) return false; lastEnd = t.x0 + t.w; return true; });
        const cellW = shown.map(t => Math.round(t.w * PXU)), AW = Math.max(...cellW, 32);
        const atlas = canvasTex(AW, PXH * shown.length, cx => shown.forEach((t, k) => { cx.save(); cx.translate(0, k * PXH); drawTab(cx, t.label, cellW[k], PXH); cx.restore(); }));
        const pos = [], uv = [], idx = [], quad = (x0, x1, y0, y1, u1, v0, v1) => { const o = pos.length / 3; pos.push(x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z); uv.push(0, v0, u1, v0, u1, v1, 0, v1); idx.push(o, o + 1, o + 2, o, o + 2, o + 3); };
        shown.forEach((t, k) => quad(t.x0, t.x0 + t.w, top - .011 - LH, top - .011, cellW[k] / AW, 1 - (k + 1) / shown.length, 1 - k / shown.length));
        const tg = new THREE.BufferGeometry(); tg.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3)); tg.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2)); tg.setIndex(idx); tg.computeVertexNormals();
        group.add(new THREE.Mesh(tg, new THREE.MeshStandardMaterial({ map: atlas, metalness: .6, roughness: .35, emissive: 0xffffff, emissiveMap: atlas, emissiveIntensity: .12 })));
        // rails: one per section, inset a hair at each end so neighbouring sections read as separate
        const rp = [], ri = [];
        tabs.forEach(t => { const o = rp.length / 3, x0 = t.x0 + .003, x1 = t.x1 - .003, y0 = top - .008, y1 = top - .003; rp.push(x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z); ri.push(o, o + 1, o + 2, o, o + 2, o + 3); });
        const rg = new THREE.BufferGeometry(); rg.setAttribute('position', new THREE.Float32BufferAttribute(rp, 3)); rg.setIndex(ri); rg.computeVertexNormals();
        group.add(new THREE.Mesh(rg, new THREE.MeshStandardMaterial({ color: 0xc9a86a, metalness: .8, roughness: .3, emissive: 0x3a2a10 })));
      }
      // a wooden bookend after the cases on fuller rows
      if (r.games.length >= 3) { const base = new THREE.Mesh(new THREE.BoxGeometry(.12, .02, w * .9), woodMat); base.position.set(x + .05, -H / 2 + .01, mesh0z(w)); const up = new THREE.Mesh(new THREE.BoxGeometry(.02, ch * .62, w * .9), woodMat); up.position.set(x + .005, -H / 2 + ch * .31, mesh0z(w)); up.castShadow = true; group.add(base, up); x += .16; }
      // console + plaque at the end of the row
      const cg = new THREE.Group(); cg.position.set(x + .3 + consoleW / 2, -H / 2 + .001, 0); group.add(cg); row.consoleG = cg; row.consoleX = cg.position.x;
      const si = standIn(st, consoleW, H * .34, DEPTH * .7); si.position.y = H * .17; cg.add(si);
      if (r.console && r.console.model) loadModel(row);
      const ptex = plaqueTex(row); const pl = new THREE.Mesh(new THREE.PlaneGeometry(1.5, .56), new THREE.MeshStandardMaterial({ map: ptex, roughness: .6, metalness: .1, emissive: 0xffffff, emissiveMap: ptex, emissiveIntensity: 0 })); row.plaque = pl; pl.position.set(x + .3 + consoleW + .12 + .75, -H / 2 + .3, -DEPTH / 2 + .06); group.add(pl);
      rows.push(row);
    });
    // the selection spotlight: one narrow, soft-edged beam that follows the case you're on (made with the wall so the
    // light count never changes later, which would recompile every material and stutter)
    spot = new THREE.SpotLight(light.spot, 0, 5, 17 * DEG, .45, 1.2); spot.castShadow = false; wall.add(spot, spot.target); spotAt = null; spotK = 0;
    applyLight();
    wallW = maxW; wallH = rows.length * ROW;
    const bottom = -(rows.length - 1) * ROW - H / 2 - plankT;
    // back wall, uprights, baseboard, floor
    const bw = new THREE.Mesh(new THREE.PlaneGeometry(maxW + 60, wallH + 8), new THREE.MeshStandardMaterial({ color: 0x171a24, roughness: .95 })); bw.receiveShadow = true; { const t0 = bw.geometry.attributes.uv; for (let i = 0; i < t0.count; i++) t0.setXY(i, t0.getX(i) * (maxW + 60) / 4, t0.getY(i) * (wallH + 8) / 4); } // world-scaled UVs so wall patterns keep their size
    bw.position.set(maxW / 2, -(rows.length - 1) * ROW / 2, -DEPTH / 2 - .01); wall.add(bw);
    const topBoard = new THREE.Mesh(new THREE.BoxGeometry(maxW + .8 + .14, plankT, DEPTH + .1), woodMat); topBoard.position.set(maxW / 2, ROW - H / 2 + plankT / 2 - .001, 0); topBoard.castShadow = true; wall.add(topBoard);
    [-.4, maxW + .4].forEach(ux => { const up = new THREE.Mesh(new THREE.BoxGeometry(.14, wallH + .4, DEPTH + .1), woodMat); up.castShadow = true; up.position.set(ux, -(rows.length - 1) * ROW / 2 - .1, 0); wall.add(up); });
    const base = new THREE.Mesh(new THREE.BoxGeometry(maxW + 60, .16, .06), woodMat); base.position.set(maxW / 2, bottom - .32, -DEPTH / 2 + .03); wall.add(base);
    const floor = new THREE.Mesh(new THREE.PlaneGeometry(maxW + 60, 40), new THREE.MeshStandardMaterial({ map: floorTex(), roughness: .32, metalness: .25, envMapIntensity: .8 })); floor.rotation.x = -Math.PI / 2; floor.position.set(maxW / 2, bottom - .4, 19.6 - DEPTH / 2); floor.receiveShadow = true; wall.add(floor);
    // the room's swappable parts, and the posters: covers of your favourite / most played games
    const artOf = g => typeof g.front === 'string' ? g.front : (g._g && g._g.art && g._g.art.front && typeof g.front === 'function' ? g.front() : null); // real box art only
    const pool = rows.flatMap(rw => rw.games).filter(g => typeof artOf(g) === 'string').sort((a, b) => (b.fav ? 1 : 0) - (a.fav ? 1 : 0) || (b.plays || 0) - (a.plays || 0));
    roomObj = { bw, floor, woodMat, bottom, maxW, midY: -(rows.length - 1) * ROW / 2, posterUrls: pool.slice(0, 4).map(artOf), extras: null }; applyRoomSurfaces();
    // the card under a pulled-out case
    // the label: printed face (unlit, so it always reads cleanly) on a thick lit backing board with a brass edge, so it has real depth
    card = new THREE.Group(); card.visible = false; card.userData.fade = 0; wall.add(card);
    const face = new THREE.Mesh(new THREE.PlaneGeometry(.9, .44), new THREE.MeshBasicMaterial({ transparent: true, opacity: 0, toneMapped: false })); face.position.z = .0125; face.renderOrder = 5; card.add(face); card.userData.face = face;
    const board = new THREE.Mesh(new RB(.94, .48, .024, 2, .006), new THREE.MeshStandardMaterial({ color: 0x8a6a34, metalness: .7, roughness: .35, transparent: true, opacity: 0 })); board.castShadow = false; board.renderOrder = 4; card.add(board); /* drawn after the shrink-wrap: while it fades in it's see-through but would still block the wrap behind it */ card.userData.board = board;
  }
  // Spine textures (a 96×768 text canvas, or the real spine art) exist only for the rows around the one you're on:
  // a whole library of them was ~0.7 MB each (≈420 MB for 600 games). Rows that leave the range give theirs back and
  // get them again, a few per frame, when you come back. Small collections (≤ SPINES cases) keep every spine.
  const SPINES = 96; let spineRow = -1, spineKeep = new Set(), wallGen = 0;
  function syncSpines(c0) {
    const c = Math.max(0, Math.min(c0, rows.length - 1)); spineRow = c0;
    const order = rows.map((r, i) => i).sort((a, b) => Math.abs(a - c) - Math.abs(b - c)); let n = 0; spineKeep = new Set();
    order.forEach(i => { const k = rows[i].boxes.length; if (Math.abs(i - c) <= 1 || n + k <= SPINES) { spineKeep.add(i); n += k; } });
    rows.forEach((r, i) => { if (!spineKeep.has(i)) r.boxes.forEach(b => { if (b.userData.spineOn && !b.userData.g.spine) dropSpine(b.userData); }); }); // real spine art stays on every row
    work = []; order.forEach(i => { if (spineKeep.has(i)) rows[i].boxes.forEach(b => { if (!b.userData.spineOn) work.push(b); }); });
    // then the small spines for every other row, so nothing on the wall is ever left blank
    order.forEach(i => { if (!spineKeep.has(i)) rows[i].boxes.forEach(b => { if (!b.userData.liteOn && !b.userData.spineOn) work.push(b); }); });
  }
  function dropSpine(u) { u.spineGen = (u.spineGen || 0) + 1; const m = u.spine.map; u.spine.map = null; u.spine.color.set(u.st.caseColor); u.spine.needsUpdate = true; u.spineOn = false; u.liteOn = false; freeMap(m); }
  // a few spine textures per frame (text canvases, or the real spine art when there is one)
  let shadowDirty = true; // shadows only re-render when something that casts them moved, not every frame
  // marks the shadows for a redraw only if o has moved, turned or grown since the last look (an animation that has come
  // to rest, or a case held still, costs nothing)
  function moved(o) { if (!o) return; const p = o.position, q = o.rotation, u = o.userData, sk = p.x + p.y * 7 + p.z * 13 + q.x * 17 + q.y * 19 + q.z * 23 + o.scale.x * 29; if (u._mk === undefined || Math.abs(sk - u._mk) > 1e-4) { u._mk = sk; shadowDirty = true; } }
  function doWork(ms) {
    const sc = wide && !intro.on && spineRow >= 0 ? (preOn && preRow >= 0 ? preRow : spineRow) : rowI; if (spineRow !== sc) syncSpines(sc); // a shelf framed in the wide view gets its spines first
    const t0 = performance.now();
    while (work.length && performance.now() - t0 < ms) {
      const mesh = work.shift(), u = mesh.userData; if (u.spineOn) continue; fst.sp++;
      if (!spineKeep.has(u.ri) && !u.g.spine) { if (u.liteOn) continue; u.liteOn = true; const old = u.spine.map; u.spine.map = spineTexLite(u.g.title, u.st); u.spine.color.set(0xffffff); u.spine.needsUpdate = true; if (old) freeMap(old); continue; }
      u.spineOn = true; if (u.liteOn) { u.liteOn = false; const old = u.spine.map; u.spine.map = null; if (old) freeMap(old); }
      if (!u.g.spine) { u.spine.map = spineTex(u.g.title, u.st); u.spine.color.set(0xffffff); u.spine.needsUpdate = true; continue; }
      const t = loadTex(u.g.spine, 'spine'), mat = u.spine; // the printed text spine stands in while the image loads, and stays if it can't
      mat.map = spineTex(u.g.title, u.st); mat.color.set(0xffffff); mat.needsUpdate = true;
      const gen = u.spineGen = (u.spineGen || 0) + 1;
      whenLoaded(t, () => { if (!u.spineOn || u.spineGen !== gen) { unref(t); return; } const old = mat.map; mat.map = t; mat.needsUpdate = true; if (old && old !== t) freeMap(old); }, () => unref(t));
    }
  }
  // Covers: made / loaded for the cases near the selection (two per frame, nearest first; generated ones are encoded
  // off the main thread and arrive later) and given back once the selection is more than COVERS cases away
  const COVERS = 15; let selKey = '';
  function loadFront(b) {
    const u = b.userData; u.frontLoaded = true; const gen = wallGen; fst.fr++;
    const url = typeof u.g.front === 'function' ? u.g.front() : u.g.front;
    if (url && url.then) { const req = u.frontReq = url; url.then(v => { if (u.frontReq !== req || !u.frontLoaded || gen !== wallGen || !active) return; u.frontReq = null; setFront(u, v); }, () => {}); }
    else setFront(u, url);
  }
  // the case keeps its own colour until the picture has arrived; a picture that fails to load leaves it that way
  function setFront(u, url) { if (!url) return; const t = loadTex(url, 'front'); u.front.map = null; u.front.color.set(u.st.caseColor); u.front.needsUpdate = true; u.frontTex = t;
    whenLoaded(t, () => { if (u.frontTex !== t || !u.frontLoaded) return; u.front.map = t; u.front.color.set(0xffffff); u.front.needsUpdate = true; }, () => {}); }
  function dropFront(u) { u.frontLoaded = false; u.frontReq = null; const m = u.front.map, t = u.frontTex; u.frontTex = null; if (t && t !== m) freeMap(t); if (!m) return; /* (t differs from the map while a new picture loads over the old one: refreshArt) */ u.front.map = null; u.front.color.set(u.st.caseColor); u.front.needsUpdate = true; freeMap(m); }
  // (0.3.5) new box art for games on the wall (Android's Art Studio over the museum, or its background fetch): fresh(g)
  // gives the game's pictures now ({ spine, back, disc, boxFull }; the front is read through g.front as always). The old
  // cover stays up until the new one has loaded; a spine is made again, a back cover is fetched again when next held.
  function refreshArt(ids, fresh) {
    if (!active || !rows.length) return 0; const want = new Set((ids || []).map(String)); let n = 0;
    rows.forEach(rw => rw.boxes.forEach(b => { const u = b.userData; if (!u || u.mail || !u.g || !want.has(String(u.g.id))) return;
      const g = u.g, a = typeof fresh === 'function' ? fresh(g) : null; n++;
      if (a) { if ((a.spine || null) !== (g.spine || null)) { g.spine = a.spine || null; if (u.spineOn || u.liteOn) { dropSpine(u); work.push(b); } }
        if ((a.back || null) !== (g.back || null)) { g.back = a.back || null; if (u.backM && b !== heldCase) dropBack(u, b); }
        g.disc = a.disc || null; g.boxFull = !!a.boxFull; }
      if (!u.frontLoaded) return; // (it's read again when it comes near)
      const url = typeof g.front === 'function' ? g.front() : g.front;
      if (!url || url.then) { dropFront(u); loadFront(b); return; } // a generated cover: made again
      const t = loadTex(url, 'front'); if (!t) return; if (t === u.frontTex || t === u.front.map) { unref(t); return; } // the same picture
      if (u.frontTex && u.frontTex !== u.front.map) freeMap(u.frontTex);
      u.frontTex = t;
      whenLoaded(t, () => { if (u.frontTex !== t || !u.frontLoaded) return; const was = u.front.map; u.front.map = t; u.front.color.set(0xffffff); u.front.needsUpdate = true; if (was && was !== t) freeMap(was); }, () => {});
    }));
    return n;
  }
  // the last case on each row shows its whole front cover at the end of the row (it faces the console), so its picture is
  // always loaded, on every row, not only when you're near it
  function ensureEnds() { rows.forEach(rw => { const b = rw.boxes[rw.boxes.length - 1]; if (b && !b.userData.frontLoaded) loadFront(b); }); }
  function trimFronts(r) { ensureEnds(); const ri0 = rows.indexOf(r); rows.forEach((rw, ri) => rw.boxes.forEach((b, i) => { const u = b.userData; if (u.mail) return; const end = i === rw.boxes.length - 1; const far = Math.abs(ri - ri0) > 1 || (rw === r && Math.abs(i - colI) > COVERS); /* the shelf above and below keep theirs, so going back is free */ if (end && u.frontLoaded) { if (u.backM && far && b !== heldCase && !(peek && peek.box === b)) dropBack(u, b); return; } if (u.frontLoaded && far) dropFront(u); if (u.backM && far && b !== heldCase && !(peek && peek.box === b)) dropBack(u, b); })); }

  function stats(r, hist, favs) {
    let played = 0, ms = 0, top = null, topMs = -1, fav = 0;
    r.games.forEach(g => { const h = hist[g.id]; if (h && h.count) { played++; ms += h.ms || 0; if ((h.ms || 0) > topMs) { topMs = h.ms || 0; top = g.title; } } if (favs.has(g.id)) fav++; });
    const hrs = ms / 36e5; return { played, hours: hrs >= 1 ? `${hrs.toFixed(1)} h played` : ms ? `${Math.round(ms / 6e4)} min played` : 'not played yet', top: topMs > 0 ? top : null, fav };
  }
  const fitZ = (w, h) => { const fov = camera.fov * DEG, asp = innerWidth / innerHeight; return Math.max((h / 2) / Math.tan(fov / 2), (w / 2) / Math.tan(fov / 2) / asp); };
  const rowView = () => { const r = rows[rowI], sel = r.boxes[colI] || null, ri = sel ? null : rackItem(); const cx = sel ? sel.userData.home.x : ri ? ri.mesh.userData.home.x + .2 : r.consoleX; if (fan && fan.it.ri === rowI && !(libHeld && libHeld.temp)) { const h0 = fan.it.mesh.userData.home; return { x: h0.x, y: r.y + .2, z: h0.z + 1.55 + 2.85 }; } if (libHeld && libHeld.ri === rowI) { const h0 = libHeld.mesh.userData.home; return { x: h0.x + hPan.x, y: r.y + .22 + hPan.y, z: h0.z + 1.7 + 2.05 / handZoom }; } if (heldCase) { const k = peek && peek.box === heldCase ? ss(peek.k) : 0, wp = heldCase.getWorldPosition(sv('rowWp')); return { x: wp.x - r.w * .5 * k / handZoom + hPan.x, y: wp.y + hPan.y, z: wp.z + (2.35 + .3 * k) / handZoom }; } /* far enough back to see the whole case (and both halves once open) */ /* open: both halves in view */ return holding ? { x: cx, y: r.y + .42, z: 3.0 } : { x: cx - .2, y: r.y + .02, z: 3.7 }; };
  // the whole wall (bookcase and reading corner), snug; with the Room menu open, framed in the space left of the menu
  const wallL = () => -.55 - (tvSide() === 'left' ? tvDims().W + .95 : 0), wallR = () => Math.max(wallW, roomObj && roomObj.rackEnd || 0) + (tvSide() === 'right' ? tvDims().W + .95 : .55);
  const overview = () => { const l = wallL(), r = wallR(), lo = tvOn && room.tvStand !== 'wall' ? .55 : 0; return { x: (l + r) / 2, y: -(rows.length - 1) * ROW / 2 + .15 - lo, z: fitZ(r - l + .6, wallH + 1.1 + lo * 2) + .25 }; }; // with the TV: a little more floor in view
  let roomView = false, snapCam = false;
  // the wide view: the stick moves a soft gold frame between the shelves and the racks; A zooms in on the one it's on
  let wideSel = 0, wideFrame = null; // 0..rows-1: a shelf; rows + u: rack unit u
  const wideCount = () => rows.length + (rack && rack.units ? rack.units.length : 0) + (tv ? 1 : 0);
  const isTvSel = i => !!tv && i === wideCount() - 1;
  function wideBox(i) {
    const plankT = .055; if (isTvSel(i)) return tvBox();
    if (i < rows.length) { const r = rows[i]; return { x0: -.47, x1: (roomObj ? roomObj.maxW : wallW) + .47, y0: r.y - H / 2 - plankT - .03, y1: r.y + ROW - H / 2 - plankT + .02, label: r.name, sub: `${r.games.length} game${r.games.length === 1 ? '' : 's'}` }; }
    const u = rack && rack.units[i - rows.length]; if (!u) { const r = rows[Math.max(0, Math.min(rows.length - 1, rowI))] || { y: 0 }; return { x0: -.47, x1: (roomObj ? roomObj.maxW : wallW) + .47, y0: r.y - H / 2 - plankT - .03, y1: r.y + ROW - H / 2 - plankT + .02, label: r.name || '', sub: '' }; }
    const plankT2 = .055; let top = ROW - H / 2 + .1, bot = rows[rows.length - 1].y - H / 2 - .1;
    if (u.top != null && rows[u.top]) { const split = rows[u.top].y + ROW - H / 2 - plankT2 - .03; if (u.band) top = split - .02; else bot = split + .02; } // the movies have the lower half
    if (u.movie) { const mv = u.n - u.shows; return { x0: u.x0 - .16, x1: u.x1 + .16, y0: bot, y1: top, label: u.label, sub: [mv ? `${mv} movie${mv === 1 ? '' : 's'}` : '', u.shows ? `${u.shows} show${u.shows === 1 ? '' : 's'}` : ''].filter(Boolean).join(' · ') || 'Nothing yet' }; }
    return { x0: u.x0 - .16, x1: u.x1 + .16, y0: bot, y1: top, label: u.label, sub: `${u.n} ${u.guide ? 'guide' : 'issue'}${u.n === 1 ? '' : 's'}` };
  }
  function wideFrameMesh() {
    if (wideFrame) return wideFrame; const THREE = T(), g = new THREE.Group(), m = new THREE.MeshBasicMaterial({ color: 0xffd98a, transparent: true, opacity: 0, depthWrite: false, toneMapped: false });
    for (let i = 0; i < 4; i++) g.add(new THREE.Mesh(new THREE.BoxGeometry(1, 1, .01), m));
    g.userData.mat = m; g.userData.k = 0; wall.add(g); wideFrame = g; return g;
  }
  function placeFrame(b, t) {
    const f = wideFrameMesh(), w = b.x1 - b.x0, h = b.y1 - b.y0, th = .035, z = b.z != null ? b.z : DEPTH / 2 + .12, cx = (b.x0 + b.x1) / 2, cy = (b.y0 + b.y1) / 2;
    const at = (m, x, y, sx, sy) => { const k = t == null ? 1 : t; m.position.set(sm(m.position.x, x, k), sm(m.position.y, y, k), z); m.scale.set(sm(m.scale.x, sx, k), sm(m.scale.y, sy, k), 1); };
    const [a, b2, c, d] = f.children; at(a, cx, b.y1, w + th, th); at(b2, cx, b.y0, w + th, th); at(c, b.x0, cy, th, h); at(d, b.x1, cy, th, h);
  }
  function wideFromHere() { const r = rows[rowI]; if (r && colI > r.boxes.length && rack && rack.units && rack.units.length) { const it = rackItem(); const u = it ? rack.units.findIndex(u => !!u.movie === !!it.movie && it.mesh.userData.home.x >= u.x0 && it.mesh.userData.home.x <= u.x1) : 0; return rows.length + Math.max(0, u); } return rowI; }
  function wideMove(dx, dy) {
    const n = wideCount(); if (!n) return; let i = wideSel;
    if (isTvSel(i)) { if (dx < 0) { const U = (rack && rack.units) || [], pr = U.map((x, j) => j).filter(j => !U[j].movie && !U[j].band).sort((p, q) => U[q].x0 - U[p].x0); i = pr.length ? rows.length + pr[0] : Math.min(rows.length - 1, Math.max(0, rowI)); wideSel = i; hooks.onSelect && hooks.onSelect(current()); } return; } // the TV: left goes back to the reading corner
    if (i < rows.length) { if (dy) i = Math.max(0, Math.min(rows.length - 1, i + dy)); else if (dx > 0 && n > rows.length) i = rows.length; }
    else if (!rack || !rack.units) i = Math.min(rows.length - 1, Math.max(0, rowI)); // the rack went away (library emptied): back to the shelves
    else { const U = rack.units, u = U[i - rows.length], idx = j => rows.length + j;
      if (dy && u && u.band != null) { const want = u.band + dy, mid = (u.x0 + u.x1) / 2, j = U.findIndex(x => x.band === want && mid >= x.x0 - .01 && mid <= x.x1 + .01); if (want >= 0 && want <= 1 && j >= 0) i = idx(j); } // straight up / down: same column
      else if (dx) { const same = U.map((x, j) => j).filter(j => (U[j].band || 0) === ((u && u.band) || 0)).sort((p, q) => U[p].x0 - U[q].x0), at = same.indexOf(i - rows.length), t = at + dx;
        i = t < 0 ? Math.min(rows.length - 1, Math.max(0, rowI)) : t < same.length ? idx(same[t]) : tv ? n - 1 : i; } // past the last unit: the TV
      if (i >= rows.length && rack.units[i - rows.length] && !rack.units[i - rows.length].movie) rack.lastPrint = i - rows.length; }
    if (i !== wideSel) { wideSel = i; hooks.onSelect && hooks.onSelect(current()); }
  }
  function wideEnter() {
    const i = wideSel;
    if (isTvSel(i)) { wide = false; tvView = true; tvReturn = null; const was = tv.power && tv.pow > 0; tvPower(true); lastInput = lastNav = woke(); return { tv: true, playing: was }; } // already on (left playing): carry on
    if (i >= rows.length && !(rack && rack.units && rack.units[i - rows.length])) { wideSel = Math.max(0, Math.min(wideSel, wideCount() - 1)); hooks.onSelect && hooks.onSelect(current()); return null; } // the rack changed under the frame
    if (i >= rows.length && rack && rack.units[i - rows.length] && !rack.units[i - rows.length].n) return rack.units[i - rows.length].movie ? null : { find: rack.units[i - rows.length].guide ? 'guide' : 'magazine' }; // an empty rack: go find some
    wide = false; zoomAt = performance.now();
    if (i < rows.length) { if (rowI !== i) { rowI = i; colI = Math.min(colI, rows[i].boxes.length); } if (colI > rows[i].boxes.length) colI = 0; }
    else { const u = rack && rack.units[i - rows.length]; if (!u) return null; let done = false;
      for (let ri = 0; ri < rows.length && !done; ri++) { const list = rack.byRow[ri] || []; const k = list.findIndex(it => !!it.movie === !!u.movie && it.mesh.userData.home.x >= u.x0 && it.mesh.userData.home.x <= u.x1); if (k >= 0) { rowI = ri; colI = rows[ri].boxes.length + 1 + k; done = true; } } }
    hooks.onSelect && hooks.onSelect(current());
  }
  function roomOverview() {
    const l = wallL(), r = wallR(), w = r - l, fov = camera.fov * DEG, asp = innerWidth / innerHeight, side = Math.min(.45, (Math.min(420, innerWidth * .4) + 56) / innerWidth); // the menu's share of the screen
    const z = Math.max((wallH + 1.5) / 2 / Math.tan(fov / 2), (w + .8) / 2 / Math.tan(fov / 2) / (asp * (1 - side))) + .35;
    const visW = 2 * z * Math.tan(fov / 2) * asp; return { x: (l + r) / 2 + visW * side / 2, y: -(rows.length - 1) * ROW / 2 + .15, z };
  }

  let warmed = false;
  // Compile the museum's shaders and environment while the shelf is idle, so the first real open doesn't stutter
  // Warm-up (done once, while you're on the shelf): set up the 3D renderer and compile the museum's shaders, so the first
  // visit doesn't pay for it. Split into small steps, each in a quiet moment, and the shaders compile in the
  // background (compileAsync) instead of in one long block that made the shelf hitch a second or two after startup.
  let warming = null;
  const idleStep = () => new Promise(r => window.requestIdleCallback ? requestIdleCallback(() => r(), { timeout: 1200 }) : setTimeout(r, 30));
  function warm() {
    if (warmed || warming) return warming;
    const gen = glGen; // a rebuild of the drawing surface (performance level) cancels a warm-up still in progress
    warming = (async () => {
      try {
        await idleStep(); if (gen !== glGen || !ensure()) return;
        await idleStep(); if (gen !== glGen) return; if (active) { warmed = true; return; } makeEnv();
        await idleStep(); if (gen !== glGen) return; if (active) { warmed = true; return; } // the museum opened meanwhile: it does its own
        const THREE = T(), style = { ratio: .71, depth: .1, caseColor: '#222222', caseSide: '#111111', spineInk: '#ffffff', spineBand: { bg: '#333333', fg: '#ffffff', label: 'X' }, glow: '#888888' };
        build([{ id: 'warm', name: 'Warm-up', style, games: [{ id: 'w1', title: 'Warm-up', front: null, spine: null }], console: {}, stats: { played: 0, hours: '', top: null, fav: 0 } }]);
        rows[0].lamp.castShadow = true; rows[0].lamp.intensity = 10; doWork(50); shadowRow = 0;
        if (!dummyTex) { dummyTex = new THREE.DataTexture(new Uint8Array([255, 255, 255, 255]), 1, 1); dummyTex.needsUpdate = true; }
        const u0 = rows[0].boxes[0] && rows[0].boxes[0].userData; if (u0) { u0.front.map = dummyTex; u0.front.needsUpdate = true; } // the picture-on-a-case shader too
        primeKeep = []; primeTv(rows[0]); // and the Retro TV's (its screen, a video on it, its glow), whether it's on or not
        renderer.setSize(8, 8, false); camera.aspect = 1; camera.updateProjectionMatrix(); camera.position.set(1, 0, 3); camera.lookAt(1, 0, 0);
        // one shader at a time, each in its own quiet moment (a whole set at once was a ~0.2 s block), against the real
        // scene's lights so they're the exact programs the museum will use
        { const THREE2 = T(), seen = new Set(), items = []; scene.traverse(o => { if (!o.isMesh && !o.isPoints && !o.isLine) return; (Array.isArray(o.material) ? o.material : [o.material]).forEach(m => { if (m && !seen.has(m)) { seen.add(m); items.push([o, m]); } }); });
          for (const [o, m] of items) { if (active) break; const tmp = new THREE2.Scene(), copy = new o.constructor(o.geometry, m); copy.castShadow = o.castShadow; copy.receiveShadow = o.receiveShadow; tmp.add(copy); try { renderer.compile(tmp, camera, scene); } catch {} await idleStep(); if (gen !== glGen) return; } // (a new drawing surface meanwhile: this warm-up's objects are gone)
          // let the graphics driver finish linking them in the background (parallel shader compile) before anything is
          // drawn with them: drawing straight away is what made the browser wait for every program at once
          for (let tries = 0; tries < 40 && !active; tries++) { let pending = false; for (const [, m] of items) { const pr = renderer.properties.get(m).currentProgram; if (pr && pr.isReady && !pr.isReady()) { pending = true; break; } } if (!pending) break; await new Promise(r => setTimeout(r, 25)); if (gen !== glGen) return; } }
        if (active) { warmed = true; return; }
        await idleStep(); if (gen !== glGen) return; if (active) { warmed = true; return; }
        if (u0) u0.front.map = null;
        // (no warm-up draw: the museum's own unseen preparation, while the screen is black, does the first draw)
        unprime(); closeFan(true); disposeWall(); if (shimM) { try { shimM.dispose(); } catch {} shimM = null; } rack = null; wideFrame = null; rows = []; work = []; dust = []; card = null; shadowRow = -1;
        warmed = true;
      } catch (e) { if (gen !== glGen) return; console.warn('museum warm-up', e); warmed = true; }
    })();
    return warming;
  }
  // the TV's materials, drawn tiny and unseen with the preparation whether the TV is on or not, so switching it on (or to a
  // video) mid-visit never compiles a shader; put away again by unprime()
  function primeTv(r0) { const THREE = T(), M = tvMats(); ['plastic', 'dark', 'metal', 'pale', 'led', 'clock', 'screen', 'screenV', 'shade', 'glow'].forEach(k => {
    const g = new THREE.PlaneGeometry(.004, .004); if (k === 'led') g.setAttribute('color', new THREE.Float32BufferAttribute(new Float32Array(12).fill(1), 3)); const m = new THREE.Mesh(g, M[k]); m.position.set(wallW / 2, r0 ? r0.y : 0, DEPTH / 2 + .3); m.renderOrder = 4; scene.add(m); primeKeep.push(m); }); }
  function open(data, h) {
    if (!ensure()) return false; makeEnv(); const THREE = T(); lastOpen = { data, h };
    hooks = h || {}; active = true; paused = false; pauseWhy.clear(); resize();
    try { seen = new Set(JSON.parse(localStorage.getItem('museum-seen') || '[]')); } catch { seen = new Set(); }
    // Mail: games that are new since your last visit (and ones you haven't opened yet) wait on the shelf as parcels
    { let pend = []; try { pend = JSON.parse(localStorage.getItem('museum-mail') || '[]').map(String); } catch {}
      const ids = new Set(); data.forEach(r => (r.games || []).forEach(g => ids.add(String(g.id))));
      const fresh = seen.size ? [...ids].filter(id => !seen.has(id) && !seen.has(+id)) : [];
      owner = String(data.owner || '').slice(0, 24); mailSet = data.mail === false ? new Set() : new Set([...pend, ...fresh].filter(id => ids.has(id)));
      try { mailAt = JSON.parse(localStorage.getItem('museum-mail-at') || '{}') || {}; } catch { mailAt = {}; }
      let no = ids.size - mailSet.size; for (const id of mailSet) if (!mailAt[id]) mailAt[id] = { at: Date.now(), no: ++no }; // item numbers: next in the collection
      for (const id of Object.keys(mailAt)) if (!mailSet.has(id)) delete mailAt[id];
      try { localStorage.setItem('museum-mail', JSON.stringify([...mailSet])); localStorage.setItem('museum-mail-at', JSON.stringify(mailAt)); } catch {} }
    rowI = Math.max(0, rowI | 0);
    build(data.map(r => ({ ...r, stats: stats(r, data.hist || {}, new Set(data.favs || [])) })));
    tvOn = data.tv === true; // before the rack: the room's extras make way for the TV
    lastLib = data.lib || []; try { buildRack(lastLib); } catch (e) { console.warn('rack', e); }
    try { buildTV(); } catch (e) { console.warn('tv', e); }
    const all = []; rows.forEach(r => r.boxes.forEach(b => { all.push(b.userData.g.id); b.userData.isNew = seen.size > 0 && !seen.has(b.userData.g.id) && !b.userData.mail; if (b.userData.isNew) { b.userData.slide = 1; b.position.z = b.userData.home.z + 1.1; } }));
    try { localStorage.setItem('museum-seen', JSON.stringify(all)); } catch {}
    rowI = Math.min(rowI, rows.length - 1); colI = 0; wide = false; holding = null; heldCase = null; shadowRow = -1; lastInput = woke();
    // a visit that ended with something in your hands (hold A to play with the disc out, Stremio from a Blu-ray) left the room
    // dimmed, the camera's eye-level look and the hand zoom / pan behind: the next walk-in started dark and looking straight ahead
    dimK = 0; holdLook = 0; driftK = 0; handZoom = handZoomT = 1; hPan.x = hPan.y = hPan.tx = hPan.ty = 0; hPan.lx = hPan.ly = 0; hold = { rx: 0, ry: 0, trx: 0, try: 0 }; roomView = false; preRow = -1; preOn = false;
    // the mail: one delivery for everything new, and the walk-in ends at the first new game's place
    { const list = []; rows.forEach(r => r.boxes.forEach(b => { if (b.userData.mail) { b.userData.mk = list.length; list.push(b); } }));
      dvFrom = { rowI, colI };
      if (list.length) { try { buildDelivery(list); const u0 = list[0].userData; rowI = u0.ri; colI = rows[u0.ri].boxes.indexOf(list[0]); }
        catch (e) { console.warn('mail', e); if (delivery) dropDelivery(); delivery = null; rows.forEach(r => r.boxes.forEach(b => { const u = b.userData; if (u.finalX != null) { u.home.x = u.finalX; b.position.x = u.finalX; delete u.finalX; } if (u.mail) { u.mail = false; b.visible = true; delete u.mk; } })); } } }
    const ov = overview(); camP = { x: ov.x, y: ov.y - .3, z: ov.z + 1.6 }; tw.on = false; twKey = null; camV.x = camV.y = camV.z = 0;
    intro = { on: true, t: 0, len: .9 + rows.length * .36 + .8 };
    // Room → "Lights come on as you walk in" off (or Reduce motion): straight to the overview with every lamp lit
    if (data.walkIn === false) { camP = { x: ov.x, y: ov.y, z: ov.z }; endIntro(); }
    // Shaders now, for exactly what the first frames will draw: the row in view's lamp already casting its shadow (a
    // different light setup recompiled every material on the first frame), and a case with a picture on it (the first
    // cover to arrive used to compile its shader mid-animation)
    const t0 = performance.now();
    setShadowRow();
    // the info card too: it is hidden until you zoom in to a shelf, so it was skipped here and its two shaders and its
    // 1800×880 picture were first made in the middle of the first zoom (a stutter). Its picture is drawn now (any later
    // selection draws over the same canvas) and it stays in the scene, see-through, for the unseen preparation's draws.
    unprime(); // anything left from a visit closed during its preparation
    if (card) { try { const fm = card.userData.face.material, r0 = rows[rowI], g0 = r0 && r0.boxes[0] && r0.boxes[0].userData.g; if (g0 && !fm.map) { fm.map = cardTex(g0, r0, null); fm.needsUpdate = true; } card.userData.for = null; card.userData.fade = 0; fm.opacity = card.userData.board.material.opacity = 0; if (r0) card.position.set(wallW / 2, r0.y - H / 2 + .25 * CARD_S, (DEPTH + .1) / 2); card.scale.setScalar(CARD_S); card.visible = true; } catch {} }
    try { if (!dummyTex) { dummyTex = new THREE.DataTexture(new Uint8Array([255, 255, 255, 255]), 1, 1); dummyTex.needsUpdate = true; }
      const dm = fillify(new THREE.MeshStandardMaterial({ map: dummyTex, roughness: .42 }), { value: 0 }), dmesh = new THREE.Mesh(new THREE.BoxGeometry(.01, .01, .01), [dm, dm, dm, dm, dm, dm]); dmesh.castShadow = true; dmesh.position.y = -50; scene.add(dmesh);
      const smesh = new THREE.Mesh(new THREE.PlaneGeometry(.01, .01), shimMat()), r0 = rows[rowI]; smesh.renderOrder = 4; smesh.position.set(wallW / 2, r0 ? r0.y : 0, DEPTH / 2 + .3); scene.add(smesh); primeKeep = [smesh]; // the sealed-case glint too (its material is kept, so it stays compiled)
      let pm = null; if (mailSet && mailSet.size) { if (!sparkM) sparkM = new THREE.PointsMaterial({ size: .03, vertexColors: true, transparent: true, opacity: 1, blending: THREE.AdditiveBlending, depthWrite: false }); const pg = new THREE.BufferGeometry(); pg.setAttribute('position', new THREE.Float32BufferAttribute([0, 0, 0], 3)); pg.setAttribute('color', new THREE.Float32BufferAttribute([1, 1, 1], 3)); pm = new THREE.Points(pg, sparkM); pm.position.y = -50; scene.add(pm); } // mail waiting: the unboxing sparkle too
      if (rows.length) { const f = wideFrameMesh(); f.visible = true; primeKeep.push(f); } primeTv(r0); renderer.compile(scene, camera); scene.remove(dmesh); if (pm) { scene.remove(pm); pm.geometry.dispose(); } dmesh.geometry.dispose(); dm.dispose(); } catch {}
    // then a short, unseen preparation: the shelf in view gets its covers, spines and console models and they go to the
    // graphics card before the walk-in starts, so the camera never moves over half-loaded shelves (at most ~1.4 s)
    prep = { t0: performance.now() };
    const rr = rows[rowI]; if (rr) rr.boxes.forEach((b, i) => { if (Math.abs(i - colI) <= 5 && !b.userData.mail && !b.userData.frontLoaded) loadFront(b); }); ensureEnds();
    cancelAnimationFrame(raf); lastT = 0; raf = requestAnimationFrame(frame);
    hooks.onSelect && hooks.onSelect(current());
    hooks.onReady && hooks.onReady(Math.round(performance.now() - t0));
    return true;
  }
  function close() { lastOpen = null; endPeek(); if (tv) tvOff(); tvView = false; if (delivery) dropDelivery(); opening = null; active = false; if (sparks) { try { sparks.parent.remove(sparks); sparks.geometry.dispose(); } catch {} sparks = null; } /* an unboxing's sparkle cut short */ cancelAnimationFrame(raf); raf = 0; if (canvas) canvas.classList.remove('on'); setTimeout(() => { if (!active && wall) { disposeWall(); rows = []; work = []; dust = []; card = null; dropQueued(); tex.forEach(t => { try { t.dispose(); } catch {} }); trimTex(24); if (renderer) renderer.setSize(8, 8, false); /* (beta.8) the full-screen drawing buffers go too (open() sizes them again first) */ if (rebuildGL) { rebuildGL = false; teardown(); } } }, 700); } // GPU copies only: the decoded images stay, so reopening re-uploads instead of re-downloading
  // the walk-in ends on the whole room (the overview, a gold frame on the shelf you were last on) instead of zooming into a shelf
  function endIntro() { intro.on = false; rows.forEach(r => { r.lampOn = 1; r.lamp.intensity = LAMP(); }); if (!wide && rows.length) { wide = true; wideSel = Math.max(0, Math.min(rows.length - 1, rowI)); hooks.onSelect && hooks.onSelect(current()); } }
  function skipFill() { if (!intro.on) return false; endIntro(); return true; }

  /* the console fit panel: what the shelf you're on has, and live changes (saved by the shelf in console.json) */
  function consoleFit() {
    const r = rows[rowI]; if (!r || !r.console || !r.console.model) return null; const o = r.console.opts || {}, a = r.fit;
    return { id: r.id, name: r.name, ready: !!a, parts: a ? a.parts.map(p => ({ key: p.key, label: p.label, main: !!p.main })) : [], guessYaw: a ? a.yaw : 0, controllerGuess: a ? a.controller : null,
      yaw: a ? ((yawOf(o, a) % 360) + 360) % 360 : 0, yawSaved: o.museumYaw != null, scale: +o.museumScale || 1, controller: ctlChoice(r, o, a), controllerYaw: o.controllerYaw != null ? +o.controllerYaw : null, hasFile: !!r.console.controller, shown: Object.assign({}, o.parts || {}) };
  }
  function setConsoleFit(patch) {
    const r = rows[rowI]; if (!r || !r.console || !r.console.model) return null; const o = r.console.opts = Object.assign({}, r.console.opts || {});
    Object.entries(patch || {}).forEach(([k, v]) => { if (v === null || v === undefined) delete o[k]; else o[k] = v; });
    loadModel(r); return consoleFit();
  }
  function toConsole() { const r = rows[rowI]; if (!r) return; wide = false; colI = r.boxes.length; lastInput = lastNav = woke(); hooks.onSelect && hooks.onSelect(current()); }

  /* ---- the reading corner: a magazine rack to the right of the bookcase, one ledge level with each shelf, covers
     facing out (magazines on the upper ledges, guides below). Right from a console steps onto its ledge. ---- */
  let rack = null, libHeld = null, lastLib = []; // lastLib: the reading corner's list, kept so the corner can move for the TV // libHeld: the magazine / guide in your hands // { group, items: [{ d, mesh, ri, k }], byRow: [[item…]], x0, x1 }
  const rackLen = ri => rack && rack.byRow[ri] ? rack.byRow[ri].length : 0;
  const rackItem = () => { const r = rows[rowI]; if (!r || !rack) return null; const k = colI - r.boxes.length - 1; return k >= 0 && rack.byRow[rowI] ? rack.byRow[rowI][k] || null : null; };
  const RACK_H = H * .9, RACK_W = RACK_H * .76, RACK_GAP = .1;
  function placeholderTex(d) {
    const guide = d.kind === 'guide', movie = d.kind === 'movie';
    return canvasTex(384, 512, (x, w, h) => {
      const g = x.createLinearGradient(0, 0, 0, h); g.addColorStop(0, movie ? '#16233c' : guide ? '#1d3354' : '#5a1620'); g.addColorStop(1, movie ? '#070b14' : guide ? '#0c1626' : '#22080c'); x.fillStyle = g; x.fillRect(0, 0, w, h);
      x.fillStyle = 'rgba(255,255,255,.12)'; x.fillRect(0, 0, w, 64); x.fillStyle = '#fff'; x.font = '700 26px Lexend, sans-serif'; x.textBaseline = 'middle'; x.fillText(movie ? (d.type === 'series' ? 'TV SHOW' : 'MOVIE') : guide ? 'GUIDE' : 'MAGAZINE', 22, movie ? 100 : 33);
      if (movie) x.translate(0, 70); // under the case's blue band
      x.font = '700 34px Lexend, sans-serif'; const words = d.title.split(' '); let line = '', y = 130; const lines = [];
      words.forEach(wd => { const t = line ? line + ' ' + wd : wd; if (x.measureText(t).width > w - 44 && line) { lines.push(line); line = wd; } else line = t; }); if (line) lines.push(line);
      lines.slice(0, 7).forEach(l => { x.fillText(l, 22, y); y += 46; });
      if (d.series) { x.font = '500 22px Lexend, sans-serif'; x.fillStyle = 'rgba(255,255,255,.7)'; x.fillText(d.series.slice(0, 30), 22, h - 40); }
    });
  }
  function disposeRack() {
    libHeld = null; if (!rack) return; rack.items.forEach(it => { if (it.rig) disposeRig(it); });
    closeFan(true); if (shim && rack.items.some(it => it.mesh === shim.box)) endShim(); if (sparks && rack.items.some(it => it.mesh === sparks.parent)) { try { sparks.parent.remove(sparks); sparks.geometry.dispose(); } catch {} sparks = null; }
    rack.items.forEach(it => { const m = it.mesh; if (m.parent) m.parent.remove(m); m.geometry.dispose();
      [...m.children].forEach(ch => ch.traverse(n => { if (n.geometry) n.geometry.dispose(); if (n.material && !n.userData.keepMat) [].concat(n.material).forEach(mt => { if (mt.map) mt.map.dispose(); mt.dispose(); }); })); [].concat(m.material).forEach((mt, i) => { if (i === 4) { freeMap(mt.map); if (it.ph && it.ph !== mt.map) freeMap(it.ph); } if (i === 5 && it.backTex) { freeMap(it.backTex); it.backTex = null; } mt.dispose(); }); });
    const shared = new Set([roomObj && roomObj.woodMat && roomObj.woodMat.map, beamTex(), ...roomTex.values()]);
    rack.group.traverse(n => { if (n.geometry) n.geometry.dispose(); if (n.material) [].concat(n.material).forEach(mt => { if (mt !== (roomObj && roomObj.woodMat)) { if (mt.map && !mt.map.userData.url && !shared.has(mt.map)) mt.map.dispose(); mt.dispose(); } }); });
    if (rack.group.parent) rack.group.parent.remove(rack.group);
    rack = null; if (roomObj) roomObj.rackEnd = 0;
  }
  // Which box a magazine or guide goes in: the magazine's brand (every Xbox magazine together, whatever the issue or
  // folder is called; Nintendo Power together; Game Informer together…), a guide's publisher; anything not recognised
  // falls back to its folder. Display only: no files are moved.
  const MAG_BRANDS = [ // a magazine's own name first (Game Informer's Wii special is still Game Informer), then the console makers' official ones
    [/nintendo power/i, 'Nintendo Power'], [/game ?informer/i, 'Game Informer'], [/electronic gaming monthly|\begm\b/i, 'EGM'], [/\bgame ?pro\b/i, 'GamePro'], [/\bgame ?fan\b/i, 'GameFan'],
    [/pc gamer/i, 'PC Gamer'], [/computer gaming world|\bcgw\b/i, 'Computer Gaming World'], [/next generation/i, 'Next Generation'], [/retro ?gamer/i, 'Retro Gamer'], [/\bign\b/i, 'IGN'],
    [/\bxbox\b/i, 'Xbox'], [/playstation|\bps[1-5]\b|\bopm\b/i, 'PlayStation'], [/\bnintendo\b|\bn64\b|gamecube|\bngc\b|\bwii\b|\bswitch\b/i, 'Nintendo'], [/\bsega\b|dreamcast|\bsaturn\b|mega ?drive|genesis/i, 'Sega']];
  const GUIDE_BRANDS = [[/nintendo power/i, 'Nintendo Power'], [/\bprima\b/i, 'Prima'], [/\bbrady/i, 'BradyGames'], [/piggyback/i, 'Piggyback'], [/versus books/i, 'Versus Books'], [/future ?press/i, 'Future Press'], [/doublejump/i, 'DoubleJump']];
  const groupOf = d => { const t = (d.series || '') + ' ' + (d.title || ''); for (const [re, name] of d.kind === 'guide' ? GUIDE_BRANDS : MAG_BRANDS) if (re.test(t)) return name; return d.series || ''; };
  const yearOf = d => { const m = /\b(19[5-9]\d|20[0-4]\d)\b/.exec(d.title || ''); return m ? +m[1] : 0; };
  const yearSpan = ys => { const v = ys.filter(Boolean); if (!v.length) return ''; const a = Math.min(...v), b = Math.max(...v); return a === b ? String(a) : a + '–' + b; };
  // A magazine file box, the way collectors keep a run of issues: an open holder (deeper the more issues it holds),
  // the newest cover standing at the front with its top half showing above the label panel, page edges behind it.
  // Children of the cover, so it slides out and dims with it. Guides get a navy box, magazines a colour from the
  // series' name (the same series always gets the same colour).
  function buildBox(it, th, guide) {
    const THREE = T(), m = it.mesh, n = it.box.issues.length, D = Math.min(.3, .03 + n * (guide ? .016 : .006));
    let hsh = 0; for (const ch of it.box.series) hsh = (hsh * 31 + ch.charCodeAt(0)) >>> 0;
    const col = new THREE.Color().setHSL(guide ? .6 : (hsh % 360) / 360, guide ? .45 : .42, guide ? .2 : .24);
    const boxM = new THREE.MeshStandardMaterial({ color: col, roughness: .62, metalness: .05 });
    const paper = new THREE.Mesh(new THREE.BoxGeometry(RACK_W * .97, RACK_H * .97, D), new THREE.MeshStandardMaterial({ color: 0xe9e2d2, roughness: .95 }));
    paper.position.set(0, -RACK_H * .015, -th / 2 - D / 2 - .002); m.add(paper);
    const W = RACK_W * 1.07, d0 = th + D + .02, zc = th / 2 - d0 / 2 + .006, hB = RACK_H * .9, hF = RACK_H * .4, bot = -RACK_H / 2 - .008, t = .014;
    const add = (w, h, dd, x, y, z, mat) => { const p = new THREE.Mesh(new THREE.BoxGeometry(w, h, dd), mat || boxM); p.position.set(x, y, z); p.castShadow = true; m.add(p); return p; };
    add(W, t, d0, 0, bot - t / 2, zc); // bottom
    add(W, hB, t, 0, bot + hB / 2, zc - d0 / 2 - t / 2); // back
    [-1, 1].forEach(sg => { const side = add(t, hB, d0, sg * (W / 2 - t / 2), bot + hB / 2, zc); // sides, cut on a slant from the back down to the front panel
      const pos = side.geometry.attributes.position; for (let i = 0; i < pos.count; i++) if (pos.getY(i) > 0 && pos.getZ(i) > 0) pos.setY(i, -hB / 2 + hF); pos.needsUpdate = true; side.geometry.computeVertexNormals(); });
    // the front panel: the series name, how many issues, the years
    const years = yearSpan(it.box.years);
    const lt = canvasTex(512, Math.round(512 * hF / W), (c, w, h) => { const g = c.createLinearGradient(0, 0, 0, h); g.addColorStop(0, '#' + col.clone().multiplyScalar(1.35).getHexString()); g.addColorStop(1, '#' + col.getHexString()); c.fillStyle = g; c.fillRect(0, 0, w, h);
      c.fillStyle = '#efe6d2'; c.fillRect(26, h * .2, w - 52, h * .58); c.strokeStyle = '#b8924f'; c.lineWidth = 4; c.strokeRect(26, h * .2, w - 52, h * .58);
      c.fillStyle = '#2a1c08'; c.textAlign = 'center'; c.textBaseline = 'middle'; let fs = 52; c.font = `700 ${fs}px Lexend, sans-serif`; while (c.measureText(it.box.series).width > w - 80 && fs > 22) { fs -= 2; c.font = `700 ${fs}px Lexend, sans-serif`; }
      c.fillText(it.box.series, w / 2, h * .4); c.font = '500 30px Lexend, sans-serif'; c.fillStyle = '#5a4526'; c.fillText(`${n} ${guide ? 'guides' : 'issues'}${years ? ' · ' + years : ''}`, w / 2, h * .62); });
    const fm = new THREE.MeshStandardMaterial({ map: lt, roughness: .5, emissive: 0xffffff, emissiveMap: lt, emissiveIntensity: .22 });
    add(W, hF, t, 0, bot + hF / 2, zc + d0 / 2 + t / 2, [boxM, boxM, boxM, boxM, fm, boxM]);
    it.labelM = fm;
  }
  // ---- the movie shelves: your Stremio library on the lower half of the reading corner, Movies then TV Shows (each A–Z,
  // the most recently added first when there are more than fit), as Blu-ray cases: blue plastic, the title's poster on
  // the front under the blue band. A lifts one into your hands; A again opens it in Stremio. ----
  const MOV_H = RACK_H * .98, MOV_W = MOV_H * .789, MOV_GAP = .07;
  let bdBandT = null;
  function bdBand() { if (bdBandT && bdBandT.image && bdBandT.image.width) return bdBandT; bdBandT = canvasTex(512, 64, (c, w, h) => { const gr = c.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, '#2f7fe0'); gr.addColorStop(1, '#1a4fa8'); c.fillStyle = gr; c.fillRect(0, 0, w, h); c.fillStyle = '#fff'; c.font = '800 40px Lexend, sans-serif'; c.textBaseline = 'middle'; c.fillText('BLU-RAY', 18, h / 2 + 2); c.font = '600 18px Lexend, sans-serif'; c.textAlign = 'right'; c.globalAlpha = .8; c.fillText('STREMIO', w - 16, h / 2 + 2); }); return bdBandT; }
  function buildMovies(movies, g, units, byRow, all, top, plankT, lipM) {
    const printUnits = units.slice(); units.forEach(u => { u.band = 0; u.top = top; }); // the magazines and guides keep the top half
    const THREE = T(), typeOf = d => d.type === 'series' ? 1 : 0, nat = (a, b) => String(a).localeCompare(String(b), undefined, { numeric: true, sensitivity: 'base' });
    // movies under the magazines (left), TV shows under the guides (right); each A–Z, the newest kept when there are too many
    const cols = printUnits.slice(0, 2).map((u, c) => { const per = Math.max(1, Math.floor((u.width - MOV_GAP) / (MOV_W + MOV_GAP))), cap = per * (rows.length - top);
      return { u, per, label: c ? 'TV SHOWS' : 'MOVIES', list: movies.filter(d => typeOf(d) === c).slice(0, cap).sort((x, y) => nat(x.title, y.title)) }; });
    const edgeM = new THREE.MeshStandardMaterial({ color: 0x1f5fc4, roughness: .32, metalness: .05 }), backM0 = new THREE.MeshStandardMaterial({ color: 0x14306a, roughness: .5 });
    const geo = caseGroups(new THREE.BoxGeometry(MOV_W, MOV_H, .032), MOV_GROUPS), bandG = new THREE.PlaneGeometry(MOV_W, MOV_W * 64 / 512 * 1.15), bandM = new THREE.MeshStandardMaterial({ map: bdBand(), roughness: .35, emissive: 0xffffff, emissiveMap: bdBand(), emissiveIntensity: .22, polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2 }); // drawn over the cover, never fighting it (it flickered)
    const r0 = rows[top];
    cols.forEach(({ u, label, list }, c) => {
      // a brass plate on the back of the unit, over its first movie ledge
      const plT = canvasTex(1040, 160, (q, w, h) => { const gr = q.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, '#e2c486'); gr.addColorStop(.5, '#b8924f'); gr.addColorStop(1, '#8a6a34'); q.fillStyle = gr; q.fillRect(0, 0, w, h); q.strokeStyle = 'rgba(60,40,10,.6)'; q.lineWidth = 6; q.strokeRect(10, 10, w - 20, h - 20); q.fillStyle = '#2a1c08'; q.font = '800 84px Lexend, sans-serif'; q.textAlign = 'center'; q.textBaseline = 'middle'; q.fillText(label, w / 2, h / 2 + 4); });
      const plW = Math.min(u.width * .6, 1.5), plate = new THREE.Mesh(new THREE.PlaneGeometry(plW, plW / 6.5), new THREE.MeshStandardMaterial({ map: plT, metalness: .6, roughness: .32, emissive: 0xffffff, emissiveMap: plT, emissiveIntensity: .18 })); plate.position.set(u.cx, r0.y - H / 2 + MOV_H + .2, -DEPTH / 2 + .03); g.add(plate);
      units.push({ label: c ? 'TV shows' : 'Movies', movie: true, band: 1, top, x0: u.x0, x1: u.x1, n: list.length, shows: c ? list.length : 0 });
      let n = 0;
      for (let ri = top; ri < rows.length && n < list.length; ri++) {
        const row = rows[ri];
        for (let k = 0; k < cols[c].per && n < list.length; k++, n++) {
          const d = list[n], ph = placeholderTex(d), ix = u.x0 + MOV_GAP + MOV_W / 2 + k * (MOV_W + MOV_GAP);
          const front = new THREE.MeshStandardMaterial({ map: ph, roughness: .38, emissive: 0xffffff, emissiveMap: ph, emissiveIntensity: .3 }), backM = backM0.clone();
          const m = new THREE.Mesh(geo, [edgeM, front, backM]); m.castShadow = true; // (the four edges in one draw: 3 a case, not 6)
          m.position.set(ix, -H / 2 + MOV_H / 2 + .01, -DEPTH / 2 + .2); m.rotation.x = -12 * DEG; m.userData = { lib: true, home: m.position.clone(), p: 0, lift: 0 };
          const band = new THREE.Mesh(bandG, bandM); band.position.set(0, MOV_H / 2 - MOV_W * 64 / 512 * 1.15 / 2, .0185); m.add(band);
          row.group.add(m); const it = { d, mesh: m, ri, k: byRow[ri].length, front, backM, ph, coverOn: false, box: null, bag: null, movie: true }; all.push(it); byRow[ri].push(it);
        }
      }
    });
    rows.forEach((r, ri) => byRow[ri].sort((x, y) => x.mesh.position.x - y.mesh.position.x).forEach((it, k) => { it.k = k; })); // left to right on each ledge
  }
  // ---- a Blu-ray in your hands opens like a real one: A swings the cover open on its hinge (the insert inside the
  // cover, the disc on its hub in the tray), A again lifts the disc out to hold and turn, A once more plays it in
  // Stremio. B steps back: disc in, case shut, then onto the shelf. Built the first time a case is opened. ----
  let trayT = null;
  function trayTex() { if (trayT && trayT.image && trayT.image.width) return trayT; trayT = canvasTex(400, 512, (c, w, h) => { const g0 = c.createLinearGradient(0, 0, w, h); g0.addColorStop(0, '#2a6fd0'); g0.addColorStop(1, '#174a96'); c.fillStyle = g0; c.fillRect(0, 0, w, h);
      c.fillStyle = 'rgba(255,255,255,.08)'; for (let y = 0; y < h; y += 6) c.fillRect(0, y, w, 1); // moulded texture
      const cx = w / 2, cy = h * .5, R = w * .43; c.fillStyle = 'rgba(0,0,0,.18)'; c.beginPath(); c.arc(cx, cy, R * 1.02, 0, Math.PI * 2); c.fill(); // the disc well
      c.strokeStyle = 'rgba(255,255,255,.25)'; c.lineWidth = 3; c.beginPath(); c.arc(cx, cy, R * 1.02, 0, Math.PI * 2); c.stroke();
      c.fillStyle = 'rgba(255,255,255,.35)'; c.font = '800 22px Lexend, sans-serif'; c.textAlign = 'center'; c.fillText('BLU-RAY DISC', cx, h - 28); }); trayT.userData.shared = true; return trayT; }
  let discBackT = null;
  function discBackTex() { if (discBackT && discBackT.image && discBackT.image.width) return discBackT; discBackT = canvasTex(512, 512, (c, w, h) => { const r = w / 2; c.clearRect(0, 0, w, h);
      const sv = c.createRadialGradient(r, r, r * .2, r, r, r); sv.addColorStop(0, '#c9d0dc'); sv.addColorStop(.5, '#e9edf3'); sv.addColorStop(1, '#aeb6c4'); c.fillStyle = sv; c.beginPath(); c.arc(r, r, r, 0, Math.PI * 2); c.fill();
      const rb = c.createConicGradient(0, r, r); ['rgba(255,80,160,.35)', 'rgba(255,220,80,.3)', 'rgba(80,255,200,.3)', 'rgba(80,140,255,.35)', 'rgba(220,90,255,.3)', 'rgba(255,80,160,.35)'].forEach((col, i, a) => rb.addColorStop(i / (a.length - 1), col));
      c.fillStyle = rb; c.beginPath(); c.arc(r, r, r * .97, 0, Math.PI * 2); c.arc(r, r, r * .36, 0, Math.PI * 2, true); c.fill(); // the data side catches the light in a rainbow
      c.strokeStyle = 'rgba(120,130,150,.5)'; c.lineWidth = 4; c.beginPath(); c.arc(r, r, r * .36, 0, Math.PI * 2); c.stroke();
      c.fillStyle = 'rgba(210,216,226,1)'; c.beginPath(); c.arc(r, r, r * .3, 0, Math.PI * 2); c.fill();
      c.globalCompositeOperation = 'destination-out'; c.beginPath(); c.arc(r, r, r * .11, 0, Math.PI * 2); c.fill(); c.globalCompositeOperation = 'source-over'; }); discBackT.userData.shared = true; return discBackT; }
  function movieRig(it) {
    if (it.rig) return it.rig; const THREE = T(), m = it.mesh, W = MOV_W, Hh = MOV_H, D = .032, R = W * .43, own = [];
    const mk = (geo, mat) => { const x = new THREE.Mesh(geo, mat); own.push(geo); return x; };
    const edgeM = [].concat(m.material)[0], insideM = new THREE.MeshStandardMaterial({ color: 0x1d4f9e, roughness: .5 }), trayM = new THREE.MeshStandardMaterial({ map: trayTex(), roughness: .45, color: 0xffffff });
    const hinge = new THREE.Group(); hinge.position.set(-W / 2, 0, D / 2); m.add(hinge);
    const cover = mk(new THREE.BoxGeometry(W, Hh, .003), [edgeM, edgeM, edgeM, edgeM, it.front, insideM]); cover.position.set(W / 2, 0, .0015); cover.userData.keepMat = true; hinge.add(cover);
    const band = m.children.find(ch => ch.geometry && ch.geometry.type === 'PlaneGeometry'); let band2 = null; if (band) { band2 = new THREE.Mesh(band.geometry, band.material); band2.userData.keepMat = true; band2.position.set(0, band.position.y, .0035); cover.add(band2); }
    const discG = new THREE.Group(); discG.position.set(0, 0, D / 2 + .0015); m.add(discG);
    const discBody = mk(new THREE.CylinderGeometry(R, R, .0012, 64), new THREE.MeshStandardMaterial({ color: 0xd8dde6, metalness: .35, roughness: .3 })); discBody.rotation.x = Math.PI / 2; discBody.userData.keepMat = true; discG.add(discBody);
    const printM = new THREE.MeshStandardMaterial({ color: 0x2a5fb8, roughness: .45, transparent: true, alphaTest: .4 }), print = mk(new THREE.CircleGeometry(R * .995, 64), printM); print.position.z = .0007; print.userData.keepMat = true; discG.add(print);
    const dataM = new THREE.MeshStandardMaterial({ map: discBackTex(), roughness: .12, metalness: .55, transparent: true, alphaTest: .4, emissive: 0xffffff, emissiveMap: discBackTex(), emissiveIntensity: .12 }), dataSide = mk(new THREE.CircleGeometry(R * .995, 64), dataM); dataSide.rotation.y = Math.PI; dataSide.position.z = -.0007; dataSide.userData.keepMat = true; discG.add(dataSide);
    const hub = mk(new THREE.CylinderGeometry(R * .14, R * .16, .006, 32), new THREE.MeshStandardMaterial({ color: 0x163f80, roughness: .5 })); hub.rotation.x = Math.PI / 2; hub.position.set(0, 0, D / 2 + .002); hub.userData.keepMat = true; m.add(hub);
    hinge.visible = discG.visible = hub.visible = false;
    it.rig = { hinge, cover, band, band2, discG, hub, insideM, trayM, printM, dataM, discBody, own, open: 0, openT: 0, disc: 0, discT: 0, art: false };
    return it.rig;
  }
  function movieArt(it) { const rg = it.rig; if (!rg || rg.art || !hooks.movieArt) return; rg.art = true; const gen = wallGen;
    Promise.resolve(hooks.movieArt(it.d)).then(a => { if (!a || gen !== wallGen || it.rig !== rg) return;
      if (a.disc) { const t = loadTex(a.disc, 'disc'); rg.discTex = t; whenLoaded(t, () => { if (it.rig !== rg) return; rg.printM.map = t; rg.printM.color.set(0xffffff); rg.printM.needsUpdate = true; }, () => {}); }
      if (a.inside) { const t = loadTex(a.inside, 'inside'); rg.insideTex = t; whenLoaded(t, () => { if (it.rig !== rg) return; rg.insideM.map = t; rg.insideM.color.set(0xffffff); rg.insideM.needsUpdate = true; }, () => {}); } }, () => {}); }
  // one frame of a held Blu-ray's case: the cover's swing, the tray showing through, the disc lifting out
  function stepMovieRig(it, dt) {
    const rg = it.rig; if (!rg) return 0; const m = it.mesh, k = 1 - Math.exp(-dt * 6);
    rg.open = sm(rg.open, rg.openT, k); if (Math.abs(rg.open - rg.openT) < 1e-3) rg.open = rg.openT; rg.disc = sm(rg.disc, rg.discT, 1 - Math.exp(-dt * 5)); if (Math.abs(rg.disc - rg.discT) < 1e-3) rg.disc = rg.discT;
    const shown = rg.open > .001, mats = m.material; rg.hinge.visible = rg.discG.visible = rg.hub.visible = shown; if (rg.band) rg.band.visible = !shown;
    if (mats[MOV_FRONT] !== (shown ? rg.trayM : it.front)) { mats[MOV_FRONT] = shown ? rg.trayM : it.front; }
    const eo = ss(rg.open), ed = ss(rg.disc); rg.hinge.rotation.y = -eo * Math.PI * .94;
    rg.discG.position.set(0, ed * .04, .016 + .0015 + ed * .26); rg.discG.scale.setScalar(1 + ed * .12);
    // the disc has its own angles (turned while it's out, the case set square behind it), eased home by the short way when it
    // goes back: it used to borrow the case's hold.ry, so every turn you'd given the case was replayed as it went onto the hub
    { const kd = 1 - Math.exp(-dt * (rg.discT ? 7 : 14)), tY = rg.discT ? rg.dry || 0 : 0, tX = rg.discT ? rg.drx || 0 : 0; rg.cry = sm(rg.cry || 0, tY, kd); rg.crx = sm(rg.crx || 0, tX, kd); if (Math.abs(rg.cry - tY) < .01) rg.cry = tY; if (Math.abs(rg.crx - tX) < .01) rg.crx = tX; }
    if (rg.disc > .001) { const f = rg.discT ? 1 : ed; /* square again by the time it's on the hub */ rg.discG.rotation.set(rg.crx * f * DEG, rg.cry * f * DEG, 0); } else rg.discG.rotation.set(0, 0, 0);
    moved(rg.hinge); moved(rg.discG);
    return eo;
  }
  function disposeRig(it) { const rg = it.rig; if (!rg) return; it.rig = null; const m = it.mesh; try { m.material[MOV_FRONT] = it.front; [rg.hinge, rg.discG, rg.hub].forEach(n => m.remove(n)); } catch {}
    rg.own.forEach(g => g.dispose()); [rg.insideM, rg.trayM, rg.printM, rg.dataM, rg.discBody.material, rg.hub.material].forEach(mt => { if (mt.map && mt.map !== trayT && mt.map !== discBackT && mt.map !== rg.discTex && mt.map !== rg.insideTex) mt.map.dispose(); mt.dispose(); }); if (rg.discTex) freeMap(rg.discTex); if (rg.insideTex) freeMap(rg.insideTex); }
  // ---- the retro TV (Room menu → Retro TV): an old CRT on a wooden cart (a VCR blinking 12:00 under it), on a low cabinet
  // with a couple of consoles inside, as a floor-standing tube set, or on a wall bracket; small, medium or large; left or
  // right of the room, or between the bookcase and the reading corner (which moves along to make room). From the overview A
  // switches it on and brings you in close; left / right change what's on, up / down the channel (vintage commercials, game
  // trailers, recently played), Y opens the TV guide, X switches it off, B steps back and leaves it playing. Holding a
  // game, Y shows its ad. Built from a few merged meshes (one per material) on materials made once per visit; the picture
  // effects (scanlines, curvature, glow, static, the power-on line) are in the screen's own shader: no extra pass or light. ----
  let tv = null, tvView = false, tvReturn = null, tvOn = false, tvM = null, tvPeekAt = -1e9;
  const TV_SZ = { s: .8, m: 1, l: 1.3 }, TV_CH = { ads: 'Vintage commercials', trailers: 'Game trailers', recent: 'Recently played', mix: 'Shuffle all' };
  // the set's measurements for the current choices (needed before it's built: the reading corner makes room for it in the middle)
  function tvDims() { const S = TV_SZ[room.tvSize] || 1, st = ['cart', 'cabinet', 'floor', 'wall'].includes(room.tvStand) ? room.tvStand : 'cart', TW = 1.25 * S, TH = .98 * S, TD = .78 * S;
    return { S, st, TW, TH, TD, W: st === 'cart' ? TW + .22 : st === 'cabinet' ? Math.max(TW + .55, 1.8) : st === 'floor' ? TW * 1.4 : TW }; }
  const tvSide = () => !tvOn ? null : room.tvPos === 'left' || room.tvPos === 'centre' ? room.tvPos : 'right';
  const tvGap = () => tvSide() === 'centre' ? tvDims().W + 1.1 : 0; // how far the reading corner moves right for a TV in the middle
  // the screen: the picture bent onto the curved glass, scanlines, a soft glow and a dark edge (uCrt: Settings' strength),
  // static (uNoise), and switching on (uPow 0→1: a bright line opens into the picture) and off (it folds back to a dot)
  const CRT_FRAG = `#ifdef USE_MAP
	vec2 cc = vMapUv * 2.0 - 1.0; cc *= 1.0 + uCrt * 0.11 * dot( cc.yx, cc.yx ); vec2 cuv = cc * 0.5 + 0.5;
	float inside = step( 0.0, cuv.x ) * step( cuv.x, 1.0 ) * step( 0.0, cuv.y ) * step( cuv.y, 1.0 );
	vec4 sampledDiffuseColor = texture2D( map, clamp( cuv, 0.0, 1.0 ) );
	#ifdef DECODE_VIDEO_TEXTURE
		sampledDiffuseColor = sRGBTransferEOTF( sampledDiffuseColor );
	#endif
	vec3 pic = sampledDiffuseColor.rgb;
	float nz = fract( sin( dot( floor( vMapUv * vec2( 200.0, 150.0 ) ) + floor( uTime * 24.0 ) * vec2( 7.13, 3.71 ), vec2( 12.9898, 78.233 ) ) ) * 43758.5453 );
	pic = mix( pic, vec3( nz * nz * 0.9 ), uNoise );
	pic *= 1.0 - uCrt * 0.4 * ( 0.5 - 0.5 * sin( cuv.y * 1005.3 ) );
	pic *= 1.0 + uCrt * 0.3; pic += uCrt * 0.18 * pic * pic;
	pic *= mix( 1.0, smoothstep( 1.3, 0.5, length( cc ) ), 0.15 + uCrt * 0.75 );
	float on = step( 0.001, uPow ), sy = max( smoothstep( 0.3, 0.8, uPow ), 0.008 ), sx = smoothstep( 0.0, 0.3, uPow );
	pic = mix( pic, vec3( 1.4 ), ( 1.0 - smoothstep( 0.25, 0.7, uPow ) ) * on ) * ( 1.0 - smoothstep( sy, sy + 0.03, abs( cc.y ) ) ) * ( 1.0 - smoothstep( sx, sx + 0.03, abs( cc.x ) ) ) * inside * on;
	vec3 glass = mix( vec3( 0.085, 0.095, 0.11 ), vec3( 0.012, 0.014, 0.017 ), clamp( length( vMapUv - vec2( 0.32, 0.72 ) ) * 1.5, 0.0, 1.0 ) );
	diffuseColor.rgb = glass + pic + vec3( exp( -dot( cc, cc ) * 70.0 ) * ( 1.0 - sx ) * on * 1.5 );
#endif`;
  // the TV's materials, made once per visit (and compiled with the rest of the room when the museum opens, TV or not), so
  // switching the TV on, or changing its stand, size or place, never compiles a shader
  function tvMats() {
    if (tvM) return tvM; const THREE = T(), M = o => new THREE.MeshStandardMaterial(o), U = { uCrt: { value: .6 }, uPow: { value: 0 }, uNoise: { value: 0 }, uTime: { value: 0 } };
    const crt = m => { m.customProgramCacheKey = () => 'bs-crt'; m.onBeforeCompile = sh => { Object.assign(sh.uniforms, U); sh.fragmentShader = 'uniform float uCrt, uPow, uNoise, uTime;\n' + sh.fragmentShader.replace('#include <map_fragment>', CRT_FRAG); }; return m; };
    const black = new THREE.DataTexture(new Uint8Array([0, 0, 0, 255]), 1, 1); black.colorSpace = THREE.SRGBColorSpace; black.needsUpdate = true;
    const vdummy = new THREE.VideoTexture(document.createElement('video')); vdummy.colorSpace = THREE.SRGBColorSpace; // a video's shader variant, ready before the first ad
    const radial = canvasTex(64, 64, (x, w, h) => { const g = x.createRadialGradient(w / 2, h / 2, 0, w / 2, h / 2, w / 2); g.addColorStop(0, 'rgba(255,255,255,1)'); g.addColorStop(.45, 'rgba(255,255,255,.5)'); g.addColorStop(1, 'rgba(255,255,255,0)'); x.fillStyle = g; x.fillRect(0, 0, w, h); });
    const cardC = document.createElement('canvas'); cardC.width = 512; cardC.height = 384; const cardT = new THREE.CanvasTexture(cardC); cardT.colorSpace = THREE.SRGBColorSpace;
    const clockT = canvasTex(256, 64, (c, w, h) => { c.fillStyle = '#05140a'; c.fillRect(0, 0, w, h); c.fillStyle = '#39ff7a'; c.shadowColor = '#39ff7a'; c.shadowBlur = 12; c.font = '700 44px monospace'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText('12:00', w / 2, h / 2 + 2); });
    tvM = { U, black, vdummy, radial, cardT, clockT, imgs: new Map(),
      plastic: M({ color: 0x2a2b2f, roughness: .55 }), dark: M({ color: 0x111215, roughness: .5 }), metal: M({ color: 0x8a8f99, metalness: .6, roughness: .35 }), pale: M({ color: 0xd9dade, roughness: .45 }),
      led: new THREE.MeshBasicMaterial({ vertexColors: true, toneMapped: false }), clock: new THREE.MeshBasicMaterial({ map: clockT, toneMapped: false, transparent: true }),
      screen: crt(new THREE.MeshBasicMaterial({ map: black, toneMapped: false })), screenV: crt(new THREE.MeshBasicMaterial({ map: vdummy, toneMapped: false })), // pictures; a playing video (its own variant)
      shade: new THREE.MeshBasicMaterial({ color: 0x000000, map: radial, transparent: true, opacity: .6, depthWrite: false }), // its contact shadow, drawn once (no shadow-casting light)
      glow: new THREE.MeshBasicMaterial({ color: 0x9fb8ff, map: radial, transparent: true, opacity: 0, depthWrite: false, blending: THREE.AdditiveBlending, toneMapped: false }) }; // the screen's light on the floor
    return tvM;
  }
  function disposeTvMats() { if (!tvM) return; const m = tvM; tvM = null; ['plastic', 'dark', 'metal', 'pale', 'led', 'clock', 'screen', 'screenV', 'shade', 'glow'].forEach(k => { try { m[k].dispose(); } catch {} }); ['black', 'vdummy', 'radial', 'cardT', 'clockT'].forEach(k => { try { m[k].dispose(); } catch {} }); try { m.cardT.image.width = m.cardT.image.height = 0; } catch {} m.imgs.clear(); }
  // one geometry from many small parts (position / normal / uv, and a flat colour each when asked): a whole set is a few draws
  function mergeParts(list, withColor) {
    const THREE = T(); let nv = 0, ni = 0; list.forEach(p => { const n = p.g.attributes.position.count; nv += n; ni += p.g.index ? p.g.index.count : n; });
    const pos = new Float32Array(nv * 3), nor = new Float32Array(nv * 3), uv = new Float32Array(nv * 2), col = withColor ? new Float32Array(nv * 3) : null, idx = new (nv > 65535 ? Uint32Array : Uint16Array)(ni), ranges = [];
    let v = 0, i = 0; list.forEach(p => { const g = p.g; g.applyMatrix4(p.m); const P = g.attributes.position, n = P.count; pos.set(P.array, v * 3); nor.set(g.attributes.normal.array, v * 3); if (g.attributes.uv) uv.set(g.attributes.uv.array, v * 2);
      if (col) { const c = p.c || { r: 1, g: 1, b: 1 }; for (let k = 0; k < n; k++) col.set([c.r, c.g, c.b], (v + k) * 3); }
      if (g.index) { const a = g.index.array; for (let k = 0; k < a.length; k++) idx[i++] = a[k] + v; } else for (let k = 0; k < n; k++) idx[i++] = v + k;
      ranges.push([v, n]); v += n; g.dispose(); });
    const out = new THREE.BufferGeometry(); out.setAttribute('position', new THREE.BufferAttribute(pos, 3)); out.setAttribute('normal', new THREE.BufferAttribute(nor, 3)); out.setAttribute('uv', new THREE.BufferAttribute(uv, 2)); if (col) out.setAttribute('color', new THREE.BufferAttribute(col, 3));
    out.setIndex(new THREE.BufferAttribute(idx, 1)); out.computeBoundingSphere(); out.userData.ranges = ranges; return out;
  }
  function buildTV() {
    // what's playing survives a rebuild (a new stand, size or place while an ad is on)
    const keep = tv ? { mode: tv.mode, video: tv.video, vtex: tv.vtex, meta: tv.meta, power: tv.power, pow: tv.pow, gain: tv.gain, gT: tv.gT, base: tv.base, card: tv.card } : null; if (tv) tv.video = tv.vtex = null;
    disposeTV(true); if (!tvOn || !roomObj || !wall) { if (keep) { tv = keep; tvStopVideo(); tv = null; } return; }
    const THREE = T(), M = tvMats(), d = tvDims(), { S, st, TW, TH, TD } = d, parts = {}, PI = Math.PI;
    const q = new THREE.Quaternion(), eu = new THREE.Euler(), v3 = new THREE.Vector3(), one = new THREE.Vector3(1, 1, 1);
    const add = (k, geo, x, y, z, rx = 0, ry = 0, rz = 0, c = null) => { (parts[k] = parts[k] || []).push({ g: geo, m: new THREE.Matrix4().compose(v3.set(x, y, z), q.setFromEuler(eu.set(rx, ry, rz)), one), c }); return parts[k].length - 1; };
    const box = (k, w, h, dd, x, y, z, rx, ry, rz) => add(k, new THREE.BoxGeometry(w, h, dd), x, y, z, rx, ry, rz);
    const cyl = (k, r1, r2, h, n, x, y, z, rx, ry, rz) => add(k, new THREE.CylinderGeometry(r1, r2, h, n), x, y, z, rx, ry, rz);
    const lit = (hex, w, h, x, y, z) => add('led', w ? new THREE.PlaneGeometry(w, h) : new THREE.CircleGeometry(h, 12), x, y, z, 0, 0, 0, new THREE.Color(hex));
    // the stand (y0: where the set's bottom sits; the group's origin is on the floor under the set's middle)
    let y0 = 0, wallZ = 0, clockAt = null;
    if (st === 'cart') { // a wooden TV cart on metal legs and casters, a VCR on its lower shelf
      const CW = d.W, CD = TD + .06; y0 = .68; box('wood', CW, .045, CD, 0, y0 - .0225, 0); box('wood', CW - .06, .035, CD - .06, 0, .2, 0);
      [-1, 1].forEach(a => [-1, 1].forEach(b => { const x = a * (CW / 2 - .045), z = b * (CD / 2 - .045), h = y0 - .045 - .055; box('metal', .032, h, .032, x, .055 + h / 2, z); cyl('dark', .03, .03, .028, 12, x, .03, z, 0, 0, PI / 2); }));
      box('dark', TW * .72, .1, TD * .62, 0, .2175 + .05, .03); box('metal', TW * .28, .012, .004, -TW * .13, .275, .03 + TD * .31 + .002); clockAt = [TW * .2, .262, .03 + TD * .31 + .003];
    } else if (st === 'cabinet') { // a low cabinet, open at the front: two consoles on the left, a few games and a slim console on the right
      const CW = d.W, CD = TD * 1.12, CH = .52; y0 = CH;
      box('wood', CW, .04, CD, 0, CH - .02, 0); box('wood', CW, .04, CD, 0, .08, 0); [-1, 1].forEach(a => box('wood', .04, CH - .14, CD, a * (CW / 2 - .02), .1 + (CH - .14) / 2, 0));
      box('wood', CW - .08, CH - .14, .02, 0, .1 + (CH - .14) / 2, -CD / 2 + .01); box('wood', .03, CH - .14, CD - .04, 0, .1 + (CH - .14) / 2, 0);
      [-1, 1].forEach(a => [-1, 1].forEach(b => cyl('dark', .028, .022, .06, 10, a * (CW / 2 - .08), .03, b * (CD / 2 - .08))));
      const lx = -CW / 4, fz = CD / 2 - .06; box('dark', CW * .32, .09, CD * .62, lx, .1 + .045, 0); lit(0x7a5cff, CW * .16, .007, lx, .12, CD * .31 + .001); lit(0x39ff7a, 0, .008, lx + CW * .12, .12, CD * .31 + .001);
      box('pale', CW * .2, .075, CD * .5, lx - CW * .02, .19 + .0375, -.02); lit(0x4aa8ff, 0, .007, lx + CW * .05, .23, CD * .23 + .001);
      const rx = CW / 4; for (let i = 0; i < 4; i++) box(i % 2 ? 'dark' : 'plastic', .022, .19, .135, rx - .16 + i * .026, .1 + .095, fz - .2);
      box('pale', .07, .27, CD * .55, rx + .1, .1 + .135, -.02); lit(0x4aa8ff, .004, .1, rx + .1, .25, CD * .255 + .001);
    } else if (st === 'floor') y0 = .14; // a console TV: the cabinet is the set itself, on short legs
    else { wallZ = -(TD / 2 + .17); y0 = 1.2; } // on a bracket on the wall
    // the set
    let sx, sy, sw, sh, fz = TD / 2, kx, powIdx;
    if (st === 'floor') {
      const FW = d.W, FH = TH * 1.62, FD = TD * 1.05; fz = FD / 2; box('wood', FW, FH, FD, 0, y0 + FH / 2, 0); box('dark', FW * .7, FH * .62, FD * .4, 0, y0 + FH * .6, -FD * .62);
      sw = TW * .8; sh = TH * .74; sx = -FW * .1; sy = y0 + FH - .09 * S - sh / 2; kx = FW * .37;
      const gy = y0 + FH * .21, gh = FH * .27, gw = FW * .78; box('dark', gw, gh, .02, 0, gy, fz + .004); for (let i = 0; i < 11; i++) box('wood', .022, gh, .026, -gw / 2 + .04 + i * (gw - .08) / 10, gy, fz + .013);
      [-1, 1].forEach(a => [-1, 1].forEach(b => cyl('wood', .035, .02, y0, 10, a * (FW / 2 - .1), y0 / 2, b * (FD / 2 - .1))));
      [.12, -.06].forEach((dy, k) => cyl('metal', k ? .04 * S : .06 * S, k ? .04 * S : .06 * S, .04, 20, kx, sy + dy * S, fz + .02, PI / 2)); powIdx = lit(0x331010, 0, .016, kx, sy - .2 * S, fz + .001);
    } else {
      const cy = y0 + TH / 2; box('plastic', TW, TH, TD * .52, 0, cy, TD * .24); box('plastic', TW * .76, TH * .8, TD * .5, 0, cy - TH * .04, -TD * .25); // the deep back of a tube set
      sw = TW * .74; sh = TH * .72; sx = -TW * .07; sy = cy + .02 * S; kx = TW * .38;
      [.18, -.02, -.2].forEach((dy, k) => cyl('metal', k ? .035 * S : .055 * S, k ? .035 * S : .055 * S, .04, 20, kx, cy + dy * S, fz + .02, PI / 2));
      for (let i = 0; i < 4; i++) box('dark', TW * .13, .008 * S, .006, kx, cy - TH * .3 + i * .024 * S, fz + .003); powIdx = lit(0x331010, 0, .014 * S, kx, cy - TH * .4, fz + .001);
      if (st === 'wall') { box('metal', .42 * S, .34 * S, .02, 0, cy, wallZ + .01); [-1, 1].forEach(a => box('metal', .04, .04, .18, a * .15 * S, cy, wallZ + .1)); }
    }
    box('dark', sw + .08 * S, sh + .08 * S, .03, sx, sy, fz - .008);
    const g = new THREE.Group(), body = new THREE.Group(), own = []; g.add(body); let ledG = null;
    Object.keys(parts).forEach(k => { const geo = mergeParts(parts[k], k === 'led'); own.push(geo); const m = new THREE.Mesh(geo, k === 'wood' ? roomObj.woodMat : M[k]); m.receiveShadow = k === 'wood'; body.add(m); if (k === 'led') ledG = geo; });
    const scrG = new THREE.PlaneGeometry(sw, sh, 18, 14), pos = scrG.attributes.position; for (let i = 0; i < pos.count; i++) { const x = pos.getX(i) / (sw / 2), y = pos.getY(i) / (sh / 2); pos.setZ(i, .035 * S * (1 - x * x) * (1 - y * y)); } scrG.computeVertexNormals(); own.push(scrG);
    const screen = new THREE.Mesh(scrG, M.screen); screen.position.set(sx, sy, fz + .004); body.add(screen);
    if (clockAt) { const cg = new THREE.PlaneGeometry(.2, .05); own.push(cg); const ck = new THREE.Mesh(cg, M.clock); ck.position.set(...clockAt); body.add(ck); }
    // baked contact shadow (on the floor, or on the wall behind a mounted set) and the screen's glow on the floor
    const decal = (w, h, m, x, y, z, rx) => { const pg = new THREE.PlaneGeometry(w, h); own.push(pg); const p = new THREE.Mesh(pg, m); p.position.set(x, y, z); p.rotation.x = rx; p.renderOrder = 1; g.add(p); return p; };
    if (st === 'wall') decal(TW + .55, TH + .5, M.shade, 0, y0 + TH / 2, wallZ + .004, 0); else decal(d.W + .5, (st === 'cabinet' ? TD * 1.12 : TD) + .45, M.shade, 0, .003, 0, -PI / 2);
    const glow = decal(TW * 2.4, 1.9, M.glow, sx, .005, fz + .95, -PI / 2); glow.visible = false;
    // where it stands: left, right, or in the gap the reading corner leaves in the middle
    const side = tvSide(), fy = roomObj.bottom - .4; let x, ry;
    if (side === 'left') { x = -.47 - .8 - d.W / 2; ry = st === 'wall' ? .06 : .2; }
    else if (side === 'centre' && rack) { x = (roomObj.maxW + .47 + rack.x0 - .3) / 2; ry = 0; }
    else { x = (roomObj.rackEnd || roomObj.maxW + .47) + .75 + d.W / 2; ry = st === 'wall' ? -.06 : -.2; }
    g.position.set(x, fy, st === 'wall' ? -DEPTH / 2 - .01 - wallZ : .62); g.rotation.y = ry; wall.add(g); g.updateMatrixWorld(true);
    const box3 = new THREE.Box3().setFromObject(body), focus = screen.getWorldPosition(new THREE.Vector3());
    tv = { g, body, screen, glow, own, ledG, ledR: ledG && powIdx != null ? ledG.userData.ranges[powIdx] : null, box: box3, focus, sh, ry, mode: 'off', video: null, vtex: null, meta: null, card: null, power: false, pow: 0, blip: 0, gain: room.tvNear ? 0 : 1, base: .7 };
    if (keep) { Object.assign(tv, keep); tvSet(tv.mode === 'video' && !tv.vtex ? 'static' : tv.mode); if (tv.card && tv.mode === 'card') tvDrawCard(); } else tvLed();
    shadowDirty = true;
  }
  function disposeTV(geomOnly) { if (!tv) { if (!geomOnly) disposeTvMats(); return; } if (!geomOnly) tvStopVideo(); try { if (tv.g.parent) tv.g.parent.remove(tv.g); } catch {} tv.own.forEach(g => g.dispose()); tv = null; if (!geomOnly) { tvView = false; tvReturn = null; disposeTvMats(); } }
  // the power light (red when on) and the set's frame for the overview's gold frame
  function tvLed() { if (!tv || !tv.ledR) return; const a = tv.ledG.attributes.color, [v0, n] = tv.ledR, on = tv.power; for (let k = 0; k < n; k++) a.setXYZ(v0 + k, on ? 1 : .2, on ? .19 : .06, on ? .19 : .06); a.needsUpdate = true; }
  function tvBox() { if (!tv) return null; const b = tv.box; return { x0: b.min.x - .12, x1: b.max.x + .12, y0: roomObj.bottom - .45, y1: b.max.y + .1, z: b.max.z + .18, label: 'Retro TV', sub: TV_CH[room.tvCh] || TV_CH.ads, tv: true }; } // the frame in front of the set (it stands out from the wall)
  // close up: straight in front of the screen, far enough back to see all of it
  // with the TV guide open the set slides into the space left of the guide (and steps back a little so it still fits)
  let guideView = false;
  const tvView0 = () => { const f = tv.focus; let d = Math.max(1.5, tv.sh * 3.3); if (!guideView) return { x: f.x, y: f.y - .08, z: f.z + d }; d *= 1.18; const side = Math.min(.46, (Math.min(500, innerWidth * .42) + 56) / innerWidth), visW = 2 * d * Math.tan(camera.fov * DEG / 2) * (innerWidth / innerHeight); return { x: f.x + visW * side / 2, y: f.y - .08, z: f.z + d }; };
  // a TV option changed in the Room menu: a short look at the whole set (framed in the space left of the menu), then back
  function tvPeekView() {
    const b = tv.box, fov = camera.fov * DEG, asp = innerWidth / innerHeight, side = Math.min(.45, (Math.min(420, innerWidth * .4) + 56) / innerWidth);
    const kz = 1.3 / tvDims().S, w = (b.max.x - b.min.x) * kz + 1.2, h = (b.max.y - (roomObj.bottom - .4)) * kz + .8, dist = /* framed as for the large set, so small and large look it */ Math.max(h / 2 / Math.tan(fov / 2), w / 2 / Math.tan(fov / 2) / (asp * (1 - side)));
    const visW = 2 * dist * Math.tan(fov / 2) * asp; return { x: (b.min.x + b.max.x) / 2 + visW * side / 2, y: (b.max.y + roomObj.bottom - .4) / 2 - .1, z: b.max.z + dist };
  }
  function tvStopVideo() { if (!tv) return; const v = tv.video; if (v) { try { v.pause(); v.removeAttribute('src'); v.load(); } catch {} } if (tv.vtex) { tv.vtex.dispose(); tv.vtex = null; } if (tvM) tvM.screenV.map = tvM.vdummy; tv.video = null; }
  function tvSet(mode) {
    if (!tv) return; const M = tvMats(); tv.mode = mode;
    if (mode === 'video' && tv.vtex) { M.screenV.map = tv.vtex; tv.screen.material = M.screenV; } else { tv.screen.material = M.screen; M.screen.map = mode === 'card' ? M.cardT : M.black; } // (both shaders already built: a swap, never a compile)
    tvLed();
  }
  // switch on / off: the picture opens out of a bright line (or folds away to a dot); the sound and the floor glow follow
  function tvPower(on) { if (!tv) return; if (on) { if (!tv.power) { tv.power = true; snd('lidClick'); } if (tv.mode === 'off') tvSet('static'); } else if (tv.power) { tv.power = false; tv.meta = null; tv.card = null; snd('seat'); } tvLed(); }
  function tvOff() { if (!tv) return; tvStopVideo(); tv.power = false; tv.pow = 0; tv.meta = null; tv.card = null; tvSet('off'); }
  function tvStatic() { if (!tv) return; tvStopVideo(); tvSet('static'); snd('tick'); }
  // play an ad (a local file Boxshelf fetched): static until it can play, then the picture; the app picks the next one when it ends
  function tvShow(url, meta, vol) {
    if (!tv || !tv.power) return false; tvStopVideo(); tvSet('static'); tv.meta = meta || null; tv.card = null; if (vol != null) tv.base = Math.max(0, Math.min(1, vol)); const THREE = T(), v = document.createElement('video'); tv.video = v;
    v.src = url; v.playsInline = true; v.preload = 'auto'; v.crossOrigin = 'anonymous'; if (window.BS_SINK && typeof v.setSinkId === 'function') v.setSinkId(window.BS_SINK).catch(() => {}); /* Settings → Sound → Output device (gone: the default) */ v.volume = tv.base * tv.gain; v.muted = !(tv.base > 0);
    v.addEventListener('canplay', () => { if (tv && tv.video === v && tv.power) { if (!tv.vtex) { tv.vtex = new THREE.VideoTexture(v); tv.vtex.colorSpace = THREE.SRGBColorSpace; } tvSet('video'); v.play().catch(() => {}); } }, { once: true });
    v.addEventListener('ended', () => { if (tv && tv.video === v) hooks.onTvEnded && hooks.onTvEnded(); });
    v.addEventListener('error', () => { if (tv && tv.video === v) hooks.onTvEnded && hooks.onTvEnded(true); });
    hooks.onSelect && tvView && hooks.onSelect(current()); return true;
  }
  // a still on the screen (the trailers and recently-played channels): the game's cover and a title card, drawn on one canvas
  function tvCard(card, meta) { if (!tv || !tv.power) return false; tvStopVideo(); tv.card = card || null; tv.meta = meta || null; tv.blip = .28; tvDrawCard(); tvSet('card'); hooks.onSelect && tvView && hooks.onSelect(current()); return true; }
  function tvDrawCard() {
    const M = tvMats(), c = tv && tv.card; if (!c) return; const cv = M.cardT.image, x = cv.getContext('2d'), W = cv.width, H0 = cv.height, trailer = c.kind === 'trailer';
    let img = null; if (c.img) { const e = M.imgs.get(c.img); if (e) img = e.ok ? e.img : null; else { if (M.imgs.size >= 40) { const k0 = M.imgs.keys().next().value, e0 = M.imgs.get(k0); M.imgs.delete(k0); try { e0.img.onload = e0.img.onerror = null; e0.img.src = ''; } catch {} } /* the oldest cover makes way (it used to stop loading covers after 40 for the rest of the visit) */ const im = new Image(), ent = { img: im, ok: false }; M.imgs.set(c.img, ent); im.decoding = 'async'; im.onload = () => { ent.ok = true; if (tv && tv.card && tv.card.img === c.img) { tvDrawCard(); } }; im.onerror = () => {}; im.src = c.img; } }
    const bg = x.createLinearGradient(0, 0, 0, H0); bg.addColorStop(0, trailer ? '#3a0d12' : '#0d1f3a'); bg.addColorStop(1, '#050507'); x.fillStyle = bg; x.fillRect(0, 0, W, H0);
    x.fillStyle = trailer ? '#ffcf4a' : '#7fd1ff'; x.font = '700 22px Lexend, sans-serif'; x.textBaseline = 'middle'; x.textAlign = 'left'; x.fillText(trailer ? 'NOW SHOWING' : 'RECENTLY PLAYED', 30, 36);
    if (c.n) { x.textAlign = 'right'; x.fillStyle = 'rgba(255,255,255,.55)'; x.font = '600 18px Lexend, sans-serif'; x.fillText(c.n, W - 30, 36); x.textAlign = 'left'; }
    const bx = 30, by = 66, bw = 186, bh = 262; x.fillStyle = 'rgba(255,255,255,.06)'; x.fillRect(bx, by, bw, bh);
    if (img && img.naturalWidth) { const k = Math.min(bw / img.naturalWidth, bh / img.naturalHeight), iw = img.naturalWidth * k, ih = img.naturalHeight * k; try { x.drawImage(img, bx + (bw - iw) / 2, by + (bh - ih) / 2, iw, ih); } catch {} }
    else { x.fillStyle = 'rgba(255,255,255,.35)'; x.font = '600 18px Lexend, sans-serif'; x.textAlign = 'center'; x.fillText(trailer ? '▶' : '★', bx + bw / 2, by + bh / 2); x.textAlign = 'left'; }
    const tx = 240, tw = W - tx - 26, fit = (t, f) => { x.font = f; if (x.measureText(t).width <= tw) return t; while (t.length > 3 && x.measureText(t + '…').width > tw) t = t.slice(0, -1); return t + '…'; };
    // the title over up to three lines, then the console and one line of history
    x.fillStyle = '#fff'; const words = String(c.title || '').split(/\s+/); let line = '', ly = 98; const lines = []; x.font = '700 34px Lexend, sans-serif';
    words.forEach(wd => { const t = line ? line + ' ' + wd : wd; if (x.measureText(t).width > tw && line) { lines.push(line); line = wd; } else line = t; }); if (line) lines.push(line);
    lines.slice(0, 3).forEach((l, i) => { x.fillText(fit(i === 2 && lines.length > 3 ? l + '…' : l, '700 34px Lexend, sans-serif'), tx, ly); ly += 40; });
    x.fillStyle = 'rgba(255,255,255,.75)'; x.fillText(fit(c.sub || '', '500 22px Lexend, sans-serif'), tx, ly + 10); x.fillStyle = trailer ? '#ffcf4a' : '#9fe0ff'; x.fillText(fit(c.line || '', '600 20px Lexend, sans-serif'), tx, ly + 46);
    M.cardT.needsUpdate = true;
  }
  function tvVolume(vol) { if (!tv) return; tv.base = Math.max(0, Math.min(1, +vol || 0)); if (tv.video) { tv.video.volume = tv.base * tv.gain; tv.video.muted = !(tv.base > 0); } }
  // from a game in your hands: over to the TV for its ad, B brings you back to the shelf where it was
  function watchTv(keep) { if (!tv) return false; const was = tvView; if (!was) tvReturn = { rowI, colI }; endPeek(); heldCase = null; libHeld = null; wide = false; tvView = true; const on = tv.power && tv.meta; tvPower(true); if (!keep || !on) tvSet('static'); /* keep: the guide walks you over without cutting off what's on */ lastInput = lastNav = woke(); snd('lift'); return true; }
  function stepTV(dt, now) {
    if (!tv) return; const M = tvM, U = M.U, want = tv.power ? 1 : 0;
    if (tv.pow !== want) { tv.pow = room.tvAnim === false ? want : Math.max(0, Math.min(1, tv.pow + (want ? dt / .55 : -dt / .4))); if (!tv.pow && !tv.power) { tvStopVideo(); tvSet('off'); } }
    U.uPow.value = tv.pow < 1 && want ? ss(tv.pow) : tv.pow; if (!tv.pow) { tv.glow.material.opacity = 0; tv.glow.visible = false; return; } // off: nothing else to do (and the glow isn't drawn)
    tv.blip = Math.max(0, tv.blip - dt); U.uNoise.value = tv.mode === 'static' ? 1 : Math.min(1, tv.blip / .12); U.uTime.value = now / 1000; U.uCrt.value = Math.max(0, Math.min(1, room.tvCrt == null ? .6 : +room.tvCrt));
    // the sound: as loud as you set it; with "Only when I'm looking at it", it fades in as you face it and come close
    let g = 1; if (room.tvNear && !tvView) { const to = sv('tvTo').copy(tv.focus).sub(camera.position), dist = to.length(), dir = camera.getWorldDirection(sv('tvDir')), face = to.dot(dir) / Math.max(dist, .001); g = ss((9.5 - dist) / 5) * ss((face - .6) / .32); }
    tv.gT = g; tv.gain = sm(tv.gain, g, 1 - Math.exp(-dt * 2.5)); if (Math.abs(tv.gain - g) < .002) tv.gain = g; if (tv.video) { const vv = Math.max(0, Math.min(1, tv.base * tv.gain)); if (Math.abs(tv.video.volume - vv) > .003) tv.video.volume = vv; }
    // its light on the floor, a little livelier while a picture moves
    const fl = tv.mode === 'video' ? .82 + .18 * Math.sin(now / 90) * Math.sin(now / 37) : tv.mode === 'static' ? .7 + .3 * Math.random() : 1;
    tv.glow.material.opacity = Math.min(1, .3 * tv.pow * fl * light.tvGlow); tv.glow.visible = tv.glow.material.opacity > .003; tv.glow.material.color.setHex(tv.mode === 'card' ? (tv.card && tv.card.kind === 'trailer' ? 0xffb080 : 0x90c8ff) : 0x9fb8ff);
  }
  function buildRack(list) {
    const THREE = T(); disposeRack(); if (!wall || !rows.length || !roomObj) return;
    const all0 = list || [], items = all0.filter(d => d.kind !== 'movie').slice(0, 600), mags = items.filter(d => d.kind !== 'guide'), guides = items.filter(d => d.kind === 'guide'), movies = all0.filter(d => d.kind === 'movie');
    const printRows = movies.length ? Math.max(1, Math.ceil(rows.length / 2)) : rows.length; // with movies, magazines and guides keep to the top half
    const g = new THREE.Group(); wall.add(g);
    const x0 = roomObj.maxW + .4 + .07 + .35 + .07 + tvGap(); /* a hand's width from the bookcase (more with the TV in between) */
    // two racks side by side, Magazines then Strategy Guides; each grouped by series (in issue order), with a brass tab
    // where a series starts. One ledge level with each shelf; a rack is as wide as it takes to fit on the shelves you have.
    const nat = (a, b) => String(a).localeCompare(String(b), undefined, { numeric: true, sensitivity: 'base' });
    const sortS = l => l.map(d => [d, groupOf(d), yearOf(d)]).sort((a, b) => nat(a[1] || '￿', b[1] || '￿') || (a[2] || 0) - (b[2] || 0) || nat(a[0].title, b[0].title)).map(x => x[0]); // by brand, then year, then title
    const woodMat = roomObj.woodMat, plankT = .055, lipM = new THREE.MeshStandardMaterial({ color: 0xc9a86a, metalness: .75, roughness: .32 });
    const byRow = rows.map(() => []), all = [], rack_beams = [], units = []; let x = x0, lastX1 = x0;
    const bagM = new THREE.BoxGeometry(RACK_W * 1.035, RACK_H * 1.05, .012 + .008), bagG = new THREE.BoxGeometry(RACK_W * 1.035, RACK_H * 1.05, .035 + .008);
    // a series of two or more issues goes into one file box (newest cover at the front); one-offs stand on their own
    const slotsOf = l => { const out = []; for (let i = 0; i < l.length;) { const d = l[i], g = groupOf(d); let j = i + 1; if (g) while (j < l.length && groupOf(l[j]) === g) j++;
      if (j - i >= 2) { const issues = l.slice(i, j); out.push({ d: issues[issues.length - 1], box: { series: g, issues, years: issues.map(yearOf) } }); } else out.push({ d }); i = j; } return out; };
    [['MAGAZINES', slotsOf(sortS(mags)), false], ['STRATEGY GUIDES', slotsOf(sortS(guides)), true]].forEach(([label, list, guide]) => {
      let per = 3; while (Math.ceil(list.length / per) > printRows && per < 300) per++;
      if (Math.ceil(list.length / per) > printRows) list = list.slice(0, per * printRows); // more than fits: the rest wait (it used to crash building the corner)
      const empty = !list.length;
      const used = Math.ceil(list.length / per), width = per * (RACK_W + RACK_GAP) + RACK_GAP, rx0 = x, rx1 = x + width, cx = (rx0 + rx1) / 2;
      // built like the bookcase beside it: full-height side panels in the same wood, a top board with an engraved brass
      // name plate, a darker back panel, a slanted display ledge level with every shelf, and a picture lamp over the top
      const midY = -(rows.length - 1) * ROW / 2, upH = wallH + .4, RD = DEPTH * .62, rz = -DEPTH / 2 + RD / 2;
      [rx0 - .07, rx1 + .07].forEach(px => { const p = new THREE.Mesh(new THREE.BoxGeometry(.14, upH, RD + .04), woodMat); p.position.set(px, midY - .1, rz); p.castShadow = true; p.receiveShadow = true; g.add(p); });
      const topY = ROW - H / 2 + plankT / 2 - .001;
      const top = new THREE.Mesh(new THREE.BoxGeometry(width + .28 + .06, plankT * 1.6, RD + .1), woodMat); top.position.set(cx, topY + .02, rz + .03); top.castShadow = true; g.add(top);
      const crown = new THREE.Mesh(new THREE.BoxGeometry(width + .28, .16, .05), woodMat); crown.position.set(cx, topY - .1, rz + RD / 2 + .03); g.add(crown);
      const backM = new THREE.MeshStandardMaterial({ color: 0x4a3626, roughness: .8 });
      const back = new THREE.Mesh(new THREE.PlaneGeometry(width, upH - .1), backM); back.position.set(cx, midY - .1, -DEPTH / 2 + .008); back.receiveShadow = true; g.add(back);
      // the name plate: brass, engraved, on the crown
      const plW = Math.min(width * .7, 1.7), plH = plW / 6.5;
      const plT = canvasTex(1040, 160, (c, w, h) => { const gr = c.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, '#e2c486'); gr.addColorStop(.5, '#b8924f'); gr.addColorStop(1, '#8a6a34'); c.fillStyle = gr; c.fillRect(0, 0, w, h); c.strokeStyle = 'rgba(60,40,10,.6)'; c.lineWidth = 6; c.strokeRect(10, 10, w - 20, h - 20); c.fillStyle = '#2a1c08'; c.font = '700 78px Lexend, sans-serif'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText(label, w / 2, h / 2 + 4); });
      const plate = new THREE.Mesh(new THREE.PlaneGeometry(plW, plH), new THREE.MeshStandardMaterial({ map: plT, metalness: .6, roughness: .32, emissive: 0xffffff, emissiveMap: plT, emissiveIntensity: .18 })); plate.position.set(cx, topY - .1, rz + RD / 2 + .057); g.add(plate);
      // picture lamp: a brass bar on two arms, and a soft fall of light down the front
      const brass = new THREE.MeshStandardMaterial({ color: 0x8a6a34, metalness: .85, roughness: .3 });
      const bar = new THREE.Mesh(new THREE.CylinderGeometry(.028, .028, Math.min(width * .8, 2.4), 14), brass); bar.rotation.z = Math.PI / 2; bar.position.set(cx, topY + .1, rz + RD / 2 + .2); g.add(bar);
      [-1, 1].forEach(sgn => { const arm = new THREE.Mesh(new THREE.BoxGeometry(.02, .02, .22), brass); arm.position.set(cx + sgn * Math.min(width * .3, .9), topY + .1, rz + RD / 2 + .09); g.add(arm); });
      const bulb = new THREE.Mesh(new THREE.CylinderGeometry(.012, .012, Math.min(width * .75, 2.2), 10), new THREE.MeshBasicMaterial({ color: 0xffe6b8 })); bulb.rotation.z = Math.PI / 2; bulb.position.set(cx, topY + .08, rz + RD / 2 + .21); g.add(bulb);
      const beam = new THREE.Mesh(new THREE.PlaneGeometry(width * .95, Math.min(upH * .55, 3.2)), new THREE.MeshBasicMaterial({ map: beamTex(), transparent: true, opacity: .16, depthWrite: false, blending: THREE.AdditiveBlending, toneMapped: false, color: 0xffe2b8 })); beam.position.set(cx, topY - Math.min(upH * .55, 3.2) / 2, rz + RD / 2 + .12); g.add(beam); rack_beams.push(beam);
      if (empty) { // nothing on it yet: a little card that says how to fill it
        const ct = canvasTex(640, 820, (c, w, h) => { c.fillStyle = '#efe6d2'; c.fillRect(0, 0, w, h); c.strokeStyle = '#b8924f'; c.lineWidth = 10; c.strokeRect(18, 18, w - 36, h - 36); c.fillStyle = '#2a1c08'; c.textAlign = 'center';
          c.font = '700 54px Lexend, sans-serif'; c.fillText(guide ? 'No guides' : 'No magazines', w / 2, 200); c.fillText('yet', w / 2, 266);
          c.font = '500 38px Lexend, sans-serif'; c.fillStyle = '#4a3a20'; ['Select this rack to', 'find some on the', 'Internet Archive'] /* no button name: drawn once, and the button differs by pad (the hint bar shows the right one) */.forEach((t, q) => c.fillText(t, w / 2, 420 + q * 58)); });
        const card = new THREE.Mesh(new THREE.PlaneGeometry(RACK_W, RACK_W * 820 / 640), new THREE.MeshStandardMaterial({ map: ct, roughness: .8, emissive: 0xffffff, emissiveMap: ct, emissiveIntensity: .25 }));
        card.position.set(cx, rows[0].y - H / 2 + RACK_W * 820 / 640 / 2 + .02, -DEPTH / 2 + .16); card.rotation.x = -12 * DEG; g.add(card);
      }
      for (let ri = used; ri < rows.length; ri++) { // empty ledges still there, so the unit reads as furniture
        const row = rows[ri]; const ledge = new THREE.Mesh(new THREE.BoxGeometry(width, plankT, .34), woodMat); ledge.position.set(cx, row.y - H / 2 - plankT / 2, -DEPTH / 2 + .17); ledge.receiveShadow = true; g.add(ledge);
        const lip = new THREE.Mesh(new THREE.BoxGeometry(width, .03, .015), lipM); lip.position.set(cx, row.y - H / 2 + .015, -DEPTH / 2 + .33); g.add(lip); }
      for (let ri = 0; ri < used; ri++) {
        const row = rows[ri], chunk = list.slice(ri * per, ri * per + per);
        const ledge = new THREE.Mesh(new THREE.BoxGeometry(width, plankT, .34), woodMat); ledge.position.set((rx0 + rx1) / 2, row.y - H / 2 - plankT / 2, -DEPTH / 2 + .17); ledge.receiveShadow = ledge.castShadow = true; g.add(ledge);
        const lip = new THREE.Mesh(new THREE.BoxGeometry(width, .03, .015), lipM); lip.position.set((rx0 + rx1) / 2, row.y - H / 2 + .015, -DEPTH / 2 + .33); g.add(lip);
        chunk.forEach((slot, c) => {
          const d = slot.d, th = guide ? .035 : .012, ph = placeholderTex(d);
          const edge = new THREE.MeshStandardMaterial({ color: 0xece6d8, roughness: .9 }), backM = new THREE.MeshStandardMaterial({ color: 0xd9d2c4, roughness: .7 });
          const front = new THREE.MeshStandardMaterial({ map: ph, roughness: .45, emissive: 0xffffff, emissiveMap: ph, emissiveIntensity: .3 });
          const m = new THREE.Mesh(new THREE.BoxGeometry(RACK_W, RACK_H, th), [edge, edge, edge, edge, front, backM]); m.castShadow = true;
          const ix = rx0 + RACK_GAP + RACK_W / 2 + c * (RACK_W + RACK_GAP);
          m.position.set(ix, -H / 2 + RACK_H / 2 + .01, -DEPTH / 2 + .2); m.rotation.x = -12 * DEG;
          m.userData = { lib: true, home: m.position.clone(), p: 0, lift: 0 };
          // a clear collector's bag (the same film as a sealed game), a little taller than the issue with its flap at the top
          let bag = null; if (libSealed(d)) { bag = new THREE.Mesh(guide ? bagG : bagM, bagMat()); bag.position.y = RACK_H * .025; bag.renderOrder = 3; bag.userData.keepMat = true; m.add(bag); }
          row.group.add(m); const it = { d, mesh: m, ri, k: byRow[ri].length, front, backM, ph, coverOn: false, box: slot.box || null, bag }; all.push(it); byRow[ri].push(it);
          if (slot.box) { buildBox(it, th, guide); return; } // a file box carries its own label
          // a brass tab on the lip where a series starts
          const prev = list[ri * per + c - 1] && list[ri * per + c - 1].d, grp = groupOf(d);
          if (grp && (!prev || groupOf(prev) !== grp)) {
            const tt = canvasTex(256, 48, (q, w, h) => { q.fillStyle = '#c9a86a'; q.fillRect(0, 0, w, h); q.fillStyle = '#2a1d0c'; q.font = '700 26px Lexend, sans-serif'; q.textBaseline = 'middle'; let t = grp; while (q.measureText(t).width > w - 16 && t.length > 3) t = t.slice(0, -2); q.fillText(t === grp ? t : t + '…', 8, h / 2 + 1); });
            const tab = new THREE.Mesh(new THREE.PlaneGeometry(RACK_W * .9, RACK_W * .9 * 48 / 256), new THREE.MeshStandardMaterial({ map: tt, metalness: .5, roughness: .35, emissive: 0xffffff, emissiveMap: tt, emissiveIntensity: .15 }));
            tab.position.set(ix - RACK_W * .05, row.y - H / 2 - .055 - RACK_W * .9 * 48 / 256 / 2 + .012, -DEPTH / 2 + .345); g.add(tab); /* hangs from the ledge's front edge, under the covers */
          }
        });
      }
      units.push({ label: guide ? 'Strategy guides' : 'Magazines', guide, x0: rx0, x1: rx1, n: list.length, width, cx, rz, RD });
      lastX1 = rx1; x = rx1 + .14 + .35;
    });
    if (movies.length && rows.length > printRows) { try { buildMovies(movies, g, units, byRow, all, printRows, plankT, lipM); } catch (e) { console.warn('movies', e); } }
    rack = { group: g, items: all, byRow, x0, x1: lastX1, beams: rack_beams, units, bagM, bagG }; roomObj.rackEnd = lastX1 + .1; wallW = Math.max(roomObj.maxW, lastX1); buildExtras(); shadowDirty = true;
  }
  // covers: every magazine and guide shows its own cover from the moment you walk in, like the cases (nearest first,
  // through the same paced loader: small copies, one picture handed to the graphics card at a time). Only a very big
  // reading corner keeps to the ledges near you. A cover is page 1, made once by the shelf.
  function rackCovers() {
    if (!rack || !rack.items.length || rack.coverRow === rowI) return; rack.coverRow = rowI; const gen = wallGen; // only when the row changes (it ran every frame)
    const all = rack.items.length <= 150;
    rack.items.slice().sort((a, b) => Math.abs(a.ri - rowI) - Math.abs(b.ri - rowI)).forEach(it => {
      const near = all || Math.abs(it.ri - rowI) <= 1;
      if (near && !it.coverOn) { it.coverOn = true; const p = hooks.libCover ? hooks.libCover(it.d) : null;
        Promise.resolve(p).then(url => { if (!url || gen !== wallGen || !rack || !rack.items.includes(it) || !it.coverOn || it.tex) return; const t = loadTex(url, 'front'); it.tex = t;
          whenLoaded(t, () => { if (it.tex !== t || !it.coverOn) return; it.front.map = it.front.emissiveMap = t; it.front.needsUpdate = true; }, () => {}); }, () => {}); }
      else if (!all && !near && Math.abs(it.ri - rowI) > 2 && it.coverOn) { it.coverOn = false; if (it.tex) { if (it.front.map === it.tex) { it.front.map = it.front.emissiveMap = it.ph; it.front.needsUpdate = true; } freeMap(it.tex); it.tex = null; } }
    });
  }
  // A on a magazine: it comes off the rack into your hands (the room dims); the stick turns it to see the back cover
  function liftLib() {
    const it = rackItem(); if (!it || intro.on || wide || holding || heldCase) return false;
    libHeld = it; { const u = it.mesh.userData, ry = u.hry || 0; hold = { rx: u.hrx || 0, ry, trx: -4, try: Math.round(ry / 360) * 360 }; } handZoomT = 1; snd('lift');
    if (!it.backTex && hooks.libBack) { const gen = wallGen; Promise.resolve(hooks.libBack(it.d)).then(url => { if (!url || gen !== wallGen || libHeld !== it || it.backTex) return; const t = loadTex(url, 'back'); it.backTex = t; whenLoaded(t, () => { if (it.backTex !== t) return; it.backM.map = t; it.backM.color.set(0xffffff); it.backM.needsUpdate = true; }, () => {}); }, () => {}); }
    return true;
  }
  // (beta.11) Y in the fan: the issue you're on comes into your hands like a magazine standing on its own (turn it to see
  // its back, A reads it, B puts it back and the fan returns). It's a stand-in made like the rack's magazines (its own
  // geometry and materials; the fan slot's cover texture shared, with a reference of its own let go in dropTemp), kept in
  // rack.items only while held. L1 / R1 and A act on it, not on the fan behind it
  function fanLift() {
    if (!fan || fan.closing || libPull > 0 || holdA || libHeld || !rack) return false;
    const d = fan.it.box.issues[fan.i], sl = fan.slots.get(fan.i); if (!d) return false;
    // the fan's cover is shared only once its picture is there (an empty texture draws black): until then the stand-in shows
    // its own placeholder and takes the cover as it.tex when it has loaded
    const THREE = T(), guide = d.kind === 'guide', th = guide ? .035 : .012, st = sl && sl.tex ? sl.tex : null, ready = !!(st && st.userData.state === 'ok'), ph = ready ? st : placeholderTex(d);
    if (st && st.userData.url) st.userData.refs = (st.userData.refs || 0) + 1; // (its own hold on the fan's cover: freed in dropTemp, so the fan freeing it can't blank this one)
    const edge = new THREE.MeshStandardMaterial({ color: 0xece6d8, roughness: .9 }), backM = new THREE.MeshStandardMaterial({ color: 0xd9d2c4, roughness: .7 });
    const front = new THREE.MeshStandardMaterial({ map: ph, roughness: .45, emissive: 0xffffff, emissiveMap: ph, emissiveIntensity: .38 });
    const m = new THREE.Mesh(new THREE.BoxGeometry(RACK_W, RACK_H, th), [edge, edge, edge, edge, front, backM]); m.castShadow = true;
    const h0 = fan.it.mesh.userData.home; m.position.set(h0.x, h0.y, h0.z); m.userData = { home: { x: h0.x, y: h0.y, z: h0.z }, lift: 0, p: 0 };
    rows[fan.it.ri].group.add(m);
    // still sealed: it comes up in its bag, and holding A unwraps it in your hands (unwrapLib), as on the rack
    let bag = null; if (libSealed(d)) { bag = new THREE.Mesh(guide ? rack.bagG : rack.bagM, bagMat()); bag.position.y = RACK_H * .025; bag.renderOrder = 3; bag.userData.keepMat = true; m.add(bag); }
    const it = { d, mesh: m, ri: fan.it.ri, k: -1, front, backM, ph, tex: st && !ready && st.userData.url ? st : null, coverOn: true, temp: true, ownTex: !ready, bag, fanI: fan.i };
    if (it.tex) { const t = it.tex; whenLoaded(t, () => { if (it.tex !== t || !rack || !rack.items.includes(it)) return; front.map = front.emissiveMap = t; front.needsUpdate = true; }, () => {}); }
    rack.items.push(it); fan.group.visible = false;
    libHeld = it; hold = { rx: 0, ry: 0, trx: -4, try: 0 }; handZoomT = 1; snd('lift');
    if (!sl || !sl.tex) { if (hooks.libCover) { const f = fan; Promise.resolve(hooks.libCover(d)).then(url => { if (!url || libHeld !== it || fan !== f) return; const t = loadTex(url, 'front'); it.tex = t; whenLoaded(t, () => { if (it.tex !== t) return; front.map = front.emissiveMap = t; front.needsUpdate = true; }, () => {}); }, () => {}); } }
    if (hooks.libBack) { const gen = wallGen; Promise.resolve(hooks.libBack(d)).then(url => { if (!url || gen !== wallGen || libHeld !== it || it.backTex) return; const t = loadTex(url, 'back'); it.backTex = t; whenLoaded(t, () => { if (it.backTex !== t) return; backM.map = t; backM.color.set(0xffffff); backM.needsUpdate = true; }, () => {}); }, () => {}); }
    hooks.onSelect && hooks.onSelect(current());
    return true;
  }
  // the stand-in once it's out of your hands (put back, the TV, a rebuild): gone, and the fan shows again
  function dropTemp(it) {
    if (it.bag) { if (libPullBag === it.bag) { libPullBag = null; libPull = 0; } if (it.bag.material !== bagM0) { try { it.bag.material.dispose(); } catch {} } }
    // unwrapped in your hands: its copy in the fan loses its bag too
    if (fan && !libSealed(it.d)) { const sl = fan.slots.get(it.fanI); if (sl && sl.bag) { if (libPullBag === sl.bag) { libPullBag = null; libPull = 0; } sl.mesh.remove(sl.bag); sl.bag = null; } }
    for (let i = unwraps.length - 1; i >= 0; i--) if (unwraps[i].b === it.mesh) { try { unwraps[i].sl.material.dispose(); } catch {} unwraps.splice(i, 1); }
    if (shim && shim.box === it.mesh) endShim(); if (sparks && sparks.parent === it.mesh) { try { it.mesh.remove(sparks); sparks.geometry.dispose(); } catch {} sparks = null; }
    try { if (it.mesh.parent) it.mesh.parent.remove(it.mesh); it.mesh.geometry.dispose(); [it.front, it.backM].forEach(mt => { try { mt.dispose(); } catch {} }); it.mesh.material[0].dispose(); } catch {}
    if (it.tex) freeMap(it.tex); if (it.backTex) freeMap(it.backTex); if (it.ownTex && it.ph) { try { it.ph.dispose(); } catch {} } else if (it.ph && it.ph.userData.url) freeMap(it.ph); // (the fan's shared cover: our hold on it let go)
    if (rack) { const k = rack.items.indexOf(it); if (k >= 0) rack.items.splice(k, 1); }
    if (fan) fan.group.visible = true;
  }
  // A on a file box: the issues come up out of it and fan out in front of you, the one you're on facing you, its
  // neighbours turned away to each side (only the ones near it are made, so a box of 300 is as quick as a box of 3).
  // Left / right flips through, L1 / R1 jumps a year (or 10 when the titles carry no year), A reads it, B puts them back.
  let fan = null;
  function openFan(it) {
    const THREE = T(), n = it.box.issues.length;
    fan = { it, n, i: n - 1, cur: n - 1, k: 0, closing: false, slots: new Map(), geo: new THREE.PlaneGeometry(RACK_W, RACK_H), bagGeo: new THREE.PlaneGeometry(RACK_W * 1.035, RACK_H * 1.05), gen: wallGen, group: new THREE.Group() };
    rows[it.ri].group.add(fan.group); snd('lift'); handZoomT = 1; libHeld = null;
  }
  function fanSlot(idx) {
    let sl = fan.slots.get(idx); if (sl) return sl;
    const THREE = T(), d = fan.it.box.issues[idx], ph = placeholderTex(d);
    const mat = new THREE.MeshStandardMaterial({ map: ph, roughness: .45, emissive: 0xffffff, emissiveMap: ph, emissiveIntensity: .34 });
    const mesh = new THREE.Mesh(fan.geo, mat); mesh.castShadow = false; fan.group.add(mesh);
    let bag = null; if (libSealed(d)) { bag = new THREE.Mesh(fan.bagGeo, bagMat()); bag.position.set(0, RACK_H * .025, .004); bag.renderOrder = 3; mesh.add(bag); } // still in its bag
    sl = { mesh, mat, ph, tex: null, bag, d }; fan.slots.set(idx, sl);
    if (hooks.libCover) { const f = fan; Promise.resolve(hooks.libCover(d)).then(url => { if (!url || fan !== f || f.slots.get(idx) !== sl || sl.tex) return; const t = loadTex(url, 'front'); sl.tex = t;
      whenLoaded(t, () => { if (sl.tex !== t || fan !== f) return; mat.map = mat.emissiveMap = t; mat.needsUpdate = true; }, () => {}); }, () => {}); }
    return sl;
  }
  function dropFanSlot(idx) { const sl = fan.slots.get(idx); if (!sl) return; if (sl.bag && libPullBag === sl.bag) { libPullBag = null; libPull = 0; } for (let i = unwraps.length - 1; i >= 0; i--) if (unwraps[i].b === sl.mesh) { try { unwraps[i].sl.material.dispose(); } catch {} unwraps.splice(i, 1); } if (shim && shim.box === sl.mesh) endShim(); if (sparks && sparks.parent === sl.mesh) { try { sl.mesh.remove(sparks); sparks.geometry.dispose(); } catch {} sparks = null; } fan.group.remove(sl.mesh); sl.mat.dispose(); freeMap(sl.ph); if (sl.tex) freeMap(sl.tex); fan.slots.delete(idx); }
  function closeFan(now) { if (!fan) return; if (!now) { fan.closing = true; snd('slideBack'); return; } try { [...fan.slots.keys()].forEach(dropFanSlot); if (fan.group.parent) fan.group.parent.remove(fan.group); fan.geo.dispose(); fan.bagGeo.dispose(); } catch {} fan = null; }
  let holdA = false; // A is being held (unwrapping): the fan doesn't move under your thumb
  function fanStep(d) { if (!fan || fan.closing || libPull > 0 || holdA || (libHeld && libHeld.temp)) return; /* (beta.11: not while an issue from it is in your hands) */ const ni = Math.max(0, Math.min(fan.n - 1, fan.i + d)); if (ni === fan.i) { snd('toast'); return; } fan.i = ni; snd('page'); hooks.onSelect && hooks.onSelect(current()); }
  function fanJump(dir) { if (!fan || fan.closing || libPull > 0 || holdA || (libHeld && libHeld.temp)) return; const ys = fan.it.box.years, y0 = ys[fan.i]; let ni = fan.i;
    if (y0 && ys.some(Boolean)) { if (dir > 0) { while (ni < fan.n - 1 && ys[ni] <= y0) ni++; } else if (ni > 0 && ys[ni - 1] === y0) { while (ni > 0 && ys[ni - 1] === y0) ni--; } else { while (ni > 0 && (ys[ni] >= y0 || !ys[ni])) ni--; const y1 = ys[ni]; while (ni > 0 && ys[ni - 1] === y1) ni--; } } // back: to the start of this year first, then a year at a time; forward: the first issue of the next year
    else ni = Math.max(0, Math.min(fan.n - 1, ni + dir * 10));
    if (ni === fan.i) { snd('toast'); return; } fan.i = ni; snd('flip'); hooks.onSelect && hooks.onSelect(current()); }
  function stepFan(dt) {
    if (!fan) return; const f = fan, home = f.it.mesh.userData.home;
    f.k = sm(f.k, f.closing ? 0 : 1, 1 - Math.exp(-dt * (f.closing ? 7 : 4.5))); if (f.closing && f.k < .02) { closeFan(true); hooks.onSelect && hooks.onSelect(current()); return; }
    f.cur = sm(f.cur, f.i, 1 - Math.exp(-dt * 11)); if (Math.abs(f.cur - f.i) < 1e-3) f.cur = f.i;
    const c = Math.round(f.cur), lo = Math.max(0, c - 5), hi = Math.min(f.n - 1, c + 5);
    [...f.slots.keys()].forEach(idx => { if (idx < lo - 1 || idx > hi + 1) dropFanSlot(idx); }); // only the ones near you exist
    const cx = home.x, cy = home.y + .3, cz = home.z + 1.55, bx = home.x, by = home.y + RACK_H * .1, bz = home.z - .05;
    for (let idx = lo; idx <= hi; idx++) {
      const sl = fanSlot(idx), o = idx - f.cur, a = Math.max(-1, Math.min(1, o)), far = o - a;
      const ke = ss(Math.max(0, Math.min(1, f.k * 1.5 - Math.abs(o) * .09))); // the middle comes up first, the rest follow out to the sides
      const x = cx + a * RACK_W * .66 + far * RACK_W * .24, y = cy - Math.abs(a) * .02, z = cz - Math.abs(a) * .42 - Math.abs(far) * .05;
      sl.mesh.position.set(sm(bx, x, ke), sm(by, y, ke), sm(bz, z, ke));
      sl.mesh.rotation.set(-12 * DEG * (1 - ke), -a * 60 * DEG * ke, 0);
      const sc = (1 + .06 * (1 - Math.abs(a))) * (.85 + .15 * ke); sl.mesh.scale.set(sc, sc, 1);
      sl.mesh.renderOrder = 10 - Math.abs(Math.round(o)); sl.mesh.visible = ke > .01;
    }
  }

  // a new library while a magazine is in your hands or a box is open waits until it's put back (frame() applies it),
  // so a rebuild never pulls it out of your hands or leaves the selection on the wrong item
  let libPending = null;
  const libBusy = () => !!(libHeld || fan || reader || (rack && rack.items.some(it => (it.mesh.userData.lift || 0) > 0)));
  function setLibrary(list) { if (!active || !rows.length) return; if (libBusy()) { libPending = list || []; return; } libPending = null; lastLib = list || []; const was = rackItem(), wasTv = wide && isTvSel(wideSel); buildRack(list); buildTV(); let r = rows[rowI]; if (r && colI > r.boxes.length) colI = Math.min(colI, r.boxes.length + rackLen(rowI));
    if (was && was.d && was.d.id != null && rack) { const id = was.d.id; for (let ri = 0; ri < rows.length; ri++) { const k = (rack.byRow[ri] || []).findIndex(it => it.d && it.d.id === id); if (k >= 0) { if (ri !== rowI && !wide) { rowI = ri; r = rows[ri]; } if (ri === rowI) colI = r.boxes.length + 1 + k; break; } } } // the same magazine stays selected, wherever it moved
    if (was && !rackItem()) colI = r.boxes.length; if (wide) wideSel = wasTv ? wideCount() - 1 : Math.max(0, Math.min(wideSel, wideCount() - 1)); hooks.onSelect && hooks.onSelect(current()); }
  function current() { const r = rows[rowI]; if (!r) return null; if (tvView && tv) return { tv: { meta: tv.meta, mode: tv.mode, card: tv.card, ch: room.tvCh || 'ads' }, row: r }; if (wide && tv && isTvSel(wideSel) && !delivery) return { wide: { label: 'Retro TV', sub: TV_CH[room.tvCh] || TV_CH.ads, tv: true, on: !!tv.power }, row: r }; if (delivery) return { delivery: { n: delivery.n, crate: delivery.crate, shelf: dvShelf(delivery.list), open: !!delivery.open, waiting: !delivery.shown }, parcel: delivery.shown && !delivery.open, crate: delivery.crate && !delivery.open ? delivery.n : 0, row: r }; if (wide && wideSel < wideCount()) { const b = wideBox(wideSel); return { wide: { label: b.label, sub: b.sub, rack: wideSel >= rows.length, kind: wideSel >= rows.length ? (rack.units[wideSel - rows.length].movie ? 'movie' : rack.units[wideSel - rows.length].guide ? 'guide' : 'magazine') : null, empty: wideSel >= rows.length && !rack.units[wideSel - rows.length].n }, row: r }; } if (libHeld && libHeld.temp) return { lib: libHeld.d, row: rows[libHeld.ri] || r }; if (fan && !fan.closing) return { lib: fan.it.box.issues[fan.i], fan: { i: fan.i, n: fan.n, series: fan.it.box.series, year: fan.it.box.years[fan.i] || 0 }, row: rows[fan.it.ri] || r }; if (colI > r.boxes.length) { const it = rackItem(); if (it) return it.box ? { lib: it.d, stack: { series: it.box.series, n: it.box.issues.length, years: yearSpan(it.box.years), guide: it.d.kind === 'guide' }, row: r } : { lib: it.d, row: r }; } if (colI >= r.boxes.length) return { console: true, row: r }; const bx = r.boxes[colI]; return { game: bx.userData.g, sealed: !!bx.userData.sleeve, row: r }; }
  // Hands-on: A takes the case off the shelf into your hands (the room dims, a light falls on it) and the stick turns it.
  // X opens it on its hinge (the spine side): the disc / cartridge sits in the tray and the manual is clipped inside
  // the lid. X again takes the disc out (hold A then plays it), Y lifts the manual out to read, page by page. B steps
  // back one stage at a time: manual → disc → closed case → back on the shelf. While it's open the real case is stood
  // in for by an open-able one of the same size, handed back once it has closed.
  let peek = null, reader = null, dimK = 0, lastNav = 0, holdLook = 0, driftK = 0 /* (beta.8) the idle drift, eased out */, handZoom = 1, handZoomT = 1; const hPan = { x: 0, y: 0, tx: 0, ty: 0 }; const HIDE = [];
  const heldReady = () => heldCase && (heldCase.userData.lift || 0) > .9;
  function buildPeek(b) {
    const THREE = T(), u = b.userData, g = u.g; b.geometry.computeBoundingBox(); const sz = b.geometry.boundingBox.getSize(new THREE.Vector3());
    const d = sz.x, h = sz.y, w = sz.z, lt = Math.max(.012, d * .2), tray = d - lt, sys = (rows[u.ri] && rows[u.ri].id) || '';
    const kind = g.media === 'cart' ? 'cart' : g.media === 'umd' ? 'umd' : 'disc', kit = window.Inspect3D && Inspect3D.kit;
    const col = new THREE.Color(u.st.caseColor), inner = col.clone().lerp(new THREE.Color(0xffffff), .12);
    const caseM = new THREE.MeshStandardMaterial({ color: col, roughness: .42 }), innerM = new THREE.MeshStandardMaterial({ color: inner, roughness: .55 });
    const frontM = new THREE.MeshStandardMaterial({ color: u.front.map ? 0xffffff : col, map: u.front.map || null, roughness: .3, metalness: .05 });
    const rig = new THREE.Group(), mats = [caseM, innerM, frontM], geos = [], texs = [], own = [];
    // the lid borrows the case's cover with a hold of its own (let go in endPeek), so refreshArt or the case letting go of
    // its cover can't free the picture under the open lid (it went black)
    const lidT = u.front.map || u.frontTex || null; if (lidT && lidT.userData.url) { lidT.userData.refs = (lidT.userData.refs || 0) + 1; texs.push(lidT); }
    // opened before its cover arrived: put it on when it does (it stayed blank)
    if (!u.front.map && lidT) { if (lidT.userData.state === 'ok') { frontM.map = lidT; frontM.color.set(0xffffff); } else whenLoaded(lidT, () => { if (!peek || peek.box !== b || frontM.map) return; frontM.map = lidT; frontM.color.set(0xffffff); frontM.needsUpdate = true; }, () => {}); }
    const mesh = (geo, m) => { geos.push(geo); return new THREE.Mesh(geo, m); };
    // The same moulded case as Inspect: rounded-rectangle back and lid with this console's corner radius and soft
    // bevels, one continuous rounded wall ring, a moulded interior (disc well and finger notches, hub rosette, cart
    // cradle…). Built face-on (x across, y up, z out of the cover) and turned so the cover faces +x and the spine +z.
    const L = new THREE.Group(); L.rotation.y = Math.PI / 2; rig.add(L);
    const rc = Math.max(.01, Math.min(w * .45, w * (u.st.rc || .04))), bev = Math.min(.0028, d * .05);
    const rr = (W, H, R) => { const sh = new THREE.Shape(), x = -W / 2, y = -H / 2; R = Math.max(.001, Math.min(R, W / 2 - .001, H / 2 - .001));
      sh.moveTo(x + R, y); sh.lineTo(x + W - R, y); sh.quadraticCurveTo(x + W, y, x + W, y + R); sh.lineTo(x + W, y + H - R); sh.quadraticCurveTo(x + W, y + H, x + W - R, y + H);
      sh.lineTo(x + R, y + H); sh.quadraticCurveTo(x, y + H, x, y + H - R); sh.lineTo(x, y + R); sh.quadraticCurveTo(x, y, x + R, y); return sh; };
    const face = (W, H, R, m) => { const fg = new THREE.ShapeGeometry(rr(W, H, R), 12), uv = fg.attributes.uv, pos = fg.attributes.position; for (let i = 0; i < pos.count; i++) uv.setXY(i, pos.getX(i) / W + .5, pos.getY(i) / H + .5); uv.needsUpdate = true; return mesh(fg, m); };
    const slab = (W, H, R, Tk, m) => { const sg = new THREE.ExtrudeGeometry(rr(W - 2 * bev, H - 2 * bev, R - bev), { depth: Math.max(.0005, Tk - 2 * bev), bevelEnabled: true, bevelThickness: bev, bevelSize: bev, bevelSegments: 2, curveSegments: 10 }); sg.translate(0, 0, -(Tk - 2 * bev) / 2); return mesh(sg, m); };
    // local z: the back of the case at -d/2, the cover at +d/2; the tray is everything under the lid
    const tb = Math.min(.008, tray * .3), z0 = -d / 2;
    const back = slab(w, h, rc, tb, caseM); back.position.z = z0 + tb / 2; back.castShadow = true; L.add(back);
    const outFace = face(w - .004, h - .004, rc, u.backM || caseM); outFace.rotation.y = Math.PI; outFace.position.z = z0 - .0006; L.add(outFace);
    const R = g.mediaSize ? w * g.mediaSize / 2 : Math.min(w, h) * (kind === 'umd' ? .34 : .44); // the real disc size for this console (a GameCube disc is a small 8 cm one), as in Inspect
    const my = kit && kit.discLift ? kit.discLift(kind, sys) * h : 0; let inM = innerM;
    if (kit) { const it = kit.interiorTexture(u.st.caseColor, kind, (w - .02) / h, (h - .02) / h, (kind === 'cart' ? w * .42 / 1.8 : R) / h, sys); texs.push(it); own.push(it); inM = new THREE.MeshStandardMaterial({ map: it, roughness: .7 }); mats.push(inM); }
    const floorZ = z0 + tb; const inFace = face(w - .02, h - .02, rc * .8, inM); inFace.position.z = floorZ + .0004; L.add(inFace);
    const wall = Math.min(.011, w * .03), Dw = tray - tb;
    { const ringS = rr(w, h, rc), hole = rr(w - 2 * wall, h - 2 * wall, rc - wall); ringS.holes.push(new THREE.Path(hole.getPoints(40)));
      const rg = new THREE.ExtrudeGeometry(ringS, { depth: Dw, bevelEnabled: false, curveSegments: 12 }); const ring = mesh(rg, [innerM, caseM]); ring.position.z = floorZ; ring.castShadow = true; L.add(ring); }
    // the printed spine wraps the hinge side (local -x)
    const sp = mesh(new THREE.PlaneGeometry(d - .003, h - rc * 1.1), u.spine); sp.rotation.y = -Math.PI / 2; sp.position.set(-w / 2 - .0008, 0, 0); L.add(sp);
    // the hub rosette (collar, ten springy fingers, push button) or the UMD's pegs
    const hubM = new THREE.MeshStandardMaterial({ color: inner, roughness: .5 }), btnM = new THREE.MeshStandardMaterial({ color: col.clone().lerp(new THREE.Color(0), .25), roughness: .35 }); mats.push(hubM, btnM);
    const hubH = Math.max(.004, Math.min(.016, Dw * .7));
    if (kind === 'disc') { const hr = R * .135, hub = new THREE.Group(); hub.position.set(0, my, floorZ); L.add(hub);
      const collar = mesh(new THREE.CylinderGeometry(hr * 1.45, hr * 1.6, hubH * .22, 40), hubM); collar.rotation.x = Math.PI / 2; collar.position.z = hubH * .11; hub.add(collar);
      const fg = new THREE.BoxGeometry(hr * .4, hr * .32, hubH * .7); geos.push(fg);
      for (let k = 0; k < 10; k++) { const a = k / 10 * Math.PI * 2, f = new THREE.Mesh(fg, hubM); f.position.set(Math.cos(a) * hr * .7, Math.sin(a) * hr * .7, hubH * .6); f.rotation.z = a + Math.PI / 2; f.rotation.x = .25; hub.add(f); }
      const btn = mesh(new THREE.CylinderGeometry(hr * .45, hr * .5, hubH, 28), btnM); btn.rotation.x = Math.PI / 2; btn.position.z = hubH * .55; hub.add(btn); }
    else if (kind === 'umd') { const pg = new THREE.BoxGeometry(R * .14, R * .14, hubH * .8); geos.push(pg); [[-1, 1], [1, 1], [-1, -1], [1, -1]].forEach(([sx, sy]) => { const peg = new THREE.Mesh(pg, hubM); peg.position.set(sx * R * 1.1, sy * R * 1.2, floorZ + hubH * .4); L.add(peg); }); }
    // the lid, hinged on the spine edge; the pivot keeps the old name/axis so the opening animation is unchanged
    const pivot = new THREE.Group(); pivot.position.set(-w / 2, 0, d / 2 - lt / 2); L.add(pivot);
    const lid = slab(w, h, rc, lt, caseM); lid.position.x = w / 2; lid.castShadow = true; pivot.add(lid);
    const cover = face(w - .004, h - .004, rc, frontM); cover.position.set(w / 2, 0, lt / 2 + .0006); pivot.add(cover);
    const under = face(w - .01, h - .01, rc * .9, innerM); under.rotation.y = Math.PI; under.position.set(w / 2, 0, -lt / 2 - .0004); pivot.add(under);
    // the manual, clipped inside the lid, and its two clips
    const manU = g.manualPage || (g.booklet && g.manualPages && g.manualPages[0]) || null, manT = manU ? loadTex(manU, 'front') : null; if (manT) texs.push(manT);
    const manM = new THREE.MeshStandardMaterial({ color: manT ? 0xffffff : 0xece5d6, map: manT, roughness: .8 }); mats.push(manM);
    const man = mesh(new THREE.PlaneGeometry(w * .8, h * .86), manM); man.rotation.y = Math.PI; man.position.set(w / 2 + w * .02, 0, -lt / 2 - .0018); pivot.add(man);
    if (manT) whenLoaded(manT, () => { if (kit && kit.coverRect) kit.fitCover(man, kit.coverRect(manT.image, typeof ImageBitmap !== 'undefined' && manT.image instanceof ImageBitmap), w * .8, h * .86); }, () => { manM.map = null; manM.color.set(0xece5d6); manM.needsUpdate = true; });
    { const cg = new THREE.BoxGeometry(w * .07, h * .09, .004); geos.push(cg); [.34, -.34].forEach(y => { const c = new THREE.Mesh(cg, caseM); c.position.set(w * .135, y * h, -lt / 2 - .003); pivot.add(c); }); }
    // the disc (label art on top, the real mirrored data side underneath) or a cartridge, in its own group so it can come out
    const faceX = -d / 2 + tb + Math.min(hubH * .9, Dw * .6), artT = g.disc ? loadTex(g.disc, 'front') : null; if (artT) texs.push(artT);
    const media = new THREE.Group(); media.position.set(faceX, my, 0); rig.add(media); const spinner = new THREE.Group(); media.add(spinner);
    const M = new THREE.Group(); M.rotation.y = Math.PI / 2; spinner.add(M);
    if (kind === 'cart') {
      const cm = new THREE.MeshStandardMaterial({ color: artT ? 0xffffff : 0x26272d, map: artT, roughness: .5 }), shell = new THREE.MeshStandardMaterial({ color: 0x24252b, roughness: .55 }); mats.push(cm, shell);
      const cw = w * .42, ch = cw * 1.1, ct = Math.min(.012, lt * .55, Dw * .8); /* thinner than the lid, so a closed (thin) Switch case never shows it through the cover */
      const c = mesh(new window.BS3D.RoundedBoxGeometry(cw, ch, ct, 2, Math.min(.006, ct * .45)), [shell, shell, shell, shell, cm, shell]); c.position.set(0, 0, ct / 2); M.add(c);
      const pins = mesh(new THREE.BoxGeometry(cw * .7, ch * .1, ct + .001), new THREE.MeshStandardMaterial({ color: 0xc9a24a, metalness: .8, roughness: .35 })); mats.push(pins.material); pins.position.set(0, -ch / 2 + ch * .07, ct / 2); M.add(pins);
    } else {
      const TH = .0035, lab = new THREE.MeshStandardMaterial({ color: artT ? 0xffffff : 0xd9dde6, map: artT, metalness: artT ? .1 : .85, roughness: artT ? .35 : .18 }); mats.push(lab);
      const planar = (rg, Rr) => { const pa = rg.attributes.position, uv = rg.attributes.uv; for (let i = 0; i < pa.count; i++) uv.setXY(i, .5 + pa.getX(i) / (2 * Rr), .5 + pa.getY(i) / (2 * Rr)); return rg; };
      const top = mesh(planar(new THREE.RingGeometry(R * .22, R, 96), R), lab); top.position.z = TH / 2; M.add(top);
      const clear = new THREE.MeshStandardMaterial({ color: 0xe8eef8, transparent: true, opacity: .35, roughness: .05, metalness: .1, side: THREE.DoubleSide }); mats.push(clear);
      const fmt = sys === 'ps3' || sys === 'ps4' || sys === 'ps5' || sys === 'wiiu' ? 'bd' : sys === 'gc' ? 'gc' : 'dvd';
      let hc = 0; for (const ch of (g.title || 'disc')) hc = (hc * 31 + ch.charCodeAt(0)) >>> 0;
      const code = `${(sys || 'DSC').toUpperCase()}-${hc % 9000 + 1000}-${String.fromCharCode(65 + hc % 26)}${String.fromCharCode(65 + (hc >> 5) % 26)}  ${(hc >> 9) % 90 + 10}`;
      const dt = kit ? kit.discBackTexture(fmt, code) : null; if (dt) { texs.push(dt); own.push(dt); }
      const data = new THREE.MeshPhysicalMaterial({ color: 0xffffff, map: dt, metalness: .92, roughness: .1, iridescence: 1, iridescenceIOR: fmt === 'bd' ? 1.8 : 1.6, iridescenceThicknessRange: fmt === 'bd' ? [200, 560] : [120, 480] }); mats.push(data);
      const bot = mesh(planar(new THREE.RingGeometry(R * .2, R * .985, 96), R), data); bot.rotation.y = Math.PI; bot.position.z = -TH / 2; M.add(bot);
      const rim = mesh(new THREE.RingGeometry(R * .985, R, 96), clear); rim.rotation.y = Math.PI; rim.position.z = -TH / 2; M.add(rim);
      const hubT = mesh(new THREE.RingGeometry(R * .14, R * .22, 48), clear); hubT.position.z = TH / 2; M.add(hubT);
      const edge = mesh(new THREE.CylinderGeometry(R, R, TH, 96, 1, true), clear); edge.rotation.x = Math.PI / 2; M.add(edge);
      if (kind === 'umd') { const sm2 = new THREE.MeshStandardMaterial({ color: 0xdfe6ff, transparent: true, opacity: .22, roughness: .1 }); mats.push(sm2); const sh = mesh(new window.BS3D.RoundedBoxGeometry(R * 2.3, R * 2.5, Math.min(.03, Dw * .8), 2, .012), sm2); sh.position.set(0, -R * .1, 0); M.add(sh); }
    }
    b.add(rig);
    if (!HIDE.length) for (let i = 0; i < 6; i++) HIDE.push(new THREE.MeshBasicMaterial({ visible: false }));
    if (b.material !== HIDE) u.realMat = b.material; const keep = u.realMat || b.material; b.material = HIDE;
    return { box: b, rig, L, pivot, man, manM, media, spinner, faceX, my, w, k: 0, t: 1, disc: 0, discT: 0, keep, mats, geos, texs, own };
  }
  function endPeek() {
    closeReader(true);
    if (!peek) return; const p = peek; peek = null;
    try { p.box.material = p.keep; p.box.remove(p.rig); p.mats.forEach(m => m.dispose()); p.geos.forEach(g => g.dispose()); p.texs.forEach(t => p.own && p.own.includes(t) ? t.dispose() : unref(t)); } catch {}
    shadowDirty = true;
  }
  // ---- the manual, lifted out of the lid and held open like a real booklet: the cover on its own first, then two
  // pages side by side (2–3, 4–5 …); turning a page swings the right-hand leaf over to the left ----
  const pageUrl = u => /^bsfile:/.test(u) && !/[?&]t=/.test(u) ? u + (u.includes('?') ? '&' : '?') + 't=1600' : u;
  const spreadOf = (s, n) => s === 0 ? [-1, 0] : [2 * s - 1, 2 * s < n ? 2 * s : -1]; // [left, right] page index, -1 = none
  const spreadCount = n => n <= 1 ? 1 : 1 + Math.ceil((n - 1) / 2);
  function openReader() {
    const THREE = T(), g = heldCase && heldCase.userData.g, pages = (g && g.manualPages) || []; if (!pages.length) return false;
    const grp = new THREE.Group(), geos = [], mats = [], pgGeo = new THREE.PlaneGeometry(1, 1); geos.push(pgGeo);
    const pageMesh = () => { const m = new THREE.MeshBasicMaterial({ color: 0xf2ece0, toneMapped: false, transparent: true, opacity: 0, side: THREE.FrontSide }); mats.push(m); return new THREE.Mesh(pgGeo, m); };
    const left = pageMesh(), right = pageMesh(); grp.add(left, right);
    // the turning leaf, hinged on the spine: one page on its front, the next on its back
    const leafPivot = new THREE.Group(), leafF = pageMesh(), leafB = pageMesh(); leafB.rotation.y = Math.PI; leafPivot.add(leafF, leafB); leafPivot.visible = false; leafPivot.position.z = .003; grp.add(leafPivot);
    // a thin shadow down the spine
    const shM = new THREE.MeshBasicMaterial({ color: 0x000000, transparent: true, opacity: 0, toneMapped: false, depthWrite: false }); mats.push(shM); const sh = new THREE.Mesh(pgGeo, shM); sh.position.z = .001; grp.add(sh);
    scene.add(grp);
    reader = { grp, left, right, leafPivot, leafF, leafB, sh, geos, mats, pages, s: 0, n: spreadCount(pages.length), texs: new Map(), fade: 0, out: false, flip: null, zoom: 1, zoomT: 1, pan: 0, panT: 0, aspect: .7 };
    // a first page that's a whole spread or print sheet: swap in just its front cover once that's cut out
    const kit = window.Inspect3D && Inspect3D.kit, r0 = reader; if (kit && kit.coverCrop && !g.booklet) kit.coverCrop(pageUrl(pages[0])).then(u => { if (!u || reader !== r0) return; r0.pages = [u].concat(pages.slice(1)); const old = r0.texs.get(0); if (old) { unref(old); r0.texs.delete(0); } showSpread(r0.s); }, () => {});
    showSpread(0); if (peek) peek.man.visible = false; snd('flip'); return true;
  }
  // textures by page index, shared; pages near the open spread are kept (and the next ones preloaded), the rest let go
  function pageTex(i) { const r = reader; if (!r || i < 0 || i >= r.pages.length) return null; if (!r.texs.has(i)) { const t = loadTex(pageUrl(r.pages[i]), 'page'); r.texs.set(i, t); whenLoaded(t, () => { const im = t.image; if (reader === r && im && im.width && im.height && i === 0) r.aspect = im.width / im.height; paintAll(); }, () => {}); } return r.texs.get(i); }
  function paint(mesh, i) { const m = mesh.material; mesh.visible = i >= 0; if (i < 0) return; const t = pageTex(i), ok = t && t.userData.state === 'ok'; if (m.map !== (ok ? t : null)) { m.map = ok ? t : null; m.color.set(ok ? 0xffffff : 0xf2ece0); m.needsUpdate = true; } }
  function paintAll() { const r = reader; if (!r) return; const [L, R] = spreadOf(r.s, r.pages.length); if (!r.flip) { paint(r.left, L); paint(r.right, R); } else { paint(r.left, r.flip.under[0]); paint(r.right, r.flip.under[1]); paint(r.leafF, r.flip.leaf[0]); paint(r.leafB, r.flip.leaf[1]); } }
  function showSpread(s) {
    const r = reader; r.s = s; const n = r.pages.length, keep = new Set();
    for (let q = s - 1; q <= s + 2; q++) if (q >= 0 && q < r.n) spreadOf(q, n).forEach(i => i >= 0 && keep.add(i));
    keep.forEach(i => pageTex(i)); r.texs.forEach((t, i) => { if (!keep.has(i)) { unref(t); r.texs.delete(i); } });
    paintAll();
  }
  function turnPage(dir) {
    const r = reader; if (!r || r.out) return false; const to = r.s + dir; if (to < 0 || to >= r.n) return false;
    if (r.flip) { r.flip = null; r.leafPivot.visible = false; }
    const n = r.pages.length, [L0, R0] = spreadOf(r.s, n), [L1, R1] = spreadOf(to, n);
    // forward: the right page lifts and turns over to the left, showing the next spread's left page on its back.
    // Underneath, the old left page stays until it's covered, and the new right page is already there.
    r.flip = dir > 0 ? { k: 0, dir, under: [L0, R1], leaf: [R0, L1] } : { k: 0, dir, under: [L1, R0], leaf: [L0, R1] };
    r.leafPivot.visible = true; showSpread(to); snd('page'); nudge(); return true;
  }
  function closeReader(now) {
    const r = reader; if (!r) return; if (!now) { r.out = true; return; }
    reader = null; try { scene.remove(r.grp); r.geos.forEach(g => g.dispose()); r.mats.forEach(m => m.dispose()); r.texs.forEach(t => unref(t)); } catch {}
    if (peek) peek.man.visible = true;
  }
  function frameReader(dt) {
    const r = reader; if (!r) return;
    r.fade = sm(r.fade, r.out ? 0 : 1, 1 - Math.exp(-dt * (r.out ? 9 : 5))); if (r.out && r.fade < .02) { closeReader(true); return; }
    r.zoom = sm(r.zoom, r.zoomT, 1 - Math.exp(-dt * 7)); r.pan = sm(r.pan, r.panT, 1 - Math.exp(-dt * 7));
    // in front of the camera, a little below eye level, as if held up to read; it rises out of the lid as it fades in
    const fwd = sv('readFwd'); camera.getWorldDirection(fwd);
    const e = ss(r.fade), hgt = .42 * r.zoom, wid = hgt * r.aspect;
    // the cover alone sits on the right half, like the closed booklet's front; spreads are centred on the spine
    const single = r.s === 0 && !r.flip, shift = single ? -wid / 2 : 0;
    r.grp.position.copy(camera.position).addScaledVector(fwd, 1.02 + (1 - e) * .5); r.grp.quaternion.copy(camera.quaternion);
    r.shiftV = sm(r.shiftV == null ? shift : r.shiftV, shift, 1 - Math.exp(-dt * 6)); r.grp.translateY(.03 - (1 - e) * .25 - r.pan * hgt * .5); r.grp.translateX(r.shiftV);
    r.left.scale.set(wid, hgt, 1); r.left.position.x = -wid / 2; r.right.scale.set(wid, hgt, 1); r.right.position.x = wid / 2;
    r.sh.scale.set(wid * .06, hgt, 1); r.sh.material.opacity = .18 * e * (r.left.visible && r.right.visible ? 1 : 0);
    [r.left, r.right, r.leafF, r.leafB].forEach(m => { m.material.opacity = e; });
    if (r.flip) {
      r.flip.k = Math.min(1, r.flip.k + dt * 2.4); const f = ss(r.flip.k), dir = r.flip.dir;
      r.leafF.scale.set(wid, hgt, 1); r.leafB.scale.set(wid, hgt, 1);
      // forward: hinged on the spine, it starts as the right page and swings over to the left; back: the mirror image
      r.leafF.position.x = r.leafB.position.x = dir > 0 ? wid / 2 : -wid / 2; // back to back: one sheet
      r.leafPivot.rotation.y = (dir > 0 ? -1 : 1) * Math.PI * f;
      paint(r.leafF, r.flip.leaf[0]); paint(r.leafB, r.flip.leaf[1]); paint(r.left, r.flip.under[0]); paint(r.right, r.flip.under[1]);
      if (r.flip.k >= 1) { r.flip = null; r.leafPivot.visible = false; paintAll(); }
    }
  }
  // ---- what the buttons do in your hands ----
  function pickUp() { const r = rows[rowI], b = r && r.boxes[colI]; if (!b || intro.on || wide || holding) return false; heldCase = b; { const u = b.userData, ry = u.hry || 0; hold = { rx: u.hrx || 0, ry, trx: -6, try: Math.round(ry / 360) * 360 }; } /* from wherever it is (it may still be turning back from last time), to face you by the short way */ snd('lift'); backArt(b); const g = b.userData.g; if (!(g.manualPages && g.manualPages.length) && hooks.booklet && g._g && g._g.kind === 'rom') hooks.booklet(g).then(u => { if (u && u.length && !(g.manualPages && g.manualPages.length)) { g.manualPages = u; g.booklet = true; if (peek && peek.box === b && peek.manM && !peek.manM.map) { const t = loadTex(u[0], 'front'); peek.texs.push(t); whenLoaded(t, () => { if (!peek || peek.box !== b) return; peek.manM.map = t; peek.manM.color.set(0xffffff); peek.manM.needsUpdate = true; }, () => {}); } } }, () => {}); return true; }
  // on the shelf a case only needs its front and spine; in your hands it gets its back cover too (kept until it's far away again)
  function backArt(b) {
    const u = b.userData; if (u.backM || !u.g.back) return; const THREE = T();
    u.backM = new THREE.MeshStandardMaterial({ color: u.st.caseColor, roughness: .3, metalness: .05 }); const base = b.material === HIDE ? (u.realMat || b.material) : b.material, m = base.slice(); m[1] = u.backM; if (b.material === HIDE) { u.realMat = m; if (peek && peek.box === b) peek.keep = m; } else b.material = m;
    const t = u.backTex = loadTex(u.g.back, 'back'); whenLoaded(t, () => { if (u.backTex !== t || !u.backM) return; u.backM.map = t; u.backM.color.set(0xffffff); u.backM.needsUpdate = true; }, () => {});
  }
  function dropBack(u, b) { if (!u.backM) return; if (b && Array.isArray(b.material) && b.material !== HIDE) { const m = b.material.slice(); m[1] = m[2]; b.material = m; } try { u.backM.dispose(); } catch {} u.backM = null; if (u.backTex) { unref(u.backTex); u.backTex = null; } }
  // X: pick up and open (from the shelf), open, disc out, disc back in
  function handX() {
    lastInput = woke();
    if (!heldCase) { if (!pickUp()) return null; if (heldCase.userData.sleeve) return { lifted: true, wrapped: true }; if (peek && peek.box === heldCase) { peek.t = 1; snd('caseOpen'); return { open: true }; } endPeek(); peek = buildPeek(heldCase); hold.trx = -4; snd('caseOpen'); return { open: true }; }
    if (reader && !reader.out) return null; if (reader) closeReader(true); // still fading away: finish it now
    if (peek && peek.box !== heldCase) endPeek();
    if (!peek && heldCase.userData.sleeve) { snd('hold'); return null; } // sealed: hold A to unwrap it first
    if (!peek) { peek = buildPeek(heldCase); hold.try = Math.round(hold.try / 360) * 360; snd('caseOpen'); return { open: true }; }
    if (!peek.t) { peek.t = 1; snd('caseOpen'); return { open: true }; }
    peek.discT = peek.discT ? 0 : 1; if (peek.discT) { peek.base = { trx: hold.trx, try: hold.try }; hold.trx = 0; hold.try = Math.round(hold.try / 360) * 360; peek.drx = peek.dry = 0; } else discHome();
    snd(peek.discT ? 'discOut' : 'discIn'); return { disc: !!peek.discT };
  }
  // The disc back into its case: the case turns back to how you held it, and the disc's own turns unwind by the short way
  // (at most half a turn). It used to replay every spin you'd given it, the case whirling round and round.
  const wrap180 = a => ((a % 360) + 540) % 360 - 180;
  // the same for a Blu-ray's disc. Its case is drawn from hold the whole time (it used to be drawn from a copy of the eased
  // angle while the disc was out, and jumped to the real one when the disc was home)
  function movieDiscHome(rg) { rg.cry = wrap180(rg.cry || 0); rg.dry = rg.drx = 0; }
  // an object's own copy of the in-hand angle (see frame): follows hold while it's the one in your hands; let go, its whole
  // turns are dropped and it eases square from where it is
  function ownTurn(u, mine, dt) { if (mine) { u.hrx = hold.rx; u.hry = hold.ry; u.hHeld = true; return; } if (u.hHeld) { u.hHeld = false; u.hry = wrap180(u.hry || 0); } if (u.hrx || u.hry) { const kh = 1 - Math.exp(-dt * 6); u.hrx = sm(u.hrx || 0, 0, kh); u.hry = sm(u.hry || 0, 0, kh); if (Math.abs(u.hrx) + Math.abs(u.hry) < .01) u.hrx = u.hry = 0; } }
  function discHome() { if (!peek) return; if (peek.base) { hold.trx = peek.base.trx; hold.try = peek.base.try; peek.base = null; } peek.cry = wrap180(peek.cry || 0); peek.dry = peek.drx = 0;
    if (peek.spinner) { const x = peek.spinner.rotation.x % (2 * Math.PI); peek.spinner.rotation.x = x > Math.PI ? x - 2 * Math.PI : x < -Math.PI ? x + 2 * Math.PI : x; } }
  // Y: the manual out to read, and back into the lid
  function handY() { lastInput = woke(); if (!peek || !peek.t || peek.discT) return null; if (reader && reader.out) closeReader(true); if (reader) { if (!reader.out) { reader.out = true; snd('flip'); } return { reading: false }; } return openReader() ? { reading: true } : null; }
  function readerZoom(dz) { if (!reader) return; nudge(); reader.zoomT = Math.max(1, Math.min(2.4, dz > 0 ? reader.zoomT * 1.3 : reader.zoomT / 1.3)); if (reader.zoomT < 1.05) { reader.zoomT = 1; reader.panT = 0; } }
  function readerPan(dy) { if (!reader || reader.zoomT <= 1) return; nudge(); const lim = 1 - 1 / reader.zoomT; reader.panT = Math.max(-lim, Math.min(lim, reader.panT + dy)); }
  // up/down in your hands: step closer to the case (or back)
  function zoomHand(dz) { if ((!heldCase && !libHeld) || reader) return; nudge(); handZoomT = Math.max(1, Math.min(3, dz > 0 ? handZoomT * 1.15 : handZoomT / 1.15)); if (handZoomT < 1.04) handZoomT = 1; clampPan(); }
  // left stick in your hands: slide the view across the case (further the closer you are), to look at the manual, the disc, a corner
  function clampPan() { const open = peek && peek.box === heldCase && peek.t ? 1 : 0, lx = .25 + .35 * open + .3 * (handZoomT - 1), ly = .2 + .3 * (handZoomT - 1); hPan.tx = Math.max(-lx, Math.min(lx, hPan.tx)); hPan.ty = Math.max(-ly, Math.min(ly, hPan.ty)); }
  function panHand(dx, dy) { if (!heldCase || reader) return; nudge(); hPan.tx += dx; hPan.ty += dy; clampPan(); }
  // the stick leans the view: tilt it and it shifts that way in proportion, let go and it glides back to centre
  // (on top of any keyboard nudges); sx, sy are -1..1 after the dead zone
  function leanHand(sx, sy) { if (!heldCase || reader) { hPan.lx = hPan.ly = 0; return; } const open = peek && peek.box === heldCase && peek.t ? 1 : 0, lx = sx * (.22 + .3 * open + .3 * (handZoomT - 1)), ly = sy * (.18 + .28 * (handZoomT - 1)); if (lx !== (hPan.lx || 0) || ly !== (hPan.ly || 0)) nudge(); hPan.lx = lx; hPan.ly = ly; }
  function handState() { return { fan: !!(fan && !fan.closing && !(libHeld && libHeld.temp)) /* (beta.11: an issue picked up from it is in your hands) */, fanYears: !!(fan && fan.it.box.years.some(Boolean)), wrapped: !!(heldCase && heldCase.userData.sleeve) || libWrappedNow() || !!selParcel(), parcel: !!selParcel(), opening: !!opening, crate: !!(delivery && delivery.crate), delivery: !!delivery, dvShown: !!(delivery && delivery.shown), libWrapped: libWrappedNow(), libHeld: !!libHeld, tv: tvView, tvAvail: !!tv, movieOpen: !!(libHeld && libHeld.rig && libHeld.rig.openT), movieDisc: !!(libHeld && libHeld.rig && libHeld.rig.discT), libMovie: !!(libHeld && libHeld.movie) || !!(!heldCase && !libHeld && !wide && rackItem() && rackItem().movie), held: !!heldCase, ready: !!heldReady(), open: !!(peek && peek.t), disc: !!(peek && peek.discT && peek.disc > .9), discOut: !!(peek && peek.discT), reading: !!(reader && !reader.out), page: reader ? reader.s : 0, pageLabel: reader ? (() => { const [L, R] = spreadOf(reader.s, reader.pages.length); return L < 0 ? 'Cover' : R < 0 ? `Page ${L + 1}` : `Pages ${L + 1}–${R + 1}`; })() : '', pages: reader ? reader.pages.length : 0, hasManual: !!(heldCase && heldCase.userData.g.manualPages && heldCase.userData.g.manualPages.length), game: heldCase ? heldCase.userData.g : null }; }
  let zoomAt = -1e9; // when A last zoomed in from the wide view (its glide gets the same light per-frame pacing as a change of shelf)
  let rowNavAt = -1e9; // when you last went up or down a shelf (the change of row is eased, see frame)
  // Discrete camera moves (wide view <-> a shelf, shelf <-> shelf) are timed tweens on an ease-in-out curve instead of the
  // chase: they set off gently, land exactly on time and never creep. A new move mid-tween starts from where the camera is
  // and how fast it's going (a cubic Hermite term carries the velocity), so B mid-zoom turns back smoothly.
  const tw = { on: false, t: 0, d: 1, s: 1, zoom: 0, x0: 0, y0: 0, z0: 0, x1: 0, y1: 0, z1: 0, vx: 0, vy: 0, vz: 0, ay: 0, az: 0 }, camV = { x: 0, y: 0, z: 0 }, fbx = { x0: 0, x1: 0, y0: 0, y1: 0, z: null };
  let twKey = null, twSndAt = -1e9, rmMQ = null, preRow = -1, preAt = 0, preOn = false, lastTg = null;
  const viewKey = () => intro.on || tvView || roomView ? -2 : heldCase || libHeld || fan || holding ? -3 : wide ? -1 : rowI; // -1 wide, >= 0 a shelf, < -1 no tween (the chase)
  const reducedMo = () => document.body.classList.contains('reduce-motion'); /* (beta.12: Boxshelf's own Reduce motion, which follows the system only when told to) */
  function twStart(tg, zoom, dur) {
    const dx = tg.x - camP.x, dy = tg.y - camP.y, dz = tg.z - camP.z, dist = Math.sqrt(dx * dx + dy * dy + dz * dz), rm = reducedMo();
    tw.on = true; tw.t = 0; tw.s = 0; tw.zoom = zoom; tw.x0 = camP.x; tw.y0 = camP.y; tw.z0 = camP.z; tw.x1 = tg.x; tw.y1 = tg.y; tw.z1 = tg.z; tw.vx = camV.x; tw.vy = camV.y; tw.vz = camV.z;
    tw.d = dur || (rm ? .3 : zoom ? Math.max(.55, Math.min(.9, .45 + dist * .045)) : .5);
    const a = rm || !zoom || dur ? 0 : Math.min(1, dist / 6); tw.ay = .07 * a; tw.az = .2 * a; // a slight rise and hang-back mid-way: walking up to it, not flying
  }
  function twStep(dt, tg) {
    if (Math.abs(tg.x - tw.x1) + Math.abs(tg.y - tw.y1) + Math.abs(tg.z - tw.z1) > .01) twStart(tg, tw.zoom, Math.max(.3, tw.d - tw.t)); // the target moved (a step along the shelf mid-glide): carry on from here
    tw.t += dt; const s = tw.s = Math.min(1, tw.t / tw.d);
    if (s >= 1) { camP.x = tw.x1; camP.y = tw.y1; camP.z = tw.z1; tw.on = false; return; } // land exactly
    const e = s < .5 ? 4 * s * s * s : 1 - (2 - 2 * s) * (2 - 2 * s) * (2 - 2 * s) / 2, h = s * (1 - s) * (1 - s) * tw.d, b = Math.sin(Math.PI * s), bb = b * b;
    camP.x = tw.x0 + (tw.x1 - tw.x0) * e + tw.vx * h; camP.y = tw.y0 + (tw.y1 - tw.y0) * e + tw.vy * h + tw.ay * bb; camP.z = tw.z0 + (tw.z1 - tw.z0) * e + tw.vz * h + tw.az * bb;
  }
  // resting the wide view's frame on a shelf: its covers near where A will land (and its spines) start loading, a little a frame
  function preload(now) {
    const i = wide && !intro.on && !tw.on && wideSel < rows.length ? wideSel : -1; if (i !== preRow) { preRow = i; preAt = now; preOn = false; } if (i < 0 || now - preAt < 250) return; preOn = true;
    if (upQ.length > 1 || loadQ.length > 2) return; const r = rows[i], n = r.boxes.length, c = Math.min(i === rowI ? (colI > n ? 0 : colI) : Math.min(colI, n), n - 1);
    for (let d = 0; d <= 4; d++) for (let sg = -1; sg <= 1; sg += 2) { const b = r.boxes[c + d * sg]; if (b && !b.userData.frontLoaded && !b.userData.mail) { loadFront(b); return; } if (!d) break; }
  }
  function move(dx, dy) {
    if (tvView) { lastInput = woke(); if (dx) hooks.onTvChannel && hooks.onTvChannel(dx); else if (dy) hooks.onTvBand && hooks.onTvBand(dy); return; } // the TV: left / right what's on, up / down the channel
    if (delivery) { if (delivery.shown && !delivery.open) { const ro = delivery.rot; ro.ty += dx * 45 * DEG; ro.tx = Math.max(-55 * DEG, Math.min(55 * DEG, ro.tx + dy * 30 * DEG)); lastInput = woke(); } return; } // the package in front of you: turn it to look it over
    if (!rows[rowI]) return; // closed (the wall already let go) or not built yet
    { const t = performance.now(); navRun = t - lastNav < 200 ? navRun + 1 : 0; lastInput = lastNav = t; unsleep(); } if (intro.on) skipFill();
    if (fan) { if (dx) fanStep(dx); return; }
    if (holding || heldCase || libHeld) return;
    if (wide) { wideMove(dx, dy); return; }
    if (dy && rack) { const r0 = rows[rowI], k = colI - r0.boxes.length - 1, cur = k >= 0 && rack.byRow[rowI] && rack.byRow[rowI][k];
      if (cur) { // in the reading corner: up / down go to the next ledge that has something on it (the empty ones between the magazines and the movies are passed over), to the item nearest above / below
        let ri = rowI + dy; while (ri >= 0 && ri < rows.length && !rackLen(ri)) ri += dy;
        if (ri >= 0 && ri < rows.length) { const x = cur.mesh.position.x; let best = 0, bd = 1e9; rack.byRow[ri].forEach((it, i) => { const dd = Math.abs(it.mesh.position.x - x); if (dd < bd) { bd = dd; best = i; } }); rowI = ri; rowNavAt = performance.now(); colI = rows[ri].boxes.length + 1 + best; }
        hooks.onSelect && hooks.onSelect(current()); return; } }
    if (dy) { const r0 = rows[rowI], k = colI - r0.boxes.length - 1, ri0 = rowI; rowI = Math.max(0, Math.min(rows.length - 1, rowI + dy)); if (rowI === ri0) { hooks.onSelect && hooks.onSelect(current()); return; } rowNavAt = performance.now(); const r = rows[rowI]; colI = k >= 0 && rackLen(rowI) ? r.boxes.length + 1 + Math.min(k, rackLen(rowI) - 1) : Math.min(colI, r.boxes.length); }
    if (dx) { const r = rows[rowI]; colI = Math.max(0, Math.min(r.boxes.length + rackLen(rowI), colI + dx)); }
    hooks.onSelect && hooks.onSelect(current());
  }
  function jump(n) { if (delivery || tvView) return; lastInput = lastNav = woke(); if (fan) { fanJump(Math.sign(n)); return; } const r = rows[rowI]; if (!r || holding || heldCase || libHeld) return; colI = Math.max(0, Math.min(r.boxes.length + rackLen(rowI), colI + n)); hooks.onSelect && hooks.onSelect(current()); }
  function activate() {
    lastInput = woke(); if (intro.on) { skipFill(); return null; }
    if (tvView) return { tvPlay: true };
    if (delivery) { if (delivery.open) { skipOpening(); return { skipped: true }; } return delivery.shown ? { parcelTap: true } : null; }
    if (fan && !(libHeld && libHeld.temp)) { if (fan.closing) return null; const d = fan.it.box.issues[fan.i]; if (libSealed(d)) return { sealedLib: true }; return { lib: d }; } // (beta.11: an issue picked up from it is handled as libHeld below)
    if (wide) return wideEnter();
    if (libHeld && libHeld.movie) { const rg = movieRig(libHeld);
      if (!rg.openT) { rg.openT = 1; hold.try = Math.round(hold.try / 360) * 360; hold.trx = 0; movieArt(libHeld); snd('caseOpen'); return { movieOpen: true }; }
      if (!rg.discT) { if (rg.open < .85) return null; rg.drx = rg.dry = 0; rg.discT = 1; snd('slide'); return { movieDisc: true }; } // the case stays as you held it, behind the disc (the stick turns the disc now, see turn)
      return { lib: libHeld.d }; } // the disc in your hands: play it
    if (libHeld) return libHeld.bag ? { sealedLib: true } : { lib: libHeld.d };
    if (heldCase) return { inHand: true }; // A in your hands: hold-to-play is handled by the shelf (needs the disc out)
    const c = current(); if (!c) return null;
    if (selParcel()) return { parcelTap: true }; // a parcel opens with a hold, not a tap
    if (c.stack) { const it = rackItem(); if (!it || !it.box || holding || heldCase) return null; openFan(it); return { fan: true }; }
    if (c.lib) return liftLib() ? { libLift: true } : null;
    if (c.console) { holding = holding ? null : c.row; const cg = c.row && c.row.consoleG, ry = cg ? cg.rotation.y / DEG : 0; hold = { rx: cg ? cg.rotation.x / DEG : 0, ry, trx: -10, try: 25 + Math.round((ry - 25) / 360) * 360 }; return { held: !!holding }; }
    if (pickUp()) return { lifted: true };
    return null;
  }
  function back() {
    lastInput = woke(); if (intro.on) { skipFill(); return true; }
    if (delivery) { if (delivery.shown) skipOpening(); return true; }
    if (tvView) { tvView = false; if (tvReturn && rows[tvReturn.rowI]) { rowI = tvReturn.rowI; colI = tvReturn.colI; wide = false; } else { wide = true; wideSel = wideCount() - 1; } tvReturn = null; hooks.onSelect && hooks.onSelect(current()); return true; } // off the TV, back where you were (it keeps playing; X switches it off)
    if (reader && !reader.out) { reader.out = true; snd('flip'); return true; }
    if (peek && peek.discT) { peek.discT = 0; discHome(); snd('discIn'); return true; }
    if (peek && peek.t) { peek.t = 0; clampPan(); snd('caseClose'); return true; } // (an open case lets the view pan further: bring it back inside a closed one's limits)
    if (heldCase) { heldCase = null; if (peek) peek.t = 0; snd('slideBack'); return true; }
    if (libHeld && libHeld.rig && libHeld.rig.discT) { libHeld.rig.discT = 0; movieDiscHome(libHeld.rig); snd('discIn'); return true; } // the disc back on its hub
    if (libHeld && libHeld.rig && libHeld.rig.openT) { libHeld.rig.openT = 0; snd('caseClose'); return true; } // the case shut
    // (beta.11) a fan issue put back: its stand-in goes now, not on the next drawn frame (a slow or paused draw left it in the rack)
    if (libHeld) { const h = libHeld; libHeld = null; if (h.temp && rack && rack.items.includes(h)) dropTemp(h); snd('slideBack'); return true; }
    if (fan) { if (!fan.closing) closeFan(false); return true; }
    if (holding) { holding = null; return true; }
    if (!wide) { wide = true; wideSel = wideFromHere(); hooks.onSelect && hooks.onSelect(current()); return true; } return false;
  }
  function turn(dx, dy) { lastInput = woke(); if (!holding && !heldCase && !libHeld) return; if (reader) return;
    if (peek && peek.discT && peek.box === heldCase) { peek.dry = (peek.dry || 0) + dx; peek.drx = Math.max(-75, Math.min(75, (peek.drx || 0) + dy)); return; } // the disc turns in your hands; its case stays put, set aside
    if (libHeld && libHeld.rig && libHeld.rig.discT) { const rg = libHeld.rig; rg.dry = (rg.dry || 0) + dx; rg.drx = Math.max(-75, Math.min(75, (rg.drx || 0) + dy)); return; } // a Blu-ray's disc, the same
    hold.try += dx; hold.trx = Math.max(-60, Math.min(60, hold.trx + dy)); }
  function release() { if (!holding && !heldCase && !libHeld) return; nudge(); hold.try = Math.round(hold.try / 90) * 90; }

  const sm = (a, b, k) => a + (b - a) * k, ss = t => t <= 0 ? 0 : t >= 1 ? 1 : t * t * (3 - 2 * t);
  let prep = null, dummyTex = null, primeKeep = [];
  // things drawn once, unseen, during the preparation only so their shaders are really used before the first visible frame
  // (compiling alone still left the driver's first-use work to the moment they appeared): put away again here
  function unprime() { if (card && !card.userData.fade) card.visible = false; primeKeep.forEach(o => { if (o === wideFrame) { o.visible = o.userData.k > .01; return; } if (o.parent) o.parent.remove(o); try { o.geometry.dispose(); } catch {} }); primeKeep = []; }
  // only the lamp over the row in view casts a shadow (and only with shadows on); the lamp it moves away from gives its
  // shadow map back (each kept one was ~8 MB of graphics memory, one per row visited)
  // Only the lamp over the row in view casts shadows. Changing row hands its shadow map (same size) to the new lamp
  // instead of throwing it away and making a new one, which cost a stall on every change of shelf.
  function setShadowRow() { const on = renderer.shadowMap.enabled; let spare = null; fst.row = 1;
    rows.forEach((rw, i) => { if (!(on && i === rowI) && rw.lamp.shadow.map) { if (!spare) spare = rw.lamp.shadow.map; else { try { rw.lamp.shadow.map.dispose(); } catch {} } rw.lamp.shadow.map = null; } });
    rows.forEach((rw, i) => { const want = on && i === rowI; if (want && !rw.lamp.shadow.map && spare && spare.width === rw.lamp.shadow.mapSize.x) { rw.lamp.shadow.map = spare; spare = null; } rw.lamp.castShadow = want; });
    if (spare) { try { spare.dispose(); } catch {} } shadowRow = rowI; }
  function prepReady() {
    if (work.length || upQ.length || loadQ.length || loading) return false;
    if (rows.some(rw => rw.console && rw.console.model && rw.consoleG && !rw.consoleG.userData.model && !rw.fitFailed)) return false;
    const rr = rows[rowI]; if (rr && rr.boxes.some((b, i) => { const u = b.userData; return Math.abs(i - colI) <= 5 && !u.mail && (u.frontReq || (u.frontTex && !u.frontTex.userData.state)); })) return false;
    return true;
  }
  // The handheld (Android) lets an idle museum sleep: SLEEP_MS after the last input, once nothing is moving, loading or
  // playing, the loop stops (no frames at all) until the next input or a picture arriving. Windows keeps its idle drift.
  const SLEEP_MS = 8000; let sleepOk = null;
  function unsleep() { if (pauseWhy.delete('idle')) { paused = pauseWhy.size > 0; if (!paused && active && !raf) { lastT = 0; raf = requestAnimationFrame(frame); } } }
  function woke() { unsleep(); return performance.now(); }
  // input that only steers something in your hands (a pinch, the left stick, a page turn, A held): on the handheld it wakes
  // the museum and starts the SLEEP_MS over, like any other input. Windows never sleeps, so there it changes nothing.
  function nudge() { if (sleepOk) lastInput = woke(); }
  // Something in your hands may sleep too, once it has come to rest: not while A pulls at the plastic, the plastic peels
  // off or sparkles, it is still coming up to you, or the fan, the lid, the disc, a Blu-ray case or a page is on its way
  function handStill() {
    if (holdA || libPull > 0 || (pullCase && pullCase.userData.pull > 0) || unwraps.length || sparks) return false;
    if ((heldCase || holding || libHeld) && (Math.abs(hold.rx - hold.trx) > .01 || Math.abs(hold.ry - hold.try) > .01)) return false;
    if (Math.abs(handZoom - handZoomT) > 1e-4) return false;
    if (Math.abs(hPan.x - hPan.tx - (hPan.lx || 0)) > 1e-4 || Math.abs(hPan.y - hPan.ty - (hPan.ly || 0)) > 1e-4) return false;
    if (heldCase && heldCase.userData.lift !== 1) return false;
    if (libHeld) { const u = libHeld.mesh.userData, rg = libHeld.rig; if (u.lift !== 1 || (rg && (rg.open !== rg.openT || rg.disc !== rg.discT))) return false; }
    if (fan && (fan.closing || fan.cur !== fan.i || Math.abs(fan.k - 1) > 1e-3)) return false;
    if (peek && ((peek.k !== 0 && peek.k !== 1) || (peek.disc !== 0 && peek.disc !== 1))) return false;
    if (reader && (reader.out || reader.flip || Math.abs(reader.fade - 1) > 1e-3 || Math.abs(reader.zoom - reader.zoomT) > 1e-4 || Math.abs(reader.pan - reader.panT) > 1e-4)) return false;
    return true;
  }
  function maybeSleep(now) {
    if (sleepOk === null) sleepOk = !!(window.bsPlatform && window.bsPlatform.os === 'android');
    if (!sleepOk || now - lastInput < SLEEP_MS || intro.on || tw.on || prep || delivery || shim || shadowDirty) return;
    if ((heldCase || holding || libHeld || peek || fan || reader) && !handStill()) return;
    if (work.length || upQ.length || loadQ.length || loading || (tv && (tv.power || tv.pow > 0))) return;
    pauseWhy.add('idle'); paused = true;
    if (raf) { cancelAnimationFrame(raf); raf = 0; } lastT = 0; // the frame already asked for would only wake to stop
  }
  function frame(now) {
    if (!active || !renderer || paused) { lastT = 0; raf = 0; return; } // paused (the reader, a dimmed screen, an idle handheld): no loop at all until it resumes
    raf = requestAnimationFrame(frame);
    if (prep) {
      // unseen: build and upload as much as fits in a frame, draw once (so the first visible frame has nothing new to do)
      // While the shelf is still fading to black, only work that stays on the processor (spines, reading models): anything
      // for the graphics card (uploads, a first draw of the whole wall) can hold up the card the fade itself is drawn
      // with, which showed as a small hitch in the fade. The heavy first draw happens once the screen is black.
      const waited = now - prep.t0;
      if (waited < 640) { doWork(8); return; }
      doWork(10); uploadSome(10); if (shadowRow !== rowI) setShadowRow();
      if (!prep.drawn || upQ.length || !prepReady()) { renderer.shadowMap.needsUpdate = !prep.drawn; try { camera.position.set(camP.x, camP.y, camP.z); camera.lookAt(camP.x, camP.y - .05, 0); drawFrame(); prep.drawn = true; } catch {} } // the opening view: the whole wall, so everything gets drawn (and uploaded) once
      if ((prepReady() && prep.drawn && waited > 700) || waited > 1900) { if (hooks.log) hooks.log(`museum: ready to show after ${Math.round(waited)} ms of preparation${waited > 1900 ? ' (timed out)' : ''}`); prep = null; unprime(); canvas.classList.add('on'); lastT = 0; hooks.onShown && hooks.onShown(); }
      return;
    }
    // a frame that came late: note what the one before it did (Boxshelf log), so a stutter can be traced on any PC
    if (lastT && now - lastT > 50 && !intro.on && !document.hidden && hitchN < 40 && now - hitchAt > 1000) { hitchAt = now; hitchN++; hooks.log && hooks.log(`museum hitch: ${Math.round(now - lastT)} ms between frames (${Math.round(now - rowNavAt) < 3000 ? 'shelf changed ' + Math.round(now - rowNavAt) + ' ms before; ' : ''}frame before: ${fst.jsMs} ms of work, ${fst.up} uploads, ${fst.fr} covers, ${fst.sp} spines${fst.sh ? ', shadows redrawn' : ''}${fst.row ? ', shadow lamp moved' : ''}, ${renderer.info.programs.length} shaders)`); }
    dynRes(now);
    const fT0 = performance.now(); fst.up = fst.fr = fst.sp = fst.sh = fst.row = 0;
    const settling = !intro.on && now - rowNavAt < 550; // just changed shelf: the camera is gliding, keep each frame light
    // the zoom-in from the wide view glides too: covers and spines then trickle in (one upload a frame, none in the frame the
    // shadow lamp changes over, which redraws its shadows) instead of landing several to a frame (the camera itself is unchanged)
    const glide = settling || (!intro.on && now - zoomAt < 900);
    // racing along a shelf (the third quick step on, until you stop for 140 ms): the cases only flash past, so covers, the
    // tidy-up of far ones and most of the shadow redraws wait until you stop; at most one picture goes to the graphics card a
    // frame. Each of those landing on a step was what made a fast scroll stutter. (140 ms: the cover you stop on still loads
    // well before its case has turned to face you, which starts 260 ms after the last step.)
    // (Android only: a desktop keeps up, and there the covers keep showing as they go by)
    if (raceOk === null) raceOk = !!(window.bsPlatform && window.bsPlatform.os === 'android');
    const racing = raceOk && !intro.on && navRun >= 2 && now - lastNav < 140;
    // a camera tween keeps its frames clear: no uploads, spines, covers, cards or shadow changes between its first frame and its last tenth
    const vk = rows[rowI] ? viewKey() : twKey, vkCh = vk !== twKey, twQuiet = (vkCh && twKey !== null && twKey >= -1 && vk >= -1) || (tw.on && !vkCh && tw.s < .9); // (the frame a tween starts on too: a zoom out began with up to 4 ms of uploads)
    uploadSome(intro.on ? 3 : glide || racing ? 1.5 : 4, twQuiet ? 0 : glide ? (shadowRow !== rowI ? 0 : 1) : racing ? 1 : 99);
    const dt = Math.min(.05, lastT ? (now - lastT) / 1000 : .016); lastT = now; stepLight(dt);
    const THREE = T(), r = rows[rowI]; if (!r) { drawFrame(); return; }
    if (!twQuiet) doWork(intro.on ? 3 : glide || racing ? 1.5 : 5); preload(now);
    // shadows: only the lamp over the row in view casts them
    if (shadowRow !== rowI && !twQuiet) { setShadowRow(); shadowDirty = true; }
    // opening sequence: lamps on in order (with a flicker), camera pulls back, then zooms to the row
    let camTarget;
    if (intro.on) {
      intro.t += dt;
      rows.forEach((rw, i) => { const at = .9 + i * .36, k = (intro.t - at) / .7; if (k <= 0) return; if (rw.lampOn === 0) snd('lamp');
        const e = Math.min(1, k), strike = e < .18 ? e / .18 * .55 : e < .3 ? .55 - (e - .18) / .12 * .35 : .2 + (1 - Math.pow(1 - (e - .3) / .7, 3)) * .8; // strike → dip → bloom
        const over = e > .6 && e < 1 ? 1 + .12 * Math.sin((e - .6) / .4 * Math.PI) : 1;
        rw.lampOn = Math.max(rw.lampOn, Math.min(1, e)); rw.lamp.intensity = LAMP() * Math.max(0, strike) * over; });
      camTarget = overview(); // drift in to the overview while the lights come on, and stay on it
      if (intro.t > intro.len) endIntro();
    } else camTarget = tvView && tv ? tvView0() : roomView ? (tv && now - tvPeekAt < (window.__musPeekMs || 2600) ? tvPeekView() : roomOverview()) : wide ? overview() : rowView();
    // a view worked out while the window was minimised (0 × 0: the fit maths divides by it) is NaN, and the camera eases toward
    // its target, so one NaN froze it there for good (a black room after restoring the window): hold still instead
    if (!isFinite(camTarget.x + camTarget.y + camTarget.z)) camTarget = { x: camP.x, y: camP.y, z: camP.z };
    lastTg = camTarget;
    if (vkCh) { const pk = twKey; twKey = vk; if (pk !== null && pk >= -1 && vk >= -1) { twStart(camTarget, pk === -1 ? 1 : vk === -1 ? -1 : 0); if (pk === -1) { snd('slide'); twSndAt = now; } } else tw.on = false; } // zooming in: the whoosh with the first step
    else if (tw.on && vk < -1) tw.on = false;
    const twHold = tw.on && tw.s < .8; // the chosen case, the spotlight and the card wait for the camera's last fifth
    // the room itself brightens with the lamps (environment + bulbs), so the switch-on reads clearly
    const lit = rows.length ? rows.reduce((a, rw) => a + rw.lampOn, 0) / rows.length : 1;
    dimK = sm(dimK, heldCase || libHeld || (fan && !fan.closing) ? 1 : 0, 1 - Math.exp(-dt * 3)); if (!heldCase && !libHeld) { handZoomT = 1; hPan.tx = hPan.ty = 0; } handZoom = sm(handZoom, handZoomT, 1 - Math.exp(-dt * 6)); if (!heldCase) hPan.lx = hPan.ly = 0; hPan.x = sm(hPan.x, hPan.tx + (hPan.lx || 0), 1 - Math.exp(-dt * 5)); hPan.y = sm(hPan.y, hPan.ty + (hPan.ly || 0), 1 - Math.exp(-dt * 5)); // gentle easing: no jitter from a twitchy stick // holding a case: the room falls back, the case stays in its light
    scene.environmentIntensity = (.04 + .18 * lit) * light.env * room.bright * (1 - .5 * dimK);
    if (rack && rack.items.length && (dimK > .001 || rack.dimmed)) { rack.dimmed = dimK > .001; rack.items.forEach(it => { it.front.emissiveIntensity = it === libHeld ? .38 : .3 * (1 - .8 * dimK); if (it.labelM) it.labelM.emissiveIntensity = .22 * (1 - .8 * dimK); }); }
    if (wideFrame || wide) { const f = wideFrameMesh(), on = wide && !intro.on && !roomView && wideSel < wideCount(), zf = tw.on && tw.zoom && !intro.on && !roomView && wideSel < wideCount();
      // zooming in the frame swells outward and fades over the first 40%; zooming out it comes back over the last 40%, drawing in to the shelf
      if (zf) f.userData.k = tw.zoom > 0 ? Math.min(f.userData.k, 1 - ss(tw.s / .4)) : Math.max(f.userData.k, ss((tw.s - .6) / .4)); else f.userData.k = sm(f.userData.k, on ? 1 : 0, 1 - Math.exp(-dt * 8));
      f.userData.mat.opacity = f.userData.k * (.55 + .2 * Math.sin(now / 420)); f.visible = f.userData.k > .01;
      if (zf) { if (f.visible) { const b = wideBox(wideSel), g = (1 - f.userData.k) * .3; fbx.x0 = b.x0 - g; fbx.x1 = b.x1 + g; fbx.y0 = b.y0 - g; fbx.y1 = b.y1 + g; fbx.z = b.z; placeFrame(fbx, null); } } else if (on) placeFrame(wideBox(wideSel), f.userData.k < .05 ? null : 1 - Math.exp(-dt * 14)); }
    if (rack && rack.beams) rack.beams.forEach(b => { b.material.opacity = (light.beam || 0) * .7 * room.bright * (1 - dimK); });
    rows.forEach((rw, i) => {
      // the walk-in: each row's light line draws across left to right with a quick flicker, then the glow fills the back
      const k0 = intro.on ? (intro.t - (.9 + i * .36)) / .7 : 9, draw = Math.max(0, Math.min(1, k0 / .8)), e = 1 - Math.pow(1 - draw, 3);
      const flick = intro.on && draw > 0 && draw < 1 ? (Math.sin(intro.t * 70 + i) > .6 ? .55 : 1) : 1;
      if (rw.strip) { rw.strip.scale.x = Math.max(.0001, rw.stripW * e); const so = Math.min(1, light.strip) * (1 - light.hue); rw.strip.material.opacity = so; rw.strip.visible = so > .01 && draw > 0; }
      if (rw.wash) rw.wash.material.opacity = .16 * light.wash * (1 - light.hue) * Math.max(0, Math.min(1, (k0 - .3) / .9)) * flick * room.bright * (1 - .5 * dimK);
      rw.fill = (intro.on ? Math.max(0, Math.min(1, (k0 - .2) / 1)) : 1) * flick;
    });
    rows.forEach(rw => { if (rw.bulb) rw.bulb.material.emissiveIntensity = LAMP() ? (rw.lamp.intensity / LAMP()) * 2.2 : 0; if (rw.beam) rw.beam.material.opacity = (light.beam || 0) * rw.lampOn * room.bright * (1 - dimK) * 1; if (rw.plaque) rw.plaque.material.emissiveIntensity = .35 * rw.lampOn; });
    // dust drifts in the light
    const dustOn = perf().dust, f60 = dt * 60; dust.forEach(({ pts, seed, row }) => { pts.visible = dustOn; if (!dustOn) return; pts.material.opacity = .55 * row.lampOn * room.dust; if (row.lampOn <= 0 || Math.abs(row.y - camP.y) > 3.2) return; const a = pts.geometry.attributes.position; for (let i = 0; i < a.count; i++) { a.array[i * 3] += Math.sin(now / 1700 + seed[i]) * .0004 * f60; a.array[i * 3 + 1] += (Math.cos(now / 2300 + seed[i] * 1.3) * .0003 - .00006) * f60; /* (per 60 Hz frame, scaled to the real one) */ if (a.array[i * 3 + 1] < -H / 2) a.array[i * 3 + 1] = H * .7; } a.needsUpdate = true; });
    // new games slide into their gap once the lights are on (the picture lamp now follows you, so every other row's new
    // games slide in as soon as the walk-in is over instead of waiting, pulled out, for a lamp that may never come on)
    rows.forEach(rw => rw.boxes.forEach(b => { if (b.userData.slide > 0 && (rw.lampOn >= 1 || !intro.on)) b.userData.slide = Math.max(0, b.userData.slide - dt * .9); }));
    stepWrap(dt, now, r); evenFill();
    // selection: the chosen case slides out first, then turns to face you (and turns back before sliding in), so it never cuts through its neighbours
    const sel = r.boxes[colI] || null, rsel = sel ? null : rackItem();
    // the picture light follows what you're looking at along a long shelf (the console at the end of a 70-game row
    // used to sit outside its cone); the other shelves' lights ease back to their middle. Moving a light is free.
    rows.forEach(rw => { const want = rw === r && !wide ? (sel ? sel.userData.home.x : colI >= rw.boxes.length && !rsel ? rw.consoleX + .4 : rw.lampX0) : rw.lampX0;
      const lx = rw.lamp.position.x; if (Math.abs(lx - want) > .002) { const nx = sm(lx, want, 1 - Math.exp(-dt * 2.5)); rw.lamp.position.x = nx; rw.lamp.target.position.x = nx; shadowDirty = true; } });
    // what's in your hands turns towards where you've turned it (once a frame, here only). Each object then keeps its own copy
    // of the angle (u.hrx/hry, the console its rotation): let go of it any way at all (B, A, Esc, the mouse, the TV, a new
    // pick-up) and it goes back by the short way from where it is, its whole turns dropped (wrap180), instead of being drawn
    // from the shared hold, which was eased to 0 through every turn you'd given it (and fought over by two easings)
    if (heldCase || holding || libHeld) { const kh = 1 - Math.exp(-dt * 6); hold.rx = sm(hold.rx, hold.trx, kh); hold.ry = sm(hold.ry, hold.try, kh); }
    if (rack) for (let i = rack.items.length - 1; i >= 0; i--) { const t = rack.items[i]; if (t.temp && t !== libHeld) dropTemp(t); } // (beta.11) a fan issue put down
    if (rack && rack.items.length) { rackCovers(); rack.items.forEach(it => { const m = it.mesh, u = m.userData, on = it === rsel && !intro.on && !wide && !twHold, held = it === libHeld;
      u.lift = sm(u.lift || 0, held ? 1 : 0, 1 - Math.exp(-dt * 5)); if (Math.abs(u.lift - (held ? 1 : 0)) < 1e-3) u.lift = held ? 1 : 0;
      if (!held && u.hHeld && it.rig) { const rg = it.rig; rg.discT = 0; rg.openT = 0; movieDiscHome(rg); } // let go with it open (the TV, a play): the disc goes in and the case shuts on the way back, not left open on the rack
      if (held || u.hHeld || u.hrx || u.hry) ownTurn(u, held, dt);
      if (u.lift > 0) { const e = ss(u.lift), eo = it.rig ? stepMovieRig(it, dt) : 0, rx = u.hrx || 0, ry = u.hry || 0; m.position.set(u.home.x + MOV_W * .5 * eo * e, u.home.y + .3 * e, u.home.z + (1.7 - .25 * eo) * e); m.rotation.set(-12 * DEG * (1 - e) + rx * DEG * e, ry * DEG * e, 0); u.p = held ? 1 : u.p; moved(m); if (!held && it.rig && u.lift < .02 && !it.rig.openT) { it.rig.open = 0; stepMovieRig(it, 0); } return; }
      if (!on && u.p === 0) return; u.p = sm(u.p, on ? 1 : 0, 1 - Math.exp(-dt * 8)); if (Math.abs(u.p - (on ? 1 : 0)) < 1e-3) u.p = on ? 1 : 0; const e = ss(u.p), pz = it.movie ? .14 : .32; m.position.set(u.home.x, u.home.y + (it.movie ? .025 : .05) * e, u.home.z + pz * e); m.rotation.x = -12 * DEG * (1 - e * (it.movie ? .6 : 1)); moved(m); }); } // a Blu-ray (wider than a magazine) only eases forward, not over its neighbours
    stepFan(dt);
    if (libPending && !libBusy()) setLibrary(libPending); // a library update that waited for the magazine to go back
    // spotlight: when you land on a case the beam glides over to it and brightens with a soft "strike", so the case you're on
    // reads at a glance; kept well under the picture light so covers never wash out, and off while you hold or inspect
    if (spot) {
      const want = heldCase ? heldCase : !delivery && !fan && !intro.on && !wide && !holding && !twHold && (sel || rsel) ? (sel || rsel.mesh) : null;
      if (want !== spotAt) { if (want && spotAt) spotK = Math.min(spotK, .35); if (want && !spotAt && spotK < .05) spot.target.position.set(want.position.x, r.y + want.position.y, want.position.z); spotAt = want; }
      spotK = sm(spotK, want ? 1 : 0, 1 - Math.exp(-dt * (want ? 3.2 : 6)));
      if (want) { const tx = want.position.x, ty = r.y + want.position.y, tz = want.position.z;
        const k = 1 - Math.exp(-dt * 9); spot.target.position.x = sm(spot.target.position.x, tx, k); spot.target.position.y = sm(spot.target.position.y, ty, k); spot.target.position.z = sm(spot.target.position.z, tz, k);
        spot.position.set(spot.target.position.x + .1, spot.target.position.y + .95, spot.target.position.z + 2.1); } // above-front, just wide enough for the whole case: even light on the cover, and its glare reflects downward, never into your view
      const strike = spotK < 1 ? 1 + .25 * Math.sin(Math.PI * Math.min(1, spotK)) : 1; // a small swell as it arrives
      spot.intensity = light.spotI * .6 * (room ? room.spot : 1) * spotK * strike * r.lampOn; /* toned down: covers kept their colour instead of washing out */
      if (!intro.on) rows.forEach(rw => { if (rw.lampOn >= 1) rw.lamp.intensity = (rw === r ? LAMP() * (1 - .45 * spotK) : LAMP()) * (1 - .55 * dimK); }); // the row's picture light eases down a touch so the two don't stack into a hot spot
    }
    const key = rowI + ':' + colI; if (key !== selKey && !twQuiet && !racing) { selKey = key; facc.trim++; trimFronts(r); }
    const want = [];
    rows.forEach((rw) => rw.boxes.forEach((b, i) => {
      const u = b.userData, on = rw === r && b === sel && !holding && !intro.on && !wide;
      if (u.mail) return; // still in the mail: the delivery moves it
      if (!on && u.p === 0 && u.slide === 0 && !u.isNew && !(u.lift > .001) && (rw !== r || Math.abs(i - colI) > 4 || u.frontLoaded)) return; // resting case: nothing to do this frame
      // while you're still scrolling the chosen case only peeks out; it waits for the previous one to turn back before coming right out
      const scrolling = now - lastNav < 260, blocked = on && rw.boxes.some(o => o !== b && o.userData.p > .5);
      // after a change of shelf the case you left slides home a little slower, and the new one waits until the camera has
      // mostly arrived before it comes out (they used to snap in and out together)
      const rowSettling = now - rowNavAt < 300;
      const goal = !on ? 0 : rowSettling || twHold ? 0 : (scrolling || blocked) ? .16 : 1;
      const was = u.p; u.p = sm(u.p, goal, 1 - Math.exp(-dt * (goal > u.p ? (now - rowNavAt < 1100 ? 6 : 7) : now - rowNavAt < 800 ? 9 : 12))); if (u.p < .002 && !on) u.p = 0; if (Math.abs(u.p - goal) < 1e-3) u.p = goal; /* settle exactly, so a resting case stops re-rendering shadows */
      if (on && goal === 1 && was < .5 && u.p >= .5 && now - twSndAt > 1500) snd('slide'); // (not again right after the zoom's own)
      if (!on && was >= .5 && u.p < .5) snd('slideBack'); // the case you leave goes back into its gap
      const slideZ = u.slide > 0 ? (1 - Math.pow(1 - u.slide, 2)) * 1.1 : 0;
      const out = ss(Math.min(1, u.p / .62)), face = ss((u.p - .55) / .45);
      const lift = b === heldCase ? 1 : 0; u.lift = sm(u.lift || 0, lift, 1 - Math.exp(-dt * 6)); if (Math.abs(u.lift - lift) < 1e-4) u.lift = lift;
      if (b === heldCase || u.hHeld || u.hrx || u.hry) ownTurn(u, b === heldCase, dt);
      b.position.z = u.home.z + out * (rw.w * .62 + rw.d) + slideZ + u.lift * .9; b.position.y = u.home.y + out * .06 + u.lift * .16;
      b.rotation.set(u.lift * (u.hrx || 0) * DEG, -90 * DEG * face + u.lift * (u.hry || 0) * DEG, 0);
      // holding A to unwrap: the case shakes in your hands, harder as the bar fills, like tearing at the plastic
      if (b === heldCase && u.sleeve && u.pull > 0) { const p = u.pull, t = now / 1000; b.rotation.z += Math.sin(t * 38) * .045 * p + Math.sin(t * 61) * .02 * p; b.rotation.x += Math.sin(t * 47 + 1) * .03 * p; b.rotation.y += Math.sin(t * 53) * .03 * p; b.position.y += Math.abs(Math.sin(t * 29)) * .012 * p; }
      const near = rw === r && Math.abs(i - colI) <= 4;
      if ((on || near) && !u.frontLoaded) want.push(b); // covers are made/loaded only for cases near the selection
      if (u.isNew) { const pulse = .5 + .5 * Math.sin(now / 320 + i); u.front.emissive = u.front.emissive || new THREE.Color(); u.front.emissive.setRGB(1 * pulse, .8 * pulse, .3 * pulse); u.front.emissiveIntensity = .22; }
    }));
    if (want.length && !twQuiet && !racing) want.sort((a, b) => Math.abs(a.userData.i - colI) - Math.abs(b.userData.i - colI)).slice(0, 2).forEach(loadFront);
    // in your hands: the lid swings on its hinge once the case has come up to you; the disc rises out of the tray and
    // turns slowly; once the case is closed and back on the shelf the real case returns
    if (peek) {
      if (peek.box !== heldCase && (peek.t = 0, peek.discT = 0, true) && peek.k === 0) endPeek();
      else {
        if (peek.box.material !== HIDE) { peek.box.userData.realMat = peek.box.material; peek.keep = peek.box.material; peek.box.material = HIDE; }
        const want = peek.box === heldCase && heldReady() ? peek.t : 0; if (!want) peek.discT = 0; /* closing: the disc / cartridge goes home first, the lid only shuts once it's back in the tray (it used to shut with the cart still outside, floating over the cover) */ const tgt = want || peek.disc > .02 ? 1 : 0; peek.k = sm(peek.k, tgt, 1 - Math.exp(-dt * (tgt ? 4.2 : 6))); if (Math.abs(peek.k - tgt) < 1e-3) peek.k = tgt;
        peek.pivot.rotation.y = -168 * DEG * ss(peek.k); moved(peek.pivot);
        const dT = peek.discT && peek.k > .95 ? 1 : 0; peek.disc = sm(peek.disc, dT, 1 - Math.exp(-dt * (dT ? 3.6 : 9))); if (Math.abs(peek.disc - dT) < 1e-3) peek.disc = dT;
        const e = ss(peek.disc); peek.media.position.set(peek.faceX + e * .3, (peek.my || 0) * (1 - e), 0); peek.media.scale.setScalar(1 + .55 * e);
        // disc out: the case slides away to the side and back, out of view, and the disc alone sits in your hands
        if (peek.L) { peek.L.position.set(-e * .5, -e * .25, -e * peek.w * 2.4); peek.L.visible = e < .985; moved(peek.L); }
        const idle = performance.now() - lastInput > 1500; peek.spinV = sm(peek.spinV || 0, peek.disc > .95 && idle ? .35 : 0, 1 - Math.exp(-dt * 2));
        if (peek.discT) peek.spinner.rotation.x += dt * peek.spinV; else { peek.spinV = 0; peek.spinner.rotation.x = Math.abs(peek.spinner.rotation.x) < 1e-3 ? 0 : sm(peek.spinner.rotation.x, 0, 1 - Math.exp(-dt * 7)); }
        { const kd = 1 - Math.exp(-dt * (peek.discT ? 7 : 14)), tY = peek.discT ? peek.dry || 0 : 0, tX = peek.discT ? peek.drx || 0 : 0; peek.cry = sm(peek.cry || 0, tY, kd); peek.crx = sm(peek.crx || 0, tX, kd); if (Math.abs(peek.cry - tY) < .01) peek.cry = tY; if (Math.abs(peek.crx - tX) < .01) peek.crx = tX; const f = peek.discT ? 1 : e; /* going home it's square again by the time it reaches the tray */ peek.media.rotation.set(peek.crx * f * DEG, peek.cry * f * DEG, 0); }
        moved(peek.media); moved(peek.spinner);
        if (!peek.t && peek.k === 0 && peek.box !== heldCase) endPeek();
      }
    }
    frameReader(dt);
    // the card under the pulled case
    if (card) {
      if (sel && !holding && !heldCase && !intro.on && !wide) {
        // drawn only once the case is mostly out (not for every case you scroll past); until then the last card fades out quickly
        const u = sel.userData, fm = card.userData.face.material; if (card.userData.for !== sel && u.p > .5 && !twQuiet) { card.userData.for = sel; facc.card++; const nt = cardTex(u.g, r, fm.map); if (fm.map !== nt) { if (fm.map) fm.map.dispose(); fm.map = nt; fm.needsUpdate = true; } }
        const mine = card.userData.for === sel; card.userData.fade = sm(card.userData.fade, mine ? ss((u.p - .7) / .3) : 0, 1 - Math.exp(-dt * (mine ? 10 : 25))); card.visible = card.userData.fade > .01; fm.opacity = card.userData.board.material.opacity = card.userData.fade; card.scale.setScalar(CARD_S);
        // beside the case (to its right), leaning back on the plank, so the cover is never covered
        // beside the case: on the right unless that runs into the bookend / console, then on the left
        const right = sel.position.x + r.w / 2 + .52 * CARD_S, left = sel.position.x - r.w / 2 - .52 * CARD_S;
        if (right + .47 * CARD_S < r.consoleX - r.consoleW / 2 - .2) { card.position.set(right, r.y - H / 2 + .25 * CARD_S + .01, Math.max(sel.position.z, (DEPTH + .1) / 2 - .02)); card.rotation.set(-4 * DEG, 0, 0); } // standing beside the case, in front of the other spines and clear of the plank
        else if (left - .47 * CARD_S > -.25) { card.position.set(left, r.y - H / 2 + .25 * CARD_S + .01, Math.max(sel.position.z, (DEPTH + .1) / 2 - .02)); card.rotation.set(-4 * DEG, 0, 0); }
        else { card.position.set(right, r.y - H / 2 + .25 * CARD_S + .01, (DEPTH + .1) / 2 + .2); card.rotation.set(-4 * DEG, 0, 0); } // no clear space: still beside the case, stood forward in front of the console (it used to hang under the shelf, where the row below covered it)
      } else { card.userData.fade = sm(card.userData.fade, 0, 1 - Math.exp(-dt * 10)); card.userData.face.material.opacity = card.userData.board.material.opacity = card.userData.fade; if (card.userData.fade < .02) card.visible = false; }
    }
    // console in hand
    rows.forEach(rw => { const cg = rw.consoleG; if (!cg) return; const held = holding === rw;
      const lift = held ? .45 : 0; cg.position.y = sm(cg.position.y, -H / 2 + .001 + lift, 1 - Math.exp(-dt * 6)); cg.position.z = sm(cg.position.z, held ? .9 : 0, 1 - Math.exp(-dt * 6));
      if (held) { cg.rotation.set(hold.rx * DEG, hold.ry * DEG, 0); cg.userData.hHeld = true; } else if (cg.userData.hHeld) { cg.userData.hHeld = false; cg.rotation.y = Math.atan2(Math.sin(cg.rotation.y), Math.cos(cg.rotation.y)); } /* put down: whole turns dropped, it settles the short way */
      if (!held && (cg.rotation.x || cg.rotation.y)) { cg.rotation.x = sm(cg.rotation.x, 0, 1 - Math.exp(-dt * 6)); cg.rotation.y = sm(cg.rotation.y, 0, 1 - Math.exp(-dt * 6)); if (Math.abs(cg.rotation.x) + Math.abs(cg.rotation.y) < .002) cg.rotation.set(0, 0, 0); } moved(cg); });
    // camera, with a slow drift when you leave it alone
    const idle = !intro.on && now - lastInput > 3000 && !reducedMo(), drift = idle ? Math.min(1, (now - lastInput - 3000) / 2500) : 0;
    // changing shelf: the camera eases in (slow start, softer glide) instead of leaping off at full speed
    const rowAge = (now - rowNavAt) / 1000, rowEase = rowAge < .9 ? Math.min(1, .5 + rowAge / .22 * .5) * .86 : 1;
    const k = 1 - Math.exp(-dt * (intro.on ? (intro.t < intro.len - 1.3 ? 1.1 : 3.2) : 3.2 * rowEase));
    const px = camP.x, py = camP.y, pz = camP.z;
    if (snapCam) { snapCam = false; tw.on = false; camP.x = camTarget.x; camP.y = camTarget.y; camP.z = camTarget.z; }
    if (tw.on) twStep(dt, camTarget); else { camP.x = sm(camP.x, camTarget.x, k); camP.y = sm(camP.y, camTarget.y, k); camP.z = sm(camP.z, camTarget.z, k); }
    camV.x = (camP.x - px) / dt; camV.y = (camP.y - py) / dt; camV.z = (camP.z - pz) / dt;
    // (beta.8) the drift eases back out when you touch the controls (it used to vanish in one frame: a jump sideways)
    driftK = drift >= driftK ? drift : sm(driftK, drift, 1 - Math.exp(-dt * 4));
    const dx = driftK * Math.sin(now / 4200) * .18, dy = driftK * Math.sin(now / 5600) * .06, dz = driftK * Math.sin(now / 7000) * .25;
    // a case in your hands: the camera faces it straight on, at eye level, wherever it is (the shelf view looks down at the wall)
    holdLook = sm(holdLook, heldCase ? 1 : 0, 1 - Math.exp(-dt * 4));
    camera.position.set(camP.x + dx * (1 - holdLook), camP.y + .2 * (1 - holdLook) + dy * (1 - holdLook), camP.z + dz * (1 - holdLook));
    camera.lookAt(camP.x + dx * .4 * (1 - holdLook), (camP.y - .05) * (1 - holdLook) + camP.y * holdLook, holdLook > 0.001 ? camP.z - 4 * holdLook - (1 - holdLook) * camP.z : 0);
    if (delivery) stepDelivery(dt, now);
    if (tv) stepTV(dt, now);
    // shadows only re-render when a case in the lit row actually moved (the selected case alone used to force it every frame)
    if (r) for (const b of r.boxes) { const sk = b.position.x + b.position.y * 7 + b.position.z * 13 + b.rotation.x * 17 + b.rotation.y * 19 + b.rotation.z * 23; if (Math.abs(sk - (b.userData._sk || 0)) > 1e-4) { b.userData._sk = sk; shadowDirty = true; } }
    // while the camera glides to a new shelf the moving lamp and cases would redraw the shadows every frame: every third is plenty
    if (shadowDirty && (fst.row || (!twQuiet && (!(settling || racing) || ++shadowSkip % 3 === 0)))) { renderer.shadowMap.needsUpdate = true; shadowDirty = false; fst.sh = 1; }
    // the fill light rides with the camera, aimed where it looks
    if (fillL) { fillL.intensity = (light.fill || .25) * (.7 + .3 * room.bright); fillL.position.copy(camera.position); const f = sv('fillDir'); camera.getWorldDirection(f); fillL.target.position.copy(camera.position).addScaledVector(f, 4); }
    drawFrame(); const fms = performance.now() - fT0; fst.jsMs = Math.round(fms); maybeSleep(now);
    facc.n++; facc.js += fms; if (fms > facc.jsMax) { facc.jsMax = fms; facc.worst = Object.assign({ sinceStep: Math.round(now - lastNav), racing, twQuiet }, fst); } facc.up += fst.up; facc.fr += fst.fr; if (racing) { facc.rn++; facc.rFr += fst.fr; if (fst.up > facc.rUpMax) facc.rUpMax = fst.up; } facc.sp += fst.sp; facc.sh += fst.sh; facc.row += fst.row;
  }
  let hitchAt = 0, hitchN = 0, shadowSkip = 0;
  // steps that came less than 200 ms apart, one after another (a held stick or d-pad racing along a shelf)
  let navRun = 0; let raceOk = null; // (Android or not: asked on first use, since museum.js loads before platform.js)
  // Android: the row lamps don't light the room themselves (a spot light is paid for by every pixel of every lit surface,
  // for every row); three spot lights follow the row in view and the rows either side of it, copying those lamps each
  // frame. Always three, lit or not, so the shaders never change. Shadows are off on the handheld level anyway.
  // (0.3.1) the rows outside the three were darker than with a lamp each: the room's ambient light is lifted on the
  // handheld (applyAtmos, AMB_LIFT) and the three lamps a little too
  let lampPool = null, poolOk = null; const POOL_LIFT = 1.15;
  function lampPooled() { if (poolOk === null) poolOk = !!(window.bsPlatform && window.bsPlatform.os === 'android'); return poolOk; }
  function poolLamps() {
    if (!lampPool || lampPool[0].parent !== wall) { const THREE = T(); lampPool = [0, 1, 2].map(() => { const l = new THREE.SpotLight(0xffe0b0, 0, 7.5, 58 * DEG, .75, 1.1); l.castShadow = false; wall.add(l, l.target); return l; }); }
    const mid = Math.max(1, Math.min(rows.length - 2, rowI));
    lampPool.forEach((l, k) => { const rw = rows[mid - 1 + k]; if (!rw || !rw.lamp || !rw.group) { l.intensity = 0; return; } const s = rw.lamp, y = rw.group.position.y;
      l.position.set(s.position.x, s.position.y + y, s.position.z); l.target.position.set(s.target.position.x, s.target.position.y + y, s.target.position.z);
      l.intensity = s.visible === false ? 0 : s.intensity * POOL_LIFT; l.color.copy(s.color); l.angle = s.angle; l.penumbra = s.penumbra; l.distance = s.distance; l.decay = s.decay; });
  }
  function drawFrame() { if (window.__musNoDraw) return; if (lampPooled() && wall) poolLamps(); renderer.render(scene, camera); } // (__musNoDraw: profiling in a software-GL browser times the frame's own work without the draw)
  // A case box's six faces as four draws instead of six: the cover (+x), the back (-x, where the back-cover art goes
  // when you pick a case up), the top, bottom and far end together, and the spine (+z). The box's faces come in the order
  // +x, -x, +y, -y, +z, -z; the rounded box is non-indexed, so an index listing its vertices in the new order is added.
  // a Blu-ray / TV case: the four edges (one material) drawn together, then the cover, then the back
  const MOV_GROUPS = [[0, 1, 2, 3], [4], [5]], MOV_FRONT = 1;
  function caseGroups(geo, order = [[0], [1], [2, 3, 5], [4]]) {
    const gs = geo.groups; if (gs.length !== 6) return geo;
    const n = geo.index ? geo.index.count : geo.attributes.position.count, src = geo.index ? geo.index.array : null;
    const out = new (n > 65535 ? Uint32Array : Uint16Array)(n); let o = 0;
    const put = g => { for (let k = g.start; k < g.start + g.count; k++) out[o++] = src ? src[k] : k; };
    const ranges = order.map(list => { const a = o; list.forEach(i => put(gs[i])); return [a, o - a]; });
    if (o !== n) return geo; // groups didn't cover every vertex: leave it as it was
    geo.setIndex(new (T().BufferAttribute)(out, 1)); geo.clearGroups(); ranges.forEach(([a, c], mi) => geo.addGroup(a, c, mi)); return geo;
  }
  // Automatic level only: frames slower than ~45 fps for a second step the render scale down (to 60% at most), frames with
  // room to spare for a few seconds step it back up. A softer picture instead of a stutter on weaker graphics.
  function dynRes(now) {
    if (!lastT || intro.on || document.hidden) return; const dt = now - lastT; if (dt > 1500) return; frameEma = frameEma ? frameEma * .9 + dt * .1 : dt;
    if (!perf().auto) { if (dynScale !== 1) { dynScale = 1; dynCeil = 1; resize(); } return; }
    // a scale that ran slow isn't tried again for 30 s (a 60 Hz screen jumping between 60 and 30 fps would otherwise
    // sharpen and soften every few seconds)
    if (dynCeil < 1 && now - dynCeilAt > 30000) dynCeil = Math.min(1, +(dynCeil + .1).toFixed(2));
    if (frameEma > 22) { dynFast = 0; if ((dynSlow += dt) > 1000 && dynScale > .6) { dynWant = -1; dynSlow = 0; } }
    else if (frameEma < 17.5) { dynSlow = 0; if ((dynFast += dt) > 3000 && dynScale + .1 < dynCeil + 1e-6) { dynWant = 1; dynFast = 0; } }
    else { dynSlow = Math.max(0, dynSlow - dt); dynFast = 0; }
    // the resize itself (a new drawing surface: a hitch of its own) waits on Android until the view has come to rest
    if (dynHold === null) dynHold = !!(window.bsPlatform && window.bsPlatform.os === 'android');
    if (!dynWant || (dynHold && (now - lastNav < 600 || tw.on || Math.abs(camV.x) + Math.abs(camV.y) + Math.abs(camV.z) > .05))) return;
    if (dynWant < 0) { dynCeil = dynScale; dynCeilAt = now; dynScale = Math.max(.6, +(dynScale - .1).toFixed(2)); if (dynLogN++ < 8) hooks.log && hooks.log('museum: render scale ' + dynScale + ' (frames ' + Math.round(frameEma) + ' ms)'); }
    else dynScale = Math.min(1, +(dynScale + .1).toFixed(2));
    dynWant = 0; resize();
  }
  // a new performance level (Settings): scale, shadows and dust follow at once; anti-aliasing from the next start
  addEventListener('bs-perf', () => { if (!renderer) return; dynScale = 1; dynCeil = 1; dynWant = 0;
    // anti-aliasing is fixed when the drawing surface is made: a change starts a fresh one (now, or when you next leave the museum)
    if (rendererAA !== perf().aa) { if (!active) { teardown(); return; } rebuildGL = true; }
    if (active) resize(); /* closed: open() sizes it (a warmed-up renderer stays 8×8 on the shelf instead of a full-screen buffer) */ const on = wantShadows(); if (renderer.shadowMap.enabled !== on) { renderer.shadowMap.enabled = on; renderer.shadowMap.needsUpdate = true; rows.forEach(r => { r.lamp.castShadow = false; }); shadowRow = -1; }
    // the shadow map's size follows too (Low's 512 px map stayed after a switch to High until the museum was rebuilt)
    const sz = perf().shadowSize || 1024; rows.forEach(r => { const sh = r.lamp.shadow; if (sh.mapSize.x === sz) return; sh.mapSize.set(sz, sz); if (sh.map) { sh.map.dispose(); sh.map = null; } shadowRow = -1; shadowDirty = true; }); });
  addEventListener('resize', () => { if (renderer && active) resize(); });
  document.addEventListener('visibilitychange', () => { lastT = 0; }); // back from hidden: not a hitch
  // numbers for tuning (draw calls, lights, triangles, render scale)
  // (glStats, not stats: a second function named stats replaced the plaque one above for the whole museum, so every
  // plaque read "nothing played yet")
  // ---- a second screen (the AYN Thor's bottom screen, Android build only: nothing else calls these) ----
  // The row you're on as plain data, which case is chosen on it, and a way to choose or unwrap one by touch. Choosing
  // and unwrapping go through the same paths as the controller, and only when the wall is in its plain "looking along a
  // row" state (not stepped back, not holding anything, no TV / mail / magazine box open), so a touch can't tangle them.
  const touchFree = () => active && !!rows[rowI] && !wide && !fan && !tvView && !delivery && !holding && !heldCase && !libHeld;
  // stepped back to see the whole wall (as it is after the walk-in), the row under the frame is the one shown
  const wideRow = () => wide && !fan && !tvView && !delivery && wideSel < rows.length ? wideSel : -1;
  function bottomRow() { const wr = wideRow(), ri = wr >= 0 ? wr : rowI, r = rows[ri]; if (!active || !r) return null;
    return { row: ri, rows: rows.length, id: r.id, name: r.name, sel: (touchFree() || (heldCase && wr < 0 && r.boxes[colI] === heldCase)) && colI < r.boxes.length ? colI : -1, held: !!heldCase, /* the case in your hands counts too: the bottom screen can unwrap or play it */ wide: wr >= 0,
      boxes: r.boxes.map(b => ({ g: b.userData.g, sealed: !!b.userData.sleeve })) }; }
  function pickCol(i) {
    if (intro.on) skipFill(); // (which steps back to the whole wall)
    const wr = wideRow();
    if (wr >= 0 && !holding && !heldCase && !libHeld) { wide = false; zoomAt = performance.now(); rowI = wr; } // from the whole wall: walk up to that row, as A would
    else if (!touchFree()) return false;
    const r = rows[rowI]; if (!r || !r.boxes.length) return false; i = Math.max(0, Math.min(r.boxes.length - 1, i | 0));
    lastInput = lastNav = woke(); colI = i; hooks.onSelect && hooks.onSelect(current()); return true; }
  // Which case is under a finger (screen point in CSS pixels), for a tap on the top screen (Android's touch layer). A ray
  // from the camera against the cases, only when a tap lands (never while a finger moves): { row, col, sel: it's the
  // chosen one, held: it's the one in your hands } or null for bare wall / nothing open.
  let ray = null;
  function pickAt(x, y) {
    if (!active || !camera || !canvas || !scene) return null; const THREE = T(), rc = canvas.getBoundingClientRect(); if (!rc.width || !rc.height) return null;
    ray = ray || new THREE.Raycaster(); scene.updateMatrixWorld(); camera.updateMatrixWorld(); // (fresh even when nothing has drawn yet)
    const list = [], at = new Map(); rows.forEach((r, ri) => r.boxes.forEach((b, ci) => { if (b.visible) { list.push(b); at.set(b, [ri, ci]); } }));
    if (heldCase && !at.has(heldCase)) { list.push(heldCase); at.set(heldCase, [rowI, colI]); }
    // a magazine / Blu-ray in your hands is a target too: a tap on it is not a tap on bare wall (which puts it back)
    const lh = libHeld && libHeld.mesh; if (lh && lh.parent && !at.has(lh)) { list.push(lh); at.set(lh, [rowI, colI]); }
    let hit = null; // a fingertip is wide: a near miss tries a little to each side (a spine on the wall is a thin target)
    for (const [ox, oy] of [[0, 0], [-14, 0], [14, 0], [0, -14], [0, 14]]) {
      ray.setFromCamera(new THREE.Vector2((x + ox - rc.left) / rc.width * 2 - 1, -((y + oy - rc.top) / rc.height) * 2 + 1), camera);
      if ((hit = ray.intersectObjects(list, true)[0])) break; }
    if (!hit) return null;
    let o = hit.object; while (o && !at.has(o)) o = o.parent; if (!o) return null; const [row, col] = at.get(o), wr = wideRow();
    return { row, col, sel: wr < 0 && row === rowI && col === colI, held: o === heldCase || !!(lh && o === lh) }; }
  function unwrapCol(i) { const r = rows[rowI]; if (!active || !r) return false;
    if (heldCase) { if (r.boxes[i] !== heldCase) return false; const ok = unwrapHeld(); if (ok) hooks.onSelect && hooks.onSelect(current()); return ok; }
    if (!touchFree() || i !== colI) return false; const b = r.boxes[i]; if (!b || !b.userData.sleeve) return false;
    lastInput = woke(); startUnwrap(b); snd('unlock'); endShim(); const m = shimMesh(b); b.add(m); shim = { box: b, mesh: m, t: 0, fast: true }; burst(b);
    hooks.onSelect && hooks.onSelect(current()); return true; }
  function glStats() { if (!renderer) return null; const i = renderer.info; let lights = 0, shadowL = 0; scene && scene.traverse(o => { if (o.isLight && o.visible && o.intensity > 0) { lights++; if (o.castShadow) shadowL++; } }); return { calls: i.render.calls, tris: i.render.triangles, geos: i.memory.geometries, texs: i.memory.textures, progs: (i.programs || []).length, progNames: (i.programs || []).map(p => p.name + '#' + p.id), lights, shadowL, pr: renderer.getPixelRatio(), aa: !!(renderer.getContext().getContextAttributes() || {}).antialias, shadows: renderer.shadowMap.enabled }; }
  return { _perf: reset => { const o = Object.assign({}, facc), r = rows[rowI]; o.pending = r ? r.boxes.filter((b, i) => Math.abs(i - colI) <= 4 && !b.userData.mail && !b.userData.frontLoaded).length : -1; if (reset) Object.keys(facc).forEach(k => facc[k] = 0); return o; }, stats: glStats, tvShow, tvCard, tvStatic, tvOff, tvVolume, watchTv, tvMeta: () => tv && tv.power ? tv.meta : null, tvPower: on => { tvPower(!!on); hooks.onSelect && hooks.onSelect(current()); }, tvPowered: () => !!(tv && tv.power), tvAvail: () => !!tv, showTv, _tv: () => tv && { mode: tv.mode, power: tv.power, pow: tv.pow, gain: tv.gain, gt: tv.gT, base: tv.base, vol: tv.video ? tv.video.volume : null, box: [tv.box.min.x, tv.box.max.x, tv.box.min.y, tv.box.max.y], card: tv.card && tv.card.title, crt: tvM && tvM.U.uCrt.value, prog: tv.screen.material === (tvM && tvM.screenV) ? 'video' : 'still' }, lightFade: () => lightK, _dv: () => delivery && { rot: delivery.rot, shown: delivery.shown, ot: delivery.open && delivery.open.t }, skipDelivery, setHoldA: v => { holdA = !!v; if (holdA) nudge(); }, resealAll, opened: () => unwrapped.size, preloadModel, _snapCam: () => { snapCam = true; }, _cam: () => ({ x: camP.x, y: camP.y, z: camP.z, tx: lastTg ? lastTg.x : 0, ty: lastTg ? lastTg.y : 0, tz: lastTg ? lastTg.z : 0, on: tw.on, s: tw.s, t: tw.t, d: tw.d, zoom: tw.zoom, key: twKey, pre: preOn ? preRow : -1, fk: wideFrame ? wideFrame.userData.k : 0, up: fst.up, fr: fst.fr, sp: fst.sp, sh: fst.sh, row: fst.row, js: fst.jsMs, upQ: upQ.length }), setRoomView: on => { roomView = !!on; lastInput = woke(); }, setGuideView: on => { guideView = !!on; lastInput = woke(); }, setLibrary, consoleFit, setConsoleFit, toConsole, setRoom, room: () => Object.assign({}, room), handX, handY, handState, wrapPull, unwrapHeld, zoomHand, panHand, leanHand, turnPage, readerZoom, readerPan, lights: () => Object.entries(LIGHTS).map(([id, l]) => [id, l.name]), setLighting, lighting: () => lightId, _rows: () => rows, _rack: () => rack && rack.items, available, open, close, warm, _lights: () => { let n = 0, spots = 0; if (scene) scene.traverseVisible(o => { if (o.isLight) { n++; if (o.isSpotLight) spots++; } }); return { n, spots }; }, setPaused: (p, why = 'x') => { if (p) pauseWhy.add(why); else pauseWhy.delete(why); paused = pauseWhy.size > 0; if (!paused && active && !raf) { lastT = 0; raf = requestAnimationFrame(frame); } }, wake: () => { if (window.bsPlatform && window.bsPlatform.os === 'android') lastInput = woke(); } /* a change from outside (the bottom screen, the Room menu) counts as input: woken, and not straight back to sleep */, sleeping: () => pauseWhy.has('idle') /* (the reader and the dimmed screen each hold their own pause: waking from a dim mustn't unpause under an open magazine) */, setShadows: on => { window.__musNoShadows = !on; if (renderer) { renderer.shadowMap.enabled = wantShadows(); renderer.shadowMap.needsUpdate = true; rows.forEach(r => { r.lamp.castShadow = false; }); shadowRow = -1; } }, move, jump, activate, back, bottomRow, pickCol, pickAt, unwrapCol, refreshArt, turn, release, skipFill, current, fanLift /* (beta.11) Y in a file box's fan */, isActive: () => active, isHolding: () => !!holding || !!heldCase || !!libHeld, isHoldingCase: () => !!heldCase, isFilling: () => intro.on, isWide: () => wide, setRow: i => { rowI = Math.max(0, i | 0); colI = 0; } };
})();
