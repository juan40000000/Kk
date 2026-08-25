import type { BaseMetrics, Metrics } from './types';

export const EMPTY_BASE: BaseMetrics = {
  spend: 0,
  impressions: 0,
  reach: 0,
  linkClicks: 0,
  results: 0,
  purchases: 0,
  purchaseValue: 0,
};

export function addBase(a: BaseMetrics, b: BaseMetrics): BaseMetrics {
  return {
    spend: a.spend + b.spend,
    impressions: a.impressions + b.impressions,
    reach: a.reach + b.reach,
    linkClicks: a.linkClicks + b.linkClicks,
    results: a.results + b.results,
    purchases: a.purchases + b.purchases,
    purchaseValue: a.purchaseValue + b.purchaseValue,
  };
}

export function sumBase(list: BaseMetrics[]): BaseMetrics {
  return list.reduce(addBase, EMPTY_BASE);
}

export function scaleBase(m: BaseMetrics, k: number): BaseMetrics {
  return {
    spend: m.spend * k,
    impressions: m.impressions * k,
    reach: m.reach * k,
    linkClicks: m.linkClicks * k,
    results: m.results * k,
    purchases: m.purchases * k,
    purchaseValue: m.purchaseValue * k,
  };
}

const safeDiv = (a: number, b: number): number | null => (b > 0 ? a / b : null);

/**
 * Métricas derivadas. Estas son EXACTAMENTE las fórmulas del Ads Manager:
 *
 *   CPM               = Gasto / Impresiones * 1000
 *   CPC (enlace)      = Gasto / Clics en el enlace
 *   CTR (enlace)      = Clics en el enlace / Impresiones
 *   Frecuencia        = Impresiones / Alcance
 *   Costo por result. = Gasto / Resultados          (0 resultados → "—")
 *   ROAS              = Valor de conversión / Gasto (0 gasto → "—")
 *
 * Como todo se deriva de las bases sumadas, cualquier fila agregada
 * (campaña, totales) cumple las mismas identidades sin ajustes.
 */
export function derive(base: BaseMetrics): Metrics {
  return {
    ...base,
    cpm: base.impressions > 0 ? (base.spend / base.impressions) * 1000 : null,
    cpc: safeDiv(base.spend, base.linkClicks),
    ctr: safeDiv(base.linkClicks, base.impressions),
    frequency: safeDiv(base.impressions, base.reach),
    costPerResult: safeDiv(base.spend, base.results),
    // Meta muestra "—" (no 0,00) cuando no hay ninguna compra atribuida.
    roas: base.purchaseValue > 0 ? safeDiv(base.purchaseValue, base.spend) : null,
  };
}

/** Inversa de las fórmulas: útil para tests y para el panel de control. */
export function impressionsFromSpend(spend: number, cpm: number): number {
  return cpm > 0 ? (spend / cpm) * 1000 : 0;
}
