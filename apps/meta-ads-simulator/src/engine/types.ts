/**
 * Tipos del motor de simulación.
 *
 * Regla de oro: NADA se guarda "ya calculado". Todas las métricas derivadas
 * (CPM, CPC, CTR, frecuencia, costo por resultado, ROAS) se calculan siempre
 * a partir de las métricas base sumadas, así las cuentas cierran en cualquier
 * nivel de agregación (anuncio → conjunto → campaña → totales).
 */

export type CurrencyCode = 'ARS' | 'CLP' | 'EUR';

export type DeliveryStatus =
  | 'active'
  | 'off'
  | 'learning'
  | 'in_review'
  | 'rejected'
  | 'completed'
  | 'limited';

export type ResultType =
  | 'purchases'
  | 'leads'
  | 'link_clicks'
  | 'messages'
  | 'landing_views'
  | 'reach';

export type BudgetType = 'daily' | 'lifetime';

export interface Budget {
  type: BudgetType;
  amount: number;
}

/** Parámetros de entrada de un conjunto de anuncios. Todo lo demás se deriva. */
export interface SimParams {
  /** Gasto objetivo TOTAL del período (no diario). */
  spend: number;
  /** CPM base: costo por mil impresiones. */
  cpm: number;
  /** Variación relativa del CPM día a día (0.15 = ±15%). */
  cpmVariance: number;
  /** CTR del enlace: clics / impresiones (0.0135 = 1,35%). */
  ctr: number;
  /** Frecuencia objetivo al final del período: impresiones / alcance. */
  frequency: number;
  /** Tasa de conversión: resultados / clics en el enlace. */
  conversionRate: number;
  /** Ticket promedio, para valor de conversión y ROAS. */
  aov: number;
  /** Ruido gaussiano diario general (0.12 = ±12%). */
  noise: number;
  /** Días de fase de aprendizaje (arranque lento). */
  learningDays: number;
  /** Multiplicador de fin de semana (0.85 = sábados y domingos más flojos). */
  weekendFactor: number;
  /** Cuántas compras genera cada resultado cuando el resultado NO es "compra". */
  purchasesPerResult: number;
}

export interface Ad {
  id: string;
  adSetId: string;
  name: string;
  status: DeliveryStatus;
  /** Peso relativo dentro del conjunto (se normaliza contra sus hermanos). */
  weight: number;
  creative: {
    format: 'image' | 'video' | 'carousel';
    headline: string;
    destination: string;
  };
}

export interface AdSet {
  id: string;
  campaignId: string;
  name: string;
  status: DeliveryStatus;
  budget: Budget;
  resultType: ResultType;
  params: SimParams;
  audience: string;
}

export interface Campaign {
  id: string;
  name: string;
  status: DeliveryStatus;
  objective: string;
  /** 'cbo' = presupuesto de campaña Advantage; 'adset' = presupuesto por conjunto. */
  budgetMode: 'cbo' | 'adset';
  budget: Budget;
}

export interface DateRange {
  /** ISO 'YYYY-MM-DD' inclusive. */
  start: string;
  /** ISO 'YYYY-MM-DD' inclusive. */
  end: string;
}

export interface Scenario {
  id: string;
  name: string;
  seed: number;
  account: {
    name: string;
    id: string;
    currency: CurrencyCode;
  };
  dateRange: DateRange;
  campaigns: Campaign[];
  adSets: AdSet[];
  ads: Ad[];
}

/** Métricas base: son las únicas que se suman. */
export interface BaseMetrics {
  spend: number;
  impressions: number;
  reach: number;
  linkClicks: number;
  results: number;
  purchases: number;
  purchaseValue: number;
}

/** Métricas base + derivadas. `null` = "—" en la tabla. */
export interface Metrics extends BaseMetrics {
  cpm: number | null;
  cpc: number | null;
  ctr: number | null;
  frequency: number | null;
  costPerResult: number | null;
  roas: number | null;
}

/** Un punto diario de la serie de un conjunto de anuncios. */
export interface DayPoint {
  date: string;
  dayIndex: number;
  spend: number;
  impressions: number;
  linkClicks: number;
  results: number;
  purchases: number;
  purchaseValue: number;
}

export interface AdSetSeries {
  adSetId: string;
  days: DayPoint[];
  /** Acumulado inclusive hasta el índice i. */
  cumulative: BaseMetrics[];
  /** Frecuencia acumulada al final de cada día (crece con el período). */
  frequencyAt: number[];
}
