'use strict';
// ---------------------------------------------------------------
// Arte procedural: fondos con parallax, tiles luminosos, personajes
// ---------------------------------------------------------------
const LAYER_W = 1600;

function buildArt(wi) {
  const W = WORLDS[wi], rng = mulberry32(333 + wi * 71);
  const art = { wi };
  // Cielo
  const sky = makeCanvas(4, VIEW_H), sg = sky.getContext('2d');
  const gr = sg.createLinearGradient(0, 0, 0, VIEW_H);
  gr.addColorStop(0, W.sky[0]); gr.addColorStop(0.55, W.sky[1]); gr.addColorStop(1, W.sky[2]);
  sg.fillStyle = gr; sg.fillRect(0, 0, 4, VIEW_H); art.sky = sky;

  // Estrellas + nebulosa
  const st = makeCanvas(LAYER_W, VIEW_H), g = st.getContext('2d');
  g.globalCompositeOperation = 'lighter';
  for (let i = 0; i < 6; i++) {
    const c = [W.accent, W.accent2, W.accent3][i % 3];
    const x = rng() * LAYER_W, y = rng() * VIEW_H * 0.6, r = 150 + rng() * 250;
    g.globalAlpha = 0.12 + rng() * 0.1; g.drawImage(glowSprite(c), x - r, y - r * 0.6, r * 2, r * 1.2);
    if (x < r) g.drawImage(glowSprite(c), x - r + LAYER_W, y - r * 0.6, r * 2, r * 1.2);
    if (x > LAYER_W - r) g.drawImage(glowSprite(c), x - r - LAYER_W, y - r * 0.6, r * 2, r * 1.2);
  }
  g.globalAlpha = 1;
  const nStars = W.layer === 'cave' ? 60 : W.layer === 'ocean' ? 90 : 320;
  for (let i = 0; i < nStars; i++) {
    const x = rng() * LAYER_W, y = rng() * VIEW_H * 0.85, s = rng() < 0.08 ? 2.2 : 0.6 + rng() * 1.1;
    g.fillStyle = rng() < 0.3 ? W.accent3 : '#ffffff'; g.globalAlpha = 0.3 + rng() * 0.7;
    g.beginPath(); g.arc(x, y, s, 0, 7); g.fill();
    if (s > 2) { g.globalAlpha = 0.5; g.drawImage(glowSprite('#ffffff'), x - 12, y - 12, 24, 24); }
  }
  g.globalAlpha = 1;
  art.stars = st;
  art.twinkles = []; for (let i = 0; i < 40; i++) art.twinkles.push({ x: rng(), y: rng() * 0.7, p: rng() * 6, s: 3 + rng() * 5 });

  // Capas lejana y media
  art.far = makeCanvas(LAYER_W, VIEW_H); art.mid = makeCanvas(LAYER_W, VIEW_H);
  paintLayers(art, W, rng);
  art.tiles = buildTiles(W, wi);
  art.ambient = [];
  for (let i = 0; i < 45; i++) art.ambient.push({ x: rng() * 2000, y: rng() * VIEW_H, p: rng() * 6, s: 0.5 + rng(), v: 0.5 + rng() });
  return art;
}

// Silueta de montañas con ruido
function ridge(g, rng, base, amp, rough, color, rim, seg) {
  seg = seg || 40;
  const pts = []; const n = Math.ceil(LAYER_W / seg) + 1;
  let h = 0; const f = [];
  for (let i = 0; i < n; i++) f.push(rng());
  for (let i = 0; i < n; i++) {
    const a = (f[i] + f[(i + 1) % (n - 1)] * 0.5 + f[(i + 2) % (n - 1)] * 0.3) / 1.8;
    h = base - a * amp - Math.sin(i / n * Math.PI * 4) * amp * rough; pts.push([i * seg, h]);
  }
  pts[n - 1][1] = pts[0][1];
  g.beginPath(); g.moveTo(0, VIEW_H); for (const [x, y] of pts) g.lineTo(x, y); g.lineTo(LAYER_W, VIEW_H); g.closePath();
  g.fillStyle = color; g.fill();
  if (rim) {
    g.save(); g.shadowColor = rim; g.shadowBlur = 14; g.strokeStyle = rgba(rim, 0.6); g.lineWidth = 2;
    g.beginPath(); for (let i = 0; i < pts.length; i++) i ? g.lineTo(pts[i][0], pts[i][1]) : g.moveTo(pts[i][0], pts[i][1]); g.stroke(); g.restore();
  }
  return pts;
}
function wrapDraw(g, x, w, fn) { fn(x); if (x - w < 0) fn(x + LAYER_W); if (x + w > LAYER_W) fn(x - LAYER_W); }

function paintLayers(art, W, rng) {
  const fg = art.far.getContext('2d'), mg = art.mid.getContext('2d');
  const dark1 = W.ground2, dark2 = W.ground;
  const glowDot = (g, x, y, r, c, a) => { g.globalCompositeOperation = 'lighter'; g.globalAlpha = a || 0.8; g.drawImage(glowSprite(c), x - r, y - r, r * 2, r * 2); g.globalAlpha = 1; g.globalCompositeOperation = 'source-over'; };
  switch (W.layer) {
    case 'forest': {
      ridge(fg, rng, 430, 120, 0.2, '#041a1c', W.accent, 50);
      for (let i = 0; i < 26; i++) { // árboles lejanos
        const x = rng() * LAYER_W, h = 180 + rng() * 200, base = 470;
        wrapDraw(fg, x, 60, X => {
          fg.fillStyle = '#031416'; fg.fillRect(X - 5, base - h, 10, h);
          fg.beginPath(); fg.ellipse(X, base - h, 30 + rng() * 25, 50 + rng() * 30, 0, 0, 7); fg.fill();
          for (let k = 0; k < 4; k++) glowDot(fg, X + (rng() - 0.5) * 50, base - h + (rng() - 0.5) * 70, 6, rng() < 0.5 ? W.accent : W.accent2, 0.7);
        });
      }
      ridge(mg, rng, 520, 60, 0.3, '#020d0e', null, 60);
      for (let i = 0; i < 9; i++) { // hongos gigantes
        const x = rng() * LAYER_W, h = 140 + rng() * 150, r = 45 + rng() * 45, c = rng() < 0.5 ? W.accent : W.accent2;
        wrapDraw(mg, x, r + 20, X => {
          mg.fillStyle = '#031212'; mg.beginPath(); mg.moveTo(X - 9, 560); mg.quadraticCurveTo(X - 14, 560 - h / 2, X - 6, 560 - h); mg.lineTo(X + 6, 560 - h); mg.quadraticCurveTo(X + 14, 560 - h / 2, X + 9, 560); mg.fill();
          mg.save(); mg.shadowColor = c; mg.shadowBlur = 30;
          const cg = mg.createLinearGradient(0, 560 - h - r * 0.7, 0, 560 - h);
          cg.addColorStop(0, rgba(c, 0.55)); cg.addColorStop(1, '#021010');
          mg.fillStyle = cg; mg.beginPath(); mg.ellipse(X, 560 - h, r, r * 0.6, 0, Math.PI, 0); mg.fill(); mg.restore();
          for (let k = 0; k < 5; k++) glowDot(mg, X + (rng() - 0.5) * r * 1.4, 560 - h - rng() * r * 0.45, 5, '#ffffff', 0.5);
          glowDot(mg, X, 560 - h, r * 1.3, c, 0.25);
        });
      }
      break;
    }
    case 'cave': {
      // techo con estalactitas
      for (const [g, col, len, n] of [[fg, '#0e0720', 170, 40], [mg, '#070312', 110, 26]]) {
        g.fillStyle = col; g.beginPath(); g.moveTo(0, 0);
        for (let i = 0; i <= n; i++) { const x = i / n * LAYER_W; g.lineTo(x - LAYER_W / n / 2, 20 + rng() * 20); g.lineTo(x, (i === 0 || i === n) ? 40 : 40 + rng() * len); }
        g.lineTo(LAYER_W, 0); g.fill();
      }
      ridge(fg, rng, 470, 140, 0.25, '#0d0620', W.accent, 30);
      const crystal = (g, x, y, h, w, c, ang) => {
        g.save(); g.translate(x, y); g.rotate(ang); g.shadowColor = c; g.shadowBlur = 25;
        const cg = g.createLinearGradient(-w, 0, w, 0); cg.addColorStop(0, rgba(c, 0.25)); cg.addColorStop(0.5, rgba(c, 0.85)); cg.addColorStop(1, rgba(c, 0.15));
        g.fillStyle = cg; g.beginPath(); g.moveTo(-w, 0); g.lineTo(-w * 0.8, -h * 0.8); g.lineTo(0, -h); g.lineTo(w * 0.8, -h * 0.8); g.lineTo(w, 0); g.fill();
        g.restore();
      };
      for (let i = 0; i < 18; i++) { const x = rng() * LAYER_W; wrapDraw(fg, x, 60, X => { for (let k = 0; k < 3; k++) crystal(fg, X + k * 14 - 14, 470 - rng() * 60, 50 + rng() * 90, 10 + rng() * 8, rng() < 0.6 ? W.accent : W.accent3, (k - 1) * 0.3); }); }
      ridge(mg, rng, 540, 60, 0.3, '#060210', null, 40);
      for (let i = 0; i < 8; i++) { const x = rng() * LAYER_W; wrapDraw(mg, x, 80, X => { for (let k = 0; k < 4; k++) crystal(mg, X + k * 22 - 33, 560, 90 + rng() * 160, 16 + rng() * 10, rng() < 0.6 ? W.accent2 : W.accent, (k - 1.5) * 0.22); glowDot(mg, X, 520, 140, W.accent2, 0.18); }); }
      break;
    }
    case 'aurora': {
      ridge(fg, rng, 420, 200, 0.2, '#0c1a33', '#dff3ff', 60);
      // nieve en picos
      ridge(mg, rng, 500, 90, 0.25, '#08132a', '#9fd8ff', 50);
      for (let i = 0; i < 40; i++) { // pinos
        const x = rng() * LAYER_W, h = 60 + rng() * 90, base = 560;
        wrapDraw(mg, x, 30, X => {
          mg.fillStyle = '#050d1e'; mg.beginPath(); mg.moveTo(X, base - h); mg.lineTo(X - h * 0.28, base); mg.lineTo(X + h * 0.28, base); mg.fill();
          mg.strokeStyle = rgba('#cfeaff', 0.5); mg.lineWidth = 1.5; mg.beginPath(); mg.moveTo(X, base - h); mg.lineTo(X - h * 0.14, base - h * 0.5); mg.stroke();
        });
      }
      break;
    }
    case 'ocean': {
      fg.globalCompositeOperation = 'lighter';
      for (let i = 0; i < 9; i++) { // rayos de luz
        const x = rng() * LAYER_W, w = 40 + rng() * 80; const lg = fg.createLinearGradient(0, 0, 0, VIEW_H);
        lg.addColorStop(0, rgba(W.accent, 0.16)); lg.addColorStop(1, rgba(W.accent, 0));
        fg.fillStyle = lg; fg.beginPath(); fg.moveTo(x, 0); fg.lineTo(x + w, 0); fg.lineTo(x + w * 2 + 120, VIEW_H); fg.lineTo(x + 120, VIEW_H); fg.fill();
      }
      fg.globalCompositeOperation = 'source-over';
      ridge(fg, rng, 480, 130, 0.2, '#03182a', W.accent3, 40);
      ridge(mg, rng, 540, 50, 0.3, '#010d18', null, 40);
      for (let i = 0; i < 30; i++) { // algas
        const x = rng() * LAYER_W, h = 120 + rng() * 220, c = rng() < 0.5 ? W.accent : W.accent3;
        wrapDraw(mg, x, 40, X => {
          mg.strokeStyle = '#021624'; mg.lineWidth = 7; mg.lineCap = 'round'; mg.beginPath(); mg.moveTo(X, 580);
          for (let k = 1; k <= 10; k++) mg.lineTo(X + Math.sin(k * 0.9 + x) * 14, 580 - h * k / 10); mg.stroke();
          for (let k = 3; k <= 10; k += 2) glowDot(mg, X + Math.sin(k * 0.9 + x) * 14, 580 - h * k / 10, 7, c, 0.8);
        });
      }
      break;
    }
    case 'volcano': {
      const hg = fg.createLinearGradient(0, 250, 0, VIEW_H); hg.addColorStop(0, 'rgba(255,80,20,0)'); hg.addColorStop(1, 'rgba(255,80,20,0.25)');
      fg.fillStyle = hg; fg.fillRect(0, 250, LAYER_W, VIEW_H);
      for (let i = 0; i < 4; i++) { // volcanes
        const x = 150 + i * 400 + rng() * 100, h = 200 + rng() * 120, w = 260 + rng() * 120, base = 520;
        wrapDraw(fg, x, w, X => {
          fg.fillStyle = '#1a0605'; fg.beginPath(); fg.moveTo(X - w, base); fg.lineTo(X - 30, base - h); fg.lineTo(X + 30, base - h); fg.lineTo(X + w, base); fg.fill();
          fg.save(); fg.shadowColor = W.accent; fg.shadowBlur = 20; fg.strokeStyle = W.accent; fg.lineWidth = 3;
          for (let k = 0; k < 3; k++) { fg.beginPath(); let px = X + (k - 1) * 18, py = base - h; fg.moveTo(px, py); for (let s = 0; s < 8; s++) { px += (rng() - 0.5) * 30 + (k - 1) * 10; py += h / 8; fg.lineTo(px, py); } fg.stroke(); }
          fg.restore(); glowDot(fg, X, base - h, 90, W.accent, 0.6); glowDot(fg, X, base - h - 40, 140, W.accent3, 0.25);
        });
      }
      ridge(mg, rng, 540, 90, 0.3, '#0e0303', W.accent3, 30);
      break;
    }
    case 'cosmic': {
      const island = (g, x, y, w, c) => {
        g.fillStyle = '#150c24'; g.beginPath(); g.moveTo(x - w, y); g.quadraticCurveTo(x, y - 14, x + w, y); g.lineTo(x + w * 0.3, y + w * 0.9); g.lineTo(x - w * 0.2, y + w * 0.7); g.closePath(); g.fill();
        g.save(); g.shadowColor = c; g.shadowBlur = 16; g.strokeStyle = rgba(c, 0.8); g.lineWidth = 2; g.beginPath(); g.moveTo(x - w, y); g.quadraticCurveTo(x, y - 14, x + w, y); g.stroke(); g.restore();
        for (let k = 0; k < 3; k++) { const tx = x + (rng() - 0.5) * w, th = 20 + rng() * 30; g.fillStyle = '#150c24'; g.fillRect(tx - 2, y - th, 4, th); glowDot(g, tx, y - th, 14, c, 0.9); }
        glowDot(g, x, y + w * 0.6, w * 0.8, c, 0.15);
      };
      for (let i = 0; i < 9; i++) { const x = rng() * LAYER_W; wrapDraw(fg, x, 80, X => island(fg, X, 200 + rng() * 220, 30 + rng() * 40, rng() < 0.5 ? W.accent : W.accent2)); }
      ridge(mg, rng, 540, 70, 0.3, '#0c0616', W.accent2, 40);
      for (let i = 0; i < 5; i++) { const x = rng() * LAYER_W; wrapDraw(mg, x, 110, X => island(mg, X, 330 + rng() * 120, 60 + rng() * 40, rng() < 0.5 ? W.accent3 : W.accent)); }
      break;
    }
  }
}

// Tiles pre-renderizados a 2x
function buildTiles(W, wi) {
  const S = TILE * 2, T = {};
  const mk = fn => { const c = makeCanvas(S, S), g = c.getContext('2d'); g.scale(2, 2); fn(g); return c; };
  const rng = mulberry32(99 + wi);
  const fillBase = (g, seed) => {
    g.fillStyle = W.ground; g.fillRect(0, 0, TILE, TILE);
    const r = mulberry32(seed);
    for (let i = 0; i < 5; i++) { g.fillStyle = rgba(r() < 0.5 ? W.accent : W.accent3, 0.05 + r() * 0.08); g.beginPath(); g.arc(r() * TILE, r() * TILE, 1 + r() * 3, 0, 7); g.fill(); }
    g.strokeStyle = 'rgba(255,255,255,0.03)'; g.beginPath(); g.moveTo(r() * TILE, 0); g.lineTo(r() * TILE, TILE); g.stroke();
  };
  const edge = (c) => { const e = makeCanvas(2, 32), eg = e.getContext('2d'), gr = eg.createLinearGradient(0, 0, 0, 32);
    gr.addColorStop(0, rgba(c, 0)); gr.addColorStop(0.42, rgba(c, 0.55)); gr.addColorStop(0.5, 'rgba(255,255,255,0.95)'); gr.addColorStop(0.58, rgba(c, 0.55)); gr.addColorStop(1, rgba(c, 0));
    eg.fillStyle = gr; eg.fillRect(0, 0, 2, 32); return e; };
  T.edge = edge(W.accent); T.edge2 = edge(W.accent2);
  T.fill = [0, 1, 2].map(i => mk(g => fillBase(g, 10 + i)));
  T.top = [0, 1, 2].map(i => mk(g => {
    fillBase(g, 20 + i);
    const r = mulberry32(30 + i);
    const eg = g.createLinearGradient(0, 0, 0, 14); eg.addColorStop(0, rgba(W.accent, 0.95)); eg.addColorStop(0.25, rgba(W.accent, 0.35)); eg.addColorStop(1, rgba(W.accent, 0));
    g.fillStyle = eg; g.fillRect(0, 0, TILE, 14);
    g.fillStyle = '#ffffff'; g.globalAlpha = 0.8; g.fillRect(0, 0, TILE, 1.5); g.globalAlpha = 1;
    // detalle superior según mundo
    g.fillStyle = rgba(W.accent2, 0.9); g.strokeStyle = rgba(W.accent, 0.9); g.lineWidth = 1.5;
    for (let k = 0; k < 6; k++) {
      const x = r() * TILE, h = 3 + r() * 6;
      if (W.deco === 'mushroom' || W.deco === 'coral' || W.deco === 'flower') { g.beginPath(); g.moveTo(x, 1); g.quadraticCurveTo(x + 2, -h / 2, x + (r() - 0.5) * 5, -h + 1); g.stroke(); }
      else if (W.deco === 'crystal' || W.deco === 'ice') { g.beginPath(); g.moveTo(x - 2, 1); g.lineTo(x, -h); g.lineTo(x + 2, 1); g.fill(); }
      else { g.fillStyle = rgba(W.accent2, 0.6); g.fillRect(x, 3 + r() * 10, 1 + r() * 4, 1.5); }
    }
  }));
  T.brick = mk(g => {
    g.fillStyle = W.ground2; g.fillRect(0, 0, TILE, TILE);
    g.strokeStyle = rgba(W.accent3, 0.55); g.lineWidth = 1.5; g.strokeRect(1.5, 1.5, TILE - 3, TILE - 3);
    g.strokeStyle = rgba(W.accent3, 0.25); g.beginPath(); g.moveTo(0, TILE / 2); g.lineTo(TILE, TILE / 2); g.moveTo(TILE / 2, 0); g.lineTo(TILE / 2, TILE / 2); g.moveTo(TILE / 4, TILE / 2); g.lineTo(TILE / 4, TILE); g.moveTo(TILE * 0.75, TILE / 2); g.lineTo(TILE * 0.75, TILE); g.stroke();
    g.fillStyle = rgba(W.accent3, 0.12); g.fillRect(3, 3, TILE - 6, 4);
  });
  const star = (g, cx, cy, r, c) => { g.beginPath(); for (let i = 0; i < 8; i++) { const a = i * Math.PI / 4 - Math.PI / 2, rr = i % 2 ? r * 0.38 : r; g.lineTo(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr); } g.closePath(); g.fillStyle = c; g.fill(); };
  T.box = mk(g => {
    const gr = g.createLinearGradient(0, 0, 0, TILE); gr.addColorStop(0, '#2a1d05'); gr.addColorStop(1, '#140c02');
    g.fillStyle = gr; g.fillRect(0, 0, TILE, TILE);
    g.strokeStyle = '#ffd24a'; g.lineWidth = 2; g.strokeRect(2, 2, TILE - 4, TILE - 4);
    g.save(); g.shadowColor = '#ffe27a'; g.shadowBlur = 10; star(g, TILE / 2, TILE / 2, 12, '#fff2b0'); g.restore();
    g.fillStyle = '#ffd24a'; for (const [x, y] of [[5, 5], [TILE - 5, 5], [5, TILE - 5], [TILE - 5, TILE - 5]]) { g.beginPath(); g.arc(x, y, 1.6, 0, 7); g.fill(); }
  });
  T.used = mk(g => { g.fillStyle = '#16121c'; g.fillRect(0, 0, TILE, TILE); g.strokeStyle = 'rgba(255,255,255,0.15)'; g.lineWidth = 2; g.strokeRect(2, 2, TILE - 4, TILE - 4); star(g, TILE / 2, TILE / 2, 9, 'rgba(255,255,255,0.08)'); });
  T.oneway = mk(g => {
    g.fillStyle = W.ground2; g.beginPath(); g.moveTo(0, 0); g.lineTo(TILE, 0); g.lineTo(TILE - 3, 14); g.lineTo(3, 14); g.fill();
    g.fillStyle = rgba(W.accent2, 0.9); g.fillRect(0, 0, TILE, 2);
    g.fillStyle = rgba(W.accent2, 0.25); g.fillRect(0, 2, TILE, 4);
    g.fillStyle = rgba(W.accent2, 0.6); for (let i = 0; i < 3; i++) { g.beginPath(); g.arc(8 + i * 12, 16 + (i % 2) * 5, 1.5, 0, 7); g.fill(); }
  });
  T.hazard = mk(g => {
    const c = W.hazard;
    g.save(); g.shadowColor = c; g.shadowBlur = 8;
    for (let i = 0; i < 4; i++) {
      const x = i * 10 + 5, h = 22 + (i % 2) * 10;
      const sg = g.createLinearGradient(0, TILE - h, 0, TILE); sg.addColorStop(0, '#ffffff'); sg.addColorStop(0.2, c); sg.addColorStop(1, rgba(c, 0.2));
      g.fillStyle = sg; g.beginPath(); g.moveTo(x - 5, TILE); g.lineTo(x, TILE - h); g.lineTo(x + 5, TILE); g.fill();
    }
    g.restore();
  });
  return T;
}

// ---------------------------------------------------------------
// Dibujo de fondo animado
// ---------------------------------------------------------------
function drawBackground(ctx, art, camX, t, VW) {
  const W = WORLDS[art.wi];
  ctx.drawImage(art.sky, 0, 0, VW, VIEW_H);
  const layer = (img, f, dy) => {
    let ox = -((camX * f) % LAYER_W); if (ox > 0) ox -= LAYER_W;
    for (let x = ox; x < VW; x += LAYER_W) ctx.drawImage(img, x, dy || 0);
  };
  layer(art.stars, 0.03);
  // estrellas titilantes
  ctx.globalCompositeOperation = 'lighter';
  for (const s of art.twinkles) {
    const a = 0.5 + 0.5 * Math.sin(t * 2 + s.p); if (a < 0.2) continue;
    const x = ((s.x * VW * 1.3 - camX * 0.03) % VW + VW) % VW;
    ctx.globalAlpha = a * 0.8; ctx.drawImage(glowSprite('#ffffff', true), x - s.s, s.y * VIEW_H - s.s, s.s * 2, s.s * 2);
  }
  ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over';
  if (W.planet) drawPlanet(ctx, W.planet, VW, t, camX);
  if (W.layer === 'aurora') drawAurora(ctx, W, VW, t, camX);
  layer(art.far, 0.15);
  layer(art.mid, 0.4, 20);
}
function drawPlanet(ctx, p, VW, t, camX) {
  const x = p.x * VW - camX * 0.02, y = p.y * VIEW_H, r = p.r;
  ctx.globalCompositeOperation = 'lighter'; ctx.globalAlpha = 0.35;
  ctx.drawImage(glowSprite(p.c), x - r * 3, y - r * 3, r * 6, r * 6);
  ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over';
  const g = ctx.createRadialGradient(x - r * 0.4, y - r * 0.4, r * 0.1, x, y, r);
  g.addColorStop(0, '#ffffff'); g.addColorStop(0.3, p.c); g.addColorStop(1, rgba(p.c, 0.15));
  ctx.fillStyle = g; ctx.beginPath(); ctx.arc(x, y, r, 0, 7); ctx.fill();
  if (p.ring) {
    ctx.save(); ctx.translate(x, y); ctx.rotate(-0.35); ctx.strokeStyle = rgba('#ffffff', 0.55); ctx.lineWidth = 3;
    ctx.beginPath(); ctx.ellipse(0, 0, r * 1.9, r * 0.42, 0, 0, 7); ctx.stroke();
    ctx.strokeStyle = rgba(p.c, 0.4); ctx.lineWidth = 8; ctx.beginPath(); ctx.ellipse(0, 0, r * 1.6, r * 0.34, 0, 0, 7); ctx.stroke(); ctx.restore();
  }
}
let _aur = null;
function drawAurora(ctx, W, VW, t, camX) {
  // cortina de aurora dibujada a baja resolución y escalada (suave y barata)
  const cw = Math.ceil(VW / 6), ch = 60;
  if (!_aur || _aur.c.width !== cw || _aur.W !== W) {
    const cols = [W.accent, W.accent2, W.accent3].map(c => {
      const s = makeCanvas(1, 64), g = s.getContext('2d'), gr = g.createLinearGradient(0, 0, 0, 64);
      gr.addColorStop(0, rgba(c, 0)); gr.addColorStop(0.75, rgba(c, 1)); gr.addColorStop(1, rgba(c, 0));
      g.fillStyle = gr; g.fillRect(0, 0, 1, 64); return s;
    });
    _aur = { c: makeCanvas(cw, ch), cols, W };
  }
  const g = _aur.c.getContext('2d');
  g.clearRect(0, 0, cw, ch); g.globalCompositeOperation = 'lighter';
  for (let b = 0; b < 3; b++) {
    const base = 18 + b * 7;
    for (let i = 0; i < cw; i++) {
      const wx = i * 6 + camX * 0.05;
      const y = base + Math.sin(wx * 0.006 + t * 0.4 + b) * 7 + Math.sin(wx * 0.017 - t * 0.7) * 2.5;
      const h = 16 + Math.sin(wx * 0.011 + t * 0.9 + b * 2) * 9;
      g.globalAlpha = clamp(0.25 + 0.2 * Math.sin(wx * 0.02 + t + b), 0, 1);
      g.drawImage(_aur.cols[b], i, y - h, 1, h + 4);
    }
  }
  g.globalAlpha = 1;
  ctx.globalCompositeOperation = 'lighter'; ctx.globalAlpha = 0.55;
  ctx.drawImage(_aur.c, 0, 0, cw, ch, 0, 0, VW, ch * 6);
  ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over';
}

// Partículas ambientales en primer plano (aditivas)
function drawAmbient(ctx, art, camX, t, VW) {
  const W = WORLDS[art.wi];
  ctx.globalCompositeOperation = 'lighter';
  for (const a of art.ambient) {
    let x, y, c = W.accent, s = 6 * a.s, al = 0.8;
    const bx = ((a.x - camX * 0.9 * a.v) % 2000 + 2000) % 2000 - 100;
    switch (W.ambient) {
      case 'firefly': x = bx + Math.sin(t * 0.7 + a.p) * 30; y = a.y + Math.sin(t * 0.9 + a.p * 2) * 25; c = a.p > 3 ? W.accent2 : W.accent; al = 0.4 + 0.6 * Math.max(0, Math.sin(t * 2 + a.p)); break;
      case 'sparkle': x = bx; y = a.y + Math.sin(t + a.p) * 10; c = a.p > 3 ? W.accent2 : W.accent3; al = Math.max(0, Math.sin(t * 3 + a.p * 3)); s = 5; break;
      case 'snow': x = bx + Math.sin(t + a.p) * 20; y = (a.y + t * 30 * a.v) % VIEW_H; c = '#e8f6ff'; s = 3 * a.s + 2; al = 0.6; break;
      case 'bubble': x = bx + Math.sin(t * 1.5 + a.p) * 8; y = VIEW_H - ((VIEW_H - a.y + t * 40 * a.v) % (VIEW_H + 40)); c = W.accent; s = 4 * a.s + 2; al = 0.5; break;
      case 'ember': x = bx + Math.sin(t * 2 + a.p) * 12; y = VIEW_H - ((VIEW_H - a.y + t * 60 * a.v) % (VIEW_H + 40)); c = a.p > 3 ? W.accent2 : W.accent; s = 3 * a.s + 2; al = 0.9; break;
      default: x = bx + Math.sin(t * 0.5 + a.p) * 40; y = (a.y + t * 18 * a.v) % VIEW_H; c = a.p > 3 ? W.accent2 : W.accent; s = 4 * a.s + 2; al = 0.7;
    }
    if (x < -20 || x > VW + 20) continue;
    ctx.globalAlpha = al; ctx.drawImage(glowSprite(c, true), x - s * 2, y - s * 2, s * 4, s * 4);
  }
  ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over';
}

// ---------------------------------------------------------------
// Decoraciones de superficie
// ---------------------------------------------------------------
function drawDeco(ctx, d, W, t, lights) {
  const x = d.x, y = d.y, s = d.s, pulse = 0.75 + 0.25 * Math.sin(t * 2 + d.ph);
  const c = d.k === 0 ? W.accent : d.k === 1 ? W.accent2 : W.accent3;
  switch (W.deco) {
    case 'mushroom': {
      const h = 14 * s + 6, r = 9 * s + 3;
      ctx.fillStyle = '#d8fff5'; ctx.fillRect(x - 2 * s, y - h, 4 * s, h);
      ctx.fillStyle = c; ctx.beginPath(); ctx.ellipse(x, y - h, r, r * 0.65, 0, Math.PI, 0); ctx.fill();
      ctx.fillStyle = '#ffffff'; ctx.globalAlpha = 0.8; ctx.beginPath(); ctx.arc(x - r * 0.3, y - h - r * 0.3, 1.5, 0, 7); ctx.arc(x + r * 0.35, y - h - r * 0.2, 1.2, 0, 7); ctx.fill(); ctx.globalAlpha = 1;
      lights.push(x, y - h, 50 * s * pulse, c, 0.6); break;
    }
    case 'crystal': {
      ctx.fillStyle = rgba(c, 0.85);
      for (let i = -1; i <= 1; i++) { const h = (18 - Math.abs(i) * 7) * s + 6, w = 4 * s + 1; ctx.beginPath(); ctx.moveTo(x + i * w * 1.6 - w, y); ctx.lineTo(x + i * w * 2.6, y - h); ctx.lineTo(x + i * w * 1.6 + w, y); ctx.fill(); }
      ctx.fillStyle = 'rgba(255,255,255,0.6)'; ctx.beginPath(); ctx.moveTo(x - 1, y); ctx.lineTo(x, y - 20 * s); ctx.lineTo(x + 1, y); ctx.fill();
      lights.push(x, y - 10 * s, 55 * s * pulse, c, 0.7); break;
    }
    case 'ice': {
      ctx.strokeStyle = rgba(c, 0.9); ctx.lineWidth = 1.5;
      const h = 16 * s + 5; ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x, y - h); ctx.stroke();
      ctx.fillStyle = c; for (let i = 0; i < 5; i++) { const a = i / 5 * Math.PI * 2 + t * 0.3; ctx.beginPath(); ctx.ellipse(x + Math.cos(a) * 4 * s, y - h + Math.sin(a) * 4 * s, 3 * s, 1.5 * s, a, 0, 7); ctx.fill(); }
      ctx.fillStyle = '#ffffff'; ctx.beginPath(); ctx.arc(x, y - h, 2, 0, 7); ctx.fill();
      lights.push(x, y - h, 40 * s * pulse, c, 0.6); break;
    }
    case 'coral': {
      ctx.strokeStyle = c; ctx.lineWidth = 2.5 * s; ctx.lineCap = 'round';
      for (let i = -1; i <= 1; i++) { const sw = Math.sin(t * 1.5 + d.ph + i) * 4; ctx.beginPath(); ctx.moveTo(x, y); ctx.quadraticCurveTo(x + i * 6 * s, y - 10 * s, x + i * 9 * s + sw, y - (18 - Math.abs(i) * 5) * s); ctx.stroke(); ctx.fillStyle = '#ffffff'; ctx.beginPath(); ctx.arc(x + i * 9 * s + sw, y - (18 - Math.abs(i) * 5) * s, 1.8, 0, 7); ctx.fill(); }
      lights.push(x, y - 12 * s, 45 * s * pulse, c, 0.6); break;
    }
    case 'ember': {
      ctx.fillStyle = '#1a0806'; ctx.beginPath(); ctx.moveTo(x - 8 * s, y); ctx.lineTo(x - 2, y - 20 * s); ctx.lineTo(x + 3, y - 12 * s); ctx.lineTo(x + 8 * s, y); ctx.fill();
      ctx.strokeStyle = W.accent; ctx.lineWidth = 1.5; ctx.beginPath(); ctx.moveTo(x - 2, y - 18 * s); ctx.lineTo(x, y - 8 * s); ctx.lineTo(x - 3, y); ctx.stroke();
      lights.push(x, y - 6, 45 * s * pulse, d.k ? W.accent : W.accent3, 0.6); break;
    }
    case 'flower': {
      const h = 16 * s + 6, sw = Math.sin(t + d.ph) * 3;
      ctx.strokeStyle = '#5a8a6a'; ctx.lineWidth = 1.5; ctx.beginPath(); ctx.moveTo(x, y); ctx.quadraticCurveTo(x, y - h / 2, x + sw, y - h); ctx.stroke();
      ctx.fillStyle = c; for (let i = 0; i < 5; i++) { const a = i / 5 * Math.PI * 2 + t * 0.5; ctx.beginPath(); ctx.ellipse(x + sw + Math.cos(a) * 4 * s, y - h + Math.sin(a) * 4 * s, 3.5 * s, 2 * s, a, 0, 7); ctx.fill(); }
      ctx.fillStyle = '#fffbe0'; ctx.beginPath(); ctx.arc(x + sw, y - h, 2.2 * s, 0, 7); ctx.fill();
      lights.push(x + sw, y - h, 50 * s * pulse, c, 0.6); break;
    }
  }
}

// ---------------------------------------------------------------
// Astronauta
// ---------------------------------------------------------------
function drawAstronaut(ctx, p, t, W) {
  const cx = p.x + p.w / 2, by = p.y + p.h;
  ctx.save(); ctx.translate(cx, by); ctx.scale(p.face, 1);
  if (p.squash) ctx.scale(1 + p.squash * 0.25, 1 - p.squash * 0.25);
  const run = p.onGround && Math.abs(p.vx) > 20, ph = p.anim;
  const legA = run ? Math.sin(ph) * 7 : (p.onGround ? 0 : -3), legB = run ? -Math.sin(ph) * 7 : (p.onGround ? 0 : 4);
  const bob = run ? Math.abs(Math.sin(ph)) * 2 : 0;
  // mochila propulsora
  ctx.fillStyle = '#b9c2d6'; roundRect(ctx, -15, -38 - bob, 9, 20, 3); ctx.fill();
  ctx.fillStyle = W.accent; ctx.fillRect(-13, -33 - bob, 5, 2); ctx.fillRect(-13, -28 - bob, 5, 2);
  if (p.jetT > 0) { // llama del jetpack
    const fl = 10 + Math.random() * 10;
    const fg = ctx.createLinearGradient(0, -18, 0, -18 + fl); fg.addColorStop(0, '#ffffff'); fg.addColorStop(0.3, W.accent2); fg.addColorStop(1, rgba(W.accent, 0));
    ctx.fillStyle = fg; ctx.beginPath(); ctx.moveTo(-15, -18 - bob); ctx.lineTo(-10.5, -18 + fl - bob); ctx.lineTo(-6, -18 - bob); ctx.fill();
  }
  // piernas
  ctx.fillStyle = '#e8ecf5';
  ctx.save(); ctx.translate(-4, -14 - bob); ctx.rotate(legA * 0.06); roundRect(ctx, -3.5, 0, 7, 14, 3); ctx.fill(); ctx.fillStyle = '#9aa3b8'; ctx.fillRect(-4, 10, 8, 4); ctx.restore();
  ctx.fillStyle = '#e8ecf5';
  ctx.save(); ctx.translate(4, -14 - bob); ctx.rotate(legB * 0.06); roundRect(ctx, -3.5, 0, 7, 14, 3); ctx.fill(); ctx.fillStyle = '#9aa3b8'; ctx.fillRect(-4, 10, 8, 4); ctx.restore();
  // cuerpo
  const bg = ctx.createLinearGradient(-10, 0, 12, 0); bg.addColorStop(0, '#c9d0e0'); bg.addColorStop(0.5, '#ffffff'); bg.addColorStop(1, '#cfd6e6');
  ctx.fillStyle = bg; roundRect(ctx, -10, -34 - bob, 20, 22, 6); ctx.fill();
  ctx.fillStyle = W.accent; ctx.fillRect(-3, -27 - bob, 6, 3); ctx.fillStyle = W.accent2; ctx.fillRect(-3, -22 - bob, 3, 2);
  // brazo
  const arm = run ? -Math.sin(ph) * 0.6 : (p.onGround ? 0.1 : -1.2);
  ctx.save(); ctx.translate(2, -31 - bob); ctx.rotate(arm); ctx.fillStyle = '#e8ecf5'; roundRect(ctx, -3, 0, 6, 13, 3); ctx.fill(); ctx.restore();
  // casco
  ctx.fillStyle = '#f4f6fb'; ctx.beginPath(); ctx.arc(1, -44 - bob, 12, 0, 7); ctx.fill();
  const vg = ctx.createLinearGradient(0, -52, 0, -36); vg.addColorStop(0, '#0b1b3a'); vg.addColorStop(1, rgba(W.accent, 0.9));
  ctx.fillStyle = vg; roundRect(ctx, -1, -51 - bob, 13, 12, 5); ctx.fill();
  ctx.fillStyle = 'rgba(255,255,255,0.8)'; ctx.beginPath(); ctx.ellipse(6, -48 - bob, 3, 1.6, -0.4, 0, 7); ctx.fill();
  ctx.fillStyle = W.accent2; ctx.fillRect(-2, -58 - bob, 2, 5); ctx.beginPath(); ctx.arc(-1, -59 - bob, 2, 0, 7); ctx.fill();
  ctx.restore();
}
function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath(); ctx.moveTo(x + r, y); ctx.arcTo(x + w, y, x + w, y + h, r); ctx.arcTo(x + w, y + h, x, y + h, r); ctx.arcTo(x, y + h, x, y, r); ctx.arcTo(x, y, x + w, y, r); ctx.closePath();
}

// Estrella de polvo estelar (moneda)
function drawOrb(ctx, x, y, t, c, scale) {
  const s = (scale || 1) * (8 + Math.sin(t * 4) * 1.2), w = Math.abs(Math.cos(t * 2.2)) * 0.7 + 0.3;
  ctx.save(); ctx.translate(x, y); ctx.scale(w, 1);
  ctx.fillStyle = '#ffffff'; ctx.beginPath();
  for (let i = 0; i < 8; i++) { const a = i * Math.PI / 4 - Math.PI / 2, r = i % 2 ? s * 0.4 : s; ctx.lineTo(Math.cos(a) * r, Math.sin(a) * r); }
  ctx.closePath(); ctx.fill();
  ctx.fillStyle = c; ctx.beginPath(); ctx.arc(0, 0, s * 0.3, 0, 7); ctx.fill();
  ctx.restore();
}

function drawWalker(ctx, e, t, W) {
  const x = e.x + e.w / 2, y = e.y + e.h, sq = e.dead ? Math.min(1, e.deadT * 4) : 0;
  const wob = Math.sin(e.t * 10) * 2;
  ctx.save(); ctx.translate(x, y); ctx.scale(1 + sq * 0.5, 1 - sq * 0.7);
  ctx.fillStyle = '#07030c'; ctx.beginPath(); ctx.moveTo(-15, 0);
  ctx.quadraticCurveTo(-17, -26 - wob, 0, -30 - wob); ctx.quadraticCurveTo(17, -26 + wob, 15, 0);
  for (let i = 0; i < 4; i++) ctx.lineTo(15 - (i + 0.5) * 7.5, (i % 2 ? 0 : -4) + Math.sin(e.t * 12 + i) * 1.5);
  ctx.closePath(); ctx.fill();
  ctx.strokeStyle = rgba(W.accent3, 0.7); ctx.lineWidth = 1.5; ctx.stroke();
  const dir = e.vx < 0 ? -1 : 1;
  ctx.fillStyle = W.hazard; ctx.beginPath(); ctx.ellipse(-5 + dir * 3, -18 - wob, 3.4, sq ? 1 : 4.2, 0, 0, 7); ctx.ellipse(5 + dir * 3, -18 - wob, 3.4, sq ? 1 : 4.2, 0, 0, 7); ctx.fill();
  ctx.fillStyle = '#fff'; ctx.beginPath(); ctx.arc(-4 + dir * 4, -19 - wob, 1.2, 0, 7); ctx.arc(6 + dir * 4, -19 - wob, 1.2, 0, 7); ctx.fill();
  ctx.restore();
}
function drawFloater(ctx, e, t, W) {
  const x = e.x + e.w / 2, y = e.y + e.h / 2, c = W.accent3;
  ctx.save(); ctx.translate(x, y); if (e.dead) { ctx.globalAlpha = Math.max(0, 1 - e.deadT * 2); ctx.scale(1 + e.deadT, 1 - e.deadT); }
  const pulse = Math.sin(e.t * 4) * 2;
  const gg = ctx.createRadialGradient(0, -4, 2, 0, 0, 18); gg.addColorStop(0, '#ffffff'); gg.addColorStop(0.4, rgba(c, 0.8)); gg.addColorStop(1, rgba(c, 0.1));
  ctx.fillStyle = gg; ctx.beginPath(); ctx.ellipse(0, 0, 16 + pulse, 12 - pulse * 0.5, 0, Math.PI, 0); ctx.quadraticCurveTo(0, 6, -16 - pulse, 0); ctx.fill();
  ctx.strokeStyle = rgba(c, 0.8); ctx.lineWidth = 1.5;
  for (let i = -2; i <= 2; i++) { ctx.beginPath(); ctx.moveTo(i * 5, 2); ctx.quadraticCurveTo(i * 5 + Math.sin(e.t * 5 + i) * 5, 12, i * 4 + Math.sin(e.t * 4 + i) * 3, 20); ctx.stroke(); }
  ctx.fillStyle = '#1a0022'; ctx.beginPath(); ctx.arc(-5, -4, 2, 0, 7); ctx.arc(5, -4, 2, 0, 7); ctx.fill();
  ctx.restore();
}
function drawPortal(ctx, g, t, W) {
  const x = g.x, y = g.y - 80;
  ctx.save(); ctx.translate(x, y);
  ctx.globalCompositeOperation = 'lighter';
  for (let i = 0; i < 3; i++) {
    ctx.strokeStyle = rgba([W.accent, W.accent2, '#ffffff'][i], 0.7 - i * 0.15); ctx.lineWidth = 4 - i;
    ctx.beginPath(); ctx.ellipse(0, 0, 38 + i * 6 + Math.sin(t * 3 + i) * 3, 64 + i * 6, 0, t * (i + 1) * 0.5, t * (i + 1) * 0.5 + Math.PI * 1.6); ctx.stroke();
  }
  const cg = ctx.createRadialGradient(0, 0, 4, 0, 0, 60); cg.addColorStop(0, 'rgba(255,255,255,0.9)'); cg.addColorStop(0.3, rgba(W.accent, 0.45)); cg.addColorStop(1, rgba(W.accent2, 0));
  ctx.fillStyle = cg; ctx.beginPath(); ctx.ellipse(0, 0, 38, 64, 0, 0, 7); ctx.fill();
  for (let i = 0; i < 12; i++) { const a = t * 1.5 + i / 12 * Math.PI * 2, r = 30 + Math.sin(t * 2 + i) * 16; ctx.fillStyle = i % 2 ? W.accent : '#ffffff'; ctx.beginPath(); ctx.arc(Math.cos(a) * r * 0.7, Math.sin(a) * r * 1.2, 2, 0, 7); ctx.fill(); }
  ctx.restore();
  ctx.fillStyle = W.ground2; ctx.fillRect(x - 46, g.y - 8, 92, 8);
  ctx.fillStyle = W.accent; ctx.fillRect(x - 46, g.y - 8, 92, 2);
}
function drawCheck(ctx, e, t, W) {
  const x = e.x, y = e.y;
  ctx.fillStyle = '#c8cfdf'; ctx.fillRect(x - 2, y - 70, 4, 70);
  const c = e.on ? W.accent2 : '#606880';
  ctx.fillStyle = c; ctx.beginPath(); ctx.moveTo(x + 2, y - 70); ctx.lineTo(x + 30 + (e.on ? Math.sin(t * 6) * 3 : 0), y - 62); ctx.lineTo(x + 2, y - 54); ctx.fill();
  ctx.fillStyle = e.on ? '#ffffff' : '#8890a8'; ctx.beginPath(); ctx.arc(x, y - 72, 5, 0, 7); ctx.fill();
}
function drawSpring(ctx, e, t, W) {
  const c = e.t > 0 ? 1 - e.t * 3 : 1, x = e.x, y = e.y + e.h;
  ctx.fillStyle = '#e8ecf5'; ctx.fillRect(x + 12, y - 10 * c, 8, 10 * c);
  ctx.fillStyle = W.accent2; ctx.beginPath(); ctx.ellipse(x + 16, y - 10 * c - 3, 18, 7, 0, Math.PI, 0); ctx.fill();
  ctx.fillStyle = '#ffffff'; ctx.fillRect(x + 4, y - 10 * c - 4, 24, 2);
}
function drawMPlat(ctx, e, W) {
  ctx.fillStyle = W.ground2; roundRect(ctx, e.x, e.y, e.w, e.h, 6); ctx.fill();
  ctx.fillStyle = W.accent2; ctx.fillRect(e.x + 4, e.y, e.w - 8, 2);
  ctx.fillStyle = rgba(W.accent, 0.8); for (let i = 0; i < 3; i++) { ctx.beginPath(); ctx.arc(e.x + 20 + i * 40, e.y + 9, 2.5, 0, 7); ctx.fill(); }
}
function drawItem(ctx, it, t, W) {
  const x = it.x + it.w / 2, y = it.y + it.h / 2;
  if (it.kind === 'shield') {
    ctx.save(); ctx.translate(x, y); ctx.rotate(Math.sin(t * 3) * 0.2);
    const g = ctx.createRadialGradient(-3, -3, 1, 0, 0, 13); g.addColorStop(0, '#ffffff'); g.addColorStop(0.5, rgba(W.accent, 0.9)); g.addColorStop(1, rgba(W.accent3, 0.6));
    ctx.fillStyle = g; ctx.beginPath(); ctx.moveTo(0, -13); ctx.lineTo(11, -7); ctx.lineTo(9, 6); ctx.lineTo(0, 13); ctx.lineTo(-9, 6); ctx.lineTo(-11, -7); ctx.closePath(); ctx.fill();
    ctx.restore();
  } else {
    ctx.save(); ctx.translate(x, y); ctx.fillStyle = '#b8ff7a';
    ctx.beginPath(); for (let i = 0; i < 10; i++) { const a = i * Math.PI / 5 - Math.PI / 2, r = i % 2 ? 6 : 14; ctx.lineTo(Math.cos(a) * r, Math.sin(a) * r); } ctx.closePath(); ctx.fill();
    ctx.fillStyle = '#ffffff'; ctx.font = 'bold 10px sans-serif'; ctx.textAlign = 'center'; ctx.fillText('1', 0, 4); ctx.restore();
  }
}
