import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Box,
  Chip,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
  Button,
} from '@mui/material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { AuditEntry, AuditParams, listAudit } from '../api/client';
import { SideNav } from '../components/SideNav';
import { ENTITIES, ENTITY_CLASS_BY_KEY } from '../config/entities';

const OP_COLOR: Record<string, 'success' | 'info' | 'error' | 'default'> = {
  INSERT: 'success',
  UPDATE: 'info',
  DELETE: 'error',
};

function ymd(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export function AuditPage() {
  const [filters, setFilters] = useState<AuditParams>({ page: 0, size: 50 });
  const set = (patch: Partial<AuditParams>) => setFilters((f) => ({ ...f, ...patch, page: 0 }));

  const { data, isLoading } = useQuery({
    queryKey: ['audit', filters],
    queryFn: () => listAudit(filters),
  });

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: 4 }}>
        <Typography variant="h4" sx={{ fontWeight: 700, mb: 2 }}>
          Audit log
        </Typography>

        <Stack direction="row" spacing={2} sx={{ mb: 2 }} flexWrap="wrap" useFlexGap>
          <TextField
            select
            size="small"
            label="Module"
            value={filters.entity ?? ''}
            onChange={(e) => set({ entity: e.target.value || undefined })}
            sx={{ width: 150 }}
          >
            <MenuItem value="">All</MenuItem>
            {ENTITIES.map((e) => (
              <MenuItem key={e.key} value={ENTITY_CLASS_BY_KEY[e.key]}>
                {e.label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            size="small"
            label="Operation"
            value={filters.op ?? ''}
            onChange={(e) => set({ op: e.target.value || undefined })}
            sx={{ width: 130 }}
          >
            <MenuItem value="">All</MenuItem>
            <MenuItem value="INSERT">Insert</MenuItem>
            <MenuItem value="UPDATE">Update</MenuItem>
            <MenuItem value="DELETE">Delete</MenuItem>
          </TextField>
          <TextField
            size="small"
            label="Field"
            value={filters.field ?? ''}
            onChange={(e) => set({ field: e.target.value || undefined })}
            sx={{ width: 140 }}
          />
          <DatePicker
            label="From"
            value={filters.from ? new Date(`${filters.from}T00:00:00`) : null}
            onChange={(d) => set({ from: d ? ymd(d as Date) : undefined })}
            slotProps={{ textField: { size: 'small', sx: { width: 160 } }, field: { clearable: true } }}
          />
          <DatePicker
            label="To"
            value={filters.to ? new Date(`${filters.to}T00:00:00`) : null}
            onChange={(d) => set({ to: d ? ymd(d as Date) : undefined })}
            slotProps={{ textField: { size: 'small', sx: { width: 160 } }, field: { clearable: true } }}
          />
        </Stack>

        <Paper variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>When</TableCell>
                <TableCell>Module</TableCell>
                <TableCell>Record</TableCell>
                <TableCell>Op</TableCell>
                <TableCell>Field</TableCell>
                <TableCell>Old → New</TableCell>
                <TableCell>By</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {(data?.content ?? []).map((a: AuditEntry) => (
                <TableRow key={a.id} hover>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    {new Date(a.changedAt).toLocaleString()}
                  </TableCell>
                  <TableCell>{a.entity}</TableCell>
                  <TableCell>#{a.entityId}</TableCell>
                  <TableCell>
                    <Chip size="small" label={a.op} color={OP_COLOR[a.op] ?? 'default'} />
                  </TableCell>
                  <TableCell>{a.field ?? '—'}</TableCell>
                  <TableCell sx={{ maxWidth: 420 }}>
                    {a.op === 'UPDATE' ? (
                      <Box dir="auto" sx={{ fontSize: 13 }}>
                        <Box component="span" sx={{ color: 'error.main', textDecoration: 'line-through' }}>
                          {truncate(a.oldValue)}
                        </Box>{' '}
                        →{' '}
                        <Box component="span" sx={{ color: 'success.main' }}>
                          {truncate(a.newValue)}
                        </Box>
                      </Box>
                    ) : (
                      '—'
                    )}
                  </TableCell>
                  <TableCell>{a.changedBy}</TableCell>
                </TableRow>
              ))}
              {!isLoading && (data?.content?.length ?? 0) === 0 && (
                <TableRow>
                  <TableCell colSpan={7} sx={{ color: 'text.secondary', py: 3 }}>
                    No changes match these filters.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </Paper>

        {data && data.totalPages > 1 && (
          <Stack direction="row" spacing={2} alignItems="center" sx={{ mt: 2 }}>
            <Button
              size="small"
              disabled={(filters.page ?? 0) <= 0}
              onClick={() => setFilters((f) => ({ ...f, page: (f.page ?? 0) - 1 }))}
            >
              Previous
            </Button>
            <Typography variant="body2">
              Page {(filters.page ?? 0) + 1} of {data.totalPages} · {data.totalElements} changes
            </Typography>
            <Button
              size="small"
              disabled={(filters.page ?? 0) >= data.totalPages - 1}
              onClick={() => setFilters((f) => ({ ...f, page: (f.page ?? 0) + 1 }))}
            >
              Next
            </Button>
          </Stack>
        )}
      </Box>
    </Box>
  );
}

function truncate(v?: string): string {
  if (v == null) return '∅';
  return v.length > 80 ? v.slice(0, 80) + '…' : v;
}
