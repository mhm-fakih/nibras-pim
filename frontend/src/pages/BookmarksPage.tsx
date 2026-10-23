import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Box, Chip, List, ListItemButton, ListItemText, Typography } from '@mui/material';
import Star from '@mui/icons-material/Star';
import { BookmarkItem, getBookmarks } from '../api/client';
import { SideNav } from '../components/SideNav';
import { DetailPane } from '../components/DetailPane';
import { ModuleBar } from '../components/ModuleBar';
import { ENTITY_BY_KEY } from '../config/entities';
import { useModules } from '../hooks/useModules';

export function BookmarksPage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { modules } = useModules();
  const { data } = useQuery({ queryKey: ['bookmarks'], queryFn: getBookmarks });
  const items = data ?? [];
  const [selected, setSelected] = useState<{ module: string; id: number } | null>(null);

  // Preserve the backend's module order while grouping.
  const groups = useMemo(() => {
    const order: string[] = [];
    const map = new Map<string, BookmarkItem[]>();
    for (const it of items) {
      if (!map.has(it.module)) {
        map.set(it.module, []);
        order.push(it.module);
      }
      map.get(it.module)!.push(it);
    }
    return order.map((m) => [m, map.get(m)!] as const);
  }, [items]);

  const selectedConfig = selected ? ENTITY_BY_KEY[selected.module] : undefined;

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />

      {/* List pane */}
      <Box
        sx={{
          width: { xs: '100%', md: 380 },
          flexShrink: 0,
          borderRight: 1,
          borderColor: 'divider',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <Box sx={{ p: 1.5, borderBottom: 1, borderColor: 'divider' }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 700, display: 'flex', alignItems: 'center', gap: 1 }}>
            <Star color="primary" fontSize="small" /> Bookmarks · {items.length}
          </Typography>
        </Box>
        <Box sx={{ flex: 1, overflow: 'auto' }}>
          {groups.map(([module, list]) => {
            const meta = modules.find((m) => m.moduleKey === module);
            return (
              <Box key={module}>
                <Typography
                  variant="overline"
                  color="text.secondary"
                  sx={{ px: 1.5, pt: 1, display: 'block' }}
                >
                  {ENTITY_BY_KEY[module]?.label ?? module} · {list.length}
                </Typography>
                <List dense disablePadding>
                  {list.map((it) => (
                    <ListItemButton
                      key={`${it.module}-${it.id}`}
                      divider
                      selected={selected?.module === it.module && selected?.id === it.id}
                      onClick={() => setSelected({ module: it.module, id: it.id })}
                    >
                      <ModuleBar code={meta?.code} color={meta?.color} title={ENTITY_BY_KEY[module]?.label} />
                      <ListItemText
                        primary={it.title || '(untitled)'}
                        primaryTypographyProps={{ dir: 'auto', noWrap: true, sx: { textAlign: 'start' } }}
                      />
                      <Chip size="small" variant="outlined" label={`#${it.id}`} sx={{ ml: 1 }} />
                    </ListItemButton>
                  ))}
                </List>
              </Box>
            );
          })}
          {items.length === 0 && (
            <Typography sx={{ p: 3, color: 'text.secondary' }}>No bookmarked records yet.</Typography>
          )}
        </Box>
      </Box>

      {/* Detail pane */}
      <Box sx={{ flex: 1, overflow: 'auto' }}>
        {selected && selectedConfig ? (
          <DetailPane
            config={selectedConfig}
            id={selected.id}
            onEdit={() => navigate(`/${selected.module}?select=${selected.id}`)}
            onDeleted={() => {
              setSelected(null);
              qc.invalidateQueries({ queryKey: ['bookmarks'] });
            }}
          />
        ) : (
          <Box sx={{ p: 6, color: 'text.secondary' }}>
            <Typography>Select a bookmark to read it here.</Typography>
          </Box>
        )}
      </Box>
    </Box>
  );
}
