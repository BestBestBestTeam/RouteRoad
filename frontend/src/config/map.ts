const DEFAULT_TILE_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';

function getTileUrl(value: unknown): string {
  if (typeof value !== 'string' || !['{z}', '{x}', '{y}'].every((token) => value.includes(token))) {
    return DEFAULT_TILE_URL;
  }
  try {
    const url = new URL(value.replaceAll('{z}', '0').replaceAll('{x}', '0').replaceAll('{y}', '0'));
    return url.protocol === 'https:' ? value : DEFAULT_TILE_URL;
  } catch {
    return DEFAULT_TILE_URL;
  }
}

// Public browser configuration. A different provider may require its own attribution.
export const mapConfig = {
  center: [55.03, 82.92] as [number, number],
  zoom: 13,
  minZoom: 10,
  maxZoom: 19,
  tileUrl: getTileUrl(import.meta.env.VITE_MAP_TILE_URL),
  tileAttribution:
    '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a> contributors',
};
