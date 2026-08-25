import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { createDefaultScenario } from './defaultScenario';
import { DEFAULT_COLUMNS, type ColumnId } from '@/config/columns';
import { applyPreset, PRESETS_BY_ID, type PresetId } from '@/config/presets';
import { MARKET_BENCHMARKS, paramsForMarket } from '@/config/benchmarks';
import { CURRENCIES } from '@/config/currencies';
import { addDays, dayCount, toISO } from '@/engine/dates';
import { uid } from '@/lib/id';
import type {
  Ad,
  AdSet,
  Budget,
  Campaign,
  CurrencyCode,
  DeliveryStatus,
  Scenario,
  SimParams,
} from '@/engine/types';

export type Level = 'campaigns' | 'adsets' | 'ads';
export type ChartMetric =
  | 'spend'
  | 'results'
  | 'impressions'
  | 'linkClicks'
  | 'purchaseValue'
  | 'roas'
  | 'cpm'
  | 'ctr';

export interface UIState {
  level: Level;
  campaignFilter: string | null;
  adSetFilter: string | null;
  selected: string[];
  columns: ColumnId[];
  chartOpen: boolean;
  chartMetric: ChartMetric;
  chartCumulative: boolean;
  /** true = pantalla limpia para grabar (sin ningún control de simulación). */
  cleanMode: boolean;
  panelOpen: boolean;
  watermark: boolean;
  /** Fecha/hora "de la captura" que se muestra en la barra superior. */
  displayDateTime: string | null;
  scaleSpendOnRangeChange: boolean;
}

export interface Playback {
  playing: boolean;
  /** 0 → 1 sobre el rango de fechas. */
  progress: number;
  /** Segundos de video para todo el período (time-lapse). */
  durationSec: number;
  loop: boolean;
}

export interface SavedScenario {
  id: string;
  name: string;
  savedAt: string;
  scenario: Scenario;
}

interface AppState {
  scenario: Scenario;
  library: SavedScenario[];
  ui: UIState;
  playback: Playback;

  // --- cuenta / escenario -------------------------------------------------
  setAccountName: (name: string) => void;
  setAccountId: (id: string) => void;
  setScenarioName: (name: string) => void;
  setCurrency: (currency: CurrencyCode, retune: boolean) => void;
  setDateRange: (start: string, end: string) => void;
  setSeed: (seed: number) => void;
  reseed: () => void;
  resetScenario: () => void;

  // --- entidades ----------------------------------------------------------
  renameEntity: (level: Level, id: string, name: string) => void;
  setStatus: (level: Level, id: string, status: DeliveryStatus) => void;
  toggleStatus: (level: Level, id: string) => void;
  setBudget: (level: 'campaigns' | 'adsets', id: string, budget: Partial<Budget>) => void;
  updateParams: (adSetId: string, patch: Partial<SimParams>) => void;
  applyPresetTo: (adSetId: string, preset: PresetId) => void;
  applyPresetToAll: (preset: PresetId) => void;
  addCampaign: () => void;
  addAdSet: (campaignId: string) => void;
  addAd: (adSetId: string) => void;
  duplicate: (level: Level, id: string) => void;
  remove: (level: Level, id: string) => void;
  setAdWeight: (adId: string, weight: number) => void;
  setCreative: (adId: string, patch: Partial<Ad['creative']>) => void;
  setAdSetResultType: (adSetId: string, resultType: AdSet['resultType']) => void;

  // --- UI -----------------------------------------------------------------
  setLevel: (level: Level) => void;
  drillToCampaign: (campaignId: string) => void;
  drillToAdSet: (adSetId: string) => void;
  clearFilters: () => void;
  toggleSelected: (id: string) => void;
  selectAll: (ids: string[]) => void;
  setColumns: (columns: ColumnId[]) => void;
  patchUI: (patch: Partial<UIState>) => void;

  // --- filmación ----------------------------------------------------------
  patchPlayback: (patch: Partial<Playback>) => void;
  play: () => void;
  pause: () => void;
  restart: () => void;

  // --- biblioteca ---------------------------------------------------------
  saveToLibrary: (name?: string) => void;
  loadFromLibrary: (id: string) => void;
  deleteFromLibrary: (id: string) => void;
  importScenario: (scenario: Scenario) => void;
}

const initialUI: UIState = {
  level: 'campaigns',
  campaignFilter: null,
  adSetFilter: null,
  selected: [],
  columns: DEFAULT_COLUMNS,
  chartOpen: false,
  chartMetric: 'spend',
  chartCumulative: true,
  cleanMode: true,
  panelOpen: false,
  watermark: true,
  displayDateTime: null,
  scaleSpendOnRangeChange: true,
};

const initialPlayback: Playback = {
  playing: false,
  progress: 1,
  durationSec: 20,
  loop: false,
};

const clone = <T,>(v: T): T => JSON.parse(JSON.stringify(v));

export const useStore = create<AppState>()(
  persist(
    (set, get) => ({
      scenario: createDefaultScenario('ARS'),
      library: [],
      ui: initialUI,
      playback: initialPlayback,

      setAccountName: (name) =>
        set((s) => ({ scenario: { ...s.scenario, account: { ...s.scenario.account, name } } })),
      setAccountId: (id) =>
        set((s) => ({ scenario: { ...s.scenario, account: { ...s.scenario.account, id } } })),
      setScenarioName: (name) => set((s) => ({ scenario: { ...s.scenario, name } })),

      setCurrency: (currency, retune) =>
        set((s) => {
          const from = s.scenario.account.currency;
          const scenario = clone(s.scenario);
          scenario.account.currency = currency;
          if (retune && from !== currency) {
            // Reajusta la escala del mercado: CPM, gasto, presupuestos y ticket
            // se convierten con el ratio de benchmarks, así las impresiones,
            // clics y resultados quedan iguales y los importes siguen siendo
            // creíbles para ese país. Los overrides manuales se respetan porque
            // se multiplican, no se pisan.
            const kCpm = MARKET_BENCHMARKS[currency].cpm / MARKET_BENCHMARKS[from].cpm;
            const kAov = MARKET_BENCHMARKS[currency].aov / MARKET_BENCHMARKS[from].aov;
            const dec = CURRENCIES[currency].decimals;
            const r = (v: number) => Math.round(v * Math.pow(10, dec)) / Math.pow(10, dec);

            scenario.adSets = scenario.adSets.map((a) => ({
              ...a,
              budget: { ...a.budget, amount: r(a.budget.amount * kCpm) },
              params: {
                ...a.params,
                cpm: r(a.params.cpm * kCpm),
                spend: r(a.params.spend * kCpm),
                aov: r(a.params.aov * kAov),
              },
            }));
            scenario.campaigns = scenario.campaigns.map((c) => ({
              ...c,
              budget: { ...c.budget, amount: r(c.budget.amount * kCpm) },
            }));
          }
          return { scenario };
        }),

      setDateRange: (start, end) =>
        set((s) => {
          const oldDays = dayCount(s.scenario.dateRange.start, s.scenario.dateRange.end) || 1;
          const newDays = dayCount(start, end) || 1;
          const k = newDays / oldDays;
          const scenario = clone(s.scenario);
          scenario.dateRange = { start, end };
          if (s.ui.scaleSpendOnRangeChange && k !== 1) {
            scenario.adSets = scenario.adSets.map((a) => ({
              ...a,
              params: { ...a.params, spend: Math.round(a.params.spend * k * 100) / 100 },
            }));
          }
          return { scenario };
        }),

      setSeed: (seed) => set((s) => ({ scenario: { ...s.scenario, seed } })),
      reseed: () =>
        set((s) => ({ scenario: { ...s.scenario, seed: Math.floor(Math.random() * 1e9) } })),
      resetScenario: () =>
        set((s) => ({
          scenario: createDefaultScenario(s.scenario.account.currency),
          ui: { ...s.ui, campaignFilter: null, adSetFilter: null, level: 'campaigns', selected: [] },
        })),

      renameEntity: (level, id, name) =>
        set((s) => {
          const scenario = clone(s.scenario);
          const list =
            level === 'campaigns' ? scenario.campaigns : level === 'adsets' ? scenario.adSets : scenario.ads;
          const item = (list as { id: string; name: string }[]).find((x) => x.id === id);
          if (item) item.name = name;
          return { scenario };
        }),

      setStatus: (level, id, status) =>
        set((s) => {
          const scenario = clone(s.scenario);
          const list =
            level === 'campaigns' ? scenario.campaigns : level === 'adsets' ? scenario.adSets : scenario.ads;
          const item = (list as { id: string; status: DeliveryStatus }[]).find((x) => x.id === id);
          if (item) item.status = status;
          return { scenario };
        }),

      toggleStatus: (level, id) => {
        const s = get().scenario;
        const list = level === 'campaigns' ? s.campaigns : level === 'adsets' ? s.adSets : s.ads;
        const item = (list as { id: string; status: DeliveryStatus }[]).find((x) => x.id === id);
        if (!item) return;
        get().setStatus(level, id, item.status === 'off' ? 'active' : 'off');
      },

      setBudget: (level, id, budget) =>
        set((s) => {
          const scenario = clone(s.scenario);
          const list = level === 'campaigns' ? scenario.campaigns : scenario.adSets;
          const item = (list as { id: string; budget: Budget }[]).find((x) => x.id === id);
          if (item) item.budget = { ...item.budget, ...budget };
          return { scenario };
        }),

      updateParams: (adSetId, patch) =>
        set((s) => {
          const scenario = clone(s.scenario);
          const adSet = scenario.adSets.find((a) => a.id === adSetId);
          if (adSet) adSet.params = { ...adSet.params, ...patch };
          return { scenario };
        }),

      applyPresetTo: (adSetId, preset) =>
        set((s) => {
          const scenario = clone(s.scenario);
          const adSet = scenario.adSets.find((a) => a.id === adSetId);
          if (adSet) {
            const p = PRESETS_BY_ID[preset];
            adSet.params = applyPreset(p, adSet.params, scenario.account.currency);
            if (p.resultType) adSet.resultType = p.resultType;
          }
          return { scenario };
        }),

      applyPresetToAll: (preset) =>
        set((s) => {
          const scenario = clone(s.scenario);
          const p = PRESETS_BY_ID[preset];
          scenario.adSets = scenario.adSets.map((a) => ({
            ...a,
            params: applyPreset(p, a.params, scenario.account.currency),
            resultType: p.resultType ?? a.resultType,
          }));
          return { scenario };
        }),

      addCampaign: () =>
        set((s) => {
          const scenario = clone(s.scenario);
          const id = uid('cmp');
          scenario.campaigns.push({
            id,
            name: `Ventas | Campaña nueva ${scenario.campaigns.length + 1} | ABO`,
            status: 'active',
            objective: 'Ventas',
            budgetMode: 'adset',
            budget: { type: 'daily', amount: 5000 },
          });
          const setId = uid('set');
          const days = dayCount(scenario.dateRange.start, scenario.dateRange.end) || 30;
          scenario.adSets.push({
            id: setId,
            campaignId: id,
            name: 'Público nuevo | Amplio',
            status: 'active',
            budget: { type: 'daily', amount: 5000 },
            resultType: 'purchases',
            audience: 'Amplio',
            params: paramsForMarket(scenario.account.currency, 5000 * days),
          });
          scenario.ads.push({
            id: uid('ad'),
            adSetId: setId,
            name: 'Anuncio nuevo',
            status: 'active',
            weight: 100,
            creative: { format: 'image', headline: 'Titular', destination: 'tienda.com' },
          });
          return { scenario };
        }),

      addAdSet: (campaignId) =>
        set((s) => {
          const scenario = clone(s.scenario);
          const id = uid('set');
          const days = dayCount(scenario.dateRange.start, scenario.dateRange.end) || 30;
          scenario.adSets.push({
            id,
            campaignId,
            name: 'Conjunto de anuncios nuevo',
            status: 'active',
            budget: { type: 'daily', amount: 5000 },
            resultType: 'purchases',
            audience: 'Amplio',
            params: paramsForMarket(scenario.account.currency, 5000 * days),
          });
          scenario.ads.push({
            id: uid('ad'),
            adSetId: id,
            name: 'Anuncio nuevo',
            status: 'active',
            weight: 100,
            creative: { format: 'image', headline: 'Titular', destination: 'tienda.com' },
          });
          return { scenario };
        }),

      addAd: (adSetId) =>
        set((s) => {
          const scenario = clone(s.scenario);
          scenario.ads.push({
            id: uid('ad'),
            adSetId,
            name: 'Anuncio nuevo',
            status: 'active',
            weight: 50,
            creative: { format: 'image', headline: 'Titular', destination: 'tienda.com' },
          });
          return { scenario };
        }),

      duplicate: (level, id) =>
        set((s) => {
          const scenario = clone(s.scenario);
          if (level === 'campaigns') {
            const src = scenario.campaigns.find((c) => c.id === id);
            if (!src) return { scenario };
            const newId = uid('cmp');
            scenario.campaigns.push({ ...clone(src), id: newId, name: `${src.name} — copia` });
            for (const set of scenario.adSets.filter((a) => a.campaignId === id)) {
              const newSetId = uid('set');
              scenario.adSets.push({ ...clone(set), id: newSetId, campaignId: newId });
              for (const ad of scenario.ads.filter((a) => a.adSetId === set.id)) {
                scenario.ads.push({ ...clone(ad), id: uid('ad'), adSetId: newSetId });
              }
            }
          } else if (level === 'adsets') {
            const src = scenario.adSets.find((a) => a.id === id);
            if (!src) return { scenario };
            const newId = uid('set');
            scenario.adSets.push({ ...clone(src), id: newId, name: `${src.name} — copia` });
            for (const ad of scenario.ads.filter((a) => a.adSetId === id)) {
              scenario.ads.push({ ...clone(ad), id: uid('ad'), adSetId: newId });
            }
          } else {
            const src = scenario.ads.find((a) => a.id === id);
            if (!src) return { scenario };
            scenario.ads.push({ ...clone(src), id: uid('ad'), name: `${src.name} — copia` });
          }
          return { scenario };
        }),

      remove: (level, id) =>
        set((s) => {
          const scenario = clone(s.scenario);
          if (level === 'campaigns') {
            scenario.campaigns = scenario.campaigns.filter((c) => c.id !== id);
            const setIds = scenario.adSets.filter((a) => a.campaignId === id).map((a) => a.id);
            scenario.adSets = scenario.adSets.filter((a) => a.campaignId !== id);
            scenario.ads = scenario.ads.filter((a) => !setIds.includes(a.adSetId));
          } else if (level === 'adsets') {
            scenario.adSets = scenario.adSets.filter((a) => a.id !== id);
            scenario.ads = scenario.ads.filter((a) => a.adSetId !== id);
          } else {
            scenario.ads = scenario.ads.filter((a) => a.id !== id);
          }
          return { scenario };
        }),

      setAdWeight: (adId, weight) =>
        set((s) => {
          const scenario = clone(s.scenario);
          const ad = scenario.ads.find((a) => a.id === adId);
          if (ad) ad.weight = Math.max(0, weight);
          return { scenario };
        }),

      setCreative: (adId, patch) =>
        set((s) => {
          const scenario = clone(s.scenario);
          const ad = scenario.ads.find((a) => a.id === adId);
          if (ad) ad.creative = { ...ad.creative, ...patch };
          return { scenario };
        }),

      setAdSetResultType: (adSetId, resultType) =>
        set((s) => {
          const scenario = clone(s.scenario);
          const adSet = scenario.adSets.find((a) => a.id === adSetId);
          if (adSet) adSet.resultType = resultType;
          return { scenario };
        }),

      setLevel: (level) => set((s) => ({ ui: { ...s.ui, level, selected: [] } })),

      drillToCampaign: (campaignId) =>
        set((s) => ({
          ui: { ...s.ui, level: 'adsets', campaignFilter: campaignId, adSetFilter: null, selected: [] },
        })),

      drillToAdSet: (adSetId) =>
        set((s) => ({ ui: { ...s.ui, level: 'ads', adSetFilter: adSetId, selected: [] } })),

      clearFilters: () =>
        set((s) => ({ ui: { ...s.ui, campaignFilter: null, adSetFilter: null, selected: [] } })),

      toggleSelected: (id) =>
        set((s) => ({
          ui: {
            ...s.ui,
            selected: s.ui.selected.includes(id)
              ? s.ui.selected.filter((x) => x !== id)
              : [...s.ui.selected, id],
          },
        })),

      selectAll: (ids) =>
        set((s) => ({
          ui: { ...s.ui, selected: s.ui.selected.length === ids.length ? [] : ids },
        })),

      setColumns: (columns) => set((s) => ({ ui: { ...s.ui, columns } })),
      patchUI: (patch) => set((s) => ({ ui: { ...s.ui, ...patch } })),

      patchPlayback: (patch) => set((s) => ({ playback: { ...s.playback, ...patch } })),
      play: () =>
        set((s) => ({
          playback: {
            ...s.playback,
            playing: true,
            progress: s.playback.progress >= 1 ? 0 : s.playback.progress,
          },
        })),
      pause: () => set((s) => ({ playback: { ...s.playback, playing: false } })),
      restart: () => set((s) => ({ playback: { ...s.playback, playing: true, progress: 0 } })),

      saveToLibrary: (name) =>
        set((s) => {
          const scenarioName = name?.trim() || s.scenario.name;
          const entry: SavedScenario = {
            id: uid('scn'),
            name: scenarioName,
            savedAt: new Date().toISOString(),
            scenario: { ...clone(s.scenario), name: scenarioName },
          };
          const library = [entry, ...s.library.filter((x) => x.name !== scenarioName)];
          return { library, scenario: { ...s.scenario, name: scenarioName } };
        }),

      loadFromLibrary: (id) =>
        set((s) => {
          const entry = s.library.find((x) => x.id === id);
          if (!entry) return {};
          return {
            scenario: clone(entry.scenario),
            ui: { ...s.ui, campaignFilter: null, adSetFilter: null, level: 'campaigns', selected: [] },
            playback: { ...s.playback, playing: false, progress: 1 },
          };
        }),

      deleteFromLibrary: (id) => set((s) => ({ library: s.library.filter((x) => x.id !== id) })),

      importScenario: (scenario) =>
        set((s) => ({
          scenario: clone(scenario),
          ui: { ...s.ui, campaignFilter: null, adSetFilter: null, level: 'campaigns', selected: [] },
          playback: { ...s.playback, playing: false, progress: 1 },
        })),
    }),
    {
      name: 'meta-ads-simulator-v1',
      version: 1,
      partialize: (s) => ({
        scenario: s.scenario,
        library: s.library,
        ui: { ...s.ui, panelOpen: false, selected: [] },
        playback: { ...s.playback, playing: false, progress: 1 },
      }),
    },
  ),
);

/** Rango "últimos N días" terminando hoy. */
export function lastNDays(n: number): { start: string; end: string } {
  const end = toISO(new Date());
  return { start: addDays(end, -(n - 1)), end };
}

export type { Campaign, AdSet, Ad, Scenario };
