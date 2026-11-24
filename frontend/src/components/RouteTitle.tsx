import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { ENTITY_BY_KEY } from '../config/entities';
import { setRouteTitle } from '../util/documentTitle';
import { TOOLS } from './SideNav';

/** Pages the side rail has no entry for. */
const OTHER_PAGES: Record<string, string> = {
  '/login': 'Sign in',
  '/search': 'Search',
  '/users': 'Users',
  '/permissions': 'Permissions',
  '/reports/shelf-map': 'Shelf map',
  '/reports/all-highlights': 'All highlights',
};

function titleOf(pathname: string): string | undefined {
  const exact = OTHER_PAGES[pathname] ?? TOOLS.find((t) => t.path === pathname)?.label;
  if (exact) return exact;
  const first = pathname.split('/')[1] ?? '';
  return ENTITY_BY_KEY[first]?.label ?? TOOLS.find((t) => t.path === `/${first}`)?.label;
}

/** Names the browser tab after the current route; a page can then name it more closely (useDocumentTitle). */
export function RouteTitle() {
  const { pathname } = useLocation();
  useEffect(() => setRouteTitle(titleOf(pathname)), [pathname]);
  return null;
}
