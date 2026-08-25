import { useStore } from '@/store/useStore';

/**
 * Marca de agua "SIMULACIÓN". Viene prendida a propósito: el hook del video
 * es la data, no hacer creer que la plata se gastó de verdad.
 */
export function Watermark() {
  const on = useStore((s) => s.ui.watermark);
  if (!on) return null;
  return (
    <div className="pointer-events-none fixed bottom-3 right-4 z-40 select-none">
      <div className="rounded-md border border-black/10 bg-black/[0.045] px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.22em] text-black/35 backdrop-blur-[1px]">
        Simulación
      </div>
    </div>
  );
}

/** Esquina discreta para abrir el panel sin atajo de teclado. */
export function HotCorner() {
  const patchUI = useStore((s) => s.patchUI);
  return (
    <button
      type="button"
      aria-label="Panel de control"
      onClick={() => patchUI({ panelOpen: true })}
      className="fixed bottom-0 left-0 z-40 h-6 w-6 cursor-default opacity-0"
    />
  );
}
