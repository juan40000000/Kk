import { useStore, type Level } from '@/store/useStore';
import { cn } from '@/lib/cn';
import { IconChevronRight, IconX } from './icons';

const TAB_LABEL: Record<Level, string> = {
  campaigns: 'Campañas',
  adsets: 'Conjuntos de anuncios',
  ads: 'Anuncios',
};

export function NavTabs() {
  const ui = useStore((s) => s.ui);
  const scenario = useStore((s) => s.scenario);
  const setLevel = useStore((s) => s.setLevel);
  const patchUI = useStore((s) => s.patchUI);

  const adSetsInScope = ui.campaignFilter
    ? scenario.adSets.filter((a) => a.campaignId === ui.campaignFilter)
    : scenario.adSets;
  const adsInScope = ui.adSetFilter
    ? scenario.ads.filter((a) => a.adSetId === ui.adSetFilter)
    : scenario.ads.filter((a) => adSetsInScope.some((s) => s.id === a.adSetId));

  const counts: Record<Level, number> = {
    campaigns: scenario.campaigns.length,
    adsets: adSetsInScope.length,
    ads: adsInScope.length,
  };

  const campaign = scenario.campaigns.find((c) => c.id === ui.campaignFilter);
  const adSet = scenario.adSets.find((a) => a.id === ui.adSetFilter);

  return (
    <div className="shrink-0 border-b border-meta-border bg-white px-3">
      <div className="flex items-end gap-1">
        {(Object.keys(TAB_LABEL) as Level[]).map((level) => {
          const active = ui.level === level;
          return (
            <button
              key={level}
              type="button"
              onClick={() => setLevel(level)}
              className={cn(
                'relative flex items-center gap-1.5 px-3 pb-2.5 pt-3 text-[13px] transition-colors',
                active
                  ? 'font-semibold text-meta-blueLink'
                  : 'font-medium text-meta-secondary hover:text-meta-text',
              )}
            >
              <span>{TAB_LABEL[level]}</span>
              <span
                className={cn(
                  'rounded-full px-1.5 py-[1px] text-2xs font-semibold',
                  active ? 'bg-meta-blueHover text-meta-blueLink' : 'bg-[#F0F2F5] text-meta-secondary',
                )}
              >
                {counts[level]}
              </span>
              {active && (
                <span className="absolute inset-x-0 bottom-0 h-[3px] rounded-t bg-meta-blue" />
              )}
            </button>
          );
        })}
      </div>

      {(campaign || adSet) && (
        <div className="flex items-center gap-1.5 pb-2 text-2xs text-meta-secondary">
          <button
            type="button"
            className="rounded px-1 py-[2px] hover:bg-meta-hover hover:text-meta-text"
            onClick={() => patchUI({ campaignFilter: null, adSetFilter: null, level: 'campaigns' })}
          >
            Todas las campañas
          </button>
          {campaign && (
            <>
              <IconChevronRight size={12} className="text-meta-tertiary" />
              <button
                type="button"
                className="max-w-[300px] truncate rounded px-1 py-[2px] hover:bg-meta-hover hover:text-meta-text"
                onClick={() => patchUI({ adSetFilter: null, level: 'adsets' })}
              >
                {campaign.name}
              </button>
            </>
          )}
          {adSet && (
            <>
              <IconChevronRight size={12} className="text-meta-tertiary" />
              <span className="max-w-[300px] truncate px-1 font-semibold text-meta-text">
                {adSet.name}
              </span>
            </>
          )}
          <button
            type="button"
            title="Quitar filtros"
            onClick={() => patchUI({ campaignFilter: null, adSetFilter: null })}
            className="ml-1 rounded-full p-[3px] hover:bg-meta-hover hover:text-meta-text"
          >
            <IconX size={12} />
          </button>
        </div>
      )}
    </div>
  );
}
