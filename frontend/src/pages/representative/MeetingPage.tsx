import { Link, useNavigate, useParams } from 'react-router';
import { useDemoSessionStore } from '@/stores/sessionStore';
import { useWorkdayStore } from '@/stores/workdayStore';
import { VisitStatusBadge } from '@/components/workday/VisitStatusBadge';
import { Avatar } from '@/components/ui/Avatar';
import { Icon } from '@/components/ui/Icon';
import { WorkdayMap } from '@/components/map/WorkdayMap';
import { DemoClientContact, VisitActionPanel } from '@/components/representative/VisitActionPanel';

export default function MeetingPage() {
  const { orderId } = useParams<{ orderId: string }>();
  const navigate = useNavigate();
  const user = useDemoSessionStore((state) => state.user);
  const visits = useWorkdayStore((state) => state.visits);
  const routes = useWorkdayStore((state) => state.routes);
  const office = useWorkdayStore((state) => state.office);
  const alerts = useWorkdayStore((state) => state.alerts);
  const visit = useWorkdayStore((state) => state.visits.find((item) => item.id === orderId));

  if (!visit || !user?.representativeId || visit.representativeId !== user.representativeId) {
    return (
      <section className="px-5 pt-6 pb-6">
        <Link
          to="/representative"
          className="inline-flex min-h-11 items-center gap-2 rounded-xl text-sm"
        >
          <Icon name="back" />
          Мой день
        </Link>
        <h1 className="mt-6 text-2xl font-semibold">Встреча недоступна</h1>
        <p className="mt-3 text-sm leading-relaxed text-muted">
          Эта встреча не назначена вам. Выберите встречу из своего расписания.
        </p>
      </section>
    );
  }

  const clientInitials = visit.client
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0))
    .join('');

  return (
    <section className="px-5 pt-6 pb-6 md:px-8" aria-labelledby="visit-heading">
      <header className="flex items-center gap-4">
        <Link
          to="/representative"
          aria-label="Вернуться к моему дню"
          className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-canvas"
        >
          <Icon name="back" />
        </Link>
        <h1 id="visit-heading" className="text-[20px] font-semibold tracking-[-0.3px]">
          Встреча #{visit.number}
        </h1>
      </header>

      <div className="mt-5 flex items-center justify-between gap-3">
        <VisitStatusBadge visit={visit} />
        <p className="text-[11px] text-muted">
          Окно {visit.time}–{visit.endTime}
        </p>
      </div>

      <div className="md:mt-5 md:grid md:grid-cols-[minmax(0,1.2fr)_minmax(280px,1fr)] md:items-start md:gap-6">
        <WorkdayMap
          className="mt-4 h-[260px] rounded-xl md:mt-0 md:h-[480px]"
          visits={visits.filter((item) => item.representativeId === user.representativeId)}
          routes={routes.filter((item) => item.representativeId === user.representativeId)}
          office={office}
          selectedRepresentativeId={user.representativeId}
          selectedVisitId={visit.id}
          alertVisitIds={alerts
            .filter((alert) => alert.status === 'open')
            .map((alert) => alert.visitId)}
          onOpenVisit={(id) => {
            if (
              visits.some(
                (item) => item.id === id && item.representativeId === user.representativeId,
              )
            ) {
              navigate(`/representative/visits/${id}`);
            }
          }}
          label={`Карта встречи: ${visit.address}`}
        />

        <div>
          <h2 className="mt-5 text-[22px] font-semibold leading-snug tracking-[-0.4px] md:mt-0">
            {visit.address}
          </h2>
          <p className="mt-2 text-[12px] leading-relaxed text-muted">{visit.district}</p>

          <section
            className="mt-5 flex min-h-[83px] items-center gap-3 rounded-2xl bg-canvas px-4 py-4"
            aria-label="Информация о клиенте"
          >
            <Avatar initials={clientInitials} tone="gray" className="size-10" />
            <div className="min-w-0 flex-1">
              <h3 className="text-[16px] font-semibold">{visit.client}</h3>
              <p className="mt-1 text-[12px] text-muted">{visit.product}</p>
            </div>
            <DemoClientContact visit={visit} />
          </section>

          <dl className="mt-5 grid grid-cols-2 gap-3">
            <div className="rounded-[14px] bg-canvas px-3.5 py-3.5">
              <dt className="text-[11px] text-muted">Временное окно</dt>
              <dd className="mt-2 text-[16px] font-semibold tracking-[-0.3px]">
                {visit.time}–{visit.endTime}
              </dd>
            </div>
            <div className="rounded-[14px] bg-canvas px-3.5 py-3.5">
              <dt className="text-[11px] text-muted">Длительность</dt>
              <dd className="mt-2 text-[16px] font-semibold">{visit.duration} минут</dd>
            </div>
          </dl>

          <VisitActionPanel visit={visit} />
        </div>
      </div>
    </section>
  );
}
