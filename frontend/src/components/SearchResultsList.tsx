import { Box, Chip, IconButton, List, ListItemButton, ListItemText, Stack, Typography } from '@mui/material';
import Close from '@mui/icons-material/Close';
import { SavedSearchRecordRef } from '../api/client';

/**
 * Saved-search (HQL entity) results shown in the middle pane as a full record list — one full-width
 * line per record; clicking one opens it in the detail pane.
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
      <List dense sx={{ flex: 1, overflow: 'auto', py: 0 }}>
        {records.map((rec) => (
          <ListItemButton
            key={`${rec.module}-${rec.id}`}
            selected={selectedId === rec.id}
            onClick={() => onSelect(rec)}
          >
            <ListItemText
              primary={rec.title || '(untitled)'}
              secondary={rec.module}
              primaryTypographyProps={{ dir: 'auto', noWrap: true, fontSize: 14 }}
              secondaryTypographyProps={{ fontSize: 11 }}
            />
            <Chip size="small" variant="outlined" label={`#${rec.id}`} sx={{ ml: 1 }} />
          </ListItemButton>
        ))}
      </List>
    </Box>
  );
}
