export type VisitStatus =
  | 'new'
  | 'assigned'
  | 'en-route'
  | 'arrived'
  | 'in-progress'
  | 'completed'
  | 'cancelled'
  | 'attention';

export interface Coordinate {
  lat: number;
  lon: number;
}

export interface Visit {
  id: string;
  number: string;
  client: string;
  address: string;
  district: string;
  location: Coordinate;
  time: string;
  endTime: string;
  duration: number;
  representativeId: string | null;
  status: VisitStatus;
  product: string;
  assignment: AssignmentDetails | null;
}

export interface AssignmentDetails {
  reasonCode: string;
  reasonText: string;
  assignedAt: string;
  assignedBy: string;
}

export interface Representative {
  id: string;
  name: string;
  shortName: string;
  initials: string;
  completed: number;
  total: number;
  tone: 'yellow' | 'purple' | 'mint' | 'red' | 'blue' | 'gray';
  needsHelp?: boolean;
}

export interface Office {
  id: string;
  name: string;
  address: string;
  location: Coordinate;
}

export interface RoutePlan {
  id: string;
  representativeId: string;
  visitIds: string[];
  geometry: {
    type: 'LineString';
    /** GeoJSON coordinates are [longitude, latitude], unlike Leaflet's LatLng. */
    coordinates: [number, number][];
  };
}

export type ActorRole = 'dispatcher' | 'representative' | 'system';

export interface AuditActor {
  id: string;
  name: string;
  role: ActorRole;
}

export interface MutationContext {
  actor: AuditActor;
  /** Demo ownership context; Java will determine identity from its real session later. */
  representativeId?: string | null;
}

export type HistoryEventType =
  | 'data-loaded'
  | 'routes-built'
  | 'status-changed'
  | 'assignment-changed'
  | 'alert-created'
  | 'alert-resolved';

export interface HistoryChange {
  field: string;
  label: string;
  before: string | null;
  after: string | null;
}

export interface HistoryEntry {
  id: number;
  type: HistoryEventType;
  createdAt: string;
  actor: AuditActor;
  visitId?: string;
  alertId?: string;
  reason: string;
  changes: HistoryChange[];
  title: string;
  description: string;
  /** Kept for the compact compatibility view; detailed history uses createdAt. */
  time: string;
}

export interface AlertResolution {
  resolvedAt: string;
  actor: AuditActor;
  reason: string;
}

export interface WorkdayAlert {
  id: string;
  visitId: string;
  representativeId: string;
  kind: 'delay' | 'refusal';
  severity: 'warning' | 'critical';
  status: 'open' | 'resolved';
  title: string;
  reasonCode: string;
  reasonText: string;
  impact: string[];
  createdAt: string;
  deadlineAt: string;
  resolution: AlertResolution | null;
}

export interface WorkdaySnapshot {
  visits: Visit[];
  representatives: Representative[];
  routes: RoutePlan[];
  office: Office | null;
  history: HistoryEntry[];
  alerts: WorkdayAlert[];
  serviceDate: string;
}

export type OperationStatus = 'idle' | 'loading' | 'success' | 'error';
