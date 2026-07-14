import { FormEvent, useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Autocomplete,
  Box,
  Button,
  CircularProgress,
  FormControlLabel,
  MenuItem,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker';
import {
  createEntity,
  getEntity,
  getLookups,
  listCourses,
  listEntities,
  updateEntity,
  CourseOption,
  EntityRecord,
  Lookup,
} from '../api/client';
import { EntityConfig, FieldDef } from '../config/entities';

interface Props {
  config: EntityConfig;
  recordId?: number;
  onClose: () => void;
  onSaved: (saved: EntityRecord) => void;
}

/** Pull a human-readable message out of a save error (Spring ProblemDetail / validation body). */
function saveErrorMessage(err: unknown): string {
  const data = (err as { response?: { data?: unknown } })?.response?.data as
    | { detail?: string; message?: string; error?: string; errors?: Array<{ defaultMessage?: string; field?: string }> }
    | undefined;
  if (data?.errors?.length) {
    return data.errors
      .map((e) => (e.field ? `${e.field}: ${e.defaultMessage ?? 'invalid'}` : e.defaultMessage ?? 'invalid'))
      .join('; ');
  }
  return data?.detail ?? data?.message ?? data?.error ?? 'Save failed. Check the required fields and try again.';
}

type FormState = Record<string, unknown>;

export function EntityForm({ config, recordId, onClose, onSaved }: Props) {
  const qc = useQueryClient();
  const isEdit = recordId != null;

  const { data: lookups } = useQuery({
    queryKey: ['lookups', config.key],
    queryFn: () => getLookups(config.key),
  });

  const { data: record, isLoading } = useQuery({
    queryKey: ['record', config.key, recordId],
    queryFn: () => getEntity(config.key, recordId!),
    enabled: isEdit,
  });

  const [form, setForm] = useState<FormState>({});

  useEffect(() => {
    setForm(initForm(config.fields, record));
  }, [config.key, record]);

  const set = (name: string, value: unknown) => setForm((f) => ({ ...f, [name]: value }));

  const save = useMutation({
    mutationFn: () => {
      const body = buildPayload(config.fields, form);
      return isEdit ? updateEntity(config.key, recordId!, body) : createEntity(config.key, body);
    },
    onSuccess: (saved: EntityRecord) => {
      qc.invalidateQueries({ queryKey: ['list', config.key] });
      qc.invalidateQueries({ queryKey: ['calendar', config.key] });
      if (isEdit) qc.invalidateQueries({ queryKey: ['record', config.key, recordId] });
      onSaved(saved);
    },
  });

  if (isEdit && isLoading) {
    return (
      <Box sx={{ p: 6, textAlign: 'center' }}>
        <CircularProgress size={28} />
      </Box>
    );
  }

  return (
    <Box
      component="form"
      // Enter in a single-line field submits (multiline textareas keep Enter for a newline).
      onSubmit={(e: FormEvent) => {
        e.preventDefault();
        if (!save.isPending) save.mutate();
      }}
      sx={{ p: 3, maxWidth: 900 }}
    >
      <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
        {isEdit ? `Edit ${config.label.toLowerCase()}` : `New ${config.label.toLowerCase()}`}
      </Typography>

      <Stack spacing={2}>
        {config.fields.map((f) => (
          <FieldInput
            key={f.name}
            field={f}
            value={form[f.name]}
            lookups={lookups}
            record={record}
            onChange={(v) => set(f.name, v)}
            setField={set}
          />
        ))}
      </Stack>

      {save.isError && (
        <Typography color="error" sx={{ mt: 2 }} dir="auto">
          {saveErrorMessage(save.error)}
        </Typography>
      )}

      <Stack direction="row" spacing={1} sx={{ mt: 3 }}>
        <Button type="submit" variant="contained" disabled={save.isPending}>
          {save.isPending ? 'Saving…' : 'Save'}
        </Button>
        <Button type="button" onClick={onClose}>
          Cancel
        </Button>
      </Stack>
    </Box>
  );
}

function FieldInput({
  field,
  value,
  lookups,
  record,
  onChange,
  setField,
}: {
  field: FieldDef;
  value: unknown;
  lookups?: Record<string, Lookup[]>;
  record?: EntityRecord;
  onChange: (v: unknown) => void;
  setField: (name: string, value: unknown) => void;
}) {
  switch (field.type) {
    case 'book':
      return (
        <BookField
          value={(value as number | '') ?? ''}
          initialLabel={record?.book?.name}
          onChange={(bookId) => onChange(bookId ?? '')}
        />
      );
    case 'course':
      return (
        <CourseField
          value={(value as number | '') ?? ''}
          onPick={(courseId, departmentId) => {
            onChange(courseId ?? '');
            if (departmentId != null) setField('department', departmentId); // course → set its dept
          }}
        />
      );
    case 'textarea':
    case 'markdown':
    case 'richtext': {
      const isMarkdown = field.type !== 'textarea'; // richtext + markdown edit as Markdown source
      return (
        <TextField
          label={isMarkdown ? `${field.label} (Markdown)` : field.label}
          value={(value as string) ?? ''}
          onChange={(e) => onChange(e.target.value)}
          multiline
          minRows={isMarkdown ? 6 : 2}
          // Cap the height: past this many rows the field stops growing and scrolls internally,
          // so a long paste doesn't stretch the form and hijack the page scrollbar.
          maxRows={isMarkdown ? 18 : 8}
          fullWidth
          inputProps={{ dir: 'auto', style: isMarkdown ? { fontFamily: 'monospace' } : undefined }}
        />
      );
    }
    case 'number':
      return (
        <TextField
          label={field.label}
          type="number"
          value={(value as number | string) ?? ''}
          onChange={(e) => onChange(e.target.value === '' ? null : Number(e.target.value))}
          sx={{ maxWidth: 200 }}
        />
      );
    case 'switch':
      return (
        <FormControlLabel
          control={
            <Switch checked={Boolean(value)} onChange={(e) => onChange(e.target.checked)} />
          }
          label={field.label}
        />
      );
    case 'datetime':
      return (
        <DateTimePicker
          label={field.label}
          value={(value as Date) ?? null}
          onChange={(d) => onChange(d)}
          slotProps={{ textField: { sx: { maxWidth: 280 } }, field: { clearable: true } }}
        />
      );
    case 'status':
    case 'type':
    case 'department': {
      const lookupKey =
        field.type === 'status' ? 'statuses' : field.type === 'type' ? 'types' : 'departments';
      const options = lookups?.[lookupKey] ?? [];
      return (
        <TextField
          select
          label={field.label}
          value={(value as number | '') ?? ''}
          onChange={(e) => onChange(e.target.value === '' ? null : Number(e.target.value))}
          sx={{ maxWidth: 280 }}
        >
          <MenuItem value="">
            <em>None</em>
          </MenuItem>
          {options.map((o) => (
            <MenuItem key={o.id} value={o.id}>
              {o.code && o.name ? `${o.code} — ${o.name}` : o.name ?? o.code ?? ''}
            </MenuItem>
          ))}
        </TextField>
      );
    }
    case 'goal': {
      // Bookmarked goals only (the active ones) — searchable so a long list stays usable.
      const options = lookups?.goals ?? [];
      const id = (value as number | '') ?? '';
      // An edited task may point at a goal that's no longer bookmarked; keep it selectable.
      const current = record?.goal as Lookup | undefined;
      const all =
        id && current && !options.some((o) => o.id === id) ? [current, ...options] : options;
      const selected = all.find((o) => o.id === id) ?? null;
      return (
        <Autocomplete
          options={all}
          value={selected}
          getOptionLabel={(o) => (o.code ? `${o.code} — ${o.name}` : o.name ?? '')}
          isOptionEqualToValue={(a, b) => a.id === b.id}
          onChange={(_, o) => onChange(o ? o.id : '')}
          renderInput={(params) => (
            <TextField {...params} label={field.label} placeholder="Link to a bookmarked goal…" />
          )}
          sx={{ maxWidth: 480 }}
        />
      );
    }
    case 'tags': {
      const options = lookups?.tags ?? [];
      const ids = (value as number[]) ?? [];
      const selected = options.filter((o) => ids.includes(o.id));
      return (
        <Autocomplete
          multiple
          options={options}
          value={selected}
          getOptionLabel={(o) => o.name}
          isOptionEqualToValue={(a, b) => a.id === b.id}
          onChange={(_, v) => onChange(v.map((o) => o.id))}
          renderInput={(params) => <TextField {...params} label={field.label} />}
        />
      );
    }
    default:
      return (
        <TextField
          label={field.label}
          value={(value as string) ?? ''}
          onChange={(e) => onChange(e.target.value)}
          fullWidth
          inputProps={{ dir: 'auto' }}
        />
      );
  }
}

/** Searchable course picker; selecting a course auto-fills the record's department. */
function CourseField({
  value,
  onPick,
}: {
  value: number | '';
  onPick: (courseId: number | null, departmentId: number | null) => void;
}) {
  const { data } = useQuery({ queryKey: ['courses-all'], queryFn: listCourses, staleTime: 300_000 });
  const courses: CourseOption[] = data ?? [];
  const selected = courses.find((c) => c.id === value) ?? null;
  return (
    <Autocomplete
      options={courses}
      value={selected}
      getOptionLabel={(c) => `${c.displayTitle}${c.department?.code ? ` (${c.department.code})` : ''}`}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      onChange={(_, c) => onPick(c ? c.id : null, c ? c.departmentId ?? null : null)}
      renderInput={(params) => <TextField {...params} label="Course" />}
      sx={{ maxWidth: 400 }}
    />
  );
}

/** Searchable book picker (books are many, so it queries the resources list as you type). */
function BookField({
  value,
  initialLabel,
  onChange,
}: {
  value: number | '';
  initialLabel?: string;
  onChange: (bookId: number | null) => void;
}) {
  const [input, setInput] = useState('');
  const [q, setQ] = useState('');
  useEffect(() => {
    const t = setTimeout(() => setQ(input.trim()), 300);
    return () => clearTimeout(t);
  }, [input]);

  const { data } = useQuery({
    queryKey: ['book-search', q],
    queryFn: () => listEntities('resources', { q: q || undefined, size: 20 }),
  });

  const options = (data?.content ?? []).map((b) => ({ id: b.id, title: b.displayTitle || `#${b.id}` }));
  const selected = value
    ? options.find((o) => o.id === value) ?? { id: value as number, title: initialLabel || `#${value}` }
    : null;
  const all = selected && !options.some((o) => o.id === selected.id) ? [selected, ...options] : options;

  return (
    <Autocomplete
      options={all}
      value={selected}
      getOptionLabel={(o) => o.title}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      filterOptions={(x) => x}
      onInputChange={(_, v) => setInput(v)}
      onChange={(_, o) => onChange(o ? o.id : null)}
      renderInput={(params) => <TextField {...params} label="Book" placeholder="Search books…" />}
      sx={{ maxWidth: 480 }}
    />
  );
}

function initForm(fields: FieldDef[], record?: EntityRecord): FormState {
  const f: FormState = {};
  for (const field of fields) {
    const raw = record?.[field.name];
    switch (field.type) {
      case 'status':
        f[field.name] = record?.statusId ?? '';
        break;
      case 'type':
        f[field.name] = record?.typeId ?? '';
        break;
      case 'department':
        f[field.name] = record?.departmentId ?? '';
        break;
      case 'course':
        f[field.name] = record?.courseId ?? '';
        break;
      case 'book':
        f[field.name] = record?.bookId ?? '';
        break;
      case 'goal':
        f[field.name] = record?.goalId ?? '';
        break;
      case 'tags':
        f[field.name] = record?.tagIds ?? [];
        break;
      case 'datetime':
        f[field.name] = raw ? new Date(String(raw)) : null;
        break;
      case 'switch':
        f[field.name] = Boolean(raw);
        break;
      default:
        f[field.name] = raw ?? '';
    }
  }
  return f;
}

function buildPayload(fields: FieldDef[], form: FormState): Record<string, unknown> {
  const body: Record<string, unknown> = {};
  for (const field of fields) {
    const v = form[field.name];
    switch (field.type) {
      case 'status':
        body.statusId = v === '' || v == null ? null : v;
        break;
      case 'type':
        body.typeId = v === '' || v == null ? null : v;
        break;
      case 'department':
        body.departmentId = v === '' || v == null ? null : v;
        break;
      case 'course':
        body.courseId = v === '' || v == null ? null : v;
        break;
      case 'book':
        body.bookId = v === '' || v == null ? null : v;
        break;
      case 'goal':
        body.goalId = v === '' || v == null ? null : v;
        break;
      case 'tags':
        body.tagIds = (v as number[]) ?? [];
        break;
      case 'datetime':
        body[field.name] = v ? toLocalIso(v as Date) : null;
        break;
      case 'switch':
        body[field.name] = Boolean(v);
        break;
      case 'number':
        body[field.name] = v === '' || v == null ? null : Number(v);
        break;
      default:
        body[field.name] = v === '' ? null : v;
    }
  }
  return body;
}

/** Local date-time without timezone, matching the backend LocalDateTime. */
function toLocalIso(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(
    d.getMinutes(),
  )}:${p(d.getSeconds())}`;
}
