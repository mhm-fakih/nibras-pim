import { useQuery } from '@tanstack/react-query';
import { AppModule, listModules } from '../api/client';
import { ENTITIES } from '../config/entities';

/** Fallback module list (all enabled) used until the backend responds. */
const FALLBACK: AppModule[] = ENTITIES.map((e, i) => ({
  moduleKey: e.key,
  label: e.label,
  enabled: true,
  sortOrder: i + 1,
}));

/**
 * Backend-driven module config (enabled flag, code, order). The field metadata still
 * lives in config/entities.ts; this only controls visibility/order/codes.
 */
export function useModules() {
  const query = useQuery({ queryKey: ['modules'], queryFn: listModules, staleTime: 60_000 });
  const modules = (query.data ?? FALLBACK)
    .slice()
    .sort((a, b) => a.sortOrder - b.sortOrder);
  const enabled = modules.filter((m) => m.enabled);
  /**
   * Is this module switched on? A module the backend does not list at all counts as off, so a
   * feature gated on this stays hidden on databases whose `app_module` predates it rather than
   * appearing half-wired. Used for UI that a module owns but that lives on *other* modules' pages
   * (contact tagging on a task, say) — the nav and the settings list work off `enabled` directly.
   */
  const isEnabled = (key: string) => enabled.some((m) => m.moduleKey === key);
  return { modules, enabled, isEnabled, isLoading: query.isLoading };
}
