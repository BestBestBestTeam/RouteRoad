import type { DemoOperation, WorkdayApi } from '@/types/workdayApi';
import { createLoadedDemoSnapshot, demoAssignments } from './data';
import type {
  AuditActor,
  HistoryEntry,
  MutationContext,
  RoutePlan,
  Visit,
  VisitStatus,
  WorkdaySnapshot,
} from '@/types/workday';
import type { PageResult, PageQuery } from '@/types/workdayQuery';
import {
  DEFAULT_REQUEST_QUERY,
  DEFAULT_REPRESENTATIVE_QUERY,
  DEFAULT_HISTORY_QUERY,
  DEFAULT_ALERT_QUERY,
  normalizePageQuery,
} from '@/utils/workdayQuery';
import {
  createEmptyWorkdaySnapshot,
  summarizeRepresentatives,
  visitStatusLabels,
} from '@/utils/workday';

export type { DemoOperation } from '@/types/workdayApi';
export interface DemoWorkdayApiOptions {
  latencyMs?: number;
  failOperation?: (operation: DemoOperation, attempt: number) => string | undefined;
  wait?: (milliseconds: number) => Promise<void>;
  now?: () => number;
}
const system: AuditActor = { id: 'demo-system', name: 'Демонстрационный источник', role: 'system' };
const transitions: Record<VisitStatus, readonly VisitStatus[]> = {
  new: [],
  assigned: ['en-route'],
  'en-route': ['arrived'],
  arrived: ['in-progress'],
  'in-progress': ['completed'],
  completed: [],
  cancelled: [],
  attention: [],
};
const text = (value: string) =>
  value.toLocaleLowerCase('ru').replaceAll('ё', 'е').trim().replace(/^#/, '');
const matches = (search: string, value: string) => text(value).includes(text(search));

function createRoutes(snapshot: WorkdaySnapshot): RoutePlan[] {
  const office = snapshot.office;
  if (!office) return [];
  return snapshot.representatives.flatMap((rep): RoutePlan[] => {
    const visits = snapshot.visits
      .filter((v) => v.representativeId === rep.id && v.status !== 'cancelled')
      .sort((a, b) => a.time.localeCompare(b.time));
    return visits.length
      ? [
          {
            id: `demo-route-${rep.id}`,
            representativeId: rep.id,
            visitIds: visits.map((v) => v.id),
            geometry: {
              type: 'LineString',
              coordinates: [
                [office.location.lon, office.location.lat],
                ...visits.map((v): [number, number] => [v.location.lon, v.location.lat]),
              ],
            },
          },
        ]
      : [];
  });
}

export function createDemoWorkdayApi(options: DemoWorkdayApiOptions = {}): WorkdayApi {
  let snapshot = createEmptyWorkdaySnapshot();
  let generation = 0;
  let sequence = 0;
  const now = options.now ?? (() => Date.now());
  let origin = now();
  const modelStart = Date.parse('2026-10-26T09:00:00+07:00');
  const timestamp = () => new Date(modelStart + now() - origin).toISOString();
  let attempts: Partial<Record<DemoOperation, number>> = {};
  const wait =
    options.wait ?? ((ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms)));
  const actor = (context?: MutationContext) => context?.actor ?? system;
  const repName = (id: string | null) =>
    snapshot.representatives.find((r) => r.id === id)?.name ?? 'Не назначен';

  function record(
    next: WorkdaySnapshot,
    event: Omit<HistoryEntry, 'id' | 'createdAt' | 'time'>,
  ): WorkdaySnapshot {
    const createdAt = timestamp();
    return {
      ...next,
      history: [
        {
          ...event,
          id: ++sequence,
          createdAt,
          time: new Date(createdAt).toLocaleTimeString('ru', {
            timeZone: 'Asia/Novosibirsk',
            hour: '2-digit',
            minute: '2-digit',
          }),
        },
        ...next.history,
      ],
    };
  }
  function resolveAlerts(
    next: WorkdaySnapshot,
    visitId: string,
    reason: string,
    context?: MutationContext,
  ) {
    const open = next.alerts.filter((a) => a.visitId === visitId && a.status === 'open');
    for (const alert of open) {
      next = {
        ...next,
        alerts: next.alerts.map((a) =>
          a.id === alert.id
            ? {
                ...a,
                status: 'resolved',
                resolution: { resolvedAt: timestamp(), actor: actor(context), reason },
              }
            : a,
        ),
      };
      next = record(next, {
        type: 'alert-resolved',
        actor: actor(context),
        visitId,
        alertId: alert.id,
        title: 'Обращение обработано',
        description: reason,
        reason,
        changes: [{ field: 'status', label: 'Обращение', before: 'Открыто', after: 'Обработано' }],
      });
    }
    return next;
  }
  async function request<T>(operation: DemoOperation, action: () => T): Promise<T> {
    const currentGeneration = generation;
    const attempt = (attempts[operation] ?? 0) + 1;
    attempts[operation] = attempt;
    await wait(options.latencyMs ?? 150);
    if (currentGeneration !== generation)
      throw new Error('Демонстрационная операция отменена после сброса дня.');
    const failure = options.failOperation?.(operation, attempt);
    if (failure) throw new Error(failure);
    return action();
  }
  function sharePending<Args extends unknown[], Result>(
    read: (...args: Args) => Promise<Result>,
  ): (...args: Args) => Promise<Result> {
    const pending = new Map<string, Promise<Result>>();
    return (...args) => {
      const key = `${generation}:${JSON.stringify(args)}`;
      let response = pending.get(key);
      if (!response) {
        response = read(...args);
        pending.set(key, response);
        const clear = () => {
          pending.delete(key);
        };
        // Share only a running read (including StrictMode subscribers), never a settled result.
        void response.then(clear, clear);
      }
      return response.then((result) => structuredClone(result));
    };
  }
  function execute(operation: DemoOperation, change: () => WorkdaySnapshot) {
    return request(operation, () => {
      snapshot = change();
      return structuredClone(snapshot);
    });
  }
  function list<T>(
    operation: DemoOperation,
    query: Partial<PageQuery>,
    select: () => T[],
  ): Promise<PageResult<T>> {
    return request(operation, () => {
      const items = select();
      const paging = normalizePageQuery(query);
      const page = Math.min(paging.page, Math.max(1, Math.ceil(items.length / paging.pageSize)));
      return structuredClone({
        items: items.slice((page - 1) * paging.pageSize, page * paging.pageSize),
        total: items.length,
        page,
        pageSize: paging.pageSize,
        serverNow: timestamp(),
      });
    });
  }
  function activeVisit(id: string, context?: MutationContext): Visit {
    const visit = snapshot.visits.find((v) => v.id === id);
    if (!visit || !visit.representativeId || !snapshot.routes.length)
      throw new Error('Сначала назначьте встречу и постройте маршрут.');
    if (visit.status === 'completed' || visit.status === 'cancelled')
      throw new Error('Встреча уже завершена.');
    if (
      context?.actor.role === 'representative' &&
      context.representativeId !== visit.representativeId
    )
      throw new Error('Встреча назначена другому представителю.');
    return visit;
  }
  function report(
    id: string,
    kind: 'delay' | 'refusal',
    reason: string,
    context?: MutationContext,
  ) {
    const visit = activeVisit(id, context);
    if (snapshot.alerts.some((a) => a.visitId === id && a.kind === kind && a.status === 'open'))
      return snapshot;
    const alertId = `alert-${sequence + 1}`;
    const alert = {
      id: alertId,
      visitId: id,
      representativeId: visit.representativeId!,
      kind,
      severity: kind === 'refusal' ? ('critical' as const) : ('warning' as const),
      status: 'open' as const,
      title:
        kind === 'refusal' ? 'Представитель отказался от встречи' : 'Представитель задерживается',
      reasonCode: kind === 'refusal' ? 'representative-refusal' : 'representative-delay',
      reasonText: reason,
      impact:
        kind === 'refusal'
          ? [
              'Нужен другой представитель или решение диспетчера.',
              'Текущее назначение сохранено до решения.',
            ]
          : [
              'Временное окно встречи может быть нарушено.',
              'Дорожное время и следующие встречи автоматически не пересчитывались.',
            ],
      createdAt: timestamp(),
      deadlineAt: new Date(Date.parse(timestamp()) + 10 * 60_000).toISOString(),
      resolution: null,
    };
    return record(
      { ...snapshot, alerts: [alert, ...snapshot.alerts] },
      {
        type: 'alert-created',
        actor: actor(context),
        visitId: id,
        alertId,
        title: `Заявка #${visit.number}: ${alert.title.toLocaleLowerCase('ru')}`,
        description: reason,
        reason,
        changes: [{ field: 'alert', label: 'Обращение', before: null, after: alert.title }],
      },
    );
  }

  const api: WorkdayApi = {
    loadRequests: (context) =>
      execute('loadRequests', () => {
        if (snapshot.visits.length) return snapshot;
        origin = now();
        return record(createLoadedDemoSnapshot(), {
          type: 'data-loaded',
          actor: actor(context),
          title: 'Загружено 18 заявок',
          description: 'Синтетические заявки и 6 представителей. Назначений пока нет.',
          reason: 'Загрузка демонстрационного рабочего дня',
          changes: [{ field: 'requests', label: 'Количество заявок', before: '0', after: '18' }],
        });
      }),
    buildRoutes: (context) =>
      execute('buildRoutes', () => {
        if (!snapshot.visits.length || !snapshot.office)
          throw new Error('Сначала загрузите заявки для рабочего дня.');
        if (snapshot.routes.length) return snapshot;
        const assignments = new Map(
          Object.entries(demoAssignments).flatMap(([id, ids]) =>
            ids.map((visitId) => [visitId, id] as const),
          ),
        );
        const reason =
          'Заранее заданное распределение для демонстрации UI. Оптимизация и проверка дорожного времени не выполнялись.';
        const visits = snapshot.visits.map((v) => ({
          ...v,
          representativeId: assignments.get(v.id) ?? null,
          status: 'assigned' as const,
          assignment: {
            reasonCode: 'demo-plan',
            reasonText: reason,
            assignedAt: timestamp(),
            assignedBy: actor(context).name,
          },
        }));
        let next: WorkdaySnapshot = {
          ...snapshot,
          visits,
          representatives: summarizeRepresentatives(visits, snapshot.representatives),
        };
        next = { ...next, routes: createRoutes(next) };
        for (const v of visits)
          next = record(next, {
            type: 'assignment-changed',
            actor: actor(context),
            visitId: v.id,
            title: `Назначена заявка #${v.number}`,
            description: repName(v.representativeId),
            reason,
            changes: [
              {
                field: 'representative',
                label: 'Представитель',
                before: null,
                after: repName(v.representativeId),
              },
            ],
          });
        return record(next, {
          type: 'routes-built',
          actor: actor(context),
          title: 'Построены демонстрационные маршруты',
          description: '6 маршрутов. Линии соединяют точки напрямую.',
          reason,
          changes: [{ field: 'routes', label: 'Количество маршрутов', before: '0', after: '6' }],
        });
      }),
    listRequests: (input = {}) => {
      const q = { ...DEFAULT_REQUEST_QUERY, ...input };
      return list('listRequests', q, () =>
        snapshot.visits.filter(
          (v) =>
            matches(q.search, `${v.number} ${v.client} ${v.address} ${v.product}`) &&
            (q.status === 'all' || v.status === q.status) &&
            (q.representativeId === 'all' ||
              (q.representativeId === 'unassigned'
                ? v.representativeId === null
                : v.representativeId === q.representativeId)),
        ),
      );
    },
    listRepresentatives: (input = {}) => {
      const q = { ...DEFAULT_REPRESENTATIVE_QUERY, ...input };
      return list('listRepresentatives', q, () =>
        snapshot.representatives.filter(
          (r) =>
            matches(q.search, r.name) &&
            (q.availability === 'all' ||
              (q.availability === 'needs-help'
                ? snapshot.alerts.some((a) => a.representativeId === r.id && a.status === 'open')
                : !r.needsHelp &&
                  !snapshot.alerts.some(
                    (a) => a.representativeId === r.id && a.status === 'open',
                  ))) &&
            (q.workload === 'all' ||
              (q.workload === 'assigned' ? r.total > r.completed : r.total === r.completed)),
        ),
      );
    },
    listHistory: (input = {}) => {
      const q = { ...DEFAULT_HISTORY_QUERY, ...input };
      return list('listHistory', q, () =>
        snapshot.history.filter(
          (e) =>
            matches(q.search, `${e.title} ${e.description} ${e.reason} ${e.actor.name}`) &&
            (q.type === 'all' || e.type === q.type) &&
            (q.actorRole === 'all' || e.actor.role === q.actorRole),
        ),
      );
    },
    listAlerts: (input = {}) => {
      const q = { ...DEFAULT_ALERT_QUERY, ...input };
      return list('listAlerts', q, () =>
        snapshot.alerts.filter((a) => {
          const v = snapshot.visits.find((v) => v.id === a.visitId);
          return (
            matches(
              q.search,
              `${a.title} ${a.reasonText} ${v?.number} ${v?.address} ${repName(a.representativeId)}`,
            ) &&
            (q.status === 'all' || a.status === q.status) &&
            (q.severity === 'all' || a.severity === q.severity)
          );
        }),
      );
    },
    getVisitDetails: (id) =>
      request('getVisitDetails', () => {
        const visit = snapshot.visits.find((item) => item.id === id);
        if (!visit) throw new Error('Заявка не найдена. Обновите список рабочего дня.');
        return structuredClone({
          visit,
          representativeName:
            snapshot.representatives.find((item) => item.id === visit.representativeId)?.name ??
            null,
          history: snapshot.history.filter((item) => item.visitId === id),
          alerts: snapshot.alerts.filter((item) => item.visitId === id),
        });
      }),
    setVisitStatus: (id, status, context) =>
      execute('setVisitStatus', () => {
        if (snapshot.visits.find((v) => v.id === id)?.status === status) return snapshot;
        const current = activeVisit(id, context);
        if (!transitions[current.status].includes(status))
          throw new Error('Такой переход недоступен в демонстрационном сценарии.');
        if (
          snapshot.alerts.some(
            (a) => a.visitId === id && a.kind === 'refusal' && a.status === 'open',
          )
        )
          throw new Error('Дождитесь решения диспетчера по отказу.');
        const visits = snapshot.visits.map((v) => (v.id === id ? { ...v, status } : v));
        let next = record(
          {
            ...snapshot,
            visits,
            representatives: summarizeRepresentatives(visits, snapshot.representatives),
          },
          {
            type: 'status-changed',
            actor: actor(context),
            visitId: id,
            title: `Встреча #${current.number}: ${visitStatusLabels[status]}`,
            description: 'Локальное изменение статуса.',
            reason: 'Действие представителя в демонстрационном UI',
            changes: [
              {
                field: 'status',
                label: 'Статус',
                before: visitStatusLabels[current.status],
                after: visitStatusLabels[status],
              },
            ],
          },
        );
        if (status === 'completed')
          next = resolveAlerts(next, id, 'Встреча завершена представителем.', context);
        return next;
      }),
    reassignVisit: (id, representativeId, input, context) =>
      execute('reassignVisit', () => {
        const current = activeVisit(id);
        const rep = snapshot.representatives.find((r) => r.id === representativeId);
        if (!rep || rep.needsHelp) throw new Error('Представитель недоступен для назначения.');
        if (current.representativeId === representativeId) return snapshot;
        const reason = input?.trim() || 'Ручное назначение диспетчером';
        if (reason.length > 500) throw new Error('Причина должна содержать не более 500 символов.');
        const visits = snapshot.visits.map((v) =>
          v.id === id
            ? {
                ...v,
                representativeId,
                status: 'assigned' as const,
                assignment: {
                  reasonCode: 'dispatcher-manual',
                  reasonText: reason,
                  assignedAt: timestamp(),
                  assignedBy: actor(context).name,
                },
              }
            : v,
        );
        let next = {
          ...snapshot,
          visits,
          representatives: summarizeRepresentatives(visits, snapshot.representatives),
        };
        next = record(
          { ...next, routes: createRoutes(next) },
          {
            type: 'assignment-changed',
            actor: actor(context),
            visitId: id,
            title: `Изменено назначение #${current.number}`,
            description: rep.name,
            reason,
            changes: [
              {
                field: 'representative',
                label: 'Представитель',
                before: repName(current.representativeId),
                after: rep.name,
              },
              {
                field: 'status',
                label: 'Статус',
                before: visitStatusLabels[current.status],
                after: 'Назначена',
              },
            ],
          },
        );
        return resolveAlerts(next, id, `Заявка передана: ${rep.name}. ${reason}`, context);
      }),
    reportDelay: (id, minutes, context) =>
      execute('reportDelay', () => {
        if (!Number.isInteger(minutes) || minutes < 1 || minutes > 120)
          throw new Error('Укажите задержку от 1 до 120 минут.');
        return report(
          id,
          'delay',
          `Задержка на ${minutes} минут по сообщению представителя.`,
          context,
        );
      }),
    refuseVisit: (id, reason, context) =>
      execute('refuseVisit', () => {
        if (reason.trim().length < 3 || reason.trim().length > 500)
          throw new Error('Укажите причину отказа: от 3 до 500 символов.');
        return report(id, 'refusal', reason.trim(), context);
      }),
    reset: () => {
      generation++;
      sequence = 0;
      attempts = {};
      origin = now();
      snapshot = createEmptyWorkdaySnapshot();
    },
  };
  return {
    ...api,
    listRequests: sharePending(api.listRequests),
    listRepresentatives: sharePending(api.listRepresentatives),
    listHistory: sharePending(api.listHistory),
    listAlerts: sharePending(api.listAlerts),
    getVisitDetails: sharePending(api.getVisitDetails),
  };
}
