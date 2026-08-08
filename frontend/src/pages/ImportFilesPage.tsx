import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import FolderIcon from '@mui/icons-material/Folder';
import InsertDriveFileIcon from '@mui/icons-material/InsertDriveFile';
import DriveFileMoveIcon from '@mui/icons-material/DriveFileMove';
import SyncIcon from '@mui/icons-material/Sync';
import { Divider } from '@mui/material';
import {
  ImportFilesResult,
  PendingSync,
  applyFulltext,
  exportBookmarkedToc,
  getPendingSync,
  importFiles,
  previewImportFiles,
} from '../api/client';
import { SideNav } from '../components/SideNav';
import { DiffText } from '../components/DiffText';
import { ENTITY_BY_KEY } from '../config/entities';
import { useModules } from '../hooks/useModules';

const CREATABLE = ['tasks', 'journals', 'planners', 'resources', 'notes', 'goals', 'writings', 'courses'];

export function ImportFilesPage() {
  const qc = useQueryClient();
  const { enabled } = useModules();
  const options = enabled.filter((m) => CREATABLE.includes(m.moduleKey));
  const [dir, setDir] = useState('');
  const [module, setModule] = useState('tasks');
  const [scanned, setScanned] = useState(false);
  const [result, setResult] = useState<ImportFilesResult | null>(null);

  const preview = useQuery({
    queryKey: ['import-files', dir, module],
    queryFn: () => previewImportFiles(dir, module),
    enabled: scanned,
  });

  // Remember the resolved dir the backend reported.
  useEffect(() => {
    if (preview.data?.dir && !dir) setDir(preview.data.dir);
  }, [preview.data?.dir]); // eslint-disable-line react-hooks/exhaustive-deps

  const run = useMutation({
    mutationFn: (only?: string) => importFiles(dir, module, only),
    onSuccess: (res) => {
      setResult(res);
      qc.invalidateQueries({ queryKey: ['import-files'] });
      qc.invalidateQueries({ queryKey: ['list'] });
      preview.refetch();
    },
  });

  const items = preview.data?.items ?? [];

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: 4, maxWidth: 1000 }}>
        <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }}>
          Import files
        </Typography>

        <PendingSyncSection />
        <Divider sx={{ my: 3 }} />

        <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>
          Import files / folders by name
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Each file/folder name in the drop folder is a command (e.g.{' '}
          <code>t ?done Final report.pdf</code>): it creates a record, then a <b>file</b> is moved
          into the record's repo-1 folder and renamed to its title, while a <b>folder</b> becomes the
          record's id-named folder. Preview the plan first, then import.
        </Typography>

        <Stack direction="row" spacing={2} sx={{ mb: 2 }} flexWrap="wrap" useFlexGap>
          <TextField
            size="small"
            label="Drop folder"
            value={dir}
            onChange={(e) => setDir(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                setScanned(true);
                setResult(null);
                preview.refetch();
              }
            }}
            helperText="~ expands to home. Remembered for next time."
            sx={{ minWidth: 360, flex: 1 }}
            inputProps={{ style: { fontFamily: 'monospace' } }}
          />
          <TextField
            select
            size="small"
            label="Default module"
            value={module}
            onChange={(e) => setModule(e.target.value)}
            SelectProps={{ native: true }}
            sx={{ width: 180 }}
          >
            {options.map((m) => (
              <option key={m.moduleKey} value={m.moduleKey}>
                {m.label}
                {m.code ? ` (${m.code})` : ''}
              </option>
            ))}
          </TextField>
          <Button
            variant="outlined"
            onClick={() => {
              setScanned(true);
              setResult(null);
              preview.refetch();
            }}
          >
            Scan / preview
          </Button>
        </Stack>

        {preview.isError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {(preview.error as { response?: { data?: { message?: string } } })?.response?.data?.message ??
              'Could not read the folder.'}
          </Alert>
        )}

        {scanned && !preview.isFetching && items.length === 0 && !preview.isError && (
          <Alert severity="info">Nothing to import — the folder is empty.</Alert>
        )}

        {items.length > 0 && (
          <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 2 }}>
            <Button
              variant="contained"
              startIcon={run.isPending ? <CircularProgress size={16} color="inherit" /> : <DriveFileMoveIcon />}
              disabled={run.isPending || items.every((i) => i.error)}
              onClick={() => run.mutate(undefined)}
            >
              Import all ({items.filter((i) => !i.error).length})
            </Button>
            {result && (
              <Alert severity={result.imported > 0 ? 'success' : 'warning'} sx={{ py: 0 }}>
                Imported {result.imported} of {result.items.length}.
              </Alert>
            )}
          </Stack>
        )}

        <Stack spacing={1.5}>
          {items.map((it) => {
            const res = result?.items.find((r) => r.name === it.name);
            return (
              <Card key={it.name} variant="outlined">
                <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                    {it.directory ? <FolderIcon fontSize="small" color="action" /> : <InsertDriveFileIcon fontSize="small" color="action" />}
                    <Typography sx={{ fontFamily: 'monospace', flex: 1 }} dir="auto">
                      {it.name}
                    </Typography>
                    {it.module && <Chip size="small" color="primary" label={it.module} />}
                    <Chip size="small" variant="outlined" label={it.directory ? 'folder' : 'file'} />
                    {!it.error && (
                      <Button
                        size="small"
                        variant="contained"
                        disabled={run.isPending || Boolean(res && !res.error)}
                        onClick={() => run.mutate(it.name)}
                      >
                        Import
                      </Button>
                    )}
                  </Stack>

                  {it.error ? (
                    <Alert severity="warning" sx={{ mt: 1, py: 0 }}>
                      {it.error}
                    </Alert>
                  ) : (
                    <Box sx={{ mt: 1 }}>
                      <Typography variant="body2" dir="auto">
                        Create <b>{it.module}</b>: “{it.title}”
                      </Typography>
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                        {it.operation}
                      </Typography>
                      <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                        → {it.target}
                      </Typography>
                    </Box>
                  )}

                  {res && (
                    <Typography
                      variant="caption"
                      sx={{ display: 'block', mt: 0.5 }}
                      color={res.error ? 'error' : 'success.main'}
                    >
                      {res.error ? `✗ ${res.error}` : `✓ ${res.module} #${res.id} → ${res.movedTo}`}
                    </Typography>
                  )}
                </CardContent>
              </Card>
            );
          })}
        </Stack>
      </Box>
    </Box>
  );
}

/** Consolidated list of records whose checkout file (e.g. W18.md) has unsynced edits, with one-click sync. */
function PendingSyncSection() {
  const qc = useQueryClient();
  const { data, isFetching, refetch } = useQuery({ queryKey: ['pending-sync'], queryFn: getPendingSync });
  const items = data ?? [];
  const [busy, setBusy] = useState<number | 'all' | null>(null);

  // Bookmarked-records checkout + master TOC (repo1/TOC.md) — the exported files feed the same
  // pending-sync loop above, which is why the button lives in this section.
  const toc = useMutation({ mutationFn: exportBookmarkedToc });

  const invalidate = (module: string, id: number) => {
    qc.invalidateQueries({ queryKey: ['record', module, id] });
    qc.invalidateQueries({ queryKey: ['list', module] });
  };

  const syncOne = async (it: PendingSync) => {
    setBusy(it.id);
    try {
      await applyFulltext(it.module, it.id);
      invalidate(it.module, it.id);
    } finally {
      setBusy(null);
      refetch();
    }
  };

  const syncAll = async () => {
    setBusy('all');
    try {
      for (const it of items) {
        await applyFulltext(it.module, it.id).catch(() => {});
        invalidate(it.module, it.id);
      }
    } finally {
      setBusy(null);
      refetch();
    }
  };

  return (
    <Box>
      <Stack direction="row" alignItems="center" spacing={2} sx={{ mb: 1 }}>
        <Typography variant="h6" sx={{ fontWeight: 700, flex: 1 }}>
          Pending sync — edited record files ({items.length})
        </Typography>
        <Button size="small" onClick={() => refetch()} disabled={isFetching}>
          {isFetching ? 'Scanning…' : 'Rescan'}
        </Button>
        <Button
          size="small"
          variant="outlined"
          startIcon={toc.isPending ? <CircularProgress size={14} /> : <InsertDriveFileIcon fontSize="small" />}
          disabled={toc.isPending}
          onClick={() => toc.mutate()}
        >
          Bookmarked TOC
        </Button>
        {items.length > 0 && (
          <Button
            size="small"
            variant="contained"
            startIcon={busy === 'all' ? <CircularProgress size={16} color="inherit" /> : <SyncIcon />}
            disabled={busy !== null}
            onClick={syncAll}
          >
            Sync all
          </Button>
        )}
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        Records whose checkout file (<code>W18.md</code>-style, exported to repo 1) differs from the
        database — sync them here instead of opening each record. <b>Bookmarked TOC</b> checks out
        every bookmarked record (tasks, planners, goals, journals, notes, writings, resources) and
        rebuilds the master index (<code>repo1/TOC.md</code>, department → course → record) for the
        Zettlr-style editor; edits made there land back in this list. A checkout that already exists
        with different content is never overwritten — the old file is kept beside it with a
        timestamp for manual merging.
      </Typography>
      {toc.isSuccess && (
        <Alert severity="success" sx={{ py: 0, mb: 1 }}>
          Checked out {toc.data.exported} bookmarked record{toc.data.exported === 1 ? '' : 's'} — TOC
          written to <code>{toc.data.tocPath}</code>
        </Alert>
      )}
      {toc.isSuccess && toc.data.conflicts.length > 0 && (
        <Alert severity="warning" sx={{ py: 0, mb: 1 }}>
          {toc.data.conflicts.length} checkout file{toc.data.conflicts.length === 1 ? '' : 's'} already
          existed and differed from the fresh export (unsynced edits?). The old copies were kept for
          manual comparing/merging:
          <ul style={{ margin: '4px 0 0', paddingInlineStart: 20 }}>
            {toc.data.conflicts.map((c) => (
              <li key={`${c.module}-${c.id}`}>
                <Typography variant="caption" component="span" dir="auto">
                  {c.title} — <code>{c.backupFile}</code>
                </Typography>
              </li>
            ))}
          </ul>
        </Alert>
      )}
      {toc.isError && (
        <Alert severity="error" sx={{ py: 0, mb: 1 }}>
          TOC export failed — check the backend log.
        </Alert>
      )}

      {!isFetching && items.length === 0 ? (
        <Alert severity="success" sx={{ py: 0 }}>
          Nothing to sync — all exported files match the database.
        </Alert>
      ) : (
        <Stack spacing={1.5}>
          {items.map((it) => (
            <Card key={`${it.module}-${it.id}`} variant="outlined">
              <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
                  <Chip size="small" label={ENTITY_BY_KEY[it.module]?.label ?? it.module} />
                  <Typography sx={{ fontWeight: 600, flex: 1 }} dir="auto" noWrap>
                    {it.title || `#${it.id}`}
                  </Typography>
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={busy === it.id ? <CircularProgress size={14} /> : <SyncIcon fontSize="small" />}
                    disabled={busy !== null}
                    onClick={() => syncOne(it)}
                  >
                    Sync
                  </Button>
                </Stack>
                {it.diffs.map((d) => (
                  <Box key={d.field} sx={{ mb: 1 }}>
                    <Typography variant="caption" sx={{ fontWeight: 700, color: 'text.secondary' }}>
                      {d.field}
                    </Typography>
                    <DiffText oldValue={d.oldValue} newValue={d.newValue} />
                  </Box>
                ))}
              </CardContent>
            </Card>
          ))}
        </Stack>
      )}
    </Box>
  );
}
