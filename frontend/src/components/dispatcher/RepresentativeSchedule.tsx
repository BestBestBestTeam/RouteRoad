import { useMemo, useState } from 'react';
import { workdayApi } from '@/api/workdayApi';
import { usePagedQuery } from '@/hooks/usePagedQuery';
import { VisitAssignment } from '@/components/dispatcher/VisitAssignment';
import { VisitsTable } from '@/components/dispatcher/VisitsTable';
import { FilterField, inputClass, Pagination, QueryFeedback } from '@/components/ui/QueryControls';
import { useWorkdayStore } from '@/stores/workdayStore';
import { DEFAULT_REQUEST_QUERY } from '@/utils/workdayQuery';

export function RepresentativeSchedule({ id }: { id: string }) {
  const representatives = useWorkdayStore((s) => s.representatives);
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState('');
  const query = useMemo(
    () => ({ ...DEFAULT_REQUEST_QUERY, representativeId: id, search, page }),
    [id, search, page],
  );
  const result = usePagedQuery(workdayApi.listRequests, query);
  return (
    <section
      className="space-y-4"
      aria-label={`Расписание: ${representatives.find((r) => r.id === id)?.name}`}
    >
      <h2 className="text-lg font-semibold">{representatives.find((r) => r.id === id)?.name}</h2>
      <FilterField label="Поиск по заявкам представителя">
        <input
          type="search"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(1);
          }}
          className={inputClass}
        />
      </FilterField>
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
          <Pagination {...result.data} onPage={setPage} />
        </>
      )}
    </section>
  );
}
