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
import { AuditEntry, getRecordHistory } from '../api/client';
import { useAuth } from '../hooks/useAuth';

const OP_COLOR: Record<string, 'success' | 'info' | 'error' | 'default'> = {
  INSERT: 'success',
  UPDATE: 'info',
  DELETE: 'error',
};

/**
 * Per-record change history (drawn from the audit trail). `entityClass` is e.g. "Task".
 *
 * Renders nothing at all for a role without `tools.audit`, rather than an empty panel. The data
 * comes from `/api/audit/{entity}/{id}`, which SecurityConfig gates on that capability, and a 403
 * is indistinguishable here from an empty result — so the panel used to tell people who weren't
 * allowed to see the history that the record had none. Saying "no changes" to someone who simply
 * isn't permitted to know is worse than saying nothing.
 *
 * Gated here rather than at the call site so the rule travels with the component, and so the
 * request is never made in the first place: an ordinary record view would otherwise fire a 403 on
 * every open for most of the roles in the matrix.
 */
export function HistoryPanel({ entityClass, id }: { entityClass: string; id: number }) {
  const { can } = useAuth();
  const allowed = can('tools.audit');

  const { data } = useQuery({
    queryKey: ['history', entityClass, id],
    queryFn: () => getRecordHistory(entityClass, id),
    enabled: allowed,
  });

  if (!allowed) {
    return null;
  }

  const entries = data ?? [];

  return (
    <Accordion variant="outlined" disableGutters expanded sx={{ mt: 2 }}>
      <AccordionSummary sx={{ cursor: 'default' }}>
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
