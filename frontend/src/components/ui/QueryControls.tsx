import type { ReactNode } from 'react';
import { Button } from './Button';
export const inputClass =
  'min-h-11 w-full rounded-xl border border-line bg-white px-3 text-sm text-ink';
export function FilterField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="grid min-w-0 gap-2 text-xs text-muted">
      <span>{label}</span>
      {children}
    </label>
  );
}
export function QueryFeedback({
  loading,
  error,
  retry,
  loadingMessage = 'Загружаем список…',
}: {
  loading: boolean;
  error?: string;
  retry: () => void;
  loadingMessage?: string;
}) {
  if (error)
    return (
      <div
        role="alert"
        className="flex flex-wrap items-center gap-3 rounded-xl bg-red-50 p-4 text-sm text-danger"
      >
        {error}
        <Button variant="secondary" onClick={retry}>
          Повторить загрузку
        </Button>
      </div>
    );
  return loading ? (
    <p role="status" className="p-4 text-sm text-muted">
      {loadingMessage}
    </p>
  ) : null;
}
export function Pagination({
  total,
  page,
  pageSize,
  onPage,
}: {
  total: number;
  page: number;
  pageSize: number;
  onPage: (page: number) => void;
}) {
  const pages = Math.max(1, Math.ceil(total / pageSize));
  return (
    <div
      className="flex flex-wrap items-center justify-between gap-4 rounded-xl bg-white p-4 text-sm"
      aria-label="Пагинация"
    >
      <p>
        Найдено: {total} · Страница {page} из {pages}
      </p>
      <div className="flex flex-wrap items-center gap-2">
        <Button
          variant="secondary"
          disabled={page <= 1}
          onClick={() => onPage(page - 1)}
          aria-label="Предыдущая страница"
        >
          ←
        </Button>
        <Button
          variant="secondary"
          disabled={page >= pages}
          onClick={() => onPage(page + 1)}
          aria-label="Следующая страница"
        >
          →
        </Button>
      </div>
    </div>
  );
}
