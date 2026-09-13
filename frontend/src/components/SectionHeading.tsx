import { Box, Chip, Typography } from '@mui/material';

/**
 * A section heading that survives a dense page.
 *
 * <p>These lists are walls of small coloured chips, and an `overline` in `text.secondary` — small,
 * grey, and the same weight as everything around it — simply disappeared into them: there was no
 * telling where one group ended and the next began without reading the chips themselves.
 *
 * <p>Three things do the work. It is heavier and in primary ink so it outranks the chips instead of
 * competing with them; it carries an accent bar and a rule, so the eye finds the boundary without
 * reading anything; and it STICKS, so on a long page the group you are looking at is always named.
 * The opaque background is what makes sticking usable — chips scrolling underneath a transparent
 * header would read as a smear.
 */
export function SectionHeading({
  label,
  count,
  /** Off inside a card, where there is nothing to scroll past and a stuck header looks wrong. */
  sticky = true,
}: {
  label: string;
  count?: number;
  sticky?: boolean;
}) {
  return (
    <Box
      sx={{
        ...(sticky ? { position: 'sticky', top: 0, zIndex: 2, bgcolor: 'background.default' } : {}),
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        py: 1,
        mb: 1,
        borderBottom: 1,
        borderColor: 'divider',
      }}
    >
      <Box sx={{ width: 4, height: 22, borderRadius: 1, bgcolor: 'primary.main', flexShrink: 0 }} />
      <Typography
        variant="subtitle1"
        sx={{ fontWeight: 800, color: 'text.primary', letterSpacing: 0.2, lineHeight: 1.2 }}
      >
        {label}
      </Typography>
      {count !== undefined && (
        <Chip size="small" label={count} sx={{ height: 20, fontWeight: 600 }} />
      )}
    </Box>
  );
}
