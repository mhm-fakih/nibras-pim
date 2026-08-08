import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Box,
  Button,
  Chip,
  IconButton,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import RestoreFromTrash from '@mui/icons-material/RestoreFromTrash';
import CallMerge from '@mui/icons-material/CallMerge';
import { listTrash, restoreFromTrash, TrashEntry } from '../api/client';
import { SideNav } from '../components/SideNav';
import { ENTITIES } from '../config/entities';

const LABEL_BY_MODULE: Record<string, string> = Object.fromEntries(
  ENTITIES.map((e) => [e.key, e.label]),
);

export function TrashPage() {
  const qc = useQueryClient();
  const [restoringId, setRestoringId] = useState<string | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['trash'],
    queryFn: listTrash,
  });

  const restore = useMutation({
    mutationFn: (entry: TrashEntry) => restoreFromTrash(entry.module, entry.id),
    onMutate: (entry) => setRestoringId(`${entry.module}:${entry.id}`),
    onSettled: () => setRestoringId(null),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['trash'] });
      qc.invalidateQueries({ queryKey: ['list'] });
    },
  });

  const items = data ?? [];

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: 4 }}>
        <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }}>
          Trash
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Soft-deleted records across every module. Restoring puts a record back where it was.
        </Typography>

        <Paper variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Module</TableCell>
                <TableCell>Title</TableCell>
                <TableCell>Deleted</TableCell>
                <TableCell>Reason</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {items.map((e) => {
                const key = `${e.module}:${e.id}`;
                return (
                  <TableRow key={key} hover>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{LABEL_BY_MODULE[e.module] ?? e.module}</TableCell>
                    <TableCell>
                      #{e.id} {e.title}
                    </TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{new Date(e.deletedOn).toLocaleString()}</TableCell>
                    <TableCell>
                      {e.mergedIntoId ? (
                        <Tooltip title={`Merged into #${e.mergedIntoId} ${e.mergedIntoTitle ?? ''}`}>
                          <Chip
                            size="small"
                            icon={<CallMerge fontSize="small" />}
                            label={`Merged → #${e.mergedIntoId}`}
                            color="info"
                            variant="outlined"
                          />
                        </Tooltip>
                      ) : (
                        <Chip size="small" label="Deleted" variant="outlined" />
                      )}
                    </TableCell>
                    <TableCell align="right">
                      <Tooltip title="Restore">
                        <span>
                          <IconButton
                            size="small"
                            color="primary"
                            disabled={restoringId === key}
                            onClick={() => restore.mutate(e)}
                          >
                            <RestoreFromTrash fontSize="small" />
                          </IconButton>
                        </span>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                );
              })}
              {!isLoading && items.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} sx={{ color: 'text.secondary', py: 3 }}>
                    Trash is empty.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </Paper>

        {restore.isError && (
          <Typography color="error" variant="body2" sx={{ mt: 2 }}>
            Restore failed. Please try again.
          </Typography>
        )}

        {items.length > 0 && (
          <Box sx={{ mt: 2 }}>
            <Button size="small" onClick={() => qc.invalidateQueries({ queryKey: ['trash'] })}>
              Refresh
            </Button>
          </Box>
        )}
      </Box>
    </Box>
  );
}
