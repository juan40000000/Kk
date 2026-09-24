'use strict';
// ---------------------------------------------------------------
// Utilidades, entrada y audio generativo
// ---------------------------------------------------------------
const TILE = 40, ROWS = 15, VIEW_H = ROWS * TILE;

function mulberry32(a) {
  return function () {
    a |= 0; a = a + 0x6D2B79F5 | 0;
    let t = Math.imul(a ^ a >>> 15, 1 | a);
    t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
    return ((t ^ t >>> 14) >>> 0) / 4294967296;
  };
}
const clamp = (v, a, b) => v < a ? a : v > b ? b : v;
const lerp = (a, b, t) => a + (b - a) * t;
function makeCanvas(w, h) { const c = document.createElement('canvas'); c.width = Math.max(1, w | 0); c.height = Math.max(1, h | 0); return c; }
function hexToRgb(h) { const n = parseInt(h.slice(1), 16); return [n >> 16 & 255, n >> 8 & 255, n & 255]; }
function rgba(h, a) { const c = hexToRgb(h); return `rgba(${c[0]},${c[1]},${c[2]},${a})`; }

// Sprites de brillo (gradiente radial) en caché
const _glowCache = {};
function glowSprite(color, hard) {
  const key = color + (hard ? 'h' : '');
  if (_glowCache[key]) return _glowCache[key];
  const s = 128, c = makeCanvas(s, s), g = c.getContext('2d');
  const gr = g.createRadialGradient(s / 2, s / 2, 0, s / 2, s / 2, s / 2);
  gr.addColorStop(0, rgba(color, 1));
  gr.addColorStop(hard ? 0.25 : 0.12, rgba(color, hard ? 0.8 : 0.55));
  gr.addColorStop(0.45, rgba(color, 0.18));
  gr.addColorStop(1, rgba(color, 0));
  g.fillStyle = gr; g.fillRect(0, 0, s, s);
  return (_glowCache[key] = c);
}

// ---------------------------------------------------------------
// Entrada: teclado + táctil multitouch
// ---------------------------------------------------------------
const Input = {
  left: false, right: false, jump: false, fire: false, jumpPressed: false, anyPressed: false,
  touch: ('ontouchstart' in window) || navigator.maxTouchPoints > 0,
  keys: {}, pointers: new Map(), taps: [],
  consume() { this.jumpPressed = false; this.anyPressed = false; this.taps.length = 0; },
};
(function () {
  const L = ['ArrowLeft', 'KeyA'], R = ['ArrowRight', 'KeyD'], J = ['Space', 'ArrowUp', 'KeyW', 'KeyZ', 'KeyK'], F = ['KeyX', 'KeyJ', 'KeyF', 'KeyC', 'ControlLeft', 'ShiftLeft'];
  addEventListener('keydown', e => {
    if (e.repeat) { if ([...L, ...R, ...J, ...F].includes(e.code)) e.preventDefault(); return; }
    Input.keys[e.code] = true; Input.anyPressed = true;
    if (J.includes(e.code)) Input.jumpPressed = true;
    if ([...L, ...R, ...J, ...F].includes(e.code)) e.preventDefault();
    Sound.unlock();
  });
  addEventListener('keyup', e => { Input.keys[e.code] = false; });
  Input.updateKeys = function () {
    const k = this.keys;
    let l = L.some(c => k[c]), r = R.some(c => k[c]), j = J.some(c => k[c]), f = F.some(c => k[c]);
    for (const p of this.pointers.values()) {
      if (p.btn === 'L') l = true; else if (p.btn === 'R') r = true; else if (p.btn === 'J') j = true; else if (p.btn === 'F') f = true;
    }
    this.left = l; this.right = r; this.jump = j; this.fire = f;
  };
})();

// ---------------------------------------------------------------
// Audio generativo: pads, arpegios, bajo, campanas y efectos
// ---------------------------------------------------------------
const Sound = (() => {
  let ctx = null, master, musicBus, sfxBus, revIn, delayIn, noiseBuf;
  let song = null, songStart = 0, step = 0, nextTime = 0, timer = null, musicOn = true, sfxOn = true;
  const mtof = m => 440 * Math.pow(2, (m - 69) / 12);

  function load() {
    try { const s = JSON.parse(localStorage.getItem('astroglow_audio') || '{}'); if (s.m === false) musicOn = false; if (s.s === false) sfxOn = false; } catch (e) { }
  }
  function save() { try { localStorage.setItem('astroglow_audio', JSON.stringify({ m: musicOn, s: sfxOn })); } catch (e) { } }
  load();

  function impulse(sec, decay) {
    const len = ctx.sampleRate * sec, b = ctx.createBuffer(2, len, ctx.sampleRate);
    for (let ch = 0; ch < 2; ch++) {
      const d = b.getChannelData(ch);
      for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, decay);
    }
    return b;
  }
  function init() {
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    ctx = new AC();
    const comp = ctx.createDynamicsCompressor();
    comp.threshold.value = -16; comp.ratio.value = 4;
    master = ctx.createGain(); master.gain.value = 0.85;
    master.connect(comp); comp.connect(ctx.destination);
    musicBus = ctx.createGain(); musicBus.gain.value = musicOn ? 0.6 : 0; musicBus.connect(master);
    sfxBus = ctx.createGain(); sfxBus.gain.value = sfxOn ? 0.55 : 0; sfxBus.connect(master);
    const rev = ctx.createConvolver(); rev.buffer = impulse(3.5, 2.6);
    revIn = ctx.createGain(); revIn.gain.value = 1; const revOut = ctx.createGain(); revOut.gain.value = 0.55;
    revIn.connect(rev); rev.connect(revOut); revOut.connect(musicBus);
    const dl = ctx.createDelay(1.5), fb = ctx.createGain(), dOut = ctx.createGain();
    delayIn = ctx.createGain(); fb.gain.value = 0.38; dOut.gain.value = 0.35;
    delayIn.connect(dl); dl.connect(fb); fb.connect(dl); dl.connect(dOut); dOut.connect(musicBus); dOut.connect(revIn);
    Sound._dl = dl;
    noiseBuf = ctx.createBuffer(1, ctx.sampleRate, ctx.sampleRate);
    const nd = noiseBuf.getChannelData(0); for (let i = 0; i < nd.length; i++) nd[i] = Math.random() * 2 - 1;
    timer = setInterval(tick, 25);
  }
  function unlock() {
    if (!ctx) init();
    if (ctx && ctx.state === 'suspended') ctx.resume();
  }

  // ---- instrumentos ----
  function env(g, t, a, peak, hold, rel) {
    g.gain.setValueAtTime(0.0001, t);
    g.gain.linearRampToValueAtTime(peak, t + a);
    g.gain.setValueAtTime(peak, t + a + hold);
    g.gain.exponentialRampToValueAtTime(0.0001, t + a + hold + rel);
  }
  function pad(notes, t, dur, s) {
    const f = ctx.createBiquadFilter(); f.type = 'lowpass'; f.Q.value = 0.7;
    const c = s.padCut || 1100;
    f.frequency.setValueAtTime(c * 0.6, t); f.frequency.linearRampToValueAtTime(c * 1.6, t + dur * 0.5); f.frequency.linearRampToValueAtTime(c * 0.8, t + dur + 1);
    const g = ctx.createGain(); env(g, t, Math.min(1.4, dur * 0.3), s.padVol || 0.05, dur - 0.6, 2.2);
    f.connect(g); g.connect(musicBus); g.connect(revIn);
    for (const n of notes) for (const d of [-9, 9]) {
      const o = ctx.createOscillator(); o.type = s.padWave || 'sawtooth'; o.frequency.value = mtof(n); o.detune.value = d;
      o.connect(f); o.start(t); o.stop(t + dur + 3);
    }
  }
  function pluck(n, t, vol, type, dec, send) {
    const o = ctx.createOscillator(), g = ctx.createGain();
    o.type = type || 'triangle'; o.frequency.value = mtof(n);
    g.gain.setValueAtTime(0.0001, t); g.gain.linearRampToValueAtTime(vol, t + 0.008); g.gain.exponentialRampToValueAtTime(0.0001, t + dec);
    o.connect(g); g.connect(musicBus); if (send !== false) { g.connect(delayIn); g.connect(revIn); }
    o.start(t); o.stop(t + dec + 0.05);
  }
  function bell(n, t, vol) {
    for (const [r, v, d] of [[1, 1, 2.2], [2, 0.35, 1.2], [3.01, 0.15, 0.7]]) {
      const o = ctx.createOscillator(), g = ctx.createGain(); o.type = 'sine'; o.frequency.value = mtof(n) * r;
      g.gain.setValueAtTime(0.0001, t); g.gain.linearRampToValueAtTime(vol * v, t + 0.01); g.gain.exponentialRampToValueAtTime(0.0001, t + d);
      o.connect(g); g.connect(musicBus); g.connect(revIn); g.connect(delayIn); o.start(t); o.stop(t + d + 0.1);
    }
  }
  function bass(n, t, dur, vol) {
    const o = ctx.createOscillator(), o2 = ctx.createOscillator(), g = ctx.createGain(), f = ctx.createBiquadFilter();
    o.type = 'sine'; o2.type = 'triangle'; o.frequency.value = o2.frequency.value = mtof(n);
    f.type = 'lowpass'; f.frequency.value = 420;
    env(g, t, 0.02, vol, dur * 0.6, dur * 0.5);
    o.connect(g); o2.connect(f); f.connect(g); g.connect(musicBus);
    o.start(t); o2.start(t); o.stop(t + dur + 0.2); o2.stop(t + dur + 0.2);
  }
  function kick(t, vol) {
    const o = ctx.createOscillator(), g = ctx.createGain();
    o.frequency.setValueAtTime(140, t); o.frequency.exponentialRampToValueAtTime(42, t + 0.18);
    g.gain.setValueAtTime(vol, t); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.35);
    o.connect(g); g.connect(musicBus); o.start(t); o.stop(t + 0.4);
  }
  function noise(t, dur, vol, freq, type, dest) {
    const s = ctx.createBufferSource(), f = ctx.createBiquadFilter(), g = ctx.createGain();
    s.buffer = noiseBuf; f.type = type || 'highpass'; f.frequency.value = freq || 7000;
    g.gain.setValueAtTime(vol, t); g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    s.connect(f); f.connect(g); g.connect(dest || musicBus); s.start(t, Math.random() * 0.5); s.stop(t + dur + 0.05);
    return f;
  }

  // ---- secuenciador ----
  function tick() {
    if (!ctx || !song || ctx.state !== 'running') return;
    const sd = 60 / song.bpm / 4;
    if (nextTime < ctx.currentTime) { nextTime = ctx.currentTime + 0.05; songStart = nextTime - step * sd; }
    while (nextTime < ctx.currentTime + 0.2) { playStep(step, nextTime, sd); nextTime += sd; step++; }
  }
  function playStep(s, t, sd) {
    const S = song, bpc = S.barsPerChord || 1, pos = s % 16, bar = Math.floor(s / 16);
    const ci = Math.floor(bar / bpc) % S.prog.length, chord = S.prog[ci], root = S.root;
    const tones = []; for (let o = 0; o < 3; o++) for (const c of chord) tones.push(root + c + o * 12);
    if (pos === 0 && bar % bpc === 0) pad(chord.map(c => root + c), t, sd * 16 * bpc, S);
    if (S.bass && S.bass.includes(pos)) bass(root + chord[0] - 24, t, sd * (S.bassLen || 3), S.bassVol || 0.16);
    if (S.arp) {
      const every = S.arpEvery || 2;
      if (pos % every === 0 && !(S.arpIntroBars && bar < S.arpIntroBars)) {
        const i = (pos / every + bar * 16 / every) % S.arp.length;
        pluck(tones[S.arp[i]] + (S.arpOct || 12), t, S.arpVol || 0.05, S.arpWave, S.arpDec || 0.5);
      }
    }
    if (S.kick && S.kick.includes(pos) && bar >= (S.drumIn || 0)) kick(t, S.kickVol || 0.35);
    if (S.hat && S.hat.includes(pos) && bar >= (S.drumIn || 0)) noise(t, 0.05, S.hatVol || 0.03);
    if (S.snare && S.snare.includes(pos) && bar >= (S.drumIn || 0)) { noise(t, 0.18, S.snareVol || 0.12, 1900, 'bandpass'); pluck(50, t, 0.06, 'triangle', 0.1, false); }
    if (S.sub && pos % 2 === 1) pluck(root + chord[0] - 12, t, 0.03, 'sawtooth', 0.12, false);
    if (S.lead) {
      const phraseBar = bar % 4;
      if (bar >= (S.leadIn || 2)) for (const [b, p, ti, len] of S.lead) if (b === phraseBar && p === pos) {
        const tn = tones[ti] + 12;
        if (S.leadType === 'bell') bell(tn, t, S.leadVol || 0.05); else pluck(tn, t, S.leadVol || 0.06, 'sine', sd * len * 1.4);
      }
    }
    if (pos === 0 && bar % 8 === 0 && S.shimmer) bell(root + chord[2] + 36, t, 0.02);
  }
  function playSong(s) {
    if (song === s) return;
    song = s; step = 0;
    if (ctx) {
      nextTime = ctx.currentTime + 0.15; songStart = nextTime;
      if (Sound._dl) Sound._dl.delayTime.setValueAtTime(60 / s.bpm * 0.75, ctx.currentTime);
    }
  }

  // ---- efectos de sonido ----
  function sweep(type, f0, f1, dur, vol, t0) {
    if (!ctx || !sfxOn) return;
    const t = ctx.currentTime + (t0 || 0), o = ctx.createOscillator(), g = ctx.createGain();
    o.type = type; o.frequency.setValueAtTime(f0, t); o.frequency.exponentialRampToValueAtTime(f1, t + dur);
    g.gain.setValueAtTime(vol, t); g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(g); g.connect(sfxBus); o.start(t); o.stop(t + dur + 0.02);
  }
  function chime(notes, gap, vol, type) {
    if (!ctx || !sfxOn) return;
    notes.forEach((n, i) => {
      const t = ctx.currentTime + i * gap, o = ctx.createOscillator(), g = ctx.createGain();
      o.type = type || 'sine'; o.frequency.value = mtof(n);
      g.gain.setValueAtTime(0.0001, t); g.gain.linearRampToValueAtTime(vol, t + 0.01); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.6);
      o.connect(g); g.connect(sfxBus); g.connect(revIn); o.start(t); o.stop(t + 0.7);
    });
  }
  const sfx = {
    jump() { sweep('square', 260, 620, 0.14, 0.05); sweep('sine', 300, 700, 0.16, 0.12); },
    djump() { sweep('sine', 400, 1100, 0.2, 0.1); if (ctx && sfxOn) noise(ctx.currentTime, 0.25, 0.12, 1200, 'bandpass', sfxBus); },
    coin() { chime([88, 95], 0.07, 0.1); },
    stomp() { sweep('sine', 220, 60, 0.18, 0.35); if (ctx && sfxOn) noise(ctx.currentTime, 0.1, 0.1, 800, 'lowpass', sfxBus); },
    bump() { sweep('square', 160, 90, 0.1, 0.08); },
    power() { chime([72, 76, 79, 84, 88, 91], 0.06, 0.09, 'triangle'); },
    life() { chime([79, 83, 86, 91, 95], 0.08, 0.1); },
    hurt() { sweep('sawtooth', 500, 120, 0.3, 0.08); },
    die() { chime([76, 72, 69, 64, 60], 0.12, 0.1, 'triangle'); },
    spring() { sweep('sine', 180, 900, 0.3, 0.2); },
    check() { chime([84, 88, 91, 96], 0.09, 0.08); },
    portal() { chime([72, 76, 79, 83, 84, 88, 91, 95, 96], 0.09, 0.08); },
    select() { chime([84, 91], 0.05, 0.07); },
    shoot() { sweep('square', 1400, 380, 0.08, 0.035); sweep('sine', 1800, 600, 0.06, 0.05); },
    hit() { sweep('square', 600, 200, 0.05, 0.05); },
    explode() { if (!ctx || !sfxOn) return; const t = ctx.currentTime; noise(t, 0.5, 0.35, 900, 'lowpass', sfxBus); sweep('sine', 160, 35, 0.45, 0.4); },
    boom() { if (!ctx || !sfxOn) return; const t = ctx.currentTime; noise(t, 1.4, 0.5, 600, 'lowpass', sfxBus); sweep('sine', 120, 25, 1.2, 0.55); chime([60, 67, 72, 79, 84], 0.1, 0.08, 'sawtooth'); },
    eshot() { sweep('triangle', 500, 240, 0.12, 0.06); },
    orb() { chime([84, 91, 96], 0.03, 0.08, 'triangle'); sweep('sine', 500, 1400, 0.18, 0.1); },
    boost() { sweep('sawtooth', 200, 1200, 0.35, 0.05); if (ctx && sfxOn) noise(ctx.currentTime, 0.4, 0.15, 2000, 'bandpass', sfxBus); },
    combo(n) { chime([72 + Math.min(n, 12) * 2, 79 + Math.min(n, 12) * 2], 0.05, 0.09, 'square'); },
    roar() { if (!ctx || !sfxOn) return; sweep('sawtooth', 90, 40, 1.4, 0.2); sweep('sawtooth', 95, 42, 1.4, 0.2); noise(ctx.currentTime, 1.2, 0.2, 400, 'lowpass', sfxBus); },
    warn() { chime([69, 69, 69], 0.25, 0.1, 'square'); },
  };

  // Fase del pulso actual (0..1) para sincronizar luces con la música
  function beat() {
    if (!ctx || !song || ctx.state !== 'running') return (performance.now() / 600) % 1;
    const spb = 60 / song.bpm, e = ctx.currentTime - songStart;
    return e < 0 ? 0 : (e / spb) % 1;
  }
  return {
    unlock, playSong, sfx, beat,
    get musicOn() { return musicOn; }, get sfxOn() { return sfxOn; },
    toggleMusic() { musicOn = !musicOn; if (musicBus) musicBus.gain.setTargetAtTime(musicOn ? 0.6 : 0, ctx.currentTime, 0.1); save(); },
    toggleSfx() { sfxOn = !sfxOn; if (sfxBus) sfxBus.gain.value = sfxOn ? 0.55 : 0; save(); },
    suspend() { if (ctx && ctx.state === 'running') ctx.suspend(); },
    resume() { if (ctx && ctx.state === 'suspended') ctx.resume(); },
  };
})();
