import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Autocomplete,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
} from '@mui/material';
import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker';
import { createEntity, getLookups, getSettingsMulti, linkRecord, listEntities } from '../api/client';

/** The parameter table name behind each module's "type" lookup — matches
 * LookupDefaultsService's `lookup.default.<table>` setting keys on the backend. */
const TYPE_TABLE: Record<'journals' | 'planners', string> = {
  journals: 'journal_type',
  planners: 'planner_type',
};

interface Props {
  open: boolean;
  start: Date;
  end: Date;
  onClose: () => void;
  onCreated: () => void;
}

// CalendarPage only mounts <EventDialog> while a slot is selected (unmounting it on close), so
// plain useState can't remember the last-picked module across opens — sessionStorage can. Default
// is Journal; whatever the user picks next becomes the new default for the rest of the browser tab.
const MODULE_KEY = 'nibras.eventDialog.module';

function loadStickyModule(): 'journals' | 'planners' {
  try {
    return sessionStorage.getItem(MODULE_KEY) === 'planners' ? 'planners' : 'journals';
  } catch {
    return 'journals';
  }
}

/** Quick-add a Journal or Planner entry from a calendar slot. */
export function EventDialog({ open, start, end, onClose, onCreated }: Props) {
  const qc = useQueryClient();
  const [module, setModuleState] = useState<'journals' | 'planners'>(loadStickyModule);
  const setModule = (m: 'journals' | 'planners') => {
    setModuleState(m);
    setTypeId(null); // journal types and planner types are different lookup sets
    try {
      sessionStorage.setItem(MODULE_KEY, m);
    } catch {
      /* ignore */
    }
  };
  const [summary, setSummary] = useState('');
  const [description, setDescription] = useState('');
  const [startStr, setStartStr] = useState(toLocalInput(start));
  const [endStr, setEndStr] = useState(toLocalInput(end));
  const [goalId, setGoalId] = useState<number | null>(null);
  const [taskId, setTaskId] = useState<number | null>(null);
  const [typeId, setTypeId] = useState<number | null>(null);

  // Re-seed times whenever the dialog opens on a new slot (module is deliberately left alone —
  // it's sticky across opens, not reset per-slot).
  useEffect(() => {
    if (open) {
      setStartStr(toLocalInput(start));
      setEndStr(toLocalInput(end));
      setSummary('');
      setDescription('');
      setGoalId(null);
      setTaskId(null);
      setTypeId(null);
    }
  }, [open, start, end]);

  // The type dropdown's options switch with the module — journals and planners each have their
  // own type lookup (journal_type vs planner_type).
  const types = useQuery({
    queryKey: ['lookups', module],
    queryFn: () => getLookups(module),
    enabled: open,
  });

  // The admin-configured default type (LookupDefaultsService) is silently applied server-side
  // when typeId is left blank — pre-select it here too, so the dropdown doesn't lie by showing
  // "None" for a value that won't actually end up blank once saved.
  const typeDefaults = useQuery({
    queryKey: ['setting', 'lookup-defaults', 'journal_type', 'planner_type'],
    queryFn: () => getSettingsMulti([`lookup.default.${TYPE_TABLE.journals}`, `lookup.default.${TYPE_TABLE.planners}`]),
    enabled: open,
    staleTime: 300_000,
  });
  useEffect(() => {
    // Only fills in a still-blank field — never fights a type the user (or the module switch's
    // own reset) has already set, so `typeId` is deliberately left out of the dependency list.
    if (!open || typeId !== null || !typeDefaults.data) return;
    const idStr = typeDefaults.data[`lookup.default.${TYPE_TABLE[module]}`];
    if (idStr) setTypeId(Number(idStr));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, module, typeDefaults.data]);

  // Bookmarked goals/tasks to optionally link the event to.
  const goals = useQuery({
    queryKey: ['bookmarked', 'goals'],
    queryFn: () => listEntities('goals', { bookmarked: true, size: 100 }),
    enabled: open,
    staleTime: 60_000,
  });
  const tasks = useQuery({
    queryKey: ['bookmarked', 'tasks'],
    queryFn: () => listEntities('tasks', { bookmarked: true, size: 100 }),
    enabled: open,
    staleTime: 60_000,
  });

  const create = useMutation({
    mutationFn: async () => {
      const rec = await createEntity(module, {
        summary,
        description,
        startDate: `${startStr}:00`,
        endDate: `${endStr}:00`,
        typeId,
        // Bookmarked so it round-trips to the phone on its next sync (mobile only pulls bookmarked
        // records — see SyncService's exportJson* — same default the mobile app itself already
        // applies when it creates a calendar entry via mobilePushCalendar).
        bookmarked: true,
      });
      if (goalId) await linkRecord(module, rec.id, 'goal', goalId);
      if (taskId) await linkRecord(module, rec.id, 'task', taskId);
      return rec;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['calendar-events'] });
      qc.invalidateQueries({ queryKey: ['list', module] });
      onCreated();
      onClose();
    },
  });

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>New calendar entry</DialogTitle>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          create.mutate();
        }}
      >
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              select
              label="Module"
              size="small"
              value={module}
              onChange={(e) => setModule(e.target.value as 'journals' | 'planners')}
            >
              <MenuItem value="planners">Planner</MenuItem>
              <MenuItem value="journals">Journal</MenuItem>
            </TextField>
            <TextField
              select
              label="Type"
              size="small"
              value={typeId ?? ''}
              onChange={(e) => setTypeId(e.target.value === '' ? null : Number(e.target.value))}
            >
              <MenuItem value="">
                <em>None</em>
              </MenuItem>
              {(types.data?.types ?? []).map((t) => (
                <MenuItem key={t.id} value={t.id}>
                  {t.name}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Summary"
              size="small"
              value={summary}
              onChange={(e) => setSummary(e.target.value)}
              autoFocus
              inputProps={{ dir: 'auto' }}
            />
            <TextField
              label="Description"
              size="small"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              multiline
              minRows={3}
              inputProps={{ dir: 'auto' }}
            />
            <DateTimePicker
              label="Start"
              value={startStr ? new Date(startStr) : null}
              onChange={(d) => d && setStartStr(toLocalInput(d as Date))}
              slotProps={{ textField: { size: 'small' }, field: { clearable: false } }}
            />
            <DateTimePicker
              label="End"
              value={endStr ? new Date(endStr) : null}
              onChange={(d) => d && setEndStr(toLocalInput(d as Date))}
              slotProps={{ textField: { size: 'small' }, field: { clearable: false } }}
            />
            <Autocomplete
              size="small"
              options={(goals.data?.content ?? []).map((g) => ({ id: g.id, title: g.displayTitle || `#${g.id}` }))}
              getOptionLabel={(o) => o.title}
              isOptionEqualToValue={(a, b) => a.id === b.id}
              onChange={(_, o) => setGoalId(o ? o.id : null)}
              renderInput={(p) => <TextField {...p} label="Link to goal (bookmarked, optional)" />}
            />
            <Autocomplete
              size="small"
              options={(tasks.data?.content ?? []).map((t) => ({ id: t.id, title: t.displayTitle || `#${t.id}` }))}
              getOptionLabel={(o) => o.title}
              isOptionEqualToValue={(a, b) => a.id === b.id}
              onChange={(_, o) => setTaskId(o ? o.id : null)}
              renderInput={(p) => <TextField {...p} label="Link to task (bookmarked, optional)" />}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button type="button" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" variant="contained" disabled={create.isPending || !summary.trim()}>
            Create
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** Date -> value for <input type="datetime-local"> (local, no timezone, minute precision). */
function toLocalInput(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(
    d.getMinutes(),
  )}`;
}
