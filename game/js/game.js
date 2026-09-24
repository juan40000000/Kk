'use strict';
// ---------------------------------------------------------------
// Astro Glow — lógica principal, física, estados y UI
// ---------------------------------------------------------------
const cv = document.getElementById('c'), ctx = cv.getContext('2d');
let VW = 1000, SCALE = 1;
const lc = makeCanvas(256, 150), lx = lc.getContext('2d');
let vignette = null;

function resize() {
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  let w = innerWidth * dpr, h = innerHeight * dpr;
  const k = h > 900 ? 900 / h : 1;
  cv.width = Math.round(w * k); cv.height = Math.round(h * k);
  SCALE = cv.height / VIEW_H; VW = cv.width / SCALE;
  lc.width = Math.ceil(VW / 4); lc.height = VIEW_H / 4;
  vignette = makeCanvas(256, 150); const vg = vignette.getContext('2d');
  const g = vg.createRadialGradient(128, 75, 40, 128, 75, 150); g.addColorStop(0, 'rgba(0,0,0,0)'); g.addColorStop(1, 'rgba(0,0,0,0.55)');
  vg.fillStyle = g; vg.fillRect(0, 0, 256, 150);
}
addEventListener('resize', resize); resize();

// Física
const RUN = 300, ACC_G = 2300, ACC_A = 1500, FRIC = 2600, BASE_G = 2200, JV = 880, DJV = 740, MAXF = 900;
const STEP = 1 / 60;

const G = {
  state: 'title', stateT: 0, t: 0, wi: 0, li: 0, lives: 3, orbs: 0, score: 0,
  L: null, art: null, arts: {}, p: null, cam: { x: 0 }, cp: null,
  parts: [], items: [], pops: [], bumps: {}, buttons: [], sel: 0, shake: 0, titleWorld: 0,
  save: { unlocked: 0, orbsTotal: 0 },
};
try { Object.assign(G.save, JSON.parse(localStorage.getItem('astroglow_save') || '{}')); } catch (e) { }
function persist() { try { localStorage.setItem('astroglow_save', JSON.stringify(G.save)); } catch (e) { } }
function getArt(wi) { return G.arts[wi] || (G.arts[wi] = buildArt(wi)); }

function setState(s) { G.state = s; G.stateT = 0; G.sel = 0; }

// ---------------------------------------------------------------
// Nivel
// ---------------------------------------------------------------
function startLevel(wi, li, fresh) {
  G.wi = wi; G.li = li;
  G.L = generateLevel(wi, li); G.art = getArt(wi);
  G.parts = []; G.items = []; G.pops = []; G.bumps = {}; G.cp = null;
  if (fresh) { G.lives = Math.max(G.lives, 3); }
  spawnPlayer();
  G.cam.x = 0;
  Sound.playSong(WORLDS[wi].music);
  setState('intro');
}
function spawnPlayer() {
  const s = G.cp || G.L.start;
  G.p = { x: s.x, y: s.y, w: 24, h: 46, vx: 0, vy: 0, face: 1, onGround: false, jumps: 0, coyote: 0, jbuf: 0, anim: 0, jetT: 0,
    shield: G.p ? G.p.shield && !G.p.dead : false, inv: 1.2, dead: false, deadT: 0, ride: null, squash: 0, win: false };
  G.cam.x = clamp(G.p.x - VW * 0.4, 0, G.L.w * TILE - VW);
}

const solid = v => v >= 1 && v <= 4;
function tileAt(tx, ty) {
  const L = G.L;
  if (tx < 0 || tx >= L.w) return T_GROUND;
  if (ty < 0 || ty >= L.h) return T_EMPTY;
  return L.grid[ty * L.w + tx];
}
function moveBody(b, dt, oneway) {
  const res = { wall: 0, ground: false, head: null };
  b.x += b.vx * dt;
  const top = Math.floor(b.y / TILE), bot = Math.floor((b.y + b.h - 0.01) / TILE);
  if (b.vx > 0) {
    const tx = Math.floor((b.x + b.w) / TILE);
    for (let ty = top; ty <= bot; ty++) if (solid(tileAt(tx, ty))) { b.x = tx * TILE - b.w - 0.01; b.vx = 0; res.wall = 1; break; }
  } else if (b.vx < 0) {
    const tx = Math.floor(b.x / TILE);
    for (let ty = top; ty <= bot; ty++) if (solid(tileAt(tx, ty))) { b.x = (tx + 1) * TILE + 0.01; b.vx = 0; res.wall = -1; break; }
  }
  const prevBottom = b.y + b.h;
  b.y += b.vy * dt;
  const l = Math.floor(b.x / TILE), r = Math.floor((b.x + b.w - 0.01) / TILE);
  if (b.vy > 0) {
    const ty = Math.floor((b.y + b.h) / TILE);
    for (let tx = l; tx <= r; tx++) {
      const v = tileAt(tx, ty);
      if (solid(v) || (oneway && v === T_ONEWAY && prevBottom <= ty * TILE + 1)) { b.y = ty * TILE - b.h; b.vy = 0; res.ground = true; break; }
    }
  } else if (b.vy < 0) {
    const ty = Math.floor(b.y / TILE); let best = null, bestD = 1e9; const cx = b.x + b.w / 2;
    for (let tx = l; tx <= r; tx++) if (solid(tileAt(tx, ty))) { const d = Math.abs(tx * TILE + TILE / 2 - cx); if (d < bestD) { bestD = d; best = tx; } }
    if (best !== null) { b.y = (ty + 1) * TILE; b.vy = 0; res.head = { tx: best, ty }; }
  }
  return res;
}
const overlap = (a, b) => a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y;

function burst(x, y, c, n, spd, life, grav) {
  for (let i = 0; i < n; i++) {
    const a = Math.random() * Math.PI * 2, s = spd * (0.3 + Math.random() * 0.7);
    G.parts.push({ x, y, vx: Math.cos(a) * s, vy: Math.sin(a) * s, life: life * (0.6 + Math.random() * 0.4), max: life, c, s: 4 + Math.random() * 6, g: grav || 0 });
  }
  if (G.parts.length > 400) G.parts.splice(0, G.parts.length - 400);
}
function popup(x, y, text, c) { G.pops.push({ x, y, text, c, t: 0 }); }

function hurt() {
  const p = G.p; if (p.inv > 0 || p.dead || p.win) return;
  if (p.shield) {
    p.shield = false; p.inv = 1.6; p.vy = -620; Sound.sfx.hurt(); G.shake = 0.3;
    burst(p.x + p.w / 2, p.y + p.h / 2, WORLDS[G.wi].accent, 30, 400, 0.7);
  } else die();
}
function die() {
  const p = G.p; if (p.dead) return;
  p.dead = true; p.deadT = 0; p.vy = -760; p.vx = 0; p.shield = false; G.lives--; G.shake = 0.4;
  Sound.sfx.die();
  burst(p.x + p.w / 2, p.y + p.h / 2, '#ffffff', 25, 350, 0.8);
}

function bumpBox(tx, ty) {
  const L = G.L, idx = ty * L.w + tx, W = WORLDS[G.wi];
  G.bumps[idx] = 0;
  // enemigos encima reciben el golpe
  for (const e of L.ents) if (e.type === 'walker' && e.alive && Math.abs(e.y + e.h - ty * TILE) < 6 && e.x + e.w > tx * TILE && e.x < tx * TILE + TILE) killEnemy(e);
  if (L.grid[idx] !== T_BOX) { Sound.sfx.bump(); return; }
  L.grid[idx] = T_USED;
  const c = L.boxes[idx] || 'orb';
  if (c === 'orb') {
    addOrbs(1); Sound.sfx.coin();
    G.parts.push({ x: tx * TILE + 20, y: ty * TILE - 10, vx: 0, vy: -420, life: 0.5, max: 0.5, c: W.accent2, s: 14, g: 1200, orb: true });
  } else {
    Sound.sfx.power();
    G.items.push({ kind: c, x: tx * TILE + 6, y: ty * TILE - 4, w: 28, h: 28, vx: 0, vy: 0, rise: 30, dir: 1 });
  }
  burst(tx * TILE + 20, ty * TILE, W.accent2, 10, 200, 0.5);
}
function addOrbs(n) {
  G.orbs += n; G.score += n * 10; G.save.orbsTotal = (G.save.orbsTotal || 0) + n;
  if (G.orbs >= 100) { G.orbs -= 100; G.lives++; Sound.sfx.life(); popup(G.p.x, G.p.y - 20, '+1 VIDA', '#b8ff7a'); }
}
function killEnemy(e) {
  e.alive = false; e.dead = true; e.deadT = 0; G.score += 100;
  burst(e.x + e.w / 2, e.y + e.h / 2, WORLDS[G.wi].accent3, 18, 300, 0.6);
  Sound.sfx.stomp();
}

// ---------------------------------------------------------------
// Actualización de juego
// ---------------------------------------------------------------
function updatePlay(dt) {
  const L = G.L, p = G.p, W = WORLDS[G.wi], grav = BASE_G * W.gravity;
  G.t += dt;
  // plataformas móviles
  for (const e of L.ents) if (e.type === 'mplat') {
    const ox = e.x, oy = e.y; e.ph += e.spd * dt;
    const k = (1 - Math.cos(e.ph)) / 2; e.x = e.x0 + k * e.dx; e.y = e.y0 + (e.dy ? (1 - k) * e.dy : 0);
    e.fdx = e.x - ox; e.fdy = e.y - oy;
  }
  if (p.dead) {
    p.deadT += dt; p.vy += grav * 0.8 * dt; p.y += p.vy * dt;
    if (p.deadT > 1.8) {
      if (G.lives <= 0) { setState('gameover'); Sound.playSong(SONGS.title); }
      else spawnPlayer();
    }
  } else if (p.win) {
    // absorbido por el portal
    const gx = L.goal.x - p.w / 2, gy = L.goal.y - 80 - p.h / 2;
    p.x = lerp(p.x, gx, dt * 2.5); p.y = lerp(p.y, gy, dt * 2.5); p.anim += dt * 8;
    if (Math.random() < 0.5) burst(L.goal.x, L.goal.y - 80, Math.random() < 0.5 ? W.accent : W.accent2, 2, 200, 1);
  } else updatePlayer(dt, grav);

  // enemigos
  const cx0 = G.cam.x - 200, cx1 = G.cam.x + VW + 200;
  for (const e of L.ents) {
    if (e.type === 'walker') {
      if (e.dead) { e.deadT += dt; continue; }
      if (e.x < cx0 || e.x > cx1) continue;
      e.t += dt; e.vy = Math.min(e.vy + grav * dt, MAXF);
      const dir = Math.sign(e.vx) || -1, spd = Math.abs(e.vx) || 60;
      // girar en bordes
      const fx = dir > 0 ? e.x + e.w + 2 : e.x - 2, below = tileAt(Math.floor(fx / TILE), Math.floor((e.y + e.h + 4) / TILE));
      const r = moveBody(e, dt, true);
      if (r.wall) e.vx = -dir * spd; else if (r.ground && !solid(below) && below !== T_ONEWAY) e.vx = -dir * spd;
      if (e.y > ROWS * TILE + 50) e.alive = false, e.dead = true, e.deadT = 9;
      checkEnemyHit(e);
    } else if (e.type === 'floater') {
      e.t += dt;
      if (e.dead) { e.deadT += dt; continue; }
      e.x = e.bx - e.w / 2 + (e.ax ? Math.sin(e.t * e.spd + e.ph) * e.amp : 0);
      e.y = e.by - e.h / 2 + (e.ax ? Math.sin(e.t * e.spd * 2 + e.ph) * 12 : Math.sin(e.t * e.spd + e.ph) * e.amp);
      if (e.x > cx0 && e.x < cx1) checkEnemyHit(e);
    } else if (e.type === 'spring') {
      if (e.t > 0) e.t = Math.max(0, e.t - dt);
      if (!p.dead && p.vy > 0 && overlap(p, e) && p.y + p.h - e.y < 20) {
        p.vy = -1280; p.jumps = 1; p.onGround = false; p.ride = null; e.t = 0.3; Sound.sfx.spring();
        burst(e.x + 16, e.y, WORLDS[G.wi].accent2, 14, 250, 0.5);
      }
    } else if (e.type === 'check') {
      e.t += dt;
      if (!e.on && !p.dead && p.x + p.w > e.x - 10) {
        e.on = true; G.cp = { x: e.x - 12, y: e.y - p.h }; Sound.sfx.check();
        burst(e.x, e.y - 70, W.accent2, 30, 300, 0.9); popup(e.x, e.y - 90, 'PUNTO DE CONTROL', W.accent2);
      }
    }
  }
  // objetos
  for (const it of G.items) {
    if (it.rise > 0) { const d = Math.min(it.rise, 40 * dt); it.y -= d; it.rise -= d; if (it.rise <= 0) it.vx = 90 * it.dir; continue; }
    it.vy = Math.min(it.vy + grav * 0.9 * dt, MAXF);
    const r = moveBody(it, dt, true);
    if (r.wall) it.vx = -r.wall * 90;
    if (r.ground && it.kind === 'life') it.vy = -480;
    if (!p.dead && overlap(p, it)) {
      it.gone = true;
      if (it.kind === 'shield') { if (p.shield) addOrbs(10); p.shield = true; popup(p.x, p.y - 20, 'ESCUDO DE LUZ', W.accent); Sound.sfx.power(); }
      else { G.lives++; popup(p.x, p.y - 20, '+1 VIDA', '#b8ff7a'); Sound.sfx.life(); }
      burst(it.x + 14, it.y + 14, W.accent, 24, 300, 0.7);
    }
    if (it.y > ROWS * TILE + 100) it.gone = true;
  }
  G.items = G.items.filter(i => !i.gone);
  // polvo estelar
  const pcx = p.x + p.w / 2, pcy = p.y + p.h / 2;
  if (!p.dead) for (const c of L.coins) {
    if (c.taken || Math.abs(c.x - pcx) > 30) continue;
    if (Math.abs(c.y - pcy) < 34) { c.taken = true; addOrbs(1); Sound.sfx.coin(); burst(c.x, c.y, W.accent2, 8, 160, 0.4); }
  }
  // partículas
  for (const q of G.parts) { q.life -= dt; q.vy += q.g * dt; q.x += q.vx * dt; q.y += q.vy * dt; q.vx *= 0.98; }
  G.parts = G.parts.filter(q => q.life > 0);
  for (const k in G.bumps) { G.bumps[k] += dt; if (G.bumps[k] > 0.2) delete G.bumps[k]; }
  for (const q of G.pops) q.t += dt; G.pops = G.pops.filter(q => q.t < 1.4);
  G.shake = Math.max(0, G.shake - dt);
  // cámara
  if (!p.dead) {
    const target = p.x + p.w / 2 - VW * 0.4 + p.face * 40;
    G.cam.x += (target - G.cam.x) * Math.min(1, dt * 5);
  }
  G.cam.x = clamp(G.cam.x, 0, Math.max(0, L.w * TILE - VW));
  // meta
  if (!p.dead && !p.win && p.x + p.w > L.goal.x - 10) {
    p.win = true; p.vx = p.vy = 0; Sound.sfx.portal(); setState('clear'); G.score += 1000;
  }
}

function updatePlayer(dt, grav) {
  const p = G.p, L = G.L, W = WORLDS[G.wi];
  let ax = 0; if (Input.left) ax -= 1; if (Input.right) ax += 1;
  if (ax) { if (Math.sign(p.vx) !== ax && p.onGround) p.vx *= 0.8; p.vx += ax * (p.onGround ? ACC_G : ACC_A) * dt; p.face = ax; }
  else if (p.onGround) { const f = FRIC * dt; p.vx = Math.abs(p.vx) <= f ? 0 : p.vx - Math.sign(p.vx) * f; }
  else p.vx *= 0.99;
  p.vx = clamp(p.vx, -RUN, RUN);
  // salto con buffer y coyote time
  if (Input.jumpPressed) p.jbuf = 0.13; else p.jbuf -= dt;
  p.coyote = p.onGround ? 0.1 : p.coyote - dt;
  if (p.jbuf > 0) {
    if (p.coyote > 0) {
      p.vy = -JV; p.coyote = 0; p.jbuf = 0; p.jumps = 1; p.onGround = false; p.ride = null; Sound.sfx.jump();
      burst(p.x + p.w / 2, p.y + p.h, '#ffffff', 6, 120, 0.3);
    } else if (p.jumps < 2) {
      p.vy = -DJV; p.jumps = 2; p.jbuf = 0; p.jetT = 0.35; Sound.sfx.djump();
      burst(p.x + p.w / 2 - p.face * 8, p.y + p.h - 10, W.accent2, 16, 260, 0.5);
    }
  }
  p.jetT = Math.max(0, p.jetT - dt);
  let g = grav; if (p.vy < 0 && !Input.jump) g *= 2.4;
  p.vy = Math.min(p.vy + g * dt, MAXF);
  // arrastre por plataforma
  if (p.ride) { p.x += p.ride.fdx || 0; if (p.vy >= 0) p.y = p.ride.y - p.h; }
  const wasGround = p.onGround, prevBottom = p.y + p.h;
  const r = moveBody(p, dt, true);
  p.onGround = r.ground;
  if (r.head) bumpBox(r.head.tx, r.head.ty);
  // aterrizar en plataformas móviles
  let rode = null;
  if (p.vy >= 0) for (const e of L.ents) if (e.type === 'mplat') {
    if (p.x + p.w > e.x + 2 && p.x < e.x + e.w - 2 && prevBottom <= e.y + 8 && p.y + p.h >= e.y) { p.y = e.y - p.h; p.vy = 0; p.onGround = true; rode = e; break; }
  }
  p.ride = rode;
  if (p.onGround) { p.jumps = 0; if (!wasGround) { p.squash = 1; burst(p.x + p.w / 2, p.y + p.h, '#ffffff', 4, 80, 0.25); } }
  p.squash = Math.max(0, p.squash - dt * 6);
  p.anim += Math.abs(p.vx) * dt * 0.05;
  p.inv = Math.max(0, p.inv - dt);
  // peligros
  const hx0 = Math.floor((p.x + 4) / TILE), hx1 = Math.floor((p.x + p.w - 4) / TILE), hy0 = Math.floor((p.y + 6) / TILE), hy1 = Math.floor((p.y + p.h - 1) / TILE);
  for (let ty = hy0; ty <= hy1; ty++) for (let tx = hx0; tx <= hx1; tx++) {
    if (tileAt(tx, ty) === T_HAZARD && p.y + p.h > ty * TILE + 14) { hurt(); if (p.dead) return; }
  }
  if (p.y > ROWS * TILE + 60) die();
}

function checkEnemyHit(e) {
  const p = G.p; if (p.dead || p.win || !e.alive) return;
  const box = { x: e.x + 3, y: e.y + 3, w: e.w - 6, h: e.h - 3 };
  if (!overlap(p, box)) return;
  if (p.vy > 60 && p.y + p.h - e.y < 18 + p.vy * STEP) {
    killEnemy(e); p.vy = Input.jump ? -780 : -500; p.jumps = 1; p.onGround = false;
  } else hurt();
}

// ---------------------------------------------------------------
// Renderizado del mundo
// ---------------------------------------------------------------
const lights = [], edges = [];
function renderWorld(showPlayer) {
  const L = G.L, W = WORLDS[G.wi], art = G.art, t = G.t;
  const camX = Math.round(G.cam.x * SCALE) / SCALE;
  drawBackground(ctx, art, camX, t, VW);
  lights.length = 0;
  const sx = G.shake > 0 ? (Math.random() - 0.5) * 10 * G.shake : 0, sy = G.shake > 0 ? (Math.random() - 0.5) * 10 * G.shake : 0;
  ctx.save(); ctx.translate(-camX + sx, sy);
  const x0 = camX - 60, x1 = camX + VW + 60;
  for (const d of L.decos) if (d.x > x0 && d.x < x1) drawDeco(ctx, d, W, t, lights);
  // tiles
  const tx0 = Math.max(0, Math.floor(camX / TILE)), tx1 = Math.min(L.w - 1, Math.ceil((camX + VW) / TILE));
  const T = art.tiles, g = L.grid, Lw = L.w; edges.length = 0;
  for (let tx = tx0; tx <= tx1; tx++) {
    for (let ty = 0; ty < ROWS; ty++) {
      const v = g[ty * Lw + tx]; if (!v) continue;
      const x = tx * TILE, y = ty * TILE;
      const h = (tx * 7 + ty * 13) % 3;
      if (v === T_GROUND) {
        const above = ty > 0 ? g[(ty - 1) * Lw + tx] : 0;
        ctx.drawImage(solid(above) ? T.fill[h] : T.top[h], x, y, TILE, TILE);
        if (tx > 0 && !solid(g[ty * Lw + tx - 1])) { ctx.fillStyle = rgba(W.accent, 0.35); ctx.fillRect(x, y, 2, TILE); }
        if (tx < Lw - 1 && !solid(g[ty * Lw + tx + 1])) { ctx.fillStyle = rgba(W.accent, 0.35); ctx.fillRect(x + TILE - 2, y, 2, TILE); }
        if (!solid(above)) { edges.push(x, y, 0); if (h === 0) lights.push(x + 20, y, 60, W.accent, 0.35); }
      } else if (v === T_BRICK) ctx.drawImage(T.brick, x, y, TILE, TILE);
      else if (v === T_BOX || v === T_USED) {
        const b = G.bumps[ty * Lw + tx]; const off = b !== undefined ? -Math.sin(b / 0.2 * Math.PI) * 8 : 0;
        ctx.drawImage(v === T_BOX ? T.box : T.used, x, y + off, TILE, TILE);
        if (v === T_BOX) lights.push(x + 20, y + 20, 70 + Math.sin(t * 3) * 10, '#ffd24a', 0.7);
      } else if (v === T_ONEWAY) ctx.drawImage(T.oneway, x, y, TILE, TILE);
      else if (v === T_HAZARD) {
        if (ty === ROWS - 1 && W.layer === 'volcano') {
          const lg = ctx.createLinearGradient(0, y, 0, y + TILE); lg.addColorStop(0, '#fff3a0'); lg.addColorStop(0.15, W.hazard); lg.addColorStop(1, '#5a0a00');
          ctx.fillStyle = lg; ctx.fillRect(x, y + 6 + Math.sin(t * 3 + tx) * 3, TILE, TILE);
        } else ctx.drawImage(T.hazard, x, y, TILE, TILE);
        lights.push(x + 20, y + 30, 70, W.hazard, 0.6);
      }
    }
  }
  // polvo estelar
  for (const c of L.coins) {
    if (c.taken || c.x < x0 || c.x > x1) continue;
    drawOrb(ctx, c.x, c.y + Math.sin(t * 2 + c.t) * 3, t + c.t, W.accent2);
    lights.push(c.x, c.y, 36, W.accent2, 0.8);
  }
  // portal
  if (L.goal.x > x0 - 100 && L.goal.x < x1 + 100) { drawPortal(ctx, L.goal, t, W); lights.push(L.goal.x, L.goal.y - 80, 260, W.accent, 1); }
  // entidades
  for (const e of L.ents) {
    const ex = e.x !== undefined ? e.x : e.bx;
    if (ex < x0 - 150 || ex > x1 + 150) continue;
    switch (e.type) {
      case 'walker': if (!e.dead || e.deadT < 0.4) { drawWalker(ctx, e, t, W); lights.push(e.x + 14, e.y + 12, 40, W.hazard, 0.5); } break;
      case 'floater': if (!e.dead || e.deadT < 0.5) { drawFloater(ctx, e, t, W); lights.push(e.x + 15, e.y + 15, 90, W.accent3, 0.8); } break;
      case 'spring': drawSpring(ctx, e, t, W); lights.push(e.x + 16, e.y, 50, W.accent2, 0.6); break;
      case 'mplat': drawMPlat(ctx, e, W); for (let k = 0; k < 3; k++) edges.push(e.x + k * TILE, e.y + 1, 1); lights.push(e.x + e.w / 2, e.y, 80, W.accent2, 0.5); break;
      case 'check': drawCheck(ctx, e, t, W); lights.push(e.x, e.y - 72, e.on ? 120 : 40, e.on ? W.accent2 : '#8890a8', 0.8); break;
    }
  }
  for (const it of G.items) { drawItem(ctx, it, t, W); lights.push(it.x + 14, it.y + 14, 80, it.kind === 'shield' ? W.accent : '#b8ff7a', 0.9); }
  // jugador
  const p = G.p;
  if (showPlayer && p) {
    const blink = p.inv > 0 && !p.dead && Math.floor(p.inv * 12) % 2 === 0;
    if (!blink) {
      ctx.save();
      if (p.win) { const k = clamp(1 - G.stateT / 2.2, 0, 1); ctx.globalAlpha = k; ctx.translate(p.x + p.w / 2, p.y + p.h / 2); ctx.rotate(G.stateT * 4); ctx.scale(k, k); ctx.translate(-(p.x + p.w / 2), -(p.y + p.h / 2)); }
      if (p.dead) { ctx.translate(p.x + p.w / 2, p.y + p.h / 2); ctx.rotate(p.deadT * 5); ctx.translate(-(p.x + p.w / 2), -(p.y + p.h / 2)); }
      drawAstronaut(ctx, p, t, W);
      ctx.restore();
    }
    if (p.shield && !p.dead) {
      ctx.globalCompositeOperation = 'lighter';
      ctx.strokeStyle = rgba(W.accent, 0.5 + Math.sin(t * 6) * 0.2); ctx.lineWidth = 2;
      ctx.beginPath(); ctx.ellipse(p.x + p.w / 2, p.y + p.h / 2 - 6, 26, 34, 0, 0, 7); ctx.stroke();
      ctx.globalCompositeOperation = 'source-over';
    }
    lights.push(p.x + p.w / 2, p.y + 10, p.shield ? 260 : 210, p.shield ? W.accent : '#dff6ff', 1);
    if (p.jetT > 0) lights.push(p.x + p.w / 2, p.y + p.h, 90, W.accent2, 0.9);
  }
  // partículas
  ctx.globalCompositeOperation = 'lighter';
  for (const q of G.parts) {
    const a = clamp(q.life / q.max, 0, 1);
    if (q.orb) { drawOrb(ctx, q.x, q.y, t * 3, q.c, 1.2); continue; }
    ctx.globalAlpha = a; ctx.drawImage(glowSprite(q.c, true), q.x - q.s, q.y - q.s, q.s * 2, q.s * 2);
  }
  ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over';
  ctx.restore();

  applyLighting(W, camX);
  // bordes de neón por encima de la oscuridad
  ctx.globalCompositeOperation = 'lighter'; ctx.globalAlpha = 0.8;
  for (let i = 0; i < edges.length; i += 3) ctx.drawImage(edges[i + 2] ? T.edge2 : T.edge, edges[i] - camX + sx, edges[i + 1] - 8 + sy, TILE, 16);
  ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over';
  drawAmbient(ctx, art, camX, t, VW);
  // textos flotantes
  ctx.textAlign = 'center'; ctx.font = 'bold 18px "Trebuchet MS", sans-serif';
  for (const q of G.pops) {
    ctx.globalAlpha = clamp(1.4 - q.t, 0, 1); glowText(q.text, q.x - camX + 12, q.y - q.t * 40, q.c, 12);
  }
  ctx.globalAlpha = 1;
}

function applyLighting(W, camX) {
  const k = lc.width / VW;
  lx.globalCompositeOperation = 'source-over'; lx.globalAlpha = 1;
  lx.clearRect(0, 0, lc.width, lc.height);
  lx.fillStyle = rgba(W.dark, W.darkA); lx.fillRect(0, 0, lc.width, lc.height);
  lx.drawImage(vignette, 0, 0, lc.width, lc.height);
  lx.globalCompositeOperation = 'destination-out';
  const wg = glowSprite('#ffffff');
  for (let i = 0; i < lights.length; i += 5) {
    const r = lights[i + 2] * k; lx.globalAlpha = Math.min(1, lights[i + 4]);
    lx.drawImage(wg, (lights[i] - camX) * k - r, lights[i + 1] * k - r, r * 2, r * 2);
  }
  lx.globalAlpha = 1; lx.globalCompositeOperation = 'source-over';
  ctx.drawImage(lc, 0, 0, VW, VIEW_H);
  // bloom de color
  ctx.globalCompositeOperation = 'lighter';
  for (let i = 0; i < lights.length; i += 5) {
    const r = lights[i + 2] * 0.55; ctx.globalAlpha = lights[i + 4] * 0.3;
    ctx.drawImage(glowSprite(lights[i + 3]), lights[i] - camX - r, lights[i + 1] - r, r * 2, r * 2);
  }
  ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over';
}

// ---------------------------------------------------------------
// UI
// ---------------------------------------------------------------
function glowText(text, x, y, c, blur) {
  ctx.save(); ctx.shadowColor = c; ctx.shadowBlur = blur || 16; ctx.fillStyle = '#ffffff'; ctx.fillText(text, x, y);
  ctx.shadowBlur = (blur || 16) * 0.5; ctx.fillText(text, x, y); ctx.restore();
}
function button(label, x, y, w, h, cb, c) {
  const i = G.buttons.length, sel = G.sel === i, W = WORLDS[G.wi] || WORLDS[0];
  c = c || W.accent;
  G.buttons.push({ x, y, w, h, cb });
  ctx.save();
  ctx.fillStyle = sel ? rgba(c, 0.28) : 'rgba(10,10,25,0.55)';
  roundRect(ctx, x, y, w, h, h / 2); ctx.fill();
  ctx.shadowColor = c; ctx.shadowBlur = sel ? 22 : 10; ctx.strokeStyle = rgba(c, sel ? 1 : 0.7); ctx.lineWidth = sel ? 3 : 2; ctx.stroke();
  ctx.shadowBlur = 0; ctx.fillStyle = '#ffffff'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  ctx.font = `bold ${Math.round(h * 0.4)}px "Trebuchet MS", sans-serif`; ctx.fillText(label, x + w / 2, y + h / 2 + 1);
  ctx.restore();
}
function wrapText(text, x, y, maxW, lh) {
  const words = text.split(' '); let line = '', lines = [];
  for (const w of words) { const t = line ? line + ' ' + w : w; if (ctx.measureText(t).width > maxW && line) { lines.push(line); line = w; } else line = t; }
  lines.push(line);
  lines.forEach((l, i) => ctx.fillText(l, x, y + i * lh));
  return lines.length;
}
function drawStarIcon(x, y, r, c) {
  ctx.fillStyle = c; ctx.beginPath();
  for (let i = 0; i < 8; i++) { const a = i * Math.PI / 4 - Math.PI / 2, rr = i % 2 ? r * 0.4 : r; ctx.lineTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr); }
  ctx.closePath(); ctx.fill();
}
function drawHUD() {
  const W = WORLDS[G.wi], p = G.p;
  ctx.textBaseline = 'middle'; ctx.textAlign = 'left'; ctx.font = 'bold 22px "Trebuchet MS", sans-serif';
  // vidas
  ctx.fillStyle = '#f4f6fb'; ctx.beginPath(); ctx.arc(34, 34, 12, 0, 7); ctx.fill();
  ctx.fillStyle = W.accent; roundRect(ctx, 33, 27, 11, 10, 4); ctx.fill();
  glowText('× ' + G.lives, 54, 35, W.accent, 8);
  // orbes
  drawStarIcon(126, 34, 12, '#ffffff'); ctx.fillStyle = W.accent2; ctx.beginPath(); ctx.arc(126, 34, 3.5, 0, 7); ctx.fill();
  glowText('× ' + G.orbs, 146, 35, W.accent2, 8);
  if (p && p.shield) { ctx.fillStyle = rgba(W.accent, 0.9); ctx.beginPath(); ctx.moveTo(232, 22); ctx.lineTo(243, 28); ctx.lineTo(241, 41); ctx.lineTo(232, 48); ctx.lineTo(223, 41); ctx.lineTo(221, 28); ctx.closePath(); ctx.fill(); }
  ctx.textAlign = 'center'; ctx.font = 'bold 16px "Trebuchet MS", sans-serif';
  glowText(`${G.wi + 1}-${G.li + 1}  ·  ${W.name.toUpperCase()}`, VW / 2, 30, W.accent, 10);
  // progreso del nivel
  const pw = 180, px = VW / 2 - pw / 2, pr = p ? clamp(p.x / G.L.goal.x, 0, 1) : 0;
  ctx.fillStyle = 'rgba(255,255,255,0.12)'; ctx.fillRect(px, 48, pw, 3);
  ctx.fillStyle = W.accent; ctx.fillRect(px, 48, pw * pr, 3);
  ctx.fillStyle = '#fff'; ctx.beginPath(); ctx.arc(px + pw * pr, 49.5, 4, 0, 7); ctx.fill();
  // pausa
  ctx.strokeStyle = 'rgba(255,255,255,0.7)'; ctx.lineWidth = 2; ctx.beginPath(); ctx.arc(VW - 44, 38, 22, 0, 7); ctx.stroke();
  ctx.fillStyle = '#fff'; ctx.fillRect(VW - 51, 29, 5, 18); ctx.fillRect(VW - 42, 29, 5, 18);
  // controles táctiles
  if (Input.touch) {
    const act = { L: false, R: false, J: false }; for (const q of Input.pointers.values()) if (q.btn) act[q.btn] = true;
    const pad = (x, y, r, on, drawIcon) => {
      ctx.fillStyle = on ? rgba(W.accent, 0.3) : 'rgba(255,255,255,0.06)'; ctx.beginPath(); ctx.arc(x, y, r, 0, 7); ctx.fill();
      ctx.strokeStyle = rgba(W.accent, on ? 0.9 : 0.35); ctx.lineWidth = 2; ctx.stroke();
      ctx.fillStyle = on ? '#ffffff' : 'rgba(255,255,255,0.6)'; drawIcon(x, y);
    };
    const tri = (x, y, d) => { ctx.beginPath(); ctx.moveTo(x + d * 16, y); ctx.lineTo(x - d * 10, y - 16); ctx.lineTo(x - d * 10, y + 16); ctx.fill(); };
    pad(90, 500, 52, act.L, (x, y) => tri(x, y, -1));
    pad(218, 500, 52, act.R, (x, y) => tri(x, y, 1));
    pad(VW - 110, 490, 64, act.J, (x, y) => { ctx.beginPath(); ctx.moveTo(x, y - 20); ctx.lineTo(x + 18, y + 6); ctx.lineTo(x + 6, y + 6); ctx.lineTo(x + 6, y + 18); ctx.lineTo(x - 6, y + 18); ctx.lineTo(x - 6, y + 6); ctx.lineTo(x - 18, y + 6); ctx.closePath(); ctx.fill(); });
  }
}

function overlay(a) { ctx.fillStyle = `rgba(2,2,10,${a})`; ctx.fillRect(0, 0, VW, VIEW_H); }

function drawTitle() {
  const wi = G.titleWorld, art = getArt(wi), W = WORLDS[wi], t = G.stateT;
  drawBackground(ctx, art, t * 40, t, VW);
  drawAmbient(ctx, art, t * 40, t, VW);
  ctx.drawImage(vignette, 0, 0, VW, VIEW_H);
  // astronauta flotando
  const ax = VW * 0.22, ay = 330 + Math.sin(t * 1.3) * 16;
  ctx.globalCompositeOperation = 'lighter'; ctx.globalAlpha = 0.5; ctx.drawImage(glowSprite(W.accent), ax - 110, ay - 130, 220, 220); ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over';
  ctx.save(); ctx.translate(ax, ay); ctx.rotate(Math.sin(t * 0.8) * 0.25); ctx.scale(2.2, 2.2);
  drawAstronaut(ctx, { x: -12, y: -23, w: 24, h: 46, face: 1, onGround: false, vx: 0, anim: 0, jetT: 1, squash: 0 }, t, W); ctx.restore();
  // título
  ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  const cx = VW * 0.6;
  ctx.font = 'bold 84px "Trebuchet MS", sans-serif'; glowText('ASTRO GLOW', cx, 150, W.accent, 30);
  ctx.font = 'italic 22px Georgia, serif'; ctx.fillStyle = rgba(W.accent2, 0.95); ctx.fillText('Viaje a través de la luz', cx, 205);
  const bw = 280, bh = 56;
  G.buttons = [];
  button(G.save.unlocked > 0 ? 'CONTINUAR' : 'JUGAR', cx - bw / 2, 260, bw, bh, () => { G.lives = 3; G.orbs = 0; G.score = 0; startLevel(G.save.unlocked, 0, true); });
  button('MUNDOS', cx - bw / 2, 332, bw, bh, () => setState('select'));
  button('MÚSICA: ' + (Sound.musicOn ? 'SÍ' : 'NO'), cx - bw / 2, 404, bw / 2 - 6, 46, () => Sound.toggleMusic());
  button('SONIDO: ' + (Sound.sfxOn ? 'SÍ' : 'NO'), cx + 6, 404, bw / 2 - 6, 46, () => Sound.toggleSfx());
  ctx.font = '14px "Trebuchet MS", sans-serif'; ctx.fillStyle = 'rgba(255,255,255,0.55)';
  ctx.fillText(Input.touch ? 'Toca los botones para moverte y saltar · doble salto con propulsor' : 'Flechas / A-D para moverte · Espacio para saltar (doble salto) · P pausa', VW / 2, 570);
  const fade = t % 12;
  if (fade > 11.3) overlay((fade - 11.3) / 0.7); else if (fade < 0.6 && t > 1) overlay(1 - fade / 0.6);
  G.titleWorld = Math.floor((t + 0.35) / 12) % WORLDS.length;
}

function drawSelect() {
  const art = getArt(G.titleWorld), t = G.stateT;
  drawBackground(ctx, art, t * 30 + 400, t, VW); overlay(0.45);
  ctx.textAlign = 'center'; ctx.textBaseline = 'middle'; ctx.font = 'bold 40px "Trebuchet MS", sans-serif';
  glowText('ELIGE TU GALAXIA', VW / 2, 60, WORLDS[G.titleWorld].accent, 20);
  G.buttons = [];
  const cw = Math.min(300, (VW - 80) / 3 - 20), ch = 150, gx = VW / 2 - (cw * 3 + 40) / 2;
  WORLDS.forEach((W, i) => {
    const x = gx + (i % 3) * (cw + 20), y = 110 + Math.floor(i / 3) * (ch + 22), locked = i > G.save.unlocked;
    const bi = G.buttons.length, sel = G.sel === bi;
    G.buttons.push({ x, y, w: cw, h: ch, cb: () => { if (!locked) { G.lives = 3; G.orbs = 0; G.score = 0; startLevel(i, 0, true); } else Sound.sfx.bump(); } });
    ctx.save();
    const gr = ctx.createLinearGradient(0, y, 0, y + ch); gr.addColorStop(0, W.sky[0]); gr.addColorStop(1, W.sky[2]);
    ctx.fillStyle = gr; roundRect(ctx, x, y, cw, ch, 16); ctx.fill();
    ctx.clip();
    ctx.globalCompositeOperation = 'lighter';
    ctx.globalAlpha = locked ? 0.15 : 0.6; ctx.drawImage(glowSprite(W.accent), x + cw * 0.55, y + 10, cw * 0.6, ch * 0.9);
    ctx.drawImage(glowSprite(W.accent2), x - 20, y + ch * 0.4, cw * 0.5, ch * 0.7);
    ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over';
    ctx.restore();
    ctx.save(); ctx.shadowColor = W.accent; ctx.shadowBlur = sel ? 26 : 10; ctx.strokeStyle = rgba(W.accent, locked ? 0.3 : sel ? 1 : 0.7); ctx.lineWidth = sel ? 3 : 2;
    roundRect(ctx, x, y, cw, ch, 16); ctx.stroke(); ctx.restore();
    ctx.fillStyle = locked ? 'rgba(255,255,255,0.35)' : '#fff';
    ctx.font = 'bold 15px "Trebuchet MS", sans-serif'; ctx.fillText('MUNDO ' + (i + 1), x + cw / 2, y + 30);
    ctx.font = 'bold 19px "Trebuchet MS", sans-serif'; wrapText(W.name, x + cw / 2, y + 66, cw - 20, 24);
    if (locked) { ctx.font = '26px sans-serif'; ctx.fillText('🔒', x + cw / 2, y + 118); }
    else { ctx.font = '13px "Trebuchet MS", sans-serif'; ctx.fillStyle = rgba(W.accent2, 0.9); ctx.fillText('Toca para viajar', x + cw / 2, y + 122); }
  });
  button('VOLVER', VW / 2 - 100, 470, 200, 50, () => setState('title'));
}

function drawIntro() {
  const W = WORLDS[G.wi], t = G.stateT;
  renderWorld(true);
  const a = t < 3 ? 1 : clamp(1 - (t - 3) / 0.6, 0, 1);
  overlay(0.75 * a);
  ctx.globalAlpha = a; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  ctx.font = 'bold 22px "Trebuchet MS", sans-serif'; ctx.fillStyle = rgba(W.accent2, 1);
  ctx.fillText(`MUNDO ${G.wi + 1}-${G.li + 1}`, VW / 2, 190);
  ctx.font = 'bold 56px "Trebuchet MS", sans-serif'; glowText(W.name.toUpperCase(), VW / 2, 245, W.accent, 26);
  ctx.globalAlpha = a * clamp((t - 0.6) / 0.8, 0, 1);
  ctx.font = 'italic 24px Georgia, serif'; ctx.fillStyle = '#e8f0ff';
  wrapText('“' + W.quote + '”', VW / 2, 320, Math.min(760, VW - 80), 34);
  ctx.globalAlpha = 1;
}
function drawClearOverlay() {
  const W = WORLDS[G.wi], t = G.stateT; if (t < 1.2) return;
  const a = clamp((t - 1.2) / 0.5, 0, 1);
  ctx.globalAlpha = a; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  ctx.font = 'bold 50px "Trebuchet MS", sans-serif';
  glowText(G.li === LEVELS_PER_WORLD - 1 ? '¡GALAXIA ILUMINADA!' : '¡NIVEL COMPLETADO!', VW / 2, 200, W.accent2, 26);
  ctx.font = '22px "Trebuchet MS", sans-serif'; ctx.fillStyle = '#fff';
  ctx.fillText('Puntuación: ' + G.score, VW / 2, 260);
  ctx.globalAlpha = 1;
}
function drawGameOver() {
  const W = WORLDS[G.wi];
  renderWorld(false); overlay(0.7);
  ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  ctx.font = 'bold 58px "Trebuchet MS", sans-serif'; glowText('¡NUNCA TE RINDAS!', VW / 2, 170, W.accent, 26);
  ctx.font = 'italic 24px Georgia, serif'; ctx.fillStyle = '#e8f0ff';
  ctx.fillText('“Cada caída es solo impulso para tu próximo salto.”', VW / 2, 235);
  G.buttons = [];
  button('REINTENTAR', VW / 2 - 130, 300, 260, 56, () => { G.lives = 3; G.orbs = 0; startLevel(G.wi, G.li, true); });
  button('MENÚ', VW / 2 - 130, 372, 260, 50, () => { setState('title'); Sound.playSong(SONGS.title); });
}
function drawPause() {
  renderWorld(true); overlay(0.6);
  const W = WORLDS[G.wi];
  ctx.textAlign = 'center'; ctx.textBaseline = 'middle'; ctx.font = 'bold 50px "Trebuchet MS", sans-serif';
  glowText('PAUSA', VW / 2, 120, W.accent, 24);
  G.buttons = [];
  const bw = 280, x = VW / 2 - bw / 2;
  button('CONTINUAR', x, 180, bw, 54, () => setState('play'));
  button('REINICIAR NIVEL', x, 246, bw, 50, () => { G.cp = null; G.p = null; startLevel(G.wi, G.li, false); });
  button('MÚSICA: ' + (Sound.musicOn ? 'SÍ' : 'NO'), x, 308, bw / 2 - 6, 46, () => Sound.toggleMusic());
  button('SONIDO: ' + (Sound.sfxOn ? 'SÍ' : 'NO'), x + bw / 2 + 6, 308, bw / 2 - 6, 46, () => Sound.toggleSfx());
  button('MENÚ PRINCIPAL', x, 366, bw, 50, () => { setState('title'); Sound.playSong(SONGS.title); });
}
function drawEnding() {
  const t = G.stateT, wi = 5, art = getArt(wi), W = WORLDS[wi];
  drawBackground(ctx, art, t * 25, t, VW); drawAmbient(ctx, art, t * 25, t, VW);
  ctx.globalCompositeOperation = 'lighter';
  for (let i = 0; i < 6; i++) { const c = WORLDS[i].accent, a = t * 0.4 + i / 6 * Math.PI * 2; ctx.globalAlpha = 0.5; ctx.drawImage(glowSprite(c), VW / 2 + Math.cos(a) * 220 - 60, 300 + Math.sin(a) * 60 - 60, 120, 120); }
  ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over';
  ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  ctx.font = 'bold 54px "Trebuchet MS", sans-serif'; glowText('HAS ILUMINADO LA GALAXIA', VW / 2, 120, W.accent, 30);
  ctx.font = 'italic 25px Georgia, serif'; ctx.fillStyle = '#fff4e0';
  wrapText('“Cada paso en la oscuridad fue un paso hacia tu propia luz.”', VW / 2, 190, Math.min(760, VW - 80), 34);
  ctx.save(); ctx.translate(VW / 2, 310 + Math.sin(t * 1.5) * 10); ctx.scale(2, 2);
  drawAstronaut(ctx, { x: -12, y: -23, w: 24, h: 46, face: 1, onGround: false, vx: 0, anim: 0, jetT: 1, squash: 0 }, t, W); ctx.restore();
  ctx.font = '22px "Trebuchet MS", sans-serif'; ctx.fillStyle = '#fff';
  ctx.fillText('Puntuación final: ' + G.score + '   ·   Polvo estelar total: ' + (G.save.orbsTotal || 0), VW / 2, 420);
  G.buttons = [];
  if (t > 3) button('VOLVER AL INICIO', VW / 2 - 150, 470, 300, 54, () => { setState('title'); Sound.playSong(SONGS.title); });
}

// ---------------------------------------------------------------
// Bucle principal
// ---------------------------------------------------------------
function nextLevel() {
  let wi = G.wi, li = G.li + 1;
  if (li >= LEVELS_PER_WORLD) { wi++; li = 0; }
  if (wi >= WORLDS.length) { G.save.unlocked = WORLDS.length - 1; persist(); setState('ending'); Sound.playSong(SONGS.ending); return; }
  G.save.unlocked = Math.max(G.save.unlocked, wi); persist();
  G.p.shield = G.p.shield; G.cp = null;
  startLevel(wi, li, false);
}

function update(dt) {
  Input.updateKeys();
  G.stateT += dt;
  // navegación de menús con teclado
  const nav = Input.menuKeys.splice(0);
  if (G.buttons.length && G.state !== 'play') {
    for (const k of nav) {
      if (k === 'ArrowDown' || k === 'ArrowRight' || k === 'KeyS' || k === 'KeyD') { G.sel = (G.sel + 1) % G.buttons.length; Sound.sfx.select(); }
      else if (k === 'ArrowUp' || k === 'ArrowLeft' || k === 'KeyW' || k === 'KeyA') { G.sel = (G.sel - 1 + G.buttons.length) % G.buttons.length; Sound.sfx.select(); }
      else if (k === 'Enter' || k === 'Space') { const b = G.buttons[G.sel]; if (b) { Sound.sfx.select(); b.cb(); } }
    }
  }
  if (nav.includes('Escape') || nav.includes('KeyP')) {
    if (G.state === 'play') setState('pause'); else if (G.state === 'pause') setState('play');
  }
  switch (G.state) {
    case 'intro':
      G.t += dt;
      if (G.stateT > 3.6 || (G.stateT > 0.4 && (Input.jumpPressed || Input.taps.length))) { setState('play'); Input.jumpPressed = false; }
      break;
    case 'play': updatePlay(dt); break;
    case 'clear': updatePlay(dt); if (G.stateT > 3.4) nextLevel(); break;
  }
  if (G.state !== 'play' && G.state !== 'clear') {
    for (const tp of Input.taps) {
      for (let i = 0; i < G.buttons.length; i++) { const b = G.buttons[i]; if (tp.x >= b.x && tp.x <= b.x + b.w && tp.y >= b.y && tp.y <= b.y + b.h) { G.sel = i; Sound.sfx.select(); b.cb(); break; } }
    }
  }
  Input.consume();
}

function render() {
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.fillStyle = '#000'; ctx.fillRect(0, 0, cv.width, cv.height);
  ctx.setTransform(SCALE, 0, 0, SCALE, 0, 0);
  if (G.state !== 'title' && G.state !== 'select' && G.state !== 'ending' && G.state !== 'pause' && G.state !== 'gameover') G.buttons = [];
  switch (G.state) {
    case 'title': drawTitle(); break;
    case 'select': drawSelect(); break;
    case 'intro': drawIntro(); break;
    case 'play': renderWorld(true); drawHUD(); break;
    case 'clear': renderWorld(true); drawHUD(); drawClearOverlay(); if (G.stateT > 2.8) overlay(clamp((G.stateT - 2.8) / 0.6, 0, 1)); break;
    case 'pause': drawPause(); break;
    case 'gameover': drawGameOver(); break;
    case 'ending': drawEnding(); break;
  }
  if (G.sel >= G.buttons.length) G.sel = 0;
}

let last = performance.now(), acc = 0;
function frame(now) {
  const dt = Math.min(0.1, (now - last) / 1000); last = now; acc += dt;
  let n = 0;
  while (acc >= STEP && n < 5) { update(STEP); acc -= STEP; n++; }
  if (n === 5) acc = 0;
  render();
  requestAnimationFrame(frame);
}

// ---------------------------------------------------------------
// Eventos de puntero / ciclo de vida
// ---------------------------------------------------------------
Input.menuKeys = [];
addEventListener('keydown', e => { if (!e.repeat) Input.menuKeys.push(e.code); });
function toLogical(e) { const r = cv.getBoundingClientRect(); return { x: (e.clientX - r.left) / r.width * VW, y: (e.clientY - r.top) / r.height * VIEW_H }; }
function zoneFor(x, y) {
  if (G.state !== 'play') return null;
  if (x < 154) return 'L';
  if (x < Math.min(VW * 0.42, 330)) return 'R';
  if (x > VW * 0.55) return 'J';
  return null;
}
cv.addEventListener('pointerdown', e => {
  e.preventDefault(); Sound.unlock();
  if (e.pointerType === 'touch') Input.touch = true;
  const q = toLogical(e);
  if (G.state === 'play' && Math.hypot(q.x - (VW - 44), q.y - 38) < 36) { setState('pause'); return; }
  if (G.state === 'play' && e.pointerType !== 'touch') { Input.taps.push(q); return; }
  const btn = zoneFor(q.x, q.y);
  Input.pointers.set(e.pointerId, { btn });
  if (btn === 'J') Input.jumpPressed = true;
  Input.taps.push(q);
  try { cv.setPointerCapture(e.pointerId); } catch (err) { }
});
cv.addEventListener('pointermove', e => {
  const p = Input.pointers.get(e.pointerId); if (!p) return;
  if (p.btn === 'L' || p.btn === 'R') { const q = toLogical(e); const z = zoneFor(q.x, q.y); if (z === 'L' || z === 'R') p.btn = z; }
});
const endPtr = e => Input.pointers.delete(e.pointerId);
cv.addEventListener('pointerup', endPtr); cv.addEventListener('pointercancel', endPtr);
addEventListener('contextmenu', e => e.preventDefault());
document.addEventListener('visibilitychange', () => {
  if (document.hidden) { if (G.state === 'play') setState('pause'); Sound.suspend(); Input.pointers.clear(); }
  else Sound.resume();
});
// Botón "atrás" de Android: devuelve true si el juego lo manejó
window.onAndroidBack = function () {
  switch (G.state) {
    case 'play': setState('pause'); return true;
    case 'pause': setState('play'); return true;
    case 'select': setState('title'); return true;
    case 'gameover': case 'ending': case 'intro': case 'clear': setState('title'); Sound.playSong(SONGS.title); return true;
    default: return false;
  }
};
window.onAndroidPause = function () { if (G.state === 'play') setState('pause'); Sound.suspend(); Input.pointers.clear(); };
window.onAndroidResume = function () { Sound.resume(); };

Sound.playSong(SONGS.title);
requestAnimationFrame(frame);
