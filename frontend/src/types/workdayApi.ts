import type {
  MutationContext,
  VisitStatus,
  WorkdaySnapshot,
  Visit,
  Representative,
  HistoryEntry,
  WorkdayAlert,
} from '@/types/workday';
import type {
  RequestQuery,
  RepresentativeQuery,
  HistoryQuery,
  AlertQuery,
  PageResult,
} from '@/types/workdayQuery';

/** UI-facing read model; an HTTP adapter composes it from Java responses. */
export interface VisitDetails {
  visit: Visit;
  representativeName: string | null;
  history: HistoryEntry[];
  alerts: WorkdayAlert[];
}

export interface WorkdayApi {
  loadRequests: (context?: MutationContext) => Promise<WorkdaySnapshot>;
  buildRoutes: (context?: MutationContext) => Promise<WorkdaySnapshot>;
  listRequests: (query?: Partial<RequestQuery>) => Promise<PageResult<Visit>>;
  listRepresentatives: (
    query?: Partial<RepresentativeQuery>,
  ) => Promise<PageResult<Representative>>;
  listHistory: (query?: Partial<HistoryQuery>) => Promise<PageResult<HistoryEntry>>;
  listAlerts: (query?: Partial<AlertQuery>) => Promise<PageResult<WorkdayAlert>>;
  getVisitDetails: (id: string) => Promise<VisitDetails>;
  setVisitStatus: (
    visitId: string,
    status: VisitStatus,
    context?: MutationContext,
  ) => Promise<WorkdaySnapshot>;
  reassignVisit: (
    visitId: string,
    representativeId: string,
    reason?: string,
    context?: MutationContext,
  ) => Promise<WorkdaySnapshot>;
  reportDelay: (
    visitId: string,
    minutes: number,
    context?: MutationContext,
  ) => Promise<WorkdaySnapshot>;
  refuseVisit: (
    visitId: string,
    reason: string,
    context?: MutationContext,
  ) => Promise<WorkdaySnapshot>;
  reset: () => void;
}

export type DemoOperation = Exclude<keyof WorkdayApi, 'reset'>;
