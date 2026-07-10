import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Chip,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import PlaylistAddIcon from '@mui/icons-material/PlaylistAdd';
import { batchEntry, getEntity, BatchEntryResult } from '../api/client';
import { SideNav } from '../components/SideNav';
import { RecordCard } from '../components/RecordCard';
import { useModules } from '../hooks/useModules';

/** Fetches a just-created record and shows it as a card (used for each successful batch line). */
function CreatedRecordCard({ module, id }: { module: string; id: number }) {
  const { data } = useQuery({ queryKey: ['record', module, id], queryFn: () => getEntity(module, id) });
  if (!data) return null;
  return <RecordCard module={module} record={data} sx={{ mb: 1 }} />;
}

/** Modules that batch entry can create records in. */
const CREATABLE = ['tasks', 'journals', 'planners', 'resources', 'notes', 'goals', 'writings', 'courses'];

export function BatchEntryPage() {
  const qc = useQueryClient();
  const { enabled } = useModules();
  const options = enabled.filter((m) => CREATABLE.includes(m.moduleKey));
  const [module, setModule] = useState('tasks');
  const [text, setText] = useState('');
  const [result, setResult] = useState<BatchEntryResult | null>(null);

  const add = useMutation({
    mutationFn: () => batchEntry(module, text),
    onSuccess: (res) => {
      setResult(res);
      qc.invalidateQueries({ queryKey: ['list'] });
      qc.invalidateQueries({ queryKey: ['kpis'] });
      // Drop the lines that were created so a re-run won't duplicate them.
      const failed = res.results.filter((r) => r.error).map((r) => r.input);
      setText(failed.join('\n'));
    },
  });

  const lines = text.split('\n').filter((l) => l.trim()).length;

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: 4, maxWidth: 900 }}>
        <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }}>
          Batch entry
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          One record per line. Tokens are resolved against the line's module; the remaining words
          become the title. A line may start with a module code (e.g. <code>t</code>) to override the
          default module below.
        </Typography>

        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mb: 2 }}>
          <SyntaxChip token="#code" label="type" />
          <SyntaxChip token="?code" label="status" />
          <SyntaxChip token="@code" label="context" />
          <SyntaxChip token="dCODE" label="department" />
          <SyntaxChip token="cCODE" label="course" />
          <SyntaxChip token="p3" label="priority" />
          <SyntaxChip token="=code" label="code" />
          <SyntaxChip token="*" label="bookmark" />
        </Stack>

        <TextField
          select
          size="small"
          label="Default module"
          value={module}
          onChange={(e) => setModule(e.target.value)}
          sx={{ width: 220, mb: 2 }}
        >
          {options.map((m) => (
            <MenuItem key={m.moduleKey} value={m.moduleKey}>
              {m.label}
              {m.code ? ` (${m.code})` : ''}
            </MenuItem>
          ))}
        </TextField>

        <TextField
          fullWidth
          multiline
          minRows={8}
          placeholder={
            't p3 ?done @home a task done at home\nn a quick note\nc dA =alg Algebra'
          }
          value={text}
          onChange={(e) => setText(e.target.value)}
          inputProps={{ style: { fontFamily: 'monospace' }, dir: 'auto' }}
          sx={{ mb: 2 }}
        />

        <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 3 }}>
          <Button
            variant="contained"
            startIcon={<PlaylistAddIcon />}
            disabled={add.isPending || lines === 0}
            onClick={() => add.mutate()}
          >
            Add {lines || ''} record{lines === 1 ? '' : 's'}
          </Button>
          {add.isError && (
            <Alert severity="error" sx={{ py: 0 }}>
              {(add.error as { response?: { data?: { message?: string } } })?.response?.data?.message ??
                'Failed.'}
            </Alert>
          )}
        </Stack>

        {result && (
          <Box>
            <Alert severity={result.created > 0 ? 'success' : 'warning'} sx={{ mb: 2 }}>
              Created {result.created} of {result.results.length} line(s).
              {result.created < result.results.length &&
                ' Failed lines were kept in the box above so you can fix and re-run.'}
            </Alert>
            <Stack spacing={1}>
              {result.results.map((r) =>
                r.error ? (
                  <Stack key={r.line} direction="row" spacing={1} alignItems="flex-start">
                    <Chip size="small" color="error" variant="outlined" label="error" />
                    <Box sx={{ minWidth: 0 }}>
                      <Typography sx={{ fontFamily: 'monospace', fontSize: 13 }} dir="auto">
                        {r.input}
                      </Typography>
                      <Typography variant="caption" color="error">
                        {r.error}
                      </Typography>
                    </Box>
                  </Stack>
                ) : r.module && r.id != null ? (
                  <CreatedRecordCard key={r.line} module={r.module} id={r.id} />
                ) : null,
              )}
            </Stack>
          </Box>
        )}
      </Box>
    </Box>
  );
}

function SyntaxChip({ token, label }: { token: string; label: string }) {
  return (
    <Chip
      size="small"
      variant="outlined"
      label={
        <span>
          <code>{token}</code> {label}
        </span>
      }
    />
  );
}
