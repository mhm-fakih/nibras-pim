import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  IconButton,
  InputAdornment,
  List,
  ListItemButton,
  Menu,
  MenuItem,
  Paper,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
import Bookmark from '@mui/icons-material/Bookmark';
import BookmarkBorder from '@mui/icons-material/BookmarkBorder';
import Download from '@mui/icons-material/Download';
import Edit from '@mui/icons-material/Edit';
import MoreVert from '@mui/icons-material/MoreVert';
import Search from '@mui/icons-material/Search';
import Sync from '@mui/icons-material/Sync';
import SubdirectoryArrowLeft from '@mui/icons-material/SubdirectoryArrowLeft';
import { SideNav } from '../components/SideNav';
import {
  bookmarkQuranSurah,
  copyQuran,
  downloadQuranSurah,
  editQuranAya,
  editQuranSurah,
  getQuran,
  getQuranSurah,
  searchQuran,
  type QuranAya,
  type QuranLinkedNote,
} from '../api/client';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { errorDetail } from '../util/errors';
import { linkProps } from '../util/links';

/*
 * The Quran exegesis and notes module: a surah list (mushaf or revelation order) beside a reader
 * that shows each aya vocalized, its exegesis, its notes (editable here), and the notes linked to
 * it in the records (entity code N + record id), nested as they link to one another.
 */

const ARABIC = '"Amiri", "Noto Naskh Arabic", "Scheherazade New", "Traditional Arabic", serif';
const GREEN = '#1b5e20';
/** Notes longer than this start folded. */
const FOLD = 700;

export function QuranPage() {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const surahNo = Number(params.get('s') ?? '1') || 1;
  const ayaNo = params.get('a') ? Number(params.get('a')) : null;
  const [order, setOrder] = useState<'mushaf' | 'revelation'>('mushaf');
  const [filter, setFilter] = useState('');
  const [query, setQuery] = useState('');
  const [submitted, setSubmitted] = useState('');
  const [menu, setMenu] = useState<HTMLElement | null>(null);
  const [exportMenu, setExportMenu] = useState<HTMLElement | null>(null);

  const overview = useQuery({ queryKey: ['quran'], queryFn: getQuran });
  const copied = (overview.data?.surahs ?? 0) > 0;
  const surah = useQuery({
    queryKey: ['quran', 'surah', surahNo],
    queryFn: () => getQuranSurah(surahNo),
    enabled: copied,
    staleTime: 60_000,
  });
  const hits = useQuery({
    queryKey: ['quran', 'search', submitted],
    queryFn: () => searchQuran(submitted),
    enabled: submitted.trim().length >= 2,
  });
  const markSurah = useMutation({
    mutationFn: (v: { n: number; on: boolean }) => bookmarkQuranSurah(v.n, v.on),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['quran'] }),
  });
  const copy = useMutation({
    mutationFn: copyQuran,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['quran'] }),
  });
  useDocumentTitle(surah.data ? `سورة ${surah.data.name}` : 'Quran');

  // Bring the aya asked for (?a=) into view once its surah is drawn.
  useEffect(() => {
    if (!ayaNo || !surah.data) return;
    const el = document.getElementById(`aya-${ayaNo}`);
    el?.scrollIntoView({ block: 'start', behavior: 'smooth' });
  }, [ayaNo, surah.data]);

  const list = useMemo(() => {
    const rows = (overview.data?.list ?? []).slice();
    if (order === 'revelation') rows.sort((a, b) => (a.revelationOrder ?? 999) - (b.revelationOrder ?? 999));
    const f = filter.trim();
    return f ? rows.filter((r) => r.name.includes(f) || String(r.number) === f) : rows;
  }, [overview.data, order, filter]);

  const open = (s: number, a?: number) => {
    setSubmitted('');
    setParams(a ? { s: String(s), a: String(a) } : { s: String(s) });
  };

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />

      {/* The surahs */}
      <Box sx={{ width: 250, flexShrink: 0, borderRight: 1, borderColor: 'divider', display: 'flex', flexDirection: 'column', minHeight: 0 }}>
        <Box sx={{ p: 1, pb: 0.5 }}>
          <Typography variant="h6" sx={{ fontSize: 18 }}>Quran</Typography>
          <ToggleButtonGroup size="small" exclusive fullWidth value={order} onChange={(_, v) => v && setOrder(v)} sx={{ my: 0.75 }}>
            <ToggleButton value="mushaf">Mushaf</ToggleButton>
            <ToggleButton value="revelation">Revelation</ToggleButton>
          </ToggleButtonGroup>
          <TextField
            size="small"
            fullWidth
            placeholder="Surah name or number"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            inputProps={{ dir: 'auto' }}
          />
        </Box>
        <List dense sx={{ overflowY: 'auto', flex: 1, minHeight: 0 }}>
          {list.map((r) => (
            <ListItemButton key={r.number} selected={r.number === surahNo && !submitted} onClick={() => open(r.number)} sx={{ py: 0.25 }}>
              <Typography sx={{ width: 30, fontSize: 12, color: 'text.secondary' }}>
                {order === 'revelation' ? r.revelationOrder : r.number}
              </Typography>
              <Typography dir="rtl" sx={{ flex: 1, fontFamily: ARABIC, fontSize: 17, textAlign: 'right' }}>{r.name}</Typography>
              {r.surahBookmarked && <Bookmark sx={{ fontSize: 14, color: GREEN, ml: 0.5 }} />}
              <Tooltip title={`${r.ayaCount} ayat${r.withNotes ? ` · ${r.withNotes} with notes` : ''}${r.linked ? ` · ${r.linked} linked notes` : ''}`}>
                <Typography sx={{ fontSize: 11, color: r.withNotes || r.linked ? GREEN : 'text.secondary', ml: 1, minWidth: 28, textAlign: 'right' }}>
                  {r.ayaCount}
                  {(r.withNotes > 0 || r.linked > 0) && '•'}
                </Typography>
              </Tooltip>
            </ListItemButton>
          ))}
        </List>
      </Box>

      {/* The reader */}
      <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ px: 2, py: 1, borderBottom: 1, borderColor: 'divider' }}>
          <TextField
            size="small"
            placeholder="Search the ayat, exegesis and notes"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && setSubmitted(query.trim())}
            inputProps={{ dir: 'auto' }}
            sx={{ flex: 1, maxWidth: 480 }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Search fontSize="small" />
                </InputAdornment>
              ),
            }}
          />
          {submitted && <Button size="small" onClick={() => { setSubmitted(''); setQuery(''); }}>Back to the surah</Button>}
          <Box sx={{ flex: 1 }} />
          {copied && surah.data && (
            <>
              <Button size="small" startIcon={<Download />} onClick={(e) => setExportMenu(e.currentTarget)}>Export</Button>
              <Menu anchorEl={exportMenu} open={!!exportMenu} onClose={() => setExportMenu(null)}>
                {(['md', 'html', 'epub'] as const).map((f) => (
                  <MenuItem key={f} onClick={() => { setExportMenu(null); void downloadQuranSurah(surahNo, f); }}>
                    {f === 'md' ? 'Markdown' : f === 'html' ? 'HTML' : 'EPUB'} — this surah
                  </MenuItem>
                ))}
              </Menu>
            </>
          )}
          <IconButton size="small" aria-label="More" onClick={(e) => setMenu(e.currentTarget)}><MoreVert /></IconButton>
          <Menu anchorEl={menu} open={!!menu} onClose={() => setMenu(null)}>
            <MenuItem onClick={() => { setMenu(null); copy.mutate(); }}>
              <Sync fontSize="small" sx={{ mr: 1 }} /> Copy again from the records
            </MenuItem>
            <MenuItem disabled sx={{ fontSize: 12, whiteSpace: 'normal', maxWidth: 320, opacity: '1 !important' }}>
              Refreshes the text and exegesis from the surah and aya records; notes written here are kept.
            </MenuItem>
          </Menu>
        </Stack>

        <Box sx={{ flex: 1, overflowY: 'auto', px: { xs: 1.5, md: 4 }, py: 2 }}>
          {overview.isLoading && <CircularProgress />}
          {overview.isError && <Alert severity="error">{errorDetail(overview.error, 'Could not load the Quran.')}</Alert>}
          {copy.isError && <Alert severity="error" sx={{ mb: 2 }}>{errorDetail(copy.error, 'The copy failed.')}</Alert>}
          {copy.data && (
            <Alert severity="success" sx={{ mb: 2 }} onClose={() => copy.reset()}>
              Copied {copy.data.surahs} surahs and {copy.data.ayat} ayat{copy.data.skipped ? ` (${copy.data.skipped} ayat skipped: no surah or number)` : ''}.
            </Alert>
          )}
          {overview.data && !overview.data.enabled && (
            <Alert severity="info" sx={{ mb: 2 }}>
              The Quran module is off: turn it on in Settings → Modules to list it first among the modules and to keep the
              source surahs and ayat out of Writings and Notes.
            </Alert>
          )}
          {overview.data && !copied && (
            <Paper variant="outlined" sx={{ p: 3, maxWidth: 560 }}>
              <Typography sx={{ mb: 1 }}>
                Nothing copied yet. The records hold {overview.data.sourceSurahs} surahs and {overview.data.sourceAyat} ayat
                (with their exegesis and notes).
              </Typography>
              <Button variant="contained" startIcon={copy.isPending ? <CircularProgress size={16} color="inherit" /> : <Sync />} disabled={copy.isPending} onClick={() => copy.mutate()}>
                Copy them into the Quran module
              </Button>
            </Paper>
          )}

          {submitted ? (
            <SearchResults loading={hits.isLoading} error={hits.error} hits={hits.data ?? []} q={submitted} onOpen={open} />
          ) : (
            copied && (
              <>
                {surah.isLoading && <CircularProgress />}
                {surah.isError && <Alert severity="error">{errorDetail(surah.error, 'Could not load the surah.')}</Alert>}
                {surah.data && (
                  <Box sx={{ maxWidth: 900, mx: 'auto' }}>
                    <Box dir="rtl" sx={{ textAlign: 'center', mb: 2 }}>
                      <Typography sx={{ fontFamily: ARABIC, fontSize: 34, color: GREEN, lineHeight: 1.4 }}>
                        سورة {surah.data.name}
                        <Tooltip title={surah.data.bookmarked ? 'Remove the surah bookmark' : 'Bookmark this surah'}>
                          <IconButton
                            size="small"
                            sx={{ mr: 1 }}
                            onClick={() => markSurah.mutate({ n: surah.data!.number, on: !surah.data!.bookmarked })}
                          >
                            {surah.data.bookmarked ? <Bookmark sx={{ color: GREEN }} /> : <BookmarkBorder />}
                          </IconButton>
                        </Tooltip>
                      </Typography>
                      <Typography sx={{ fontSize: 13, color: 'text.secondary' }} dir="ltr">
                        Surah {surah.data.number} · {surah.data.ayaCount} ayat
                        {surah.data.revelationOrder ? ` · revealed ${surah.data.revelationOrder}${ordinal(surah.data.revelationOrder)}` : ''}
                      </Typography>
                    </Box>
                    <SurahNotes number={surah.data.number} notes={surah.data.notes} />
                    {surah.data.ayat.map((a) => (
                      <AyaCard key={a.id} aya={a} surahNo={surah.data.number} highlight={a.number === ayaNo} onNavigate={navigate} />
                    ))}
                  </Box>
                )}
              </>
            )
          )}
        </Box>
      </Box>
    </Box>
  );
}

const ordinal = (n: number) => (n % 100 >= 11 && n % 100 <= 13 ? 'th' : ['th', 'st', 'nd', 'rd'][n % 10] ?? 'th');

function SearchResults({
  loading,
  error,
  hits,
  q,
  onOpen,
}: {
  loading: boolean;
  error: unknown;
  hits: { surah: number; surahName: string; aya: number; vocalized: string | null; field: string; snippet: string }[];
  q: string;
  onOpen: (s: number, a?: number) => void;
}) {
  if (loading) return <CircularProgress />;
  if (error) return <Alert severity="error">{errorDetail(error, 'The search failed.')}</Alert>;
  return (
    <Box sx={{ maxWidth: 900, mx: 'auto' }}>
      <Typography sx={{ fontSize: 13, color: 'text.secondary', mb: 1 }}>
        {hits.length === 0 ? `Nothing found for “${q}”.` : `${hits.length}${hits.length >= 200 ? '+' : ''} ayat for “${q}”`}
      </Typography>
      {hits.map((h) => (
        <Paper key={`${h.surah}:${h.aya}`} variant="outlined" sx={{ p: 1.5, mb: 1, cursor: 'pointer', '&:hover': { borderColor: GREEN } }} onClick={() => onOpen(h.surah, h.aya)}>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
            <Chip size="small" label={`${h.surah}:${h.aya}`} />
            <Typography dir="rtl" sx={{ fontFamily: ARABIC, fontSize: 16 }}>{h.surahName}</Typography>
            <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>in the {h.field}</Typography>
          </Stack>
          <Typography dir="rtl" sx={{ fontFamily: ARABIC, fontSize: 19, textAlign: 'right', lineHeight: 1.9 }}>{h.vocalized}</Typography>
          {h.field !== 'text' && (
            <Typography dir="rtl" sx={{ fontSize: 14, textAlign: 'right', color: 'text.secondary', mt: 0.5 }}>{h.snippet}</Typography>
          )}
        </Paper>
      ))}
    </Box>
  );
}

function SurahNotes({ number, notes }: { number: number; notes: string | null }) {
  const qc = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(notes ?? '');
  const save = useMutation({
    mutationFn: () => editQuranSurah(number, draft),
    onSuccess: () => { setEditing(false); void qc.invalidateQueries({ queryKey: ['quran'] }); },
  });
  useEffect(() => setDraft(notes ?? ''), [notes]);
  if (!editing && !notes?.trim()) {
    return (
      <Box sx={{ textAlign: 'center', mb: 2 }}>
        <Button size="small" startIcon={<Edit />} onClick={() => setEditing(true)}>Notes on this surah</Button>
      </Box>
    );
  }
  return (
    <Paper variant="outlined" sx={{ p: 1.5, mb: 2, bgcolor: '#fafaf5' }}>
      {editing ? (
        <NoteEditor value={draft} onChange={setDraft} onSave={() => save.mutate()} onCancel={() => { setEditing(false); setDraft(notes ?? ''); }} saving={save.isPending} />
      ) : (
        <Stack direction="row" alignItems="flex-start">
          <Typography dir="rtl" sx={{ flex: 1, whiteSpace: 'pre-wrap', textAlign: 'right' }}>{notes}</Typography>
          <IconButton size="small" onClick={() => setEditing(true)}><Edit fontSize="small" /></IconButton>
        </Stack>
      )}
    </Paper>
  );
}

function NoteEditor({ value, onChange, onSave, onCancel, saving }: { value: string; onChange: (v: string) => void; onSave: () => void; onCancel: () => void; saving: boolean }) {
  return (
    <Box>
      <TextField
        multiline
        minRows={4}
        maxRows={24}
        fullWidth
        autoFocus
        value={value}
        onChange={(e) => onChange(e.target.value)}
        inputProps={{ dir: 'rtl', style: { textAlign: 'right', lineHeight: 1.8 } }}
        onKeyDown={(e) => {
          if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') onSave();
          if (e.key === 'Escape') onCancel();
        }}
      />
      <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
        <Button size="small" variant="contained" disabled={saving} onClick={onSave}>Save</Button>
        <Button size="small" onClick={onCancel}>Cancel</Button>
        <Typography sx={{ fontSize: 12, color: 'text.secondary', alignSelf: 'center' }}>Ctrl+Enter saves · Esc cancels</Typography>
      </Stack>
    </Box>
  );
}

function AyaCard({ aya, surahNo, highlight, onNavigate }: { aya: QuranAya; surahNo: number; highlight: boolean; onNavigate: (to: string) => void }) {
  const qc = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(aya.notes ?? '');
  const [unfold, setUnfold] = useState(false);
  const save = useMutation({
    mutationFn: (edit: { notes?: string; bookmarked?: boolean }) => editQuranAya(aya.id, edit),
    onSuccess: () => {
      setEditing(false);
      void qc.invalidateQueries({ queryKey: ['quran', 'surah', surahNo] });
      void qc.invalidateQueries({ queryKey: ['quran'], exact: true });
    },
  });
  const notes = aya.notes?.trim() ?? '';
  const long = notes.length > FOLD;

  return (
    <Paper
      id={`aya-${aya.number}`}
      variant="outlined"
      sx={{ p: 2, mb: 1.5, scrollMarginTop: 12, borderColor: highlight ? GREEN : undefined, borderWidth: highlight ? 2 : 1 }}
    >
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
        <Chip size="small" label={`${surahNo}:${aya.number}`} sx={{ fontVariantNumeric: 'tabular-nums' }} />
        <Box sx={{ flex: 1 }} />
        {!editing && (
          <Tooltip title={notes ? 'Edit the notes' : 'Write notes'}>
            <IconButton size="small" onClick={() => { setDraft(aya.notes ?? ''); setEditing(true); }}><Edit fontSize="small" /></IconButton>
          </Tooltip>
        )}
        <Tooltip title={aya.bookmarked ? 'Remove the bookmark' : 'Bookmark'}>
          <IconButton size="small" onClick={() => save.mutate({ bookmarked: !aya.bookmarked })}>
            {aya.bookmarked ? <Bookmark fontSize="small" sx={{ color: GREEN }} /> : <BookmarkBorder fontSize="small" />}
          </IconButton>
        </Tooltip>
      </Stack>

      {/* the aya, vocalized (the record's short description), else plain */}
      <Typography dir="rtl" sx={{ fontFamily: ARABIC, fontSize: 26, lineHeight: 2.1, textAlign: 'right' }}>
        {aya.vocalized?.trim() || aya.text}
        <Box component="span" sx={{ color: GREEN, mx: 1, fontSize: 22 }}>﴿{aya.number}﴾</Box>
      </Typography>

      {aya.exegesis?.trim() && (
        <Box dir="rtl" sx={{ mt: 1.5, pr: 1.5, borderRight: `3px solid ${GREEN}` }}>
          <Typography sx={{ fontSize: 12, color: GREEN, fontWeight: 700, textAlign: 'right' }}>التفسير</Typography>
          <Typography sx={{ fontSize: 16, lineHeight: 1.9, textAlign: 'right', whiteSpace: 'pre-wrap' }}>{aya.exegesis.trim()}</Typography>
        </Box>
      )}

      {editing ? (
        <Box sx={{ mt: 1.5 }}>
          <NoteEditor value={draft} onChange={setDraft} onSave={() => save.mutate({ notes: draft })} onCancel={() => setEditing(false)} saving={save.isPending} />
        </Box>
      ) : (
        notes && (
          <Box dir="rtl" sx={{ mt: 1.5, p: 1.25, bgcolor: '#fafaf5', borderRadius: 1 }}>
            <Typography sx={{ fontSize: 12, color: 'text.secondary', fontWeight: 700, textAlign: 'right' }}>ملاحظات</Typography>
            <Typography sx={{ fontSize: 15, lineHeight: 1.85, textAlign: 'right', whiteSpace: 'pre-wrap' }}>
              {long && !unfold ? `${notes.slice(0, FOLD)}…` : notes}
            </Typography>
            {long && (
              <Button size="small" onClick={() => setUnfold((u) => !u)}>{unfold ? 'Show less' : `Show all (${notes.length.toLocaleString()} characters)`}</Button>
            )}
          </Box>
        )
      )}

      {aya.linked.length > 0 && (
        <Box sx={{ mt: 1.5 }}>
          <Typography sx={{ fontSize: 12, color: 'text.secondary', fontWeight: 700 }} dir="ltr">
            Linked notes ({countNotes(aya.linked)})
          </Typography>
          <LinkedNotes notes={aya.linked} depth={0} onNavigate={onNavigate} />
        </Box>
      )}
    </Paper>
  );
}

const countNotes = (ns: QuranLinkedNote[]): number => ns.reduce((n, x) => n + 1 + countNotes(x.children), 0);

function LinkedNotes({ notes, depth, onNavigate }: { notes: QuranLinkedNote[]; depth: number; onNavigate: (to: string) => void }) {
  return (
    <Box sx={{ mt: 0.5 }}>
      {notes.map((n) => {
        const title = n.title?.trim() || `Note ${n.id}`;
        const text = n.text?.trim();
        return (
          <Box key={n.id} sx={{ ml: depth ? 2.5 : 0, mt: 0.75, pl: 1.25, borderLeft: '2px solid #c8e6c9' }}>
            <Stack direction="row" spacing={0.5} alignItems="center">
              {depth > 0 && <SubdirectoryArrowLeft sx={{ fontSize: 14, color: 'text.disabled', transform: 'scaleX(-1)' }} />}
              <Typography
                {...linkProps(`/notes?select=${n.id}`, () => onNavigate(`/notes?select=${n.id}`))}
                dir="auto"
                sx={{ fontWeight: 600, fontSize: 14, color: GREEN, textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}
              >
                {title}
              </Typography>
              {n.type && <Typography sx={{ fontSize: 11, color: 'text.secondary' }}>· {n.type}</Typography>}
            </Stack>
            {text && text !== title && (
              <Typography dir="auto" sx={{ fontSize: 14, whiteSpace: 'pre-wrap', textAlign: 'start', lineHeight: 1.75 }}>
                {text.length > 1200 ? `${text.slice(0, 1200)}…` : text}
              </Typography>
            )}
            {n.notes?.trim() && (
              <Typography dir="auto" sx={{ fontSize: 13, color: 'text.secondary', whiteSpace: 'pre-wrap', textAlign: 'start' }}>
                {n.notes.trim().length > 600 ? `${n.notes.trim().slice(0, 600)}…` : n.notes.trim()}
              </Typography>
            )}
            {n.children.length > 0 && <LinkedNotes notes={n.children} depth={depth + 1} onNavigate={onNavigate} />}
          </Box>
        );
      })}
    </Box>
  );
}
