import { useEffect, useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Box, Button, Stack, TextField, Tooltip, Typography } from '@mui/material';
import NoteAdd from '@mui/icons-material/NoteAdd';
import ExpandLess from '@mui/icons-material/ExpandLess';
import ExpandMore from '@mui/icons-material/ExpandMore';
import { createEntity } from '../api/client';
import { useAuth } from '../hooks/useAuth';

const COLLAPSED_KEY = 'nibras.quickNoteCollapsed';

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
 * or the header row isn't being looked at, so the toast is the primary confirmation. */
export function QuickNoteBar({ onSaved }: { onSaved?: (msg: string) => void }) {
  const { canWriteContent } = useAuth();
  const qc = useQueryClient();
  const [open, setOpen] = useState(() => localStorage.getItem(COLLAPSED_KEY) !== 'true');
  const [text, setText] = useState('');
  const [savedFlash, setSavedFlash] = useState(false);
  const inputRef = useRef<HTMLTextAreaElement | null>(null);

  useEffect(() => {
    try {
      localStorage.setItem(COLLAPSED_KEY, open ? 'false' : 'true');
    } catch {
      /* ignore */
    }
  }, [open]);

  const add = useMutation({
    mutationFn: () => {
      const { summary, description } = parseQuickNote(text);
      return createEntity('notes', { summary, description });
    },
    onSuccess: (saved) => {
      setText('');
      setSavedFlash(true);
      setTimeout(() => setSavedFlash(false), 1500);
      qc.invalidateQueries({ queryKey: ['list', 'notes'] });
      inputRef.current?.focus();
      onSaved?.(`Note added${saved.displayTitle ? `: ${saved.displayTitle}` : ''}`);
    },
  });

  if (!canWriteContent) return null;

  const submit = () => {
    if (text.trim() && !add.isPending) add.mutate();
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
        <Typography variant="subtitle2" sx={{ fontWeight: 700, flex: 1 }}>
          Quick note
        </Typography>
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
            placeholder="Jot something down… first line becomes the title if it's short. Ctrl+Enter to add."
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
              <Button variant="contained" size="small" disabled={!text.trim() || add.isPending} onClick={submit}>
                Add
              </Button>
            </span>
          </Tooltip>
        </Stack>
      )}
    </Box>
  );
}
