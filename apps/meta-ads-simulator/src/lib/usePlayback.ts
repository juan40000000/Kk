import { useEffect, useRef } from 'react';
import { useStore } from '@/store/useStore';

/**
 * Bucle de reproducción: comprime todo el período en `durationSec` segundos.
 * Avanza con requestAnimationFrame para que los contadores no salten.
 */
export function usePlaybackLoop() {
  const playing = useStore((s) => s.playback.playing);
  const durationSec = useStore((s) => s.playback.durationSec);
  const loop = useStore((s) => s.playback.loop);
  const patchPlayback = useStore((s) => s.patchPlayback);
  const lastRef = useRef(0);

  useEffect(() => {
    if (!playing) return;
    let raf = 0;
    lastRef.current = performance.now();

    const tick = (now: number) => {
      const dt = (now - lastRef.current) / 1000;
      lastRef.current = now;
      const step = dt / Math.max(0.5, durationSec);
      const current = useStore.getState().playback.progress;
      let next = current + step;

      if (next >= 1) {
        if (loop) next = 0;
        else {
          patchPlayback({ progress: 1, playing: false });
          return;
        }
      }
      patchPlayback({ progress: next });
      raf = requestAnimationFrame(tick);
    };

    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [playing, durationSec, loop, patchPlayback]);
}

const isTyping = (el: EventTarget | null) => {
  const node = el as HTMLElement | null;
  if (!node) return false;
  const tag = node.tagName;
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || node.isContentEditable;
};

/**
 * Atajos de teclado. Todo lo "de simulación" vive acá, no en pantalla:
 *   Cmd/Ctrl+K  panel de control      Espacio  reproducir / pausar
 *   R           reiniciar             W        marca de agua
 *   L           modo limpio           G        gráfico
 */
export function useHotkeys() {
  const patchUI = useStore((s) => s.patchUI);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const s = useStore.getState();

      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        patchUI({ panelOpen: !s.ui.panelOpen });
        return;
      }
      if (isTyping(e.target) || e.metaKey || e.ctrlKey || e.altKey) return;

      switch (e.key.toLowerCase()) {
        case ' ':
          e.preventDefault();
          s.playback.playing ? s.pause() : s.play();
          break;
        case 'r':
          s.restart();
          break;
        case 'w':
          patchUI({ watermark: !s.ui.watermark });
          break;
        case 'l':
          patchUI({ cleanMode: !s.ui.cleanMode });
          break;
        case 'g':
          patchUI({ chartOpen: !s.ui.chartOpen });
          break;
        case 'escape':
          if (s.ui.panelOpen) patchUI({ panelOpen: false });
          break;
      }
    };

    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [patchUI]);
}
