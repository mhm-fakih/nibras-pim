import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Collapse,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { Link } from 'react-router-dom';
import NoteAdd from '@mui/icons-material/NoteAdd';
import { CourseOption, EntityRecord, createEntity, listCourses } from '../api/client';
import { dirOf } from '../util/rtl';

/**
 * Write a note ABOUT this resource, from the resource's own page.
 *
 * Distinct from the append bar above it, and deliberately so: that one adds text to the resource's
 * own `notes` column, which is a scratch field on the resource. This creates a real record in the
 * notes module, with its own course, priority and lifecycle, linked back through `index_card.book_id`.
 * Reading a book produces notes that outlive the book's own margin.
 *
 * The course is pre-filled from the resource's, because a note about a book almost always belongs to
 * the same course as the book. It stays editable — "almost always" is not always.
 */
export function AddNoteToResource({ record, module }: { record: EntityRecord; module: string }) {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [summary, setSummary] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState<number | ''>('');
  const [courseId, setCourseId] = useState<number | ''>('');
  const [created, setCreated] = useState<EntityRecord | null>(null);

  // The lookup is what the record DTO carries; there is no bare courseId on EntityRecord.
  const bookCourseId = record.course?.id;

  const { data: courses } = useQuery({
    queryKey: ['courses-all'],
    queryFn: listCourses,
    staleTime: 300_000,
    enabled: open,
  });

  // Seeded when the form opens, not on every render: once it is open the field is the user's, and
  // re-seeding would undo a deliberate change to a different course.
  useEffect(() => {
    if (open) setCourseId(bookCourseId ?? '');
  }, [open, bookCourseId]);

  const save = useMutation({
    mutationFn: () =>
      createEntity('notes', {
        summary: summary.trim(),
        description: description.trim() || null,
        priority: priority === '' ? null : priority,
        courseId: courseId === '' ? null : courseId,
        // The point of the whole form: the new note knows which resource it came from.
        bookId: record.id,
      }),
    onSuccess: (made) => {
      setCreated(made);
      setSummary('');
      setDescription('');
      // The resource's related-records panel gains a row, and the notes list gains a record.
      qc.invalidateQueries({ queryKey: ['related', module, record.id] });
      qc.invalidateQueries({ queryKey: ['list', 'notes'] });
    },
  });

  const selectedCourse = (courses ?? []).find((c: CourseOption) => c.id === courseId) ?? null;

  return (
    <Box sx={{ mt: 2 }}>
      <Button size="small" startIcon={<NoteAdd />} onClick={() => setOpen((v) => !v)}>
        {open ? 'Cancel' : 'Add a note about this resource'}
      </Button>

      <Collapse in={open}>
        <Paper variant="outlined" sx={{ p: 2, mt: 1 }}>
          <Stack spacing={2}>
            {/* Said before anything is typed, because the fix is on the resource, not here: with no
                course the note lands uncategorised, and the honest moment to mention that is now. */}
            {bookCourseId == null && (
              <Alert severity="warning" sx={{ py: 0.5 }}>
                Book not linked to a course — pick one below, or set it on the resource first.
              </Alert>
            )}

            <TextField
              label="Summary"
              value={summary}
              onChange={(e) => setSummary(e.target.value)}
              size="small"
              fullWidth
              autoFocus
              inputProps={{ dir: dirOf(summary) }}
            />
            <TextField
              label="Description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              size="small"
              fullWidth
              multiline
              minRows={4}
              inputProps={{ dir: dirOf(description) }}
            />
            <Stack direction="row" spacing={2}>
              <TextField
                select
                label="Priority"
                value={priority}
                onChange={(e) => setPriority(e.target.value === '' ? '' : Number(e.target.value))}
                size="small"
                sx={{ minWidth: 140 }}
              >
                <MenuItem value="">—</MenuItem>
                {[1, 2, 3, 4, 5].map((p) => (
                  <MenuItem key={p} value={p}>
                    {p}
                  </MenuItem>
                ))}
              </TextField>
              <Autocomplete
                options={courses ?? []}
                value={selectedCourse}
                getOptionLabel={(c: CourseOption) =>
                  `${c.displayTitle}${c.department?.code ? ` (${c.department.code})` : ''}`
                }
                isOptionEqualToValue={(a, b) => a.id === b.id}
                onChange={(_, c) => setCourseId(c ? c.id : '')}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label="Course"
                    size="small"
                    helperText={
                      bookCourseId != null && courseId === bookCourseId
                        ? "From the resource"
                        : undefined
                    }
                  />
                )}
                sx={{ flex: 1 }}
              />
            </Stack>

            {save.isError && <Alert severity="error">{String(save.error)}</Alert>}
            {created && (
              <Alert severity="success" sx={{ py: 0.5 }}>
                Created{' '}
                <Link to={`/notes?select=${created.id}`}>
                  {created.displayTitle || `note #${created.id}`}
                </Link>
              </Alert>
            )}

            <Stack direction="row" spacing={2}>
              <Button
                variant="contained"
                disabled={!summary.trim() || save.isPending}
                onClick={() => save.mutate()}
              >
                {save.isPending ? 'Saving…' : 'Create note'}
              </Button>
              <Typography variant="caption" color="text.secondary" sx={{ alignSelf: 'center' }}>
                Linked to this resource; it appears under Related records.
              </Typography>
            </Stack>
          </Stack>
        </Paper>
      </Collapse>
    </Box>
  );
}
