import { Link, useNavigate } from 'react-router';
import { useDemoSessionStore } from '@/stores/sessionStore';
import { useWorkdayStore } from '@/stores/workdayStore';
import { VisitStatusBadge } from '@/components/workday/VisitStatusBadge';
import { WorkdayMap } from '@/components/map/WorkdayMap';
import { EmptySchedule } from '@/components/representative/EmptySchedule';
import { Icon } from '@/components/ui/Icon';
import { getRouteVisits } from '@/utils/schedule';
import { formatServiceDate } from '@/utils/date';

export default function RoutePage() {
  const navigate = useNavigate();
  const user = useDemoSessionStore((state) => state.user);
  const visits = useWorkdayStore((state) => state.visits);
  const routes = useWorkdayStore((state) => state.routes);
  const office = useWorkdayStore((state) => state.office);
  const alerts = useWorkdayStore((state) => state.alerts);
  const serviceDate = useWorkdayStore((state) => state.serviceDate);
  const route = routes.find((item) => item.representativeId === user?.representativeId);
  const stops = getRouteVisits(route, visits).filter(
    (visit) => visit.representativeId === user?.representativeId,
  );

  return (
    <section className="px-5 py-6 md:px-8" aria-labelledby="route-heading">
      <h1 id="route-heading" className="text-[28px] font-semibold tracking-tight">
        Мой маршрут
      </h1>
      <p className="mt-3 text-xs text-muted">
        {formatServiceDate(serviceDate)} · {user?.city}
      </p>
      {!route ? (
        <EmptySchedule title="Маршрут ещё не построен" />
      ) : (
        <div className="md:mt-5 md:grid md:grid-cols-[minmax(0,1.2fr)_minmax(280px,1fr)] md:items-start md:gap-6">
          <div className="min-w-0 md:sticky md:top-6">
            <WorkdayMap
              visits={stops}
              routes={[route]}
              office={office}
              selectedRepresentativeId={user?.representativeId ?? undefined}
              alertVisitIds={alerts
                .filter((alert) => alert.status === 'open')
                .map((alert) => alert.visitId)}
              onOpenVisit={(id) => {
                if (
                  stops.some(
                    (visit) => visit.id === id && visit.representativeId === user?.representativeId,
                  )
                ) {
                  navigate(`/representative/visits/${id}`);
                }
              }}
              className="mt-5 h-[280px] md:mt-0 md:h-[440px]"
              label="Карта моего маршрута"
            />
            <p className="mt-2 text-[11px] leading-relaxed text-muted">
              Тестовая последовательность остановок. Линии пока соединяют точки, а не повторяют
              дорожный маршрут.
            </p>
          </div>
          <section className="mt-6 md:mt-0" aria-labelledby="stops-heading">
            <h2 id="stops-heading" className="text-lg font-semibold">
              Остановки маршрута
            </h2>
            <ol className="mt-4 space-y-3">
              {office && (
                <li className="flex items-start gap-3 rounded-xl bg-accent-soft p-3">
                  <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-accent">
                    <Icon name="grid" />
                  </span>
                  <div>
                    <h3 className="text-sm font-semibold">{office.name}</h3>
                    <p className="mt-1 text-xs text-muted">{office.address}</p>
                    <p className="mt-1 text-[11px] text-muted">Получение карт перед выездом</p>
                  </div>
                </li>
              )}
              {stops.map((visit, index) => (
                <li key={visit.id}>
                  <Link
                    to={`/representative/visits/${visit.id}`}
                    className="flex gap-3 rounded-xl border border-line p-3 hover:bg-canvas"
                  >
                    <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-ink text-xs font-semibold text-white">
                      {index + 1}
                    </span>
                    <div className="min-w-0 flex-1">
                      <h3 className="text-sm font-semibold">
                        {visit.time} · Встреча #{visit.number}
                      </h3>
                      <p className="my-2 text-xs leading-relaxed text-muted">{visit.address}</p>
                      <VisitStatusBadge visit={visit} />
                    </div>
                    <Icon name="chevron" />
                  </Link>
                </li>
              ))}
            </ol>
          </section>
        </div>
      )}
    </section>
  );
}
