import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Box,
  Card,
  CardContent,
  Chip,
  FormControl,
  Grid,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Select,
  Stack,
  Typography,
} from '@mui/material';
import CheckCircleOutline from '@mui/icons-material/CheckCircleOutline';
import MenuBook from '@mui/icons-material/MenuBook';
import EventNote from '@mui/icons-material/EventNote';
import LocalLibrary from '@mui/icons-material/LocalLibrary';
import StickyNote2 from '@mui/icons-material/StickyNote2';
import Flag from '@mui/icons-material/Flag';
import Create from '@mui/icons-material/Create';
import InsightsIcon from '@mui/icons-material/Insights';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { getInsights, listEntities, ModuleInsight } from '../api/client';
import { SideNav } from '../components/SideNav';
import { dirOf } from '../util/rtl';

// Fixed categorical order (never cycled/reassigned by sort or filter) — slots 1/3/4/5/6/7 of the
// validated 8-hue default palette (see the dataviz skill's reference palette); slot 2 (orange) is
// reserved separately for the "updated" series in the per-module histograms below, so it never
// double-books as a module's identity color.
const MODULE_COLOR: Record<string, { light: string; dark: string; Icon: typeof Flag }> = {
  tasks: { light: '#2a78d6', dark: '#3987e5', Icon: CheckCircleOutline },
  journals: { light: '#1baf7a', dark: '#199e70', Icon: MenuBook },
  planners: { light: '#eda100', dark: '#c98500', Icon: EventNote },
  resources: { light: '#e87ba4', dark: '#d55181', Icon: LocalLibrary },
  notes: { light: '#008300', dark: '#008300', Icon: StickyNote2 },
  writings: { light: '#4a3aa7', dark: '#9085e9', Icon: Create },
  goals: { light: '#e34948', dark: '#e66767', Icon: Flag },
};

// The two series shown in every per-module histogram — a fixed pair (not module identity), so
// "created" and "updated" mean the same color across all 7 cards. Adjacent slots 1/2 of the
// default palette, validated to clear the CVD/normal-vision floors together.
const CREATED_COLOR = '#2a78d6';
const UPDATED_COLOR = '#eb6834';

// Status palette (fixed, never themed) — reserved for "how fresh is this module", never reused
// as a module identity color. Always paired with an icon/label per the dataviz skill's rule.
const STATUS = {
  good: '#0ca30c',
  warning: '#fab219',
  serious: '#ec835a',
  critical: '#d03b3b',
};

// The covered period (denominator + charts) and the trailing "review window" (what counts as
// "reviewed") are independently configurable — a short period keeps the page focused on recent
// work instead of a multi-year backlog; a short review window is what actually motivates review.
const PERIOD_OPTIONS = [
  { days: 7, label: 'Last week' },
  { days: 30, label: 'Last month' },
  { days: 90, label: 'Last 3 months' },
  { days: 180, label: 'Last 6 months' },
  { days: 365, label: 'Last year' },
];
const REVIEW_OPTIONS = [
  { days: 1, label: 'Last day' },
  { days: 7, label: 'Last week' },
  { days: 14, label: 'Last 2 weeks' },
  { days: 30, label: 'Last month' },
];
const DEFAULT_PERIOD_DAYS = 90;
const DEFAULT_REVIEW_DAYS = 7;

function loadStoredDays(key: string, fallback: number): number {
  const raw = Number(localStorage.getItem(key));
  return Number.isFinite(raw) && raw > 0 ? raw : fallback;
}

function isoDaysAgo(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - (days - 1));
  return d.toISOString().slice(0, 10);
}

function freshnessStatus(pct: number): { color: string; label: string } {
  if (pct >= 50) return { color: STATUS.good, label: 'Fresh' };
  if (pct >= 20) return { color: STATUS.warning, label: 'Aging' };
  if (pct >= 5) return { color: STATUS.serious, label: 'Stale' };
  return { color: STATUS.critical, label: 'Neglected' };
}

/** Bucket a daily {date,count} series into ISO-week totals — 90 daily bars is unreadable at this
 *  card size; ~13 weekly bars reads cleanly. */
function toWeeklyBuckets(series: { date: string; count: number }[]): { week: string; count: number }[] {
  const byWeek = new Map<string, number>();
  for (const { date, count } of series) {
    const d = new Date(date);
    const monday = new Date(d);
    const day = (d.getDay() + 6) % 7; // 0 = Monday
    monday.setDate(d.getDate() - day);
    const key = monday.toISOString().slice(0, 10);
    byWeek.set(key, (byWeek.get(key) ?? 0) + count);
  }
  return Array.from(byWeek.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([week, count]) => ({
      week: new Date(week).toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
      count,
    }));
}

export function InsightsPage() {
  const navigate = useNavigate();
  const [periodDays, setPeriodDays] = useState(() => loadStoredDays('nibras.insights.periodDays', DEFAULT_PERIOD_DAYS));
  const [reviewDays, setReviewDays] = useState(() => loadStoredDays('nibras.insights.reviewDays', DEFAULT_REVIEW_DAYS));

  const changePeriod = (days: number) => {
    setPeriodDays(days);
    localStorage.setItem('nibras.insights.periodDays', String(days));
  };
  const changeReview = (days: number) => {
    setReviewDays(days);
    localStorage.setItem('nibras.insights.reviewDays', String(days));
  };

  const from = useMemo(() => isoDaysAgo(periodDays), [periodDays]);
  const to = new Date().toISOString().slice(0, 10);
  const periodLabel = PERIOD_OPTIONS.find((o) => o.days === periodDays)?.label.toLowerCase() ?? `${periodDays} days`;
  const reviewLabel = REVIEW_OPTIONS.find((o) => o.days === reviewDays)?.label.toLowerCase() ?? `${reviewDays} days`;

  const { data, isLoading } = useQuery({
    queryKey: ['insights', from, to, reviewDays],
    queryFn: () => getInsights(from, to, reviewDays),
  });
  const modules = useMemo(() => data?.modules ?? [], [data]);

  const totalRecords = modules.reduce((sum, m) => sum + m.total, 0);
  const pieData = modules
    .filter((m) => m.total > 0)
    .map((m) => ({ name: m.label, key: m.key, value: m.total }));

  // Most-neglected first — a ranking meant to motivate reviewing whichever type has drifted the
  // furthest, but bar COLOR still follows module identity (never recolored by rank/position).
  const ranked = [...modules].sort((a, b) => a.percentUpdatedRecent - b.percentUpdatedRecent);

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: 3 }}>
        <Stack direction="row" alignItems="center" spacing={1.5} sx={{ mb: 1.5 }} flexWrap="wrap">
          <InsightsIcon color="primary" />
          <Typography variant="h5" sx={{ fontWeight: 700, mr: 1 }}>
            Insights
          </Typography>
          <FormControl size="small">
            <Select value={periodDays} onChange={(e) => changePeriod(Number(e.target.value))} sx={{ fontSize: 13 }}>
              {PERIOD_OPTIONS.map((o) => (
                <MenuItem key={o.days} value={o.days} sx={{ fontSize: 13 }}>
                  {o.label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <Typography variant="body2" color="text.secondary">
            reviewed in
          </Typography>
          <FormControl size="small">
            <Select value={reviewDays} onChange={(e) => changeReview(Number(e.target.value))} sx={{ fontSize: 13 }}>
              {REVIEW_OPTIONS.map((o) => (
                <MenuItem key={o.days} value={o.days} sx={{ fontSize: 13 }}>
                  {o.label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Stack>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          {data
            ? `${totalRecords.toLocaleString()} records created in the ${periodLabel} across ${modules.length} types · "reviewed" = updated in the ${reviewLabel}`
            : 'Loading…'}
        </Typography>

        {!isLoading && modules.length > 0 && (
          <Grid container spacing={2} sx={{ mb: 3 }}>
            <Grid item xs={12} md={5}>
              <Card variant="outlined" sx={{ height: '100%' }}>
                <CardContent>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
                    Where your records live
                  </Typography>
                  <ResponsiveContainer width="100%" height={240}>
                    <PieChart>
                      <Pie data={pieData} dataKey="value" nameKey="name" innerRadius={50} outerRadius={90} paddingAngle={2}>
                        {pieData.map((entry) => (
                          <Cell key={entry.key} fill={MODULE_COLOR[entry.key]?.light ?? '#898781'} />
                        ))}
                      </Pie>
                      <Tooltip formatter={(value) => Number(value ?? 0).toLocaleString()} />
                      <Legend wrapperStyle={{ fontSize: 11 }} />
                    </PieChart>
                  </ResponsiveContainer>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} md={7}>
              <Card variant="outlined" sx={{ height: '100%' }}>
                <CardContent>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>
                    Needs review first
                  </Typography>
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
                    Share of {periodLabel}&rsquo;s records updated in the {reviewLabel} — lowest first.
                  </Typography>
                  <Stack spacing={1}>
                    {ranked.map((m) => {
                      const status = freshnessStatus(m.percentUpdatedRecent);
                      const color = MODULE_COLOR[m.key]?.light ?? '#898781';
                      return (
                        <Box key={m.key}>
                          <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 0.25 }}>
                            <Typography variant="body2" sx={{ flex: 1, fontWeight: 600 }}>
                              {m.label}
                            </Typography>
                            <Chip
                              size="small"
                              label={`${status.label} · ${m.percentUpdatedRecent.toFixed(0)}%`}
                              sx={{ bgcolor: status.color, color: '#fff', fontWeight: 600, height: 20, fontSize: 11 }}
                            />
                          </Stack>
                          <Box sx={{ height: 8, borderRadius: 1, bgcolor: 'action.hover', overflow: 'hidden' }}>
                            <Box
                              sx={{
                                height: '100%',
                                width: `${Math.min(100, m.percentUpdatedRecent)}%`,
                                bgcolor: color,
                                borderRadius: 1,
                                transition: 'width 0.3s',
                              }}
                            />
                          </Box>
                        </Box>
                      );
                    })}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        )}

        {/* One shared legend for every per-module histogram below — the created/updated color
            mapping is identical across all cards, so repeating a legend per card would be noise. */}
        {modules.length > 0 && (
          <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 1 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
              Activity by type
            </Typography>
            <Stack direction="row" spacing={0.75} alignItems="center">
              <Box sx={{ width: 10, height: 10, borderRadius: 0.5, bgcolor: CREATED_COLOR }} />
              <Typography variant="caption" color="text.secondary">
                Created
              </Typography>
            </Stack>
            <Stack direction="row" spacing={0.75} alignItems="center">
              <Box sx={{ width: 10, height: 10, borderRadius: 0.5, bgcolor: UPDATED_COLOR }} />
              <Typography variant="caption" color="text.secondary">
                Updated
              </Typography>
            </Stack>
          </Stack>
        )}

        <Grid container spacing={2}>
          {modules.map((m) => (
            <Grid item key={m.key} xs={12} sm={6} lg={4}>
              <ModuleCard module={m} from={from} to={to} onOpenRecord={(id) => navigate(`/${m.key}?select=${id}`)} />
            </Grid>
          ))}
        </Grid>
      </Box>
    </Box>
  );
}

function ModuleCard({
  module: m,
  from,
  to,
  onOpenRecord,
}: {
  module: ModuleInsight;
  from: string;
  to: string;
  onOpenRecord: (id: number) => void;
}) {
  const { Icon } = MODULE_COLOR[m.key] ?? { Icon: Flag };
  const color = MODULE_COLOR[m.key]?.light ?? '#898781';
  const status = freshnessStatus(m.percentUpdatedRecent);

  const created = toWeeklyBuckets(m.createdSeries);
  const updated = toWeeklyBuckets(m.updatedSeries);
  const chartData = created.map((c, i) => ({ week: c.week, created: c.count, updated: updated[i]?.count ?? 0 }));

  // Scoped to the same period as the rest of the page — an item from years ago has no business
  // topping the "needs review" list for a "last 3 months" view.
  const { data: stale } = useQuery({
    queryKey: ['insights-stale', m.key, from, to],
    queryFn: () => listEntities(m.key, { from, to, size: 4, sort: 'lastUpdated,asc' }),
  });

  return (
    <Card variant="outlined">
      <CardContent>
        <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 0.5 }}>
          <Icon fontSize="small" sx={{ color }} />
          <Typography sx={{ fontWeight: 700, flex: 1 }}>{m.label}</Typography>
          <Typography variant="body2" color="text.secondary">
            {m.total.toLocaleString()}
          </Typography>
        </Stack>
        <Chip
          size="small"
          label={`${status.label} · ${m.percentUpdatedRecent.toFixed(0)}% updated recently`}
          sx={{ bgcolor: status.color, color: '#fff', fontWeight: 600, fontSize: 11, mb: 1 }}
        />

        {chartData.length > 0 ? (
          <ResponsiveContainer width="100%" height={110}>
            <BarChart data={chartData} margin={{ top: 4, right: 4, bottom: 0, left: -20 }} barGap={2}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="week" tick={{ fontSize: 9 }} interval="preserveStartEnd" />
              <YAxis tick={{ fontSize: 10 }} width={30} allowDecimals={false} />
              <Tooltip />
              <Bar dataKey="created" fill={CREATED_COLOR} radius={[2, 2, 0, 0]} maxBarSize={14} />
              <Bar dataKey="updated" fill={UPDATED_COLOR} radius={[2, 2, 0, 0]} maxBarSize={14} />
            </BarChart>
          </ResponsiveContainer>
        ) : (
          <Typography variant="caption" color="text.secondary">
            No activity in this window.
          </Typography>
        )}

        {stale && stale.content.length > 0 && (
          <Box sx={{ mt: 1 }}>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.25 }}>
              Haven&rsquo;t been touched in a while:
            </Typography>
            <List dense disablePadding>
              {stale.content.map((r) => {
                const rtl = dirOf(r.displayTitle) === 'rtl';
                return (
                  <ListItemButton key={r.id} sx={{ py: 0.1, px: 0.5, borderRadius: 1 }} onClick={() => onOpenRecord(r.id)}>
                    <ListItemText
                      primary={r.displayTitle || '(untitled)'}
                      primaryTypographyProps={{
                        dir: rtl ? 'rtl' : 'ltr',
                        align: rtl ? 'right' : 'left',
                        noWrap: true,
                        fontSize: 13,
                      }}
                    />
                  </ListItemButton>
                );
              })}
            </List>
          </Box>
        )}
      </CardContent>
    </Card>
  );
}
