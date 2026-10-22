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
      const res = (err as AxiosError<{ message?: string; error?: string; detail?: string; title?: string; status?: number }>)?.response;
      // Which failure is it? The backend's own errors are always JSON: a message/error field
      // (SecurityConfig's writeError) or an RFC 9457 problem detail (ApiExceptionHandler, and how a
      // bad-credentials 401 from /auth/login arrives — application/problem+json with
      // type/title/status/instance, and detail only when there is one: Spring omits an empty
      // detail, so testing for `detail` alone misread such a reply as the server being down).
      // Only the app answers 401, so a 401 is "wrong credentials" whatever its body. "Can't reach
      // the server" is kept for real outages: no response at all, or a server error that is not
      // the app's own JSON — a proxy reporting the backend unreachable (Vite's dev proxy answers
      // 500 with an empty text/plain body, a reverse proxy 502/503/504).
      const data = res?.data;
      const isAppError =
        typeof data === 'object' && data !== null &&
        ['message', 'error', 'detail', 'title', 'status'].some((k) => k in data);
      const gatewayDown = !!res && res.status >= 500 && !isAppError;
      if (!res || gatewayDown) {
        setError("Can't reach the server — the backend may be down. Check with your administrator.");
      } else if (res.status === 401) {
        setError('Invalid username or password.');
      } else if (isAppError) {
        // The app said why; show it rather than a generic line.
        const why = data?.detail ?? data?.message ?? data?.error ?? data?.title;
        setError(why ? `Sign-in failed: ${why}` : 'Sign-in failed. Please try again.');
      } else {
        setError('Sign-in failed. Please try again.');
      }
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
