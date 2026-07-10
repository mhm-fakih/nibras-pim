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
  return { modules, enabled, isLoading: query.isLoading };
}
