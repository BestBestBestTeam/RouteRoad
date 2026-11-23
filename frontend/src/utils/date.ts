export function formatServiceDate(serviceDate: string): string {
  const date = new Date(`${serviceDate}T12:00:00+07:00`);
  if (Number.isNaN(date.getTime())) return 'Рабочий день';
  const label = new Intl.DateTimeFormat('ru-RU', {
    weekday: 'short',
    day: 'numeric',
    month: 'long',
    timeZone: 'Asia/Novosibirsk',
  }).format(date);
  return label.charAt(0).toUpperCase() + label.slice(1);
}
