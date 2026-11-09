import { Avatar } from '@/components/ui/Avatar';
import type { Representative } from '@/types/workday';

interface RepresentativeCardsProps {
  representatives: Representative[];
  selectedId: string;
  onSelect: (id: string) => void;
  countLabel?: string;
}

export function RepresentativeCards({
  representatives,
  selectedId,
  onSelect,
  countLabel,
}: RepresentativeCardsProps) {
  return (
    <section aria-labelledby="representatives-heading">
      <div className="mb-3 flex items-baseline gap-3">
        <h2 id="representatives-heading" className="text-base font-semibold">
          Представители
        </h2>
        <p className="text-xs text-muted">{countLabel ?? `${representatives.length} на смене`}</p>
      </div>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
        {representatives.map((representative) => (
          <button
            key={representative.id}
            type="button"
            aria-label={`Назначения: ${representative.name}`}
            aria-pressed={selectedId === representative.id}
            onClick={() => onSelect(representative.id)}
            className={`rounded-[14px] border bg-white p-3.5 text-left transition-colors hover:border-accent ${selectedId === representative.id ? 'border-accent' : 'border-transparent'}`}
          >
            <div className="flex items-center gap-2.5">
              <Avatar
                initials={representative.initials}
                tone={representative.tone}
                className="size-8 shrink-0 text-[10px]"
              />
              <div className="min-w-0">
                <p className="truncate text-xs font-semibold">{representative.shortName}</p>
                <p
                  className={`mt-1 truncate text-[10px] ${representative.needsHelp ? 'text-danger' : 'text-muted'}`}
                >
                  {representative.needsHelp
                    ? 'Нужна помощь'
                    : `${representative.completed} из ${representative.total} встреч`}
                </p>
              </div>
            </div>
            <progress
              aria-label={`Завершено встреч: ${representative.name}`}
              value={representative.completed}
              max={Math.max(1, representative.total)}
              className={`mt-4 block h-1 w-full appearance-none overflow-hidden rounded-full [&::-webkit-progress-bar]:bg-canvas [&::-webkit-progress-value]:rounded-full [&::-moz-progress-bar]:rounded-full ${representative.needsHelp ? '[&::-webkit-progress-value]:bg-danger [&::-moz-progress-bar]:bg-danger' : selectedId === representative.id ? '[&::-webkit-progress-value]:bg-accent [&::-moz-progress-bar]:bg-accent' : '[&::-webkit-progress-value]:bg-gray-300 [&::-moz-progress-bar]:bg-gray-300'}`}
            />
            <p className="mt-2 text-[10px] text-muted">
              {representative.total > 0 ? 'Назначения на сегодня' : 'Пока без назначений'}
            </p>
          </button>
        ))}
      </div>
    </section>
  );
}
