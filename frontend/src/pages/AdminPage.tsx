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
import { AdminModuleStats, getAdminStats, reindexSearch } from '../api/client';
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
