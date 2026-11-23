import { useState } from 'react';
import { workdayApi } from '@/api/workdayApi';
import { usePagedQuery } from '@/hooks/usePagedQuery';
import { EmptyState } from '@/components/ui/EmptyState';
import { PageHeader } from '@/components/ui/PageHeader';
import { WorkdayFeedback } from '@/components/dispatcher/WorkdayFeedback';
import { VisitAssignment } from '@/components/dispatcher/VisitAssignment';
import { AlertCountdown } from '@/components/dispatcher/AlertCountdown';
import { Button } from '@/components/ui/Button';
import { FilterField, inputClass, Pagination, QueryFeedback } from '@/components/ui/QueryControls';
import { useWorkdayStore } from '@/stores/workdayStore';
import type { AlertQuery } from '@/types/workdayQuery';
import { DEFAULT_ALERT_QUERY } from '@/utils/workdayQuery';
import { formatEventDate } from '@/utils/history';

export default function AlertsPage() {
  const visits = useWorkdayStore((s) => s.visits);
  const reps = useWorkdayStore((s) => s.representatives);
  const [query, setQuery] = useState<AlertQuery>({ ...DEFAULT_ALERT_QUERY });
  const result = usePagedQuery(workdayApi.listAlerts, query);
  const change = (patch: Partial<AlertQuery>) => setQuery((q) => ({ ...q, ...patch, page: 1 }));
  const data = result.data;
  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader
        title="Требуют внимания"
        description="Задержки и отказы представителей: причина, влияние и срок реакции"
      />
      <WorkdayFeedback />
      <div className="grid gap-4 rounded-2xl bg-white p-5 sm:grid-cols-3">
        <FilterField label="Поиск по алертам">
          <input
            type="search"
            value={query.search}
            placeholder="Заявка, представитель или причина"
            onChange={(e) => change({ search: e.target.value })}
            className={inputClass}
          />
        </FilterField>
        <FilterField label="Состояние алерта">
          <select
            value={query.status}
            onChange={(e) => change({ status: e.target.value as AlertQuery['status'] })}
            className={inputClass}
          >
            <option value="all">Все обращения</option>
            <option value="open">Открытые</option>
            <option value="resolved">Обработанные</option>
          </select>
        </FilterField>
        <FilterField label="Важность алерта">
          <select
            value={query.severity}
            onChange={(e) => change({ severity: e.target.value as AlertQuery['severity'] })}
            className={inputClass}
          >
            <option value="all">Любая важность</option>
            <option value="warning">Задержка</option>
            <option value="critical">Отказ</option>
          </select>
        </FilterField>
      </div>
      <QueryFeedback loading={result.loading} error={result.error} retry={result.refetch} />
      {data && (
        <>
          {data.total === 0 && (
            <EmptyState
              icon="alert"
              title="Нет предупреждений"
              description="Здесь появятся сообщения о задержке или отказе со страницы встречи представителя. Проверьте также выбранные фильтры."
            />
          )}
          <VisitAssignment>
            {(openVisit) => (
              <div className="space-y-4">
                {data.items.map((alert) => {
                  const visit = visits.find((v) => v.id === alert.visitId);
                  return (
                    <article
                      key={alert.id}
                      className="grid gap-5 rounded-2xl bg-white p-5 lg:grid-cols-[minmax(0,1fr)_260px]"
                    >
                      <div>
                        <div className="flex flex-wrap items-center gap-3">
                          <h2 className="text-lg font-semibold">{alert.title}</h2>
                          <span
                            className={`rounded-lg px-2 py-1 text-xs ${alert.status === 'resolved' ? 'bg-canvas text-muted' : 'bg-red-50 text-danger'}`}
                          >
                            {alert.status === 'resolved'
                              ? 'Обработано'
                              : alert.severity === 'critical'
                                ? 'Высокий приоритет'
                                : 'Нужна реакция'}
                          </span>
                        </div>
                        <p className="mt-3 text-sm font-medium">
                          Заявка #{visit?.number} · {visit?.address}
                        </p>
                        <p className="mt-2 text-xs text-muted">
                          {visit?.client} · окно {visit?.time}–{visit?.endTime}
                        </p>
                        <p className="mt-3 text-sm">
                          {reps.find((r) => r.id === alert.representativeId)?.name}
                        </p>
                        <p className="mt-1 text-xs text-muted">
                          Сообщение от {formatEventDate(alert.createdAt)}
                        </p>
                        <h3 className="mt-4 text-sm font-semibold">Причина</h3>
                        <p className="mt-2 text-sm leading-relaxed">{alert.reasonText}</p>
                        <h3 className="mt-4 text-sm font-semibold">Что затронуто</h3>
                        <ul className="mt-2 list-disc space-y-1 pl-5 text-xs leading-relaxed text-muted">
                          {alert.impact.map((line) => (
                            <li key={line}>{line}</li>
                          ))}
                        </ul>
                        {alert.resolution && (
                          <div className="mt-4 rounded-xl bg-accent-soft p-4 text-sm">
                            <p className="font-semibold">Решение</p>
                            <p className="mt-2 leading-relaxed">{alert.resolution.reason}</p>
                            <p className="mt-2 text-xs text-muted">
                              {alert.resolution.actor.name} ·{' '}
                              {formatEventDate(alert.resolution.resolvedAt)}
                            </p>
                          </div>
                        )}
                      </div>
                      <div className="space-y-3">
                        {alert.status === 'open' && (
                          <AlertCountdown
                            deadlineAt={alert.deadlineAt}
                            serverNow={data.serverNow}
                            receivedAt={result.receivedAt}
                          />
                        )}
                        <Button
                          variant={alert.status === 'open' ? 'primary' : 'secondary'}
                          className="w-full"
                          onClick={() => openVisit(alert.visitId)}
                        >
                          {alert.status === 'open' ? 'Выбрать представителя' : 'Открыть заявку'}
                        </Button>
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
          </VisitAssignment>
          <Pagination {...data} onPage={(page) => setQuery((q) => ({ ...q, page }))} />
        </>
      )}
    </div>
  );
}
