/* Your own 3D console for the insert animation.
   Drop a model at themes\<system>\console.glb and it replaces the built-in cube:
   it rises in, its lid (if the model has one) swings open, the disc lands on top, the lid closes.
   Optional themes\<system>\console.json adjusts it:
   { "yaw": 0, "pitch": 0, "roll": 0, "scale": 1, "lift": 0,
     "lid": "lid", "lidHinge": "back", "lidAxis": "x", "lidAngle": -70,
     "discX": 0, "discZ": 0, "discHeight": 0, "discScale": 1, "exposure": 1 } */
window.ConsoleModel = (() => {
  const T = () => window.BS3D && window.BS3D.THREE;
  let renderer, scene, camera, canvas, root, lidPivot, disc, discMat, lidAngle = -70, discR = .25, topY = .5, discX = 0, discZ = 0;
  let slot = 'top', slotY = 0, slotZ0 = 0, slotZ1 = 0; // 'front': the disc goes in upright through a slot in the front face (Wii, PS2, PS4, 360)
  let loaded = null, loadedUrl = null, loading = null, raf = 0, startAt = 0, lastAt = 0, playTok = 0, mode = null, lastError = null, exposure = 1;
  // the last few models stay built (geometry, textures, compiled shaders) and are swapped in and out of the scene, so
  // moving between two shelves that both have a custom console doesn't reload and recompile it every time
  const KEEP = 3, cache = new Map(); // url -> { key, holder, lidPivot, disc, discMat, state }, oldest first
  const SIZE = () => Math.round(Math.min(innerHeight * .62, innerWidth * .6));

  function ensure() {
    if (renderer) return true;
    const THREE = T(); if (!THREE) return false;
    canvas = document.createElement('canvas'); canvas.className = 'ins-model';
    try { renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true, powerPreference: 'high-performance' }); }
    catch (e) { lastError = 'WebGL is not available'; return false; }
    renderer.outputColorSpace = THREE.SRGBColorSpace; renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = 1;
    scene = new THREE.Scene();
    const pm = new THREE.PMREMGenerator(renderer); scene.environment = pm.fromScene(new window.BS3D.RoomEnvironment(), .04).texture; pm.dispose();
    camera = new THREE.PerspectiveCamera(28, 1, .05, 50); camera.position.set(0, 1.15, 3.5); camera.lookAt(0, .18, 0);
    scene.environmentIntensity = .55;
    const key = new THREE.DirectionalLight(0xffffff, 1.3); key.position.set(2.5, 4, 3); scene.add(key);
    const fill = new THREE.DirectionalLight(0x9fb4ff, .35); fill.position.set(-3, 1.5, 1); scene.add(fill);
    root = new THREE.Group(); scene.add(root);
    const stage = document.querySelector('#insert .ins-stage'); if (stage) stage.appendChild(canvas);
    canvas.addEventListener('webglcontextlost', e => { e.preventDefault(); lost = true; });
    return true;
  }
  let lost = false;
  function teardown() {
    // (beta.10) the cached models' geometry / textures and the room light are freed too, and the context is let go (like Inspect's)
    // when it isn't already lost: browsers cap live WebGL contexts, so a dropped one used to linger until the page went
    try { cache.forEach(e => { kill(e.holder); kill(e.lidPivot); kill(e.disc); }); } catch {}
    try { if (scene && scene.environment) scene.environment.dispose(); } catch {}
    try { cancelAnimationFrame(raf); raf = 0; if (renderer) { const ctxLost = lost || renderer.getContext().isContextLost(); renderer.dispose(); if (!ctxLost && renderer.forceContextLoss) renderer.forceContextLoss(); } } catch {}
    try { canvas && canvas.remove(); } catch {}
    renderer = scene = camera = canvas = root = lidPivot = disc = discMat = null; loaded = null; loadedUrl = null; loading = null; lost = false; cache.clear(); ++loadSeq; mode = null;
  }
  function gone() { return lost || (renderer && renderer.getContext().isContextLost()); }
  // the cap is per 1080p-sized pixel like the museum's and Inspect's (BS_AUTOZOOM: the page is zoomed 2× on a 4K TV at 100 %
  // scaling), so a 4K TV gets the same sharpness, not three quarters of it; set on every resize so a zoom change is picked up
  function resize() { const s = SIZE(); renderer.setPixelRatio(Math.min(devicePixelRatio || 1, 1.5 * (window.BS_AUTOZOOM || 1))); renderer.setSize(s, s, false); canvas.style.width = canvas.style.height = s + 'px'; camera.aspect = 1; camera.updateProjectionMatrix(); }

  // Load (or reuse) the model. Resolves true when ready, false (with .error()) when it can't be used, null when a newer
  // load (or swap) took over before it finished (superseded: not an error, nothing to tell anyone)
  let loadSeq = 0;
  function load(url, opts) {
    if (!url) { ++loadSeq; loading = null; detach(); return Promise.resolve(false); }
    if (gone()) teardown();
    const key = JSON.stringify(opts || {});
    // (beta.10) already shown / built: a load still downloading for another console is superseded, so it can't land on top later
    if (url === loadedUrl && loaded && cache.has(url) && cache.get(url).key === key) { if (loading) { ++loadSeq; loading = null; } return Promise.resolve(true); }
    if (cache.has(url) && cache.get(url).key === key && renderer) { ++loadSeq; loading = null; detach(); attach(url); return Promise.resolve(true); } // built earlier: just swap it back in
    if (loading && loading.url === url) return loading.p;
    const ticket = ++loadSeq;
    const p = (async () => {
      if (!ensure()) return false;
      const THREE = T();
      try {
        const gltf = await new window.BS3D.GLTFLoader().loadAsync(url);
        // a newer load started while this one was downloading (quick shelf switches): drop this one
        if (ticket !== loadSeq) { kill(gltf.scene); return null; }
        const o = Object.assign({ yaw: 0, pitch: 0, roll: 0, scale: 1, lift: 0, lid: '', lidHinge: 'back', lidAxis: 'x', lidAngle: -70, discX: 0, discZ: 0, discHeight: 0, discScale: 1, exposure: 1, slot: 'top', hide: 'controller|gamepad|joystick|cable|memory.?card|wire' }, opts || {});
        if (o.error) { detach(); drop(url); kill(gltf.scene); throw new Error(o.error); }
        const m = gltf.scene;
        // old Sketchfab materials, then only the console itself (a bundled controller, disc or cable is split off by
        // ConsoleFit; parts you switched on for the museum show here too, the controller never does)
        await ConsoleFit.fixMaterials(gltf);
        // (beta.10) checked again after the last await, and only then the shown model comes off: a newer load could have finished meanwhile (two consoles on the stage)
        if (ticket !== loadSeq || !renderer || !root) { kill(m); return null; }
        detach(); drop(url); // the previous model stays cached (off the scene); an older build of this url (other options) goes
        { const fa = ConsoleFit.analyze(m, o.hide); ConsoleFit.show(m, fa, o, 'console'); }
        m.traverse(n => { if (n.isMesh) { n.frustumCulled = false; } });
        m.rotation.set(THREE.MathUtils.degToRad(o.pitch), THREE.MathUtils.degToRad(o.yaw), THREE.MathUtils.degToRad(o.roll));
        m.updateMatrixWorld(true);
        // normalise: centre on the floor, longest side = 1
        const bboxOf = obj => { const b = new THREE.Box3(); obj.updateMatrixWorld(true); obj.traverse(n => { if (n.isMesh && n.visible && visibleUp(n)) { if (!n.geometry.boundingBox) n.geometry.computeBoundingBox(); const bb = n.geometry.boundingBox.clone().applyMatrix4(n.matrixWorld); b.union(bb); } }); return b; };
        const visibleUp = n => { for (let q = n; q; q = q.parent) if (q.visible === false) return false; return true; };
        let box = bboxOf(m); let size = box.getSize(new THREE.Vector3()); const k = (1 / Math.max(size.x, size.y, size.z)) * o.scale;
        const holder = new THREE.Group(); holder.add(m); m.scale.setScalar(k); m.updateMatrixWorld(true);
        box = bboxOf(m); size = box.getSize(new THREE.Vector3()); const c = box.getCenter(new THREE.Vector3());
        m.position.set(-c.x, -box.min.y + o.lift, -c.z); m.updateMatrixWorld(true);
        box = bboxOf(m); topY = box.max.y;
        root.add(holder); loaded = holder; loadedUrl = url;
        exposure = o.exposure; renderer.toneMappingExposure = exposure;
        // lid: a mesh whose name matches; hinge it on the chosen edge
        lidPivot = null; lidAngle = o.lidAngle;
        const re = o.lid ? new RegExp(o.lid.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i') : /\b(lid|cover|door|hatch|flap)\b|_lid|lid_|disc.?cover|top.?cover/i;
        slot = o.slot === 'front' ? 'front' : 'top';
        let lid = null; if (slot === 'top') m.traverse(n => { if (!lid && n !== m && n.name && re.test(n.name) && (n.isMesh || n.children.length) && visibleUp(n)) lid = n; });
        if (!lid && o.lid !== false && slot === 'top') {
          // no named lid: look for a thin, roughly square plate sitting flush with the top of the console
          let bestA = 0;
          m.traverse(n => { if (n.isMesh && visibleUp(n)) { const b = bboxOf(n), s = b.getSize(new THREE.Vector3()); if (s.y < size.y * .04 && b.max.y > box.max.y - size.y * .03 && s.x > size.x * .22 && s.x < size.x * .75 && s.z > size.z * .22 && Math.abs(s.x - s.z) < Math.max(s.x, s.z) * .35 && s.x * s.z > bestA) { bestA = s.x * s.z; lid = n; } } });
        }
        if (lid) {
          const lb = bboxOf(lid), lc = lb.getCenter(new THREE.Vector3());
          const pivot = new THREE.Group();
          const hinge = { back: [lc.x, lb.min.y, lb.min.z], front: [lc.x, lb.min.y, lb.max.z], left: [lb.min.x, lb.min.y, lc.z], right: [lb.max.x, lb.min.y, lc.z] }[o.lidHinge] || [lc.x, lb.min.y, lb.min.z];
          pivot.position.set(...hinge); root.add(pivot); pivot.attach(lid); pivot.userData.axis = o.lidAxis; lidPivot = pivot;
        }
        // the disc that appears on top
        discR = Math.max(size.x, size.z) * .26 * o.discScale; discX = o.discX; discZ = o.discZ;
        if (lidPivot) { const lb = bboxOf(lidPivot), lc = lb.getCenter(new THREE.Vector3()), ls = lb.getSize(new THREE.Vector3()); discR = Math.min(ls.x, ls.z) * .46 * o.discScale; discX += lc.x; discZ += lc.z; topY = lb.min.y; }
        discMat = new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: .35, metalness: .15, side: THREE.DoubleSide });
        if (slot === 'front') discR = Math.max(size.x, size.y) * .38 * o.discScale; // the front face's bigger dimension sets the disc size
        disc = new THREE.Mesh(new THREE.RingGeometry(discR * .14, discR, 72), discMat); disc.visible = false;
        if (slot === 'front') {
          // upright, centred on the front face; it slides in from just in front of the slot to mostly inside the body
          slotY = o.lift + size.y / 2 + o.discHeight; slotZ0 = size.z / 2 + discR * 1.05 + o.discZ; slotZ1 = size.z / 2 - discR * .9 + o.discZ;
          disc.rotation.set(0, 0, 0); disc.position.set(discX, slotY, slotZ0);
        } else { disc.rotation.x = -Math.PI / 2; disc.position.set(discX, topY + .004 + o.discHeight, discZ); }
        root.add(disc);
        cache.set(url, { key, holder, lidPivot, disc, discMat, state: snap() }); trim();
        resize(); renderer.render(scene, camera);
        lastError = null; return true;
      } catch (e) { if (ticket !== loadSeq) return null; lastError = e && e.message || String(e); console.warn('console model', e); return false; }
    })();
    loading = { url, p }; p.finally(() => { if (loading && loading.p === p) loading = null; });
    return p;
  }
  // every texture a material holds (a downloaded model brings normal / roughness / emissive maps too: only .map was freed,
  // so each model dropped from the cache left the rest on the graphics card)
  const kill = o => o && o.traverse(n => { if (n.geometry) n.geometry.dispose(); if (n.material) (Array.isArray(n.material) ? n.material : [n.material]).forEach(m => { if (!m) return; for (const k in m) { const v = m[k]; if (v && v.isTexture) v.dispose(); } m.dispose(); }); });
  const snap = () => ({ lidAngle, discR, topY, discX, discZ, slot, slotY, slotZ0, slotZ1, exposure });
  // take the shown model off the stage (it stays built in the cache) / put a cached one on it
  function detach() { if (root) { if (loaded) root.remove(loaded); if (lidPivot) root.remove(lidPivot); if (disc) root.remove(disc); } loaded = null; loadedUrl = null; lidPivot = null; disc = null; discMat = null; }
  function attach(url) {
    const e = cache.get(url); cache.delete(url); cache.set(url, e); // most recent last
    ({ lidAngle, discR, topY, discX, discZ, slot, slotY, slotZ0, slotZ1, exposure } = e.state);
    loaded = e.holder; lidPivot = e.lidPivot; disc = e.disc; discMat = e.discMat; loadedUrl = url;
    root.add(loaded); if (lidPivot) { lidPivot.rotation.set(0, 0, 0); root.add(lidPivot); } if (disc) root.add(disc);
    renderer.toneMappingExposure = exposure; lastError = null;
  }
  function drop(url) { const e = cache.get(url); if (!e) return; cache.delete(url); if (e.holder === loaded) detach(); kill(e.holder); kill(e.lidPivot); kill(e.disc); }
  function trim() { for (const u of [...cache.keys()]) { if (cache.size <= KEEP) break; if (u !== loadedUrl) drop(u); } }
  function error() { return lastError; }
  function ready(url) { if (gone()) teardown(); return !!loaded && (!url || url === loadedUrl); }

  // Where the disc will sit, in screen pixels: centre, projected width and height (the CSS disc flies there).
  function target() {
    if (!loaded) return null;
    const THREE = T(); canvas.classList.add('on'); resize();
    const r = canvas.getBoundingClientRect();
    const proj = (x, y, z) => { const v = new THREE.Vector3(x, y, z).project(camera); return [r.left + (v.x + 1) / 2 * r.width, r.top + (1 - v.y) / 2 * r.height]; };
    if (slot === 'front') { const c = proj(discX, slotY, slotZ0), a = proj(discX - discR, slotY, slotZ0), b = proj(discX + discR, slotY, slotZ0), f = proj(discX, slotY + discR, slotZ0), k = proj(discX, slotY - discR, slotZ0); return { x: c[0], y: c[1], w: Math.hypot(b[0] - a[0], b[1] - a[1]), h: Math.hypot(f[0] - k[0], f[1] - k[1]) }; }
    const y = topY + .004, c = proj(discX, y, discZ), a = proj(discX - discR, y, discZ), b = proj(discX + discR, y, discZ), f = proj(discX, y, discZ + discR), k = proj(discX, y, discZ - discR);
    return { x: c[0], y: c[1], w: Math.hypot(b[0] - a[0], b[1] - a[1]), h: Math.hypot(f[0] - k[0], f[1] - k[1]) };
  }

  // Timeline (seconds, matches insert.css): rise .9–1.6, lid open 1.35–1.95, disc lands 2.33, lid closes 2.6–3.1, spin from 3.05
  const ease = t => t < 0 ? 0 : t > 1 ? 1 : 1 - Math.pow(1 - t, 3);
  const easeIO = t => t < 0 ? 0 : t > 1 ? 1 : (t < .5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);
  function frame(now) {
    raf = requestAnimationFrame(frame);
    const t = (now - startAt) / 1000, dt = Math.min(.1, Math.max(0, (now - (lastAt || now)) / 1000)); lastAt = now; // spin per second, not per frame (it ran twice as fast on a 120 Hz screen)
    const rise = mode === 'test' ? 1 : ease((t - .9) / .7);
    root.position.y = -1.6 * (1 - rise);
    if (lidPivot) {
      const open = ease((t - 1.35) / .6), close = easeIO((t - 2.6) / .5);
      const a = (window.BS3D.THREE.MathUtils.degToRad(lidAngle)) * (open - close);
      lidPivot.rotation.set(0, 0, 0); lidPivot.rotation[lidPivot.userData.axis === 'z' ? 'z' : 'x'] = a;
    }
    if (disc && slot === 'front') {
      // slides in through the slot (a little hesitation as the drive grabs it), then it's inside
      disc.visible = t >= 2.33; const p = ease((t - 2.33) / .7); disc.position.z = slotZ0 + (slotZ1 - slotZ0) * (p < .3 ? p * .5 : .15 + (p - .3) / .7 * .85);
    } else if (disc) {
      disc.visible = t >= 2.33;
      const drop = 1 - ease((t - 2.33) / .18); disc.position.y = topY + .004 + drop * .06;
      if (t >= 3.05) disc.rotation.z -= 15 * dt;
    }
    renderer.render(scene, camera);
    if (t > 6) stop();
  }
  function play(frontUrl) {
    if (!loaded) return false;
    const THREE = T();
    // the label arrives later: by then another console may be on the stage (a quick shelf switch) and this one's disc material
    // swapped out or freed (it threw on a null material, or put the old game's label on the new console's disc)
    const tok = ++playTok; // (the same console played twice keeps the same discMat: only the latest play's label may land)
    if (frontUrl) { const dm = discMat; new THREE.TextureLoader().load(frontUrl, tx => { if (!dm || dm !== discMat || tok !== playTok) { tx.dispose(); return; } tx.colorSpace = THREE.SRGBColorSpace; tx.center.set(.5, .5); tx.repeat.set(1, 1); if (dm.map) dm.map.dispose(); dm.map = tx; dm.needsUpdate = true; }); }
    if (disc) { disc.visible = false; disc.rotation.z = 0; }
    if (lidPivot) lidPivot.rotation.set(0, 0, 0);
    resize(); canvas.classList.add('on'); mode = 'play'; startAt = performance.now(); lastAt = 0; cancelAnimationFrame(raf); raf = requestAnimationFrame(frame);
    return true;
  }
  function stop() { cancelAnimationFrame(raf); raf = 0; mode = null; if (canvas) canvas.classList.remove('on'); }
  // For tuning console.json: names and sizes of the model's parts
  function parts() { if (!loaded) return []; const THREE = T(), out = []; loaded.traverse(n => { if (n.name && n !== loaded) { const b = new THREE.Box3().setFromObject(n); if (!b.isEmpty()) { const s = b.getSize(new THREE.Vector3()), c = b.getCenter(new THREE.Vector3()); out.push({ name: n.name, mesh: !!n.isMesh, visible: n.visible, size: [s.x, s.y, s.z].map(v => +v.toFixed(3)), center: [c.x, c.y, c.z].map(v => +v.toFixed(3)) }); } } }); return out; }
  return { load, play, stop, target, ready, error, parts };
})();
