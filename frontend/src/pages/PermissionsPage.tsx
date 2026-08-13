import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Checkbox,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import {
  Permission,
  PermissionMatrix,
  Role,
  getPermissionMatrix,
  setRolePermissions,
} from '../api/client';
import { SideNav } from '../components/SideNav';
import { useAuth } from '../hooks/useAuth';

const ROLE_LABEL: Record<Role, string> = {
  ADMIN: 'Admin',
  DATA_ENTRY: 'Data entry',
  PARAMETERS_DATA_ENTRY: 'Parameters data entry',
  READ_ONLY: 'Read only',
};

const PERMISSION_LABEL: Record<Permission, string> = {
  'content.write': 'Write content',
  'params.write': 'Write parameters',
  'users.manage': 'Manage users',
  'tools.admin': 'Admin tools',
  'tools.settings': 'Settings',
  'tools.audit': 'Audit log',
};

const ROLE_ORDER: Role[] = ['ADMIN', 'DATA_ENTRY', 'PARAMETERS_DATA_ENTRY', 'READ_ONLY'];

export function PermissionsPage() {
  const qc = useQueryClient();
  const { user, setUser } = useAuth();
  const { data } = useQuery({ queryKey: ['permissions'], queryFn: getPermissionMatrix });

  const [error, setError] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: ({ role, permissions }: { role: Role; permissions: Permission[] }) =>
      setRolePermissions(role, permissions),
    onSuccess: (matrix: PermissionMatrix) => {
      setError(null);
      qc.setQueryData(['permissions'], matrix);
      // If the signed-in user's own role changed, refresh their capabilities so the UI updates live.
      if (user && matrix.roles[user.role]) {
        setUser({ ...user, permissions: matrix.roles[user.role] });
      }
    },
    // A checkbox here is drawn from the SERVER's matrix, so a failed save leaves it exactly where
    // it was — which reads as the box refusing the click rather than as an error. Say so out loud:
    // this screen has no other way to tell the difference between "didn't save" and "didn't move".
    onError: (e: unknown) => {
      const detail = (e as { response?: { data?: { detail?: string; message?: string } } })?.response?.data;
      setError(detail?.detail ?? detail?.message ?? 'The change could not be saved.');
    },
  });

  const toggle = (role: Role, permission: Permission, held: string[]) => {
    const next = held.includes(permission)
      ? (held.filter((p) => p !== permission) as Permission[])
      : ([...held, permission] as Permission[]);
    save.mutate({ role, permissions: next });
  };

  const matrix = data;
  const columns = matrix?.permissions ?? [];

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: 4 }}>
        <Stack direction="row" alignItems="baseline" spacing={2} sx={{ mb: 1 }}>
          <Typography variant="h4" sx={{ fontWeight: 700 }}>
            Roles &amp; permissions
          </Typography>
        </Stack>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Tick a capability to grant it to a role. Changes take effect immediately. The Admin role always
          has every capability and can&apos;t be changed.
        </Typography>

        {error && (
          <Alert severity="error" onClose={() => setError(null)} sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        <Paper sx={{ overflow: 'auto' }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 700 }}>Role</TableCell>
                {columns.map((p) => (
                  <TableCell key={p} align="center" sx={{ fontWeight: 700 }}>
                    <Tooltip title={p}>
                      <span>{PERMISSION_LABEL[p]}</span>
                    </Tooltip>
                  </TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {matrix &&
                ROLE_ORDER.map((role) => {
                  const held = matrix.roles[role] ?? [];
                  const locked = role === 'ADMIN';
                  return (
                    <TableRow key={role} hover>
                      <TableCell>{ROLE_LABEL[role]}</TableCell>
                      {columns.map((p) => (
                        <TableCell key={p} align="center">
                          <Checkbox
                            size="small"
                            checked={held.includes(p)}
                            disabled={locked || save.isPending}
                            onChange={() => toggle(role, p, held)}
                          />
                        </TableCell>
                      ))}
                    </TableRow>
                  );
                })}
            </TableBody>
          </Table>
        </Paper>
      </Box>
    </Box>
  );
}
