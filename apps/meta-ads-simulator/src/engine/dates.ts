/** Fechas en horario local, sin sorpresas de zona horaria (nada de new Date(iso)). */

export function parseISO(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, (m ?? 1) - 1, d ?? 1);
}

export function toISO(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function addDays(iso: string, days: number): string {
  const d = parseISO(iso);
  d.setDate(d.getDate() + days);
  return toISO(d);
}

export function dayCount(start: string, end: string): number {
  const a = parseISO(start).getTime();
  const b = parseISO(end).getTime();
  return Math.max(0, Math.round((b - a) / 86400000) + 1);
}

export function eachDay(start: string, end: string): string[] {
  const out: string[] = [];
  const n = dayCount(start, end);
  for (let i = 0; i < n; i++) out.push(addDays(start, i));
  return out;
}

const MONTHS_SHORT = [
  'ene', 'feb', 'mar', 'abr', 'may', 'jun',
  'jul', 'ago', 'sep', 'oct', 'nov', 'dic',
];

/** "25 ago 2026", como el selector de fechas de Meta. */
export function formatShortDate(iso: string, withYear = true): string {
  const d = parseISO(iso);
  return `${d.getDate()} ${MONTHS_SHORT[d.getMonth()]}${withYear ? ` ${d.getFullYear()}` : ''}`;
}

/** "25 ago" para los ejes del gráfico. */
export function formatAxisDate(iso: string): string {
  return formatShortDate(iso, false);
}

export function formatRange(start: string, end: string): string {
  const a = parseISO(start);
  const b = parseISO(end);
  if (start === end) return formatShortDate(start);
  if (a.getFullYear() === b.getFullYear()) {
    return `${formatShortDate(start, false)} - ${formatShortDate(end)}`;
  }
  return `${formatShortDate(start)} - ${formatShortDate(end)}`;
}
