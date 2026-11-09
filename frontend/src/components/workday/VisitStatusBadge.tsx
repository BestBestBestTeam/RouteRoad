import type { Visit, VisitStatus } from '@/types/workday';
import { getVisitStatusLabel } from '@/utils/workday';

const colors: Record<VisitStatus, string> = {
  new: 'bg-accent-soft text-[#786715]',
  assigned: 'bg-canvas text-muted',
  'en-route': 'bg-blue-50 text-info',
  arrived: 'bg-blue-50 text-info',
  'in-progress': 'bg-accent-soft text-ink',
  completed: 'bg-emerald-50 text-success',
  cancelled: 'bg-canvas text-muted',
  attention: 'bg-red-50 text-danger',
};

export function VisitStatusBadge({ visit, className = '' }: { visit: Visit; className?: string }) {
  return (
    <span
      className={`inline-flex min-h-6 items-center justify-center rounded-lg px-2.5 py-1 text-[11px] font-medium leading-tight ${colors[visit.status]} ${className}`}
    >
      {getVisitStatusLabel(visit)}
    </span>
  );
}
