import { Link } from '@mui/material';
import { BASE } from '../api/client';

/**
 * A record's name ("N231", "R88") as a link to its own page, opened in a new tab.
 *
 * A gallery is read one card at a time, and following a card to its record would otherwise mean
 * losing your place in the deck — hence the new tab, rather than an in-app navigation.
 *
 * The href is built from {@link BASE} (the servlet context, read from `<base href>`) rather than
 * from a bare "/read/…": the same WAR is deployed under several contexts, and a root-absolute link
 * lands on the wrong app under every one of them but "/".
 */
export function RecordRefLink({ module, id, label }: { module: string; id: number; label: string }) {
  return (
    <Link
      href={`${BASE}read/${module}/${id}`}
      target="_blank"
      rel="noopener"
      underline="hover"
      variant="subtitle2"
      fontWeight={700}
      // Stops the card's own arrow-key/click handling from firing when the link is used.
      onClick={(e) => e.stopPropagation()}
      title={`Open ${label} in a new tab`}
    >
      {label}
    </Link>
  );
}
