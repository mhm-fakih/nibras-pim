import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Box, Paper, Stack, Typography } from '@mui/material';
import Flag from '@mui/icons-material/Flag';
import CheckCircleOutline from '@mui/icons-material/CheckCircleOutline';
import { EntityRecord, getKanban } from '../api/client';
import { SideNav } from '../components/SideNav';
import { RecordCard } from '../components/RecordCard';

/** Completed = stamped completedOn (mobile flow) or status "done" (web flow). */
function isDone(r: EntityRecord): boolean {
  return r.completedOn != null || r.status?.code === 'done';
}

/** Open tasks first, then by priority (desc), then newest first. */
function taskOrder(a: EntityRecord, b: EntityRecord): number {
  const done = Number(isDone(a)) - Number(isDone(b));
  if (done !== 0) return done;
  const prio = ((b.priority as number) ?? 0) - ((a.priority as number) ?? 0);
  if (prio !== 0) return prio;
  return b.dateCreated.localeCompare(a.dateCreated);
}

function priorityOrder(a: EntityRecord, b: EntityRecord): number {
  const prio = ((b.priority as number) ?? 0) - ((a.priority as number) ?? 0);
  if (prio !== 0) return prio;
  return b.dateCreated.localeCompare(a.dateCreated);
}

interface Column {
  code: string;
  name: string;
  goals: EntityRecord[];
  /** Tasks with no goal link (or a goal that isn't on the board) — kept in their own dept. */
  looseTasks: EntityRecord[];
}

export function KanbanPage() {
  const navigate = useNavigate();
  const { data } = useQuery({ queryKey: ['kanban'], queryFn: getKanban });
  const goals = useMemo(() => data?.goals ?? [], [data]);
  const tasks = useMemo(() => data?.tasks ?? [], [data]);

  // Tasks nest under their goal (wherever that goal's column is); the rest go to their own dept.
  const tasksByGoal = useMemo(() => {
    const shown = new Set(goals.map((g) => g.id));
    const map = new Map<number, EntityRecord[]>();
    for (const t of tasks) {
      if (t.goalId != null && shown.has(t.goalId)) {
        if (!map.has(t.goalId)) map.set(t.goalId, []);
        map.get(t.goalId)!.push(t);
      }
    }
    for (const list of map.values()) list.sort(taskOrder);
    return map;
  }, [goals, tasks]);

  const columns = useMemo<Column[]>(() => {
    const shown = new Set(goals.map((g) => g.id));
    const map = new Map<string, Column>();
    const col = (r: EntityRecord) => {
      const key = r.department?.code ?? '—';
      if (!map.has(key)) {
        map.set(key, {
          code: key,
          name: r.department?.name ?? 'No department',
          goals: [],
          looseTasks: [],
        });
      }
      return map.get(key)!;
    };
    for (const g of goals) col(g).goals.push(g);
    for (const t of tasks) {
      if (t.goalId == null || !shown.has(t.goalId)) col(t).looseTasks.push(t);
    }
    for (const c of map.values()) {
      c.goals.sort(priorityOrder);
      c.looseTasks.sort(taskOrder);
    }
    return [...map.values()].sort((a, b) => a.code.localeCompare(b.code));
  }, [goals, tasks]);

  const doneSx = { textDecoration: 'line-through', opacity: 0.55 } as const;

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <Typography variant="h4" sx={{ fontWeight: 700, p: 2, pb: 1 }}>
          Kanban — goals &amp; their tasks
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
                sx={{ width: 360, flexShrink: 0, height: '100%', overflow: 'auto', bgcolor: '#fafafa' }}
              >
                <Box
                  sx={{
                    p: 1.5,
                    position: 'sticky',
                    top: 0,
                    bgcolor: '#fafafa',
                    zIndex: 1,
                    borderBottom: 1,
                    borderColor: 'divider',
                  }}
                >
                  <Typography sx={{ fontWeight: 700 }} dir="auto">
                    <code>{col.code}</code> {col.name}
                  </Typography>
                </Box>
                <Box sx={{ p: 1 }}>
                  {col.goals.length > 0 && (
                    <SectionTitle icon={<Flag fontSize="small" />} title="Goals" />
                  )}
                  <Stack spacing={1.5}>
                    {col.goals.map((g) => (
                      <Box key={g.id}>
                        <RecordCard module="goals" record={g} onClick={() => navigate('/goals')} />
                        {(tasksByGoal.get(g.id) ?? []).length > 0 && (
                          <Stack
                            spacing={0.5}
                            sx={{ mt: 0.5, ml: 1.5, pl: 1, borderLeft: 2, borderColor: 'divider' }}
                          >
                            {tasksByGoal.get(g.id)!.map((t) => (
                              <RecordCard
                                key={t.id}
                                module="tasks"
                                record={t}
                                onClick={() => navigate('/tasks')}
                                sx={isDone(t) ? doneSx : undefined}
                              />
                            ))}
                          </Stack>
                        )}
                      </Box>
                    ))}
                  </Stack>

                  {col.looseTasks.length > 0 && (
                    <Box sx={{ mt: col.goals.length > 0 ? 2 : 0 }}>
                      <SectionTitle
                        icon={<CheckCircleOutline fontSize="small" />}
                        title="Tasks — no goal"
                      />
                      <Stack spacing={0.5}>
                        {col.looseTasks.map((t) => (
                          <RecordCard
                            key={t.id}
                            module="tasks"
                            record={t}
                            onClick={() => navigate('/tasks')}
                            sx={isDone(t) ? doneSx : undefined}
                          />
                        ))}
                      </Stack>
                    </Box>
                  )}
                </Box>
              </Paper>
            ))}
          </Stack>
        </Box>
      </Box>
    </Box>
  );
}

function SectionTitle({ icon, title }: { icon: React.ReactNode; title: string }) {
  return (
    <Stack direction="row" spacing={0.5} alignItems="center" sx={{ mb: 0.5, color: 'text.secondary' }}>
      {icon}
      <Typography variant="overline">{title}</Typography>
    </Stack>
  );
}
