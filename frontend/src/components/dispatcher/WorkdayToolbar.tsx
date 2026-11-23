import { Button } from '@/components/ui/Button';
import { Icon } from '@/components/ui/Icon';
import { useWorkdayStore } from '@/stores/workdayStore';

export function WorkdayToolbar() {
  const visits = useWorkdayStore((state) => state.visits);
  const routes = useWorkdayStore((state) => state.routes);
  const loadStatus = useWorkdayStore((state) => state.loadStatus);
  const planStatus = useWorkdayStore((state) => state.planStatus);
  const pendingVisitId = useWorkdayStore((state) => state.pendingVisitId);
  const loadRequests = useWorkdayStore((state) => state.loadRequests);
  const buildRoutes = useWorkdayStore((state) => state.buildRoutes);
  const resetDay = useWorkdayStore((state) => state.resetDay);
  const loading = loadStatus === 'loading';
  const planning = planStatus === 'loading';
  const busy = loading || planning || pendingVisitId !== null;
  const hasRequests = visits.length > 0;
  const hasRoutes = routes.length > 0;

  return (
    <div className="flex flex-wrap gap-3 pt-1" aria-label="Управление рабочим днём">
      <Button
        type="button"
        variant="secondary"
        disabled={busy || hasRequests}
        onClick={() => void loadRequests()}
      >
        <Icon name="download" className="size-4" />
        {loading ? 'Загрузка заявок…' : hasRequests ? 'Заявки загружены' : 'Загрузить заявки'}
      </Button>
      <Button
        type="button"
        disabled={!hasRequests || busy || hasRoutes}
        onClick={() => void buildRoutes()}
      >
        <Icon name="route" className="size-4" />
        {planning
          ? 'Построение маршрутов…'
          : hasRoutes
            ? 'Маршруты построены'
            : 'Построить маршруты'}
      </Button>
      {(hasRequests || loading || planning) && (
        <Button type="button" variant="secondary" onClick={resetDay}>
          <Icon name="refresh" className="size-4" />
          Очистить день
        </Button>
      )}
    </div>
  );
}
