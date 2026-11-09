import { useCallback, useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';
import { Avatar } from '@/components/ui/Avatar';
import { Button } from '@/components/ui/Button';
import { Icon } from '@/components/ui/Icon';
import {
  useInfiniteRepresentatives,
  type RepresentativeFetcher,
} from '@/hooks/useInfiniteRepresentatives';
import type { RepresentativeQuery } from '@/types/workdayQuery';
import { EmptyState } from '@/components/ui/EmptyState';

interface RepresentativeStripProps {
  query: RepresentativeQuery;
  selectedId: string;
  onSelect: (id: string) => void;
  fetcher?: RepresentativeFetcher;
}

export function RepresentativeStrip({
  query,
  selectedId,
  onSelect,
  fetcher,
}: RepresentativeStripProps) {
  const result = useInfiniteRepresentatives(query, fetcher);
  const viewport = useRef<HTMLDivElement>(null);
  const descriptionId = useId();
  const headingId = useId();
  const [edges, setEdges] = useState({ left: false, right: false });
  const updateEdges = useCallback(() => {
    const element = viewport.current;
    if (!element) return;
    const left = element.scrollLeft > 1;
    const right = element.scrollLeft + element.clientWidth < element.scrollWidth - 1;
    setEdges((current) =>
      current.left === left && current.right === right ? current : { left, right },
    );
  }, []);

  useEffect(() => {
    const element = viewport.current;
    if (!element) return;
    const observer = new ResizeObserver(updateEdges);
    observer.observe(element);
    const track = element.firstElementChild;
    if (track) observer.observe(track);
    return () => observer.disconnect();
  }, [updateEdges, result.items.length]);

  useEffect(() => {
    viewport.current?.scrollTo({ left: 0 });
  }, [result.resetKey, fetcher]);

  useEffect(() => {
    if (result.loading || result.error || result.hasMore || selectedId === 'all') return;
    if (!result.items.some((representative) => representative.id === selectedId)) onSelect('all');
  }, [result.loading, result.error, result.hasMore, result.items, selectedId, onSelect]);

  function scroll(direction: -1 | 1) {
    const element = viewport.current;
    if (!element) return;
    element.scrollBy({
      left: direction * Math.max(220, element.clientWidth * 0.75),
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
    });
  }

  function navigateCards(event: KeyboardEvent<HTMLDivElement>) {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    const buttons = Array.from(
      event.currentTarget.querySelectorAll<HTMLButtonElement>('[data-representative-card]'),
    );
    const current = buttons.findIndex((button) => button === event.target);
    if (current < 0 || buttons.length === 0) return;
    event.preventDefault();
    const index =
      event.key === 'Home'
        ? 0
        : event.key === 'End'
          ? buttons.length - 1
          : Math.max(
              0,
              Math.min(buttons.length - 1, current + (event.key === 'ArrowRight' ? 1 : -1)),
            );
    buttons[index]?.focus({ preventScroll: true });
    buttons[index]?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  }

  return (
    <section className="min-w-0 space-y-3" aria-labelledby={headingId}>
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-baseline gap-3">
          <h2 id={headingId} className="text-base font-semibold">
            Представители
          </h2>
          <p className="text-xs text-muted" aria-live="polite">
            Показано {result.items.length} из {result.total}
          </p>
        </div>
        <div className="flex gap-2">
          <Button
            variant="secondary"
            className="size-10 min-h-10 p-0"
            aria-label="Прокрутить представителей влево"
            disabled={!edges.left}
            onClick={() => scroll(-1)}
          >
            <Icon name="back" className="size-4" />
          </Button>
          <Button
            variant="secondary"
            className="size-10 min-h-10 p-0"
            aria-label="Прокрутить представителей вправо"
            disabled={!edges.right}
            onClick={() => scroll(1)}
          >
            <Icon name="back" className="size-4 rotate-180" />
          </Button>
        </div>
      </header>
      <p id={descriptionId} className="sr-only">
        Карточки расположены в одну строку. Tab и стрелки влево и вправо перемещают фокус между
        карточками. Home и End переходят к краям загруженного списка. Enter открывает назначения.
      </p>
      <div
        ref={viewport}
        tabIndex={0}
        role="region"
        aria-label="Лента представителей"
        aria-describedby={descriptionId}
        aria-busy={result.loading}
        className="max-w-full overflow-x-auto rounded-2xl py-1 outline-offset-2"
        onKeyDown={navigateCards}
        onScroll={(event) => {
          updateEdges();
          const element = event.currentTarget;
          if (
            element.scrollLeft > 0 &&
            element.scrollWidth - element.scrollLeft - element.clientWidth < 160
          )
            result.loadMore();
        }}
      >
        <div className="flex w-max min-w-full gap-3 px-0.5 pb-2">
          {result.items.map((representative) => (
            <button
              key={representative.id}
              type="button"
              data-representative-card
              aria-label={`Назначения: ${representative.name}`}
              aria-pressed={selectedId === representative.id}
              onClick={() => onSelect(representative.id)}
              className={`flex w-[220px] shrink-0 items-center gap-3 rounded-[14px] border bg-white px-3.5 py-3 text-left transition-colors hover:border-accent ${selectedId === representative.id ? 'border-accent' : 'border-line'}`}
            >
              <Avatar
                initials={representative.initials}
                tone={representative.tone}
                className="size-9 shrink-0 text-[11px]"
              />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-xs font-semibold" title={representative.name}>
                  {representative.name}
                </span>
                <span
                  className={`mt-1.5 block truncate text-[11px] ${representative.needsHelp ? 'text-danger' : 'text-muted'}`}
                >
                  {representative.needsHelp
                    ? 'Нужна помощь'
                    : `${representative.completed} из ${representative.total} встреч`}
                </span>
              </span>
            </button>
          ))}
        </div>
      </div>
      {result.error && (
        <div
          role="alert"
          className="flex flex-wrap items-center gap-3 rounded-xl bg-red-50 p-3 text-sm text-danger"
        >
          <p className="min-w-0 flex-1">{result.error}</p>
          <Button variant="secondary" onClick={result.retry}>
            Повторить загрузку представителей
          </Button>
        </div>
      )}
      {result.loading ? (
        <p role="status" className="px-1 text-xs text-muted">
          {result.items.length > 0 ? 'Загружаем ещё представителей…' : 'Загружаем представителей…'}
        </p>
      ) : !result.error && result.items.length === 0 ? (
        <EmptyState
          icon="users"
          title="Представители не найдены"
          description="Загрузите рабочий день или измените фильтры."
        />
      ) : (
        !result.error && (
          <footer className="flex flex-wrap items-center justify-between gap-3 px-1 text-xs text-muted">
            <p>
              {result.hasMore
                ? 'Прокрутите ленту вправо, чтобы увидеть остальных.'
                : 'Конец списка'}
            </p>
            {result.hasMore && (
              <Button variant="secondary" onClick={result.loadMore}>
                Показать ещё представителей
              </Button>
            )}
          </footer>
        )
      )}
    </section>
  );
}
