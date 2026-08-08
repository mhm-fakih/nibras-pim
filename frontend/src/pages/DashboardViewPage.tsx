import { useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Box, CircularProgress, Paper, Stack, Typography } from '@mui/material';
import DashboardOutlined from '@mui/icons-material/DashboardOutlined';
import { getPublicDashboard } from '../api/client';
import { PublicDashboardComponent } from '../components/DashboardComponents';

/** Full-screen, centered message — used whenever this page can't show the dashboard itself. Kept
 * calm and legible from across a room: this is the one screen in the app most likely to be sitting
 * unattended on a TV, so a broken/missing dashboard should read clearly at a glance, not as a stray
 * line of red text (or, before this route existed, the server's raw Whitelabel error page). */
function FullScreenMessage({ heading, detail }: { heading: string; detail?: string }) {
  return (
    <Box
      sx={{
        height: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: 'background.default',
      }}
    >
      <Stack spacing={2} alignItems="center" sx={{ textAlign: 'center', px: 4 }}>
        <DashboardOutlined sx={{ fontSize: 56, color: 'text.disabled' }} />
        <Typography variant="h5" sx={{ fontWeight: 700 }}>
          {heading}
        </Typography>
        {detail && (
          <Typography color="text.secondary" sx={{ maxWidth: 480 }}>
            {detail}
          </Typography>
        )}
      </Stack>
    </Box>
  );
}

/** Chrome-free, unauthenticated rendering of one dashboard — no SideNav, no login, meant to be
 * left open on a TV or a spare monitor. Everything comes from one public snapshot call (see
 * PublicDashboardService on the backend); this route sits outside the app's RequireAuth wrapper in
 * main.tsx so it renders with no session at all. `/dashboards` (the builder) is where
 * regions/components get configured and needs a normal login. */
export function DashboardViewPage() {
  const { id } = useParams();
  const dashboardId = Number(id);
  const { data, isLoading, isError } = useQuery({
    queryKey: ['public-dashboard', dashboardId],
    queryFn: () => getPublicDashboard(dashboardId),
    enabled: Number.isFinite(dashboardId),
    staleTime: 0,
    refetchInterval: 2 * 60 * 1000,
  });

  // A full page reload every refreshMinutes — the simplest way to guarantee a screen left on this
  // page for days never runs stale JS or accumulates memory, on top of the snapshot's own periodic
  // refetch.
  const refreshMinutes = data?.refreshMinutes;
  useEffect(() => {
    if (!refreshMinutes || refreshMinutes <= 0) return;
    const id = window.setInterval(() => window.location.reload(), refreshMinutes * 60 * 1000);
    return () => window.clearInterval(id);
  }, [refreshMinutes]);

  if (!Number.isFinite(dashboardId)) {
    return (
      <FullScreenMessage
        heading="No dashboard specified"
        detail="This link is missing a dashboard id — check the URL, or pick a dashboard from the builder."
      />
    );
  }
  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', height: '100vh', alignItems: 'center', justifyContent: 'center' }}>
        <CircularProgress />
      </Box>
    );
  }
  if (isError || !data) {
    return (
      <FullScreenMessage
        heading="Dashboard not found"
        detail={`No dashboard exists with id ${id} — it may have been deleted, or the link is out of date.`}
      />
    );
  }

  const regions = [...data.regions].sort((a, b) => a.orderIndex - b.orderIndex);
  const columns = data.columnsCount || 2;
  // Calendar (and kanban, which can also run long) claim real vertical space and stretch to fill
  // it; everything else (quote, file, the heatmap) just wraps its own content — stretching those
  // to match a tall neighbour left a bordered box full of empty space below the actual text.
  const fillsHeight = (type: string) => type === 'calendar' || type === 'kanban';

  return (
    <Box sx={{ height: '100vh', overflow: 'hidden', position: 'relative', bgcolor: 'background.default' }}>
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: `repeat(${columns}, 1fr)`,
          gap: 2,
          p: 2,
          height: '100%',
          boxSizing: 'border-box',
          overflow: 'auto',
        }}
      >
        {regions.map((r) => {
          const fill = fillsHeight(r.componentType);
          const span = Math.min(Math.max(r.colSpan ?? 1, 1), columns);
          return (
            <Paper
              key={r.id ?? r.orderIndex}
              variant="outlined"
              sx={{
                p: 2,
                gridColumn: `span ${span}`,
                minHeight: fill ? '70vh' : 120,
                alignSelf: fill ? 'stretch' : 'start',
                display: 'flex',
                flexDirection: 'column',
                overflow: 'hidden',
              }}
            >
              <PublicDashboardComponent region={r} />
            </Paper>
          );
        })}
        {regions.length === 0 && (
          <Typography color="text.secondary" sx={{ p: 2 }}>
            This dashboard has no regions yet — add some from the builder.
          </Typography>
        )}
      </Box>
    </Box>
  );
}
