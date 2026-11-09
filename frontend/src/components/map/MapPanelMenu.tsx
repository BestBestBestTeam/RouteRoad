import { useCallback, useEffect, useId, useRef, useState } from 'react';
import type { KeyboardEvent, PointerEvent, ReactNode } from 'react';

interface MapPanelMenuProps {
  children: ReactNode;
  showSelector: boolean;
  showCard: boolean;
  onToggleSelector: () => void;
  onToggleCard: () => void;
}

interface MenuPosition {
  x: number;
  y: number;
  focusTarget: HTMLElement;
}

function isMapSurface(target: EventTarget | null, wrapper: HTMLElement): target is Element {
  if (!(target instanceof Element)) return false;
  if (
    target.closest(
      'button, a, input, select, textarea, [role="menu"], .leaflet-control, .leaflet-popup, .leaflet-marker-icon',
    )
  )
    return false;
  return target === wrapper || target.closest('.leaflet-container') !== null;
}

export function MapPanelMenu({
  children,
  showSelector,
  showCard,
  onToggleSelector,
  onToggleCard,
}: MapPanelMenuProps) {
  const wrapper = useRef<HTMLDivElement>(null);
  const menuElement = useRef<HTMLDivElement>(null);
  const press = useRef<{ id: number; x: number; y: number; timeout: number } | null>(null);
  const suppressClick = useRef(false);
  const [menu, setMenu] = useState<MenuPosition | null>(null);
  const hintId = useId();

  const clearPress = useCallback(() => {
    if (press.current) window.clearTimeout(press.current.timeout);
    press.current = null;
  }, []);

  const openAt = useCallback((clientX: number, clientY: number, focusTarget: HTMLElement) => {
    const rect = wrapper.current?.getBoundingClientRect();
    if (!rect) return;
    setMenu({
      x: Math.max(8, Math.min(clientX - rect.left, rect.width - 264)),
      y: Math.max(8, Math.min(clientY - rect.top, Math.min(rect.height - 176, 240))),
      focusTarget,
    });
  }, []);

  const closeMenu = useCallback(
    (restoreFocus = true) => {
      setMenu(null);
      if (restoreFocus) menu?.focusTarget.focus({ preventScroll: true });
    },
    [menu],
  );

  useEffect(() => clearPress, [clearPress]);

  useEffect(() => {
    if (!menu) return;
    menuElement.current?.querySelector<HTMLButtonElement>('button')?.focus({ preventScroll: true });
    const outside = (event: globalThis.PointerEvent) => {
      if (event.target instanceof Node && !menuElement.current?.contains(event.target)) {
        closeMenu(false);
      }
    };
    document.addEventListener('pointerdown', outside);
    return () => document.removeEventListener('pointerdown', outside);
  }, [menu, closeMenu]);

  function beginPress(event: PointerEvent<HTMLDivElement>) {
    clearPress();
    if (
      !event.isPrimary ||
      (event.pointerType !== 'touch' && event.pointerType !== 'pen') ||
      !isMapSurface(event.target, event.currentTarget)
    )
      return;
    const { pointerId, clientX, clientY } = event;
    const focusTarget =
      event.target.closest<HTMLElement>('.leaflet-container') ?? event.currentTarget;
    press.current = {
      id: pointerId,
      x: clientX,
      y: clientY,
      timeout: window.setTimeout(() => {
        suppressClick.current = true;
        openAt(clientX, clientY, focusTarget);
        press.current = null;
      }, 650),
    };
  }

  function movePress(event: PointerEvent<HTMLDivElement>) {
    const start = press.current;
    if (
      start &&
      start.id === event.pointerId &&
      Math.hypot(event.clientX - start.x, event.clientY - start.y) > 10
    )
      clearPress();
  }

  function menuKeys(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.preventDefault();
      event.stopPropagation();
      closeMenu();
      return;
    }
    if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    const buttons = Array.from(event.currentTarget.querySelectorAll<HTMLButtonElement>('button'));
    const current = buttons.findIndex((button) => button === document.activeElement);
    const next =
      event.key === 'Home'
        ? 0
        : event.key === 'End'
          ? buttons.length - 1
          : (current + (event.key === 'ArrowDown' ? 1 : -1) + buttons.length) % buttons.length;
    buttons[next]?.focus();
  }

  return (
    <div
      ref={wrapper}
      role="group"
      aria-label="Карта и панели представителя"
      aria-describedby={hintId}
      tabIndex={0}
      className="relative isolate rounded-2xl"
      onContextMenu={(event) => {
        if (!isMapSurface(event.target, event.currentTarget)) return;
        event.preventDefault();
        const focusTarget =
          event.target.closest<HTMLElement>('.leaflet-container') ?? event.currentTarget;
        openAt(event.clientX, event.clientY, focusTarget);
      }}
      onKeyDown={(event) => {
        if (!isMapSurface(event.target, event.currentTarget)) return;
        if (event.key !== 'ContextMenu' && !(event.shiftKey && event.key === 'F10')) return;
        event.preventDefault();
        event.stopPropagation();
        const rect = event.currentTarget.getBoundingClientRect();
        const focusTarget =
          event.target.closest<HTMLElement>('.leaflet-container') ?? event.currentTarget;
        openAt(rect.left + rect.width / 2, rect.top + 120, focusTarget);
      }}
      onPointerDownCapture={beginPress}
      onPointerMoveCapture={movePress}
      onPointerUpCapture={clearPress}
      onPointerCancelCapture={clearPress}
      onClickCapture={(event) => {
        if (!suppressClick.current) return;
        suppressClick.current = false;
        if (event.target instanceof Node && menuElement.current?.contains(event.target)) return;
        event.preventDefault();
        event.stopPropagation();
      }}
    >
      {children}
      <p id={hintId} className="mt-2 text-[11px] leading-relaxed text-muted">
        Панели карты: правый клик или долгое нажатие. С клавиатуры — Shift+F10.
      </p>
      {menu && (
        <div
          ref={menuElement}
          role="menu"
          aria-label="Параметры карты"
          className="absolute z-[1200] w-64 max-w-[calc(100%-16px)] rounded-xl border border-line bg-white p-2 shadow-xl"
          style={{ left: menu.x, top: menu.y }}
          onKeyDown={menuKeys}
          onBlur={(event) => {
            if (!event.currentTarget.contains(event.relatedTarget)) closeMenu(false);
          }}
        >
          <p className="px-3 py-2 text-xs font-semibold text-muted">Отображение панелей</p>
          <button
            type="button"
            role="menuitemcheckbox"
            aria-checked={showSelector}
            onClick={onToggleSelector}
            className="flex min-h-11 w-full items-center gap-3 rounded-lg px-3 text-left text-sm hover:bg-canvas"
          >
            <span aria-hidden="true" className="w-4 text-center">
              {showSelector ? '✓' : ''}
            </span>
            Выбор представителя
          </button>
          <button
            type="button"
            role="menuitemcheckbox"
            aria-checked={showCard}
            onClick={onToggleCard}
            className="flex min-h-11 w-full items-center gap-3 rounded-lg px-3 text-left text-sm hover:bg-canvas"
          >
            <span aria-hidden="true" className="w-4 text-center">
              {showCard ? '✓' : ''}
            </span>
            Карточка представителя
          </button>
        </div>
      )}
    </div>
  );
}
