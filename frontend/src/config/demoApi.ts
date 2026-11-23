import type { DemoOperation } from '@/types/workdayApi';

const operations: readonly DemoOperation[] = [
  'loadRequests',
  'buildRoutes',
  'listRequests',
  'listRepresentatives',
  'listHistory',
  'listAlerts',
  'getVisitDetails',
  'setVisitStatus',
  'reassignVisit',
  'reportDelay',
  'refuseVisit',
];

interface DemoApiSettings {
  latencyMs: number;
  failOnce: DemoOperation[];
}

type DemoApiEnvironment = Pick<ImportMetaEnv, 'VITE_DEMO_LATENCY_MS' | 'VITE_DEMO_FAIL_ONCE'>;

function isDemoOperation(value: string): value is DemoOperation {
  return operations.some((operation) => operation === value);
}

/** Called only in Vite development; production uses the ordinary demo defaults. */
export function readDemoApiSettings(environment: DemoApiEnvironment): DemoApiSettings {
  const latency = environment.VITE_DEMO_LATENCY_MS?.trim() ?? '';
  const latencyMs = latency === '' ? 150 : Number(latency);
  if (
    (latency !== '' && !/^\d+$/.test(latency)) ||
    !Number.isInteger(latencyMs) ||
    latencyMs < 0 ||
    latencyMs > 10_000
  )
    throw new Error('VITE_DEMO_LATENCY_MS: укажите целое число от 0 до 10000.');

  const failOnce: DemoOperation[] = [];
  for (const value of environment.VITE_DEMO_FAIL_ONCE?.split(',') ?? []) {
    const operation = value.trim();
    if (operation === '') continue;
    if (!isDemoOperation(operation))
      throw new Error(
        `VITE_DEMO_FAIL_ONCE: неизвестная операция «${operation}». Доступны: ${operations.join(', ')}.`,
      );
    if (!failOnce.includes(operation)) failOnce.push(operation);
  }
  return { latencyMs, failOnce };
}
