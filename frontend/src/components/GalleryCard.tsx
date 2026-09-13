import { ReactNode } from 'react';
import { Box, IconButton, Typography } from '@mui/material';
import { grouped } from '../util/numbers';
import ChevronLeft from '@mui/icons-material/ChevronLeft';
import ChevronRight from '@mui/icons-material/ChevronRight';

/**
 * The frame both galleries share: one item at a time, filling the space, with the arrows either
 * side and a position counter.
 *
 * A gallery rather than a list, deliberately. Reading something closely and scrolling a feed are
 * different acts, and a list quietly invites the second — the eye moves to the next card before the
 * current one is finished. One at a time is the point.
 */
export function GalleryCard({
  index,
  count,
  onPrev,
  onNext,
  header,
  children,
  actions,
}: {
  index: number;
  count: number;
  onPrev: () => void;
  onNext: () => void;
  header?: ReactNode;
  children: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 1, py: 0.5 }}>
        <Typography variant="caption" color="text.secondary">
          {count === 0 ? 'nothing here' : `${grouped(index + 1)} of ${grouped(count)}`}
        </Typography>
        <Box sx={{ flex: 1 }} />
        {header}
      </Box>

      <Box sx={{ display: 'flex', alignItems: 'stretch', gap: 1, flex: 1, minHeight: 0 }}>
        <IconButton onClick={onPrev} disabled={count < 2} aria-label="Previous" sx={{ alignSelf: 'center' }}>
          <ChevronLeft />
        </IconButton>

        <Box
          sx={{
            flex: 1,
            minWidth: 0,
            minHeight: 0,
            display: 'flex',
            flexDirection: 'column',
            border: 1,
            borderColor: 'divider',
            borderRadius: 2,
            bgcolor: 'action.hover',
            p: { xs: 2, md: 3 },
          }}
        >
          {children}
        </Box>

        <IconButton onClick={onNext} disabled={count < 2} aria-label="Next" sx={{ alignSelf: 'center' }}>
          <ChevronRight />
        </IconButton>
      </Box>

      {actions ? <Box sx={{ display: 'flex', gap: 1, pt: 1.5, px: 1 }}>{actions}</Box> : null}
    </Box>
  );
}
