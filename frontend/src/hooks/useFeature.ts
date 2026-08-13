import { useQuery } from '@tanstack/react-query';
import { getSettingsMulti } from '../api/client';
import { FEATURE_SETTING_KEYS, featureEnabled } from '../config/pages';

/**
 * Is a Settings → Features toggle on? Defaults to ON while the settings are still loading, which
 * matches the stored rule ("enabled unless explicitly false") and keeps the UI from flickering a
 * control away and back on every page load.
 *
 * Shares SideNav's `['feature-settings']` query rather than fetching again — react-query dedupes on
 * the key, so every consumer gets one request and they all invalidate together when Settings saves.
 */
export function useFeature(key: string): boolean {
  const { data } = useQuery({
    queryKey: ['feature-settings'],
    queryFn: () => getSettingsMulti(FEATURE_SETTING_KEYS),
    staleTime: 60_000,
  });
  return featureEnabled(data, key);
}

/**
 * The same answer for a key not known until render — a field's `feature`, say, where calling
 * [useFeature] per field would mean a hook count that changes with the config.
 *
 * The returned predicate treats an absent key as "on", which is what makes it safe to ask about a
 * field that names no feature at all.
 */
export function useFeatureFlags(): (key?: string) => boolean {
  const { data } = useQuery({
    queryKey: ['feature-settings'],
    queryFn: () => getSettingsMulti(FEATURE_SETTING_KEYS),
    staleTime: 60_000,
  });
  return (key?: string) => (key == null ? true : featureEnabled(data, key));
}
