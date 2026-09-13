import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Autocomplete,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Divider,
  IconButton,
  Link,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import Edit from '@mui/icons-material/Edit';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import FolderOpen from '@mui/icons-material/FolderOpen';
import AccountTreeOutlined from '@mui/icons-material/AccountTreeOutlined';
import Star from '@mui/icons-material/Star';
import StarBorder from '@mui/icons-material/StarBorder';
import UrgentIcon from '@mui/icons-material/Error';
import UrgentOutlineIcon from '@mui/icons-material/ErrorOutline';
import CheckCircle from '@mui/icons-material/CheckCircle';
import CheckCircleOutline from '@mui/icons-material/CheckCircleOutline';
import OpenInFull from '@mui/icons-material/OpenInFull';
import {
  BASE,
  addTagToRecord,
  coverUrl,
  createEntity,
  createSubtask,
  deleteEntity,
  getEntity,
  getSetting,
  listEntities,
  listRepoFiles,
  listTags,
  openRecordFolder,
  removeTagFromRecord,
  updateEntity,
  EntityRecord,
  TagRef,
  errorDetail,
} from '../api/client';
import { EntityConfig, ENTITY_CLASS_BY_KEY } from '../config/entities';
import { lookupStyle } from './statusStyle';
import { HistoryPanel } from './HistoryPanel';
import { RepoFilesPanel } from './RepoFilesPanel';
import { RelatedRecordsPanel } from './RelatedRecordsPanel';
import { MarkdownView } from './MarkdownView';
import { toggleChecklistItem } from '../util/checklist';
import { FulltextPanel } from './FulltextPanel';
import { isRtlText } from '../util/rtl';
import { useAuth } from '../hooks/useAuth';
import { useFeatureFlags } from '../hooks/useFeature';
import { AuthImage } from './AuthImage';
import { TaskTimeTracker } from './TaskTimeTracker';
import { GoalHabits } from './HabitHeatmap';

interface Props {
  config: EntityConfig;
  id: number;
  onEdit: () => void;
  onDeleted: () => void;
}

export function DetailPane({ config, id, onEdit, onDeleted }: Props) {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const { canWriteContent } = useAuth();
  const featureOn = useFeatureFlags();
  const [confirm, setConfirm] = useState(false);

  const { data: record, isLoading } = useQuery({
    queryKey: ['record', config.key, id],
    queryFn: () => getEntity(config.key, id),
  });

  const del = useMutation({
    mutationFn: () => deleteEntity(config.key, id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['list', config.key] });
      qc.invalidateQueries({ queryKey: ['calendar', config.key] });
      setConfirm(false);
      onDeleted();
    },
  });

  // Quick toggle for the star (bookmarked) / exclamation (urgent) buttons — sends the full record
  // back with just one field flipped (the generic PUT applies every DTO field, so a partial body
  // would null out everything else).
  const toggleField = useMutation({
    mutationFn: (field: 'bookmarked' | 'urgent') =>
      updateEntity(config.key, id, { ...record, [field]: !record![field] }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['record', config.key, id] });
      qc.invalidateQueries({ queryKey: ['list', config.key] });
    },
  });

  /**
   * Tick or untick a `- [ ]` item in one of the record's long-text fields.
   *
   * The whole record goes back, like every other quick toggle here: the generic PUT applies every
   * DTO field, so a partial body would null out everything it left unsaid. The text is rewritten
   * from the record's CURRENT value rather than from anything the DOM holds, so two ticks in quick
   * succession can't race — the second reads what the first saved.
   */
  const toggleChecklist = useMutation({
    mutationFn: ({ field, index }: { field: string; index: number }) => {
      const source = String((record as Record<string, unknown>)[field] ?? '');
      return updateEntity(config.key, id, {
        ...record,
        [field]: toggleChecklistItem(source, index),
      });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['record', config.key, id] });
      qc.invalidateQueries({ queryKey: ['list', config.key] });
    },
  });

  // Completion checkmark (tasks/goals): toggling on stamps completedOn and clears bookmarked (a
  // completed record no longer needs to stay pinned); toggling off just clears completedOn.
  const toggleCompletion = useMutation({
    mutationFn: () => {
      const completing = !record!.completedOn;
      const patch: Record<string, unknown> = {
        ...record,
        completedOn: completing ? new Date().toISOString() : null,
      };
      if (completing && record!.bookmarked) patch.bookmarked = false;
      return updateEntity(config.key, id, patch);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['record', config.key, id] });
      qc.invalidateQueries({ queryKey: ['list', config.key] });
    },
  });

  if (isLoading || !record) {
    return (
      <Box sx={{ p: 6, textAlign: 'center' }}>
        <CircularProgress size={28} />
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3, maxWidth: 900 }}>
      <CoverImage module={config.key} id={id} />
      <Typography
        variant="h5"
        dir={isRtlText(record.displayTitle) ? 'rtl' : 'ltr'}
        align={isRtlText(record.displayTitle) ? 'right' : 'left'}
        sx={{ fontWeight: 700, mb: 1 }}
      >
        {record.displayTitle || '(untitled)'}
      </Typography>
      <Stack direction="row" spacing={1} alignItems="flex-start" flexWrap="wrap" useFlexGap>
        {config.fields.some((f) => f.name === 'bookmarked') && (
          <Tooltip title={record.bookmarked ? 'Bookmarked — click to remove' : 'Click to bookmark'}>
            <span>
              <IconButton
                size="small"
                color="primary"
                disabled={!canWriteContent}
                onClick={() => toggleField.mutate('bookmarked')}
              >
                {record.bookmarked ? <Star fontSize="small" /> : <StarBorder fontSize="small" />}
              </IconButton>
            </span>
          </Tooltip>
        )}
        {config.fields.some((f) => f.name === 'urgent') && (
          <Tooltip title={record.urgent ? 'Urgent — click to clear' : 'Click to mark urgent'}>
            <span>
              <IconButton
                size="small"
                color="error"
                disabled={!canWriteContent}
                onClick={() => toggleField.mutate('urgent')}
              >
                {record.urgent ? <UrgentIcon fontSize="small" /> : <UrgentOutlineIcon fontSize="small" />}
              </IconButton>
            </span>
          </Tooltip>
        )}
        {(config.key === 'tasks' || config.key === 'goals') && (
          <Tooltip title={record.completedOn ? 'Completed — click to reopen' : 'Click to mark complete'}>
            <span>
              <IconButton
                size="small"
                color="success"
                disabled={!canWriteContent}
                onClick={() => toggleCompletion.mutate()}
              >
                {record.completedOn ? <CheckCircle fontSize="small" /> : <CheckCircleOutline fontSize="small" />}
              </IconButton>
            </span>
          </Tooltip>
        )}
        {canWriteContent && config.key === 'tasks' && <TaskTimeTracker taskId={id} />}
        {canWriteContent && config.key === 'tasks' && record.goal && (
          <SubtaskButton taskId={id} />
        )}
        {canWriteContent && (
          <Button size="small" startIcon={<Edit />} variant="outlined" onClick={onEdit}>
            Edit
          </Button>
        )}
        <Tooltip title="Open in the full reading page (new tab)">
          <IconButton
            size="small"
            onClick={() => window.open(`${BASE}read/${config.key}/${id}`, '_blank', 'noopener')}
          >
            <OpenInFull fontSize="small" />
          </IconButton>
        </Tooltip>
        <OpenFolderButtons module={config.key} id={id} />
        {canWriteContent && (
          <Button
            size="small"
            startIcon={<DeleteOutline />}
            color="error"
            variant="outlined"
            onClick={() => setConfirm(true)}
          >
            Delete
          </Button>
        )}
      </Stack>

      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 1.5 }}>
        {/* The record's id — matches its repo folder name and the <CODE><id>.md checkout.
            "ID:" not "#": the hash means 'type' in the nibras command syntax. */}
        <Chip size="small" variant="outlined" label={`ID: ${record.id}`} sx={{ fontFamily: 'monospace' }} />
        {record.recurring && (
          <Chip
            size="small"
            variant="outlined"
            label={`🔁 ${
              record.recurrencePreset
                ? record.recurrencePreset.charAt(0) + record.recurrencePreset.slice(1).toLowerCase()
                : 'Repeats'
            }${record.fixedSchedule ? ' (fixed)' : ''}`}
          />
        )}
        {record.status && (
          <Chip size="small" label={record.status.name} sx={lookupStyle(record.status)} />
        )}
        {record.type && (
          <Chip size="small" label={record.type.name} sx={lookupStyle(record.type)} />
        )}
        {record.context && (
          <Chip size="small" label={record.context.name} sx={lookupStyle(record.context)} />
        )}
        {record.department && (
          <Chip
            size="small"
            label={[record.department.code, record.department.name].filter(Boolean).join(' · ')}
            sx={lookupStyle(record.department)}
          />
        )}
        {record.course && (
          <Chip
            size="small"
            label={`📚 ${[record.course.code, record.course.name].filter(Boolean).join(' · ')}`}
            sx={lookupStyle(record.course)}
          />
        )}
        {record.book && (
          <Chip
            size="small"
            label={`📖 ${record.book.name}`}
            onClick={() => navigate(`/resources?select=${record.book!.id}`)}
            clickable
          />
        )}
        <Chip
          size="small"
          variant="outlined"
          label={`Created ${new Date(record.dateCreated).toLocaleString()}`}
        />
      </Stack>

      {/* Tags are editable here (view mode) — no need to open the form. Only for taggable modules.
          Read-only users still see them, without add/remove. */}
      {Array.isArray(record.tags) && (
        <TagEditor
          module={config.key}
          id={id}
          tags={record.tags}
          readOnly={!canWriteContent}
          sx={{ mt: 1.5 }}
        />
      )}

      <Divider sx={{ my: 2 }} />

      <Stack spacing={2}>
        {/* Same Settings → Features switches the form obeys, so a field turned off is off in both
            places. Nothing is deleted by hiding it — see FieldDef.feature. */}
        {config.fields.filter((f) => featureOn(f.feature)).map((f) => (
          <Box key={f.name}>
            <FieldView
              label={f.label}
              type={f.type}
              value={record[f.name]}
              record={record}
              onToggleChecklistItem={
                canWriteContent && (f.type === 'markdown' || f.type === 'richtext')
                  ? (index) => toggleChecklist.mutate({ field: f.name, index })
                  : undefined
              }
            />
            {(f.name === 'description' || f.name === 'notes') && canWriteContent && (
              <AppendToDescriptionBar
                key={`${record.id}-${f.name}`}
                module={config.key}
                id={id}
                record={record}
                field={f.name}
                fieldLabel={f.label}
              />
            )}
          </Box>
        ))}
      </Stack>

      {config.key === 'goals' && <GoalHabits goalId={id} goalTitle={record.displayTitle} />}

      {config.key === 'goals' && canWriteContent && <GoalQuickAddTask goalId={id} />}

      <RelatedRecordsPanel module={config.key} id={id} />

      <FulltextPanel module={config.key} id={id} />

      <RepoFilesPanel module={config.key} id={id} />

      <HistoryPanel entityClass={ENTITY_CLASS_BY_KEY[config.key] ?? config.key} id={id} />

      <Dialog open={confirm} onClose={() => setConfirm(false)}>
        <DialogTitle>Delete this {config.label.toLowerCase()}?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            It will be soft-deleted (recoverable in the database), not permanently removed.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirm(false)}>Cancel</Button>
          <Button color="error" onClick={() => del.mutate()} disabled={del.isPending}>
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

/**
 * "+ Subtask" — only rendered when the task has a Goal (see DetailPane). Just a summary field;
 * the goal link, order number, and department/course/priority/context inheritance all happen
 * server-side (TaskService.createSubtask).
 */
function SubtaskButton({ taskId }: { taskId: number }) {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [summary, setSummary] = useState('');

  const create = useMutation({
    mutationFn: () => createSubtask(taskId, summary.trim()),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['related', 'tasks', taskId] });
      qc.invalidateQueries({ queryKey: ['list', 'tasks'] });
      qc.invalidateQueries({ queryKey: ['calendar', 'tasks'] });
      setOpen(false);
      setSummary('');
    },
  });

  return (
    <>
      <Button
        size="small"
        startIcon={<AccountTreeOutlined />}
        variant="outlined"
        onClick={() => setOpen(true)}
      >
        + Subtask
      </Button>
      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>New subtask</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            label="Summary"
            value={summary}
            onChange={(e) => setSummary(e.target.value)}
            inputProps={{ dir: 'auto' }}
            sx={{ mt: 1 }}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && summary.trim() && !create.isPending) create.mutate();
            }}
          />
          {create.isError && (
            <Typography color="error" sx={{ mt: 1 }}>
              Could not create subtask. Check the parent task still has a goal.
            </Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={!summary.trim() || create.isPending}
            onClick={() => create.mutate()}
          >
            Create
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

/**
 * Always-visible "type a task and hit Enter" field on a goal's detail page — no button to reveal
 * it first. Creates the task linked to this goal via the generic entity endpoint (unlike
 * SubtaskButton's createSubtask, there's no parent task to inherit order/department/course from
 * here, so this just sets goalId).
 */
function GoalQuickAddTask({ goalId }: { goalId: number }) {
  const qc = useQueryClient();
  const [summary, setSummary] = useState('');

  const create = useMutation({
    mutationFn: () => createEntity('tasks', { summary: summary.trim(), goalId }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['related', 'goals', goalId] });
      qc.invalidateQueries({ queryKey: ['list', 'tasks'] });
      setSummary('');
    },
  });

  return (
    <Box sx={{ mt: 2 }}>
      <TextField
        size="small"
        fullWidth
        placeholder="+ Add a task to this goal… (Enter to create)"
        value={summary}
        onChange={(e) => setSummary(e.target.value)}
        inputProps={{ dir: 'auto' }}
        disabled={create.isPending}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && summary.trim() && !create.isPending) create.mutate();
        }}
      />
      {create.isError && (
        <Typography variant="caption" color="error">
          Could not create the task.
        </Typography>
      )}
    </Box>
  );
}

/**
 * Always-visible "append to description" field under the description field in the detail pane —
 * no button to reveal it first, focused as soon as the pane mounts (and again on switching to a
 * different record, via the `key={record.id}` on the call site). Wraps whatever is typed with a
 * configurable prefix/suffix (Settings → "Append to description") before appending — the default
 * prefix is a blank line, so a plain Enter starts a new paragraph rather than running text together.
 */
function AppendToDescriptionBar({
  module,
  id,
  record,
  field,
  fieldLabel,
}: {
  module: string;
  id: number;
  record: EntityRecord;
  /** The record property appended to — `description` or `notes`. */
  field: string;
  fieldLabel: string;
}) {
  const qc = useQueryClient();
  const [text, setText] = useState('');

  const { data: wrap } = useQuery({
    queryKey: ['setting', 'description.append'],
    queryFn: async () => ({
      prefix: await getSetting('description.append.prefix', '\\n\\n'),
      suffix: await getSetting('description.append.suffix', ''),
    }),
    staleTime: 300_000,
  });
  // Settings store the literal two characters "\n" (so they're editable in a plain text field);
  // interpret that as a real newline when applying.
  const interp = (s: string) => s.replace(/\\n/g, '\n');

  const append = useMutation({
    mutationFn: async () => {
      // Re-read the record at submit time rather than appending to the copy this component was
      // rendered with.
      //
      // Description and Notes each have one of these bars, both fed by the same cached record.
      // Appending to Description writes, invalidates and refetches — and if you append to Notes
      // before that refetch lands, the stale copy still holds the OLD description. The PUT has to
      // send the whole record (the generic update applies every DTO field, so a partial body nulls
      // the rest), so that stale description would be written back and the first append silently
      // undone.
      //
      // Nothing else catches it: every entity carries an @Version column, but no service applies
      // the version from the DTO — update() loads the row and compares it against itself — so a
      // stale write raises no conflict at all. Reading fresh here narrows the window to the
      // milliseconds between this GET and the PUT below.
      const fresh = await getEntity(module, id);
      const current = String(fresh[field] ?? '');
      const next = current + interp(wrap?.prefix ?? '') + text.trim() + interp(wrap?.suffix ?? '');
      return updateEntity(module, id, { ...fresh, [field]: next });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['record', module, id] });
      qc.invalidateQueries({ queryKey: ['list', module] });
      setText('');
    },
  });

  return (
    <Box sx={{ mt: 0.5 }}>
      {/* Deliberately NOT autoFocus. This bar is rendered inline on every record you open, so
          focusing it stole the keyboard the moment a record was selected — and the shell's key
          handler stands down whenever an input has focus (see AppShell.onKey), which killed j/k
          to move, e to edit, o to open, f1/f2/f3 for the folders, every one of them, on every
          record. A field you have to click into costs one click; one that grabs focus by itself
          costs every shortcut on the page. (The subtask dialog above keeps its autoFocus: a dialog
          you opened on purpose should be ready to type into.) */}
      <TextField
        size="small"
        fullWidth
        multiline
        minRows={1}
        // Grows with what you type and then scrolls. Appending a paragraph used to mean typing it
        // blind through a one-line slot, or writing it elsewhere and pasting.
        maxRows={7}
        placeholder={`Append to ${fieldLabel}… (Ctrl+Enter to add)`}
        value={text}
        onChange={(e) => setText(e.target.value)}
        inputProps={{ dir: 'auto' }}
        disabled={append.isPending}
        onKeyDown={(e) => {
          // Enter is a newline now that the field is multiline, so submitting moves to
          // Ctrl/Cmd+Enter. Binding plain Enter here would make it impossible to type the second
          // line of anything.
          if (e.key === 'Enter' && (e.ctrlKey || e.metaKey) && text.trim() && !append.isPending) {
            e.preventDefault();
            append.mutate();
          }
        }}
      />
      {append.isError && (
        <Typography variant="caption" color="error" sx={{ display: 'block', mt: 0.5 }}>
          {/* Show the server's own words. A 409 here means someone else changed the record and
              says to reload — "Could not append." would hide the one thing worth knowing. */}
          {errorDetail(append.error, 'Could not append.')}
        </Typography>
      )}
    </Box>
  );
}

/**
 * Inline tag editor for the detail pane: existing tags as deletable chips plus an autocomplete to
 * attach an existing tag or create a new one (type a name + Enter). Updates the record cache in place
 * so chips refresh immediately, and invalidates the list so the row's tags update too.
 */
function TagEditor({
  module,
  id,
  tags,
  sx,
  readOnly,
}: {
  module: string;
  id: number;
  tags: TagRef[];
  sx?: object;
  readOnly?: boolean;
}) {
  const qc = useQueryClient();
  const { data: allTags } = useQuery({ queryKey: ['tags'], queryFn: listTags });

  const onUpdated = (rec: EntityRecord) => {
    qc.setQueryData(['record', module, id], rec);
    qc.invalidateQueries({ queryKey: ['list', module] });
    qc.invalidateQueries({ queryKey: ['tags'] });
  };
  const add = useMutation({
    mutationFn: (name: string) => addTagToRecord(module, id, { name }),
    onSuccess: onUpdated,
  });
  const remove = useMutation({
    mutationFn: (tagId: number) => removeTagFromRecord(module, id, tagId),
    onSuccess: onUpdated,
  });

  const have = new Set(tags.map((t) => t.name.toLowerCase()));
  const options = (allTags ?? []).map((t) => t.name).filter((n) => !have.has(n.toLowerCase()));

  // Read-only viewers still see the tags, just without add/remove affordances.
  if (readOnly) {
    if (tags.length === 0) return null;
    return (
      <Stack direction="row" spacing={0.5} alignItems="center" flexWrap="wrap" useFlexGap sx={sx}>
        {tags.map((t) => (
          <Chip key={t.id} size="small" variant="outlined" label={`#${t.name}`} />
        ))}
      </Stack>
    );
  }

  return (
    <Stack direction="row" spacing={0.5} alignItems="center" flexWrap="wrap" useFlexGap sx={sx}>
      {tags.map((t) => (
        <Chip
          key={t.id}
          size="small"
          variant="outlined"
          label={`#${t.name}`}
          onDelete={() => remove.mutate(t.id)}
        />
      ))}
      <Autocomplete
        freeSolo
        size="small"
        options={options}
        value={null}
        blurOnSelect
        clearOnBlur
        disabled={add.isPending}
        onChange={(_, value) => {
          const name = (value ?? '').trim();
          if (name) add.mutate(name);
        }}
        sx={{ minWidth: 160 }}
        renderInput={(params) => (
          <TextField {...params} variant="standard" placeholder="+ add tag…" />
        )}
      />
    </Stack>
  );
}

/** Open the record's folder in repo 1/2/3 (also bound to f→1/2/3 in AppShell). Exported so the
 *  standalone reading page offers the same actions without duplicating them. */
export function OpenFolderButtons({ module, id }: { module: string; id: number }) {
  // Share RepoFilesPanel's cache (same key) so the per-repo count is live and costs no extra fetch.
  const { data } = useQuery({
    queryKey: ['repo-files', module, id],
    queryFn: () => listRepoFiles(module, id),
  });
  const files = data ?? [];
  // Repos 2 and 3 are hideable (Settings → Features). Repo 1 is not: it is where checkouts and
  // bookmarks live, so a record always has one. Hiding a button changes nothing on disk, and the
  // f→2 / f→3 shortcut still works — this is about a toolbar that fits, not about access.
  const featureOn = useFeatureFlags();
  const shown = ([1, 2, 3] as const).filter((r) => r === 1 || featureOn(`repo${r}`));
  const open = (repo: 1 | 2 | 3) =>
    openRecordFolder(module, id, repo).catch(() => {
      /* backend surfaces errors; ignore here */
    });
  return (
    <Stack direction="row" spacing={0.75} alignItems="center">
      {shown.map((r) => {
        const count = files.filter((f) => f.repo === r && !f.directory).length;
        return (
          <Tooltip
            key={r}
            title={`Open repository ${r} folder — ${count} file${count === 1 ? '' : 's'}  (f ${r})`}
          >
            <Button
              size="small"
              variant="outlined"
              color={count > 0 ? 'primary' : 'inherit'}
              sx={{ minWidth: 0, px: 1, textTransform: 'none', gap: 0.5 }}
              startIcon={<FolderOpen fontSize="small" />}
              onClick={() => open(r)}
            >
              Repo{r}
              <Box
                component="span"
                sx={{
                  fontSize: 11,
                  fontWeight: 700,
                  px: 0.6,
                  borderRadius: 1,
                  bgcolor: count > 0 ? 'primary.main' : 'action.disabledBackground',
                  color: count > 0 ? 'primary.contrastText' : 'text.secondary',
                  minWidth: 16,
                  textAlign: 'center',
                }}
              >
                {count}
              </Box>
            </Button>
          </Tooltip>
        );
      })}
    </Stack>
  );
}

/** Large cover image (cover.<ext> in the record folder); hidden when there is none. */
function CoverImage({ module, id }: { module: string; id: number }) {
  return (
    <AuthImage
      url={coverUrl(module, id)}
      sx={{ maxWidth: '100%', maxHeight: 240, objectFit: 'contain', borderRadius: 1, mb: 2, display: 'block' }}
    />
  );
}

export function FieldView({
  label,
  type,
  value,
  record,
  onToggleChecklistItem,
}: {
  label: string;
  type: string;
  value: unknown;
  record: EntityRecord;
  /** Ticking a `- [ ]` item in this field. Omitted where the record can't be written. */
  onToggleChecklistItem?: (index: number) => void;
}) {
  // status/type/department/course/book/tags/recurrence are already shown as chips in the header.
  if (['status', 'type', 'department', 'course', 'book', 'tags', 'recurrence'].includes(type)) return null;
  if (value === null || value === undefined || value === '') return null;

  // Direction follows the field's own content (not the language field, which may be wrong).
  const rtl = isRtlText(String(value));

  let body;
  if (type === 'richtext' || type === 'markdown') {
    body = (
      <Box dir={rtl ? 'rtl' : 'ltr'} sx={{ textAlign: rtl ? 'right' : 'left' }}>
        <MarkdownView value={String(value)} onToggleItem={onToggleChecklistItem} />
      </Box>
    );
  } else if (type === 'url') {
    body = (
      <Link href={String(value)} target="_blank" rel="noopener noreferrer" sx={{ wordBreak: 'break-all' }}>
        {String(value)}
      </Link>
    );
  } else if (type === 'switch') {
    body = <Typography>{value ? 'Yes' : 'No'}</Typography>;
  } else if (type === 'datetime') {
    body = <Typography>{new Date(String(value)).toLocaleString()}</Typography>;
  } else {
    body = (
      <Typography
        dir={rtl ? 'rtl' : 'ltr'}
        align={rtl ? 'right' : 'left'}
        sx={{ whiteSpace: 'pre-wrap' }}
      >
        {String(value)}
      </Typography>
    );
  }

  return (
    <Box>
      <Typography variant="overline" color="text.secondary">
        {label}
      </Typography>
      {body}
    </Box>
  );
}
