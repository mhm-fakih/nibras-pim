import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Typography,
} from '@mui/material';
import SwapHoriz from '@mui/icons-material/SwapHoriz';
import CallMerge from '@mui/icons-material/CallMerge';
import { mergeRecords } from '../api/client';

export interface MergeDraft {
  module: string;
  moduleLabel: string;
  primaryId: number;
  primaryTitle: string;
  secondaryId: number;
  secondaryTitle: string;
}

/** Confirms merging two records of the same type: the secondary is absorbed into the primary,
 * which survives. Shared by the list pane (drag-and-drop merge) and the "find duplicates already
 * in the database" scan on the Import Notes page. */
export function MergeDialog({
  draft,
  onSwap,
  onCancel,
  onMerged,
}: {
  draft: MergeDraft | null;
  onSwap: () => void;
  onCancel: () => void;
  onMerged: (survivorId: number) => void;
}) {
  const qc = useQueryClient();
  const merge = useMutation({
    mutationFn: () => mergeRecords(draft!.module, draft!.primaryId, draft!.secondaryId),
    onSuccess: (merged) => {
      qc.invalidateQueries({ queryKey: ['list', draft!.module] });
      qc.invalidateQueries({ queryKey: ['record', draft!.module] });
      onMerged(merged.id);
    },
  });

  if (!draft) return null;
  return (
    <Dialog open onClose={onCancel} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <CallMerge fontSize="small" /> Merge {draft.moduleLabel.toLowerCase()}
      </DialogTitle>
      <DialogContent>
        <DialogContentText component="div">
          <Typography variant="body2" sx={{ mb: 1 }}>
            <strong>
              #{draft.secondaryId} {draft.secondaryTitle || '(untitled)'}
            </strong>{' '}
            will be merged into{' '}
            <strong>
              #{draft.primaryId} {draft.primaryTitle || '(untitled)'}
            </strong>
            , which survives.
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Text fields (description, notes, …) are concatenated; other blank fields on the survivor are
            filled in from the merged record, tags are combined, and any records linked to the merged
            record (e.g. a goal's tasks) follow the survivor. Files are moved under a
            “{draft.secondaryId}-files” folder. The merged record is then moved to Trash.
          </Typography>
          {merge.isError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {(merge.error as { response?: { data?: { detail?: string } } })?.response?.data?.detail ??
                'Merge failed.'}
            </Alert>
          )}
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button startIcon={<SwapHoriz />} onClick={onSwap} disabled={merge.isPending}>
          Swap
        </Button>
        <Box sx={{ flex: 1 }} />
        <Button onClick={onCancel} disabled={merge.isPending}>
          Cancel
        </Button>
        <Button variant="contained" onClick={() => merge.mutate()} disabled={merge.isPending}>
          Merge
        </Button>
      </DialogActions>
    </Dialog>
  );
}
