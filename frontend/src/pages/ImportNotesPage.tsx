import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import FileDownload from '@mui/icons-material/FileDownload';
import Save from '@mui/icons-material/Save';
import {
  EntityRecord,
  ImportNote,
  getEntity,
  importNote,
  listImportNotes,
  setImportDir,
} from '../api/client';
import { SideNav } from '../components/SideNav';
import { RecordCard } from '../components/RecordCard';

export function ImportNotesPage() {
  const qc = useQueryClient();
  // filename -> the imported record (+ its module), so we can show a card of it.
  const [done, setDone] = useState<Record<string, { module: string; record: EntityRecord }>>({});

  const { data, isLoading } = useQuery({ queryKey: ['import-notes'], queryFn: listImportNotes });

  const [dir, setDir] = useState('');
  // Keep the editable path in sync when the server tells us the current one.
  useEffect(() => {
    if (data?.dir) setDir(data.dir);
  }, [data?.dir]);

  const saveDir = useMutation({
    mutationFn: (d: string) => setImportDir(d),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['import-notes'] }),
  });

  const doImport = useMutation({
    mutationFn: (n: ImportNote) => importNote(n.filename),
    onSuccess: async (res, n) => {
      // Fetch the full record so we can show a card of what was just created.
      const record = await getEntity(res.module, res.id).catch(() => null);
      if (record) setDone((d) => ({ ...d, [n.filename]: { module: res.module, record } }));
      qc.invalidateQueries({ queryKey: ['import-notes'] });
      qc.invalidateQueries({ queryKey: ['list', res.module] });
    },
  });

  const notes = data?.notes ?? [];

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: 4, maxWidth: 1000 }}>
        <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }}>
          Import notes
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Plain-text notes from your knotes folder. The filename
          (<code>date~TYPE~pPRIORITY~DEPT.md</code>) sets the target module, priority,
          department and creation date. Click Import to create the record; the file is then
          moved to <code>imported/</code>.
        </Typography>

        {/* Configurable import folder (defaults to $HOME/new/knotes). */}
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 3 }}>
          <TextField
            label="Import folder"
            size="small"
            value={dir}
            onChange={(e) => setDir(e.target.value)}
            fullWidth
            InputProps={{ sx: { fontFamily: 'monospace' } }}
          />
          <Button
            variant="outlined"
            startIcon={<Save />}
            disabled={saveDir.isPending || !dir.trim() || dir === data?.dir}
            onClick={() => saveDir.mutate(dir.trim())}
          >
            Save
          </Button>
        </Stack>

        {!isLoading && notes.length === 0 && (
          <Alert severity="info">No notes to import. The folder is empty.</Alert>
        )}

        <Stack spacing={2}>
          {notes.map((n) => (
            <Card key={n.filename} variant="outlined">
              <CardContent>
                <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }} flexWrap="wrap" useFlexGap>
                  <Typography variant="subtitle2" sx={{ fontFamily: 'monospace', flex: 1 }}>
                    {n.filename}
                  </Typography>
                  {n.parseable ? (
                    <>
                      <Chip size="small" color="primary" label={n.moduleLabel ?? n.moduleKey ?? '??'} />
                      {n.priority != null && <Chip size="small" variant="outlined" label={`p${n.priority}`} />}
                      {n.deptCode && (
                        <Chip
                          size="small"
                          variant="outlined"
                          color="secondary"
                          label={`${n.deptCode}${n.deptName ? ` · ${n.deptName}` : ''}`}
                        />
                      )}
                      {n.timestamp && (
                        <Chip size="small" variant="outlined" label={new Date(n.timestamp).toLocaleString()} />
                      )}
                    </>
                  ) : (
                    <Chip size="small" color="warning" label="unrecognized filename" />
                  )}
                </Stack>

                {n.summary && (
                  <Typography dir="auto" sx={{ fontWeight: 600, mb: 0.5 }}>
                    {n.summary}
                  </Typography>
                )}
                <Typography
                  dir="auto"
                  variant="body2"
                  color="text.secondary"
                  sx={{ whiteSpace: 'pre-wrap', maxHeight: 120, overflow: 'auto' }}
                >
                  {n.body}
                </Typography>

                <Stack direction="row" spacing={2} alignItems="center" sx={{ mt: 2 }}>
                  {(() => {
                    // Require type, priority AND department before importing — a note missing any of
                    // them can't produce a complete record.
                    const missing = [
                      !n.typeCode && 'type',
                      n.priority == null && 'priority',
                      !n.deptCode && 'department',
                    ].filter(Boolean) as string[];
                    const blocked = !n.parseable || !n.moduleKey || missing.length > 0;
                    return (
                      <>
                        <Button
                          variant="contained"
                          size="small"
                          startIcon={<FileDownload />}
                          disabled={blocked || doImport.isPending || Boolean(done[n.filename])}
                          onClick={() => doImport.mutate(n)}
                        >
                          Import
                        </Button>
                        {!n.moduleKey && n.parseable && (
                          <Typography variant="caption" color="error">
                            No module matches type code “{n.typeCode}”. Set its code in Settings.
                          </Typography>
                        )}
                        {n.parseable && n.moduleKey && missing.length > 0 && (
                          <Typography variant="caption" color="error">
                            Missing {missing.join(', ')} — can’t import.
                          </Typography>
                        )}
                      </>
                    );
                  })()}
                  {done[n.filename] && (
                    <Typography variant="caption" color="success.main" sx={{ fontWeight: 700 }}>
                      ✓ Imported
                    </Typography>
                  )}
                </Stack>
              </CardContent>
            </Card>
          ))}
        </Stack>

        {/* Imported this session — persists here even after each note (above) is archived and drops
            off the list. Newest first. */}
        {Object.keys(done).length > 0 && (
          <Box sx={{ mt: 4 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
              Imported this session ({Object.keys(done).length})
            </Typography>
            <Stack spacing={1}>
              {Object.entries(done)
                .reverse()
                .map(([filename, { module, record }]) => (
                  <RecordCard key={filename} module={module} record={record} />
                ))}
            </Stack>
          </Box>
        )}
      </Box>
    </Box>
  );
}
