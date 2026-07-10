import { useMemo, useState } from 'react';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  IconButton,
  Stack,
  TextField,
  Tooltip as MuiTooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import {
  DueIndicator,
  EntityRecord,
  getIndicatorSeries,
  getIndicatorsDue,
  getKpis,
  listEntities,
  listMonitoredIndicators,
  saveIndicatorData,
} from '../api/client';
import { SideNav } from '../components/SideNav';
import { EntityForm } from '../components/EntityForm';
import { INDICATOR_CONFIG } from '../config/entities';

const KIND_COLOR: Record<string, 'primary' | 'secondary' | 'success' | 'default'> = {
  QUERY: 'primary',
  FILE: 'secondary',
  SCRIPT: 'success',
  MANUAL: 'default',
};

export function IndicatorsPage() {
  const qc = useQueryClient();
  const { data: due } = useQuery({ queryKey: ['indicators-due'], queryFn: getIndicatorsDue });
  const { data: kpis } = useQuery({ queryKey: ['kpis'], queryFn: getKpis, staleTime: 60_000 });
  const { data: monitored } = useQuery({
    queryKey: ['indicators-monitored'],
    queryFn: listMonitoredIndicators,
  });
  const { data: allPage } = useQuery({
    queryKey: ['indicators-all'],
    queryFn: () => listEntities('indicators', { size: 500 }),
  });
  const all = allPage?.content ?? [];
  const [editor, setEditor] = useState<{ open: boolean; id?: number }>({ open: false });

  // Group monitored indicators: those sharing a `category` overlay on one chart; the rest stand alone.
  const { categories, singles } = useMemo(() => {
    const byCat: Record<string, EntityRecord[]> = {};
    const loners: EntityRecord[] = [];
    for (const m of monitored ?? []) {
      const cat = ((m.category as string) ?? '').trim();
      if (cat) (byCat[cat] ??= []).push(m);
      else loners.push(m);
    }
    return { categories: Object.entries(byCat), singles: loners };
  }, [monitored]);

  const closeEditor = () => setEditor({ open: false });
  const afterSave = () => {
    qc.invalidateQueries({ queryKey: ['indicators-monitored'] });
    qc.invalidateQueries({ queryKey: ['indicators-due'] });
    qc.invalidateQueries({ queryKey: ['indicators-all'] });
    qc.invalidateQueries({ queryKey: ['kpis'] });
    closeEditor();
  };

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: 4 }}>
        <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
          <Typography variant="h4" sx={{ fontWeight: 700, flex: 1 }}>
            Indicators
          </Typography>
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditor({ open: true })}>
            New indicator
          </Button>
        </Stack>

        {/* Standard at-a-glance KPIs */}
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
            gap: 2,
            mt: 2,
          }}
        >
          {(kpis ?? []).map((k) => (
            <Card key={k.key} variant="outlined">
              <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                <Typography variant="h4" sx={{ fontWeight: 700, color: 'primary.main', lineHeight: 1.1 }}>
                  {k.value}
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  {k.label}
                </Typography>
                {k.hint && (
                  <Typography variant="caption" color="text.secondary">
                    {k.hint}
                  </Typography>
                )}
              </CardContent>
            </Card>
          ))}
        </Box>

        <Typography variant="h6" sx={{ mt: 4, mb: 1 }}>
          Needs data {due ? `(${due.length})` : ''}
        </Typography>
        {due && due.length === 0 && (
          <Alert severity="success" sx={{ mb: 2 }}>
            All monitored indicators are up to date for the current period.
          </Alert>
        )}
        <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 2 }}>
          {(due ?? []).map((d) => (
            <DueCard key={d.id} due={d} onEdit={() => setEditor({ open: true, id: d.id })} />
          ))}
        </Box>

        <Typography variant="h6" sx={{ mt: 4, mb: 1 }}>
          Monitored indicators {monitored ? `(${monitored.length})` : ''}
        </Typography>
        <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(340px, 1fr))', gap: 2 }}>
          {categories.map(([cat, inds]) => (
            <CombinedSeriesCard
              key={`cat-${cat}`}
              category={cat}
              indicators={inds}
              onEdit={(id) => setEditor({ open: true, id })}
            />
          ))}
          {singles.map((m) => (
            <SeriesCard key={m.id} indicator={m} onEdit={() => setEditor({ open: true, id: m.id })} />
          ))}
        </Box>

        <Typography variant="h6" sx={{ mt: 4, mb: 1 }}>
          All indicators ({all.length})
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          Every defined indicator. ★ = monitored (bookmarked). Click to edit or change monitoring.
        </Typography>
        <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, columnGap: 3, columns: { xs: 1, sm: 2, md: 3 } }}>
          {[...all]
            .sort((a, b) => Number(Boolean(b.bookmarked)) - Number(Boolean(a.bookmarked)))
            .map((rec) => {
              const kind = rec.query ? 'QUERY' : rec.path ? 'FILE' : rec.script ? 'SCRIPT' : 'MANUAL';
              return (
                <Box
                  component="li"
                  key={rec.id}
                  onClick={() => setEditor({ open: true, id: rec.id })}
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 1,
                    py: 0.4,
                    cursor: 'pointer',
                    breakInside: 'avoid',
                    borderRadius: 1,
                    '&:hover': { bgcolor: 'action.hover' },
                  }}
                >
                  <Typography component="span" sx={{ width: 14, color: 'primary.main' }}>
                    {rec.bookmarked ? '★' : ''}
                  </Typography>
                  <Typography component="span" sx={{ flex: 1 }} dir="auto" noWrap>
                    {rec.displayTitle || `#${rec.id}`}
                  </Typography>
                  <Chip size="small" variant="outlined" label={kind} color={KIND_COLOR[kind]} sx={{ height: 18, fontSize: 10 }} />
                </Box>
              );
            })}
        </Box>
      </Box>

      <Dialog open={editor.open} onClose={closeEditor} maxWidth="md" fullWidth>
        <EntityForm
          config={INDICATOR_CONFIG}
          recordId={editor.id}
          onClose={closeEditor}
          onSaved={afterSave}
        />
      </Dialog>
    </Box>
  );
}

function DueCard({ due, onEdit }: { due: DueIndicator; onEdit: () => void }) {
  const qc = useQueryClient();
  const [value, setValue] = useState(due.proposed != null ? String(due.proposed) : '');

  const save = useMutation({
    mutationFn: () => saveIndicatorData(due.id, Number(value)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['indicators-due'] });
      qc.invalidateQueries({ queryKey: ['indicator-series', due.id] });
    },
  });

  return (
    <Card variant="outlined">
      <CardContent>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }} flexWrap="wrap" useFlexGap>
          <Typography sx={{ fontWeight: 600, flex: 1 }} dir="auto">
            {due.name}
          </Typography>
          <Chip size="small" label={due.kind} color={KIND_COLOR[due.kind]} />
          {due.frequency && <Chip size="small" variant="outlined" label={due.frequency} />}
          <MuiTooltip title="Edit indicator">
            <IconButton size="small" onClick={onEdit}>
              <EditIcon fontSize="small" />
            </IconButton>
          </MuiTooltip>
        </Stack>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
          {due.lastValue != null
            ? `Last: ${due.lastValue}${due.lastDate ? ` · ${new Date(due.lastDate).toLocaleDateString()}` : ''}`
            : 'No data yet'}
          {due.scale ? ` · ${due.scale}` : ''}
        </Typography>
        {due.proposeError && (
          <Alert severity="warning" sx={{ mb: 1, py: 0, fontSize: 12 }}>
            {due.proposeError}
          </Alert>
        )}
        <Stack direction="row" spacing={1} alignItems="center">
          <TextField
            size="small"
            type="number"
            label={due.kind === 'MANUAL' ? 'Enter value' : 'Proposed value'}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && value !== '' && save.mutate()}
            sx={{ width: 150 }}
          />
          <Button
            variant="contained"
            size="small"
            disabled={value === '' || save.isPending}
            onClick={() => save.mutate()}
          >
            Confirm
          </Button>
        </Stack>
      </CardContent>
    </Card>
  );
}

function SeriesCard({ indicator, onEdit }: { indicator: EntityRecord; onEdit: () => void }) {
  const { data: series } = useQuery({
    queryKey: ['indicator-series', indicator.id],
    queryFn: () => getIndicatorSeries(indicator.id),
  });
  const points = (series ?? []).map((p) => ({
    date: new Date(p.date).toLocaleDateString(),
    value: p.value,
  }));
  const latest = points.length ? points[points.length - 1].value : null;

  return (
    <Card variant="outlined">
      <CardContent>
        <Stack direction="row" alignItems="baseline" spacing={1} sx={{ mb: 1 }}>
          <Typography sx={{ fontWeight: 600, flex: 1 }} dir="auto" noWrap>
            {indicator.displayTitle || (indicator.name as string) || '(indicator)'}
          </Typography>
          {latest != null && (
            <Typography variant="h6" color="primary">
              {latest}
            </Typography>
          )}
          <MuiTooltip title="Edit indicator">
            <IconButton size="small" onClick={onEdit}>
              <EditIcon fontSize="small" />
            </IconButton>
          </MuiTooltip>
        </Stack>
        {points.length > 0 ? (
          <ResponsiveContainer width="100%" height={110}>
            <LineChart data={points} margin={{ top: 4, right: 8, bottom: 0, left: -20 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="date" tick={{ fontSize: 10 }} hide={points.length > 12} />
              <YAxis tick={{ fontSize: 10 }} width={36} />
              <Tooltip />
              <Line
                type="monotone"
                dataKey="value"
                stroke={(indicator.color as string) || '#0078d4'}
                dot={points.length < 30}
                strokeWidth={2}
              />
            </LineChart>
          </ResponsiveContainer>
        ) : (
          <Typography variant="caption" color="text.secondary">
            No data points yet.
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}

/**
 * One chart overlaying every indicator in a category, each as its own coloured line with a legend.
 * Series are fetched per indicator and merged by date so the X axis is shared.
 */
function CombinedSeriesCard({
  category,
  indicators,
  onEdit,
}: {
  category: string;
  indicators: EntityRecord[];
  onEdit: (id: number) => void;
}) {
  const results = useQueries({
    queries: indicators.map((ind) => ({
      queryKey: ['indicator-series', ind.id],
      queryFn: () => getIndicatorSeries(ind.id),
    })),
  });

  const keyOf = (ind: EntityRecord) =>
    (ind.displayTitle as string) || (ind.name as string) || `#${ind.id}`;

  // Merge all series into rows keyed by date (kept sortable via the raw timestamp).
  const data = useMemo(() => {
    const byDate = new Map<string, Record<string, number | string>>();
    indicators.forEach((ind, i) => {
      const series = results[i].data ?? [];
      for (const p of series) {
        const ts = new Date(p.date).getTime();
        const label = new Date(p.date).toLocaleDateString();
        const row = byDate.get(label) ?? { date: label, _ts: ts };
        row[keyOf(ind)] = p.value;
        byDate.set(label, row);
      }
    });
    return [...byDate.values()].sort((a, b) => (a._ts as number) - (b._ts as number));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [results.map((r) => r.dataUpdatedAt).join(','), indicators]);

  return (
    <Card variant="outlined" sx={{ gridColumn: indicators.length > 1 ? 'span 2' : undefined }}>
      <CardContent>
        <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }} flexWrap="wrap" useFlexGap>
          <Typography sx={{ fontWeight: 600 }}>{category}</Typography>
          <Box sx={{ flex: 1 }} />
          {indicators.map((ind) => (
            <Chip
              key={ind.id}
              size="small"
              variant="outlined"
              label={keyOf(ind)}
              onClick={() => onEdit(ind.id)}
              sx={{ borderColor: (ind.color as string) || undefined }}
            />
          ))}
        </Stack>
        {data.length > 0 ? (
          <ResponsiveContainer width="100%" height={160}>
            <LineChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: -20 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="date" tick={{ fontSize: 10 }} hide={data.length > 12} />
              <YAxis tick={{ fontSize: 10 }} width={36} />
              <Tooltip />
              <Legend wrapperStyle={{ fontSize: 11 }} />
              {indicators.map((ind) => (
                <Line
                  key={ind.id}
                  type="monotone"
                  dataKey={keyOf(ind)}
                  stroke={(ind.color as string) || '#0078d4'}
                  dot={false}
                  strokeWidth={2}
                  connectNulls
                />
              ))}
            </LineChart>
          </ResponsiveContainer>
        ) : (
          <Typography variant="caption" color="text.secondary">
            No data points yet. Confirm values for these indicators to populate the chart.
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}
