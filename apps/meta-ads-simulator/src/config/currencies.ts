import type { CurrencyCode } from '@/engine/types';

export interface CurrencyConfig {
  code: CurrencyCode;
  name: string;
  symbol: string;
  locale: string;
  /** Decimales para importes grandes (gasto, presupuesto, valor de conversión). */
  decimals: number;
  /** Decimales para métricas chicas (CPC, CPM, costo por resultado). */
  microDecimals: number;
  symbolFirst: boolean;
  space: boolean;
}

export const CURRENCIES: Record<CurrencyCode, CurrencyConfig> = {
  // $1.234.567,89 — punto para miles, coma para decimales.
  ARS: {
    code: 'ARS',
    name: 'Peso argentino',
    symbol: '$',
    locale: 'es-AR',
    decimals: 2,
    microDecimals: 2,
    symbolFirst: true,
    space: false,
  },
  // $1.234.568 — el peso chileno no usa decimales.
  CLP: {
    code: 'CLP',
    name: 'Peso chileno',
    symbol: '$',
    locale: 'es-CL',
    decimals: 0,
    microDecimals: 0,
    symbolFirst: true,
    space: false,
  },
  // 1.234.567,89 € — símbolo al final, como lo muestra Meta en España.
  EUR: {
    code: 'EUR',
    name: 'Euro',
    symbol: '€',
    locale: 'es-ES',
    decimals: 2,
    microDecimals: 2,
    symbolFirst: false,
    space: true,
  },
};

export const CURRENCY_ORDER: CurrencyCode[] = ['ARS', 'CLP', 'EUR'];
