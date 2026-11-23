import { create } from 'zustand';
import { useDemoSessionStore } from '@/stores/sessionStore';
import { workdayApi } from '@/api/workdayApi';
import type { WorkdayApi } from '@/types/workdayApi';
import type {
  MutationContext,
  OperationStatus,
  VisitStatus,
  WorkdaySnapshot,
} from '@/types/workday';
import { createEmptyWorkdaySnapshot } from '@/utils/workday';

export interface WorkdayStore extends WorkdaySnapshot {
  revision: number;
  selectedRepresentativeId: string;
  searchQuery: string;
  notice: string | null;
  loadStatus: OperationStatus;
  planStatus: OperationStatus;
  error: string | null;
  pendingVisitId: string | null;
  selectRepresentative: (id: string) => void;
  setSearchQuery: (query: string) => void;
  setNotice: (message: string | null) => void;
  loadRequests: () => Promise<void>;
  buildRoutes: () => Promise<void>;
  setVisitStatus: (id: string, status: VisitStatus) => Promise<void>;
  reassignVisit: (id: string, representativeId: string, reason?: string) => Promise<void>;
  reportDelay: (id: string, minutes: number) => Promise<void>;
  refuseVisit: (id: string, reason: string) => Promise<void>;
  resetDay: () => void;
}

function errorMessage(error: unknown): string {
  return error instanceof Error
    ? error.message
    : 'Не удалось выполнить демонстрационную операцию. Повторите попытку.';
}

function isBusy(state: WorkdayStore): boolean {
  return (
    state.loadStatus === 'loading' ||
    state.planStatus === 'loading' ||
    state.pendingVisitId !== null
  );
}

export function createWorkdayStore(
  api: WorkdayApi,
  context: () => MutationContext | undefined = () => undefined,
) {
  let generation = 0;
  return create<WorkdayStore>((set, get) => {
    async function changeVisit(
      id: string,
      operation: () => Promise<WorkdaySnapshot>,
      message: string,
    ): Promise<void> {
      if (isBusy(get())) return;
      const operationGeneration = generation;
      set({ pendingVisitId: id, error: null, notice: null });
      try {
        const snapshot = await operation();
        if (operationGeneration !== generation) return;
        set((state) => ({
          ...snapshot,
          revision: state.revision + 1,
          pendingVisitId: null,
          error: null,
          notice: message,
        }));
      } catch (error) {
        if (operationGeneration !== generation) return;
        set({ pendingVisitId: null, error: errorMessage(error) });
      }
    }

    return {
      ...createEmptyWorkdaySnapshot(),
      revision: 0,
      selectedRepresentativeId: 'all',
      searchQuery: '',
      notice: null,
      loadStatus: 'idle',
      planStatus: 'idle',
      error: null,
      pendingVisitId: null,
      selectRepresentative: (id) => set({ selectedRepresentativeId: id || 'all' }),
      setSearchQuery: (searchQuery) => set({ searchQuery }),
      setNotice: (notice) => set({ notice }),
      loadRequests: async () => {
        if (isBusy(get()) || get().loadStatus === 'success') return;
        const operationGeneration = generation;
        set({ loadStatus: 'loading', planStatus: 'idle', error: null, notice: null });
        try {
          const snapshot = await api.loadRequests(context());
          if (operationGeneration !== generation) return;
          set({
            ...snapshot,
            revision: get().revision + 1,
            loadStatus: 'success',
            planStatus: snapshot.routes.length > 0 ? 'success' : 'idle',
            error: null,
            notice: 'Заявки загружены. Можно построить маршруты.',
          });
        } catch (error) {
          if (operationGeneration !== generation) return;
          set({ loadStatus: 'error', error: errorMessage(error) });
        }
      },
      buildRoutes: async () => {
        if (isBusy(get())) return;
        if (get().visits.length === 0) {
          set({ planStatus: 'error', error: 'Сначала загрузите заявки для рабочего дня.' });
          return;
        }
        if (get().planStatus === 'success') return;
        const operationGeneration = generation;
        set({ planStatus: 'loading', error: null, notice: null });
        try {
          const snapshot = await api.buildRoutes(context());
          if (operationGeneration !== generation) return;
          set({
            ...snapshot,
            revision: get().revision + 1,
            planStatus: 'success',
            error: null,
            notice: 'Тестовые маршруты готовы.',
          });
        } catch (error) {
          if (operationGeneration !== generation) return;
          set({ planStatus: 'error', error: errorMessage(error) });
        }
      },
      setVisitStatus: async (id, status) => {
        if (get().visits.find((visit) => visit.id === id)?.status === status) return;
        await changeVisit(
          id,
          () => api.setVisitStatus(id, status, context()),
          'Статус встречи обновлён локально.',
        );
      },
      reassignVisit: async (id, representativeId, reason) => {
        if (get().visits.find((visit) => visit.id === id)?.representativeId === representativeId)
          return;
        await changeVisit(
          id,
          () => api.reassignVisit(id, representativeId, reason, context()),
          'Назначение обновлено.',
        );
      },
      reportDelay: async (id, minutes) =>
        changeVisit(
          id,
          () => api.reportDelay(id, minutes, context()),
          'Сообщение о задержке добавлено в локальные алерты диспетчера.',
        ),
      refuseVisit: async (id, reason) =>
        changeVisit(
          id,
          () => api.refuseVisit(id, reason, context()),
          'Отказ добавлен в локальные алерты. Назначение сохранено до решения диспетчера.',
        ),
      resetDay: () => {
        generation += 1;
        api.reset();
        set({
          ...createEmptyWorkdaySnapshot(),
          revision: get().revision + 1,
          selectedRepresentativeId: 'all',
          searchQuery: '',
          notice: 'День очищен. Сначала загрузите заявки.',
          loadStatus: 'idle',
          planStatus: 'idle',
          error: null,
          pendingVisitId: null,
        });
      },
    };
  });
}

export const useWorkdayStore = createWorkdayStore(workdayApi, () => {
  const user = useDemoSessionStore.getState().user;
  return user
    ? {
        actor: { id: user.id, name: user.name, role: user.role },
        representativeId: user.representativeId,
      }
    : undefined;
});
