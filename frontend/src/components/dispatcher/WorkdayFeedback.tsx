import { Button } from '@/components/ui/Button';
import { Icon } from '@/components/ui/Icon';
import { useWorkdayStore } from '@/stores/workdayStore';

export function WorkdayFeedback() {
  const notice = useWorkdayStore((state) => state.notice);
  const error = useWorkdayStore((state) => state.error);
  const setNotice = useWorkdayStore((state) => state.setNotice);
  const loadStatus = useWorkdayStore((state) => state.loadStatus);
  const planStatus = useWorkdayStore((state) => state.planStatus);
  const loadRequests = useWorkdayStore((state) => state.loadRequests);
  const buildRoutes = useWorkdayStore((state) => state.buildRoutes);
  return (
    <>
      {notice && (
        <div
          role="status"
          className="flex items-start gap-3 rounded-xl border border-accent/40 bg-accent-soft/70 px-4 py-3 text-sm"
        >
          <Icon name="check" className="mt-0.5 size-4 shrink-0" />
          <p className="min-w-0 flex-1 leading-relaxed">{notice}</p>
          <button
            type="button"
            aria-label="Закрыть сообщение"
            onClick={() => setNotice(null)}
            className="flex size-7 shrink-0 items-center justify-center rounded-lg hover:bg-white"
          >
            <Icon name="close" className="size-3" />
          </button>
        </div>
      )}
      {error && (
        <div
          role="alert"
          className="flex flex-wrap items-center gap-3 rounded-xl border border-danger/25 bg-red-50 px-4 py-3 text-sm text-danger"
        >
          <p className="min-w-0 flex-1">{error}</p>
          {loadStatus === 'error' && (
            <Button type="button" variant="secondary" onClick={() => void loadRequests()}>
              Повторить загрузку
            </Button>
          )}
          {planStatus === 'error' && (
            <Button type="button" variant="secondary" onClick={() => void buildRoutes()}>
              Повторить построение
            </Button>
          )}
        </div>
      )}
    </>
  );
}
