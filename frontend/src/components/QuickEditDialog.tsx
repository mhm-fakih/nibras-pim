import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Autocomplete, Box, Dialog, TextField, Typography } from '@mui/material';
import { batchEdit, getLookups, listContexts, listCourses, Lookup,
  errorDetail,
} from '../api/client';

export type QuickKind = 'priority' | 'type' | 'status' | 'context' | 'department' | 'course';

const SYMBOL: Record<QuickKind, string> = {
  priority: 'p',
  type: '#',
  status: '?',
  context: '@',
  department: 'd',
  course: 'c',
};
const LABEL: Record<QuickKind, string> = {
  priority: 'Priority',
  type: 'Type',
  status: 'Status',
  context: 'Context',
  department: 'Department',
  course: 'Course',
};

interface Props {
  kind: QuickKind;
  module: string;
  id: number;
  onClose: () => void;
  onApplied: (msg: string) => void;
}

/**
 * Keyboard quick-edit of one field on the selected record. Builds a single command token
 * (`p3`, `#code`, `?code`, `@code`, `dCODE`, `cCODE`) and applies it through the shared
 * batch-edit grammar — so this stays in lockstep with batch edit / entry / search.
 */
export function QuickEditDialog({ kind, module, id, onClose, onApplied }: Props) {
  const qc = useQueryClient();
  const [priority, setPriority] = useState('');

  const lookupsQ = useQuery({
    queryKey: ['lookups', module],
    queryFn: () => getLookups(module),
    enabled: kind === 'type' || kind === 'status' || kind === 'department',
  });
  const contextsQ = useQuery({
    queryKey: ['contexts'],
    queryFn: listContexts,
    enabled: kind === 'context',
    staleTime: 300_000,
  });
  const coursesQ = useQuery({
    queryKey: ['courses-all'],
    queryFn: listCourses,
    enabled: kind === 'course',
    staleTime: 300_000,
  });

  const options: Lookup[] = useMemo(() => {
    if (kind === 'type') return lookupsQ.data?.types ?? [];
    if (kind === 'status') return lookupsQ.data?.statuses ?? [];
    if (kind === 'department') return lookupsQ.data?.departments ?? [];
    if (kind === 'context') return contextsQ.data ?? [];
    if (kind === 'course')
      return (coursesQ.data ?? [])
        .filter((c) => c.code)
        .map((c) => ({ id: c.id, name: c.displayTitle, code: c.code }));
    return [];
  }, [kind, lookupsQ.data, contextsQ.data, coursesQ.data]);

  const mut = useMutation({
    mutationFn: (command: string) => batchEdit(module, [id], command),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['record', module, id] });
      qc.invalidateQueries({ queryKey: ['list', module] });
    },
  });

  const run = async (command: string, message: string) => {
    try {
      await mut.mutateAsync(command);
      onApplied(message);
    } catch (e) {
      onApplied(errorDetail(e, 'Update failed.'));
    }
    onClose();
  };

  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <Box sx={{ p: 2 }}>
        <Typography variant="overline" color="text.secondary">
          {LABEL[kind]}
        </Typography>
        {kind === 'priority' ? (
          <TextField
            autoFocus
            fullWidth
            size="small"
            type="number"
            label="New priority"
            value={priority}
            onChange={(e) => setPriority(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && priority !== '') {
                run(`p${Number(priority)}`, `${LABEL.priority} → ${Number(priority)}`);
              }
            }}
          />
        ) : (
          <Autocomplete
            openOnFocus
            options={options}
            getOptionLabel={(o) => (o.code ? `${o.code} — ${o.name}` : o.name)}
            isOptionEqualToValue={(a, b) => a.id === b.id}
            onChange={(_, o) => {
              if (o?.code) run(`${SYMBOL[kind]}${o.code}`, `${LABEL[kind]} → ${o.name}`);
            }}
            renderInput={(params) => (
              <TextField {...params} autoFocus size="small" label={`Pick ${LABEL[kind].toLowerCase()}`} />
            )}
          />
        )}
      </Box>
    </Dialog>
  );
}
