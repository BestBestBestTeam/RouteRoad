import { useState, type ReactNode } from 'react';
import { useVisitDetails } from '@/hooks/useVisitDetails';
import { useWorkdayStore } from '@/stores/workdayStore';
import type { Visit } from '@/types/workday';
import { Dialog } from '@/components/ui/Dialog';
import { QueryFeedback } from '@/components/ui/QueryControls';
import { ReassignDrawer } from './ReassignDrawer';

type OpenVisit = (visit: Visit | string) => void;

export function VisitAssignment({ children }: { children: (openVisit: OpenVisit) => ReactNode }) {
  const [selectedVisitId, setSelectedVisitId] = useState<string | null>(null);
  const result = useVisitDetails(selectedVisitId);
  const details = result.details;
  const representatives = useWorkdayStore((state) => state.representatives);
  const pendingVisitId = useWorkdayStore((state) => state.pendingVisitId);
  const error = useWorkdayStore((state) => state.error);
  const reassignVisit = useWorkdayStore((state) => state.reassignVisit);

  async function assign(representativeId: string, reason: string) {
    if (!details) return;
    await reassignVisit(details.visit.id, representativeId, reason);
    if (!useWorkdayStore.getState().error) setSelectedVisitId(null);
  }

  return (
    <>
      {children((item) => setSelectedVisitId(typeof item === 'string' ? item : item.id))}
      {selectedVisitId !== null && !details && (
        <Dialog title="Заявка" onClose={() => setSelectedVisitId(null)}>
          <QueryFeedback
            loading={result.loading}
            error={result.error}
            retry={result.refetch}
            loadingMessage="Загружаем заявку…"
          />
        </Dialog>
      )}
      {details && (
        <ReassignDrawer
          key={details.visit.id}
          visit={details.visit}
          representativeName={details.representativeName}
          history={details.history}
          alerts={details.alerts}
          representatives={representatives}
          isSubmitting={pendingVisitId === details.visit.id}
          error={error}
          onClose={() => setSelectedVisitId(null)}
          onAssign={assign}
        />
      )}
    </>
  );
}
