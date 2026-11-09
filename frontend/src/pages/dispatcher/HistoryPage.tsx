import { useState } from 'react';
import { workdayApi } from '@/api/workdayApi';
import { usePagedQuery } from '@/hooks/usePagedQuery';
import { EmptyState } from '@/components/ui/EmptyState';
import { PageHeader } from '@/components/ui/PageHeader';
import { WorkdayFeedback } from '@/components/dispatcher/WorkdayFeedback';
import { VisitAssignment } from '@/components/dispatcher/VisitAssignment';
import { Dialog } from '@/components/ui/Dialog';
import { Button } from '@/components/ui/Button';
import { FilterField, inputClass, Pagination, QueryFeedback } from '@/components/ui/QueryControls';
import type { HistoryEntry } from '@/types/workday';
import type { HistoryQuery } from '@/types/workdayQuery';
import { DEFAULT_HISTORY_QUERY } from '@/utils/workdayQuery';
import { actorLabels, formatEventDate, historyLabels } from '@/utils/history';

export default function HistoryPage() {
  const [query, setQuery] = useState<HistoryQuery>({ ...DEFAULT_HISTORY_QUERY });
  const [selected, setSelected] = useState<HistoryEntry | null>(null);
  const result = usePagedQuery(workdayApi.listHistory, query);
  const change = (patch: Partial<HistoryQuery>) => setQuery((q) => ({ ...q, ...patch, page: 1 }));
  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader
        title="История дня"
        description="Кто, когда и почему изменил заявки, назначения или обращения"
      />
      <WorkdayFeedback />
      <div className="grid gap-4 rounded-2xl bg-white p-5 sm:grid-cols-3">
        <FilterField label="Поиск по истории">
          <input
            type="search"
            value={query.search}
            placeholder="Номер заявки, событие или автор"
            onChange={(e) => change({ search: e.target.value })}
            className={inputClass}
          />
        </FilterField>
        <FilterField label="Тип события">
          <select
            value={query.type}
            onChange={(e) => change({ type: e.target.value as HistoryQuery['type'] })}
            className={inputClass}
          >
            <option value="all">Все события</option>
            {Object.entries(historyLabels).map(([id, label]) => (
              <option key={id} value={id}>
                {label}
              </option>
            ))}
          </select>
        </FilterField>
        <FilterField label="Роль автора">
          <select
            value={query.actorRole}
            onChange={(e) => change({ actorRole: e.target.value as HistoryQuery['actorRole'] })}
            className={inputClass}
          >
            <option value="all">Все авторы</option>
            {Object.entries(actorLabels).map(([id, label]) => (
              <option key={id} value={id}>
                {label}
              </option>
            ))}
          </select>
        </FilterField>
      </div>
      <QueryFeedback loading={result.loading} error={result.error} retry={result.refetch} />
      {result.data && (
        <>
          {result.data.total === 0 ? (
            <EmptyState
              icon="clock"
              title="События не найдены"
              description="События появятся после загрузки дня и изменений заявок. Попробуйте изменить фильтры."
            />
          ) : (
            <ol className="divide-y divide-line rounded-2xl bg-white px-5">
              {result.data.items.map((entry) => (
                <li key={entry.id}>
                  <button
                    type="button"
                    onClick={() => setSelected(entry)}
                    className="flex w-full items-start gap-4 rounded-xl py-5 text-left hover:bg-canvas"
                  >
                    <span className="shrink-0 pt-0.5 text-xs text-muted">{entry.time}</span>
                    <span className="min-w-0 flex-1 border-l-2 border-accent pl-4">
                      <span className="block text-sm font-semibold">{entry.title}</span>
                      <span className="mt-2 block text-xs leading-relaxed text-muted">
                        {entry.description}
                      </span>
                      <span className="mt-2 block text-xs text-muted">
                        {entry.actor.name} · {historyLabels[entry.type]}
                      </span>
                    </span>
                  </button>
                </li>
              ))}
            </ol>
          )}
          <Pagination {...result.data} onPage={(page) => setQuery((q) => ({ ...q, page }))} />
        </>
      )}
      <VisitAssignment>
        {(openVisit) =>
          selected && (
            <Dialog title={selected.title} onClose={() => setSelected(null)}>
              <p className="text-xs text-muted">
                {formatEventDate(selected.createdAt)} · {actorLabels[selected.actor.role]}
              </p>
              <p className="mt-2 text-sm font-medium">{selected.actor.name}</p>
              <h3 className="mt-5 text-sm font-semibold">Причина</h3>
              <p className="mt-2 text-sm leading-relaxed">{selected.reason}</p>
              <dl className="mt-5 space-y-4">
                {selected.changes.map((item) => (
                  <div key={item.field} className="rounded-xl bg-canvas p-4">
                    <dt className="text-sm font-semibold">{item.label}</dt>
                    <dd className="mt-2 grid gap-2 text-sm sm:grid-cols-2">
                      <span>
                        <span className="block text-xs text-muted">До</span>
                        {item.before ?? 'Не задано'}
                      </span>
                      <span>
                        <span className="block text-xs text-muted">После</span>
                        {item.after ?? 'Не задано'}
                      </span>
                    </dd>
                  </div>
                ))}
              </dl>
              {selected.alertId && (
                <p className="mt-4 text-xs text-muted">Обращение: {selected.alertId}</p>
              )}
              {selected.visitId && (
                <Button
                  className="mt-5"
                  onClick={() => {
                    const visitId = selected.visitId;
                    setSelected(null);
                    if (visitId) openVisit(visitId);
                  }}
                >
                  Открыть связанную заявку
                </Button>
              )}
            </Dialog>
          )
        }
      </VisitAssignment>
    </div>
  );
}
