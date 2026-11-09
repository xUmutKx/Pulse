/* Fitting your own 3D console (themes\<system>\console.glb) — shared by the museum shelf and the insert animation.
   A downloaded model often brings extras: a controller, a cable, a disc, a stand. analyze() splits the model into parts:
     - the console: the biggest group of meshes that touch each other;
     - everything else: loose groups (a controller lying beside it), plus anything named like a disc / cable / controller,
       or a thin piece hanging below the console (a disc poking out of the slot would make it float above the shelf).
   It also guesses which side is the front (the disc slot / ports side) so the console faces you.
   console.json keeps your choices: { "museumYaw": 90, "museumScale": 1, "parts": { "p2": "show" },
     "controller": "p1" | "file" | "none", "controllerYaw": 0 } */
window.ConsoleFit = (() => {
  const T = () => window.BS3D && window.BS3D.THREE;
  const cache = new WeakMap(); // scene -> { hide string -> analysis }
  const RE = {
    controller: /controller|gamepad|joy-?con|joystick|wiimote|remote|\bpad\b|_pad|pad_|nunchuck|dualshock|dualsense/i,
    disc: /\bdisc|disk|circle|\bcd\b|dvd|umd|blu-?ray|cartridge|\bcart\b/i,
    cable: /cable|wire|cord|plug|hdmi|av_?out/i,
    stand: /stand|\bbase\b|dock|cradle/i,
    card: /memory.?card|mem_?card/i,
  };
  const DEFAULT_HIDE = 'controller|gamepad|joystick|cable|memory.?card|wire';
  const stem = n => { const m = /^(.+?)_(?:[A-Za-z]+\d*_)?\d+$/.exec(n || ''); return m ? m[1] : (n || ''); };
  const meshesOf = root => { const out = []; root.traverse(n => { if (n.isMesh) out.push(n); }); return out; };

  function analyze(root, hide) {
    const key = hide === false ? '' : String(hide || DEFAULT_HIDE);
    let per = cache.get(root); if (!per) { per = new Map(); cache.set(root, per); }
    if (per.has(key)) return per.get(key);
    const THREE = T(); root.updateMatrixWorld(true);
    const hideRe = key ? new RegExp(key, 'i') : null;
    const meshes = meshesOf(root), objs = new Map();
    meshes.forEach((m, i) => {
      if (!m.geometry) return; if (!m.geometry.boundingBox) m.geometry.computeBoundingBox();
      const b = m.geometry.boundingBox.clone().applyMatrix4(m.matrixWorld); if (b.isEmpty()) return;
      const nm = stem(m.name) || (m.parent && m.parent !== root ? m.parent.name || m.parent.uuid : m.uuid);
      let named = ''; for (let q = m; q && q !== root; q = q.parent) named += ' ' + (q.name || '');
      let o = objs.get(nm); if (!o) { o = { name: nm, idx: [], box: new THREE.Box3(), names: '' }; objs.set(nm, o); }
      o.idx.push(i); o.box.union(b); o.names += named;
    });
    const list = [...objs.values()];
    list.forEach(o => { const z = o.box.getSize(new THREE.Vector3()); o.size = z; o.vol = Math.max(z.x * z.y * z.z, 1e-12); o.forced = !!(hideRe && hideRe.test(o.names)); });
    const all = new THREE.Box3(); list.forEach(o => all.union(o.box)); const diag = all.getSize(new THREE.Vector3()).length() || 1, pad = diag * .02;
    // groups of objects that touch (union-find); huge models: one group, nothing split
    const par = list.map((_, i) => i), find = i => par[i] === i ? i : (par[i] = find(par[i]));
    if (list.length <= 1500) for (let i = 0; i < list.length; i++) { const e = list[i].box.clone().expandByScalar(pad); for (let j = i + 1; j < list.length; j++) if (e.intersectsBox(list[j].box)) par[find(i)] = find(j); }
    else list.forEach((_, i) => { par[i] = 0; });
    const groups = new Map(); list.forEach((o, i) => { if (o.forced) return; const r = find(i); if (!groups.has(r)) groups.set(r, []); groups.get(r).push(o); });
    let mainG = null, best = -1; groups.forEach(g => { const v = g.reduce((a, o) => a + o.vol, 0); if (v > best) { best = v; mainG = g; } });
    if (!mainG) mainG = list.slice(); // everything was matched by the hide names: keep it all as the console
    // inside the console group: pull out discs and a thin piece hanging below the rest
    const mainSet = new Set(mainG), split = [];
    if (mainG.length > 1) {
      const mainVol = mainG.reduce((a, o) => a + o.vol, 0), H = all.getSize(new THREE.Vector3()).y || 1;
      mainG.forEach(o => {
        const d = [o.size.x, o.size.y, o.size.z].sort((a, b) => a - b), thin = d[0] < d[2] * .2;
        const others = mainG.filter(x => x !== o && !split.includes(x)); if (!others.length) return;
        const restMin = Math.min(...others.map(x => x.box.min.y));
        const hangs = thin && o.box.min.y < restMin - H * .02 && o.vol < mainVol * .25;
        if ((RE.disc.test(o.names) && o.vol < mainVol * .3) || hangs) split.push(o);
      });
      split.forEach(o => mainSet.delete(o));
    }
    // parts: the console first, then the rest (largest first)
    // everything else, grouped again by touch among themselves (a named controller made of 20 pieces is one part)
    const left = list.filter(o => !mainSet.has(o)), lp = left.map((_, i) => i), lf = i => lp[i] === i ? i : (lp[i] = lf(lp[i]));
    if (left.length <= 1500) for (let i = 0; i < left.length; i++) { const e = left[i].box.clone().expandByScalar(pad); for (let j = i + 1; j < left.length; j++) if (e.intersectsBox(left[j].box)) lp[lf(i)] = lf(j); }
    const lg = new Map(); left.forEach((o, i) => { const r = lf(i); if (!lg.has(r)) lg.set(r, []); lg.get(r).push(o); });
    const rest = [...lg.values()];
    const vol = g => g.reduce((a, o) => a + o.vol, 0); rest.sort((a, b) => vol(b) - vol(a));
    const mainObjs = [...mainSet], mainVol = vol(mainObjs);
    const parts = [{ key: 'p0', label: 'Console', main: true, objs: mainObjs }];
    rest.forEach((g, i) => parts.push({ key: 'p' + (i + 1), objs: g }));
    let ctl = null;
    parts.forEach(p => {
      p.idx = p.objs.flatMap(o => o.idx); p.box = new THREE.Box3(); p.objs.forEach(o => p.box.union(o.box)); if (p.main) return;
      const names = p.objs.map(o => o.names).join(' ');
      p.label = RE.controller.test(names) ? 'Controller' : RE.card.test(names) ? 'Memory card' : RE.disc.test(names) ? 'Disc' : RE.cable.test(names) ? 'Cable' : RE.stand.test(names) ? 'Stand' : '';
      if (p.label === 'Controller' && !ctl) ctl = p;
    });
    // no part named like a controller: the biggest unnamed loose group made of several pieces probably is one
    if (!ctl) { const c = parts.find(p => !p.main && !p.label && p.objs.length >= 3 && vol(p.objs) > mainVol * .02); if (c) { c.label = 'Controller'; ctl = c; } }
    let n = 1; parts.forEach(p => { if (!p.label) p.label = 'Extra part ' + (n++); p.objs = undefined; });
    // same labels: number them (Controller, Controller 2)
    const seen = {}; parts.forEach(p => { seen[p.label] = (seen[p.label] || 0) + 1; if (seen[p.label] > 1) p.label += ' ' + seen[p.label]; });
    const res = { parts, controller: ctl ? ctl.key : null, yaw: guessYaw(meshes, parts[0]), count: meshes.length };
    per.set(key, res); return res;
  }

  // Which way is the front? Scored per side (+z, -x, -z, +x): the front is where the separate little pieces sit (buttons,
  // ports, the disc slot or tray, the power light) and where the detail (vertices) bunches up; lit parts count extra.
  // Shape nudges it: a console standing on its edge (Wii, 360) has its front on a narrow end, a flat one on a long side.
  // glTF's own front (+z) wins a tie.
  function guessYaw(meshes, main) {
    const THREE = T(), b = main.box, sz = b.getSize(new THREE.Vector3()); if (!(sz.x > 0 && sz.z > 0)) return 0;
    const faces = [{ ax: 'z', hi: true, yaw: 0 }, { ax: 'x', hi: false, yaw: 90 }, { ax: 'z', hi: false, yaw: 180 }, { ax: 'x', hi: true, yaw: 270 }];
    faces.forEach(f => { f.n = 0; f.v = 0; f.em = 0; });
    const slab = f => (b.max[f.ax] - b.min[f.ax]), near = (f, a, k) => f.hi ? a > b.max[f.ax] - slab(f) * k : a < b.min[f.ax] + slab(f) * k;
    let total = 0; main.idx.forEach(i => { const p = meshes[i].geometry.attributes.position; if (p) total += p.count; });
    const stride = Math.max(1, Math.ceil(total / 120000)), v = new THREE.Vector3(), c = new THREE.Vector3();
    main.idx.forEach(i => {
      const m = meshes[i], p = m.geometry.attributes.position; if (!p) return;
      m.geometry.boundingBox.clone().applyMatrix4(m.matrixWorld).getCenter(c);
      const lit = [].concat(m.material).some(mt => mt && mt.emissive && mt.emissive.getHex() && (mt.emissiveIntensity == null || mt.emissiveIntensity > 0));
      faces.forEach(f => { if (near(f, c[f.ax], .25)) { f.n++; if (lit) f.em++; } });
      for (let k = 0; k < p.count; k += stride) { v.fromBufferAttribute(p, k).applyMatrix4(m.matrixWorld); faces.forEach(f => { if (near(f, v[f.ax], .08)) f.v++; }); }
    });
    const vt = faces.reduce((a, f) => a + f.v, 0) || 1, minH = Math.min(sz.x, sz.z), standing = minH < sz.y * .5, longAx = sz.x >= sz.z ? 'x' : 'z';
    faces.forEach(f => { f.s = f.n + 3 * f.em + 4 * f.v / vt + (f.ax === 'z' && f.hi ? .3 : 0) + (Math.abs(sz.x - sz.z) / Math.max(sz.x, sz.z) < .15 ? 0 : standing ? (f.ax === longAx ? 1 : 0) : (f.ax !== longAx ? .5 : 0)); });
    faces.sort((p, q) => q.s - p.s); return faces[0].yaw;
  }

  // which meshes show: 'console' = the console + the parts you switched on; 'controller' = only that part
  function show(root, a, o, mode) {
    const meshes = meshesOf(root), on = new Set(), opts = o || {}, sel = opts.parts || {};
    if (mode === 'controller') { const p = a.parts.find(x => x.key === opts.controller); if (p) p.idx.forEach(i => on.add(i)); }
    else a.parts.forEach(p => { if (p.main || (sel[p.key] === 'show' && p.key !== opts.controller)) p.idx.forEach(i => on.add(i)); });
    meshes.forEach((m, i) => { m.visible = on.has(i); });
    // a hidden parent would hide a part we want: make sure the way up is visible
    meshes.forEach(m => { if (m.visible) for (let q = m.parent; q && q !== root; q = q.parent) q.visible = true; });
    return meshes;
  }
  // world box of what's visible
  function visibleBox(root) {
    const THREE = T(), box = new THREE.Box3(); root.updateMatrixWorld(true);
    root.traverseVisible(n => { if (n.isMesh && n.geometry) { if (!n.geometry.boundingBox) n.geometry.computeBoundingBox(); box.union(n.geometry.boundingBox.clone().applyMatrix4(n.matrixWorld)); } });
    return box;
  }
  // Older Sketchfab exports use KHR_materials_pbrSpecularGlossiness, which three.js no longer reads: rebuild those materials
  async function fixMaterials(gltf) {
    const THREE = T();
    try {
      const json = gltf.parser.json, mats = json.materials || [], fixes = new Map();
      for (let i = 0; i < mats.length; i++) { const sg = mats[i].extensions && mats[i].extensions.KHR_materials_pbrSpecularGlossiness; if (sg) fixes.set(mats[i].name || ('#' + i), sg); }
      if (!fixes.size) return;
      const todo = [];
      gltf.scene.traverse(n => { if (n.isMesh && n.material) { const arr = Array.isArray(n.material) ? n.material : [n.material]; arr.forEach((mt, k) => { const sg = fixes.get(mt.name); if (sg) todo.push({ n, k, mt, sg, arr: Array.isArray(n.material) }); }); } });
      await Promise.all(todo.map(async ({ n, k, mt, sg, arr }) => {
        const nm = new THREE.MeshStandardMaterial({ name: mt.name, roughness: 1 - (sg.glossinessFactor == null ? 1 : sg.glossinessFactor) * .9, metalness: .05, side: mt.side, transparent: mt.transparent, opacity: mt.opacity });
        if (sg.diffuseFactor) { nm.color.setRGB(sg.diffuseFactor[0], sg.diffuseFactor[1], sg.diffuseFactor[2], THREE.SRGBColorSpace); if (sg.diffuseFactor[3] < 1) { nm.transparent = true; nm.opacity = sg.diffuseFactor[3]; } }
        if (sg.diffuseTexture) { try { const tx = await gltf.parser.getDependency('texture', sg.diffuseTexture.index); tx.colorSpace = THREE.SRGBColorSpace; nm.map = tx; } catch {} }
        if (mt.normalMap) nm.normalMap = mt.normalMap;
        if (arr) n.material[k] = nm; else n.material = nm;
      }));
    } catch (e) { console.warn('material fix', e); }
  }
  return { analyze, show, visibleBox, fixMaterials };
})();
