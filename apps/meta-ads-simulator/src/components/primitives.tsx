import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { cn } from '@/lib/cn';
import { IconPencil } from './icons';
import type { DeliveryStatus } from '@/engine/types';

/* ------------------------------------------------------------------ Toggle */

export function Toggle({
  on,
  onChange,
  title,
}: {
  on: boolean;
  onChange: () => void;
  title?: string;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      title={title}
      onClick={(e) => {
        e.stopPropagation();
        onChange();
      }}
      className={cn(
        'relative h-[18px] w-[32px] shrink-0 rounded-full transition-colors duration-150',
        on ? 'bg-meta-blue' : 'bg-meta-off',
      )}
    >
      <span
        className={cn(
          'absolute top-[2px] h-[14px] w-[14px] rounded-full bg-white shadow-[0_1px_2px_rgba(0,0,0,.25)] transition-all duration-150',
          on ? 'left-[16px]' : 'left-[2px]',
        )}
      />
    </button>
  );
}

/* --------------------------------------------------------------- Checkbox */

export function Checkbox({
  checked,
  onChange,
  indeterminate,
}: {
  checked: boolean;
  onChange: () => void;
  indeterminate?: boolean;
}) {
  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={checked}
      onClick={(e) => {
        e.stopPropagation();
        onChange();
      }}
      className={cn(
        'flex h-[15px] w-[15px] shrink-0 items-center justify-center rounded-[3px] border transition-colors',
        checked || indeterminate
          ? 'border-meta-blue bg-meta-blue text-white'
          : 'border-[#B0B3B8] bg-white hover:border-meta-secondary',
      )}
    >
      {indeterminate ? (
        <span className="h-[2px] w-[8px] rounded-full bg-white" />
      ) : checked ? (
        <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round">
          <path d="m5 12.5 5 5L19 7" />
        </svg>
      ) : null}
    </button>
  );
}

/* --------------------------------------------------------- Estado de entrega */

export const STATUS_META: Record<DeliveryStatus, { label: string; color: string; note?: string }> = {
  active: { label: 'Activa', color: '#31A24C' },
  off: { label: 'Desactivada', color: '#BCC0C4' },
  learning: { label: 'Activa', color: '#1877F2', note: 'En fase de aprendizaje' },
  in_review: { label: 'En revisión', color: '#F7B928' },
  rejected: { label: 'Rechazada', color: '#E41E3F' },
  completed: { label: 'Completada', color: '#8A8D91' },
  limited: { label: 'Activa', color: '#F7B928', note: 'Aprendizaje limitado' },
};

export function DeliveryCell({ status }: { status: DeliveryStatus }) {
  const s = STATUS_META[status];
  return (
    <div className="leading-tight">
      <div className="flex items-center gap-1.5">
        <span
          className="inline-block h-[7px] w-[7px] shrink-0 rounded-full"
          style={{ backgroundColor: s.color }}
        />
        <span className="text-xs text-meta-text">{s.label}</span>
      </div>
      {s.note && <div className="mt-[1px] pl-[13px] text-2xs text-meta-secondary">{s.note}</div>}
    </div>
  );
}

/* ------------------------------------------------------- Texto editable */

export function EditableText({
  value,
  onChange,
  className,
  linkStyle,
  onOpen,
}: {
  value: string;
  onChange: (v: string) => void;
  className?: string;
  linkStyle?: boolean;
  onOpen?: () => void;
}) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(value);
  const ref = useRef<HTMLInputElement>(null);

  useLayoutEffect(() => {
    if (editing) {
      ref.current?.focus();
      ref.current?.select();
    }
  }, [editing]);

  const commit = () => {
    setEditing(false);
    const next = draft.trim();
    if (next && next !== value) onChange(next);
    else setDraft(value);
  };

  if (editing) {
    return (
      <input
        ref={ref}
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onBlur={commit}
        onKeyDown={(e) => {
          if (e.key === 'Enter') commit();
          if (e.key === 'Escape') {
            setDraft(value);
            setEditing(false);
          }
          e.stopPropagation();
        }}
        onClick={(e) => e.stopPropagation()}
        className="w-full rounded border border-meta-blue px-1 py-[1px] text-xs outline-none ring-2 ring-meta-blue/20"
      />
    );
  }

  return (
    <span className="group/name flex min-w-0 items-center gap-1.5">
      <span
        onClick={(e) => {
          if (onOpen) {
            e.stopPropagation();
            onOpen();
          }
        }}
        className={cn(
          'truncate',
          linkStyle && 'cursor-pointer text-meta-blueLink hover:underline',
          className,
        )}
        title={value}
      >
        {value}
      </span>
      <button
        type="button"
        title="Editar nombre"
        onClick={(e) => {
          e.stopPropagation();
          setDraft(value);
          setEditing(true);
        }}
        className="hidden shrink-0 rounded p-[3px] text-meta-secondary hover:bg-meta-hover hover:text-meta-text group-hover/row:inline-flex"
      >
        <IconPencil />
      </button>
    </span>
  );
}

/* --------------------------------------------------- Contadores animados */

const easeOutCubic = (t: number) => 1 - Math.pow(1 - t, 3);

/**
 * Suaviza los saltos de un número. Durante la reproducción el valor ya llega
 * interpolado cuadro a cuadro, así que el tween se desactiva y no arrastra.
 */
export function useTween(value: number, enabled: boolean, duration = 700): number {
  const [display, setDisplay] = useState(value);
  const fromRef = useRef(value);
  const rafRef = useRef(0);

  useEffect(() => {
    if (!enabled) {
      cancelAnimationFrame(rafRef.current);
      fromRef.current = value;
      setDisplay(value);
      return;
    }
    const from = fromRef.current;
    if (from === value) return;
    const t0 = performance.now();

    const tick = (now: number) => {
      const t = Math.min(1, (now - t0) / duration);
      const v = from + (value - from) * easeOutCubic(t);
      fromRef.current = v;
      setDisplay(v);
      if (t < 1) rafRef.current = requestAnimationFrame(tick);
      else fromRef.current = value;
    };
    rafRef.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafRef.current);
  }, [value, enabled, duration]);

  return display;
}

export function AnimatedValue({
  value,
  format,
  tween,
  className,
}: {
  value: number | null;
  format: (v: number | null) => string;
  tween: boolean;
  className?: string;
}) {
  const display = useTween(value ?? 0, tween && value !== null);
  return <span className={className}>{format(value === null ? null : display)}</span>;
}
