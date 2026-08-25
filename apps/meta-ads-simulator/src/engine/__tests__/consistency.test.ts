import { describe, expect, it } from 'vitest';
import { createDefaultScenario } from '@/store/defaultScenario';
import { simulate, aggregateDaily } from '@/engine/simulate';
import { derive, sumBase } from '@/engine/metrics';
import { apportion } from '@/engine/random';
import { PRESETS_BY_ID, applyPreset } from '@/config/presets';
import { paramsForMarket } from '@/config/benchmarks';
import type { Metrics } from '@/engine/types';

const scenario = createDefaultScenario('ARS');
const sim = simulate(scenario, 1);

/** Las identidades del Ads Manager, chequeadas fila por fila. */
function expectIdentities(m: Metrics, label: string) {
  if (m.impressions > 0) {
    expect(m.cpm, `CPM ${label}`).toBeCloseTo((m.spend / m.impressions) * 1000, 6);
    expect(m.ctr, `CTR ${label}`).toBeCloseTo(m.linkClicks / m.impressions, 9);
  }
  if (m.linkClicks > 0) {
    expect(m.cpc, `CPC ${label}`).toBeCloseTo(m.spend / m.linkClicks, 6);
  }
  if (m.reach > 0) {
    expect(m.frequency, `Frecuencia ${label}`).toBeCloseTo(m.impressions / m.reach, 6);
  }
  if (m.results > 0) {
    expect(m.costPerResult, `Costo por resultado ${label}`).toBeCloseTo(m.spend / m.results, 6);
  } else {
    expect(m.costPerResult, `Costo por resultado ${label} debe ser "—"`).toBeNull();
  }
  if (m.spend > 0 && m.purchaseValue > 0) {
    expect(m.roas, `ROAS ${label}`).toBeCloseTo(m.purchaseValue / m.spend, 9);
  } else {
    expect(m.roas, `ROAS ${label} debe ser "—"`).toBeNull();
  }
  // Impresiones = (Gasto / CPM) * 1000, la inversa que hace cualquiera a mano.
  if (m.cpm && m.cpm > 0) {
    expect((m.spend / m.cpm) * 1000).toBeCloseTo(m.impressions, 4);
  }
  // Alcance = Impresiones / Frecuencia
  if (m.frequency && m.frequency > 0) {
    expect(m.impressions / m.frequency).toBeCloseTo(m.reach, 4);
  }
}

describe('identidades de las métricas', () => {
  it('cierran en cada anuncio', () => {
    for (const ad of scenario.ads) expectIdentities(sim.metricsByAd[ad.id], ad.name);
  });

  it('cierran en cada conjunto de anuncios', () => {
    for (const s of scenario.adSets) expectIdentities(sim.metricsByAdSet[s.id], s.name);
  });

  it('cierran en cada campaña', () => {
    for (const c of scenario.campaigns) expectIdentities(sim.metricsByCampaign[c.id], c.name);
  });

  it('cierran en la fila de totales', () => {
    const total = derive(sumBase(scenario.campaigns.map((c) => sim.baseByCampaign[c.id])));
    expectIdentities(total, 'TOTAL');
  });
});

describe('agregación jerárquica', () => {
  it('los anuncios suman exactamente su conjunto', () => {
    for (const set of scenario.adSets) {
      const ads = scenario.ads.filter((a) => a.adSetId === set.id);
      if (ads.length === 0) continue;
      const sum = sumBase(ads.map((a) => sim.baseByAd[a.id]));
      const parent = sim.baseByAdSet[set.id];
      expect(sum.spend).toBeCloseTo(Math.round(parent.spend * 100) / 100, 2);
      expect(sum.impressions).toBe(Math.round(parent.impressions));
      expect(sum.linkClicks).toBe(Math.round(parent.linkClicks));
      expect(sum.results).toBe(Math.round(parent.results));
      expect(sum.purchaseValue).toBeCloseTo(Math.round(parent.purchaseValue * 100) / 100, 2);
    }
  });

  it('los conjuntos suman exactamente su campaña', () => {
    for (const c of scenario.campaigns) {
      const sets = scenario.adSets.filter((s) => s.campaignId === c.id);
      const sum = sumBase(sets.map((s) => sim.baseByAdSet[s.id]));
      expect(sum.spend).toBeCloseTo(sim.baseByCampaign[c.id].spend, 6);
      expect(sum.impressions).toBeCloseTo(sim.baseByCampaign[c.id].impressions, 6);
    }
  });

  it('la serie diaria suma exactamente el total del período', () => {
    for (const set of scenario.adSets) {
      const series = sim.seriesByAdSet[set.id];
      const totalSpend = series.days.reduce((a, d) => a + d.spend, 0);
      expect(totalSpend).toBeCloseTo(set.params.spend, 2);
      expect(totalSpend).toBeCloseTo(sim.baseByAdSet[set.id].spend, 2);

      const totalImpr = series.days.reduce((a, d) => a + d.impressions, 0);
      expect(totalImpr).toBe(sim.baseByAdSet[set.id].impressions);
    }
  });

  it('el gráfico acumulado termina igual que la tabla', () => {
    const ids = scenario.adSets.map((s) => s.id);
    const daily = aggregateDaily(sim, ids);
    const last = daily[daily.length - 1];
    const total = sumBase(ids.map((id) => sim.baseByAdSet[id]));
    expect(last.cumulative.spend).toBeCloseTo(total.spend, 2);
    expect(last.cumulative.impressions).toBe(total.impressions);
    // el alcance se redondea por conjunto, así que se admite ±1 por conjunto
    expect(Math.abs(last.cumulative.reach - total.reach)).toBeLessThanOrEqual(scenario.adSets.length);
  });
});

describe('modo absurdo', () => {
  const absurdo = scenario.adSets.find((s) => s.id === 'set_stunt_amplio')!;
  const m = sim.metricsByAdSet[absurdo.id];

  it('gasta el millón entero', () => {
    expect(m.spend).toBeCloseTo(1_000_000, 2);
  });

  it('genera cientos de miles de impresiones y miles de clics', () => {
    expect(m.impressions).toBeGreaterThan(300_000);
    expect(m.linkClicks).toBeGreaterThan(8_000);
  });

  it('no genera ni una sola venta', () => {
    expect(m.results).toBe(0);
    expect(m.purchases).toBe(0);
    expect(m.purchaseValue).toBe(0);
  });

  it('deja costo por resultado y ROAS en "—"', () => {
    expect(m.costPerResult).toBeNull();
    expect(m.roas).toBeNull();
  });
});

describe('presets', () => {
  const roasOf = (p: ReturnType<typeof paramsForMarket>) =>
    (1000 * p.ctr * p.conversionRate * p.aov) / p.cpm;

  it('la ganadora cae en ROAS 3-6x', () => {
    const p = applyPreset(PRESETS_BY_ID.winner, paramsForMarket('ARS', 100), 'ARS');
    expect(roasOf(p)).toBeGreaterThan(3);
    expect(roasOf(p)).toBeLessThan(6);
  });

  it('la normal es rentable pero sin milagros', () => {
    const p = applyPreset(PRESETS_BY_ID.normal, paramsForMarket('ARS', 100), 'ARS');
    expect(roasOf(p)).toBeGreaterThan(1);
    expect(roasOf(p)).toBeLessThan(3);
  });

  it('la desastre no llega ni a 1x', () => {
    const p = applyPreset(PRESETS_BY_ID.disaster, paramsForMarket('ARS', 100), 'ARS');
    expect(roasOf(p)).toBeLessThan(0.5);
  });

  it('funcionan en las tres monedas', () => {
    for (const cur of ['ARS', 'CLP', 'EUR'] as const) {
      const p = applyPreset(PRESETS_BY_ID.normal, paramsForMarket(cur, 100), cur);
      expect(roasOf(p)).toBeGreaterThan(1);
      expect(roasOf(p)).toBeLessThan(3);
    }
  });
});

describe('reparto por resto mayor', () => {
  it('la suma de las partes es exactamente el total', () => {
    expect(apportion(100, [1, 1, 1], 0).reduce((a, b) => a + b, 0)).toBe(100);
    expect(apportion(1234.56, [3, 5, 11, 2], 2).reduce((a, b) => a + b, 0)).toBeCloseTo(1234.56, 2);
    expect(apportion(7, [0, 0, 0], 0)).toEqual([0, 0, 0]);
  });
});

describe('determinismo', () => {
  it('la misma semilla produce exactamente los mismos números', () => {
    const a = simulate(createDefaultScenario('ARS'), 1);
    const b = simulate(createDefaultScenario('ARS'), 1);
    expect(a.baseByAdSet).toEqual(b.baseByAdSet);
  });

  it('el acumulado a mitad de período es menor que el total', () => {
    const mid = simulate(scenario, 0.5);
    for (const s of scenario.adSets) {
      expect(mid.baseByAdSet[s.id].spend).toBeLessThan(sim.baseByAdSet[s.id].spend);
      expect(mid.baseByAdSet[s.id].spend).toBeGreaterThan(0);
    }
  });
});
