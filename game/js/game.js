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
  shots: [], rings: [], combo: 0, comboT: 0, comboPop: 0, hitstop: 0, slowT: 0, flash: { a: 0, c: '#fff' },
  boss: null, bossOn: false, banner: 0, jq: false,
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
  G.shots = []; G.rings = []; G.combo = 0; G.comboT = 0; G.hitstop = 0; G.slowT = 0; G.flash.a = 0; G.banner = 0;
  G.boss = G.L.boss || null; G.bossOn = false;
  if (fresh) { G.lives = Math.max(G.lives, 3); }
  spawnPlayer();
  G.cam.x = 0;
  Sound.playSong(WORLDS[wi].music);
  setState('intro');
}
function spawnPlayer() {
  const s = G.cp || G.L.start;
  G.p = { x: s.x, y: s.y, w: 24, h: 46, vx: 0, vy: 0, face: 1, onGround: false, jumps: 0, coyote: 0, jbuf: 0, anim: 0, jetT: 0,
    shield: G.p ? G.p.shield && !G.p.dead : false, inv: 1.2, dead: false, deadT: 0, ride: null, squash: 0, win: false,
    fireCd: 0, muzzle: 0, trail: [], boostT: 0, spin: 0 };
  G.shots = G.shots.filter(s => s.pl);
  if (G.boss && G.boss.alive && G.bossOn && G.boss.mode !== 'enter') { G.boss.mode = 'idle'; G.boss.st = -1; }
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
    p.shield = false; p.inv = 1.6; p.vy = -620; Sound.sfx.hurt(); G.shake = 0.4; flash('#ff3050', 0.35); G.hitstop = 0.08;
    burst(p.x + p.w / 2, p.y + p.h / 2, WORLDS[G.wi].accent, 30, 400, 0.7);
  } else die();
}
function die() {
  const p = G.p; if (p.dead) return;
  p.dead = true; p.deadT = 0; p.vy = -760; p.vx = 0; p.shield = false; G.lives--; G.shake = 0.6; G.combo = 0;
  flash('#ff3050', 0.55); G.hitstop = 0.12; ring(p.x + p.w / 2, p.y + p.h / 2, '#ffffff', 160, 0.5, 6);
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
  const W = WORLDS[G.wi];
  e.alive = false; e.dead = true; e.deadT = 0; G.score += 100;
  explode(e.x + e.w / 2, e.y + e.h / 2, e.type === 'floater' ? W.accent3 : W.hazard, e.type === 'charger');
  addCombo(e.x + e.w / 2, e.y);
  addOrbs(e.type === 'charger' ? 5 : e.type === 'turret' ? 3 : 1);
}

// ---------------------------------------------------------------
// Combate: disparos, explosiones, combos y jefe
// ---------------------------------------------------------------
function ring(x, y, c, max, life, w) { G.rings.push({ x, y, c, max, life, t: 0, w: w || 4 }); }
function flash(c, a) { G.flash.c = c; G.flash.a = Math.max(G.flash.a, a); }
function explode(x, y, c, big) {
  burst(x, y, c, big ? 60 : 26, big ? 640 : 400, big ? 1.1 : 0.7);
  burst(x, y, '#ffffff', big ? 22 : 8, big ? 320 : 200, 0.4);
  for (let i = 0; i < (big ? 16 : 7); i++) G.parts.push({ x, y, vx: (Math.random() - 0.5) * 520, vy: -200 - Math.random() * 420, life: 1, max: 1, c, s: 3 + Math.random() * 3, g: 1400 });
  ring(x, y, c, big ? 280 : 110, big ? 0.7 : 0.4, big ? 9 : 5);
  if (big) { ring(x, y, '#ffffff', 190, 0.5, 3); flash(c, 0.35); }
  G.shake = Math.max(G.shake, big ? 0.7 : 0.28);
  G.hitstop = Math.max(G.hitstop, big ? 0.1 : 0.045);
  Sound.sfx[big ? 'boom' : 'explode']();
}
function addCombo(x, y) {
  G.combo = G.comboT > 0 ? G.combo + 1 : 1; G.comboT = 2.4; G.comboPop = 1;
  G.score += 100 * G.combo;
  if (G.combo >= 2) {
    Sound.sfx.combo(G.combo);
    if (G.combo % 5 === 0) { flash(WORLDS[G.wi].accent2, 0.4); popup(x, y - 30, '¡IMPARABLE!', '#ffe45a'); G.slowT = Math.max(G.slowT, 0.5); }
  }
}
function damageEnemy(e, dmg, kx) {
  if (!e.alive) return;
  e.hp -= dmg; e.flash = 0.1;
  if (kx && e.type !== 'turret' && e.type !== 'floater') e.x += kx;
  if (e.hp <= 0) killEnemy(e); else Sound.sfx.hit();
}
function fireShot(x, y, vx, vy, pl, c, r) { G.shots.push({ x, y, vx, vy, pl, c, r: r || 7, life: pl ? 0.6 : 6, t: 0 }); }
function hitBoss(d) {
  const b = G.boss, W = WORLDS[G.wi]; if (!b || !b.alive) return;
  b.hp -= d; b.flash = 0.09; G.score += 25 * d; Sound.sfx.hit();
  if (b.phase === 1 && b.hp <= b.max / 2) {
    b.phase = 2; flash(W.hazard, 0.5); Sound.sfx.roar(); G.shake = 0.8;
    ring(b.x + b.w / 2, b.y + b.h / 2, W.hazard, 340, 0.8, 10); popup(b.x + b.w / 2 - 40, b.y - 20, '¡FURIA!', W.hazard);
  }
  if (b.hp <= 0) {
    b.alive = false; b.deadT = 0; b.hp = 0; G.slowT = 2.2; Sound.sfx.roar(); flash('#ffffff', 0.6);
    for (const s of G.shots) if (!s.pl) { s.life = 0; burst(s.x, s.y, W.accent2, 4, 120, 0.4); }
  }
}
function updateBoss(dt) {
  const b = G.boss, L = G.L, p = G.p, W = WORLDS[G.wi], A = L.arena;
  if (!b) return;
  const px = p.x + p.w / 2, py = p.y + p.h / 2;
  if (!G.bossOn) {
    if (!p.dead && p.x > A.x0 + 3 * TILE) {
      G.bossOn = true; G.cp = { x: A.x0 + 2 * TILE, y: A.floor - p.h };
      b.mode = 'enter'; b.st = 0; b.y = -160; b.x = A.x1 - 9 * TILE;
      Sound.playSong(SONGS.boss); Sound.sfx.warn(); G.banner = 3;
    }
    return;
  }
  b.t += dt; b.st += dt; b.flash = Math.max(0, b.flash - dt);
  let cx = b.x + b.w / 2, cy = b.y + b.h / 2;
  if (!b.alive) {
    b.deadT += dt; b.y += 30 * dt;
    if (b.deadT < 1.6 && Math.random() < 0.25) explode(cx + (Math.random() - 0.5) * 120, cy + (Math.random() - 0.5) * 120, Math.random() < 0.5 ? W.hazard : W.accent, false);
    if (b.deadT > 1.6 && !b.finalBoom) {
      b.finalBoom = true; explode(cx, cy, W.accent, true); explode(cx, cy, '#ffffff', true); flash('#ffffff', 1);
      L.goalLocked = false; G.score += 5000; addOrbs(30);
      popup(cx - 60, cy - 70, '¡GUARDIÁN VENCIDO!', W.accent2);
      ring(L.goal.x, L.goal.y - 80, W.accent, 400, 1.2, 10);
      Sound.playSong(W.music);
    }
    return;
  }
  const P2 = b.phase > 1, spd = P2 ? 1.35 : 1;
  const hover = () => { b.y = lerp(b.y, 120 + Math.sin(b.t * 2) * 35, dt * 3); b.x = lerp(b.x, b.tx, dt * 1.5 * spd); };
  switch (b.mode) {
    case 'enter':
      b.y = lerp(b.y, 130, dt * 1.6);
      if (b.st > 2.2) { Sound.sfx.roar(); G.shake = 0.9; flash(W.hazard, 0.3); ring(cx, cy, W.hazard, 320, 0.8, 8); b.mode = 'idle'; b.st = 0; b.tx = b.x; }
      break;
    case 'idle':
      if (b.st === dt || b.tx === undefined) b.tx = A.x0 + 4 * TILE + Math.random() * (A.x1 - A.x0 - 8 * TILE);
      hover();
      if (b.st > (P2 ? 0.8 : 1.3)) {
        const opts = ['aim', 'ring', 'dive'].concat(P2 ? ['rain', 'summon', 'aim'] : []);
        b.next = opts[Math.floor(Math.random() * opts.length)]; if (b.next === b.last) b.next = 'aim';
        b.last = b.next; b.mode = 'tell'; b.st = 0; ring(cx, cy, W.hazard, 120, 0.4, 4); Sound.sfx.warn();
      }
      break;
    case 'tell':
      hover();
      if (b.st > (P2 ? 0.4 : 0.6)) { b.mode = b.next; b.st = 0; b.n = 0; b.tx = px; }
      break;
    case 'aim':
      hover();
      if (b.st > 0.38) {
        b.st = 0; b.n++;
        const a = Math.atan2(py - cy, px - cx), k = P2 ? 5 : 3;
        for (let i = 0; i < k; i++) { const an = a + (i - (k - 1) / 2) * 0.22; fireShot(cx, cy, Math.cos(an) * 380 * spd, Math.sin(an) * 380 * spd, false, W.hazard, 9); }
        Sound.sfx.eshot();
        if (b.n >= 3) { b.mode = 'idle'; b.st = 0; b.tx = undefined; }
      }
      break;
    case 'ring':
      hover();
      if (b.st > 0.55) {
        b.st = 0; b.n++;
        const k = P2 ? 20 : 14, off = b.n * 0.3;
        for (let i = 0; i < k; i++) { const an = off + i / k * Math.PI * 2; fireShot(cx, cy, Math.cos(an) * 250 * spd, Math.sin(an) * 250 * spd, false, W.accent3, 8); }
        ring(cx, cy, W.accent3, 160, 0.4, 5); Sound.sfx.eshot();
        if (b.n >= (P2 ? 3 : 2)) { b.mode = 'idle'; b.st = 0; b.tx = undefined; }
      }
      break;
    case 'dive':
      if (b.n === 0) { b.x = lerp(b.x, b.tx - b.w / 2, dt * 5); b.y = lerp(b.y, 90, dt * 5); if (b.st > 0.55) { b.n = 1; b.vy = 200; } }
      else if (b.n === 1) {
        b.vy += 3000 * dt; b.y += b.vy * dt;
        if (b.y + b.h >= A.floor) {
          b.y = A.floor - b.h; b.n = 2; b.st = 0; G.shake = 0.9; flash(W.hazard, 0.25); Sound.sfx.explode();
          ring(cx, A.floor, W.hazard, 260, 0.6, 8); burst(cx, A.floor, W.hazard, 30, 450, 0.7);
          for (const d of [-1, 1]) fireShot(cx + d * 50, A.floor - 14, d * 380 * spd, 0, false, W.hazard, 14);
        }
      } else { b.y = lerp(b.y, 130, dt * 2.2); if (b.st > 0.9) { b.mode = 'idle'; b.st = 0; b.tx = undefined; } }
      break;
    case 'rain':
      hover();
      if (b.st > 0.1 && b.n < 14) { b.st = 0; b.n++; fireShot(A.x0 + Math.random() * (A.x1 - A.x0), -20, 0, 240 + Math.random() * 120, false, W.accent2, 9); }
      if (b.n >= 14) { b.mode = 'idle'; b.st = 0; b.tx = undefined; }
      break;
    case 'summon':
      hover();
      for (let i = 0; i < 2; i++) L.ents.push({ type: 'floater', hp: 1, bx: cx + (i ? 90 : -90), by: cy + 60, x: 0, y: 0, w: 30, h: 30, ax: 1, amp: 70, spd: 1.6, ph: i * 3, alive: true, t: 0 });
      ring(cx, cy, W.accent3, 200, 0.5, 6); Sound.sfx.roar();
      b.mode = 'idle'; b.st = 0; b.tx = undefined;
      break;
  }
  b.x = clamp(b.x, A.x0 + 10, A.x1 - b.w - 10);
  cx = b.x + b.w / 2; cy = b.y + b.h / 2;
  // contacto
  if (!p.dead && !p.win && b.mode !== 'enter' && Math.hypot(px - cx, py - cy) < b.w / 2 + 14) {
    if (p.vy > 100 && py < cy - b.h * 0.25) { hitBoss(3); p.vy = -820; p.jumps = 1; Sound.sfx.stomp(); ring(px, p.y + p.h, '#ffffff', 80, 0.3, 4); }
    else hurt();
  }
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
  if (G.boss) updateBoss(dt);

  // disparos
  for (const s of G.shots) {
    s.t += dt; s.life -= dt; s.x += s.vx * dt; s.y += s.vy * dt;
    const tx = Math.floor(s.x / TILE), ty = Math.floor(s.y / TILE), v = tileAt(tx, ty);
    if (solid(v) && !(s.vy === 0 && !s.pl && s.r >= 14)) {
      s.life = 0; burst(s.x, s.y, s.pl ? W.accent : (s.c || W.hazard), 6, 160, 0.25);
      if (s.pl && v === T_BOX) bumpBox(tx, ty);
      continue;
    }
    if (s.pl) {
      for (const e of L.ents) if (e.alive && e.hp && Math.abs(s.x - (e.x + e.w / 2)) < e.w / 2 + 6 && Math.abs(s.y - (e.y + e.h / 2)) < e.h / 2 + 8) {
        damageEnemy(e, 1, Math.sign(s.vx) * 5); s.life = 0; burst(s.x, s.y, '#ffffff', 6, 220, 0.2); break;
      }
      const b = G.boss;
      if (s.life > 0 && b && b.alive && G.bossOn && b.mode !== 'enter' && Math.hypot(s.x - (b.x + b.w / 2), s.y - (b.y + b.h / 2)) < b.w / 2 + 10) {
        hitBoss(1); s.life = 0; burst(s.x, s.y, '#ffffff', 8, 260, 0.25);
      }
      if (s.life > 0) for (const o of G.shots) if (!o.pl && o.life > 0 && o.r < 14 && Math.hypot(o.x - s.x, o.y - s.y) < o.r + 8) {
        o.life = 0; s.life = 0; burst(o.x, o.y, o.c || W.hazard, 8, 200, 0.3); G.score += 10; break;
      }
    } else if (!p.dead && !p.win && Math.abs(s.x - (p.x + p.w / 2)) < p.w / 2 + s.r * 0.5 && Math.abs(s.y - (p.y + p.h / 2)) < p.h / 2 + s.r * 0.5) { s.life = 0; hurt(); }
  }
  G.shots = G.shots.filter(s => s.life > 0 && s.x > G.cam.x - 400 && s.x < G.cam.x + VW + 400 && s.y < VIEW_H + 60 && s.y > -400);
  for (const r of G.rings) r.t += dt; G.rings = G.rings.filter(r => r.t < r.life);
  G.comboT = Math.max(0, G.comboT - dt); if (G.comboT <= 0) G.combo = 0; G.comboPop = Math.max(0, G.comboPop - dt * 4);
  G.flash.a = Math.max(0, G.flash.a - dt * 2.5); G.banner = Math.max(0, G.banner - dt);

  // enemigos
  const cx0 = G.cam.x - 200, cx1 = G.cam.x + VW + 200, px0 = p.x + p.w / 2, py0 = p.y + p.h / 2;
  for (const e of L.ents) {
    if (e.type === 'walker') {
      if (e.dead) { e.deadT += dt; continue; }
      if (e.x < cx0 || e.x > cx1) continue;
      e.t += dt; e.vy = Math.min(e.vy + grav * dt, MAXF); e.flash = Math.max(0, (e.flash || 0) - dt);
      const dir = Math.sign(e.vx) || -1, spd = Math.abs(e.vx) || 60;
      // girar en bordes
      const fx = dir > 0 ? e.x + e.w + 2 : e.x - 2, below = tileAt(Math.floor(fx / TILE), Math.floor((e.y + e.h + 4) / TILE));
      const r = moveBody(e, dt, true);
      if (r.wall) e.vx = -dir * spd; else if (r.ground && !solid(below) && below !== T_ONEWAY) e.vx = -dir * spd;
      if (e.y > ROWS * TILE + 50) e.alive = false, e.dead = true, e.deadT = 9;
      checkEnemyHit(e);
    } else if (e.type === 'floater') {
      e.t += dt; e.flash = Math.max(0, (e.flash || 0) - dt);
      if (e.dead) { e.deadT += dt; continue; }
      e.x = e.bx - e.w / 2 + (e.ax ? Math.sin(e.t * e.spd + e.ph) * e.amp : 0);
      e.y = e.by - e.h / 2 + (e.ax ? Math.sin(e.t * e.spd * 2 + e.ph) * 12 : Math.sin(e.t * e.spd + e.ph) * e.amp);
      if (e.x > cx0 && e.x < cx1) checkEnemyHit(e);
    } else if (e.type === 'turret') {
      if (e.dead) { e.deadT += dt; continue; }
      e.t += dt; e.flash = Math.max(0, (e.flash || 0) - dt);
      if (e.x < cx0 || e.x > cx1) continue;
      const dx = px0 - (e.x + 16), dy = py0 - (e.y + 8); e.aim = Math.atan2(dy, dx);
      if (e.charge > 0) {
        e.charge -= dt;
        if (e.charge <= 0) {
          const sp = 290 + L.diff * 12, n = L.diff >= 5 ? 3 : 1;
          for (let k = 0; k < n; k++) { const a = e.aim + (k - (n - 1) / 2) * 0.22; fireShot(e.x + 16, e.y + 8, Math.cos(a) * sp, Math.sin(a) * sp, false, W.hazard, 8); }
          Sound.sfx.eshot(); e.cd = 1.5 + Math.random() * 0.7; burst(e.x + 16, e.y + 8, W.hazard, 6, 150, 0.3);
        }
      } else if (Math.abs(dx) < 560 && !p.dead) { e.cd -= dt; if (e.cd <= 0) e.charge = 0.45; }
      checkEnemyHit(e);
    } else if (e.type === 'charger') {
      if (e.dead) { e.deadT += dt; continue; }
      if (e.x < cx0 || e.x > cx1) continue;
      e.t += dt; e.flash = Math.max(0, (e.flash || 0) - dt); e.vy = Math.min(e.vy + grav * dt, MAXF); e.mt -= dt;
      const dx = px0 - (e.x + e.w / 2), same = Math.abs(p.y + p.h - (e.y + e.h)) < 70;
      if (e.mode === 'walk') { e.vx = (Math.sign(e.vx) || -1) * 45; if (e.mt <= 0 && same && Math.abs(dx) < 360 && !p.dead) { e.mode = 'tell'; e.mt = 0.5; e.vx = Math.sign(dx) * 0.01; } }
      else if (e.mode === 'tell') { if (e.mt <= 0) { e.mode = 'charge'; e.mt = 1.1; e.vx = Math.sign(e.vx) * 440; Sound.sfx.eshot(); } }
      else if (e.mode === 'charge') { if (Math.random() < 0.6) burst(e.x + e.w / 2, e.y + e.h, W.hazard, 1, 80, 0.3); if (e.mt <= 0) { e.mode = 'walk'; e.mt = 0.9; } }
      const dir = Math.sign(e.vx) || -1, spd = Math.abs(e.vx);
      const fx = dir > 0 ? e.x + e.w + 2 : e.x - 2, below = tileAt(Math.floor(fx / TILE), Math.floor((e.y + e.h + 4) / TILE));
      const r = moveBody(e, dt, true);
      const turn = r.wall || (r.ground && !solid(below) && below !== T_ONEWAY);
      if (turn) {
        if (e.mode === 'charge' && r.wall) { G.shake = Math.max(G.shake, 0.3); ring(e.x + e.w / 2, e.y + e.h / 2, W.hazard, 70, 0.3, 4); }
        if (e.mode === 'charge') { e.mode = 'walk'; e.mt = 0.9; }
        e.vx = -dir * Math.max(spd, 45);
      }
      if (e.y > ROWS * TILE + 50) { e.alive = false; e.dead = true; e.deadT = 9; }
      checkEnemyHit(e);
    } else if (e.type === 'jorb') {
      e.t += dt; e.used = Math.max(0, e.used - dt * 3);
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
  if (G.bossOn && L.arena) {
    const A = L.arena, lo = A.x0 - (VW > A.x1 - A.x0 ? (VW - (A.x1 - A.x0)) / 2 : 0);
    G.cam.x = clamp(G.cam.x, lo, Math.max(lo, A.x1 - VW));
    if (!p.dead) p.x = clamp(p.x, A.x0, A.x1 - p.w);
  }
  // meta
  if (!p.dead && !p.win && !L.goalLocked && p.x + p.w > L.goal.x - 10) {
    p.win = true; p.vx = p.vy = 0; Sound.sfx.portal(); setState('clear'); G.score += 1000;
  }
}

function updatePlayer(dt, grav) {
  const p = G.p, L = G.L, W = WORLDS[G.wi];
  let ax = 0; if (Input.left) ax -= 1; if (Input.right) ax += 1;
  if (ax) { if (Math.sign(p.vx) !== ax && p.onGround) p.vx *= 0.8; p.vx += ax * (p.onGround ? ACC_G : ACC_A) * dt; p.face = ax; }
  else if (p.onGround) { const f = FRIC * dt; p.vx = Math.abs(p.vx) <= f ? 0 : p.vx - Math.sign(p.vx) * f; }
  else p.vx *= 0.99;
  if (p.boostT > 0) { p.boostT -= dt; p.vx = Math.max(p.vx, 660); if (Math.random() < 0.8) burst(p.x, p.y + p.h / 2 + (Math.random() - 0.5) * 30, W.accent2, 1, 60, 0.3); }
  p.vx = clamp(p.vx, -RUN, p.boostT > 0 ? 700 : RUN);
  // disparo de luz
  p.fireCd -= dt; p.muzzle = Math.max(0, p.muzzle - dt);
  if (Input.fire && p.fireCd <= 0) {
    p.fireCd = 0.13; p.muzzle = 0.05;
    // autoapuntado: enemigo más cercano delante, dentro de un cono
    const ox = p.x + p.w / 2 + p.face * 20, oy = p.y + 24;
    let best = null, bd = 1e9;
    const consider = (tx, ty) => {
      const dx = tx - ox, dy = ty - oy; if (dx * p.face < 10) return;
      const d = Math.hypot(dx, dy); if (d > 650 || Math.abs(Math.atan2(dy, Math.abs(dx))) > 1.15) return;
      if (d < bd) { bd = d; best = [dx / d, dy / d]; }
    };
    for (const e of L.ents) if (e.alive && e.hp) consider(e.x + e.w / 2, e.y + e.h / 2);
    if (G.boss && G.boss.alive && G.bossOn) consider(G.boss.x + G.boss.w / 2, G.boss.y + G.boss.h / 2);
    const dir = best || [p.face, 0];
    fireShot(ox, oy, dir[0] * 1000, dir[1] * 1000 + (Math.random() - 0.5) * 30, true);
    Sound.sfx.shoot(); if (p.onGround) p.vx -= p.face * 20;
  }
  // salto con buffer y coyote time
  if (Input.jumpPressed || G.jq) p.jbuf = 0.13; else p.jbuf -= dt;
  G.jq = false;
  // orbes de salto (Geometry Dash)
  if (p.jbuf > 0 && !p.onGround) for (const e of L.ents) if (e.type === 'jorb' && e.used <= 0.05 && Math.hypot(e.x - (p.x + p.w / 2), e.y - (p.y + p.h / 2)) < e.r + 42) {
    p.vy = -JV * 1.08; p.jumps = 1; p.jbuf = 0; e.used = 1; p.spin = 1; Sound.sfx.orb();
    ring(e.x, e.y, '#ffe45a', 100, 0.4, 5); burst(e.x, e.y, '#ffe45a', 18, 320, 0.5); flash('#ffe45a', 0.12);
    break;
  }
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
  if (p.onGround) for (const e of L.ents) if (e.type === 'boost' && p.x + p.w > e.x && p.x < e.x + e.w && Math.abs(p.y + p.h - (e.y + e.h)) < 14) {
    if (p.boostT < 0.3) { Sound.sfx.boost(); ring(e.x + 20, e.y, W.accent2, 90, 0.4, 5); flash(W.accent2, 0.12); }
    p.boostT = 0.75; p.face = 1;
  }
  p.spin = Math.max(0, p.spin - dt * 2.4);
  p.trail.push(p.x + p.w / 2, p.y + p.h / 2); if (p.trail.length > 28) p.trail.splice(0, 2);
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
    Sound.sfx.stomp(); ring(p.x + p.w / 2, p.y + p.h, '#ffffff', 70, 0.3, 4);
    if (e.type === 'charger') damageEnemy(e, 2, 0); else killEnemy(e);
    p.vy = Input.jump ? -800 : -520; p.jumps = 1; p.onGround = false; p.spin = 1;
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
        if (!solid(above)) { edges.push(x, y, 0); if (h === 0) lights.push(x + 20, y, 60 + PULSE * 25, W.accent, 0.35 + PULSE * 0.2); }
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
  if (!L.goalLocked && L.goal.x > x0 - 100 && L.goal.x < x1 + 100) { drawPortal(ctx, L.goal, t, W); lights.push(L.goal.x, L.goal.y - 80, 260, W.accent, 1); }
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
      case 'turret': if (!e.dead || e.deadT < 0.1) { drawTurret(ctx, e, t, W); drawHitFlash(ctx, e); lights.push(e.x + 16, e.y + 8, 60 + (e.charge > 0 ? 80 : 0), W.hazard, 0.8); } break;
      case 'charger': if (!e.dead || e.deadT < 0.1) { drawCharger(ctx, e, t, W); drawHitFlash(ctx, e); lights.push(e.x + 19, e.y + 10, e.mode === 'charge' ? 130 : 60, W.hazard, 0.8); } break;
      case 'jorb': drawJOrb(ctx, e, t, W); lights.push(e.x, e.y, 110 + PULSE * 30, '#ffe45a', 1); break;
      case 'boost': drawBoost(ctx, e, t, W); lights.push(e.x + 20, e.y, 90, W.accent2, 0.8); break;
    }
    if ((e.type === 'walker' || e.type === 'floater') && e.flash > 0) drawHitFlash(ctx, e);
  }
  for (const it of G.items) { drawItem(ctx, it, t, W); lights.push(it.x + 14, it.y + 14, 80, it.kind === 'shield' ? W.accent : '#b8ff7a', 0.9); }
  // jefe
  const p = G.p;
  if (G.boss && G.bossOn) { const b = G.boss; drawBoss(ctx, b, t, W, p.x + p.w / 2, p.y + p.h / 2); if (b.alive || b.deadT < 1.6) lights.push(b.x + b.w / 2, b.y + b.h / 2, 300 + PULSE * 60, b.phase > 1 ? W.hazard : W.accent3, 1); }
  // estela del jugador
  if (showPlayer && p && !p.dead && p.trail.length > 4) {
    ctx.globalCompositeOperation = 'lighter';
    const tc = p.boostT > 0 ? W.accent2 : W.accent, n = p.trail.length / 2;
    for (let i = 0; i < n - 1; i++) {
      const k = i / n; ctx.globalAlpha = k * (p.boostT > 0 ? 0.9 : 0.45);
      const r = 4 + k * (p.boostT > 0 ? 18 : 10);
      ctx.drawImage(glowSprite(tc, true), p.trail[i * 2] - r, p.trail[i * 2 + 1] - r, r * 2, r * 2);
    }
    ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over';
  }
  // jugador
  if (showPlayer && p) {
    const blink = p.inv > 0 && !p.dead && Math.floor(p.inv * 12) % 2 === 0;
    if (!blink) {
      ctx.save();
      if (p.win) { const k = clamp(1 - G.stateT / 2.2, 0, 1); ctx.globalAlpha = k; ctx.translate(p.x + p.w / 2, p.y + p.h / 2); ctx.rotate(G.stateT * 4); ctx.scale(k, k); ctx.translate(-(p.x + p.w / 2), -(p.y + p.h / 2)); }
      if (p.dead) { ctx.translate(p.x + p.w / 2, p.y + p.h / 2); ctx.rotate(p.deadT * 5); ctx.translate(-(p.x + p.w / 2), -(p.y + p.h / 2)); }
      if (p.spin > 0 && !p.dead) { ctx.translate(p.x + p.w / 2, p.y + p.h / 2); ctx.rotate((1 - p.spin) * Math.PI * 2 * p.face); ctx.translate(-(p.x + p.w / 2), -(p.y + p.h / 2)); }
      drawAstronaut(ctx, p, t, W);
      ctx.restore();
    }
    if (p.shield && !p.dead) {
      ctx.globalCompositeOperation = 'lighter';
      ctx.strokeStyle = rgba(W.accent, 0.5 + Math.sin(t * 6) * 0.2); ctx.lineWidth = 2;
      ctx.beginPath(); ctx.ellipse(p.x + p.w / 2, p.y + p.h / 2 - 6, 26, 34, 0, 0, 7); ctx.stroke();
      ctx.globalCompositeOperation = 'source-over';
    }
    lights.push(p.x + p.w / 2, p.y + 10, (p.shield ? 260 : 210) + PULSE * 30, p.shield ? W.accent : '#dff6ff', 1);
    if (p.muzzle > 0 && !p.dead) {
      const mx = p.x + p.w / 2 + p.face * 20, my = p.y + 24;
      ctx.globalCompositeOperation = 'lighter'; ctx.drawImage(glowSprite(W.accent, true), mx - 18, my - 18, 36, 36); ctx.globalCompositeOperation = 'source-over';
      lights.push(mx, my, 120, W.accent, 1);
    }
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
  ctx.globalCompositeOperation = 'lighter'; ctx.globalAlpha = Math.min(1, 0.65 + PULSE * 0.5);
  for (let i = 0; i < edges.length; i += 3) ctx.drawImage(edges[i + 2] ? T.edge2 : T.edge, edges[i] - camX + sx, edges[i + 1] - 8 + sy, TILE, 16);
  ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over';
  // disparos y ondas de choque por encima de la oscuridad
  ctx.save(); ctx.translate(-camX + sx, sy);
  for (const s of G.shots) drawShot(ctx, s, W);
  ctx.globalCompositeOperation = 'lighter';
  for (const r of G.rings) {
    const k = r.t / r.life, e = 1 - Math.pow(1 - k, 3);
    ctx.globalAlpha = 1 - k; ctx.strokeStyle = r.c; ctx.lineWidth = r.w * (1 - k) + 1;
    ctx.beginPath(); ctx.arc(r.x, r.y, 6 + r.max * e, 0, 7); ctx.stroke();
  }
  ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over';
  ctx.restore();
  if (p && p.boostT > 0) { // líneas de velocidad
    ctx.globalCompositeOperation = 'lighter'; ctx.fillStyle = rgba(W.accent2, 0.35);
    for (let i = 0; i < 14; i++) { const y = (i * 97 + t * 900) % VIEW_H, x = (i * 331 + t * -2400) % VW; ctx.fillRect((x + VW) % VW, y, 90 + (i % 3) * 40, 2); }
    ctx.globalCompositeOperation = 'source-over';
  }
  drawAmbient(ctx, art, camX, t, VW);
  if (G.flash.a > 0) { ctx.globalCompositeOperation = 'lighter'; ctx.globalAlpha = Math.min(1, G.flash.a); ctx.fillStyle = G.flash.c; ctx.fillRect(0, 0, VW, VIEW_H); ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over'; }
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
  const b = G.boss;
  if (b && G.bossOn && (b.alive || b.deadT < 1.6)) {
    const bw = Math.min(460, VW * 0.4), bx = VW / 2 - bw / 2, c = b.phase > 1 ? W.hazard : W.accent3;
    glowText(b.name.toUpperCase(), VW / 2, 26, c, 14);
    ctx.fillStyle = 'rgba(0,0,0,0.6)'; roundRect(ctx, bx - 3, 40, bw + 6, 16, 8); ctx.fill();
    ctx.save(); ctx.shadowColor = c; ctx.shadowBlur = 14; ctx.fillStyle = c; roundRect(ctx, bx, 43, Math.max(0, bw * b.hp / b.max), 10, 5); ctx.fill(); ctx.restore();
    ctx.fillStyle = 'rgba(255,255,255,0.5)'; ctx.fillRect(bx, 44, Math.max(0, bw * b.hp / b.max), 2);
  } else {
    glowText(`${G.wi + 1}-${G.li + 1}  ·  ${W.name.toUpperCase()}`, VW / 2, 30, W.accent, 10);
    // progreso del nivel
    const pw = 180, px = VW / 2 - pw / 2, pr = p ? clamp(p.x / G.L.goal.x, 0, 1) : 0;
    ctx.fillStyle = 'rgba(255,255,255,0.12)'; ctx.fillRect(px, 48, pw, 3);
    ctx.fillStyle = W.accent; ctx.fillRect(px, 48, pw * pr, 3);
    ctx.fillStyle = '#fff'; ctx.beginPath(); ctx.arc(px + pw * pr, 49.5, 4, 0, 7); ctx.fill();
  }
  // puntuación y combo
  ctx.textAlign = 'right'; ctx.font = 'bold 18px "Trebuchet MS", sans-serif';
  glowText(String(G.score).padStart(6, '0'), VW - 82, 36, W.accent, 8);
  if (G.combo >= 2) {
    const sc = 1 + G.comboPop * 0.5 + PULSE * 0.06;
    ctx.save(); ctx.translate(VW - 90, 92); ctx.scale(sc, sc); ctx.rotate(-0.08); ctx.textAlign = 'center';
    ctx.font = 'bold 38px "Trebuchet MS", sans-serif'; glowText('x' + G.combo, 0, 0, G.combo >= 5 ? '#ffe45a' : W.accent2, 22);
    ctx.font = 'bold 13px "Trebuchet MS", sans-serif'; glowText('COMBO', 0, 26, W.accent2, 8);
    ctx.fillStyle = rgba(W.accent2, 0.8); ctx.fillRect(-36, 36, 72 * G.comboT / 2.4, 3);
    ctx.restore();
  }
  // aviso de jefe
  if (G.banner > 0) {
    const a = Math.min(1, G.banner) * (0.6 + 0.4 * Math.sin(G.t * 18));
    ctx.fillStyle = `rgba(255,30,60,${0.25 * a})`; ctx.fillRect(0, VIEW_H / 2 - 60, VW, 120);
    ctx.globalAlpha = Math.min(1, G.banner); ctx.textAlign = 'center';
    ctx.font = 'bold 22px "Trebuchet MS", sans-serif'; glowText('⚠  ¡PELIGRO!  ⚠', VW / 2, VIEW_H / 2 - 28, W.hazard, 16);
    ctx.font = 'bold 48px "Trebuchet MS", sans-serif'; glowText(b ? b.name.toUpperCase() : '', VW / 2, VIEW_H / 2 + 18, W.hazard, 28);
    ctx.globalAlpha = 1;
  }
  // pausa
  ctx.strokeStyle = 'rgba(255,255,255,0.7)'; ctx.lineWidth = 2; ctx.beginPath(); ctx.arc(VW - 44, 38, 22, 0, 7); ctx.stroke();
  ctx.fillStyle = '#fff'; ctx.fillRect(VW - 51, 29, 5, 18); ctx.fillRect(VW - 42, 29, 5, 18);
  // controles táctiles
  if (Input.touch) {
    const act = { L: false, R: false, J: false, F: false }; for (const q of Input.pointers.values()) if (q.btn) act[q.btn] = true;
    const pad = (x, y, r, on, drawIcon) => {
      ctx.fillStyle = on ? rgba(W.accent, 0.3) : 'rgba(255,255,255,0.06)'; ctx.beginPath(); ctx.arc(x, y, r, 0, 7); ctx.fill();
      ctx.strokeStyle = rgba(W.accent, on ? 0.9 : 0.35); ctx.lineWidth = 2; ctx.stroke();
      ctx.fillStyle = on ? '#ffffff' : 'rgba(255,255,255,0.6)'; drawIcon(x, y);
    };
    const tri = (x, y, d) => { ctx.beginPath(); ctx.moveTo(x + d * 16, y); ctx.lineTo(x - d * 10, y - 16); ctx.lineTo(x - d * 10, y + 16); ctx.fill(); };
    pad(90, 500, 52, act.L, (x, y) => tri(x, y, -1));
    pad(218, 500, 52, act.R, (x, y) => tri(x, y, 1));
    pad(VW - 255, 505, 50, act.F, (x, y) => { ctx.beginPath(); ctx.moveTo(x + 4, y - 20); ctx.lineTo(x - 10, y + 3); ctx.lineTo(x, y + 3); ctx.lineTo(x - 4, y + 20); ctx.lineTo(x + 10, y - 3); ctx.lineTo(x, y - 3); ctx.closePath(); ctx.fill(); });
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
  ctx.fillText(Input.touch ? '◀ ▶ moverte · ⚡ disparar · ⬆ saltar (doble salto) · toca los orbes dorados en el aire' : 'Flechas / A-D: mover · Espacio: saltar (doble salto) · X / J / clic: disparar · P: pausa', VW / 2, 570);
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
    case 'play':
      if (Input.jumpPressed) G.jq = true;
      if (G.hitstop > 0) G.hitstop -= dt;
      else { const k = G.slowT > 0 ? 0.35 : 1; G.slowT = Math.max(0, G.slowT - dt); updatePlay(dt * k); }
      break;
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
  PULSE = Math.pow(1 - Sound.beat(), 3);
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
  if (x > VW * 0.55) return x < VW - 185 ? 'F' : 'J';
  return null;
}
cv.addEventListener('pointerdown', e => {
  e.preventDefault(); Sound.unlock();
  if (e.pointerType === 'touch') Input.touch = true;
  const q = toLogical(e);
  if (G.state === 'play' && Math.hypot(q.x - (VW - 44), q.y - 38) < 36) { setState('pause'); return; }
  if (G.state === 'play' && e.pointerType !== 'touch') { Input.pointers.set(e.pointerId, { btn: 'F' }); return; }
  const btn = zoneFor(q.x, q.y);
  Input.pointers.set(e.pointerId, { btn });
  if (btn === 'J') Input.jumpPressed = true;
  Input.taps.push(q);
  try { cv.setPointerCapture(e.pointerId); } catch (err) { }
});
cv.addEventListener('pointermove', e => {
  const p = Input.pointers.get(e.pointerId); if (!p) return;
  const q = toLogical(e), z = zoneFor(q.x, q.y);
  if ((p.btn === 'L' || p.btn === 'R') && (z === 'L' || z === 'R')) p.btn = z;
  else if (p.btn === 'F' && z === 'J') { p.btn = 'J'; Input.jumpPressed = true; }
  else if (p.btn === 'J' && z === 'F') p.btn = 'F';
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
