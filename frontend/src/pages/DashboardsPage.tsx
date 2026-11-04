import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Box,
  Button,
  Checkbox,
  Chip,
  FormControlLabel,
  IconButton,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import Add from '@mui/icons-material/Add';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import Edit from '@mui/icons-material/Edit';
import VisibilityOutlined from '@mui/icons-material/VisibilityOutlined';
import ArrowUpward from '@mui/icons-material/ArrowUpward';
import ArrowDownward from '@mui/icons-material/ArrowDownward';
import {
  BASE,
  CustomDashboard,
  CustomDashboardRegion,
  createCustomDashboard,
  deleteCustomDashboard,
  getCustomDashboard,
  getLookups,
  listCustomDashboards,
  updateCustomDashboard,
} from '../api/client';
import { SideNav } from '../components/SideNav';
import {
  DASHBOARD_COMPONENT_TYPES,
  DashboardComponent,
  DEFAULT_TEXT_SCALE,
  TEXT_SCALES,
  planTypesOf,
  regionZoom,
} from '../components/DashboardComponents';

const BLANK: CustomDashboard = { name: '', columnsCount: 2, refreshMinutes: 5, regions: [] };

export function DashboardsPage() {
  const qc = useQueryClient();
  const { data: dashboards } = useQuery({ queryKey: ['custom-dashboards'], queryFn: listCustomDashboards });
  const [editingId, setEditingId] = useState<number | 'new' | null>(null);

  const remove = useMutation({
    mutationFn: (id: number) => deleteCustomDashboard(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['custom-dashboards'] }),
  });

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: 4, maxWidth: 900 }}>
        <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }}>
          Dashboards
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Build a dashboard from regions — quote of the day, a calendar of journal and planner
          entries, today's checklist, open tasks, local task notes, the goals habit heatmap, a kanban board, or a
          file's content — arranged in a grid; a region can span several columns and rows. Open a dashboard's view link on a TV or spare monitor
          for a chrome-free display; drag the line between two columns there to resize them.
        </Typography>

        {editingId == null && (
          <>
            <Button variant="contained" startIcon={<Add />} onClick={() => setEditingId('new')} sx={{ mb: 2 }}>
              New dashboard
            </Button>
            <Paper variant="outlined">
              <List disablePadding>
                {(dashboards ?? []).map((d) => (
                  <DashboardListRow
                    key={d.id}
                    dashboard={d}
                    onEdit={() => setEditingId(d.id!)}
                    onDelete={() => window.confirm(`Delete "${d.name}"?`) && remove.mutate(d.id!)}
                  />
                ))}
                {dashboards && dashboards.length === 0 && (
                  <ListItem>
                    <ListItemText secondary="No dashboards yet." />
                  </ListItem>
                )}
              </List>
            </Paper>
          </>
        )}

        {editingId != null && (
          <DashboardEditor
            id={editingId === 'new' ? null : editingId}
            onDone={() => {
              setEditingId(null);
              qc.invalidateQueries({ queryKey: ['custom-dashboards'] });
            }}
          />
        )}
      </Box>
    </Box>
  );
}

function DashboardListRow({
  dashboard,
  onEdit,
  onDelete,
}: {
  dashboard: CustomDashboard;
  onEdit: () => void;
  onDelete: () => void;
}) {
  return (
    <ListItem
      divider
      secondaryAction={
        <Stack direction="row" spacing={0.5}>
          <Tooltip title="View (chrome-free, opens in a new tab)">
            <IconButton
              component="a"
              href={`${BASE}dashboards/${dashboard.id}/view`}
              target="_blank"
              rel="noopener noreferrer"
            >
              <VisibilityOutlined fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title="Edit">
            <IconButton onClick={onEdit}>
              <Edit fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title="Delete">
            <IconButton onClick={onDelete}>
              <DeleteOutline fontSize="small" />
            </IconButton>
          </Tooltip>
        </Stack>
      }
    >
      <ListItemText
        primary={dashboard.name}
        secondary={`${dashboard.regions.length} region(s) · ${dashboard.columnsCount} column(s)`}
      />
    </ListItem>
  );
}

function DashboardEditor({ id, onDone }: { id: number | null; onDone: () => void }) {
  const { data } = useQuery({
    queryKey: ['custom-dashboard', id],
    queryFn: () => getCustomDashboard(id!),
    enabled: id != null,
  });
  const [form, setForm] = useState<CustomDashboard>(BLANK);

  useEffect(() => {
    if (data) setForm(data);
  }, [data]);

  const save = useMutation({
    mutationFn: () =>
      id == null ? createCustomDashboard(form) : updateCustomDashboard(id, form),
    onSuccess: onDone,
  });

  const addRegion = () => {
    setForm((f) => ({
      ...f,
      regions: [
        ...f.regions,
        {
          orderIndex: f.regions.length,
          componentType: DASHBOARD_COMPONENT_TYPES[0].type,
          config: '{}',
          colSpan: 1,
        },
      ],
    }));
  };
  const updateRegion = (index: number, patch: Partial<CustomDashboardRegion>) => {
    setForm((f) => ({
      ...f,
      regions: f.regions.map((r, i) => (i === index ? { ...r, ...patch } : r)),
    }));
  };
  const removeRegion = (index: number) => {
    setForm((f) => ({ ...f, regions: f.regions.filter((_, i) => i !== index) }));
  };
  const moveRegion = (index: number, dir: -1 | 1) => {
    setForm((f) => {
      const next = [...f.regions];
      const target = index + dir;
      if (target < 0 || target >= next.length) return f;
      [next[index], next[target]] = [next[target], next[index]];
      return { ...f, regions: next.map((r, i) => ({ ...r, orderIndex: i })) };
    });
  };

  return (
    <Box>
      <Stack direction="row" spacing={2} sx={{ mb: 3 }}>
        <TextField
          label="Dashboard name"
          size="small"
          value={form.name}
          onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
          sx={{ minWidth: 260 }}
        />
        <TextField
          label="Columns"
          type="number"
          size="small"
          value={form.columnsCount}
          onChange={(e) => setForm((f) => ({ ...f, columnsCount: Number(e.target.value) || 1 }))}
          inputProps={{ min: 1, max: 6 }}
          sx={{ width: 100 }}
        />
        <TextField
          label="Column widths"
          size="small"
          placeholder="equal"
          value={form.columnWidths ?? ''}
          onChange={(e) => setForm((f) => ({ ...f, columnWidths: e.target.value }))}
          helperText="e.g. 1,2,1 — or drag in the view"
          sx={{ width: 190 }}
        />
        <TextField
          label="Auto-refresh (min)"
          type="number"
          size="small"
          value={form.refreshMinutes ?? 5}
          onChange={(e) => setForm((f) => ({ ...f, refreshMinutes: Number(e.target.value) || 0 }))}
          helperText="0 disables"
          inputProps={{ min: 0, max: 1440 }}
          sx={{ width: 140 }}
        />
      </Stack>

      <Stack spacing={2}>
        {form.regions.map((r, i) => (
          <RegionEditor
            key={i}
            region={r}
            onChange={(patch) => updateRegion(i, patch)}
            onRemove={() => removeRegion(i)}
            onMoveUp={i > 0 ? () => moveRegion(i, -1) : undefined}
            onMoveDown={i < form.regions.length - 1 ? () => moveRegion(i, 1) : undefined}
          />
        ))}
      </Stack>

      <Button startIcon={<Add />} onClick={addRegion} sx={{ mt: 2 }}>
        Add region
      </Button>

      <Stack direction="row" spacing={1} sx={{ mt: 4 }}>
        <Button
          variant="contained"
          disabled={!form.name.trim() || save.isPending}
          onClick={() => save.mutate()}
        >
          Save
        </Button>
        <Button onClick={onDone}>Cancel</Button>
      </Stack>
      {save.isError && (
        <Typography color="error" variant="body2" sx={{ mt: 1 }}>
          Save failed.
        </Typography>
      )}
    </Box>
  );
}

function RegionEditor({
  region,
  onChange,
  onRemove,
  onMoveUp,
  onMoveDown,
}: {
  region: CustomDashboardRegion;
  onChange: (patch: Partial<CustomDashboardRegion>) => void;
  onRemove: () => void;
  onMoveUp?: () => void;
  onMoveDown?: () => void;
}) {
  let cfg: Record<string, unknown> = {};
  try {
    cfg = region.config ? JSON.parse(region.config) : {};
  } catch {
    cfg = {};
  }
  const setCfg = (patch: Record<string, unknown>) => onChange({ config: JSON.stringify({ ...cfg, ...patch }) });

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1.5 }}>
        <TextField
          select
          size="small"
          label="Component"
          value={region.componentType}
          onChange={(e) =>
            onChange({
              componentType: e.target.value,
              // A new component starts with its own settings, but keeps the text size.
              config: cfg.textScale ? JSON.stringify({ textScale: cfg.textScale }) : '{}',
            })
          }
          sx={{ minWidth: 220 }}
        >
          {DASHBOARD_COMPONENT_TYPES.map((c) => (
            <MenuItem key={c.type} value={c.type}>
              {c.label}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          size="small"
          type="number"
          label="Columns"
          value={region.colSpan ?? 1}
          onChange={(e) => onChange({ colSpan: Math.max(1, Number(e.target.value) || 1) })}
          helperText="Columns it spans"
          inputProps={{ min: 1, max: 6 }}
          sx={{ width: 110 }}
        />
        <TextField
          size="small"
          type="number"
          label="Rows"
          value={region.rowSpan ?? 1}
          onChange={(e) => onChange({ rowSpan: Math.max(1, Math.min(6, Number(e.target.value) || 1)) })}
          helperText="Rows it spans"
          inputProps={{ min: 1, max: 6 }}
          sx={{ width: 100 }}
        />
        <TextField
          select
          size="small"
          label="Text size"
          value={(cfg.textScale as number) ?? DEFAULT_TEXT_SCALE}
          onChange={(e) => setCfg({ textScale: Number(e.target.value) })}
          helperText="Of the normal size"
          sx={{ width: 110 }}
        >
          {TEXT_SCALES.map((t) => (
            <MenuItem key={t} value={t}>
              {t} %
            </MenuItem>
          ))}
        </TextField>
        <Box sx={{ flex: 1 }} />
        <Tooltip title="Move up">
          <span>
            <IconButton size="small" disabled={!onMoveUp} onClick={onMoveUp}>
              <ArrowUpward fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
        <Tooltip title="Move down">
          <span>
            <IconButton size="small" disabled={!onMoveDown} onClick={onMoveDown}>
              <ArrowDownward fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
        <Tooltip title="Remove region">
          <IconButton size="small" onClick={onRemove}>
            <DeleteOutline fontSize="small" />
          </IconButton>
        </Tooltip>
      </Stack>

      {region.componentType === 'quote' && (
        <TextField
          size="small"
          label="Note type code"
          placeholder="e.g. quote"
          value={(cfg.typeCode as string) ?? ''}
          onChange={(e) => setCfg({ typeCode: e.target.value })}
          helperText="Shows the most recent bookmarked note of this type."
          fullWidth
        />
      )}
      {region.componentType === 'calendar' && (
        <Stack spacing={2}>
          <Typography variant="caption" color="text.secondary">
            Shows this week's journal and planner entries, repeating ones included (marked with ↻).
          </Typography>
          <TextField
            size="small"
            label="Event type name"
            placeholder="e.g. family"
            value={(cfg.eventType as string) ?? ''}
            onChange={(e) => setCfg({ eventType: e.target.value })}
            helperText="Matches this type name in both Journal and Planner entries — define it under
              Settings → Parameter colours for both, each can have its own colour. Leave blank for
              every event."
            fullWidth
          />
          <Stack direction="row" spacing={2}>
            <TextField
              size="small"
              type="number"
              label="Start hour"
              value={(cfg.startHour as number) ?? 7}
              onChange={(e) => setCfg({ startHour: Number(e.target.value) })}
              inputProps={{ min: 0, max: 23 }}
              sx={{ width: 140 }}
            />
            <TextField
              size="small"
              type="number"
              label="End hour"
              value={(cfg.endHour as number) ?? 22}
              onChange={(e) => setCfg({ endHour: Number(e.target.value) })}
              inputProps={{ min: 1, max: 24 }}
              sx={{ width: 140 }}
            />
          </Stack>
        </Stack>
      )}
      {region.componentType === 'file' && (
        <Stack direction="row" spacing={2}>
          <TextField
            size="small"
            type="number"
            label="Repo"
            value={(cfg.repo as number) ?? 1}
            onChange={(e) => setCfg({ repo: Number(e.target.value) || 1 })}
            inputProps={{ min: 1, max: 3 }}
            sx={{ width: 100 }}
          />
          <TextField
            size="small"
            label="Relative path"
            placeholder="e.g. dashboard-top.md"
            value={(cfg.path as string) ?? ''}
            onChange={(e) => setCfg({ path: e.target.value })}
            fullWidth
          />
        </Stack>
      )}
      {region.componentType === 'tasks' && (
        <>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
          Open tasks: not completed, and with a status ticked “active” on the Parameters page
          (such as Not started or In progress) or no status at all. Those due first, soonest first, then
          the rest by priority. Each has a checkbox to mark it completed (needs a signed-in screen).
        </Typography>
        <Stack direction="row" spacing={2} alignItems="flex-start" flexWrap="wrap" useFlexGap>
          <TextField
            size="small"
            type="number"
            label="Show at most"
            value={(cfg.limit as number) ?? 30}
            onChange={(e) => setCfg({ limit: Math.max(1, Number(e.target.value) || 30) })}
            inputProps={{ min: 1, max: 200 }}
            sx={{ width: 130 }}
          />
          <TextField
            size="small"
            type="number"
            label="Overdue up to (days)"
            value={(cfg.overdueDays as number) ?? 30}
            onChange={(e) => setCfg({ overdueDays: Math.max(0, Number(e.target.value) || 0) })}
            helperText="Older overdue tasks are left out; 0 shows all"
            inputProps={{ min: 0 }}
            sx={{ width: 220 }}
          />
          <FormControlLabel
            control={
              <Checkbox
                checked={cfg.bookmarkedOnly === true}
                onChange={(e) => setCfg({ bookmarkedOnly: e.target.checked })}
              />
            }
            label="Bookmarked tasks only"
          />
        </Stack>
        </>
      )}
      {region.componentType === 'plans' && <PlansOptions cfg={cfg} setCfg={setCfg} />}
      {region.componentType === 'checklist' && (
        <>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
            The phone's checklist with a tick box per day for the last few days, today last — to
            compare ticks across days at a glance. Up to 7 days.
          </Typography>
          <TextField
            size="small"
            type="number"
            label="Days shown"
            value={(cfg.days as number) ?? 3}
            onChange={(e) => setCfg({ days: Math.min(7, Math.max(1, Number(e.target.value) || 3)) })}
            inputProps={{ min: 1, max: 7 }}
            sx={{ width: 130 }}
          />
        </>
      )}
      {region.componentType === 'localTasks' && (
        <>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
            Task notes in the local notes folder: files whose name has type T (…~T~p3~X.md), at
            the chosen priority or higher. Highest priority first, then newest.
          </Typography>
          <Stack direction="row" spacing={2}>
            <TextField
              size="small"
              type="number"
              label="Lowest priority"
              value={(cfg.minPriority as number) ?? 3}
              onChange={(e) => setCfg({ minPriority: Math.max(1, Number(e.target.value) || 3) })}
              inputProps={{ min: 1, max: 9 }}
              sx={{ width: 140 }}
            />
            <TextField
              size="small"
              type="number"
              label="Show at most"
              value={(cfg.limit as number) ?? 30}
              onChange={(e) => setCfg({ limit: Math.max(1, Number(e.target.value) || 30) })}
              inputProps={{ min: 1, max: 200 }}
              sx={{ width: 130 }}
            />
          </Stack>
        </>
      )}
      {(region.componentType === 'goalsHeatmap' ||
        region.componentType === 'kanban' ||
        region.componentType === 'checklist') && (
        <Typography variant="caption" color="text.secondary">
          No settings for this component.
        </Typography>
      )}

      <Box sx={{ mt: 2, p: 1.5, bgcolor: 'action.hover', borderRadius: 1, maxHeight: 260, overflow: 'auto' }}>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
          Preview
        </Typography>
        <Box sx={{ zoom: regionZoom(region.config) }}>
          <DashboardComponent componentType={region.componentType} config={region.config} />
        </Box>
      </Box>
    </Paper>
  );
}

/** Options for the 'plans' component: which planner types, how many, and a heading. */
function PlansOptions({
  cfg,
  setCfg,
}: {
  cfg: Record<string, unknown>;
  setCfg: (patch: Record<string, unknown>) => void;
}) {
  const { data: lookups } = useQuery({ queryKey: ['lookups', 'planners'], queryFn: () => getLookups('planners') });
  const chosen = planTypesOf(JSON.stringify(cfg));
  const toggle = (code: string) => {
    const c = code.toLowerCase();
    const next = chosen.includes(c) ? chosen.filter((x) => x !== c) : [...chosen, c];
    setCfg({ types: next.join(',') });
  };
  return (
    <>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
        Bookmarked planner entries of the types picked here — this month&apos;s focus, standing policies — as a list,
        newest first. Bookmark an entry to show it; unbookmark it to take it off. No type picked: every type.
      </Typography>
      <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap sx={{ mb: 1.5 }}>
        {(lookups?.types ?? []).map((t) => (
          <Chip
            key={t.id}
            label={t.name}
            size="small"
            color={chosen.includes(String(t.code ?? t.name).toLowerCase()) ? 'primary' : 'default'}
            variant={chosen.includes(String(t.code ?? t.name).toLowerCase()) ? 'filled' : 'outlined'}
            onClick={() => toggle(String(t.code ?? t.name))}
          />
        ))}
      </Stack>
      <Stack direction="row" spacing={2}>
        <TextField
          size="small"
          label="Heading"
          placeholder="Plans"
          value={(cfg.title as string) ?? ''}
          onChange={(e) => setCfg({ title: e.target.value })}
          sx={{ width: 220 }}
        />
        <TextField
          size="small"
          type="number"
          label="Show at most"
          value={(cfg.limit as number) ?? 20}
          onChange={(e) => setCfg({ limit: Math.max(1, Number(e.target.value) || 20) })}
          inputProps={{ min: 1, max: 100 }}
          sx={{ width: 130 }}
        />
      </Stack>
    </>
  );
}
