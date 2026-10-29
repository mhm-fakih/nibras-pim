import {
  MutableRefObject,
  ReactNode,
  createContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { useNavigate } from 'react-router-dom';
import { GO_TO } from '../config/shortcuts';
import { ShortcutsHelp } from './ShortcutsHelp';

/**
 * Lets other components (AppShell) coordinate with the global shortcuts:
 * - `gPending`: a `g` chord is mid-flight, so AppShell shouldn't also act on the next key.
 * - `recordSelected`: a record is selected, so `?` is yielded to AppShell's status quick-edit
 *   instead of toggling the help overlay.
 */
export const ShortcutContext = createContext<{
  gPending: MutableRefObject<boolean>;
  recordSelected: MutableRefObject<boolean>;
} | null>(null);

/** True when the keyboard event targets an editable field (so we don't hijack typing). */
function isTyping(e: KeyboardEvent): boolean {
  const t = e.target as HTMLElement | null;
  if (!t) return false;
  const tag = t.tagName;
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || t.isContentEditable;
}

/**
 * App-wide keyboard shortcuts: Shift+? opens the help overlay, `/` focuses the global search, and
 * `g` then a letter navigates to a module/tool. Contextual list/record shortcuts live in AppShell.
 * Mounted once inside the router.
 */
export function ShortcutsProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [helpOpen, setHelpOpen] = useState(false);
  const pendingG = useRef(false);
  const recordSelected = useRef(false);
  const gTimer = useRef<ReturnType<typeof setTimeout>>();
  const ctx = useMemo(() => ({ gPending: pendingG, recordSelected }), []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        pendingG.current = false;
        if (helpOpen) setHelpOpen(false);
        return;
      }
      if (isTyping(e) || e.ctrlKey || e.metaKey || e.altKey) return;

      // `g` then a letter -> navigate.
      if (pendingG.current) {
        const target = GO_TO.find((g) => g.key === e.key.toLowerCase());
        pendingG.current = false;
        clearTimeout(gTimer.current);
        if (target) {
          e.preventDefault();
          navigate(target.path);
          return;
        }
      }

      if (e.key === '?') {
        // With a record selected, `?` is AppShell's status quick-edit — don't also toggle help.
        if (recordSelected.current) return;
        e.preventDefault();
        setHelpOpen((o) => !o);
      } else if (e.key === '/') {
        e.preventDefault();
        // The search form's words field, or the search page itself from anywhere else.
        const field = document.getElementById('global-search');
        if (field) field.focus();
        else navigate('/search');
      } else if (e.key === 'g') {
        pendingG.current = true;
        clearTimeout(gTimer.current);
        gTimer.current = setTimeout(() => (pendingG.current = false), 1500);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [helpOpen, navigate]);

  return (
    <ShortcutContext.Provider value={ctx}>
      {children}
      <ShortcutsHelp open={helpOpen} onClose={() => setHelpOpen(false)} />
    </ShortcutContext.Provider>
  );
}
