import type {
  AlertQuery,
  HistoryQuery,
  PageQuery,
  RepresentativeQuery,
  RequestQuery,
} from '@/types/workdayQuery';

export const DEFAULT_REQUEST_QUERY: Readonly<RequestQuery> = Object.freeze({
  search: '',
  status: 'all',
  representativeId: 'all',
  page: 1,
  pageSize: 10,
});

export const DEFAULT_REPRESENTATIVE_QUERY: Readonly<RepresentativeQuery> = Object.freeze({
  search: '',
  availability: 'all',
  workload: 'all',
  page: 1,
  pageSize: 10,
});

export const DEFAULT_HISTORY_QUERY: Readonly<HistoryQuery> = Object.freeze({
  search: '',
  type: 'all',
  actorRole: 'all',
  page: 1,
  pageSize: 10,
});

export const DEFAULT_ALERT_QUERY: Readonly<AlertQuery> = Object.freeze({
  search: '',
  status: 'all',
  severity: 'all',
  page: 1,
  pageSize: 10,
});

/** Generic numeric normalization only; business filtering belongs to the API adapter. */
export function normalizePageQuery(query: Partial<PageQuery>): PageQuery {
  const page =
    typeof query.page === 'number' && Number.isFinite(query.page)
      ? Math.max(1, Math.floor(query.page))
      : 1;
  const pageSize =
    typeof query.pageSize === 'number' && Number.isFinite(query.pageSize)
      ? Math.min(100, Math.max(1, Math.floor(query.pageSize)))
      : 10;
  return { page, pageSize };
}
