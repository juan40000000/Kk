import { addDays, toISO } from '@/engine/dates';
import { applyPreset, PRESETS_BY_ID } from '@/config/presets';
import { paramsForMarket } from '@/config/benchmarks';
import type { Ad, AdSet, Campaign, CurrencyCode, Scenario, SimParams } from '@/engine/types';

export function todayISO(): string {
  return toISO(new Date());
}

function build(
  preset: keyof typeof PRESETS_BY_ID,
  currency: CurrencyCode,
  spend: number,
  patch: Partial<SimParams> = {},
): SimParams {
  const base = paramsForMarket(currency, spend);
  const applied = applyPreset(PRESETS_BY_ID[preset], base, currency);
  return { ...applied, spend, ...patch };
}

/**
 * Escenario de arranque: las cuatro "personalidades" de campaña en una sola
 * pantalla (stunt absurdo, ganadora, normal, desastre) para que la tabla se
 * vea llena y creíble apenas abrís la app.
 */
export function createDefaultScenario(currency: CurrencyCode = 'ARS'): Scenario {
  const end = todayISO();
  const start = addDays(end, -29);

  const campaigns: Campaign[] = [
    {
      id: 'cmp_stunt',
      name: 'Ventas | EXPERIMENTO — Tráfico a google.com | ABO',
      status: 'active',
      objective: 'Ventas',
      budgetMode: 'adset',
      budget: { type: 'daily', amount: 35000 },
    },
    {
      id: 'cmp_verano',
      name: 'Ventas | Catálogo Verano 2026 | Advantage+ | ABO',
      status: 'active',
      objective: 'Ventas',
      budgetMode: 'adset',
      budget: { type: 'daily', amount: 16000 },
    },
    {
      id: 'cmp_retarget',
      name: 'Ventas | Retargeting 30d — VC + ATC | ABO',
      status: 'active',
      objective: 'Ventas',
      budgetMode: 'cbo',
      budget: { type: 'daily', amount: 9000 },
    },
    {
      id: 'cmp_test',
      name: 'Ventas | Testeo creativos setiembre | ABO',
      status: 'off',
      objective: 'Ventas',
      budgetMode: 'adset',
      budget: { type: 'daily', amount: 12000 },
    },
  ];

  const adSets: AdSet[] = [
    {
      id: 'set_stunt_amplio',
      campaignId: 'cmp_stunt',
      name: 'Público amplio 18-65 | Todo el país | Ventaja+',
      status: 'active',
      budget: { type: 'lifetime', amount: 1_000_000 },
      resultType: 'purchases',
      audience: 'Argentina · 18-65 · Todos los géneros · Segmentación Advantage+',
      params: build('absurd', currency, 1_000_000),
    },
    {
      id: 'set_verano_intereses',
      campaignId: 'cmp_verano',
      name: 'Intereses | Moda + Verano | 25-45 | AMBA',
      status: 'active',
      budget: { type: 'daily', amount: 9500 },
      resultType: 'purchases',
      audience: 'AMBA · 25-45 · Intereses: moda, indumentaria, verano',
      params: build('winner', currency, 285_000),
    },
    {
      id: 'set_verano_lal',
      campaignId: 'cmp_verano',
      name: 'LAL 1% | Compradores 180d | 25-55',
      status: 'learning',
      budget: { type: 'daily', amount: 6500 },
      resultType: 'purchases',
      audience: 'Argentina · Público similar 1% compradores 180 días',
      params: build('winner', currency, 195_000, { learningDays: 6, noise: 0.16 }),
    },
    {
      id: 'set_retarget_vc',
      campaignId: 'cmp_retarget',
      name: 'RT | Visitantes 30d sin compra',
      status: 'active',
      budget: { type: 'daily', amount: 5200 },
      resultType: 'purchases',
      audience: 'Público personalizado · Visitantes del sitio 30 días',
      params: build('normal', currency, 156_000, { frequency: 3.4, cpm: 11200 }),
    },
    {
      id: 'set_retarget_atc',
      campaignId: 'cmp_retarget',
      name: 'RT | Agregaron al carrito 14d',
      status: 'active',
      budget: { type: 'daily', amount: 3800 },
      resultType: 'purchases',
      audience: 'Público personalizado · AddToCart 14 días',
      params: build('normal', currency, 114_000, {
        frequency: 4.1,
        cpm: 13400,
        conversionRate: 0.062,
      }),
    },
    {
      id: 'set_test_creativos',
      campaignId: 'cmp_test',
      name: 'Test | Creativos UGC vs. estudio',
      status: 'off',
      budget: { type: 'daily', amount: 12000 },
      resultType: 'purchases',
      audience: 'Argentina · 18-45 · Amplio',
      params: build('disaster', currency, 360_000),
    },
  ];

  const ads: Ad[] = [
    {
      id: 'ad_stunt_video',
      adSetId: 'set_stunt_amplio',
      name: 'Video 15s | "Hacé clic acá" | google.com',
      status: 'active',
      weight: 62,
      creative: { format: 'video', headline: 'Hacé clic y fijate qué pasa', destination: 'google.com' },
    },
    {
      id: 'ad_stunt_imagen',
      adSetId: 'set_stunt_amplio',
      name: 'Imagen 1:1 | Fondo azul | google.com',
      status: 'active',
      weight: 38,
      creative: { format: 'image', headline: 'No hagas clic acá', destination: 'google.com' },
    },
    {
      id: 'ad_verano_carrusel',
      adSetId: 'set_verano_intereses',
      name: 'Carrusel | Top 6 productos verano',
      status: 'active',
      weight: 55,
      creative: { format: 'carousel', headline: 'Todo verano hasta 40% OFF', destination: 'tienda.com.ar/verano' },
    },
    {
      id: 'ad_verano_ugc',
      adSetId: 'set_verano_intereses',
      name: 'Video UGC | Testimonio Caro | 22s',
      status: 'active',
      weight: 45,
      creative: { format: 'video', headline: 'Lo probé una semana y esto pasó', destination: 'tienda.com.ar/verano' },
    },
    {
      id: 'ad_lal_video',
      adSetId: 'set_verano_lal',
      name: 'Video | Unboxing | 18s',
      status: 'learning',
      weight: 70,
      creative: { format: 'video', headline: 'Llega en 48hs a todo el país', destination: 'tienda.com.ar' },
    },
    {
      id: 'ad_lal_imagen',
      adSetId: 'set_verano_lal',
      name: 'Imagen | Producto + precio',
      status: 'in_review',
      weight: 30,
      creative: { format: 'image', headline: '3 cuotas sin interés', destination: 'tienda.com.ar' },
    },
    {
      id: 'ad_rt_vc',
      adSetId: 'set_retarget_vc',
      name: 'DPA | Catálogo dinámico',
      status: 'active',
      weight: 100,
      creative: { format: 'carousel', headline: 'Seguís pensándolo?', destination: 'tienda.com.ar' },
    },
    {
      id: 'ad_rt_atc',
      adSetId: 'set_retarget_atc',
      name: 'DPA | Carrito abandonado + 10% OFF',
      status: 'active',
      weight: 100,
      creative: { format: 'carousel', headline: 'Te dejamos 10% OFF', destination: 'tienda.com.ar/carrito' },
    },
    {
      id: 'ad_test_ugc',
      adSetId: 'set_test_creativos',
      name: 'UGC | Celular | vertical',
      status: 'off',
      weight: 50,
      creative: { format: 'video', headline: 'Mirá esto', destination: 'tienda.com.ar' },
    },
    {
      id: 'ad_test_estudio',
      adSetId: 'set_test_creativos',
      name: 'Estudio | Producción propia',
      status: 'rejected',
      weight: 50,
      creative: { format: 'image', headline: 'Nueva colección', destination: 'tienda.com.ar' },
    },
  ];

  return {
    id: 'scn_default',
    name: 'Stunt google.com',
    seed: 20260825,
    account: {
      name: 'Mercado Nómade — Cuenta principal',
      id: 'act_128374619',
      currency,
    },
    dateRange: { start, end },
    campaigns,
    adSets,
    ads,
  };
}
