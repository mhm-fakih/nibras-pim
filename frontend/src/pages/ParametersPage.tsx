import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  IconButton,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import Add from '@mui/icons-material/Add';
import Close from '@mui/icons-material/Close';
import Check from '@mui/icons-material/Check';
import Edit from '@mui/icons-material/Edit';
import {
  LookupValue,
  createLookupValue,
  deleteLookupValue,
  fillLookupColors,
  getLookupGroups,
  renameLookupValue,
  setLookupDefault,
  setLookupStyle,
} from '../api/client';
import { SideNav } from '../components/SideNav';
import { lookupStyle } from '../components/statusStyle';

/**
 * Parameter (lookup) data entry — deliberately its own page, separate from Settings. Settings is
 * app configuration behind `tools.settings`; this is reference data behind `params.write`, the
 * capability the PARAMETERS_DATA_ENTRY role holds, so that role can maintain the vocabulary
 * without being handed the admin/settings surface.
 */
export function ParametersPage() {
  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: 4, maxWidth: 900 }}>
        <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }}>
          Parameters
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          The shared vocabulary records are classified by. Per-module usage counts for these live on
          the Administration page.
        </Typography>

        <LookupColorsSection />
      </Box>
    </Box>
  );
}

/** Add/rename/remove/colour each parametric lookup value (status / type / context) + its default. */
function LookupColorsSection() {
  const qc = useQueryClient();
  const { data } = useQuery({ queryKey: ['lookup-groups'], queryFn: getLookupGroups });
  const groups = data ?? [];

  const fill = useMutation({
    mutationFn: fillLookupColors,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['lookup-groups'] }),
  });

  return (
    <Box sx={{ mt: 3 }}>
      <Stack direction="row" alignItems="center" spacing={2} sx={{ mb: 1 }}>
        <Typography variant="h6" sx={{ flex: 1, fontWeight: 700, color: 'text.primary' }}>
          Values &amp; colours
        </Typography>
        <Button
          variant="outlined"
          size="small"
          disabled={fill.isPending}
          onClick={() => fill.mutate()}
        >
          Fill empty colours
        </Button>
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Add, rename, remove, colour and set the default for each status / type / context used
        across records (department/course have their own full pages — they're regular records, not
        simple lookups). Click the ★ to make a value the parameter’s <strong>default</strong>
        (marked ★): new records left blank get it, even when the column is required.
      </Typography>
      {fill.isSuccess && (
        <Alert severity="success" sx={{ mb: 2, py: 0 }}>
          Filled {fill.data} value(s).
        </Alert>
      )}

      <Stack spacing={2}>
        {groups.map((g) => (
          <Box key={g.table}>
            <Typography variant="overline" color="text.secondary">
              {g.label}
            </Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 0.5 }}>
              {g.values.map((v) => (
                <LookupChip
                  key={v.id}
                  table={g.table}
                  value={v}
                  isDefault={g.defaultId === v.id}
                />
              ))}
              {g.values.length === 0 && (
                <Typography variant="caption" color="text.secondary">
                  (none)
                </Typography>
              )}
              <AddLookupValueForm table={g.table} />
            </Stack>
          </Box>
        ))}
      </Stack>
    </Box>
  );
}

/** A lookup value shown as its coloured chip: inline colour picker, default (★) toggle, and — via
 * the pencil — inline rename of both the display name and the code. */
function LookupChip({
  table,
  value,
  isDefault,
}: {
  table: string;
  value: LookupValue;
  isDefault: boolean;
}) {
  const qc = useQueryClient();
  const style = lookupStyle(value);
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(value.name);
  const [code, setCode] = useState(value.code ?? '');

  const invalidate = () => qc.invalidateQueries({ queryKey: ['lookup-groups'] });
  const save = useMutation({
    mutationFn: (hex: string) => setLookupStyle(table, value.id, `background-color:${hex}`),
    onSuccess: invalidate,
  });
  const setDefault = useMutation({
    // Toggle: starring the current default clears it; starring another moves the default.
    mutationFn: () => setLookupDefault(table, isDefault ? null : value.id),
    onSuccess: invalidate,
  });
  const remove = useMutation({
    mutationFn: () => deleteLookupValue(table, value.id),
    onSuccess: invalidate,
  });
  const rename = useMutation({
    mutationFn: () => renameLookupValue(table, value.id, name.trim(), code.trim() || undefined),
    onSuccess: () => {
      setEditing(false);
      invalidate();
    },
  });

  const startEdit = () => {
    setName(value.name);
    setCode(value.code ?? '');
    setEditing(true);
  };
  const submitRename = () => name.trim() && !rename.isPending && rename.mutate();

  if (editing) {
    return (
      <Box
        sx={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 0.5,
          px: 1,
          py: 0.25,
          borderRadius: 4,
          fontSize: 13,
          ...style,
        }}
      >
        <TextField
          variant="standard"
          autoFocus
          value={name}
          onChange={(e) => setName(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') submitRename();
            if (e.key === 'Escape') setEditing(false);
          }}
          sx={{ width: 110 }}
          inputProps={{ style: { fontSize: 13 } }}
        />
        <TextField
          variant="standard"
          placeholder="code"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') submitRename();
            if (e.key === 'Escape') setEditing(false);
          }}
          sx={{ width: 60 }}
          inputProps={{ style: { fontSize: 13, fontFamily: 'monospace' } }}
        />
        <Tooltip title="Save">
          <span>
            <IconButton size="small" disabled={!name.trim() || rename.isPending} onClick={submitRename}>
              <Check sx={{ fontSize: 15 }} />
            </IconButton>
          </span>
        </Tooltip>
        <Tooltip title="Cancel">
          <IconButton size="small" onClick={() => setEditing(false)}>
            <Close sx={{ fontSize: 15 }} />
          </IconButton>
        </Tooltip>
      </Box>
    );
  }

  return (
    <Box
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 0.5,
        px: 1,
        py: 0.25,
        borderRadius: 4,
        fontSize: 13,
        ...style,
      }}
    >
      <Box
        component="span"
        onClick={() => setDefault.mutate()}
        title={isDefault ? 'Default — click to clear' : 'Set as default for blank records'}
        sx={{ cursor: 'pointer', opacity: isDefault ? 1 : 0.4, lineHeight: 1 }}
      >
        {isDefault ? '★' : '☆'}
      </Box>
      <Box
        component="span"
        onDoubleClick={startEdit}
        title="Double-click to rename"
        sx={{ cursor: 'default' }}
      >
        {value.name}
        {value.code && (
          <Box component="span" sx={{ opacity: 0.6, ml: 0.5, fontFamily: 'monospace', fontSize: 11 }}>
            {value.code}
          </Box>
        )}
      </Box>
      <Box component="label" sx={{ display: 'inline-flex', cursor: 'pointer', lineHeight: 1 }} title="Recolour">
        <input
          type="color"
          value={style.backgroundColor && /^#/.test(style.backgroundColor) ? style.backgroundColor : '#cccccc'}
          onChange={(e) => save.mutate(e.target.value)}
          style={{ width: 18, height: 18, border: 'none', background: 'none', padding: 0, cursor: 'pointer' }}
        />
      </Box>
      <Box
        component="span"
        onClick={startEdit}
        title="Rename"
        sx={{ cursor: 'pointer', opacity: 0.4, lineHeight: 1, '&:hover': { opacity: 1 } }}
      >
        <Edit sx={{ fontSize: 14 }} />
      </Box>
      <Box
        component="span"
        onClick={() => {
          if (window.confirm(`Remove "${value.name}"? Existing records keep showing it until changed.`)) {
            remove.mutate();
          }
        }}
        title="Remove this value"
        sx={{ cursor: 'pointer', opacity: 0.4, lineHeight: 1, '&:hover': { opacity: 1 } }}
      >
        <Close sx={{ fontSize: 14 }} />
      </Box>
    </Box>
  );
}

/** Inline "add a new value" mini-form appended to a lookup group's chip row. */
function AddLookupValueForm({ table }: { table: string }) {
  const qc = useQueryClient();
  const [name, setName] = useState('');
  const [code, setCode] = useState('');

  const create = useMutation({
    mutationFn: () => createLookupValue(table, name.trim(), code.trim() || undefined),
    onSuccess: () => {
      setName('');
      setCode('');
      qc.invalidateQueries({ queryKey: ['lookup-groups'] });
    },
  });

  const submit = () => {
    if (name.trim() && !create.isPending) create.mutate();
  };

  return (
    <Stack direction="row" spacing={0.5} alignItems="center">
      <TextField
        variant="standard"
        placeholder="New value…"
        value={name}
        onChange={(e) => setName(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && submit()}
        sx={{ width: 120 }}
        inputProps={{ style: { fontSize: 13 } }}
      />
      <TextField
        variant="standard"
        placeholder="code"
        value={code}
        onChange={(e) => setCode(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && submit()}
        sx={{ width: 70 }}
        inputProps={{ style: { fontSize: 13, fontFamily: 'monospace' } }}
      />
      <Tooltip title="Add value">
        <span>
          <IconButton size="small" disabled={!name.trim() || create.isPending} onClick={submit}>
            <Add fontSize="small" />
          </IconButton>
        </span>
      </Tooltip>
    </Stack>
  );
}
