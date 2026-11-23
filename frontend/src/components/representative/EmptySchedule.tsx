import { Link } from 'react-router';
import { Icon } from '@/components/ui/Icon';

export function EmptySchedule({ title = 'Расписание пока не готово' }: { title?: string }) {
  return (
    <section className="mt-6 rounded-2xl bg-canvas px-5 py-8 text-center">
      <span className="mx-auto flex size-12 items-center justify-center rounded-2xl bg-accent-soft">
        <Icon name="route" />
      </span>
      <h2 className="mt-4 text-lg font-semibold">{title}</h2>
      <p className="mt-3 text-sm leading-relaxed text-muted">
        После загрузки заявок и построения маршрутов диспетчером здесь появятся ваши встречи.
      </p>
      <Link
        to="/representative/profile"
        className="mt-5 inline-flex min-h-11 items-center rounded-xl border border-line bg-white px-4 text-sm font-medium"
      >
        Открыть профиль
      </Link>
    </section>
  );
}
