import { useCallback, useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Box, CircularProgress, Dialog, IconButton, Slider, Stack, Tooltip, Typography } from '@mui/material';
import ChevronLeft from '@mui/icons-material/ChevronLeft';
import ChevronRight from '@mui/icons-material/ChevronRight';
import Close from '@mui/icons-material/Close';
import Download from '@mui/icons-material/Download';
import Fullscreen from '@mui/icons-material/Fullscreen';
import FullscreenExit from '@mui/icons-material/FullscreenExit';
import OpenInNew from '@mui/icons-material/OpenInNew';
import ViewCarousel from '@mui/icons-material/ViewCarousel';
import { getSlides, mediaUrl } from '../api/client';
import { errorDetail } from '../util/errors';
import { hrefOf } from '../util/links';
import { dirOf } from '../util/rtl';

export interface SlideSource {
  module: string;
  id: number;
  repo: number;
  path: string;
}

export const slidesRoute = (s: SlideSource) =>
  `/slides?module=${encodeURIComponent(s.module)}&id=${s.id}&repo=${s.repo}&path=${encodeURIComponent(s.path)}`;

/**
 * A document shown as a deck, SlideShare-style: one large page on a dark stage, arrows at its
 * sides, the page count and a slider above, a strip of page thumbnails below. A presentation,
 * word-processor file or PDF alike — the server converts the first two to PDF and draws each page
 * as an image, so nothing but images reaches the browser.
 *
 * Keys: ← → (also Page Up/Down, Space), Home / End, F for full screen, Esc to close.
 */
export function SlideDeckView({ source, onClose }: { source: SlideSource; onClose?: () => void }) {
  const deck = useQuery({
    queryKey: ['slides', source.module, source.id, source.repo, source.path],
    queryFn: () => getSlides(source.module, source.id, source.repo, source.path),
    staleTime: 6 * 3600_000,
  });
  const [n, setN] = useState(1);
  const [full, setFull] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const strip = useRef<HTMLDivElement>(null);
  const pages = deck.data?.pages ?? 0;

  const go = useCallback((to: number) => setN((cur) => Math.min(Math.max(1, to), Math.max(1, pages || cur))), [pages]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement | null;
      if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable)) return;
      if (e.ctrlKey || e.metaKey || e.altKey) return;
      if (['ArrowRight', 'PageDown', ' '].includes(e.key)) setN((c) => Math.min(c + 1, pages || c));
      else if (['ArrowLeft', 'PageUp'].includes(e.key)) setN((c) => Math.max(c - 1, 1));
      else if (e.key === 'Home') setN(1);
      else if (e.key === 'End') setN(pages || 1);
      else if (e.key === 'f' || e.key === 'F') toggleFull();
      else return;
      e.preventDefault();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pages]);

  useEffect(() => {
    const onFs = () => setFull(document.fullscreenElement === root.current);
    document.addEventListener('fullscreenchange', onFs);
    return () => document.removeEventListener('fullscreenchange', onFs);
  }, []);

  // Keep the current page's thumbnail in view.
  useEffect(() => {
    strip.current?.querySelector<HTMLElement>(`[data-page="${n}"]`)?.scrollIntoView({ block: 'nearest', inline: 'center' });
  }, [n]);

  const toggleFull = () => {
    if (document.fullscreenElement) void document.exitFullscreen();
    else void root.current?.requestFullscreen?.();
  };

  const pageSrc = (page: number, w: number) => (deck.data ? mediaUrl(`${deck.data.page}&n=${page}&w=${w}`) : '');
  const stageWidth = full ? 1920 : 1280;

  return (
    <Box
      ref={root}
      sx={{ display: 'flex', flexDirection: 'column', height: '100%', bgcolor: '#111', color: '#eee', minHeight: 0 }}
    >
      <Stack direction="row" alignItems="center" spacing={1} sx={{ px: 1.5, py: 0.75, borderBottom: '1px solid #333' }}>
        <ViewCarousel fontSize="small" sx={{ color: '#aaa' }} />
        <Typography
          variant="subtitle2"
          noWrap
          dir={dirOf(source.path)}
          sx={{ flex: 1, minWidth: 0, textAlign: dirOf(source.path) === 'rtl' ? 'right' : 'left' }}
          title={source.path}
        >
          {deck.data?.name ?? source.path}
        </Typography>
        {pages > 0 && (
          <Typography variant="body2" sx={{ color: '#bbb', whiteSpace: 'nowrap' }}>
            {n} / {pages}
          </Typography>
        )}
        {deck.data && (
          <Tooltip title="Download">
            <IconButton size="small" sx={{ color: '#ddd' }} component="a" href={mediaUrl(`${deck.data.file}&download=true`)}>
              <Download fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
        {onClose && (
          <Tooltip title="Open in a new tab">
            <IconButton size="small" sx={{ color: '#ddd' }} component="a" href={hrefOf(slidesRoute(source))} target="_blank">
              <OpenInNew fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
        <Tooltip title={full ? 'Leave full screen (F)' : 'Full screen (F)'}>
          <IconButton size="small" sx={{ color: '#ddd' }} onClick={toggleFull}>
            {full ? <FullscreenExit fontSize="small" /> : <Fullscreen fontSize="small" />}
          </IconButton>
        </Tooltip>
        {onClose && (
          <Tooltip title="Close (Esc)">
            <IconButton size="small" sx={{ color: '#ddd' }} onClick={onClose}>
              <Close fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
      </Stack>

      <Box sx={{ position: 'relative', flex: 1, minHeight: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', p: 1 }}>
        {deck.isLoading && (
          <Stack alignItems="center" spacing={1}>
            <CircularProgress size={28} />
            <Typography variant="body2" sx={{ color: '#aaa' }}>
              Preparing the preview… (a presentation is converted the first time)
            </Typography>
          </Stack>
        )}
        {deck.isError && (
          <Typography sx={{ color: '#f88' }}>{errorDetail(deck.error, 'This document could not be previewed.')}</Typography>
        )}
        {deck.data && (
          <>
            <Box
              component="img"
              key={n}
              src={pageSrc(n, stageWidth)}
              alt={`Page ${n}`}
              onClick={(e: React.MouseEvent<HTMLImageElement>) => {
                const r = e.currentTarget.getBoundingClientRect();
                go(e.clientX - r.left < r.width / 3 ? n - 1 : n + 1);
              }}
              sx={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain', bgcolor: '#fff', boxShadow: 6, cursor: 'pointer', userSelect: 'none' }}
            />
            {/* the next page, loaded ahead so the step is instant */}
            {n < pages && <link rel="prefetch" href={pageSrc(n + 1, stageWidth)} />}
            <IconButton
              onClick={() => go(n - 1)}
              disabled={n <= 1}
              sx={{ position: 'absolute', left: 8, top: '50%', transform: 'translateY(-50%)', bgcolor: 'rgba(0,0,0,.45)', color: '#fff', '&:hover': { bgcolor: 'rgba(0,0,0,.7)' } }}
              aria-label="Previous page"
            >
              <ChevronLeft />
            </IconButton>
            <IconButton
              onClick={() => go(n + 1)}
              disabled={n >= pages}
              sx={{ position: 'absolute', right: 8, top: '50%', transform: 'translateY(-50%)', bgcolor: 'rgba(0,0,0,.45)', color: '#fff', '&:hover': { bgcolor: 'rgba(0,0,0,.7)' } }}
              aria-label="Next page"
            >
              <ChevronRight />
            </IconButton>
          </>
        )}
      </Box>

      {pages > 1 && (
        <Box sx={{ px: 3, pt: 0.5 }}>
          <Slider size="small" min={1} max={pages} value={n} onChange={(_, v) => go(v as number)} valueLabelDisplay="auto" sx={{ color: '#4fc3f7' }} />
        </Box>
      )}
      {pages > 1 && (
        <Box ref={strip} sx={{ display: 'flex', gap: 1, overflowX: 'auto', px: 1.5, pb: 1, pt: 0.25, flexShrink: 0 }}>
          {Array.from({ length: pages }, (_, i) => i + 1).map((p) => (
            <Box
              key={p}
              data-page={p}
              onClick={() => go(p)}
              sx={{
                flex: '0 0 auto',
                width: 120,
                cursor: 'pointer',
                border: '2px solid',
                borderColor: p === n ? '#4fc3f7' : 'transparent',
                borderRadius: 1,
                overflow: 'hidden',
                position: 'relative',
                bgcolor: '#222',
              }}
            >
              <Box component="img" loading="lazy" src={pageSrc(p, 240)} alt="" sx={{ width: '100%', display: 'block', bgcolor: '#fff' }} />
              <Typography variant="caption" sx={{ position: 'absolute', bottom: 2, right: 4, bgcolor: 'rgba(0,0,0,.6)', px: 0.5, borderRadius: 0.5 }}>
                {p}
              </Typography>
            </Box>
          ))}
        </Box>
      )}
    </Box>
  );
}

/** The deck in a large dialog over the page (the record stays where it was). */
export function SlideViewerDialog({ source, onClose }: { source: SlideSource | null; onClose: () => void }) {
  return (
    <Dialog open={!!source} onClose={onClose} fullWidth maxWidth={false} PaperProps={{ sx: { width: '96vw', height: '94vh', m: 0, bgcolor: '#111' } }}>
      {source && <SlideDeckView source={source} onClose={onClose} />}
    </Dialog>
  );
}
