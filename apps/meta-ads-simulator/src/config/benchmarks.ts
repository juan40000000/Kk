/**
 * ============================================================================
 *  BENCHMARKS — TOCÁ ESTE ARCHIVO, NO EL MOTOR
 * ============================================================================
 *
 * Valores base de CPM / CTR / conversión / ticket por mercado. Son los que
 * hacen que el escenario sea creíble para alguien que abre Ads Manager todos
 * los días. Ajustalos a los números reales de TUS cuentas.
 *
 *  - cpm ................ costo por mil impresiones, en la moneda del mercado
 *  - ctr ................ clics en el enlace / impresiones (0.013 = 1,30%)
 *  - conversionRate ..... resultados / clics en el enlace (0.025 = 2,50%)
 *  - aov ................ ticket promedio de la compra
 *  - frequency .......... impresiones / alcance al final de un período de ~30d
 *
 * Referencias rápidas de e-commerce (2024/2025, tráfico frío, feed + reels):
 *   CTR de enlace sano:      0,90% – 1,80%
 *   Conversión de landing:   1,00% – 3,50%
 *   Frecuencia sana a 30d:   1,4 – 2,6
 */

import type { CurrencyCode, SimParams } from '@/engine/types';

export interface MarketBenchmark {
  label: string;
  cpm: number;
  ctr: number;
  conversionRate: number;
  aov: number;
  frequency: number;
}

export const MARKET_BENCHMARKS: Record<CurrencyCode, MarketBenchmark> = {
  // Argentina — CPMs en pesos, inflacionados respecto de USD.
  ARS: {
    label: 'Argentina',
    cpm: 7800,
    ctr: 0.0125,
    conversionRate: 0.022,
    aov: 52000,
    frequency: 1.9,
  },
  // Chile
  CLP: {
    label: 'Chile',
    cpm: 3400,
    ctr: 0.0115,
    conversionRate: 0.017,
    aov: 39900,
    frequency: 1.8,
  },
  // España
  EUR: {
    label: 'España',
    cpm: 6.4,
    ctr: 0.0105,
    conversionRate: 0.019,
    aov: 62,
    frequency: 1.7,
  },
};

/** Parámetros por defecto de un conjunto nuevo (antes de aplicar el mercado). */
export const DEFAULT_PARAMS: SimParams = {
  spend: 250000,
  cpm: 7800,
  cpmVariance: 0.15,
  ctr: 0.0125,
  frequency: 1.9,
  conversionRate: 0.022,
  aov: 52000,
  noise: 0.14,
  learningDays: 3,
  weekendFactor: 0.88,
  purchasesPerResult: 0,
};

export function paramsForMarket(currency: CurrencyCode, spend: number): SimParams {
  const b = MARKET_BENCHMARKS[currency];
  return {
    ...DEFAULT_PARAMS,
    spend,
    cpm: b.cpm,
    ctr: b.ctr,
    conversionRate: b.conversionRate,
    aov: b.aov,
    frequency: b.frequency,
  };
}
