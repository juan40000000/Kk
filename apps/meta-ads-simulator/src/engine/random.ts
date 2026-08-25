/**
 * PRNG determinístico. Mismo escenario + misma semilla = mismos números,
 * en cada render y en cada máquina. Sin esto los números "bailarían" en cámara.
 */

export function hashString(str: string): number {
  let h = 2166136261 >>> 0;
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

/** mulberry32: rápido, determinístico y con buena distribución. */
export function mulberry32(seed: number): () => number {
  let a = seed >>> 0;
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function createRng(...parts: (string | number)[]): () => number {
  return mulberry32(hashString(parts.join('|')));
}

/** Ruido gaussiano (Box-Muller) recortado a ±2.5σ para evitar outliers absurdos. */
export function gaussian(rng: () => number): number {
  let u = 0;
  let v = 0;
  while (u === 0) u = rng();
  while (v === 0) v = rng();
  const g = Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
  return Math.max(-2.5, Math.min(2.5, g));
}

/** Multiplicador de ruido: 1 + gauss * amount, nunca por debajo de `floor`. */
export function jitter(rng: () => number, amount: number, floor = 0.25): number {
  return Math.max(floor, 1 + gaussian(rng) * amount);
}

/**
 * Reparte `total` en `weights.length` partes proporcionales a los pesos,
 * usando el método del resto mayor, de modo que la suma de las partes sea
 * EXACTAMENTE `total` (clave para que las cuentas cierren).
 */
export function apportion(total: number, weights: number[], decimals = 0): number[] {
  const factor = Math.pow(10, decimals);
  const target = Math.round(total * factor);
  const sumW = weights.reduce((a, b) => a + b, 0);
  if (sumW <= 0 || weights.length === 0) return weights.map(() => 0);

  const exact = weights.map((w) => (w / sumW) * target);
  const floors = exact.map((e) => Math.floor(e));
  let remainder = target - floors.reduce((a, b) => a + b, 0);

  const order = exact
    .map((e, i) => ({ i, frac: e - Math.floor(e) }))
    .sort((a, b) => b.frac - a.frac);

  const out = floors.slice();
  let k = 0;
  while (remainder > 0 && order.length > 0) {
    out[order[k % order.length].i] += 1;
    remainder -= 1;
    k += 1;
  }
  return out.map((v) => v / factor);
}
