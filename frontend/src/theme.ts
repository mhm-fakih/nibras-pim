import { createTheme } from '@mui/material/styles';
// Registers MuiDateCalendar (and the other picker components) in the theme's `components` typing.
import type {} from '@mui/x-date-pickers/themeAugmentation';

export const theme = createTheme({
  palette: {
    mode: 'light',
    // Dark navy brand colour — serious tone (app name, buttons, icons, selection).
    primary: { main: '#133155', dark: '#0c2038', light: '#2b507f', contrastText: '#ffffff' },
    background: { default: '#faf9f8' },
  },
  typography: {
    // App-wide font stack. Latin text uses Segoe UI / system UI; Arabic text falls back to
    // "Noto Naskh Arabic" then "Segoe UI Arabic" — naskh faces that render Arabic (incl. the
    // mixed Arabic/Latin content on the Import notes page) clearly and with correct shaping.
    // See NOTES.md › Typography. "Noto Naskh Arabic" must be installed on the client to apply;
    // otherwise the browser/OS Arabic default is used.
    fontFamily:
      '"Segoe UI", system-ui, -apple-system, "Noto Naskh Arabic", "Segoe UI Arabic", sans-serif',
    fontSize: 13,
  },
  components: {
    MuiListItemButton: {
      styleOverrides: { root: { borderRadius: 6 } },
    },
    // Show ISO week numbers (Monday start, from the enGB locale) on every date/time picker calendar.
    MuiDateCalendar: {
      defaultProps: { displayWeekNumber: true },
    },
  },
});
