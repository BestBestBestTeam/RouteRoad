import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router';
import { useDemoSessionStore } from '@/stores/sessionStore';
import type { DemoRole } from '@/types/session';
import { DEMO_USERS } from '@/config/demoUsers';
import { Avatar } from '@/components/ui/Avatar';
import { Button } from '@/components/ui/Button';
import { Icon } from '@/components/ui/Icon';

export default function LoginPage() {
  const activeRole = useDemoSessionStore((state) => state.role);
  const enter = useDemoSessionStore((state) => state.enter);
  const [role, setRole] = useState<DemoRole>('dispatcher');
  const navigate = useNavigate();

  if (activeRole) return <Navigate to={`/${activeRole}`} replace />;

  return (
    <main className="grid min-h-dvh place-items-center px-5 py-10">
      <div className="w-full max-w-[440px]">
        <div className="mb-10 flex items-center justify-center gap-3">
          <span className="flex size-11 items-center justify-center rounded-2xl bg-accent">
            <Icon name="route" />
          </span>
          <div>
            <p className="text-2xl font-bold tracking-tight">RouteRoad</p>
            <p className="mt-0.5 text-xs text-muted">Выездные встречи</p>
          </div>
        </div>
        <section className="rounded-3xl border border-line bg-white p-6 shadow-[0_12px_60px_-32px_#737780] sm:p-8">
          <span className="rounded-md bg-accent-soft px-2 py-1 text-[10px] font-semibold tracking-wider">
            UI · ДЕМОНСТРАЦИЯ
          </span>
          <h1 className="mt-5 text-[28px] font-semibold leading-tight tracking-tight">
            Ваш рабочий день
          </h1>
          <p className="mt-3 text-sm leading-relaxed text-muted">
            Выберите интерфейс, чтобы посмотреть встречи и маршруты на примере одного дня в
            Новосибирске.
          </p>
          <form
            className="mt-7"
            onSubmit={(event) => {
              event.preventDefault();
              enter(role);
              navigate(`/${role}`, { replace: true });
            }}
          >
            <fieldset className="space-y-3">
              <legend className="mb-3 text-xs font-medium text-muted">Тестовый пользователь</legend>
              {(
                [
                  {
                    role: 'dispatcher',
                    title: 'Диспетчер',
                    description: 'Встречи, представители и маршруты',
                  },
                  {
                    role: 'representative',
                    title: 'Представитель',
                    description: 'Мой день и карточки встреч',
                  },
                ] as const
              ).map((option) => (
                <label
                  key={option.role}
                  className={`flex cursor-pointer items-center gap-3 rounded-2xl border p-4 transition-colors focus-within:ring-2 focus-within:ring-info ${role === option.role ? 'border-accent bg-accent-soft/40' : 'border-line hover:bg-canvas'}`}
                >
                  <input
                    type="radio"
                    name="role"
                    value={option.role}
                    checked={role === option.role}
                    onChange={() => setRole(option.role)}
                    className="size-4 accent-[#d6b000]"
                  />
                  <Avatar
                    initials={DEMO_USERS[option.role].initials}
                    tone={option.role === 'dispatcher' ? 'gray' : 'yellow'}
                  />
                  <span className="min-w-0">
                    <span className="block text-sm font-semibold">{option.title}</span>
                    <span className="mt-1 block text-xs text-muted">
                      {DEMO_USERS[option.role].name}
                    </span>
                    <span className="mt-1.5 block text-[10px] text-muted">
                      {option.description}
                    </span>
                  </span>
                </label>
              ))}
            </fieldset>
            <Button type="submit" className="mt-6 w-full">
              Открыть интерфейс
              <Icon name="chevron" className="size-4" />
            </Button>
          </form>
          <p className="mt-5 text-[11px] leading-relaxed text-muted">
            Тестовый вход — временный механизм разработки. Серверная авторизация ещё не подключена;
            все данные синтетические.
          </p>
        </section>
        <p className="mt-6 text-center text-xs text-muted">
          Учебный проект · тестовые пользователи
        </p>
      </div>
    </main>
  );
}
