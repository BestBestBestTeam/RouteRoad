import { useEffect, useRef } from 'react';
import type { LatLngTuple } from 'leaflet';
import { useMap } from 'react-leaflet';
import { fitMapViewport } from './viewport';

interface MapViewportProps {
  points: LatLngTuple[];
  selectedPoint?: LatLngTuple;
}

export function MapViewport({ points, selectedPoint }: MapViewportProps) {
  const map = useMap();
  const lastView = useRef('');
  useEffect(() => {
    // Status updates and parent renders must not undo a user's pan or zoom.
    const view = JSON.stringify({ points, selectedPoint });
    if (lastView.current === view) return;
    lastView.current = view;
    fitMapViewport(map, points, selectedPoint);
  }, [map, points, selectedPoint]);
  useEffect(() => {
    const observer = new ResizeObserver(() => map.invalidateSize({ pan: false }));
    observer.observe(map.getContainer());
    return () => observer.disconnect();
  }, [map]);
  return null;
}
