import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import FileDownload from '@mui/icons-material/FileDownload';
import Save from '@mui/icons-material/Save';
import CallMerge from '@mui/icons-material/CallMerge';
import Search from '@mui/icons-material/Search';
import {
  DuplicatePair,
  EntityRecord,
  ImportNote,
  SearchHit,
  findDuplicatePairs,
  findSemanticDuplicates,
  getEntity,
  getLookups,
  getSemanticDedupStatus,
  importNote,
  listImportNotes,
  searchAll,
  setImportDir,
} from '../api/client';
import { SideNav } from '../components/SideNav';
import { RecordCard } from '../components/RecordCard';
import { MergeDialog, MergeDraft } from '../components/MergeDialog';
import { ENTITIES, MERGEABLE_MODULES } from '../config/entities';

/**
 * How many note cards are drawn before "show more".
 *
 * Each one issues a similarity search, so this is a request budget as much as a rendering one.
 * Twenty is about a screen and a half — enough to work through without the page stalling on open.
 */
const PAGE = 20;

export function ImportNotesPage() {
  const qc = useQueryClient();
  const navigate = useNavigate();
  // filename -> the imported record (+ its module and the original note). Keeping the note lets the
  // card stay in place after the server list refreshes (the file is moved to imported/), so the
  // saved record shows right under the note it came from.
  const [done, setDone] = useState<
    Record<string, { module: string; record: EntityRecord; note: ImportNote }>
  >({});

  const { data, isLoading } = useQuery({ queryKey: ['import-notes'], queryFn: listImportNotes });

  // Find-duplicates-in-the-database scan (separate from the per-note "similar records" column
  // below, which compares a not-yet-imported file against the database — this compares the
  // database against itself, module by module).
  const mergeableEntities = ENTITIES.filter((e) => MERGEABLE_MODULES.has(e.key));
  const [dedupModule, setDedupModule] = useState(mergeableEntities[0]?.key ?? '');
  const [dedupMode, setDedupMode] = useState<'fast' | 'embedding' | 'llm'>('fast');
  const [dedupPairs, setDedupPairs] = useState<DuplicatePair[] | null>(null);
  const [mergeDraft, setMergeDraft] = useState<MergeDraft | null>(null);
  const dedupLabel = mergeableEntities.find((e) => e.key === dedupModule)?.label ?? dedupModule;

  // Local Ollama status, so the AI modes can be disabled with an explanation if it's not set up.
  const { data: ollamaStatus } = useQuery({
    queryKey: ['semantic-dedup-status'],
    queryFn: getSemanticDedupStatus,
    staleTime: 60_000,
    retry: false,
  });

  const scanDuplicates = useMutation({
    mutationFn: () =>
      dedupMode === 'fast'
        ? findDuplicatePairs(dedupModule, 50)
        : findSemanticDuplicates(dedupModule, dedupMode, dedupMode === 'llm' ? 20 : 30),
    onSuccess: (pairs) => setDedupPairs(pairs),
  });

  // Resource types — an 'R' note's type must be chosen BEFORE import, because it places the
  // record's repo folder (R/<type>/…). A page-level default + a per-note override.
  const { data: resourceLookups } = useQuery({
    queryKey: ['lookups', 'resources'],
    queryFn: () => getLookups('resources'),
  });
  const resourceTypes = resourceLookups?.types ?? [];
  const [defaultTypeId, setDefaultTypeId] = useState<number | ''>('');
  const [typeOverrides, setTypeOverrides] = useState<Record<string, number>>({});
  const effectiveTypeId = (n: ImportNote): number | undefined =>
    typeOverrides[n.filename] ?? (defaultTypeId === '' ? undefined : defaultTypeId);

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
    mutationFn: (n: ImportNote) =>
      importNote(n.filename, n.moduleKey === 'resources' ? effectiveTypeId(n) : undefined),
    onSuccess: async (res, n) => {
      // Fetch the full record so we can show a card of what was just created.
      const record = await getEntity(res.module, res.id).catch(() => null);
      if (record) setDone((d) => ({ ...d, [n.filename]: { module: res.module, record, note: n } }));
      qc.invalidateQueries({ queryKey: ['import-notes'] });
      qc.invalidateQueries({ queryKey: ['list', res.module] });
    },
  });

  // Server list (still-pending files) merged with this session's imported notes, which the server
  // no longer returns (their files moved to imported/). Filenames start with the date, so sorting
  // by filename keeps everything in chronological order.
  const notes = (() => {
    const pending = data?.notes ?? [];
    const have = new Set(pending.map((n) => n.filename));
    const imported = Object.values(done)
      .map((d) => d.note)
      .filter((n) => !have.has(n.filename));
    return [...pending, ...imported].sort((a, b) => a.filename.localeCompare(b.filename));
  })();

  /*
   * Filters, and a cap on how many cards are drawn at once.
   *
   * Both exist for the same reason, and it is not cosmetic: every note card mounts a
   * SimilarRecordsColumn, and that fires its own `searchAll` against the server. A folder holding
   * three hundred notes therefore opened the page with three hundred concurrent searches, which is
   * what made it crawl. Cutting the list down is what stops them being issued at all — a card that
   * never mounts never searches.
   *
   * So the cap applies even with no filter chosen. Filtering to the subset you actually mean to
   * import is the other half of the request, and the two work the same way.
   */
  const [priorityFilter, setPriorityFilter] = useState<number | 'all'>('all');
  const [typeFilter, setTypeFilter] = useState<string | 'all'>('all');
  const [shownCount, setShownCount] = useState(PAGE);

  // Options come from the folder's own notes, so nothing is offered that would filter to nothing,
  // and each carries its count — which is the number worth knowing before importing a batch.
  const priorityOptions = (() => {
    const counts = new Map<number, number>();
    notes.forEach((n) => {
      if (n.priority != null) counts.set(n.priority, (counts.get(n.priority) ?? 0) + 1);
    });
    return [...counts.entries()].sort((a, b) => a[0] - b[0]);
  })();
  const typeOptions = (() => {
    const counts = new Map<string, { label: string; count: number }>();
    notes.forEach((n) => {
      const code = n.typeCode ?? '';
      if (!code) return;
      const prev = counts.get(code);
      counts.set(code, {
        label: n.moduleLabel ?? n.moduleKey ?? code,
        count: (prev?.count ?? 0) + 1,
      });
    });
    return [...counts.entries()].sort((a, b) => a[0].localeCompare(b[0]));
  })();

  const filtered = notes.filter(
    (n) =>
      (priorityFilter === 'all' || n.priority === priorityFilter) &&
      (typeFilter === 'all' || (n.typeCode ?? '') === typeFilter),
  );
  const shown = filtered.slice(0, shownCount);

  // A changed filter means a different set entirely; carrying the old "show more" depth over would
  // either hide the start of the new set or re-mount hundreds of cards at once.
  useEffect(() => setShownCount(PAGE), [priorityFilter, typeFilter]);

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: 4, maxWidth: 1300 }}>
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
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !saveDir.isPending && dir.trim() && dir !== data?.dir) {
                saveDir.mutate(dir.trim());
              }
            }}
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

        {/* Default resource type for this session's 'R' imports — the type must be chosen before
            import because it places the record's repo folder (R/<type>/…). Each R note below can
            override it individually. */}
        {notes.some((n) => n.moduleKey === 'resources') && (
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 3 }}>
            <TextField
              select
              size="small"
              label="Default resource type (for R notes)"
              value={defaultTypeId}
              onChange={(e) =>
                setDefaultTypeId(e.target.value === '' ? '' : Number(e.target.value))
              }
              sx={{ minWidth: 320 }}
            >
              <MenuItem value="">
                <em>None — pick per note</em>
              </MenuItem>
              {resourceTypes.map((t) => (
                <MenuItem key={t.id} value={t.id}>
                  {t.code && t.name ? `${t.code} — ${t.name}` : t.name ?? t.code ?? ''}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
        )}

        <Divider sx={{ mb: 3 }} />

        {/* Find-duplicates-in-the-database: a one-off fuzzy scan within a module (title similarity),
            independent of the not-yet-imported files below — for cleaning up records already entered
            twice. Reuses the same merge feature as dragging one record onto another in the list pane. */}
        <Paper variant="outlined" sx={{ p: 2, mb: 4 }}>
          <Typography variant="h6" sx={{ fontWeight: 700, mb: 0.5 }}>
            Find duplicates already in the database
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            One-off scan for near-identical titles within a module, so records entered twice can be
            merged instead of left as clutter.
          </Typography>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }} flexWrap="wrap" useFlexGap>
            <TextField
              select
              size="small"
              label="Module"
              value={dedupModule}
              onChange={(e) => {
                setDedupModule(e.target.value);
                setDedupPairs(null);
              }}
              sx={{ minWidth: 200 }}
            >
              {mergeableEntities.map((e) => (
                <MenuItem key={e.key} value={e.key}>
                  {e.label}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              size="small"
              label="Method"
              value={dedupMode}
              onChange={(e) => {
                setDedupMode(e.target.value as typeof dedupMode);
                setDedupPairs(null);
              }}
              sx={{ minWidth: 280 }}
            >
              <MenuItem value="fast">Fast — title match (instant)</MenuItem>
              <MenuItem value="embedding" disabled={!ollamaStatus?.hasEmbedModel}>
                Cheap AI — embeddings, same language (~30-60s)
              </MenuItem>
              <MenuItem value="llm" disabled={!ollamaStatus?.hasJudgeModel}>
                Smart AI — aya-expanse, any language (slow, a few minutes)
              </MenuItem>
            </TextField>
            <Button
              variant="contained"
              size="small"
              startIcon={<Search />}
              disabled={!dedupModule || scanDuplicates.isPending}
              onClick={() => scanDuplicates.mutate()}
            >
              Scan
            </Button>
          </Stack>
          {ollamaStatus && !ollamaStatus.reachable && (
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
              AI modes need a local Ollama running on this machine (not reachable right now).
            </Typography>
          )}
          {dedupMode === 'llm' && (
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 2 }}>
              Checks up to 20 candidate pairs with AI judgment — can take a few minutes. Catches
              same-meaning duplicates across languages that title matching or embeddings alone would miss.
            </Typography>
          )}

          {scanDuplicates.isPending && (
            <Typography variant="body2" color="text.secondary">
              {dedupMode === 'llm' ? 'Scanning with AI — this may take a few minutes…' : 'Scanning…'}
            </Typography>
          )}
          {scanDuplicates.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              Scan failed. {dedupMode !== 'fast' ? 'Is Ollama running with the right models pulled?' : ''}
            </Alert>
          )}
          {dedupPairs && dedupPairs.length === 0 && (
            <Alert severity="success">No close duplicates found in {dedupLabel.toLowerCase()}.</Alert>
          )}
          {dedupPairs && dedupPairs.length > 0 && (
            <Stack spacing={1}>
              {dedupPairs.map((p) => (
                <Stack
                  key={`${p.id1}-${p.id2}`}
                  direction="row"
                  spacing={1}
                  alignItems="center"
                  sx={{ p: 1, border: 1, borderColor: 'divider', borderRadius: 1 }}
                >
                  <Chip size="small" label={`${Math.round(p.score * 100)}%`} color={p.score >= 0.5 ? 'warning' : 'default'} />
                  <Typography variant="body2" sx={{ flex: 1 }} noWrap>
                    #{p.id1} {p.title1}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    vs
                  </Typography>
                  <Typography variant="body2" sx={{ flex: 1 }} noWrap>
                    #{p.id2} {p.title2}
                  </Typography>
                  <Button
                    size="small"
                    startIcon={<CallMerge />}
                    onClick={() =>
                      setMergeDraft({
                        module: p.module,
                        moduleLabel: dedupLabel,
                        primaryId: p.id1,
                        primaryTitle: p.title1,
                        secondaryId: p.id2,
                        secondaryTitle: p.title2,
                      })
                    }
                  >
                    Merge
                  </Button>
                </Stack>
              ))}
            </Stack>
          )}
        </Paper>

        {!isLoading && notes.length === 0 && (
          <Alert severity="info">No notes to import. The folder is empty.</Alert>
        )}

        {notes.length > 0 && (
          <Paper variant="outlined" sx={{ p: 1.5, mb: 2 }}>
            <Stack direction="row" spacing={1.5} alignItems="center" flexWrap="wrap" useFlexGap>
              <TextField
                select
                size="small"
                label="Type"
                value={typeFilter}
                onChange={(e) => setTypeFilter(e.target.value === 'all' ? 'all' : e.target.value)}
                sx={{ minWidth: 190 }}
              >
                <MenuItem value="all">All types ({notes.length})</MenuItem>
                {typeOptions.map(([code, { label, count }]) => (
                  <MenuItem key={code} value={code}>
                    {code} · {label} ({count})
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                select
                size="small"
                label="Priority"
                value={priorityFilter}
                onChange={(e) =>
                  setPriorityFilter(e.target.value === 'all' ? 'all' : Number(e.target.value))
                }
                sx={{ minWidth: 150 }}
              >
                <MenuItem value="all">All priorities</MenuItem>
                {priorityOptions.map(([p, count]) => (
                  <MenuItem key={p} value={p}>
                    p{p} ({count})
                  </MenuItem>
                ))}
              </TextField>
              {(priorityFilter !== 'all' || typeFilter !== 'all') && (
                <Button
                  size="small"
                  onClick={() => {
                    setTypeFilter('all');
                    setPriorityFilter('all');
                  }}
                >
                  Clear
                </Button>
              )}
              <Box sx={{ flex: 1 }} />
              <Typography variant="caption" color="text.secondary">
                Showing {shown.length} of {filtered.length}
                {filtered.length !== notes.length && ` (${notes.length} in the folder)`}
              </Typography>
            </Stack>
          </Paper>
        )}

        {!isLoading && notes.length > 0 && filtered.length === 0 && (
          <Alert severity="info" sx={{ mb: 2 }}>
            No notes match this filter.
          </Alert>
        )}

        <Stack spacing={2}>
          {shown.map((n) => (
            <Stack key={n.filename} direction="row" spacing={2} alignItems="flex-start">
            <Card variant="outlined" sx={{ flex: 1, minWidth: 0 }}>
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

                {/* Resource type for this R note — the default (top of the page) highlighted;
                    click a chip to override it for this note only. */}
                {n.moduleKey === 'resources' && !done[n.filename] && (
                  <Stack direction="row" spacing={0.5} alignItems="center" flexWrap="wrap" useFlexGap sx={{ mt: 1.5 }}>
                    <Typography variant="caption" color="text.secondary" sx={{ mr: 0.5 }}>
                      Resource type:
                    </Typography>
                    {resourceTypes.map((t) => {
                      const selected = effectiveTypeId(n) === t.id;
                      const isOverride = typeOverrides[n.filename] === t.id;
                      return (
                        <Chip
                          key={t.id}
                          size="small"
                          label={t.code || t.name}
                          title={t.name}
                          color={selected ? 'primary' : 'default'}
                          variant={selected && !isOverride ? 'outlined' : selected ? 'filled' : 'outlined'}
                          onClick={() =>
                            setTypeOverrides((o) => {
                              const next = { ...o };
                              // Clicking the current override clears it (back to the default).
                              if (next[n.filename] === t.id) delete next[n.filename];
                              else next[n.filename] = t.id;
                              return next;
                            })
                          }
                          sx={{ height: 22 }}
                        />
                      );
                    })}
                  </Stack>
                )}

                <Stack direction="row" spacing={2} alignItems="center" sx={{ mt: 2 }}>
                  {(() => {
                    // Require type, priority AND department before importing — a note missing any of
                    // them can't produce a complete record. R notes also need a resource type (it
                    // places the type-bucketed repo folder).
                    const missing = [
                      !n.typeCode && 'type',
                      n.priority == null && 'priority',
                      !n.deptCode && 'department',
                      n.moduleKey === 'resources' && effectiveTypeId(n) == null && 'resource type',
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

                {/* The record this note became, right where it was imported. */}
                {done[n.filename] && (
                  <RecordCard
                    module={done[n.filename].module}
                    record={done[n.filename].record}
                    sx={{ mt: 1.5 }}
                  />
                )}
              </CardContent>
            </Card>
            <SimilarRecordsColumn summary={n.summary} onOpen={(hit) => navigate(`/${hit.module}?select=${hit.id}`)} />
            </Stack>
          ))}
        </Stack>

        {shown.length < filtered.length && (
          <Stack alignItems="center" sx={{ mt: 2 }}>
            <Button variant="outlined" onClick={() => setShownCount((c) => c + PAGE)}>
              Show {Math.min(PAGE, filtered.length - shown.length)} more
              {' · '}
              {filtered.length - shown.length} left
            </Button>
          </Stack>
        )}
      </Box>

      <MergeDialog
        draft={mergeDraft}
        onSwap={() =>
          setMergeDraft((d) =>
            d
              ? {
                  ...d,
                  primaryId: d.secondaryId,
                  primaryTitle: d.secondaryTitle,
                  secondaryId: d.primaryId,
                  secondaryTitle: d.primaryTitle,
                }
              : d,
          )
        }
        onCancel={() => setMergeDraft(null)}
        onMerged={() => {
          setDedupPairs((pairs) =>
            pairs?.filter((p) => p.id1 !== mergeDraft?.secondaryId && p.id2 !== mergeDraft?.secondaryId) ?? null,
          );
          setMergeDraft(null);
        }}
      />
    </Box>
  );
}

/** Existing records whose title is a close fuzzy match for this note's summary (line 1) — a
 * quick way to notice "this was probably already entered" before importing a duplicate. Uses the
 * search API's `~` fuzzy-duplicate mode (character-trigram similarity); same-language matches
 * only for now. */
function SimilarRecordsColumn({
  summary,
  onOpen,
}: {
  summary?: string;
  onOpen: (hit: SearchHit) => void;
}) {
  const q = summary?.trim();
  const { data, isFetching } = useQuery({
    queryKey: ['similar-records', q],
    queryFn: () => searchAll(`~${q}`, 5),
    enabled: Boolean(q),
    staleTime: 5 * 60 * 1000,
  });

  if (!q) {
    return <Box sx={{ width: 260, flexShrink: 0 }} />;
  }

  const hits = data ?? [];
  return (
    <Box sx={{ width: 260, flexShrink: 0, pt: 1 }}>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
        Similar existing records
      </Typography>
      {isFetching && (
        <Typography variant="caption" color="text.secondary">
          Checking…
        </Typography>
      )}
      {!isFetching && hits.length === 0 && (
        <Typography variant="caption" color="text.secondary">
          No close matches.
        </Typography>
      )}
      <Stack spacing={0.5} alignItems="flex-start">
        {hits.map((h) => (
          <Chip
            key={`${h.module}-${h.id}`}
            size="small"
            variant="outlined"
            color={h.score >= 0.5 ? 'warning' : 'default'}
            label={`${h.title} · ${Math.round(h.score * 100)}%`}
            onClick={() => onOpen(h)}
            sx={{ maxWidth: '100%', justifyContent: 'flex-start' }}
          />
        ))}
      </Stack>
    </Box>
  );
}
