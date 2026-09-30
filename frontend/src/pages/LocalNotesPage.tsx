import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  IconButton,
  InputAdornment,
  MenuItem,
  Stack,
  Tab,
  Tabs,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import Add from '@mui/icons-material/Add';
import Search from '@mui/icons-material/Search';
import Clear from '@mui/icons-material/Clear';
import Refresh from '@mui/icons-material/Refresh';
import SmartToy from '@mui/icons-material/SmartToy';
import AttachFile from '@mui/icons-material/AttachFile';
import {
  LocalNote,
  LocalNotesView,
  createLocalNote,
  getLocalNote,
  getLocalNotes,
  updateLocalNote,
} from '../api/client';
import { SideNav } from '../components/SideNav';
import { dirOf } from '../util/rtl';

/**
 * The markdown folder, in the browser.
 *
 * These notes are files, not records. The desktop app writes them, the phone syncs them, and the
 * import page turns the ones worth keeping into records — but until now the only way to READ them
 * was to be sitting at the machine that holds them. The backend reads the folder off its own disk,
 * so this page works from anywhere the app does.
 *
 * Two axes, because the folder genuinely has two: **by day**, which is how it was written, and **by
 * subject**, which is how it is looked for. The subject axis is the one that needs machinery — a
 * note only carries a slug like `#A.salat`, and it takes the tag bank to know that means course
 * 9208 inside department A. Both axes come down in one response so the halves cannot disagree.
 */
export function LocalNotesPage() {
  const qc = useQueryClient();
  const [tab, setTab] = useState(0);
  const [q, setQ] = useState('');
  const [adding, setAdding] = useState(false);
  const [open, setOpen] = useState<string | null>(null);

  // A search result links straight to the note it found. Consumed into state and cleared from the
  // URL, so closing the dialog doesn't leave a link that reopens it on the next render.
  const [params, setParams] = useSearchParams();
  useEffect(() => {
    const wanted = params.get('open');
    if (wanted) {
      setOpen(wanted);
      params.delete('open');
      setParams(params, { replace: true });
    }
  }, [params, setParams]);

  const { data, isLoading, isFetching, error } = useQuery({
    queryKey: ['local-notes'],
    queryFn: getLocalNotes,
    staleTime: 30_000,
  });

  // Filtering happens here rather than on the server: the whole folder is already loaded, so a
  // round trip per keystroke would make a search that is currently instant feel slower than it is.
  // The consolidated search on the Search page is the one that has to ask the server, because half
  // of what it searches lives in the database.
  const needle = q.trim().toLowerCase();
  const match = (n: LocalNote) =>
    !needle ||
    n.filename.toLowerCase().includes(needle) ||
    (n.title ?? '').toLowerCase().includes(needle) ||
    n.snippet.toLowerCase().includes(needle) ||
    n.tags.some((t) => t.includes(needle)) ||
    (n.courseName ?? '').toLowerCase().includes(needle);

  const days = useMemo(
    () =>
      (data?.days ?? [])
        .map((d) => ({ ...d, notes: d.notes.filter(match) }))
        .filter((d) => d.notes.length > 0),
    [data, needle],
  );

  const subjects = useMemo(
    () =>
      (data?.subjects ?? [])
        .map((s) => ({
          ...s,
          courses: s.courses
            .map((c) => ({ ...c, notes: c.notes.filter(match) }))
            .filter((c) => c.notes.length > 0),
        }))
        .filter((s) => s.courses.length > 0),
    [data, needle],
  );

  const shown = days.reduce((n, d) => n + d.notes.length, 0);

  return (
    <Box sx={{ display: 'flex' }}>
      <SideNav />
      <Box sx={{ flex: 1, p: 3, minWidth: 0 }}>
        <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 0.5 }}>
          <Typography variant="h5" sx={{ flex: 1 }}>
            Local notes
          </Typography>
          <Tooltip title="Re-read the folder">
            <span>
              <IconButton
                onClick={() => qc.invalidateQueries({ queryKey: ['local-notes'] })}
                disabled={isFetching}
              >
                <Refresh />
              </IconButton>
            </span>
          </Tooltip>
          <Button variant="contained" startIcon={<Add />} onClick={() => setAdding(true)}>
            Add note
          </Button>
        </Stack>

        <Folder data={data} shown={shown} filtered={needle.length > 0} />

        {error && <Alert severity="error" sx={{ mb: 2 }}>{String(error)}</Alert>}

        <TextField
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Filter these notes — text, tag or course…"
          size="small"
          fullWidth
          sx={{ mb: 2, maxWidth: 560 }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <Search fontSize="small" />
              </InputAdornment>
            ),
            endAdornment: q ? (
              <InputAdornment position="end">
                <IconButton size="small" onClick={() => setQ('')}>
                  <Clear fontSize="small" />
                </IconButton>
              </InputAdornment>
            ) : null,
          }}
        />

        <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
          <Tab label="By day" />
          <Tab label="By subject" />
        </Tabs>

        {isLoading && <Typography color="text.secondary">Reading the folder…</Typography>}

        {!isLoading && tab === 0 && (
          <Stack spacing={3}>
            {days.map((d) => (
              <Box key={d.day}>
                <SectionHeading primary={dayLabel(d.day)} secondary={`${d.notes.length}`} />
                <Stack spacing={1}>
                  {d.notes.map((n) => (
                    <NoteRow key={n.filename} note={n} onOpen={setOpen} showSubject />
                  ))}
                </Stack>
              </Box>
            ))}
            {days.length === 0 && <Empty filtered={needle.length > 0} />}
          </Stack>
        )}

        {!isLoading && tab === 1 && (
          <Stack spacing={3}>
            {subjects.map((s) => (
              <Box key={s.dept}>
                <SectionHeading
                  primary={deptLabel(s.dept, s.deptName)}
                  secondary={`${s.courses.reduce((n, c) => n + c.notes.length, 0)}`}
                />
                <Stack spacing={2} sx={{ pl: 1, borderLeft: 1, borderColor: 'divider' }}>
                  {s.courses.map((c) => (
                    <Box key={`${c.courseId ?? 'none'}-${c.tag ?? ''}`} sx={{ pl: 1.5 }}>
                      <Stack direction="row" spacing={1} alignItems="baseline" sx={{ mb: 0.5 }}>
                        <Typography
                          variant="subtitle2"
                          dir={dirOf(c.courseName)}
                          sx={{ fontWeight: 600 }}
                        >
                          {c.courseName ?? 'Not filed to a course'}
                        </Typography>
                        {c.tag && <Chip label={`#${c.tag}`} size="small" variant="outlined" />}
                        <Typography variant="caption" color="text.secondary">
                          {c.notes.length}
                        </Typography>
                      </Stack>
                      <Stack spacing={1}>
                        {c.notes.map((n) => (
                          <NoteRow key={n.filename} note={n} onOpen={setOpen} />
                        ))}
                      </Stack>
                    </Box>
                  ))}
                </Stack>
              </Box>
            ))}
            {subjects.length === 0 && <Empty filtered={needle.length > 0} />}
          </Stack>
        )}

        {adding && <AddNoteDialog onClose={() => setAdding(false)} />}
        {open && <NoteDialog filename={open} onClose={() => setOpen(null)} />}
      </Box>
    </Box>
  );
}

/** Where the notes and the vocabulary are read from — the two facts that explain an empty page. */
function Folder({
  data,
  shown,
  filtered,
}: {
  data?: LocalNotesView;
  shown: number;
  filtered: boolean;
}) {
  if (!data) return null;
  return (
    <Stack sx={{ mb: 2 }}>
      <Typography variant="caption" color="text.secondary">
        {data.dir} · {filtered ? `${shown} of ${data.total}` : `${data.total}`} notes
      </Typography>
      <Typography variant="caption" color="text.secondary">
        {data.tagBankEntries > 0 ? (
          <>
            Tag bank: {data.tagBankEntries} courses · {data.tagBankPath}
          </>
        ) : (
          <>
            No tag bank at {data.tagBankPath} — notes are grouped by their department letter only,
            without course names.
          </>
        )}
      </Typography>
    </Stack>
  );
}

function SectionHeading({ primary, secondary }: { primary: string; secondary: string }) {
  return (
    <>
      <Stack direction="row" spacing={1} alignItems="baseline">
        <Typography variant="subtitle1" dir={dirOf(primary)} sx={{ fontWeight: 600 }}>
          {primary}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          {secondary}
        </Typography>
      </Stack>
      <Divider sx={{ mb: 1 }} />
    </>
  );
}

function Empty({ filtered }: { filtered: boolean }) {
  return (
    <Typography color="text.secondary">
      {filtered ? 'No note matches that.' : 'No notes in the folder.'}
    </Typography>
  );
}

/** One note. Compact on purpose: the folder runs to hundreds, and a card each would not scan. */
function NoteRow({
  note,
  onOpen,
  showSubject,
}: {
  note: LocalNote;
  onOpen: (f: string) => void;
  showSubject?: boolean;
}) {
  const title = note.title ?? note.filename;
  return (
    <Box
      onClick={() => onOpen(note.filename)}
      sx={{
        cursor: 'pointer',
        px: 1,
        py: 0.75,
        borderRadius: 1,
        '&:hover': { bgcolor: 'action.hover' },
      }}
    >
      <Stack direction="row" spacing={1} alignItems="baseline" sx={{ flexWrap: 'wrap' }}>
        <Typography variant="caption" color="text.secondary" sx={{ fontVariantNumeric: 'tabular-nums' }}>
          {note.timestamp.slice(11, 16)}
        </Typography>
        {note.type && <Chip label={note.type} size="small" sx={{ height: 18, fontSize: 11 }} />}
        {note.priority != null && (
          <Typography variant="caption" color="text.secondary">
            p{note.priority}
          </Typography>
        )}
        <Typography dir={dirOf(title)} sx={{ flex: 1, minWidth: 200 }}>
          {title}
        </Typography>
        {note.generated && (
          <Tooltip title="Written by the Q&A workflow">
            <SmartToy fontSize="inherit" color="disabled" />
          </Tooltip>
        )}
        {note.files > 0 && (
          <Tooltip title={`${note.files} file(s) in the companion folder`}>
            <Stack direction="row" alignItems="center">
              <AttachFile fontSize="inherit" color="disabled" />
              <Typography variant="caption" color="text.secondary">
                {note.files}
              </Typography>
            </Stack>
          </Tooltip>
        )}
        {showSubject && note.courseName && (
          <Chip
            label={note.courseName}
            size="small"
            variant="outlined"
            sx={{ height: 18, fontSize: 11 }}
          />
        )}
        {note.tags.slice(0, 3).map((t) => (
          <Typography key={t} variant="caption" color="primary.main">
            #{t}
          </Typography>
        ))}
      </Stack>
    </Box>
  );
}

/** Read one note, and edit it in place. */
function NoteDialog({ filename, onClose }: { filename: string; onClose: () => void }) {
  const qc = useQueryClient();
  const [draft, setDraft] = useState<string | null>(null);
  const { data, isLoading } = useQuery({
    queryKey: ['local-note', filename],
    queryFn: () => getLocalNote(filename),
  });
  const save = useMutation({
    mutationFn: (body: string) => updateLocalNote(filename, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['local-notes'] });
      qc.invalidateQueries({ queryKey: ['local-note', filename] });
      setDraft(null);
    },
  });
  const body = draft ?? data?.body ?? '';
  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ pb: 0 }}>
        <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
          {filename}
        </Typography>
      </DialogTitle>
      <DialogContent>
        {isLoading ? (
          <Typography color="text.secondary">Reading…</Typography>
        ) : (
          <>
            {data && data.note.tags.length > 0 && (
              <Stack direction="row" spacing={1} sx={{ mb: 1, flexWrap: 'wrap' }}>
                {data.note.tags.map((t) => (
                  <Chip key={t} label={`#${t}`} size="small" variant="outlined" />
                ))}
              </Stack>
            )}
            <TextField
              value={body}
              onChange={(e) => setDraft(e.target.value)}
              multiline
              minRows={14}
              fullWidth
              inputProps={{ dir: dirOf(body), style: { fontFamily: 'inherit' } }}
            />
          </>
        )}
        {save.isError && (
          <Alert severity="error" sx={{ mt: 1 }}>
            {String(save.error)}
          </Alert>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button
          variant="contained"
          disabled={draft === null || save.isPending || !body.trim()}
          onClick={() => save.mutate(body)}
        >
          {save.isPending ? 'Saving…' : 'Save'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

const TYPES = [
  { code: 'N', label: 'N — Note' },
  { code: 'T', label: 'T — Task' },
  { code: 'J', label: 'J — Journal' },
  { code: 'R', label: 'R — Resource' },
  { code: 'P', label: 'P — Plan' },
  { code: 'G', label: 'G — Goal' },
  { code: 'W', label: 'W — Writing' },
];

/**
 * Write a note into the folder.
 *
 * The department letter is NOT a field here. The archive's invariant is that the letter in the file
 * name equals the department of the note's first tag, so the form derives it from the text and says
 * what it derived — a form that let you pick both would let you contradict yourself, and the file
 * name is the half that would be wrong.
 */
function AddNoteDialog({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient();
  const [body, setBody] = useState('');
  const [type, setType] = useState('N');
  const [priority, setPriority] = useState(2);

  const firstTag = body.match(/(?:^|[^\w#/])#(\w[\w.\-]{0,48})/u)?.[1] ?? null;
  const derived = firstTag && firstTag.indexOf('.') === 1 ? firstTag[0].toUpperCase() : null;

  const create = useMutation({
    mutationFn: () => createLocalNote({ type, priority, body }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['local-notes'] });
      onClose();
    },
  });

  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Add a note</DialogTitle>
      <DialogContent>
        <Stack direction="row" spacing={2} sx={{ mb: 2, mt: 1 }}>
          <TextField
            select
            label="Type"
            value={type}
            onChange={(e) => setType(e.target.value)}
            size="small"
            sx={{ minWidth: 160 }}
          >
            {TYPES.map((t) => (
              <MenuItem key={t.code} value={t.code}>
                {t.label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="Priority"
            value={priority}
            onChange={(e) => setPriority(Number(e.target.value))}
            size="small"
            sx={{ minWidth: 120 }}
          >
            {[1, 2, 3, 4].map((p) => (
              <MenuItem key={p} value={p}>
                p{p}
              </MenuItem>
            ))}
          </TextField>
        </Stack>
        <TextField
          value={body}
          onChange={(e) => setBody(e.target.value)}
          multiline
          minRows={12}
          fullWidth
          autoFocus
          placeholder={'Title on the first line\n\nThe body.\n\n#A.salat #D.time'}
          inputProps={{ dir: dirOf(body) }}
        />
        <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
          {derived ? (
            <>
              Filed under department <b>{derived}</b>, from the first tag <b>#{firstTag}</b>.
            </>
          ) : (
            <>
              No tag yet — the note is written with no department letter, which reads as “not filed”.
              Add a tag like <code>#A.salat</code> to file it.
            </>
          )}
        </Typography>
        {create.isError && (
          <Alert severity="error" sx={{ mt: 1 }}>
            {String(create.error)}
          </Alert>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={!body.trim() || create.isPending}
          onClick={() => create.mutate()}
        >
          {create.isPending ? 'Writing…' : 'Write note'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

/**
 * A department heading.
 *
 * The letter is NOT always worth prefixing: a department's name in this database already begins
 * with its own letter ("A الدين"), so prefixing produced "A · A الدين". Prefix only when the name
 * does not already carry it, which keeps the heading right whichever convention a database uses.
 *
 * `?` is the service's sentinel for a note with no department at all — the state a capture starts
 * in — and deserves a word rather than a punctuation mark.
 */
function deptLabel(dept: string, name?: string | null): string {
  if (dept === '?') return name ? `Not filed · ${name}` : 'Not filed';
  if (!name) return dept;
  return name.startsWith(dept) ? name : `${dept} · ${name}`;
}

/** "Monday 29 September 2026" — the day is a heading, so it is spelled out. */
function dayLabel(iso: string): string {
  const d = new Date(`${iso}T00:00:00`);
  return Number.isNaN(d.getTime())
    ? iso
    : d.toLocaleDateString(undefined, {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
        year: 'numeric',
      });
}
