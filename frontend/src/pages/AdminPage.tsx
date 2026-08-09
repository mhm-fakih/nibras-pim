import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  Box,
  Button,
  Chip,
  LinearProgress,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableRow,
  Typography,
} from '@mui/material';
import Sync from '@mui/icons-material/Sync';
import { AdminModuleStats, getAdminStats, getClientSyncView, reindexSearch } from '../api/client';
import { SideNav } from '../components/SideNav';
import { ENTITY_BY_KEY } from '../config/entities';

export function AdminPage() {
  const { data } = useQuery({ queryKey: ['admin-stats'], queryFn: getAdminStats });
  const [reindexMsg, setReindexMsg] = useState<string | null>(null);
  const modules = data ?? [];

  const reindex = useMutation({
    mutationFn: reindexSearch,
    onSuccess: (n) => setReindexMsg(`Search index rebuilt: ${n.toLocaleString()} records.`),
    onError: () => setReindexMsg('Reindex failed.'),
  });

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: 4 }}>
        <Stack direction="row" alignItems="center" sx={{ mb: 2 }}>
          <Typography variant="h4" sx={{ fontWeight: 700, flex: 1 }}>
            Administration
          </Typography>
          <Button
            variant="outlined"
            startIcon={<Sync />}
            onClick={() => reindex.mutate()}
            disabled={reindex.isPending}
          >
            Rebuild search index
          </Button>
        </Stack>
        {reindex.isPending && <LinearProgress sx={{ mb: 2 }} />}
        {reindexMsg && (
          <Typography variant="body2" color="success.main" sx={{ mb: 2 }}>
            {reindexMsg}
          </Typography>
        )}

        <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 2 }}>
          {modules.map((m) => (
            <ModuleCard key={m.module} stats={m} />
          ))}
        </Box>

        <ClientSyncPanel />
      </Box>
    </Box>
  );
}

function ModuleCard({ stats }: { stats: AdminModuleStats }) {
  const cfg = ENTITY_BY_KEY[stats.module];
  const usedByField = new Map(stats.parametric.map((p) => [p.field, p]));
  const fields = cfg?.fields ?? [];

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" alignItems="baseline" spacing={1} sx={{ mb: 1 }}>
        <Typography variant="h6" sx={{ fontWeight: 700 }}>
          {cfg?.label ?? stats.module}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {stats.total.toLocaleString()} records
        </Typography>
      </Stack>
      <Table size="small">
        <TableBody>
          {fields.map((f) => {
            const stat = usedByField.get(f.name) ?? (f.type === 'tags' ? usedByField.get('tags') : undefined);
            return (
              <TableRow key={f.name}>
                <TableCell sx={{ py: 0.25, border: 0 }}>
                  <code>{f.name}</code>{' '}
                  <Typography component="span" variant="caption" color="text.secondary">
                    {f.type}
                  </Typography>
                </TableCell>
                <TableCell align="right" sx={{ py: 0.25, border: 0 }}>
                  {stat && (
                    <Chip
                      size="small"
                      label={`${stat.used} / ${stat.available} used`}
                      color={stat.used > 0 ? 'primary' : 'default'}
                      variant="outlined"
                    />
                  )}
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </Paper>
  );
}

/**
 * The parameter vocabulary the mobile / terminal / desktop clients are expected to be working from,
 * filtered exactly as they filter it (departments priority >= 2 by orderNumber; types of enabled
 * modules only, modules in their configured sync order). Shown here so a device that's drifted —
 * a renamed department still cached on the phone, a type no client knows about — is visible by
 * comparison rather than discovered through a mis-filed record.
 */
function ClientSyncPanel() {
  const { data } = useQuery({ queryKey: ['admin-client-sync'], queryFn: getClientSyncView });
  if (!data) return null;

  return (
    <Box sx={{ mt: 5 }}>
      <Typography variant="h5" sx={{ fontWeight: 700, mb: 0.5 }}>
        Synced to clients
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        What mobile, terminal and desktop should be showing. Departments are limited to priority 2
        and above and ordered by their order number; types are those of enabled modules, in each
        module&rsquo;s sync order. Compare against a device to spot a stale copy.
      </Typography>

      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Typography variant="overline" color="text.secondary">
          Departments ({data.departments.length})
        </Typography>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 1 }}>
          {data.departments.map((d) => (
            <Chip
              key={d.id}
              size="small"
              variant="outlined"
              label={`${d.code ?? '?'} · ${d.name ?? '(unnamed)'}  p${d.priority ?? '-'}`}
            />
          ))}
          {data.departments.length === 0 && (
            <Typography variant="caption" color="text.secondary">
              No department reaches priority 2 — the clients would offer none.
            </Typography>
          )}
        </Stack>
      </Paper>

      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 2 }}>
        {data.modules.map((m) => (
          <Paper key={m.moduleKey} variant="outlined" sx={{ p: 2 }}>
            <Stack direction="row" alignItems="baseline" spacing={1} sx={{ mb: 1 }}>
              <Chip size="small" label={m.code ?? '?'} sx={{ fontFamily: 'monospace' }} />
              <Typography sx={{ fontWeight: 700, flex: 1 }}>{m.label}</Typography>
              <Typography variant="caption" color="text.secondary">
                #{m.sortOrder}
              </Typography>
            </Stack>
            <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
              {m.types.map((t) => (
                <Chip key={t.id} size="small" variant="outlined" label={t.code ? `${t.name} (${t.code})` : t.name} />
              ))}
              {m.types.length === 0 && (
                <Typography variant="caption" color="text.secondary">
                  No types.
                </Typography>
              )}
            </Stack>
          </Paper>
        ))}
      </Box>
    </Box>
  );
}
