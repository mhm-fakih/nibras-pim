import { useEffect } from 'react';
import { pushPageTitle } from '../util/documentTitle';

/** Names the browser tab after what this page shows. Undefined (still loading) leaves the route's name. */
export function useDocumentTitle(title: string | undefined) {
  useEffect(() => (title ? pushPageTitle(title) : undefined), [title]);
}
