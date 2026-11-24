import { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { EntityRecord, updateEntity } from '../api/client';
import { ENTITY_BY_KEY } from '../config/entities';
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

/** Highlight mode for one record, shared by the read page and the details panel. */
export function useRecordHighlight(entity: string | undefined, id: number, record: EntityRecord | undefined) {
  const qc = useQueryClient();
  const [highlighting, setHighlighting] = useState(false);
  const fields = highlightFields(entity);

  // The details panel stays mounted as the selection moves; the mode belongs to one record.
  useEffect(() => setHighlighting(false), [entity, id]);

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
      return updateEntity(entity!, id, {
        ...record,
        [fields!.source]: result.source,
        [fields!.target]: tgt,
      });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['record', entity, id] });
      qc.invalidateQueries({ queryKey: ['list', entity] });
    },
  });

  return { fields, highlighting, setHighlighting, toggle };
}
