import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  IconButton,
  InputAdornment,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import ArrowBack from '@mui/icons-material/ArrowBack';
import Bookmark from '@mui/icons-material/Bookmark';
import BookmarkAdd from '@mui/icons-material/BookmarkAdd';
import Download from '@mui/icons-material/Download';
import Movie from '@mui/icons-material/Movie';
import Refresh from '@mui/icons-material/Refresh';
import Search from '@mui/icons-material/Search';
import { SideNav } from '../components/SideNav';
import { VideoItem, VideoMark, addVideoMark, getVideoMarks, listVideos, mediaUrl } from '../api/client';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { fmtBytes } from '../util/bytes';
import { errorDetail } from '../util/errors';
import { linkProps } from '../util/links';
import { dirOf } from '../util/rtl';

// ---- watched position, per browser (the phone keeps its own) -------------------------------------

const POS_KEY = 'nibras.videos.pos';

type Positions = Record<string, { at: number; dur: number }>;

function loadPositions(): Positions {
  try {
    return JSON.parse(localStorage.getItem(POS_KEY) ?? '{}') as Positions;
  } catch {
    return {};
  }
}

function savePosition(path: string, at: number, dur: number) {
  try {
    const all = loadPositions();
    all[path] = { at, dur };
    localStorage.setItem(POS_KEY, JSON.stringify(all));
  } catch {
    /* private window: nothing remembered */
  }
}

export function formatDuration(ms: number): string {
  const t = Math.max(0, Math.round(ms / 1000));
  const h = Math.floor(t / 3600);
  const m = Math.floor((t % 3600) / 60);
  const s = t % 60;
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}` : `${m}:${String(s).padStart(2, '0')}`;
}

const rtf = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });

function ago(iso: string): string {
  const sec = (new Date(iso).getTime() - Date.now()) / 1000;
  const steps: [Intl.RelativeTimeFormatUnit, number][] = [
    ['year', 31536000], ['month', 2592000], ['week', 604800], ['day', 86400], ['hour', 3600], ['minute', 60],
  ];
  for (const [unit, s] of steps) if (Math.abs(sec) >= s) return rtf.format(Math.round(sec / s), unit);
  return rtf.format(0, 'minute');
}

const watchRoute = (path: string) => `/videos?v=${encodeURIComponent(path)}`;

/**
 * The phone's Videos page on the web, YouTube-style: every video under the videos folder
 * (repository 1 — the folder the phone syncs — unless Settings name another), newest first, as
 * thumbnail cards with their length and how far they were watched; folder chips and a search
 * above. A card opens the watch view: the player, its bookmarks (the phone player's `.tmk`, so
 * marks made on either side show on both), and the next videos from the same folder beside it.
 */
export function VideosPage() {
  const [params] = useSearchParams();
  const watching = params.get('v');
  const list = useQuery({ queryKey: ['videos'], queryFn: listVideos, staleTime: 60_000 });
  useDocumentTitle(watching ? watching.split('/').pop()?.replace(/\.[^.]+$/, '') : 'Videos');

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, minWidth: 0, overflowY: 'auto' }}>
        {list.isLoading && (
          <Box sx={{ display: 'grid', placeItems: 'center', height: '60vh' }}>
            <CircularProgress />
          </Box>
        )}
        {list.isError && (
          <Typography color="error" sx={{ p: 3 }}>
            {errorDetail(list.error, 'Could not list the videos.')}
          </Typography>
        )}
        {list.data &&
          (watching ? (
            <WatchView videos={list.data.videos} path={watching} />
          ) : (
            <Gallery videos={list.data.videos} root={list.data.root} refetch={() => list.refetch()} refreshing={list.isFetching} />
          ))}
      </Box>
    </Box>
  );
}

function Gallery({ videos, root, refetch, refreshing }: { videos: VideoItem[]; root: string; refetch: () => void; refreshing: boolean }) {
  const [folder, setFolder] = useState<string | null>(null);
  const [q, setQ] = useState('');
  const positions = useMemo(loadPositions, []);
  // Folders in order of their newest video, so the one wanted is usually near the start.
  const folders = useMemo(() => [...new Set(videos.map((v) => v.folder))], [videos]);
  const shown = useMemo(() => {
    const words = q.toLowerCase().split(/\s+/).filter(Boolean);
    return videos.filter(
      (v) =>
        (folder == null || v.folder === folder) &&
        words.every((w) => v.name.toLowerCase().includes(w) || v.folder.toLowerCase().includes(w)),
    );
  }, [videos, folder, q]);

  return (
    <Box sx={{ p: 2 }}>
      <Stack direction="row" alignItems="center" spacing={1.5} sx={{ mb: 1.5, flexWrap: 'wrap', rowGap: 1 }}>
        <Typography variant="h6" sx={{ fontWeight: 500 }}>
          Videos
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {shown.length} {shown.length === 1 ? 'video' : 'videos'}
        </Typography>
        <Box sx={{ flex: 1 }} />
        <TextField
          size="small"
          placeholder="Search videos"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          sx={{ width: { xs: '100%', sm: 320 } }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <Search fontSize="small" />
              </InputAdornment>
            ),
          }}
        />
        <Tooltip title="Look for new videos">
          <span>
            <IconButton onClick={refetch} disabled={refreshing}>
              <Refresh />
            </IconButton>
          </span>
        </Tooltip>
      </Stack>
      {folders.length > 1 && (
        <Stack direction="row" spacing={1} sx={{ mb: 2, overflowX: 'auto', pb: 0.5 }}>
          <Chip label="All" color={folder == null ? 'primary' : 'default'} onClick={() => setFolder(null)} />
          {folders.map((f) => (
            <Chip
              key={f}
              label={f || '(top folder)'}
              dir={dirOf(f)}
              color={folder === f ? 'primary' : 'default'}
              onClick={() => setFolder(folder === f ? null : f)}
              sx={{ maxWidth: 260 }}
            />
          ))}
        </Stack>
      )}
      {videos.length === 0 ? (
        <Typography color="text.secondary" sx={{ mt: 6, textAlign: 'center' }}>
          No videos under {root}.
        </Typography>
      ) : (
        <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 2, rowGap: 3 }}>
          {shown.map((v) => (
            <VideoCard key={v.path} v={v} pos={positions[v.path]} />
          ))}
        </Box>
      )}
    </Box>
  );
}

function Thumb({ v, pos, compact }: { v: VideoItem; pos?: { at: number; dur: number }; compact?: boolean }) {
  const [failed, setFailed] = useState(false);
  const dur = v.durationMs ?? (pos ? pos.dur * 1000 : 0);
  return (
    <Box sx={{ position: 'relative', aspectRatio: '16 / 9', borderRadius: compact ? 1.5 : 3, overflow: 'hidden', bgcolor: '#202020', flexShrink: 0 }}>
      <Movie sx={{ position: 'absolute', inset: 0, m: 'auto', fontSize: compact ? 28 : 48, color: '#555' }} />
      {!failed && (
        <Box
          component="img"
          src={mediaUrl(v.thumb)}
          loading="lazy"
          alt=""
          onError={() => setFailed(true)}
          sx={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover' }}
        />
      )}
      {dur > 0 && (
        <Typography
          variant="caption"
          sx={{ position: 'absolute', bottom: 6, right: 6, px: 0.6, borderRadius: 0.5, bgcolor: 'rgba(0,0,0,.8)', color: '#fff', fontWeight: 700 }}
        >
          {formatDuration(dur)}
        </Typography>
      )}
      {pos && pos.dur > 0 && pos.at > 5 && (
        <Box sx={{ position: 'absolute', left: 0, right: 0, bottom: 0, height: 4, bgcolor: 'rgba(255,255,255,.4)' }}>
          <Box sx={{ height: '100%', width: `${Math.min(100, (pos.at / pos.dur) * 100)}%`, bgcolor: '#ff3d3d' }} />
        </Box>
      )}
    </Box>
  );
}

function VideoCard({ v, pos }: { v: VideoItem; pos?: { at: number; dur: number } }) {
  const navigate = useNavigate();
  return (
    <Box {...linkProps(watchRoute(v.path), () => navigate(watchRoute(v.path)))} sx={{ color: 'inherit', textDecoration: 'none', display: 'block', '&:hover img': { filter: 'brightness(1.08)' } }}>
      <Thumb v={v} pos={pos} />
      <Typography
        variant="subtitle2"
        dir={dirOf(v.name)}
        sx={{ mt: 1, fontWeight: 600, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', textAlign: 'start' }}
        title={v.name}
      >
        {v.name}
      </Typography>
      <Stack direction="row" alignItems="center" spacing={0.5} sx={{ color: 'text.secondary' }}>
        <Typography variant="caption" noWrap sx={{ minWidth: 0 }}>
          {[v.folder, ago(v.modified), fmtBytes(v.size)].filter(Boolean).join(' · ')}
        </Typography>
        {v.bookmarks > 0 && (
          <>
            <Bookmark sx={{ fontSize: 12 }} />
            <Typography variant="caption">{v.bookmarks}</Typography>
          </>
        )}
      </Stack>
    </Box>
  );
}

function WatchView({ videos, path }: { videos: VideoItem[]; path: string }) {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const video = videos.find((v) => v.path === path);
  const player = useRef<HTMLVideoElement>(null);
  const [markText, setMarkText] = useState('');
  const marks = useQuery({ queryKey: ['video-marks', path], queryFn: () => getVideoMarks(path), enabled: !!video });
  const add = useMutation({
    mutationFn: (m: { ms: number; text: string }) => addVideoMark(path, m.ms, m.text),
    onSuccess: (list) => {
      qc.setQueryData(['video-marks', path], list);
      void qc.invalidateQueries({ queryKey: ['videos'] });
      setMarkText('');
    },
  });

  // Up next: the rest of this folder in order, then the newest elsewhere.
  const next = useMemo(() => {
    if (!video) return [];
    const same = videos.filter((v) => v.folder === video.folder && v.path !== path).sort((a, b) => a.name.localeCompare(b.name));
    const other = videos.filter((v) => v.folder !== video.folder);
    return [...same, ...other].slice(0, 30);
  }, [videos, video, path]);
  const positions = useMemo(loadPositions, [path]);

  // Resume where it was left (unless it was nearly over), and remember as it plays.
  useEffect(() => {
    const el = player.current;
    if (!el) return;
    const p = loadPositions()[path];
    const onMeta = () => {
      if (p && p.at > 5 && p.at < el.duration - 10) el.currentTime = p.at;
    };
    let last = 0;
    const onTime = () => {
      if (Math.abs(el.currentTime - last) >= 5) {
        last = el.currentTime;
        savePosition(path, el.currentTime, el.duration || 0);
      }
    };
    const onStop = () => savePosition(path, el.currentTime, el.duration || 0);
    el.addEventListener('loadedmetadata', onMeta);
    el.addEventListener('timeupdate', onTime);
    el.addEventListener('pause', onStop);
    return () => {
      onStop();
      el.removeEventListener('loadedmetadata', onMeta);
      el.removeEventListener('timeupdate', onTime);
      el.removeEventListener('pause', onStop);
    };
  }, [path]);

  const seek = (m: VideoMark) => {
    const el = player.current;
    if (!el) return;
    el.currentTime = m.ms / 1000;
    void el.play();
  };
  const bookmarkNow = () => {
    const el = player.current;
    if (el) add.mutate({ ms: Math.round(el.currentTime * 1000), text: markText.trim() });
  };

  if (!video) {
    return (
      <Box sx={{ p: 3 }}>
        <Typography>That video is no longer in the videos folder.</Typography>
        <Button startIcon={<ArrowBack />} onClick={() => navigate('/videos')} sx={{ mt: 1 }}>
          All videos
        </Button>
      </Box>
    );
  }

  return (
    <Box sx={{ p: 2, display: 'flex', gap: 3, flexDirection: { xs: 'column', lg: 'row' }, alignItems: 'flex-start' }}>
      <Box sx={{ flex: 1, minWidth: 0, width: '100%' }}>
        <Box sx={{ bgcolor: '#000', borderRadius: 3, overflow: 'hidden', aspectRatio: '16 / 9', maxHeight: '72vh' }}>
          <video
            ref={player}
            key={path}
            src={mediaUrl(video.src)}
            controls
            autoPlay
            preload="metadata"
            style={{ width: '100%', height: '100%', display: 'block', background: '#000' }}
          />
        </Box>
        <Typography variant="h6" dir={dirOf(video.name)} sx={{ mt: 1.5, fontWeight: 600, textAlign: 'start', wordBreak: 'break-word' }}>
          {video.name}
        </Typography>
        <Stack direction="row" alignItems="center" spacing={1} sx={{ mt: 0.5, flexWrap: 'wrap', rowGap: 1 }}>
          <Typography variant="body2" color="text.secondary" sx={{ flex: 1, minWidth: 200 }}>
            {[video.folder, ago(video.modified), fmtBytes(video.size)].filter(Boolean).join(' · ')}
          </Typography>
          <Button size="small" startIcon={<ArrowBack />} {...linkProps('/videos', () => navigate('/videos'))}>
            All videos
          </Button>
          <Button size="small" startIcon={<Download />} component="a" href={`${mediaUrl(video.src)}&download=true`}>
            Download
          </Button>
        </Stack>

        <Box sx={{ mt: 2, p: 1.5, borderRadius: 2, bgcolor: 'action.hover' }}>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
              Bookmarks
            </Typography>
            <Typography variant="caption" color="text.secondary">
              shared with the phone player
            </Typography>
          </Stack>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
            <TextField
              size="small"
              placeholder="What is here (optional)"
              value={markText}
              onChange={(e) => setMarkText(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') bookmarkNow();
              }}
              inputProps={{ dir: 'auto' }}
              sx={{ flex: 1, maxWidth: 420 }}
            />
            <Button variant="outlined" size="small" startIcon={<BookmarkAdd />} onClick={bookmarkNow} disabled={add.isPending}>
              Bookmark here
            </Button>
          </Stack>
          {add.isError && (
            <Typography variant="body2" color="error">
              {errorDetail(add.error, 'The bookmark was not saved.')}
            </Typography>
          )}
          {(marks.data ?? []).length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              No bookmarks yet.
            </Typography>
          ) : (
            <Stack spacing={0.25}>
              {marks.data!.map((m, i) => (
                <Stack
                  key={`${m.ms}-${i}`}
                  direction="row"
                  spacing={1}
                  alignItems="baseline"
                  onClick={() => seek(m)}
                  sx={{ cursor: 'pointer', borderRadius: 1, px: 0.5, '&:hover': { bgcolor: 'action.selected' } }}
                >
                  <Typography variant="body2" sx={{ color: 'primary.main', fontVariantNumeric: 'tabular-nums', minWidth: 56 }}>
                    {formatDuration(m.ms)}
                  </Typography>
                  <Typography variant="body2" dir={dirOf(m.text)} sx={{ textAlign: 'start' }}>
                    {m.text}
                  </Typography>
                </Stack>
              ))}
            </Stack>
          )}
        </Box>
      </Box>

      <Box sx={{ width: { xs: '100%', lg: 400 }, flexShrink: 0 }}>
        <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
          Up next
        </Typography>
        <Stack spacing={1.25}>
          {next.map((v) => (
            <Box
              key={v.path}
              {...linkProps(watchRoute(v.path), () => navigate(watchRoute(v.path)))}
              sx={{ display: 'flex', gap: 1, color: 'inherit', textDecoration: 'none', borderRadius: 1.5, '&:hover': { bgcolor: 'action.hover' } }}
            >
              <Box sx={{ width: 168, flexShrink: 0 }}>
                <Thumb v={v} pos={positions[v.path]} compact />
              </Box>
              <Box sx={{ minWidth: 0, py: 0.25 }}>
                <Typography
                  variant="body2"
                  dir={dirOf(v.name)}
                  sx={{ fontWeight: 600, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', textAlign: 'start' }}
                  title={v.name}
                >
                  {v.name}
                </Typography>
                <Typography variant="caption" color="text.secondary" noWrap component="div">
                  {v.folder}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {ago(v.modified)}
                </Typography>
              </Box>
            </Box>
          ))}
        </Stack>
      </Box>
    </Box>
  );
}
