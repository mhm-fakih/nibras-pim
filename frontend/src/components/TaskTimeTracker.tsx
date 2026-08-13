import { useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Box, Button, TextField, Tooltip, Typography } from '@mui/material';
import PlayArrow from '@mui/icons-material/PlayArrow';
import Stop from '@mui/icons-material/Stop';
import { startTimer, stopTimer, useRunningTimer } from '../hooks/useRunningTimer';

/**
 * Time-tracking control for a task. Play captures the start; Stop (or an auto-fire after the
 * configured max slot, default 1h) captures the end and creates a Journal record linked to the
 * task, with an optional summary.
 *
 * The running session lives in [useRunningTimer], not here, so **only one timer can run at a
 * time**: starting this one stops and saves whichever was running before. That matters as soon as
 * the control appears in more than one place — a task's record page and its row in a sprint are two
 * views of the same task, and two different tasks in one sprint are one tap apart.
 */
export function TaskTimeTracker({ taskId }: { taskId: number }) {
  const qc = useQueryClient();

  const running = useRunningTimer();
  const startMs = running?.taskId === taskId ? running.startMs : null;

  const [summary, setSummary] = useState('');
  const summaryRef = useRef('');
  useEffect(() => {
    summaryRef.current = summary;
  }, [summary]);

  const [now, setNow] = useState(() => Date.now());
  const [busy, setBusy] = useState(false);

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['related', 'tasks', taskId] });
    qc.invalidateQueries({ queryKey: ['list', 'journals'] });
  };

  const start = async () => {
    setBusy(true);
    try {
      await startTimer(taskId);   // stops + saves any other running timer first
      setNow(Date.now());
    } finally {
      setBusy(false);
      refresh();
    }
  };

  const stop = async () => {
    setBusy(true);
    try {
      await stopTimer(summaryRef.current);
      setSummary('');
    } finally {
      setBusy(false);
      refresh();
    }
  };

  // Tick once a second while running (drives the elapsed display).
  useEffect(() => {
    if (startMs == null) return;
    const t = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(t);
  }, [startMs]);

  // The auto-stop is NOT here. It lives in useRunningTimer, armed once per session, so it survives
  // navigating away from whichever row started it — a component-scoped timeout died with the
  // component and left the session running. Keeping a copy here would also mean two cut-offs
  // racing, on two different values: this one read a per-browser preference, the store reads the
  // server setting.

  if (startMs == null) {
    return (
      <Tooltip title="Start a time-tracking session (creates a linked journal entry)">
        <span>
          <Button
            size="small"
            startIcon={<PlayArrow />}
            variant="outlined"
            onClick={start}
            disabled={busy}
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
        disabled={busy}
      >
        Stop
      </Button>
    </Box>
  );
}
