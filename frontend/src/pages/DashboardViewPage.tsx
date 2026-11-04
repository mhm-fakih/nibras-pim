import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Box, CircularProgress, Paper, Stack, Typography } from '@mui/material';
import DashboardOutlined from '@mui/icons-material/DashboardOutlined';
import { getPublicDashboard, saveDashboardColumnWidths, saveDashboardRowHeights } from '../api/client';
import { dragPair, parseWeights, placeRegions, rowSeparators } from '../util/dashboardLayout';
import { Hour12Context } from '../hooks/useHour12';
import { PublicDashboardComponent, parseColumnWidths, regionZoom } from '../components/DashboardComponents';

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
const GAP = 16; // the grid's gap, px (MUI spacing 2)
const PAD = 16; // the grid's padding, px
const localKey = (id: number, what: 'columnWidths' | 'rowHeights' = 'columnWidths') => `nibras.dashboard.${id}.${what}`;
/** No row is dragged shorter than this. */
const MIN_ROW_PX = 120;

function readLocal(id: number, what: 'columnWidths' | 'rowHeights' = 'columnWidths'): string | null {
  try {
    return window.localStorage.getItem(localKey(id, what));
  } catch {
    return null;
  }
}

function writeLocal(id: number, widths: string | null, what: 'columnWidths' | 'rowHeights' = 'columnWidths') {
  try {
    if (widths) window.localStorage.setItem(localKey(id, what), widths);
    else window.localStorage.removeItem(localKey(id, what));
  } catch {
    /* private window: the sizes just don't outlive the page */
  }
}

const fmt = (ws: number[]) => ws.map((w) => Math.round(w * 100) / 100).join(',');

export function DashboardViewPage() {
  const { id } = useParams();
  const dashboardId = Number(id);
  const qc = useQueryClient();
  const gridRef = useRef<HTMLDivElement>(null);
  const [gridWidth, setGridWidth] = useState(0);
  // While dragging: the live widths. After a drop that couldn't be saved to the server (no
  // session on this screen): the widths kept in this browser.
  const [dragWidths, setDragWidths] = useState<number[] | null>(null);
  const [localWidths, setLocalWidths] = useState<string | null>(() =>
    Number.isFinite(dashboardId) ? readLocal(dashboardId) : null,
  );
  const [savedNote, setSavedNote] = useState<string | null>(null);
  // The same pair for row heights (the separators between stacked regions).
  const [dragHeights, setDragHeights] = useState<number[] | null>(null);
  const [localHeights, setLocalHeights] = useState<string | null>(() =>
    Number.isFinite(dashboardId) ? readLocal(dashboardId, 'rowHeights') : null,
  );
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

  // The grid's width, for placing the separators and turning a drag in px into width units.
  const hasData = !!data;
  useEffect(() => {
    const el = gridRef.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setGridWidth(el.clientWidth));
    ro.observe(el);
    setGridWidth(el.clientWidth);
    return () => ro.disconnect();
  }, [hasData]);

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
  const widths = dragWidths ?? parseColumnWidths(localWidths ?? data.columnWidths, columns);
  const total = widths.reduce((a, b) => a + b, 0);
  const tracks = Math.max(0, gridWidth - 2 * PAD - GAP * (columns - 1));
  // x of the middle of the gap after column i, for the separator handles.
  const separators = widths.slice(0, -1).map((_, i) => {
    const before = widths.slice(0, i + 1).reduce((a, b) => a + b, 0);
    return PAD + (tracks * before) / total + GAP * i + GAP / 2;
  });

  // Pinned placement (the browser's own auto-placement, computed here) so the rows are known.
  const { placed, rows } = placeRegions(
    regions.map((r) => ({ colSpan: r.colSpan ?? 1, rowSpan: Math.min(Math.max(r.rowSpan ?? 1, 1), 6) })),
    columns,
  );
  const heights = dragHeights ?? parseWeights(localHeights ?? data.rowHeights, rows);
  const rowSeps = rowSeparators(placed, rows, columns);

  const startRowDrag = (i: number) => (e: ReactPointerEvent<HTMLDivElement>) => {
    e.preventDefault();
    const grid = gridRef.current;
    if (!grid) return;
    // The rows' real heights, as laid out — the browser resolves the fr tracks to px here.
    const px = getComputedStyle(grid).gridTemplateRows.split(' ').map((v) => parseFloat(v) || 0);
    const startY = e.clientY;
    const start = [...heights];
    let latest = start;
    const move = (ev: PointerEvent) => {
      latest = dragPair(start, px, i, ev.clientY - startY, MIN_ROW_PX);
      setDragHeights(latest);
    };
    const up = async () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
      const value = fmt(latest);
      if (await saveDashboardRowHeights(dashboardId, value)) {
        writeLocal(dashboardId, null, 'rowHeights');
        setLocalHeights(null);
        await qc.invalidateQueries({ queryKey: ['public-dashboard', dashboardId] });
        setSavedNote('Row heights saved.');
      } else {
        writeLocal(dashboardId, value, 'rowHeights');
        setLocalHeights(value);
        setSavedNote('Row heights kept on this screen only — sign in here to save them for every screen.');
      }
      setDragHeights(null);
      window.setTimeout(() => setSavedNote(null), 5000);
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  };

  const startDrag = (i: number) => (e: ReactPointerEvent<HTMLDivElement>) => {
    e.preventDefault();
    const startX = e.clientX;
    const start = [...widths];
    const pair = start[i] + start[i + 1];
    const min = (total * 0.05); // no column narrower than 5 % of the whole
    let latest = start;
    const move = (ev: PointerEvent) => {
      const dw = tracks > 0 ? ((ev.clientX - startX) * total) / tracks : 0;
      const left = Math.min(pair - min, Math.max(min, start[i] + dw));
      latest = start.map((w, k) => (k === i ? left : k === i + 1 ? pair - left : w));
      setDragWidths(latest);
    };
    const up = async () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
      const value = fmt(latest);
      if (await saveDashboardColumnWidths(dashboardId, value)) {
        writeLocal(dashboardId, null);
        setLocalWidths(null);
        await qc.invalidateQueries({ queryKey: ['public-dashboard', dashboardId] });
        setSavedNote('Column widths saved.');
      } else {
        writeLocal(dashboardId, value);
        setLocalWidths(value);
        setSavedNote('Column widths kept on this screen only — sign in here to save them for every screen.');
      }
      setDragWidths(null);
      window.setTimeout(() => setSavedNote(null), 5000);
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  };
  // Calendar (and kanban, which can also run long) claim real vertical space and stretch to fill
  // it; everything else (quote, file, the heatmap) just wraps its own content — stretching those
  // to match a tall neighbour left a bordered box full of empty space below the actual text.
  const fillsHeight = (type: string) => ['calendar', 'kanban', 'checklist', 'tasks', 'localTasks', 'plans'].includes(type);

  return (
    // The clock comes with the snapshot: this page makes no signed-in requests (see Hour12Context).
    <Hour12Context.Provider value={data.hour12 ?? true}>
    <Box sx={{ height: '100vh', overflow: 'hidden', position: 'relative', bgcolor: 'background.default' }}>
      <Box
        ref={gridRef}
        sx={{
          display: 'grid',
          gridTemplateColumns: widths.map((w) => `minmax(0, ${w}fr)`).join(' '),
          // Rows share the screen's height by their weights (equal until a row separator is
          // dragged; a region spanning two takes both shares); past what fits, rows keep a usable
          // minimum and the page scrolls.
          gridTemplateRows: heights.map((h) => `minmax(${MIN_ROW_PX}px, ${h}fr)`).join(' ') || undefined,
          gridAutoRows: 'minmax(260px, 1fr)',
          gap: 2,
          p: 2,
          height: '100%',
          boxSizing: 'border-box',
          overflow: 'auto',
        }}
      >
        {regions.map((r, idx) => {
          const fill = fillsHeight(r.componentType);
          const at = placed[idx];
          return (
            <Paper
              key={r.id ?? r.orderIndex}
              variant="outlined"
              sx={{
                p: 2,
                gridColumn: `${at.col + 1} / span ${at.colSpan}`,
                gridRow: `${at.row + 1} / span ${at.rowSpan}`,
                minHeight: 0,
                alignSelf: fill ? 'stretch' : 'start',
                display: 'flex',
                flexDirection: 'column',
                overflow: 'hidden',
              }}
            >
              <Box
                sx={{
                  zoom: regionZoom(r.config),
                  flex: 1,
                  minHeight: 0,
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <PublicDashboardComponent region={r} />
              </Box>
            </Paper>
          );
        })}
        {/* Row separators: drag one to move the boundary between two stacked regions. Grid items
            in the upper row's cells, pulled down into the gap, so they sit exactly between the
            two whatever the heights or the scroll. Invisible until hovered. */}
        {rowSeps.map((sep) => (
          <Box
            key={`r${sep.row}-${sep.fromCol}`}
            onPointerDown={startRowDrag(sep.row)}
            title="Drag to resize the rows"
            sx={{
              gridRow: `${sep.row + 1}`,
              gridColumn: `${sep.fromCol + 1} / ${sep.toCol + 2}`,
              alignSelf: 'end',
              height: 12,
              mb: `${-(GAP / 2 + 6)}px`,
              position: 'relative',
              zIndex: 2,
              cursor: 'row-resize',
              touchAction: 'none',
              '&::after': {
                content: '""',
                position: 'absolute',
                left: 8,
                right: 8,
                top: 5,
                height: 2,
                borderRadius: 1,
                bgcolor: 'primary.main',
                opacity: dragHeights ? 0.6 : 0,
                transition: 'opacity 120ms',
              },
              '&:hover::after': { opacity: 0.6 },
            }}
          />
        ))}
        {regions.length === 0 && (
          <Typography color="text.secondary" sx={{ p: 2 }}>
            This dashboard has no regions yet — add some from the builder.
          </Typography>
        )}
      </Box>
      {/* Column separators: drag one to move the boundary between two columns. Invisible until
          hovered, so a TV shows just the dashboard. */}
      {columns > 1 &&
        separators.map((x, i) => (
          <Box
            key={i}
            onPointerDown={startDrag(i)}
            title="Drag to resize the columns"
            sx={{
              position: 'absolute',
              top: 0,
              bottom: 0,
              left: x - 6,
              width: 12,
              cursor: 'col-resize',
              touchAction: 'none',
              zIndex: 2,
              '&::after': {
                content: '""',
                position: 'absolute',
                top: 8,
                bottom: 8,
                left: 5,
                width: 2,
                borderRadius: 1,
                bgcolor: 'primary.main',
                opacity: dragWidths ? 0.6 : 0,
                transition: 'opacity 120ms',
              },
              '&:hover::after': { opacity: 0.6 },
            }}
          />
        ))}
      {savedNote && (
        <Paper
          elevation={3}
          sx={{ position: 'absolute', bottom: 16, left: '50%', transform: 'translateX(-50%)', px: 2, py: 1, zIndex: 3 }}
        >
          <Typography variant="body2">{savedNote}</Typography>
        </Paper>
      )}
    </Box>
    </Hour12Context.Provider>
  );
}
