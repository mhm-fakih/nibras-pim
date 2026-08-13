import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link as RouterLink } from 'react-router-dom';
import { Box, CircularProgress, Link as MuiLink, Tooltip, Typography } from '@mui/material';
import { checkUpdates, getInfo, UpdateInfo } from '../api/client';
import { useFeature } from '../hooks/useFeature';

/** Thin small-font status bar (server IP, DB, version, update check, help). */
export function StatusBar() {
  const { data } = useQuery({ queryKey: ['info'], queryFn: getInfo, staleTime: 600_000 });
  // Only the CODENAME is toggleable (Settings → Features), not the version: the number is what you
  // quote when something is wrong, so it stays. The same switch governs the sidebar header, so the
  // two places that carry it agree.
  const showCodename = useFeature('codename');
  const [update, setUpdate] = useState<UpdateInfo | null>(null);
  const [checking, setChecking] = useState(false);

  const runCheck = async () => {
    if (checking) return;
    setChecking(true);
    try {
      setUpdate(await checkUpdates());
    } catch {
      setUpdate(null);
    } finally {
      setChecking(false);
    }
  };

  const cell = (label: string, value?: string | number) =>
    value == null ? null : (
      <Box component="span" sx={{ mr: 2, whiteSpace: 'nowrap' }}>
        <Box component="span" sx={{ color: 'text.disabled' }}>
          {label}:
        </Box>{' '}
        <Box component="span" sx={{ fontFamily: 'monospace' }}>
          {value}
        </Box>
      </Box>
    );

  const updateLabel = () => {
    if (checking) return 'checking…';
    if (!update) return 'check for updates';
    if (!update.configured) return 'updates: not configured';
    if (update.error) return 'update check failed';
    if (update.updateAvailable) return `update available: v${update.latest}`;
    return 'up to date';
  };

  const updateTitle = !update
    ? 'Check GitHub for a newer release'
    : update.error ?? (update.updateAvailable ? 'A newer release is on GitHub' : 'You are on the latest version');

  return (
    <Box
      sx={{
        height: 22,
        flexShrink: 0,
        borderTop: 1,
        borderColor: 'divider',
        bgcolor: '#f3f2f1',
        display: 'flex',
        alignItems: 'center',
        px: 1.5,
        overflow: 'hidden',
      }}
    >
      <Typography variant="caption" sx={{ fontSize: 11, color: 'text.secondary' }} noWrap>
        {cell('Server', data ? `${data.serverIp}:${data.port}` : '…')}
        {cell('DB', data?.dbName)}
        {cell('App', data?.app)}
        {cell(
          'Version',
          data ? `v${data.version}${showCodename && data.codename ? ` "${data.codename}"` : ''}` : '…',
        )}
      </Typography>

      <Box sx={{ flex: 1 }} />

      <Typography variant="caption" sx={{ fontSize: 11 }} noWrap>
        <Tooltip title={updateTitle}>
          <MuiLink
            component="button"
            type="button"
            onClick={runCheck}
            underline="hover"
            sx={{
              fontSize: 11,
              color: update?.updateAvailable ? 'warning.main' : 'text.secondary',
              fontWeight: update?.updateAvailable ? 700 : 400,
              display: 'inline-flex',
              alignItems: 'center',
              gap: 0.5,
              mr: 2,
            }}
          >
            {checking && <CircularProgress size={10} />}
            {updateLabel()}
          </MuiLink>
        </Tooltip>
        <MuiLink component={RouterLink} to="/help" underline="hover" sx={{ fontSize: 11, color: 'text.secondary' }}>
          Help
        </MuiLink>
      </Typography>
    </Box>
  );
}
