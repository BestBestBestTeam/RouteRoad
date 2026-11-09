import { useState, type RefObject } from 'react';
import { useDemoSessionStore } from '@/stores/sessionStore';
import { Dialog } from '@/components/ui/Dialog';
import { Button } from '@/components/ui/Button';
import { FilterField, inputClass } from '@/components/ui/QueryControls';

export function ProfileSettings({
  onClose,
  returnFocusRef,
}: {
  onClose: () => void;
  returnFocusRef?: RefObject<HTMLElement | null>;
}) {
  const user = useDemoSessionStore((s) => s.user);
  const updateProfile = useDemoSessionStore((s) => s.updateProfile);
  const [name, setName] = useState(user?.name ?? '');
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);
  return (
    <Dialog title="Настройки профиля" onClose={onClose} returnFocusRef={returnFocusRef}>
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault();
          try {
            updateProfile({ name });
            setError('');
            setSaved(true);
          } catch (error) {
            setError(error instanceof Error ? error.message : 'Не удалось сохранить имя.');
          }
        }}
      >
        <FilterField label="Имя и фамилия">
          <input
            autoComplete="name"
            value={name}
            minLength={2}
            maxLength={64}
            required
            onChange={(e) => {
              setName(e.target.value);
              setSaved(false);
            }}
            className={inputClass}
          />
        </FilterField>
        <p className="text-xs leading-relaxed text-muted">
          Имя сохраняется в этой вкладке браузера. Настоящий профиль и авторизация появятся после
          подключения сервера.
        </p>
        {error && (
          <p role="alert" className="text-sm text-danger">
            {error}
          </p>
        )}
        {saved && (
          <p role="status" className="text-sm text-success">
            Сохранено в этой вкладке
          </p>
        )}
        <Button type="submit">Сохранить</Button>
      </form>
    </Dialog>
  );
}
