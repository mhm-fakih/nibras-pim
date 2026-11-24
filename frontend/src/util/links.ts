import type React from 'react';

/** The app's base path ("/" or "/nibras/"), as the router's basename. */
const base = () => new URL(document.baseURI).pathname;

/** An in-app route ("/resources?select=5") as the address a browser tab can open. */
export const hrefOf = (to: string) => `${base()}${to.replace(/^\//, '')}`;

/** Ctrl, Cmd or Shift held, or not the main button: the browser's own new-tab/window click. */
export const isNewTabClick = (e: React.MouseEvent) =>
  e.ctrlKey || e.metaKey || e.shiftKey || e.altKey || e.button !== 0;

/**
 * Props that make any MUI element (ListItemButton, MenuItem, Link, Card…) a real link to an
 * in-app page: a plain click runs {@code onPlain} (usually a router navigate) in this tab, while
 * Ctrl/Cmd/Shift-click, middle-click and the context menu's "Open in new tab" work as on any link.
 */
export function linkProps(to: string, onPlain: () => void) {
  return {
    component: 'a' as const,
    href: hrefOf(to),
    onClick: (e: React.MouseEvent) => {
      if (isNewTabClick(e)) {
        e.stopPropagation(); // the browser opens it; nothing in this page reacts
        return;
      }
      e.preventDefault();
      onPlain();
    },
  };
}
