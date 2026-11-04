import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Link as MuiLink,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import Add from '@mui/icons-material/Add';
import Search from '@mui/icons-material/Search';
import {
  CatalogFile,
  CatalogRecordSummary,
  StorageLocation,
  StorageLocationInput,
  catalogRecordsOn,
  createStorageLocation,
  deleteStorageLocation,
  listStorageLocations,
  scanStorageLocation,
  searchCatalog,
  updateStorageLocation,
} from '../api/client';
import { ENTITY_BY_KEY } from '../config/entities';
import { errorDetail } from '../util/errors';
import { fmtBytes } from '../util/bytes';
import { useAuth } from '../hooks/useAuth';
import { SideNav } from '../components/SideNav';

const REPO_NAMES: Record<number, string> = { 1: '1 · sandbox', 2: '2 · main', 3: '3 · dismissed' };

/** "Resources #101 — title", the way a catalogue row names its record. */
function RecordLink({ moduleKey, id, title, exists }: { moduleKey: string; id: number; title?: string | null; exists: boolean }) {
  const navigate = useNavigate();
  const label = `${ENTITY_BY_KEY[moduleKey]?.label ?? moduleKey} #${id}`;
  return (
    <Box>
      <MuiLink component="button" type="button" onClick={() => navigate(`/${moduleKey}?select=${id}`)} sx={{ fontWeight: 600 }}>
        {label}
      </MuiLink>
      {title ? (
        <Typography variant="caption" dir="auto" sx={{ display: 'block', color: 'text.secondary' }}>
          {title}
        </Typography>
      ) : (
        !exists && (
          <Typography variant="caption" sx={{ display: 'block', color: 'warning.main' }}>
            not found in the database
          </Typography>
        )
      )}
    </Box>
  );
}

const EMPTY: StorageLocationInput = { name: '', repo: 3, rootPath: '', note: '' };

/**
 * The disk catalogue: storage locations (an external disk holding a repository copy), scanned into
 * the database so each record's files there can be listed and searched while the disk is unplugged
 * — and so a record isn't deleted without knowing it still has files somewhere.
 */
export function CatalogPage() {
  const qc = useQueryClient();
  const { canWriteContent } = useAuth();
  const [editing, setEditing] = useState<{ id: number | null; input: StorageLocationInput } | null>(null);
  const [deleting, setDeleting] = useState<StorageLocation | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [submitted, setSubmitted] = useState<{ q: string; location: number | '' } | null>(null);
  const [searchLocation, setSearchLocation] = useState<number | ''>('');
  const [recordsOf, setRecordsOf] = useState<number | null>(null);

  const { data: locations = [] } = useQuery({
    queryKey: ['catalog-locations'],
    queryFn: listStorageLocations,
    // Poll only while something is scanning, to show progress and notice when it ends.
    refetchInterval: (q) => ((q.state.data ?? []).some((l) => l.scanning) ? 1000 : false),
  });
  const anyScanning = locations.some((l) => l.scanning);
  const [wasScanning, setWasScanning] = useState(false);
  if (wasScanning !== anyScanning) {
    setWasScanning(anyScanning);
    if (!anyScanning) {
      // A scan just finished: what was shown below may be stale.
      qc.invalidateQueries({ queryKey: ['catalog-search'] });
      qc.invalidateQueries({ queryKey: ['catalog-records'] });
      qc.invalidateQueries({ queryKey: ['catalog-record'] });
    }
  }

  const save = useMutation({
    mutationFn: (e: { id: number | null; input: StorageLocationInput }) =>
      e.id == null ? createStorageLocation(e.input) : updateStorageLocation(e.id, e.input),
    onSuccess: () => {
      setEditing(null);
      qc.invalidateQueries({ queryKey: ['catalog-locations'] });
    },
    onError: (e) => setError(errorDetail(e, 'Could not save the location.')),
  });
  const scan = useMutation({
    mutationFn: (id: number) => scanStorageLocation(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['catalog-locations'] }),
    onError: (e) => setError(errorDetail(e, 'Could not start the scan.')),
  });
  const remove = useMutation({
    mutationFn: (id: number) => deleteStorageLocation(id),
    onSuccess: () => {
      setDeleting(null);
      qc.invalidateQueries({ queryKey: ['catalog-locations'] });
      qc.invalidateQueries({ queryKey: ['catalog-search'] });
    },
    onError: (e) => setError(errorDetail(e, 'Could not remove the location.')),
  });

  const { data: results, isFetching: searching } = useQuery({
    queryKey: ['catalog-search', submitted],
    queryFn: () => searchCatalog(submitted!.q, submitted!.location === '' ? null : submitted!.location),
    enabled: submitted != null,
  });
  const { data: records } = useQuery({
    queryKey: ['catalog-records', recordsOf],
    queryFn: () => catalogRecordsOn(recordsOf!),
    enabled: recordsOf != null,
  });

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
    <SideNav />
    <Box sx={{ flex: 1, p: 4, maxWidth: 1200, overflow: 'auto', boxSizing: 'border-box' }}>
      <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }}>
        Disk catalogue
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3, maxWidth: 820 }}>
        Record files kept on storage that isn’t always attached — an external disk with a copy of a repository. Add the
        folder that holds the repository on that disk (e.g. <code>/media/hd1/repo3</code>) and scan it while the disk is
        attached: every file inside a record’s folder, however deeply nested, is recorded against that record. The list
        stays when the disk is unplugged, shows under each record’s files, and the record’s delete dialog warns about it.
      </Typography>
      {error && (
        <Alert severity="error" onClose={() => setError(null)} sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Stack direction="row" alignItems="center" sx={{ mb: 1 }}>
        <Typography variant="h6" sx={{ flex: 1 }}>
          Storage locations
        </Typography>
        {canWriteContent && (
          <Button startIcon={<Add />} onClick={() => setEditing({ id: null, input: EMPTY })}>
            Add location
          </Button>
        )}
      </Stack>
      <Paper variant="outlined" sx={{ overflowX: 'auto', mb: 4 }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Name</TableCell>
              <TableCell>Repo</TableCell>
              <TableCell>Folder</TableCell>
              <TableCell>Last scan</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {locations.length === 0 && (
              <TableRow>
                <TableCell colSpan={5}>
                  <Typography variant="body2" color="text.secondary">
                    No storage locations yet.
                  </Typography>
                </TableCell>
              </TableRow>
            )}
            {locations.map((l) => (
              <TableRow key={l.id} selected={recordsOf === l.id}>
                <TableCell>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {l.name}
                  </Typography>
                  {l.note && (
                    <Typography variant="caption" color="text.secondary">
                      {l.note}
                    </Typography>
                  )}
                </TableCell>
                <TableCell>{REPO_NAMES[l.repo] ?? l.repo}</TableCell>
                <TableCell>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <code style={{ wordBreak: 'break-all' }}>{l.rootPath}</code>
                    <Chip
                      size="small"
                      variant="outlined"
                      color={l.mounted ? 'success' : 'default'}
                      label={l.mounted ? 'attached' : 'not attached'}
                    />
                  </Stack>
                </TableCell>
                <TableCell>
                  {l.scanning ? (
                    <Typography variant="body2" color="primary">
                      Scanning… {l.scanProgress ?? 0} files
                    </Typography>
                  ) : l.lastScanAt ? (
                    <>
                      <Typography variant="body2">
                        {l.lastScanFiles} files · {l.lastScanRecords} records · {fmtBytes(l.lastScanBytes)}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {new Date(l.lastScanAt).toLocaleString()}
                      </Typography>
                    </>
                  ) : (
                    <Typography variant="body2" color="text.secondary">
                      never
                    </Typography>
                  )}
                  {l.lastScanError && !l.scanning && (
                    <Typography variant="caption" sx={{ display: 'block', color: 'warning.main' }}>
                      {l.lastScanError}
                    </Typography>
                  )}
                </TableCell>
                <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                  <Button size="small" onClick={() => setRecordsOf(recordsOf === l.id ? null : l.id)}>
                    Records
                  </Button>
                  {canWriteContent && (
                    <>
                      <Tooltip title={l.mounted ? '' : 'Attach the disk first'}>
                        <span>
                          <Button size="small" disabled={!l.mounted || l.scanning || scan.isPending} onClick={() => scan.mutate(l.id)}>
                            Scan
                          </Button>
                        </span>
                      </Tooltip>
                      <Button
                        size="small"
                        onClick={() =>
                          setEditing({ id: l.id, input: { name: l.name, repo: l.repo, rootPath: l.rootPath, note: l.note ?? '' } })
                        }
                      >
                        Edit
                      </Button>
                      <Button size="small" color="error" disabled={l.scanning} onClick={() => setDeleting(l)}>
                        Remove
                      </Button>
                    </>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>

      {recordsOf != null && (
        <Box sx={{ mb: 4 }}>
          <Typography variant="h6" sx={{ mb: 1 }}>
            Records on {locations.find((l) => l.id === recordsOf)?.name} ({records?.length ?? '…'})
          </Typography>
          <RecordsTable rows={records ?? []} />
        </Box>
      )}

      <Typography variant="h6" sx={{ mb: 1 }}>
        Search the catalogue
      </Typography>
      <Stack
        component="form"
        direction="row"
        spacing={1}
        sx={{ mb: 2 }}
        onSubmit={(e) => {
          e.preventDefault();
          setSubmitted({ q: query.trim(), location: searchLocation });
        }}
      >
        <TextField
          size="small"
          label="File name, path or record title — or a record, e.g. R101"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          sx={{ flex: 1, maxWidth: 520 }}
        />
        <TextField
          select
          size="small"
          label="Location"
          value={searchLocation}
          onChange={(e) => setSearchLocation(e.target.value === '' ? '' : Number(e.target.value))}
          sx={{ width: 200 }}
        >
          <MenuItem value="">All locations</MenuItem>
          {locations.map((l) => (
            <MenuItem key={l.id} value={l.id}>
              {l.name}
            </MenuItem>
          ))}
        </TextField>
        <Button type="submit" variant="contained" startIcon={<Search />} disabled={searching}>
          Search
        </Button>
      </Stack>
      {results && <FilesTable rows={results} />}

      <Dialog open={editing != null} onClose={() => setEditing(null)} fullWidth maxWidth="sm">
        <DialogTitle>{editing?.id == null ? 'Add storage location' : 'Edit storage location'}</DialogTitle>
        {editing && (
          <DialogContent>
            <Stack spacing={2} sx={{ mt: 1 }}>
              <TextField
                label="Name"
                placeholder="hd1 — repo 3 copy"
                value={editing.input.name}
                onChange={(e) => setEditing({ ...editing, input: { ...editing.input, name: e.target.value } })}
                autoFocus
              />
              <TextField
                select
                label="Holds repository"
                value={editing.input.repo}
                onChange={(e) => setEditing({ ...editing, input: { ...editing.input, repo: Number(e.target.value) } })}
              >
                {[1, 2, 3].map((r) => (
                  <MenuItem key={r} value={r}>
                    {REPO_NAMES[r]}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                label="Folder (the repository root on that storage)"
                placeholder="/media/hd1/repo3"
                value={editing.input.rootPath}
                onChange={(e) => setEditing({ ...editing, input: { ...editing.input, rootPath: e.target.value } })}
                helperText="Module folders (R, T, …) sit directly inside it, as in the repository itself."
              />
              <TextField
                label="Note"
                value={editing.input.note ?? ''}
                onChange={(e) => setEditing({ ...editing, input: { ...editing.input, note: e.target.value } })}
                multiline
                minRows={2}
              />
            </Stack>
          </DialogContent>
        )}
        <DialogActions>
          <Button onClick={() => setEditing(null)}>Cancel</Button>
          <Button variant="contained" disabled={save.isPending} onClick={() => editing && save.mutate(editing)}>
            Save
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={deleting != null} onClose={() => setDeleting(null)}>
        <DialogTitle>Remove “{deleting?.name}”?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Its catalogue ({deleting?.lastScanFiles ?? 0} files) is removed from the database. Nothing on the disk itself
            is touched; scanning it again later brings the catalogue back.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleting(null)}>Cancel</Button>
          <Button color="error" disabled={remove.isPending} onClick={() => deleting && remove.mutate(deleting.id)}>
            Remove
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
    </Box>
  );
}

function FilesTable({ rows }: { rows: CatalogFile[] }) {
  if (rows.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        Nothing found.
      </Typography>
    );
  }
  return (
    <Paper variant="outlined" sx={{ overflowX: 'auto' }}>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Record</TableCell>
            <TableCell>File</TableCell>
            <TableCell>Location</TableCell>
            <TableCell align="right">Size</TableCell>
            <TableCell>Modified</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((f, i) => (
            <TableRow key={`${f.locationId}-${f.moduleKey}-${f.recordId}-${f.relPath}-${i}`}>
              <TableCell sx={{ minWidth: 180 }}>
                <RecordLink moduleKey={f.moduleKey} id={f.recordId} title={f.recordTitle} exists={f.recordExists} />
              </TableCell>
              <TableCell sx={{ wordBreak: 'break-all' }}>
                <Tooltip title={f.fullPath} placement="bottom-start">
                  <span>{f.relPath}</span>
                </Tooltip>
              </TableCell>
              <TableCell sx={{ whiteSpace: 'nowrap' }}>
                {f.locationName}
                {!f.mounted && (
                  <Typography variant="caption" sx={{ display: 'block' }} color="text.secondary">
                    not attached
                  </Typography>
                )}
              </TableCell>
              <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                {fmtBytes(f.size)}
              </TableCell>
              <TableCell sx={{ whiteSpace: 'nowrap' }}>{f.modified ? new Date(f.modified).toLocaleDateString() : '—'}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      {rows.length >= 300 && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', p: 1 }}>
          Showing the first 300 — narrow the search to see more.
        </Typography>
      )}
    </Paper>
  );
}

function RecordsTable({ rows }: { rows: CatalogRecordSummary[] }) {
  if (rows.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        No record files catalogued here yet — scan the location while its disk is attached.
      </Typography>
    );
  }
  return (
    <Paper variant="outlined" sx={{ overflowX: 'auto', maxHeight: 480 }}>
      <Table size="small" stickyHeader>
        <TableHead>
          <TableRow>
            <TableCell>Record</TableCell>
            <TableCell align="right">Files</TableCell>
            <TableCell align="right">Size</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((r) => (
            <TableRow key={`${r.moduleKey}-${r.recordId}`}>
              <TableCell>
                <RecordLink moduleKey={r.moduleKey} id={r.recordId} title={r.recordTitle} exists={r.recordExists} />
              </TableCell>
              <TableCell align="right">{r.files}</TableCell>
              <TableCell align="right">{fmtBytes(r.bytes)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Paper>
  );
}
