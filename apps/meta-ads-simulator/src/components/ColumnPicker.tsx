import { COLUMNS, DEFAULT_COLUMNS, type ColumnId } from '@/config/columns';
import { useStore } from '@/store/useStore';
import { Dropdown } from './Dropdown';
import { Checkbox } from './primitives';
import { IconChevronDown, IconColumns } from './icons';

const GROUPS = ['Rendimiento', 'Entrega', 'Conversiones'] as const;

export function ColumnPicker() {
  const columns = useStore((s) => s.ui.columns);
  const setColumns = useStore((s) => s.setColumns);

  const toggle = (id: ColumnId) => {
    setColumns(
      columns.includes(id)
        ? columns.filter((c) => c !== id)
        : DEFAULT_COLUMNS.filter((c) => columns.includes(c) || c === id),
    );
  };

  return (
    <Dropdown
      align="right"
      width={280}
      panelClassName="max-h-[70vh] overflow-y-auto"
      trigger={(open) => (
        <button type="button" className={`meta-btn h-[32px] ${open ? 'bg-meta-hover' : ''}`}>
          <IconColumns className="text-meta-secondary" />
          <span>Columnas: Rendimiento</span>
          <IconChevronDown className="text-meta-secondary" size={14} />
        </button>
      )}
    >
      {() => (
        <div className="pb-1">
          {GROUPS.map((group) => (
            <div key={group}>
              <div className="px-3 pb-1 pt-2 text-2xs font-semibold uppercase tracking-wide text-meta-secondary">
                {group}
              </div>
              {COLUMNS.filter((c) => c.group === group).map((c) => (
                <button
                  key={c.id}
                  type="button"
                  onClick={() => toggle(c.id)}
                  className="flex w-full items-center gap-2.5 px-3 py-[6px] text-left text-xs hover:bg-meta-hover"
                >
                  <Checkbox checked={columns.includes(c.id)} onChange={() => toggle(c.id)} />
                  <span className="flex-1">{c.label}</span>
                </button>
              ))}
            </div>
          ))}
          <div className="mt-1 border-t border-meta-borderLight px-3 pt-2">
            <button
              type="button"
              onClick={() => setColumns(DEFAULT_COLUMNS)}
              className="text-xs font-semibold text-meta-blueLink hover:underline"
            >
              Restablecer columnas
            </button>
          </div>
        </div>
      )}
    </Dropdown>
  );
}
