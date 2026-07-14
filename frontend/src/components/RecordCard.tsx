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
 */
export function RecordCard({
  module,
  record,
  sx,
  onClick,
}: {
  module: string;
  record: EntityRecord;
  sx?: SxProps<Theme>;
  onClick?: () => void;
}) {
  const { modules } = useModules();
  const meta = modules.find((m) => m.moduleKey === module);

  const desc = typeof record.description === 'string' ? record.description : '';
  const preview = desc.replace(/\s+/g, ' ').trim().slice(0, 220);
  const dept = record.department;
  const course = record.course;

  return (
    <Card
      variant="outlined"
      onClick={onClick}
      sx={{
        display: 'flex',
        alignItems: 'stretch',
        p: 1,
        gap: 1,
        ...(onClick ? { cursor: 'pointer', '&:hover': { bgcolor: 'action.hover' } } : {}),
        ...sx,
      }}
    >
      <ModuleBar code={meta?.code} color={meta?.color} title={module} />
      <Box sx={{ minWidth: 0, flex: 1 }}>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: preview ? 0.5 : 0 }}>
          <Typography dir="auto" sx={{ fontWeight: 700, flex: 1, minWidth: 0 }} noWrap>
            {record.displayTitle || (record.summary as string) || '(untitled)'}
          </Typography>
          {record.bookmarked && <Star fontSize="small" color="primary" />}
        </Stack>

        {preview && (
          <Typography
            dir="auto"
            variant="body2"
            color="text.secondary"
            sx={{
              mb: 1,
              display: '-webkit-box',
              WebkitLineClamp: 2,
              WebkitBoxOrient: 'vertical',
              overflow: 'hidden',
            }}
          >
            {preview}
          </Typography>
        )}

        <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
          {record.status && (
            <Chip size="small" label={record.status.name} sx={{ height: 20, ...statusStyle(record.status) }} />
          )}
          {record.type && (
            <Chip size="small" label={record.type.name} sx={{ height: 20, ...statusStyle(record.type) }} />
          )}
          {dept && (
            <Chip
              size="small"
              variant="outlined"
              sx={{ height: 20 }}
              label={[dept.code, dept.name].filter(Boolean).join(' · ')}
            />
          )}
          {course && (
            <Chip
              size="small"
              variant="outlined"
              sx={{ height: 20 }}
              label={`📚 ${[course.code, course.name].filter(Boolean).join(' · ')}`}
            />
          )}
        </Stack>
      </Box>
    </Card>
  );
}
