import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Box, Button, CircularProgress, Stack, Typography } from '@mui/material';
import PrintOutlined from '@mui/icons-material/Print';
import { EntityRecord, coverUrl, fetchBlob, getReport, runReport } from '../api/client';
import { ReportTable } from '../components/ReportTable';
import { ENTITY_BY_KEY } from '../config/entities';

/** Rows fetched per request while collecting the whole report. */
const CHUNK = 200;

/**
 * Ceiling on how much one printout may hold.
 *
 * A report over a module with tens of thousands of records would otherwise try to build a table of
 * all of them in the DOM and hand the browser a thousand-page print job. The page says when it has
 * stopped short rather than quietly printing a prefix — a truncated report that looks complete is
 * the one thing worse than a truncated report.
 */
const MAX_ROWS = 2000;

/**
 * A report as a sheet of paper: no nav rail, no app chrome, black on white, table headers repeated
 * on every page.
 *
 * <h3>Why a separate route rather than a print stylesheet on the report runner</h3>
 * The runner lives inside the app shell, which is a full-height flex layout with its own scrolling
 * panes — exactly the structure that prints as one page of whatever happened to be in view.
 * Untangling that with `@media print` overrides means the printed output depends on the shell's
 * layout continuing to work the way it does today. A page of its own has one job and keeps it.
 *
 * <h3>Every row, not the page you were looking at</h3>
 * The runner is paginated because a screen is. Paper isn't: printing "the report" and getting rows
 * 1–50 of 300 would be a bug, so this collects the lot (up to [MAX_ROWS]) before offering to print.
 */
export function ReportPrintPage() {
  const { id } = useParams();
  const reportId = Number(id);

  const { data: report, isError: reportFailed } = useQuery({
    queryKey: ['report', reportId],
    queryFn: () => getReport(reportId),
    enabled: Number.isFinite(reportId),
  });

  const { data: all, isError: runFailed } = useQuery({
    queryKey: ['report-print-rows', reportId],
    enabled: Number.isFinite(reportId),
    queryFn: async () => {
      const rows: EntityRecord[] = [];
      let page = 0;
      let totalElements = 0;
      for (;;) {
        const chunk = await runReport(reportId, page, CHUNK);
        totalElements = chunk.totalElements;
        rows.push(...chunk.content);
        page += 1;
        if (chunk.content.length === 0 || page >= chunk.totalPages || rows.length >= MAX_ROWS) break;
      }
      return { rows: rows.slice(0, MAX_ROWS), totalElements };
    },
  });

  const covers = useCovers(report?.moduleKey, report?.columns, all?.rows);

  const title = report?.name ?? 'Report';
  useEffect(() => {
    document.title = title;
  }, [title]);

  if (reportFailed || runFailed) {
    return (
      <Box sx={{ p: 6 }}>
        <Typography color="error">This report could not be loaded.</Typography>
      </Box>
    );
  }
  if (!report || !all) {
    return (
      <Box sx={{ p: 6, display: 'flex', justifyContent: 'center' }}>
        <CircularProgress />
      </Box>
    );
  }

  const truncated = all.totalElements > all.rows.length;

  return (
    <Box sx={PRINT_SX}>
      {/* Everything in here is screen-only — see PRINT_SX. */}
      <Stack
        className="no-print"
        direction="row"
        spacing={2}
        alignItems="center"
        sx={{ mb: 3, flexWrap: 'wrap' }}
      >
        <Button
          variant="contained"
          startIcon={<PrintOutlined />}
          onClick={() => window.print()}
          disabled={!covers.ready}
        >
          Print
        </Button>
        <Typography variant="body2" color="text.secondary">
          {covers.ready
            ? `${all.rows.length} row(s) ready.`
            : `Loading ${covers.pending} image(s) — printing now would leave them blank.`}
        </Typography>
      </Stack>

      <Box component="header" sx={{ mb: 2 }}>
        <Typography variant="h5" sx={{ fontWeight: 700 }}>
          {report.name}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {ENTITY_BY_KEY[report.moduleKey]?.label ?? report.moduleKey} · {all.rows.length} of{' '}
          {all.totalElements} record(s) · {new Date().toLocaleDateString()}
        </Typography>
        {report.query && (
          <Typography variant="caption" component="pre" sx={{ m: 0, mt: 0.5, whiteSpace: 'pre-wrap' }}>
            {report.query}
          </Typography>
        )}
        {report.notes && (
          <Typography variant="body2" sx={{ mt: 1 }}>
            {report.notes}
          </Typography>
        )}
        {truncated && (
          <Typography variant="body2" color="error" sx={{ mt: 1 }}>
            Showing the first {all.rows.length} of {all.totalElements} records — narrow the report to
            print the rest.
          </Typography>
        )}
      </Box>

      <ReportTable moduleKey={report.moduleKey} columns={report.columns} rows={all.rows} />
    </Box>
  );
}

/**
 * Pre-fetches every cover the printout needs.
 *
 * Covers come from a token-protected endpoint, so they are fetched as blobs rather than being
 * ordinary `<img src>` the print engine would load for itself. That makes "are the images ready?" a
 * real question with a wrong answer: print too early and the covers column comes out empty. So they
 * are loaded up front and the Print button waits for them.
 *
 * The object URLs are deliberately NOT revoked per-render — they must outlive the print dialog,
 * which is a browser-modal the page can't observe. They die with the tab, which for a page whose
 * whole purpose is to be printed and closed is the right lifetime.
 */
function useCovers(moduleKey?: string, columns?: string[], rows?: EntityRecord[]) {
  const needed = useMemo(() => {
    if (!moduleKey || !columns?.includes('cover') || !rows) return [];
    return rows.filter((r) => r.hasCover).map((r) => r.id);
  }, [moduleKey, columns, rows]);

  const [loaded, setLoaded] = useState(0);

  useEffect(() => {
    if (!moduleKey || needed.length === 0) {
      setLoaded(0);
      return;
    }
    let active = true;
    setLoaded(0);
    Promise.all(
      needed.map((recordId) =>
        fetchBlob(coverUrl(moduleKey, recordId))
          .catch(() => null)
          .finally(() => {
            if (active) setLoaded((n) => n + 1);
          }),
      ),
    );
    return () => {
      active = false;
    };
  }, [moduleKey, needed]);

  return { ready: loaded >= needed.length, pending: Math.max(0, needed.length - loaded) };
}

/**
 * Screen styling plus the print rules.
 *
 * `thead { display: table-header-group }` is the one that matters most: without it a report longer
 * than a page has column headers on page 1 only, and pages 2+ are a grid of unlabelled values.
 */
const PRINT_SX = {
  p: { xs: 2, sm: 4 },
  maxWidth: 1100,
  mx: 'auto',
  bgcolor: 'background.default',
  minHeight: '100vh',
  '@media print': {
    p: 0,
    maxWidth: 'none',
    minHeight: 0,
    bgcolor: '#fff',
    color: '#000',
    '.no-print': { display: 'none !important' },
    // The table must not sit in a scroller on paper — there is nowhere to scroll to.
    '& .MuiBox-root': { overflow: 'visible !important' },
    '& table': { width: '100%', borderCollapse: 'collapse', fontSize: '10pt' },
    '& thead': { display: 'table-header-group' },   // repeat headers on every printed page
    '& tr': { pageBreakInside: 'avoid', breakInside: 'avoid' },
    '& th, & td': { border: '1px solid #999', padding: '4px 6px', color: '#000' },
    '& img': { maxWidth: '18mm', maxHeight: '18mm' },
    '& .MuiChip-root': {
      border: '1px solid #999',
      background: 'transparent !important',
      color: '#000 !important',
      height: 'auto',
    },
  },
} as const;
