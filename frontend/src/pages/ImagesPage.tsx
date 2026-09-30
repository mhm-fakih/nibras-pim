import { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  IconButton,
  InputAdornment,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import ChevronLeft from '@mui/icons-material/ChevronLeft';
import ChevronRight from '@mui/icons-material/ChevronRight';
import DeleteForever from '@mui/icons-material/DeleteForever';
import DriveFileRenameOutline from '@mui/icons-material/DriveFileRenameOutline';
import Undo from '@mui/icons-material/Undo';
import { deleteImage, imageFileUrl, listImages, renameImage, restoreImage,
  errorDetail,
} from '../api/client';
import { AuthImage } from '../components/AuthImage';
import { SideNav } from '../components/SideNav';

type LastAction =
  | { type: 'delete'; name: string; index: number; trash: string }
  | { type: 'rename'; from: string; to: string; index: number };

/** Split "photo.jpg" -> ["photo", ".jpg"]; no extension -> [name, ""]. */
function splitExt(name: string): [string, string] {
  const dot = name.lastIndexOf('.');
  return dot > 0 ? [name.slice(0, dot), name.slice(dot)] : [name, ''];
}

const SHORTCUTS: [string, string][] = [
  ['← / h', 'Previous image'],
  ['→ / l / Space', 'Next image'],
  ['r / F2 / Enter', 'Edit the name'],
  ['Enter', 'Rename + go next (while editing)'],
  ['Esc', 'Cancel editing'],
  ['d / Del', 'Delete (no confirm)'],
  ['u', 'Undo last delete/rename'],
];

const LAST_DIR_KEY = 'nibras.lastImageDir';

export function ImagesPage() {
  const [dirInput, setDirInput] = useState(() => localStorage.getItem(LAST_DIR_KEY) ?? '');
  const [dir, setDir] = useState<string | null>(null);
  const [images, setImages] = useState<string[]>([]);
  const [index, setIndex] = useState(0);
  const [base, setBase] = useState(''); // edited base name, WITHOUT extension
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [lastAction, setLastAction] = useState<LastAction | null>(null);
  const nameRef = useRef<HTMLInputElement>(null);

  const current = images[index];
  const ext = current ? splitExt(current)[1] : '';
  const proposed = base.trim() + ext;
  const dirty = Boolean(base.trim()) && proposed !== current;

  useEffect(() => {
    setBase(current ? splitExt(current)[0] : '');
  }, [current]);

  const focusRename = () => {
    const el = nameRef.current;
    if (!el) return;
    el.focus();
    el.setSelectionRange(0, el.value.length); // whole base (extension isn't in the field)
  };

  const fail = (e: unknown) => setError(errorDetail(e, 'Something went wrong.'));

  const load = async () => {
    setError(null);
    try {
      const res = await listImages(dirInput);
      localStorage.setItem(LAST_DIR_KEY, dirInput); // remember the last folder
      setDir(res.dir);
      setImages(res.images);
      setIndex(0);
      setLastAction(null);
      if (res.images.length === 0) setError('No images found in that folder.');
    } catch (e) {
      setDir(null);
      setImages([]);
      fail(e);
    }
  };

  // Auto-load the remembered folder on first open.
  useEffect(() => {
    if (dirInput) load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const go = (delta: number) =>
    setIndex((i) => Math.max(0, Math.min(images.length - 1, i + delta)));

  const del = async () => {
    if (!dir || !current) return;
    const delName = current;
    const delIndex = index;
    setBusy(true);
    setError(null);
    try {
      const trash = await deleteImage(dir, delName);
      const next = images.filter((_, i) => i !== delIndex);
      setImages(next);
      setIndex((i) => Math.min(i, Math.max(0, next.length - 1)));
      setLastAction({ type: 'delete', name: delName, index: delIndex, trash });
    } catch (e) {
      fail(e);
    } finally {
      setBusy(false);
    }
  };

  const undo = async () => {
    if (!dir || !lastAction || busy) return;
    setBusy(true);
    setError(null);
    try {
      if (lastAction.type === 'delete') {
        await restoreImage(dir, lastAction.name, lastAction.trash);
        setImages((arr) => {
          const copy = arr.slice();
          copy.splice(Math.min(lastAction.index, copy.length), 0, lastAction.name);
          return copy;
        });
      } else {
        await renameImage(dir, lastAction.to, lastAction.from);
        setImages((arr) => arr.map((n, i) => (i === lastAction.index ? lastAction.from : n)));
      }
      setIndex(lastAction.index);
      setLastAction(null);
    } catch (e) {
      fail(e);
    } finally {
      setBusy(false);
    }
  };

  const rename = async () => {
    if (!dir || !current || !dirty) return;
    const renName = current;
    const renIndex = index;
    setBusy(true);
    setError(null);
    try {
      const saved = await renameImage(dir, renName, proposed); // base + original extension
      setImages((arr) => arr.map((n, i) => (i === renIndex ? saved : n)));
      setLastAction({ type: 'rename', from: renName, to: saved, index: renIndex });
      nameRef.current?.blur();
      go(1); // advance to the next image after a successful rename
    } catch (e) {
      fail(e);
    } finally {
      setBusy(false);
    }
  };

  // Keyboard-only workflow. Shortcuts are ignored while typing in a text field
  // (so you can edit the name), except Escape which cancels and blurs.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const el = document.activeElement as HTMLElement | null;
      const typing = el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA');
      if (typing) {
        if (e.key === 'Escape') {
          setBase(current ? splitExt(current)[0] : '');
          el?.blur();
        }
        return;
      }
      if (!dir) return;
      if (e.key === 'u') {
        e.preventDefault();
        undo(); // works even when the folder is now empty
        return;
      }
      if (images.length === 0) return;
      switch (e.key) {
        case 'ArrowRight':
        case 'l':
        case ' ':
          e.preventDefault();
          go(1);
          break;
        case 'ArrowLeft':
        case 'h':
          go(-1);
          break;
        case 'Delete':
        case 'd':
          e.preventDefault();
          del();
          break;
        case 'r':
        case 'F2':
        case 'Enter':
          e.preventDefault();
          focusRename();
          break;
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [dir, images, index, current, busy, lastAction]);

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {/* Main */}
        <Box sx={{ flex: 1, overflow: 'auto', p: 4 }}>
          <Typography variant="h4" sx={{ fontWeight: 700, mb: 2 }}>
            Image renamer
          </Typography>

          <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
            <TextField
              fullWidth
              size="small"
              label="Directory path"
              placeholder="/my-home/Pictures/screenshots"
              value={dirInput}
              onChange={(e) => setDirInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && load()}
            />
            <Button variant="contained" onClick={load} disabled={!dirInput.trim()}>
              Load
            </Button>
          </Stack>

          {error && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}

          {dir && images.length > 0 && current && (
            <Box>
              <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
                <IconButton onClick={() => go(-1)} disabled={index === 0}>
                  <ChevronLeft />
                </IconButton>
                <Typography variant="body2" sx={{ minWidth: 90, textAlign: 'center' }}>
                  {index + 1} / {images.length}
                </Typography>
                <IconButton onClick={() => go(1)} disabled={index >= images.length - 1}>
                  <ChevronRight />
                </IconButton>
              </Stack>

              {/* Full path of the current image (read-only) so its location is unambiguous. */}
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{ display: 'block', mb: 1, fontFamily: 'monospace', wordBreak: 'break-all' }}
              >
                {dir}/{current}
              </Typography>

              <Box
                sx={{
                  border: 1,
                  borderColor: 'divider',
                  borderRadius: 1,
                  bgcolor: '#1e1e1e',
                  display: 'flex',
                  justifyContent: 'center',
                  alignItems: 'center',
                  height: '60vh',
                  mb: 2,
                }}
              >
                <AuthImage
                  key={imageFileUrl(dir, current)}
                  url={imageFileUrl(dir, current)}
                  alt={current}
                  sx={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }}
                />
              </Box>

              <Stack direction="row" spacing={1} alignItems="center">
                <TextField
                  fullWidth
                  size="small"
                  label="File name"
                  inputRef={nameRef}
                  value={base}
                  onChange={(e) => setBase(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && rename()}
                  inputProps={{ dir: 'auto', style: { fontFamily: 'monospace' } }}
                  InputProps={{
                    endAdornment: ext ? (
                      <InputAdornment position="end">
                        <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                          {ext}
                        </Typography>
                      </InputAdornment>
                    ) : undefined,
                  }}
                />
                <Button
                  variant="outlined"
                  startIcon={<DriveFileRenameOutline />}
                  onClick={rename}
                  disabled={busy || !dirty}
                >
                  Rename
                </Button>
                <Button
                  variant="contained"
                  color="error"
                  startIcon={<DeleteForever />}
                  onClick={del}
                  disabled={busy}
                >
                  Delete
                </Button>
                <Button
                  variant="text"
                  startIcon={<Undo />}
                  onClick={undo}
                  disabled={busy || !lastAction}
                  title={
                    lastAction
                      ? lastAction.type === 'delete'
                        ? `Undo delete of ${lastAction.name}`
                        : `Undo rename to ${lastAction.to}`
                      : 'Nothing to undo'
                  }
                >
                  Undo
                </Button>
              </Stack>
            </Box>
          )}

          {dir && images.length === 0 && !error && (
            <Stack direction="row" spacing={2} alignItems="center">
              <Typography color="text.secondary">
                All images handled — nothing left in this folder.
              </Typography>
              {lastAction && (
                <Button startIcon={<Undo />} onClick={undo} disabled={busy}>
                  Undo last
                </Button>
              )}
            </Stack>
          )}
        </Box>

        {/* Shortcuts (mouseless operation) */}
        <Box sx={{ width: 260, flexShrink: 0, borderLeft: 1, borderColor: 'divider', p: 2, bgcolor: '#f7f7f7', overflow: 'auto' }}>
          <Typography variant="overline" color="text.secondary">
            Keyboard shortcuts
          </Typography>
          <Stack spacing={1} sx={{ mt: 1 }}>
            {SHORTCUTS.map(([keys, desc]) => (
              <Box key={desc}>
                <Box
                  component="span"
                  sx={{
                    display: 'inline-block',
                    fontFamily: 'monospace',
                    fontSize: 12,
                    fontWeight: 700,
                    bgcolor: '#fff',
                    border: 1,
                    borderColor: 'divider',
                    borderRadius: 1,
                    px: 0.75,
                    py: 0.25,
                    mb: 0.25,
                  }}
                >
                  {keys}
                </Box>
                <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary' }}>
                  {desc}
                </Typography>
              </Box>
            ))}
          </Stack>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
            The extension is kept automatically — you only edit the name.
          </Typography>
        </Box>
      </Box>
    </Box>
  );
}
