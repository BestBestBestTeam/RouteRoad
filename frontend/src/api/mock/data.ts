import { DEMO_SERVICE_DATE } from '@/config/demo';
import type { Office, Representative, Visit, WorkdaySnapshot } from '@/types/workday';

const office: Office = {
  id: 'office-main',
  name: 'Офис банка',
  address: 'ул. Ленина, 12',
  location: { lat: 55.0304, lon: 82.9206 },
};

const representatives: Representative[] = [
  {
    id: 'alexander',
    name: 'Александр Смирнов',
    shortName: 'Александр С.',
    initials: 'АС',
    completed: 0,
    total: 0,
    tone: 'yellow',
  },
  {
    id: 'maria',
    name: 'Мария Петрова',
    shortName: 'Мария П.',
    initials: 'МП',
    completed: 0,
    total: 0,
    tone: 'purple',
  },
  {
    id: 'ilya',
    name: 'Илья Романов',
    shortName: 'Илья Р.',
    initials: 'ИР',
    completed: 0,
    total: 0,
    tone: 'mint',
  },
  {
    id: 'denis',
    name: 'Денис Котов',
    shortName: 'Денис К.',
    initials: 'ДК',
    completed: 0,
    total: 0,
    tone: 'red',
  },
  {
    id: 'elena',
    name: 'Елена Волкова',
    shortName: 'Елена В.',
    initials: 'ЕВ',
    completed: 0,
    total: 0,
    tone: 'blue',
  },
  {
    id: 'pavel',
    name: 'Павел Никитин',
    shortName: 'Павел Н.',
    initials: 'ПН',
    completed: 0,
    total: 0,
    tone: 'gray',
  },
];

interface DemoRequest {
  number: string;
  client: string;
  address: string;
  lat: number;
  lon: number;
  time: string;
  endTime: string;
  duration?: number;
}

// Synthetic coordinates illustrate points near the city centre; these are not geocoding results.
const requests: DemoRequest[] = [
  {
    number: '024',
    client: 'Анна М.',
    address: 'Красный проспект, 25',
    lat: 55.0333,
    lon: 82.9239,
    time: '11:40',
    endTime: '12:10',
  },
  {
    number: '038',
    client: 'Ольга С.',
    address: 'ул. Депутатская, 2',
    lat: 55.0284,
    lon: 82.9265,
    time: '12:30',
    endTime: '13:00',
  },
  {
    number: '042',
    client: 'Денис П.',
    address: 'ул. Фрунзе, 19',
    lat: 55.0408,
    lon: 82.9268,
    time: '13:30',
    endTime: '13:50',
    duration: 20,
  },
  {
    number: '048',
    client: 'Елена В.',
    address: 'ул. Гоголя, 8',
    lat: 55.0435,
    lon: 82.9279,
    time: '14:20',
    endTime: '14:50',
  },
  {
    number: '031',
    client: 'Михаил К.',
    address: 'ул. Советская, 18',
    lat: 55.0291,
    lon: 82.9164,
    time: '09:30',
    endTime: '10:00',
  },
  {
    number: '055',
    client: 'Светлана Б.',
    address: 'ул. Крылова, 31',
    lat: 55.0488,
    lon: 82.9322,
    time: '11:00',
    endTime: '11:30',
  },
  {
    number: '061',
    client: 'Максим Т.',
    address: 'ул. Романова, 26',
    lat: 55.0383,
    lon: 82.9301,
    time: '13:00',
    endTime: '13:30',
  },
  {
    number: '067',
    client: 'Алина Н.',
    address: 'ул. Державина, 11',
    lat: 55.0437,
    lon: 82.9221,
    time: '09:40',
    endTime: '10:10',
  },
  {
    number: '073',
    client: 'Олег Д.',
    address: 'ул. Максима Горького, 77',
    lat: 55.0246,
    lon: 82.9214,
    time: '11:10',
    endTime: '11:40',
  },
  {
    number: '079',
    client: 'Ирина С.',
    address: 'ул. Каменская, 32',
    lat: 55.0325,
    lon: 82.9352,
    time: '13:30',
    endTime: '14:00',
  },
  {
    number: '085',
    client: 'Сергей А.',
    address: 'ул. Мичурина, 6',
    lat: 55.0371,
    lon: 82.9193,
    time: '10:00',
    endTime: '10:30',
  },
  {
    number: '091',
    client: 'Татьяна И.',
    address: 'ул. Семьи Шамшиных, 15',
    lat: 55.0354,
    lon: 82.9408,
    time: '11:30',
    endTime: '12:00',
  },
  {
    number: '097',
    client: 'Алексей Н.',
    address: 'ул. Орджоникидзе, 23',
    lat: 55.0354,
    lon: 82.9248,
    time: '14:00',
    endTime: '14:30',
  },
  {
    number: '103',
    client: 'Виктория Р.',
    address: 'ул. Потанинская, 10',
    lat: 55.0298,
    lon: 82.9132,
    time: '09:30',
    endTime: '10:00',
  },
  {
    number: '109',
    client: 'Андрей В.',
    address: 'ул. Дмитрия Шамшурина, 9',
    lat: 55.0453,
    lon: 82.9171,
    time: '11:40',
    endTime: '12:10',
  },
  {
    number: '115',
    client: 'Екатерина С.',
    address: 'Красный проспект, 50',
    lat: 55.0468,
    lon: 82.9228,
    time: '14:10',
    endTime: '14:40',
  },
  {
    number: '121',
    client: 'Наталья П.',
    address: 'ул. Советская, 52',
    lat: 55.0491,
    lon: 82.9142,
    time: '10:20',
    endTime: '10:50',
  },
  {
    number: '127',
    client: 'Артём Л.',
    address: 'ул. Ленина, 24',
    lat: 55.0284,
    lon: 82.9053,
    time: '12:20',
    endTime: '12:50',
  },
];

export const demoAssignments: Readonly<Record<string, readonly string[]>> = {
  alexander: ['visit-024', 'visit-038', 'visit-042', 'visit-048'],
  maria: ['visit-031', 'visit-055', 'visit-061'],
  ilya: ['visit-067', 'visit-073', 'visit-079'],
  denis: ['visit-085', 'visit-091', 'visit-097'],
  elena: ['visit-103', 'visit-109', 'visit-115'],
  pavel: ['visit-121', 'visit-127'],
};

export function createLoadedDemoSnapshot(): WorkdaySnapshot {
  const visits: Visit[] = requests.map((request) => ({
    id: `visit-${request.number}`,
    number: request.number,
    client: request.client,
    address: request.address,
    district: 'Центральный район · синтетический адрес',
    location: { lat: request.lat, lon: request.lon },
    time: request.time,
    endTime: request.endTime,
    duration: request.duration ?? 30,
    representativeId: null,
    status: 'new',
    product: 'Дебетовая карта',
    assignment: null,
  }));
  return {
    visits,
    representatives: representatives.map((representative) => ({ ...representative })),
    routes: [],
    office: { ...office, location: { ...office.location } },
    history: [],
    alerts: [],
    serviceDate: DEMO_SERVICE_DATE,
  };
}
