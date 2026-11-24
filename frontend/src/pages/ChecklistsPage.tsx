import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Box,
  Button,
  Checkbox,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  IconButton,
  Menu,
  MenuItem,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
import ChevronLeft from '@mui/icons-material/ChevronLeft';
import ChevronRight from '@mui/icons-material/ChevronRight';
import EditIcon from '@mui/icons-material/Edit';
import DoneIcon from '@mui/icons-material/Done';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import MoreVert from '@mui/icons-material/MoreVert';
import OpenInNew from '@mui/icons-material/OpenInNew';
import PrintIcon from '@mui/icons-material/Print';
import {
  createChecklistGroup,
  createChecklistItem,
  getChecklistHistory,
  getChecklistIndex,
  getChecklistWeek,
  getInactiveChecklistItems,
  importChecklistFiles,
  setChecklistCheck,
  setChecklistWinner,
  updateChecklistGroup,
  updateChecklistItem,
  type ChecklistGroup,
  type ChecklistItem,
  type ChecklistMark,
  type ChecklistWeek,
} from '../api/client';
import { SideNav } from '../components/SideNav';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { dirOf } from '../util/rtl';
import { addDaysIso, shiftWeek, weekIdOf } from '../util/isoWeek';
import { checklistPrintHtml, dayLabel, printDays, printHtml } from '../util/checklistPrint';

/*
 * The checklists, to tick and annotate from the web: the same rows the phone syncs, kept in the
 * database. Click a box to cycle its mark (done → partly → none, as on the phone); right-click it,
 * or use the note button, to write a note for that day. "Edit lists" adds, renames and ends groups
 * and items for the week on screen onwards. The wall view (/checklists/view) stays read-only.
 */

const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const ACCENT = '#2e7d32';
const TODAY_SHADE = '#e8f5e9';
const NOTE_MARK = '#f9a825';

const SYMBOL: Record<ChecklistMark, string> = { DONE: '✓', PARTIAL: '◐', NONE: '' };
const NEXT: Record<ChecklistMark, ChecklistMark> = { NONE: 'DONE', DONE: 'PARTIAL', PARTIAL: 'NONE' };
const score = (m: ChecklistMark) => (m === 'DONE' ? 1 : m === 'PARTIAL' ? 0.5 : 0);

function pct(marks: ChecklistMark[]) {
  return marks.length ? `${Math.round((marks.reduce((s, m) => s + score(m), 0) / marks.length) * 100)}%` : '–';
}

/** The slot's date: a daily item's day, a weekly item's Monday. */
function slotDay(week: ChecklistWeek, group: ChecklistGroup, slot: number) {
  return addDaysIso(week.monday, group.weekly ? 0 : slot);
}

interface Slot {
  group: ChecklistGroup;
  item: ChecklistItem;
  slot: number;
}

export function ChecklistsPage() {
  const qc = useQueryClient();
  const [params, setParams] = useSearchParams();
  const [editing, setEditing] = useState(false);
  const [noteFor, setNoteFor] = useState<Slot | null>(null);
  const [historyFor, setHistoryFor] = useState<ChecklistItem | null>(null);
  const [menu, setMenu] = useState<HTMLElement | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [printing, setPrinting] = useState(false);

  const index = useQuery({ queryKey: ['checklists', 'index'], queryFn: getChecklistIndex });
  const weekId = params.get('week') ?? index.data?.current ?? weekIdOf(new Date());
  const week = useQuery({
    queryKey: ['checklists', 'week', weekId],
    queryFn: () => getChecklistWeek(weekId),
    retry: false,
  });
  useDocumentTitle(week.data ? `Checklists · Week ${week.data.week}` : 'Checklists');

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['checklists'] });
    qc.invalidateQueries({ queryKey: ['checklist-week'] });
  };

  const check = useMutation({
    mutationFn: (v: { item: string; day: string; mark: ChecklistMark; note: string | null }) =>
      setChecklistCheck(v.item, v.day, v.mark, v.note),
    onMutate: (v) => {
      // Show the tick at once; the refetch afterwards confirms it (or puts it back on failure).
      qc.setQueryData<ChecklistWeek>(['checklists', 'week', weekId], (w) => {
        if (!w) return w;
        return {
          ...w,
          groups: w.groups.map((g) => ({
            ...g,
            items: g.items.map((i) => {
              if (i.uid !== v.item) return i;
              const slot = g.weekly ? 0 : Math.round((Date.parse(v.day) - Date.parse(w.monday)) / 86_400_000);
              return {
                ...i,
                marks: i.marks.map((m, k) => (k === slot ? v.mark : m)),
                notes: i.notes.map((n, k) => (k === slot ? v.note : n)),
              };
            }),
          })),
        };
      });
    },
    onSettled: refresh,
  });

  const edit = useMutation({ mutationFn: (f: () => Promise<unknown>) => f(), onSettled: refresh });

  const winner = useMutation({ mutationFn: setChecklistWinner, onSettled: refresh });
  const importFiles = useMutation({
    mutationFn: importChecklistFiles,
    onSuccess: (r) => setNotice(r.added ? `Imported ${r.added} rows from the week files.` : 'Nothing new in the week files.'),
    onSettled: refresh,
  });

  const go = (id: string) => setParams({ week: id });
  const todayLocal = useMemo(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }, []);

  const w = week.data;
  const empty = week.isError || (w && w.groups.length === 0);

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', p: 2, overflow: 'auto' }}>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1.5, flexWrap: 'wrap', rowGap: 1 }}>
          <Typography variant="h6" sx={{ mr: 1 }}>Checklists</Typography>
          <Tooltip title="Previous week">
            <IconButton size="small" onClick={() => go(shiftWeek(weekId, -1))}><ChevronLeft /></IconButton>
          </Tooltip>
          <Typography sx={{ fontWeight: 600, whiteSpace: 'nowrap' }}>
            Week {Number(weekId.slice(-2))}
            {w && (
              <Typography component="span" sx={{ color: 'text.secondary', ml: 1, fontSize: 14 }}>
                {w.monday} – {w.sunday}
              </Typography>
            )}
          </Typography>
          <Tooltip title="Next week">
            <IconButton size="small" onClick={() => go(shiftWeek(weekId, 1))}><ChevronRight /></IconButton>
          </Tooltip>
          {index.data && weekId !== index.data.current && (
            <Button size="small" onClick={() => go(index.data!.current)}>This week</Button>
          )}
          <Box sx={{ flex: 1 }} />
          <Button
            size="small"
            variant={editing ? 'contained' : 'outlined'}
            startIcon={editing ? <DoneIcon /> : <EditIcon />}
            onClick={() => setEditing((e) => !e)}
          >
            {editing ? 'Done editing' : 'Edit lists'}
          </Button>
          <Tooltip title="Print the lists with three days of squares on one A4 sheet, to tick by pen">
            <span>
              <IconButton size="small" aria-label="Print" disabled={!w || w.groups.length === 0} onClick={() => setPrinting(true)}>
                <PrintIcon fontSize="small" />
              </IconButton>
            </span>
          </Tooltip>
          <Tooltip title="Wall view (read-only, fitted to the screen)">
            <IconButton size="small" component="a" href={`/checklists/view?week=${weekId}`} target="_blank" rel="noopener">
              <OpenInNew fontSize="small" />
            </IconButton>
          </Tooltip>
          <IconButton size="small" aria-label="More" onClick={(e) => setMenu(e.currentTarget)}><MoreVert /></IconButton>
          <Menu anchorEl={menu} open={!!menu} onClose={() => setMenu(null)}>
            <MenuItem disabled sx={{ fontSize: 13, opacity: '1 !important' }}>When the phone and the web changed the same tick:</MenuItem>
            <MenuItem
              selected={index.data?.winner !== 'web'}
              onClick={() => { winner.mutate('mobile'); setMenu(null); }}
            >
              The phone's change stands
            </MenuItem>
            <MenuItem
              selected={index.data?.winner === 'web'}
              onClick={() => { winner.mutate('web'); setMenu(null); }}
            >
              The web's change stands
            </MenuItem>
            <MenuItem
              divider
              onClick={() => { importFiles.mutate(); setMenu(null); }}
            >
              Import the week files again
            </MenuItem>
          </Menu>
        </Stack>
        {notice && (
          <Typography sx={{ fontSize: 13, color: 'text.secondary', mb: 1 }} onClick={() => setNotice(null)}>{notice}</Typography>
        )}
        {!editing && w && w.groups.length > 0 && (
          <Typography sx={{ fontSize: 12, color: 'text.secondary', mb: 1 }}>
            Click a box to tick it (done, then partly, then clear). Right-click it to write a note. Click an item for its history.
          </Typography>
        )}

        {week.isLoading && <CircularProgress sx={{ alignSelf: 'center', mt: 4 }} />}
        {empty && !editing && (
          <Typography color="text.secondary" sx={{ mt: 2 }}>
            No checklists in this week. Use <b>Edit lists</b> to add a group.
          </Typography>
        )}

        {w && w.groups.map((g) => (
          <GroupTable
            key={g.uid}
            week={w}
            group={g}
            editing={editing}
            todayIso={todayLocal}
            onCycle={(item, slot) =>
              check.mutate({ item: item.uid, day: slotDay(w, g, slot), mark: NEXT[item.marks[slot]], note: item.notes[slot] })
            }
            onNote={(item, slot) => setNoteFor({ group: g, item, slot })}
            onHistory={(item) => setHistoryFor(item)}
            onEdit={(f) => edit.mutate(f)}
          />
        ))}

        {editing && (
          <>
            <AddGroup onAdd={(name, weekly) => edit.mutate(() => createChecklistGroup(name, weekly, weekId))} />
            <Inactive weekId={weekId} onReopen={(uid) => edit.mutate(() => updateChecklistItem(uid, { active: true, week: weekId }))} />
          </>
        )}
      </Box>

      {noteFor && w && (
        <NoteDialog
          slot={noteFor}
          day={slotDay(w, noteFor.group, noteFor.slot)}
          onClose={() => setNoteFor(null)}
          onSave={(mark, note) => {
            check.mutate({ item: noteFor.item.uid, day: slotDay(w, noteFor.group, noteFor.slot), mark, note });
            setNoteFor(null);
          }}
        />
      )}
      {historyFor && <HistoryDialog item={historyFor} onClose={() => setHistoryFor(null)} />}
      {printing && w && (
        <PrintDialog
          week={w}
          start={todayLocal >= w.monday && todayLocal <= w.sunday ? todayLocal : w.monday}
          onClose={() => setPrinting(false)}
        />
      )}
    </Box>
  );
}

function GroupTable({
  week,
  group,
  editing,
  todayIso,
  onCycle,
  onNote,
  onHistory,
  onEdit,
}: {
  week: ChecklistWeek;
  group: ChecklistGroup;
  editing: boolean;
  todayIso: string;
  onCycle: (item: ChecklistItem, slot: number) => void;
  onNote: (item: ChecklistItem, slot: number) => void;
  onHistory: (item: ChecklistItem) => void;
  onEdit: (f: () => Promise<unknown>) => void;
}) {
  const slots = group.weekly ? [0] : [0, 1, 2, 3, 4, 5, 6];
  const [adding, setAdding] = useState('');
  const cell = { width: 46, minWidth: 46, textAlign: 'center' as const, p: 0 };

  return (
    <Box sx={{ mb: 2.5, maxWidth: 900 }}>
      <Stack direction="row" alignItems="center" spacing={1} sx={{ borderBottom: `2px solid ${ACCENT}`, pb: 0.25, mb: 0.5 }}>
        {editing ? (
          <>
            <RenameField
              value={group.title}
              label="Group"
              onSave={(name) => onEdit(() => updateChecklistGroup(group.uid, { name }))}
            />
            <FormControlLabel
              sx={{ mr: 0 }}
              control={
                <Checkbox
                  size="small"
                  checked={group.weekly}
                  onChange={(e) => onEdit(() => updateChecklistGroup(group.uid, { weekly: e.target.checked }))}
                />
              }
              label={<Typography sx={{ fontSize: 13 }}>Weekly</Typography>}
            />
            <Tooltip title="Take this group off the list from this week on (its history is kept)">
              <IconButton
                size="small"
                onClick={() => onEdit(() => updateChecklistGroup(group.uid, { active: false, week: week.id }))}
              >
                <DeleteOutline fontSize="small" />
              </IconButton>
            </Tooltip>
          </>
        ) : (
          <Typography dir={dirOf(group.title)} sx={{ fontWeight: 700, color: ACCENT, flex: 1, textAlign: 'start' }}>
            <Typography component="span" sx={{ fontSize: 12, color: 'text.disabled', mr: 0.75 }}>{group.code}</Typography>
            {group.title}
          </Typography>
        )}
        <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{pct(group.items.flatMap((i) => i.marks))}</Typography>
      </Stack>

      <Box component="table" sx={{ borderCollapse: 'collapse', width: '100%' }}>
        <thead>
          <tr>
            <Box component="th" />
            {slots.map((s) => {
              const day = addDaysIso(week.monday, s);
              return (
                <Box
                  component="th"
                  key={s}
                  sx={{ ...cell, fontSize: 12, fontWeight: 500, color: 'text.secondary', bgcolor: !group.weekly && day === todayIso ? TODAY_SHADE : undefined }}
                >
                  {group.weekly ? 'Week' : `${DAY_NAMES[s]} ${Number(day.slice(8))}`}
                </Box>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {group.items.map((item) => (
            <Box component="tr" key={item.uid} sx={{ '&:hover': { bgcolor: '#fafafa' } }}>
              <Box component="td" sx={{ py: 0.25, pr: 1 }}>
                {editing ? (
                  <Stack direction="row" alignItems="center" spacing={0.5}>
                    <Typography sx={{ fontSize: 11, color: 'text.disabled', width: 32 }}>{group.code}.{item.code}</Typography>
                    <RenameField value={item.text} label="Item" onSave={(name) => onEdit(() => updateChecklistItem(item.uid, { name }))} />
                    <Tooltip title="Take this item off the list from this week on (its history is kept)">
                      <IconButton size="small" onClick={() => onEdit(() => updateChecklistItem(item.uid, { active: false, week: week.id }))}>
                        <DeleteOutline fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </Stack>
                ) : (
                  <Box
                    dir={dirOf(item.text)}
                    onClick={() => onHistory(item)}
                    sx={{ cursor: 'pointer', textAlign: 'start', '&:hover .t': { textDecoration: 'underline' } }}
                  >
                    <Typography component="span" sx={{ fontSize: 11, color: 'text.disabled', mx: 0.5 }}>{group.code}.{item.code}</Typography>
                    <Typography component="span" className="t" sx={{ fontSize: 14 }}>{item.text}</Typography>
                  </Box>
                )}
              </Box>
              {slots.map((s) => {
                const mark = item.marks[s] ?? 'NONE';
                const note = item.notes?.[s] ?? null;
                const day = addDaysIso(week.monday, s);
                const box = (
                  <Box
                    role="button"
                    aria-label={`${item.text} ${group.weekly ? 'this week' : DAY_NAMES[s]}: ${mark.toLowerCase()}`}
                    onClick={() => onCycle(item, s)}
                    onContextMenu={(e) => {
                      e.preventDefault();
                      onNote(item, s);
                    }}
                    sx={{
                      position: 'relative',
                      mx: 'auto',
                      width: 30,
                      height: 26,
                      lineHeight: '24px',
                      border: '1px solid #cfd8dc',
                      borderRadius: 1,
                      cursor: 'pointer',
                      userSelect: 'none',
                      fontSize: 16,
                      color: '#fff',
                      bgcolor: mark === 'DONE' ? ACCENT : mark === 'PARTIAL' ? '#81c784' : '#fff',
                      '&:hover': { borderColor: ACCENT },
                    }}
                  >
                    {SYMBOL[mark]}
                    {note && (
                      <Box
                        sx={{
                          position: 'absolute',
                          top: -1,
                          right: -1,
                          width: 0,
                          height: 0,
                          borderTop: `9px solid ${NOTE_MARK}`,
                          borderLeft: '9px solid transparent',
                        }}
                      />
                    )}
                  </Box>
                );
                return (
                  <Box
                    component="td"
                    key={s}
                    sx={{ ...cell, py: 0.25, bgcolor: !group.weekly && day === todayIso ? TODAY_SHADE : undefined }}
                  >
                    {note ? (
                      <Tooltip title={<span dir={dirOf(note)} style={{ whiteSpace: 'pre-wrap' }}>{note}</span>}>{box}</Tooltip>
                    ) : (
                      box
                    )}
                  </Box>
                );
              })}
            </Box>
          ))}
        </tbody>
      </Box>
      {editing && (
        <Stack direction="row" spacing={1} sx={{ mt: 0.75 }}>
          <TextField
            size="small"
            placeholder="Add item"
            value={adding}
            inputProps={{ dir: dirOf(adding) }}
            onChange={(e) => setAdding(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && adding.trim()) {
                const name = adding.trim();
                onEdit(() => createChecklistItem(group.uid, name, week.id));
                setAdding('');
              }
            }}
            sx={{ flex: 1, maxWidth: 420 }}
          />
          <Button
            size="small"
            disabled={!adding.trim()}
            onClick={() => {
              const name = adding.trim();
              onEdit(() => createChecklistItem(group.uid, name, week.id));
              setAdding('');
            }}
          >
            Add
          </Button>
        </Stack>
      )}
    </Box>
  );
}

/** A text field that saves on Enter or when it loses focus, and only when the text changed. */
function RenameField({ value, label, onSave }: { value: string; label: string; onSave: (v: string) => void }) {
  const [text, setText] = useState(value);
  const save = () => {
    const t = text.trim();
    if (t && t !== value) onSave(t);
    else setText(value);
  };
  return (
    <TextField
      size="small"
      variant="standard"
      aria-label={label}
      value={text}
      inputProps={{ dir: dirOf(text) }}
      onChange={(e) => setText(e.target.value)}
      onBlur={save}
      onKeyDown={(e) => {
        if (e.key === 'Enter') (e.target as HTMLInputElement).blur();
        if (e.key === 'Escape') setText(value);
      }}
      sx={{ flex: 1, minWidth: 200 }}
    />
  );
}

function AddGroup({ onAdd }: { onAdd: (name: string, weekly: boolean) => void }) {
  const [name, setName] = useState('');
  const [weekly, setWeekly] = useState(false);
  const add = () => {
    if (!name.trim()) return;
    onAdd(name.trim(), weekly);
    setName('');
    setWeekly(false);
  };
  return (
    <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 1, maxWidth: 900 }}>
      <TextField
        size="small"
        placeholder="Add group (a time in front, like 07:30 Start of day, sets the phone's reminder)"
        value={name}
        inputProps={{ dir: dirOf(name) }}
        onChange={(e) => setName(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && add()}
        sx={{ flex: 1 }}
      />
      <FormControlLabel control={<Checkbox size="small" checked={weekly} onChange={(e) => setWeekly(e.target.checked)} />} label="Weekly" />
      <Button size="small" variant="outlined" disabled={!name.trim()} onClick={add}>Add group</Button>
    </Stack>
  );
}

/** Items ended before this week (or starting after it), each with a button to bring it back. */
function Inactive({ weekId, onReopen }: { weekId: string; onReopen: (uid: string) => void }) {
  const q = useQuery({ queryKey: ['checklists', 'inactive', weekId], queryFn: () => getInactiveChecklistItems(weekId) });
  if (!q.data?.length) return null;
  return (
    <Box sx={{ mt: 3, maxWidth: 900 }}>
      <Typography sx={{ fontWeight: 600, fontSize: 14, mb: 0.5 }}>Not on this week's list</Typography>
      {q.data.map((i) => (
        <Stack key={i.uid} direction="row" alignItems="center" spacing={1}>
          <Typography dir={dirOf(i.name)} sx={{ fontSize: 14, flex: 1, textAlign: 'start' }}>{i.name}</Typography>
          <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>
            {i.end ? `ended ${i.end}` : `starts ${i.start}`}
          </Typography>
          <Button size="small" onClick={() => onReopen(i.uid)}>Reopen</Button>
        </Stack>
      ))}
    </Box>
  );
}

function NoteDialog({
  slot,
  day,
  onClose,
  onSave,
}: {
  slot: Slot;
  day: string;
  onClose: () => void;
  onSave: (mark: ChecklistMark, note: string | null) => void;
}) {
  const [mark, setMark] = useState<ChecklistMark>(slot.item.marks[slot.slot] ?? 'NONE');
  const [note, setNote] = useState(slot.item.notes?.[slot.slot] ?? '');
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle dir={dirOf(slot.item.text)} sx={{ textAlign: 'start' }}>
        {slot.item.text}
        <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{slot.group.weekly ? `Week of ${day}` : day}</Typography>
      </DialogTitle>
      <DialogContent>
        <ToggleButtonGroup exclusive size="small" value={mark} onChange={(_, m) => m && setMark(m)} sx={{ mb: 2 }}>
          <ToggleButton value="DONE">✓ Done</ToggleButton>
          <ToggleButton value="PARTIAL">◐ Partly</ToggleButton>
          <ToggleButton value="NONE">Not done</ToggleButton>
        </ToggleButtonGroup>
        <TextField
          autoFocus
          fullWidth
          multiline
          minRows={3}
          label="Note"
          value={note}
          inputProps={{ dir: dirOf(note) }}
          onChange={(e) => setNote(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) onSave(mark, note.trim() || null);
          }}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={() => onSave(mark, note.trim() || null)}>Save</Button>
      </DialogActions>
    </Dialog>
  );
}

function HistoryDialog({ item, onClose }: { item: ChecklistItem; onClose: () => void }) {
  const q = useQuery({ queryKey: ['checklists', 'history', item.uid], queryFn: () => getChecklistHistory(item.uid) });
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle dir={dirOf(item.text)} sx={{ textAlign: 'start' }}>{item.text}</DialogTitle>
      <DialogContent>
        {q.isLoading && <CircularProgress size={20} />}
        {q.data && q.data.length === 0 && <Typography color="text.secondary">Nothing ticked or noted yet.</Typography>}
        {q.data?.map((h) => (
          <Stack key={h.day} direction="row" spacing={1.5} sx={{ py: 0.5, borderBottom: '1px solid #eee' }}>
            <Typography sx={{ fontSize: 13, color: 'text.secondary', width: 110, flexShrink: 0 }}>
              {new Date(`${h.day}T00:00:00`).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })}
            </Typography>
            <Typography sx={{ width: 20, color: ACCENT }}>{SYMBOL[h.mark]}</Typography>
            <Typography dir={dirOf(h.note)} sx={{ fontSize: 14, flex: 1, textAlign: 'start', whiteSpace: 'pre-wrap' }}>
              {h.note}
            </Typography>
          </Stack>
        ))}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}

/** Three blank days of this week's lists on one A4 sheet, sent to the browser's print dialog. */
function PrintDialog({ week, start, onClose }: { week: ChecklistWeek; start: string; onClose: () => void }) {
  const [from, setFrom] = useState(start);
  const valid = /^\d{4}-\d{2}-\d{2}$/.test(from);
  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Print paper checklists</DialogTitle>
      <DialogContent>
        <Typography sx={{ fontSize: 14, mb: 2 }}>
          One A4 page with this week&apos;s groups and items, each with three squares, one per day, to tick by
          pen, and a notes box for each day at the foot.
        </Typography>
        <TextField
          type="date"
          label="First day"
          size="small"
          value={from}
          onChange={(e) => setFrom(e.target.value)}
          InputLabelProps={{ shrink: true }}
          fullWidth
        />
        {valid && (
          <Typography sx={{ fontSize: 13, color: 'text.secondary', mt: 1.5 }}>
            {printDays(from).map(dayLabel).join(' · ')}
          </Typography>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          startIcon={<PrintIcon />}
          disabled={!valid}
          onClick={() => {
            printHtml(checklistPrintHtml(week, from));
            onClose();
          }}
        >
          Print
        </Button>
      </DialogActions>
    </Dialog>
  );
}
