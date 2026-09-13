import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Box, IconButton, Stack, Typography } from '@mui/material';
import Remove from '@mui/icons-material/Remove';
import Add from '@mui/icons-material/Add';
import { Highlight, getHighlights } from '../api/client';
import { SideNav } from '../components/SideNav';
import { GalleryCard } from '../components/GalleryCard';
import { grouped } from '../util/numbers';
import { RecordRefLink } from '../components/RecordRefLink';
import { MarkdownView } from '../components/MarkdownView';
import { useGalleryKeys } from '../hooks/useGalleryKeys';

const FONT_KEY = 'nibras.highlights.fontScale';

/**
 * Your records' key points, one record at a time.
 *
 * Read-only and LIVE, which is what makes it a different thing from the flash cards next door: that
 * is a frozen one-time job with decisions attached, this is simply what your records currently say.
 * A record whose highlights are filled in today is in this gallery today, and there is nothing here
 * to decide.
 *
 * Every module that carries highlights feeds this — notes, tasks, journals, planners, goals,
 * writings and resources — so the card names which one it came from, and the record's name links
 * out to its own page.
 *
 * The bullets arrive already split by the server, so this page and the phone cannot disagree about
 * what counts as a line.
 */
export function HighlightsPage() {
  const [index, setIndex] = useState(0);
  const [fontScale, setFontScale] = useState(() => Number(localStorage.getItem(FONT_KEY)) || 1.15);
  useEffect(() => localStorage.setItem(FONT_KEY, String(fontScale)), [fontScale]);

  const { data, isLoading } = useQuery({ queryKey: ['highlights'], queryFn: () => getHighlights() });
  const items: Highlight[] = data ?? [];
  const count = items.length;
  const safeIndex = count === 0 ? 0 : Math.min(index, count - 1);
  const item = items[safeIndex];
  const step = (d: number) => setIndex((i) => (count === 0 ? 0 : (i + d + count) % count));

  // No actions here — this gallery is read-only — so only the navigation keys. Up and Down stay
  // free for scrolling the bullet list, which can be longer than the card.
  useGalleryKeys({
    onPrev: () => step(-1),
    onNext: () => step(1),
    onFirst: () => setIndex(0),
    onLast: () => setIndex(Math.max(0, count - 1)),
    enabled: count > 0,
  });

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', p: 2 }}>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
          <Typography variant="h6">Highlights</Typography>
          <Box sx={{ flex: 1 }} />
          <Typography variant="caption" color="text.secondary">
            {grouped(count)} {count === 1 ? 'record' : 'records'} with highlights
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ display: { xs: 'none', md: 'inline' } }}>
            ← → move
          </Typography>
          <IconButton size="small" onClick={() => setFontScale((f) => Math.max(0.8, f - 0.1))} aria-label="Smaller text">
            <Remove fontSize="small" />
          </IconButton>
          <IconButton size="small" onClick={() => setFontScale((f) => Math.min(2.6, f + 0.1))} aria-label="Larger text">
            <Add fontSize="small" />
          </IconButton>
        </Stack>

        {count === 0 ? (
          <Typography color="text.secondary" sx={{ p: 4 }}>
            {isLoading ? 'Loading…' : 'No record has its highlights filled in yet.'}
          </Typography>
        ) : (
          <GalleryCard index={safeIndex} count={count} onPrev={() => step(-1)} onNext={() => step(1)}>
            <Stack direction="row" spacing={1} alignItems="baseline" sx={{ mb: 1.5 }}>
              <RecordRefLink module={item.module} id={item.id} label={item.recordRef} />
              <Typography
                variant="subtitle1"
                sx={{ minWidth: 0, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}
              >
                {item.title ?? ''}
              </Typography>
              <Box sx={{ flex: 1 }} />
              <Typography variant="caption" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>
                {[item.subtitle, item.date].filter(Boolean).join(' · ')}
              </Typography>
            </Stack>
            <Box
              component="ul"
              dir={item.rtl ? 'rtl' : 'ltr'}
              sx={{
                flex: 1,
                minHeight: 0,
                overflowY: 'auto',
                m: 0,
                // The field holds bullet points, so it is rendered as a list. The padding follows
                // the text direction, or an Arabic list hangs its markers off the wrong edge.
                pl: item.rtl ? 0 : 3,
                pr: item.rtl ? 3 : 0,
                fontSize: `${fontScale}rem`,
                lineHeight: 1.8,
                '& li': { mb: 1 },
              }}
            >
              {/* Rendered as Markdown, not as plain text: a resource keeps its highlights in a
                  Markdown field, and shown raw the bold ones read as "**Governing principle:**".
                  The paragraph margins are dropped so a one-line bullet stays one line. */}
              {item.bullets.map((b, i) => (
                <li key={i}>
                  <Box sx={{ '& p': { m: 0 }, '& > *:last-child': { mb: 0 } }}>
                    <MarkdownView value={b} />
                  </Box>
                </li>
              ))}
            </Box>
          </GalleryCard>
        )}
      </Box>
    </Box>
  );
}
