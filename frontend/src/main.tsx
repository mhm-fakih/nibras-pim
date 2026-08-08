import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFnsV3';
// en-GB starts the week on Monday and uses ISO week numbering (for the mini-calendar + all pickers).
import { enGB } from 'date-fns/locale';
import { theme } from './theme';
import { AppShell } from './components/AppShell';
import { AuthProvider } from './hooks/useAuth';
import { RequireAuth } from './components/RequireAuth';
import { ChangePasswordDialog } from './components/ChangePasswordDialog';
import { LoginPage } from './pages/LoginPage';
import { UsersPage } from './pages/UsersPage';
import { PermissionsPage } from './pages/PermissionsPage';
import { ShortcutsProvider } from './components/ShortcutsProvider';
import { NotificationWatcher } from './components/NotificationWatcher';
import { CalendarPage } from './pages/CalendarPage';
import { RecordReadPage } from './pages/RecordReadPage';
import { SettingsPage } from './pages/SettingsPage';
import { AuditPage } from './pages/AuditPage';
import { ImportNotesPage } from './pages/ImportNotesPage';
import { BatchEntryPage } from './pages/BatchEntryPage';
import { ImportFilesPage } from './pages/ImportFilesPage';
import { ImagesPage } from './pages/ImagesPage';
import { KanbanPage } from './pages/KanbanPage';
import { DashboardsPage } from './pages/DashboardsPage';
import { DashboardViewPage } from './pages/DashboardViewPage';
import { AdminPage } from './pages/AdminPage';
import { SearchPage } from './pages/SearchPage';
import { BookmarksPage } from './pages/BookmarksPage';
import { IndicatorsPage } from './pages/IndicatorsPage';
import { InsightsPage } from './pages/InsightsPage';
import { ParametersPage } from './pages/ParametersPage';
import { HelpPage } from './pages/HelpPage';
import { TrashPage } from './pages/TrashPage';
import { SitesPage } from './pages/SitesPage';
import { useModules } from './hooks/useModules';

const queryClient = new QueryClient({
  defaultOptions: { queries: { refetchOnWindowFocus: false, staleTime: 30_000 } },
});

/**
 * The homepage / any unknown route lands on the FIRST ENABLED module (by sortOrder) rather than a
 * hardcoded /tasks — which may be disabled. Falls back to /tasks only until modules have loaded.
 */
function HomeRedirect() {
  const { enabled, isLoading } = useModules();
  if (isLoading) return null;
  return <Navigate to={`/${enabled[0]?.moduleKey ?? 'tasks'}`} replace />;
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <LocalizationProvider dateAdapter={AdapterDateFns} adapterLocale={enGB}>
          <CssBaseline />
          <BrowserRouter basename={new URL(document.baseURI).pathname}>
            <AuthProvider>
            <Routes>
              {/* Public: login, and the chrome-free dashboard view (meant for a TV/kiosk with no
                  session at all — see PublicDashboardService on the backend). */}
              <Route path="/login" element={<LoginPage />} />
              <Route path="/dashboards/:id/view" element={<DashboardViewPage />} />
              {/* Everything else requires a signed-in user. */}
              <Route
                path="/*"
                element={
                  <RequireAuth>
                    <ChangePasswordDialog />
                    <ShortcutsProvider>
                      <NotificationWatcher />
                      <Routes>
                        <Route path="/calendar" element={<CalendarPage />} />
                        {/* Chrome-free, narrow reading view for a single record — opened in a new
                            tab (e.g. from a calendar event) so the page it was opened from stays put. */}
                        <Route path="/read/:entity/:id" element={<RecordReadPage />} />
                        <Route path="/kanban" element={<KanbanPage />} />
                        <Route path="/dashboards" element={<DashboardsPage />} />
                        <Route path="/bookmarks" element={<BookmarksPage />} />
                        <Route path="/indicators" element={<IndicatorsPage />} />
                        <Route path="/insights" element={<InsightsPage />} />
                        <Route
                          path="/admin"
                          element={
                            <RequireAuth permission="tools.admin">
                              <AdminPage />
                            </RequireAuth>
                          }
                        />
                        <Route
                          path="/users"
                          element={
                            <RequireAuth permission="users.manage">
                              <UsersPage />
                            </RequireAuth>
                          }
                        />
                        <Route
                          path="/permissions"
                          element={
                            <RequireAuth permission="users.manage">
                              <PermissionsPage />
                            </RequireAuth>
                          }
                        />
                        <Route path="/search" element={<SearchPage />} />
                        <Route
                          path="/import"
                          element={
                            <RequireAuth permission="content.write">
                              <ImportNotesPage />
                            </RequireAuth>
                          }
                        />
                        <Route
                          path="/batch"
                          element={
                            <RequireAuth permission="content.write">
                              <BatchEntryPage />
                            </RequireAuth>
                          }
                        />
                        <Route
                          path="/import-files"
                          element={
                            <RequireAuth permission="content.write">
                              <ImportFilesPage />
                            </RequireAuth>
                          }
                        />
                        <Route
                          path="/images"
                          element={
                            <RequireAuth permission="content.write">
                              <ImagesPage />
                            </RequireAuth>
                          }
                        />
                        <Route
                          path="/sites"
                          element={
                            <RequireAuth permission="content.write">
                              <SitesPage />
                            </RequireAuth>
                          }
                        />
                        <Route
                          path="/settings"
                          element={
                            <RequireAuth permission="tools.settings">
                              <SettingsPage />
                            </RequireAuth>
                          }
                        />
                        {/* Parameter (lookup) data entry — its own capability, so the
                            PARAMETERS_DATA_ENTRY role reaches it without tools.settings. */}
                        <Route
                          path="/parameters"
                          element={
                            <RequireAuth permission="params.write">
                              <ParametersPage />
                            </RequireAuth>
                          }
                        />
                        <Route path="/help" element={<HelpPage />} />
                        <Route
                          path="/audit"
                          element={
                            <RequireAuth permission="tools.audit">
                              <AuditPage />
                            </RequireAuth>
                          }
                        />
                        <Route
                          path="/trash"
                          element={
                            <RequireAuth permission="content.write">
                              <TrashPage />
                            </RequireAuth>
                          }
                        />
                        <Route path="/:entity" element={<AppShell />} />
                        <Route path="*" element={<HomeRedirect />} />
                      </Routes>
                    </ShortcutsProvider>
                  </RequireAuth>
                }
              />
            </Routes>
            </AuthProvider>
          </BrowserRouter>
        </LocalizationProvider>
      </ThemeProvider>
    </QueryClientProvider>
  </React.StrictMode>,
);
