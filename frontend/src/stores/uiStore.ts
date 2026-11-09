import { create } from 'zustand';

interface UiStore {
  menuOpen: boolean;
  sidebarCollapsed: boolean;
  setMenuOpen: (open: boolean) => void;
  toggleSidebar: () => void;
}

export const useUiStore = create<UiStore>((set) => ({
  menuOpen: false,
  sidebarCollapsed: false,
  setMenuOpen: (menuOpen) => set({ menuOpen }),
  toggleSidebar: () => set((state) => ({ sidebarCollapsed: !state.sidebarCollapsed })),
}));
