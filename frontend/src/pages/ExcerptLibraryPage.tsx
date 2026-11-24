import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  LinearProgress,
  List,
  ListItemButton,
  Stack,
  Tab,
  Tabs,
  TextField,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import ChevronLeft from '@mui/icons-material/ChevronLeft';
import AutoStories from '@mui/icons-material/AutoStories';
import Casino from '@mui/icons-material/Casino';
import DoNotDisturbOn from '@mui/icons-material/DoNotDisturbOn';
import OpenInNew from '@mui/icons-material/OpenInNew';
import Restore from '@mui/icons-material/Restore';
import Upgrade from '@mui/icons-material/Upgrade';
import { SideNav } from '../components/SideNav';
import {
  generateSamples,
  getSample,
  getSampleOverview,
  listSamples,
  mediaUrl,
  sampleAction,
  saveSample,
  type SampleDetail,
  type SampleRow,
} from '../api/client';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { errorDetail } from '../util/errors';
import { linkProps } from '../util/links';
import { dirOf } from '../util/rtl';

/*
 * The excerpt library: 2–3 pages taken at random from the body of the ebk books, to read and give
 * a one-line summary and details, then promote to a real Excerpt or set aside. "Make N more" draws
 * new books at random, in the background.
 */

const STATUSES = [
  { key: 'new', label: 'To summarize' },
  { key: 'summarized', label: 'Summarized' },
  { key: 'promoted', label: 'Promoted' },
  { key: 'skipped', label: 'Set aside' },
  { key: 'all', label: 'All' },
] as const;

/** "pp. 120–122 of 362", or "at 34–36% of the text" for an ebook. */
export function placeOf(r: Pick<SampleRow, 'format' | 'pageFrom' | 'pageTo' | 'totalPages'>): string {
  if (r.pageFrom == null || r.pageTo == null) return '';
  if (r.totalPages === 1000 && r.format !== 'pdf' && r.format !== 'djvu') {
    return `at ${Math.round(r.pageFrom / 10)}–${Math.round(r.pageTo / 10)}% of the text`;
  }
  const pp = r.pageFrom === r.pageTo ? `p. ${r.pageFrom}` : `pp. ${r.pageFrom}–${r.pageTo}`;
  return r.totalPages ? `${pp} of ${r.totalPages}` : pp;
}

const rtlSx = (s?: string | null) => ({ textAlign: dirOf(s) === 'rtl' ? ('right' as const) : ('left' as const) });

export function ExcerptLibraryPage() {
  useDocumentTitle('Excerpt library');
  const qc = useQueryClient();
  const [params, setParams] = useSearchParams();
  const [status, setStatus] = useState<string>(params.get('status') ?? 'new');
  const [q, setQ] = useState('');
  const [count, setCount] = useState(50);
  const selected = params.get('id') ? Number(params.get('id')) : null;
  // On a phone the list and the sample take the whole width in turn, as in the record pages.
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));

  const overview = useQuery({
    queryKey: ['excerpt-library', 'overview'],
    queryFn: getSampleOverview,
    refetchInterval: (query) => (query.state.data?.job.running ? 2500 : false),
  });
  const running = !!overview.data?.job.running;
  const list = useQuery({
    queryKey: ['excerpt-library', 'list', status, q],
    queryFn: () => listSamples(status, q),
    refetchInterval: running ? 5000 : false,
  });
  // When a batch ends, the list and counts are refreshed once more.
  useEffect(() => {
    if (!running) void qc.invalidateQueries({ queryKey: ['excerpt-library', 'list'] });
  }, [running, qc]);

  const generate = useMutation({
    mutationFn: () => generateSamples(count),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['excerpt-library', 'overview'] }),
  });

  const rows = list.data ?? [];
  const select = (id: number | null) => {
    const next = new URLSearchParams(params);
    if (id == null) next.delete('id');
    else next.set('id', String(id));
    setParams(next, { replace: true });
  };
  // The next sample after the one shown, for "Save & next".
  const nextOf = (id: number) => {
    const i = rows.findIndex((r) => r.id === id);
    return i >= 0 && i + 1 < rows.length ? rows[i + 1].id : null;
  };

  const c = overview.data?.counts;
  const job = overview.data?.job;

  return (
    <Box sx={{ display: 'flex', height: '100dvh', overflow: 'hidden' }}>
      <SideNav />
      {(!isMobile || !selected) && (
      <Box sx={{ width: { xs: '100%', md: 380 }, flexShrink: 0, borderRight: 1, borderColor: 'divider', display: 'flex', flexDirection: 'column', minHeight: 0 }}>
        {/* On a phone the menu button floats at the top left: the title starts after it. */}
        <Box sx={{ p: 1.5, pb: 0.5, pl: { xs: 8, md: 1.5 } }}>
          <Typography variant="h6" sx={{ fontSize: 18 }}>Excerpt library</Typography>
          {c && (
            <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>
              {c.total} samples from {c.sampledBooks - c.failed} of {c.books.toLocaleString()} ebooks · {c.fresh} to summarize · {c.promoted} promoted
              {c.failed ? ` · ${c.failed} books had no readable file` : ''}
            </Typography>
          )}
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 1 }}>
            <TextField
              size="small"
              type="number"
              value={count}
              onChange={(e) => setCount(Math.max(1, Math.min(500, Number(e.target.value) || 1)))}
              sx={{ width: 84 }}
              inputProps={{ min: 1, max: 500 }}
            />
            <Button
              variant="contained"
              size="small"
              startIcon={running ? <CircularProgress size={14} color="inherit" /> : <Casino />}
              disabled={running || generate.isPending}
              onClick={() => generate.mutate()}
            >
              {running ? 'Making…' : 'Make more'}
            </Button>
          </Stack>
          {generate.isError && <Typography color="error" sx={{ fontSize: 12 }}>{errorDetail(generate.error, 'Could not start.')}</Typography>}
          {job && (job.running || job.finished) && (
            <Box sx={{ mt: 1 }}>
              <LinearProgress variant="determinate" value={job.wanted ? (job.made / job.wanted) * 100 : 0} />
              <Typography sx={{ fontSize: 12, color: 'text.secondary', mt: 0.25 }} noWrap title={job.current ?? ''}>
                {job.running
                  ? `${job.made} of ${job.wanted} made · ${job.tried} books tried${job.current ? ` · ${job.current}` : ''}`
                  : `Last batch: ${job.made} of ${job.wanted} made, ${job.failed} books without a readable file`}
              </Typography>
            </Box>
          )}
          <Tabs value={status} onChange={(_, v) => { setStatus(v); select(null); }} variant="scrollable" sx={{ minHeight: 36, mt: 0.5 }}>
            {STATUSES.map((s) => <Tab key={s.key} value={s.key} label={s.label} sx={{ minHeight: 36, px: 1, fontSize: 12 }} />)}
          </Tabs>
          <TextField size="small" fullWidth placeholder="Search title, author, summary, text" value={q} onChange={(e) => setQ(e.target.value)} inputProps={{ dir: 'auto' }} sx={{ mt: 0.5 }} />
        </Box>
        <List dense sx={{ overflowY: 'auto', flex: 1, minHeight: 0 }}>
          {list.isLoading && <CircularProgress size={20} sx={{ m: 2 }} />}
          {!list.isLoading && rows.length === 0 && (
            <Typography sx={{ p: 2, fontSize: 13, color: 'text.secondary' }}>
              {status === 'new' ? 'Nothing to summarize. Make more samples above.' : 'Nothing here.'}
            </Typography>
          )}
          {rows.map((r) => (
            <ListItemButton key={r.id} selected={r.id === selected} onClick={() => select(r.id)} sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.25, py: 0.75 }}>
              <BookCover link={r.coverLink} width={36} />
              <Box sx={{ flex: 1, minWidth: 0 }}>
              <Typography dir="auto" sx={{ fontSize: 14, fontWeight: 600, ...rtlSx(r.book) }} noWrap>{r.book ?? `Resource ${r.bookId}`}</Typography>
              <Typography dir="auto" sx={{ fontSize: 12.5, color: r.summary ? 'text.primary' : 'text.secondary', ...rtlSx(r.summary ?? r.preview) }} noWrap>
                {r.summary || r.preview?.replace(/\s+/g, ' ') || (r.hasText ? '' : 'Pages only (no text layer)')}
              </Typography>
              <Typography sx={{ fontSize: 11, color: 'text.disabled' }}>
                {r.format.toUpperCase()} · {placeOf(r)}{r.hasText ? '' : ' · image only'}
              </Typography>
              </Box>
            </ListItemButton>
          ))}
        </List>
      </Box>
      )}

      {(!isMobile || selected) && (
      <Box sx={{ flex: 1, minWidth: 0, overflowY: 'auto' }}>
        {isMobile && (
          <Box sx={{ position: 'sticky', top: 0, zIndex: 2, bgcolor: 'background.default', borderBottom: 1, borderColor: 'divider', pl: 8, py: 1 }}>
            <Button size="small" startIcon={<ChevronLeft />} onClick={() => select(null)}>Back to the list</Button>
          </Box>
        )}
        {selected ? (
          <SampleView key={selected} id={selected} onNext={(id) => select(nextOf(id))} />
        ) : (
          <Box sx={{ p: 4, color: 'text.secondary', maxWidth: 640 }}>
            <AutoStories sx={{ fontSize: 40, mb: 1 }} />
            <Typography>
              Each sample is 2–3 pages from somewhere in the body of one of your ebooks — past the opening pages, before the
              notes and indexes. Read it, give it a one-line summary and any details, then promote it to a real Excerpt of its
              book or set it aside. Pick one on the left.
            </Typography>
          </Box>
        )}
      </Box>
      )}
    </Box>
  );
}

function SampleView({ id, onNext }: { id: number; onNext: (id: number) => void }) {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const sample = useQuery({ queryKey: ['excerpt-library', 'sample', id], queryFn: () => getSample(id) });
  const [summary, setSummary] = useState('');
  const [details, setDetails] = useState('');
  const [view, setView] = useState<'text' | 'pages'>('text');
  const d = sample.data;

  useEffect(() => {
    if (!d) return;
    setSummary(d.row.summary ?? '');
    setDetails(d.description ?? '');
    setView(d.text ? 'text' : 'pages');
  }, [d]);

  const refresh = (fresh?: SampleDetail) => {
    if (fresh) qc.setQueryData(['excerpt-library', 'sample', fresh.row.id], fresh);
    void qc.invalidateQueries({ queryKey: ['excerpt-library', 'list'] });
    void qc.invalidateQueries({ queryKey: ['excerpt-library', 'overview'] });
  };
  const save = useMutation({ mutationFn: () => saveSample(id, summary, details), onSuccess: (x) => refresh(x) });
  const act = useMutation({
    mutationFn: (a: 'skip' | 'restore' | 'another' | 'promote') => sampleAction(id, a),
    onSuccess: (x, a) => {
      refresh(x);
      if (a === 'another' && x.row.id !== id) navigate(`/excerpt-library?id=${x.row.id}`, { replace: true });
    },
  });
  const dirty = !!d && (summary !== (d.row.summary ?? '') || details !== (d.description ?? ''));
  const busy = save.isPending || act.isPending;

  if (sample.isLoading) return <CircularProgress sx={{ m: 4 }} />;
  if (sample.isError || !d) return <Alert severity="error" sx={{ m: 2 }}>{errorDetail(sample.error, 'Could not load the sample.')}</Alert>;
  const r = d.row;
  const pageUrls = d.pageLink ? Array.from({ length: d.pages }, (_, i) => mediaUrl(`${d.pageLink}&n=${i + 1}&w=1280`)) : [];

  return (
    <Box sx={{ p: { xs: 1.5, md: 2 }, maxWidth: 1000, mx: 'auto' }}>
      <Stack direction="row" spacing={1.5} alignItems="flex-start" sx={{ mb: 1 }}>
        <BookCover link={r.coverLink} width={84} />
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography
            {...linkProps(`/resources?select=${r.bookId}`, () => navigate(`/resources?select=${r.bookId}`))}
            dir="auto"
            sx={{ fontSize: { xs: 18, md: 20 }, fontWeight: 600, overflowWrap: 'anywhere', color: 'text.primary', textDecoration: 'none', display: 'block', ...rtlSx(r.book), '&:hover': { textDecoration: 'underline' } }}
          >
            {r.book ?? `Resource ${r.bookId}`}
          </Typography>
          <Typography sx={{ fontSize: 13, color: 'text.secondary', overflowWrap: 'anywhere' }}>
            {[r.author, r.format.toUpperCase(), placeOf(r), d.sourceFile].filter(Boolean).join(' · ')}
          </Typography>
        </Box>
        <Chip size="small" label={r.status === 'skipped' ? 'set aside' : r.status} color={r.status === 'promoted' ? 'success' : r.status === 'summarized' ? 'primary' : 'default'} />
      </Stack>

      <Stack direction="row" spacing={1} sx={{ mb: 1.5, flexWrap: 'wrap', rowGap: 1 }}>
        {d.sourceLink && (
          <Button size="small" startIcon={<OpenInNew />} href={mediaUrl(d.sourceLink)} target="_blank" rel="noopener">
            Open the book{r.format === 'pdf' ? ' at this page' : ''}
          </Button>
        )}
        {r.status !== 'promoted' && (
          <Tooltip title="Set this one aside and take 2–3 pages from another place in the same book">
            <span><Button size="small" startIcon={<Casino />} disabled={busy} onClick={() => act.mutate('another')}>Another place</Button></span>
          </Tooltip>
        )}
        {r.status === 'skipped' ? (
          <Button size="small" startIcon={<Restore />} disabled={busy} onClick={() => act.mutate('restore')}>Bring back</Button>
        ) : r.status !== 'promoted' ? (
          <Button size="small" startIcon={<DoNotDisturbOn />} disabled={busy} onClick={() => act.mutate('skip')}>Set aside</Button>
        ) : null}
        {r.excerptId ? (
          <Button size="small" color="success" {...linkProps(`/excerpts?select=${r.excerptId}`, () => navigate(`/excerpts?select=${r.excerptId}`))}>
            Open the excerpt
          </Button>
        ) : (
          <Tooltip title={r.summary ? 'Make it an Excerpt of its book: the summary, details, text and pages go with it' : 'Save a one-line summary first'}>
            <span>
              <Button size="small" variant="outlined" color="success" startIcon={<Upgrade />} disabled={busy || !r.summary || dirty} onClick={() => act.mutate('promote')}>
                Promote to an Excerpt
              </Button>
            </span>
          </Tooltip>
        )}
      </Stack>
      {act.isError && <Alert severity="error" sx={{ mb: 1 }}>{errorDetail(act.error, 'That did not work.')}</Alert>}

      {/* The summary first: it is what this page is for. */}
      <Box sx={{ p: 1.5, mb: 2, border: 1, borderColor: 'divider', borderRadius: 1, bgcolor: 'background.paper' }}>
        <TextField
          label="One-line summary"
          fullWidth
          size="small"
          value={summary}
          onChange={(e) => setSummary(e.target.value.replace(/\n/g, ' '))}
          inputProps={{ maxLength: 255, dir: dirOf(summary), style: rtlSx(summary) }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              save.mutate();
            }
          }}
        />
        <TextField
          label="Details (optional)"
          fullWidth
          multiline
          minRows={2}
          maxRows={10}
          value={details}
          onChange={(e) => setDetails(e.target.value)}
          inputProps={{ dir: dirOf(details || summary), style: rtlSx(details || summary) }}
          sx={{ mt: 1 }}
          onKeyDown={(e) => {
            if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') save.mutate();
          }}
        />
        <Stack direction="row" alignItems="center" sx={{ mt: 1, flexWrap: 'wrap', gap: 1 }}>
          <Button size="small" variant="contained" disabled={busy || !dirty} onClick={() => save.mutate()}>Save</Button>
          <Button
            size="small"
            disabled={busy}
            onClick={() => (dirty ? save.mutateAsync().then(() => onNext(id)) : onNext(id))}
          >
            {dirty ? 'Save & next' : 'Next'}
          </Button>
          <Typography sx={{ fontSize: 12, color: 'text.secondary', display: { xs: 'none', md: 'block' } }}>Enter saves the summary · Ctrl+Enter saves the details</Typography>
          {save.isError && <Typography color="error" sx={{ fontSize: 12 }}>{errorDetail(save.error, 'Not saved.')}</Typography>}
        </Stack>
      </Box>

      {(d.text || pageUrls.length > 0) && (
        <Tabs value={view} onChange={(_, v) => setView(v)} sx={{ minHeight: 36, mb: 1 }}>
          <Tab value="text" label="Text" disabled={!d.text} sx={{ minHeight: 36 }} />
          <Tab value="pages" label={`Pages${pageUrls.length ? ` (${pageUrls.length})` : ''}`} disabled={pageUrls.length === 0} sx={{ minHeight: 36 }} />
        </Tabs>
      )}
      {view === 'text' && d.text && <SampleText text={d.text} />}
      {view === 'pages' && pageUrls.map((u, i) => (
        <Box key={u} component="img" src={u} alt={`Page ${(r.pageFrom ?? 1) + i}`} loading="lazy"
          sx={{ display: 'block', width: '100%', maxWidth: 900, mx: 'auto', mb: 1.5, boxShadow: 2, bgcolor: '#fff' }} />
      ))}
      {!d.text && pageUrls.length === 0 && <Alert severity="warning">Neither text nor pages are available for this sample.{d.error ? ` ${d.error}` : ''}</Alert>}
    </Box>
  );
}

/** The book's cover (signed link, no sign-in needed); nothing when the book has none. */
function BookCover({ link, width }: { link: string | null | undefined; width: number }) {
  const [failed, setFailed] = useState(false);
  if (!link || failed) return null;
  return (
    <Box
      component="img"
      src={mediaUrl(link)}
      alt=""
      loading="lazy"
      onError={() => setFailed(true)}
      sx={{ width, flexShrink: 0, aspectRatio: '2 / 3', objectFit: 'cover', borderRadius: 0.5, boxShadow: 1, bgcolor: 'action.hover' }}
    />
  );
}

/** The sample's text, a paragraph each, each in its own direction (Arabic right-aligned). */
function SampleText({ text }: { text: string }) {
  const paras = useMemo(() => text.split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean), [text]);
  return (
    <Box sx={{ fontSize: { xs: 16, md: 17 }, lineHeight: 1.85, maxWidth: 820, mx: 'auto' }}>
      {paras.map((p, i) => (
        <Typography key={i} dir={dirOf(p)} sx={{ fontSize: 'inherit', lineHeight: 'inherit', mb: 1, whiteSpace: 'pre-wrap', ...rtlSx(p) }}>
          {p}
        </Typography>
      ))}
    </Box>
  );
}
