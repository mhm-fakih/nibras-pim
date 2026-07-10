import { FormEvent, useState } from 'react';
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Stack,
  TextField,
} from '@mui/material';
import { AxiosError } from 'axios';
import { changePassword } from '../api/client';
import { useAuth } from '../hooks/useAuth';

/**
 * Forced password change. Rendered app-wide; becomes a blocking (non-dismissable) modal whenever the
 * signed-in user has `mustChangePassword` set — e.g. the seeded admin, or an account an admin created
 * with a temporary password.
 */
export function ChangePasswordDialog() {
  const { user, setUser } = useAuth();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const open = !!user?.mustChangePassword;
  if (!open) return null;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    if (next !== confirm) {
      setError('The new passwords do not match.');
      return;
    }
    setBusy(true);
    try {
      const updated = await changePassword(current, next);
      setUser(updated);
    } catch (err) {
      const data = (err as AxiosError<{ detail?: string; error?: string }>)?.response?.data;
      setError(data?.detail ?? data?.error ?? 'Could not change the password.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open disableEscapeKeyDown maxWidth="xs" fullWidth>
      <DialogTitle>Change your password</DialogTitle>
      <form onSubmit={submit}>
        <DialogContent>
          <DialogContentText sx={{ mb: 2 }}>
            You must set a new password before continuing.
          </DialogContentText>
          <Stack spacing={2}>
            {error && <Alert severity="error">{error}</Alert>}
            <TextField
              label="Current password"
              type="password"
              value={current}
              onChange={(e) => setCurrent(e.target.value)}
              autoComplete="current-password"
              autoFocus
              fullWidth
            />
            <TextField
              label="New password"
              type="password"
              value={next}
              onChange={(e) => setNext(e.target.value)}
              autoComplete="new-password"
              fullWidth
            />
            <TextField
              label="Confirm new password"
              type="password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              autoComplete="new-password"
              fullWidth
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button type="submit" variant="contained" disabled={busy || !current || !next || !confirm}>
            {busy ? 'Saving…' : 'Change password'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
