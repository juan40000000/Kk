import { apportion, createRng, jitter } from './random';
import { EMPTY_BASE, addBase } from './metrics';
import { eachDay, parseISO } from './dates';
import type { AdSet, AdSetSeries, BaseMetrics, DayPoint } from './types';

/**
 * Curva de reparto del gasto diario. No es plana: arranca lento (fase de
 * aprendizaje), tiene un pico y mesetas, los fines de semana pesan distinto,
 * y encima lleva ruido gaussiano suave.
 */
function spendWeights(adSet: AdSet, dates: string[], rng: () => number): number[] {
  const n = dates.length;
  const p = adSet.params;
  const learn = Math.min(Math.max(0, p.learningDays), Math.max(0, n - 1));
  const peak = (n - 1) * 0.62;
  const sigma = Math.max(1.2, n * 0.18);

  return dates.map((iso, i) => {
    let w = 1;
    // Fase de aprendizaje: Meta entrega menos hasta salir del learning.
    if (learn > 0 && i < learn) w *= 0.45 + 0.55 * ((i + 1) / (learn + 1));
    // Pico de entrega a ~62% del período.
    w *= 1 + 0.28 * Math.exp(-((i - peak) ** 2) / (2 * sigma ** 2));
    // Fatiga de creativo hacia el final.
    w *= 1 - 0.06 * (n > 1 ? i / (n - 1) : 0);
    // Fin de semana.
    const dow = parseISO(iso).getDay();
    if (dow === 0 || dow === 6) w *= p.weekendFactor;
    // Ruido diario.
    w *= jitter(rng, p.noise * 0.8, 0.3);
    return Math.max(0.01, w);
  });
}

/**
 * Frecuencia acumulada al cierre del día i. Arranca cerca de 1 (cada impresión
 * es gente nueva) y converge a la frecuencia objetivo al final del período.
 */
function frequencyCurve(target: number, n: number): number[] {
  const t = Math.max(1.01, target);
  return Array.from({ length: n }, (_, i) => 1 + (t - 1) * Math.pow((i + 1) / n, 0.65));
}

/**
 * Genera la serie diaria de un conjunto de anuncios.
 *
 * Cada día se calcula con las mismas fórmulas que la tabla, con ruido propio,
 * y el gasto diario se reparte con resto mayor para que la suma dé EXACTAMENTE
 * el gasto objetivo del período.
 */
export function buildSeries(adSet: AdSet, start: string, end: string, seed: number): AdSetSeries {
  const dates = eachDay(start, end);
  const n = dates.length;
  const p = adSet.params;

  if (n === 0) {
    return { adSetId: adSet.id, days: [], cumulative: [], frequencyAt: [] };
  }

  const rngSpend = createRng(seed, adSet.id, 'spend');
  const rngCpm = createRng(seed, adSet.id, 'cpm');
  const rngCtr = createRng(seed, adSet.id, 'ctr');
  const rngCvr = createRng(seed, adSet.id, 'cvr');

  const weights = spendWeights(adSet, dates, rngSpend);
  const dailySpend = apportion(Math.max(0, p.spend), weights, 2);
  const freqCurve = frequencyCurve(p.frequency, n);

  let resultCarry = 0;
  let purchaseCarry = 0;

  const days: DayPoint[] = dates.map((date, i) => {
    const spend = dailySpend[i];

    const cpmDay = Math.max(1e-6, p.cpm * jitter(rngCpm, p.cpmVariance, 0.35));
    const impressions = cpmDay > 0 ? Math.round((spend / cpmDay) * 1000) : 0;

    const ctrDay = Math.max(0, p.ctr * jitter(rngCtr, p.noise, 0.3));
    const linkClicks = Math.min(impressions, Math.round(impressions * ctrDay));

    const cvrDay = Math.max(0, p.conversionRate * jitter(rngCvr, p.noise * 1.2, 0.2));
    resultCarry += linkClicks * cvrDay;
    const results = Math.min(linkClicks, Math.floor(resultCarry));
    resultCarry -= results;

    let purchases: number;
    if (adSet.resultType === 'purchases') {
      purchases = results;
    } else {
      purchaseCarry += results * Math.max(0, p.purchasesPerResult);
      purchases = Math.floor(purchaseCarry);
      purchaseCarry -= purchases;
    }

    // Ticket fijo: valor / compras = AOV exacto, así la cuenta cierra a ojo.
    const purchaseValue = Math.round(purchases * p.aov * 100) / 100;

    return { date, dayIndex: i, spend, impressions, linkClicks, results, purchases, purchaseValue };
  });

  const cumulative: BaseMetrics[] = [];
  let running: BaseMetrics = { ...EMPTY_BASE };
  days.forEach((d, i) => {
    running = addBase(running, {
      spend: d.spend,
      impressions: d.impressions,
      reach: 0,
      linkClicks: d.linkClicks,
      results: d.results,
      purchases: d.purchases,
      purchaseValue: d.purchaseValue,
    });
    // Alcance = impresiones acumuladas / frecuencia acumulada del período.
    cumulative.push({ ...running, reach: Math.round(running.impressions / freqCurve[i]) });
  });

  return { adSetId: adSet.id, days, cumulative, frequencyAt: freqCurve };
}

/**
 * Acumulado del conjunto en un punto arbitrario del período.
 * `progress` va de 0 a 1 sobre el rango de fechas: permite el time-lapse
 * (gasto acumulándose en vivo) con transición continua entre días.
 */
export function baseAtProgress(series: AdSetSeries, progress: number): BaseMetrics {
  const n = series.days.length;
  if (n === 0) return { ...EMPTY_BASE };

  const t = Math.max(0, Math.min(1, progress)) * n;
  const idx = Math.min(n - 1, Math.floor(t));
  const frac = Math.max(0, Math.min(1, t - idx));

  const prev = idx > 0 ? series.cumulative[idx - 1] : EMPTY_BASE;
  const day = series.days[idx];

  const impressions = prev.impressions + day.impressions * frac;
  // Frecuencia interpolada entre el cierre del día anterior y el de hoy.
  const freqPrev = idx > 0 ? series.frequencyAt[idx - 1] : 1;
  const freq = freqPrev + (series.frequencyAt[idx] - freqPrev) * frac;

  return {
    spend: prev.spend + day.spend * frac,
    impressions,
    reach: freq > 0 ? impressions / freq : 0,
    linkClicks: prev.linkClicks + day.linkClicks * frac,
    results: prev.results + day.results * frac,
    purchases: prev.purchases + day.purchases * frac,
    purchaseValue: prev.purchaseValue + day.purchaseValue * frac,
  };
}
