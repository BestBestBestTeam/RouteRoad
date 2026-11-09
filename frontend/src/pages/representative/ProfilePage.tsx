import { useNavigate } from 'react-router';
import { useDemoSessionStore } from '@/stores/sessionStore';
import { useWorkdayStore } from '@/stores/workdayStore';
import { Avatar } from '@/components/ui/Avatar';
import { Button } from '@/components/ui/Button';
import { Icon } from '@/components/ui/Icon';
import { formatServiceDate } from '@/utils/date';
import { useState } from 'react';
import { ProfileSettings } from '@/components/profile/ProfileSettings';

export default function ProfilePage() {
  const [settings, setSettings] = useState(false);
  const user = useDemoSessionStore((state) => state.user);
  const leave = useDemoSessionStore((state) => state.leave);
  const visits = useWorkdayStore((state) => state.visits);
  const office = useWorkdayStore((state) => state.office);
  const serviceDate = useWorkdayStore((state) => state.serviceDate);
  const setNotice = useWorkdayStore((state) => state.setNotice);
  const navigate = useNavigate();
  const ownVisits = visits.filter((visit) => visit.representativeId === user?.representativeId);
  const completed = ownVisits.filter((visit) => visit.status === 'completed').length;

  return (
    <section className="max-w-3xl px-5 py-6 md:px-8">
      <h1 className="text-[28px] font-semibold tracking-tight">Профиль</h1>
      <div className="mt-6 flex items-center gap-4">
        <Avatar initials={user?.initials ?? ''} className="size-14 text-base" />
        <div>
          <h2 className="text-lg font-semibold">{user?.name}</h2>
          <p className="mt-1 text-sm text-muted">Выездной представитель</p>
        </div>
      </div>
      <section className="mt-6 rounded-2xl bg-canvas p-4">
        <h2 className="text-sm font-semibold">Рабочий день</h2>
        <dl className="mt-4 space-y-3 text-sm">
          <div className="flex justify-between gap-4">
            <dt className="text-muted">Дата</dt>
            <dd className="text-right font-medium">{formatServiceDate(serviceDate)}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-muted">Смена</dt>
            <dd>
              {user?.shiftStart}–{user?.shiftEnd}
            </dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-muted">Город</dt>
            <dd>{user?.city}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-muted">Завершено</dt>
            <dd>
              {completed} из {ownVisits.length} встреч
            </dd>
          </div>
        </dl>
      </section>
      {office && (
        <section className="mt-4 rounded-2xl border border-line p-4">
          <h2 className="flex items-center gap-2 text-sm font-semibold">
            <Icon name="pin" />
            Ваш офис
          </h2>
          <p className="mt-3 text-sm">{office.address}</p>
        </section>
      )}
      <p className="mt-5 text-xs leading-relaxed text-muted">
        Тестовый пользователь. При смене роли подготовленный день сохраняется до перезагрузки
        страницы или очистки диспетчером.
      </p>
      <Button className="mt-6 w-full" onClick={() => setSettings(true)}>
        Настройки профиля
      </Button>
      {settings && <ProfileSettings onClose={() => setSettings(false)} />}
      <Button
        className="mt-6 w-full"
        variant="secondary"
        onClick={() => {
          setNotice(null);
          leave();
          navigate('/login', { replace: true });
        }}
      >
        <Icon name="logout" />
        Сменить роль
      </Button>
    </section>
  );
}
