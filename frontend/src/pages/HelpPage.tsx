import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Grid,
  Link as MuiLink,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import SystemUpdateAltIcon from '@mui/icons-material/SystemUpdateAlt';
import { checkUpdates, getInfo, UpdateInfo } from '../api/client';
import { GO_TO, SHORTCUTS } from '../config/shortcuts';
import { SideNav } from '../components/SideNav';
import { MarkdownCheatsheet } from '../components/MarkdownCheatsheet';

export function HelpPage() {
  const { data: info } = useQuery({ queryKey: ['info'], queryFn: getInfo, staleTime: 600_000 });
  const [update, setUpdate] = useState<UpdateInfo | null>(null);
  const [checking, setChecking] = useState(false);

  const runCheck = async () => {
    setChecking(true);
    try {
      setUpdate(await checkUpdates());
    } catch {
      setUpdate({ configured: false, current: info?.version ?? '?', updateAvailable: false, error: 'Request failed.' });
    } finally {
      setChecking(false);
    }
  };

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: 4, maxWidth: 1100 }}>
        <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }}>
          Help
        </Typography>

        {/* About + version + updates */}
        <Paper variant="outlined" sx={{ p: 2, mb: 4 }}>
          <Typography variant="h6" sx={{ mb: 1 }}>
            About
          </Typography>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap sx={{ mb: 1.5 }}>
            <Chip label={`${info?.app ?? 'Nibras PIM'}`} color="primary" size="small" />
            <Chip label={`v${info?.version ?? '…'}`} size="small" variant="outlined" />
            {info?.codename && <Chip label={`"${info.codename}"`} size="small" variant="outlined" />}
            {info?.dbName && <Chip label={`DB: ${info.dbName}`} size="small" variant="outlined" />}
            {info?.repoUrl && (
              <MuiLink href={info.repoUrl} target="_blank" rel="noreferrer" variant="body2">
                GitHub repository
              </MuiLink>
            )}
          </Stack>
          <Stack direction="row" spacing={2} alignItems="center">
            <Button
              variant="outlined"
              size="small"
              startIcon={checking ? <CircularProgress size={16} /> : <SystemUpdateAltIcon />}
              disabled={checking}
              onClick={runCheck}
            >
              Check for updates
            </Button>
          </Stack>
          {update && (
            <Box sx={{ mt: 1.5 }}>
              {!update.configured ? (
                <Alert severity="info">
                  Update checks aren't configured yet — set the GitHub repository (server{' '}
                  <code>nibras.repo</code>) to enable them.
                </Alert>
              ) : update.error ? (
                <Alert severity="warning">{update.error}</Alert>
              ) : update.updateAvailable ? (
                <Alert
                  severity="success"
                  action={
                    update.releaseUrl && (
                      <Button color="inherit" size="small" href={update.releaseUrl} target="_blank" rel="noreferrer">
                        View release
                      </Button>
                    )
                  }
                >
                  Version <b>{update.latest}</b> is available (you have {update.current}).
                </Alert>
              ) : (
                <Alert severity="success">You're on the latest version ({update.current}).</Alert>
              )}
            </Box>
          )}
        </Paper>

        {/* Feature guides — the workflows that aren't obvious from the screens alone. */}
        <Typography variant="h6" sx={{ mb: 1 }}>
          Feature guides
        </Typography>
        <Stack spacing={1.5} sx={{ mb: 4 }}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
              Goals on the kanban
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Link a task to a bookmarked goal from the task form's <b>Goal</b> picker. The kanban
              nests each goal's open tasks under its card (tasks completed in the last 7 days show
              struck-out before dropping off); the separate tasks section lists only tasks with no
              goal — the point is to keep tasks attached to goals.
            </Typography>
          </Paper>

          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
              Task time tracking
            </Typography>
            <Typography variant="body2" color="text.secondary">
              <b>Track</b> on a task's detail pane starts a session; <b>Stop</b> (or the auto-stop
              after the configured max slot) creates a Journal entry linked to the task, with an
              optional summary. The running session is also flagged on the server
              (<code>GET /api/timer</code>) — the repo's <code>timer-status.sh</code> turns that
              into a status-bar indicator: 🟢 + task name while tracking, 🔴 when idle, so an
              untracked work session stares back at you.
            </Typography>
          </Paper>

          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
              Import notes — resource types
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Knotes files (<code>date~TYPE~pN~DEPT.md</code>) import with type, priority and
              department taken from the filename. <b>'R' notes additionally need a resource
              type</b> before import, because the type places the record's repo folder
              (<code>R/&lt;type&gt;/…</code>): pick a session-wide default at the top of the
              Import notes page, and override it per note with the chips on the note's card
              (clicking the override again reverts to the default). The saved record's card
              appears right under its note.
            </Typography>
          </Paper>

          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
              Checkout files, pending sync &amp; the bookmarked TOC
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Every record's long text can be exported to a per-record <b>checkout file</b>
              (<code>W18.md</code>-style, in its repo-1 folder) and edited in any editor — edits
              show up under <b>Import files → Pending sync</b> for one-click apply (audit-tracked).
              The <b>Bookmarked TOC</b> button checks out every bookmarked record across all
              modules and rebuilds <code>repo1/TOC.md</code> (department → course → record, with
              manuscript-index embeds for the Zettlr-style writer). A checkout that already exists
              with different content is never overwritten: the old file is parked beside it with a
              timestamp and listed as a conflict for manual merging.
            </Typography>
          </Paper>
        </Stack>

        {/* Keyboard shortcuts */}
        <Typography variant="h6" sx={{ mb: 1 }}>
          Keyboard shortcuts
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Press <b>Shift + ?</b> anywhere to pop this list up as an overlay.
        </Typography>
        <Grid container spacing={3} sx={{ mb: 2 }}>
          {SHORTCUTS.map((group) => (
            <Grid item xs={12} sm={6} key={group.title}>
              <Typography variant="overline" color="text.secondary">
                {group.title}
              </Typography>
              <Stack spacing={0.75} sx={{ mt: 0.5 }}>
                {group.items.map((s) => (
                  <Stack key={s.keys} direction="row" spacing={1.5} alignItems="center">
                    <Box component="kbd" sx={kbdSx}>
                      {s.keys}
                    </Box>
                    <Typography variant="body2">{s.desc}</Typography>
                  </Stack>
                ))}
              </Stack>
            </Grid>
          ))}
          <Grid item xs={12}>
            <Typography variant="overline" color="text.secondary">
              Go to (press g, then…)
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75, mt: 0.5 }}>
              {GO_TO.map((g) => (
                <Chip
                  key={g.key}
                  size="small"
                  variant="outlined"
                  label={
                    <span>
                      <b>g {g.key}</b> · {g.label}
                    </span>
                  }
                />
              ))}
            </Box>
          </Grid>
        </Grid>

        <Divider sx={{ my: 3 }} />

        {/* Markdown cheatsheet */}
        <Typography variant="h6" sx={{ mb: 1 }}>
          Markdown cheatsheet
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Text fields render as Markdown. The most common syntax:
        </Typography>
        <MarkdownCheatsheet />
      </Box>
    </Box>
  );
}

const kbdSx = {
  fontFamily: 'monospace',
  fontSize: 12,
  px: 0.75,
  py: 0.25,
  border: '1px solid',
  borderColor: 'divider',
  borderRadius: 0.75,
  bgcolor: '#f5f5f5',
  whiteSpace: 'nowrap',
  minWidth: 96,
  textAlign: 'center',
} as const;
