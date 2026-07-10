import { useEffect } from 'react';
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import { Box, Divider, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material';
import FormatBold from '@mui/icons-material/FormatBold';
import FormatItalic from '@mui/icons-material/FormatItalic';
import FormatListBulleted from '@mui/icons-material/FormatListBulleted';
import FormatListNumbered from '@mui/icons-material/FormatListNumbered';
import Title from '@mui/icons-material/Title';

interface Props {
  label: string;
  value: string;
  onChange: (html: string) => void;
}

export function RichTextEditor({ label, value, onChange }: Props) {
  const editor = useEditor({
    extensions: [StarterKit],
    content: value || '',
    onUpdate: ({ editor }) => onChange(editor.getHTML()),
  });

  // Keep editor in sync when switching records.
  useEffect(() => {
    if (editor && value !== editor.getHTML()) {
      editor.commands.setContent(value || '', false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editor, value]);

  if (!editor) return null;

  return (
    <Box>
      <Typography variant="overline" color="text.secondary">
        {label}
      </Typography>
      <Box sx={{ border: 1, borderColor: 'divider', borderRadius: 1 }}>
        <ToggleButtonGroup size="small" sx={{ p: 0.5 }}>
          <ToggleButton
            value="bold"
            selected={editor.isActive('bold')}
            onClick={() => editor.chain().focus().toggleBold().run()}
          >
            <FormatBold fontSize="small" />
          </ToggleButton>
          <ToggleButton
            value="italic"
            selected={editor.isActive('italic')}
            onClick={() => editor.chain().focus().toggleItalic().run()}
          >
            <FormatItalic fontSize="small" />
          </ToggleButton>
          <ToggleButton
            value="h2"
            selected={editor.isActive('heading', { level: 2 })}
            onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}
          >
            <Title fontSize="small" />
          </ToggleButton>
          <ToggleButton
            value="ul"
            selected={editor.isActive('bulletList')}
            onClick={() => editor.chain().focus().toggleBulletList().run()}
          >
            <FormatListBulleted fontSize="small" />
          </ToggleButton>
          <ToggleButton
            value="ol"
            selected={editor.isActive('orderedList')}
            onClick={() => editor.chain().focus().toggleOrderedList().run()}
          >
            <FormatListNumbered fontSize="small" />
          </ToggleButton>
        </ToggleButtonGroup>
        <Divider />
        <Box
          dir="auto"
          sx={{
            p: 1.5,
            minHeight: 140,
            '& .ProseMirror': { outline: 'none', minHeight: 120, lineHeight: 1.7 },
          }}
        >
          <EditorContent editor={editor} />
        </Box>
      </Box>
    </Box>
  );
}
