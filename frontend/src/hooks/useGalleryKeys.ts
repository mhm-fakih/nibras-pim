import { useEffect } from 'react';

/**
 * Keyboard control for the one-item-at-a-time galleries.
 *
 * <p>Two jobs, and the second is the less obvious one. It binds the keys, and it stops the browser
 * doing its own thing with them: before this, Left and Right nudged the card's content sideways a
 * few pixels, because the arrows were scrolling the overflow container rather than moving between
 * cards. Every key this claims is `preventDefault`ed.
 *
 * <p>Up and Down are deliberately NOT claimed. A flash card never scrolls, but a resource's
 * highlights are a list of unknown length, and taking the vertical arrows would leave no way to
 * read down one. Sideways moves between items; vertical scrolls within one.
 */
export function useGalleryKeys({
  onPrev,
  onNext,
  onFirst,
  onLast,
  actions = {},
  enabled = true,
}: {
  onPrev: () => void;
  onNext: () => void;
  onFirst?: () => void;
  onLast?: () => void;
  /** Single-character shortcuts, keyed by lower-case letter. */
  actions?: Record<string, (() => void) | undefined>;
  enabled?: boolean;
}) {
  useEffect(() => {
    if (!enabled) return;

    const handler = (e: KeyboardEvent) => {
      // Never steal a keystroke meant for a field, and never shadow a browser shortcut.
      const el = e.target as HTMLElement | null;
      if (el && (el.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName))) return;
      if (e.ctrlKey || e.metaKey || e.altKey) return;

      const run = (fn?: () => void) => {
        if (!fn) return;
        e.preventDefault();
        fn();
      };

      switch (e.key) {
        case 'ArrowRight':
        case 'PageDown':
          return run(onNext);
        case 'ArrowLeft':
        case 'PageUp':
          return run(onPrev);
        case 'Home':
          return run(onFirst);
        case 'End':
          return run(onLast);
        default: {
          // Enter is spelled out so it can act as "the main action" alongside its letter.
          const key = e.key === 'Enter' ? 'enter' : e.key.toLowerCase();
          if (key.length === 1 || key === 'enter') run(actions[key]);
        }
      }
    };

    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onPrev, onNext, onFirst, onLast, actions, enabled]);
}
