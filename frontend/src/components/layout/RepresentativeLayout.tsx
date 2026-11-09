import { NavLink, Outlet, useLocation } from 'react-router';
import { Icon } from '@/components/ui/Icon';
import { useWorkdayStore } from '@/stores/workdayStore';
import { ProfileMenu } from '@/components/profile/ProfileMenu';

const tabs = [
  { to: '/representative', label: 'Мой день', icon: 'list' },
  { to: '/representative/route', label: 'Маршрут', icon: 'route' },
  { to: '/representative/profile', label: 'Профиль', icon: 'user' },
];

export function RepresentativeLayout() {
  const location = useLocation();
  const notice = useWorkdayStore((state) => state.notice);
  const error = useWorkdayStore((state) => state.error);
  const setNotice = useWorkdayStore((state) => state.setNotice);
  const isVisit = location.pathname.startsWith('/representative/visits/');
  return (
    <div
      className={`mx-auto min-h-dvh w-full max-w-[1200px] bg-white shadow-sm ${isVisit ? 'pb-6' : 'pb-24 md:pb-8'}`}
    >
      <div className="bg-accent-soft/65 px-5 py-2 text-center text-[10px] text-[#786715]">
        Тестовый режим
      </div>
      <header className="flex items-center justify-between gap-4 border-b border-line px-5 py-2 md:px-8">
        <span className="text-sm font-bold">RouteRoad</span>
        <nav
          aria-label="Разделы представителя на планшете"
          className="hidden items-center gap-2 md:flex"
        >
          {tabs.map((tab) => (
            <NavLink
              key={tab.to}
              to={tab.to}
              end={tab.to === '/representative'}
              className={({ isActive }) =>
                `rounded-xl px-4 py-3 text-sm ${isActive ? 'bg-accent-soft font-semibold' : 'text-muted hover:bg-canvas'}`
              }
            >
              {tab.label}
            </NavLink>
          ))}
        </nav>
        <ProfileMenu />
      </header>
      {notice && (
        <div
          role="status"
          className="mx-5 mt-4 flex items-start gap-2 rounded-xl border border-accent bg-accent-soft/70 px-3 py-3 text-xs leading-relaxed"
        >
          <p className="min-w-0 flex-1">{notice}</p>
          <button
            type="button"
            aria-label="Закрыть сообщение"
            onClick={() => setNotice(null)}
            className="flex size-8 shrink-0 items-center justify-center rounded-lg hover:bg-white"
          >
            <Icon name="close" />
          </button>
        </div>
      )}
      {error && (
        <p
          role="alert"
          className="mx-5 mt-4 rounded-xl border border-danger/20 bg-red-50 p-3 text-xs leading-relaxed text-danger"
        >
          {error} Повторите действие.
        </p>
      )}
      <main>
        <Outlet />
      </main>
      {!isVisit && (
        <nav
          aria-label="Разделы представителя"
          className="fixed inset-x-0 bottom-0 z-20 flex w-full justify-around border-t border-line bg-white/95 px-2 pt-2 pb-[max(12px,env(safe-area-inset-bottom))] backdrop-blur-sm md:hidden"
        >
          {tabs.map((tab) => (
            <NavLink
              key={tab.to}
              to={tab.to}
              end={tab.to === '/representative'}
              className={({ isActive }) =>
                `group flex min-h-14 w-1/3 flex-col items-center gap-1.5 rounded-xl px-3 text-[10px] ${isActive ? 'font-semibold text-ink' : 'text-muted'}`
              }
            >
              <span className="inline-flex h-7 w-[50px] items-center justify-center rounded-xl group-aria-[current=page]:bg-accent-soft">
                <Icon name={tab.icon} />
              </span>
              {tab.label}
            </NavLink>
          ))}
        </nav>
      )}
    </div>
  );
}
