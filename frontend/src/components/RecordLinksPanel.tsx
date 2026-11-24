import { useEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Chip,
  CircularProgress,
  FormControlLabel,
  IconButton,
  List,
  ListItemButton,
  ListItemText,
  Stack,
  Switch,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import LinkIcon from '@mui/icons-material/Link';
import LinkOff from '@mui/icons-material/LinkOff';
import AddLink from '@mui/icons-material/AddLink';
import {
  SearchHit,
  createEntity,
  errorDetail,
  getLinkableModules,
  getRecordLinks,
  linkNote,
  linkNotes,
  searchAll,
  unlinkNote,
} from '../api/client';
import { ENTITY_BY_KEY } from '../config/entities';
import { useModules } from '../hooks/useModules';
import { useAuth } from '../hooks/useAuth';
import { linkProps } from '../util/links';
import { dirOf } from '../util/rtl';

/** Fired by the `l` shortcut: the open record's links panel opens its picker. */
export const LINK_EVENT = 'nibras:link-record';

interface Option {
  module: string;
  id: number;
  title: string;
}

/**
 * A record to link to, found by its words, its id, or a module letter and id ("W802", "r 25489",
 * the letters shown on the module bars). {@code only} keeps to one module (notes, say).
 */
function RecordPicker({
  only,
  exclude,
  label,
  onPick,
  autoFocus,
}: {
  only?: string;
  exclude?: { module: string; id: number };
  label: string;
  onPick: (o: Option) => void;
  autoFocus?: boolean;
}) {
  const { modules } = useModules();
  const linkable = useQuery({ queryKey: ['links', 'modules'], queryFn: getLinkableModules, staleTime: Infinity });
  const [input, setInput] = useState('');
  const [q, setQ] = useState('');
  useEffect(() => {
    const t = setTimeout(() => setQ(input.trim()), 250);
    return () => clearTimeout(t);
  }, [input]);

  // "W802" / "w 802" / "W#802": the module whose letter it is, and that id.
  const byCode = useMemo(() => {
    const m = /^([A-Za-z])\s*#?\s*(\d+)$/.exec(q);
    if (!m) return null;
    const mod = modules.find((x) => x.code?.toUpperCase() === m[1].toUpperCase());
    return mod ? { module: mod.moduleKey, id: Number(m[2]) } : null;
  }, [q, modules]);

  const found = useQuery({
    queryKey: ['link-picker', byCode ? String(byCode.id) : q],
    queryFn: () => searchAll(byCode ? String(byCode.id) : q, 30),
    enabled: q.length >= 2 || /^\d+$/.test(q) || !!byCode,
  });

  const options: Option[] = (found.data ?? [])
    .filter((h: SearchHit) => !byCode || (h.module === byCode.module && h.id === byCode.id))
    .filter((h) => (only ? h.module === only : linkable.data?.includes(h.module) ?? true))
    .filter((h) => !(exclude && h.module === exclude.module && h.id === exclude.id))
    .map((h) => ({ module: h.module, id: h.id, title: h.title }));

  const labelOf = (o: Option) => `${ENTITY_BY_KEY[o.module]?.label ?? o.module} #${o.id} — ${o.title || '(untitled)'}`;

  return (
    <Autocomplete<Option>
      size="small"
      options={options}
      filterOptions={(x) => x}
      loading={found.isFetching}
      getOptionLabel={labelOf}
      isOptionEqualToValue={(a, b) => a.module === b.module && a.id === b.id}
      inputValue={input}
      onInputChange={(_, v, reason) => reason !== 'reset' && setInput(v)}
      value={null}
      onChange={(_, o) => {
        if (o) {
          onPick(o);
          setInput('');
        }
      }}
      noOptionsText={q ? 'Nothing found' : 'Type words, an id, or a letter and id (W802)'}
      renderOption={(props, o) => (
        <li {...props} key={`${o.module}-${o.id}`}>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0 }}>
            <Chip size="small" variant="outlined" label={`${ENTITY_BY_KEY[o.module]?.label ?? o.module} #${o.id}`} />
            <Typography noWrap dir="auto" sx={{ fontSize: 14 }}>
              {o.title || '(untitled)'}
            </Typography>
          </Stack>
        </li>
      )}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          autoFocus={autoFocus}
          InputProps={{
            ...params.InputProps,
            endAdornment: (
              <>
                {found.isFetching ? <CircularProgress size={16} /> : null}
                {params.InputProps.endAdornment}
              </>
            ),
          }}
        />
      )}
    />
  );
}

/**
 * Notes linked to records, both ways: on a note, the record it is linked to (change or unlink
 * it); on any record, the notes linked to it in their order (a surah's verses), with a line to
 * write a new one there and a picker to bring existing notes in. `l` opens the picker.
 */
export function RecordLinksPanel({ module, id }: { module: string; id: number }) {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { canWriteContent } = useAuth();
  const linkable = useQuery({ queryKey: ['links', 'modules'], queryFn: getLinkableModules, staleTime: Infinity });
  const canLink = linkable.data?.includes(module) ?? false;
  const { data } = useQuery({
    queryKey: ['links', module, id],
    queryFn: () => getRecordLinks(module, id),
    enabled: canLink,
  });
  const [picking, setPicking] = useState(false);
  const [newNote, setNewNote] = useState('');
  const [numbered, setNumbered] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const panel = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const open = () => {
      setPicking(true);
      panel.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    };
    window.addEventListener(LINK_EVENT, open);
    return () => window.removeEventListener(LINK_EVENT, open);
  }, []);

  const refresh = (other?: { module: string; id: number }) => {
    qc.invalidateQueries({ queryKey: ['links', module, id] });
    if (other) qc.invalidateQueries({ queryKey: ['links', other.module, other.id] });
    qc.invalidateQueries({ queryKey: ['links', 'notes'] });
  };
  const fail = (e: unknown) => setError(errorDetail(e, 'Could not change the link.'));

  const notes = data?.notes ?? [];
  const nextOrder = notes.reduce((m, n) => Math.max(m, n.order ?? 0), 0) + 1;
  const ordered = notes.some((n) => n.order != null);
  const setTarget = useMutation({
    mutationFn: (o: Option) => linkNote(id, o.module, o.id),
    onSuccess: (_, o) => {
      setPicking(false);
      refresh(o);
    },
    onError: fail,
  });
  const clearTarget = useMutation({
    mutationFn: () => unlinkNote(id),
    onSuccess: () => refresh(data?.target ?? undefined),
    onError: fail,
  });
  const bringIn = useMutation({
    mutationFn: (o: Option) => linkNotes(module, id, [o.id], numbered || ordered ? nextOrder : undefined),
    onSuccess: (_, o) => refresh({ module: 'notes', id: o.id }),
    onError: fail,
  });
  const release = useMutation({
    mutationFn: (noteId: number) => unlinkNote(noteId),
    onSuccess: (_, noteId) => refresh({ module: 'notes', id: noteId }),
    onError: fail,
  });
  const addNote = useMutation({
    mutationFn: async (summary: string) => {
      const body: Record<string, unknown> = { summary };
      if (numbered || ordered) body.orderInWriting = nextOrder;
      const rec = await createEntity('notes', body);
      await linkNote(Number(rec.id), module, id);
    },
    onSuccess: () => {
      setNewNote('');
      refresh();
      qc.invalidateQueries({ queryKey: ['list', 'notes'] });
    },
    onError: (e) => setError(errorDetail(e, 'Could not add the note.')),
  });

  if (!canLink || !data) return null;
  const target = data.target;
  const isNote = module === 'notes';
  if (!canWriteContent && !target && data.count === 0) return null;

  const go = (to: string) => linkProps(to, () => navigate(to));

  return (
    <Box ref={panel} sx={{ mt: 2 }}>
      <Typography variant="overline" color="text.secondary" sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
        <LinkIcon fontSize="inherit" /> Links
      </Typography>
      {error && (
        <Alert severity="error" onClose={() => setError(null)} sx={{ mb: 1 }}>
          {error}
        </Alert>
      )}

      {isNote && (
        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap sx={{ mb: 1 }}>
          <Typography variant="body2" color="text.secondary">
            Linked to
          </Typography>
          {target ? (
            <>
              <Chip
                size="small"
                color={target.exists ? 'primary' : 'default'}
                variant="outlined"
                label={`${ENTITY_BY_KEY[target.module]?.label ?? target.module} #${target.id}${target.title ? ` — ${target.title}` : ''}${target.exists ? '' : ' (gone)'}`}
                {...(target.exists ? go(`/${target.module}?select=${target.id}`) : {})}
                clickable={target.exists}
                sx={{ maxWidth: '100%', '& .MuiChip-label': { overflow: 'hidden', textOverflow: 'ellipsis' } }}
                dir="auto"
              />
              {canWriteContent && (
                <Tooltip title="Unlink">
                  <IconButton size="small" disabled={clearTarget.isPending} onClick={() => clearTarget.mutate()}>
                    <LinkOff fontSize="small" />
                  </IconButton>
                </Tooltip>
              )}
            </>
          ) : (
            <Typography variant="body2" color="text.disabled">
              nothing
            </Typography>
          )}
          {canWriteContent && !picking && (
            <Button size="small" startIcon={<AddLink />} onClick={() => setPicking(true)}>
              {target ? 'Change' : 'Link to a record'} (l)
            </Button>
          )}
        </Stack>
      )}
      {isNote && picking && (
        <Box sx={{ mb: 1.5 }}>
          <RecordPicker
            autoFocus
            label="Link this note to…"
            exclude={{ module: 'notes', id }}
            onPick={(o) => setTarget.mutate(o)}
          />
          <Button size="small" color="inherit" onClick={() => setPicking(false)} sx={{ mt: 0.5 }}>
            Cancel
          </Button>
        </Box>
      )}

      {(data.count > 0 || canWriteContent) && (
        <Box>
          <Typography variant="caption" sx={{ fontWeight: 700, color: 'text.secondary' }}>
            Notes linked here ({data.count})
          </Typography>
          {notes.length > 0 && (
            <List dense disablePadding sx={{ maxHeight: 360, overflow: 'auto' }}>
              {notes.map((n) => {
                const rtl = dirOf(n.title) === 'rtl';
                return (
                  <ListItemButton
                    key={n.id}
                    sx={{ py: 0.25, borderRadius: 1, '&:hover .unlink': { visibility: 'visible' } }}
                    {...go(`/notes?select=${n.id}`)}
                  >
                    {n.order != null && (
                      <Typography variant="caption" color="text.secondary" sx={{ minWidth: 28, fontVariantNumeric: 'tabular-nums' }}>
                        {n.order}
                      </Typography>
                    )}
                    <ListItemText
                      primary={n.title || '(untitled)'}
                      primaryTypographyProps={{ dir: rtl ? 'rtl' : 'ltr', align: rtl ? 'right' : 'left', noWrap: true, fontSize: 13 }}
                    />
                    {canWriteContent && (
                      <Tooltip title="Unlink this note">
                        <IconButton
                          className="unlink"
                          size="small"
                          sx={{ visibility: 'hidden', ml: 0.5 }}
                          onClick={(e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            release.mutate(n.id);
                          }}
                        >
                          <LinkOff fontSize="inherit" />
                        </IconButton>
                      </Tooltip>
                    )}
                  </ListItemButton>
                );
              })}
            </List>
          )}
          {data.count > notes.length && (
            <Typography variant="caption" color="text.secondary" sx={{ pl: 1 }}>
              …and {data.count - notes.length} more
            </Typography>
          )}
          {canWriteContent && (
            <Stack spacing={1} sx={{ mt: 1 }}>
              <TextField
                size="small"
                placeholder="A new note linked here — Enter to add"
                value={newNote}
                onChange={(e) => setNewNote(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && newNote.trim() && !addNote.isPending) {
                    e.preventDefault();
                    addNote.mutate(newNote.trim());
                  }
                }}
                inputProps={{ dir: 'auto' }}
                helperText={ordered || numbered ? `It comes in as number ${nextOrder}` : undefined}
              />
              {(!isNote || !picking) && (
                <Stack direction="row" spacing={1} alignItems="center">
                  <Box sx={{ flex: 1 }}>
                    <RecordPicker
                      key={picking ? 'picking' : 'idle'}
                      only="notes"
                      exclude={{ module, id }}
                      label="Link an existing note here…"
                      autoFocus={!isNote && picking}
                      onPick={(o) => bringIn.mutate(o)}
                    />
                  </Box>
                  {!ordered && (
                    <FormControlLabel
                      control={<Switch size="small" checked={numbered} onChange={(e) => setNumbered(e.target.checked)} />}
                      label={<Typography variant="caption">Numbered</Typography>}
                      sx={{ mr: 0 }}
                    />
                  )}
                </Stack>
              )}
            </Stack>
          )}
        </Box>
      )}
    </Box>
  );
}
