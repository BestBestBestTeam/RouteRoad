import type { getWorkdayCounts } from '@/utils/workday';

export function DaySummary({ counts }: { counts: ReturnType<typeof getWorkdayCounts> }) {
  const metrics = [
    { label: 'Заявок на сегодня', value: counts.total, description: 'весь рабочий день' },
    {
      label: 'Назначено',
      value: counts.assigned,
      description: counts.total ? `из ${counts.total} заявок` : 'пока нет заявок',
    },
    { label: 'Завершено', value: counts.completed, description: 'успешных встреч' },
    {
      label: 'Требуют внимания',
      value: counts.attention,
      description: 'нужно разобрать',
      warning: true,
    },
  ];
  return (
    <dl className="grid grid-cols-2 gap-y-5 rounded-2xl bg-white px-5 py-3.5 lg:grid-cols-4 lg:gap-y-0">
      {metrics.map((metric, index) => (
        <div
          key={metric.label}
          className={`min-w-0 ${index > 0 ? 'lg:border-l lg:border-line lg:pl-6' : ''}`}
        >
          <dt className="text-xs text-muted">{metric.label}</dt>
          <dd className="mt-1.5 flex flex-wrap items-baseline gap-x-3 gap-y-1">
            <span
              className={`text-[30px] font-semibold leading-none tracking-tight ${metric.warning ? 'text-danger' : 'text-ink'}`}
            >
              {metric.value}
            </span>
            <span className="text-[11px] text-muted">{metric.description}</span>
          </dd>
        </div>
      ))}
    </dl>
  );
}
