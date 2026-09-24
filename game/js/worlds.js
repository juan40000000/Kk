'use strict';
// ---------------------------------------------------------------
// Mundos: paleta, física, ambiente y música de cada galaxia
// ---------------------------------------------------------------
// Acordes en semitonos respecto a la tónica
const C = {
  I: [0, 4, 7, 14], ii: [2, 5, 9, 12], iii: [4, 7, 11, 16], IV: [5, 9, 12, 16], iv: [5, 8, 12, 17],
  V: [7, 11, 14, 19], vi: [9, 12, 16, 19], bVI: [8, 12, 15, 19], bVII: [10, 14, 17, 22], Isus: [0, 5, 7, 14],
};
// Frases melódicas: [compás, paso, índiceDeTono, duración]
const LEAD_A = [[0, 0, 4, 6], [0, 6, 5, 2], [0, 8, 6, 8], [1, 0, 5, 4], [1, 4, 4, 4], [1, 8, 3, 8], [2, 0, 4, 6], [2, 6, 6, 2], [2, 8, 7, 8], [3, 0, 6, 8], [3, 8, 5, 8]];
const LEAD_B = [[0, 0, 6, 4], [0, 4, 5, 4], [0, 8, 4, 8], [1, 2, 4, 2], [1, 4, 5, 4], [1, 8, 6, 8], [2, 0, 7, 4], [2, 4, 6, 4], [2, 8, 5, 8], [3, 0, 4, 12]];
const LEAD_C = [[0, 0, 4, 2], [0, 3, 5, 2], [0, 6, 6, 4], [0, 12, 7, 4], [1, 0, 6, 8], [2, 0, 4, 2], [2, 3, 5, 2], [2, 6, 6, 4], [2, 12, 8, 4], [3, 0, 7, 12]];

const SONGS = {
  title: { root: 52, bpm: 72, prog: [C.I, C.vi, C.IV, C.V], barsPerChord: 1, padCut: 900, padVol: 0.05,
    arp: [0, 1, 2, 3, 4, 3, 2, 1], arpEvery: 2, arpWave: 'sine', arpVol: 0.05, arpDec: 0.9,
    bass: [0], bassLen: 12, lead: LEAD_B, leadType: 'bell', leadVol: 0.04, leadIn: 4, shimmer: true },
  boss: { root: 52, bpm: 140, prog: [C.vi, C.IV, C.I, C.V], padCut: 1800, padVol: 0.045,
    arp: [0, 3, 4, 3, 1, 3, 4, 6], arpEvery: 1, arpWave: 'sawtooth', arpVol: 0.025, arpDec: 0.2,
    bass: [0, 2, 3, 6, 8, 10, 11, 14], bassVol: 0.17, bassLen: 1.5, sub: true,
    kick: [0, 4, 8, 12], kickVol: 0.36, hat: [2, 6, 10, 14], hatVol: 0.04, snare: [4, 12], snareVol: 0.15,
    lead: LEAD_C, leadVol: 0.06, leadIn: 2 },
  ending: { root: 50, bpm: 80, prog: [C.I, C.V, C.vi, C.iii, C.IV, C.I, C.IV, C.V], padCut: 1300, padVol: 0.055,
    arp: [0, 2, 4, 6, 5, 4, 2, 1], arpEvery: 2, arpWave: 'triangle', arpVol: 0.045, arpDec: 0.8,
    bass: [0, 10], lead: LEAD_A, leadType: 'bell', leadVol: 0.05, shimmer: true, kick: [0, 8], kickVol: 0.2, drumIn: 4 },
};

const WORLDS = [
  {
    name: 'Bosque Bioluminiscente', quote: 'Incluso en la noche más oscura, la vida encuentra su propia luz.',
    sky: ['#010608', '#031a1f', '#08302f'], dark: '#000a0c', darkA: 0.42,
    accent: '#46ffd0', accent2: '#b8ff5a', accent3: '#58a8ff', ground: '#04130f', ground2: '#08231c', hazard: '#ff4fa0',
    gravity: 1, deco: 'mushroom', ambient: 'firefly', layer: 'forest', planet: { x: 0.75, y: 0.2, r: 70, c: '#b9fff0' },
    music: { root: 50, bpm: 112, prog: [C.I, C.V, C.vi, C.IV], padCut: 1100, padVol: 0.05,
      arp: [0, 1, 2, 3, 4, 5, 6, 5, 4, 3, 2, 1, 2, 3, 4, 5], arpEvery: 1, arpWave: 'triangle', arpVol: 0.035, arpDec: 0.35, arpIntroBars: 1,
      bass: [0, 3, 6, 10, 12], bassVol: 0.15, bassLen: 2, kick: [0, 4, 8, 12], kickVol: 0.3, hat: [2, 6, 10, 14], snare: [4, 12], drumIn: 2, lead: LEAD_A, leadVol: 0.05, leadIn: 4 },
  },
  {
    name: 'Cavernas de Cristal', quote: 'La presión convierte la piedra en cristal. Tú también estás hecho para brillar.',
    sky: ['#05010c', '#12062a', '#1d0b3d'], dark: '#05000c', darkA: 0.5,
    accent: '#c07bff', accent2: '#5ef0ff', accent3: '#ff7ce1', ground: '#0b0616', ground2: '#170c2a', hazard: '#5ef0ff',
    gravity: 1, deco: 'crystal', ambient: 'sparkle', layer: 'cave', planet: null,
    music: { root: 57, bpm: 118, prog: [C.vi, C.IV, C.I, C.V], padCut: 900, padVol: 0.05, padWave: 'sawtooth',
      arp: [0, 2, 4, 6, 4, 2], arpEvery: 2, arpWave: 'sine', arpVol: 0.05, arpDec: 1.1,
      bass: [0, 3, 8, 11], bassLen: 2, bassVol: 0.15, lead: LEAD_B, leadType: 'bell', leadVol: 0.05, leadIn: 4, shimmer: true, kick: [0, 4, 8, 12], kickVol: 0.3, hat: [2, 6, 10, 14, 15], snare: [4, 12], drumIn: 2 },
  },
  {
    name: 'Tundra de Aurora', quote: 'Después de la tormenta, el cielo aprende a bailar en colores.',
    sky: ['#010512', '#041029', '#0a1f3f'], dark: '#00040d', darkA: 0.45,
    accent: '#7dffb5', accent2: '#ff8ae2', accent3: '#9fd8ff', ground: '#0a1426', ground2: '#13233d', hazard: '#9fd8ff',
    gravity: 0.95, deco: 'ice', ambient: 'snow', layer: 'aurora', planet: { x: 0.2, y: 0.18, r: 46, c: '#e4f2ff' },
    music: { root: 53, bpm: 124, prog: [C.IV, C.V, C.iii, C.vi], padCut: 1400, padVol: 0.05,
      arp: [0, 1, 2, 3, 4, 3, 2, 1], arpEvery: 1, arpWave: 'sine', arpVol: 0.04, arpDec: 0.5,
      bass: [0, 3, 8, 11], bassVol: 0.14, bassLen: 2, kick: [0, 4, 8, 12], kickVol: 0.3, hat: [2, 6, 10, 14], snare: [4, 12], drumIn: 1, lead: LEAD_C, leadVol: 0.05, leadIn: 4, shimmer: true },
  },
  {
    name: 'Océano Nebular', quote: 'Respira profundo. Hasta el océano más hondo guarda estrellas.',
    sky: ['#000812', '#001a2e', '#013047'], dark: '#000611', darkA: 0.46,
    accent: '#39e6ff', accent2: '#ff9d6b', accent3: '#a98bff', ground: '#021420', ground2: '#052436', hazard: '#ff5f7a',
    gravity: 0.72, deco: 'coral', ambient: 'bubble', layer: 'ocean', planet: null,
    music: { root: 50, bpm: 108, prog: [C.I, C.iii, C.IV, C.iv], padCut: 800, padVol: 0.055, padWave: 'triangle',
      arp: [0, 2, 4, 5, 6, 5, 4, 2], arpEvery: 2, arpWave: 'sine', arpVol: 0.05, arpDec: 1.2,
      bass: [0, 6, 10], bassLen: 3, bassVol: 0.15, lead: LEAD_B, leadType: 'bell', leadVol: 0.05, leadIn: 4, shimmer: true, kick: [0, 6, 8], kickVol: 0.28, hat: [4, 12], snare: [4, 12], drumIn: 2 },
  },
  {
    name: 'Volcán Estelar', quote: 'El fuego que te pone a prueba es el mismo que te forja.',
    sky: ['#0a0102', '#240507', '#3a0d06'], dark: '#0a0100', darkA: 0.46,
    accent: '#ff8a2a', accent2: '#ffd34d', accent3: '#ff3d5e', ground: '#120605', ground2: '#1f0b08', hazard: '#ff6a00',
    gravity: 1.05, deco: 'ember', ambient: 'ember', layer: 'volcano', planet: { x: 0.68, y: 0.22, r: 90, c: '#ff9a5a' },
    music: { root: 52, bpm: 136, prog: [C.vi, C.V, C.IV, C.V], padCut: 1300, padVol: 0.05,
      arp: [0, 3, 4, 3, 1, 3, 4, 3], arpEvery: 1, arpWave: 'sawtooth', arpVol: 0.02, arpDec: 0.25,
      bass: [0, 3, 6, 8, 11, 14], bassVol: 0.16, bassLen: 2, kick: [0, 4, 8, 12], kickVol: 0.3, hat: [2, 6, 10, 14], hatVol: 0.035, snare: [4, 12], sub: true, drumIn: 1, lead: LEAD_C, leadVol: 0.055, leadIn: 4 },
  },
  {
    name: 'Jardín Cósmico', quote: 'Eres polvo de estrellas que aprendió a soñar. Sigue brillando.',
    sky: ['#040210', '#140a2e', '#2c1440'], dark: '#03010a', darkA: 0.4,
    accent: '#ffd66b', accent2: '#ff8fd6', accent3: '#7cc9ff', ground: '#120b1e', ground2: '#1f1433', hazard: '#ff5da8',
    gravity: 0.88, deco: 'flower', ambient: 'petal', layer: 'cosmic', planet: { x: 0.72, y: 0.26, r: 110, c: '#ffc2e8', ring: true },
    music: { root: 55, bpm: 128, prog: [C.IV, C.V, C.vi, C.I], padCut: 1500, padVol: 0.05,
      arp: [0, 1, 2, 3, 4, 5, 6, 7, 8, 7, 6, 5, 4, 3, 2, 1], arpEvery: 1, arpWave: 'triangle', arpVol: 0.03, arpDec: 0.4,
      bass: [0, 6, 8, 14], bassVol: 0.15, kick: [0, 4, 8, 12], kickVol: 0.3, hat: [2, 6, 10, 14], snare: [4, 12], drumIn: 1, lead: LEAD_A, leadType: 'bell', leadVol: 0.06, leadIn: 4, shimmer: true },
  },
];
const LEVELS_PER_WORLD = 2;
const BOSS_NAMES = ['Devorador de Luz', 'Coloso de Cuarzo', 'Espectro Glacial', 'Leviatán Abisal', 'Titán de Magma', 'El Eclipse'];
