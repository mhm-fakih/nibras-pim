import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  FormControlLabel,
  Paper,
  Stack,
  Switch,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
import Download from '@mui/icons-material/Download';
import Headphones from '@mui/icons-material/Headphones';
import Movie from '@mui/icons-material/Movie';
import Subtitles from '@mui/icons-material/Subtitles';
import Stop from '@mui/icons-material/Stop';
import Replay from '@mui/icons-material/Replay';
import MoveToInbox from '@mui/icons-material/MoveToInbox';
import { SideNav } from '../components/SideNav';
import {
  cancelMediaCapture,
  getMediaCapture,
  importNoteFrom,
  retryMediaCapture,
  startMediaCapture,
  type MediaCaptureJob,
  type MediaCaptureMode,
} from '../api/client';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { errorDetail } from '../util/errors';
import { dirOf } from '../util/rtl';

/*
 * YouTube downloads from the web: ids or links in, the capture tools' media-dl.sh run on the
 * server, one Resource note (with the media in its folder) out, in the capture tools' notes folder.
 */

const ID = /^[A-Za-z0-9_-]{11}$/;

/** The YouTube id of a bare id or a youtube.com / youtu.be link, else null. */
export function youtubeId(s: string): string | null {
  const t = s.trim();
  if (ID.test(t)) return t;
  try {
    const u = new URL(t);
    const host = u.hostname.replace(/^(www\.|m\.|music\.)/, '');
    if (host === 'youtu.be') return ID.test(u.pathname.slice(1)) ? u.pathname.slice(1) : null;
    if (host === 'youtube.com') {
      const v = u.searchParams.get('v');
      if (v && ID.test(v)) return v;
      const m = /^\/(shorts|embed|live)\/([A-Za-z0-9_-]{11})/.exec(u.pathname);
      return m ? m[2] : null;
    }
  } catch {
    /* not a link */
  }
  return null;
}

const words = (s: string) => s.split(/\s+/).filter(Boolean);

const STATUS_COLOR = { QUEUED: 'default', RUNNING: 'info', DONE: 'success', FAILED: 'error' } as const;
const MODE_LABEL: Record<MediaCaptureMode, string> = { BOTH: 'Video + audio', VIDEO: 'Video', AUDIO: 'Audio' };

export function MediaDownloadPage() {
  useDocumentTitle('YouTube download');
  const qc = useQueryClient();
  const [links, setLinks] = useState('');
  const [mode, setMode] = useState<MediaCaptureMode>('BOTH');
  const [subtitles, setSubtitles] = useState(true);
  const [embed, setEmbed] = useState(false);
  const [voice, setVoice] = useState(false);
  const [text, setText] = useState('');

  const state = useQuery({
    queryKey: ['media-capture'],
    queryFn: getMediaCapture,
    refetchInterval: (q) => (q.state.data?.jobs.some((j) => j.status === 'RUNNING' || j.status === 'QUEUED') ? 2000 : false),
  });
  const start = useMutation({
    mutationFn: startMediaCapture,
    onSuccess: () => {
      setLinks('');
      setText('');
      void qc.invalidateQueries({ queryKey: ['media-capture'] });
    },
  });

  const items = useMemo(() => words(links), [links]);
  const bad = items.filter((w) => !ID.test(w) && !/^https?:\/\/\S+$/i.test(w));
  const problem = state.data?.problem;

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, minWidth: 0, overflow: 'auto', p: 2 }}>
        <Box sx={{ maxWidth: 900 }}>
          <Typography variant="h6">YouTube download</Typography>
          <Typography sx={{ fontSize: 13, color: 'text.secondary', mb: 2 }}>
            Downloads with the capture tools&apos; <code>media-dl.sh</code> on the server: one Resource note per
            run, the media in the note&apos;s folder, in the capture tools&apos; notes folder.
          </Typography>
          {problem && <Alert severity="warning" sx={{ mb: 2 }}>{problem}</Alert>}

          <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
            <TextField
              label="YouTube video ids or links"
              placeholder={'dQw4w9WgXcQ\nhttps://www.youtube.com/watch?v=…'}
              multiline
              minRows={2}
              fullWidth
              value={links}
              onChange={(e) => setLinks(e.target.value)}
              error={bad.length > 0}
              helperText={bad.length ? `Not an id or a link: ${bad.join(', ')}` : 'One or more, separated by spaces or new lines. Other sites yt-dlp reads work too.'}
            />
            {items.some((w) => youtubeId(w)) && (
              <Stack direction="row" spacing={1} sx={{ mt: 1, overflowX: 'auto' }}>
                {items.map((w) => youtubeId(w)).filter((v): v is string => !!v).map((v, k) => (
                  <Box
                    key={`${v}-${k}`}
                    component="img"
                    src={`https://i.ytimg.com/vi/${v}/mqdefault.jpg`}
                    alt={v}
                    title={v}
                    sx={{ width: 160, borderRadius: 1, flex: '0 0 auto' }}
                  />
                ))}
              </Stack>
            )}

            <Stack direction="row" spacing={3} alignItems="center" sx={{ mt: 2, flexWrap: 'wrap', rowGap: 1 }}>
              <ToggleButtonGroup size="small" exclusive value={mode} onChange={(_, v) => v && setMode(v)}>
                <ToggleButton value="BOTH"><Movie fontSize="small" sx={{ mr: 0.5 }} />+<Headphones fontSize="small" sx={{ ml: 0.5, mr: 0.75 }} />Both</ToggleButton>
                <ToggleButton value="VIDEO"><Movie fontSize="small" sx={{ mr: 0.75 }} />Video</ToggleButton>
                <ToggleButton value="AUDIO"><Headphones fontSize="small" sx={{ mr: 0.75 }} />Audio</ToggleButton>
              </ToggleButtonGroup>
              <FormControlLabel
                control={<Switch checked={subtitles} onChange={(e) => setSubtitles(e.target.checked)} />}
                label={<Stack direction="row" alignItems="center" spacing={0.5}><Subtitles fontSize="small" /><span>Subtitles</span></Stack>}
              />
              {subtitles && mode !== 'AUDIO' && (
                <Tooltip title="Also put the subtitles inside the mp4 (they are saved as an .srt next to it either way)">
                  <FormControlLabel control={<Checkbox size="small" checked={embed} onChange={(e) => setEmbed(e.target.checked)} />} label="Inside the video" />
                </Tooltip>
              )}
              {mode !== 'VIDEO' && (
                <Tooltip title="Mono 64 kbps: speech sounds the same at half the size">
                  <FormControlLabel control={<Checkbox size="small" checked={voice} onChange={(e) => setVoice(e.target.checked)} />} label="Voice quality" />
                </Tooltip>
              )}
            </Stack>
            <Typography sx={{ fontSize: 12, color: 'text.secondary', mt: 0.5 }}>
              {mode === 'BOTH' && 'An mp4 and an mp3 of each video.'}
              {mode === 'VIDEO' && 'An mp4 of each video, no separate audio file.'}
              {mode === 'AUDIO' && 'Just an mp3 of each, no video.'}
              {subtitles ? ' Subtitles (.srt) in the spoken language: the uploader’s, else the automatic ones, cleaned.' : ' No subtitles.'}
            </Typography>

            <TextField
              label="Note text (optional)"
              placeholder="Empty uses the video's title"
              size="small"
              fullWidth
              value={text}
              onChange={(e) => setText(e.target.value)}
              inputProps={{ dir: dirOf(text), style: { textAlign: dirOf(text) === 'rtl' ? 'right' : 'left' } }}
              sx={{ mt: 2 }}
            />
            <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 2 }}>
              <Button
                variant="contained"
                startIcon={start.isPending ? <CircularProgress size={16} color="inherit" /> : <Download />}
                disabled={!items.length || bad.length > 0 || !!problem || start.isPending}
                onClick={() => start.mutate({ links: items, mode, subtitles, embedSubtitles: embed, voice, text })}
              >
                Download
              </Button>
              {start.isError && <Typography color="error" sx={{ fontSize: 13 }}>{errorDetail(start.error, 'Could not start.')}</Typography>}
            </Stack>
          </Paper>

          {state.isLoading && <CircularProgress />}
          {state.isError && <Alert severity="error">{errorDetail(state.error, 'Could not load the downloads.')}</Alert>}
          {state.data && (
            <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>
              Recent downloads
              <Typography component="span" sx={{ fontSize: 13, color: 'text.secondary', ml: 1 }}>
                {state.data.jobs.length ? `${state.data.jobs.length}, newest first` : 'none yet'}
              </Typography>
            </Typography>
          )}
          {state.data?.jobs.map((j) => <JobCard key={j.id} job={j} />)}
        </Box>
      </Box>
    </Box>
  );
}

function JobCard({ job }: { job: MediaCaptureJob }) {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const logBox = useRef<HTMLPreElement>(null);
  const [open, setOpen] = useState(job.status !== 'DONE');
  const cancel = useMutation({ mutationFn: () => cancelMediaCapture(job.id), onSettled: () => qc.invalidateQueries({ queryKey: ['media-capture'] }) });
  const imp = useMutation({ mutationFn: () => importNoteFrom(job.noteFile!, job.noteDir!) });
  const retry = useMutation({ mutationFn: () => retryMediaCapture(job.id), onSettled: () => qc.invalidateQueries({ queryKey: ['media-capture'] }) });
  const running = job.status === 'RUNNING' || job.status === 'QUEUED';

  useEffect(() => {
    if (logBox.current) logBox.current.scrollTop = logBox.current.scrollHeight;
  }, [job.log.length, open]);

  return (
    <Paper variant="outlined" sx={{ p: 1.5, mb: 1.5 }}>
      <Stack direction="row" spacing={1} alignItems="center" sx={{ flexWrap: 'wrap', rowGap: 0.5 }}>
        <Chip
          size="small"
          color={job.interrupted ? 'warning' : STATUS_COLOR[job.status]}
          label={job.interrupted ? 'interrupted' : job.status.toLowerCase()}
          icon={running ? <CircularProgress size={12} /> : undefined}
        />
        <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>
          {new Date(job.started).toLocaleString()}
          {job.finished && ` (${Math.max(1, Math.round((Date.parse(job.finished) - Date.parse(job.started)) / 60000))} min)`}
          {' · '}{MODE_LABEL[job.mode]}{job.subtitles ? ' · subtitles' : ''}
        </Typography>
        <Box sx={{ flex: 1 }} />
        {running && (
          <Button size="small" color="warning" startIcon={<Stop />} onClick={() => cancel.mutate()}>Stop</Button>
        )}
        {job.status === 'FAILED' && (
          <Button size="small" startIcon={<Replay />} disabled={retry.isPending} onClick={() => retry.mutate()}>Retry</Button>
        )}
        <Button size="small" onClick={() => setOpen((o) => !o)}>{open ? 'Hide log' : 'Show log'}</Button>
      </Stack>
      <Box sx={{ mt: 0.5 }}>
        {job.links.map((l) => (
          <Typography key={l} sx={{ fontSize: 13, wordBreak: 'break-all' }}>
            <a href={l} target="_blank" rel="noopener noreferrer">{l}</a>
          </Typography>
        ))}
      </Box>
      {job.note && (
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 1, flexWrap: 'wrap', rowGap: 0.5 }}>
          <Typography sx={{ fontSize: 13 }}>Note: <code>{job.note}</code></Typography>
          {imp.data ? (
            <Button size="small" onClick={() => navigate(`/${imp.data!.module}?select=${imp.data!.id}`)}>
              Open “{imp.data.displayTitle}”
            </Button>
          ) : (
            <Button size="small" startIcon={<MoveToInbox />} disabled={imp.isPending} onClick={() => imp.mutate()}>
              Import as a record
            </Button>
          )}
          {imp.isError && <Typography color="error" sx={{ fontSize: 13 }}>{errorDetail(imp.error, 'Import failed.')}</Typography>}
        </Stack>
      )}
      {open && job.log.length > 0 && (
        <Box
          component="pre"
          ref={logBox}
          sx={{ mt: 1, mb: 0, p: 1, maxHeight: 260, overflow: 'auto', bgcolor: 'grey.900', color: 'grey.100', fontSize: 12, borderRadius: 1, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}
        >
          {job.log.join('\n')}
        </Box>
      )}
    </Paper>
  );
}
