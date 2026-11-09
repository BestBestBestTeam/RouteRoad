import { Link } from 'react-router';
import { useDemoSessionStore } from '@/stores/sessionStore';
import { Icon } from '@/components/ui/Icon';

export default function StatusPage({ forbidden = false }: { forbidden?: boolean }) {
  const role = useDemoSessionStore((state) => state.role);
  return (
    <main className="grid min-h-dvh place-items-center px-5">
      <div className="max-w-sm text-center">
        <span className="mx-auto flex size-14 items-center justify-center rounded-2xl bg-accent">
          <Icon name="route" />
        </span>
        <p className="mt-7 text-sm text-muted">
          {forbidden ? '403 · Нет доступа' : '404 · Не найдено'}
        </p>
        <h1 className="mt-3 text-2xl font-semibold">
          {forbidden ? 'Это рабочее пространство другой роли' : 'Страница не найдена'}
        </h1>
        <p className="mt-3 text-sm leading-relaxed text-muted">
          {forbidden
            ? 'В демо доступны отдельные интерфейсы диспетчера и представителя.'
            : 'Проверьте адрес или вернитесь к своему рабочему дню.'}
        </p>
        <Link
          to={role ? `/${role}` : '/login'}
          className="mt-6 inline-flex min-h-11 items-center rounded-xl bg-accent px-5 text-sm font-semibold"
        >
          К рабочему дню
        </Link>
      </div>
    </main>
  );
}
