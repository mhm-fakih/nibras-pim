import type { EntityRecord, Lookup } from '../api/client';

/** "Study › Cabinet A › Shelf 2" for a resource, from whatever levels it has; '' when unplaced. */
export function placePath(record: EntityRecord | null | undefined): string {
  if (!record) return '';
  return [record.storageRoom, record.storageCabinet, record.storageShelf]
    .map((l) => (l as Lookup | null | undefined)?.name)
    .filter(Boolean)
    .join(' › ');
}

/** The three ids a form holds for a resource's place. */
export interface PlaceValue {
  room: number | '';
  cabinet: number | '';
  shelf: number | '';
}
