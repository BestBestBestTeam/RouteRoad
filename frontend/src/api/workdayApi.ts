import { createDemoWorkdayApi } from '@/api/mock/demoWorkdayApi';
import { readDemoApiSettings } from '@/config/demoApi';

export type { WorkdayApi, VisitDetails } from '@/types/workdayApi';

const settings = import.meta.env.DEV ? readDemoApiSettings(import.meta.env) : undefined;

// Replace this adapter with Java HTTP calls after agreeing the contract.
export const workdayApi = createDemoWorkdayApi(
  settings
    ? {
        latencyMs: settings.latencyMs,
        failOperation: (operation, attempt) =>
          attempt === 1 && settings.failOnce.includes(operation)
            ? `Тестовая ошибка ${operation}. Повторите операцию.`
            : undefined,
      }
    : undefined,
);
