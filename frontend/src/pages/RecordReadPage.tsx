import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Stack,
  Typography,
} from '@mui/material';
import Edit from '@mui/icons-material/Edit';
import { BASE, coverUrl, getEntity, updateEntity } from '../api/client';
import { toggleChecklistItem } from '../util/checklist';
import { ENTITY_BY_KEY } from '../config/entities';
import { lookupStyle } from '../components/statusStyle';
import { RelatedRecordsPanel } from '../components/RelatedRecordsPanel';
import { FulltextPanel } from '../components/FulltextPanel';
import { FieldLabel, FieldView, OpenFolderButtons } from '../components/DetailPane';
import { AuthImage } from '../components/AuthImage';
import { isRtlText } from '../util/rtl';
import { useAuth } from '../hooks/useAuth';
import { useFeatureFlags } from '../hooks/useFeature';
import { HighlightableText } from '../components/HighlightableText';
import { appendHighlight, removeHighlight, toggleRange } from '../util/highlights';

/**
 * Which field carries the readable body and which collects highlighted sentences, per module.
 * Every module with a `mainHighlights` field highlights its `description`; resources are the
 * exception on both sides — the body worth highlighting is `fullText`, and the collector stays
 * `highlights`, which is the field the phone's own highlight push (mobilePushHighlight -> Book
 * .highlights) writes to. Pointing the web at `mainHighlights` for resources instead would split
 * the same book's highlights across two fields depending on which device made them.
 */
const HIGHLIGHT_OVERRIDES: Record<string, { source: string; target: string }> = {
  resources: { source: 'fullText', target: 'highlights' },
};

function highlightFields(entity: string | undefined): { source: string; target: string } | undefined {
  if (!entity) return undefined;
  const override = HIGHLIGHT_OVERRIDES[entity];
  if (override) return override;
  const cfg = ENTITY_BY_KEY[entity];
  if (!cfg) return undefined;
  const has = (n: string) => cfg.fields.some((f) => f.name === n);
  return has('mainHighlights') && has('description')
    ? { source: 'description', target: 'mainHighlights' }
    : undefined;
}

/**
 * A record's content on its own tab, narrow and reading-optimized — no list/nav chrome. Editing
 * still lives in the master-detail shell, so "Edit" hands the record back to it via
 * `?select=<id>&edit=1` rather than duplicating the form here.
 */
export function RecordReadPage() {
  const { entity, id } = useParams();
  const qc = useQueryClient();
  const { canWriteContent } = useAuth();
  const featureOn = useFeatureFlags();
  const config = entity ? ENTITY_BY_KEY[entity] : undefined;
  const recordId = Number(id);

  const [highlighting, setHighlighting] = useState(false);

  const { data: record, isLoading } = useQuery({
    queryKey: ['record', entity, recordId],
    queryFn: () => getEntity(entity!, recordId),
    enabled: !!config && Number.isFinite(recordId),
  });

  const fields = highlightFields(entity);

  // Toggling a highlight rewrites two fields at once: the body (== markers) and the highlights
  // collector. Sent as a full-record PUT — the generic endpoint applies every DTO field, so a
  // partial body would null out everything else.
  const toggle = useMutation({
    // From the record's CURRENT value, by range: the sentence's flags flip and the markers are
    // rewritten from them (util/highlights), so an older clause highlight inside the sentence is
    // absorbed rather than nested. The collector loses every highlight that disappeared (that
    // clause) and gains every one that appeared (the sentence).
    mutationFn: async ({ start, end }: { start: number; end: number }) => {
      const src = String(record?.[fields!.source] ?? '');
      const result = toggleRange(src, start, end);
      if (!result) return; // refused: it would have changed the text itself
      let tgt = (record?.[fields!.target] as string | null | undefined) ?? '';
      for (const t of result.removed) tgt = removeHighlight(tgt, t);
      for (const t of result.added) tgt = appendHighlight(tgt, t);
      return updateEntity(entity!, recordId, {
        ...record,
        [fields!.source]: result.source,
        [fields!.target]: tgt,
      });
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['record', entity, recordId] }),
  });

  /**
   * Tick a `- [ ]` item — the same shape as the highlight toggle above, and for the same reason:
   * a full-record PUT, rewritten from the record's current value rather than from the DOM, so two
   * quick ticks can't race each other into writing the same stale text twice.
   */
  const toggleItem = useMutation({
    mutationFn: ({ field, index }: { field: string; index: number }) => {
      const source = String(record?.[field] ?? '');
      return updateEntity(entity!, recordId, {
        ...record,
        [field]: toggleChecklistItem(source, index),
      });
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['record', entity, recordId] }),
  });

  if (!config || !Number.isFinite(recordId)) {
    return (
      <Box sx={{ p: 4 }}>
        <Typography color="error">Unknown record.</Typography>
      </Box>
    );
  }

  if (isLoading || !record) {
    return (
      <Box sx={{ p: 6, textAlign: 'center' }}>
        <CircularProgress size={28} />
      </Box>
    );
  }

  return (
    <Box sx={{ maxWidth: 680, mx: 'auto', px: 3, py: 4 }}>
      {record.hasCover && (
        <AuthImage
          url={coverUrl(config.key, recordId)}
          sx={{ maxWidth: '100%', maxHeight: 320, objectFit: 'contain', borderRadius: 1, mb: 3, display: 'block' }}
        />
      )}

      <Typography
        variant="h4"
        dir={isRtlText(record.displayTitle) ? 'rtl' : 'ltr'}
        align={isRtlText(record.displayTitle) ? 'right' : 'left'}
        sx={{ fontWeight: 700, mb: 2, lineHeight: 1.25 }}
      >
        {record.displayTitle || '(untitled)'}
      </Typography>

      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mb: 3 }}>
        <Chip size="small" variant="outlined" label={`${config.label} · ID ${record.id}`} sx={{ fontFamily: 'monospace' }} />
        {record.status && <Chip size="small" label={record.status.name} sx={lookupStyle(record.status)} />}
        {record.type && <Chip size="small" label={record.type.name} sx={lookupStyle(record.type)} />}
        {record.context && <Chip size="small" label={record.context.name} sx={lookupStyle(record.context)} />}
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
        {record.book && <Chip size="small" label={`📖 ${record.book.name}`} />}
        <Chip size="small" variant="outlined" label={`Created ${new Date(record.dateCreated).toLocaleString()}`} />
      </Stack>

      {Array.isArray(record.tags) && record.tags.length > 0 && (
        <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap sx={{ mb: 3 }}>
          {record.tags.map((t) => (
            <Chip key={t.id} size="small" variant="outlined" label={`#${t.name}`} />
          ))}
        </Stack>
      )}

      {/* Actions: editing happens back in the master-detail shell; the repo buttons are the same
          component the detail pane uses. */}
      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap sx={{ mb: 2 }}>
        {canWriteContent && (
          <Button
            size="small"
            variant="outlined"
            startIcon={<Edit />}
            onClick={() => {
              window.location.assign(`${BASE}${config.key}?select=${recordId}&edit=1`);
            }}
          >
            Edit
          </Button>
        )}
        <OpenFolderButtons module={config.key} id={recordId} />
      </Stack>

      {fields && canWriteContent && String(record[fields.source] ?? '').trim() && (
        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap sx={{ mb: 1 }}>
          <Button
            size="small"
            variant={highlighting ? 'contained' : 'outlined'}
            color="warning"
            onClick={() => setHighlighting((h) => !h)}
          >
            {highlighting ? 'Done highlighting' : 'Highlight'}
          </Button>
          {highlighting && (
            <>
              <Typography variant="caption" color="text.secondary">
                Click a sentence to highlight it — it also goes to “{config.fields.find((f) => f.name === fields.target)?.label ?? fields.target}”.
              </Typography>
            </>
          )}
          {toggle.isPending && <CircularProgress size={16} />}
        </Stack>
      )}

      <Stack spacing={3}>
        {/* Honours the same Settings → Features switches as the form and the detail pane, so a
            hidden field doesn't come back the moment the record is opened for reading. */}
        {config.fields.filter((f) => featureOn(f.feature)).map((f) =>
          highlighting && fields && f.name === fields.source ? (
            <Box key={f.name}>
              <FieldLabel>{f.label}</FieldLabel>
              <HighlightableText
                source={String(record[f.name] ?? '')}
                disabled={toggle.isPending}
                onToggle={(start, end) => toggle.mutate({ start, end })}
              />
              <Divider sx={{ mt: 2 }} />
            </Box>
          ) : (
            <FieldView
              key={f.name}
              label={f.label}
              type={f.type}
              value={record[f.name]}
              record={record}
              onToggleChecklistItem={
                f.type === 'markdown' || f.type === 'richtext'
                  ? (index) => toggleItem.mutate({ field: f.name, index })
                  : undefined
              }
            />
          ),
        )}
      </Stack>

      <RelatedRecordsPanel module={config.key} id={recordId} />

      <FulltextPanel module={config.key} id={recordId} />
    </Box>
  );
}
