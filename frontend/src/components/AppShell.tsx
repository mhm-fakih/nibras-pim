import { useContext, useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Box, Button, Snackbar, Typography, useMediaQuery, useTheme } from '@mui/material';
import ChevronLeft from '@mui/icons-material/ChevronLeft';
import { batchEdit, createJournalDay, exportFulltext, getSetting, openRecordFolder, EntityRecord, SavedSearchRecordRef } from '../api/client';
import { ENTITY_BY_KEY } from '../config/entities';
import { SearchResultsList } from './SearchResultsList';
import { ShortcutContext } from './ShortcutsProvider';
import { MiniCalendar } from './MiniCalendar';
import { SideNav } from './SideNav';
import { StatusBar } from './StatusBar';
import { ListPane } from './ListPane';
import { DetailPane } from './DetailPane';
import { EntityForm } from './EntityForm';
import { RecordCard } from './RecordCard';
import { QuickEditDialog, QuickKind } from './QuickEditDialog';
import { useAuth } from '../hooks/useAuth';

export type PaneMode = 'view' | 'edit' | 'create';

export function AppShell() {
  const { entity } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const config = entity ? ENTITY_BY_KEY[entity] : undefined;

  const [selectedDate, setSelectedDate] = useState<Date | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [mode, setMode] = useState<PaneMode>('view');
  const [itemIds, setItemIds] = useState<number[]>([]);
  const [quick, setQuick] = useState<QuickKind | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [justSaved, setJustSaved] = useState<EntityRecord | null>(null);
  // HQL saved-search results shown in the middle pane; resultModule = the opened record's module
  // (results can be a different entity than the current route, so the detail pane uses its config).
  const [searchResults, setSearchResults] = useState<{ title: string; records: SavedSearchRecordRef[] } | null>(null);
  const [resultModule, setResultModule] = useState<string | null>(null);
  // Resizable middle (list) pane — width persisted per browser (dragged via the divider handle).
  const [listWidth, setListWidth] = useState(() => {
    const raw = localStorage.getItem('nibras.listPaneWidth');
    const n = raw ? Number(raw) : NaN;
    return Number.isFinite(n) ? Math.min(900, Math.max(280, n)) : 400;
  });
  const qc = useQueryClient();
  const pendingF = useRef(false);
  const shortcuts = useContext(ShortcutContext);
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const { canWriteContent } = useAuth();

  // Reset selection when switching entities.
  useEffect(() => {
    setSelectedId(null);
    setMode('view');
    setJustSaved(null);
    setSearchResults(null);
    setResultModule(null);
  }, [entity]);

  // Tell the global shortcut handler a record is selected, so `?` becomes status quick-edit (not help).
  useEffect(() => {
    if (shortcuts) shortcuts.recordSelected.current = mode === 'view' && selectedId != null;
    return () => {
      if (shortcuts) shortcuts.recordSelected.current = false;
    };
  }, [shortcuts, mode, selectedId]);

  // Preselect a record passed via ?select=<id> (e.g. after creating a day's journal from the calendar).
  const selectParam = searchParams.get('select');
  useEffect(() => {
    if (selectParam) {
      setSelectedId(Number(selectParam));
      setMode('view');
    }
  }, [selectParam]);

  // Keyboard: list (j/k/n/o) + record (e, f→1/2/3) shortcuts; Esc backs out. Skipped while typing.
  useEffect(() => {
    if (!config) return;
    const moveSel = (delta: number) => {
      if (itemIds.length === 0) return;
      const idx = selectedId == null ? -1 : itemIds.indexOf(selectedId);
      const next = Math.max(0, Math.min(itemIds.length - 1, (idx < 0 ? -1 : idx) + delta));
      setSelectedId(itemIds[next]);
      setMode('view');
    };
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement | null;
      const typing =
        !!t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT' || t.isContentEditable);
      if (typing) {
        if (e.key === 'Escape') t!.blur();
        return;
      }
      if (e.ctrlKey || e.metaKey || e.altKey) return;
      // A `g` chord is in flight (global nav) — let ShortcutsProvider consume this key.
      if (shortcuts?.gPending.current) return;
      // While the quick-edit dialog is open, it owns the keyboard.
      if (quick) return;

      const toggleBookmark = (rid: number) => {
        const rec = qc.getQueryData(['record', config.key, rid]) as EntityRecord | undefined;
        const wasMarked = Boolean(rec?.bookmarked);
        batchEdit(config.key, [rid], wasMarked ? '*-' : '*')
          .then(() => {
            qc.invalidateQueries({ queryKey: ['record', config.key, rid] });
            qc.invalidateQueries({ queryKey: ['list', config.key] });
            setToast(wasMarked ? 'Removed bookmark' : 'Bookmarked');
          })
          .catch(() => setToast('Update failed'));
      };

      // f then 1/2/3 -> open the selected record's folder in repo 1/2/3.
      if (pendingF.current) {
        pendingF.current = false;
        if (['1', '2', '3'].includes(e.key) && selectedId != null) {
          e.preventDefault();
          openRecordFolder(config.key, selectedId, Number(e.key) as 1 | 2 | 3).catch(() => {});
          return;
        }
      }
      if (mode !== 'view') {
        if (e.key === 'Escape') setMode('view');
        return;
      }
      switch (e.key) {
        case 'j': moveSel(1); e.preventDefault(); break;
        case 'k': moveSel(-1); e.preventDefault(); break;
        case 'n': if (canWriteContent) { setSelectedId(null); setMode('create'); e.preventDefault(); } break;
        case 'e': if (canWriteContent && selectedId != null) { setMode('edit'); e.preventDefault(); } break;
        case 'o': if (selectedId != null) { setMode('view'); e.preventDefault(); } break;
        case 'Escape': if (selectedId != null) setSelectedId(null); break;
        case 'f': pendingF.current = true; setTimeout(() => (pendingF.current = false), 1200); break;
        // Quick-edit the selected record's parameters (content writers only).
        case 'p': if (canWriteContent && selectedId != null) { setQuick('priority'); e.preventDefault(); } break;
        case '#': if (canWriteContent && selectedId != null) { setQuick('type'); e.preventDefault(); } break;
        case '?': if (canWriteContent && selectedId != null) { setQuick('status'); e.preventDefault(); } break;
        case '@': if (canWriteContent && selectedId != null) { setQuick('context'); e.preventDefault(); } break;
        case 'd': if (canWriteContent && selectedId != null) { setQuick('department'); e.preventDefault(); } break;
        case 'c': if (canWriteContent && selectedId != null) { setQuick('course'); e.preventDefault(); } break;
        case '*': if (canWriteContent && selectedId != null) { e.preventDefault(); toggleBookmark(selectedId); } break;
        default: break;
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [config, selectedId, itemIds, mode, shortcuts, quick, qc, canWriteContent]);

  if (!config) {
    navigate('/tasks', { replace: true });
    return null;
  }

  // On mobile, show the list OR the detail/form (not both side by side).
  const showDetail =
    mode === 'create' || mode === 'edit' || (mode === 'view' && selectedId != null);

  // A result record can be a different entity than the current route → the detail pane uses its config.
  const detailConfig = resultModule ? ENTITY_BY_KEY[resultModule] : config;

  const startResize = (e: React.MouseEvent) => {
    e.preventDefault();
    const startX = e.clientX;
    const startW = listWidth;
    const clamp = (w: number) => Math.min(900, Math.max(280, w));
    const onMove = (ev: MouseEvent) => setListWidth(clamp(startW + (ev.clientX - startX)));
    const onUp = (ev: MouseEvent) => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
      const w = clamp(startW + (ev.clientX - startX));
      try {
        localStorage.setItem('nibras.listPaneWidth', String(w));
      } catch {
        /* ignore */
      }
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav
        onSearchRecords={(title, records) => {
          setSearchResults({ title, records });
          setResultModule(null);
          setSelectedId(null);
          setMode('view');
        }}
        calendar={
          <MiniCalendar
            entity={config.key}
            selectedDate={selectedDate}
            onSelect={(d) => {
              // Clicking a day opens that day's journal AND filters the journals list to that day
              // (date-aware: journals filter by their date, so the day's journal shows in the list).
              setSelectedDate(d);
              const day = formatLocalDate(d);
              createJournalDay(day)
                .then(async (j) => {
                  // A brand-new day journal: export its _record.md to repo 1 right away.
                  // Whether the file is opened afterwards is controlled by its own setting
                  // (calendar.journalDay.open), independent of the manual-export 'export.open'.
                  if (j.created) {
                    const open = (await getSetting('calendar.journalDay.open', 'false')) === 'true';
                    exportFulltext(j.module, j.id, open).catch(() => {});
                  }
                  navigate(`/journals?select=${j.id}&onDay=${day}`);
                })
                .catch(() => {});
            }}
          />
        }
      />

      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <Box sx={{ flex: 1, display: 'flex', overflow: 'hidden' }}>

      {/* Middle: list (full width on mobile; hidden there when a detail is open) */}
      {(!isMobile || !showDetail) && (
      <Box
        sx={{
          width: { xs: '100%', md: `${listWidth}px` },
          flexShrink: 0,
          borderRight: 1,
          borderColor: 'divider',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        {searchResults ? (
          <SearchResultsList
            title={searchResults.title}
            records={searchResults.records}
            selectedId={selectedId}
            onSelect={(rec) => {
              setSelectedId(rec.id);
              setResultModule(rec.module);
              setMode('view');
            }}
            onClose={() => {
              setSearchResults(null);
              setResultModule(null);
              setSelectedId(null);
            }}
          />
        ) : (
          <ListPane
            config={config}
            selectedId={selectedId}
            onSelect={(id) => {
              setSelectedId(id);
              setMode('view');
            }}
            onNew={() => {
              setSelectedId(null);
              setMode('create');
            }}
            onItems={setItemIds}
          />
        )}
      </Box>
      )}

      {/* Drag handle to resize the middle list pane (desktop only — both panes visible). */}
      {!isMobile && (
        <Box
          onMouseDown={startResize}
          sx={{
            width: '6px',
            flexShrink: 0,
            cursor: 'col-resize',
            ml: '-3px',
            zIndex: 2,
            '&:hover': { bgcolor: 'primary.main', opacity: 0.4 },
          }}
        />
      )}

      {/* Right: detail / form */}
      {(!isMobile || showDetail) && (
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {isMobile && (
          <Box sx={{ flexShrink: 0, borderBottom: 1, borderColor: 'divider', px: 0.5, py: 0.25 }}>
            <Button
              size="small"
              startIcon={<ChevronLeft />}
              onClick={() => {
                setSelectedId(null);
                setMode('view');
              }}
            >
              Back to list
            </Button>
          </Box>
        )}
        <Box sx={{ flex: 1, overflow: 'auto' }}>
        {mode === 'create' && (
          <EntityForm
            config={config}
            onClose={() => setMode('view')}
            onSaved={(saved) => {
              setJustSaved(saved);       // show a card of what was just created
              setSelectedId(saved.id);
              setMode('view');
            }}
          />
        )}
        {mode === 'edit' && selectedId != null && (
          <EntityForm
            config={detailConfig}
            recordId={selectedId}
            onClose={() => setMode('view')}
            onSaved={(saved) => {
              setSelectedId(saved.id);
              setMode('view');
            }}
          />
        )}
        {mode === 'view' && selectedId != null && (
          <>
            {justSaved && justSaved.id === selectedId && (
              <Box sx={{ p: 2, pb: 0 }}>
                <Typography variant="caption" color="success.main" sx={{ fontWeight: 700 }}>
                  ✓ Saved
                </Typography>
                <RecordCard module={config.key} record={justSaved} sx={{ mt: 0.5 }} />
              </Box>
            )}
            <DetailPane
              config={detailConfig}
              id={selectedId}
              onEdit={() => setMode('edit')}
              onDeleted={() => {
                setSelectedId(null);
                setResultModule(null);
              }}
            />
          </>
        )}
        {mode === 'view' && selectedId == null && (
          <Box sx={{ p: 6, color: 'text.secondary' }}>
            <Typography>Select an item, or create a new {config.label.toLowerCase()}.</Typography>
          </Box>
        )}
        </Box>
      </Box>
      )}
        </Box>
        <StatusBar />
      </Box>

      {quick && selectedId != null && (
        <QuickEditDialog
          kind={quick}
          module={config.key}
          id={selectedId}
          onClose={() => setQuick(null)}
          onApplied={(msg) => setToast(msg)}
        />
      )}
      <Snackbar
        open={Boolean(toast)}
        autoHideDuration={2500}
        onClose={() => setToast(null)}
        message={toast}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      />
    </Box>
  );
}

function formatLocalDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}
