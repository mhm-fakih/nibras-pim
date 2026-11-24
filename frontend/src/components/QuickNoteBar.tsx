import { useEffect, useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Box, Button, Link, Stack, TextField, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material';
import NoteAdd from '@mui/icons-material/NoteAdd';
import ExpandLess from '@mui/icons-material/ExpandLess';
import ExpandMore from '@mui/icons-material/ExpandMore';
import { appendToBoard, BoardAppend, createEntity, EntityRecord } from '../api/client';
import { useAuth } from '../hooks/useAuth';
import { RecordCard } from './RecordCard';

const COLLAPSED_KEY = 'nibras.quickNoteCollapsed';
const TARGET_KEY = 'nibras.quickNoteTarget';

/** Where the bar saves: a new note, or appended to today's board journal. */
type Target = 'note' | 'board';

/**
 * First non-blank line (trimmed) under 120 chars becomes the summary, with everything after it as
 * the description; a first line at or over that length means there's no natural title, so the
 * whole thing becomes the description instead.
 */
export function parseQuickNote(raw: string): { summary: string; description: string } {
  const text = raw.replace(/\r\n/g, '\n');
  const nl = text.indexOf('\n');
  const firstLine = (nl === -1 ? text : text.slice(0, nl)).trim();
  if (firstLine.length < 120) {
    const rest = nl === -1 ? '' : text.slice(nl + 1).trim();
    return { summary: firstLine, description: rest };
  }
  return { summary: '', description: text.trim() };
}

/** Always-present quick-capture bar pinned to the bottom of the detail pane — jot something down
 * as a "notes" (IndexCard) record without leaving whatever's currently open. Collapsible; the
 * open/closed state is remembered across sessions.
 *
 * `onSaved` fires a bottom-center Snackbar toast (owned by AppShell, the same one QuickEditDialog
 * uses) — the in-header "Added" flash alone was easy to miss when the bar is scrolled out of view
 * or the header row isn't being looked at, so the toast is the primary confirmation. The note just
 * added is also shown as its card under the box, so what was saved can be checked or opened. */
export function QuickNoteBar({ onSaved }: { onSaved?: (msg: string) => void }) {
  const { canWriteContent } = useAuth();
  const qc = useQueryClient();
  const [open, setOpen] = useState(() => localStorage.getItem(COLLAPSED_KEY) !== 'true');
  const [text, setText] = useState('');
  const [savedFlash, setSavedFlash] = useState(false);
  const [lastSaved, setLastSaved] = useState<EntityRecord | null>(null);
  const [lastBoard, setLastBoard] = useState<BoardAppend | null>(null);
  const [target, setTarget] = useState<Target>(() => {
    try {
      return localStorage.getItem(TARGET_KEY) === 'board' ? 'board' : 'note';
    } catch {
      return 'note';
    }
  });
  const navigate = useNavigate();
  const inputRef = useRef<HTMLTextAreaElement | null>(null);

  useEffect(() => {
    try {
      localStorage.setItem(COLLAPSED_KEY, open ? 'false' : 'true');
    } catch {
      /* ignore */
    }
  }, [open]);

  useEffect(() => {
    try {
      localStorage.setItem(TARGET_KEY, target);
    } catch {
      /* ignore */
    }
  }, [target]);

  const add = useMutation({
    mutationFn: () => {
      const { summary, description } = parseQuickNote(text);
      return createEntity('notes', { summary, description });
    },
    onSuccess: (saved) => {
      setText('');
      setLastSaved(saved);
      setSavedFlash(true);
      setTimeout(() => setSavedFlash(false), 1500);
      qc.invalidateQueries({ queryKey: ['list', 'notes'] });
      inputRef.current?.focus();
      onSaved?.(`Note added${saved.displayTitle ? `: ${saved.displayTitle}` : ''}`);
    },
  });

  // Board: the day's 'board' journal collects the jots in order, each under its time stamp.
  const board = useMutation({
    mutationFn: () => appendToBoard(text),
    onSuccess: (res) => {
      setText('');
      setLastBoard(res);
      setSavedFlash(true);
      setTimeout(() => setSavedFlash(false), 1500);
      qc.invalidateQueries({ queryKey: ['list', 'journals'] });
      qc.invalidateQueries({ queryKey: ['record', 'journals', res.id] });
      qc.invalidateQueries({ queryKey: ['calendar', 'journals'] });
      if (res.typeCreated) qc.invalidateQueries({ queryKey: ['lookups'] });
      inputRef.current?.focus();
      onSaved?.(res.created ? `Started ${res.summary}` : `Added to ${res.summary}`);
    },
  });
  const busy = add.isPending || board.isPending;
  const failed = target === 'board' ? board.error : add.error;

  if (!canWriteContent) return null;

  const submit = () => {
    if (!text.trim() || busy) return;
    if (target === 'board') board.mutate();
    else add.mutate();
  };

  return (
    <Box sx={{ flexShrink: 0, borderTop: 1, borderColor: 'divider' }}>
      <Box
        onClick={() => setOpen((o) => !o)}
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          px: 2,
          py: 1,
          cursor: 'pointer',
          bgcolor: 'action.hover',
          userSelect: 'none',
        }}
      >
        <NoteAdd fontSize="small" />
        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
          {target === 'board' ? 'Quick board' : 'Quick note'}
        </Typography>
        <ToggleButtonGroup
          size="small"
          exclusive
          value={target}
          onClick={(e) => e.stopPropagation()}
          onChange={(_, v: Target | null) => v && setTarget(v)}
          sx={{ '& .MuiToggleButton-root': { py: 0, px: 1, fontSize: '0.75rem', textTransform: 'none' } }}
        >
          <Tooltip title="Save as a new note">
            <ToggleButton value="note">Note</ToggleButton>
          </Tooltip>
          <Tooltip title="Append to today's board journal, stamped with the time">
            <ToggleButton value="board">Board</ToggleButton>
          </Tooltip>
        </ToggleButtonGroup>
        <Box sx={{ flex: 1 }} />
        {savedFlash && (
          <Typography variant="caption" color="success.main" sx={{ fontWeight: 700 }}>
            Added
          </Typography>
        )}
        {open ? <ExpandLess fontSize="small" /> : <ExpandMore fontSize="small" />}
      </Box>
      {open && (
        <Stack direction="row" spacing={1} alignItems="flex-end" sx={{ p: 1.5 }}>
          <TextField
            inputRef={inputRef}
            size="small"
            fullWidth
            multiline
            minRows={1}
            maxRows={6}
            placeholder={
              target === 'board'
                ? "Add to today's board… stamped with the time. Ctrl+Enter to add."
                : "Jot something down… first line becomes the title if it's short. Ctrl+Enter to add."
            }
            value={text}
            onChange={(e) => setText(e.target.value)}
            inputProps={{ dir: 'auto' }}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
                e.preventDefault();
                submit();
              }
            }}
          />
          <Tooltip title="Ctrl+Enter">
            <span>
              <Button variant="contained" size="small" disabled={!text.trim() || busy} onClick={submit}>
                Add
              </Button>
            </span>
          </Tooltip>
        </Stack>
      )}
      {open && failed && (
        <Typography variant="caption" color="error" sx={{ display: 'block', px: 1.5, pb: 1 }}>
          Not saved: {(failed as Error).message}
        </Typography>
      )}
      {open && target === 'board' && lastBoard && (
        <Box sx={{ px: 1.5, pb: 1.5 }}>
          <Link component="button" variant="body2" onClick={() => navigate(`/journals?select=${lastBoard.id}`)}>
            Open {lastBoard.summary}
          </Link>
        </Box>
      )}
      {open && target === 'note' && lastSaved && (
        <Box sx={{ px: 1.5, pb: 1.5 }}>
          <RecordCard
            module="notes"
            record={lastSaved}
            onClick={() => navigate(`/notes?select=${lastSaved.id}`)}
          />
        </Box>
      )}
    </Box>
  );
}
