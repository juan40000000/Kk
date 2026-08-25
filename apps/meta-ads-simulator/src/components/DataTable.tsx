import { useState } from 'react';
import { COLUMNS_BY_ID, RESULT_TYPE_LABEL, PIXEL_SOURCE } from '@/config/columns';
import { useStore } from '@/store/useStore';
import type { RowsResult, TableRow } from '@/store/useRows';
import { cn } from '@/lib/cn';
import { int, money, parseNumberInput } from '@/lib/format';
import { AnimatedValue, Checkbox, DeliveryCell, EditableText, Toggle } from './primitives';
import { IconChartBars, IconDuplicate } from './icons';
import type { Metrics } from '@/engine/types';

const CHECK_W = 34;
const NAME_W = 320;

export function DataTable({ data }: { data: RowsResult }) {
  const ui = useStore((s) => s.ui);
  const currency = useStore((s) => s.scenario.account.currency);
  const playing = useStore((s) => s.playback.playing);
  const toggleSelected = useStore((s) => s.toggleSelected);
  const selectAll = useStore((s) => s.selectAll);
  const toggleStatus = useStore((s) => s.toggleStatus);
  const renameEntity = useStore((s) => s.renameEntity);
  const duplicate = useStore((s) => s.duplicate);
  const drillToCampaign = useStore((s) => s.drillToCampaign);
  const drillToAdSet = useStore((s) => s.drillToAdSet);

  const columns = ui.columns.map((id) => COLUMNS_BY_ID[id]);
  const minWidth = CHECK_W + NAME_W + columns.reduce((a, c) => a + c.width, 0);
  const allIds = data.rows.map((r) => r.id);
  const allSelected = allIds.length > 0 && ui.selected.length === allIds.length;

  const drill = (row: TableRow) => {
    if (!row.drillTo) return;
    if (row.drillTo.level === 'adsets') drillToCampaign(row.drillTo.id);
    else drillToAdSet(row.drillTo.id);
  };

  return (
    <div className="min-h-0 flex-1 overflow-auto bg-white">
      <table
        className="table-fixed border-separate border-spacing-0 text-xs"
        style={{ width: minWidth, minWidth }}
      >
        <thead>
          <tr className="h-[46px]">
            <th
              className="sticky left-0 top-0 z-30 border-b border-r border-meta-border bg-meta-header px-2 align-middle"
              style={{ width: CHECK_W, minWidth: CHECK_W }}
            >
              <Checkbox
                checked={allSelected}
                indeterminate={ui.selected.length > 0 && !allSelected}
                onChange={() => selectAll(allIds)}
              />
            </th>
            <th
              className="sticky top-0 z-30 border-b border-r border-meta-border bg-meta-header px-2 text-left align-middle text-2xs font-semibold text-meta-secondary shadow-[1px_0_0_0_#DADDE1]"
              style={{ left: CHECK_W, width: NAME_W, minWidth: NAME_W }}
            >
              {ui.level === 'campaigns'
                ? 'Campaña'
                : ui.level === 'adsets'
                  ? 'Conjunto de anuncios'
                  : 'Anuncio'}
            </th>
            {columns.map((c) => (
              <th
                key={c.id}
                className={cn(
                  'sticky top-0 z-20 border-b border-meta-border bg-meta-header px-2 align-middle text-2xs font-semibold leading-[13px] text-meta-secondary',
                  c.align === 'right' ? 'text-right' : 'text-left',
                )}
                style={{ width: c.width, minWidth: c.width }}
              >
                {c.label}
              </th>
            ))}
          </tr>
        </thead>

        <tbody>
          {data.rows.map((row) => (
            <Row
              key={row.id}
              row={row}
              columns={columns}
              selected={ui.selected.includes(row.id)}
              tween={!playing}
              onSelect={() => toggleSelected(row.id)}
              onToggle={() => toggleStatus(row.level, row.id)}
              onRename={(name) => renameEntity(row.level, row.id, name)}
              onDuplicate={() => duplicate(row.level, row.id)}
              onDrill={() => drill(row)}
              currency={currency}
            />
          ))}
          {data.rows.length === 0 && (
            <tr>
              <td colSpan={columns.length + 2} className="py-14 text-center text-meta-secondary">
                No hay resultados para este filtro.
              </td>
            </tr>
          )}
        </tbody>

        <tfoot>
          <tr className="h-[42px]">
            <td
              className="sticky bottom-0 left-0 z-30 border-r border-t border-meta-border bg-[#F5F6F7]"
              style={{ width: CHECK_W }}
            />
            <td
              className="sticky bottom-0 z-30 border-r border-t border-meta-border bg-[#F5F6F7] px-2 text-xs font-semibold shadow-[1px_0_0_0_#DADDE1]"
              style={{ left: CHECK_W, width: NAME_W }}
            >
              {data.totalLabel}
            </td>
            {columns.map((c) => (
              <td
                key={c.id}
                className={cn(
                  'num sticky bottom-0 z-20 border-t border-meta-border bg-[#F5F6F7] px-2 text-xs font-semibold',
                  c.align === 'right' ? 'text-right' : 'text-left',
                )}
              >
                <TotalCell columnId={c.id} total={data.total} rows={data.rows} tween={!playing} />
              </td>
            ))}
          </tr>
        </tfoot>
      </table>
    </div>
  );
}

/* ------------------------------------------------------------------ Fila */

function Row({
  row,
  columns,
  selected,
  tween,
  onSelect,
  onToggle,
  onRename,
  onDuplicate,
  onDrill,
  currency,
}: {
  row: TableRow;
  columns: (typeof COLUMNS_BY_ID)[keyof typeof COLUMNS_BY_ID][];
  selected: boolean;
  tween: boolean;
  onSelect: () => void;
  onToggle: () => void;
  onRename: (name: string) => void;
  onDuplicate: () => void;
  onDrill: () => void;
  currency: Parameters<typeof money>[1];
}) {
  const bg = selected ? 'bg-meta-blueHover' : 'bg-white group-hover/row:bg-meta-hover';

  return (
    <tr className="group/row h-[48px] cursor-default" onClick={onSelect}>
      <td
        className={cn('sticky left-0 z-10 border-b border-r border-meta-borderLight px-2 align-middle', bg)}
        style={{ width: CHECK_W }}
      >
        <Checkbox checked={selected} onChange={onSelect} />
      </td>

      <td
        className={cn(
          'sticky z-10 border-b border-r border-meta-borderLight px-2 align-middle shadow-[1px_0_0_0_#DADDE1]',
          bg,
        )}
        style={{ left: CHECK_W, width: NAME_W }}
      >
        <div className="flex items-center gap-2">
          <Toggle
            on={row.status !== 'off'}
            onChange={onToggle}
            title={row.status !== 'off' ? 'Desactivar' : 'Activar'}
          />
          <div className="min-w-0 flex-1">
            <div className="text-xs leading-[15px]">
              <EditableText value={row.name} onChange={onRename} linkStyle={!!row.drillTo} onOpen={onDrill} />
            </div>
            {row.sub && (
              <div className="truncate text-2xs leading-[14px] text-meta-secondary" title={row.sub}>
                {row.sub}
              </div>
            )}
          </div>
          <div className="hidden shrink-0 items-center gap-0.5 group-hover/row:flex">
            <button
              type="button"
              title="Duplicar"
              onClick={(e) => {
                e.stopPropagation();
                onDuplicate();
              }}
              className="rounded p-1 text-meta-secondary hover:bg-[#E4E6EB] hover:text-meta-text"
            >
              <IconDuplicate />
            </button>
            <button
              type="button"
              title="Ver gráficos"
              onClick={(e) => e.stopPropagation()}
              className="rounded p-1 text-meta-secondary hover:bg-[#E4E6EB] hover:text-meta-text"
            >
              <IconChartBars />
            </button>
          </div>
        </div>
      </td>

      {columns.map((c) => (
        <td
          key={c.id}
          className={cn(
            'num border-b border-meta-borderLight px-2 align-middle text-xs',
            c.align === 'right' ? 'text-right' : 'text-left',
            bg,
          )}
        >
          <Cell columnId={c.id} row={row} tween={tween} currency={currency} />
        </td>
      ))}
    </tr>
  );
}

/* ----------------------------------------------------------------- Celdas */

function Cell({
  columnId,
  row,
  tween,
  currency,
}: {
  columnId: keyof typeof COLUMNS_BY_ID;
  row: TableRow;
  tween: boolean;
  currency: Parameters<typeof money>[1];
}) {
  const def = COLUMNS_BY_ID[columnId];

  if (columnId === 'delivery') return <DeliveryCell status={row.status} />;
  if (columnId === 'budget') return <BudgetCell row={row} currency={currency} />;

  if (columnId === 'results') {
    return (
      <div className="leading-tight">
        <AnimatedValue
          value={row.metrics.results}
          tween={tween}
          format={(v) => int(v, currency, { dashOnZero: true })}
          className="block text-xs"
        />
        <span className="block text-2xs text-meta-secondary">
          {RESULT_TYPE_LABEL[row.resultType]}
        </span>
      </div>
    );
  }

  const numericValue = (row.metrics as unknown as Record<string, number | null>)[columnId] ?? null;
  const cell = (
    <AnimatedValue
      value={numericValue}
      tween={tween}
      format={(v) => (def.format ? def.format({ ...row.metrics, [columnId]: v } as Metrics, currency) : '')}
    />
  );

  if (columnId === 'purchases' || columnId === 'purchaseValue') {
    return (
      <div className="leading-tight">
        <span className="block text-xs">{cell}</span>
        <span className="block text-2xs text-meta-secondary">{PIXEL_SOURCE}</span>
      </div>
    );
  }
  return cell;
}

function BudgetCell({ row, currency }: { row: TableRow; currency: Parameters<typeof money>[1] }) {
  const setBudget = useStore((s) => s.setBudget);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState('');

  if (!row.budget) {
    return (
      <div className="leading-tight text-right">
        <span className="block text-xs text-meta-text">—</span>
        {row.budgetNote && (
          <span className="block text-2xs text-meta-secondary">{row.budgetNote}</span>
        )}
      </div>
    );
  }

  const commit = () => {
    const n = parseNumberInput(draft);
    if (n !== null && row.level !== 'ads') {
      setBudget(row.level as 'campaigns' | 'adsets', row.id, { amount: n });
    }
    setEditing(false);
  };

  if (editing) {
    return (
      <input
        autoFocus
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onBlur={commit}
        onKeyDown={(e) => {
          if (e.key === 'Enter') commit();
          if (e.key === 'Escape') setEditing(false);
          e.stopPropagation();
        }}
        onClick={(e) => e.stopPropagation()}
        className="w-full rounded border border-meta-blue px-1 py-[1px] text-right text-xs outline-none ring-2 ring-meta-blue/20"
      />
    );
  }

  return (
    <div
      className="cursor-text leading-tight text-right"
      onClick={(e) => {
        e.stopPropagation();
        setDraft(String(row.budget?.amount ?? ''));
        setEditing(true);
      }}
      title="Clic para editar el presupuesto"
    >
      <span className="block text-xs">{money(row.budget.amount, currency)}</span>
      <span className="block text-2xs text-meta-secondary">
        {row.budget.type === 'daily' ? 'Diario' : 'Total'}
      </span>
    </div>
  );
}

function TotalCell({
  columnId,
  total,
  rows,
  tween,
}: {
  columnId: keyof typeof COLUMNS_BY_ID;
  total: Metrics;
  rows: TableRow[];
  tween: boolean;
}) {
  const currency = useStore((s) => s.scenario.account.currency);
  const def = COLUMNS_BY_ID[columnId];

  if (columnId === 'delivery') return null;

  if (columnId === 'budget') {
    const sum = rows.reduce((a, r) => a + (r.budget?.amount ?? 0), 0);
    if (sum === 0) return <span className="text-meta-secondary">—</span>;
    return (
      <div className="leading-tight">
        <span className="block">{money(sum, currency)}</span>
        <span className="block text-2xs font-normal text-meta-secondary">Total</span>
      </div>
    );
  }

  if (columnId === 'results') {
    const types = Array.from(new Set(rows.map((r) => r.resultType)));
    return (
      <div className="leading-tight">
        <AnimatedValue
          value={total.results}
          tween={tween}
          format={(v) => int(v, currency, { dashOnZero: true })}
          className="block"
        />
        <span className="block text-2xs font-normal text-meta-secondary">
          {types.length === 1 ? RESULT_TYPE_LABEL[types[0]] : 'Varios'}
        </span>
      </div>
    );
  }

  const value = (total as unknown as Record<string, number | null>)[columnId] ?? null;
  return (
    <AnimatedValue
      value={value}
      tween={tween}
      format={(v) => (def.format ? def.format({ ...total, [columnId]: v } as Metrics, currency) : '')}
    />
  );
}
