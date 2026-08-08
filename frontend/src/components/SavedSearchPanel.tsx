import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  IconButton,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import Add from '@mui/icons-material/Add';
import Tune from '@mui/icons-material/Tune';
import Edit from '@mui/icons-material/Edit';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import PlayArrow from '@mui/icons-material/PlayArrow';
import {
  SavedSearch,
  SavedSearchResult,
  SavedSearchRecordRef,
  createSavedSearch,
  deleteSavedSearch,
  listSavedSearches,
  runSavedSearch,
  runSavedSearchAdhoc,
  updateSavedSearch,
} from '../api/client';

const TYPE_COLOR: Record<string, 'default' | 'info' | 'warning'> = {
  hql: 'info',
  sql: 'warning',
  form: 'default',
};

/**
 * The module's user-defined reports (saved searches), shown in the SideNav under the standard reports.
 * Clicking a `form` search applies its filter to the list; an `hql`/`sql` search runs and shows results
 * (or the affected-row count for an UPDATE/DELETE). The gear opens the manager to add/edit/remove them.
 */
export function SavedSearchPanel({
  module,
  onRunForm,
  onRecords,
}: {
  module: string;
  onRunForm: (query: string) => void;
  onRecords?: (title: string, records: SavedSearchRecordRef[]) => void;
}) {
  const { data: searches } = useQuery({
    queryKey: ['saved-searches', module],
    queryFn: () => listSavedSearches(module),
  });
  const [manage, setManage] = useState(false);
  const [result, setResult] = useState<SavedSearchResult | null>(null);
  const [resultTitle, setResultTitle] = useState('');
  const [runningId, setRunningId] = useState<number | null>(null);

  const run = async (s: SavedSearch) => {
    if (s.queryType === 'form') {
      onRunForm(s.query);
      return;
    }
    setRunningId(s.id ?? null);
    try {
      const res = await runSavedSearch(s.id!);
      // HQL that selected whole entities → open them as real clickable records in the 2nd pane;
      // anything else (a `select col, …` projection, a mutation, an error) stays the table dialog.
      if (onRecords && res.type === 'rows' && res.records && res.records.length > 0) {
        onRecords(s.summary, res.records);
      } else {
        setResultTitle(s.summary);
        setResult(res);
      }
    } finally {
      setRunningId(null);
    }
  };

  return (
    <>
      <Stack direction="row" alignItems="center" spacing={0.5} sx={{ px: 1, pt: 0.5 }}>
        <Typography variant="overline" color="text.secondary" sx={{ flex: 1 }}>
          Saved searches
        </Typography>
        <Tooltip title="Manage saved searches">
          <IconButton size="small" onClick={() => setManage(true)}>
            <Tune fontSize="small" />
          </IconButton>
        </Tooltip>
      </Stack>
      <List dense disablePadding>
        {(searches ?? []).map((s) => (
          <ListItemButton key={s.id} sx={{ py: 0.25, borderRadius: 1 }} onClick={() => run(s)}>
            <ListItemText primary={s.summary} primaryTypographyProps={{ fontSize: 13, noWrap: true }} />
            {runningId === s.id ? (
              <CircularProgress size={12} />
            ) : (
              <Chip
                size="small"
                label={s.queryType}
                color={TYPE_COLOR[s.queryType] ?? 'default'}
                variant="outlined"
                sx={{ height: 16, fontSize: 10 }}
              />
            )}
          </ListItemButton>
        ))}
        {(searches ?? []).length === 0 && (
          <Typography variant="caption" color="text.disabled" sx={{ px: 1.5 }}>
            None yet — add one with the gear.
          </Typography>
        )}
      </List>

      <ResultDialog title={resultTitle} result={result} onClose={() => setResult(null)} />
      <ManagerDialog module={module} open={manage} onClose={() => setManage(false)} />
    </>
  );
}

/** Shows a run result: a rows table, an affected-row count, or an error. */
function ResultDialog({
  title,
  result,
  onClose,
}: {
  title: string;
  result: SavedSearchResult | null;
  onClose: () => void;
}) {
  return (
    <Dialog open={!!result} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ pb: 1 }}>{title || 'Result'}</DialogTitle>
      <DialogContent dividers>{result && <ResultView result={result} />}</DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}

function ResultView({ result }: { result: SavedSearchResult }) {
  if (result.type === 'error') {
    return <Alert severity="error">{result.message}</Alert>;
  }
  if (result.type === 'mutation') {
    return <Alert severity="success">{result.affected} row(s) affected.</Alert>;
  }
  if (result.type === 'form') {
    return <Alert severity="info">Filter applied to the list.</Alert>;
  }
  const rows = result.rows ?? [];
  if (rows.length === 0) {
    return <Typography color="text.secondary">No rows.</Typography>;
  }
  return (
    <>
      <Typography variant="caption" color="text.secondary">
        {result.count} row(s){result.count === 500 ? ' (capped)' : ''}
      </Typography>
      <Table size="small">
        <TableBody>
          {rows.map((row, i) => (
            <TableRow key={i}>
              {row.map((cell, j) => (
                <TableCell key={j} sx={{ fontSize: 13 }}>
                  {cell === null || cell === undefined ? '' : String(cell)}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </>
  );
}

const BLANK: SavedSearch = { queryType: 'hql', query: '', summary: '', priority: 0 };

/** Create / edit / delete the module's saved searches, with an inline preview-run. */
function ManagerDialog({
  module,
  open,
  onClose,
}: {
  module: string;
  open: boolean;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const { data: searches } = useQuery({
    queryKey: ['saved-searches', module],
    queryFn: () => listSavedSearches(module),
    enabled: open,
  });
  const [form, setForm] = useState<SavedSearch | null>(null);
  const [preview, setPreview] = useState<SavedSearchResult | null>(null);

  const invalidate = () => qc.invalidateQueries({ queryKey: ['saved-searches', module] });

  const save = useMutation({
    mutationFn: (s: SavedSearch) =>
      s.id ? updateSavedSearch(s.id, s) : createSavedSearch({ ...s, module }),
    onSuccess: () => {
      invalidate();
      setForm(null);
      setPreview(null);
    },
  });
  const remove = useMutation({
    mutationFn: (id: number) => deleteSavedSearch(id),
    onSuccess: invalidate,
  });
  const previewRun = useMutation({
    mutationFn: (s: SavedSearch) => runSavedSearchAdhoc(s.queryType, s.query),
    onSuccess: setPreview,
  });

  const set = (patch: Partial<SavedSearch>) => setForm((f) => ({ ...(f ?? BLANK), ...patch }));

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Saved searches — {module}</DialogTitle>
      <DialogContent dividers>
        {!form && (
          <>
            <Button startIcon={<Add />} variant="outlined" size="small" onClick={() => setForm({ ...BLANK })}>
              New report
            </Button>
            <List dense>
              {(searches ?? []).map((s) => (
                <Stack key={s.id} direction="row" alignItems="center" spacing={1}>
                  <ListItemText
                    primary={s.summary}
                    secondary={`${s.queryType} · ${s.entityName ?? module}`}
                    sx={{ flex: 1 }}
                  />
                  <IconButton size="small" onClick={() => { setForm(s); setPreview(null); }}>
                    <Edit fontSize="small" />
                  </IconButton>
                  <IconButton size="small" color="error" onClick={() => remove.mutate(s.id!)}>
                    <DeleteOutline fontSize="small" />
                  </IconButton>
                </Stack>
              ))}
              {(searches ?? []).length === 0 && (
                <Typography color="text.secondary" sx={{ mt: 1 }}>
                  No saved searches for this module yet.
                </Typography>
              )}
            </List>
          </>
        )}

        {form && (
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Name"
              size="small"
              fullWidth
              value={form.summary}
              onChange={(e) => set({ summary: e.target.value })}
              onKeyDown={(e) =>
                e.key === 'Enter' &&
                !save.isPending &&
                form.summary.trim() &&
                form.query.trim() &&
                save.mutate(form)
              }
            />
            <Stack direction="row" spacing={2}>
              <TextField
                select
                label="Query type"
                size="small"
                value={form.queryType}
                onChange={(e) => set({ queryType: e.target.value as SavedSearch['queryType'] })}
                sx={{ minWidth: 140 }}
              >
                <MenuItem value="hql">HQL</MenuItem>
                <MenuItem value="sql">SQL (native)</MenuItem>
                <MenuItem value="form">Search form</MenuItem>
              </TextField>
              <TextField
                label="Priority"
                type="number"
                size="small"
                value={form.priority ?? 0}
                onChange={(e) => set({ priority: Number(e.target.value) })}
                onKeyDown={(e) =>
                  e.key === 'Enter' &&
                  !save.isPending &&
                  form.summary.trim() &&
                  form.query.trim() &&
                  save.mutate(form)
                }
                sx={{ width: 110 }}
              />
            </Stack>
            <TextField
              label={
                form.queryType === 'form'
                  ? 'Filter params (e.g. bookmarked=true&highPriority=true)'
                  : form.queryType === 'sql'
                    ? 'Native SQL'
                    : 'HQL (e.g. from Task where bookmarked = true)'
              }
              size="small"
              fullWidth
              multiline
              minRows={3}
              value={form.query}
              onChange={(e) => set({ query: e.target.value })}
              inputProps={{ style: { fontFamily: 'monospace', fontSize: 13 } }}
            />
            {form.queryType !== 'form' && (
              <Box>
                <Button
                  size="small"
                  startIcon={<PlayArrow />}
                  onClick={() => previewRun.mutate(form)}
                  disabled={!form.query.trim() || previewRun.isPending}
                >
                  Preview
                </Button>
                {preview && (
                  <Box sx={{ mt: 1 }}>
                    <ResultView result={preview} />
                  </Box>
                )}
              </Box>
            )}
            <Divider />
            <Stack direction="row" spacing={1} justifyContent="flex-end">
              <Button onClick={() => { setForm(null); setPreview(null); }}>Cancel</Button>
              <Button
                variant="contained"
                onClick={() => save.mutate(form)}
                disabled={!form.summary.trim() || !form.query.trim() || save.isPending}
              >
                Save
              </Button>
            </Stack>
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
