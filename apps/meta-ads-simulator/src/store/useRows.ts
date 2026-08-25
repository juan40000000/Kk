import { useMemo } from 'react';
import { useStore, type Level } from './useStore';
import { buildScenarioSeries, simulate, type Simulation } from '@/engine/simulate';
import { derive, sumBase } from '@/engine/metrics';
import type { Budget, DeliveryStatus, Metrics, ResultType } from '@/engine/types';

export interface TableRow {
  id: string;
  level: Level;
  name: string;
  status: DeliveryStatus;
  metrics: Metrics;
  resultType: ResultType;
  budget: Budget | null;
  /** Nota gris debajo del presupuesto (ej. "Del presupuesto de la campaña"). */
  budgetNote?: string;
  /** Segunda línea gris debajo del nombre. */
  sub?: string;
  /** A dónde navega al hacer clic en el nombre. */
  drillTo?: { level: Level; id: string };
}

/** Series diarias + corte en el punto actual de reproducción. */
export function useSimulation(): Simulation {
  const scenario = useStore((s) => s.scenario);
  const progress = useStore((s) => s.playback.progress);
  const prebuilt = useMemo(() => buildScenarioSeries(scenario), [scenario]);
  return useMemo(() => simulate(scenario, progress, prebuilt), [scenario, progress, prebuilt]);
}

export interface RowsResult {
  rows: TableRow[];
  total: Metrics;
  /** Conjuntos incluidos en la vista actual (para el gráfico). */
  adSetIdsInScope: string[];
  totalLabel: string;
}

export function useTableRows(sim: Simulation): RowsResult {
  const scenario = useStore((s) => s.scenario);
  const ui = useStore((s) => s.ui);

  return useMemo(() => {
    const { level, campaignFilter, adSetFilter } = ui;

    const adSetsInScope = campaignFilter
      ? scenario.adSets.filter((a) => a.campaignId === campaignFilter)
      : scenario.adSets;

    let rows: TableRow[] = [];
    let scopeIds: string[] = [];
    let totalLabel = '';

    if (level === 'campaigns') {
      rows = scenario.campaigns.map((c) => {
        const sets = scenario.adSets.filter((s) => s.campaignId === c.id);
        return {
          id: c.id,
          level: 'campaigns' as const,
          name: c.name,
          status: c.status,
          metrics: sim.metricsByCampaign[c.id] ?? derive(sumBase([])),
          resultType: sets[0]?.resultType ?? 'purchases',
          budget: c.budgetMode === 'cbo' ? c.budget : null,
          budgetNote: c.budgetMode === 'cbo' ? undefined : 'Presup. del conjunto',
          sub: c.objective,
          drillTo: { level: 'adsets' as const, id: c.id },
        };
      });
      scopeIds = scenario.adSets.map((a) => a.id);
      totalLabel = `Resultados de ${rows.length} ${rows.length === 1 ? 'campaña' : 'campañas'}`;
    } else if (level === 'adsets') {
      rows = adSetsInScope.map((a) => {
        const campaign = scenario.campaigns.find((c) => c.id === a.campaignId);
        return {
          id: a.id,
          level: 'adsets' as const,
          name: a.name,
          status: a.status,
          metrics: sim.metricsByAdSet[a.id] ?? derive(sumBase([])),
          resultType: a.resultType,
          budget: campaign?.budgetMode === 'cbo' ? null : a.budget,
          budgetNote: campaign?.budgetMode === 'cbo' ? 'Presup. de campaña' : undefined,
          sub: a.audience,
          drillTo: { level: 'ads' as const, id: a.id },
        };
      });
      scopeIds = adSetsInScope.map((a) => a.id);
      totalLabel = `Resultados de ${rows.length} ${
        rows.length === 1 ? 'conjunto de anuncios' : 'conjuntos de anuncios'
      }`;
    } else {
      const ads = adSetFilter
        ? scenario.ads.filter((a) => a.adSetId === adSetFilter)
        : scenario.ads.filter((a) => adSetsInScope.some((s) => s.id === a.adSetId));
      rows = ads.map((ad) => {
        const parent = scenario.adSets.find((s) => s.id === ad.adSetId);
        return {
          id: ad.id,
          level: 'ads' as const,
          name: ad.name,
          status: ad.status,
          metrics: sim.metricsByAd[ad.id] ?? derive(sumBase([])),
          resultType: parent?.resultType ?? 'purchases',
          budget: null,
          budgetNote: 'Del conjunto',
          sub: ad.creative.destination,
        };
      });
      scopeIds = adSetFilter
        ? [adSetFilter]
        : Array.from(new Set(ads.map((a) => a.adSetId)));
      totalLabel = `Resultados de ${rows.length} ${rows.length === 1 ? 'anuncio' : 'anuncios'}`;
    }

    const total = derive(sumBase(rows.map((r) => r.metrics)));
    return { rows, total, adSetIdsInScope: scopeIds, totalLabel };
  }, [scenario, ui, sim]);
}
