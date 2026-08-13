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
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
import Edit from '@mui/icons-material/Edit';
import { BASE, coverUrl, getEntity, updateEntity } from '../api/client';
import { ENTITY_BY_KEY } from '../config/entities';
import { lookupStyle } from '../components/statusStyle';
import { RelatedRecordsPanel } from '../components/RelatedRecordsPanel';
import { FulltextPanel } from '../components/FulltextPanel';
import { FieldView, OpenFolderButtons } from '../components/DetailPane';
import { AuthImage } from '../components/AuthImage';
import { isRtlText } from '../util/rtl';
import { useAuth } from '../hooks/useAuth';
import { useFeatureFlags } from '../hooks/useFeature';
import {
  HighlightMode,
  HighlightableText,
  addMarker,
  appendHighlight,
  removeHighlight,
  removeMarker,
} from '../components/HighlightableText';

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

/** Highlight granularity is remembered PER RECORD — a dense article wants clauses while a short
 *  note wants whole sentences, so a single global setting would be wrong for one of them. */
const modeKey = (entity: string, id: number) => `nibras.read.highlightMode.${entity}.${id}`;

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

  const [mode, setMode] = useState<HighlightMode>(
    () =>
      (entity && Number.isFinite(recordId)
        ? (localStorage.getItem(modeKey(entity, recordId)) as HighlightMode | null)
        : null) || 'sentence',
  );
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
    mutationFn: ({ text, wasMarked }: { text: string; wasMarked: boolean }) => {
      const src = String(record?.[fields!.source] ?? '');
      const tgt = record?.[fields!.target] as string | null | undefined;
      return updateEntity(entity!, recordId, {
        ...record,
        [fields!.source]: wasMarked ? removeMarker(src, text) : addMarker(src, text),
        [fields!.target]: wasMarked ? removeHighlight(tgt, text) : appendHighlight(tgt, text),
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
              <ToggleButtonGroup
                size="small"
                exclusive
                value={mode}
                onChange={(_, v: HighlightMode | null) => {
                  if (v) {
                    setMode(v);
                    localStorage.setItem(modeKey(entity!, recordId), v);
                  }
                }}
              >
                <Tooltip title="Whole grammatical sentences — same split the mobile app uses">
                  <ToggleButton value="sentence">Sentence</ToggleButton>
                </Tooltip>
                <Tooltip title="Also splits at ، ؛ , ; : — for articles with very long sentences">
                  <ToggleButton value="clause">Clause</ToggleButton>
                </Tooltip>
              </ToggleButtonGroup>
              <Typography variant="caption" color="text.secondary">
                Click text to highlight — it also goes to “{config.fields.find((f) => f.name === fields.target)?.label ?? fields.target}”.
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
              <Typography variant="overline" color="text.secondary">
                {f.label}
              </Typography>
              <HighlightableText
                source={String(record[f.name] ?? '')}
                mode={mode}
                disabled={toggle.isPending}
                onToggle={(text, wasMarked) => toggle.mutate({ text, wasMarked })}
              />
              <Divider sx={{ mt: 2 }} />
            </Box>
          ) : (
            <FieldView key={f.name} label={f.label} type={f.type} value={record[f.name]} record={record} />
          ),
        )}
      </Stack>

      <RelatedRecordsPanel module={config.key} id={recordId} />

      <FulltextPanel module={config.key} id={recordId} />
    </Box>
  );
}
