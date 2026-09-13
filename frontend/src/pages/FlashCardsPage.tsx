import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Box, Button, Chip, IconButton, Stack, Typography } from '@mui/material';
import CheckCircle from '@mui/icons-material/CheckCircle';
import Replay from '@mui/icons-material/Replay';
import Bookmark from '@mui/icons-material/Bookmark';
import Undo from '@mui/icons-material/Undo';
import Remove from '@mui/icons-material/Remove';
import Add from '@mui/icons-material/Add';
import {
  FlashCard,
  FlashCardState,
  getFlashCardSummary,
  getFlashCards,
  setFlashCardState,
} from '../api/client';
import { SideNav } from '../components/SideNav';
import { GalleryCard } from '../components/GalleryCard';
import { grouped } from '../util/numbers';
import { RecordRefLink } from '../components/RecordRefLink';
import { useGalleryKeys } from '../hooks/useGalleryKeys';

type Tab = 'NEW' | 'REPEAT' | 'KEPT';

/** Remembered per browser — a reading preference, not something worth a row on the server. */
const FONT_KEY = 'nibras.flashcards.fontScale';

/**
 * The flash-card review job: passages from long notes, one at a time, with three decisions.
 *
 * The same rows the phone reads, so a card reviewed here is gone there too. The undecided queue is
 * capped by the server and the two decided lists are not — a bank of twenty-odd thousand served
 * whole is unusable, but having said "come back to this", nothing should truncate the result.
 */
export function FlashCardsPage() {
  const qc = useQueryClient();
  const [tab, setTab] = useState<Tab>('NEW');
  const [index, setIndex] = useState(0);
  const [fontScale, setFontScale] = useState(() => Number(localStorage.getItem(FONT_KEY)) || 1.25);

  useEffect(() => localStorage.setItem(FONT_KEY, String(fontScale)), [fontScale]);
  useEffect(() => setIndex(0), [tab]);

  const cards = useQuery({ queryKey: ['flashcards', tab], queryFn: () => getFlashCards(tab) });
  const summary = useQuery({ queryKey: ['flashcards', 'summary'], queryFn: getFlashCardSummary });

  const decide = useMutation({
    mutationFn: ({ id, state }: { id: number; state: FlashCardState }) => setFlashCardState(id, state),
    onSuccess: () => {
      // Every list can change: a card leaves one and joins another.
      qc.invalidateQueries({ queryKey: ['flashcards'] });
    },
  });

  const items: FlashCard[] = cards.data ?? [];
  const count = items.length;
  const safeIndex = count === 0 ? 0 : Math.min(index, count - 1);
  const card = items[safeIndex];

  const step = (delta: number) => setIndex((i) => (count === 0 ? 0 : (i + delta + count) % count));

  const act = (state: FlashCardState) => {
    if (!card) return;
    decide.mutate({ id: card.id, state });
    // Stay on the same position: the card leaves the list and the next one arrives here.
    setIndex((i) => (count <= 1 ? 0 : Math.min(i, count - 2)));
  };

  useGalleryKeys({
    onPrev: () => step(-1),
    onNext: () => step(1),
    onFirst: () => setIndex(0),
    onLast: () => setIndex(Math.max(0, count - 1)),
    actions: {
      // d for done, and Enter as the same thing — it is the action most cards get.
      d: () => act('REVIEWED'),
      enter: () => act('REVIEWED'),
      r: tab !== 'REPEAT' ? () => act('REPEAT') : undefined,
      k: tab !== 'KEPT' ? () => act('KEPT') : undefined,
      u: tab !== 'NEW' ? () => act('NEW') : undefined,
    },
    enabled: count > 0,
  });

  const s = summary.data;

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', p: 2 }}>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1, flexWrap: 'wrap' }}>
          <Typography variant="h6" sx={{ mr: 1 }}>Flash cards</Typography>
          <Chip
            label={`Review ${grouped(tab === 'NEW' ? count : (s?.remaining ?? 0))}`}
            color={tab === 'NEW' ? 'primary' : 'default'}
            onClick={() => setTab('NEW')}
            size="small"
          />
          <Chip
            label={`Repeat ${grouped(tab === 'REPEAT' ? count : (s?.repeat ?? 0))}`}
            color={tab === 'REPEAT' ? 'primary' : 'default'}
            onClick={() => setTab('REPEAT')}
            size="small"
          />
          <Chip
            label={`Kept ${grouped(tab === 'KEPT' ? count : (s?.kept ?? 0))}`}
            color={tab === 'KEPT' ? 'primary' : 'default'}
            onClick={() => setTab('KEPT')}
            size="small"
          />
          <Box sx={{ flex: 1 }} />
          {s ? (
            <Typography variant="caption" color="text.secondary">
              {grouped(s.remaining)} left of {grouped(s.total)} · showing {grouped(count)} at a time
            </Typography>
          ) : null}
          <Typography variant="caption" color="text.secondary" sx={{ display: { xs: 'none', md: 'inline' } }}>
            ← → move · d reviewed · r repeat · k keep
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
            {cards.isLoading
              ? 'Loading…'
              : tab === 'NEW'
                ? 'Nothing left to review — every passage has been dealt with.'
                : tab === 'REPEAT'
                  ? 'Nothing set to repeat.'
                  : 'Nothing kept yet.'}
          </Typography>
        ) : (
          <GalleryCard
            index={safeIndex}
            count={count}
            onPrev={() => step(-1)}
            onNext={() => step(1)}
            actions={
              // Reviewed sits on the RIGHT. It is the action taken on most cards, and on a phone
              // held in one hand the right edge is where the thumb already is; the web page keeps
              // the same order so the two do not teach opposite habits.
              <>
                {tab !== 'NEW' && (
                  <Button variant="text" startIcon={<Undo />} onClick={() => act('NEW')}>
                    Unset (u)
                  </Button>
                )}
                {tab !== 'KEPT' && (
                  <Button variant="outlined" startIcon={<Bookmark />} onClick={() => act('KEPT')}>
                    Keep (k)
                  </Button>
                )}
                {tab !== 'REPEAT' && (
                  <Button variant="outlined" startIcon={<Replay />} onClick={() => act('REPEAT')}>
                    Repeat (r)
                  </Button>
                )}
                <Box sx={{ flex: 1 }} />
                <Button variant="contained" startIcon={<CheckCircle />} onClick={() => act('REVIEWED')}>
                  Reviewed (d)
                </Button>
              </>
            }
          >
            <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1.5 }}>
              <RecordRefLink module="notes" id={card.noteId} label={card.recordRef} />
              <Typography variant="caption" color="text.secondary">{card.writtenOn ?? ''}</Typography>
              <Box sx={{ flex: 1 }} />
              <Typography variant="caption" color="text.secondary">
                {card.topic === 'r' ? 'religious' : card.topic === 'p' ? 'psychological' : ''}
              </Typography>
            </Stack>
            <Box
              // Direction from the passage itself, and justified: Arabic settles to a clean right
              // margin and English to the left, without either being told which it is.
              dir={card.rtl ? 'rtl' : 'ltr'}
              sx={{
                flex: 1,
                minHeight: 0,
                overflowY: 'auto',
                textAlign: 'justify',
                fontSize: `${fontScale}rem`,
                lineHeight: 1.9,
                whiteSpace: 'pre-wrap',
              }}
            >
              {card.passage}
            </Box>
          </GalleryCard>
        )}
      </Box>
    </Box>
  );
}
