import type { ActorRole, HistoryEventType } from '@/types/workday';
export const historyLabels: Record<HistoryEventType, string> = {
  'data-loaded': 'Загрузка данных',
  'routes-built': 'Построение маршрутов',
  'status-changed': 'Статус встречи',
  'assignment-changed': 'Назначение',
  'alert-created': 'Новое обращение',
  'alert-resolved': 'Обработка обращения',
};
export const actorLabels: Record<ActorRole, string> = {
  dispatcher: 'Диспетчер',
  representative: 'Представитель',
  system: 'Система',
};
export function formatEventDate(value: string) {
  return new Intl.DateTimeFormat('ru', {
    timeZone: 'Asia/Novosibirsk',
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(value));
}
