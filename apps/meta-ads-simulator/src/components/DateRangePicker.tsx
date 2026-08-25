import { useStore } from '@/store/useStore';
import { Dropdown, DropdownItem } from './Dropdown';
import { IconCalendar, IconChevronDown } from './icons';
import { addDays, dayCount, formatRange, toISO } from '@/engine/dates';

interface RangePreset {
  label: string;
  build: () => { start: string; end: string };
}

const today = () => toISO(new Date());

const PRESETS: RangePreset[] = [
  { label: 'Hoy', build: () => ({ start: today(), end: today() }) },
  { label: 'Ayer', build: () => ({ start: addDays(today(), -1), end: addDays(today(), -1) }) },
  { label: 'Últimos 7 días', build: () => ({ start: addDays(today(), -6), end: today() }) },
  { label: 'Últimos 14 días', build: () => ({ start: addDays(today(), -13), end: today() }) },
  { label: 'Últimos 30 días', build: () => ({ start: addDays(today(), -29), end: today() }) },
  {
    label: 'Este mes',
    build: () => {
      const d = new Date();
      return { start: toISO(new Date(d.getFullYear(), d.getMonth(), 1)), end: today() };
    },
  },
  {
    label: 'Mes anterior',
    build: () => {
      const d = new Date();
      const first = new Date(d.getFullYear(), d.getMonth() - 1, 1);
      const last = new Date(d.getFullYear(), d.getMonth(), 0);
      return { start: toISO(first), end: toISO(last) };
    },
  },
  { label: 'Últimos 90 días', build: () => ({ start: addDays(today(), -89), end: today() }) },
];

export function DateRangePicker() {
  const range = useStore((s) => s.scenario.dateRange);
  const setDateRange = useStore((s) => s.setDateRange);
  const days = dayCount(range.start, range.end);

  const activeLabel =
    PRESETS.find((p) => {
      const r = p.build();
      return r.start === range.start && r.end === range.end;
    })?.label ?? 'Personalizado';

  return (
    <Dropdown
      align="right"
      width={300}
      trigger={(open) => (
        <button
          type="button"
          className={`meta-btn h-[34px] whitespace-nowrap ${open ? 'bg-meta-hover' : ''}`}
        >
          <IconCalendar className="text-meta-secondary" />
          <span className="font-semibold">{activeLabel}:</span>
          <span className="font-normal text-meta-secondary">{formatRange(range.start, range.end)}</span>
          <IconChevronDown className="text-meta-secondary" size={14} />
        </button>
      )}
    >
      {(close) => (
        <div>
          <div className="px-3 pb-1 pt-1 text-2xs font-semibold uppercase tracking-wide text-meta-secondary">
            Períodos
          </div>
          {PRESETS.map((p) => (
            <DropdownItem
              key={p.label}
              active={p.label === activeLabel}
              onClick={() => {
                const r = p.build();
                setDateRange(r.start, r.end);
                close();
              }}
            >
              <span className="flex-1">{p.label}</span>
              <span className="text-2xs text-meta-tertiary">
                {formatRange(p.build().start, p.build().end)}
              </span>
            </DropdownItem>
          ))}
          <div className="mt-1 border-t border-meta-borderLight px-3 pb-1 pt-2">
            <div className="mb-1 text-2xs font-semibold uppercase tracking-wide text-meta-secondary">
              Personalizado
            </div>
            <div className="flex items-center gap-2">
              <input
                type="date"
                value={range.start}
                max={range.end}
                onChange={(e) => setDateRange(e.target.value, range.end)}
                className="panel-input"
              />
              <span className="text-meta-secondary">–</span>
              <input
                type="date"
                value={range.end}
                min={range.start}
                onChange={(e) => setDateRange(range.start, e.target.value)}
                className="panel-input"
              />
            </div>
            <div className="mt-2 text-2xs text-meta-secondary">
              {days} {days === 1 ? 'día' : 'días'} en el período
            </div>
          </div>
        </div>
      )}
    </Dropdown>
  );
}
