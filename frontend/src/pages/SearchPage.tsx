import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Box,
  Chip,
  InputAdornment,
  List,
  ListItemButton,
  ListItemText,
  TextField,
  Typography,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import { searchAll, SearchHit } from '../api/client';
import { SideNav } from '../components/SideNav';
import { ENTITY_BY_KEY } from '../config/entities';

export function SearchPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const q = searchParams.get('q') ?? '';
  const [input, setInput] = useState(q);

  useEffect(() => {
    setInput(q);
  }, [q]);

  const { data, isFetching } = useQuery({
    queryKey: ['search', q],
    queryFn: () => searchAll(q, 200),
    enabled: q.trim().length > 0,
  });
  const hits = data ?? [];

  const grouped = useMemo(() => {
    const map = new Map<string, SearchHit[]>();
    for (const h of hits) {
      if (!map.has(h.module)) map.set(h.module, []);
      map.get(h.module)!.push(h);
    }
    return [...map.entries()];
  }, [hits]);

  const dedupe = q.trimStart().startsWith('~');

  // Highlight only the free-text words — drop the leading ~ and command tokens (#?@=* and p3).
  const terms = useMemo(
    () =>
      q
        .replace(/^\s*~/, '')
        .trim()
        .split(/\s+/)
        .filter((w) => !/^[#?@=*]/.test(w) && !/^[pP]\d+$/.test(w))
        .map((w) => w.toLowerCase())
        .filter((w) => w.length >= 2),
    [q],
  );

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: 4, maxWidth: 900 }}>
        <Typography variant="h4" sx={{ fontWeight: 700, mb: 0.5 }}>
          Search
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Full text plus filter tokens: <code>p4</code> priority · <code>?code</code> status ·{' '}
          <code>#code</code> type · <code>@code</code> context · <code>*</code> bookmarked (e.g.{' '}
          <code>p4 linux</code>). Start with <code>~</code> to find near-duplicates (e.g.{' '}
          <code>~ IT system hand over</code>).
        </Typography>
        <TextField
          fullWidth
          size="small"
          placeholder="e.g.  p4 linux   ·   ?done report"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && setSearchParams(input.trim() ? { q: input.trim() } : {})}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" />
              </InputAdornment>
            ),
          }}
          sx={{ mb: 3 }}
        />

        {dedupe && hits.length > 0 && (
          <Typography variant="overline" color="warning.main" sx={{ display: 'block', mb: 1 }}>
            Possible duplicates — review before adding a new record
          </Typography>
        )}

        {q && !isFetching && hits.length === 0 && (
          <Typography color="text.secondary">
            {dedupe ? 'No similar records found.' : `No matches for “${q}”.`}
          </Typography>
        )}

        {grouped.map(([module, list]) => (
          <Box key={module} sx={{ mb: 3 }}>
            <Typography variant="overline" color="text.secondary">
              {ENTITY_BY_KEY[module]?.label ?? module} · {list.length}
            </Typography>
            <List dense>
              {list.map((h) => (
                <ListItemButton
                  key={`${h.module}-${h.id}`}
                  onClick={() => navigate(`/${h.module}?select=${h.id}`)}
                  divider
                  alignItems="flex-start"
                >
                  <ListItemText
                    primary={h.title || '(untitled)'}
                    primaryTypographyProps={{ dir: 'auto', noWrap: true, fontWeight: 600 }}
                    secondary={h.snippet ? <Highlight text={h.snippet} terms={terms} /> : undefined}
                    secondaryTypographyProps={{ dir: 'auto', component: 'span' }}
                  />
                  {dedupe && (
                    <Chip
                      size="small"
                      color="warning"
                      label={`${Math.round(h.score * 100)}%`}
                      sx={{ ml: 1, mt: 0.5 }}
                    />
                  )}
                  <Chip size="small" variant="outlined" label={`#${h.id}`} sx={{ ml: 1, mt: 0.5 }} />
                </ListItemButton>
              ))}
            </List>
          </Box>
        ))}
      </Box>
    </Box>
  );
}

/** Renders a snippet with the matched query terms highlighted. */
function Highlight({ text, terms }: { text: string; terms: string[] }) {
  if (terms.length === 0) return <>{text}</>;
  const escaped = terms.map((t) => t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
  const re = new RegExp(`(${escaped.join('|')})`, 'gi');
  const lower = new Set(terms.map((t) => t.toLowerCase()));
  const parts = text.split(re);
  return (
    <>
      {parts.map((part, i) =>
        lower.has(part.toLowerCase()) ? (
          <Box key={i} component="mark" sx={{ bgcolor: 'warning.light', px: 0.25, borderRadius: 0.5 }}>
            {part}
          </Box>
        ) : (
          <span key={i}>{part}</span>
        ),
      )}
    </>
  );
}
