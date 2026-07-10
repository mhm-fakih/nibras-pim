import { useQuery } from '@tanstack/react-query';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Chip,
  Stack,
  Typography,
} from '@mui/material';
import ExpandMore from '@mui/icons-material/ExpandMore';
import { AuditEntry, getRecordHistory } from '../api/client';

const OP_COLOR: Record<string, 'success' | 'info' | 'error' | 'default'> = {
  INSERT: 'success',
  UPDATE: 'info',
  DELETE: 'error',
};

/** Per-record change history (drawn from the audit trail). `entityClass` is e.g. "Task". */
export function HistoryPanel({ entityClass, id }: { entityClass: string; id: number }) {
  const { data } = useQuery({
    queryKey: ['history', entityClass, id],
    queryFn: () => getRecordHistory(entityClass, id),
  });

  const entries = data ?? [];

  return (
    <Accordion variant="outlined" disableGutters sx={{ mt: 2 }}>
      <AccordionSummary expandIcon={<ExpandMore />}>
        <Typography variant="overline" color="text.secondary">
          History ({entries.length})
        </Typography>
      </AccordionSummary>
      <AccordionDetails>
        {entries.length === 0 && (
          <Typography variant="body2" color="text.secondary">
            No recorded changes yet.
          </Typography>
        )}
        <Stack spacing={1}>
          {entries.map((a: AuditEntry) => (
            <Box key={a.id} sx={{ fontSize: 13 }}>
              <Stack direction="row" spacing={1} alignItems="center">
                <Chip size="small" label={a.op} color={OP_COLOR[a.op] ?? 'default'} />
                <Typography variant="caption" color="text.secondary">
                  {new Date(a.changedAt).toLocaleString()} · {a.changedBy}
                </Typography>
              </Stack>
              {a.op === 'UPDATE' && (
                <Box dir="auto" sx={{ ml: 0.5, mt: 0.5 }}>
                  <strong>{a.field}</strong>:{' '}
                  <Box component="span" sx={{ color: 'error.main', textDecoration: 'line-through' }}>
                    {a.oldValue ?? '∅'}
                  </Box>{' '}
                  →{' '}
                  <Box component="span" sx={{ color: 'success.main' }}>
                    {a.newValue ?? '∅'}
                  </Box>
                </Box>
              )}
            </Box>
          ))}
        </Stack>
      </AccordionDetails>
    </Accordion>
  );
}
