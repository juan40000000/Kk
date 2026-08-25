import { useStore } from '@/store/useStore';
import { useSimulation, useTableRows } from '@/store/useRows';
import { usePlaybackLoop, useHotkeys } from '@/lib/usePlayback';
import { TopBar } from '@/components/TopBar';
import { NavTabs } from '@/components/NavTabs';
import { Toolbar } from '@/components/Toolbar';
import { ChartPanel } from '@/components/ChartPanel';
import { DataTable } from '@/components/DataTable';
import { ControlPanel } from '@/components/ControlPanel';
import { HotCorner, Watermark } from '@/components/Watermark';
import { StudioBar } from '@/components/StudioBar';

export default function App() {
  const chartOpen = useStore((s) => s.ui.chartOpen);
  const cleanMode = useStore((s) => s.ui.cleanMode);

  const sim = useSimulation();
  const data = useTableRows(sim);

  usePlaybackLoop();
  useHotkeys();

  return (
    <div className="flex h-full flex-col overflow-hidden bg-white font-meta text-meta-text">
      <TopBar />
      <NavTabs />
      <Toolbar rows={data.rows} totalLabel={data.totalLabel} />
      {chartOpen && <ChartPanel sim={sim} adSetIds={data.adSetIdsInScope} />}
      <DataTable data={data} />

      <Watermark />
      <HotCorner />
      {!cleanMode && <StudioBar />}
      <ControlPanel />
    </div>
  );
}
