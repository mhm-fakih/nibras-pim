import { Box, Card, Chip, Stack, Typography } from '@mui/material';
import Star from '@mui/icons-material/Star';
import type { SxProps, Theme } from '@mui/material';
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
export function RecordCard({
  module,
  record,
  sx,
  onClick,
  dense = false,
}: {
  module: string;
  record: EntityRecord;
  sx?: SxProps<Theme>;
  onClick?: () => void;
  dense?: boolean;
}) {
  const { modules } = useModules();
  const meta = modules.find((m) => m.moduleKey === module);

  const desc = typeof record.description === 'string' ? record.description : '';
  const preview = desc.replace(/\s+/g, ' ').trim().slice(0, 220);
  const dept = record.department;
  const course = record.course;
  const chipSx = dense ? { height: 16, fontSize: '0.66rem', '& .MuiChip-label': { px: 0.75 } } : { height: 20 };

  return (
    <Card
      variant="outlined"
      onClick={onClick}
      sx={{
        display: 'flex',
        alignItems: 'stretch',
        p: dense ? 0.5 : 1,
        gap: dense ? 0.75 : 1,
        ...(onClick ? { cursor: 'pointer', '&:hover': { bgcolor: 'action.hover' } } : {}),
        ...sx,
      }}
    >
      <ModuleBar code={meta?.code} color={meta?.color} title={module} />
      <Box sx={{ minWidth: 0, flex: 1 }}>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: preview ? (dense ? 0.25 : 0.5) : 0 }}>
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
              mb: dense ? 0.25 : 1,
              ...(dense ? { fontSize: '0.72rem', lineHeight: 1.3 } : {}),
              display: '-webkit-box',
              WebkitLineClamp: dense ? 1 : 2,
              WebkitBoxOrient: 'vertical',
              overflow: 'hidden',
            }}
          >
            {preview}
          </Typography>
        )}

        <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
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
