import { useStore } from '@/store/useStore';
import { CURRENCIES, CURRENCY_ORDER } from '@/config/currencies';
import { DateRangePicker } from './DateRangePicker';
import { Dropdown, DropdownItem } from './Dropdown';
import { EditableText } from './primitives';
import { IconBell, IconGrid, IconHelp, IconMeta, IconSearch } from './icons';

export function TopBar() {
  const account = useStore((s) => s.scenario.account);
  const setAccountName = useStore((s) => s.setAccountName);
  const setCurrency = useStore((s) => s.setCurrency);
  const cleanMode = useStore((s) => s.ui.cleanMode);

  return (
    <header className="flex h-[52px] shrink-0 items-center gap-2 border-b border-meta-border bg-white px-3">
      <button type="button" className="rounded-full p-2 text-meta-text hover:bg-meta-hover">
        <IconGrid />
      </button>

      <div className="flex items-center gap-2 pr-1">
        <IconMeta className="text-meta-blue" size={26} />
        <span className="text-[15px] font-bold leading-none text-meta-text">
          Administrador de anuncios
        </span>
      </div>

      <div className="mx-1 h-6 w-px bg-meta-border" />

      <div className="group/row flex min-w-0 flex-col justify-center">
        <div className="max-w-[380px] text-[13px] font-semibold leading-[16px]">
          <EditableText value={account.name} onChange={setAccountName} />
        </div>
        <div className="flex items-center gap-1.5 text-2xs leading-[14px] text-meta-secondary">
          <span>Cuenta publicitaria: {account.id}</span>
          <span className="text-meta-tertiary">·</span>
          <Dropdown
            width={210}
            trigger={() => (
              <button
                type="button"
                className="rounded px-1 text-2xs text-meta-secondary hover:bg-meta-hover hover:text-meta-text"
                title="Moneda de la cuenta"
              >
                {account.currency}
              </button>
            )}
          >
            {(close) => (
              <>
                <div className="px-3 pb-1 text-2xs font-semibold uppercase tracking-wide text-meta-secondary">
                  Moneda de la cuenta
                </div>
                {CURRENCY_ORDER.map((code) => (
                  <DropdownItem
                    key={code}
                    active={code === account.currency}
                    onClick={() => {
                      setCurrency(code, true);
                      close();
                    }}
                  >
                    <span className="w-8 font-semibold">{code}</span>
                    <span className="text-meta-secondary">{CURRENCIES[code].name}</span>
                  </DropdownItem>
                ))}
                {!cleanMode && (
                  <div className="border-t border-meta-borderLight px-3 pt-1.5 text-2xs text-meta-secondary">
                    Al cambiar la moneda se convierten CPM, gasto, presupuestos y ticket al benchmark de ese mercado.
                  </div>
                )}
              </>
            )}
          </Dropdown>
        </div>
      </div>

      <div className="ml-auto flex items-center gap-2">
        <div className="relative hidden xl:block">
          <IconSearch className="absolute left-2.5 top-1/2 -translate-y-1/2 text-meta-secondary" />
          <input
            placeholder="Buscar"
            className="h-[34px] w-[190px] rounded-md border border-meta-border bg-white pl-8 pr-3 text-xs outline-none placeholder:text-meta-tertiary focus:border-meta-blue"
          />
        </div>
        <DateRangePicker />
        <button type="button" className="rounded-full p-2 text-meta-text hover:bg-meta-hover">
          <IconBell />
        </button>
        <button type="button" className="rounded-full p-2 text-meta-text hover:bg-meta-hover">
          <IconHelp />
        </button>
        <div className="ml-1 flex h-7 w-7 items-center justify-center rounded-full bg-[#E4E6EB] text-2xs font-bold text-meta-secondary">
          MN
        </div>
      </div>
    </header>
  );
}
