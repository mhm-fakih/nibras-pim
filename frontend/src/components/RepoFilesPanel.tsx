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
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import UploadFile from '@mui/icons-material/UploadFile';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import Folder from '@mui/icons-material/Folder';
import InsertDriveFile from '@mui/icons-material/InsertDriveFile';
import {
  RepoFile,
  deleteRepoFile,
  listRepoFiles,
  repoFileUrl,
  downloadFile,
  uploadRepoFile,
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

  const { data } = useQuery({ queryKey: key, queryFn: () => listRepoFiles(module, id) });

  const upload = useMutation({
    mutationFn: (file: File) => uploadRepoFile(module, id, repo, file),
    onSuccess: () => qc.invalidateQueries({ queryKey: key }),
  });
  const del = useMutation({
    mutationFn: (f: RepoFile) => deleteRepoFile(module, id, f.repo, f.name),
    onSuccess: () => qc.invalidateQueries({ queryKey: key }),
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
                    secondaryAction={
                      !f.directory && (
                        <IconButton
                          edge="end"
                          size="small"
                          color="error"
                          onClick={() => del.mutate(f)}
                          disabled={del.isPending}
                        >
                          <DeleteOutline fontSize="small" />
                        </IconButton>
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
    </Box>
  );
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
