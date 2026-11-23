import { useState } from 'react';
import { useWorkdayStore } from '@/stores/workdayStore';
import type { Visit, VisitStatus } from '@/types/workday';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { Icon } from '@/components/ui/Icon';
import { inputClass } from '@/components/ui/QueryControls';

type DialogKind = 'delay' | 'refuse' | 'contact';
const actions: Partial<Record<VisitStatus, { label: string; nextStatus: VisitStatus }>> = {
  assigned: { label: 'Выехать на встречу', nextStatus: 'en-route' },
  'en-route': { label: 'Я прибыл', nextStatus: 'arrived' },
  arrived: { label: 'Начать встречу', nextStatus: 'in-progress' },
  'in-progress': { label: 'Завершить успешно', nextStatus: 'completed' },
};
function VisitHelpDialog({
  kind,
  visit,
  onClose,
}: {
  kind: DialogKind;
  visit: Visit;
  onClose: () => void;
}) {
  const [delay, setDelay] = useState('10');
  const [reason, setReason] = useState('');
  const pending = useWorkdayStore((s) => s.pendingVisitId);
  const error = useWorkdayStore((s) => s.error);
  const reportDelay = useWorkdayStore((s) => s.reportDelay);
  const refuseVisit = useWorkdayStore((s) => s.refuseVisit);
  async function submit() {
    if (kind === 'delay') await reportDelay(visit.id, Number(delay));
    if (kind === 'refuse') await refuseVisit(visit.id, reason);
    if (!useWorkdayStore.getState().error && !useWorkdayStore.getState().pendingVisitId) onClose();
  }
  return (
    <Dialog
      title={
        kind === 'contact'
          ? 'Связаться с клиентом'
          : kind === 'delay'
            ? 'Сообщить о задержке'
            : 'Отказаться от встречи?'
      }
      onClose={onClose}
    >
      {kind === 'contact' ? (
        <>
          <p className="text-sm leading-relaxed text-muted">
            {visit.client} — вымышленный клиент. В деморежиме реальные номера не используются,
            звонки недоступны.
          </p>
          <Button className="mt-5 w-full" onClick={onClose}>
            Понятно
          </Button>
        </>
      ) : (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void submit();
          }}
          className="space-y-5"
        >
          <p className="text-sm leading-relaxed text-muted">
            Сообщение появится в локальных алертах диспетчера. Назначение сохраняется до решения; на
            сервер ничего не отправляется.
          </p>
          {kind === 'delay' ? (
            <fieldset disabled={pending !== null}>
              <legend className="text-sm font-medium">Время задержки</legend>
              <div className="mt-3 grid grid-cols-3 gap-2">
                {['5', '10', '15'].map((minutes) => (
                  <label
                    key={minutes}
                    className={`flex min-h-12 items-center justify-center gap-2 rounded-xl border text-sm ${delay === minutes ? 'border-accent bg-accent-soft' : 'border-line'}`}
                  >
                    <input
                      type="radio"
                      name="delay"
                      value={minutes}
                      checked={delay === minutes}
                      onChange={() => setDelay(minutes)}
                    />
                    {minutes} мин
                  </label>
                ))}
              </div>
            </fieldset>
          ) : (
            <label className="grid gap-2 text-sm font-medium">
              Причина отказа
              <textarea
                className={inputClass + ' py-3'}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                rows={3}
                minLength={3}
                maxLength={500}
                required
                placeholder="Что мешает провести встречу?"
              />
            </label>
          )}
          {error && (
            <p role="alert" className="text-sm text-danger">
              {error}
            </p>
          )}
          <Button
            type="submit"
            disabled={pending !== null}
            variant={kind === 'refuse' ? 'danger' : 'primary'}
            className="w-full"
          >
            {pending
              ? 'Сохраняем…'
              : kind === 'delay'
                ? 'Сообщить диспетчеру'
                : 'Подтвердить отказ'}
          </Button>
          <Button variant="secondary" className="w-full" onClick={onClose}>
            Вернуться к встрече
          </Button>
        </form>
      )}
    </Dialog>
  );
}
export function VisitActionPanel({ visit }: { visit: Visit }) {
  const setVisitStatus = useWorkdayStore((s) => s.setVisitStatus);
  const pending = useWorkdayStore((s) => s.pendingVisitId);
  const alerts = useWorkdayStore((s) => s.alerts);
  const [kind, setKind] = useState<DialogKind | null>(null);
  const action = actions[visit.status];
  const terminal = visit.status === 'completed' || visit.status === 'cancelled';
  const refusal = alerts.some(
    (a) => a.visitId === visit.id && a.kind === 'refusal' && a.status === 'open',
  );
  const delayReported = alerts.some(
    (a) => a.visitId === visit.id && a.kind === 'delay' && a.status === 'open',
  );
  return (
    <>
      <div className="mt-5 space-y-3">
        {refusal && (
          <p role="status" className="rounded-xl bg-accent-soft p-4 text-sm leading-relaxed">
            Отказ зарегистрирован. Дождитесь решения диспетчера; текущее назначение сохранено.
          </p>
        )}
        {action && (
          <Button
            className="min-h-[52px] w-full"
            disabled={pending !== null || refusal}
            aria-busy={pending === visit.id}
            onClick={() => void setVisitStatus(visit.id, action.nextStatus)}
          >
            {pending === visit.id ? 'Сохраняем…' : action.label}
          </Button>
        )}
        {!terminal && (
          <>
            <Button
              className="w-full"
              variant="secondary"
              disabled={pending !== null || delayReported || refusal}
              onClick={() => setKind('delay')}
            >
              {delayReported ? 'О задержке уже сообщено' : 'Сообщить о задержке'}
            </Button>
            <Button
              className="w-full"
              variant="danger"
              disabled={pending !== null || refusal}
              onClick={() => setKind('refuse')}
            >
              Отказаться от встречи
            </Button>
          </>
        )}
        {terminal && (
          <p className="rounded-xl bg-canvas p-4 text-center text-sm text-muted">
            {visit.status === 'completed'
              ? 'Встреча завершена в деморежиме'
              : 'Встреча отменена в демонстрационных данных'}
          </p>
        )}
      </div>
      {kind && <VisitHelpDialog kind={kind} visit={visit} onClose={() => setKind(null)} />}
    </>
  );
}
export function DemoClientContact({ visit }: { visit: Visit }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        type="button"
        aria-label={`Связаться с клиентом ${visit.client}`}
        onClick={() => setOpen(true)}
        className="flex size-10 shrink-0 items-center justify-center rounded-full bg-white hover:bg-accent-soft"
      >
        <Icon name="phone" />
      </button>
      {open && <VisitHelpDialog kind="contact" visit={visit} onClose={() => setOpen(false)} />}
    </>
  );
}
