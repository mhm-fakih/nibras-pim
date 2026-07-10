import { useMemo } from 'react';
import { Box } from '@mui/material';
import MarkdownIt from 'markdown-it';
import markdownItMark from 'markdown-it-mark';

// html:true keeps legacy <br>/HTML working during the move to Markdown; breaks:true turns
// plain newlines into line breaks; markdown-it-mark renders ==text== as <mark>.
const md = new MarkdownIt({ html: true, linkify: true, breaks: true }).use(markdownItMark);

/** Renders a text field as Markdown (with ==highlight== support). */
export function MarkdownView({ value }: { value: string }) {
  const html = useMemo(() => md.render(value ?? ''), [value]);
  return (
    <Box
      dir="auto"
      sx={{
        lineHeight: 1.7,
        '& img': { maxWidth: '100%' },
        '& mark': { backgroundColor: '#fff3a3', px: '2px', borderRadius: '2px' },
        '& pre': { bgcolor: '#f5f5f5', p: 1.5, borderRadius: 1, overflow: 'auto' },
        '& code': { bgcolor: '#f5f5f5', px: '4px', borderRadius: '3px' },
        '& blockquote': { borderLeft: '3px solid #ccc', pl: 1.5, color: 'text.secondary', my: 1 },
        '& :first-of-type': { mt: 0 },
      }}
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
}
