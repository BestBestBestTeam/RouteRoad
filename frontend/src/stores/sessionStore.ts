import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';
import { DEMO_USERS } from '@/config/demoUsers';
import type { DemoProfile, DemoRole, DemoUser } from '@/types/session';

type DemoProfiles = Partial<Record<DemoRole, DemoProfile>>;

interface DemoSessionStore {
  role: DemoRole | null;
  user: DemoUser | null;
  profiles: DemoProfiles;
  enter: (role: DemoRole) => void;
  leave: () => void;
  updateProfile: (patch: DemoProfile) => void;
}

function normalizeName(name: string): string {
  return name.trim().replace(/\s+/g, ' ');
}

function validName(name: string): boolean {
  return name.length >= 2 && name.length <= 64;
}

function buildUser(role: DemoRole, profiles: DemoProfiles): DemoUser {
  const name = profiles[role]?.name ?? DEMO_USERS[role].name;
  const initials = name
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('');
  return { ...DEMO_USERS[role], name, initials };
}

function readProfiles(value: unknown): DemoProfiles {
  if (typeof value !== 'object' || value === null) return {};
  const profiles: DemoProfiles = {};
  for (const role of ['dispatcher', 'representative'] as const) {
    const profile: unknown =
      role === 'dispatcher'
        ? 'dispatcher' in value
          ? value.dispatcher
          : null
        : 'representative' in value
          ? value.representative
          : null;
    if (
      typeof profile !== 'object' ||
      profile === null ||
      !('name' in profile) ||
      typeof profile.name !== 'string'
    )
      continue;
    const name = normalizeName(profile.name);
    if (validName(name)) profiles[role] = { name };
  }
  return profiles;
}

export const useDemoSessionStore = create<DemoSessionStore>()(
  persist(
    (set, get) => ({
      role: null,
      user: null,
      profiles: {},
      enter: (role) => set({ role, user: buildUser(role, get().profiles) }),
      leave: () => set({ role: null, user: null }),
      updateProfile: ({ name: input }) => {
        const { role, profiles } = get();
        if (!role) throw new Error('Сначала выберите демопользователя.');
        const name = normalizeName(input);
        if (!validName(name)) throw new Error('Введите имя длиной от 2 до 64 символов.');
        const nextProfiles = { ...profiles, [role]: { name } };
        set({ profiles: nextProfiles, user: buildUser(role, nextProfiles) });
      },
    }),
    {
      name: 'routeroad-demo-session-v1',
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({ role: state.role, profiles: state.profiles }),
      merge: (stored, current) => {
        const storedRole =
          typeof stored === 'object' && stored !== null && 'role' in stored ? stored.role : null;
        const role =
          storedRole === 'dispatcher' || storedRole === 'representative' ? storedRole : null;
        const profiles = readProfiles(
          typeof stored === 'object' && stored !== null && 'profiles' in stored
            ? stored.profiles
            : null,
        );
        return { ...current, profiles, role, user: role ? buildUser(role, profiles) : null };
      },
    },
  ),
);
