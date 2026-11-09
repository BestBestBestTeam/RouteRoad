import { Navigate, Outlet } from 'react-router';
import { useDemoSessionStore } from '@/stores/sessionStore';
import type { DemoRole } from '@/types/session';

export function ProtectedRoute({ role }: { role: DemoRole }) {
  const activeRole = useDemoSessionStore((state) => state.role);
  if (activeRole === null) return <Navigate to="/login" replace />;
  if (activeRole !== role) return <Navigate to="/403" replace />;
  return <Outlet />;
}
