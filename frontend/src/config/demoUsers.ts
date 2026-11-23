import type { DemoRole, DemoUser } from '@/types/session';

// Temporary identities for UI development; this is not a source of server permissions.
export const DEMO_USERS: Record<DemoRole, DemoUser> = {
  dispatcher: {
    id: 'dispatcher-demo',
    role: 'dispatcher',
    name: 'Анна Кузнецова',
    initials: 'АК',
    representativeId: null,
    city: 'Новосибирск',
    shiftStart: '09:00',
    shiftEnd: '18:00',
  },
  representative: {
    id: 'representative-demo',
    role: 'representative',
    name: 'Александр Смирнов',
    initials: 'АС',
    representativeId: 'alexander',
    city: 'Новосибирск',
    shiftStart: '09:00',
    shiftEnd: '18:00',
  },
};
