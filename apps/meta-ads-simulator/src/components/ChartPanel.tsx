import { useMemo, useState } from 'react';
import { useStore, type ChartMetric } from '@/store/useStore';
import { aggregateDaily, type Simulation } from '@/engine/simulate';
import { formatAxisDate, formatShortDate } from '@/engine/dates';
import { compact, int, money, percent, ratio } from '@/lib/format';
import { useElementWidth } from '@/lib/useElementWidth';
import { cn } from '@/lib/cn';
import { IconX } from './icons';
import type { CurrencyCode, Metrics } from '@/engine/types';

interface MetricDef {
  id: ChartMetric;
  label: string;
  get: (m: Metrics) => number | null;
  format: (v: number | null, c: CurrencyCode) => string;
}

const METRICS: MetricDef[] = [
  { id: 'spend', label: 'Importe gastado', get: (m) => m.spend, format: (v, c) => money(v, c) },
  { id: 'results', label: 'Resultados', get: (m) => m.results, format: (v, c) => int(v, c) },
  { id: 'impressions', label: 'Impresiones', get: (m) => m.impressions, format: (v, c) => int(v, c) },
  { id: 'linkClicks', label: 'Clics en el enlace', get: (m) => m.linkClicks, format: (v, c) => int(v, c) },
  {
    id: 'purchaseValue',
    label: 'Valor de conversión',
    get: (m) => m.purchaseValue,
    format: (v, c) => money(v, c),
  },
  { id: 'roas', label: 'ROAS de compras', get: (m) => m.roas, format: (v, c) => ratio(v, c) },
  { id: 'cpm', label: 'CPM', get: (m) => m.cpm, format: (v, c) => money(v, c, { micro: true }) },
  { id: 'ctr', label: 'CTR del enlace', get: (m) => m.ctr, format: (v, c) => percent(v, c) },
];

const PAD = { top: 14, right: 16, bottom: 24, left: 62 };
const HEIGHT = 208;

export function ChartPanel({ sim, adSetIds }: { sim: Simulation; adSetIds: string[] }) {
  const ui = useStore((s) => s.ui);
  const patchUI = useStore((s) => s.patchUI);
  const currency = useStore((s) => s.scenario.account.currency);
  const progress = useStore((s) => s.playback.progress);
  const { ref, width } = useElementWidth<HTMLDivElement>();
  const [hover, setHover] = useState<number | null>(null);

  const series = useMemo(() => aggregateDaily(sim, adSetIds), [sim, adSetIds]);
  const def = METRICS.find((m) => m.id === ui.chartMetric) ?? METRICS[0];

  const values = series.map((p) =>
    def.get(ui.chartCumulative ? p.cumulative : p.daily) ?? 0,
  );
  const n = values.length;
  const max = Math.max(1, ...values);
  const niceMax = niceCeil(max);

  // Recorte por reproducción: la línea se dibuja sola durante el time-lapse.
  const t = progress * n;
  const full = Math.min(n, Math.floor(t));
  const frac = t - full;
  const drawn: number[] = values.slice(0, full);
  if (frac > 0 && full < n) {
    const prev = full > 0 ? values[full - 1] : 0;
    drawn.push(ui.chartCumulative ? prev + (values[full] - prev) * frac : values[full] * frac);
  }

  const innerW = Math.max(10, width - PAD.left - PAD.right);
  const innerH = HEIGHT - PAD.top - PAD.bottom;
  const x = (i: number) => PAD.left + (n <= 1 ? innerW / 2 : (i / (n - 1)) * innerW);
  const y = (v: number) => PAD.top + innerH - (v / niceMax) * innerH;

  const linePath = drawn.map((v, i) => `${i === 0 ? 'M' : 'L'}${x(i)},${y(v)}`).join(' ');
  const areaPath =
    drawn.length > 1
      ? `${linePath} L${x(drawn.length - 1)},${PAD.top + innerH} L${x(0)},${PAD.top + innerH} Z`
      : '';

  const ticks = 5;
  const yTicks = Array.from({ length: ticks + 1 }, (_, i) => (niceMax / ticks) * i);
  const labelEvery = Math.max(1, Math.ceil(n / 8));
  const hoverIdx = hover !== null && hover < drawn.length ? hover : null;

  return (
    <div className="shrink-0 border-b border-meta-border bg-white px-3 pb-2 pt-2.5">
      <div className="mb-2 flex items-center gap-1.5 overflow-x-auto">
        {METRICS.map((m) => {
          const active = m.id === ui.chartMetric;
          const totalValue = def.get(series[n - 1]?.cumulative ?? ({} as Metrics));
          return (
            <button
              key={m.id}
              type="button"
              onClick={() => patchUI({ chartMetric: m.id })}
              className={cn(
                'shrink-0 rounded-lg border px-3 py-1.5 text-left transition-colors',
                active
                  ? 'border-meta-blue bg-meta-blueHover'
                  : 'border-meta-borderLight hover:bg-meta-hover',
              )}
            >
              <div
                className={cn(
                  'text-2xs font-semibold',
                  active ? 'text-meta-blueLink' : 'text-meta-secondary',
                )}
              >
                {m.label}
              </div>
              <div className="num text-[13px] font-semibold leading-[16px]">
                {active
                  ? def.format(totalValue, currency)
                  : m.format(m.get(series[n - 1]?.cumulative ?? ({} as Metrics)), currency)}
              </div>
            </button>
          );
        })}

        <div className="ml-auto flex shrink-0 items-center gap-1.5 pl-2">
          <div className="flex rounded-md border border-meta-border p-[2px]">
            {(['Acumulado', 'Diario'] as const).map((label, i) => {
              const isCum = i === 0;
              const active = ui.chartCumulative === isCum;
              return (
                <button
                  key={label}
                  type="button"
                  onClick={() => patchUI({ chartCumulative: isCum })}
                  className={cn(
                    'rounded px-2.5 py-[3px] text-2xs font-semibold transition-colors',
                    active ? 'bg-meta-blueHover text-meta-blueLink' : 'text-meta-secondary hover:bg-meta-hover',
                  )}
                >
                  {label}
                </button>
              );
            })}
          </div>
          <button
            type="button"
            title="Cerrar gráficos"
            onClick={() => patchUI({ chartOpen: false })}
            className="rounded-full p-1.5 text-meta-secondary hover:bg-meta-hover"
          >
            <IconX />
          </button>
        </div>
      </div>

      <div ref={ref} className="relative">
        <svg width={width || 600} height={HEIGHT} className="block select-none">
          <defs>
            <linearGradient id="chartFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#0866FF" stopOpacity="0.20" />
              <stop offset="100%" stopColor="#0866FF" stopOpacity="0.02" />
            </linearGradient>
          </defs>

          {yTicks.map((v, i) => (
            <g key={i}>
              <line
                x1={PAD.left}
                x2={PAD.left + innerW}
                y1={y(v)}
                y2={y(v)}
                stroke={i === 0 ? '#DADDE1' : '#EBEDF0'}
                strokeWidth="1"
              />
              <text
                x={PAD.left - 8}
                y={y(v) + 4}
                textAnchor="end"
                className="num"
                fontSize="10"
                fill="#8A8D91"
              >
                {def.id === 'ctr' ? percent(v, currency, 1) : compact(v, currency)}
              </text>
            </g>
          ))}

          {areaPath && <path d={areaPath} fill="url(#chartFill)" />}
          {linePath && (
            <path
              d={linePath}
              fill="none"
              stroke="#0866FF"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          )}

          {drawn.length > 0 && (
            <circle cx={x(drawn.length - 1)} cy={y(drawn[drawn.length - 1])} r="3.5" fill="#0866FF" />
          )}

          {series.map((p, i) =>
            i % labelEvery === 0 || i === n - 1 ? (
              <text
                key={p.date}
                x={x(i)}
                y={HEIGHT - 7}
                textAnchor={i === 0 ? 'start' : i === n - 1 ? 'end' : 'middle'}
                fontSize="10"
                fill="#8A8D91"
              >
                {formatAxisDate(p.date)}
              </text>
            ) : null,
          )}

          {hoverIdx !== null && (
            <>
              <line
                x1={x(hoverIdx)}
                x2={x(hoverIdx)}
                y1={PAD.top}
                y2={PAD.top + innerH}
                stroke="#8A8D91"
                strokeWidth="1"
                strokeDasharray="3 3"
              />
              <circle cx={x(hoverIdx)} cy={y(drawn[hoverIdx])} r="4" fill="#0866FF" stroke="#fff" strokeWidth="1.5" />
            </>
          )}

          <rect
            x={PAD.left}
            y={PAD.top}
            width={innerW}
            height={innerH}
            fill="transparent"
            onMouseLeave={() => setHover(null)}
            onMouseMove={(e) => {
              const rect = (e.target as SVGRectElement).getBoundingClientRect();
              const rel = (e.clientX - rect.left) / Math.max(1, rect.width);
              setHover(Math.max(0, Math.min(n - 1, Math.round(rel * (n - 1)))));
            }}
          />
        </svg>

        {hoverIdx !== null && (
          <div
            className="pointer-events-none absolute z-10 rounded-md border border-meta-borderLight bg-white px-2.5 py-1.5 shadow-[0_4px_14px_rgba(0,0,0,.18)]"
            style={{
              left: Math.min(Math.max(0, x(hoverIdx) - 70), Math.max(0, width - 150)),
              top: Math.max(0, y(drawn[hoverIdx]) - 54),
            }}
          >
            <div className="text-2xs text-meta-secondary">{formatShortDate(series[hoverIdx].date)}</div>
            <div className="num text-xs font-semibold">{def.format(drawn[hoverIdx], currency)}</div>
          </div>
        )}
      </div>
    </div>
  );
}

/** Redondea el techo del eje Y a un número "lindo" (1, 2, 2.5, 5 × 10^k). */
function niceCeil(v: number): number {
  if (v <= 0) return 1;
  const exp = Math.floor(Math.log10(v));
  const base = Math.pow(10, exp);
  const f = v / base;
  const nice = f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10;
  return nice * base;
}
