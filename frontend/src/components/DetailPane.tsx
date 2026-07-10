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
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import Edit from '@mui/icons-material/Edit';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import FolderOpen from '@mui/icons-material/FolderOpen';
import {
  addTagToRecord,
  coverUrl,
  deleteEntity,
  getEntity,
  listRepoFiles,
  listTags,
  openRecordFolder,
  removeTagFromRecord,
  EntityRecord,
  TagRef,
} from '../api/client';
import { EntityConfig, ENTITY_CLASS_BY_KEY } from '../config/entities';
import { lookupStyle } from './statusStyle';
import { HistoryPanel } from './HistoryPanel';
import { RepoFilesPanel } from './RepoFilesPanel';
import { RelatedRecordsPanel } from './RelatedRecordsPanel';
import { MarkdownView } from './MarkdownView';
import { FulltextPanel } from './FulltextPanel';
import { isRtlText } from '../util/rtl';
import { useAuth } from '../hooks/useAuth';
import { AuthImage } from './AuthImage';
import { TaskTimeTracker } from './TaskTimeTracker';

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
      <Stack direction="row" spacing={1} alignItems="flex-start">
        <Typography
          variant="h5"
          dir={isRtlText(record.displayTitle) ? 'rtl' : 'ltr'}
          align={isRtlText(record.displayTitle) ? 'right' : 'left'}
          sx={{ flex: 1, fontWeight: 700 }}
        >
          {record.displayTitle || '(untitled)'}
        </Typography>
        {canWriteContent && config.key === 'tasks' && <TaskTimeTracker taskId={id} />}
        {canWriteContent && (
          <Button size="small" startIcon={<Edit />} variant="outlined" onClick={onEdit}>
            Edit
          </Button>
        )}
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
        {record.bookmarked ? <Chip size="small" color="primary" label="★ Bookmarked" /> : null}
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
        {config.fields.map((f) => (
          <FieldView key={f.name} label={f.label} type={f.type} value={record[f.name]} record={record} />
        ))}
      </Stack>

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

/** Open the record's folder in repo 1/2/3 (also bound to f→1/2/3 in AppShell). */
function OpenFolderButtons({ module, id }: { module: string; id: number }) {
  // Share RepoFilesPanel's cache (same key) so the per-repo count is live and costs no extra fetch.
  const { data } = useQuery({
    queryKey: ['repo-files', module, id],
    queryFn: () => listRepoFiles(module, id),
  });
  const files = data ?? [];
  const open = (repo: 1 | 2 | 3) =>
    openRecordFolder(module, id, repo).catch(() => {
      /* backend surfaces errors; ignore here */
    });
  return (
    <Stack direction="row" spacing={0.75} alignItems="center">
      {([1, 2, 3] as const).map((r) => {
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
              R{r}
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

function FieldView({
  label,
  type,
  value,
  record,
}: {
  label: string;
  type: string;
  value: unknown;
  record: EntityRecord;
}) {
  // status/type/department/course/book/tags are already shown as chips in the header.
  if (['status', 'type', 'department', 'course', 'book', 'tags'].includes(type)) return null;
  if (value === null || value === undefined || value === '') return null;

  // Direction follows the field's own content (not the language field, which may be wrong).
  const rtl = isRtlText(String(value));

  let body;
  if (type === 'richtext' || type === 'markdown') {
    body = (
      <Box dir={rtl ? 'rtl' : 'ltr'} sx={{ textAlign: rtl ? 'right' : 'left' }}>
        <MarkdownView value={String(value)} />
      </Box>
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
