import { useState } from 'react';
import { Avatar } from '@/components/ui/Avatar';
import { Icon } from '@/components/ui/Icon';
import { WorkdayMap } from '@/components/map/WorkdayMap';
import { MapPanelMenu } from '@/components/map/MapPanelMenu';
import type { Office, Representative, RoutePlan, Visit } from '@/types/workday';

interface DispatcherMapProps {
  visits: Visit[];
  representatives: Representative[];
  routes?: RoutePlan[];
  office?: Office | null;
  selectedRepresentativeId?: string;
  alertVisitIds?: string[];
  onOpenVisit?: (id: string) => void;
  onSelect: (id: string) => void;
  attentionCount: number;
  onOpenAlerts: () => void;
}

export function DispatcherMap({
  visits,
  representatives,
  routes = [],
  office = null,
  selectedRepresentativeId = 'all',
  alertVisitIds = [],
  onOpenVisit,
  onSelect,
  attentionCount,
  onOpenAlerts,
}: DispatcherMapProps) {
  const [showSelector, setShowSelector] = useState(true);
  const [showCard, setShowCard] = useState(true);
  const selectedRepresentative = representatives.find(
    (representative) => representative.id === selectedRepresentativeId,
  );
  return (
    <MapPanelMenu
      showSelector={showSelector}
      showCard={showCard}
      onToggleSelector={() => setShowSelector((value) => !value)}
      onToggleCard={() => setShowCard((value) => !value)}
    >
      <WorkdayMap
        visits={visits}
        routes={routes}
        office={office}
        selectedRepresentativeId={selectedRepresentativeId}
        alertVisitIds={alertVisitIds}
        onOpenVisit={onOpenVisit}
        className="h-[425px] rounded-2xl"
      />
      {showSelector && representatives.length > 0 && (
        <div className="absolute top-4 left-4 z-[1000] max-w-[calc(100%-90px)] rounded-xl bg-white px-3 shadow-sm">
          <label className="flex items-center gap-2">
            <span className="size-2 rounded-full bg-accent" aria-hidden="true" />
            <span className="sr-only">Представитель на карте</span>
            <select
              className="min-h-10 max-w-full bg-transparent pr-3 text-xs font-medium outline-none"
              value={selectedRepresentativeId}
              onChange={(event) => onSelect(event.target.value)}
            >
              <option value="all">Все представители</option>
              {representatives.map((representative) => (
                <option key={representative.id} value={representative.id}>
                  {representative.name}
                </option>
              ))}
            </select>
          </label>
        </div>
      )}
      {showCard && selectedRepresentative && (
        <section
          aria-label="Карточка представителя"
          className="absolute bottom-14 left-4 z-[1000] max-w-[calc(100%-32px)] rounded-xl bg-white p-3 shadow-sm sm:max-w-[250px]"
        >
          <div className="flex items-center gap-2.5">
            <Avatar
              initials={selectedRepresentative.initials}
              tone={selectedRepresentative.tone}
              className="size-8 text-[10px]"
            />
            <div>
              <p className="text-xs font-semibold">{selectedRepresentative.name}</p>
              <p className="mt-0.5 text-[10px] text-muted">
                {selectedRepresentative.completed} из {selectedRepresentative.total} встреч
                завершены
              </p>
            </div>
          </div>
          <p className="mt-2 text-[10px] text-muted">Демонстрационные назначения</p>
        </section>
      )}
      {attentionCount > 0 && (
        <button
          type="button"
          onClick={onOpenAlerts}
          className="absolute right-4 bottom-14 z-[1000] flex max-w-[calc(100%-32px)] items-center gap-2 rounded-xl bg-ink px-3.5 py-3 text-[11px] text-white shadow-sm"
        >
          <Icon name="alert" className="size-4" />
          <span>Требуют внимания: {attentionCount}</span>
          <Icon name="chevron" className="ml-3 size-3" />
        </button>
      )}
    </MapPanelMenu>
  );
}
