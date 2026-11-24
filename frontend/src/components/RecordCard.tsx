import { Box, Card, Chip, Stack, Typography } from '@mui/material';
import Star from '@mui/icons-material/Star';
import type { SxProps, Theme } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { linkProps } from '../util/links';
import { EntityRecord } from '../api/client';
import { useModules } from '../hooks/useModules';
import { ModuleBar } from './ModuleBar';
import { statusStyle } from './statusStyle';

/**
 * Compact single-record card — the shared template for showing ONE record on a region of the screen
 * outside the detail pane (just-saved feedback, batch add, etc.). Leftmost coloured bar = the entity
 * (module code/colour, same as the list rows). Shows summary, a description preview, and the
 * status / type / department / course / bookmarked fields.
 *
 * `dense`: a board's card (the Kanban page) — regular-weight title, one preview line, small chips
 * and tight padding, so a column holds many records.
 */
/** A description as plain text: no HTML or highlight marks, on one line unless `keepLines`. */
export function plainDescription(value: unknown, keepLines = false): string {
  const raw = String(value ?? '');
  // Older descriptions hold HTML, where the line breaks are tags.
  const lined = keepLines ? raw.replace(/<br\s*\/?>|<\/(p|div|li)>/gi, '\n') : raw;
  const text = lined.replace(/<[^>]+>/g, ' ').replace(/==/g, '');
  return (keepLines ? text : text.replace(/\s+/g, ' ')).trim();
}

export function RecordCard({
  module,
  record,
  sx,
  onClick,
  to,
  dense = false,
  hideDescription = false,
  fullDescription = false,
}: {
  module: string;
  record: EntityRecord;
  sx?: SxProps<Theme>;
  onClick?: () => void;
  /**
   * The in-app page this card stands for ("/tasks?select=5"): the card becomes a real link, so
   * Ctrl/middle-click opens it in a new tab. A plain click runs {@code onClick}, or goes there.
   */
  to?: string;
  dense?: boolean;
  hideDescription?: boolean;
  /** All of the description with its line breaks, rather than a short preview. */
  fullDescription?: boolean;
}) {
  const { modules } = useModules();
  const navigate = useNavigate();
  const clickable = Boolean(onClick || to);
  const link = to ? linkProps(to, onClick ?? (() => navigate(to))) : { onClick };
  const meta = modules.find((m) => m.moduleKey === module);

  const desc = typeof record.description === 'string' ? record.description : '';
  const preview = hideDescription
    ? ''
    : fullDescription
      ? plainDescription(desc, true)
      : desc.replace(/\s+/g, ' ').trim().slice(0, 220);
  const dept = record.department;
  const course = record.course;
  // Board chips stay small but get room around their text: at 16px with 6px sides they read as
  // one run of labels.
  const chipSx = dense ? { height: 20, fontSize: '0.7rem', '& .MuiChip-label': { px: 1 } } : { height: 20 };

  return (
    <Card
      variant="outlined"
      {...link}
      sx={{
        color: 'inherit',
        textDecoration: 'none',
        display: 'flex',
        alignItems: 'stretch',
        p: dense ? 1 : 1,
        gap: dense ? 1 : 1,
        ...(clickable ? { cursor: 'pointer', '&:hover': { bgcolor: 'action.hover' } } : {}),
        ...sx,
      }}
    >
      <ModuleBar code={meta?.code} color={meta?.color} title={module} />
      <Box sx={{ minWidth: 0, flex: 1 }}>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: preview ? 0.5 : dense ? 0.75 : 0 }}>
          <Typography
            dir="auto"
            sx={dense ? { fontWeight: 400, fontSize: '0.82rem', lineHeight: 1.3, flex: 1, minWidth: 0 } : { fontWeight: 700, flex: 1, minWidth: 0 }}
            noWrap
          >
            {record.displayTitle || (record.summary as string) || '(untitled)'}
          </Typography>
          {record.bookmarked && <Star fontSize="small" color="primary" sx={dense ? { fontSize: 14 } : undefined} />}
        </Stack>

        {preview && (
          <Typography
            dir="auto"
            variant="body2"
            color="text.secondary"
            sx={{
              mb: dense ? 0.75 : 1,
              ...(dense ? { fontSize: '0.72rem', lineHeight: 1.3 } : {}),
              ...(fullDescription
                ? { whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }
                : {
                    display: '-webkit-box',
                    WebkitLineClamp: dense ? 1 : 2,
                    WebkitBoxOrient: 'vertical',
                    overflow: 'hidden',
                  }),
            }}
          >
            {preview}
          </Typography>
        )}

        <Stack direction="row" spacing={dense ? 0.75 : 0.5} flexWrap="wrap" useFlexGap>
          {record.status && (
            <Chip size="small" label={record.status.name} sx={{ ...chipSx, ...statusStyle(record.status) }} />
          )}
          {record.type && (
            <Chip size="small" label={record.type.name} sx={{ ...chipSx, ...statusStyle(record.type) }} />
          )}
          {dept && (
            <Chip
              size="small"
              variant="outlined"
              sx={chipSx}
              label={[dept.code, dept.name].filter(Boolean).join(' · ')}
            />
          )}
          {course && (
            <Chip
              size="small"
              variant="outlined"
              sx={chipSx}
              label={`📚 ${[course.code, course.name].filter(Boolean).join(' · ')}`}
            />
          )}
        </Stack>
      </Box>
    </Card>
  );
}
