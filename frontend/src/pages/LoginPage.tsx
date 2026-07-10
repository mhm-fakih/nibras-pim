import { FormEvent, useState } from 'react';
import { useNavigate, useLocation, Navigate } from 'react-router-dom';
import { Alert, Box, Button, Paper, Stack, TextField, Typography } from '@mui/material';
import { AxiosError } from 'axios';
import { useAuth } from '../hooks/useAuth';

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Already signed in — skip the form.
  if (user) {
    const from = (location.state as { from?: string })?.from ?? '/tasks';
    return <Navigate to={from} replace />;
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await login(username.trim(), password);
      const from = (location.state as { from?: string })?.from ?? '/tasks';
      navigate(from, { replace: true });
    } catch (err) {
      const status = (err as AxiosError)?.response?.status;
      setError(status === 401 ? 'Invalid username or password.' : 'Sign-in failed. Please try again.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Box sx={{ display: 'flex', height: '100vh', alignItems: 'center', justifyContent: 'center', p: 2 }}>
      <Paper sx={{ p: 4, width: 360, maxWidth: '100%' }} elevation={3}>
        <Typography variant="h5" sx={{ fontWeight: 700, mb: 1 }}>
          Sign in
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Nibras PIM
        </Typography>
        <form onSubmit={submit}>
          <Stack spacing={2}>
            {error && <Alert severity="error">{error}</Alert>}
            <TextField
              label="Username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoFocus
              autoComplete="username"
              fullWidth
            />
            <TextField
              label="Password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              fullWidth
            />
            <Button type="submit" variant="contained" size="large" disabled={busy || !username || !password}>
              {busy ? 'Signing in…' : 'Sign in'}
            </Button>
          </Stack>
        </form>
      </Paper>
    </Box>
  );
}
