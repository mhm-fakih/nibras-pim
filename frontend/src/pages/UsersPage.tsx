import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  IconButton,
  MenuItem,
  Paper,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import Add from '@mui/icons-material/Add';
import Edit from '@mui/icons-material/Edit';
import Delete from '@mui/icons-material/Delete';
import {
  ManagedUser,
  Role,
  createUser,
  deleteUser,
  listUsers,
  updateUser,
  errorDetail,
} from '../api/client';
import { SideNav } from '../components/SideNav';

const ROLES: { value: Role; label: string }[] = [
  { value: 'ADMIN', label: 'Admin' },
  { value: 'DATA_ENTRY', label: 'Data entry' },
  { value: 'PARAMETERS_DATA_ENTRY', label: 'Parameters data entry' },
  { value: 'READ_ONLY', label: 'Read only' },
];

const ROLE_LABEL: Record<Role, string> = {
  ADMIN: 'Admin',
  DATA_ENTRY: 'Data entry',
  PARAMETERS_DATA_ENTRY: 'Parameters data entry',
  READ_ONLY: 'Read only',
};

interface Draft {
  id?: number;
  username: string;
  displayName: string;
  role: Role;
  enabled: boolean;
  password: string;
}

const BLANK: Draft = { username: '', displayName: '', role: 'READ_ONLY', enabled: true, password: '' };

export function UsersPage() {
  const qc = useQueryClient();
  const { data: users = [] } = useQuery({ queryKey: ['users'], queryFn: listUsers });
  const [draft, setDraft] = useState<Draft | null>(null);
  const [error, setError] = useState<string | null>(null);

  const invalidate = () => qc.invalidateQueries({ queryKey: ['users'] });

  const save = useMutation({
    mutationFn: (d: Draft) => {
      const body = {
        username: d.username.trim(),
        displayName: d.displayName.trim() || null,
        role: d.role,
        enabled: d.enabled,
        // Only send the password when set (blank on edit = keep existing).
        ...(d.password ? { password: d.password } : {}),
      };
      return d.id ? updateUser(d.id, body) : createUser(body);
    },
    onSuccess: () => {
      invalidate();
      setDraft(null);
      setError(null);
    },
    onError: (err: unknown) => setError(errorDetail(err, 'Could not save the user.')),
  });

  const remove = useMutation({
    mutationFn: (id: number) => deleteUser(id),
    onSuccess: invalidate,
    onError: (err: unknown) => setError(errorDetail(err, 'Could not delete the user.')),
  });

  const startEdit = (u: ManagedUser) =>
    setDraft({
      id: u.id,
      username: u.username,
      displayName: u.displayName ?? '',
      role: u.role,
      enabled: u.enabled,
      password: '',
    });

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: 4 }}>
        <Stack direction="row" alignItems="center" sx={{ mb: 2 }}>
          <Typography variant="h4" sx={{ fontWeight: 700, flex: 1 }}>
            Users
          </Typography>
          <Button variant="contained" startIcon={<Add />} onClick={() => { setError(null); setDraft({ ...BLANK }); }}>
            Add user
          </Button>
        </Stack>

        <Paper>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Username</TableCell>
                <TableCell>Name</TableCell>
                <TableCell>Role</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {users.map((u) => (
                <TableRow key={u.id} hover>
                  <TableCell>{u.username}</TableCell>
                  <TableCell>{u.displayName ?? '—'}</TableCell>
                  <TableCell>{ROLE_LABEL[u.role]}</TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      label={u.enabled ? 'Active' : 'Disabled'}
                      color={u.enabled ? 'success' : 'default'}
                    />
                  </TableCell>
                  <TableCell align="right">
                    <IconButton size="small" onClick={() => startEdit(u)}>
                      <Edit fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      onClick={() => {
                        if (window.confirm(`Delete user "${u.username}"?`)) remove.mutate(u.id);
                      }}
                    >
                      <Delete fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>

        {error && !draft && (
          <Typography color="error" sx={{ mt: 2 }}>
            {error}
          </Typography>
        )}
      </Box>

      <Dialog open={!!draft} onClose={() => setDraft(null)} maxWidth="xs" fullWidth>
        <DialogTitle>{draft?.id ? 'Edit user' : 'Add user'}</DialogTitle>
        <DialogContent>
          {draft && (
            <Stack spacing={2} sx={{ mt: 1 }}>
              {error && <Typography color="error">{error}</Typography>}
              <TextField
                label="Username"
                value={draft.username}
                onChange={(e) => setDraft({ ...draft, username: e.target.value })}
                onKeyDown={(e) =>
                  e.key === 'Enter' &&
                  !save.isPending &&
                  draft.username &&
                  (draft.id || draft.password) &&
                  save.mutate(draft)
                }
                fullWidth
              />
              <TextField
                label="Display name"
                value={draft.displayName}
                onChange={(e) => setDraft({ ...draft, displayName: e.target.value })}
                onKeyDown={(e) =>
                  e.key === 'Enter' &&
                  !save.isPending &&
                  draft.username &&
                  (draft.id || draft.password) &&
                  save.mutate(draft)
                }
                fullWidth
              />
              <TextField
                select
                label="Role"
                value={draft.role}
                onChange={(e) => setDraft({ ...draft, role: e.target.value as Role })}
                fullWidth
              >
                {ROLES.map((r) => (
                  <MenuItem key={r.value} value={r.value}>
                    {r.label}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                label={draft.id ? 'New password (leave blank to keep)' : 'Password'}
                type="password"
                value={draft.password}
                onChange={(e) => setDraft({ ...draft, password: e.target.value })}
                onKeyDown={(e) =>
                  e.key === 'Enter' &&
                  !save.isPending &&
                  draft.username &&
                  (draft.id || draft.password) &&
                  save.mutate(draft)
                }
                autoComplete="new-password"
                fullWidth
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={draft.enabled}
                    onChange={(e) => setDraft({ ...draft, enabled: e.target.checked })}
                  />
                }
                label="Active"
              />
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDraft(null)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={!draft?.username || (!draft?.id && !draft?.password) || save.isPending}
            onClick={() => draft && save.mutate(draft)}
          >
            Save
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
