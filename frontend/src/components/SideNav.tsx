import { MouseEvent as ReactMouseEvent, ReactNode, useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  Box,
  Divider,
  Drawer,
  InputAdornment,
  Link as MuiLink,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  TextField,
  Tooltip,
  IconButton,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import CheckCircleOutline from '@mui/icons-material/CheckCircleOutline';
import MenuBook from '@mui/icons-material/MenuBook';
import EventNote from '@mui/icons-material/EventNote';
import LocalLibrary from '@mui/icons-material/LocalLibrary';
import StickyNote2 from '@mui/icons-material/StickyNote2';
import Flag from '@mui/icons-material/Flag';
import Create from '@mui/icons-material/Create';
import AccountTree from '@mui/icons-material/AccountTree';
import School from '@mui/icons-material/School';
import ShowChart from '@mui/icons-material/ShowChart';
import CalendarMonth from '@mui/icons-material/CalendarMonth';
import ViewKanban from '@mui/icons-material/ViewKanban';
import Dashboard from '@mui/icons-material/Dashboard';
import InsightsIcon from '@mui/icons-material/Insights';
import StarIcon from '@mui/icons-material/Star';
import AdminPanelSettings from '@mui/icons-material/AdminPanelSettings';
import Tune from '@mui/icons-material/Tune';
import History from '@mui/icons-material/History';
import Settings from '@mui/icons-material/Settings';
import HelpOutline from '@mui/icons-material/HelpOutline';
import MoveToInbox from '@mui/icons-material/MoveToInbox';
import PlaylistAdd from '@mui/icons-material/PlaylistAdd';
import DriveFileMove from '@mui/icons-material/DriveFileMove';
import Photo from '@mui/icons-material/Photo';
import Public from '@mui/icons-material/Public';
import RssFeed from '@mui/icons-material/RssFeed';
import SearchIcon from '@mui/icons-material/Search';
import AssessmentIcon from '@mui/icons-material/Assessment';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import People from '@mui/icons-material/People';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import Security from '@mui/icons-material/Security';
import Logout from '@mui/icons-material/Logout';
import { useAuth } from '../hooks/useAuth';
import { BASE, Permission } from '../api/client';
import { useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { ENTITY_BY_KEY } from '../config/entities';
import { reportsFor } from '../config/reports';
import { useModules } from '../hooks/useModules';
import { getSetting, getSettingsMulti, listEntities, ListParams } from '../api/client';
import { FEATURE_SETTING_KEYS, PAGE_SETTING_KEYS, featureEnabled, pageEnabled } from '../config/pages';
import { SavedSearchPanel } from './SavedSearchPanel';
import type { SavedSearchRecordRef } from '../api/client';
import { CalendarNotebookIcon } from './CalendarNotebookIcon';

const ICONS: Record<string, typeof Flag> = {
  CheckCircleOutline, MenuBook, EventNote, LocalLibrary, StickyNote2, Flag, Create,
  AccountTree, School, ShowChart,
};

/** Per-module icon names not derived from entities.ts (modules without a field config). */
const MODULE_ICON: Record<string, string> = { indicators: 'ShowChart' };

const ROLE_LABEL: Record<string, string> = {
  ADMIN: 'Admin',
  DATA_ENTRY: 'Data entry',
  PARAMETERS_DATA_ENTRY: 'Parameters data entry',
  READ_ONLY: 'Read only',
};

// Each tool optionally requires a capability key; who holds it is decided by the DB permission
// matrix (editable at /permissions). Tools with no `perm` are visible to any signed-in user. All are
// still subject to the per-page enabled setting.
const TOOLS: { path: string; label: string; icon: typeof Flag; perm?: Permission }[] = [
  { path: '/calendar', label: 'Calendar', icon: CalendarMonth },
  { path: '/kanban', label: 'Kanban', icon: ViewKanban },
  { path: '/dashboards', label: 'Dashboards', icon: Dashboard },
  { path: '/insights', label: 'Insights', icon: InsightsIcon },
  { path: '/bookmarks', label: 'All bookmarks', icon: StarIcon },
  { path: '/parameters', label: 'Parameters', icon: Tune, perm: 'params.write' },
  { path: '/admin', label: 'Admin', icon: AdminPanelSettings, perm: 'tools.admin' },
  { path: '/batch', label: 'Batch entry', icon: PlaylistAdd, perm: 'content.write' },
  { path: '/import-files', label: 'Import files', icon: DriveFileMove, perm: 'content.write' },
  { path: '/import', label: 'Import notes', icon: MoveToInbox, perm: 'content.write' },
  { path: '/images', label: 'Image renamer', icon: Photo, perm: 'content.write' },
  { path: '/sites', label: 'Static website', icon: Public, perm: 'content.write' },
  { path: '/audit', label: 'Audit log', icon: History, perm: 'tools.audit' },
  { path: '/trash', label: 'Trash', icon: DeleteOutline, perm: 'content.write' },
  { path: '/settings', label: 'Settings', icon: Settings, perm: 'tools.settings' },
  { path: '/help', label: 'Help & shortcuts', icon: HelpOutline },
];

/** Outlook-style rail: search · calendar · reports for the active module · module + tool buttons. */
export function SideNav({
  calendar,
  onSearchRecords,
}: {
  /** The rail's mini calendar (MiniCalendar), rendered inline — it owns its own two-week/full-month
   * expand state. */
  calendar?: ReactNode;
  onSearchRecords?: (title: string, records: SavedSearchRecordRef[]) => void;
}) {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { pathname, search } = useLocation();
  const { entity } = useParams();
  // Clicking a tool icon for the page you're already on can't re-navigate (same route = no
  // remount), so the data would stay stale. Treat it as a refresh: refetch the active queries.
  const goTool = (path: string) => {
    if (pathname === path) qc.invalidateQueries();
    else navigate(path);
  };
  const { enabled } = useModules();
  const { user, logout, can } = useAuth();
  const { data: appName = 'Nibras' } = useQuery({
    queryKey: ['setting', 'app.name'],
    queryFn: () => getSetting('app.name', 'Nibras'),
    staleTime: 300_000,
  });
  // The browser tab title follows the configurable app name (Settings → Application) instead of
  // the static fallback baked into index.html — SideNav is mounted on every authenticated page.
  useEffect(() => {
    document.title = appName;
  }, [appName]);
  const { data: pageSettings } = useQuery({
    queryKey: ['page-settings'],
    queryFn: () => getSettingsMulti(PAGE_SETTING_KEYS),
    staleTime: 60_000,
  });
  const { data: featureSettings } = useQuery({
    queryKey: ['feature-settings'],
    queryFn: () => getSettingsMulti(FEATURE_SETTING_KEYS),
    staleTime: 60_000,
  });
  const showModuleReports = featureEnabled(featureSettings, 'moduleReports');
  const showRss = featureEnabled(featureSettings, 'rss');
  const [q, setQ] = useState('');
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem('nibras.railCollapsed') === '1');
  const [width, setWidth] = useState(() => {
    const saved = Number(localStorage.getItem('nibras.railWidth'));
    return saved >= 220 && saved <= 520 ? saved : 280;
  });
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const [mobileOpen, setMobileOpen] = useState(false);

  // Close the mobile drawer whenever the route changes.
  useEffect(() => setMobileOpen(false), [pathname, search]);

  const setRail = (v: boolean) => {
    setCollapsed(v);
    localStorage.setItem('nibras.railCollapsed', v ? '1' : '0');
  };

  // Drag the right edge to resize the rail; the width is remembered.
  const startResize = (e: ReactMouseEvent) => {
    e.preventDefault();
    let next = width;
    const onMove = (ev: MouseEvent) => {
      next = Math.min(520, Math.max(220, ev.clientX));
      setWidth(next);
    };
    const onUp = () => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
      document.body.style.userSelect = '';
      localStorage.setItem('nibras.railWidth', String(next));
    };
    document.body.style.userSelect = 'none';
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  const activeModule = entity && ENTITY_BY_KEY[entity] ? entity : 'tasks';
  const reports = reportsFor(activeModule);
  const moduleLabel = ENTITY_BY_KEY[activeModule]?.label ?? activeModule;

  // A matching-record count per report, shown as a badge so empty reports are obvious. One request
  // per report, so skip them entirely when the reports section is switched off.
  const reportCounts = useQueries({
    queries: (showModuleReports ? reports : []).map((r) => ({
      queryKey: ['report-count', activeModule, r.key],
      queryFn: () =>
        listEntities(activeModule, { ...r.params, size: 1 } as unknown as ListParams).then(
          (p) => p.totalElements,
        ),
      staleTime: 30_000,
    })),
  });

  const runReport = (params: Record<string, string>) =>
    navigate(`/${activeModule}?${new URLSearchParams(params).toString()}`);

  const rail = (
    <Box
      sx={{
        width: isMobile ? 280 : width,
        flexShrink: 0,
        borderRight: 1,
        borderColor: 'divider',
        display: 'flex',
        flexDirection: 'column',
        bgcolor: '#f3f2f1',
        height: '100vh',
        position: 'relative',
      }}
    >
      <Stack direction="row" alignItems="center" spacing={0.75} sx={{ pl: 1.5, pr: 0.5, pt: 1 }}>
        <CalendarNotebookIcon sx={{ fontSize: 32, color: 'primary.main' }} />
        <Typography variant="h6" sx={{ fontWeight: 700, color: 'primary.main' }}>
          {appName}
        </Typography>
        <Typography
          variant="caption"
          sx={{ color: 'text.secondary', alignSelf: 'flex-end', mb: 0.5, whiteSpace: 'nowrap' }}
        >
          v{__APP_VERSION__}.b{__BUILD_NUMBER__} &quot;{__APP_CODENAME__}&quot;
        </Typography>
        <Box sx={{ flex: 1 }} />
        {!isMobile && (
          <Tooltip title="Collapse sidebar">
            <IconButton size="small" onClick={() => setRail(true)}>
              <ChevronLeftIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
      </Stack>

      {/* Global full-text search */}
      <Box sx={{ px: 1.5, py: 0.5 }}>
        <TextField
          size="small"
          fullWidth
          id="global-search"
          placeholder="Search everything… ( / )"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && q.trim()) navigate(`/search?q=${encodeURIComponent(q.trim())}`);
          }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" />
              </InputAdornment>
            ),
          }}
        />
      </Box>

      {/* The mini calendar renders inline again (two weeks by default, expandable to the full
          month) — the one-line field + popover freed space but hid the day badges that make the
          rail useful at a glance. */}
      <Box sx={{ mt: -0.5 }}>{calendar}</Box>

      <Divider />

      {/* Reports for the active module. The built-in shortcuts and the RSS link are each behind
          their own Settings feature toggle; saved searches below are always shown, since those are
          the user's own and nobody would look in Settings for why they vanished. */}
      <Box sx={{ flex: 1, overflow: 'auto', px: 1, py: 0.5 }}>
        {showModuleReports && (
          <Stack direction="row" alignItems="center" spacing={0.5} sx={{ px: 1, py: 0.5 }}>
            <AssessmentIcon fontSize="small" color="action" />
            <Typography variant="overline" color="text.secondary">
              {moduleLabel} reports
            </Typography>
          </Stack>
        )}
        <List dense disablePadding>
          {showModuleReports && reports.map((r, i) => {
            const count = reportCounts[i]?.data;
            const empty = count === 0;
            return (
              <ListItemButton
                key={r.key}
                sx={{ py: 0.25, borderRadius: 1, opacity: empty ? 0.5 : 1 }}
                onClick={() => runReport(r.params)}
              >
                <ListItemText primary={r.label} primaryTypographyProps={{ fontSize: 13 }} />
                {count != null && (
                  <Typography
                    variant="caption"
                    sx={{ ml: 1, color: empty ? 'text.disabled' : 'text.secondary', fontVariantNumeric: 'tabular-nums' }}
                  >
                    {count}
                  </Typography>
                )}
              </ListItemButton>
            );
          })}
          {showRss && (
            <ListItemButton
              component="a"
              href={`${BASE}feeds/${activeModule}.xml`}
              target="_blank"
              rel="noreferrer"
              sx={{ py: 0.25, borderRadius: 1 }}
            >
              <ListItemIcon sx={{ minWidth: 28 }}>
                <RssFeed fontSize="small" />
              </ListItemIcon>
              <ListItemText primary="RSS (bookmarked)" primaryTypographyProps={{ fontSize: 13 }} />
            </ListItemButton>
          )}
        </List>

        {/* User-defined reports for the active module, after the standard ones. */}
        <SavedSearchPanel
          module={activeModule}
          onRunForm={(query) => navigate(`/${activeModule}?${query}`)}
          onRecords={onSearchRecords}
        />
      </Box>

      <Divider />

      {/* Module buttons (Outlook navigation) */}
      <List dense disablePadding sx={{ px: 1, py: 0.25 }}>
        {enabled.map((m) => {
          const Icon = ICONS[MODULE_ICON[m.moduleKey] ?? ENTITY_BY_KEY[m.moduleKey]?.icon ?? ''] ?? Flag;
          return (
            <ListItemButton
              key={m.moduleKey}
              selected={pathname === `/${m.moduleKey}` && !search}
              onClick={() => navigate(`/${m.moduleKey}`)}
              sx={{ py: 0.1 }}
            >
              <ListItemIcon sx={{ minWidth: 32, color: 'primary.main' }}>
                <Icon fontSize="small" />
              </ListItemIcon>
              <ListItemText primary={m.label} />
              {m.code && (
                <Typography variant="caption" sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>
                  {m.code}
                </Typography>
              )}
            </ListItemButton>
          );
        })}
      </List>

      <Divider />

      {/* Tool pages */}
      <Stack direction="row" flexWrap="wrap" sx={{ p: 0.5, justifyContent: 'center' }}>
        {TOOLS.filter((t) => (!t.perm || can(t.perm)) && pageEnabled(pageSettings, t.path.slice(1))).map((t) => {
          const Icon = t.icon;
          return (
            <Tooltip key={t.path} title={t.label}>
              <IconButton
                size="small"
                color="primary"
                onClick={() => goTool(t.path)}
                sx={{ bgcolor: pathname === t.path ? 'action.selected' : undefined }}
              >
                <Icon fontSize="small" />
              </IconButton>
            </Tooltip>
          );
        })}
        {can('users.manage') && (
          <>
            <Tooltip title="Users">
              <IconButton
                size="small"
                color="primary"
                onClick={() => goTool('/users')}
                sx={{ bgcolor: pathname === '/users' ? 'action.selected' : undefined }}
              >
                <People fontSize="small" />
              </IconButton>
            </Tooltip>
            <Tooltip title="Roles & permissions">
              <IconButton
                size="small"
                color="primary"
                onClick={() => goTool('/permissions')}
                sx={{ bgcolor: pathname === '/permissions' ? 'action.selected' : undefined }}
              >
                <Security fontSize="small" />
              </IconButton>
            </Tooltip>
          </>
        )}
      </Stack>

      {/* Signed-in user + sign out */}
      {user && (
        <>
          <Divider />
          <Stack direction="row" alignItems="center" spacing={1} sx={{ px: 1.5, py: 0.75 }}>
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <Typography variant="body2" noWrap sx={{ fontWeight: 600 }}>
                {user.displayName || user.username}
              </Typography>
              <Typography variant="caption" color="text.secondary" noWrap>
                {ROLE_LABEL[user.role]}
              </Typography>
            </Box>
            <Tooltip title="Sign out">
              <IconButton size="small" onClick={logout}>
                <Logout fontSize="small" />
              </IconButton>
            </Tooltip>
          </Stack>
        </>
      )}

      {/* Drag the right edge to resize the rail. */}
      {!isMobile && (
        <Box
          onMouseDown={startResize}
          sx={{
            position: 'absolute',
            top: 0,
            right: -3,
            width: 6,
            height: '100%',
            cursor: 'col-resize',
            zIndex: 1,
            '&:hover': { bgcolor: 'primary.main', opacity: 0.4 },
          }}
        />
      )}
    </Box>
  );

  // Mobile: the rail lives in a drawer opened by a floating menu button.
  if (isMobile) {
    return (
      <>
        <IconButton
          onClick={() => setMobileOpen(true)}
          sx={{
            position: 'fixed',
            top: 8,
            left: 8,
            zIndex: (t) => t.zIndex.drawer + 2,
            bgcolor: 'background.paper',
            boxShadow: 2,
            '&:hover': { bgcolor: 'background.paper' },
          }}
        >
          <MenuIcon />
        </IconButton>
        <Drawer open={mobileOpen} onClose={() => setMobileOpen(false)}>
          {rail}
        </Drawer>
      </>
    );
  }

  // Desktop collapsed: a thick clickable bar that re-opens the rail.
  if (collapsed) {
    return (
      <Box
        onClick={() => setRail(false)}
        title="Expand sidebar"
        sx={{
          width: 22,
          flexShrink: 0,
          height: '100vh',
          bgcolor: '#e6e6e6',
          borderRight: 1,
          borderColor: 'divider',
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          '&:hover': { bgcolor: '#dadada' },
        }}
      >
        <ChevronRightIcon fontSize="small" color="action" />
      </Box>
    );
  }

  return rail;
}
