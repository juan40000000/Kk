import type { CurrencyCode, ResultType, SimParams } from '@/engine/types';
import { MARKET_BENCHMARKS } from './benchmarks';

export type PresetId = 'winner' | 'normal' | 'disaster' | 'absurd';

export interface Preset {
  id: PresetId;
  emoji: string;
  label: string;
  description: string;
  /** Multiplicadores sobre el benchmark del mercado. */
  mult: {
    cpm: number;
    ctr: number;
    conversionRate: number;
    aov: number;
    frequency: number;
  };
  /** Gasto fijo del stunt por moneda (si no está, se respeta el gasto actual). */
  stuntSpend?: Record<CurrencyCode, number>;
  resultType?: ResultType;
  overrides?: Partial<SimParams>;
}

/**
 * ROAS resultante ≈ (1000 × CTR × conversión × AOV) / CPM.
 * Los multiplicadores están calibrados para caer en los rangos de abajo
 * usando los benchmarks de `benchmarks.ts`.
 */
export const PRESETS: Preset[] = [
  {
    id: 'winner',
    emoji: '🟢',
    label: 'Campaña ganadora',
    description: 'CTR alto, buena conversión, ROAS ~3-6x. Sirve de contraste.',
    mult: { cpm: 0.85, ctr: 1.5, conversionRate: 1.45, aov: 1.05, frequency: 1.05 },
    overrides: { noise: 0.11, learningDays: 2 },
  },
  {
    id: 'normal',
    emoji: '🟡',
    label: 'Campaña normal',
    description: 'Métricas promedio de e-commerce del mercado elegido.',
    mult: { cpm: 1, ctr: 1, conversionRate: 1, aov: 1, frequency: 1 },
    overrides: { noise: 0.14, learningDays: 3 },
  },
  {
    id: 'disaster',
    emoji: '🔴',
    label: 'Campaña desastre',
    description: 'CTR aceptable pero la conversión se cae a pedazos. CPA impagable.',
    mult: { cpm: 1.25, ctr: 1.05, conversionRate: 0.2, aov: 0.9, frequency: 1.35 },
    overrides: { noise: 0.2, learningDays: 5 },
  },
  {
    id: 'absurd',
    emoji: '🤡',
    label: 'Modo absurdo',
    description:
      'El stunt: gasto enorme, millones de impresiones y clics, CERO ventas. ' +
      'Costo por resultado y ROAS quedan en "—".',
    mult: { cpm: 0.25, ctr: 2.4, conversionRate: 0, aov: 1, frequency: 2.2 },
    stuntSpend: { ARS: 1_000_000, CLP: 900_000, EUR: 10_000 },
    resultType: 'purchases',
    overrides: { noise: 0.16, learningDays: 1, cpmVariance: 0.12, purchasesPerResult: 0 },
  },
];

export const PRESETS_BY_ID = Object.fromEntries(PRESETS.map((p) => [p.id, p])) as Record<
  PresetId,
  Preset
>;

/** Aplica un preset sobre los parámetros actuales, respetando el mercado. */
export function applyPreset(
  preset: Preset,
  current: SimParams,
  currency: CurrencyCode,
): SimParams {
  const b = MARKET_BENCHMARKS[currency];
  return {
    ...current,
    ...preset.overrides,
    spend: preset.stuntSpend ? preset.stuntSpend[currency] : current.spend,
    cpm: round(b.cpm * preset.mult.cpm, 2),
    ctr: round(b.ctr * preset.mult.ctr, 5),
    conversionRate: round(b.conversionRate * preset.mult.conversionRate, 5),
    aov: round(b.aov * preset.mult.aov, 2),
    frequency: round(b.frequency * preset.mult.frequency, 2),
  };
}

function round(v: number, d: number): number {
  const f = Math.pow(10, d);
  return Math.round(v * f) / f;
}
