import {
  Box,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { MarkdownView } from './MarkdownView';

/** Syntax/result pairs. Results render through the app's real MarkdownView so the preview is exact. */
const ROWS: { syntax: string; note?: string }[] = [
  { syntax: '# Heading 1' },
  { syntax: '## Heading 2' },
  { syntax: '**bold**' },
  { syntax: '_italic_' },
  { syntax: '~~strikethrough~~' },
  { syntax: '==highlight==', note: 'Nibras extension' },
  { syntax: '`inline code`' },
  { syntax: '- bullet item\n- second item' },
  { syntax: '1. numbered\n2. second' },
  { syntax: '> a blockquote' },
  { syntax: '[a link](https://example.com)' },
  { syntax: '![alt text](image-url.png)', note: 'image' },
  { syntax: '| A | B |\n| - | - |\n| 1 | 2 |', note: 'table' },
  { syntax: '```\ncode block\n```' },
  { syntax: '---', note: 'horizontal rule' },
  { syntax: 'line one\nline two', note: 'a single newline becomes a line break' },
];

/** A two-column Markdown reference: the literal syntax on the left, its live rendering on the right. */
export function MarkdownCheatsheet() {
  return (
    <TableContainer component={Paper} variant="outlined">
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell sx={{ width: '45%', fontWeight: 700 }}>You type</TableCell>
            <TableCell sx={{ fontWeight: 700 }}>You get</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {ROWS.map((r) => (
            <TableRow key={r.syntax} sx={{ verticalAlign: 'top' }}>
              <TableCell>
                <Box
                  component="pre"
                  sx={{
                    m: 0,
                    fontFamily: 'monospace',
                    fontSize: 13,
                    whiteSpace: 'pre-wrap',
                    color: 'text.primary',
                  }}
                >
                  {r.syntax}
                </Box>
                {r.note && (
                  <Typography variant="caption" color="text.secondary">
                    {r.note}
                  </Typography>
                )}
              </TableCell>
              <TableCell>
                <MarkdownView value={r.syntax} />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
