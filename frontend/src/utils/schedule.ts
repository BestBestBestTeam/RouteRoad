import type { RoutePlan, Visit } from '@/types/workday';

export function getRouteVisits(route: RoutePlan | undefined, visits: Visit[]): Visit[] {
  const byId = new Map(visits.map((visit) => [visit.id, visit]));
  return (
    route?.visitIds.flatMap((id) => {
      const visit = byId.get(id);
      return visit ? [visit] : [];
    }) ?? []
  );
}
