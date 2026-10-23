import { useContext, useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Navigate, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Alert, Box, Button, Snackbar, Typography, useMediaQuery, useTheme } from '@mui/material';
import ChevronLeft from '@mui/icons-material/ChevronLeft';
import { batchEdit, createJournalDay, exportFulltext, getSetting, listEntities, listModules, openRecordFolder, setCoverFromUrl, EntityRecord, SavedSearchRecordRef,
  errorDetail,
} from '../api/client';
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
import { QuickNoteBar } from './QuickNoteBar';
import { useAuth } from '../hooks/useAuth';

export type PaneMode = 'view' | 'edit' | 'create';

/**
 * Records fetched per module for a day's cross-module view. A cap rather than every page: the
 * results pane fetches each card's body individually, so an unbounded day would fire a request per
 * record. SearchResultsList pages what it draws on top of this.
 */
const DAY_RECORD_LIMIT = 50;

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
  // Fields pulled off a URL, waiting to prefill the New-resource form (see ListPane.ScrapeFromUrl).
  // Cleared whenever the form closes so a later plain "New" starts blank. `coverUrl` rides along
  // unfetched: it can only be stored once the record exists and has an id — see onSaved below.
  const [scraped, setScraped] = useState<
    { fields: Record<string, unknown>; warnings: string[]; coverUrl: string } | null
  >(null);
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

  // Preselect a record passed via ?select=<id> (e.g. after creating a day's journal from the
  // calendar). `&edit=1` additionally opens it straight in the form — that's how the standalone
  // reading page hands a record back here to be edited, since editing lives only in this shell.
  const selectParam = searchParams.get('select');
  const editParam = searchParams.get('edit');
  useEffect(() => {
    if (selectParam) {
      setSelectedId(Number(selectParam));
      setMode(editParam === '1' && canWriteContent ? 'edit' : 'view');
    }
  }, [selectParam, editParam, canWriteContent]);

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

  // An unknown module in the address (a typo, an old link) goes to Tasks. As an element, not a
  // navigate() call: called during render, that was ignored and left a blank page.
  if (!config) return <Navigate to="/tasks" replace />;

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

  /**
   * Fetch the scraped page's main image and store it as the new record's cover. Fire-and-forget by
   * design: the record is already saved, so this can only add or fail to add a picture, and it says
   * which. The backend does the download — the browser can't read a cross-origin image as bytes.
   */
  const importCover = (module: string, id: number, url: string) => {
    setCoverFromUrl(module, id, url)
      .then(() => {
        // The cover is fetched by URL, so anything already showing one needs to look again.
        qc.invalidateQueries({ queryKey: ['repo-files', module, id] });
        setToast('Cover image imported from the page.');
      })
      .catch((e: unknown) => {
        setToast(`Saved — but the cover image could not be imported: ${errorDetail(e, 'the download failed.')}`);
      });
  };

  /**
   * The enabled modules, read through the query cache.
   *
   * Deliberately not `useModules()`: that hook falls back to "everything enabled" while its query
   * is in flight, and the caller below decides whether to CREATE a record on that answer. A wrong
   * default for a few hundred milliseconds would silently write a journal into a folder whose
   * owner switched journals off. `fetchQuery` returns the cache when it is fresh (it almost always
   * is — SideNav mounts the same query on every page) and waits for the server when it isn't, so
   * the answer is never a guess.
   */
  const enabledModules = () =>
    qc.fetchQuery({ queryKey: ['modules'], queryFn: listModules, staleTime: 60_000 })
      .then((mods) => mods.filter((m) => m.enabled));

  /**
   * Every record the enabled modules hold for one day, shown as cards in the middle pane.
   *
   * Each module answers for its own idea of "on that day" — journals and planners by their own
   * date field, everything else by when it was created (see AbstractCrudService.dayField) — which
   * is what makes one date meaningful across modules that don't share a calendar.
   */
  const showRecordsOn = async (day: string, label: string) => {
    const mods = await enabledModules();
    const perModule = await Promise.all(
      mods.map((m) =>
        listEntities(m.moduleKey, { onDay: day, size: DAY_RECORD_LIMIT })
          .then((page) =>
            page.content.map((r) => ({
              module: m.moduleKey,
              id: r.id,
              title: r.displayTitle || (r.summary as string) || `#${r.id}`,
            })),
          )
          // One module being unhappy shouldn't cost you the other six.
          .catch(() => []),
      ),
    );
    const records = perModule.flat();
    if (!records.length) {
      setToast(`Nothing recorded on ${label}.`);
      return;
    }
    setSearchResults({ title: `${label} · ${records.length} record(s)`, records });
    setResultModule(null);
    setSelectedId(null);
    setMode('view');
  };

  /**
   * Shared by the one-line date field and the full mini-calendar in its popover (SideNav), so
   * picking a day does the same thing either way.
   *
   * With **journals enabled** a day is a journal entry, so clicking one opens it — creating it
   * first if it doesn't exist yet, which is the whole point of a day journal.
   *
   * With **journals disabled** clicking a day creates nothing at all. A module that has been
   * switched off must not be written to from a side door, and a day journal is the only record in
   * the app that gets created by navigating rather than by asking. What a date still means in that
   * case is "what did I put in on that day", so it shows exactly that, from the modules that are
   * on.
   */
  const handleDateSelect = (d: Date) => {
    setSelectedDate(d);
    const day = formatLocalDate(d);
    const label = d.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });

    enabledModules()
      .then((mods) => {
        if (!mods.some((m) => m.moduleKey === 'journals')) {
          return showRecordsOn(day, label);
        }
        return createJournalDay(day).then(async (j) => {
          // A brand-new day journal: export its _record.md to repo 1 right away. Whether the file
          // is opened afterwards is controlled by its own setting (calendar.journalDay.open),
          // independent of the manual-export 'export.open'.
          if (j.created) {
            const open = (await getSetting('calendar.journalDay.open', 'false')) === 'true';
            exportFulltext(j.module, j.id, open).catch(() => {});
          }
          navigate(`/journals?select=${j.id}&onDay=${day}`);
        });
      })
      .catch(() => {});
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
          <MiniCalendar entity={config.key} selectedDate={selectedDate} onSelect={handleDateSelect} />
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
              setScraped(null);
              setSelectedId(null);
              setMode('create');
            }}
            // Resources only: everything else has no URL to build itself from.
            onScraped={
              config.key === 'resources'
                ? (fields, warnings, coverUrl) => {
                    setScraped({ fields, warnings, coverUrl });
                    setSelectedId(null);
                    setMode('create');
                  }
                : undefined
            }
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
        {mode === 'create' && scraped && (
          <Alert
            severity={scraped.warnings.length ? 'warning' : 'success'}
            variant="outlined"
            sx={{ mx: 3, mt: 2 }}
          >
            {scraped.warnings.length
              ? `Filled in from the page — check these: ${scraped.warnings.join(' ')}`
              : 'Filled in from the page. Review the fields before saving.'}
            {/* Said up front because it happens on save, not now, and it isn't visible in any
                field — nothing else on this form would hint that a file is about to arrive. */}
            {scraped.coverUrl && ' The page’s main image will be saved as the cover.'}
          </Alert>
        )}
        {mode === 'create' && (
          <EntityForm
            config={config}
            initial={scraped?.fields}
            onClose={() => {
              setScraped(null);
              setMode('view');
            }}
            onSaved={(saved) => {
              // Read before the state is cleared — the fetch below outlives this handler.
              const coverFrom = scraped?.coverUrl;
              setScraped(null);
              setJustSaved(saved);       // show a card of what was just created
              setSelectedId(saved.id);
              setMode('view');
              // The record now has an id, so the page's main image finally has somewhere to go.
              // Deliberately after the save and not part of it: a picture that won't download is
              // no reason to lose a resource that saved perfectly well, so it only ever reports.
              if (coverFrom) importCover(config.key, saved.id, coverFrom);
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
            <Typography>Select an item, or create a new one.</Typography>
          </Box>
        )}
        </Box>
        <QuickNoteBar onSaved={(msg) => setToast(msg)} />
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
