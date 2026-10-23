import { useMemo } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { useQueries, useQuery } from '@tanstack/react-query';
import {
  Box,
  Chip,
  CircularProgress,
  Divider,
  Link,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import AddCircleOutline from '@mui/icons-material/AddCircleOutline';
import EditNote from '@mui/icons-material/EditNote';
import { EntityRecord, getSetting, listEntities } from '../api/client';
import { SideNav } from '../components/SideNav';
import { ENTITY_BY_KEY } from '../config/entities';
import { useModules } from '../hooks/useModules';

/** How many records each column shows, unless Settings says otherwise. */
export const DEFAULT_TODAY_COUNT = 5;
export const TODAY_COUNT_KEY = 'home.today.count';

/** The "main" record types — the ones a person writes into. Lookups aren't news. */
const MAIN_MODULES = ['tasks', 'journals', 'planners', 'resources', 'notes', 'writings', 'goals'];

/** Short, human date-time for a record row. */
function when(iso: string | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const today = new Date();
  const sameDay =
    d.getFullYear() === today.getFullYear() &&
    d.getMonth() === today.getMonth() &&
    d.getDate() === today.getDate();
  return sameDay
    ? d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    : d.toLocaleDateString([], { day: '2-digit', month: 'short' });
}

/**
 * The landing page: what has just happened, per module.
 *
 * <p>It replaces a redirect to whichever module happened to sort first — a homepage that told you
 * nothing you didn't already know. Two columns per module: what was **created** and what was
 * **modified**, because they answer different questions ("what did I start?" vs "what am I still
 * working on?").
 *
 * <p>A record only appears under Modified if it has actually been touched since it was written.
 * Otherwise every new record would show up twice and the second column would just be a copy of the
 * first, which is the failure mode that makes a page like this ignorable.
 */
export function TodayPage() {
  const { enabled, isLoading: modulesLoading } = useModules();

  const { data: countSetting } = useQuery({
    queryKey: ['setting', TODAY_COUNT_KEY],
    queryFn: () => getSetting(TODAY_COUNT_KEY, String(DEFAULT_TODAY_COUNT)),
  });
  const count = useMemo(() => {
    const n = Number(countSetting);
    return Number.isFinite(n) && n > 0 ? Math.min(Math.trunc(n), 50) : DEFAULT_TODAY_COUNT;
  }, [countSetting]);

  const modules = enabled.filter((m) => MAIN_MODULES.includes(m.moduleKey));

  // Two queries per module. `sort` is a Spring Pageable sort, so "newest created" and "newest
  // touched" are the server's orderings, not a client-side re-sort of one page.
  const created = useQueries({
    queries: modules.map((m) => ({
      queryKey: ['today', 'created', m.moduleKey, count],
      queryFn: () => listEntities(m.moduleKey, { size: count, sort: 'dateCreated,desc' }),
    })),
  });
  const updated = useQueries({
    queries: modules.map((m) => ({
      queryKey: ['today', 'updated', m.moduleKey, count],
      queryFn: () => listEntities(m.moduleKey, { size: count * 2, sort: 'lastUpdated,desc' }),
    })),
  });

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: 4, maxWidth: 1400 }}>
        <Typography variant="h4" sx={{ fontWeight: 700, mb: 0.5 }}>
          Today
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          The latest {count} created and the latest {count} modified in each module. Change the
          number in Settings.
        </Typography>

        {modulesLoading && <CircularProgress size={22} />}

        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(420px, 1fr))',
            gap: 2,
            alignItems: 'start',
          }}
        >
          {modules.map((m, i) => (
            <ModuleCard
              key={m.moduleKey}
              moduleKey={m.moduleKey}
              label={ENTITY_BY_KEY[m.moduleKey]?.label ?? m.label}
              count={count}
              created={created[i]?.data?.content ?? []}
              updated={updated[i]?.data?.content ?? []}
              loading={Boolean(created[i]?.isLoading || updated[i]?.isLoading)}
            />
          ))}
        </Box>
      </Box>
    </Box>
  );
}

function ModuleCard({
  moduleKey,
  label,
  count,
  created,
  updated,
  loading,
}: {
  moduleKey: string;
  label: string;
  count: number;
  created: EntityRecord[];
  updated: EntityRecord[];
  loading: boolean;
}) {
  // "Modified" means modified SINCE creation. A record saved once and never touched again is news
  // in the left column only; repeating it on the right would make the column meaningless.
  const modified = updated
    .filter((r) => r.lastUpdated && r.dateCreated && r.lastUpdated > r.dateCreated)
    .slice(0, count);

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
        <Link
          component={RouterLink}
          to={`/${moduleKey}`}
          underline="hover"
          color="inherit"
          sx={{ fontWeight: 700 }}
        >
          {label}
        </Link>
        {loading && <CircularProgress size={14} />}
      </Stack>
      <Divider sx={{ mb: 1 }} />

      <RecordColumn
        moduleKey={moduleKey}
        title="Created"
        icon={<AddCircleOutline fontSize="inherit" />}
        records={created}
        stamp={(r) => when(r.dateCreated)}
        empty="Nothing yet."
      />
      <Box sx={{ mt: 1.5 }}>
        <RecordColumn
          moduleKey={moduleKey}
          title="Modified"
          icon={<EditNote fontSize="inherit" />}
          records={modified}
          stamp={(r) => when(r.lastUpdated)}
          empty="Nothing edited since it was written."
        />
      </Box>
    </Paper>
  );
}

function RecordColumn({
  moduleKey,
  title,
  icon,
  records,
  stamp,
  empty,
}: {
  moduleKey: string;
  title: string;
  icon: React.ReactNode;
  records: EntityRecord[];
  stamp: (r: EntityRecord) => string;
  empty: string;
}) {
  return (
    <>
      <Stack direction="row" alignItems="center" spacing={0.5} sx={{ mb: 0.5, fontSize: 14 }}>
        {icon}
        <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
          {title}
        </Typography>
        <Chip size="small" variant="outlined" label={records.length} sx={{ height: 16, fontSize: 10 }} />
      </Stack>
      {records.length === 0 ? (
        <Typography variant="caption" color="text.disabled">
          {empty}
        </Typography>
      ) : (
        records.map((r) => (
          <Stack key={r.id} direction="row" spacing={1} alignItems="baseline" sx={{ py: 0.25 }}>
            <Typography
              variant="caption"
              color="text.secondary"
              sx={{ minWidth: 46, flexShrink: 0, fontVariantNumeric: 'tabular-nums' }}
            >
              {stamp(r)}
            </Typography>
            <Link
              component={RouterLink}
              to={`/${moduleKey}?select=${r.id}`}
              underline="hover"
              color="inherit"
              variant="body2"
              // Direction from the title itself: Arabic aligns right with its "…" on the left end,
              // as in the module lists.
              dir="auto"
              sx={{
                flex: 1,
                minWidth: 0,
                textAlign: 'start',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {r.displayTitle || `#${r.id}`}
            </Link>
          </Stack>
        ))
      )}
    </>
  );
}
