// The four buttons under the counter (Search, Details, All games, Menu) act on the finger lifting off them, whatever happened to the click
// (the touch layer eats or delays clicks after a swipe; Search's own closing pointerdown swallows the next one). The button's own click
// handler is then silenced for that tap, so nothing runs twice. Loaded before find.js so it sees the pointer first.
'use strict';
(() => {
  const IDS = ['findBtn', 'detBtn', 'gridBtn', 'menuBtn'];
  let down = null, doneAt = -1e9, wasFind = false;
  const btn = e => { const t = e.target; return t && t.closest ? t.closest('#findBtn,#detBtn,#gridBtn,#menuBtn') : null; };
  const act = {
    findBtn: () => { const F = window.BS_FIND; if (!F) return; if (wasFind) F.close(); else F.open('ptr'); },
    detBtn: () => { const S = window.Shelf; if (!S) return; if (S.getMode && S.getMode() === 'details') S.closeDetails && S.closeDetails(); else S.openDetails && S.openDetails(); },
    gridBtn: () => { if (window.__pulseOpenGrid) window.__pulseOpenGrid(); },
    menuBtn: () => { if (window.__hudMenu) window.__hudMenu(); },
  };
  addEventListener('pointerdown', e => {
    wasFind = !!(window.BS_FIND && BS_FIND.isOpen());
    const b = btn(e); down = b ? { id: b.id, p: e.pointerId, x: e.clientX, y: e.clientY, t: performance.now() } : null;
  }, true);
  addEventListener('pointerup', e => {
    const d = down; down = null; if (!d || e.pointerId !== d.p) return;
    const b = btn(e); if (!b || b.id !== d.id) return;
    if (Math.hypot(e.clientX - d.x, e.clientY - d.y) > 14 || performance.now() - d.t > 700) return;
    doneAt = performance.now();
    try { act[d.id](); } catch (x) { console.warn('hud', x); }
  }, true);
  addEventListener('pointercancel', () => { down = null; }, true);
  // the click that follows a tap already handled: the buttons' own handlers must not run a second time
  addEventListener('click', e => { if (btn(e) && performance.now() - doneAt < 900) { e.stopImmediatePropagation(); e.preventDefault(); } }, true);
})();
