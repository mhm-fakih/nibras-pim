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
import { createEntity, linkRecord, listEntities } from '../api/client';

interface Props {
  open: boolean;
  start: Date;
  end: Date;
  onClose: () => void;
  onCreated: () => void;
}

/** Quick-add a Journal or Planner entry from a calendar slot. */
export function EventDialog({ open, start, end, onClose, onCreated }: Props) {
  const qc = useQueryClient();
  const [module, setModule] = useState<'journals' | 'planners'>('planners');
  const [summary, setSummary] = useState('');
  const [startStr, setStartStr] = useState(toLocalInput(start));
  const [endStr, setEndStr] = useState(toLocalInput(end));
  const [goalId, setGoalId] = useState<number | null>(null);
  const [taskId, setTaskId] = useState<number | null>(null);

  // Re-seed times whenever the dialog opens on a new slot.
  useEffect(() => {
    if (open) {
      setStartStr(toLocalInput(start));
      setEndStr(toLocalInput(end));
      setSummary('');
      setGoalId(null);
      setTaskId(null);
    }
  }, [open, start, end]);

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
        startDate: `${startStr}:00`,
        endDate: `${endStr}:00`,
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
            label="Summary"
            size="small"
            value={summary}
            onChange={(e) => setSummary(e.target.value)}
            autoFocus
            inputProps={{ dir: 'auto' }}
          />
          <TextField
            label="Start"
            type="datetime-local"
            size="small"
            value={startStr}
            onChange={(e) => setStartStr(e.target.value)}
            InputLabelProps={{ shrink: true }}
          />
          <TextField
            label="End"
            type="datetime-local"
            size="small"
            value={endStr}
            onChange={(e) => setEndStr(e.target.value)}
            InputLabelProps={{ shrink: true }}
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
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={create.isPending || !summary.trim()}
          onClick={() => create.mutate()}
        >
          Create
        </Button>
      </DialogActions>
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
