// Ten more shelf themes (made for the Thor in Android 0.3.5, on Windows too since 1.75). index.html loads this before the
// app's scripts; the shelf (app.js THEMES / QM_THEMES), Settings' theme tiles (settings.js) and the theme layer
// (themefx.js) add them after the original six.
//
// Each theme: id, name, swatch (Settings tile), halo(sh) (the selection glow's hue), tint(sh, g, p) (the CSS backdrop
// behind the shelf; the bottom screen wears the same string) and, for the moving ones, fx(c): one frame on the theme
// layer's canvas (c = { g, W, H, t, dt, hue, scale, floorY, lite, still }). The backdrop is complete on its own: fx only
// adds motion on top (light trails, sparks, a rolling bar), so a still frame (c.still: the Handheld level, Low, Reduce
// motion, or anything covering the shelf) still looks finished. Static patterns are SVG / gradients painted once per
// game change (when the shelf has landed), never per frame.
(function () {
  if (window.BS_EXTRA_THEMES) return;
  const hsl = (h, s, l, a = 1) => `hsla(${((Math.round(h) % 360) + 360) % 360},${s}%,${l}%,${a})`;
  // an SVG as a CSS image (single quotes inside; only the characters a data URL needs escaped)
  const svg = (vb, body, par) => `url("data:image/svg+xml,${(`<svg xmlns='http://www.w3.org/2000/svg' viewBox='${vb}' preserveAspectRatio='${par || 'none'}'>${body}</svg>`)
    .replace(/%/g, '%25').replace(/#/g, '%23').replace(/</g, '%3C').replace(/>/g, '%3E')}")`;
  // the same "random" pattern every start (a small seeded generator)
  const seeded = s => () => { s = (s + 0x6D2B79F5) | 0; let t = Math.imul(s ^ (s >>> 15), 1 | s); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
  const r1 = n => Math.round(n * 10) / 10;
  // a console's hue drawn part of the way towards the theme's own (k = how much of the console's colour is kept)
  const pull = (sh, to, k) => to + ((((sh - to) % 360) + 540) % 360 - 180) * k;

  // ---------- Cube: indigo with a soft isometric cube pattern ----------
  const CUBE = (() => {
    const d = '#110d2e', m = '#17123d', l = '#1d1849', s = '/64px 112px', e = '12%,transparent 12.5%,transparent 87%,', f = '25%,transparent 25.5%,transparent 75%,';
    return [`linear-gradient(30deg,${d} ${e}${d} 87.5%,${d}) 0 0${s}`, `linear-gradient(150deg,${d} ${e}${d} 87.5%,${d}) 0 0${s}`,
      `linear-gradient(30deg,${d} ${e}${d} 87.5%,${d}) 32px 56px${s}`, `linear-gradient(150deg,${d} ${e}${d} 87.5%,${d}) 32px 56px${s}`,
      `linear-gradient(60deg,${l} ${f}${l} 75%,${l}) 0 0${s}`, `linear-gradient(60deg,${l} ${f}${l} 75%,${l}) 32px 56px${s}`, m].join(',');
  })();

  // ---------- Blue Shift: deep blue, glass towers in the fog, slow light trails ----------
  const BLUE = (() => {
    const rnd = seeded(7); let rects = '';
    for (let i = 0; i < 26; i++) { const side = i % 2, x = side ? 1600 - (40 + rnd() * 500) : 40 + rnd() * 500, w = 10 + rnd() * 30, h = 60 + rnd() * rnd() * 330;
      rects += `<rect x='${Math.round(x)}' y='${Math.round(513 - h)}' width='${Math.round(w)}' height='${Math.round(h + 6)}'/>`; }
    return svg('0 0 1600 900', `<defs><linearGradient id='p' x1='0' y1='0' x2='0' y2='1'><stop offset='0' stop-color='#7fb4ff' stop-opacity='.02'/><stop offset='.7' stop-color='#4d8dff' stop-opacity='.16'/><stop offset='1' stop-color='#9cc8ff' stop-opacity='.42'/></linearGradient>` +
      `<linearGradient id='t' x1='0' y1='0' x2='1' y2='0'><stop offset='0' stop-color='#8fc0ff' stop-opacity='0'/><stop offset='.55' stop-color='#bcdcff' stop-opacity='.5'/><stop offset='1' stop-color='#8fc0ff' stop-opacity='0'/></linearGradient></defs>` +
      `<g id='k' fill='url(#p)'>${rects}</g><use href='#k' transform='matrix(1 0 0 -.45 0 744)' opacity='.4'/>` +
      `<path d='M-60 250C380 120 900 330 1660 150' fill='none' stroke='url(#t)' stroke-width='1.6'/><path d='M-60 800C500 760 1000 820 1660 770' fill='none' stroke='url(#t)' stroke-width='1.2' opacity='.5'/>`);
  })();
  // the moving heads' paths: [height, speed, wave height, waves, phase] (fractions of the screen)
  const TRAILS = [[.24, .5, .05, .9, 0], [.36, .42, .04, .7, 2.1], [.7, .64, .04, 1.1, 4.4], [.88, .9, .02, .8, 1.3]];
  function blueFx(c) {
    const { g, W, H } = c, t = c.still ? 14 : c.t;
    g.globalCompositeOperation = 'lighter'; g.lineCap = 'round';
    TRAILS.forEach(([y0, sp, amp, fr, ph], k) => {
      // a head drifting across the screen (one crossing every ~25-40 s), leaving a tail that fades out behind it
      const at = s => { const u = ((s * sp * .035 + ph * .17) % 1.3) - .15; return [u * W, H * (y0 + Math.sin(u * 6.28 * fr + ph) * amp)]; };
      const N = 18; let [px, py] = at(t - 3.2);
      for (let i = 1; i <= N; i++) { const s = t - 3.2 + i * 3.2 / N, [x, y] = at(s); if (Math.abs(x - px) < W * .3) { g.strokeStyle = hsl(pull(c.hue, 205, .25), 90, 72, (i / N) * .32); g.lineWidth = Math.max(1, H * .004 * (i / N) + .5); g.beginPath(); g.moveTo(px, py); g.lineTo(x, y); g.stroke(); } px = x; py = y; }
      const gr = g.createRadialGradient(px, py, 0, px, py, H * .016); gr.addColorStop(0, 'rgba(235,245,255,.95)'); gr.addColorStop(.3, 'rgba(150,200,255,.4)'); gr.addColorStop(1, 'rgba(120,170,255,0)');
      g.fillStyle = gr; g.fillRect(px - H * .016, py - H * .016, H * .032, H * .032);
    });
  }

  // ---------- Swirl: a warm orange vortex on deep grey ----------
  // one log-spiral arm (screen space 1600 x 900, centred behind the shelf), turned three ways
  const SW_C = [800, 380], swArm = th => { const r = 26 * Math.exp(.235 * th); return [SW_C[0] + Math.cos(th) * r, SW_C[1] + Math.sin(th) * r * .82]; };
  const SWIRL = (() => {
    // as smooth curves: each piece a cubic whose handles follow the spiral's direction (dr/dθ = .235 r)
    const tan = th => { const r = 26 * Math.exp(.235 * th); return [r * (.235 * Math.cos(th) - Math.sin(th)), r * .82 * (.235 * Math.sin(th) + Math.cos(th))]; };
    const N = 40, h = 16 / N; let [x0, y0] = swArm(0), d = `M${r1(x0)} ${r1(y0)}`;
    for (let i = 1; i <= N; i++) { const a = (i - 1) * h, b = i * h, [x1, y1] = swArm(b), [ax, ay] = tan(a), [bx, by] = tan(b);
      d += `C${Math.round(x0 + ax * h / 3)} ${Math.round(y0 + ay * h / 3)} ${Math.round(x1 - bx * h / 3)} ${Math.round(y1 - by * h / 3)} ${Math.round(x1)} ${Math.round(y1)}`; x0 = x1; y0 = y1; }
    const u = (rot, sw, op) => `<use href='#a' transform='rotate(${rot} 800 380)' stroke-width='${sw}' stroke-opacity='${op}'/>`;
    return svg('0 0 1600 900', `<defs><path id='a' d='${d}'/></defs><g fill='none' stroke='#ff6a1a' stroke-linecap='round'>${u(0, 34, .05)}${u(120, 34, .04)}${u(240, 34, .05)}${u(0, 2.4, .38)}${u(120, 2, .26)}${u(240, 2.4, .32)}</g>`, 'xMidYMid slice');
  })();
  function swirlFx(c) {
    if (c.still) return; // the backdrop's arms are the still picture
    const { g, W, H, t } = c, s = Math.max(W / 1600, H / 900), ox = (W - 1600 * s) / 2, oy = (H - 900 * s) / 2;
    g.globalCompositeOperation = 'lighter';
    // sparks riding the arms inwards, brightest halfway in
    for (let k = 0; k < 12; k++) {
      const arm = k % 3, life = ((t * .04 + k * .211) % 1), th = 15 - life * 10, [x0, y0] = swArm(th), a = arm * 2.0944, dx = x0 - SW_C[0], dy = y0 - SW_C[1];
      const x = ox + (SW_C[0] + dx * Math.cos(a) - dy * Math.sin(a)) * s, y = oy + (SW_C[1] + dx * Math.sin(a) + dy * Math.cos(a)) * s;
      const al = Math.sin(life * 3.14), rr = Math.max(1.5, H * (.0035 + .004 * (1 - life)));
      const gr = g.createRadialGradient(x, y, 0, x, y, rr * 4); gr.addColorStop(0, `rgba(255,236,200,${al * .9})`); gr.addColorStop(.25, `rgba(255,150,60,${al * .45})`); gr.addColorStop(1, 'rgba(255,110,30,0)');
      g.fillStyle = gr; g.fillRect(x - rr * 4, y - rr * 4, rr * 8, rr * 8);
    }
  }

  // ---------- Forest: deep green, light through the trees ----------
  const FOREST = (() => {
    const rnd = seeded(31); let far = '', near = '';
    const pine = (x, base, h) => { const w = h * .34; return `M${r1(x)} ${r1(base - h)}L${r1(x + w * .55)} ${r1(base - h * .45)}L${r1(x + w * .3)} ${r1(base - h * .45)}L${r1(x + w)} ${r1(base)}L${r1(x - w)} ${r1(base)}L${r1(x - w * .3)} ${r1(base - h * .45)}L${r1(x - w * .55)} ${r1(base - h * .45)}Z`; };
    for (let i = 0; i < 14; i++) { const side = i % 2, x = side ? 1600 - rnd() * 430 : rnd() * 430; far += pine(x, 400, 120 + rnd() * 150); }
    for (let i = 0; i < 8; i++) { const side = i % 2, x = side ? 1600 - rnd() * 300 : rnd() * 300; near += pine(x, 410, 190 + rnd() * 170); }
    return svg('0 0 1600 400', `<path d='${far}' fill='#123a28' opacity='.8'/><path d='${near}' fill='#020b06'/>`);
  })();
  function forestFx(c) {
    const { g, W, H } = c, t = c.still ? 9 : c.t;
    g.globalCompositeOperation = 'lighter';
    for (let k = 0; k < 22; k++) {
      // fireflies: wander slowly, blink on and off; kept out of the band where the game's name sits
      const x = W * (.04 + ((k * .618 + Math.sin(t * .05 + k) * .04) % 1) * .92), y = H * (.18 + ((k * .371) % 1) * .62 + Math.sin(t * .11 + k * 2.3) * .025);
      if (y > H * .66 && Math.abs(x / W - .5) < .22) continue;
      const blink = Math.max(0, Math.sin(t * (.5 + (k % 5) * .13) + k * 1.7)), al = .12 + blink * .6, rr = Math.max(1.5, H * .006);
      const gr = g.createRadialGradient(x, y, 0, x, y, rr * 2.4); gr.addColorStop(0, `rgba(226,255,150,${al})`); gr.addColorStop(1, 'rgba(160,255,90,0)');
      g.fillStyle = gr; g.fillRect(x - rr * 2.4, y - rr * 2.4, rr * 4.8, rr * 4.8);
    }
  }

  // ---------- CRT: a glowing tube, scanlines, a slow roll bar ----------
  // the tube's rounded glass edge inside a thin black bezel, with a soft inner shadow and a faint rim of light
  const CRT_BEZEL = svg('0 0 1600 900', `<path fill-rule='evenodd' fill='#020203' d='M0 0H1600V900H0ZM84 20H1516Q1580 20 1580 84V816Q1580 880 1516 880H84Q20 880 20 816V84Q20 20 84 20Z'/>` +
    `<rect x='20' y='20' width='1560' height='860' rx='64' fill='none' stroke='#000' stroke-opacity='.28' stroke-width='60'/><rect x='20' y='20' width='1560' height='860' rx='64' fill='none' stroke='#000' stroke-opacity='.45' stroke-width='18'/>` +
    `<rect x='21.5' y='21.5' width='1557' height='857' rx='63' fill='none' stroke='#b4c8dc' stroke-opacity='.12' stroke-width='1.6'/>`);
  function crtFx(c) {
    if (c.still) return; // the backdrop is the whole still picture
    const { g, W, H, t } = c, y = ((t * .085) % 1.4 - .2) * H, h = H * .22;
    const gr = g.createLinearGradient(0, y - h / 2, 0, y + h / 2); gr.addColorStop(0, 'rgba(255,255,255,0)'); gr.addColorStop(.5, hsl(c.hue, 40, 80, .055)); gr.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = gr; g.fillRect(0, y - h / 2, W, h);
  }

  // ---------- VHS: tape glow, colour bleed, the odd tracking line ----------
  // the worn tape's marks, fixed in place: a tracking band low on the screen with colour-fringed lines, and the
  // head-switching jitter along the bottom edge
  const VHS_NOISE = (() => {
    const rnd = seeded(19); let w = '', cy = '', mg = '';
    for (let i = 0; i < 16; i++) { const x = rnd() * 1100, y = 772 + rnd() * 34; w += `<rect x='${Math.round(x)}' y='${r1(y)}' width='${Math.round(180 + rnd() * 1300)}' height='${r1(.8 + rnd() * 2.6)}' opacity='${r1(.05 + rnd() * .14)}'/>`; }
    for (let i = 0; i < 3; i++) { const x = rnd() * 900, y = 776 + rnd() * 26; cy += `<rect x='${Math.round(x)}' y='${r1(y)}' width='${Math.round(300 + rnd() * 700)}' height='1.6'/>`; mg += `<rect x='${Math.round(x + 14)}' y='${r1(y + 2.2)}' width='${Math.round(300 + rnd() * 700)}' height='1.6'/>`; }
    for (let i = 0; i < 6; i++) { const y = 884 + i * 2.6; w += `<rect x='${Math.round(rnd() * 60)}' y='${r1(y)}' width='${Math.round(1500 + rnd() * 100)}' height='1.3' opacity='${r1(.04 + rnd() * .08)}'/>`; }
    return svg('0 0 1600 900', `<g fill='#eef2ff'>${w}</g><g fill='#28d2ff' opacity='.28'>${cy}</g><g fill='#ff2a80' opacity='.26'>${mg}</g>`);
  })();
  function vhsFx(c) {
    const { g, W, H, t } = c;
    if (c.still) return;
    // light snow, a different handful of specks every frame
    g.fillStyle = 'rgba(230,235,255,.10)';
    for (let i = 0; i < 40; i++) g.fillRect(Math.random() * W, Math.random() * H, 1 + Math.random() * 2.5, 1);
    // every ~7 s a tracking band rolls up the screen for a second or so: jittery bright lines and a colour fringe
    const ph = t % 7.3; if (ph > 1.4) return;
    const y = H * (1.05 - ph / 1.4 * 1.1), bh = H * .05;
    for (let i = 0; i < 7; i++) { const yy = y + (Math.random() - .5) * bh, x0 = Math.random() * W * .3; g.fillStyle = `rgba(235,240,255,${.06 + Math.random() * .12})`; g.fillRect(x0, yy, W - x0 - Math.random() * W * .2, 1 + Math.random() * 2); }
    g.fillStyle = 'rgba(255,40,120,.05)'; g.fillRect(0, y - bh * .6, W, bh * .5); g.fillStyle = 'rgba(40,200,255,.05)'; g.fillRect(0, y + bh * .1, W, bh * .5);
  }

  // ---------- Synthwave: a striped sun on the horizon, a neon grid running towards you ----------
  const HZ = .57; // the horizon, as a fraction of the screen (just above the shelf's floor)
  const SYN_GRID = (() => {
    let d = ''; for (let i = -14; i <= 14; i++) d += `M800 0L${800 + i * 175} 400`;
    for (let k = 1; k <= 9; k++) { const y = Math.round(400 * Math.pow(k / 9, 2.1)); d += `M0 ${y}H1600`; }
    return svg('0 0 1600 400', `<defs><linearGradient id='f' gradientUnits='userSpaceOnUse' x1='0' y1='0' x2='0' y2='400'><stop offset='0' stop-color='#ff4fd8' stop-opacity='.06'/><stop offset='1' stop-color='#ff4fd8' stop-opacity='.62'/></linearGradient></defs><path d='${d}' fill='none' stroke='url(#f)' stroke-width='1.4' vector-effect='non-scaling-stroke'/>`);
  })();
  const SYN_SUN = svg('0 0 200 100', `<defs><linearGradient id='s' x1='0' y1='0' x2='0' y2='.5'><stop offset='0' stop-color='#ffd84a'/><stop offset='.5' stop-color='#ff7a4e'/><stop offset='1' stop-color='#ff2d8a'/></linearGradient>` +
    `<clipPath id='c'><rect width='200' height='48'/><rect y='53' width='200' height='9'/><rect y='66' width='200' height='7'/><rect y='77' width='200' height='5'/><rect y='86' width='200' height='4'/><rect y='94' width='200' height='3'/></clipPath></defs>` +
    `<circle cx='100' cy='100' r='99' fill='url(#s)' clip-path='url(#c)'/>`, 'xMidYMax meet');
  function synthFx(c) {
    const { g, W, H } = c, hy = H * HZ, t = c.still ? 3 : c.t;
    g.globalCompositeOperation = 'lighter';
    // stars over the sunset
    const rnd = seeded(5);
    for (let i = 0; i < 70; i++) { const x = rnd() * W, y = rnd() * hy * .62, z = rnd(), tw = .5 + .5 * Math.sin(t * (1 + z * 2) + i); g.fillStyle = `rgba(255,230,255,${(.12 + z * .4) * (c.still ? .8 : tw)})`; g.fillRect(x, y, z > .9 ? 2 : 1.2, z > .9 ? 2 : 1.2); }
    if (c.still) return;
    // the ground rushing towards you: a bright line sliding down the grid, plus pulses racing along its rails
    for (let k = 0; k < 2; k++) {
      const u = ((t * .42 + k * .5) % 1), y = hy + (H - hy) * Math.pow(u, 2.1);
      const gr = g.createLinearGradient(0, 0, W, 0); gr.addColorStop(0, 'rgba(255,80,220,0)'); gr.addColorStop(.5, `rgba(255,120,235,${.08 + u * .3})`); gr.addColorStop(1, 'rgba(255,80,220,0)');
      g.fillStyle = gr; g.fillRect(0, y - 1, W, Math.max(1.5, 2.5 * u * c.scale));
    }
    for (let k = 0; k < 6; k++) {
      const lane = [-9, -6, -4, 4, 6, 9][k], u = ((t * (.18 + k * .03) + k * .29) % 1), e = Math.pow(u, 2.1);
      const x = W / 2 + lane * 175 / 1600 * W * e, y = hy + (H - hy) * e, rr = H * (.004 + .012 * e);
      const gr = g.createRadialGradient(x, y, 0, x, y, rr * 3); gr.addColorStop(0, hsl(c.hue + 180 * (k % 2), 100, 75, .7)); gr.addColorStop(1, 'rgba(0,0,0,0)');
      g.fillStyle = gr; g.fillRect(x - rr * 3, y - rr * 3, rr * 6, rr * 6);
    }
  }

  window.BS_EXTRA_THEMES = [
    { id: 'cube', name: 'Cube', halo: sh => sh,
      swatch: `linear-gradient(30deg,#110d2e 12%,transparent 12.5%,transparent 87%,#110d2e 87.5%) 0 0/24px 42px,linear-gradient(150deg,#110d2e 12%,transparent 12.5%,transparent 87%,#110d2e 87.5%) 0 0/24px 42px,linear-gradient(30deg,#110d2e 12%,transparent 12.5%,transparent 87%,#110d2e 87.5%) 12px 21px/24px 42px,linear-gradient(150deg,#110d2e 12%,transparent 12.5%,transparent 87%,#110d2e 87.5%) 12px 21px/24px 42px,linear-gradient(60deg,#2a2366 25%,transparent 25.5%,transparent 75%,#2a2366 75%) 0 0/24px 42px,linear-gradient(60deg,#2a2366 25%,transparent 25.5%,transparent 75%,#2a2366 75%) 12px 21px/24px 42px,#1d1752`,
      tint: sh => [`radial-gradient(ellipse 46% 30% at 50% 57%,${hsl(sh, 72, 52, .36)},transparent 72%)`, `radial-gradient(ellipse 44% 48% at 50% 44%,rgba(14,11,40,.8),transparent 78%)`,
        `radial-gradient(ellipse 55% 45% at 18% 8%,${hsl(sh, 60, 48, .16)},transparent 70%)`, `linear-gradient(180deg,rgba(8,6,24,.55),transparent 24%,transparent 58%,rgba(5,4,16,.9))`, CUBE].join(',') },
    { id: 'blueshift', name: 'Blue Shift', halo: sh => pull(sh, 215, .35), fx: blueFx,
      swatch: 'linear-gradient(165deg,transparent 42%,rgba(190,220,255,.7) 44%,transparent 46%),linear-gradient(90deg,transparent 12%,rgba(110,170,255,.35) 12% 17%,transparent 17% 26%,rgba(110,170,255,.25) 26% 29%,transparent 29% 70%,rgba(110,170,255,.3) 70% 76%,transparent 76%) 0 100%/100% 55% no-repeat,linear-gradient(180deg,#000615,#00134a 55%,#1a4fd0 62%,#000a2a)',
      tint: sh => [`radial-gradient(ellipse 70% 20% at 50% 57%,rgba(70,140,255,.42),transparent 70%)`, `radial-gradient(ellipse 46% 34% at 50% 58%,${hsl(sh, 80, 52, .16)},transparent 70%)`,
        `${BLUE} 0 0/100% 100% no-repeat`, 'linear-gradient(180deg,#000514 0%,#000f3c 38%,#08287f 55.5%,#0b3197 57%,#03185a 59%,#000a2a 76%,#00030e 100%)'].join(',') },
    { id: 'swirl', name: 'Swirl', halo: () => 22, fx: swirlFx,
      swatch: 'radial-gradient(circle at 50% 46%,transparent 0 14%,rgba(255,106,26,.85) 15% 18%,transparent 19% 30%,rgba(255,106,26,.5) 31% 33%,transparent 34% 48%,rgba(255,106,26,.3) 49% 50.5%,transparent 52%),linear-gradient(180deg,#1d1e23,#0e0f12)',
      tint: sh => [`radial-gradient(ellipse 34% 30% at 50% 44%,rgba(255,120,40,.14),transparent 70%)`, `radial-gradient(ellipse 60% 30% at 50% 102%,${hsl(sh, 55, 45, .16)},transparent 72%)`,
        `${SWIRL} 50% 50%/cover no-repeat`, 'radial-gradient(ellipse 90% 80% at 50% 45%,transparent 50%,rgba(0,0,0,.55))', 'linear-gradient(180deg,#1c1d22,#131418 60%,#0b0c0e)'].join(',') },
    { id: 'pocket', name: 'Pocket', halo: () => 78,
      swatch: 'repeating-linear-gradient(0deg,rgba(10,30,10,.35) 0 1px,transparent 1px 4px),repeating-linear-gradient(90deg,rgba(10,30,10,.35) 0 1px,transparent 1px 4px),radial-gradient(ellipse 80% 70% at 50% 40%,#8bac0f,#306230 75%),#0f380f',
      tint: sh => ['repeating-linear-gradient(0deg,rgba(6,22,8,.32) 0 .75px,transparent .75px 3px)', 'repeating-linear-gradient(90deg,rgba(6,22,8,.32) 0 .75px,transparent .75px 3px)',
        'linear-gradient(112deg,transparent 22%,rgba(235,255,200,.035) 22.4%,rgba(235,255,200,.035) 38%,transparent 38.4%)', // the screen's glass
        'radial-gradient(ellipse 90% 80% at 50% 45%,transparent 55%,rgba(6,18,8,.78))', `radial-gradient(ellipse 50% 26% at 50% 60%,${hsl(sh, 35, 45, .10)},transparent 72%)`,
        'radial-gradient(ellipse 72% 58% at 50% 40%,rgba(150,176,60,.30),transparent 74%)', 'linear-gradient(180deg,#22401f,#2c5229 42%,#213f20 70%,#112613)'].join(',') },
    { id: 'oled', name: 'OLED', halo: sh => sh,
      swatch: 'radial-gradient(ellipse 34% 9% at 50% 66%,rgba(120,150,255,.7),transparent 75%),#000',
      tint: sh => `radial-gradient(ellipse 24% 6% at 50% 60%,${hsl(sh, 85, 52, .22)},transparent 75%),#000` },
    { id: 'walnut', name: 'Walnut', halo: () => 30,
      swatch: 'radial-gradient(ellipse 80% 60% at 50% 10%,rgba(255,180,100,.35),transparent 70%),repeating-linear-gradient(90deg,rgba(0,0,0,.4) 0 1px,transparent 1px 26px),repeating-linear-gradient(91deg,rgba(0,0,0,.12) 0 2px,transparent 2px 5px,rgba(255,210,170,.06) 5px 7px),linear-gradient(180deg,#4a2c1b,#2c180e)',
      tint: sh => [`radial-gradient(ellipse 70% 52% at 50% 12%,rgba(255,170,90,.24),transparent 72%)`, `radial-gradient(ellipse 40% 34% at 14% 0%,${hsl(sh, 60, 55, .10)},transparent 70%)`,
        'linear-gradient(180deg,transparent 58.2%,rgba(255,196,130,.13) 58.5%,rgba(255,196,130,.04) 59.1%,rgba(10,5,2,.55) 59.4%,rgba(8,4,2,.78))','repeating-linear-gradient(90deg,rgba(0,0,0,.34) 0 2px,rgba(255,215,175,.05) 2px 3px,transparent 3px 240px)',
        'repeating-linear-gradient(90.6deg,rgba(0,0,0,.10) 0 2px,transparent 2px 9px,rgba(255,220,180,.03) 9px 12px,transparent 12px 23px)', 'repeating-linear-gradient(89.2deg,rgba(0,0,0,.08) 0 1px,transparent 1px 37px)',
        'repeating-linear-gradient(91.4deg,rgba(255,200,150,.035) 0 6px,transparent 6px 61px)', 'linear-gradient(180deg,#3c2416,#2c180e 55%,#1a0d07)'].join(',') },
    { id: 'forest', name: 'Forest', halo: sh => pull(sh, 120, .3), fx: forestFx,
      swatch: 'linear-gradient(135deg,transparent 30%,rgba(210,255,190,.25) 34%,transparent 40%,transparent 48%,rgba(210,255,190,.18) 51%,transparent 56%),linear-gradient(60deg,#04140c 18%,transparent 18%) 0 100%/30% 40% repeat-x,linear-gradient(180deg,#123826,#0a2218 60%,#04100b)',
      tint: sh => ['radial-gradient(ellipse 30% 14% at 50% 77%,rgba(3,12,8,.55),transparent 75%)', `${FOREST} 0 100%/100% 46% no-repeat`,
        'conic-gradient(from 112deg at 16% -32%,transparent 0deg,rgba(210,255,190,.1) 5deg,transparent 11deg,transparent 15deg,rgba(210,255,190,.075) 19deg,transparent 25deg,transparent 29deg,rgba(225,255,205,.11) 33deg,transparent 39deg,transparent 44deg,rgba(210,255,190,.06) 47deg,transparent 53deg,transparent 360deg)',
        `radial-gradient(ellipse 44% 30% at 50% 58%,${hsl(pull(sh, 120, .3), 40, 40, .2)},transparent 72%)`, 'radial-gradient(ellipse 50% 40% at 16% 0%,rgba(170,230,160,.13),transparent 70%)',
        'linear-gradient(180deg,#0e2e20,#0a2418 45%,#071a12 66%,#04100b)'].join(',') },
    { id: 'crt', name: 'CRT', halo: sh => sh, fx: crtFx,
      swatch: 'repeating-linear-gradient(0deg,rgba(0,0,0,.45) 0 1px,transparent 1px 3px),radial-gradient(ellipse 70% 62% at 50% 50%,transparent 55%,#000 92%),radial-gradient(ellipse 60% 50% at 50% 50%,rgba(80,170,255,.75),rgba(20,40,70,.4) 70%),#06080b',
      tint: sh => [`${CRT_BEZEL} 0 0/100% 100% no-repeat`, 'linear-gradient(158deg,rgba(255,255,255,.06),transparent 30%)',
        'radial-gradient(ellipse 80% 76% at 50% 50%,transparent 60%,rgba(0,0,0,.55))',
        'repeating-linear-gradient(0deg,rgba(0,0,0,.36) 0 1.5px,transparent 1.5px 3px)', 'repeating-linear-gradient(90deg,rgba(255,70,70,.05) 0 1px,rgba(70,255,110,.05) 1px 2px,rgba(80,120,255,.05) 2px 3px)',
        `radial-gradient(ellipse 64% 58% at 50% 48%,${hsl(sh, 75, 50, .56)},${hsl(sh, 65, 28, .32)} 55%,transparent 82%)`, 'linear-gradient(180deg,#0b0e13,#07090c)'].join(',') },
    { id: 'vhs', name: 'VHS', halo: sh => sh, fx: vhsFx,
      swatch: 'linear-gradient(180deg,transparent 76%,rgba(255,255,255,.35) 77%,transparent 79%,rgba(255,255,255,.2) 82%,transparent 84%),radial-gradient(ellipse 40% 40% at 42% 45%,rgba(40,210,255,.55),transparent 70%),radial-gradient(ellipse 40% 40% at 58% 50%,rgba(255,50,150,.5),transparent 70%),#0b0a1c',
      tint: sh => [`${VHS_NOISE} 0 0/100% 100% no-repeat`, 'repeating-linear-gradient(0deg,rgba(0,0,0,.24) 0 1.5px,transparent 1.5px 4.5px)',
        'radial-gradient(ellipse 92% 76% at 50% 50%,transparent 52%,rgba(0,0,0,.6))', 'radial-gradient(ellipse 34% 32% at 43.5% 46%,rgba(40,210,255,.27),transparent 70%)',
        'radial-gradient(ellipse 34% 32% at 56.5% 49%,rgba(255,40,120,.25),transparent 70%)', `radial-gradient(ellipse 44% 36% at 50% 48%,${hsl(sh, 70, 45, .22)},transparent 72%)`,
        'linear-gradient(90deg,rgba(255,30,110,.08),transparent 24%,transparent 76%,rgba(30,190,255,.08))', 'linear-gradient(180deg,#0d0b1f,#110e29 50%,#08071b)'].join(',') },
    { id: 'synth', name: 'Synthwave', halo: sh => sh, fx: synthFx,
      swatch: 'radial-gradient(circle at 50% 62%,#ffd36a 0 22%,transparent 23%),linear-gradient(180deg,transparent 62%,#ff4fd8 62% 63%,transparent 63%),repeating-linear-gradient(0deg,rgba(255,79,216,.6) 0 1px,transparent 1px 7px) 0 100%/100% 37% no-repeat,linear-gradient(180deg,#120428,#5a1060 50%,#ff6a5a 62%,#120420 62%)',
      tint: sh => ['radial-gradient(ellipse 30% 13% at 50% 77%,rgba(8,2,18,.6),transparent 75%)', `${SYN_GRID} 0 100%/100% 43% no-repeat`,
        `radial-gradient(ellipse 60% 7% at 50% 57%,${hsl(sh, 95, 66, .5)},transparent 70%)`, `${SYN_SUN} 50% 34.85%/auto 34% no-repeat`,
        'radial-gradient(ellipse 40% 30% at 50% 50%,rgba(255,90,140,.22),transparent 72%)', 'linear-gradient(180deg,#0b0420 0%,#1c0838 28%,#47105c 45%,#a3246e 53%,#ff6a5a 56.9%,#14062a 57%,#0c0418 76%,#05020c 100%)'].join(',') }
  ];
})();
