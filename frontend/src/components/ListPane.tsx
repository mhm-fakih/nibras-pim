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
  EntityRecord,
  ListParams,
  Lookup,
  REPORT_KEYS,
  scrapeUrl,
} from '../api/client';
import DownloadIcon from '@mui/icons-material/CloudDownload';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { EntityConfig, MERGEABLE_MODULES } from '../config/entities';
import { activeReportLabel } from '../config/reports';
import { statusStyle } from './statusStyle';
import { dirOf } from '../util/rtl';
import { useModules } from '../hooks/useModules';
import { useAuth } from '../hooks/useAuth';
import { AuthImage } from './AuthImage';
import { ModuleBar } from './ModuleBar';
import { MergeDialog, MergeDraft } from './MergeDialog';

interface Props {
  config: EntityConfig;
  selectedId: number | null;
  onSelect: (id: number) => void;
  onNew: () => void;
  /** Resources only: fetch a URL server-side and open the New form prefilled from the page. */
  onScraped?: (fields: Record<string, unknown>, warnings: string[]) => void;
  /** Reports the currently-loaded record ids (in order) for keyboard j/k navigation. */
  onItems?: (ids: number[]) => void;
}

const PAGE_SIZE = 25;

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
  const [bookmarked, setBookmarked] = useState(false);
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [command, setCommand] = useState('');
  const [batchMsg, setBatchMsg] = useState<string | null>(null);
  const [dragOverId, setDragOverId] = useState<number | null>(null);
  const [mergeDraft, setMergeDraft] = useState<MergeDraft | null>(null);

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
  }, [config.key, q, statusId, typeId, bookmarked, reportKey]);

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
    queryKey: ['list', config.key, { q, onDay, statusId, typeId, bookmarked, page, reportKey }],
    queryFn: () =>
      listEntities(config.key, {
        q: q || undefined,
        onDay,
        statusId: statusId === '' ? undefined : statusId,
        typeId: typeId === '' ? undefined : typeId,
        bookmarked: bookmarked || undefined,
        page,
        size: PAGE_SIZE,
        ...reportParams,
      }),
    placeholderData: keepPreviousData,
  });

  const items = data?.content ?? [];

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
    onError: (e: unknown) =>
      setBatchMsg(
        (e as { response?: { data?: { detail?: string } } })?.response?.data?.detail ??
          'Batch edit failed.',
      ),
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
        </Stack>
        {canWriteContent && onScraped && <ScrapeFromUrl onScraped={onScraped} />}
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
                secondary={
                  <Stack component="span" direction="row" spacing={1} alignItems="center" sx={{ mt: 0.5 }}>
                    <Typography component="span" variant="caption" color="text.secondary">
                      {formatDate(it.dateCreated)}
                    </Typography>
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
                Couldn't load {config.label.toLowerCase()}.{' '}
                {(error as { response?: { data?: { detail?: string } } })?.response?.data?.detail ??
                  (error as Error | undefined)?.message ??
                  'Please try again.'}
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
 * URL → prefilled New-resource form.
 *
 * The fetch happens on the SERVER: a browser cannot read an arbitrary cross-origin page, so this
 * could not be done client-side even in principle. What comes back is a best-effort guess at each
 * field — it opens the ordinary create form for review rather than saving anything, because
 * extraction from someone else's markup is never reliable enough to write straight to a record.
 */
function ScrapeFromUrl({
  onScraped,
}: {
  onScraped: (fields: Record<string, unknown>, warnings: string[]) => void;
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
      );
    },
    // The backend's rejections are all written for a person — a bad URL, a private address, a dead
    // page — so show the message rather than a generic failure.
    onError: (e: unknown) => {
      const detail = (e as { response?: { data?: { detail?: string; message?: string } } })?.response?.data;
      setError(detail?.detail ?? detail?.message ?? 'Could not fetch that page.');
    },
  });

  const go = () => {
    if (url.trim() && !scrape.isPending) scrape.mutate();
  };

  return (
    <Stack direction="row" spacing={1} alignItems="flex-start" sx={{ px: 1.5, pb: 1 }}>
      <TextField
        size="small"
        fullWidth
        placeholder="Paste a URL to build a resource from it…"
        value={url}
        onChange={(e) => setUrl(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && go()}
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
    </Stack>
  );
}
