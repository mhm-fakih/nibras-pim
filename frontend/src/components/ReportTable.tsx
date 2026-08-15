import { Box, Chip, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material';
import CheckIcon from '@mui/icons-material/Check';
import { EntityRecord, Lookup, coverUrl } from '../api/client';
import { ENTITY_BY_KEY, FieldType } from '../config/entities';
import { AuthImage } from './AuthImage';
import { lookupStyle } from './statusStyle';

/**
 * The one column that isn't a field: it renders the record's cover image rather than any property
 * of the DTO. Named here so the builder, the table and the backend all mean the same thing by it.
 */
export const COVER_COLUMN = 'cover';

/** How a column is drawn. Mostly the field's own type; the pseudo-columns bring their own. */
export type ColumnKind = FieldType | 'cover' | 'id' | 'created';

export interface ReportColumnDef {
  name: string;
  label: string;
  kind: ColumnKind;
}

/** Lookup-valued fields — the ones that arrive as `{id, name, code}` and render as a named chip. */
const LOOKUP_KINDS: ColumnKind[] = ['status', 'type', 'department', 'course', 'book', 'goal'];

/** Long-form fields: shown as a one-line excerpt, since a table cell can't hold an essay. */
const LONG_KINDS: ColumnKind[] = ['textarea', 'richtext', 'markdown'];

/**
 * Every column a report over `moduleKey` can show: the module's own fields, plus the handful that
 * exist on every record and so are never listed in `entities.ts`.
 */
export function reportColumns(moduleKey: string): ReportColumnDef[] {
  const config = ENTITY_BY_KEY[moduleKey];
  return [
    { name: COVER_COLUMN, label: 'Cover image', kind: 'cover' },
    { name: 'id', label: 'Id', kind: 'id' },
    { name: 'displayTitle', label: 'Title (record heading)', kind: 'text' },
    ...(config?.fields ?? []).map((f) => ({ name: f.name, label: f.label, kind: f.type as ColumnKind })),
    { name: 'dateCreated', label: 'Created', kind: 'created' as ColumnKind },
    { name: 'lastUpdated', label: 'Updated', kind: 'created' as ColumnKind },
  ];
}

/**
 * The subset of columns a report can be sorted by.
 *
 * <p>Sorting happens in the database, so it only works on things that are actually a column there:
 * an image isn't, and neither is a tag list (many rows per record — "sorted by tags" has no single
 * meaning). Offering them and letting the query fail would be a worse answer than not offering them.
 */
export function sortableColumns(moduleKey: string): ReportColumnDef[] {
  return reportColumns(moduleKey).filter((c) => c.kind !== 'cover' && c.kind !== 'tags');
}

export function columnLabel(moduleKey: string, name: string): string {
  return reportColumns(moduleKey).find((c) => c.name === name)?.label ?? name;
}

/** First line of a long text field, HTML stripped, for a table cell. */
function excerpt(value: string, max = 90): string {
  const plain = value
    .replace(/<br\s*\/?>/gi, ' ')
    .replace(/<[^>]*>/g, '')
    .replace(/\s+/g, ' ')
    .trim();
  return plain.length > max ? `${plain.slice(0, max)}…` : plain;
}

function isLookup(value: unknown): value is Lookup {
  return typeof value === 'object' && value !== null && 'name' in (value as Record<string, unknown>);
}

/**
 * One cell.
 *
 * <p>This is where "Article, not art" is actually delivered — and it costs nothing, because the
 * record's `type` already arrived as `{id, name: 'Article', code: 'art'}`. Showing the name is a
 * matter of reading the right property; the code is simply never displayed.
 */
export function ReportCell({
  column,
  record,
  moduleKey,
}: {
  column: ReportColumnDef;
  record: EntityRecord;
  moduleKey: string;
}) {
  const value = record[column.name];

  if (column.kind === 'cover') {
    if (!record.hasCover) {
      return <Box sx={{ width: 56, height: 56 }} />;
    }
    return (
      <AuthImage
        url={coverUrl(moduleKey, record.id)}
        alt=""
        sx={{ width: 56, height: 56, objectFit: 'cover', borderRadius: 1, display: 'block' }}
      />
    );
  }

  if (value == null || value === '') {
    return <Typography variant="body2" color="text.disabled">—</Typography>;
  }

  if (LOOKUP_KINDS.includes(column.kind) && isLookup(value)) {
    return <Chip size="small" label={value.name} sx={{ ...lookupStyle(value), height: 22 }} />;
  }

  if (column.kind === 'tags' && Array.isArray(value)) {
    return (
      <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
        {(value as { id: number; name: string }[]).map((t) => (
          <Chip key={t.id} size="small" variant="outlined" label={t.name} sx={{ height: 22 }} />
        ))}
      </Stack>
    );
  }

  if (column.kind === 'switch') {
    return value ? <CheckIcon fontSize="small" color="success" /> : <Typography variant="body2" color="text.disabled">—</Typography>;
  }

  if (column.kind === 'datetime' || column.kind === 'created') {
    const d = new Date(String(value));
    return (
      <Typography variant="body2" sx={{ whiteSpace: 'nowrap' }}>
        {Number.isNaN(d.getTime()) ? String(value) : d.toLocaleDateString()}
      </Typography>
    );
  }

  if (column.kind === 'url') {
    return (
      <Typography
        variant="body2"
        component="a"
        href={String(value)}
        target="_blank"
        rel="noopener noreferrer"
        sx={{ wordBreak: 'break-all' }}
      >
        {excerpt(String(value), 40)}
      </Typography>
    );
  }

  if (LONG_KINDS.includes(column.kind)) {
    return (
      <Typography variant="body2" color="text.secondary">
        {excerpt(String(value))}
      </Typography>
    );
  }

  return <Typography variant="body2">{String(value)}</Typography>;
}

/** The report's rows. Used by the builder's live preview and by the report view alike. */
export function ReportTable({
  moduleKey,
  columns,
  rows,
  onOpen,
}: {
  moduleKey: string;
  columns: string[];
  rows: EntityRecord[];
  onOpen?: (id: number) => void;
}) {
  const defs = reportColumns(moduleKey);
  // A column that no longer exists (field renamed since the report was saved) is dropped rather
  // than drawn as a blank column with a raw property name in its header.
  const shown = columns.map((name) => defs.find((d) => d.name === name)).filter(Boolean) as ReportColumnDef[];

  if (shown.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>
        No columns chosen yet.
      </Typography>
    );
  }

  return (
    <Box sx={{ overflowX: 'auto' }}>
      <Table size="small" sx={{ minWidth: 480 }}>
        <TableHead>
          <TableRow>
            {shown.map((c) => (
              <TableCell key={c.name} sx={{ fontWeight: 700, whiteSpace: 'nowrap' }}>
                {c.label}
              </TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((r) => (
            <TableRow
              key={r.id}
              hover={Boolean(onOpen)}
              onClick={onOpen ? () => onOpen(r.id) : undefined}
              sx={onOpen ? { cursor: 'pointer' } : undefined}
            >
              {shown.map((c) => (
                <TableCell key={c.name} sx={{ verticalAlign: 'top', maxWidth: 320 }}>
                  <ReportCell column={c} record={r} moduleKey={moduleKey} />
                </TableCell>
              ))}
            </TableRow>
          ))}
          {rows.length === 0 && (
            <TableRow>
              <TableCell colSpan={shown.length}>
                <Typography variant="body2" color="text.secondary">
                  No records match this report.
                </Typography>
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
    </Box>
  );
}
