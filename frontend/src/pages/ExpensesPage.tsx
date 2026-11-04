import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  MenuItem,
  Paper,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Tooltip,
  Typography,
  useTheme,
} from '@mui/material';
import Add from '@mui/icons-material/Add';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import Edit from '@mui/icons-material/Edit';
import ChevronLeft from '@mui/icons-material/ChevronLeft';
import ChevronRight from '@mui/icons-material/ChevronRight';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip as ChartTooltip,
  XAxis,
  YAxis,
} from 'recharts';
import {
  CategorySeries,
  Expense,
  ExpenseCategory,
  ExpenseInput,
  addCurrency,
  addCurrencyRate,
  createExpense,
  createExpenseCategory,
  deleteCurrency,
  deleteCurrencyRate,
  deleteExpense,
  deleteExpenseCategory,
  getExpenseYearReport,
  getExpenseYearsReport,
  listCurrencies,
  listCurrencyRates,
  listExpenseCategories,
  listExpenses,
  setDefaultCurrency,
  updateExpense,
  updateExpenseCategory,
} from '../api/client';
import { SideNav } from '../components/SideNav';
import { errorDetail } from '../util/errors';
import { isRtlText } from '../util/rtl';

type ExpTab = 'expenses' | 'report' | 'categories' | 'currencies';
const TABS: { key: ExpTab; label: string }[] = [
  { key: 'expenses', label: 'Expenses' },
  { key: 'report', label: 'Report' },
  { key: 'categories', label: 'Categories' },
  { key: 'currencies', label: 'Currencies & rates' },
];

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** Categorical slots (validated for colour-blind separation, light and dark), then grey for "Other". */
const SERIES_LIGHT = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#6250d6'];
const SERIES_DARK = ['#3987e5', '#d95926', '#199e70', '#c98500', '#d55181', '#008300', '#9085e9'];
const OTHER = { light: '#a3a29b', dark: '#6f6e68' };
/** Charts show the biggest categories by name; the rest fold into "Other" (a table lists them all). */
const TOP = 7;

const money = (v: number | null | undefined, cur?: string) =>
  v == null || !Number.isFinite(v)
    ? '—'
    : `${v.toLocaleString(undefined, { maximumFractionDigits: Math.abs(v) >= 1000 ? 0 : 2 })}${cur ? ` ${cur}` : ''}`;

const ymd = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/**
 * Expenses on the legacy payments: record them under categories, in any currency, and see where
 * the money went month by month and year by year, totalled in one currency at the exchange rate of
 * each expense's date.
 */
export function ExpensesPage() {
  const [params, setParams] = useSearchParams();
  const asked = params.get('tab');
  const tab: ExpTab = TABS.some((t) => t.key === asked) ? (asked as ExpTab) : 'expenses';
  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: { xs: 2, sm: 4 }, maxWidth: 1250 }}>
        <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }}>
          Expenses
        </Typography>
        <Tabs
          value={tab}
          onChange={(_, v: ExpTab) => setParams(v === 'expenses' ? {} : { tab: v }, { replace: true })}
          variant="scrollable"
          allowScrollButtonsMobile
          sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}
        >
          {TABS.map((t) => (
            <Tab key={t.key} value={t.key} label={t.label} />
          ))}
        </Tabs>
        {tab === 'expenses' && <ExpensesTab />}
        {tab === 'report' && <ReportTab />}
        {tab === 'categories' && <CategoriesTab />}
        {tab === 'currencies' && <CurrenciesTab />}
      </Box>
    </Box>
  );
}

function useCurrencies() {
  return useQuery({ queryKey: ['currencies'], queryFn: listCurrencies });
}

// ---------------------------------------------------------------- expenses list

function ExpensesTab() {
  const qc = useQueryClient();
  const now = new Date();
  // A month at a time by default; "All" drops the date range.
  const [month, setMonth] = useState<{ y: number; m: number } | null>({ y: now.getFullYear(), m: now.getMonth() });
  const [categoryId, setCategoryId] = useState<number | ''>('');
  const [q, setQ] = useState('');
  const [editing, setEditing] = useState<Expense | 'new' | null>(null);
  const { data: cur } = useCurrencies();
  const { data: categories = [] } = useQuery({ queryKey: ['expense-categories'], queryFn: listExpenseCategories });
  const range = month
    ? { from: ymd(new Date(month.y, month.m, 1)), to: ymd(new Date(month.y, month.m + 1, 0)) }
    : {};
  const { data: rows = [], isLoading } = useQuery({
    queryKey: ['expenses', range, categoryId, q],
    queryFn: () => listExpenses({ ...range, categoryId: categoryId === '' ? null : categoryId, q: q || undefined }),
  });
  const remove = useMutation({
    mutationFn: deleteExpense,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['expenses'] }),
  });
  const def = cur?.defaultCurrency ?? '';
  const total = rows.reduce((s, r) => s + (r.converted ?? 0), 0);
  const unconverted = rows.filter((r) => r.converted == null).length;
  const step = (d: number) => month && setMonth(((x) => ({ y: x.getFullYear(), m: x.getMonth() }))(new Date(month.y, month.m + d, 1)));

  return (
    <>
      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap sx={{ mb: 2 }}>
        <IconButton size="small" disabled={!month} onClick={() => step(-1)} aria-label="Previous month">
          <ChevronLeft />
        </IconButton>
        <Typography sx={{ minWidth: 130, textAlign: 'center', fontWeight: 600 }}>
          {month ? new Date(month.y, month.m, 1).toLocaleDateString(undefined, { month: 'long', year: 'numeric' }) : 'All dates'}
        </Typography>
        <IconButton size="small" disabled={!month} onClick={() => step(1)} aria-label="Next month">
          <ChevronRight />
        </IconButton>
        <Button size="small" onClick={() => setMonth(month ? null : { y: now.getFullYear(), m: now.getMonth() })}>
          {month ? 'All dates' : 'This month'}
        </Button>
        <TextField
          select
          size="small"
          label="Category"
          value={categoryId}
          onChange={(e) => setCategoryId(e.target.value === '' ? '' : Number(e.target.value))}
          sx={{ minWidth: 170 }}
        >
          <MenuItem value="">All categories</MenuItem>
          {categories.map((c) => (
            <MenuItem key={c.id} value={c.id} dir="auto">
              {c.name}
            </MenuItem>
          ))}
        </TextField>
        <TextField size="small" label="Search" value={q} onChange={(e) => setQ(e.target.value)} sx={{ width: 180 }} />
        <Box sx={{ flex: 1 }} />
        <Button variant="contained" startIcon={<Add />} onClick={() => setEditing('new')}>
          Add expense
        </Button>
      </Stack>

      <Stack direction="row" spacing={3} sx={{ mb: 2 }}>
        <Box>
          <Typography variant="caption" color="text.secondary">
            Total shown
          </Typography>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>
            {money(total, def)}
          </Typography>
        </Box>
        <Box>
          <Typography variant="caption" color="text.secondary">
            Expenses
          </Typography>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>
            {rows.length}
          </Typography>
        </Box>
      </Stack>
      {unconverted > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          {unconverted} expense(s) are in a currency with no rate to {def}, so they are not in the total — add a rate under
          Currencies &amp; rates.
        </Alert>
      )}

      <Paper variant="outlined" sx={{ overflowX: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Date</TableCell>
              <TableCell>Category</TableCell>
              <TableCell>What</TableCell>
              <TableCell align="right">Amount</TableCell>
              <TableCell align="right">In {def}</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {!isLoading && rows.length === 0 && (
              <TableRow>
                <TableCell colSpan={6}>
                  <Typography variant="body2" color="text.secondary">
                    No expenses {month ? 'this month' : 'found'}.
                  </Typography>
                </TableCell>
              </TableRow>
            )}
            {rows.map((r) => {
              const what = [r.summary, r.description].filter(Boolean).join(' — ');
              return (
                <TableRow key={r.id} hover>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{new Date(r.date).toLocaleDateString()}</TableCell>
                  <TableCell dir="auto">{r.categoryName ?? '—'}</TableCell>
                  <TableCell dir={isRtlText(what) ? 'rtl' : 'ltr'} sx={{ maxWidth: 420, textAlign: 'start' }}>
                    {what || '—'}
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    {money(r.amount, r.currency)}
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap', color: 'text.secondary' }}>
                    {r.currency === def ? '' : money(r.converted)}
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    <IconButton size="small" onClick={() => setEditing(r)} aria-label="Edit">
                      <Edit fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      color="error"
                      onClick={() => window.confirm('Delete this expense?') && remove.mutate(r.id)}
                      aria-label="Delete"
                    >
                      <DeleteOutline fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </Paper>
      {editing && (
        <ExpenseDialog
          expense={editing === 'new' ? null : editing}
          categories={categories}
          currencies={cur?.currencies.map((c) => c.code) ?? []}
          defaultCurrency={def}
          onClose={() => setEditing(null)}
        />
      )}
    </>
  );
}

function ExpenseDialog({
  expense,
  categories,
  currencies,
  defaultCurrency,
  onClose,
}: {
  expense: Expense | null;
  categories: ExpenseCategory[];
  currencies: string[];
  defaultCurrency: string;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const [amount, setAmount] = useState(expense ? String(expense.amount) : '');
  const [currency, setCurrency] = useState(expense?.currency ?? defaultCurrency);
  const [date, setDate] = useState(expense ? expense.date.slice(0, 10) : ymd(new Date()));
  const [category, setCategory] = useState<ExpenseCategory | string | null>(
    categories.find((c) => c.id === expense?.categoryId) ?? null,
  );
  const [summary, setSummary] = useState(expense?.summary ?? '');
  const [description, setDescription] = useState(expense?.description ?? '');
  const [error, setError] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: async () => {
      // A category typed that doesn't exist yet is created on the spot.
      let categoryId: number | null = null;
      if (typeof category === 'string' && category.trim()) {
        categoryId = (await createExpenseCategory({ name: category.trim() })).id;
      } else if (category && typeof category !== 'string') {
        categoryId = category.id;
      }
      const input: ExpenseInput = {
        amount: Number(amount),
        currency,
        date: `${date}T${expense ? expense.date.slice(11, 19) || '12:00:00' : '12:00:00'}`,
        categoryId,
        summary,
        description,
        notes: expense?.notes ?? undefined,
      };
      return expense ? updateExpense(expense.id, input) : createExpense(input);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['expenses'] });
      qc.invalidateQueries({ queryKey: ['expense-categories'] });
      qc.invalidateQueries({ queryKey: ['expense-report'] });
      onClose();
    },
    onError: (e) => setError(errorDetail(e, 'Could not save the expense.')),
  });

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{expense ? 'Edit expense' : 'Add expense'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <Stack direction="row" spacing={1}>
            <TextField
              label="Amount"
              type="number"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              inputProps={{ min: 0, step: 'any' }}
              autoFocus
              sx={{ flex: 1 }}
            />
            <TextField select label="Currency" value={currency} onChange={(e) => setCurrency(e.target.value)} sx={{ width: 120 }}>
              {currencies.map((c) => (
                <MenuItem key={c} value={c}>
                  {c}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Date"
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              InputLabelProps={{ shrink: true }}
              sx={{ width: 170 }}
            />
          </Stack>
          <Autocomplete
            freeSolo
            options={categories}
            value={category}
            onChange={(_, v) => setCategory(v)}
            onInputChange={(_, v, reason) => reason === 'input' && setCategory(categories.find((c) => c.name === v) ?? v)}
            getOptionLabel={(o) => (typeof o === 'string' ? o : o.name)}
            renderInput={(p) => (
              <TextField {...p} label="Category" helperText="Pick one, or type a new name to create it." />
            )}
          />
          <TextField label="What" value={summary} onChange={(e) => setSummary(e.target.value)} inputProps={{ dir: 'auto' }} />
          <TextField
            label="Details"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            multiline
            minRows={2}
            inputProps={{ dir: 'auto' }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={!(Number(amount) > 0) || save.isPending} onClick={() => save.mutate()}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

// ---------------------------------------------------------------- report

/**
 * Which categories get a colour of their own, and which one: the biggest {@link TOP} over ALL
 * years, in that order. Every chart on the page uses this one map, so a category wears the same
 * colour in the month chart, the year chart and the ranked bars — colour follows the category,
 * never its rank inside one chart. Everything else is "Other" (grey).
 */
/** Category id (as a string; "null" = uncategorised) → colour slot. Missing = "Other". */
type ColourKey = Map<string, number>;
const keyOf = (id: number | null) => String(id);

/** The overall map: the biggest TOP categories across all years, slots in rank order. */
function colourKeyOf(overall: CategorySeries[] | undefined): ColourKey {
  return new Map((overall ?? []).slice(0, TOP).map((c, i) => [keyOf(c.id), i]));
}

/**
 * The map for one chart: its own biggest TOP categories. One that is also in the overall map keeps
 * its overall colour, so it looks the same in every chart; the rest take the slots left free.
 */
function localKey(cats: CategorySeries[], overall: ColourKey): ColourKey {
  const shown = cats.slice(0, TOP);
  const out: ColourKey = new Map();
  shown.forEach((c) => {
    const slot = overall.get(keyOf(c.id));
    if (slot != null) out.set(keyOf(c.id), slot);
  });
  const used = new Set(out.values());
  let free = 0;
  shown.forEach((c) => {
    if (out.has(keyOf(c.id))) return;
    while (used.has(free)) free++;
    out.set(keyOf(c.id), free);
    used.add(free);
  });
  return out;
}

function seriesColour(key: ColourKey, id: number | null, dark: boolean): string {
  const i = id === -1 ? undefined : key.get(keyOf(id));
  return i != null ? (dark ? SERIES_DARK : SERIES_LIGHT)[i] : dark ? OTHER.dark : OTHER.light;
}

/** The categories with a colour, biggest first, then everything else summed as "Other". */
function foldSeries(cats: CategorySeries[], length: number, key: ColourKey) {
  const kept = cats.filter((c) => key.has(keyOf(c.id)));
  const rest = cats.filter((c) => !key.has(keyOf(c.id)));
  const other = rest.length
    ? {
        id: -1,
        name: `Other (${rest.length})`,
        values: Array.from({ length }, (_, i) => rest.reduce((s, c) => s + (c.values[i] ?? 0), 0)),
        total: rest.reduce((s, c) => s + c.total, 0),
      }
    : null;
  return other ? [...kept, other] : kept;
}

function StackedBars({
  labels,
  cats,
  currency,
  colourKey,
  height = 320,
}: {
  labels: string[];
  cats: CategorySeries[];
  currency: string;
  colourKey: ColourKey;
  height?: number;
}) {
  const theme = useTheme();
  const dark = theme.palette.mode === 'dark';
  const series = foldSeries(cats, labels.length, colourKey);
  const data = labels.map((label, i) => {
    const row: Record<string, number | string> = { label };
    series.forEach((s, k) => (row[`s${k}`] = Math.round((s.values[i] ?? 0) * 100) / 100));
    return row;
  });
  const surface = theme.palette.background.paper;
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} margin={{ top: 8, right: 8, left: 8, bottom: 0 }}>
        <CartesianGrid vertical={false} stroke={theme.palette.divider} />
        <XAxis dataKey="label" tickLine={false} axisLine={{ stroke: theme.palette.divider }} tick={{ fill: theme.palette.text.secondary, fontSize: 12 }} />
        <YAxis
          tickLine={false}
          axisLine={false}
          width={70}
          tick={{ fill: theme.palette.text.secondary, fontSize: 12 }}
          tickFormatter={(v: number) => v.toLocaleString(undefined, { notation: 'compact' })}
        />
        <ChartTooltip
          cursor={{ fill: theme.palette.action.hover }}
          formatter={(v: unknown, key: unknown) => {
            const k = Number(String(key).slice(1));
            return [money(Number(v), currency), series[k]?.name ?? ''];
          }}
          contentStyle={{ background: surface, border: `1px solid ${theme.palette.divider}`, color: theme.palette.text.primary }}
        />
        {/* Legend text in the text colour; the swatch beside it carries the identity. */}
        <Legend
          formatter={(key: unknown) => (
            <span style={{ color: theme.palette.text.primary }}>{series[Number(String(key).slice(1))]?.name ?? ''}</span>
          )}
          wrapperStyle={{ fontSize: 12 }}
        />
        {series.map((s, k) => (
          <Bar
            key={k}
            dataKey={`s${k}`}
            stackId="a"
            fill={seriesColour(colourKey, s.id === -1 ? -1 : s.id, dark)}
            stroke={surface}
            strokeWidth={1}
            maxBarSize={48}
            isAnimationActive={false}
          />
        ))}
      </BarChart>
    </ResponsiveContainer>
  );
}

/** Every category's share of the year as a ranked bar — magnitude reads best on one baseline. */
function CategoryBars({
  cats,
  total,
  currency,
  colourKey,
}: {
  cats: CategorySeries[];
  total: number;
  currency: string;
  colourKey: ColourKey;
}) {
  const theme = useTheme();
  const max = cats[0]?.total ?? 0;
  return (
    <Stack spacing={0.75}>
      {cats.map((c) => (
        <Tooltip key={c.id ?? 'none'} title={`${money(c.total, currency)} · ${total ? Math.round((c.total / total) * 100) : 0}%`} placement="top-start">
          <Box sx={{ display: 'grid', gridTemplateColumns: 'minmax(90px, 180px) 1fr auto', alignItems: 'center', gap: 1 }}>
            <Typography variant="body2" dir="auto" noWrap sx={{ textAlign: 'start' }}>
              {c.name}
            </Typography>
            <Box sx={{ height: 14, bgcolor: 'action.hover', borderRadius: 1 }}>
              <Box
                sx={{
                  height: '100%',
                  width: `${max ? Math.max(1, (c.total / max) * 100) : 0}%`,
                  bgcolor: seriesColour(colourKey, c.id, theme.palette.mode === 'dark'),
                  borderRadius: 1,
                }}
              />
            </Box>
            <Typography variant="body2" sx={{ whiteSpace: 'nowrap', minWidth: 120, textAlign: 'right' }}>
              {money(c.total, currency)}{' '}
              <Typography component="span" variant="caption" color="text.secondary">
                {total ? Math.round((c.total / total) * 100) : 0}%
              </Typography>
            </Typography>
          </Box>
        </Tooltip>
      ))}
    </Stack>
  );
}

function ReportTab() {
  const { data: cur } = useCurrencies();
  const [currency, setCurrency] = useState<string>('');
  const target = currency || cur?.defaultCurrency || '';
  const { data: years } = useQuery({
    queryKey: ['expense-report', 'years', target],
    queryFn: () => getExpenseYearsReport(target || undefined),
    enabled: !!cur,
  });
  const latest = years?.years.length ? years.years[years.years.length - 1] : new Date().getFullYear();
  const [yearPick, setYearPick] = useState<number | null>(null);
  const year = yearPick ?? latest;
  const { data: report } = useQuery({
    queryKey: ['expense-report', 'year', year, target],
    queryFn: () => getExpenseYearReport(year, target || undefined),
    enabled: !!cur,
  });
  const overallKey = colourKeyOf(years?.categories);
  const colourKey = localKey(report?.categories ?? [], overallKey);
  // Over the whole year for a past year; over the months so far for this one.
  const now = new Date();
  const monthsCounted = year < now.getFullYear() ? 12 : year === now.getFullYear() ? now.getMonth() + 1 : 12;
  const unconverted = [...(report?.unconverted ?? [])];

  return (
    <>
      <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 2 }} flexWrap="wrap" useFlexGap>
        <TextField select size="small" label="Year" value={year} onChange={(e) => setYearPick(Number(e.target.value))} sx={{ width: 120 }}>
          {(years?.years.length ? years.years : [year]).slice().reverse().map((y) => (
            <MenuItem key={y} value={y}>
              {y}
            </MenuItem>
          ))}
        </TextField>
        <TextField select size="small" label="Totals in" value={target} onChange={(e) => setCurrency(e.target.value)} sx={{ width: 130 }}>
          {(cur?.currencies ?? []).map((c) => (
            <MenuItem key={c.code} value={c.code}>
              {c.code}
            </MenuItem>
          ))}
        </TextField>
      </Stack>

      {unconverted.length > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          Not counted: {unconverted.map((u) => `${u.count} expense(s) in ${u.currency} (${money(u.amount, u.currency)})`).join(', ')} — there is no
          exchange rate from {unconverted.map((u) => u.currency).join(', ')} to {target}. Add one under Currencies &amp; rates.
        </Alert>
      )}

      {report && (
        <>
          <Stack direction="row" spacing={4} sx={{ mb: 3 }} flexWrap="wrap" useFlexGap>
            {[
              ['Spent in ' + year, money(report.total, target)],
              ['Monthly average', money(report.total / monthsCounted, target)],
              ['Biggest category', report.categories[0] ? `${report.categories[0].name}` : '—'],
            ].map(([k, v]) => (
              <Box key={k}>
                <Typography variant="caption" color="text.secondary">
                  {k}
                </Typography>
                <Typography variant="h5" dir="auto" sx={{ fontWeight: 700 }}>
                  {v}
                </Typography>
              </Box>
            ))}
          </Stack>

          <Typography variant="h6" sx={{ mb: 1 }}>
            Month by month, by category
          </Typography>
          <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
            <StackedBars labels={MONTHS} cats={report.categories} currency={target} colourKey={colourKey} />
          </Paper>

          <Typography variant="h6" sx={{ mb: 1 }}>
            Where {year}'s money went
          </Typography>
          <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
            {report.categories.length ? (
              <CategoryBars cats={report.categories} total={report.total} currency={target} colourKey={colourKey} />
            ) : (
              <Typography color="text.secondary">No expenses in {year}.</Typography>
            )}
          </Paper>

          <Typography variant="h6" sx={{ mb: 1 }}>
            Cost of each category per month ({target})
          </Typography>
          <Paper variant="outlined" sx={{ overflowX: 'auto', mb: 4 }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Category</TableCell>
                  {MONTHS.map((m) => (
                    <TableCell key={m} align="right">
                      {m}
                    </TableCell>
                  ))}
                  <TableCell align="right" sx={{ fontWeight: 700 }}>
                    Year
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {report.categories.map((c) => (
                  <TableRow key={c.id ?? 'none'} hover>
                    <TableCell dir="auto" sx={{ whiteSpace: 'nowrap' }}>
                      {c.name}
                    </TableCell>
                    {c.values.map((v, i) => (
                      <TableCell key={i} align="right" sx={{ color: v ? undefined : 'text.disabled' }}>
                        {v ? money(v) : '·'}
                      </TableCell>
                    ))}
                    <TableCell align="right" sx={{ fontWeight: 700 }}>
                      {money(c.total)}
                    </TableCell>
                  </TableRow>
                ))}
                <TableRow>
                  <TableCell sx={{ fontWeight: 700 }}>Total</TableCell>
                  {report.monthTotals.map((v, i) => (
                    <TableCell key={i} align="right" sx={{ fontWeight: 700 }}>
                      {v ? money(v) : '·'}
                    </TableCell>
                  ))}
                  <TableCell align="right" sx={{ fontWeight: 700 }}>
                    {money(report.total)}
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </Paper>
        </>
      )}

      {years && years.years.length > 0 && (
        <>
          <Typography variant="h6" sx={{ mb: 1 }}>
            Year by year, by category
          </Typography>
          <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
            <StackedBars labels={years.years.map(String)} cats={years.categories} currency={target} colourKey={overallKey} height={280} />
          </Paper>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            {years.years.map((y, i) => (
              <Chip key={y} label={`${y}: ${money(years.yearTotals[i], target)}`} variant={y === year ? 'filled' : 'outlined'} onClick={() => setYearPick(y)} />
            ))}
          </Stack>
        </>
      )}
    </>
  );
}

// ---------------------------------------------------------------- categories

function CategoriesTab() {
  const qc = useQueryClient();
  const { data: categories = [] } = useQuery({ queryKey: ['expense-categories'], queryFn: listExpenseCategories });
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [renaming, setRenaming] = useState<{ id: number; name: string; code: string } | null>(null);
  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['expense-categories'] });
    qc.invalidateQueries({ queryKey: ['expense-report'] });
  };
  const add = useMutation({
    mutationFn: () => createExpenseCategory({ name: name.trim() }),
    onSuccess: () => {
      setName('');
      refresh();
    },
    onError: (e) => setError(errorDetail(e, 'Could not add the category.')),
  });
  const rename = useMutation({
    mutationFn: (r: { id: number; name: string; code: string }) => updateExpenseCategory(r.id, { name: r.name, code: r.code }),
    onSuccess: () => {
      setRenaming(null);
      refresh();
    },
    onError: (e) => setError(errorDetail(e, 'Could not rename the category.')),
  });
  const remove = useMutation({
    mutationFn: deleteExpenseCategory,
    onSuccess: refresh,
    onError: (e) => setError(errorDetail(e, 'Could not remove the category.')),
  });
  return (
    <>
      {error && (
        <Alert severity="error" onClose={() => setError(null)} sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      <Stack component="form" direction="row" spacing={1} sx={{ mb: 2 }} onSubmit={(e) => { e.preventDefault(); if (name.trim()) add.mutate(); }}>
        <TextField size="small" label="New category" value={name} onChange={(e) => setName(e.target.value)} inputProps={{ dir: 'auto' }} />
        <Button type="submit" variant="contained" startIcon={<Add />} disabled={!name.trim() || add.isPending}>
          Add
        </Button>
      </Stack>
      <Paper variant="outlined" sx={{ maxWidth: 640 }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Category</TableCell>
              <TableCell>Code</TableCell>
              <TableCell align="right">Expenses</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {categories.map((c) => (
              <TableRow key={c.id} hover>
                <TableCell dir="auto">{c.name}</TableCell>
                <TableCell dir="auto" sx={{ color: 'text.secondary' }}>
                  {c.code}
                </TableCell>
                <TableCell align="right">{c.expenses}</TableCell>
                <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                  <IconButton size="small" onClick={() => setRenaming({ id: c.id, name: c.name, code: c.code ?? '' })} aria-label="Rename">
                    <Edit fontSize="small" />
                  </IconButton>
                  <Tooltip title={c.expenses ? 'Has expenses — move them first' : 'Remove'}>
                    <span>
                      <IconButton size="small" color="error" disabled={c.expenses > 0} onClick={() => remove.mutate(c.id)} aria-label="Remove">
                        <DeleteOutline fontSize="small" />
                      </IconButton>
                    </span>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
      <Dialog open={!!renaming} onClose={() => setRenaming(null)}>
        <DialogTitle>Rename category</DialogTitle>
        {renaming && (
          <DialogContent>
            <Stack spacing={2} sx={{ mt: 1 }}>
              <TextField label="Name" value={renaming.name} onChange={(e) => setRenaming({ ...renaming, name: e.target.value })} inputProps={{ dir: 'auto' }} />
              <TextField label="Code" value={renaming.code} onChange={(e) => setRenaming({ ...renaming, code: e.target.value })} inputProps={{ dir: 'auto' }} />
            </Stack>
          </DialogContent>
        )}
        <DialogActions>
          <Button onClick={() => setRenaming(null)}>Cancel</Button>
          <Button variant="contained" disabled={!renaming?.name.trim()} onClick={() => renaming && rename.mutate(renaming)}>
            Save
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

// ---------------------------------------------------------------- currencies & rates

function CurrenciesTab() {
  const qc = useQueryClient();
  const { data: cur } = useCurrencies();
  const { data: rates = [] } = useQuery({ queryKey: ['currency-rates'], queryFn: listCurrencyRates });
  const [error, setError] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const [cname, setCname] = useState('');
  const codes = useMemo(() => (cur?.currencies ?? []).map((c) => c.code), [cur]);
  const [rate, setRate] = useState({ from: 'USD', to: 'LBP', rate: '', validFrom: ymd(new Date()), note: '' });
  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['currencies'] });
    qc.invalidateQueries({ queryKey: ['currency-rates'] });
    qc.invalidateQueries({ queryKey: ['expenses'] });
    qc.invalidateQueries({ queryKey: ['expense-report'] });
  };
  const onError = (what: string) => (e: unknown) => setError(errorDetail(e, what));
  const setDef = useMutation({ mutationFn: setDefaultCurrency, onSuccess: refresh, onError: onError('Could not set the default.') });
  const addCur = useMutation({
    mutationFn: () => addCurrency(code.trim(), cname.trim() || undefined),
    onSuccess: () => {
      setCode('');
      setCname('');
      refresh();
    },
    onError: onError('Could not add the currency.'),
  });
  const delCur = useMutation({ mutationFn: deleteCurrency, onSuccess: refresh, onError: onError('Could not remove the currency.') });
  const addRate = useMutation({
    mutationFn: () => addCurrencyRate({ from: rate.from, to: rate.to, rate: Number(rate.rate), validFrom: rate.validFrom, note: rate.note || undefined }),
    onSuccess: () => {
      setRate({ ...rate, rate: '', note: '' });
      refresh();
    },
    onError: onError('Could not add the rate.'),
  });
  const delRate = useMutation({ mutationFn: deleteCurrencyRate, onSuccess: refresh, onError: onError('Could not remove the rate.') });

  return (
    <>
      {error && (
        <Alert severity="error" onClose={() => setError(null)} sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      <Typography variant="h6" sx={{ mb: 1 }}>
        Currencies
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5, maxWidth: 760 }}>
        Each expense keeps the currency it was paid in. Totals are shown in the default currency (or the one picked on the
        report), converting each expense at the rate in force on its date. Expenses recorded before currencies existed count as
        the default currency until you edit them.
      </Typography>
      <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 2 }} flexWrap="wrap" useFlexGap>
        <TextField
          select
          size="small"
          label="Default currency"
          value={cur?.defaultCurrency ?? ''}
          onChange={(e) => setDef.mutate(e.target.value)}
          sx={{ width: 170 }}
        >
          {codes.map((c) => (
            <MenuItem key={c} value={c}>
              {c}
            </MenuItem>
          ))}
        </TextField>
        {(cur?.currencies ?? []).map((c) => (
          <Chip
            key={c.code}
            label={`${c.code}${c.name ? ` · ${c.name}` : ''}`}
            onDelete={c.code === cur?.defaultCurrency ? undefined : () => delCur.mutate(c.code)}
            variant={c.code === cur?.defaultCurrency ? 'filled' : 'outlined'}
          />
        ))}
      </Stack>
      <Stack component="form" direction="row" spacing={1} sx={{ mb: 4 }} onSubmit={(e) => { e.preventDefault(); addCur.mutate(); }}>
        <TextField size="small" label="Code" placeholder="GBP" value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} sx={{ width: 100 }} />
        <TextField size="small" label="Name" value={cname} onChange={(e) => setCname(e.target.value)} />
        <Button type="submit" startIcon={<Add />} disabled={!code.trim() || addCur.isPending}>
          Add currency
        </Button>
      </Stack>

      <Typography variant="h6" sx={{ mb: 1 }}>
        Exchange rates
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5, maxWidth: 760 }}>
        “1 FROM = rate TO, from this date on” — enter a new row when the rate changes; older expenses keep the rate of their
        own date. A rate works both ways, and through a third currency when there is no direct one.
      </Typography>
      <Stack component="form" direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap sx={{ mb: 2 }} onSubmit={(e) => { e.preventDefault(); addRate.mutate(); }}>
        <Typography>1</Typography>
        <TextField select size="small" value={rate.from} onChange={(e) => setRate({ ...rate, from: e.target.value })} sx={{ width: 100 }}>
          {codes.map((c) => (
            <MenuItem key={c} value={c}>
              {c}
            </MenuItem>
          ))}
        </TextField>
        <Typography>=</Typography>
        <TextField size="small" type="number" label="Rate" value={rate.rate} onChange={(e) => setRate({ ...rate, rate: e.target.value })} inputProps={{ step: 'any', min: 0 }} sx={{ width: 140 }} />
        <TextField select size="small" value={rate.to} onChange={(e) => setRate({ ...rate, to: e.target.value })} sx={{ width: 100 }}>
          {codes.map((c) => (
            <MenuItem key={c} value={c}>
              {c}
            </MenuItem>
          ))}
        </TextField>
        <TextField size="small" type="date" label="From" value={rate.validFrom} onChange={(e) => setRate({ ...rate, validFrom: e.target.value })} InputLabelProps={{ shrink: true }} sx={{ width: 160 }} />
        <TextField size="small" label="Note" value={rate.note} onChange={(e) => setRate({ ...rate, note: e.target.value })} />
        <Button type="submit" variant="contained" startIcon={<Add />} disabled={!(Number(rate.rate) > 0) || rate.from === rate.to || addRate.isPending}>
          Add rate
        </Button>
      </Stack>
      <Paper variant="outlined" sx={{ maxWidth: 720 }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Rate</TableCell>
              <TableCell>From date</TableCell>
              <TableCell>Note</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {rates.length === 0 && (
              <TableRow>
                <TableCell colSpan={4}>
                  <Typography variant="body2" color="text.secondary">
                    No rates yet — totals only count expenses already in the total's currency.
                  </Typography>
                </TableCell>
              </TableRow>
            )}
            {rates.map((r) => (
              <TableRow key={r.id} hover>
                <TableCell>
                  1 {r.from} = {r.rate.toLocaleString(undefined, { maximumFractionDigits: 6 })} {r.to}
                </TableCell>
                <TableCell>{new Date(r.validFrom + 'T00:00').toLocaleDateString()}</TableCell>
                <TableCell dir="auto">{r.note}</TableCell>
                <TableCell align="right">
                  <IconButton size="small" color="error" onClick={() => delRate.mutate(r.id)} aria-label="Remove rate">
                    <DeleteOutline fontSize="small" />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
    </>
  );
}
