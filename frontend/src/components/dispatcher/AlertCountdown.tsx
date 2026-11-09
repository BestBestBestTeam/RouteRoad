import { useEffect, useState } from 'react';
import { formatEventDate } from '@/utils/history';
export function AlertCountdown({
  deadlineAt,
  serverNow,
  receivedAt,
}: {
  deadlineAt: string;
  serverNow: string;
  receivedAt: number;
}) {
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  const remaining = Math.max(
    0,
    Math.ceil(
      (Date.parse(deadlineAt) - Date.parse(serverNow) - Math.max(0, now - receivedAt)) / 1000,
    ),
  );
  const label = `${Math.floor(remaining / 60)
    .toString()
    .padStart(2, '0')}:${(remaining % 60).toString().padStart(2, '0')}`;
  return (
    <div className="rounded-xl border border-line bg-white p-4">
      <p className="text-xs text-muted">Срок реакции · до {formatEventDate(deadlineAt)}</p>
      <p
        role="timer"
        aria-label="Время на реакцию"
        className={`mt-2 text-2xl font-semibold tabular-nums ${remaining ? 'text-ink' : 'text-danger'}`}
      >
        {label}
      </p>
      <p className="mt-2 text-xs leading-relaxed text-muted">
        {remaining
          ? 'Ожидается решение диспетчера.'
          : 'Срок реакции истёк. Требуется решение диспетчера.'}{' '}
        Таймер не меняет назначение автоматически.
      </p>
    </div>
  );
}
