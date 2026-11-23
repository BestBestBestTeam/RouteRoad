import { useEffect, useRef, useState } from 'react';
import { Avatar } from '@/components/ui/Avatar';
import { Button } from '@/components/ui/Button';
import { Icon } from '@/components/ui/Icon';
import type { HistoryEntry, Representative, Visit, WorkdayAlert } from '@/types/workday';
import { VisitStatusBadge } from '@/components/workday/VisitStatusBadge';
import { formatEventDate } from '@/utils/history';

interface ReassignDrawerProps {
  visit: Visit;
  representatives: Representative[];
  representativeName: string | null;
  history: HistoryEntry[];
  alerts: WorkdayAlert[];
  onClose: () => void;
  onAssign: (representativeId: string, reason: string) => Promise<void>;
  isSubmitting?: boolean;
  error?: string | null;
}

export function ReassignDrawer({
  visit,
  representatives,
  representativeName,
  history,
  alerts,
  onClose,
  onAssign,
  isSubmitting = false,
  error = null,
}: ReassignDrawerProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [reason, setReason] = useState('');
  const candidates = representatives.filter((representative) => !representative.needsHelp);
  const [selectedId, setSelectedId] = useState(() =>
    candidates.some((representative) => representative.id === visit.representativeId)
      ? (visit.representativeId ?? '')
      : (candidates[0]?.id ?? ''),
  );
  const isFinished = visit.status === 'completed' || visit.status === 'cancelled';
  const needsPlan = visit.status === 'new';
  const selectedRepresentative = candidates.find(
    (representative) => representative.id === selectedId,
  );
  const canAssign = Boolean(
    selectedRepresentative &&
    !isFinished &&
    !needsPlan &&
    !isSubmitting &&
    selectedRepresentative.id !== visit.representativeId,
  );
  useEffect(() => {
    const dialog = dialogRef.current;
    const previousFocus = document.activeElement;
    if (!dialog?.open) dialog?.showModal();
    return () => {
      dialog?.close();
      if (previousFocus instanceof HTMLElement) previousFocus.focus();
    };
  }, []);

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby="assignment-title"
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
      className="fixed inset-y-0 left-auto m-0 h-dvh max-h-dvh w-full max-w-[440px] overflow-y-auto border-0 bg-white p-0 text-ink backdrop:bg-ink/35"
    >
      <form
        className="flex min-h-full flex-col"
        onSubmit={(event) => {
          event.preventDefault();
          if (selectedRepresentative && canAssign) void onAssign(selectedRepresentative.id, reason);
        }}
      >
        <header className="flex items-center justify-between gap-3 px-6 pt-6">
          <VisitStatusBadge visit={visit} />
          <button
            type="button"
            onClick={onClose}
            aria-label="Закрыть карточку заявки"
            className="flex size-10 items-center justify-center rounded-xl text-muted hover:bg-canvas"
          >
            <Icon name="close" className="size-4" />
          </button>
        </header>
        <div className="flex-1 px-6 pb-6">
          <h2 id="assignment-title" className="mt-3 text-2xl font-semibold tracking-tight">
            Заявка #{visit.number}
          </h2>
          <p className="mt-2 text-sm text-muted">
            {visit.address} · {visit.client}
          </p>
          <p className="mt-2 text-xs text-muted">{visit.district}</p>
          <div className="mt-6 rounded-2xl bg-accent-soft p-4">
            <p className="flex items-center gap-2 text-sm font-semibold">
              <Icon name="clock" className="size-4" />
              {visit.time}–{visit.endTime}
            </p>
            <p className="mt-2 text-xs leading-relaxed text-muted">
              {visit.duration} минут · {visit.product}
            </p>
          </div>
          <section
            className="mt-5 rounded-2xl border border-line p-4"
            aria-label="Текущее назначение"
          >
            <h3 className="text-sm font-semibold">Текущее назначение</h3>
            <p className="mt-2 text-sm">{representativeName ?? 'Представитель не назначен'}</p>
            <h4 className="mt-4 text-xs font-semibold">Причина назначения</h4>
            <p className="mt-2 text-xs leading-relaxed text-muted">
              {visit.assignment?.reasonText ?? 'Назначение появится после построения маршрутов.'}
            </p>
            {visit.assignment && (
              <p className="mt-3 text-xs text-muted">
                {visit.assignment.assignedBy} · {formatEventDate(visit.assignment.assignedAt)}
              </p>
            )}
          </section>
          {alerts
            .filter((a) => a.visitId === visit.id && a.status === 'open')
            .map((a) => (
              <section key={a.id} className="mt-4 rounded-xl bg-red-50 p-4 text-sm">
                <h3 className="font-semibold text-danger">{a.title}</h3>
                <p className="mt-2 leading-relaxed">{a.reasonText}</p>
              </section>
            ))}
          {isFinished ? (
            <p className="mt-6 rounded-xl bg-canvas p-4 text-sm text-muted">
              Для завершённой или отменённой встречи изменение назначения недоступно.
            </p>
          ) : (
            <fieldset className="mt-6" disabled={isSubmitting || needsPlan}>
              <legend className="mb-3 text-base font-semibold">Кому передать заявку</legend>
              <div className="space-y-2.5">
                {candidates.map((representative) => (
                  <label
                    key={representative.id}
                    className={`flex cursor-pointer items-center gap-3 rounded-2xl border p-3.5 transition-colors focus-within:ring-2 focus-within:ring-info ${selectedId === representative.id ? 'border-accent bg-accent-soft/45' : 'border-line hover:bg-canvas'}`}
                  >
                    <Avatar
                      initials={representative.initials}
                      tone={representative.tone}
                      className="size-9 text-xs"
                    />
                    <span className="min-w-0 flex-1">
                      <span className="block text-sm font-semibold">{representative.name}</span>
                      <span className="mt-1 block text-xs text-muted">
                        {representative.total - representative.completed} встреч осталось
                      </span>
                    </span>
                    <input
                      type="radio"
                      name="representative"
                      value={representative.id}
                      checked={selectedId === representative.id}
                      onChange={() => setSelectedId(representative.id)}
                      className="size-4 accent-ink"
                    />
                  </label>
                ))}
                {candidates.length === 0 && (
                  <p className="rounded-xl bg-canvas p-4 text-sm text-muted">
                    Нет доступных представителей.
                  </p>
                )}
              </div>
            </fieldset>
          )}
          {!isFinished && !needsPlan && (
            <label className="mt-5 grid gap-2 text-sm font-medium">
              Причина переназначения
              <textarea
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                maxLength={500}
                rows={3}
                placeholder="Например: представитель сообщил о задержке"
                className="rounded-xl border border-line p-3 text-sm font-normal"
              />
              <span className="text-xs font-normal text-muted">
                Если оставить пустым: «Ручное назначение диспетчером».
              </span>
            </label>
          )}
          <section className="mt-6" aria-label="История заявки">
            <h3 className="text-sm font-semibold">История заявки</h3>
            <ol className="mt-3 space-y-3">
              {history
                .filter((e) => e.visitId === visit.id)
                .map((e) => (
                  <li key={e.id} className="border-l-2 border-accent pl-3 text-xs">
                    <p className="font-medium">{e.title}</p>
                    <p className="mt-1 text-muted">
                      {formatEventDate(e.createdAt)} · {e.actor.name}
                    </p>
                    <p className="mt-1 leading-relaxed text-muted">{e.reason}</p>
                  </li>
                ))}
            </ol>
            {!history.some((e) => e.visitId === visit.id) && (
              <p className="mt-2 text-xs text-muted">Изменений по заявке пока нет.</p>
            )}
          </section>
          <div className="mt-6 rounded-2xl bg-canvas p-4">
            <p className="flex items-center gap-2 text-sm font-medium">
              <Icon name="pin" className="size-4" />
              Сначала за картой в офис
            </p>
            <p className="mt-2 text-xs leading-relaxed text-muted">
              При новом назначении представитель получает карту в офисе перед встречей.
            </p>
          </div>
        </div>
        <footer className="sticky bottom-0 border-t border-line bg-white px-6 py-5">
          {needsPlan && (
            <p className="mb-3 text-xs leading-relaxed text-muted">
              Сначала постройте маршруты рабочего дня. После этого можно изменить назначение.
            </p>
          )}
          {error && (
            <p role="alert" className="mb-3 text-sm leading-relaxed text-danger">
              {error}
            </p>
          )}
          {!isFinished && selectedRepresentative?.id === visit.representativeId && (
            <p className="mb-3 text-xs text-muted">
              Этот представитель уже назначен. Для переназначения выберите другого.
            </p>
          )}
          {isFinished ? (
            <Button type="button" variant="secondary" className="w-full" onClick={onClose}>
              Закрыть
            </Button>
          ) : (
            <Button type="submit" className="w-full" disabled={!canAssign}>
              {isSubmitting
                ? 'Сохраняем назначение…'
                : `Назначить: ${selectedRepresentative?.shortName ?? 'выберите представителя'}`}
            </Button>
          )}
        </footer>
      </form>
    </dialog>
  );
}
