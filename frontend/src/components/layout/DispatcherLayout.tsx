import { useEffect, useRef } from 'react';
import { Link, NavLink, Outlet } from 'react-router';
import { useDemoSessionStore } from '@/stores/sessionStore';
import { useWorkdayStore } from '@/stores/workdayStore';
import { useUiStore } from '@/stores/uiStore';
import { getWorkdayCounts } from '@/utils/workday';
import { formatServiceDate } from '@/utils/date';
import { Icon } from '@/components/ui/Icon';
import { ProfileMenu } from '@/components/profile/ProfileMenu';

const navigation = [
  { to: '/dispatcher', label: 'Рабочий день', icon: 'grid', key: 'day' },
  { to: '/dispatcher/requests', label: 'Заявки', icon: 'pin', key: 'total' },
  {
    to: '/dispatcher/representatives',
    label: 'Представители',
    icon: 'users',
    key: 'representatives',
  },
  { to: '/dispatcher/alerts', label: 'Алерты', icon: 'alert', key: 'attention' },
  { to: '/dispatcher/history', label: 'История', icon: 'clock', key: 'history' },
] as const;

export function DispatcherLayout() {
  const visits = useWorkdayStore((s) => s.visits);
  const alerts = useWorkdayStore((s) => s.alerts);
  const representatives = useWorkdayStore((s) => s.representatives);
  const serviceDate = useWorkdayStore((s) => s.serviceDate);
  const user = useDemoSessionStore((s) => s.user);
  const { menuOpen, setMenuOpen, sidebarCollapsed, toggleSidebar } = useUiStore();
  const menuButton = useRef<HTMLButtonElement>(null);
  const counts = getWorkdayCounts(visits, alerts);
  useEffect(() => {
    if (!menuOpen) return;
    const close = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setMenuOpen(false);
        menuButton.current?.focus();
      }
    };
    window.addEventListener('keydown', close);
    return () => window.removeEventListener('keydown', close);
  }, [menuOpen, setMenuOpen]);
  return (
    <div className="min-h-dvh bg-canvas">
      <a
        href="#main"
        className="sr-only fixed left-4 top-4 z-50 rounded-xl bg-ink px-4 py-3 text-white focus:not-sr-only"
      >
        К содержимому
      </a>
      {counts.attention > 0 && (
        <span id="navigation-alert-status" className="sr-only">
          Открытых алертов: {counts.attention}
        </span>
      )}
      {menuOpen && (
        <button
          type="button"
          aria-label="Закрыть меню"
          className="fixed inset-0 z-30 bg-black/25 md:hidden"
          onClick={() => {
            setMenuOpen(false);
            menuButton.current?.focus();
          }}
        />
      )}
      <aside
        id="dispatcher-sidebar"
        aria-label="Боковая панель"
        className={`fixed inset-y-0 left-0 z-40 flex w-60 flex-col border-r border-line bg-white transition-[width,transform] md:visible md:translate-x-0 ${sidebarCollapsed ? 'md:w-[76px]' : 'md:w-60'} ${menuOpen ? 'visible translate-x-0' : 'invisible -translate-x-full'}`}
      >
        <Link
          to="/dispatcher"
          aria-label="RouteRoad — рабочий день"
          onClick={() => setMenuOpen(false)}
          className="flex min-h-[88px] items-center gap-3 px-5"
        >
          <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-accent">
            <Icon name="route" />
          </span>
          <span className={sidebarCollapsed ? 'md:hidden' : ''}>
            <span className="block text-xl font-bold tracking-tight">RouteRoad</span>
            <span className="mt-1 block text-[10px] text-muted">Выездные встречи</span>
          </span>
        </Link>
        <nav aria-label="Разделы диспетчера" className="space-y-2 px-3">
          {navigation.map((item) => {
            const badge =
              item.key === 'total'
                ? counts.total
                : item.key === 'representatives'
                  ? representatives.length
                  : item.key === 'attention' && counts.attention > 0
                    ? counts.attention
                    : null;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/dispatcher'}
                title={item.label}
                aria-label={item.label}
                aria-describedby={
                  item.key === 'attention' && counts.attention > 0
                    ? 'navigation-alert-status'
                    : undefined
                }
                onClick={() => setMenuOpen(false)}
                className={({ isActive }) =>
                  `grid min-h-12 grid-cols-[20px_minmax(0,1fr)_28px] items-center gap-2 rounded-xl px-3 text-[13px] transition-colors ${sidebarCollapsed ? 'md:grid-cols-1 md:justify-items-center' : ''} ${isActive ? 'bg-accent-soft font-semibold text-ink' : 'text-muted hover:bg-canvas'}`
                }
              >
                <span className="relative inline-flex size-5" aria-hidden="true">
                  <Icon name={item.icon} className="size-5" />
                  {item.key === 'attention' && counts.attention > 0 && sidebarCollapsed && (
                    <span
                      data-alert-indicator="sidebar"
                      className="absolute -right-1 -top-1 hidden size-2.5 rounded-full bg-danger ring-2 ring-white md:block"
                    />
                  )}
                </span>
                <span className={`min-w-0 ${sidebarCollapsed ? 'md:sr-only' : ''}`}>
                  {item.label}
                </span>
                {badge !== null && (
                  <span
                    data-navigation-count={item.key}
                    className={`flex h-7 w-7 items-center justify-center rounded-lg bg-canvas text-xs tabular-nums ${item.key === 'attention' ? 'text-danger' : ''} ${sidebarCollapsed ? 'md:sr-only' : ''}`}
                  >
                    {badge}
                  </span>
                )}
              </NavLink>
            );
          })}
        </nav>
        <div className="mt-auto">
          <div className={`p-4 text-xs text-muted ${sidebarCollapsed ? 'md:hidden' : ''}`}>
            <div className="rounded-xl bg-canvas p-3">
              <p className="font-medium text-ink">Локальное демо</p>
              <p className="mt-2 text-[10px] leading-relaxed">
                Синтетические данные · изменения до перезагрузки
              </p>
            </div>
          </div>
          <div
            className={`hidden border-t border-line p-3 md:flex ${sidebarCollapsed ? 'justify-center' : 'justify-end'}`}
          >
            <button
              type="button"
              aria-label={sidebarCollapsed ? 'Развернуть боковое меню' : 'Свернуть боковое меню'}
              title={sidebarCollapsed ? 'Развернуть боковое меню' : 'Свернуть боковое меню'}
              aria-controls="dispatcher-sidebar"
              aria-expanded={!sidebarCollapsed}
              onClick={toggleSidebar}
              className="flex size-10 items-center justify-center rounded-xl text-muted hover:bg-canvas"
            >
              <Icon
                name={sidebarCollapsed ? 'sidebar-expand' : 'sidebar-collapse'}
                className="size-6"
              />
            </button>
          </div>
        </div>
      </aside>
      <div className={`transition-[margin] ${sidebarCollapsed ? 'md:ml-[76px]' : 'md:ml-60'}`}>
        <header className="flex h-[72px] items-center gap-3 border-b border-line bg-white px-4 md:gap-5 md:px-6">
          <button
            ref={menuButton}
            type="button"
            aria-label="Открыть меню"
            aria-controls="dispatcher-sidebar"
            aria-expanded={menuOpen}
            aria-describedby={counts.attention > 0 ? 'navigation-alert-status' : undefined}
            onClick={() => setMenuOpen(!menuOpen)}
            className="relative flex size-10 shrink-0 items-center justify-center rounded-xl bg-canvas md:hidden"
          >
            <Icon name="list" />
            {counts.attention > 0 && (
              <span
                data-alert-indicator="mobile-menu"
                aria-hidden="true"
                className="absolute right-1 top-1 size-2.5 rounded-full bg-danger ring-2 ring-white"
              />
            )}
          </button>
          <span className="hidden items-center gap-2 text-sm min-[420px]:flex">
            <Icon name="pin" className="size-[18px]" />
            {user?.city}
          </span>
          <span className="hidden items-center gap-2 text-sm lg:flex">
            <Icon name="calendar" className="size-[18px]" />
            {formatServiceDate(serviceDate)}
          </span>
          <span className="ml-auto rounded-md bg-accent-soft px-2 py-1 text-[10px] font-medium text-[#786715]">
            Тестовый режим
          </span>
          <Link
            to="/dispatcher/alerts"
            className="relative hidden rounded-lg p-2 sm:block"
            aria-label={`Предупреждения: ${counts.attention}`}
          >
            <Icon name="bell" />
            {counts.attention > 0 && (
              <span className="absolute right-1 top-1 size-1.5 rounded-full bg-danger" />
            )}
          </Link>
          <ProfileMenu />
        </header>
        <main id="main">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
