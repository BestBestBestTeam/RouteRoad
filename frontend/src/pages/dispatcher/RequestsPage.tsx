import { useState } from 'react';
import { workdayApi } from '@/api/workdayApi';
import { usePagedQuery } from '@/hooks/usePagedQuery';
import { DaySummary } from '@/components/dispatcher/DaySummary';
import { EmptyState } from '@/components/ui/EmptyState';
import { PageHeader } from '@/components/ui/PageHeader';
import { WorkdayFeedback } from '@/components/dispatcher/WorkdayFeedback';
import { VisitAssignment } from '@/components/dispatcher/VisitAssignment';
import { VisitsTable } from '@/components/dispatcher/VisitsTable';
import { FilterField, inputClass, Pagination, QueryFeedback } from '@/components/ui/QueryControls';
import { useWorkdayStore } from '@/stores/workdayStore';
import type { RequestQuery } from '@/types/workdayQuery';
import { DEFAULT_REQUEST_QUERY } from '@/utils/workdayQuery';
import { getWorkdayCounts, visitStatusLabels } from '@/utils/workday';

export default function RequestsPage() {
  const visits = useWorkdayStore((s) => s.visits);
  const representatives = useWorkdayStore((s) => s.representatives);
  const alerts = useWorkdayStore((s) => s.alerts);
  const [query, setQuery] = useState<RequestQuery>({ ...DEFAULT_REQUEST_QUERY });
  const result = usePagedQuery(workdayApi.listRequests, query);
  const change = (patch: Partial<RequestQuery>) => setQuery((q) => ({ ...q, ...patch, page: 1 }));
  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader
        title="Заявки на сегодня"
        description="Адреса, временные окна и назначения"
      ></PageHeader>
      <WorkdayFeedback />
      <DaySummary counts={getWorkdayCounts(visits, alerts)} />
      <div
        className="grid gap-4 rounded-2xl bg-white p-5 sm:grid-cols-2 xl:grid-cols-3"
        aria-label="Фильтры заявок"
      >
        <FilterField label="Поиск по заявкам">
          <input
            type="search"
            value={query.search}
            placeholder="Адрес, клиент или номер"
            onChange={(e) => change({ search: e.target.value })}
            className={inputClass}
          />
        </FilterField>
        <FilterField label="Статус заявки">
          <select
            value={query.status}
            onChange={(e) => change({ status: e.target.value as RequestQuery['status'] })}
            className={inputClass}
          >
            <option value="all">Все статусы</option>
            {Object.entries(visitStatusLabels).map(([id, label]) => (
              <option key={id} value={id}>
                {label}
              </option>
            ))}
          </select>
        </FilterField>
        <FilterField label="Представитель заявки">
          <select
            value={query.representativeId}
            onChange={(e) => change({ representativeId: e.target.value })}
            className={inputClass}
          >
            <option value="all">Все представители</option>
            <option value="unassigned">Без назначения</option>
            {representatives.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name}
              </option>
            ))}
          </select>
        </FilterField>
      </div>
      <QueryFeedback loading={result.loading} error={result.error} retry={result.refetch} />
      {result.data && (
        <>
          <VisitAssignment>
            {(openVisit) => (
              <VisitsTable
                visits={result.data?.items ?? []}
                representatives={representatives}
                onSelectVisit={openVisit}
              />
            )}
          </VisitAssignment>
          <Pagination {...result.data} onPage={(page) => setQuery((q) => ({ ...q, page }))} />
        </>
      )}
      {visits.length === 0 && (
        <EmptyState
          title="Заявок пока нет"
          description="Загрузите рабочий день. Назначения появятся после построения маршрутов."
        />
      )}
    </div>
  );
}
