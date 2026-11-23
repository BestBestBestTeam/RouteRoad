import { BrowserRouter, Navigate, Route, Routes } from 'react-router';
import { useDemoSessionStore } from '@/stores/sessionStore';
import LoginPage from '@/pages/LoginPage';
import DashboardPage from '@/pages/dispatcher/DashboardPage';
import RequestsPage from '@/pages/dispatcher/RequestsPage';
import RepresentativesPage from '@/pages/dispatcher/RepresentativesPage';
import AlertsPage from '@/pages/dispatcher/AlertsPage';
import HistoryPage from '@/pages/dispatcher/HistoryPage';
import SchedulePage from '@/pages/representative/SchedulePage';
import RoutePage from '@/pages/representative/RoutePage';
import ProfilePage from '@/pages/representative/ProfilePage';
import MeetingPage from '@/pages/representative/MeetingPage';
import StatusPage from '@/pages/StatusPage';
import { ProtectedRoute } from '@/routes/ProtectedRoute';
import { DispatcherLayout } from '@/components/layout/DispatcherLayout';
import { RepresentativeLayout } from '@/components/layout/RepresentativeLayout';

function RoleHome() {
  const role = useDemoSessionStore((state) => state.role);
  return <Navigate to={role ? `/${role}` : '/login'} replace />;
}

export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<RoleHome />} />
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute role="dispatcher" />}>
          <Route path="/dispatcher" element={<DispatcherLayout />}>
            <Route index element={<DashboardPage />} />
            <Route path="requests" element={<RequestsPage />} />
            <Route path="representatives" element={<RepresentativesPage />} />
            <Route path="alerts" element={<AlertsPage />} />
            <Route path="history" element={<HistoryPage />} />
          </Route>
        </Route>
        <Route element={<ProtectedRoute role="representative" />}>
          <Route path="/representative" element={<RepresentativeLayout />}>
            <Route index element={<SchedulePage />} />
            <Route path="route" element={<RoutePage />} />
            <Route path="profile" element={<ProfilePage />} />
            <Route path="visits/:orderId" element={<MeetingPage />} />
          </Route>
        </Route>
        <Route path="/403" element={<StatusPage forbidden />} />
        <Route path="*" element={<StatusPage />} />
      </Routes>
    </BrowserRouter>
  );
}
