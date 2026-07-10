import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  FormControlLabel,
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
import {
  AppModule,
  LookupValue,
  RepoSettings,
  fillLookupColors,
  getLookupGroups,
  getRepoSettings,
  getSetting,
  getSettingsMulti,
  setLookupDefault,
  setLookupStyle,
  setSetting,
  updateModule,
  updateRepoSettings,
} from '../api/client';
import { SideNav } from '../components/SideNav';
import { useModules } from '../hooks/useModules';
import { lookupStyle } from '../components/statusStyle';
import { PAGE_SETTING_KEYS, TOGGLEABLE_PAGES, pageSettingKey } from '../config/pages';

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

        <LookupColorsSection />

        <RepositoriesSection />

        <ExportSection />
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
          sx={{ width: 260 }}
        />
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

/** Edit the chip colour of each parametric lookup value (status / type / department / context). */
function LookupColorsSection() {
  const qc = useQueryClient();
  const { data } = useQuery({ queryKey: ['lookup-groups'], queryFn: getLookupGroups });
  const groups = data ?? [];

  const fill = useMutation({
    mutationFn: fillLookupColors,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['lookup-groups'] }),
  });

  return (
    <Box sx={{ mt: 5 }}>
      <Stack direction="row" alignItems="center" spacing={2} sx={{ mb: 1 }}>
        <Typography variant="subtitle1" sx={{ flex: 1 }}>
          Parameter colours
        </Typography>
        <Button
          variant="outlined"
          size="small"
          disabled={fill.isPending}
          onClick={() => fill.mutate()}
        >
          Fill empty colours
        </Button>
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        The chip colour shown for each status / type / department / context on record cards. Edit
        a colour below, or “Fill empty colours” to give every uncoloured value a default — the same
        fill can be re-run on a fresh database. Click the ★ to make a value the parameter’s
        <strong> default</strong> (marked ★): new records left blank get it, even when the column is
        required.
      </Typography>
      {fill.isSuccess && (
        <Alert severity="success" sx={{ mb: 2, py: 0 }}>
          Filled {fill.data} value(s).
        </Alert>
      )}

      <Stack spacing={2}>
        {groups.map((g) => (
          <Box key={g.table}>
            <Typography variant="overline" color="text.secondary">
              {g.label}
            </Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 0.5 }}>
              {g.values.map((v) => (
                <LookupChip
                  key={v.id}
                  table={g.table}
                  value={v}
                  isDefault={g.defaultId === v.id}
                />
              ))}
              {g.values.length === 0 && (
                <Typography variant="caption" color="text.secondary">
                  (none)
                </Typography>
              )}
            </Stack>
          </Box>
        ))}
      </Stack>
    </Box>
  );
}

/** A lookup value shown as its coloured chip with an inline colour picker + default (★) toggle. */
function LookupChip({
  table,
  value,
  isDefault,
}: {
  table: string;
  value: LookupValue;
  isDefault: boolean;
}) {
  const qc = useQueryClient();
  const style = lookupStyle(value);
  const save = useMutation({
    mutationFn: (hex: string) => setLookupStyle(table, value.id, `background-color:${hex}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['lookup-groups'] }),
  });
  const setDefault = useMutation({
    // Toggle: starring the current default clears it; starring another moves the default.
    mutationFn: () => setLookupDefault(table, isDefault ? null : value.id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['lookup-groups'] }),
  });

  return (
    <Box
      component="label"
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 0.5,
        px: 1,
        py: 0.25,
        borderRadius: 4,
        cursor: 'pointer',
        fontSize: 13,
        ...style,
      }}
      title="Click the colour swatch to recolour; ★ to set as default"
    >
      <Box
        component="span"
        onClick={(e) => {
          e.preventDefault();
          setDefault.mutate();
        }}
        title={isDefault ? 'Default — click to clear' : 'Set as default for blank records'}
        sx={{ cursor: 'pointer', opacity: isDefault ? 1 : 0.4, lineHeight: 1 }}
      >
        {isDefault ? '★' : '☆'}
      </Box>
      {value.name}
      <input
        type="color"
        value={style.backgroundColor && /^#/.test(style.backgroundColor) ? style.backgroundColor : '#cccccc'}
        onChange={(e) => save.mutate(e.target.value)}
        style={{ width: 18, height: 18, border: 'none', background: 'none', padding: 0, cursor: 'pointer' }}
      />
    </Box>
  );
}

/** Edit the three repository root paths (1=sandbox, 2=main, 3=dismissed) + the file-explorer command. */
function RepositoriesSection() {
  const qc = useQueryClient();
  const { data } = useQuery({ queryKey: ['repo-settings'], queryFn: getRepoSettings });
  const [form, setForm] = useState<RepoSettings>({ repo1: '', repo2: '', repo3: '', explorer: '' });
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
        above). Used by the “Open folder” actions and the daily-journal links.
      </Typography>
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack spacing={2} sx={{ maxWidth: 560 }}>
          {field('repo1', 'Repo 1 — sandbox / portable', 'e.g. /home/akil/rps1')}
          {field('repo2', 'Repo 2 — main')}
          {field('repo3', 'Repo 3 — dismissed')}
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
  const [error, setError] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: (body: Partial<AppModule>) => updateModule(module.moduleKey, body),
    onSuccess: () => {
      setError(null);
      qc.invalidateQueries({ queryKey: ['modules'] });
    },
    onError: (e: unknown) => {
      const msg =
        (e as { response?: { data?: { detail?: string } } })?.response?.data?.detail ??
        'Save failed';
      setError(msg);
    },
  });

  const dirty = label !== module.label || (code || '') !== (module.code ?? '') || order !== module.sortOrder;

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
        />
      </TableCell>
      <TableCell>
        <TextField
          size="small"
          variant="standard"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase().slice(0, 4))}
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
          onClick={() => save.mutate({ label, code, sortOrder: order })}
        >
          Save
        </Button>
      </TableCell>
    </TableRow>
  );
}
