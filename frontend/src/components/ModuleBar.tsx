import { Box, Tooltip } from '@mui/material';

/**
 * A mini vertical bar showing a module's short code (e.g. "T") in the module's accent colour.
 * Rendered at the left of each list row.
 */
export function ModuleBar({
  code,
  color,
  title,
}: {
  code?: string;
  color?: string;
  title?: string;
}) {
  const bar = (
    <Box
      sx={{
        width: 16,
        minHeight: 26,
        alignSelf: 'stretch',
        flexShrink: 0,
        mr: 1,
        borderRadius: 0.5,
        bgcolor: color || '#9e9e9e',
        color: '#fff',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: 10,
        fontWeight: 700,
        lineHeight: 1,
        fontFamily: 'monospace',
      }}
    >
      {code || ''}
    </Box>
  );
  return title ? <Tooltip title={title}>{bar}</Tooltip> : bar;
}
