import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { diffWords } from 'diff';
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  Typography,
} from '@mui/material';
import EditNote from '@mui/icons-material/EditNote';
import SyncAlt from '@mui/icons-material/SyncAlt';
import {
  applyFulltext,
  exportFulltext,
  FieldDiff,
  getFulltextChanges,
} from '../api/client';

const LABELS: Record<string, string> = {
  summary: 'Title / summary',
  title: 'Title',
  description: 'Description',
  fullText: 'Full text',
  highlights: 'Highlights',
  notes: 'Notes',
};

/** Export a record's text fields to one file for editing, then review + apply changes. */
export function FulltextPanel({ module, id }: { module: string; id: number }) {
  const qc = useQueryClient();
  const [path, setPath] = useState<string | null>(null);
  const [changes, setChanges] = useState<FieldDiff[] | null>(null);

  const doExport = useMutation({
    mutationFn: () => exportFulltext(module, id),
    onSuccess: (r) => setPath(r.path),
  });

  const doReview = useMutation({
    mutationFn: () => getFulltextChanges(module, id),
    onSuccess: (c) => setChanges(c),
  });

  const doApply = useMutation({
    mutationFn: () => applyFulltext(module, id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['record', module, id] });
      qc.invalidateQueries({ queryKey: ['history'] });
      qc.invalidateQueries({ queryKey: ['list', module] });
      setChanges(null);
    },
  });

  return (
    <Box sx={{ mt: 2 }}>
      <Typography variant="overline" color="text.secondary">
        Edit full text in your editor
      </Typography>
      <Stack direction="row" spacing={1} sx={{ mt: 0.5 }} flexWrap="wrap" useFlexGap>
        <Button size="small" startIcon={<EditNote />} onClick={() => doExport.mutate()} disabled={doExport.isPending}>
          Export to file
        </Button>
        <Button
          size="small"
          startIcon={<SyncAlt />}
          variant="outlined"
          onClick={() => doReview.mutate()}
          disabled={doReview.isPending}
        >
          Review &amp; sync
        </Button>
      </Stack>

      {path && (
        <Alert severity="info" sx={{ mt: 1, fontSize: 13 }}>
          Edit this file, then “Review &amp; sync”:<br />
          <code>{path}</code>
        </Alert>
      )}
      {doReview.isError && (
        <Alert severity="warning" sx={{ mt: 1, fontSize: 13 }}>
          No exported file yet — click “Export to file” first.
        </Alert>
      )}

      <Dialog open={changes !== null} onClose={() => setChanges(null)} maxWidth="md" fullWidth>
        <DialogTitle>Review changes from file</DialogTitle>
        <DialogContent dividers>
          {changes && changes.length === 0 && (
            <Typography color="text.secondary">No changes — the file matches the record.</Typography>
          )}
          <Stack spacing={2}>
            {changes?.map((c) => (
              <Box key={c.field}>
                <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
                  {LABELS[c.field] ?? c.field}
                </Typography>
                <DiffView oldValue={c.oldValue ?? ''} newValue={c.newValue ?? ''} />
              </Box>
            ))}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setChanges(null)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={!changes || changes.length === 0 || doApply.isPending}
            onClick={() => doApply.mutate()}
          >
            Apply {changes && changes.length > 0 ? `(${changes.length})` : ''}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

/** Word-level diff: additions green, deletions red strikethrough. */
function DiffView({ oldValue, newValue }: { oldValue: string; newValue: string }) {
  const parts = diffWords(oldValue, newValue);
  return (
    <Box
      dir="auto"
      sx={{
        whiteSpace: 'pre-wrap',
        fontSize: 13,
        border: 1,
        borderColor: 'divider',
        borderRadius: 1,
        p: 1,
        maxHeight: 240,
        overflow: 'auto',
      }}
    >
      {parts.map((p, i) => (
        <Box
          key={i}
          component="span"
          sx={
            p.added
              ? { bgcolor: '#d6f5d6', color: '#0a6b0a' }
              : p.removed
                ? { bgcolor: '#ffd6d6', color: '#9b1c1c', textDecoration: 'line-through' }
                : undefined
          }
        >
          {p.value}
        </Box>
      ))}
    </Box>
  );
}
