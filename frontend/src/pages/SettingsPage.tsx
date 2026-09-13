import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  FormControlLabel,
  Paper,
  MenuItem,
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
import {
  AppModule,
  RepoSettings,
  getRepoSettings,
  getLookups,
  getSetting,
  getSettingsMulti,
  setSetting,
  updateModule,
  updateRepoSettings,
  errorDetail,
} from '../api/client';
import { SideNav } from '../components/SideNav';
import { DEFAULT_MAX_SLOT_MINUTES } from '../hooks/useRunningTimer';
import { DEFAULT_TODAY_COUNT, TODAY_COUNT_KEY } from './TodayPage';
import { useModules } from '../hooks/useModules';
import {
  FEATURE_SETTING_KEYS,
  PAGE_SETTING_KEYS,
  TOGGLEABLE_FEATURES,
  TOGGLEABLE_PAGES,
  featureSettingKey,
  pageSettingKey,
} from '../config/pages';
import { useNotificationPrefs } from '../hooks/useNotificationPrefs';

export function SettingsPage() {
  const { modules } = useModules();
  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: 4, maxWidth: 900 }}>
        <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }}>
          Settings
        </Typography>

        <AppSettingsSection />
        <PagesSection />
        <FeaturesSection />
        <NotificationsSection />
        <IndicatorAutoCollectSection />
        <DescriptionAppendSection />

        <Typography variant="subtitle1" sx={{ mb: 3 }}>
          Modules
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Enable or disable a module in the navigation and give it a short code (e.g. T) used
          for command-style search/update.
        </Typography>
        <Paper variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Module</TableCell>
                <TableCell>Label</TableCell>
                <TableCell width={90}>Code</TableCell>
                <TableCell width={70}>Color</TableCell>
                <TableCell width={90}>Order</TableCell>
                <TableCell width={90}>Sync order</TableCell>
                <TableCell width={90}>Enabled</TableCell>
                <TableCell width={90}>Bucket /100</TableCell>
                <TableCell width={90}>Bucket by type</TableCell>
                <TableCell width={90} />
              </TableRow>
            </TableHead>
            <TableBody>
              {modules.map((m) => (
                <ModuleRow key={m.moduleKey} module={m} />
              ))}
            </TableBody>
          </Table>
        </Paper>

        {/* The parameter editors used to live here; they moved to their own page so the
            PARAMETERS_DATA_ENTRY role can reach them without tools.settings. */}
        <Alert severity="info" variant="outlined" sx={{ mt: 5, py: 0.5 }}>
          Status / type / context values, their colours and defaults now live on the{' '}
          <Link to="/parameters">Parameters</Link> page.
        </Alert>

        <RepositoriesSection />

        <ExportSection />

        <BookmarkSection />

        <TaskCompletionSection />
      </Box>
    </Box>
  );
}

/** App-level settings (name shown in the sidebar + browser tab). */
function AppSettingsSection() {
  const qc = useQueryClient();
  const { data } = useQuery({ queryKey: ['setting', 'app.name'], queryFn: () => getSetting('app.name', 'Nibras') });
  const [name, setName] = useState('');
  useEffect(() => {
    if (data != null) setName(data);
  }, [data]);
  const save = useMutation({
    mutationFn: () => setSetting('app.name', name.trim() || 'Nibras'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['setting', 'app.name'] }),
  });

  return (
    <Box sx={{ mb: 4 }}>
      <Typography variant="subtitle1" sx={{ mb: 1 }}>
        Application
      </Typography>
      <Stack direction="row" spacing={2} alignItems="center">
        <TextField
          size="small"
          label="App name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && !save.isPending && save.mutate()}
          sx={{ width: 260 }}
        />
        <Button variant="contained" size="small" disabled={save.isPending} onClick={() => save.mutate()}>
          Save
        </Button>
      </Stack>
    </Box>
  );
}

/**
 * Prefix/suffix wrapped around whatever is typed into the always-visible "append to description"
 * field in a record's detail pane (DetailPane.tsx). Stored as the literal two characters "\n" (not
 * an actual newline) so it stays editable in a plain text field; DetailPane interprets it.
 */
function DescriptionAppendSection() {
  const qc = useQueryClient();
  const { data } = useQuery({
    queryKey: ['setting', 'description.append'],
    queryFn: async () => ({
      prefix: await getSetting('description.append.prefix', '\\n\\n'),
      suffix: await getSetting('description.append.suffix', ''),
    }),
  });
  const [prefix, setPrefix] = useState('');
  const [suffix, setSuffix] = useState('');
  useEffect(() => {
    if (data) {
      setPrefix(data.prefix);
      setSuffix(data.suffix);
    }
  }, [data]);

  const save = useMutation({
    mutationFn: async () => {
      await setSetting('description.append.prefix', prefix);
      await setSetting('description.append.suffix', suffix);
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['setting', 'description.append'] }),
  });

  return (
    <Box sx={{ mb: 4 }}>
      <Typography variant="subtitle1" sx={{ mb: 1 }}>
        Append to description
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        Wrapped around whatever's typed into the always-visible "append" field under a record's
        description. Use <code>\n</code> for a newline — the default starts a new paragraph.
      </Typography>
      <Stack direction="row" spacing={2} alignItems="center">
        <TextField size="small" label="Prefix" value={prefix} onChange={(e) => setPrefix(e.target.value)} sx={{ width: 220 }} />
        <TextField size="small" label="Suffix" value={suffix} onChange={(e) => setSuffix(e.target.value)} sx={{ width: 220 }} />
        <Button variant="contained" size="small" disabled={save.isPending} onClick={() => save.mutate()}>
          Save
        </Button>
      </Stack>
    </Box>
  );
}

/** Show/hide the tool pages in the sidebar (Settings & Help are always available). */
function PagesSection() {
  const qc = useQueryClient();
  const { data } = useQuery({ queryKey: ['page-settings-admin'], queryFn: () => getSettingsMulti(PAGE_SETTING_KEYS) });
  const save = useMutation({
    mutationFn: ({ key, enabled }: { key: string; enabled: boolean }) =>
      setSetting(pageSettingKey(key), enabled ? 'true' : 'false'),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['page-settings-admin'] });
      qc.invalidateQueries({ queryKey: ['page-settings'] });
    },
  });

  return (
    <Box sx={{ mb: 4 }}>
      <Typography variant="subtitle1" sx={{ mb: 1 }}>
        Pages
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        Show or hide the tool pages in the sidebar. Settings &amp; Help are always available.
      </Typography>
      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(190px, 1fr))' }}>
        {TOGGLEABLE_PAGES.map((p) => (
          <FormControlLabel
            key={p.key}
            control={
              <Switch
                size="small"
                checked={(data?.[pageSettingKey(p.key)] ?? '') !== 'false'}
                onChange={(e) => save.mutate({ key: p.key, enabled: e.target.checked })}
              />
            }
            label={p.label}
          />
        ))}
      </Box>
    </Box>
  );
}

/**
 * Show/hide the features that aren't whole pages — sidebar sections rather than routes, so they
 * can't be expressed as a [TOGGLEABLE_PAGES] entry.
 */
function FeaturesSection() {
  const qc = useQueryClient();
  const { data } = useQuery({
    queryKey: ['feature-settings-admin'],
    queryFn: () => getSettingsMulti(FEATURE_SETTING_KEYS),
  });
  const save = useMutation({
    mutationFn: ({ key, enabled }: { key: string; enabled: boolean }) =>
      setSetting(featureSettingKey(key), enabled ? 'true' : 'false'),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['feature-settings-admin'] });
      qc.invalidateQueries({ queryKey: ['feature-settings'] });
    },
  });

  return (
    <Box sx={{ mb: 4 }}>
      <Typography variant="subtitle1" sx={{ mb: 1 }}>
        Features
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        Parts of the interface that aren&apos;t pages of their own.
      </Typography>
      <Stack>
        {TOGGLEABLE_FEATURES.map((f) => (
          <FormControlLabel
            key={f.key}
            control={
              <Switch
                size="small"
                checked={(data?.[featureSettingKey(f.key)] ?? '') !== 'false'}
                onChange={(e) => save.mutate({ key: f.key, enabled: e.target.checked })}
              />
            }
            label={
              <Box>
                <Typography variant="body2">{f.label}</Typography>
                <Typography variant="caption" color="text.secondary">
                  {f.description}
                </Typography>
              </Box>
            }
            sx={{ alignItems: 'flex-start', mb: 1 }}
          />
        ))}
      </Stack>
    </Box>
  );
}

/**
 * Desktop notifications: overdue/due-soon tasks & planners, and a daily "no journal entry yet"
 * reminder. The reminder hour is a server setting (same for every device); whether notifications
 * are on, and the browser permission itself, are inherently per-browser.
 */
function NotificationsSection() {
  const qc = useQueryClient();
  const { data } = useQuery({
    queryKey: ['setting', 'journal.reminder-hour'],
    queryFn: () => getSetting('journal.reminder-hour', '17'),
  });
  const [hour, setHour] = useState('');
  useEffect(() => {
    if (data != null) setHour(data);
  }, [data]);
  const save = useMutation({
    mutationFn: () => {
      const n = Math.min(23, Math.max(0, Number(hour) || 17));
      return setSetting('journal.reminder-hour', String(n));
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['setting', 'journal.reminder-hour'] }),
  });

  const { prefs, update } = useNotificationPrefs();
  const supported = typeof Notification !== 'undefined';
  const [permission, setPermission] = useState(supported ? Notification.permission : 'denied');

  const requestPermission = () => {
    if (!supported) return;
    Notification.requestPermission().then(setPermission);
  };

  return (
    <Box sx={{ mb: 4 }}>
      <Typography variant="subtitle1" sx={{ mb: 1 }}>
        Notifications
      </Typography>
      <Stack spacing={1.5} sx={{ maxWidth: 480 }}>
        {!supported && (
          <Alert severity="warning" sx={{ py: 0 }}>
            This browser doesn't support desktop notifications.
          </Alert>
        )}
        {supported && permission === 'denied' && (
          <Alert severity="warning" sx={{ py: 0 }}>
            Notifications are blocked for this site — allow them in the browser's site settings.
          </Alert>
        )}
        <Stack direction="row" spacing={2} alignItems="center">
          <FormControlLabel
            control={
              <Switch
                checked={prefs.enabled && permission === 'granted'}
                disabled={!supported}
                onChange={(e) => {
                  update({ enabled: e.target.checked });
                  if (e.target.checked && permission === 'default') requestPermission();
                }}
              />
            }
            label="Enable desktop notifications (overdue tasks/planners, missing journal entry)"
          />
        </Stack>
        <Stack direction="row" spacing={2} alignItems="center">
          <TextField
            size="small"
            type="number"
            label="Journal reminder hour"
            value={hour}
            onChange={(e) => setHour(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && !save.isPending && save.mutate()}
            helperText="24h, e.g. 17 = 5pm"
            inputProps={{ min: 0, max: 23 }}
            sx={{ width: 220 }}
          />
          <Button variant="contained" size="small" disabled={save.isPending} onClick={() => save.mutate()}>
            Save
          </Button>
        </Stack>
      </Stack>
    </Box>
  );
}

/** When "autoCollect"-flagged indicators are computed and saved automatically each day. */
function IndicatorAutoCollectSection() {
  const qc = useQueryClient();
  const { data } = useQuery({
    queryKey: ['setting', 'indicator.autoCollect.time'],
    queryFn: () => getSetting('indicator.autoCollect.time', '23:50'),
  });
  const [time, setTime] = useState('');
  useEffect(() => {
    if (data != null) setTime(data);
  }, [data]);
  const save = useMutation({
    mutationFn: () => setSetting('indicator.autoCollect.time', time || '23:50'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['setting', 'indicator.autoCollect.time'] }),
  });

  return (
    <Box sx={{ mb: 4 }}>
      <Typography variant="subtitle1" sx={{ mb: 1 }}>
        Indicator auto-collection
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5, maxWidth: 480 }}>
        Indicators with "Auto-collect" enabled (query/file-count/script) have their value computed
        and saved automatically, once a day, at this time — instead of waiting for manual
        confirmation on the Indicators page.
      </Typography>
      <Stack direction="row" spacing={2} alignItems="center">
        <TextField
          size="small"
          type="time"
          label="Run time"
          value={time}
          onChange={(e) => setTime(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && !save.isPending && save.mutate()}
          InputLabelProps={{ shrink: true }}
          sx={{ width: 220 }}
        />
        <Button variant="contained" size="small" disabled={save.isPending} onClick={() => save.mutate()}>
          Save
        </Button>
      </Stack>
    </Box>
  );
}

/** Toggle whether exporting a record's text opens the file (xdg-open) afterwards. */
function ExportSection() {
  const qc = useQueryClient();
  const { data } = useQuery({
    queryKey: ['setting', 'export.open'],
    queryFn: () => getSetting('export.open', 'true'),
  });
  const open = data !== 'false';

  const save = useMutation({
    mutationFn: (v: boolean) => setSetting('export.open', v ? 'true' : 'false'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['setting', 'export.open'] }),
  });

  // The calendar auto-exports a brand-new day journal's _record.md; this controls whether
  // that file is opened too — independent of the manual "Export to file" toggle above.
  const { data: calData } = useQuery({
    queryKey: ['setting', 'calendar.journalDay.open'],
    queryFn: () => getSetting('calendar.journalDay.open', 'false'),
  });
  const calOpen = calData === 'true';

  const saveCal = useMutation({
    mutationFn: (v: boolean) => setSetting('calendar.journalDay.open', v ? 'true' : 'false'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['setting', 'calendar.journalDay.open'] }),
  });

  return (
    <Box sx={{ mt: 5 }}>
      <Typography variant="subtitle1" sx={{ mb: 1 }}>
        Export
      </Typography>
      <Stack>
        <FormControlLabel
          control={<Switch checked={open} onChange={(e) => save.mutate(e.target.checked)} />}
          label="Open the file after “Export to file” (xdg-open)"
        />
        <FormControlLabel
          control={<Switch checked={calOpen} onChange={(e) => saveCal.mutate(e.target.checked)} />}
          label="Open the file after a new calendar-day journal is auto-exported"
        />
      </Stack>
    </Box>
  );
}


/**
 * What bookmarking a record should DO, beyond flagging it.
 *
 * Both are off by default and always have to be asked for. Bookmarking is a note to self — on its
 * own it has no business writing to the filesystem or launching a desktop app, and an editor window
 * appearing because you clicked a star is startling rather than helpful.
 *
 * Combo boxes rather than switches, because these two are unlike the visibility toggles above:
 * those hide bits of the interface, these make something happen on your disk. Having to pick "On"
 * from a list is a slightly deliberate act, which suits them.
 */
function BookmarkSection() {
  const qc = useQueryClient();

  // Two plain useQuery calls rather than a `readSetting(key)` helper: a helper that calls a hook is
  // a hook, and one that isn't named useSomething slips straight past the lint rule that keeps hook
  // order honest. Two keys don't justify the risk.
  const { data: linkValue } = useQuery({
    queryKey: ['setting', 'bookmark.symlink'],
    queryFn: () => getSetting('bookmark.symlink', 'false'),
  });
  const { data: openValue } = useQuery({
    queryKey: ['setting', 'bookmark.open-checkout'],
    queryFn: () => getSetting('bookmark.open-checkout', 'false'),
  });
  const link = linkValue === 'true';
  const open = openValue === 'true';

  const save = useMutation({
    mutationFn: ({ key, on }: { key: string; on: boolean }) => setSetting(key, on ? 'true' : 'false'),
    onSuccess: (_r, { key }) => qc.invalidateQueries({ queryKey: ['setting', key] }),
  });

  const combo = (key: string, value: boolean, label: string, help: string) => (
    <Stack direction="row" alignItems="flex-start" spacing={2} sx={{ mb: 1.5 }}>
      <TextField
        select
        size="small"
        value={value ? 'true' : 'false'}
        onChange={(e) => save.mutate({ key, on: e.target.value === 'true' })}
        sx={{ width: 96, flexShrink: 0 }}
      >
        <MenuItem value="false">Off</MenuItem>
        <MenuItem value="true">On</MenuItem>
      </TextField>
      <Box sx={{ pt: 0.5 }}>
        <Typography variant="body2">{label}</Typography>
        <Typography variant="caption" color="text.secondary">
          {help}
        </Typography>
      </Box>
    </Stack>
  );

  return (
    <Box sx={{ mt: 5 }}>
      <Typography variant="subtitle1" sx={{ mb: 1 }}>
        On bookmarking
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
        Extra things a bookmark can trigger. Both are off unless you turn them on; removing a
        bookmark always removes its symlink, whatever these say, so nothing is left stranded.
      </Typography>
      {combo(
        'bookmark.symlink',
        link,
        'Create a symbolic link in the checkout folder',
        'A link per bookmarked record, pointing at its repository-1 folder. Its checkout text is written at the same time, so the folder isn\u2019t empty.',
      )}
      {combo(
        'bookmark.open-checkout',
        open,
        'Open the record\u2019s checkout text',
        'Writes the record\u2019s _record.md and opens it (xdg-open). Independent of the Export setting above, which governs the manual \u201cExport to file\u201d button.',
      )}
    </Box>
  );
}


/**
 * Which status a task takes when its completion checkbox is ticked.
 *
 * Completing a task used to mean different things depending on where you did it: the mobile push
 * set the status by a hardcoded "done" code, while the web checkmark and the sprint review stamped
 * the completion date and left the status alone — so the same task read as finished on one screen
 * and open on another. Now all three apply whatever is chosen here.
 *
 * The list is the task form's own status list, so the choice can only ever be a status that
 * actually exists. "Leave unchanged" is a real option: an instance with no notion of "done" should
 * record the completion date and touch nothing else rather than have one invented for it.
 */
function TaskCompletionSection() {
  const qc = useQueryClient();
  const KEY = 'task.done-status-id';

  const { data: lookups } = useQuery({
    queryKey: ['lookups', 'tasks'],
    queryFn: () => getLookups('tasks'),
    staleTime: 300_000,
  });
  const { data: current } = useQuery({
    queryKey: ['setting', KEY],
    queryFn: () => getSetting(KEY, ''),
  });

  const save = useMutation({
    mutationFn: (id: string) => setSetting(KEY, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['setting', KEY] }),
  });

  const statuses = lookups?.statuses ?? [];

  return (
    <Box sx={{ mt: 5 }}>
      <Typography variant="subtitle1" sx={{ mb: 1 }}>
        Tasks
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
        The status a task moves to when you tick its completion checkbox — on the record, in a
        sprint review, or from the phone. A status you set yourself on the same save still wins.
      </Typography>
      <TimerTimeoutField />
      <LanguageCodesField />

      <TodayCountField />

      <TextField
        select
        size="small"
        label="Status on completion"
        value={current ?? ''}
        onChange={(e) => save.mutate(e.target.value)}
        sx={{ minWidth: 260 }}
      >
        <MenuItem value="">
          <em>Leave unchanged</em>
        </MenuItem>
        {statuses.map((s) => (
          <MenuItem key={s.id} value={String(s.id)}>
            {s.name}
            {s.code ? ` (${s.code})` : ''}
          </MenuItem>
        ))}
      </TextField>
    </Box>
  );
}

/**
 * How long a time-tracking session may run before it stops itself.
 *
 * There has always been a 60-minute cut-off, but it lived in a per-browser localStorage preference
 * with no UI at all — a hidden default nobody could change. It is a server setting now, so the
 * answer is the same on every machine, and blank still means 60.
 */
function TimerTimeoutField() {
  const qc = useQueryClient();
  const KEY = 'timer.max-slot-minutes';
  const { data } = useQuery({
    queryKey: ['setting', KEY],
    queryFn: () => getSetting(KEY, String(DEFAULT_MAX_SLOT_MINUTES)),
  });
  const [value, setValue] = useState('');
  useEffect(() => {
    if (data != null) setValue(data);
  }, [data]);

  const save = useMutation({
    mutationFn: (v: string) => setSetting(KEY, v),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['setting', KEY] }),
  });

  return (
    <Box sx={{ mb: 2 }}>
      <TextField
        size="small"
        type="number"
        label="Auto-stop a timer after (minutes)"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onBlur={() => value !== data && save.mutate(value)}
        onKeyDown={(e) => e.key === 'Enter' && save.mutate(value)}
        inputProps={{ min: 1 }}
        sx={{ width: 260 }}
        helperText={`Blank or invalid falls back to ${DEFAULT_MAX_SLOT_MINUTES}. The session is saved ending at the cut-off, not when the browser noticed.`}
      />
    </Box>
  );
}

/**
 * How many records each column of the Today page lists.
 *
 * A server setting rather than a per-browser one: it is a statement about how much of a summary is
 * useful, not about this machine, and the page is the first thing seen on every device.
 */
function TodayCountField() {
  const qc = useQueryClient();
  const KEY = TODAY_COUNT_KEY;
  const { data } = useQuery({
    queryKey: ['setting', KEY],
    queryFn: () => getSetting(KEY, String(DEFAULT_TODAY_COUNT)),
  });
  const [value, setValue] = useState('');
  useEffect(() => {
    if (data != null) setValue(data);
  }, [data]);

  const save = useMutation({
    mutationFn: (v: string) => setSetting(KEY, v),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['setting', KEY] });
      qc.invalidateQueries({ queryKey: ['today'] });
    },
  });

  return (
    <Box sx={{ mb: 2 }}>
      <TextField
        size="small"
        type="number"
        label="Today page: records per column"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onBlur={() => value !== data && save.mutate(value)}
        onKeyDown={(e) => e.key === 'Enter' && save.mutate(value)}
        inputProps={{ min: 1, max: 50 }}
        sx={{ width: 300 }}
        helperText={`How many newest-created and newest-modified records each module shows on the home page. Blank or invalid falls back to ${DEFAULT_TODAY_COUNT}; capped at 50.`}
      />
    </Box>
  );
}

/**
 * The language codes the `'code` tag accepts.
 *
 * A whitelist rather than "any letters after an apostrophe": an unrecognised tag is far more likely
 * to be a quoted word than a language nobody has configured, and accepting it would silently drop
 * that word from a title or a search. Only what is listed here is treated as a language.
 */
function LanguageCodesField() {
  const qc = useQueryClient();
  const KEY = 'language.codes';
  const DEFAULT = 'ar,en,fr,fa,de';
  const { data } = useQuery({
    queryKey: ['setting', KEY],
    queryFn: () => getSetting(KEY, DEFAULT),
  });
  const [value, setValue] = useState('');
  useEffect(() => {
    if (data != null) setValue(data);
  }, [data]);

  const save = useMutation({
    mutationFn: (v: string) => setSetting(KEY, v),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['setting', KEY] }),
  });

  return (
    <Box sx={{ mb: 2 }}>
      <TextField
        size="small"
        label="Language codes"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onBlur={() => value !== data && save.mutate(value)}
        onKeyDown={(e) => e.key === 'Enter' && save.mutate(value)}
        sx={{ width: 380 }}
        helperText={`Comma-separated. Typing an apostrophe and one of these — 'ar, 'en — sets a record's language when editing or importing, and filters by it when searching. Anything not listed stays ordinary text. Default: ${DEFAULT}.`}
      />
    </Box>
  );
}

/** Edit the three repository root paths (1=sandbox, 2=main, 3=dismissed) + the file-explorer command. */
function RepositoriesSection() {
  const qc = useQueryClient();
  const { data } = useQuery({ queryKey: ['repo-settings'], queryFn: getRepoSettings });
  const [form, setForm] = useState<RepoSettings>({
    repo1: '',
    repo2: '',
    repo3: '',
    checkout: '',
    explorer: '',
  });
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (data) setForm(data);
  }, [data]);

  const save = useMutation({
    mutationFn: () => updateRepoSettings(form),
    onSuccess: (res) => {
      setForm(res);
      setSaved(true);
      qc.invalidateQueries({ queryKey: ['repo-settings'] });
    },
  });

  const field = (key: keyof RepoSettings, label: string, help?: string) => (
    <TextField
      size="small"
      fullWidth
      label={label}
      helperText={help}
      value={form[key]}
      onChange={(e) => {
        setForm((f) => ({ ...f, [key]: e.target.value }));
        setSaved(false);
      }}
      onKeyDown={(e) => e.key === 'Enter' && !save.isPending && save.mutate()}
      inputProps={{ style: { fontFamily: 'monospace' } }}
    />
  );

  return (
    <Box sx={{ mt: 5 }}>
      <Typography variant="subtitle1" sx={{ mb: 1 }}>
        Repositories
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Root folders for record files. Each repo shares the layout{' '}
        <code>&lt;repo&gt;/&lt;code&gt;/[type]/[id÷100]/&lt;id&gt;</code> (the per-module “Bucket” toggles
        above). Used by the “Open folder” actions and the daily-journal links. The checkout folder
        holds a symlink per bookmarked record (named <code>&lt;code&gt;&lt;id&gt;</code>, e.g.{' '}
        <code>W18</code>) pointing at its Repo 1 folder — added when a record is bookmarked, removed
        when it's unbookmarked or deleted.
      </Typography>
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack spacing={2} sx={{ maxWidth: 560 }}>
          {field('repo1', 'Repo 1 — sandbox / portable', 'e.g. /home/akil/rps1')}
          {field('repo2', 'Repo 2 — main')}
          {field('repo3', 'Repo 3 — dismissed')}
          {field('checkout', 'Checkout folder — bookmark symlinks', 'e.g. /home/akil/checkout')}
          {field('explorer', 'File-explorer command', 'e.g. xdg-open or "nemo --existing-window"')}
          <Stack direction="row" spacing={2} alignItems="center">
            <Button variant="contained" onClick={() => save.mutate()} disabled={save.isPending}>
              Save
            </Button>
            {saved && (
              <Alert severity="success" sx={{ py: 0 }}>
                Saved.
              </Alert>
            )}
          </Stack>
        </Stack>
      </Paper>
    </Box>
  );
}

function ModuleRow({ module }: { module: AppModule }) {
  const qc = useQueryClient();
  const [label, setLabel] = useState(module.label);
  const [code, setCode] = useState(module.code ?? '');
  const [color, setColor] = useState(module.color ?? '#9e9e9e');
  const [order, setOrder] = useState(module.sortOrder);
  const [syncOrder, setSyncOrder] = useState(module.syncOrder ?? 0);
  const [error, setError] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: (body: Partial<AppModule>) => updateModule(module.moduleKey, body),
    onSuccess: () => {
      setError(null);
      qc.invalidateQueries({ queryKey: ['modules'] });
    },
    onError: (e: unknown) => setError(errorDetail(e, 'Save failed.')),
  });

  const dirty =
    label !== module.label ||
    (code || '') !== (module.code ?? '') ||
    order !== module.sortOrder ||
    syncOrder !== (module.syncOrder ?? 0);
  const saveRow = () => dirty && !save.isPending && save.mutate({ label, code, sortOrder: order, syncOrder });

  return (
    <TableRow>
      <TableCell>
        <code>{module.moduleKey}</code>
      </TableCell>
      <TableCell>
        <TextField
          size="small"
          variant="standard"
          value={label}
          onChange={(e) => setLabel(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && saveRow()}
        />
      </TableCell>
      <TableCell>
        <TextField
          size="small"
          variant="standard"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase().slice(0, 4))}
          onKeyDown={(e) => e.key === 'Enter' && saveRow()}
          inputProps={{ style: { fontFamily: 'monospace', textAlign: 'center' } }}
        />
        {error && (
          <Alert severity="error" sx={{ mt: 0.5, py: 0, fontSize: 12 }}>
            {error}
          </Alert>
        )}
      </TableCell>
      <TableCell>
        <input
          type="color"
          value={color}
          onChange={(e) => setColor(e.target.value)}
          onBlur={() => color !== (module.color ?? '') && save.mutate({ color })}
          title="Module accent colour (saved on change)"
          style={{
            width: 36,
            height: 26,
            border: 'none',
            background: 'none',
            cursor: 'pointer',
            padding: 0,
          }}
        />
      </TableCell>
      <TableCell>
        <TextField
          size="small"
          variant="standard"
          type="number"
          value={order}
          onChange={(e) => setOrder(Number(e.target.value))}
          onKeyDown={(e) => e.key === 'Enter' && saveRow()}
          sx={{ width: 60 }}
        />
      </TableCell>
      <TableCell>
        <TextField
          size="small"
          variant="standard"
          type="number"
          value={syncOrder}
          onChange={(e) => setSyncOrder(Number(e.target.value))}
          onKeyDown={(e) => e.key === 'Enter' && saveRow()}
          sx={{ width: 60 }}
        />
      </TableCell>
      <TableCell>
        <Switch
          checked={module.enabled}
          onChange={(e) => save.mutate({ enabled: e.target.checked })}
        />
      </TableCell>
      <TableCell>
        <Switch
          checked={module.bucketBy100 ?? false}
          onChange={(e) => save.mutate({ bucketBy100: e.target.checked })}
        />
      </TableCell>
      <TableCell>
        <Switch
          checked={module.bucketByType ?? false}
          onChange={(e) => save.mutate({ bucketByType: e.target.checked })}
        />
      </TableCell>
      <TableCell>
        <Button
          size="small"
          disabled={!dirty || save.isPending}
          onClick={() => save.mutate({ label, code, sortOrder: order, syncOrder })}
        >
          Save
        </Button>
      </TableCell>
    </TableRow>
  );
}
