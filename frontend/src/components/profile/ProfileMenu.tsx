import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { useDemoSessionStore } from '@/stores/sessionStore';
import { useWorkdayStore } from '@/stores/workdayStore';
import { Avatar } from '@/components/ui/Avatar';
import { ProfileSettings } from './ProfileSettings';
export function ProfileMenu() {
  const user = useDemoSessionStore((s) => s.user);
  const leave = useDemoSessionStore((s) => s.leave);
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [settings, setSettings] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (!open) return;
    function outside(e: PointerEvent) {
      if (e.target instanceof Node && !root.current?.contains(e.target)) setOpen(false);
    }
    function key(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        setOpen(false);
        trigger.current?.focus();
      }
    }
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', key);
    return () => {
      document.removeEventListener('pointerdown', outside);
      document.removeEventListener('keydown', key);
    };
  }, [open]);
  return (
    <div ref={root} className="relative shrink-0">
      <button
        ref={trigger}
        type="button"
        aria-label="Меню профиля"
        aria-expanded={open}
        aria-controls="profile-options"
        onClick={() => setOpen(!open)}
        className="rounded-full p-1 hover:bg-canvas"
      >
        <Avatar initials={user?.initials ?? ''} tone="gray" className="size-9" />
      </button>
      {open && (
        <div
          id="profile-options"
          className="absolute right-0 top-full z-50 mt-2 w-64 rounded-2xl border border-line bg-white p-3 shadow-lg"
        >
          <p className="px-2 py-1 text-sm font-semibold">{user?.name}</p>
          <p className="mb-2 px-2 text-xs text-muted">
            {user?.role === 'dispatcher' ? 'Диспетчер' : 'Выездной представитель'}
          </p>
          <button
            type="button"
            onClick={() => {
              setSettings(true);
              setOpen(false);
            }}
            className="min-h-11 w-full rounded-xl px-2 text-left text-sm hover:bg-canvas"
          >
            Настройки профиля
          </button>
          <button
            type="button"
            onClick={() => {
              useWorkdayStore.getState().setNotice(null);
              leave();
              navigate('/login');
            }}
            className="min-h-11 w-full rounded-xl px-2 text-left text-sm hover:bg-canvas"
          >
            Сменить роль
          </button>
        </div>
      )}
      {settings && (
        <ProfileSettings
          returnFocusRef={trigger}
          onClose={() => {
            setSettings(false);
          }}
        />
      )}
    </div>
  );
}
