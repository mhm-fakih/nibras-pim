import { MouseEvent as ReactMouseEvent, ReactNode, useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  Box,
  Divider,
  Drawer,
  Link as MuiLink,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Stack,
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
import Groups from '@mui/icons-material/Groups';
import School from '@mui/icons-material/School';
import ShowChart from '@mui/icons-material/ShowChart';
import CalendarMonth from '@mui/icons-material/CalendarMonth';
import ViewKanban from '@mui/icons-material/ViewKanban';
import Dashboard from '@mui/icons-material/Dashboard';
import InsightsIcon from '@mui/icons-material/Insights';
import TableChart from '@mui/icons-material/TableChart';
import DirectionsRun from '@mui/icons-material/DirectionsRun';
import StarIcon from '@mui/icons-material/Star';
import StyleIcon from '@mui/icons-material/Style';
import FormatQuote from '@mui/icons-material/FormatQuote';
import ChecklistIcon from '@mui/icons-material/Checklist';
import NotesIcon from '@mui/icons-material/Description';
import AdminPanelSettings from '@mui/icons-material/AdminPanelSettings';
import Tune from '@mui/icons-material/Tune';
import History from '@mui/icons-material/History';
import Today from '@mui/icons-material/Today';
import Storage from '@mui/icons-material/Storage';
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
import ViewQuilt from '@mui/icons-material/ViewQuilt';
import CollectionsBookmark from '@mui/icons-material/CollectionsBookmark';
import ImportExport from '@mui/icons-material/ImportExport';
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
  AccountTree, School, ShowChart, Groups,
};

/** Per-module icon names not derived from entities.ts (modules without a field config). */
const MODULE_ICON: Record<string, string> = { indicators: 'ShowChart' };

const ROLE_LABEL: Record<string, string> = {
  ADMIN: 'Admin',
  DATA_ENTRY: 'Data entry',
  PARAMETERS_DATA_ENTRY: 'Parameters data entry',
  READ_ONLY: 'Read only',
};

type ToolGroupKey = 'views' | 'library' | 'io' | 'admin' | 'help';

/**
 * The tool pages, in named groups. The rail shows one labelled row per group and a click opens the
 * group's pages in a menu beside it — five short rows instead of two dozen unnamed icons, and the
 * reports panel above keeps its height.
 */
const TOOL_GROUPS: { key: ToolGroupKey; label: string; icon: typeof Flag }[] = [
  { key: 'views', label: 'Views', icon: ViewQuilt },
  { key: 'library', label: 'Library', icon: CollectionsBookmark },
  { key: 'io', label: 'Import & export', icon: ImportExport },
  { key: 'admin', label: 'Admin', icon: AdminPanelSettings },
  { key: 'help', label: 'Help & shortcuts', icon: HelpOutline },
];

// Each tool optionally requires a capability key; who holds it is decided by the DB permission
// matrix (editable at /permissions). Tools with no `perm` are visible to any signed-in user. All are
// still subject to the per-page enabled setting.
const TOOLS: {
  path: string;
  label: string;
  icon: typeof Flag;
  group: ToolGroupKey;
  perm?: Permission;
  newTab?: boolean;
}[] = [
  { path: '/today', label: 'Today', icon: Today, group: 'views' },
  { path: '/calendar', label: 'Calendar', icon: CalendarMonth, group: 'views' },
  { path: '/kanban', label: 'Kanban', icon: ViewKanban, group: 'views' },
  { path: '/dashboards', label: 'Dashboards', icon: Dashboard, group: 'views' },
  { path: '/insights', label: 'Insights', icon: InsightsIcon, group: 'views' },
  { path: '/sprints', label: 'Sprints', icon: DirectionsRun, group: 'views' },
  { path: '/reports', label: 'Reports', icon: TableChart, group: 'views' },
  { path: '/bookmarks', label: 'All bookmarks', icon: StarIcon, group: 'library' },
  { path: '/flashcards', label: 'Flash cards', icon: StyleIcon, group: 'library' },
  { path: '/highlights', label: 'Highlights', icon: FormatQuote, group: 'library' },
  { path: '/local-notes', label: 'Local notes', icon: NotesIcon, group: 'library' },
  // A full-screen wall view, so it opens in its own tab rather than replacing this one.
  { path: '/checklists/view', label: 'Checklists (week)', icon: ChecklistIcon, group: 'library', newTab: true },
  { path: '/batch', label: 'Batch entry', icon: PlaylistAdd, group: 'io', perm: 'content.write' },
  { path: '/import-files', label: 'Import files', icon: DriveFileMove, group: 'io', perm: 'content.write' },
  { path: '/import', label: 'Import notes', icon: MoveToInbox, group: 'io', perm: 'content.write' },
  { path: '/images', label: 'Image renamer', icon: Photo, group: 'io', perm: 'content.write' },
  { path: '/sites', label: 'Static website', icon: Public, group: 'io', perm: 'content.write' },
  { path: '/parameters', label: 'Parameters', icon: Tune, group: 'admin', perm: 'params.write' },
  { path: '/admin', label: 'Admin', icon: AdminPanelSettings, group: 'admin', perm: 'tools.admin' },
  { path: '/maintenance', label: 'Maintenance', icon: Storage, group: 'admin', perm: 'content.write' },
  { path: '/audit', label: 'Audit log', icon: History, group: 'admin', perm: 'tools.audit' },
  { path: '/trash', label: 'Trash', icon: DeleteOutline, group: 'admin', perm: 'content.write' },
  { path: '/settings', label: 'Settings', icon: Settings, group: 'admin', perm: 'tools.settings' },
  { path: '/help', label: 'Help & shortcuts', icon: HelpOutline, group: 'help' },
];

/** A tool as the rail's group menus draw it. */
interface ToolEntry {
  path: string;
  label: string;
  icon: typeof Flag;
  group: ToolGroupKey;
  /** Open in a new browser tab instead of navigating this one. */
  newTab?: boolean;
}

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
  // One page of one row per module is enough to read its total.
  const countQueries = useQueries({
    queries: enabled.map((m) => ({
      queryKey: ['module-count', m.moduleKey],
      queryFn: () => listEntities(m.moduleKey, { size: 1 }),
      staleTime: 60_000,
    })),
  });
  const moduleCounts: Record<string, number | undefined> = Object.fromEntries(
    enabled.map((m, i) => [m.moduleKey, countQueries[i]?.data?.totalElements]),
  );
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
  const showCodename = featureEnabled(featureSettings, 'codename');
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem('nibras.railCollapsed') === '1');
  const [width, setWidth] = useState(() => {
    const saved = Number(localStorage.getItem('nibras.railWidth'));
    return saved >= 220 && saved <= 520 ? saved : 330;
  });
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const [mobileOpen, setMobileOpen] = useState(false);

  // The tool group whose menu is open, and the row it opened from.
  const [toolMenu, setToolMenu] = useState<{ group: ToolGroupKey; anchor: HTMLElement } | null>(null);

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

  // One list for both render modes, so a tool can never appear in one and not the other. The two
  // admin entries used to be hand-written after the loop; they are ordinary tools with a capability.
  const tools: ToolEntry[] = useMemo(() => {
    const list: ToolEntry[] = TOOLS
      .filter((t) => (!t.perm || can(t.perm)) && pageEnabled(pageSettings, t.path.slice(1)))
      .map(({ path, label, icon, group, newTab }) => ({ path, label, icon, group, newTab }));
    if (can('users.manage')) {
      list.push({ path: '/users', label: 'Users', icon: People, group: 'admin' });
      list.push({ path: '/permissions', label: 'Roles & permissions', icon: Security, group: 'admin' });
    }
    return list;
    // `can` closes over the user's permissions, which change when the matrix is edited.
  }, [pageSettings, user]);   // eslint-disable-line react-hooks/exhaustive-deps

  const openTool = (t: ToolEntry) =>
    t.newTab ? window.open(`${BASE}${t.path.slice(1)}`, '_blank', 'noopener') : goTool(t.path);
  const groups = TOOL_GROUPS
    .map((g) => ({ ...g, tools: tools.filter((t) => t.group === g.key) }))
    .filter((g) => g.tools.length > 0);
  const menuTools = toolMenu ? tools.filter((t) => t.group === toolMenu.group) : [];

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
          title={`v${__APP_VERSION__}.b${__BUILD_NUMBER__}${showCodename ? ` "${__APP_CODENAME__}"` : ''}`}
          sx={{
            color: 'text.secondary', alignSelf: 'flex-end', mb: 0.5, whiteSpace: 'nowrap',
            // Gives way to the collapse button: a long version and codename used to push the
            // button past the sidebar's edge, onto the page's own back arrow.
            minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis',
          }}
        >
          {/* Version and build number always; the codename only when Settings → Features says so
              — the same switch the status bar reads, so the two never disagree. */}
          v{__APP_VERSION__}.b{__BUILD_NUMBER__}
          {showCodename && ` "${__APP_CODENAME__}"`}
        </Typography>
        <Box sx={{ flex: 1 }} />
      </Stack>

      {/* The mini calendar renders inline again (two weeks by default, expandable to the full
          month) — the one-line field + popover freed space but hid the day badges that make the
          rail useful at a glance. */}
      <Box sx={{ mt: -0.5 }}>{calendar}</Box>

      <Divider />

      {/* Reports for the active module. The built-in shortcuts and the RSS link are each behind
          their own Settings feature toggle; saved searches below are always shown, since those are
          the user's own and nobody would look in Settings for why they vanished. */}
      {/* At least the heading and about three reports, even with the mini calendar open above;
          the module list below gives way (scrolls) on a short window instead. */}
      <Box sx={{ flex: 1, minHeight: 130, overflow: 'auto', px: 1, py: 0.5 }}>
        <Box>
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
      </Box>

      <Divider />

      {/* Module buttons (Outlook navigation), two to a row so the list takes half the height. Each
          shows its record count, the total the module's own list would page through. */}
      <List
        dense
        disablePadding
        sx={{
          px: 1,
          py: 0.25,
          flexShrink: 1,
          minHeight: 0,
          overflowY: 'auto',
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          columnGap: 0.5,
        }}
      >
        {enabled.map((m) => {
          const Icon = ICONS[MODULE_ICON[m.moduleKey] ?? ENTITY_BY_KEY[m.moduleKey]?.icon ?? ''] ?? Flag;
          return (
            <ListItemButton
              key={m.moduleKey}
              selected={pathname === `/${m.moduleKey}` && !search}
              onClick={() => navigate(`/${m.moduleKey}`)}
              title={m.code ? `${m.label} (${m.code})` : m.label}
              sx={{ py: 0.1, px: 0.75, borderRadius: 1, minWidth: 0 }}
            >
              <ListItemIcon sx={{ minWidth: 26, color: 'primary.main' }}>
                <Icon fontSize="small" />
              </ListItemIcon>
              <ListItemText
                primary={
                  <>
                    {m.label}
                    {moduleCounts[m.moduleKey] != null && (
                      <Box component="span" sx={{ fontSize: 11, color: 'text.secondary', ml: 0.5 }}>
                        ({moduleCounts[m.moduleKey]!.toLocaleString()})
                      </Box>
                    )}
                  </>
                }
                primaryTypographyProps={{ fontSize: 13, noWrap: true }}
                sx={{ minWidth: 0, my: 0.25 }}
              />
              {m.code && (
                <Typography variant="caption" sx={{ fontFamily: 'monospace', color: 'text.secondary', ml: 0.5 }}>
                  {m.code}
                </Typography>
              )}
            </ListItemButton>
          );
        })}
      </List>

      <Divider />

      {/* Tool pages, in a few named groups directly under the modules. A group with one page opens
          it; the others open a menu of their pages beside the rail. The group holding the current
          page is marked and says which page it is. */}
      <List dense disablePadding sx={{ px: 1, py: 0.25, flexShrink: 0 }}>
        {groups.map((g) => {
          const Icon = g.icon;
          const current = g.tools.find((t) => t.path === pathname);
          const single = g.tools.length === 1 ? g.tools[0] : null;
          return (
            <ListItemButton
              key={g.key}
              selected={Boolean(current)}
              onClick={(e) => (single ? openTool(single) : setToolMenu({ group: g.key, anchor: e.currentTarget }))}
              sx={{ py: 0.1, borderRadius: 1 }}
            >
              <ListItemIcon sx={{ minWidth: 32, color: 'primary.main' }}>
                <Icon fontSize="small" />
              </ListItemIcon>
              <ListItemText
                primary={single ? single.label : g.label}
                secondary={!single && current ? current.label : undefined}
                primaryTypographyProps={{ fontSize: 13 }}
                secondaryTypographyProps={{ fontSize: 11, noWrap: true }}
                sx={{ my: 0.25, display: 'flex', alignItems: 'baseline', gap: 1, minWidth: 0 }}
              />
              {!single && <ChevronRightIcon fontSize="small" color="action" />}
            </ListItemButton>
          );
        })}
      </List>
      <Menu
        anchorEl={toolMenu?.anchor}
        open={Boolean(toolMenu)}
        onClose={() => setToolMenu(null)}
        anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'left' }}
        slotProps={{ paper: { sx: { minWidth: 200 } } }}
      >
        {menuTools.map((t) => {
          const Icon = t.icon;
          return (
            <MenuItem
              key={t.path}
              dense
              selected={pathname === t.path}
              onClick={() => {
                setToolMenu(null);
                openTool(t);
              }}
            >
              <ListItemIcon sx={{ color: 'primary.main' }}>
                <Icon fontSize="small" />
              </ListItemIcon>
              <ListItemText primary={t.label} primaryTypographyProps={{ fontSize: 13 }} />
            </MenuItem>
          );
        })}
      </Menu>

      {/* Signed-in user + sign out */}
      {user && (
        <>
          <Divider />
          {/* One line — name (role), search, sign out — so the rail keeps its height for the lists. */}
          <Stack direction="row" alignItems="center" spacing={0.5} sx={{ pl: 1.5, pr: 1, py: 0.25 }}>
            <Typography variant="body2" noWrap sx={{ flex: 1, minWidth: 0 }}>
              <Box component="span" sx={{ fontWeight: 600 }}>
                {user.displayName || user.username}
              </Box>
              {ROLE_LABEL[user.role] && (
                <Box component="span" sx={{ color: 'text.secondary' }}>
                  {` (${ROLE_LABEL[user.role]})`}
                </Box>
              )}
            </Typography>
            <Tooltip title="Search everything ( / )">
              <IconButton size="small" color={pathname === '/search' ? 'primary' : 'default'} onClick={() => navigate('/search')}>
                <SearchIcon fontSize="small" />
              </IconButton>
            </Tooltip>
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

      {/* Collapse: a small round button sitting on the middle of the right edge. */}
      {!isMobile && (
        <Tooltip title="Collapse sidebar" placement="right">
          <IconButton
            size="small"
            onClick={() => setRail(true)}
            sx={{
              position: 'absolute',
              top: '50%',
              right: 0,
              transform: 'translate(50%, -50%)',
              zIndex: 2,
              width: 22,
              height: 22,
              bgcolor: 'background.paper',
              border: 1,
              borderColor: 'divider',
              boxShadow: 1,
              '&:hover': { bgcolor: 'background.paper', borderColor: 'primary.main' },
            }}
          >
            <ChevronLeftIcon sx={{ fontSize: 16 }} />
          </IconButton>
        </Tooltip>
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
