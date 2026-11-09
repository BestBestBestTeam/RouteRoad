import { Link } from 'react-router';
import { Icon } from '@/components/ui/Icon';
import type { Representative, Visit } from '@/types/workday';
import { VisitStatusBadge } from '@/components/workday/VisitStatusBadge';

interface UpcomingVisitsProps {
  visits: Visit[];
  representatives: Representative[];
  query: string;
  onQueryChange: (query: string) => void;
  onSelectVisit: (visit: Visit) => void;
  title?: string;
}

export function UpcomingVisits({
  visits,
  representatives,
  query,
  onQueryChange,
  onSelectVisit,
  title = 'Заявки на сегодня',
}: UpcomingVisitsProps) {
  const normalizedQuery = query.trim().toLocaleLowerCase('ru');
  const filteredVisits = visits.filter((visit) =>
    [visit.address, visit.client, visit.number].some((value) =>
      value.toLocaleLowerCase('ru').includes(normalizedQuery),
    ),
  );
  return (
    <section
      className="flex h-[425px] min-h-0 flex-col rounded-2xl bg-white p-4"
      aria-labelledby="upcoming-heading"
    >
      <div className="mb-3 flex shrink-0 items-center justify-between gap-3">
        <h2 id="upcoming-heading" className="text-base font-semibold">
          {title}
        </h2>
        <span className="rounded-lg bg-canvas px-2.5 py-1 text-xs text-muted">
          {filteredVisits.length}
        </span>
      </div>
      <label className="mb-2 flex min-h-10 shrink-0 items-center gap-2 rounded-xl bg-canvas px-3 text-muted focus-within:ring-2 focus-within:ring-info">
        <Icon name="search" className="size-4 shrink-0" />
        <span className="sr-only">Найти заявку по адресу, клиенту или номеру</span>
        <input
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
          placeholder="Адрес, клиент или номер"
          className="min-w-0 flex-1 bg-transparent text-xs text-ink outline-none placeholder:text-muted"
          type="search"
        />
      </label>
      <ul className="min-h-0 flex-1 overflow-auto">
        {filteredVisits.slice(0, 7).map((visit) => {
          const representative = representatives.find((item) => item.id === visit.representativeId);
          return (
            <li key={visit.id} className="border-b border-line last:border-0">
              <button
                type="button"
                onClick={() => onSelectVisit(visit)}
                className={`w-full rounded-xl px-2 py-2 text-left transition-colors hover:bg-canvas ${visit.status === 'en-route' ? 'bg-accent-soft/45' : ''}`}
              >
                <div className="flex items-center gap-2">
                  <span className="text-[10px] text-muted">#{visit.number}</span>
                  <span className="flex-1 truncate text-xs font-semibold">{visit.client}</span>
                  <VisitStatusBadge visit={visit} className="min-w-20 text-[10px]" />
                </div>
                <p className="mt-1.5 text-xs">{visit.address}</p>
                <div className="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-[10px] text-muted">
                  <span className="inline-flex items-center gap-1">
                    <Icon name="clock" className="size-3" />
                    {visit.time}–{visit.endTime}
                  </span>
                  <span>{representative?.shortName ?? 'Нет исполнителя'}</span>
                </div>
              </button>
            </li>
          );
        })}
        {filteredVisits.length === 0 && (
          <li className="px-3 py-10 text-center text-sm text-muted">
            {visits.length === 0
              ? 'Заявки появятся после загрузки рабочего дня.'
              : 'Ничего не найдено. Попробуйте другой адрес или номер.'}
          </li>
        )}
      </ul>
      <Link
        to="/dispatcher/requests"
        className="mt-3 inline-flex shrink-0 items-center gap-2 self-start text-xs font-medium text-info"
      >
        Все заявки <Icon name="chevron" className="size-3" />
      </Link>
    </section>
  );
}
