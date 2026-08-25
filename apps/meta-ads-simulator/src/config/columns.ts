import { int, money, percent, ratio } from '@/lib/format';
import type { CurrencyCode, Metrics, ResultType } from '@/engine/types';

export type ColumnId =
  | 'delivery'
  | 'budget'
  | 'spend'
  | 'results'
  | 'reach'
  | 'impressions'
  | 'costPerResult'
  | 'cpm'
  | 'linkClicks'
  | 'cpc'
  | 'ctr'
  | 'frequency'
  | 'purchases'
  | 'purchaseValue'
  | 'roas';

export interface ColumnDef {
  id: ColumnId;
  /** Etiqueta exacta del Ads Manager en español. */
  label: string;
  width: number;
  align: 'left' | 'right';
  /** 'special' lo pinta la tabla (entrega, presupuesto); el resto es texto. */
  kind: 'metric' | 'special';
  format?: (m: Metrics, currency: CurrencyCode) => string;
  /** Grupo del selector de columnas. */
  group: 'Rendimiento' | 'Entrega' | 'Conversiones';
}

export const RESULT_TYPE_LABEL: Record<ResultType, string> = {
  purchases: 'Compras',
  leads: 'Clientes potenciales',
  link_clicks: 'Clics en el enlace',
  messages: 'Conversaciones iniciadas',
  landing_views: 'Visitas a la página de destino',
  reach: 'Alcance',
};

export const COLUMNS: ColumnDef[] = [
  { id: 'delivery', label: 'Entrega', width: 114, align: 'left', kind: 'special', group: 'Entrega' },
  { id: 'budget', label: 'Presupuesto', width: 106, align: 'right', kind: 'special', group: 'Entrega' },
  {
    id: 'spend',
    label: 'Importe gastado',
    width: 112,
    align: 'right',
    kind: 'metric',
    group: 'Rendimiento',
    format: (m, c) => money(m.spend, c),
  },
  {
    id: 'results',
    label: 'Resultados',
    width: 100,
    align: 'right',
    kind: 'special',
    group: 'Rendimiento',
  },
  {
    id: 'reach',
    label: 'Alcance',
    width: 92,
    align: 'right',
    kind: 'metric',
    group: 'Entrega',
    format: (m, c) => int(m.reach, c, { dashOnZero: true }),
  },
  {
    id: 'impressions',
    label: 'Impresiones',
    width: 100,
    align: 'right',
    kind: 'metric',
    group: 'Entrega',
    format: (m, c) => int(m.impressions, c, { dashOnZero: true }),
  },
  {
    id: 'costPerResult',
    label: 'Costo por resultado',
    width: 106,
    align: 'right',
    kind: 'metric',
    group: 'Rendimiento',
    format: (m, c) => money(m.costPerResult, c, { micro: true }),
  },
  {
    id: 'cpm',
    label: 'CPM (costo por mil impresiones)',
    width: 106,
    align: 'right',
    kind: 'metric',
    group: 'Entrega',
    format: (m, c) => money(m.cpm, c, { micro: true }),
  },
  {
    id: 'linkClicks',
    label: 'Clics en el enlace',
    width: 100,
    align: 'right',
    kind: 'metric',
    group: 'Rendimiento',
    format: (m, c) => int(m.linkClicks, c, { dashOnZero: true }),
  },
  {
    id: 'cpc',
    label: 'CPC (costo por clic en el enlace)',
    width: 106,
    align: 'right',
    kind: 'metric',
    group: 'Rendimiento',
    format: (m, c) => money(m.cpc, c, { micro: true }),
  },
  {
    id: 'ctr',
    label: 'CTR (porcentaje de clics en el enlace)',
    width: 106,
    align: 'right',
    kind: 'metric',
    group: 'Rendimiento',
    format: (m, c) => percent(m.ctr, c),
  },
  {
    id: 'frequency',
    label: 'Frecuencia',
    width: 86,
    align: 'right',
    kind: 'metric',
    group: 'Entrega',
    format: (m, c) => ratio(m.frequency, c),
  },
  {
    id: 'purchases',
    label: 'Compras',
    width: 92,
    align: 'right',
    kind: 'metric',
    group: 'Conversiones',
    format: (m, c) => int(m.purchases, c, { dashOnZero: true }),
  },
  {
    id: 'purchaseValue',
    label: 'Valor de conversión de compras',
    width: 118,
    align: 'right',
    kind: 'metric',
    group: 'Conversiones',
    format: (m, c) => money(m.purchaseValue, c, { dashOnZero: true }),
  },
  {
    id: 'roas',
    label: 'ROAS de compras',
    width: 106,
    align: 'right',
    kind: 'metric',
    group: 'Conversiones',
    format: (m, c) => ratio(m.roas, c),
  },
];

export const COLUMNS_BY_ID = Object.fromEntries(COLUMNS.map((c) => [c.id, c])) as Record<
  ColumnId,
  ColumnDef
>;

export const DEFAULT_COLUMNS: ColumnId[] = COLUMNS.map((c) => c.id);

/** Sub-etiqueta gris de la columna Compras, igual que en Meta. */
export const PIXEL_SOURCE = 'Meta Pixel';
