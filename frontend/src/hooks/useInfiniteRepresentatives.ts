import { useCallback, useEffect, useRef, useState } from 'react';
import { workdayApi } from '@/api/workdayApi';
import { useWorkdayStore } from '@/stores/workdayStore';
import type { Representative } from '@/types/workday';
import type { PageResult, RepresentativeQuery } from '@/types/workdayQuery';

export type RepresentativeFetcher = (
  query?: Partial<RepresentativeQuery>,
) => Promise<PageResult<Representative>>;

interface RepresentativeSession {
  query: RepresentativeQuery;
  fetcher: RepresentativeFetcher;
  revision: number;
  cancelled: boolean;
  loading: boolean;
  page: number;
  items: Representative[];
  total: number;
  hasMore: boolean;
  failedPage: number | null;
  error?: string;
}

interface RepresentativeResult {
  query: RepresentativeQuery;
  fetcher: RepresentativeFetcher;
  revision: number;
  items: Representative[];
  total: number;
  loading: boolean;
  hasMore: boolean;
  error?: string;
}

function publishSession(session: RepresentativeSession): RepresentativeResult {
  return {
    query: session.query,
    fetcher: session.fetcher,
    revision: session.revision,
    items: session.items,
    total: session.total,
    loading: session.loading,
    hasMore: session.hasMore,
    error: session.error,
  };
}

async function fetchRepresentatives(
  session: RepresentativeSession,
  page: number,
  publish: (result: RepresentativeResult) => void,
) {
  session.loading = true;
  session.error = undefined;
  session.failedPage = null;
  // The first page starts after the filter debounce; appends and retries keep loaded cards visible.
  publish(publishSession(session));
  try {
    const response = await session.fetcher({ ...session.query, page, pageSize: 10 });
    if (session.cancelled) return;
    const items = new Map<string, Representative>();
    if (page > 1) for (const item of session.items) items.set(item.id, item);
    for (const item of response.items) items.set(item.id, item);
    session.items = [...items.values()];
    session.total = response.total;
    session.hasMore =
      response.items.length > 0 &&
      response.page > session.page &&
      response.page * response.pageSize < response.total;
    session.page = response.page;
    session.loading = false;
    publish(publishSession(session));
  } catch (error: unknown) {
    if (session.cancelled) return;
    session.loading = false;
    session.failedPage = page;
    session.error = error instanceof Error ? error.message : 'Не удалось загрузить представителей.';
    publish(publishSession(session));
  }
}

/** Each filter/revision starts a new first page; only explicit scrolling or a button loads another ten. */
export function useInfiniteRepresentatives(
  query: RepresentativeQuery,
  fetcher: RepresentativeFetcher = workdayApi.listRepresentatives,
) {
  const revision = useWorkdayStore((state) => state.revision);
  const sessionRef = useRef<RepresentativeSession | null>(null);
  const [result, setResult] = useState<RepresentativeResult>();

  useEffect(() => {
    const session: RepresentativeSession = {
      query,
      fetcher,
      revision,
      cancelled: false,
      loading: true,
      page: 0,
      items: [],
      total: 0,
      hasMore: true,
      failedPage: null,
    };
    sessionRef.current = session;
    const timer = window.setTimeout(() => {
      void fetchRepresentatives(session, 1, setResult);
    }, 180);
    return () => {
      window.clearTimeout(timer);
      session.cancelled = true;
      if (sessionRef.current === session) sessionRef.current = null;
    };
  }, [query, revision, fetcher]);

  const loadMore = useCallback(() => {
    const session = sessionRef.current;
    if (
      !session ||
      session.query !== query ||
      session.fetcher !== fetcher ||
      session.revision !== revision ||
      session.cancelled ||
      session.loading ||
      session.error ||
      !session.hasMore
    )
      return;
    void fetchRepresentatives(session, session.page + 1, setResult);
  }, [query, revision, fetcher]);

  const retry = useCallback(() => {
    const session = sessionRef.current;
    if (
      !session ||
      session.query !== query ||
      session.fetcher !== fetcher ||
      session.revision !== revision ||
      session.cancelled ||
      session.loading ||
      session.failedPage === null
    )
      return;
    void fetchRepresentatives(session, session.failedPage, setResult);
  }, [query, revision, fetcher]);

  const current =
    result?.query === query && result.revision === revision && result.fetcher === fetcher;
  return {
    items: current ? result.items : [],
    total: current ? result.total : 0,
    loading: !current || result.loading,
    hasMore: current ? result.hasMore : false,
    error: current ? result.error : undefined,
    resetKey: `${revision}:${JSON.stringify(query)}`,
    loadMore,
    retry,
  };
}
