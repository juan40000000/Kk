import { CURRENCIES } from '@/config/currencies';
import type { CurrencyCode } from '@/engine/types';

/** Lo que Meta muestra cuando no hay dato. */
export const DASH = '—';

function group(value: number, locale: string, decimals: number): string {
  return new Intl.NumberFormat(locale, {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(value);
}

/**
 * Importes en la moneda de la cuenta.
 * ARS/CLP → $1.234.567,89   ·   EUR → 1.234.567,89 €
 */
export function money(
  value: number | null | undefined,
  currency: CurrencyCode,
  opts: { micro?: boolean; dashOnZero?: boolean } = {},
): string {
  if (value === null || value === undefined) return DASH;
  if (opts.dashOnZero && value === 0) return DASH;
  const c = CURRENCIES[currency];
  const decimals = opts.micro ? c.microDecimals : c.decimals;
  const n = group(value, c.locale, decimals);
  const sep = c.space ? ' ' : '';
  return c.symbolFirst ? `${c.symbol}${sep}${n}` : `${n}${sep}${c.symbol}`;
}

export function int(
  value: number | null | undefined,
  currency: CurrencyCode = 'ARS',
  opts: { dashOnZero?: boolean } = {},
): string {
  if (value === null || value === undefined) return DASH;
  if (opts.dashOnZero && value === 0) return DASH;
  return group(Math.round(value), CURRENCIES[currency].locale, 0);
}

/** 0.0181 → "1,81 %" */
export function percent(
  value: number | null | undefined,
  currency: CurrencyCode = 'ARS',
  decimals = 2,
): string {
  if (value === null || value === undefined) return DASH;
  return `${group(value * 100, CURRENCIES[currency].locale, decimals)} %`;
}

/** ROAS y frecuencia: 4,55 */
export function ratio(
  value: number | null | undefined,
  currency: CurrencyCode = 'ARS',
  decimals = 2,
): string {
  if (value === null || value === undefined) return DASH;
  return group(value, CURRENCIES[currency].locale, decimals);
}

/** Ejes del gráfico: 1,2 M / 45,3 mil */
export function compact(value: number, currency: CurrencyCode = 'ARS'): string {
  const locale = CURRENCIES[currency].locale;
  const abs = Math.abs(value);
  if (abs >= 1_000_000) return `${group(value / 1_000_000, locale, 1)} M`;
  if (abs >= 10_000) return `${group(Math.round(value / 1000), locale, 0)} mil`;
  if (abs >= 1000) return group(Math.round(value), locale, 0);
  return group(value, locale, abs < 10 && abs % 1 !== 0 ? 1 : 0);
}

export function parseNumberInput(raw: string): number | null {
  const cleaned = raw.replace(/[^\d,.-]/g, '').replace(/\./g, '').replace(',', '.');
  const n = Number(cleaned);
  return Number.isFinite(n) ? n : null;
}
