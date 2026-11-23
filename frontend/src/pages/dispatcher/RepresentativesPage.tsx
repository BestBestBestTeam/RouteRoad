import { useState } from 'react';
import { EmptyState } from '@/components/ui/EmptyState';
import { PageHeader } from '@/components/ui/PageHeader';
import { RepresentativeStrip } from '@/components/dispatcher/RepresentativeStrip';
import { RepresentativeSchedule } from '@/components/dispatcher/RepresentativeSchedule';
import { WorkdayFeedback } from '@/components/dispatcher/WorkdayFeedback';
import { FilterField, inputClass } from '@/components/ui/QueryControls';
import { useWorkdayStore } from '@/stores/workdayStore';
import type { RepresentativeQuery } from '@/types/workdayQuery';
import { DEFAULT_REPRESENTATIVE_QUERY } from '@/utils/workdayQuery';
export default function RepresentativesPage() {
  const selectedId = useWorkdayStore((s) => s.selectedRepresentativeId);
  const select = useWorkdayStore((s) => s.selectRepresentative);
  const [query, setQuery] = useState<RepresentativeQuery>({ ...DEFAULT_REPRESENTATIVE_QUERY });
  const change = (patch: Partial<RepresentativeQuery>) => {
    select('all');
    setQuery((q) => ({ ...q, ...patch, page: 1 }));
  };
  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader
        title="Представители"
        description="Команда и назначения на текущий день"
      ></PageHeader>
      <WorkdayFeedback />
      <div className="grid gap-4 rounded-2xl bg-white p-5 sm:grid-cols-3">
        <FilterField label="Поиск по представителям">
          <input
            type="search"
            value={query.search}
            placeholder="Имя или фамилия"
            onChange={(e) => change({ search: e.target.value })}
            className={inputClass}
          />
        </FilterField>
        <FilterField label="Состояние представителя">
          <select
            value={query.availability}
            onChange={(e) =>
              change({ availability: e.target.value as RepresentativeQuery['availability'] })
            }
            className={inputClass}
          >
            <option value="all">Все состояния</option>
            <option value="available">Без открытых обращений</option>
            <option value="needs-help">Нужна помощь</option>
          </select>
        </FilterField>
        <FilterField label="Загрузка представителя">
          <select
            value={query.workload}
            onChange={(e) =>
              change({ workload: e.target.value as RepresentativeQuery['workload'] })
            }
            className={inputClass}
          >
            <option value="all">Любая загрузка</option>
            <option value="assigned">Есть незавершённые встречи</option>
            <option value="unassigned">Нет незавершённых встреч</option>
          </select>
        </FilterField>
      </div>
      <RepresentativeStrip query={query} selectedId={selectedId} onSelect={select} />
      {selectedId !== 'all' ? (
        <RepresentativeSchedule key={selectedId} id={selectedId} />
      ) : (
        <EmptyState
          icon="users"
          title="Выберите представителя"
          description="Нажмите на карточку, чтобы открыть его назначения на сегодня."
        />
      )}
    </div>
  );
}
