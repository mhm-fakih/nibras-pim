import {
  Box,
  Chip,
  Dialog,
  DialogContent,
  DialogTitle,
  Grid,
  Stack,
  Typography,
} from '@mui/material';
import { GO_TO, SHORTCUTS } from '../config/shortcuts';

/** The Shift+? keyboard-shortcuts reference overlay. */
export function ShortcutsHelp({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Keyboard shortcuts</DialogTitle>
      <DialogContent dividers>
        <Grid container spacing={3}>
          {SHORTCUTS.map((group) => (
            <Grid item xs={12} sm={6} key={group.title}>
              <Typography variant="overline" color="text.secondary">
                {group.title}
              </Typography>
              <Stack spacing={0.75} sx={{ mt: 0.5 }}>
                {group.items.map((s) => (
                  <Row key={s.keys} keys={s.keys} desc={s.desc} />
                ))}
              </Stack>
            </Grid>
          ))}
          <Grid item xs={12}>
            <Typography variant="overline" color="text.secondary">
              Go to (press g, then…)
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75, mt: 0.5 }}>
              {GO_TO.map((g) => (
                <Chip
                  key={g.key}
                  size="small"
                  label={
                    <span>
                      <b>g {g.key}</b> · {g.label}
                    </span>
                  }
                  variant="outlined"
                />
              ))}
            </Box>
          </Grid>
        </Grid>
      </DialogContent>
    </Dialog>
  );
}

function Row({ keys, desc }: { keys: string; desc: string }) {
  return (
    <Stack direction="row" spacing={1.5} alignItems="center">
      <Box
        component="kbd"
        sx={{
          fontFamily: 'monospace',
          fontSize: 12,
          px: 0.75,
          py: 0.25,
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 0.75,
          bgcolor: '#f5f5f5',
          whiteSpace: 'nowrap',
          minWidth: 96,
          textAlign: 'center',
        }}
      >
        {keys}
      </Box>
      <Typography variant="body2">{desc}</Typography>
    </Stack>
  );
}
