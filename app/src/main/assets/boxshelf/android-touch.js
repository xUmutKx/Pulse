/* Android touch gestures (AYN Thor and other touch handhelds; added to the Android build only by copy-web.js).
   1. Swipe to go back. A finger that starts at the left or right screen edge and slides inward means Back on every
      screen (the same as Android's own edge swipe). On screens that don't use sideways drags themselves (Details,
      Settings, the quick menu, lists, the setup wizard) a plain quick swipe to the LEFT anywhere means Back too, so the
      direction is always the same: the finger moves away from the right, the way a page slides off. Never on the bare
      game shelf (a sideways swipe browses games there), and in Inspect / the museum / the page reader only from an edge,
      because there a sideways drag turns the case or looks around.
      Back is sent as the Esc key, exactly what MainActivity sends for the Android back button, so every screen does
      whatever Back / B already does there.
   2. Pinch to zoom: the case in Inspect, the manual / book reader, and a case or magazine held in the museum. Two
      fingers also pan while zoomed in.
   3. The museum on the top screen: a drag along the shelf steps the chosen case (a flick carries on, slowing down), a
      swipe up / down goes to the row below / above, a tap chooses a case and a tap on the chosen one picks it up (A).
      With a case in your hands a drag turns it, and a tap off it (or the edge swipe, as before) puts it back (B).
      All through BS_TOUCH.museum(), the same paths the pad uses, so the wall's racing and sounds are the pad's.
   The renderer tells us what's on screen through window.BS_TOUCH (app.js); this file only reads gestures.
   Performance: touch listeners are passive and do no layout work on the shelf; the back arrow moves by transform and
   opacity only (its own small compositor layer), and the zoom is applied at most once per frame while fingers move. */
(() => {
  if (!window.bsPlatform || bsPlatform.os !== 'android') return;

  // Distances in layout pixels (the Android layout is 1440 px wide on the Thor's 1920 px screen)
  const EDGE = 40;          // a finger landing this close to a side edge starts an edge swipe (about Android's own 24 dp strip)
  const RECOGNISE = 16;     // sideways travel before we decide it's a back swipe (and take the finger from the app)
  const COMMIT = 90;        // travel at which letting go means Back (the arrow lights up)
  const FLING_V = 0.6;      // ...or a quick flick: px per ms at release, with at least half the travel
  const T = () => window.BS_TOUCH;

  let sw = null;            // the one-finger swipe being watched
  let pz = null;            // the pinch in progress
  let eat = false;          // the gesture owns the touch: pointer moves don't reach the app until every finger is up
  let eatClickUntil = 0;
  let sw0 = 0;              // when the first finger of this touch landed
  const down = new Set();   // touch pointers currently down

  // ---------- the back arrow (built on first use) ----------
  let arrow = null, arrowHideT = 0;
  function arrowEl() {
    if (arrow) return arrow;
    arrow = document.createElement('div');
    arrow.id = 'bsBackSwipe';
    arrow.setAttribute('aria-hidden', 'true');
    arrow.innerHTML = '<i class="bbs-ready"></i><svg viewBox="0 0 24 24" width="26" height="26"><path d="M15 5l-7 7 7 7" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"/></svg>';
    document.body.appendChild(arrow);
    return arrow;
  }
  const SIZE = 56;
  // p: 0..1+ how far toward Back; side: 'L' / 'R' (where the arrow peeks in from); y: finger height
  function arrowAt(side, p, y) {
    const a = arrowEl(), q = Math.min(1, p), over = Math.max(0, Math.min(.4, p - 1)) * 30; // a little give past the line
    const inX = q * (SIZE + 22) + over, x = side === 'L' ? -SIZE + inX : innerWidth - inX;
    const yy = Math.max(8, Math.min(innerHeight - SIZE - 8, y - SIZE / 2));
    clearTimeout(arrowHideT);
    a.classList.remove('bbs-anim'); a.classList.add('live');
    a.classList.toggle('on', p >= 1);
    a.style.opacity = Math.min(1, p * 1.6).toFixed(3);
    a.style.transform = `translate3d(${x.toFixed(1)}px,${yy.toFixed(1)}px,0) scale(${(.55 + .45 * q).toFixed(3)})`;
    a.dataset.side = side;
  }
  function arrowRelease(side, y, committed) {
    if (!arrow) return;
    const a = arrow, yy = Math.max(8, Math.min(innerHeight - SIZE - 8, y - SIZE / 2));
    const x = committed ? (side === 'L' ? 70 : innerWidth - SIZE - 70) : (side === 'L' ? -SIZE : innerWidth);
    a.classList.add('bbs-anim');
    a.style.opacity = '0';
    a.style.transform = `translate3d(${x}px,${yy.toFixed(1)}px,0) scale(${committed ? 1.05 : .55})`;
    clearTimeout(arrowHideT);
    arrowHideT = setTimeout(() => { a.classList.remove('bbs-anim', 'on', 'live'); }, 260);
  }

  // ---------- Back = Esc, as MainActivity does for the Android back button ----------
  function sendBack() {
    if (T().back) return T().back();   // app.js sends it without switching the hints to keyboard keys
    const t = document.activeElement || document.body, o = { key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true, cancelable: true };
    t.dispatchEvent(new KeyboardEvent('keydown', o));
    t.dispatchEvent(new KeyboardEvent('keyup', o));
  }

  // A plain (not edge) swipe is left alone when it starts on something that drags sideways itself
  function dragsSideways(el) {
    for (let n = el; n && n.nodeType === 1 && n !== document.body; n = n.parentElement) {
      if (n.matches('input,textarea,select,[contenteditable=""],[contenteditable="true"],.qm-vbar,[data-hswipe],[data-hold]')) return true;
      if (n.scrollWidth > n.clientWidth + 2) { const ox = getComputedStyle(n).overflowX; if (ox === 'auto' || ox === 'scroll') return true; }
    }
    return false;
  }

  // ---------- pinch ----------
  const dist = (a, b) => Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY) || 1;
  const mid = (a, b) => [(a.clientX + b.clientX) / 2, (a.clientY + b.clientY) / 2];
  const pair = (list, ids) => { const out = []; for (const t of list) if (ids.includes(t.identifier)) out.push(t); return out.length === 2 ? out : null; };
  function pinchStart(e) {
    const tz = T() && T().zoom(); if (!tz) return false;
    const a = e.touches[0], b = e.touches[1];
    pz = { tz, ids: [a.identifier, b.identifier], d0: dist(a, b), m: mid(a, b), z0: tz.get ? tz.get() : 1, ref: dist(a, b), raf: 0, cur: null };
    return true;
  }
  function pinchApply() {
    const p = pz; if (!p) return; p.raf = 0; const c = p.cur; if (!c) return; p.cur = null;
    const tz = p.tz, [mx, my] = c.m, dmx = mx - p.m[0], dmy = my - p.m[1];
    if (tz.get) {                                       // free zoom: follows the fingers exactly
      const zOld = tz.get(); tz.set(p.z0 * c.d / p.d0); const r = tz.get() / zOld;
      if (tz.pan) {                                     // keep the point between the fingers under the fingers
        const [px, py] = tz.pan(), cx = innerWidth / 2, cy = innerHeight / 2;
        tz.setPan(mx - cx - (p.m[0] - cx - px) * r, my - cy - (p.m[1] - cy - py) * r);
      } else if (tz.panBy && (dmx || dmy)) tz.panBy(dmx, dmy);
    } else {                                            // stepped zoom (the museum eases between its own steps)
      while (c.d / p.ref >= tz.ratio) { tz.step(1); p.ref *= tz.ratio; }
      while (c.d / p.ref <= 1 / tz.ratio) { tz.step(-1); p.ref /= tz.ratio; }
      if (tz.panBy && (dmx || dmy)) tz.panBy(dmx, dmy);
    }
    p.m = c.m;
  }

  // ---------- the museum (top screen) ----------
  // Moves only collect numbers; what they add up to is handed to the museum once per frame. A flick's coasting is the
  // only loop here, and it stops by itself (or at the next touch).
  const TAP = 12, TAP_MS = 400, ROW_PX = 40;
  const stepPx = () => Math.max(40, innerWidth * .055);  // finger travel per case (0.3.0: was 7% of the width; felt slow on the Thor)
  const FLICK = { min: .4, boost: 1.2, tau: 300 };        // a flick: the least speed (px/ms) that coasts, how much it carries, how slowly it fades (ms)
  const TURN = () => 270 / innerWidth;                   // degrees per px: a full-width drag turns a case 3/4 round
  let mg = null, coast = null;
  function coastStop() { if (coast) { cancelAnimationFrame(coast.raf); coast = null; } }
  function mgApply() {
    const g = mg; if (!g) return; g.raf = 0;
    if (g.state === 'h') {                                // whole cases the finger has passed since the last frame
      const want = Math.trunc((g.x0 - g.x) / stepPx());
      for (let n = 0; g.done !== want && n < 3; n++) { const d = want > g.done ? 1 : -1; if (!g.m.step(d, 0)) break; g.done += d; }
      if (g.done !== want && !g.raf) g.raf = requestAnimationFrame(mgApply);   // a very fast drag: the rest next frame
    } else if (g.state === 'turn' && (g.tx || g.ty)) { g.m.turn(g.tx * TURN(), -g.ty * TURN()); g.tx = g.ty = 0; }
  }
  // a flick: keeps stepping the way the finger went, slowing down (the same steps as the drag, so the same racing)
  function coastGo(m, v, rest) {
    coastStop();
    const c = coast = { v, acc: rest, t: performance.now(), raf: 0 };
    const tick = now => {
      if (coast !== c) return;
      // the wall still there and still ours? (B, a list or the TV guide opened mid-flick: the flick stops, it doesn't step behind it)
      m = T() && T().museum ? T().museum() : null; if (!m) { coast = null; return; }
      const dt = Math.min(50, now - c.t); c.t = now;
      c.acc += c.v * dt; c.v *= Math.exp(-dt / FLICK.tau);
      const sp = stepPx();
      while (Math.abs(c.acc) >= sp) { const d = c.acc > 0 ? 1 : -1; c.acc -= d * sp; if (!m.step(d, 0)) { coast = null; return; } }
      if (Math.abs(c.v) < .12) { coast = null; return; }
      c.raf = requestAnimationFrame(tick);
    };
    c.raf = requestAnimationFrame(tick);
  }
  function mgMove(f, ts) {
    const g = mg, x = f.clientX, y = f.clientY, dx = x - g.x0, dy = y - g.y0, dt = ts - g.lt;
    if (dt > 0) g.v = .6 * g.v + .4 * ((g.lx - x) / dt);  // px per ms the way the steps go (finger left = next case)
    if (g.state === 'turn') { g.tx += x - g.lx; g.ty += y - g.ly; }
    g.lx = x; g.ly = y; g.lt = ts; g.x = x; g.y = y;
    if (g.state === 'maybe') {
      if (Math.hypot(dx, dy) < TAP) return;
      if (g.held) { g.state = 'turn'; g.tx = dx; g.ty = dy; }
      else if (Math.abs(dx) > Math.abs(dy)) g.state = 'h';
      else g.state = 'v';
      eatClickUntil = performance.now() + 350;
    }
    if (g.state === 'v') {                                // one row per swipe: up = the row below, as a page scrolls
      if (!g.rowDone && Math.abs(dy) >= ROW_PX && Math.abs(dy) > Math.abs(dx)) { g.rowDone = true; g.m.step(0, dy < 0 ? 1 : -1); }
      return;
    }
    if (!g.raf) g.raf = requestAnimationFrame(mgApply);
  }
  function mgEnd(f, ts, cancelled) {
    const g = mg; mg = null; if (g.raf) cancelAnimationFrame(g.raf);
    if (cancelled) { if (g.state === 'turn') g.m.release(); return; }
    if (g.state === 'maybe') { if (ts - g.t0 < TAP_MS) g.m.tap(f.clientX, f.clientY); return; }
    g.x = f.clientX; mgApply();
    if (g.state === 'turn') { g.m.release(); return; }
    if (g.state === 'h' && Math.abs(g.v) > FLICK.min && ts - g.lt < 80) coastGo(g.m, g.v * FLICK.boost, (g.x0 - g.x) - g.done * stepPx());
  }

  // ---------- listeners ----------
  // Pointer events come first for each touch; while a gesture owns the fingers the app doesn't see them move.
  addEventListener('pointerdown', e => {
    if (e.pointerType !== 'touch') return;
    down.add(e.pointerId);
    // a second finger landing where two fingers zoom: the app mustn't start a new drag with it
    if (eat || (down.size > 1 && T() && T().zoom())) e.stopImmediatePropagation();
  }, true);
  addEventListener('pointermove', e => { if (eat && e.pointerType === 'touch') e.stopImmediatePropagation(); }, true);
  const lift = e => { if (e.pointerType === 'touch') down.delete(e.pointerId); };
  addEventListener('pointerup', lift, true);
  addEventListener('pointercancel', lift, true);
  addEventListener('click', e => { if (performance.now() < eatClickUntil) { e.stopImmediatePropagation(); e.preventDefault(); } }, true);

  addEventListener('touchstart', e => {
    const n = e.touches.length, t = T();
    if (!t) return;
    if (n >= 2) {
      if (pz) return;
      if (mg) { if (mg.raf) cancelAnimationFrame(mg.raf); if (mg.state === 'turn') mg.m.release(); mg = null; } // two fingers: a pinch, not a drag
      const wasBack = sw && sw.state === 'track';
      if (sw) { if (wasBack) arrowRelease(sw.side, sw.y, false); sw = null; }
      // fingers landing together: undo the sliver of a turn the first one made; a turn already under way stays
      if (pinchStart(e)) { t.cancel(e.timeStamp - sw0 < 250); eat = true; }
      return;
    }
    if (n !== 1) return;
    eat = false; pz = null; mg = null; sw0 = e.timeStamp; coastStop(); // a finger landing stops a coasting flick, as on a phone
    // Every first finger marks where the case was, so a pinch that starts with it undoes this touch's sliver of a turn
    // (never an older pose left over from an earlier touch)
    t.mark();
    // The touch that wakes a dimmed screen is used up by the input gate (perf.js stops its pointerdown before it reaches
    // us): it only wakes, the same as a key or a click, so it mustn't go Back either.
    if (!down.size) { sw = null; return; }
    const f = e.touches[0], x = f.clientX, scr = t.screen();
    if (scr === 'shelf') { sw = null; return; } // the shelf: sideways swipes browse games, never Back
    const side = x <= EDGE ? 'L' : x >= innerWidth - EDGE ? 'R' : null;
    // the museum's wall (not its buttons, nor a finger from the edge: that's still Back)
    if (scr === 'museum' && !side && t.museum && !e.target.closest('button,a,input,select,textarea,[role=button],[data-hswipe]')) {
      const m = t.museum();
      if (m) { mg = { m, id: f.identifier, x0: x, y0: f.clientY, x, y: f.clientY, lx: x, ly: f.clientY, t0: e.timeStamp, lt: e.timeStamp, v: 0, state: 'maybe', held: m.held(), done: 0, tx: 0, ty: 0, raf: 0, rowDone: false }; sw = null; return; }
    }
    const plain = !side && !t.hswipe() && !dragsSideways(e.target);
    if (!side && !plain) { sw = null; return; }
    sw = { id: f.identifier, x0: x, y0: f.clientY, y: f.clientY, edge: side, side: side || 'R', state: 'maybe', lx: x, lt: e.timeStamp, v: 0 };
  }, { capture: true, passive: true });

  addEventListener('touchmove', e => {
    if (pz) {
      const two = pair(e.touches, pz.ids); if (!two) return;
      pz.cur = { d: dist(two[0], two[1]), m: mid(two[0], two[1]) };
      if (!pz.raf) pz.raf = requestAnimationFrame(pinchApply);
      return;
    }
    if (mg) { if (e.touches.length === 1 && e.touches[0].identifier === mg.id) mgMove(e.touches[0], e.timeStamp); return; }
    if (!sw || sw.state === 'no' || e.touches.length !== 1) return;
    const f = e.touches[0]; if (f.identifier !== sw.id) return;
    const dx = f.clientX - sw.x0, dy = f.clientY - sw.y0, inward = sw.edge === 'L' ? dx : -dx;
    const dt = e.timeStamp - sw.lt; if (dt > 0) sw.v = .6 * sw.v + .4 * ((sw.edge === 'L' ? 1 : -1) * (f.clientX - sw.lx) / dt);
    sw.lx = f.clientX; sw.lt = e.timeStamp; sw.y = f.clientY;
    if (sw.state === 'maybe') {
      if (Math.abs(dy) > 14 && Math.abs(dy) > Math.abs(dx)) { sw.state = 'no'; return; }   // scrolling a list, not Back
      if (inward < -10) { sw.state = 'no'; return; }                                         // the other way
      const need = sw.edge ? RECOGNISE : RECOGNISE * 1.5, ratio = sw.edge ? 1.2 : 1.6;
      if (inward > need && inward > ratio * Math.abs(dy)) {
        // a touch outside the quick menu closes it as the finger lands, leaving the bare shelf: that swipe browses instead
        if (T().screen() === 'shelf') { sw.state = 'no'; return; }
        sw.state = 'track'; eat = true; T().cancel(true);
      } else return;
    }
    arrowAt(sw.side, Math.max(0, inward - RECOGNISE * .5) / (COMMIT - RECOGNISE * .5), f.clientY);
  }, { capture: true, passive: true });

  function end(e, cancelled) {
    if (pz) {
      if (e.touches.length < 2) { if (pz.raf) cancelAnimationFrame(pz.raf); pinchApply(); pz = null; eatClickUntil = performance.now() + 350; }
      if (!e.touches.length) eat = false;
      return;
    }
    if (mg) { for (const c of e.changedTouches) if (c.identifier === mg.id) { mgEnd(c, e.timeStamp, cancelled); break; } if (!e.touches.length) eat = false; return; }
    if (!sw) { if (!e.touches.length) eat = false; return; }
    let f = null; for (const c of e.changedTouches) if (c.identifier === sw.id) f = c;
    if (!f) return;
    if (sw.state === 'track') {
      const inward = sw.edge === 'L' ? f.clientX - sw.x0 : sw.x0 - f.clientX;
      const go = !cancelled && (inward >= COMMIT || (inward >= COMMIT / 2 && sw.v >= FLING_V));
      arrowRelease(sw.side, sw.y, go);
      eatClickUntil = performance.now() + 350;
      if (go && T().screen() !== 'shelf') sendBack();
    }
    sw = null;
    if (!e.touches.length) eat = false;
  }
  // Every finger up: forget any pointer whose pointerup we never saw, so a stale one can't make the next single finger
  // look like a second one (whose pointerdown would then be kept from the app)
  addEventListener('touchend', e => { end(e, false); if (!e.touches.length) down.clear(); }, { capture: true, passive: true });
  addEventListener('touchcancel', e => { end(e, true); if (!e.touches.length) down.clear(); }, { capture: true, passive: true });

  // ---------- 4. the tap after a flick (beta.7) ----------
  // Chromium starts a fling at the end of every quick drag. The page's touch-action keeps it from scrolling anything, but
  // the browser still takes the next finger-down as "stop the fling" and makes no click for that tap, however long after:
  // flick the shelf or the console bar, and the first tap on a case / console did nothing. So after a drag, a quick tap
  // that gets no tap from Chromium (no mousedown / click) is clicked here, once; a real one that turns up late is eaten.
  // Not for the touch that wakes a dimmed screen (its pointerdown never reached us: down is empty), the museum wall's own
  // taps (mg), or a tap one of our gestures used up (eatClickUntil). Every other tap is left to Chromium as before.
  const TAP_SLOP = 12, TAP_LONG = 450, TAP_WAIT = 140, LATE = 700;
  let tp = null, afterDrag = false, waitTap = null, synthAt = 0, synthX = 0, synthY = 0;
  addEventListener('touchstart', e => {
    if (e.touches.length !== 1) { if (tp) afterDrag = true; tp = null; return; }
    const f = e.touches[0];
    tp = { id: f.identifier, x: f.clientX, y: f.clientY, t: e.timeStamp, el: e.target, moved: false, armed: afterDrag, skip: !!mg || !down.size };
  }, { capture: true, passive: true });
  addEventListener('touchmove', e => {
    if (!tp || tp.moved) return;
    for (const c of e.changedTouches) if (c.identifier === tp.id && Math.hypot(c.clientX - tp.x, c.clientY - tp.y) > TAP_SLOP) { tp.moved = true; afterDrag = true; }
  }, { capture: true, passive: true });
  addEventListener('touchend', e => {
    const t = tp; if (!t || e.touches.length) return; tp = null;
    if (t.moved) { afterDrag = true; return; }
    afterDrag = false;
    if (!t.armed || t.skip || e.timeStamp - t.t > TAP_LONG || performance.now() < eatClickUntil) return;
    const w = waitTap = { ...t, got: false };
    setTimeout(() => {
      if (waitTap !== w) return; waitTap = null;
      if (w.got || w.el.isConnected === false || performance.now() < eatClickUntil) return;
      const el = document.elementFromPoint(w.x, w.y);
      if (!el || !(el === w.el || w.el.contains(el) || el.contains(w.el))) return; // the page changed under the finger: leave it
      synthAt = performance.now(); synthX = w.x; synthY = w.y;
      const f = el.closest('button,a[href],input,select,textarea,[tabindex]'); if (f && f.focus) { try { f.focus({ preventScroll: true }); } catch {} }
      el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, composed: true, view: window, detail: 1, button: 0, clientX: w.x, clientY: w.y }));
    }, TAP_WAIT);
  }, { capture: true, passive: true });
  addEventListener('touchcancel', () => { tp = null; }, { capture: true, passive: true });
  // Chromium's own tap: its mousedown / click mark the tap as done (no backup click); one arriving after the backup is eaten
  const tapSeen = e => {
    if (waitTap) { waitTap.got = true; return; }
    // (beta.8) only a click at the same spot as the backup one is its late twin: a real tap elsewhere always goes through
    if (e.isTrusted && synthAt && performance.now() - synthAt < LATE && Math.abs(e.clientX - synthX) < 24 && Math.abs(e.clientY - synthY) < 24) { if (e.type === 'click') synthAt = 0; e.stopImmediatePropagation(); e.preventDefault(); }
  };
  addEventListener('mousedown', tapSeen, true); addEventListener('click', tapSeen, true);
  window.BS_TAPFIX = { wait: TAP_WAIT }; // (for the device test)
})();
