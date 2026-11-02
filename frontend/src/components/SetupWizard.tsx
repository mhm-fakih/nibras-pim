import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Collapse,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Link,
  MobileStepper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { errorDetail, http } from '../api/client';
import { useAuth } from '../hooks/useAuth';
import { TOGGLEABLE_PAGES, pageSettingKey } from '../config/pages';

/** Fired (window event) by Settings → "Run the setup wizard again". */
export const OPEN_SETUP_EVENT = 'nibras:open-setup';

interface Folders {
  repo1: string;
  repo2: string;
  repo3: string;
  checkout: string;
  notes: string;
  checklists?: string;
  today: string;
}

interface SetupState {
  pending: boolean;
  appName: string;
  db: { url: string; host: string; name: string; user: string };
  folders: Folders;
  modules: { moduleKey: string; label: string; enabled: boolean }[];
}

/** What each module is for, in a sentence a first-time user can act on. */
const MODULE_HELP: Record<string, string> = {
  tasks: 'Things to do, with status, priority and due dates.',
  planners: 'Timed entries for the calendar: lectures, study blocks, exams, deadlines.',
  journals: 'A diary: one summary per day, plus study logs and reflections.',
  notes: 'Short notes: ideas, lecture points, things to remember.',
  resources: 'Books, papers, slides, videos and links, with reading status.',
  goals: 'Longer-term goals and habits that tasks can belong to.',
  writings: 'Essays, reports and drafts you are writing.',
  departments: 'The broad areas everything is filed under (Studies, Research, …), as a page of their own.',
  courses: 'Courses as records of their own, for filing records by course.',
  indicators: 'Numbers you track over time.',
  excerpts: 'Quotations and passages copied from resources.',
  contacts: 'People, linked to the records they appear in.',
};

/** Modules a fresh install starts with ticked: the everyday core. */
const STARTER_MODULES = ['tasks', 'planners', 'journals', 'notes', 'resources'];

/** Pages a fresh, focused install starts with; every other toggleable page starts off. */
// (Not Kanban: it shows goals and their tasks, and Goals is not a starter module.)
const STARTER_PAGES = new Set(['calendar', 'bookmarks', 'parameters', 'checklists/view', 'local-notes', 'dashboards']);

/** The usual layout under one main folder; each path can still be changed on its own. */
function layoutUnder(base: string): Folders {
  const b = base.trim().replace(/\/+$/, '') || '~/nibras';
  return {
    repo1: `${b}/repo1`,
    repo2: `${b}/repo2`,
    repo3: `${b}/repo3`,
    checkout: `${b}/checkout`,
    notes: `${b}/notes`,
    today: `${b}/today`,
  };
}

/**
 * First-run setup, the web counterpart of the phone app's welcome tour. Shown automatically to an
 * admin on a fresh install (the server reports `pending` while `setup.completed` is "false") and on
 * demand from Settings. Four steps: name, what to switch on, where files go, and a summary. Nothing
 * is saved until Finish; skipping closes it for this visit only.
 */
export function SetupWizard() {
  const { user, can } = useAuth();
  const allowed = !!user && !user.mustChangePassword && can('tools.settings');
  const { data, refetch } = useQuery({
    queryKey: ['setup'],
    queryFn: async () => (await http.get<SetupState>('/setup')).data,
    enabled: allowed,
    staleTime: Infinity,
  });

  const [open, setOpen] = useState(false);
  const [dismissed, setDismissed] = useState(false);
  const [step, setStep] = useState(0);
  const [appName, setAppName] = useState('');
  const [modules, setModules] = useState<Set<string>>(new Set());
  const [pages, setPages] = useState<Record<string, boolean>>({});
  const [base, setBase] = useState('~/nibras');
  const [folders, setFolders] = useState<Folders>(layoutUnder('~/nibras'));
  const [advanced, setAdvanced] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Fill the form from the server: on a pending setup the starter choices, otherwise what is set now.
  const reset = (s: SetupState, fresh: boolean) => {
    setAppName(s.appName);
    setModules(new Set(fresh ? STARTER_MODULES : s.modules.filter((m) => m.enabled).map((m) => m.moduleKey)));
    setPages(Object.fromEntries(TOGGLEABLE_PAGES.map((p) => [p.key, fresh ? STARTER_PAGES.has(p.key) : true])));
    if (fresh) {
      setBase('~/nibras');
      setFolders(layoutUnder('~/nibras'));
    } else {
      setFolders(s.folders);
    }
    setStep(0);
    setError(null);
  };

  useEffect(() => {
    if (data?.pending && !dismissed && !open) {
      reset(data, true);
      setOpen(true);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data, dismissed]);

  useEffect(() => {
    const onOpen = async () => {
      const s = (await refetch()).data;
      if (!s) return;
      reset(s, false);
      // Show the real page switches when reopening, not the starter set.
      const keys = TOGGLEABLE_PAGES.map((p) => pageSettingKey(p.key)).join(',');
      const current = (await http.get<Record<string, string>>('/settings/multi', { params: { keys } })).data;
      setPages(Object.fromEntries(TOGGLEABLE_PAGES.map((p) => [p.key, current[pageSettingKey(p.key)] !== 'false'])));
      setOpen(true);
    };
    window.addEventListener(OPEN_SETUP_EVENT, onOpen);
    return () => window.removeEventListener(OPEN_SETUP_EVENT, onOpen);
  }, [refetch]);

  const moduleList = useMemo(() => data?.modules ?? [], [data]);
  if (!allowed || !data || !open) return null;

  const toggleModule = (key: string) =>
    setModules((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });

  const finish = async () => {
    setBusy(true);
    setError(null);
    try {
      await http.post('/setup', {
        appName: appName.trim(),
        enabledModules: Array.from(modules),
        pages,
        folders,
      });
      // Modules, the rail and page switches are read all over the app; a reload is the honest refresh.
      window.location.reload();
    } catch (err) {
      setError(errorDetail(err, 'Could not save the setup.'));
      setBusy(false);
    }
  };

  const close = () => {
    setOpen(false);
    setDismissed(true);
  };

  const steps = 4;
  const field = (key: keyof Folders, label: string, help: string) => (
    <TextField
      size="small"
      label={label}
      helperText={help}
      value={folders[key] ?? ''}
      onChange={(e) => setFolders({ ...folders, [key]: e.target.value })}
      fullWidth
    />
  );

  return (
    <Dialog open maxWidth="sm" fullWidth onClose={close}>
      <DialogTitle>
        {['Welcome', 'What to use', 'Where files go', 'All set'][step]}
      </DialogTitle>
      <DialogContent dividers sx={{ minHeight: 380 }}>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        {step === 0 && (
          <Stack spacing={2}>
            <Typography>
              This is a new installation. A few questions set it up for the way you work; everything
              can be changed later in Settings, and this tour can be reopened from there.
            </Typography>
            <Typography color="text.secondary">
              Records (tasks, plans, journal entries, notes, resources) live in the database. Each
              record can also have a folder of files on this computer, kept in up to three
              repositories.
            </Typography>
            <TextField
              label="Name shown in the sidebar and the browser tab"
              value={appName}
              onChange={(e) => setAppName(e.target.value)}
              fullWidth
            />
          </Stack>
        )}

        {step === 1 && (
          <Stack spacing={1}>
            <Typography variant="subtitle2">Modules</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
              Switch on only what you will use; the rest stays out of the way and can be turned on
              any time.
            </Typography>
            {moduleList.map((m) => (
              <FormControlLabel
                key={m.moduleKey}
                sx={{ alignItems: 'flex-start', ml: 0 }}
                control={
                  <Checkbox
                    size="small"
                    checked={modules.has(m.moduleKey)}
                    onChange={() => toggleModule(m.moduleKey)}
                    sx={{ pt: 0.25 }}
                  />
                }
                label={
                  <Box>
                    <Typography variant="body2" fontWeight={600}>
                      {m.label}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {MODULE_HELP[m.moduleKey] ?? ''}
                    </Typography>
                  </Box>
                }
              />
            ))}
            <Typography variant="subtitle2" sx={{ pt: 2 }}>
              Pages
            </Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
              {TOGGLEABLE_PAGES.map((p) => (
                <FormControlLabel
                  key={p.key}
                  control={
                    <Checkbox
                      size="small"
                      checked={!!pages[p.key]}
                      onChange={(e) => setPages({ ...pages, [p.key]: e.target.checked })}
                    />
                  }
                  label={<Typography variant="body2">{p.label}</Typography>}
                />
              ))}
            </Box>
          </Stack>
        )}

        {step === 2 && (
          <Stack spacing={2}>
            <Typography variant="body2" color="text.secondary">
              One main folder holds everything this installation writes to disk. The folders are
              created when you finish. <code>~</code> means your home folder on the server.
            </Typography>
            <Stack direction="row" spacing={1}>
              <TextField
                size="small"
                label="Main folder"
                value={base}
                onChange={(e) => setBase(e.target.value)}
                fullWidth
              />
              <Button variant="outlined" onClick={() => setFolders(layoutUnder(base))}>
                Use
              </Button>
            </Stack>
            <Table size="small">
              <TableBody>
                {(
                  [
                    ['Repository 1', folders.repo1],
                    ['Repository 2', folders.repo2],
                    ['Repository 3', folders.repo3],
                    ['Notes', folders.notes],
                  ] as const
                ).map(([k, v]) => (
                  <TableRow key={k}>
                    <TableCell sx={{ pl: 0, width: 130 }}>{k}</TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>{v}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Link component="button" type="button" onClick={() => setAdvanced(!advanced)} sx={{ alignSelf: 'flex-start' }}>
              {advanced ? 'Hide the individual folders' : 'Change folders one by one'}
            </Link>
            <Collapse in={advanced}>
              <Stack spacing={2} sx={{ pt: 1 }}>
                {field('repo1', 'Repository 1', 'Working files: each record’s folder, created on demand.')}
                {field('repo2', 'Repository 2', 'The main archive, for files you keep.')}
                {field('repo3', 'Repository 3', 'Dismissed: files set aside but not deleted.')}
                {field('checkout', 'Bookmarks folder', 'A shortcut to the folder of every bookmarked record.')}
                {field('notes', 'Notes folder', 'Plain-text notes, shared with the phone and desktop apps; checklists go in its checklists/ folder.')}
                {field('today', 'Today link', 'A link that always points at today’s journal folder.')}
              </Stack>
            </Collapse>
          </Stack>
        )}

        {step === 3 && (
          <Stack spacing={2}>
            <Typography>Here is what will be saved. Press Finish to apply it.</Typography>
            <Table size="small">
              <TableBody>
                <TableRow>
                  <TableCell sx={{ pl: 0, width: 130 }}>Name</TableCell>
                  <TableCell>{appName || 'Nibras'}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ pl: 0 }}>Modules</TableCell>
                  <TableCell>
                    {moduleList.filter((m) => modules.has(m.moduleKey)).map((m) => m.label).join(', ') || 'none'}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ pl: 0 }}>Pages</TableCell>
                  <TableCell>
                    {TOGGLEABLE_PAGES.filter((p) => pages[p.key]).map((p) => p.label).join(', ') || 'none'}
                  </TableCell>
                </TableRow>
                {(
                  [
                    ['Repository 1', folders.repo1],
                    ['Repository 2', folders.repo2],
                    ['Repository 3', folders.repo3],
                    ['Notes', folders.notes],
                    ['Bookmarks', folders.checkout],
                    ['Today link', folders.today],
                  ] as const
                ).map(([k, v]) => (
                  <TableRow key={k}>
                    <TableCell sx={{ pl: 0 }}>{k}</TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>{v}</TableCell>
                  </TableRow>
                ))}
                <TableRow>
                  <TableCell sx={{ pl: 0 }}>Database</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>
                    {data.db.name} on {data.db.host} (user {data.db.user})
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </Stack>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 2 }}>
        <MobileStepper
          variant="dots"
          steps={steps}
          position="static"
          activeStep={step}
          sx={{ flex: 1, bgcolor: 'transparent', p: 0 }}
          backButton={
            <Button disabled={step === 0 || busy} onClick={() => setStep(step - 1)}>
              Back
            </Button>
          }
          nextButton={
            step < steps - 1 ? (
              <Button variant="contained" onClick={() => setStep(step + 1)}>
                Next
              </Button>
            ) : (
              <Button variant="contained" disabled={busy} onClick={finish}>
                {busy ? 'Saving…' : 'Finish'}
              </Button>
            )
          }
        />
        <Button onClick={close} disabled={busy} sx={{ ml: 1 }}>
          {data.pending ? 'Later' : 'Close'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
