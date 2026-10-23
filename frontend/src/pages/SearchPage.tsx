import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Chip,
  IconButton,
  InputAdornment,
  List,
  ListItem,
  ListItemText,
  ListItemButton,
  MenuItem,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import FolderOpenIcon from '@mui/icons-material/FolderOpen';
import {
  FILE_SOURCES,
  FileHit,
  getFileSearchConfig,
  getSearchIndexStatus,
  openFoundFile,
  searchEverything,
  searchFiles,
  SearchHit,
} from '../api/client';
import { SideNav } from '../components/SideNav';
import { ENTITY_BY_KEY } from '../config/entities';

export function SearchPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const q = searchParams.get('q') ?? '';
  const [input, setInput] = useState(q);

  useEffect(() => {
    setInput(q);
  }, [q]);

  const { data, isFetching } = useQuery({
    queryKey: ['search', q],
    queryFn: () => searchEverything(q, 200),
    enabled: q.trim().length > 0,
  });
  const hits = data?.records ?? [];
  // A ~query asks "which existing RECORD is this a duplicate of" — a note file is not a candidate
  // answer, so the local half is dropped rather than shown as a near-miss that cannot be merged.
  const notes = dedupeQuery(q) ? [] : data?.notes ?? [];

  // Only needed to explain a zero-result search, so it's fetched lazily alongside one.
  const { data: indexStatus } = useQuery({
    queryKey: ['search-index-status'],
    queryFn: getSearchIndexStatus,
    enabled: q.trim().length > 0,
    staleTime: 300_000,
  });

  const grouped = useMemo(() => {
    const map = new Map<string, SearchHit[]>();
    for (const h of hits) {
      if (!map.has(h.module)) map.set(h.module, []);
      map.get(h.module)!.push(h);
    }
    return [...map.entries()];
  }, [hits]);

  const dedupe = q.trimStart().startsWith('~');

  // Files: the free text only — the record tokens (p4, ?code, #type…) mean nothing to a file.
  const fileQuery = useMemo(
    () =>
      q
        .trim()
        .split(/\s+/)
        .filter((w) => w && !/^[#?@=*]/.test(w) && !/^[pP]\d+$/.test(w))
        .join(' '),
    [q],
  );
  const [fileSources, setFileSources] = useState<string[]>([]);
  const [fileKind, setFileKind] = useState('');
  const { data: fileConfig } = useQuery({ queryKey: ['file-search-config'], queryFn: getFileSearchConfig, staleTime: 300_000 });
  const coveredSources = FILE_SOURCES.filter((s) => fileConfig?.sources[s]?.names);
  const { data: files, isFetching: filesFetching } = useQuery({
    queryKey: ['search-files', fileQuery, fileSources, fileKind],
    queryFn: () => searchFiles(fileQuery, { sources: fileSources, exts: FILE_KINDS[fileKind]?.exts, limit: 100 }),
    enabled: fileQuery.length > 0 && !dedupe,
  });
  const fileHits = files?.hits ?? [];

  // Highlight only the free-text words — drop the leading ~ and command tokens (#?@=* and p3).
  const terms = useMemo(
    () =>
      q
        .replace(/^\s*~/, '')
        .trim()
        .split(/\s+/)
        .filter((w) => !/^[#?@=*]/.test(w) && !/^[pP]\d+$/.test(w))
        .map((w) => w.toLowerCase())
        .filter((w) => w.length >= 2),
    [q],
  );

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: 4, maxWidth: 900 }}>
        <Typography variant="h4" sx={{ fontWeight: 700, mb: 0.5 }}>
          Search
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Full text plus filter tokens: <code>p4</code> priority · <code>?code</code> status ·{' '}
          <code>#code</code> type · <code>@code</code> context · <code>*</code> bookmarked (e.g.{' '}
          <code>p4 linux</code>). Start with <code>~</code> to find near-duplicates (e.g.{' '}
          <code>~ IT system hand over</code>).
        </Typography>
        <TextField
          fullWidth
          size="small"
          placeholder="e.g.  p4 linux   ·   ?done report"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && setSearchParams(input.trim() ? { q: input.trim() } : {})}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" />
              </InputAdornment>
            ),
          }}
          sx={{ mb: 3 }}
        />

        {dedupe && hits.length > 0 && (
          <Typography variant="overline" color="warning.main" sx={{ display: 'block', mb: 1 }}>
            Possible duplicates — review before adding a new record
          </Typography>
        )}

        {q && !isFetching && !filesFetching && hits.length === 0 && notes.length === 0 && fileHits.length === 0 && (
          indexStatus?.indexed === 0 ? (
            // Distinguish "genuinely no matches" from "the index was never built" — the latter
            // silently returns nothing on a fresh install over an existing database, while each
            // module's own search keeps working, which reads as a broken global search.
            <Alert severity="warning">
              The search index is empty, so global search can&rsquo;t match anything yet — this is
              normal on a new install pointed at an existing database. Build it once with{' '}
              <strong>Rebuild search index</strong> on the <Link to="/admin">Admin</Link> page.
            </Alert>
          ) : (
            <Typography color="text.secondary">
              {dedupe ? 'No similar records found.' : `No matches for “${q}”.`}
            </Typography>
          )
        )}

        {grouped.map(([module, list]) => (
          <Box key={module} sx={{ mb: 3 }}>
            <Typography variant="overline" color="text.secondary">
              {ENTITY_BY_KEY[module]?.label ?? module} · {list.length}
            </Typography>
            <List dense>
              {list.map((h) => (
                <ListItemButton
                  key={`${h.module}-${h.id}`}
                  onClick={() => navigate(`/${h.module}?select=${h.id}`)}
                  divider
                  alignItems="flex-start"
                >
                  <ListItemText
                    primary={h.title || '(untitled)'}
                    primaryTypographyProps={{ dir: 'auto', noWrap: true, fontWeight: 600, sx: { textAlign: 'start' } }}
                    secondary={h.snippet ? <Highlight text={h.snippet} terms={terms} /> : undefined}
                    secondaryTypographyProps={{ dir: 'auto', component: 'span', sx: { display: 'block', textAlign: 'start' } }}
                  />
                  {dedupe && (
                    <Chip
                      size="small"
                      color="warning"
                      label={`${Math.round(h.score * 100)}%`}
                      sx={{ ml: 1, mt: 0.5 }}
                    />
                  )}
                  <Chip size="small" variant="outlined" label={`#${h.id}`} sx={{ ml: 1, mt: 0.5 }} />
                </ListItemButton>
              ))}
            </List>
          </Box>
        ))}

        {notes.length > 0 && (
          <Box sx={{ mb: 3 }}>
            <Typography variant="overline" color="text.secondary">
              Local notes · {notes.length}
            </Typography>
            <List dense>
              {notes.map((n) => (
                <ListItemButton
                  key={n.filename}
                  onClick={() => navigate(`/local-notes?open=${encodeURIComponent(n.filename)}`)}
                  divider
                  alignItems="flex-start"
                >
                  <ListItemText
                    primary={n.title || n.filename}
                    primaryTypographyProps={{ dir: 'auto', noWrap: true, fontWeight: 600, sx: { textAlign: 'start' } }}
                    secondary={n.snippet ? <Highlight text={n.snippet} terms={terms} /> : undefined}
                    secondaryTypographyProps={{ dir: 'auto', component: 'span', sx: { display: 'block', textAlign: 'start' } }}
                  />
                  {n.courseName && (
                    <Chip
                      size="small"
                      variant="outlined"
                      label={n.courseName}
                      sx={{ ml: 1, mt: 0.5 }}
                    />
                  )}
                  {/* A file has no id to show, so its stamp stands in: it is the archive's own
                      stable handle for a note and stays greppable when the name changes. */}
                  <Chip size="small" variant="outlined" label={n.stamp} sx={{ ml: 1, mt: 0.5 }} />
                </ListItemButton>
              ))}
            </List>
          </Box>
        )}

        {fileQuery && !dedupe && coveredSources.length > 0 && (files?.total || fileSources.length > 0 || fileKind) ? (
          <Box sx={{ mb: 3 }}>
            <Stack direction="row" alignItems="center" spacing={1} flexWrap="wrap" useFlexGap sx={{ mb: 0.5 }}>
              <Typography variant="overline" color="text.secondary" sx={{ mr: 1 }}>
                Files · {files?.total ?? 0}
                {files && files.total > fileHits.length ? ` (first ${fileHits.length})` : ''}
              </Typography>
              {coveredSources.map((s) => (
                <Chip
                  key={s}
                  size="small"
                  label={SOURCE_LABEL[s]}
                  color={fileSources.includes(s) ? 'primary' : 'default'}
                  variant={fileSources.includes(s) ? 'filled' : 'outlined'}
                  onClick={() =>
                    setFileSources((cur) => (cur.includes(s) ? cur.filter((x) => x !== s) : [...cur, s]))
                  }
                />
              ))}
              <TextField
                select
                size="small"
                label="Type"
                value={fileKind}
                onChange={(e) => setFileKind(e.target.value)}
                sx={{ minWidth: 140 }}
              >
                <MenuItem value="">Any type</MenuItem>
                {Object.entries(FILE_KINDS).map(([k, v]) => (
                  <MenuItem key={k} value={k}>
                    {v.label}
                  </MenuItem>
                ))}
              </TextField>
            </Stack>
            <List dense>
              {fileHits.map((f) => (
                <FileRow key={f.path} f={f} terms={terms} onOwner={navigate} />
              ))}
            </List>
            {fileHits.length === 0 && !filesFetching && (
              <Typography color="text.secondary" variant="body2">
                No files match with these filters.
              </Typography>
            )}
          </Box>
        ) : null}
      </Box>
    </Box>
  );
}

const SOURCE_LABEL: Record<string, string> = {
  repo1: 'Repo 1',
  repo2: 'Repo 2',
  repo3: 'Repo 3',
  notes: 'Note files',
};

const FILE_KINDS: Record<string, { label: string; exts: string[] }> = {
  pdf: { label: 'PDF', exts: ['pdf'] },
  word: { label: 'Documents', exts: ['doc', 'docx', 'odt', 'rtf', 'epub', 'djvu'] },
  sheet: { label: 'Spreadsheets', exts: ['xls', 'xlsx', 'ods', 'csv'] },
  slides: { label: 'Slides', exts: ['ppt', 'pptx', 'odp'] },
  text: { label: 'Text', exts: ['txt', 'md', 'tex', 'html', 'htm', 'json', 'xml'] },
  audio: { label: 'Audio', exts: ['mp3', 'm4a', 'wav', 'ogg', 'opus', 'flac', 'aac', 'amr'] },
  video: { label: 'Video', exts: ['mp4', 'mkv', 'webm', 'avi', 'mov', '3gp'] },
  image: { label: 'Images', exts: ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'heic', 'tif', 'tiff', 'svg'] },
};

function formatSize(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${Math.round(n / 1024)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

/** One found file: name, where it lives (record or note), the matched text, and open actions. */
function FileRow({ f, terms, onOwner }: { f: FileHit; terms: string[]; onOwner: (to: string) => void }) {
  const folder = f.rel.includes('/') ? f.rel.slice(0, f.rel.lastIndexOf('/')) : '';
  const owner = f.ownerModule && f.ownerId != null
    ? { label: `${f.ownerLabel ?? f.ownerModule} #${f.ownerId}`, to: `/${f.ownerModule}?select=${f.ownerId}` }
    : f.ownerNote
      ? { label: 'Note', to: `/local-notes?open=${encodeURIComponent(f.ownerNote)}` }
      : null;
  return (
    <ListItem
      divider
      alignItems="flex-start"
      secondaryAction={
        <Stack direction="row">
          <Tooltip title="Open the file">
            <IconButton size="small" onClick={() => openFoundFile(f.path)}>
              <OpenInNewIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title="Open its folder">
            <IconButton size="small" onClick={() => openFoundFile(f.path, true)}>
              <FolderOpenIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </Stack>
      }
      sx={{ pr: 10 }}
    >
      <ListItemText
        primary={<Highlight text={f.name} terms={terms} />}
        primaryTypographyProps={{ dir: 'auto', noWrap: true, fontWeight: 600, sx: { textAlign: 'start' } }}
        secondary={
          <>
            <Box component="span" sx={{ display: 'block', color: 'text.disabled', fontFamily: 'monospace', fontSize: 12 }} dir="ltr">
              {SOURCE_LABEL[f.source]} · {folder || '/'} · {formatSize(f.size)}
              {f.nameOnly ? ' · name match' : ''}
            </Box>
            {f.snippet && <Marked text={f.snippet} />}
          </>
        }
        secondaryTypographyProps={{ dir: 'auto', component: 'span', sx: { display: 'block', textAlign: 'start' } }}
      />
      {owner && (
        <Chip size="small" variant="outlined" label={owner.label} onClick={() => onOwner(owner.to)} sx={{ ml: 1, mt: 0.5 }} />
      )}
    </ListItem>
  );
}

/** A snippet from the file index, its matches already marked with \u0001…\u0002. */
function Marked({ text }: { text: string }) {
  const parts = text.split(/(\u0001[^\u0002]*\u0002)/);
  return (
    <Box component="span" sx={{ display: 'block' }}>
      {parts.map((p, i) =>
        p.startsWith('\u0001') ? (
          <Box key={i} component="mark" sx={{ bgcolor: 'warning.light', px: 0.25, borderRadius: 0.5 }}>
            {p.slice(1, -1)}
          </Box>
        ) : (
          <span key={i}>{p.replace(/\s+/g, ' ')}</span>
        ),
      )}
    </Box>
  );
}

/** A leading ~ asks for near-duplicate RECORDS; note files are not candidates. */
function dedupeQuery(q: string): boolean {
  return q.trimStart().startsWith('~');
}

/** Renders a snippet with the matched query terms highlighted. */
function Highlight({ text, terms }: { text: string; terms: string[] }) {
  if (terms.length === 0) return <>{text}</>;
  const escaped = terms.map((t) => t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
  const re = new RegExp(`(${escaped.join('|')})`, 'gi');
  const lower = new Set(terms.map((t) => t.toLowerCase()));
  const parts = text.split(re);
  return (
    <>
      {parts.map((part, i) =>
        lower.has(part.toLowerCase()) ? (
          <Box key={i} component="mark" sx={{ bgcolor: 'warning.light', px: 0.25, borderRadius: 0.5 }}>
            {part}
          </Box>
        ) : (
          <span key={i}>{part}</span>
        ),
      )}
    </>
  );
}
