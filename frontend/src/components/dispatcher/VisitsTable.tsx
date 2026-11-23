import { Icon } from '@/components/ui/Icon';
import type { Representative, Visit } from '@/types/workday';
import { VisitStatusBadge } from '@/components/workday/VisitStatusBadge';

interface VisitsTableProps {
  visits: Visit[];
  representatives: Representative[];
  onSelectVisit: (visit: Visit) => void;
}

export function VisitsTable({ visits, representatives, onSelectVisit }: VisitsTableProps) {
  return (
    <section className="rounded-2xl bg-white p-5" aria-label="Список заявок">
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted">На странице: {visits.length}</p>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-line text-xs font-medium text-muted">
            <tr>
              <th scope="col" className="py-3 pr-4 font-medium">
                Заявка
              </th>
              <th scope="col" className="min-w-48 px-3 py-3 font-medium">
                Адрес и клиент
              </th>
              <th scope="col" className="whitespace-nowrap px-3 py-3 font-medium">
                Временное окно
              </th>
              <th scope="col" className="px-3 py-3 font-medium">
                Представитель
              </th>
              <th scope="col" className="px-3 py-3 font-medium">
                Статус
              </th>
              <th scope="col">
                <span className="sr-only">Действие</span>
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {visits.map((visit) => (
              <tr
                key={visit.id}
                className="cursor-pointer hover:bg-canvas/60"
                onClick={() => onSelectVisit(visit)}
              >
                <th scope="row" className="py-4 pr-4 font-medium">
                  #{visit.number}
                </th>
                <td className="px-3 py-4">
                  <p>{visit.address}</p>
                  <p className="mt-1 text-xs text-muted">{visit.client}</p>
                </td>
                <td className="whitespace-nowrap px-3 py-4 text-xs">
                  {visit.time}–{visit.endTime}
                  <p className="mt-1 text-muted">{visit.duration} минут</p>
                </td>
                <td className="px-3 py-4 text-xs">
                  {representatives.find(
                    (representative) => representative.id === visit.representativeId,
                  )?.shortName ?? 'Не назначен'}
                </td>
                <td className="px-3 py-4">
                  <VisitStatusBadge visit={visit} />
                </td>
                <td className="py-4 pl-3">
                  <button
                    type="button"
                    onClick={(event) => {
                      event.stopPropagation();
                      onSelectVisit(visit);
                    }}
                    aria-label={`Открыть заявку ${visit.number}`}
                    className="flex size-10 items-center justify-center rounded-xl hover:bg-accent-soft"
                  >
                    <Icon name="chevron" className="size-4" />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {visits.length === 0 && (
        <p className="py-12 text-center text-sm text-muted">
          Подходящих заявок нет. Измените поисковый запрос.
        </p>
      )}
    </section>
  );
}
