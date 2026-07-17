import { useEffect, useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Box, Button, TextField, Tooltip, Typography } from '@mui/material';
import PlayArrow from '@mui/icons-material/PlayArrow';
import Stop from '@mui/icons-material/Stop';
import { createEntity, linkRecord, reportTimerStart, reportTimerStop, EntityRecord } from '../api/client';
import { useTimerPrefs } from '../hooks/useTimerPrefs';

/** Local-time ISO (no timezone) matching the backend LocalDateTime — same shape EntityForm uses. */
function toLocalIso(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(
    d.getMinutes(),
  )}:${p(d.getSeconds())}`;
}

const storageKey = (taskId: number) => `nibras.timer.task.${taskId}`;

/**
 * Time-tracking control for a task. Play captures the start; Stop (or an auto-fire after the
 * configured max slot, default 1h) captures the end and creates a Journal record linked to the task,
 * with an optional summary. The running start is persisted in localStorage so a page reload doesn't
 * lose an in-progress session.
 */
export function TaskTimeTracker({ taskId }: { taskId: number }) {
  const qc = useQueryClient();
  const { prefs } = useTimerPrefs();

  const [startMs, setStartMs] = useState<number | null>(() => {
    const raw = localStorage.getItem(storageKey(taskId));
    const n = raw ? Number(raw) : NaN;
    return Number.isFinite(n) ? n : null;
  });
  const [summary, setSummary] = useState('');
  const summaryRef = useRef('');
  useEffect(() => {
    summaryRef.current = summary;
  }, [summary]);

  const [now, setNow] = useState(() => Date.now());

  // Re-flag an already-running session on mount (it may predate a reload or an app update —
  // the server-side probe flag is best-effort and would have missed the original start).
  useEffect(() => {
    if (startMs != null) reportTimerStart(taskId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const save = useMutation({
    mutationFn: async ({ start, end, note }: { start: Date; end: Date; note: string }) => {
      const j = (await createEntity('journals', {
        startDate: toLocalIso(start),
        endDate: toLocalIso(end),
        summary: note.trim() || null,
      })) as EntityRecord;
      await linkRecord('journals', j.id, 'task', taskId);
      return j;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['related', 'tasks', taskId] });
      qc.invalidateQueries({ queryKey: ['list', 'journals'] });
    },
  });

  const start = () => {
    const ms = Date.now();
    localStorage.setItem(storageKey(taskId), String(ms));
    setStartMs(ms);
    setNow(ms);
    reportTimerStart(taskId); // server-side flag for external probes (status bar)
  };

  const stop = () => {
    if (startMs == null) return;
    const startDate = new Date(startMs);
    const endDate = new Date();
    localStorage.removeItem(storageKey(taskId));
    setStartMs(null);
    save.mutate({ start: startDate, end: endDate, note: summaryRef.current });
    setSummary('');
    reportTimerStop();
  };

  // Tick once a second while running (drives the elapsed display).
  useEffect(() => {
    if (startMs == null) return;
    const t = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(t);
  }, [startMs]);

  // Auto-stop at the configured max slot.
  useEffect(() => {
    if (startMs == null) return;
    const delay = startMs + prefs.maxSlotMinutes * 60_000 - Date.now();
    if (delay <= 0) {
      stop();
      return;
    }
    const t = window.setTimeout(() => stop(), delay);
    return () => window.clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [startMs, prefs.maxSlotMinutes]);

  if (startMs == null) {
    return (
      <Tooltip title="Start a time-tracking session (creates a linked journal entry)">
        <span>
          <Button
            size="small"
            startIcon={<PlayArrow />}
            variant="outlined"
            onClick={start}
            disabled={save.isPending}
          >
            Track
          </Button>
        </span>
      </Tooltip>
    );
  }

  const elapsed = Math.max(0, Math.floor((now - startMs) / 1000));
  const mmss = `${String(Math.floor(elapsed / 60)).padStart(2, '0')}:${String(elapsed % 60).padStart(2, '0')}`;

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
      <TextField
        size="small"
        placeholder="Summary (optional)"
        value={summary}
        onChange={(e) => setSummary(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && stop()}
        sx={{ width: 200 }}
      />
      <Typography variant="body2" sx={{ fontVariantNumeric: 'tabular-nums', minWidth: 46, textAlign: 'center' }}>
        {mmss}
      </Typography>
      <Button
        size="small"
        color="error"
        variant="contained"
        startIcon={<Stop />}
        onClick={stop}
        disabled={save.isPending}
      >
        Stop
      </Button>
    </Box>
  );
}
