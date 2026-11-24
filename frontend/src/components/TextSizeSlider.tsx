import { Slider, Stack, Tooltip, Typography } from '@mui/material';
import FormatSize from '@mui/icons-material/FormatSize';
import { READING_SCALE_DEFAULT, READING_SCALE_MAX, READING_SCALE_MIN } from '../hooks/useReadingScale';

/** The full text's font size, from small to large; double-click the icon for the default. */
export function TextSizeSlider({ value, onChange }: { value: number; onChange: (v: number) => void }) {
  return (
    <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 200 }}>
      <Tooltip title="Text size — double-click for the default">
        <FormatSize
          fontSize="small"
          color="action"
          onDoubleClick={() => onChange(READING_SCALE_DEFAULT)}
          sx={{ cursor: 'pointer' }}
        />
      </Tooltip>
      <Slider
        size="small"
        min={READING_SCALE_MIN}
        max={READING_SCALE_MAX}
        step={0.05}
        value={value}
        onChange={(_, v) => onChange(v as number)}
        valueLabelDisplay="auto"
        valueLabelFormat={(v) => `${Math.round(v * 100)}%`}
        aria-label="Text size"
        sx={{ width: 160 }}
      />
      <Typography variant="caption" color="text.secondary" sx={{ minWidth: 36 }}>
        {Math.round(value * 100)}%
      </Typography>
    </Stack>
  );
}
