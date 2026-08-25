import { useEffect, useMemo, useState } from 'react';
import { useStore } from '@/store/useStore';
import { PRESETS, type PresetId } from '@/config/presets';
import { MARKET_BENCHMARKS } from '@/config/benchmarks';
import { CURRENCIES, CURRENCY_ORDER } from '@/config/currencies';
import { RESULT_TYPE_LABEL } from '@/config/columns';
import { dayCount, formatRange } from '@/engine/dates';
import { int, money, percent, ratio } from '@/lib/format';
import { downloadFile, pickFile } from '@/lib/download';
import { cn } from '@/lib/cn';
import { IconExport, IconImport, IconPause, IconPlay, IconRestart, IconTrash, IconX } from './icons';
import { Toggle } from './primitives';
import type { ResultType, Scenario, SimParams } from '@/engine/types';

/* ------------------------------------------------------------- controles */

function Section({ title, hint, children }: { title: string; hint?: string; children: React.ReactNode }) {
  return (
    <section className="border-b border-meta-borderLight px-4 py-3.5">
      <h3 className="mb-2.5 text-xs font-bold uppercase tracking-wide text-meta-text">{title}</h3>
      {hint && <p className="-mt-1.5 mb-2.5 text-2xs leading-[15px] text-meta-secondary">{hint}</p>}
      {children}
    </section>
  );
}

function Field({
  label,
  value,
  onChange,
  step = 1,
  suffix,
  min = 0,
}: {
  label: string;
  value: number;
  onChange: (v: number) => void;
  step?: number;
  suffix?: string;
  min?: number;
}) {
  const [draft, setDraft] = useState<string | null>(null);
  return (
    <label className="block">
      <span className="panel-label">{label}</span>
      <div className="relative">
        <input
          type="number"
          step={step}
          min={min}
          value={draft ?? value}
          onChange={(e) => setDraft(e.target.value)}
          onBlur={() => {
            if (draft !== null) {
              const n = Number(draft);
              if (Number.isFinite(n)) onChange(n);
              setDraft(null);
            }
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter') (e.target as HTMLInputElement).blur();
          }}
          className={cn('panel-input num', suffix && 'pr-8')}
        />
        {suffix && (
          <span className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 text-2xs text-meta-secondary">
            {suffix}
          </span>
        )}
      </div>
    </label>
  );
}

function Switch({ label, on, onChange, hint }: { label: string; on: boolean; onChange: () => void; hint?: string }) {
  return (
    <div className="flex items-start justify-between gap-3 py-1.5">
      <div className="min-w-0">
        <div className="text-xs font-medium">{label}</div>
        {hint && <div className="text-2xs leading-[15px] text-meta-secondary">{hint}</div>}
      </div>
      <div className="pt-0.5">
        <Toggle on={on} onChange={onChange} />
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ panel */

export function ControlPanel() {
  const ui = useStore((s) => s.ui);
  const scenario = useStore((s) => s.scenario);
  const playback = useStore((s) => s.playback);
  const patchUI = useStore((s) => s.patchUI);
  const patchPlayback = useStore((s) => s.patchPlayback);
  const play = useStore((s) => s.play);
  const pause = useStore((s) => s.pause);
  const restart = useStore((s) => s.restart);
  const updateParams = useStore((s) => s.updateParams);
  const applyPresetTo = useStore((s) => s.applyPresetTo);
  const applyPresetToAll = useStore((s) => s.applyPresetToAll);
  const setAdSetResultType = useStore((s) => s.setAdSetResultType);
  const setScenarioName = useStore((s) => s.setScenarioName);
  const setAccountId = useStore((s) => s.setAccountId);
  const setCurrency = useStore((s) => s.setCurrency);
  const setSeed = useStore((s) => s.setSeed);
  const reseed = useStore((s) => s.reseed);
  const resetScenario = useStore((s) => s.resetScenario);
  const library = useStore((s) => s.library);
  const saveToLibrary = useStore((s) => s.saveToLibrary);
  const loadFromLibrary = useStore((s) => s.loadFromLibrary);
  const deleteFromLibrary = useStore((s) => s.deleteFromLibrary);
  const importScenario = useStore((s) => s.importScenario);

  const currency = scenario.account.currency;
  const days = dayCount(scenario.dateRange.start, scenario.dateRange.end);

  const firstSelectedAdSet = useMemo(() => {
    const fromSelection = scenario.adSets.find((a) => ui.selected.includes(a.id));
    if (fromSelection) return fromSelection.id;
    if (ui.adSetFilter) return ui.adSetFilter;
    if (ui.campaignFilter) {
      const s = scenario.adSets.find((a) => a.campaignId === ui.campaignFilter);
      if (s) return s.id;
    }
    return scenario.adSets[0]?.id ?? '';
  }, [scenario.adSets, ui.selected, ui.adSetFilter, ui.campaignFilter]);

  const [adSetId, setAdSetId] = useState(firstSelectedAdSet);
  // Al abrir el panel, saltar al conjunto que está seleccionado en la tabla.
  useEffect(() => {
    if (ui.panelOpen) setAdSetId(firstSelectedAdSet);
  }, [ui.panelOpen, firstSelectedAdSet]);
  const adSet = scenario.adSets.find((a) => a.id === adSetId) ?? scenario.adSets.find((a) => a.id === firstSelectedAdSet);
  const [saveName, setSaveName] = useState(scenario.name);

  if (!ui.panelOpen) return null;

  const p = adSet?.params;
  const projection = p ? project(p) : null;
  const set = (patch: Partial<SimParams>) => adSet && updateParams(adSet.id, patch);

  const exportJson = () => {
    downloadFile(
      `escenario-${slug(scenario.name)}.json`,
      JSON.stringify(scenario, null, 2),
      'application/json',
    );
  };

  const importJson = async () => {
    const text = await pickFile('application/json,.json');
    if (!text) return;
    try {
      const parsed = JSON.parse(text) as Scenario;
      if (!parsed.campaigns || !parsed.adSets) throw new Error('formato');
      importScenario(parsed);
      setSaveName(parsed.name);
    } catch {
      alert('Ese archivo no es un escenario válido.');
    }
  };

  return (
    <>
      <div
        className="fixed inset-0 z-40 bg-black/10"
        onClick={() => patchUI({ panelOpen: false })}
      />
      <aside className="fixed right-0 top-0 z-50 flex h-full w-[430px] flex-col border-l border-meta-border bg-white shadow-[-8px_0_28px_rgba(0,0,0,.18)]">
        <header className="flex shrink-0 items-center gap-2 border-b border-meta-border px-4 py-3">
          <div className="flex-1">
            <div className="text-[13px] font-bold">Panel de control · simulación</div>
            <div className="text-2xs text-meta-secondary">
              Fuera de cámara. Se abre con Ctrl/Cmd + K o desde la esquina inferior izquierda.
            </div>
          </div>
          <button
            type="button"
            onClick={() => patchUI({ panelOpen: false })}
            className="rounded-full p-1.5 text-meta-secondary hover:bg-meta-hover"
          >
            <IconX />
          </button>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto">
          {/* ------------------------------------------------ FILMACIÓN */}
          <Section title="Modo filmación">
            <Switch
              label="Modo limpio"
              hint="Oculta todo rastro de simulación. Dejalo prendido para grabar."
              on={ui.cleanMode}
              onChange={() => patchUI({ cleanMode: !ui.cleanMode })}
            />
            <Switch
              label={'Marca de agua "SIMULACIÓN"'}
              hint="Prendida por defecto: transparencia con la audiencia."
              on={ui.watermark}
              onChange={() => patchUI({ watermark: !ui.watermark })}
            />

            <div className="mt-2 rounded-lg border border-meta-borderLight bg-[#F7F8FA] p-3">
              <div className="mb-2 flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => (playback.playing ? pause() : play())}
                  className="meta-btn-primary h-[34px] px-4"
                >
                  {playback.playing ? <IconPause /> : <IconPlay />}
                  {playback.playing ? 'Pausar' : 'Reproducir'}
                </button>
                <button type="button" onClick={restart} className="meta-btn h-[34px]">
                  <IconRestart />
                  Reiniciar
                </button>
                <button
                  type="button"
                  onClick={() => patchPlayback({ playing: false, progress: 1 })}
                  className="meta-btn h-[34px]"
                >
                  Ir al final
                </button>
              </div>

              <label className="block">
                <span className="panel-label">
                  Avance del período · día {Math.max(1, Math.ceil(playback.progress * days))} de {days}
                </span>
                <input
                  type="range"
                  min={0}
                  max={1}
                  step={0.002}
                  value={playback.progress}
                  onChange={(e) => patchPlayback({ progress: Number(e.target.value), playing: false })}
                  className="w-full accent-[#0866FF]"
                />
              </label>

              <div className="mt-2 grid grid-cols-2 gap-2">
                <Field
                  label="Time-lapse (segundos)"
                  value={playback.durationSec}
                  min={1}
                  onChange={(v) => patchPlayback({ durationSec: Math.max(1, v) })}
                  suffix="seg"
                />
                <div className="flex items-end pb-1">
                  <span className="text-2xs leading-[15px] text-meta-secondary">
                    {days} días en {playback.durationSec} s
                    <br />({(days / playback.durationSec).toFixed(1)} días/seg)
                  </span>
                </div>
              </div>

              <div className="mt-1">
                <Switch
                  label="Repetir en loop"
                  on={playback.loop}
                  onChange={() => patchPlayback({ loop: !playback.loop })}
                />
              </div>
            </div>

            <label className="mt-3 block">
              <span className="panel-label">Fecha y hora que se muestra en pantalla</span>
              <div className="flex gap-2">
                <input
                  type="datetime-local"
                  value={ui.displayDateTime ?? ''}
                  onChange={(e) => patchUI({ displayDateTime: e.target.value || null })}
                  className="panel-input"
                />
                <button
                  type="button"
                  className="meta-btn shrink-0"
                  onClick={() => patchUI({ displayDateTime: null })}
                >
                  Ahora
                </button>
              </div>
            </label>
          </Section>

          {/* --------------------------------------------------- PRESETS */}
          <Section
            title="Presets"
            hint="Se aplican sobre el benchmark del mercado. Después podés pisar cualquier valor a mano."
          >
            <div className="grid grid-cols-2 gap-2">
              {PRESETS.map((preset) => (
                <button
                  key={preset.id}
                  type="button"
                  onClick={() => adSet && applyPresetTo(adSet.id, preset.id as PresetId)}
                  title={preset.description}
                  className="rounded-lg border border-meta-border px-3 py-2 text-left transition hover:border-meta-blue hover:bg-meta-blueHover"
                >
                  <div className="text-xs font-semibold">
                    {preset.emoji} {preset.label}
                  </div>
                  <div className="mt-0.5 line-clamp-2 text-2xs leading-[14px] text-meta-secondary">
                    {preset.description}
                  </div>
                </button>
              ))}
            </div>
            <div className="mt-2.5">
              <span className="panel-label">Aplicar a todos los conjuntos</span>
              <div className="flex flex-wrap gap-1.5">
                {PRESETS.map((preset) => (
                  <button
                    key={preset.id}
                    type="button"
                    onClick={() => applyPresetToAll(preset.id as PresetId)}
                    className="meta-btn text-2xs"
                  >
                    {preset.emoji} {preset.label.replace('Campaña ', '')}
                  </button>
                ))}
              </div>
            </div>
          </Section>

          {/* --------------------------------------- CONJUNTO SELECCIONADO */}
          <Section title="Inputs del conjunto de anuncios">
            <label className="mb-2.5 block">
              <span className="panel-label">Conjunto</span>
              <select
                value={adSet?.id ?? ''}
                onChange={(e) => setAdSetId(e.target.value)}
                className="panel-input"
              >
                {scenario.campaigns.map((c) => (
                  <optgroup key={c.id} label={c.name}>
                    {scenario.adSets
                      .filter((a) => a.campaignId === c.id)
                      .map((a) => (
                        <option key={a.id} value={a.id}>
                          {a.name}
                        </option>
                      ))}
                  </optgroup>
                ))}
              </select>
            </label>

            {p && adSet && (
              <>
                <div className="grid grid-cols-2 gap-2.5">
                  <Field
                    label="Gasto del período"
                    value={p.spend}
                    step={1000}
                    onChange={(v) => set({ spend: v })}
                    suffix={CURRENCIES[currency].symbol}
                  />
                  <Field label="CPM base" value={p.cpm} step={50} onChange={(v) => set({ cpm: v })} />
                  <Field
                    label="Variación del CPM"
                    value={round(p.cpmVariance * 100, 1)}
                    step={1}
                    onChange={(v) => set({ cpmVariance: v / 100 })}
                    suffix="%"
                  />
                  <Field
                    label="CTR del enlace"
                    value={round(p.ctr * 100, 3)}
                    step={0.05}
                    onChange={(v) => set({ ctr: v / 100 })}
                    suffix="%"
                  />
                  <Field
                    label="Frecuencia"
                    value={p.frequency}
                    step={0.1}
                    onChange={(v) => set({ frequency: v })}
                  />
                  <Field
                    label="Tasa de conversión"
                    value={round(p.conversionRate * 100, 3)}
                    step={0.1}
                    onChange={(v) => set({ conversionRate: v / 100 })}
                    suffix="%"
                  />
                  <Field
                    label="Ticket promedio"
                    value={p.aov}
                    step={100}
                    onChange={(v) => set({ aov: v })}
                    suffix={CURRENCIES[currency].symbol}
                  />
                  <Field
                    label="Ruido diario"
                    value={round(p.noise * 100, 1)}
                    step={1}
                    onChange={(v) => set({ noise: v / 100 })}
                    suffix="%"
                  />
                  <Field
                    label="Días de aprendizaje"
                    value={p.learningDays}
                    onChange={(v) => set({ learningDays: Math.round(v) })}
                  />
                  <Field
                    label="Factor fin de semana"
                    value={p.weekendFactor}
                    step={0.05}
                    onChange={(v) => set({ weekendFactor: v })}
                  />
                </div>

                <label className="mt-2.5 block">
                  <span className="panel-label">Tipo de resultado</span>
                  <select
                    value={adSet.resultType}
                    onChange={(e) => setAdSetResultType(adSet.id, e.target.value as ResultType)}
                    className="panel-input"
                  >
                    {(Object.keys(RESULT_TYPE_LABEL) as ResultType[]).map((r) => (
                      <option key={r} value={r}>
                        {RESULT_TYPE_LABEL[r]}
                      </option>
                    ))}
                  </select>
                </label>

                {projection && (
                  <div className="mt-3 rounded-lg border border-meta-borderLight bg-[#F7F8FA] p-3">
                    <div className="mb-1.5 text-2xs font-bold uppercase tracking-wide text-meta-secondary">
                      Proyección del período (sin ruido)
                    </div>
                    <dl className="grid grid-cols-2 gap-x-3 gap-y-1 text-2xs">
                      <Proj label="Impresiones" value={int(projection.impressions, currency)} />
                      <Proj label="Alcance" value={int(projection.reach, currency)} />
                      <Proj label="Clics en el enlace" value={int(projection.clicks, currency)} />
                      <Proj label="CPC" value={money(projection.cpc, currency, { micro: true })} />
                      <Proj label="Resultados" value={int(projection.results, currency)} />
                      <Proj
                        label="Costo por resultado"
                        value={money(projection.cpa, currency, { micro: true })}
                      />
                      <Proj label="Valor de conversión" value={money(projection.value, currency)} />
                      <Proj label="ROAS" value={ratio(projection.roas, currency)} />
                      <Proj label="CTR" value={percent(p.ctr, currency)} />
                      <Proj
                        label="Gasto diario medio"
                        value={money(p.spend / Math.max(1, days), currency)}
                      />
                    </dl>
                  </div>
                )}
              </>
            )}
          </Section>

          {/* ------------------------------------------------- ESCENARIO */}
          <Section title="Escenario y cuenta">
            <div className="grid grid-cols-2 gap-2.5">
              <label className="col-span-2 block">
                <span className="panel-label">Nombre del escenario</span>
                <input
                  value={scenario.name}
                  onChange={(e) => setScenarioName(e.target.value)}
                  className="panel-input"
                />
              </label>
              <label className="block">
                <span className="panel-label">ID de cuenta</span>
                <input
                  value={scenario.account.id}
                  onChange={(e) => setAccountId(e.target.value)}
                  className="panel-input"
                />
              </label>
              <label className="block">
                <span className="panel-label">Moneda</span>
                <select
                  value={currency}
                  onChange={(e) => setCurrency(e.target.value as typeof currency, true)}
                  className="panel-input"
                >
                  {CURRENCY_ORDER.map((c) => (
                    <option key={c} value={c}>
                      {c} · {MARKET_BENCHMARKS[c].label}
                    </option>
                  ))}
                </select>
              </label>
              <Field label="Semilla" value={scenario.seed} onChange={(v) => setSeed(Math.round(v))} />
              <div className="flex items-end pb-[1px]">
                <button type="button" onClick={reseed} className="meta-btn h-[32px] w-full justify-center">
                  Nueva semilla
                </button>
              </div>
            </div>

            <div className="mt-2">
              <Switch
                label="Escalar el gasto al cambiar el rango"
                hint={`Período actual: ${formatRange(scenario.dateRange.start, scenario.dateRange.end)} (${days} días).`}
                on={ui.scaleSpendOnRangeChange}
                onChange={() => patchUI({ scaleSpendOnRangeChange: !ui.scaleSpendOnRangeChange })}
              />
            </div>

            <button
              type="button"
              onClick={() => {
                if (confirm('¿Restablecer el escenario de ejemplo? Se pierden los cambios no guardados.'))
                  resetScenario();
              }}
              className="meta-btn mt-2 h-[32px]"
            >
              Restablecer escenario de ejemplo
            </button>
          </Section>

          {/* ------------------------------------------------ BIBLIOTECA */}
          <Section title="Escenarios guardados">
            <div className="flex gap-2">
              <input
                value={saveName}
                onChange={(e) => setSaveName(e.target.value)}
                placeholder="Nombre del escenario"
                className="panel-input"
              />
              <button
                type="button"
                onClick={() => saveToLibrary(saveName)}
                className="meta-btn-primary shrink-0"
              >
                Guardar
              </button>
            </div>

            <div className="mt-2.5 space-y-1">
              {library.length === 0 && (
                <p className="text-2xs text-meta-secondary">Todavía no guardaste ningún escenario.</p>
              )}
              {library.map((entry) => (
                <div
                  key={entry.id}
                  className="flex items-center gap-2 rounded-md border border-meta-borderLight px-2.5 py-1.5"
                >
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-xs font-medium">{entry.name}</div>
                    <div className="text-2xs text-meta-secondary">
                      {new Date(entry.savedAt).toLocaleString('es-AR')}
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => loadFromLibrary(entry.id)}
                    className="meta-btn-ghost text-2xs text-meta-blueLink"
                  >
                    Cargar
                  </button>
                  <button
                    type="button"
                    title="Eliminar"
                    onClick={() => deleteFromLibrary(entry.id)}
                    className="rounded p-1 text-meta-secondary hover:bg-meta-hover hover:text-meta-red"
                  >
                    <IconTrash />
                  </button>
                </div>
              ))}
            </div>

            <div className="mt-3 flex gap-2">
              <button type="button" onClick={exportJson} className="meta-btn flex-1 justify-center">
                <IconExport />
                Exportar JSON
              </button>
              <button type="button" onClick={importJson} className="meta-btn flex-1 justify-center">
                <IconImport />
                Importar JSON
              </button>
            </div>
          </Section>

          <div className="px-4 py-4 text-2xs leading-[15px] text-meta-secondary">
            Los datos de esta app son 100% simulados y se generan en tu navegador. No hay conexión
            con la API de Meta ni gasto real.
          </div>
        </div>
      </aside>
    </>
  );
}

function Proj({ label, value }: { label: string; value: string }) {
  return (
    <>
      <dt className="text-meta-secondary">{label}</dt>
      <dd className="num text-right font-semibold">{value}</dd>
    </>
  );
}

/** Las mismas fórmulas de la tabla, sin ruido: sirve para calibrar a ojo. */
function project(p: SimParams) {
  const impressions = p.cpm > 0 ? (p.spend / p.cpm) * 1000 : 0;
  const reach = p.frequency > 0 ? impressions / p.frequency : 0;
  const clicks = impressions * p.ctr;
  const results = clicks * p.conversionRate;
  const value = results * p.aov;
  return {
    impressions,
    reach,
    clicks,
    cpc: clicks > 0 ? p.spend / clicks : null,
    results,
    cpa: results > 0 ? p.spend / results : null,
    value,
    roas: p.spend > 0 && value > 0 ? value / p.spend : null,
  };
}

const round = (v: number, d: number) => Math.round(v * Math.pow(10, d)) / Math.pow(10, d);
const slug = (s: string) =>
  s
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '') || 'escenario';
