import { useMemo, useRef, useState } from 'react';
import type { TileLayer as LeafletTileLayer } from 'leaflet';
import { TileLayer } from 'react-leaflet';
import { mapConfig } from '@/config/map';

export function MapTiles() {
  const layer = useRef<LeafletTileLayer>(null);
  const [status, setStatus] = useState({ loaded: 0, errors: 0, loading: true });
  const eventHandlers = useMemo(
    () => ({
      loading: () => setStatus({ loaded: 0, errors: 0, loading: true }),
      tileload: () => setStatus((current) => ({ ...current, loaded: current.loaded + 1 })),
      tileerror: () => setStatus((current) => ({ ...current, errors: current.errors + 1 })),
      load: () => setStatus((current) => ({ ...current, loading: false })),
    }),
    [],
  );
  return (
    <>
      <TileLayer
        ref={layer}
        url={mapConfig.tileUrl}
        attribution={mapConfig.tileAttribution}
        maxZoom={mapConfig.maxZoom}
        keepBuffer={1}
        updateWhenIdle
        eventHandlers={eventHandlers}
      />
      {status.errors > 0 ? (
        <div
          role="status"
          className="absolute top-16 right-14 left-3 z-[1000] rounded-xl border border-line bg-white/95 p-3 text-xs leading-relaxed text-ink shadow-sm"
        >
          <p>
            {status.loaded > 0
              ? 'Часть карты не загрузилась. Метки и список встреч остаются доступны.'
              : 'Не удалось загрузить карту. Проверьте подключение; список встреч остаётся доступен.'}
          </p>
          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              setStatus({ loaded: 0, errors: 0, loading: true });
              layer.current?.redraw();
            }}
            className="mt-2 min-h-8 rounded-lg bg-accent px-3 py-1 font-semibold text-ink"
          >
            Повторить загрузку карты
          </button>
        </div>
      ) : status.loading && status.loaded === 0 ? (
        <p
          role="status"
          className="absolute top-16 left-3 z-[1000] rounded-lg bg-white/95 px-3 py-2 text-xs text-muted shadow-sm"
        >
          Загружаем карту…
        </p>
      ) : null}
    </>
  );
}
