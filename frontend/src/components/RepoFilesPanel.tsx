import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Box,
  Button,
  IconButton,
  Link as MuiLink,
  List,
  ListItem,
  ListItemText,
  Menu,
  MenuItem,
  Tooltip,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import UploadFile from '@mui/icons-material/UploadFile';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import DriveFileMove from '@mui/icons-material/DriveFileMove';
import Folder from '@mui/icons-material/Folder';
import InsertDriveFile from '@mui/icons-material/InsertDriveFile';
import ViewCarousel from '@mui/icons-material/ViewCarousel';
import { SlideSource, SlideViewerDialog } from './SlideViewer';
import {
  RepoFile,
  deleteRepoFile,
  transferRepoFile,
  listRepoFiles,
  repoFileUrl,
  downloadFile,
  uploadRepoFile,
  isSlideDoc,
} from '../api/client';

const REPO_LABEL: Record<number, string> = {
  1: 'Repo 1 · sandbox',
  2: 'Repo 2 · main',
  3: 'Repo 3 · dismissed',
};

/** Lists/uploads/deletes the record's files across repositories 1/2/3. `module` is the entity slug. */
export function RepoFilesPanel({ module, id }: { module: string; id: number }) {
  const qc = useQueryClient();
  const fileInput = useRef<HTMLInputElement>(null);
  const [repo, setRepo] = useState(1);
  const key = ['repo-files', module, id];
  const [slides, setSlides] = useState<SlideSource | null>(null);

  const { data } = useQuery({ queryKey: key, queryFn: () => listRepoFiles(module, id) });

  const upload = useMutation({
    mutationFn: (file: File) => uploadRepoFile(module, id, repo, file),
    onSuccess: () => qc.invalidateQueries({ queryKey: key }),
  });
  const del = useMutation({
    mutationFn: (f: RepoFile) => deleteRepoFile(module, id, f.repo, f.name),
    onSuccess: () => qc.invalidateQueries({ queryKey: key }),
  });

  // Move/copy between repositories: repo 2 → repo 1 is how a file reaches the phone (Syncthing
  // carries repo 1), and repo 1 → repo 2 takes it back off.
  const [moveMenu, setMoveMenu] = useState<{ anchor: HTMLElement; file: RepoFile } | null>(null);
  const [moveError, setMoveError] = useState<string | null>(null);
  const transfer = useMutation({
    mutationFn: (v: { f: RepoFile; to: number; move: boolean }) =>
      transferRepoFile(module, id, v.f.repo, v.to, v.f.name, v.move),
    onMutate: () => setMoveError(null),
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string; detail?: string } } })?.response?.data;
      setMoveError(msg?.detail || msg?.message || 'Could not move the file.');
    },
    onSettled: () => qc.invalidateQueries({ queryKey: key }),
  });

  const files = data ?? [];
  const repos = [1, 2, 3].filter((r) => files.some((f) => f.repo === r));

  return (
    <Box sx={{ mt: 2 }}>
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }} flexWrap="wrap" useFlexGap>
        <Typography variant="overline" color="text.secondary" sx={{ flex: 1 }}>
          Files ({files.length})
        </Typography>
        <TextField
          select
          size="small"
          label="Upload to"
          value={repo}
          onChange={(e) => setRepo(Number(e.target.value))}
          sx={{ width: 150 }}
        >
          {[1, 2, 3].map((r) => (
            <MenuItem key={r} value={r}>
              {REPO_LABEL[r]}
            </MenuItem>
          ))}
        </TextField>
        <Button
          size="small"
          startIcon={<UploadFile />}
          onClick={() => fileInput.current?.click()}
          disabled={upload.isPending}
        >
          Upload
        </Button>
        <input
          ref={fileInput}
          type="file"
          hidden
          onChange={(e) => {
            const f = e.target.files?.[0];
            if (f) upload.mutate(f);
            e.target.value = '';
          }}
        />
      </Stack>

      {files.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          No files yet. Upload one above, or use the “Open folder” buttons to add them on disk.
        </Typography>
      ) : (
        repos.map((r) => (
          <Box key={r} sx={{ mb: 1 }}>
            <Typography variant="caption" sx={{ fontWeight: 700, color: 'text.secondary' }}>
              {REPO_LABEL[r] ?? `Repo ${r}`}
            </Typography>
            <List dense disablePadding>
              {files
                .filter((f) => f.repo === r)
                .map((f: RepoFile) => (
                  <ListItem
                    key={`${f.repo}-${f.name}`}
                    disableGutters
                    sx={{ pr: f.directory ? 0 : isSlideDoc(f.name) ? 14 : 10 }}
                    secondaryAction={
                      !f.directory && (
                        <>
                        {isSlideDoc(f.name) && (
                          <Tooltip title="Preview as slides">
                            <IconButton
                              size="small"
                              onClick={() => setSlides({ module, id, repo: f.repo, path: f.name })}
                              sx={{ mr: 0.5 }}
                            >
                              <ViewCarousel fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        )}
                        <Tooltip title="Move or copy to another repository">
                          <IconButton
                            size="small"
                            onClick={(e) => setMoveMenu({ anchor: e.currentTarget, file: f })}
                            disabled={transfer.isPending}
                            sx={{ mr: 0.5 }}
                          >
                            <DriveFileMove fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <IconButton
                          edge="end"
                          size="small"
                          color="error"
                          onClick={() => del.mutate(f)}
                          disabled={del.isPending}
                        >
                          <DeleteOutline fontSize="small" />
                        </IconButton>
                        </>
                      )
                    }
                  >
                    {f.directory ? (
                      <Folder fontSize="small" sx={{ mr: 1, color: 'text.secondary' }} />
                    ) : (
                      <InsertDriveFile fontSize="small" sx={{ mr: 1, color: 'text.secondary' }} />
                    )}
                    <ListItemText
                      primary={
                        f.directory ? (
                          f.name
                        ) : (
                          <MuiLink
                            component="button"
                            type="button"
                            onClick={() => downloadFile(repoFileUrl(module, id, f.repo, f.name), f.name)}
                            underline="hover"
                          >
                            {f.name}
                          </MuiLink>
                        )
                      }
                      secondary={
                        f.directory
                          ? 'folder'
                          : `${formatSize(f.size)}${
                              f.modifiedAt ? ` · ${new Date(f.modifiedAt).toLocaleString()}` : ''
                            }`
                      }
                    />
                  </ListItem>
                ))}
            </List>
          </Box>
        ))
      )}
      {moveError && (
        <Typography variant="body2" color="error" sx={{ mt: 0.5 }}>
          {moveError}
        </Typography>
      )}
      <SlideViewerDialog source={slides} onClose={() => setSlides(null)} />
      <Menu anchorEl={moveMenu?.anchor} open={!!moveMenu} onClose={() => setMoveMenu(null)}>
        {moveMenu &&
          [1, 2, 3]
            .filter((r) => r !== moveMenu.file.repo)
            .flatMap((r) => [
              <MenuItem
                key={`m${r}`}
                onClick={() => {
                  transfer.mutate({ f: moveMenu.file, to: r, move: true });
                  setMoveMenu(null);
                }}
              >
                Move to {REPO_LABEL[r]}
                {r === 1 ? ' (syncs to the phone)' : ''}
              </MenuItem>,
              <MenuItem
                key={`c${r}`}
                onClick={() => {
                  transfer.mutate({ f: moveMenu.file, to: r, move: false });
                  setMoveMenu(null);
                }}
              >
                Copy to {REPO_LABEL[r]}
              </MenuItem>,
            ])}
      </Menu>
    </Box>
  );
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
