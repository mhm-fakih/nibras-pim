import { createContext, useContext } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getSetting } from '../api/client';
import { parseHour12 } from '../util/clock';

/** Setting key: 'true' (default) = 12-hour times on the calendars, 'false' = 24-hour. */
export const HOUR12_SETTING = 'calendar.hour12';

/**
 * Set by a page that already knows the answer without asking the server — the public dashboard,
 * which gets it in its snapshot and must not make an authenticated request (a 401 there would
 * send a wall-mounted screen to the login page).
 */
export const Hour12Context = createContext<boolean | null>(null);

/** Whether calendars show 12-hour times. */
export function useHour12(): boolean {
  const given = useContext(Hour12Context);
  const { data } = useQuery({
    queryKey: ['setting', HOUR12_SETTING],
    queryFn: () => getSetting(HOUR12_SETTING, 'true'),
    enabled: given === null,
    staleTime: 5 * 60 * 1000,
  });
  return given ?? parseHour12(data);
}
