export type DemoRole = 'dispatcher' | 'representative';

export interface DemoProfile {
  name: string;
}

export interface DemoUser {
  id: string;
  role: DemoRole;
  name: string;
  initials: string;
  representativeId: string | null;
  city: string;
  shiftStart: string;
  shiftEnd: string;
}
