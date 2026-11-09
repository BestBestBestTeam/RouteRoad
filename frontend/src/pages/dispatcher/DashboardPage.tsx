import { useNavigate } from 'react-router';
import { DaySummary } from '@/components/dispatcher/DaySummary';
import { DispatcherMap } from '@/components/dispatcher/DispatcherMap';
import { EmptyState } from '@/components/ui/EmptyState';
import { PageHeader } from '@/components/ui/PageHeader';
import { WorkdayFeedback } from '@/components/dispatcher/WorkdayFeedback';
import { RepresentativeCards } from '@/components/dispatcher/RepresentativeCards';
import { UpcomingVisits } from '@/components/dispatcher/UpcomingVisits';
import { VisitAssignment } from '@/components/dispatcher/VisitAssignment';
import { WorkdayToolbar } from '@/components/dispatcher/WorkdayToolbar';
import { useWorkdayStore } from '@/stores/workdayStore';
import { getWorkdayCounts, isActiveVisit } from '@/utils/workday';

export default function DashboardPage() {
  const navigate = useNavigate();
  const visits = useWorkdayStore((state) => state.visits);
  const alerts = useWorkdayStore((state) => state.alerts);
  const representatives = useWorkdayStore((state) => state.representatives);
  const routes = useWorkdayStore((state) => state.routes);
  const office = useWorkdayStore((state) => state.office);
  const selectedRepresentativeId = useWorkdayStore((state) => state.selectedRepresentativeId);
  const selectRepresentative = useWorkdayStore((state) => state.selectRepresentative);
  const searchQuery = useWorkdayStore((state) => state.searchQuery);
  const setSearchQuery = useWorkdayStore((state) => state.setSearchQuery);
  const counts = getWorkdayCounts(visits, alerts);
  const activeVisits = visits
    .filter(isActiveVisit)
    .sort((first, second) => first.time.localeCompare(second.time));

  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader
        title="Рабочий день"
        description="Загрузите заявки и постройте маршруты на сегодня"
      >
        <WorkdayToolbar />
      </PageHeader>
      <WorkdayFeedback />
      <DaySummary counts={counts} />
      {visits.length === 0 && (
        <EmptyState
          title="Рабочий день ещё не подготовлен"
          description="Начните с загрузки заявок. После этого можно назначить представителей и построить маршруты."
        />
      )}
      <VisitAssignment>
        {(openVisit) => (
          <div className="grid gap-5 xl:grid-cols-[minmax(0,1.9fr)_minmax(320px,1fr)]">
            <DispatcherMap
              visits={visits}
              representatives={representatives}
              routes={routes}
              office={office}
              selectedRepresentativeId={selectedRepresentativeId}
              alertVisitIds={alerts
                .filter((alert) => alert.status === 'open')
                .map((alert) => alert.visitId)}
              onOpenVisit={openVisit}
              onSelect={selectRepresentative}
              attentionCount={counts.attention}
              onOpenAlerts={() => navigate('/dispatcher/alerts')}
            />
            <UpcomingVisits
              visits={activeVisits}
              representatives={representatives}
              query={searchQuery}
              onQueryChange={setSearchQuery}
              onSelectVisit={openVisit}
              title={
                routes.length > 0
                  ? 'Ближайшие встречи'
                  : visits.length > 0
                    ? 'Загруженные заявки'
                    : 'Заявки на сегодня'
              }
            />
          </div>
        )}
      </VisitAssignment>
      {representatives.length > 0 && (
        <RepresentativeCards
          representatives={representatives}
          selectedId={selectedRepresentativeId}
          onSelect={selectRepresentative}
        />
      )}
    </div>
  );
}
