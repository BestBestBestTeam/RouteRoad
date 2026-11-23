import { Link } from 'react-router';
import { useDemoSessionStore } from '@/stores/sessionStore';
import { useWorkdayStore } from '@/stores/workdayStore';
import { VisitStatusBadge } from '@/components/workday/VisitStatusBadge';
import { isActiveVisit } from '@/utils/workday';
import { getRouteVisits } from '@/utils/schedule';
import { formatServiceDate } from '@/utils/date';
import { Icon } from '@/components/ui/Icon';
import { EmptySchedule } from '@/components/representative/EmptySchedule';

export default function SchedulePage() {
  const user = useDemoSessionStore((state) => state.user);
  const visits = useWorkdayStore((state) => state.visits);
  const routes = useWorkdayStore((state) => state.routes);
  const serviceDate = useWorkdayStore((state) => state.serviceDate);
  const route = routes.find((item) => item.representativeId === user?.representativeId);
  const ownVisits = getRouteVisits(route, visits);
  const completed = ownVisits.filter((visit) => visit.status === 'completed').length;
  const [nextVisit, ...upcoming] = ownVisits.filter(isActiveVisit);

  return (
    <section className="px-5 py-6 md:px-8" aria-labelledby="schedule-heading">
      <header>
        <div>
          <h1
            id="schedule-heading"
            className="text-[28px] font-semibold leading-tight tracking-tight"
          >
            Мой день
          </h1>
          <p className="mt-3 text-xs leading-relaxed text-muted">
            {formatServiceDate(serviceDate)} · {user?.name}
          </p>
        </div>
      </header>
      {!route ? (
        <EmptySchedule />
      ) : (
        <>
          <section
            className="mt-5 rounded-2xl bg-canvas px-4 py-3.5"
            aria-label="Выполнение рабочего дня"
          >
            <div className="flex items-center justify-between gap-3">
              <p className="text-sm font-semibold">
                {completed} из {ownVisits.length} встреч завершены
              </p>
              <span className="size-2 rounded-full bg-success" aria-hidden="true" />
            </div>
            <div
              className="mt-4 h-[5px] overflow-hidden rounded-full bg-[#e4e6e9]"
              role="progressbar"
              aria-label="Завершённые встречи"
              aria-valuemin={0}
              aria-valuemax={Math.max(ownVisits.length, 1)}
              aria-valuenow={completed}
            >
              <div
                className="h-full rounded-full bg-accent transition-[width]"
                style={{ width: `${ownVisits.length ? (completed / ownVisits.length) * 100 : 0}%` }}
              />
            </div>
          </section>
          <div className="md:grid md:grid-cols-2 md:gap-6">
            {nextVisit ? (
              <section className="mt-[18px] rounded-[20px] bg-accent p-[18px]">
                <div className="flex items-center justify-between gap-2">
                  <h2 className="text-[10px] font-semibold tracking-[1px] text-[#5a4f17]">
                    СЛЕДУЮЩАЯ ВСТРЕЧА
                  </h2>
                  <VisitStatusBadge visit={nextVisit} />
                </div>
                <p className="mt-2 text-[28px] font-semibold leading-tight tracking-tight">
                  {nextVisit.time}–{nextVisit.endTime}
                </p>
                <h3 className="mt-2 text-lg font-semibold">{nextVisit.address}</h3>
                <p className="mt-2 text-xs leading-relaxed text-[#665b22]">
                  {nextVisit.client} · {nextVisit.product}
                </p>
                <Link
                  to={`/representative/visits/${nextVisit.id}`}
                  className="mt-4 flex min-h-11 items-center justify-center rounded-xl bg-ink px-4 py-2.5 text-sm font-semibold text-white hover:bg-[#343436]"
                >
                  Открыть встречу
                </Link>
              </section>
            ) : (
              <section className="mt-5 rounded-2xl bg-accent p-5">
                <Icon name="check" />
                <h2 className="mt-3 text-xl font-semibold">На сегодня всё</h2>
                <p className="mt-2 text-sm">Все встречи маршрута завершены.</p>
              </section>
            )}
            <section className="mt-6 rounded-2xl border border-line p-5">
              <h2 className="text-lg font-semibold">
                {nextVisit ? 'Остальная часть дня' : 'Итоги маршрута'}
              </h2>
              <p className="mt-3 text-sm leading-relaxed text-muted">
                {nextVisit
                  ? `После ближайшей встречи осталось ещё ${upcoming.length}. Полный порядок остановок и карта доступны в маршруте.`
                  : 'Все встречи завершены. Посмотреть остановки и результаты можно в маршруте.'}
              </p>
              <Link
                to="/representative/route"
                className="mt-4 inline-flex min-h-11 items-center rounded-xl bg-canvas px-4 text-sm font-semibold"
              >
                Открыть маршрут
              </Link>
            </section>
          </div>
        </>
      )}
    </section>
  );
}
