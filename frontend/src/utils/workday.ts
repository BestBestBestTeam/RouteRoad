import type {
  Representative,
  Visit,
  VisitStatus,
  WorkdayAlert,
  WorkdaySnapshot,
} from '@/types/workday';

export const visitStatusLabels: Record<VisitStatus, string> = {
  new: 'Новая',
  assigned: 'Назначена',
  'en-route': 'В пути',
  arrived: 'Прибыл',
  'in-progress': 'Встреча начата',
  completed: 'Завершена',
  cancelled: 'Отменена',
  attention: 'Нужна помощь',
};

export function getVisitStatusLabel(visit: Visit): string {
  if (visit.status === 'assigned' && !visit.representativeId) return 'Без назначения';
  return visitStatusLabels[visit.status];
}

export function isActiveVisit(visit: Visit): boolean {
  return visit.status !== 'completed' && visit.status !== 'cancelled';
}

export function getWorkdayCounts(visits: readonly Visit[], alerts?: readonly WorkdayAlert[]) {
  return {
    total: visits.length,
    assigned: visits.filter((visit) => visit.representativeId !== null).length,
    completed: visits.filter((visit) => visit.status === 'completed').length,
    attention: alerts
      ? alerts.filter((alert) => alert.status === 'open').length
      : visits.filter((visit) => visit.status === 'attention').length,
  };
}

export function summarizeRepresentatives(
  visits: readonly Visit[],
  representatives: readonly Representative[],
): Representative[] {
  return representatives.map((representative) => {
    const ownVisits = visits.filter((visit) => visit.representativeId === representative.id);
    return {
      ...representative,
      total: ownVisits.length,
      completed: ownVisits.filter((visit) => visit.status === 'completed').length,
    };
  });
}

export function createEmptyWorkdaySnapshot(): WorkdaySnapshot {
  return {
    visits: [],
    representatives: [],
    routes: [],
    office: null,
    history: [],
    alerts: [],
    serviceDate: '',
  };
}
