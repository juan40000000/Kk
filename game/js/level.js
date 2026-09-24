'use strict';
// ---------------------------------------------------------------
// Generador de niveles por bloques (determinista por mundo/nivel)
// Tiles: 0 vacío, 1 suelo, 2 roca, 3 caja de energía, 4 caja usada,
//        5 plataforma de un sentido, 6 peligro
// ---------------------------------------------------------------
const T_EMPTY = 0, T_GROUND = 1, T_BRICK = 2, T_BOX = 3, T_USED = 4, T_ONEWAY = 5, T_HAZARD = 6;

function generateLevel(wi, li) {
  const rng = mulberry32(7919 * (wi + 1) + 104729 * (li + 1) + 17);
  const R = (a, b) => a + Math.floor(rng() * (b - a + 1));
  const chance = p => rng() < p;
  const diff = wi * 2 + li; // 0..11
  const W = 160 + diff * 12 + (li === LEVELS_PER_WORLD - 1 ? 50 : 0);
  const H = ROWS;
  const grid = new Uint8Array(W * H);
  const L = { w: W, h: H, grid, ents: [], coins: [], decos: [], boxes: {}, wi, li, diff };
  const set = (x, y, v) => { if (x >= 0 && x < W && y >= 0 && y < H) grid[y * W + x] = v; };
  const get = (x, y) => (x >= 0 && x < W && y >= 0 && y < H) ? grid[y * W + x] : 0;
  const ground = (x, top) => { for (let y = top; y < H; y++) set(x, y, T_GROUND); };
  const pit = x => set(x, H - 1, T_HAZARD);
  const coin = (tx, ty) => { if (ty >= 1) L.coins.push({ x: tx * TILE + 20, y: ty * TILE + 20, t: rng() * 6, taken: false }); };
  const walker = (tx, ty) => L.ents.push({ type: 'walker', x: tx * TILE + 6, y: ty * TILE - 30, w: 28, h: 30, vx: -(50 + diff * 5), vy: 0, alive: true, hp: 2, t: rng() * 6 });
  const turret = (tx, ty) => L.ents.push({ type: 'turret', x: tx * TILE + 4, y: ty * TILE - 32, w: 32, h: 32, alive: true, hp: 3, t: rng() * 6, cd: 1 + rng(), charge: 0 });
  const charger = (tx, ty) => L.ents.push({ type: 'charger', x: tx * TILE, y: ty * TILE - 36, w: 38, h: 36, vx: -40, vy: 0, alive: true, hp: 4, t: 0, mode: 'walk', mt: 0 });
  const jorb = (tx, ty) => L.ents.push({ type: 'jorb', x: tx * TILE + 20, y: ty * TILE + 20, r: 20, t: rng() * 6, used: 0 });
  const boost = (tx, ty) => L.ents.push({ type: 'boost', x: tx * TILE, y: ty * TILE - 10, w: TILE, h: 10, t: 0 });
  const floater = (tx, ty, ax) => L.ents.push({ type: 'floater', hp: 1, bx: tx * TILE + 20, by: ty * TILE, x: 0, y: 0, w: 30, h: 30, ax: ax || 0, amp: ax ? 80 : 50, spd: 1.2 + rng() * 0.8, ph: rng() * 6, alive: true, t: 0 });
  let shieldPlaced = false;
  const boxContent = () => {
    if (!shieldPlaced && chance(0.5)) { shieldPlaced = true; return 'shield'; }
    if (chance(0.07)) return 'life';
    return 'orb';
  };
  const box = (x, y) => { set(x, y, T_BOX); L.boxes[y * W + x] = boxContent(); };

  let gh = 11, x = 0;
  for (; x < 14; x++) ground(x, gh);
  L.start = { x: 3 * TILE, y: gh * TILE - 46 };
  L.checkpoint = null;
  let last = '';

  const weights = [
    ['flat', 2.5], ['gap', 3], ['step', 2], ['boxes', 2.2], ['plats', diff >= 1 ? 2 : 1],
    ['pillars', 1.6], ['stairs', 1.2], ['spikes', diff >= 1 ? 1.3 : 0.4], ['moving', diff >= 2 ? 1.6 : 0.5],
    ['spring', 0.9], ['floaters', diff >= 3 ? 1.3 : 0.5], ['lift', diff >= 4 ? 1 : 0],
    ['orbs', 1.8], ['boost', 1.4], ['turret', 1.2 + diff * 0.15], ['charger', diff >= 1 ? 1 + diff * 0.1 : 0.3], ['arena', 0.8 + diff * 0.1],
  ];
  const total = weights.reduce((a, b) => a + b[1], 0);
  const pick = () => { let r = rng() * total; for (const [n, w] of weights) { if ((r -= w) <= 0) return n; } return 'flat'; };
  const land = n => { for (let i = 0; i < n; i++) ground(x + i, gh); x += n; };

  while (x < W - 24 - (li === LEVELS_PER_WORLD - 1 ? 50 : 0)) {
    if (!L.checkpoint && x > W * 0.5) {
      land(1); L.checkpoint = { x: x * TILE + 20, y: gh * TILE };
      L.ents.push({ type: 'check', x: x * TILE + 20, y: gh * TILE, on: false, t: 0 });
      land(5); last = 'flat'; continue;
    }
    let type = pick(); if (type === last) type = pick();
    last = type;
    switch (type) {
      case 'flat': {
        const n = R(4, 8), sx = x; land(n);
        if (chance(0.5)) { const cy = gh - R(2, 3); for (let i = 1; i < n - 1; i++) coin(sx + i, cy); }
        if (chance(0.35 + diff * 0.04)) walker(sx + R(2, n - 1), gh);
        break;
      }
      case 'gap': {
        const n = R(2, Math.min(4, 2 + Math.floor(diff / 3)));
        for (let i = 0; i < n; i++) pit(x + i);
        for (let i = -1; i <= n; i++) { const k = (i + 1) / (n + 1); coin(x + i, gh - 2 - Math.round(Math.sin(k * Math.PI) * 2)); }
        x += n; gh = clamp(gh + R(-1, 1), 8, 12); land(R(3, 4));
        break;
      }
      case 'step': {
        const d = [-2, -1, 1, 2, -3][R(0, 4)];
        gh = clamp(gh + d, 7, 12); const sx = x; land(R(3, 5));
        if (chance(0.4)) coin(sx + 1, gh - 1);
        break;
      }
      case 'boxes': {
        const sx = x; land(9); const r = gh - 4;
        const pats = [[2, 3, 2, 3, 2], [0, 0, 3, 0, 0], [2, 2, 3, 2, 2], [3, 2, 3], [2, 3, 3, 2]];
        const pat = pats[R(0, pats.length - 1)];
        pat.forEach((v, i) => { if (v === 3) box(sx + 2 + i, r); else if (v === 2) set(sx + 2 + i, r, T_BRICK); });
        if (chance(0.45) && r - 4 >= 1) { box(sx + 4, r - 4); }
        for (let i = 0; i < pat.length; i++) if (pat[i] === 2) coin(sx + 2 + i, r - 1);
        if (chance(0.5 + diff * 0.03)) walker(sx + 7, gh);
        break;
      }
      case 'plats': {
        const n = R(6, 9), k = n >= 8 ? 3 : 2, sp = n / k, sx = x;
        for (let i = 0; i < n; i++) pit(x + i);
        for (let i = 0; i < k; i++) {
          const w = R(2, 3), px = sx + Math.floor(i * sp + (sp - w) / 2), py = gh - R(1, 3);
          for (let j = 0; j < w; j++) { set(px + j, py, T_ONEWAY); coin(px + j, py - 1); }
        }
        if (diff >= 2 && chance(0.5)) floater(sx + Math.floor(n / 2), gh - 5, 0);
        x += n; land(3);
        break;
      }
      case 'pillars': {
        const n = R(7, 10), sx = x; land(n);
        const h1 = R(2, 3), h2 = R(2, 4);
        for (const [px, h] of [[sx + 1, h1], [sx + n - 3, h2]]) {
          if (gh - h < 4) continue;
          for (let y = gh - h; y < gh; y++) { set(px, y, T_BRICK); set(px + 1, y, T_BRICK); }
          coin(px, gh - h - 1); coin(px + 1, gh - h - 1);
        }
        if (chance(0.7)) walker(sx + Math.floor(n / 2), gh);
        break;
      }
      case 'stairs': {
        const k = R(3, 4), gap = diff >= 3 && chance(0.6);
        if (gh - k < 4) { land(3); break; }
        for (let i = 0; i < k; i++) { ground(x, gh); for (let j = 0; j <= i; j++) set(x, gh - 1 - j, T_BRICK); x++; }
        if (gap) { for (let i = 0; i < 2; i++) { pit(x); coin(x, gh - k - 2); x++; } }
        else { ground(x, gh); for (let j = 0; j < k; j++) set(x, gh - 1 - j, T_BRICK); coin(x, gh - k - 1); x++; }
        for (let i = 0; i < k; i++) { ground(x, gh); for (let j = 0; j < k - i; j++) set(x, gh - 1 - j, T_BRICK); x++; }
        land(2);
        break;
      }
      case 'spikes': {
        const n = R(6, 8), sx = x; land(n);
        const s = R(1, Math.min(3, 1 + Math.floor(diff / 3)));
        for (let i = 0; i < s; i++) { set(sx + 3 + i, gh - 1, T_HAZARD); coin(sx + 3 + i, gh - 4); }
        break;
      }
      case 'moving': {
        const n = R(7, 10);
        for (let i = 0; i < n; i++) pit(x + i);
        L.ents.push({ type: 'mplat', x0: x * TILE, y0: (gh - 1) * TILE + 8, w: 3 * TILE, h: 16, dx: (n - 3) * TILE, dy: 0, spd: 0.7 + diff * 0.03, ph: 0, x: x * TILE, y: (gh - 1) * TILE + 8, vx: 0, vy: 0 });
        for (let i = 1; i < n - 1; i += 2) coin(x + i, gh - 4);
        x += n; land(3);
        break;
      }
      case 'lift': {
        // Plataforma vertical para subir a una cornisa alta
        const up = Math.min(4, gh - 6);
        if (up < 2) { land(2); break; }
        const n = 3; for (let i = 0; i < n; i++) pit(x + i);
        L.ents.push({ type: 'mplat', x0: x * TILE, y0: (gh - 1 - up) * TILE + 8, w: 3 * TILE, h: 16, dx: 0, dy: up * TILE, spd: 0.8, ph: 0, x: x * TILE, y: (gh - 1) * TILE, vx: 0, vy: 0 });
        x += n; gh = gh - up; const sx = x; land(4); coin(sx + 1, gh - 1); coin(sx + 2, gh - 1);
        break;
      }
      case 'spring': {
        const sx = x; land(7);
        L.ents.push({ type: 'spring', x: (sx + 2) * TILE + 4, y: gh * TILE - 18, w: 32, h: 18, t: 0 });
        const py = Math.max(1, gh - 8);
        for (let i = 0; i < 4; i++) { set(sx + 2 + i, py, T_ONEWAY); coin(sx + 2 + i, py - 1); }
        if (py - 1 >= 1) coin(sx + 3, py - 2);
        break;
      }
      case 'floaters': {
        const n = R(7, 10), sx = x; land(n);
        floater(sx + 3, gh - 3, 0);
        if (chance(0.6)) floater(sx + n - 3, gh - 4, 1);
        break;
      }
      case 'orbs': {
        // Estilo Geometry Dash: cadena de orbes de salto sobre un abismo
        // los orbes siguen la parábola del salto: ~6 casillas entre uno y otro
        const sp = Math.round(6 / WORLDS[wi].gravity), k = diff >= 4 && chance(0.5) ? 3 : 2, n = 2 + sp * k - 1;
        for (let i = 0; i < n; i++) pit(x + i);
        const oh = Math.round(4 / WORLDS[wi].gravity);
        for (let i = 0; i < k; i++) { const ox = x + 2 + sp * i; jorb(ox, gh - oh); coin(ox + (sp >> 1), gh - oh - 2); }
        x += n; land(3);
        break;
      }
      case 'boost': {
        const sx = x; land(5); boost(sx + 1, gh);
        const n = R(5, 6); for (let i = 0; i < n; i++) { pit(x + i); coin(x + i, gh - 3 - Math.round(Math.sin((i + 0.5) / n * Math.PI) * 2)); }
        x += n; land(4);
        break;
      }
      case 'turret': {
        const sx = x, n = R(8, 10); land(n);
        const px = sx + n - 3;
        if (chance(0.5) && gh - 2 >= 5) { set(px, gh - 1, T_BRICK); set(px + 1, gh - 1, T_BRICK); turret(px, gh - 1); }
        else turret(px, gh);
        coin(sx + 2, gh - 3); coin(sx + 3, gh - 3);
        break;
      }
      case 'charger': {
        const sx = x, n = R(10, 13); land(n);
        charger(sx + n - 3, gh);
        for (let i = 2; i < n - 2; i += 2) coin(sx + i, gh - 4);
        break;
      }
      case 'arena': {
        // Mini emboscada: varios enemigos a la vez
        const sx = x, n = R(12, 15); land(n);
        for (let j = 0; j < 3; j++) set(sx + 5 + j, gh - 3, T_ONEWAY);
        walker(sx + 4, gh); walker(sx + n - 3, gh);
        if (diff >= 2) floater(sx + Math.floor(n / 2), gh - 6, 1);
        if (diff >= 4) turret(sx + n - 1, gh);
        coin(sx + 5, gh - 4); coin(sx + 6, gh - 4); coin(sx + 7, gh - 4);
        break;
      }
    }
  }
  if (li === LEVELS_PER_WORLD - 1) {
    // Arena del guardián: suelo plano, plataformas para esquivar y portal oculto
    gh = 11; land(6);
    const ax = x, AW = 36;
    L.arena = { x0: ax * TILE, x1: (ax + AW) * TILE, floor: gh * TILE };
    for (let i = 0; i < AW; i++) ground(x + i, gh);
    for (const [px, py, w] of [[5, gh - 3, 4], [AW - 9, gh - 3, 4], [Math.floor(AW / 2) - 2, gh - 6, 4]]) for (let j = 0; j < w; j++) set(ax + px + j, py, T_ONEWAY);
    x += AW;
    for (let i = 0; i < 4; i++) { for (let y = 0; y < H; y++) set(x + i, y, T_BRICK); }
    x += 4;
    for (; x < W; x++) ground(x, gh);
    L.goal = { x: ax * TILE + AW * TILE - 5 * TILE, y: gh * TILE };
    L.goalLocked = true;
    L.boss = { type: 'boss', x: (ax + AW - 8) * TILE, y: -200, w: 96, h: 96, hp: 36 + wi * 8, max: 36 + wi * 8, t: 0, st: 0, mode: 'wait', flash: 0, phase: 1, name: BOSS_NAMES[wi], atk: 0, alive: true, vx: 0, vy: 0 };
    return decorate(L, rng, R, chance, get, W, H);
  }
  // Tramo final con escalera de luz y portal
  land(3);
  const k = 3;
  for (let i = 0; i < k; i++) { ground(x, gh); for (let j = 0; j <= i; j++) set(x, gh - 1 - j, T_BRICK); x++; }
  for (let i = 0; i < 2; i++) { ground(x, gh); for (let j = 0; j < k; j++) set(x, gh - 1 - j, T_BRICK); x++; }
  land(3);
  for (; x < W; x++) ground(x, gh);
  L.goal = { x: (W - 9) * TILE, y: gh * TILE };
  for (let i = W - 11; i < W - 6; i++) coin(i, gh - 5);

  return decorate(L, rng, R, chance, get, W, H);
}

function decorate(L, rng, R, chance, get, W, H) {
  // Decoración en superficies
  for (let cx = 0; cx < W; cx++) {
    for (let cy = 1; cy < H; cy++) {
      const v = get(cx, cy);
      if ((v === T_GROUND) && get(cx, cy - 1) === T_EMPTY) {
        if (chance(0.38)) L.decos.push({ x: cx * TILE + rng() * TILE, y: cy * TILE, k: R(0, 2), s: 0.6 + rng() * 0.7, ph: rng() * 6 });
        break;
      }
      if (v !== T_EMPTY) break;
    }
  }
  return L;
}
