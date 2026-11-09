import { useMemo, useRef } from 'react';
import type { LatLngTuple, Map as LeafletMap } from 'leaflet';
import { MapContainer, Polyline, ZoomControl } from 'react-leaflet';
import { mapConfig } from '@/config/map';
import { Icon } from '@/components/ui/Icon';
import type { Office, RoutePlan, Visit } from '@/types/workday';
import { MapViewport } from './MapViewport';
import { fitMapViewport } from './viewport';
import { MapTiles } from './MapTiles';
import { OfficeMapMarker, VisitMapMarker } from './MapMarkers';

interface WorkdayMapProps {
  visits: Visit[];
  routes?: RoutePlan[];
  office?: Office | null;
  selectedRepresentativeId?: string;
  selectedVisitId?: string;
  alertVisitIds?: string[];
  onOpenVisit?: (id: string) => void;
  className?: string;
  label?: string;
}

const routeColors = ['#c6a900', '#9c83cb', '#3c9c83', '#d17a53', '#5793bd', '#858a96'];

/** Geometry comes from props; a future API can replace the demo source without changing UI. */
export function WorkdayMap({
  visits,
  routes = [],
  office = null,
  selectedRepresentativeId,
  selectedVisitId,
  alertVisitIds = [],
  onOpenVisit,
  className = 'h-80',
  label = 'Карта заявок и маршрутов по Новосибирску',
}: WorkdayMapProps) {
  const map = useRef<LeafletMap>(null);
  const alertIds = useMemo(() => new Set(alertVisitIds), [alertVisitIds]);
  const visibleRoutes = useMemo(
    () =>
      selectedRepresentativeId && selectedRepresentativeId !== 'all'
        ? routes.filter((route) => route.representativeId === selectedRepresentativeId)
        : routes,
    [routes, selectedRepresentativeId],
  );
  const visibleVisits = useMemo(() => {
    // Before planning, no representative owns the unassigned requests: show all of them.
    if (!routes.length || !selectedRepresentativeId || selectedRepresentativeId === 'all') {
      return visits;
    }
    const visitsById = new Map(visits.map((visit) => [visit.id, visit]));
    return visibleRoutes.flatMap((route) =>
      route.visitIds.flatMap((id) => {
        const visit = visitsById.get(id);
        return visit ? [visit] : [];
      }),
    );
  }, [visits, routes.length, selectedRepresentativeId, visibleRoutes]);
  const points = useMemo(() => {
    const positions: LatLngTuple[] = visibleVisits.map((visit) => [
      visit.location.lat,
      visit.location.lon,
    ]);
    if (office) positions.push([office.location.lat, office.location.lon]);
    for (const route of visibleRoutes) {
      for (const [lon, lat] of route.geometry.coordinates) positions.push([lat, lon]);
    }
    return positions;
  }, [visibleVisits, office, visibleRoutes]);
  const selectedVisit = visibleVisits.find((visit) => visit.id === selectedVisitId);
  const selectedPoint: LatLngTuple | undefined = selectedVisit
    ? [selectedVisit.location.lat, selectedVisit.location.lon]
    : undefined;

  return (
    <div
      role="region"
      aria-label={label}
      className={`relative isolate overflow-hidden rounded-2xl bg-canvas ${className}`}
    >
      <MapContainer
        ref={map}
        center={mapConfig.center}
        zoom={mapConfig.zoom}
        minZoom={mapConfig.minZoom}
        maxZoom={mapConfig.maxZoom}
        zoomControl={false}
        keyboard
        scrollWheelZoom
        className="absolute inset-0 size-full font-sans"
      >
        <MapTiles />
        <ZoomControl
          position="topright"
          zoomInTitle="Увеличить карту"
          zoomOutTitle="Уменьшить карту"
        />
        <MapViewport points={points} selectedPoint={selectedPoint} />
        {visibleRoutes.map((route) => (
          <Polyline
            key={route.id}
            positions={route.geometry.coordinates.map(([lon, lat]) => [lat, lon])}
            pathOptions={{
              color: routeColors[routes.indexOf(route) % routeColors.length],
              weight: 4,
              opacity: 0.85,
            }}
          />
        ))}
        {office && <OfficeMapMarker office={office} />}
        {visibleVisits.map((visit, index) => (
          <VisitMapMarker
            key={visit.id}
            visit={visit}
            number={index + 1}
            selected={visit.id === selectedVisitId}
            hasAlert={alertIds.has(visit.id)}
            onOpen={onOpenVisit}
          />
        ))}
      </MapContainer>
      <button
        type="button"
        aria-label="Показать все точки на карте"
        title="Показать все точки на карте"
        onClick={() => {
          if (map.current) fitMapViewport(map.current, points);
        }}
        className="absolute top-[84px] right-2.5 z-[1000] flex size-[34px] items-center justify-center rounded-sm border-2 border-black/20 bg-white text-ink shadow-sm hover:bg-canvas"
      >
        <Icon name="target" className="size-4" />
      </button>
      <p className="sr-only">
        Стрелки перемещают карту, плюс и минус меняют масштаб. Маркеры доступны с клавиатуры.
        Геометрия маршрутов демонстрационная, без расчёта по дорогам.
      </p>
    </div>
  );
}
