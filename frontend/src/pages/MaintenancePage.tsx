import { useMemo, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Link as RouterLink } from 'react-router-dom';
import {
  Alert,
  AlertTitle,
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  Divider,
  Link,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import DriveFileMoveIcon from '@mui/icons-material/DriveFileMove';
import StorageIcon from '@mui/icons-material/Storage';
import RefreshIcon from '@mui/icons-material/Refresh';
import ArticleIcon from '@mui/icons-material/Article';
import {
  FolderUsage,
  FulltextReport,
  FulltextUsage,
  MoveResult,
  OffloadResult,
  UsageReport,
  diskUsage,
  fulltextUsage,
  moveToRepo2,
  offloadFullText,
} from '../api/client';
import { SideNav } from '../components/SideNav';
import { ENTITY_BY_KEY } from '../config/entities';
import { dirOf } from '../util/rtl';

const TOP = 30;

/** MB with one decimal — the unit the page is about, so it's used everywhere and never bytes. */
function mb(bytes: number): string {
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** Characters as an approximate on-disk weight; full text is what it costs the database. */
function chars(n: number): string {
  return n >= 1_000_000 ? `${(n / 1_000_000).toFixed(1)}M chars` : `${Math.round(n / 1000)}k chars`;
}

const moduleLabel = (key: string) => ENTITY_BY_KEY[key]?.label ?? key;

/**
 * Housekeeping for the two things that grow without anyone deciding they should: repository 1 and
 * the `fullText` column.
 *
 * <p>Both sections follow the same shape on purpose — a ranked table, a checkbox per row, "select
 * all" / "selected", and a stated total of what the selection would give back — because they are
 * the same decision made about two different stores, and a user who has learned one has learned
 * the other. Nothing is suggested implicitly: the page ranks, the user picks, the button says how
 * much it will reclaim before it runs.
 */
export function MaintenancePage() {
  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: 4, maxWidth: 1200 }}>
        <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }}>
          Maintenance
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Where the space is going, and the two ways to get it back: move a record's files from
          repository 1 (the portable one) to repository 2 (the archive), and move a resource's full
          text out of the database into a file beside the record.
        </Typography>

        <DiskSection />
        <Divider sx={{ my: 4 }} />
        <FulltextSection />
      </Box>
    </Box>
  );
}

// ---- repository 1 disk usage ------------------------------------------------------------

function DiskSection() {
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const [result, setResult] = useState<MoveResult | null>(null);

  const report = useQuery<UsageReport>({
    queryKey: ['maintenance', 'disk'],
    queryFn: () => diskUsage(TOP),
  });

  const rows = report.data?.top ?? [];
  const keyOf = (r: FolderUsage) => `${r.module}:${r.id}`;
  // Only rows with something to move are selectable — a folder holding just a cover is a row you
  // can read but not act on, and a checkbox that does nothing is worse than no checkbox.
  const movable = rows.filter((r) => r.movableBytes > 0);
  const selected = movable.filter((r) => picked.has(keyOf(r)));
  const reclaim = selected.reduce((sum, r) => sum + r.movableBytes, 0);

  const toggle = (r: FolderUsage) => {
    const next = new Set(picked);
    const k = keyOf(r);
    if (next.has(k)) next.delete(k);
    else next.add(k);
    setPicked(next);
  };

  const move = useMutation({
    mutationFn: () => moveToRepo2(selected.map((r) => ({ module: r.module, id: r.id }))),
    onSuccess: (res) => {
      setResult(res);
      setPicked(new Set());
      report.refetch();
    },
  });

  return (
    <>
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
        <StorageIcon color="primary" />
        <Typography variant="h6" sx={{ fontWeight: 700 }}>
          Heaviest repository&nbsp;1 folders
        </Typography>
        <Box sx={{ flex: 1 }} />
        <Button
          size="small"
          startIcon={<RefreshIcon />}
          onClick={() => report.refetch()}
          disabled={report.isFetching}
        >
          Rescan
        </Button>
      </Stack>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Moving a record sends its repo-1 files to the same record's folder in repository 2. The
        cover image and the checkout <code>.md</code> stay behind — the app looks those up in
        repo&nbsp;1 by path — so the reclaimable figure is a little under the folder size. Nothing
        in repository 2 is ever overwritten: a name already taken gets a <code>-1</code> suffix.
      </Typography>

      {report.isLoading && <CircularProgress size={22} />}
      {report.isError && <Alert severity="error">Could not scan repository 1.</Alert>}

      {report.data && (
        <>
          <Stack direction="row" spacing={1} sx={{ mb: 2 }} flexWrap="wrap" useFlexGap>
            <Chip size="small" label={`${mb(report.data.totalBytes)} in ${report.data.foldersWithFiles} folders`} />
            <Chip size="small" variant="outlined" label={`${report.data.scannedRecords} records scanned`} />
            <Chip size="small" variant="outlined" label={`top ${rows.length} shown`} />
          </Stack>

          <Stack direction="row" spacing={1} sx={{ mb: 1 }} alignItems="center" flexWrap="wrap" useFlexGap>
            <Button size="small" onClick={() => setPicked(new Set(movable.map(keyOf)))}>
              Select all ({movable.length})
            </Button>
            <Button size="small" onClick={() => setPicked(new Set())} disabled={picked.size === 0}>
              Clear
            </Button>
            <Box sx={{ flex: 1 }} />
            <Button
              variant="contained"
              startIcon={<DriveFileMoveIcon />}
              disabled={selected.length === 0 || move.isPending}
              onClick={() => move.mutate()}
            >
              Move {selected.length} to repo 2 — reclaims {mb(reclaim)}
            </Button>
          </Stack>

          <TableContainer component={Paper} variant="outlined">
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell padding="checkbox" />
                  <TableCell>Record</TableCell>
                  <TableCell>Module</TableCell>
                  <TableCell align="right">Folder</TableCell>
                  <TableCell align="right">Movable</TableCell>
                  <TableCell align="right">Files</TableCell>
                  <TableCell>Repo&nbsp;1 path</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((r) => (
                  <TableRow key={keyOf(r)} hover selected={picked.has(keyOf(r))}>
                    <TableCell padding="checkbox">
                      <Tooltip title={r.movableBytes > 0 ? '' : 'Only the cover / checkout file — nothing to move'}>
                        <span>
                          <Checkbox
                            size="small"
                            checked={picked.has(keyOf(r))}
                            disabled={r.movableBytes === 0}
                            onChange={() => toggle(r)}
                          />
                        </span>
                      </Tooltip>
                    </TableCell>
                    <TableCell dir={dirOf(r.title)} sx={{ maxWidth: 340, textAlign: 'start' }}>
                      <Link
                        component={RouterLink}
                        to={`/${r.module}?select=${r.id}`}
                        underline="hover"
                        color="inherit"
                      >
                        {r.title || `#${r.id}`}
                      </Link>
                      {r.inRepo2 && (
                        <Chip size="small" variant="outlined" label="has repo 2" sx={{ ml: 1, height: 18 }} />
                      )}
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" color="text.secondary">
                        {moduleLabel(r.module)} #{r.id}
                        {r.typeCode ? ` · ${r.typeCode}` : ''}
                      </Typography>
                    </TableCell>
                    <TableCell align="right" sx={{ fontWeight: 600 }}>{mb(r.bytes)}</TableCell>
                    <TableCell align="right">{mb(r.movableBytes)}</TableCell>
                    <TableCell align="right">{r.files}</TableCell>
                    <TableCell sx={{ maxWidth: 280 }}>
                      <Typography
                        variant="caption"
                        color="text.secondary"
                        sx={{ fontFamily: 'monospace', wordBreak: 'break-all' }}
                      >
                        {r.path}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ))}
                {rows.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={7}>
                      <Typography variant="body2" color="text.secondary">
                        No record folder in repository 1 holds any files.
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </>
      )}

      {move.isError && (
        <Alert severity="error" sx={{ mt: 2 }}>
          The move failed. Nothing was deleted — check the folder permissions and try again.
        </Alert>
      )}
      {result && (
        <Alert severity={result.skipped.length ? 'warning' : 'success'} sx={{ mt: 2 }}>
          <AlertTitle>
            Moved {result.files} file(s) from {result.records} record(s) — {mb(result.bytes)} freed in
            repository&nbsp;1
          </AlertTitle>
          {result.skipped.map((s) => (
            <Typography key={s} variant="caption" display="block">
              {s}
            </Typography>
          ))}
        </Alert>
      )}
    </>
  );
}

// ---- resource full text -----------------------------------------------------------------

function FulltextSection() {
  const [picked, setPicked] = useState<Set<number>>(new Set());
  const [result, setResult] = useState<OffloadResult | null>(null);

  const report = useQuery<FulltextReport>({
    queryKey: ['maintenance', 'fulltext'],
    queryFn: () => fulltextUsage(TOP),
  });

  const rows = report.data?.top ?? [];
  const selected = useMemo(() => rows.filter((r) => picked.has(r.id)), [rows, picked]);
  const reclaim = selected.reduce((sum, r) => sum + r.length, 0);

  const toggle = (r: FulltextUsage) => {
    const next = new Set(picked);
    if (next.has(r.id)) next.delete(r.id);
    else next.add(r.id);
    setPicked(next);
  };

  const offload = useMutation({
    mutationFn: () => offloadFullText(selected.map((r) => r.id)),
    onSuccess: (res) => {
      setResult(res);
      setPicked(new Set());
      report.refetch();
    },
  });

  return (
    <>
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
        <ArticleIcon color="primary" />
        <Typography variant="h6" sx={{ fontWeight: 700 }}>
          Longest resource full text
        </Typography>
        <Box sx={{ flex: 1 }} />
        <Button
          size="small"
          startIcon={<RefreshIcon />}
          onClick={() => report.refetch()}
          disabled={report.isFetching}
        >
          Refresh
        </Button>
      </Stack>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        The text is written to <code>R-&lt;id&gt;-fulltext.md</code> in the resource's repository-1
        folder and the field is then emptied. The file is written and verified before the column is
        cleared, and an existing file of that name is parked with a timestamp rather than replaced.
      </Typography>

      <Alert severity="info" sx={{ mb: 2 }}>
        Emptying the field takes the text out of record search — it will still be on disk, greppable
        and open in any editor, but a search in the app will no longer match on it.
      </Alert>

      {report.isLoading && <CircularProgress size={22} />}
      {report.isError && <Alert severity="error">Could not read the full-text sizes.</Alert>}

      {report.data && (
        <>
          <Stack direction="row" spacing={1} sx={{ mb: 2 }} flexWrap="wrap" useFlexGap>
            <Chip size="small" label={`${chars(report.data.totalChars)} across ${report.data.records} resources`} />
            <Chip size="small" variant="outlined" label={`top ${rows.length} shown`} />
          </Stack>

          <Stack direction="row" spacing={1} sx={{ mb: 1 }} alignItems="center" flexWrap="wrap" useFlexGap>
            <Button size="small" onClick={() => setPicked(new Set(rows.map((r) => r.id)))}>
              Select all ({rows.length})
            </Button>
            <Button size="small" onClick={() => setPicked(new Set())} disabled={picked.size === 0}>
              Clear
            </Button>
            <Box sx={{ flex: 1 }} />
            <Button
              variant="contained"
              startIcon={<DriveFileMoveIcon />}
              disabled={selected.length === 0 || offload.isPending}
              onClick={() => offload.mutate()}
            >
              Move {selected.length} to file — {chars(reclaim)} out of the database
            </Button>
          </Stack>

          <TableContainer component={Paper} variant="outlined">
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell padding="checkbox" />
                  <TableCell>Resource</TableCell>
                  <TableCell align="right">Full text</TableCell>
                  <TableCell>Target file</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((r) => (
                  <TableRow key={r.id} hover selected={picked.has(r.id)}>
                    <TableCell padding="checkbox">
                      <Checkbox size="small" checked={picked.has(r.id)} onChange={() => toggle(r)} />
                    </TableCell>
                    {/* The title's own direction, so an Arabic title keeps its #id beside it, not flipped. */}
                    <TableCell dir={dirOf(r.title)} sx={{ maxWidth: 420, textAlign: 'start' }}>
                      <Link
                        component={RouterLink}
                        to={`/resources?select=${r.id}`}
                        underline="hover"
                        color="inherit"
                      >
                        {r.title || `#${r.id}`}
                      </Link>
                      <Typography variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                        #{r.id}
                      </Typography>
                    </TableCell>
                    <TableCell align="right" sx={{ fontWeight: 600 }}>
                      {chars(r.length)}
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                        {r.fileName}
                      </Typography>
                      {r.fileExists && (
                        <Chip size="small" variant="outlined" label="exists" sx={{ ml: 1, height: 18 }} />
                      )}
                    </TableCell>
                  </TableRow>
                ))}
                {rows.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4}>
                      <Typography variant="body2" color="text.secondary">
                        No resource carries any full text.
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </>
      )}

      {offload.isError && (
        <Alert severity="error" sx={{ mt: 2 }}>
          The offload failed. The text is still in the database — nothing was cleared.
        </Alert>
      )}
      {result && (
        <Alert severity={result.skipped.length ? 'warning' : 'success'} sx={{ mt: 2 }}>
          <AlertTitle>
            Moved {chars(result.chars)} out of {result.records} resource(s)
          </AlertTitle>
          {result.written.map((w) => (
            <Typography key={w} variant="caption" display="block" sx={{ fontFamily: 'monospace' }}>
              {w}
            </Typography>
          ))}
          {result.skipped.map((s) => (
            <Typography key={s} variant="caption" display="block">
              {s}
            </Typography>
          ))}
        </Alert>
      )}
    </>
  );
}
