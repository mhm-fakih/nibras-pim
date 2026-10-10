import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Checkbox,
  Chip,
  IconButton,
  InputAdornment,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Pagination,
  Stack,
  TextField,
  Tooltip,
  Typography,
  alpha,
} from '@mui/material';
import Search from '@mui/icons-material/Search';
import Add from '@mui/icons-material/Add';
import Star from '@mui/icons-material/Star';
import StarBorder from '@mui/icons-material/StarBorder';
import Close from '@mui/icons-material/Close';
import {
  batchEdit,
  coverUrl,
  getLookups,
  listEntities,
  matchContext,
  EntityRecord,
  FieldMatch,
  ListParams,
  Lookup,
  REPORT_KEYS,
  scrapeUrl,
  errorDetail,
} from '../api/client';
import DownloadIcon from '@mui/icons-material/CloudDownload';
import LinkIcon from '@mui/icons-material/AddLink';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { EntityConfig, MERGEABLE_MODULES } from '../config/entities';
import { activeReportLabel } from '../config/reports';
import { statusStyle } from './statusStyle';
import { dirOf } from '../util/rtl';
import { useModules } from '../hooks/useModules';
import { useAuth } from '../hooks/useAuth';
import { useFeature } from '../hooks/useFeature';
import { AuthImage } from './AuthImage';
import { ModuleBar } from './ModuleBar';
import { MergeDialog, MergeDraft } from './MergeDialog';

interface Props {
  config: EntityConfig;
  selectedId: number | null;
  onSelect: (id: number) => void;
  onNew: () => void;
  /**
   * Resources only: fetch a URL server-side and open the New form prefilled from the page.
   * `coverUrl` is the page's main image, still un-fetched — the record has no id yet.
   */
  onScraped?: (
    fields: Record<string, unknown>,
    warnings: string[],
    coverUrl: string,
  ) => void;
  /** Reports the currently-loaded record ids (in order) for keyboard j/k navigation. */
  onItems?: (ids: number[]) => void;
}

const PAGE_SIZE = 25;

/** Page sizes offered in the filter bar. */
const PAGE_SIZES = [10, 25, 50, 100, 200];

const PAGE_SIZE_KEY = 'nibras.listPageSize';

/**
 * Priorities offered by the filter, as pseudo-lookups so they can share [FilterSelect].
 *
 * A fixed 0–5 rather than the distinct values in the data: a list whose options changed as you
 * filtered would be unusable, and "no priority" is already a report preset rather than a value.
 */
const PRIORITY_OPTIONS: Lookup[] = [0, 1, 2, 3, 4, 5].map((p) => ({ id: p, name: `P${p}` }));

/** Records per page, remembered across sessions — it's a preference, not a per-visit choice. */
function storedPageSize(): number {
  const saved = Number(localStorage.getItem(PAGE_SIZE_KEY));
  return PAGE_SIZES.includes(saved) ? saved : PAGE_SIZE;
}

/** yyyy-mm-dd in local time. */
function ymd(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
    d.getDate(),
  ).padStart(2, '0')}`;
}

export function ListPane({ config, selectedId, onSelect, onNew, onScraped, onItems }: Props) {
  const { canWriteContent } = useAuth();
  const qc = useQueryClient();
  const { modules } = useModules();
  const moduleMeta = modules.find((m) => m.moduleKey === config.key);
  const [searchParams, setSearchParams] = useSearchParams();
  const [qInput, setQInput] = useState('');
  const [q, setQ] = useState('');
  const [statusId, setStatusId] = useState<number | ''>('');
  const [typeId, setTypeId] = useState<number | ''>('');
  const [priority, setPriority] = useState<number | ''>('');
  const [pageSize, setPageSize] = useState<number>(storedPageSize);
  const [bookmarked, setBookmarked] = useState(false);
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [command, setCommand] = useState('');
  const [batchMsg, setBatchMsg] = useState<string | null>(null);
  const [dragOverId, setDragOverId] = useState<number | null>(null);
  const [mergeDraft, setMergeDraft] = useState<MergeDraft | null>(null);
  // "New from a URL" is folded behind the link button beside New: it's an occasional way in, and a
  // permanently-open URL box above the search box read as though it were part of the search.
  const [fromUrlOpen, setFromUrlOpen] = useState(false);
  // Called unconditionally — `a && useFeature(…)` would skip the hook whenever `a` is false.
  const fromUrlEnabled = useFeature('resourceFromUrl');
  const showFromUrl = canWriteContent && Boolean(onScraped) && fromUrlEnabled;

  // Report presets come from the URL (set by the SideNav reports panel).
  const reportParams = useMemo(() => {
    const p: ListParams = {};
    REPORT_KEYS.forEach((k) => {
      if (searchParams.get(k) === 'true') (p as Record<string, boolean>)[k] = true;
    });
    return p;
  }, [searchParams]);
  const activeReport = activeReportLabel(searchParams);
  const reportKey = searchParams.toString();
  const onDay = searchParams.get('onDay') || undefined;

  // Merge a single search param without dropping the others (reports, onDay…).
  const setParam = (key: string, value?: string) =>
    setSearchParams((prev) => {
      const p = new URLSearchParams(prev);
      if (value) p.set(key, value);
      else p.delete(key);
      return p;
    });

  useEffect(() => {
    const t = setTimeout(() => setQ(qInput.trim()), 300);
    return () => clearTimeout(t);
  }, [qInput]);

  useEffect(() => {
    setPage(0);
  }, [config.key, q, statusId, typeId, priority, bookmarked, pageSize, reportKey]);

  // Clear selection/feedback when switching entities or report.
  useEffect(() => {
    setSelected(new Set());
    setBatchMsg(null);
  }, [config.key, reportKey]);

  const { data: lookups } = useQuery({
    queryKey: ['lookups', config.key],
    queryFn: () => getLookups(config.key),
  });

  const { data, isFetching, isError, error, refetch } = useQuery({
    queryKey: [
      'list',
      config.key,
      { q, onDay, statusId, typeId, priority, bookmarked, page, pageSize, reportKey },
    ],
    queryFn: () =>
      listEntities(config.key, {
        q: q || undefined,
        onDay,
        statusId: statusId === '' ? undefined : statusId,
        typeId: typeId === '' ? undefined : typeId,
        priority: priority === '' ? undefined : priority,
        bookmarked: bookmarked || undefined,
        page,
        size: pageSize,
        ...reportParams,
      }),
    placeholderData: keepPreviousData,
  });

  const items = data?.content ?? [];

  // Where the query appears inside each listed record. Runs only while searching, and only for the
  // ids on screen — a page of context, not a second search.
  const ids = items.map((it) => it.id);
  const { data: context } = useQuery({
    queryKey: ['match-context', config.key, q, ids],
    queryFn: () => matchContext(config.key, q, ids),
    enabled: q.length > 0 && ids.length > 0,
    placeholderData: keepPreviousData,
  });

  // Report loaded ids to the shell for keyboard (j/k) navigation.
  useEffect(() => {
    onItems?.(items.map((it) => it.id));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data]);

  const batch = useMutation({
    mutationFn: () => batchEdit(config.key, [...selected], command.trim()),
    onSuccess: (res) => {
      setBatchMsg(`Updated ${res.updated} record(s): ${res.fields.join(', ')}`);
      setSelected(new Set());
      setCommand('');
      qc.invalidateQueries({ queryKey: ['list', config.key] });
    },
    onError: (e: unknown) => setBatchMsg(errorDetail(e, 'Batch edit failed.')),
  });

  const toggle = (id: number) =>
    setSelected((s) => {
      const n = new Set(s);
      n.has(id) ? n.delete(id) : n.add(id);
      return n;
    });
  const allOnPage = items.length > 0 && items.every((it) => selected.has(it.id));
  const toggleAll = () =>
    setSelected((s) => {
      const n = new Set(s);
      if (allOnPage) items.forEach((it) => n.delete(it.id));
      else items.forEach((it) => n.add(it.id));
      return n;
    });

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Box sx={{ p: 1.5, borderBottom: 1, borderColor: 'divider' }}>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
          <Checkbox
            size="small"
            sx={{ p: 0.5 }}
            checked={allOnPage}
            indeterminate={!allOnPage && items.some((it) => selected.has(it.id))}
            onChange={toggleAll}
          />
          <Typography variant="subtitle1" sx={{ fontWeight: 700, flex: 1 }}>
            {config.label}
          </Typography>
          {canWriteContent && (
            <Button size="small" variant="contained" startIcon={<Add />} onClick={onNew}>
              New
            </Button>
          )}
          {showFromUrl && (
            <Tooltip title={`New ${config.label.toLowerCase().replace(/s$/, '')} from a URL`}>
              <IconButton
                size="small"
                color={fromUrlOpen ? 'primary' : 'default'}
                onClick={() => setFromUrlOpen((o) => !o)}
                aria-label="New from a URL"
                aria-expanded={fromUrlOpen}
              >
                <LinkIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
        </Stack>
        {showFromUrl && fromUrlOpen && (
          <ScrapeFromUrl
            // The panel STAYS open after a fetch. Closing it was the obvious thing and it was
            // wrong: building resources from links is done in runs, and having the control fold
            // itself away after each one meant re-opening it for every URL. It closes when asked
            // to — the X, or Escape — and not otherwise.
            onScraped={(fields, warnings, cover) => onScraped!(fields, warnings, cover)}
            onCancel={() => setFromUrlOpen(false)}
          />
        )}
        <TextField
          fullWidth
          size="small"
          placeholder={`Search ${config.label.toLowerCase()}…`}
          value={qInput}
          onChange={(e) => setQInput(e.target.value)}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <Search fontSize="small" />
              </InputAdornment>
            ),
          }}
        />
        <Stack direction="row" spacing={1} sx={{ mt: 1 }} flexWrap="wrap" useFlexGap>
          {config.hasStatus && (
            <FilterSelect label="Status" value={statusId} options={lookups?.statuses} onChange={setStatusId} />
          )}
          {config.hasType && (
            <FilterSelect label="Type" value={typeId} options={lookups?.types} onChange={setTypeId} />
          )}
          <FilterSelect
            label="Priority"
            value={priority}
            options={PRIORITY_OPTIONS}
            onChange={setPriority}
          />
          <Tooltip title="Bookmarked only">
            <IconButton size="small" onClick={() => setBookmarked((b) => !b)} color="primary">
              {bookmarked ? <Star fontSize="small" /> : <StarBorder fontSize="small" />}
            </IconButton>
          </Tooltip>
          <DatePicker
            label="On day"
            value={onDay ? new Date(`${onDay}T00:00:00`) : null}
            onChange={(d) => setParam('onDay', d ? ymd(d as Date) : undefined)}
            slotProps={{
              textField: { size: 'small', sx: { width: 160 } },
              field: { clearable: true },
            }}
          />
          {/* Sits with the filters rather than beside the pager: how much you want to see at once
              is part of how you're looking, and it's remembered for next time. */}
          <TextField
            select
            size="small"
            label="Per page"
            value={pageSize}
            onChange={(e) => {
              const next = Number(e.target.value);
              setPageSize(next);
              localStorage.setItem(PAGE_SIZE_KEY, String(next));
            }}
            sx={{ width: 110 }}
          >
            {PAGE_SIZES.map((n) => (
              <MenuItem key={n} value={n}>
                {n}
              </MenuItem>
            ))}
          </TextField>
        </Stack>
        <Stack direction="row" spacing={1} sx={{ mt: 1 }} flexWrap="wrap" useFlexGap>
          {onDay && (
            <Chip
              size="small"
              color="secondary"
              label={`📅 ${onDay}`}
              onDelete={() => setParam('onDay')}
              deleteIcon={<Close />}
            />
          )}
          {activeReport && (
            <Chip
              size="small"
              color="info"
              label={`Report: ${activeReport}`}
              onDelete={() => setSearchParams({})}
              deleteIcon={<Close />}
            />
          )}
        </Stack>
      </Box>

      {/* Batch command bar (advanced) */}
      {selected.size > 0 && (
        <Box sx={{ p: 1, bgcolor: '#eef6ff', borderBottom: 1, borderColor: 'divider' }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <Typography variant="caption" sx={{ fontWeight: 700, whiteSpace: 'nowrap' }}>
              {selected.size} selected
            </Typography>
            <TextField
              size="small"
              fullWidth
              placeholder="cmd: p3 #art ?done @Z * *- ! !-  =code"
              value={command}
              onChange={(e) => setCommand(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && command.trim() && batch.mutate()}
              inputProps={{ style: { fontFamily: 'monospace', fontSize: 13 } }}
            />
            <Button
              size="small"
              variant="contained"
              disabled={!command.trim() || batch.isPending}
              onClick={() => batch.mutate()}
            >
              Apply
            </Button>
            <Button size="small" onClick={() => setSelected(new Set())}>
              Clear
            </Button>
          </Stack>
        </Box>
      )}
      {batchMsg && (
        <Alert severity="info" onClose={() => setBatchMsg(null)} sx={{ borderRadius: 0, py: 0 }}>
          {batchMsg}
        </Alert>
      )}

      <Box sx={{ flex: 1, overflow: 'auto' }}>
        <List dense disablePadding>
          {items.map((it: EntityRecord) => {
            // Right-align (rtl) Arabic/Hebrew titles based on the text itself, not the language field.
            const rtl = dirOf(it.displayTitle) === 'rtl';
            return (
            <ListItemButton
              key={it.id}
              divider
              selected={it.id === selectedId}
              onClick={() => onSelect(it.id)}
              draggable={canWriteContent && MERGEABLE_MODULES.has(config.key)}
              onDragStart={(e) => {
                e.dataTransfer.setData('text/plain', String(it.id));
                e.dataTransfer.effectAllowed = 'move';
              }}
              onDragOver={(e) => {
                if (canWriteContent && MERGEABLE_MODULES.has(config.key)) {
                  e.preventDefault();
                  setDragOverId(it.id);
                }
              }}
              onDragLeave={() => setDragOverId((cur) => (cur === it.id ? null : cur))}
              onDrop={(e) => {
                e.preventDefault();
                setDragOverId(null);
                const draggedId = Number(e.dataTransfer.getData('text/plain'));
                if (draggedId && draggedId !== it.id) {
                  const dragged = items.find((x) => x.id === draggedId);
                  setMergeDraft({
                    module: config.key,
                    moduleLabel: config.label,
                    primaryId: it.id,
                    primaryTitle: it.displayTitle,
                    secondaryId: draggedId,
                    secondaryTitle: dragged?.displayTitle ?? '',
                  });
                }
              }}
              sx={dragOverId === it.id ? { bgcolor: 'action.hover', outline: 2, outlineColor: 'primary.main', outlineOffset: -2 } : undefined}
            >
              <ModuleBar code={moduleMeta?.code} color={moduleMeta?.color} title={config.label} />
              <Checkbox
                size="small"
                sx={{ p: 0.5, mr: 0.5 }}
                checked={selected.has(it.id)}
                onClick={(e) => e.stopPropagation()}
                onChange={() => toggle(it.id)}
              />
              {it.hasCover && <Cover module={config.key} id={it.id} />}
              <ListItemText
                primaryTypographyProps={{
                  dir: rtl ? 'rtl' : 'ltr',
                  align: rtl ? 'right' : 'left',
                  noWrap: true,
                  fontWeight: it.id === selectedId ? 700 : 500,
                }}
                primary={
                  <>
                    {it.bookmarked ? '★ ' : ''}
                    {it.displayTitle || '(untitled)'}
                  </>
                }
                secondaryTypographyProps={{ component: 'span' }}
                secondary={
                  <>
                    <Stack component="span" direction="row" spacing={1} alignItems="center" sx={{ mt: 0.5 }}>
                    <Typography component="span" variant="caption" color="text.secondary">
                      {formatDate(it.dateCreated)}
                    </Typography>
                    {/* The record's own short code. Contacts carry one and have no status or type
                        chips to fill this line, so without it the row is a name and a date. */}
                    {it.code && (
                      <Chip
                        component="span"
                        size="small"
                        label={it.code}
                        variant="outlined"
                        sx={{ height: 18, fontSize: 11 }}
                      />
                    )}
                    {/* Contacts: how much is still open with this person. Shown even at zero —
                        "nothing outstanding" is the answer you came for as often as a number is. */}
                    {typeof it.openTasks === 'number' && (
                      <Chip
                        component="span"
                        size="small"
                        label={`${it.openTasks} open`}
                        color={it.openTasks > 0 ? 'primary' : 'default'}
                        variant={it.openTasks > 0 ? 'filled' : 'outlined'}
                        sx={{ height: 18, fontSize: 11 }}
                      />
                    )}
                    {/* The resource a note is about. Shown only when set, and outlined rather than
                        filled: it is context for the row, not a status to scan for. */}
                    {it.book && (
                      <Chip
                        component="span"
                        size="small"
                        /* Some legacy books have neither title nor author, so feedTitle() is null.
                           The id is a worse label than a name and a far better one than blank. */
                        label={it.book.name || `#${it.book.id}`}
                        variant="outlined"
                        sx={{ height: 18, fontSize: 11, maxWidth: 220 }}
                      />
                    )}
                    {it.status && (
                      <Chip
                        component="span"
                        size="small"
                        label={it.status.name}
                        sx={{ height: 18, fontSize: 11, ...statusStyle(it.status) }}
                      />
                    )}
                    {it.type && (
                      <Chip
                        component="span"
                        size="small"
                        label={it.type.name}
                        sx={{ height: 18, fontSize: 11, ...statusStyle(it.type) }}
                      />
                    )}
                    {it.tags?.slice(0, 3).map((t) => (
                      <Chip
                        component="span"
                        key={t.id}
                        size="small"
                        variant="outlined"
                        label={`#${t.name}`}
                        sx={{ height: 18, fontSize: 10 }}
                      />
                    ))}
                    </Stack>
                    <MatchContext matches={context?.[it.id]} term={q} />
                  </>
                }
              />
            </ListItemButton>
            );
          })}
          {items.length === 0 && isError && (
            <Box sx={{ p: 3 }}>
              <Alert
                severity="error"
                action={
                  <Button color="inherit" size="small" onClick={() => refetch()}>
                    Retry
                  </Button>
                }
              >
                {errorDetail(error, `Couldn't load ${config.label.toLowerCase()}.`)}
              </Alert>
            </Box>
          )}
          {items.length === 0 && !isError && (
            <Typography sx={{ p: 3, color: 'text.secondary' }}>
              {isFetching ? 'Loading…' : 'No records.'}
            </Typography>
          )}
        </List>
      </Box>

      <Box
        sx={{
          borderTop: 1,
          borderColor: 'divider',
          p: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <Typography variant="caption" color="text.secondary">
          {data?.totalElements ?? 0} items
        </Typography>
        <Pagination
          size="small"
          count={data?.totalPages ?? 1}
          page={page + 1}
          onChange={(_, p) => setPage(p - 1)}
          siblingCount={0}
        />
      </Box>

      <MergeDialog
        draft={mergeDraft}
        onSwap={() =>
          setMergeDraft((d) =>
            d
              ? {
                  ...d,
                  primaryId: d.secondaryId,
                  primaryTitle: d.secondaryTitle,
                  secondaryId: d.primaryId,
                  secondaryTitle: d.primaryTitle,
                }
              : d,
          )
        }
        onCancel={() => setMergeDraft(null)}
        onMerged={(survivorId) => {
          setMergeDraft(null);
          onSelect(survivorId);
        }}
      />
    </Box>
  );
}

/** Cover thumbnail that self-hides when the record has no cover image. */
function Cover({ module, id }: { module: string; id: number }) {
  return (
    <AuthImage
      url={coverUrl(module, id)}
      sx={{ width: 32, height: 32, objectFit: 'cover', borderRadius: 0.5, mr: 1, flexShrink: 0 }}
    />
  );
}

/** Human name for a record field, for the "where it matched" label. */
const FIELD_LABEL: Record<string, string> = {
  summary: 'Summary',
  name: 'Name',
  title: 'Title',
  author: 'Author',
  description: 'Description',
  shortDescription: 'Short description',
  fullText: 'Full text',
  highlights: 'Highlights',
  notes: 'Notes',
};

/**
 * Where the search term was found, under the record it was found in.
 *
 * The title alone answers "what matched" but not "why", and for a resource the answer is usually
 * buried in a field the list never shows — a scanned book's full text, a note from months ago. The
 * term itself is marked in each excerpt so the eye lands on it without reading the sentence.
 */
function MatchContext({ matches, term }: { matches?: FieldMatch[]; term: string }) {
  if (!matches || matches.length === 0) return null;
  return (
    <Box component="span" sx={{ display: 'block', mt: 0.25 }}>
      {matches.slice(0, 3).map((m) => (
        <Typography
          component="span"
          key={m.field}
          variant="caption"
          color="text.secondary"
          sx={{ display: 'block', whiteSpace: 'normal', lineHeight: 1.35 }}
        >
          <Box component="span" sx={{ fontWeight: 700, mr: 0.5 }}>
            {FIELD_LABEL[m.field] ?? m.field}:
          </Box>
          <MarkedText text={m.snippet} term={term} />
        </Typography>
      ))}
    </Box>
  );
}

/** The snippet with every occurrence of `term` emboldened. Case-insensitive, literal (not a regex). */
function MarkedText({ text, term }: { text: string; term: string }) {
  const needle = term.trim().toLowerCase();
  if (!needle) return <>{text}</>;
  const parts: React.ReactNode[] = [];
  let from = 0;
  for (;;) {
    const at = text.toLowerCase().indexOf(needle, from);
    if (at < 0) break;
    if (at > from) parts.push(text.slice(from, at));
    parts.push(
      <Box component="span" key={at} sx={{ fontWeight: 700, color: 'text.primary' }}>
        {text.slice(at, at + needle.length)}
      </Box>,
    );
    from = at + needle.length;
  }
  parts.push(text.slice(from));
  return <>{parts}</>;
}

function FilterSelect({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: number | '';
  options?: Lookup[];
  onChange: (v: number | '') => void;
}) {
  return (
    <TextField
      select
      size="small"
      label={label}
      value={value}
      onChange={(e) => onChange(e.target.value === '' ? '' : Number(e.target.value))}
      sx={{ minWidth: 130 }}
    >
      <MenuItem value="">
        <em>Any</em>
      </MenuItem>
      {(options ?? []).map((o) => (
        <MenuItem key={o.id} value={o.id}>
          {o.name}
        </MenuItem>
      ))}
    </TextField>
  );
}

function formatDate(iso: string): string {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString();
}

/**
 * URL → prefilled New-resource form. Revealed by the link button beside New, not always on screen.
 *
 * The fetch happens on the SERVER: a browser cannot read an arbitrary cross-origin page, so this
 * could not be done client-side even in principle. What comes back is a best-effort guess at each
 * field — it opens the ordinary create form for review rather than saving anything, because
 * extraction from someone else's markup is never reliable enough to write straight to a record.
 */
function ScrapeFromUrl({
  onScraped,
  onCancel,
}: {
  onScraped: (fields: Record<string, unknown>, warnings: string[], coverUrl: string) => void;
  onCancel: () => void;
}) {
  const [url, setUrl] = useState('');
  const [error, setError] = useState<string | null>(null);

  const scrape = useMutation({
    mutationFn: () => scrapeUrl(url.trim()),
    onSuccess: (s) => {
      setError(null);
      setUrl('');
      onScraped(
        {
          url: s.url,
          title: s.title,
          author: s.author,
          publisher: s.publisher,
          shortDescription: s.shortDescription,
          resourceYear: s.resourceYear,
          fullText: s.fullText,
        },
        s.warnings ?? [],
        // Kept out of the field set above: it isn't a Resource field, it's a file to fetch once
        // the record has an id to hang it on.
        s.imageUrl ?? '',
      );
    },
    // The backend's rejections are all written for a person — a bad URL, a private address, a
    // dead page — so show its words. And when there IS no answer, say THAT rather than blaming the
    // URL: a backend that is down used to be reported as "Could not fetch that page."
    onError: (e: unknown) => setError(errorDetail(e, 'Could not fetch that page.')),
  });

  const go = () => {
    if (url.trim() && !scrape.isPending) scrape.mutate();
  };

  return (
    // Its own tinted, bordered panel. Unstyled it sat directly above the search field as another
    // bare row of the same shape, and the two were told apart only by their placeholder text —
    // which is a poor way to know which box you are typing into.
    <Stack
      direction="row"
      spacing={1}
      alignItems="flex-start"
      sx={(theme) => ({
        mb: 1,
        p: 1,
        borderRadius: 1,
        border: 1,
        borderColor: alpha(theme.palette.primary.main, 0.35),
        bgcolor: alpha(theme.palette.primary.main, 0.06),
      })}
    >
      <TextField
        size="small"
        fullWidth
        autoFocus
        placeholder="Paste a URL to build a resource from it…"
        value={url}
        onChange={(e) => setUrl(e.target.value)}
        // Escape backs out of a control that was opened on purpose, so it should close it.
        onKeyDown={(e) => {
          if (e.key === 'Enter') go();
          if (e.key === 'Escape') onCancel();
        }}
        error={Boolean(error)}
        helperText={error ?? undefined}
        disabled={scrape.isPending}
      />
      <Button
        size="small"
        variant="outlined"
        onClick={go}
        disabled={!url.trim() || scrape.isPending}
        startIcon={scrape.isPending ? <CircularProgress size={14} /> : <DownloadIcon />}
        sx={{ mt: 0.25, flexShrink: 0 }}
      >
        {scrape.isPending ? 'Fetching…' : 'Fetch'}
      </Button>
      <Tooltip title="Cancel">
        <IconButton size="small" onClick={onCancel} sx={{ mt: 0.25 }} aria-label="Cancel">
          <Close fontSize="small" />
        </IconButton>
      </Tooltip>
    </Stack>
  );
}
