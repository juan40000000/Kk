import { COLUMNS_BY_ID } from '@/config/columns';
import { useStore } from '@/store/useStore';
import type { TableRow } from '@/store/useRows';
import { downloadFile } from '@/lib/download';
import { formatRange, formatShortDate } from '@/engine/dates';
import { money } from '@/lib/format';
import { ColumnPicker } from './ColumnPicker';
import { Dropdown, DropdownItem } from './Dropdown';
import {
  IconAB,
  IconBreakdown,
  IconChartBars,
  IconChevronDown,
  IconDuplicate,
  IconEllipsis,
  IconExport,
  IconPencil,
  IconPlus,
  IconRules,
} from './icons';
import { cn } from '@/lib/cn';

/** "Actualizado: 25 ago 2026, 14:32" — la hora que se muestra es configurable. */
function updatedLabel(displayDateTime: string | null): string {
  const d = displayDateTime ? new Date(displayDateTime) : new Date();
  const date = Number.isNaN(d.getTime()) ? new Date() : d;
  const hh = String(date.getHours()).padStart(2, '0');
  const mm = String(date.getMinutes()).padStart(2, '0');
  return `Actualizado: ${formatShortDate(
    `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(
      date.getDate(),
    ).padStart(2, '0')}`,
  )}, ${hh}:${mm}`;
}

export function Toolbar({ rows, totalLabel }: { rows: TableRow[]; totalLabel: string }) {
  const ui = useStore((s) => s.ui);
  const scenario = useStore((s) => s.scenario);
  const patchUI = useStore((s) => s.patchUI);
  const addCampaign = useStore((s) => s.addCampaign);
  const addAdSet = useStore((s) => s.addAdSet);
  const addAd = useStore((s) => s.addAd);
  const duplicate = useStore((s) => s.duplicate);
  const selected = ui.selected;

  const create = () => {
    if (ui.level === 'campaigns') addCampaign();
    else if (ui.level === 'adsets') addAdSet(ui.campaignFilter ?? scenario.campaigns[0]?.id);
    else addAd(ui.adSetFilter ?? scenario.adSets[0]?.id);
  };

  const exportCsv = () => {
    const cur = scenario.account.currency;
    const cols = ui.columns.filter((c) => c !== 'delivery' && c !== 'budget');
    const header = ['Nombre', 'Entrega', 'Presupuesto', ...cols.map((c) => COLUMNS_BY_ID[c].label)];
    const body = rows.map((r) => [
      r.name,
      r.status,
      r.budget ? money(r.budget.amount, cur) : '',
      ...cols.map((c) => {
        const def = COLUMNS_BY_ID[c];
        if (c === 'results') return String(Math.round(r.metrics.results));
        return def.format ? def.format(r.metrics, cur) : '';
      }),
    ]);
    const csv = [header, ...body]
      .map((line) => line.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(';'))
      .join('\n');
    downloadFile(
      `informe-${scenario.dateRange.start}_${scenario.dateRange.end}.csv`,
      '﻿' + csv,
      'text/csv;charset=utf-8',
    );
  };

  return (
    <div className="flex shrink-0 items-center gap-1.5 border-b border-meta-border bg-white px-3 py-2">
      <button type="button" className="meta-btn-primary h-[32px]" onClick={create}>
        <IconPlus size={15} />
        Crear
      </button>
      <button
        type="button"
        className="meta-btn h-[32px]"
        disabled={selected.length === 0}
        onClick={() => selected.forEach((id) => duplicate(ui.level, id))}
      >
        <IconDuplicate size={15} className="text-meta-secondary" />
        Duplicar
      </button>
      <button
        type="button"
        className="meta-btn h-[32px]"
        disabled={selected.length === 0}
        onClick={() => patchUI({ panelOpen: true })}
      >
        <IconPencil size={15} className="text-meta-secondary" />
        Editar
      </button>
      <button type="button" className="meta-btn h-[32px]">
        <IconAB size={15} className="text-meta-secondary" />
        Prueba A/B
      </button>
      <button type="button" className="meta-btn h-[32px]">
        <IconRules size={15} className="text-meta-secondary" />
        Reglas
        <IconChevronDown size={13} className="text-meta-secondary" />
      </button>
      <button type="button" className="meta-btn h-[32px] px-2">
        <IconEllipsis size={15} className="text-meta-secondary" />
      </button>

      <span className="ml-2 hidden text-2xs text-meta-secondary 2xl:inline">
        {totalLabel} · {formatRange(scenario.dateRange.start, scenario.dateRange.end)} ·{' '}
        {updatedLabel(ui.displayDateTime)}
      </span>

      <div className="ml-auto flex items-center gap-1.5">
        <button
          type="button"
          onClick={() => patchUI({ chartOpen: !ui.chartOpen })}
          className={cn('meta-btn h-[32px]', ui.chartOpen && 'border-meta-blue text-meta-blueLink')}
        >
          <IconChartBars size={15} className={ui.chartOpen ? '' : 'text-meta-secondary'} />
          {ui.chartOpen ? 'Ocultar gráficos' : 'Ver gráficos'}
        </button>
        <ColumnPicker />
        <button type="button" className="meta-btn h-[32px]">
          <IconBreakdown className="text-meta-secondary" />
          Desglose
          <IconChevronDown size={13} className="text-meta-secondary" />
        </button>
        <Dropdown
          align="right"
          width={220}
          trigger={() => (
            <button type="button" className="meta-btn h-[32px]">
              <IconExport className="text-meta-secondary" />
              Informes
              <IconChevronDown size={13} className="text-meta-secondary" />
            </button>
          )}
        >
          {(close) => (
            <>
              <DropdownItem
                onClick={() => {
                  exportCsv();
                  close();
                }}
              >
                Exportar tabla (.csv)
              </DropdownItem>
              <DropdownItem onClick={close}>Crear informe personalizado</DropdownItem>
              <DropdownItem onClick={close}>Programar envío por correo</DropdownItem>
            </>
          )}
        </Dropdown>
      </div>
    </div>
  );
}
