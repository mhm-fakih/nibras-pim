import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link as RouterLink } from 'react-router-dom';
import { Box, Button, Chip, CircularProgress, Link, Stack, Typography } from '@mui/material';
import Print from '@mui/icons-material/Print';
import { AllHighlightsReport, BASE, MarkedHighlight, getAllHighlights } from '../api/client';
import { dirOf } from '../util/rtl';
import { grouped } from '../util/numbers';

const SOURCES: { key: MarkedHighlight['source']; label: string }[] = [
  { key: 'resourceDescription', label: 'Resource descriptions' },
  { key: 'resourceFullText', label: 'Resource full texts' },
  { key: 'note', label: 'Local notes' },
  { key: 'aiNote', label: 'AI notes' },
];

const SOURCE_LABEL = Object.fromEntries(SOURCES.map((s) => [s.key, s.label])) as Record<string, string>;

/** Consecutive passages of one document, shown under one heading. */
interface Group {
  key: string;
  source: MarkedHighlight['source'];
  ref: string;
  title?: string;
  date?: string;
  texts: string[];
}

function groupsOf(items: MarkedHighlight[]): Group[] {
  const out: Group[] = [];
  for (const it of items) {
    const key = `${it.source}|${it.ref}`;
    const last = out[out.length - 1];
    if (last && last.key === key) last.texts.push(it.text);
    else out.push({ key, source: it.source, ref: it.ref, title: it.title, date: it.date, texts: [it.text] });
  }
  return out;
}

function SourceLink({ g }: { g: Group }) {
  if (g.source === 'resourceDescription' || g.source === 'resourceFullText') {
    const id = g.ref.replace(/^R/, '');
    return (
      <Link href={`${BASE}read/resources/${id}`} target="_blank" rel="noopener" underline="hover" fontWeight={700}>
        {g.ref}
      </Link>
    );
  }
  if (g.source === 'note') {
    return (
      <Link component={RouterLink} to={`/local-notes?open=${encodeURIComponent(g.ref)}`} underline="hover" fontWeight={700}>
        Note
      </Link>
    );
  }
  return (
    <Typography component="span" fontWeight={700} title={g.ref}>
      AI note
    </Typography>
  );
}

/**
 * Built-in report: every passage marked ==like this==, on one long page with the count — resources'
 * descriptions and full texts, the local notes folder and the AI notes folder. (The Highlights
 * gallery shows the records' "main highlights" field one card at a time; this is the marked text.)
 * The total is what the "Nb. of highlights" indicator collects each day.
 */
export function AllHighlightsPage() {
  const { data, isLoading, error } = useQuery<AllHighlightsReport>({
    queryKey: ['all-highlights'],
    queryFn: getAllHighlights,
  });
  const [only, setOnly] = useState<string | null>(null);
  const groups = useMemo(
    () => groupsOf((data?.items ?? []).filter((i) => only == null || i.source === only)),
    [data, only],
  );

  if (isLoading) {
    return (
      <Box sx={{ p: 6, display: 'flex', justifyContent: 'center' }}>
        <CircularProgress />
      </Box>
    );
  }
  if (error || !data) {
    return <Typography color="error" sx={{ p: 4 }}>Couldn't load the highlights.</Typography>;
  }
  const shown = only == null ? data.total : data.counts[only] ?? 0;

  return (
    <Box sx={{ p: { xs: 2, sm: 4 }, maxWidth: 1000, mx: 'auto' }}>
      <Stack direction="row" spacing={1} sx={{ mb: 2, '@media print': { display: 'none' } }}>
        <Button variant="contained" startIcon={<Print />} onClick={() => window.print()}>
          Print
        </Button>
        <Button component={RouterLink} to="/reports">
          Back to reports
        </Button>
      </Stack>
      <Box component="header" sx={{ mb: 2 }}>
        <Typography variant="h5" sx={{ fontWeight: 700 }}>
          All highlights
        </Typography>
        <Typography variant="body1" sx={{ mt: 0.5 }}>
          <b>{grouped(data.total)}</b> highlight{data.total === 1 ? '' : 's'} · {new Date().toLocaleDateString()}
        </Typography>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
          Passages marked ==like this==. Notes: {data.notesDir ?? '—'} · AI notes: {data.aiNotesDir ?? 'not found'}
        </Typography>
      </Box>
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mb: 3, '@media print': { display: 'none' } }}>
        <Chip label={`All ${grouped(data.total)}`} color={only == null ? 'primary' : 'default'} onClick={() => setOnly(null)} />
        {SOURCES.map((s) => (
          <Chip
            key={s.key}
            label={`${s.label} ${grouped(data.counts[s.key] ?? 0)}`}
            color={only === s.key ? 'primary' : 'default'}
            onClick={() => setOnly(only === s.key ? null : s.key)}
          />
        ))}
      </Stack>
      {shown === 0 ? (
        <Typography color="text.secondary">Nothing marked here yet.</Typography>
      ) : (
        groups.map((g) => (
          <Box key={g.key} component="section" sx={{ mb: 2.5, breakInside: 'avoid' }}>
            <Stack direction="row" spacing={1} alignItems="baseline" sx={{ borderBottom: 1, borderColor: 'divider', pb: 0.25, mb: 0.75 }}>
              <SourceLink g={g} />
              <Typography
                variant="subtitle1"
                dir={dirOf(g.title ?? '')}
                sx={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
              >
                {g.title || g.ref}
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>
                {[SOURCE_LABEL[g.source], g.date ? new Date(g.date).toLocaleDateString() : null].filter(Boolean).join(' · ')}
              </Typography>
            </Stack>
            {g.texts.map((t, i) => (
              <Typography
                key={i}
                dir={dirOf(t)}
                sx={{
                  mb: 0.75,
                  lineHeight: 1.7,
                  textAlign: 'start',
                }}
              >
                <Box
                  component="mark"
                  sx={{ bgcolor: 'rgba(242, 199, 68, 0.45)', color: 'inherit', px: 0.25, borderRadius: 0.5, printColorAdjust: 'exact' }}
                >
                  {t}
                </Box>
              </Typography>
            ))}
          </Box>
        ))
      )}
    </Box>
  );
}
