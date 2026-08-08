import { useEffect, useState } from 'react';
import { Box, Button, Card, IconButton, Skeleton, Stack, Typography } from '@mui/material';
import Close from '@mui/icons-material/Close';
import { useQueries } from '@tanstack/react-query';
import { EntityRecord, SavedSearchRecordRef, getEntity } from '../api/client';
import { RecordCard } from './RecordCard';

/** How many records are fetched and rendered before "Show more" — see the note in the component. */
const PAGE = 40;

/**
 * Saved-search (HQL entity) results shown in the middle pane as full record cards — the same
 * [RecordCard] the rest of the app uses, so a result looks like a record rather than a table row.
 * Clicking one opens it in the detail pane.
 *
 * The search itself only returns module/id/title (that is all the HQL projection can promise), so
 * each card's body is fetched per record. That is one request each, hence the [PAGE] cap and the
 * "Show more" button: a saved search matching 500 rows must not fire 500 GETs to draw a pane the
 * user is going to scroll two screens of. Until a record's fetch lands, its card shows the title
 * the search already gave us over a skeleton, so the list never reflows or flashes empty.
 */
export function SearchResultsList({
  title,
  records,
  selectedId,
  onSelect,
  onClose,
}: {
  title: string;
  records: SavedSearchRecordRef[];
  selectedId: number | null;
  onSelect: (rec: SavedSearchRecordRef) => void;
  onClose: () => void;
}) {
  const [limit, setLimit] = useState(PAGE);
  // A new search starts from the top again, however far the previous one had been expanded.
  useEffect(() => setLimit(PAGE), [title, records]);

  const shown = records.slice(0, limit);
  const details = useQueries({
    queries: shown.map((rec) => ({
      queryKey: ['entity', rec.module, rec.id],
      queryFn: () => getEntity(rec.module, rec.id),
      staleTime: 30_000,
    })),
  });

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Stack
        direction="row"
        alignItems="center"
        spacing={1}
        sx={{ px: 1.5, py: 1, borderBottom: 1, borderColor: 'divider', flexShrink: 0 }}
      >
        <Typography variant="subtitle2" noWrap sx={{ flex: 1 }} title={title}>
          {title} · {records.length}
        </Typography>
        <IconButton size="small" onClick={onClose} title="Close results (back to list)">
          <Close fontSize="small" />
        </IconButton>
      </Stack>

      <Box sx={{ flex: 1, overflow: 'auto', p: 1, display: 'flex', flexDirection: 'column', gap: 1 }}>
        {shown.map((rec, i) => {
          const record = details[i]?.data as EntityRecord | undefined;
          const selectedSx = selectedId === rec.id ? { borderColor: 'primary.main', borderWidth: 2 } : {};
          return record ? (
            <RecordCard
              key={`${rec.module}-${rec.id}`}
              module={rec.module}
              record={record}
              onClick={() => onSelect(rec)}
              sx={selectedSx}
            />
          ) : (
            <Card
              key={`${rec.module}-${rec.id}`}
              variant="outlined"
              onClick={() => onSelect(rec)}
              sx={{ p: 1, cursor: 'pointer', ...selectedSx }}
            >
              <Typography dir="auto" sx={{ fontWeight: 700 }} noWrap>
                {rec.title || '(untitled)'}
              </Typography>
              <Skeleton variant="text" width="80%" />
            </Card>
          );
        })}

        {records.length > shown.length && (
          <Button size="small" onClick={() => setLimit((n) => n + PAGE)} sx={{ alignSelf: 'center', my: 1 }}>
            Show {Math.min(PAGE, records.length - shown.length)} more of {records.length}
          </Button>
        )}
      </Box>
    </Box>
  );
}
