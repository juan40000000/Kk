/** Íconos inline, trazo y tamaños calcados de los del Ads Manager. */
type P = { className?: string; size?: number };

const base = (size: number) => ({
  width: size,
  height: size,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
});

export const IconSearch = ({ className, size = 16 }: P) => (
  <svg {...base(size)} className={className}>
    <circle cx="11" cy="11" r="7" />
    <path d="m20 20-3.5-3.5" />
  </svg>
);

export const IconChevronDown = ({ className, size = 16 }: P) => (
  <svg {...base(size)} className={className}>
    <path d="m6 9 6 6 6-6" />
  </svg>
);

export const IconChevronRight = ({ className, size = 16 }: P) => (
  <svg {...base(size)} className={className}>
    <path d="m9 6 6 6-6 6" />
  </svg>
);

export const IconPlus = ({ className, size = 16 }: P) => (
  <svg {...base(size)} className={className}>
    <path d="M12 5v14M5 12h14" />
  </svg>
);

export const IconPencil = ({ className, size = 14 }: P) => (
  <svg {...base(size)} className={className}>
    <path d="M12 20h9" />
    <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" />
  </svg>
);

export const IconDuplicate = ({ className, size = 14 }: P) => (
  <svg {...base(size)} className={className}>
    <rect x="9" y="9" width="11" height="11" rx="2" />
    <path d="M5 15V5a2 2 0 0 1 2-2h10" />
  </svg>
);

export const IconChartBars = ({ className, size = 14 }: P) => (
  <svg {...base(size)} className={className}>
    <path d="M4 20V10M10 20V4M16 20v-7M22 20H2" />
  </svg>
);

export const IconTrash = ({ className, size = 14 }: P) => (
  <svg {...base(size)} className={className}>
    <path d="M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14" />
  </svg>
);

export const IconColumns = ({ className, size = 16 }: P) => (
  <svg {...base(size)} className={className}>
    <rect x="3" y="4" width="18" height="16" rx="2" />
    <path d="M9 4v16M15 4v16" />
  </svg>
);

export const IconBreakdown = ({ className, size = 16 }: P) => (
  <svg {...base(size)} className={className}>
    <path d="M12 3a9 9 0 1 0 9 9h-9Z" />
    <path d="M12 3v9h9a9 9 0 0 0-9-9Z" />
  </svg>
);

export const IconExport = ({ className, size = 16 }: P) => (
  <svg {...base(size)} className={className}>
    <path d="M12 3v12M8 7l4-4 4 4M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
  </svg>
);

export const IconImport = ({ className, size = 16 }: P) => (
  <svg {...base(size)} className={className}>
    <path d="M12 15V3M8 11l4 4 4-4M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
  </svg>
);

export const IconCalendar = ({ className, size = 16 }: P) => (
  <svg {...base(size)} className={className}>
    <rect x="3" y="5" width="18" height="16" rx="2" />
    <path d="M8 3v4M16 3v4M3 10h18" />
  </svg>
);

export const IconPlay = ({ className, size = 16 }: P) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" className={className}>
    <path d="M8 5.5v13l11-6.5z" />
  </svg>
);

export const IconPause = ({ className, size = 16 }: P) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" className={className}>
    <path d="M7 5h4v14H7zM13 5h4v14h-4z" />
  </svg>
);

export const IconRestart = ({ className, size = 16 }: P) => (
  <svg {...base(size)} className={className}>
    <path d="M3 12a9 9 0 1 0 3-6.7L3 8" />
    <path d="M3 3v5h5" />
  </svg>
);

export const IconX = ({ className, size = 16 }: P) => (
  <svg {...base(size)} className={className}>
    <path d="M6 6l12 12M18 6 6 18" />
  </svg>
);

export const IconFilter = ({ className, size = 16 }: P) => (
  <svg {...base(size)} className={className}>
    <path d="M3 5h18l-7 8v6l-4 2v-8Z" />
  </svg>
);

export const IconEllipsis = ({ className, size = 16 }: P) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" className={className}>
    <circle cx="5" cy="12" r="1.8" />
    <circle cx="12" cy="12" r="1.8" />
    <circle cx="19" cy="12" r="1.8" />
  </svg>
);

export const IconAB = ({ className, size = 16 }: P) => (
  <svg {...base(size)} className={className}>
    <path d="M4 18 7.5 7l3.5 11M5 15h5" />
    <path d="M15 18V7h3a2.5 2.5 0 0 1 0 5h-3m0 0h3.5a3 3 0 0 1 0 6H15" />
  </svg>
);

export const IconRules = ({ className, size = 16 }: P) => (
  <svg {...base(size)} className={className}>
    <path d="M4 6h10M4 12h16M4 18h7" />
    <circle cx="18" cy="6" r="2" />
    <circle cx="15" cy="18" r="2" />
  </svg>
);

export const IconMeta = ({ className, size = 22 }: P) => (
  <svg width={size} height={size} viewBox="0 0 36 24" fill="currentColor" className={className}>
    <path d="M6.6 16.6c1 0 1.8-.5 3.2-2.8.9-1.4 1.9-3.3 2.9-5.3l.9 2.1c-1 1.9-1.9 3.4-2.7 4.6-1.6 2.4-3 3.4-5 3.4C3 18.6.8 15.9.8 11.9c0-4.6 2.6-7.9 5.9-7.9 1.8 0 3.2.9 4.6 2.7-.6.8-1.2 1.6-1.7 2.4C8.6 7.7 7.6 7 6.6 7c-1.7 0-3.1 2-3.1 4.9 0 2.8 1.2 4.7 3.1 4.7ZM17 8.4l-.8-2.4c1.4-1.4 2.8-2 4.5-2 3.1 0 5.4 2.3 8 7.3l.6 1.2c2 3.9 2.7 5 3.9 5 1.1 0 1.7-1 1.7-2.9 0-2.2-.6-3.6-2.2-3.6-.5 0-1 .1-1.7.5l-1-2.5c.9-.5 1.9-.8 2.9-.8 3.1 0 5.1 2.4 5.1 6.3 0 3.8-1.9 6.1-5 6.1-2.5 0-4.1-1.4-6.4-5.6l-1.4-2.7c-1.9-3.6-3.1-4.9-4.6-4.9-1 0-2 .6-3.6 2.3Z" />
  </svg>
);

export const IconHelp = ({ className, size = 18 }: P) => (
  <svg {...base(size)} className={className}>
    <circle cx="12" cy="12" r="9" />
    <path d="M9.6 9.5a2.5 2.5 0 1 1 3.4 2.3c-.7.3-1 .9-1 1.7v.3" />
    <circle cx="12" cy="17.2" r=".9" fill="currentColor" stroke="none" />
  </svg>
);

export const IconBell = ({ className, size = 18 }: P) => (
  <svg {...base(size)} className={className}>
    <path d="M18 8a6 6 0 1 0-12 0c0 6-2 7-2 7h16s-2-1-2-7" />
    <path d="M10.5 20a2 2 0 0 0 3 0" />
  </svg>
);

export const IconGrid = ({ className, size = 18 }: P) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" className={className}>
    <circle cx="6" cy="6" r="2" />
    <circle cx="12" cy="6" r="2" />
    <circle cx="18" cy="6" r="2" />
    <circle cx="6" cy="12" r="2" />
    <circle cx="12" cy="12" r="2" />
    <circle cx="18" cy="12" r="2" />
    <circle cx="6" cy="18" r="2" />
    <circle cx="12" cy="18" r="2" />
    <circle cx="18" cy="18" r="2" />
  </svg>
);
