import type { ActorRole, HistoryEventType, VisitStatus } from '@/types/workday';

export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  serverNow: string;
}

export interface PageQuery {
  page: number;
  pageSize: number;
}

export interface RequestQuery extends PageQuery {
  search: string;
  status: 'all' | VisitStatus;
  representativeId: string;
}

export interface RepresentativeQuery extends PageQuery {
  search: string;
  availability: 'all' | 'available' | 'needs-help';
  workload: 'all' | 'assigned' | 'unassigned';
}

export interface HistoryQuery extends PageQuery {
  search: string;
  type: 'all' | HistoryEventType;
  actorRole: 'all' | ActorRole;
}

export interface AlertQuery extends PageQuery {
  search: string;
  status: 'all' | 'open' | 'resolved';
  severity: 'all' | 'warning' | 'critical';
}
