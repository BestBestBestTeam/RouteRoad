import { useEffect, useMemo, useRef } from 'react';
import { divIcon, type Marker as LeafletMarker } from 'leaflet';
import { Marker, Popup } from 'react-leaflet';
import type { Office, Visit } from '@/types/workday';

interface VisitMapMarkerProps {
  visit: Visit;
  number: number;
  selected: boolean;
  hasAlert: boolean;
  onOpen?: (id: string) => void;
}

export function VisitMapMarker({ visit, number, selected, hasAlert, onOpen }: VisitMapMarkerProps) {
  const marker = useRef<LeafletMarker>(null);
  const label = `Заявка #${visit.number}: ${visit.address}${hasAlert ? ', есть открытый алерт' : ''}`;
  const icon = useMemo(
    () =>
      divIcon({
        className: hasAlert ? 'route-map-marker route-map-marker-alert' : 'route-map-marker',
        // Only our numeric index enters HTML; external strings stay in React text nodes.
        html: `<span class="flex size-8 items-center justify-center rounded-full border-2 border-white text-xs font-bold shadow-md ${hasAlert ? 'bg-danger text-white' : selected ? 'bg-accent text-ink' : 'bg-ink text-white'} ${selected ? 'ring-2 ring-ink' : ''}">${number}</span>`,
        iconSize: [32, 32],
        iconAnchor: [16, 16],
        popupAnchor: [0, -18],
      }),
    [number, selected, hasAlert],
  );
  useEffect(() => {
    marker.current?.getElement()?.setAttribute('aria-label', label);
  }, [label, icon]);
  return (
    <Marker
      ref={marker}
      position={[visit.location.lat, visit.location.lon]}
      icon={icon}
      title={label}
      alt={label}
      keyboard
      eventHandlers={{
        add: () => marker.current?.getElement()?.setAttribute('aria-label', label),
      }}
    >
      <Popup minWidth={180} maxWidth={260} maxHeight={180}>
        <div className="flex min-w-36 flex-col gap-1.5 font-sans text-xs text-ink">
          <strong className="text-sm">Встреча #{visit.number}</strong>
          <p className="m-0!">{visit.address}</p>
          <p className="m-0!">{visit.client}</p>
          {hasAlert && (
            <p className="m-0! font-semibold text-danger">Требует внимания диспетчера</p>
          )}
          <p className="m-0!">
            {visit.time}–{visit.endTime} · {visit.duration} мин
          </p>
          {onOpen && (
            <button
              type="button"
              onClick={() => onOpen(visit.id)}
              className="mt-2 min-h-9 rounded-lg bg-accent px-3 py-1 font-semibold"
            >
              Подробнее
            </button>
          )}
        </div>
      </Popup>
    </Marker>
  );
}

export function OfficeMapMarker({ office }: { office: Office }) {
  const marker = useRef<LeafletMarker>(null);
  const label = `${office.name}: ${office.address}`;
  const icon = useMemo(
    () =>
      divIcon({
        className: 'route-map-office',
        html: '<span class="flex size-9 items-center justify-center rounded-xl border-2 border-white bg-accent font-sans text-sm font-bold text-ink shadow-md">О</span>',
        iconSize: [36, 36],
        iconAnchor: [18, 18],
        popupAnchor: [0, -20],
      }),
    [],
  );
  useEffect(() => {
    marker.current?.getElement()?.setAttribute('aria-label', label);
  }, [label]);
  return (
    <Marker
      ref={marker}
      position={[office.location.lat, office.location.lon]}
      icon={icon}
      title={label}
      alt={label}
      keyboard
      eventHandlers={{ add: () => marker.current?.getElement()?.setAttribute('aria-label', label) }}
    >
      <Popup>
        <strong>{office.name}</strong>
        <p>{office.address}</p>
        <p>Начальная точка дня · получение карт</p>
      </Popup>
    </Marker>
  );
}
