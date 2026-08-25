import { buildSeries, baseAtProgress } from './series';
import { EMPTY_BASE, addBase, derive, sumBase } from './metrics';
import { apportion, createRng, jitter } from './random';
import { eachDay } from './dates';
import type {
  AdSetSeries,
  BaseMetrics,
  DayPoint,
  Metrics,
  Scenario,
} from './types';

export interface Simulation {
  dates: string[];
  seriesByAdSet: Record<string, AdSetSeries>;
  baseByAdSet: Record<string, BaseMetrics>;
  baseByCampaign: Record<string, BaseMetrics>;
  baseByAd: Record<string, BaseMetrics>;
  metricsByAdSet: Record<string, Metrics>;
  metricsByCampaign: Record<string, Metrics>;
  metricsByAd: Record<string, Metrics>;
}

/** Series diarias del escenario. Es lo caro: se memoiza aparte del progreso. */
export function buildScenarioSeries(scenario: Scenario): {
  dates: string[];
  seriesByAdSet: Record<string, AdSetSeries>;
} {
  const dates = eachDay(scenario.dateRange.start, scenario.dateRange.end);
  const seriesByAdSet: Record<string, AdSetSeries> = {};
  for (const adSet of scenario.adSets) {
    seriesByAdSet[adSet.id] = buildSeries(
      adSet,
      scenario.dateRange.start,
      scenario.dateRange.end,
      scenario.seed,
    );
  }
  return { dates, seriesByAdSet };
}

/**
 * Corta el escenario en un punto del período (`progress` 0→1) y arma
 * el árbol completo de métricas.
 *
 * Jerarquía:
 *   - Conjunto: acumulado de su serie diaria.
 *   - Anuncios: REPARTO del conjunto por peso (suman exactamente el conjunto).
 *   - Campaña: SUMA de sus conjuntos.
 * Todas las derivadas se recalculan sobre las bases sumadas.
 */
export function simulate(
  scenario: Scenario,
  progress = 1,
  prebuilt?: { dates: string[]; seriesByAdSet: Record<string, AdSetSeries> },
): Simulation {
  const { dates, seriesByAdSet } = prebuilt ?? buildScenarioSeries(scenario);

  const baseByAdSet: Record<string, BaseMetrics> = {};
  for (const adSet of scenario.adSets) {
    baseByAdSet[adSet.id] = baseAtProgress(seriesByAdSet[adSet.id], progress);
  }

  const baseByAd: Record<string, BaseMetrics> = {};
  for (const adSet of scenario.adSets) {
    const ads = scenario.ads.filter((a) => a.adSetId === adSet.id);
    if (ads.length === 0) continue;
    const parent = baseByAdSet[adSet.id];
    const w = ads.map((a) => Math.max(0, a.weight) || 0);
    const useW = w.some((x) => x > 0) ? w : ads.map(() => 1);

    // Cada creativo tiene su propio CPM, CTR, conversión y frecuencia: si todos
    // los anuncios de un conjunto mostraran el mismo CTR, cualquiera se daría
    // cuenta de que está mirando un reparto proporcional.
    const tilt = (adId: string, key: string, amount: number) =>
      jitter(createRng(scenario.seed, adId, key), amount, 0.55);

    const cpmT = ads.map((a) => tilt(a.id, 'ad-cpm', 0.16));
    const ctrT = ads.map((a) => tilt(a.id, 'ad-ctr', 0.22));
    const cvrT = ads.map((a) => tilt(a.id, 'ad-cvr', 0.25));
    const freqT = ads.map((a) => tilt(a.id, 'ad-freq', 0.12));

    const imprW = useW.map((x, i) => x / cpmT[i]);
    const clickW = imprW.map((x, i) => x * ctrT[i]);
    const resultW = clickW.map((x, i) => x * cvrT[i]);
    const reachW = imprW.map((x, i) => x / freqT[i]);

    const spend = apportion(parent.spend, useW, 2);
    const purchaseValue = apportion(parent.purchaseValue, resultW, 2);
    const impressions = apportion(parent.impressions, imprW, 0);
    const reach = apportion(parent.reach, reachW, 0);
    const linkClicks = apportion(parent.linkClicks, clickW, 0);
    const results = apportion(parent.results, resultW, 0);
    const purchases = apportion(parent.purchases, resultW, 0);

    ads.forEach((ad, i) => {
      baseByAd[ad.id] = {
        spend: spend[i],
        impressions: impressions[i],
        reach: reach[i],
        linkClicks: linkClicks[i],
        results: results[i],
        purchases: purchases[i],
        purchaseValue: purchaseValue[i],
      };
    });
  }

  const baseByCampaign: Record<string, BaseMetrics> = {};
  for (const campaign of scenario.campaigns) {
    const children = scenario.adSets
      .filter((s) => s.campaignId === campaign.id)
      .map((s) => baseByAdSet[s.id] ?? EMPTY_BASE);
    baseByCampaign[campaign.id] = sumBase(children);
  }

  const mapDerive = (src: Record<string, BaseMetrics>): Record<string, Metrics> => {
    const out: Record<string, Metrics> = {};
    for (const key of Object.keys(src)) out[key] = derive(src[key]);
    return out;
  };

  return {
    dates,
    seriesByAdSet,
    baseByAdSet,
    baseByCampaign,
    baseByAd,
    metricsByAdSet: mapDerive(baseByAdSet),
    metricsByCampaign: mapDerive(baseByCampaign),
    metricsByAd: mapDerive(baseByAd),
  };
}

const dayToBase = (d: DayPoint): BaseMetrics => ({
  spend: d.spend,
  impressions: d.impressions,
  reach: 0,
  linkClicks: d.linkClicks,
  results: d.results,
  purchases: d.purchases,
  purchaseValue: d.purchaseValue,
});

export interface DailyAggregate {
  date: string;
  daily: Metrics;
  cumulative: Metrics;
}

/** Serie diaria agregada de un subconjunto de adsets (para el gráfico). */
export function aggregateDaily(
  sim: Simulation,
  adSetIds: string[],
): DailyAggregate[] {
  const series = adSetIds.map((id) => sim.seriesByAdSet[id]).filter(Boolean);
  const n = sim.dates.length;
  const out: DailyAggregate[] = [];
  let running: BaseMetrics = { ...EMPTY_BASE };

  for (let i = 0; i < n; i++) {
    const dayBase = sumBase(series.map((s) => dayToBase(s.days[i])));
    running = addBase(running, dayBase);
    const cumReach = series.reduce((acc, s) => acc + (s.cumulative[i]?.reach ?? 0), 0);
    out.push({
      date: sim.dates[i],
      daily: derive(dayBase),
      cumulative: derive({ ...running, reach: cumReach }),
    });
  }
  return out;
}
