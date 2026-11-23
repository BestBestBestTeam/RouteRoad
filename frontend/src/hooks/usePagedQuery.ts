import { useEffect, useState } from 'react';
import { useWorkdayStore } from '@/stores/workdayStore';
import type { PageResult } from '@/types/workdayQuery';

/** Query functions own filtering/paging. Stale responses cannot replace newer results. */
export function usePagedQuery<Q, T>(fetcher: (query: Q) => Promise<PageResult<T>>, query: Q) {
  const revision = useWorkdayStore((state) => state.revision);
  const [retry, setRetry] = useState(0);
  const [result, setResult] = useState<{
    query: Q;
    revision: number;
    retry: number;
    data?: PageResult<T>;
    receivedAt: number;
    error?: string;
  }>();
  useEffect(() => {
    let active = true;
    void fetcher(query).then(
      (data) => {
        if (active) setResult({ query, revision, retry, data, receivedAt: Date.now() });
      },
      (error: unknown) => {
        if (active)
          setResult({
            query,
            revision,
            retry,
            receivedAt: Date.now(),
            error: error instanceof Error ? error.message : 'Не удалось загрузить список.',
          });
      },
    );
    return () => {
      active = false;
    };
  }, [fetcher, query, revision, retry]);
  const current = result?.query === query && result.revision === revision && result.retry === retry;
  return {
    data: current ? result.data : undefined,
    receivedAt: current ? result.receivedAt : 0,
    error: current ? result.error : undefined,
    loading: !current,
    refetch: () => setRetry((value) => value + 1),
  };
}
