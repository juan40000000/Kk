import { useStore } from '@/store/useStore';
import { dayCount } from '@/engine/dates';
import { IconPause, IconPlay, IconRestart } from './icons';

/**
 * Barra de estudio: SOLO se ve con el modo limpio apagado. Es el atajo para
 * preparar la toma sin abrir el panel entero.
 */
export function StudioBar() {
  const playback = useStore((s) => s.playback);
  const range = useStore((s) => s.scenario.dateRange);
  const ui = useStore((s) => s.ui);
  const play = useStore((s) => s.play);
  const pause = useStore((s) => s.pause);
  const restart = useStore((s) => s.restart);
  const patchPlayback = useStore((s) => s.patchPlayback);
  const patchUI = useStore((s) => s.patchUI);

  const days = dayCount(range.start, range.end);
  const currentDay = Math.max(1, Math.ceil(playback.progress * days));

  return (
    <div className="fixed bottom-4 left-1/2 z-40 flex -translate-x-1/2 items-center gap-3 rounded-full border border-black/10 bg-[#1c1e21] px-3 py-2 text-white shadow-[0_8px_28px_rgba(0,0,0,.35)]">
      <button
        type="button"
        onClick={() => (playback.playing ? pause() : play())}
        className="flex h-8 w-8 items-center justify-center rounded-full bg-meta-blue hover:bg-[#0059DB]"
        title="Espacio"
      >
        {playback.playing ? <IconPause size={14} /> : <IconPlay size={14} />}
      </button>
      <button
        type="button"
        onClick={restart}
        title="R"
        className="flex h-8 w-8 items-center justify-center rounded-full bg-white/10 hover:bg-white/20"
      >
        <IconRestart size={14} />
      </button>

      <input
        type="range"
        min={0}
        max={1}
        step={0.002}
        value={playback.progress}
        onChange={(e) => patchPlayback({ progress: Number(e.target.value), playing: false })}
        className="w-[220px] accent-[#0866FF]"
      />

      <span className="num w-[86px] text-2xs text-white/70">
        día {currentDay}/{days}
      </span>

      <div className="h-5 w-px bg-white/20" />

      <button
        type="button"
        onClick={() => patchUI({ cleanMode: true })}
        className="rounded-full bg-white/10 px-3 py-1 text-2xs font-semibold hover:bg-white/20"
        title="L"
      >
        Modo limpio
      </button>
      <button
        type="button"
        onClick={() => patchUI({ panelOpen: !ui.panelOpen })}
        className="rounded-full bg-white/10 px-3 py-1 text-2xs font-semibold hover:bg-white/20"
        title="Ctrl/Cmd + K"
      >
        Panel
      </button>
    </div>
  );
}
