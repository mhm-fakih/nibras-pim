import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Box,
  Button,
  Checkbox,
  Chip,
  Divider,
  FormControlLabel,
  IconButton,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Paper,
  Stack,
  Switch,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
import Add from '@mui/icons-material/Add';
import ArrowDownward from '@mui/icons-material/ArrowDownward';
import ArrowUpward from '@mui/icons-material/ArrowUpward';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import Edit from '@mui/icons-material/Edit';
import PlayArrowOutlined from '@mui/icons-material/PlayArrowOutlined';
import PrintOutlined from '@mui/icons-material/Print';
import {
  BASE,
  ListParams,
  Lookup,
  Report,
  createReport,
  deleteReport,
  getLookups,
  getReport,
  listReports,
  previewReport,
  runReport,
  updateReport,
} from '../api/client';
import { SideNav } from '../components/SideNav';
import { ReportTable, columnLabel, reportColumns, sortableColumns } from '../components/ReportTable';
import { ENTITIES, ENTITY_BY_KEY } from '../config/entities';
import { reportsFor } from '../config/reports';

/**
 * Module slug → the mapped class an HQL query names. Not derivable on the client: `resources` is
 * the `Book` entity and `notes` is `IndexCard`, both legacy names the API deliberately hides. Shown
 * as a hint so a query can be written without guessing, and left out for a module the query runner
 * doesn't recognise rather than guessed at.
 */
const ENTITY_CLASS: Record<string, string> = {
  resources: 'Book',
  notes: 'IndexCard',
  tasks: 'Task',
  journals: 'Journal',
  planners: 'Planner',
  goals: 'Goal',
  writings: 'Writing',
};

const queryExample = (moduleKey: string) =>
  moduleKey === 'resources'
    ? "from Book where type.code = 'art' order by id desc"
    : `from ${ENTITY_CLASS[moduleKey] ?? '?'} order by id desc`;

const BLANK: Report = {
  name: '',
  moduleKey: 'resources',
  columns: ['cover', 'title', 'author', 'type', 'status'],
  filters: {},
  sortField: 'dateCreated',
  sortDesc: true,
  pageSize: 50,
};

export function ReportsPage() {
  const qc = useQueryClient();
  const { data: reports } = useQuery({ queryKey: ['reports'], queryFn: () => listReports() });
  const [editingId, setEditingId] = useState<number | 'new' | null>(null);
  const [runningId, setRunningId] = useState<number | null>(null);

  const remove = useMutation({
    mutationFn: (id: number) => deleteReport(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['reports'] }),
  });

  const done = () => {
    setEditingId(null);
    qc.invalidateQueries({ queryKey: ['reports'] });
  };

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: 4 }}>
        <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }}>
          Reports
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3, maxWidth: 780 }}>
          Pick a record type, tick the columns you want to see, then say which records appear —
          either by ticking filters, or by writing the query yourself
          (<code>from Book where type.code = 'art' order by id desc</code>), in which case the
          query's own <code>order by</code> is the sort. Values show their full name, so a
          resource's type reads “Article”.
        </Typography>

        {editingId == null && runningId == null && (
          <>
            <Button variant="contained" startIcon={<Add />} onClick={() => setEditingId('new')} sx={{ mb: 2 }}>
              New report
            </Button>
            <Paper variant="outlined" sx={{ maxWidth: 900 }}>
              <List disablePadding>
                {(reports ?? []).map((r) => (
                  <ListItem
                    key={r.id}
                    divider
                    secondaryAction={
                      <Stack direction="row" spacing={0.5}>
                        <Tooltip title="Run">
                          <IconButton onClick={() => setRunningId(r.id!)}>
                            <PlayArrowOutlined fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Printable view (opens in a new tab)">
                          <IconButton
                            component="a"
                            href={`${BASE}reports/${r.id}/print`}
                            target="_blank"
                            rel="noopener noreferrer"
                          >
                            <PrintOutlined fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Edit">
                          <IconButton onClick={() => setEditingId(r.id!)}>
                            <Edit fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Delete">
                          <IconButton
                            onClick={() => window.confirm(`Delete "${r.name}"?`) && remove.mutate(r.id!)}
                          >
                            <DeleteOutline fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      </Stack>
                    }
                  >
                    <ListItemText
                      primary={r.name}
                      secondary={`${ENTITY_BY_KEY[r.moduleKey]?.label ?? r.moduleKey} · ${
                        r.columns.length
                      } column(s) · ${describeSource(r)}`}
                    />
                  </ListItem>
                ))}
                {reports && reports.length === 0 && (
                  <ListItem>
                    <ListItemText secondary="No reports yet." />
                  </ListItem>
                )}
              </List>
            </Paper>
          </>
        )}

        {editingId != null && (
          <ReportEditor id={editingId === 'new' ? null : editingId} onDone={done} />
        )}

        {runningId != null && <ReportRunner id={runningId} onClose={() => setRunningId(null)} />}
      </Box>
    </Box>
  );
}

/** Plain-language summary of what a report selects, for the list row. */
function describeSource(report: Report): string {
  if (report.query) return report.query;
  return describeFilters(report.moduleKey, report.filters);
}

function describeFilters(moduleKey: string, filters: ListParams): string {
  const parts: string[] = [];
  if (filters.q) parts.push(`“${filters.q}”`);
  if (filters.statusId) parts.push('one status');
  if (filters.typeId) parts.push('one type');
  if (filters.tagId) parts.push('one tag');
  if (filters.from || filters.to) parts.push('a date range');
  for (const preset of reportsFor(moduleKey)) {
    if ((filters as Record<string, unknown>)[preset.key]) parts.push(preset.label);
  }
  return parts.length ? parts.join(', ') : 'all records';
}

// ---------------------------------------------------------------- editor

function ReportEditor({ id, onDone }: { id: number | null; onDone: () => void }) {
  const { data } = useQuery({
    queryKey: ['report', id],
    queryFn: () => getReport(id!),
    enabled: id != null,
  });
  const [form, setForm] = useState<Report>(BLANK);
  const [queryMode, setQueryMode] = useState(false);

  useEffect(() => {
    if (data) {
      setForm(data);
      setQueryMode(Boolean(data.query));
    }
  }, [data]);

  const save = useMutation({
    mutationFn: () => (id == null ? createReport(form) : updateReport(id, form)),
    onSuccess: onDone,
  });

  const patch = (p: Partial<Report>) => setForm((f) => ({ ...f, ...p }));

  /**
   * Leaving query mode clears the query. Keeping it would be worse than losing it: the editor would
   * show filters while the saved report still ran the query, and the two would disagree silently.
   */
  const chooseMode = (wantsQuery: boolean) => {
    setQueryMode(wantsQuery);
    if (!wantsQuery) patch({ query: null });
  };


  /** Switching module invalidates every column and filter — they belong to the old module. */
  const changeModule = (moduleKey: string) =>
    setForm((f) => ({ ...f, moduleKey, columns: [], filters: {}, sortField: 'dateCreated' }));

  const toggleColumn = (name: string) =>
    setForm((f) => ({
      ...f,
      columns: f.columns.includes(name)
        ? f.columns.filter((c) => c !== name)
        : [...f.columns, name],
    }));

  const moveColumn = (index: number, dir: -1 | 1) =>
    setForm((f) => {
      const next = [...f.columns];
      const target = index + dir;
      if (target < 0 || target >= next.length) return f;
      [next[index], next[target]] = [next[target], next[index]];
      return { ...f, columns: next };
    });

  const available = reportColumns(form.moduleKey);

  return (
    <Box sx={{ maxWidth: 1000 }}>
      <Stack direction="row" spacing={2} sx={{ mb: 3 }} flexWrap="wrap" useFlexGap>
        <TextField
          label="Report name"
          size="small"
          value={form.name}
          onChange={(e) => patch({ name: e.target.value })}
          sx={{ minWidth: 260 }}
        />
        <TextField
          select
          label="Record type"
          size="small"
          value={form.moduleKey}
          onChange={(e) => changeModule(e.target.value)}
          helperText="Changing this clears the columns and filters"
          sx={{ minWidth: 200 }}
        >
          {ENTITIES.map((e) => (
            <MenuItem key={e.key} value={e.key}>
              {e.label}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          label="Rows per page"
          type="number"
          size="small"
          value={form.pageSize ?? 50}
          onChange={(e) => patch({ pageSize: Number(e.target.value) || 50 })}
          inputProps={{ min: 1, max: 500 }}
          sx={{ width: 140 }}
        />
      </Stack>

      <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
        Columns
      </Typography>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 3 }}>
        <Paper variant="outlined" sx={{ p: 2, flex: 1, maxHeight: 340, overflow: 'auto' }}>
          <Typography variant="caption" color="text.secondary">
            Tick what to show
          </Typography>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
            {available.map((c) => (
              <FormControlLabel
                key={c.name}
                control={
                  <Checkbox
                    size="small"
                    checked={form.columns.includes(c.name)}
                    onChange={() => toggleColumn(c.name)}
                  />
                }
                label={<Typography variant="body2">{c.label}</Typography>}
              />
            ))}
          </Box>
        </Paper>
        <Paper variant="outlined" sx={{ p: 2, flex: 1, maxHeight: 340, overflow: 'auto' }}>
          <Typography variant="caption" color="text.secondary">
            Order, left to right
          </Typography>
          <List dense disablePadding>
            {form.columns.map((name, i) => (
              <ListItem
                key={name}
                disableGutters
                secondaryAction={
                  <Stack direction="row">
                    <IconButton size="small" disabled={i === 0} onClick={() => moveColumn(i, -1)}>
                      <ArrowUpward fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      disabled={i === form.columns.length - 1}
                      onClick={() => moveColumn(i, 1)}
                    >
                      <ArrowDownward fontSize="small" />
                    </IconButton>
                    <IconButton size="small" onClick={() => toggleColumn(name)}>
                      <DeleteOutline fontSize="small" />
                    </IconButton>
                  </Stack>
                }
              >
                <ListItemText
                  primary={
                    <Typography variant="body2">
                      {i + 1}. {columnLabel(form.moduleKey, name)}
                      {name === 'cover' && (
                        <Chip label="image" size="small" sx={{ ml: 1, height: 18 }} />
                      )}
                    </Typography>
                  }
                />
              </ListItem>
            ))}
            {form.columns.length === 0 && (
              <ListItem disableGutters>
                <ListItemText secondary="Nothing chosen — the report would be empty." />
              </ListItem>
            )}
          </List>
        </Paper>
      </Stack>

      <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 1 }} flexWrap="wrap" useFlexGap>
        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
          Which records
        </Typography>
        <ToggleButtonGroup
          size="small"
          exclusive
          value={queryMode ? 'query' : 'filters'}
          onChange={(_, mode) => mode && chooseMode(mode === 'query')}
        >
          <ToggleButton value="filters">Point and click</ToggleButton>
          <ToggleButton value="query">Query</ToggleButton>
        </ToggleButtonGroup>
      </Stack>

      {queryMode ? (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <TextField
            label="Query (HQL)"
            fullWidth
            multiline
            minRows={2}
            maxRows={10}
            value={form.query ?? ''}
            onChange={(e) => patch({ query: e.target.value })}
            placeholder={queryExample(form.moduleKey)}
            helperText={
              <>
                Names the records and their order — e.g. <code>{queryExample(form.moduleKey)}</code>.
                {ENTITY_CLASS[form.moduleKey]
                  ? ` ${ENTITY_BY_KEY[form.moduleKey]?.label} is the ${ENTITY_CLASS[form.moduleKey]} entity.`
                  : ''}{' '}
                Must start with <code>from</code> and return whole records; a report only reads. The
                preview below runs it as you type.
              </>
            }
          />
        </Paper>
      ) : (
        <FilterBuilder
          moduleKey={form.moduleKey}
          value={form.filters}
          onChange={(filters) => patch({ filters })}
        />
      )}

      <Typography variant="subtitle2" sx={{ fontWeight: 700, mt: 3, mb: 1 }}>
        Order
      </Typography>
      {queryMode ? (
        <Typography variant="body2" color="text.secondary">
          Set by the query's own <code>order by</code>.
        </Typography>
      ) : (
        <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap" useFlexGap>
          <TextField
            select
            size="small"
            label="Sort by"
            value={form.sortField ?? 'dateCreated'}
            onChange={(e) => patch({ sortField: e.target.value })}
            sx={{ minWidth: 220 }}
          >
            {sortableColumns(form.moduleKey).map((c) => (
              <MenuItem key={c.name} value={c.name}>
                {c.label}
              </MenuItem>
            ))}
          </TextField>
          <FormControlLabel
            control={
              <Switch
                checked={form.sortDesc !== false}
                onChange={(e) => patch({ sortDesc: e.target.checked })}
              />
            }
            label={form.sortDesc !== false ? 'Newest / highest first' : 'Oldest / lowest first'}
          />
        </Stack>
      )}

      <TextField
        label="Notes (optional)"
        size="small"
        fullWidth
        multiline
        minRows={2}
        value={form.notes ?? ''}
        onChange={(e) => patch({ notes: e.target.value })}
        sx={{ mt: 3, maxWidth: 600 }}
      />

      <Divider sx={{ my: 3 }} />
      <ReportPreview form={form} />

      <Stack direction="row" spacing={1} sx={{ mt: 3 }}>
        <Button
          variant="contained"
          disabled={
            !form.name.trim() ||
            form.columns.length === 0 ||
            (queryMode && !(form.query ?? '').trim()) ||
            save.isPending
          }
          onClick={() => save.mutate()}
        >
          Save
        </Button>
        <Button onClick={onDone}>Cancel</Button>
      </Stack>
      {save.isError && (
        <Typography color="error" variant="body2" sx={{ mt: 1 }}>
          Save failed.
        </Typography>
      )}
    </Box>
  );
}

/**
 * The filters, as things you tick rather than a query.
 *
 * <p>Deliberately the same set the module list already offers — a report is a saved list view, so
 * anything you can narrow a list by, you can narrow a report by, and it behaves identically because
 * the server runs it through the same list code.
 */
function FilterBuilder({
  moduleKey,
  value,
  onChange,
}: {
  moduleKey: string;
  value: ListParams;
  onChange: (v: ListParams) => void;
}) {
  const { data: lookups } = useQuery({
    queryKey: ['lookups', moduleKey],
    queryFn: () => getLookups(moduleKey),
  });
  const config = ENTITY_BY_KEY[moduleKey];
  const set = (patch: ListParams) => {
    const next = { ...value, ...patch };
    // Unset keys are dropped rather than stored as null, so a saved definition stays readable and
    // "no filter" never becomes "filter by nothing".
    for (const k of Object.keys(next) as (keyof ListParams)[]) {
      if (next[k] === undefined || next[k] === '' || next[k] === false || next[k] === null) {
        delete next[k];
      }
    }
    onChange(next);
  };

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap sx={{ mb: 1 }}>
        <TextField
          label="Contains text"
          size="small"
          value={value.q ?? ''}
          onChange={(e) => set({ q: e.target.value })}
          helperText="Searches the module's text fields"
          sx={{ minWidth: 220 }}
        />
        {config?.hasStatus && (
          <LookupFilter
            label="Status"
            options={lookups?.statuses}
            value={value.statusId}
            onChange={(statusId) => set({ statusId })}
          />
        )}
        {config?.hasType && (
          <LookupFilter
            label="Type"
            options={lookups?.types}
            value={value.typeId}
            onChange={(typeId) => set({ typeId })}
          />
        )}
        <LookupFilter
          label="Tag"
          options={lookups?.tags}
          value={value.tagId}
          onChange={(tagId) => set({ tagId })}
        />
        <TextField
          label="Created from"
          type="date"
          size="small"
          InputLabelProps={{ shrink: true }}
          value={value.from ?? ''}
          onChange={(e) => set({ from: e.target.value })}
          sx={{ width: 170 }}
        />
        <TextField
          label="Created to"
          type="date"
          size="small"
          InputLabelProps={{ shrink: true }}
          value={value.to ?? ''}
          onChange={(e) => set({ to: e.target.value })}
          sx={{ width: 170 }}
        />
      </Stack>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', md: '1fr 1fr 1fr' } }}>
        {reportsFor(moduleKey).map((r) => (
          <FormControlLabel
            key={r.key}
            control={
              <Checkbox
                size="small"
                checked={Boolean((value as Record<string, unknown>)[r.key])}
                onChange={(e) => set({ [r.key]: e.target.checked } as ListParams)}
              />
            }
            label={<Typography variant="body2">{r.label}</Typography>}
          />
        ))}
      </Box>
    </Paper>
  );
}

function LookupFilter({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options?: Lookup[];
  value?: number;
  onChange: (v: number | undefined) => void;
}) {
  return (
    <TextField
      select
      size="small"
      label={label}
      value={value ?? ''}
      onChange={(e) => onChange(e.target.value === '' ? undefined : Number(e.target.value))}
      sx={{ minWidth: 170 }}
    >
      <MenuItem value="">Any</MenuItem>
      {(options ?? []).map((o) => (
        <MenuItem key={o.id} value={o.id}>
          {o.name}
        </MenuItem>
      ))}
    </TextField>
  );
}

/**
 * Live preview while editing.
 *
 * <p>Runs the definition being edited through the module's own list endpoint — the same route the
 * saved report takes on the server — so what you see here is what the report will show, including
 * an unsaved change you just made.
 */
function ReportPreview({ form }: { form: Report }) {
  // The definition minus the parts that don't change which rows come back, so retyping the name or
  // reordering columns doesn't re-run the query.
  const definition = useMemo(
    () => ({
      name: 'preview',
      moduleKey: form.moduleKey,
      columns: [],
      filters: form.query ? {} : form.filters,
      query: form.query,
      sortField: form.sortField,
      sortDesc: form.sortDesc,
    }),
    [form.moduleKey, form.filters, form.query, form.sortField, form.sortDesc],
  );
  const runnable = !form.query || form.query.trim().length > 0;
  const { data, error } = useQuery({
    queryKey: ['report-preview', definition],
    queryFn: () => previewReport(definition as Report, 5),
    enabled: runnable,
    retry: false,
    placeholderData: keepPreviousData,
  });

  return (
    <Box>
      <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
        Preview{data ? ` — ${data.totalElements} record(s) match, first 5 shown` : ''}
      </Typography>
      {error ? (
        <Typography variant="body2" color="error" sx={{ whiteSpace: 'pre-wrap' }}>
          {problemDetail(error)}
        </Typography>
      ) : (
        <Paper variant="outlined">
          <ReportTable moduleKey={form.moduleKey} columns={form.columns} rows={data?.content ?? []} />
        </Paper>
      )}
    </Box>
  );
}

/** The server's RFC-7807 `detail` — the message written for whoever typed the query. */
function problemDetail(error: unknown): string {
  const detail = (error as { response?: { data?: { detail?: string } } })?.response?.data?.detail;
  return detail ?? 'Could not run this report.';
}

// ---------------------------------------------------------------- runner

function ReportRunner({ id, onClose }: { id: number; onClose: () => void }) {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const { data: report } = useQuery({ queryKey: ['report', id], queryFn: () => getReport(id) });
  const { data, isFetching } = useQuery({
    queryKey: ['report-run', id, page],
    queryFn: () => runReport(id, page),
    placeholderData: keepPreviousData,
  });

  useEffect(() => setPage(0), [id]);

  if (!report) {
    return null;
  }

  return (
    <Box>
      <Stack direction="row" spacing={2} alignItems="baseline" sx={{ mb: 2 }} flexWrap="wrap" useFlexGap>
        <Typography variant="h6" sx={{ fontWeight: 700 }}>
          {report.name}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {ENTITY_BY_KEY[report.moduleKey]?.label ?? report.moduleKey} ·{' '}
          {data?.totalElements ?? 0} record(s)
        </Typography>
        <Box sx={{ flex: 1 }} />
        <Button
          startIcon={<PrintOutlined />}
          component="a"
          href={`${BASE}reports/${id}/print`}
          target="_blank"
          rel="noopener noreferrer"
        >
          Print
        </Button>
        <Button onClick={onClose}>Back to reports</Button>
      </Stack>
      {report.notes && (
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          {report.notes}
        </Typography>
      )}
      <Paper variant="outlined">
        <ReportTable
          moduleKey={report.moduleKey}
          columns={report.columns}
          rows={data?.content ?? []}
          onOpen={(recordId) => navigate(`/read/${report.moduleKey}/${recordId}`)}
        />
      </Paper>
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 2 }}>
        <Button size="small" disabled={page === 0 || isFetching} onClick={() => setPage((p) => p - 1)}>
          Previous
        </Button>
        <Typography variant="body2" color="text.secondary">
          Page {page + 1} of {Math.max(1, data?.totalPages ?? 1)}
        </Typography>
        <Button
          size="small"
          disabled={isFetching || page + 1 >= (data?.totalPages ?? 1)}
          onClick={() => setPage((p) => p + 1)}
        >
          Next
        </Button>
      </Stack>
    </Box>
  );
}
