import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Box, Chip, List, ListItemButton, ListItemText, Typography } from '@mui/material';
import LinkIcon from '@mui/icons-material/Link';
import { getRelated } from '../api/client';

/** Records linked to this one through the legacy foreign keys (Book→excerpts, Goal→tasks, …). */
export function RelatedRecordsPanel({ module, id }: { module: string; id: number }) {
  const navigate = useNavigate();
  const { data } = useQuery({ queryKey: ['related', module, id], queryFn: () => getRelated(module, id) });
  const groups = (data ?? []).filter((g) => g.count > 0);
  if (groups.length === 0) return null;

  return (
    <Box sx={{ mt: 2 }}>
      <Typography variant="overline" color="text.secondary" sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
        <LinkIcon fontSize="inherit" /> Related
      </Typography>
      {groups.map((g) => (
        <Box key={g.module} sx={{ mb: 1 }}>
          <Typography variant="caption" sx={{ fontWeight: 700, color: 'text.secondary' }}>
            {g.label} ({g.count})
          </Typography>
          <List dense disablePadding>
            {g.items.map((it) => (
              <ListItemButton
                key={it.id}
                sx={{ py: 0.25, borderRadius: 1 }}
                onClick={() => navigate(`/${it.module}?select=${it.id}`)}
              >
                <ListItemText
                  primary={it.title || '(untitled)'}
                  primaryTypographyProps={{ dir: 'auto', noWrap: true, fontSize: 13 }}
                />
                <Chip size="small" variant="outlined" label={`#${it.id}`} sx={{ ml: 1 }} />
              </ListItemButton>
            ))}
            {g.count > g.items.length && (
              <Typography variant="caption" color="text.secondary" sx={{ pl: 1 }}>
                …and {g.count - g.items.length} more — open the {g.label.toLowerCase()} list to see all
              </Typography>
            )}
          </List>
        </Box>
      ))}
    </Box>
  );
}
