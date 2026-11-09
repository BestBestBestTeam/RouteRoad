import { useEffect, useMemo, useState } from 'react';
import { workdayApi } from '@/api/workdayApi';
import { useWorkdayStore } from '@/stores/workdayStore';
import type { VisitDetails } from '@/types/workdayApi';

type DetailsFetcher = (id: string) => Promise<VisitDetails>;

interface DetailsRequest {
  id: string | null;
  revision: number;
  retry: number;
  fetcher: DetailsFetcher;
}

interface DetailsResult {
  request: DetailsRequest;
  details?: VisitDetails;
  error?: string;
}

/** Details come from the source by ID, independently of the currently loaded list page. */
export function useVisitDetails(
  id: string | null,
  fetcher: DetailsFetcher = workdayApi.getVisitDetails,
) {
  const revision = useWorkdayStore((state) => state.revision);
  const [retry, setRetry] = useState(0);
  const [result, setResult] = useState<DetailsResult>();
  const request = useMemo(() => ({ id, revision, retry, fetcher }), [id, revision, retry, fetcher]);

  useEffect(() => {
    if (request.id === null) return;
    let active = true;
    void request.fetcher(request.id).then(
      (details) => {
        if (active) setResult({ request, details });
      },
      (error: unknown) => {
        if (active)
          setResult({
            request,
            error: error instanceof Error ? error.message : 'Не удалось загрузить заявку.',
          });
      },
    );
    return () => {
      active = false;
    };
  }, [request]);

  const current = result?.request === request;

  return {
    details: current ? result.details : undefined,
    error: current ? result.error : undefined,
    loading: id !== null && !current,
    refetch: () => setRetry((value) => value + 1),
  };
}
