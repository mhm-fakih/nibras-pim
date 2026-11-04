import { useQuery } from '@tanstack/react-query';
import { Box, Chip, Stack, Tooltip, Typography } from '@mui/material';
import Storage from '@mui/icons-material/Storage';
import { CatalogFile, catalogRecordFiles } from '../api/client';
import { fmtBytes } from '../util/bytes';

/**
 * The record's files on catalogued storage (an external disk holding a repository copy) — known
 * from the last scan, so they show even while the disk is unplugged. Nothing renders when the
 * record has none.
 */
export function CatalogFilesPanel({ module, id }: { module: string; id: number }) {
  const { data } = useQuery({
    queryKey: ['catalog-record', module, id],
    queryFn: () => catalogRecordFiles(module, id),
  });
  const files = data ?? [];
  if (files.length === 0) return null;
  const byLocation = new Map<number, CatalogFile[]>();
  files.forEach((f) => byLocation.set(f.locationId, [...(byLocation.get(f.locationId) ?? []), f]));
  return (
    <Box sx={{ mt: 2 }}>
      <Typography variant="overline" color="text.secondary">
        On other storage ({files.length})
      </Typography>
      {[...byLocation.values()].map((list) => {
        const first = list[0];
        const total = list.reduce((s, f) => s + f.size, 0);
        return (
          <Box key={first.locationId} sx={{ mb: 1 }}>
            <Stack direction="row" spacing={1} alignItems="center">
              <Storage fontSize="small" sx={{ color: 'text.secondary' }} />
              <Typography variant="caption" sx={{ fontWeight: 700, color: 'text.secondary' }}>
                {first.locationName} · repo {first.repo} · {list.length} file{list.length === 1 ? '' : 's'}, {fmtBytes(total)}
              </Typography>
              <Chip
                size="small"
                variant="outlined"
                color={first.mounted ? 'success' : 'default'}
                label={first.mounted ? 'attached' : 'not attached'}
              />
            </Stack>
            {list.map((f) => (
              <Tooltip key={f.relPath} title={f.fullPath} placement="bottom-start">
                <Typography variant="body2" sx={{ pl: 3.5, py: 0.25, wordBreak: 'break-all' }}>
                  {f.relPath}{' '}
                  <Typography component="span" variant="caption" color="text.secondary">
                    {fmtBytes(f.size)}
                  </Typography>
                </Typography>
              </Tooltip>
            ))}
          </Box>
        );
      })}
    </Box>
  );
}
