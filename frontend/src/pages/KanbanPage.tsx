import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Box, Chip, Paper, Stack, Typography } from '@mui/material';
import Flag from '@mui/icons-material/Flag';
import CheckCircleOutline from '@mui/icons-material/CheckCircleOutline';
import { getKanban, KanbanCard } from '../api/client';
import { SideNav } from '../components/SideNav';

interface Column {
  code: string;
  name: string;
  goals: KanbanCard[];
  tasks: KanbanCard[];
}

export function KanbanPage() {
  const navigate = useNavigate();
  const { data } = useQuery({ queryKey: ['kanban'], queryFn: getKanban });
  const cards = data ?? [];

  const columns = useMemo<Column[]>(() => {
    const map = new Map<string, Column>();
    for (const c of cards) {
      const key = c.departmentCode ?? '—';
      if (!map.has(key)) {
        map.set(key, { code: key, name: c.departmentName ?? 'No department', goals: [], tasks: [] });
      }
      (c.module === 'goals' ? map.get(key)!.goals : map.get(key)!.tasks).push(c);
    }
    return [...map.values()].sort((a, b) => a.code.localeCompare(b.code));
  }, [cards]);

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <Typography variant="h4" sx={{ fontWeight: 700, p: 2, pb: 1 }}>
          Kanban — bookmarked goals &amp; tasks
        </Typography>
        <Box sx={{ flex: 1, overflowX: 'auto', overflowY: 'hidden', px: 2, pb: 2 }}>
          <Stack direction="row" spacing={2} sx={{ height: '100%' }}>
            {columns.length === 0 && (
              <Typography color="text.secondary" sx={{ p: 2 }}>
                No bookmarked goals or tasks. Star some records to see them here.
              </Typography>
            )}
            {columns.map((col) => (
              <Paper
                key={col.code}
                variant="outlined"
                sx={{ width: 300, flexShrink: 0, height: '100%', overflow: 'auto', bgcolor: '#fafafa' }}
              >
                <Box sx={{ p: 1.5, position: 'sticky', top: 0, bgcolor: '#fafafa', zIndex: 1, borderBottom: 1, borderColor: 'divider' }}>
                  <Typography sx={{ fontWeight: 700 }} dir="auto">
                    <code>{col.code}</code> {col.name}
                  </Typography>
                </Box>
                <Box sx={{ p: 1 }}>
                  <Section icon={<Flag fontSize="small" />} title="Goals (by type)" cards={col.goals} onOpen={navigate} />
                  <Section icon={<CheckCircleOutline fontSize="small" />} title="Tasks (by status)" cards={col.tasks} onOpen={navigate} />
                </Box>
              </Paper>
            ))}
          </Stack>
        </Box>
      </Box>
    </Box>
  );
}

function Section({
  icon,
  title,
  cards,
  onOpen,
}: {
  icon: React.ReactNode;
  title: string;
  cards: KanbanCard[];
  onOpen: (to: string) => void;
}) {
  const groups = useMemo(() => {
    const map = new Map<string, KanbanCard[]>();
    for (const c of cards) {
      const g = c.groupName ?? '(none)';
      if (!map.has(g)) map.set(g, []);
      map.get(g)!.push(c);
    }
    return [...map.entries()].sort((a, b) => a[0].localeCompare(b[0]));
  }, [cards]);

  if (cards.length === 0) return null;

  return (
    <Box sx={{ mb: 1.5 }}>
      <Stack direction="row" spacing={0.5} alignItems="center" sx={{ mb: 0.5, color: 'text.secondary' }}>
        {icon}
        <Typography variant="overline">{title}</Typography>
      </Stack>
      {groups.map(([group, gc]) => (
        <Box key={group} sx={{ mb: 1 }}>
          <Typography variant="caption" color="text.secondary" sx={{ pl: 0.5 }} dir="auto">
            {group} · {gc.length}
          </Typography>
          <Stack spacing={0.5} sx={{ mt: 0.5 }}>
            {gc.map((c) => (
              <Paper
                key={`${c.module}-${c.id}`}
                variant="outlined"
                onClick={() => onOpen(`/${c.module}`)}
                sx={{ p: 1, cursor: 'pointer', '&:hover': { bgcolor: 'action.hover' } }}
              >
                <Typography variant="body2" dir="auto" sx={{ fontWeight: 500 }}>
                  {c.title || '(untitled)'}
                </Typography>
                {c.priority != null && (
                  <Chip size="small" label={`p${c.priority}`} sx={{ height: 16, fontSize: 10, mt: 0.5 }} />
                )}
              </Paper>
            ))}
          </Stack>
        </Box>
      ))}
    </Box>
  );
}
