import { latLngBounds, type LatLngTuple, type Map as LeafletMap } from 'leaflet';
import { mapConfig } from '@/config/map';

export function fitMapViewport(
  map: LeafletMap,
  points: LatLngTuple[],
  selectedPoint?: LatLngTuple,
) {
  if (selectedPoint) {
    map.setView(selectedPoint, 15, { animate: false });
  } else if (points.length === 1 && points[0]) {
    map.setView(points[0], 15, { animate: false });
  } else if (points.length > 1) {
    map.fitBounds(latLngBounds(points), { padding: [40, 40], maxZoom: 15, animate: false });
  } else {
    map.setView(mapConfig.center, mapConfig.zoom, { animate: false });
  }
}
