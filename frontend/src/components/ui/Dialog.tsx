import { useEffect, useId, useRef, type ReactNode, type RefObject } from 'react';
import { Icon } from './Icon';

export function Dialog({
  title,
  onClose,
  children,
  drawer = false,
  returnFocusRef,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
  drawer?: boolean;
  returnFocusRef?: RefObject<HTMLElement | null>;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const element = ref.current;
    const previous = document.activeElement;
    const returnTarget = returnFocusRef?.current ?? previous;
    element?.showModal();
    return () => {
      element?.close();
      if (returnTarget instanceof HTMLElement && returnTarget.isConnected) returnTarget.focus();
    };
  }, [returnFocusRef]);
  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
      className={
        drawer
          ? 'fixed inset-y-0 left-auto m-0 h-dvh max-h-dvh w-full max-w-[500px] overflow-y-auto border-0 bg-white p-6 text-ink backdrop:bg-ink/35'
          : 'fixed inset-0 m-auto max-h-[90dvh] w-[calc(100%_-_32px)] max-w-lg overflow-y-auto rounded-2xl border-0 bg-white p-6 text-ink backdrop:bg-ink/35'
      }
    >
      <header className="mb-5 flex items-start justify-between gap-3">
        <h2 id={titleId} className="text-xl font-semibold">
          {title}
        </h2>
        <button
          type="button"
          aria-label="Закрыть диалог"
          onClick={onClose}
          className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-canvas"
        >
          <Icon name="close" />
        </button>
      </header>
      {children}
    </dialog>
  );
}
