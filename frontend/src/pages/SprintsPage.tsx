import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  IconButton,
  LinearProgress,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Paper,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import Add from '@mui/icons-material/Add';
import Close from '@mui/icons-material/Close';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import LockOutlined from '@mui/icons-material/LockOutlined';
import LockOpenOutlined from '@mui/icons-material/LockOpenOutlined';
import WarningAmber from '@mui/icons-material/WarningAmber';
import {
  Sprint,
  SprintItem,
  commitToSprint,
  createSprint,
  deleteSprint,
  getSprint,
  listSprints,
  setSprintClosed,
  setSprintTaskCompleted,
  sprintCandidates,
  sprintDefaultMinutes,
  uncommitFromSprint,
} from '../api/client';
import { SideNav } from '../components/SideNav';
import { TaskTimeTracker } from '../components/TaskTimeTracker';
import { useAuth } from '../hooks/useAuth';

/** "1h 45m", or "45m". Minutes are the unit everything is computed in; hours are just for reading. */
function humanMinutes(total: number): string {
  const h = Math.floor(total / 60);
  const m = total % 60;
  return h > 0 ? `${h}h${m > 0 ? ` ${m}m` : ''}` : `${m}m`;
}

/** Local-time ISO with no zone, matching the backend's LocalDateTime. */
function toLocalIso(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:00`;
}

/** Today at HH:00, as the datetime-local input wants it. */
function todayAt(hour: number): string {
  const d = new Date();
  d.setHours(hour, 0, 0, 0);
  return toLocalIso(d).slice(0, 16);
}

function formatWhen(iso?: string): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleString(undefined, {
    weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
  });
}

/**
 * Sprints: commit bookmarked tasks to a dated block of work, then review what got done.
 *
 * Three panes, left to right: the sprints themselves, the selected sprint's committed tasks (which
 * is also the review — tick what's done), and the bookmarked tasks available to add.
 */
export function SprintsPage() {
  const { canWriteContent } = useAuth();
  const qc = useQueryClient();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);

  const { data: sprints = [], isLoading } = useQuery({ queryKey: ['sprints'], queryFn: listSprints });
  const current = selectedId ?? sprints[0]?.id ?? null;

  const { data: sprint } = useQuery({
    queryKey: ['sprint', current],
    queryFn: () => getSprint(current!),
    enabled: current != null,
  });

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['sprints'] });
    qc.invalidateQueries({ queryKey: ['sprint', current] });
    qc.invalidateQueries({ queryKey: ['sprint-candidates'] });
    // The sprint bar on the full calendar, and any task whose completion the review just flipped.
    qc.invalidateQueries({ queryKey: ['calendar-events'] });
    qc.invalidateQueries({ queryKey: ['list', 'tasks'] });
  };

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: 3 }}>
        <Stack direction="row" alignItems="baseline" spacing={2} sx={{ mb: 2 }}>
          <Typography variant="h4" sx={{ fontWeight: 700 }}>
            Sprints
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ flex: 1 }}>
            A dated block of committed work, drawn from your bookmarked tasks.
          </Typography>
          {canWriteContent && (
            <Button variant="contained" startIcon={<Add />} onClick={() => setCreating(true)}>
              New sprint
            </Button>
          )}
        </Stack>

        {isLoading && <CircularProgress size={24} />}

        <Stack direction={{ xs: 'column', lg: 'row' }} spacing={2} alignItems="flex-start">
          <SprintList sprints={sprints} selected={current} onSelect={setSelectedId} />
          {sprint ? (
            <>
              <SprintBoard sprint={sprint} onChanged={refresh} canWrite={canWriteContent} />
              {!sprint.closedOn && canWriteContent && (
                <CandidatePane sprint={sprint} onChanged={refresh} />
              )}
            </>
          ) : (
            !isLoading && (
              <Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>
                No sprint selected.
              </Typography>
            )
          )}
        </Stack>
      </Box>

      <NewSprintDialog
        open={creating}
        onClose={() => setCreating(false)}
        onCreated={(s) => {
          setCreating(false);
          setSelectedId(s.id);
          refresh();
        }}
      />
    </Box>
  );
}

function SprintList({
  sprints,
  selected,
  onSelect,
}: {
  sprints: Sprint[];
  selected: number | null;
  onSelect: (id: number) => void;
}) {
  return (
    <Paper variant="outlined" sx={{ width: { xs: '100%', lg: 260 }, flexShrink: 0 }}>
      <List dense disablePadding>
        {sprints.map((s) => {
          const done = s.completedCount;
          const total = s.items.length;
          return (
            <ListItemButton key={s.id} selected={s.id === selected} onClick={() => onSelect(s.id)}>
              <ListItemText
                primary={s.summary || `Sprint #${s.id}`}
                secondary={`${formatWhen(s.startDate)} · ${done}/${total} done`}
                primaryTypographyProps={{ fontSize: 14, fontWeight: 600 }}
                secondaryTypographyProps={{ fontSize: 12 }}
              />
              {s.closedOn && (
                <Tooltip title="Reviewed and closed">
                  <LockOutlined fontSize="small" sx={{ color: 'text.disabled' }} />
                </Tooltip>
              )}
            </ListItemButton>
          );
        })}
        {sprints.length === 0 && (
          <Box sx={{ p: 2 }}>
            <Typography variant="body2" color="text.secondary">
              No sprints yet.
            </Typography>
          </Box>
        )}
      </List>
    </Paper>
  );
}

/** The committed tasks — and, since ticking is the review, the review screen too. */
function SprintBoard({
  sprint,
  onChanged,
  canWrite,
}: {
  sprint: Sprint;
  onChanged: () => void;
  canWrite: boolean;
}) {
  const closed = Boolean(sprint.closedOn);

  const tick = useMutation({
    mutationFn: ({ taskId, done }: { taskId: number; done: boolean }) =>
      setSprintTaskCompleted(sprint.id, taskId, done),
    onSuccess: onChanged,
  });
  const drop = useMutation({
    mutationFn: (taskId: number) => uncommitFromSprint(sprint.id, taskId),
    onSuccess: onChanged,
  });
  const close = useMutation({
    mutationFn: (v: boolean) => setSprintClosed(sprint.id, v),
    onSuccess: onChanged,
  });
  const remove = useMutation({
    mutationFn: () => deleteSprint(sprint.id),
    onSuccess: onChanged,
  });

  const over = sprint.windowMinutes != null && sprint.plannedMinutes > sprint.windowMinutes;
  const progress = sprint.items.length
    ? Math.round((sprint.completedCount / sprint.items.length) * 100)
    : 0;

  return (
    <Paper variant="outlined" sx={{ flex: 1, minWidth: 0, p: 2 }}>
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
        <Typography variant="h6" sx={{ fontWeight: 700, flex: 1 }}>
          {sprint.summary || `Sprint #${sprint.id}`}
        </Typography>
        {canWrite && (
          <>
            <Button
              size="small"
              variant={closed ? 'outlined' : 'contained'}
              startIcon={closed ? <LockOpenOutlined /> : <LockOutlined />}
              disabled={close.isPending}
              onClick={() => close.mutate(!closed)}
            >
              {closed ? 'Re-open' : 'Close review'}
            </Button>
            <Tooltip title="Delete this sprint">
              <IconButton size="small" color="error" onClick={() => remove.mutate()}>
                <DeleteOutline fontSize="small" />
              </IconButton>
            </Tooltip>
          </>
        )}
      </Stack>

      <Typography variant="body2" color="text.secondary">
        {formatWhen(sprint.startDate)} → {formatWhen(sprint.endDate)}
      </Typography>

      <Stack direction="row" spacing={1} sx={{ mt: 1.5, mb: 1 }} flexWrap="wrap" useFlexGap>
        <Chip size="small" label={`Planned ${humanMinutes(sprint.plannedMinutes)}`} />
        {sprint.windowMinutes != null && (
          <Chip
            size="small"
            color={over ? 'warning' : 'default'}
            label={`Window ${humanMinutes(sprint.windowMinutes)}`}
          />
        )}
        <Chip size="small" label={`${sprint.completedCount}/${sprint.items.length} done`} />
        {closed && <Chip size="small" icon={<LockOutlined />} label="Reviewed" />}
      </Stack>

      <LinearProgress variant="determinate" value={progress} sx={{ mb: 1.5, height: 6, borderRadius: 3 }} />

      {over && (
        <Alert severity="warning" icon={<WarningAmber />} variant="outlined" sx={{ mb: 1.5 }}>
          This sprint is planned to take {humanMinutes(sprint.plannedMinutes)} but its window is only{' '}
          {humanMinutes(sprint.windowMinutes!)}.
        </Alert>
      )}

      {sprint.unestimated.length > 0 && (
        <Alert severity="info" variant="outlined" sx={{ mb: 1.5 }}>
          {sprint.unestimated.length} task{sprint.unestimated.length === 1 ? '' : 's'} counted at the
          default because {sprint.unestimated.length === 1 ? 'it has' : 'they have'} no planned
          duration set: {sprint.unestimated.join(', ')}. The total above is a guess to that extent.
        </Alert>
      )}

      <Divider sx={{ mb: 1 }} />

      <List dense disablePadding>
        {sprint.items.map((it) => (
          <ItemRow
            key={it.taskId}
            item={it}
            canWrite={canWrite}
            closed={closed}
            onTick={(done) => tick.mutate({ taskId: it.taskId, done })}
            onDrop={closed ? undefined : () => drop.mutate(it.taskId)}
          />
        ))}
        {sprint.items.length === 0 && (
          <Typography variant="body2" color="text.secondary" sx={{ p: 1 }}>
            Nothing committed yet — pick from the bookmarked tasks on the right.
          </Typography>
        )}
      </List>
    </Paper>
  );
}

function ItemRow({
  item,
  canWrite,
  closed,
  onTick,
  onDrop,
}: {
  item: SprintItem;
  canWrite: boolean;
  /** A reviewed sprint is history — tracking time against it isn't a thing you do. */
  closed: boolean;
  onTick: (done: boolean) => void;
  onDrop?: () => void;
}) {
  return (
    <Box sx={{ py: 0.25 }}>
    <Stack direction="row" alignItems="center" spacing={1}>
      <Checkbox
        size="small"
        checked={item.completed}
        disabled={!canWrite}
        onChange={(e) => onTick(e.target.checked)}
      />
      <Typography
        variant="body2"
        sx={{
          flex: 1,
          minWidth: 0,
          textDecoration: item.completed ? 'line-through' : undefined,
          color: item.completed ? 'text.disabled' : undefined,
        }}
        noWrap
        dir="auto"
      >
        {item.title}
      </Typography>
      <MissChip misses={item.previousMisses} />
      <Tooltip title={item.estimateAssumed ? 'No planned duration set — using the default' : 'Planned duration'}>
        <Chip
          size="small"
          variant="outlined"
          label={humanMinutes(item.plannedMinutes)}
          sx={{ fontStyle: item.estimateAssumed ? 'italic' : undefined, minWidth: 62 }}
        />
      </Tooltip>
      {onDrop && canWrite && (
        <Tooltip title="Remove from this sprint">
          <IconButton size="small" onClick={onDrop}>
            <Close fontSize="small" />
          </IconButton>
        </Tooltip>
      )}
    </Stack>
      {/* The task's own timer, under the row it belongs to. A sprint is the list you work FROM, so
          starting the clock shouldn't mean opening each task's page to find the button. Only one
          timer runs at a time app-wide — starting one here stops and saves whichever was running,
          including one started on a record page (see useRunningTimer). */}
      {canWrite && !closed && !item.completed && (
        <Box sx={{ pl: 5, pb: 0.5 }}>
          <TaskTimeTracker taskId={item.taskId} />
        </Box>
      )}
    </Box>
  );
}

/** How many times this task was committed and not finished. Silent at zero — most tasks are. */
function MissChip({ misses }: { misses: number }) {
  if (misses <= 0) return null;
  return (
    <Tooltip title={`Committed to ${misses} earlier sprint${misses === 1 ? '' : 's'} and not finished`}>
      <Chip
        size="small"
        color={misses >= 3 ? 'error' : 'warning'}
        variant={misses >= 3 ? 'filled' : 'outlined'}
        label={`slipped ${misses}×`}
      />
    </Tooltip>
  );
}

/** Bookmarked, unfinished tasks not already in this sprint. */
function CandidatePane({ sprint, onChanged }: { sprint: Sprint; onChanged: () => void }) {
  const [picked, setPicked] = useState<Set<number>>(new Set());

  const { data: candidates = [] } = useQuery({
    queryKey: ['sprint-candidates', sprint.id],
    queryFn: () => sprintCandidates(sprint.id),
  });

  const add = useMutation({
    mutationFn: () => commitToSprint(sprint.id, [...picked]),
    onSuccess: () => {
      setPicked(new Set());
      onChanged();
    },
  });

  const toggle = (id: number) =>
    setPicked((p) => {
      const n = new Set(p);
      if (n.has(id)) n.delete(id);
      else n.add(id);
      return n;
    });

  // Worst offenders first: a task that has slipped repeatedly is the one to decide about
  // deliberately, rather than the one to scroll past.
  const sorted = useMemo(
    () => [...candidates].sort((a, b) => b.previousMisses - a.previousMisses),
    [candidates],
  );
  const pickedMinutes = sorted
    .filter((c) => picked.has(c.taskId))
    .reduce((sum, c) => sum + c.plannedMinutes, 0);

  return (
    <Paper variant="outlined" sx={{ width: { xs: '100%', lg: 320 }, flexShrink: 0, p: 2 }}>
      <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
        Bookmarked tasks
      </Typography>
      <Typography variant="caption" color="text.secondary">
        Unfinished and not already in this sprint. Most-slipped first.
      </Typography>

      <List dense disablePadding sx={{ mt: 1, maxHeight: 460, overflow: 'auto' }}>
        {sorted.map((c) => (
          <ListItemButton key={c.taskId} onClick={() => toggle(c.taskId)} sx={{ py: 0 }}>
            <ListItemIcon sx={{ minWidth: 32 }}>
              <Checkbox size="small" edge="start" checked={picked.has(c.taskId)} tabIndex={-1} />
            </ListItemIcon>
            <ListItemText
              primary={c.title}
              primaryTypographyProps={{ fontSize: 13, noWrap: true, dir: 'auto' }}
            />
            <MissChip misses={c.previousMisses} />
            <Typography
              variant="caption"
              sx={{ ml: 1, color: 'text.secondary', fontStyle: c.estimateAssumed ? 'italic' : undefined }}
            >
              {humanMinutes(c.plannedMinutes)}
            </Typography>
          </ListItemButton>
        ))}
        {sorted.length === 0 && (
          <Typography variant="body2" color="text.secondary" sx={{ p: 1 }}>
            No bookmarked tasks left to add.
          </Typography>
        )}
      </List>

      <Button
        fullWidth
        size="small"
        variant="contained"
        sx={{ mt: 1 }}
        disabled={picked.size === 0 || add.isPending}
        onClick={() => add.mutate()}
      >
        Commit {picked.size || ''} {picked.size ? `(${humanMinutes(pickedMinutes)})` : 'tasks'}
      </Button>
    </Paper>
  );
}

function NewSprintDialog({
  open,
  onClose,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  onCreated: (s: Sprint) => void;
}) {
  const [summary, setSummary] = useState('');
  // Same-day by default, which is how these are mostly used.
  const [start, setStart] = useState(() => todayAt(9));
  const [end, setEnd] = useState(() => todayAt(17));

  const { data: fallback = 30 } = useQuery({
    queryKey: ['sprint-default-minutes'],
    queryFn: sprintDefaultMinutes,
    staleTime: 600_000,
  });

  const create = useMutation({
    mutationFn: () =>
      createSprint({
        summary: summary.trim() || undefined,
        startDate: `${start}:00`,
        endDate: `${end}:00`,
      }),
    onSuccess: onCreated,
  });

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>New sprint</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            autoFocus
            fullWidth
            size="small"
            label="Name"
            placeholder="e.g. Tuesday morning"
            value={summary}
            onChange={(e) => setSummary(e.target.value)}
            inputProps={{ dir: 'auto' }}
          />
          <Stack direction="row" spacing={2}>
            <TextField
              type="datetime-local"
              size="small"
              label="Start"
              fullWidth
              value={start}
              onChange={(e) => setStart(e.target.value)}
              InputLabelProps={{ shrink: true }}
            />
            <TextField
              type="datetime-local"
              size="small"
              label="End"
              fullWidth
              value={end}
              onChange={(e) => setEnd(e.target.value)}
              InputLabelProps={{ shrink: true }}
            />
          </Stack>
          <Typography variant="caption" color="text.secondary">
            Add tasks after creating it. A task with no planned duration is counted at {fallback}
            &nbsp;minutes, and the sprint says which ones those were.
          </Typography>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={create.isPending} onClick={() => create.mutate()}>
          Create
        </Button>
      </DialogActions>
    </Dialog>
  );
}
