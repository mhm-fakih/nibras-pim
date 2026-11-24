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
import { FieldLabel, FieldView, isReadingField, OpenFolderButtons } from '../components/DetailPane';
import { AuthImage } from '../components/AuthImage';
import { isRtlText } from '../util/rtl';
import { useAuth } from '../hooks/useAuth';
import { useFeatureFlags } from '../hooks/useFeature';
import { HighlightableText, HighlightBar } from '../components/HighlightableText';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useRecordHighlight } from '../hooks/useRecordHighlight';
import { useReadingScale } from '../hooks/useReadingScale';
import { TextSizeSlider } from '../components/TextSizeSlider';

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

  const { data: record, isLoading } = useQuery({
    queryKey: ['record', entity, recordId],
    queryFn: () => getEntity(entity!, recordId),
    enabled: !!config && Number.isFinite(recordId),
  });

  const { fields, highlighting, setHighlighting, toggle } = useRecordHighlight(entity, recordId, record);
  const [readingScale, setReadingScale] = useReadingScale();
  useDocumentTitle(record ? record.displayTitle || `${config?.label} ${recordId}` : undefined);

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

      <Stack spacing={3}>
        {/* Honours the same Settings → Features switches as the form and the detail pane, so a
            hidden field doesn't come back the moment the record is opened for reading. */}
        {config.fields.filter((f) => featureOn(f.feature)).map((f) => {
          const isSource = !!fields && f.name === fields.source;
          const hasText = String(record[f.name] ?? '').trim() !== '';
          const reading = isReadingField(config.key, f.name);
          // The Highlight switch and the size slider sit right on top of the text they act on.
          const showHighlight = isSource && canWriteContent && hasText;
          const showSize = reading && hasText;
          const view = highlighting && isSource ? (
            <Box key={f.name}>
              <FieldLabel>{f.label}</FieldLabel>
              <HighlightableText
                source={String(record[f.name] ?? '')}
                disabled={toggle.isPending}
                onToggle={(start, end) => toggle.mutate({ start, end })}
                reading={reading ? readingScale : undefined}
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
              reading={reading ? readingScale : undefined}
            />
          );
          if (!showHighlight && !showSize) return view;
          return (
            <Box key={f.name}>
              <Stack direction="row" alignItems="center" flexWrap="wrap" useFlexGap columnGap={3} sx={{ mb: 1 }}>
                {showHighlight && (
                  <HighlightBar
                    highlighting={highlighting}
                    onChange={setHighlighting}
                    pending={toggle.isPending}
                    targetLabel={config.fields.find((t) => t.name === fields?.target)?.label}
                  />
                )}
                {showSize && <TextSizeSlider value={readingScale} onChange={setReadingScale} />}
              </Stack>
              {view}
            </Box>
          );
        })}
      </Stack>

      <RelatedRecordsPanel module={config.key} id={recordId} />

      <FulltextPanel module={config.key} id={recordId} />
    </Box>
  );
}

