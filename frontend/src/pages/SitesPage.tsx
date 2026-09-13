import { useEffect, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import PublishIcon from '@mui/icons-material/Publish';
import { generateSite, getSites, PublishableSearch, SiteResult,
  errorDetail,
} from '../api/client';
import { SideNav } from '../components/SideNav';

export function SitesPage() {
  const { data, isLoading } = useQuery({ queryKey: ['sites'], queryFn: getSites });
  const [out, setOut] = useState('');

  useEffect(() => {
    if (data?.outputDir && !out) setOut(data.outputDir);
  }, [data?.outputDir]); // eslint-disable-line react-hooks/exhaustive-deps

  const publishable = (data?.searches ?? []).filter(
    (s) => s.module && (s.queryType === 'form' || s.queryType === 'hql' || s.queryType === 'adhoc'),
  );

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, overflow: 'auto', p: 4, maxWidth: 900 }}>
        <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }}>
          Static website
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Publish a saved search (e.g. writings or notes tagged <code>published</code>) as a static
          site on disk: an index of cards with the short summary + cover thumbnail, a full page per
          record, and every image localised into <code>media/</code> — covers and embedded
          attachments are copied, external images are downloaded — so the site is self-contained and
          ready to host online.
        </Typography>

        <TextField
          fullWidth
          size="small"
          label="Output folder"
          value={out}
          onChange={(e) => setOut(e.target.value)}
          helperText="Where the site is written. Remembered for next time. ~ expands to your home folder."
          sx={{ mb: 3 }}
        />

        {isLoading && <CircularProgress />}

        {!isLoading && publishable.length === 0 && (
          <Alert severity="info">
            No publishable saved searches yet. Create a <b>form</b> or <b>HQL</b> saved search tied to
            a module (its report list) — e.g. an HQL like{' '}
            <code>from Writing w join w.tags t where t.name = 'published'</code>.
          </Alert>
        )}

        <Stack spacing={2}>
          {publishable.map((s) => (
            <SiteCard key={s.id} search={s} out={out} />
          ))}
        </Stack>
      </Box>
    </Box>
  );
}

function SiteCard({ search, out }: { search: PublishableSearch; out: string }) {
  const [result, setResult] = useState<SiteResult | null>(null);
  const gen = useMutation({
    mutationFn: () => generateSite(search.id, out || undefined),
    onSuccess: setResult,
  });

  return (
    <Card variant="outlined">
      <CardContent>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }} flexWrap="wrap" useFlexGap>
          <Typography sx={{ fontWeight: 600, flex: 1 }} dir="auto">
            {search.summary || '(unnamed report)'}
          </Typography>
          {search.module && <Chip size="small" label={search.module} />}
          <Chip size="small" variant="outlined" label={search.queryType} />
          <Button
            variant="contained"
            size="small"
            startIcon={gen.isPending ? <CircularProgress size={16} color="inherit" /> : <PublishIcon />}
            disabled={gen.isPending}
            onClick={() => gen.mutate()}
          >
            Generate
          </Button>
        </Stack>

        {gen.isError && (
          <Alert severity="error" sx={{ mt: 1 }}>
            {errorDetail(gen.error, 'Generation failed.')}
          </Alert>
        )}

        {result && (
          <Alert severity="success" sx={{ mt: 1 }}>
            Generated <b>{result.posts}</b> page{result.posts === 1 ? '' : 's'} and copied{' '}
            <b>{result.mediaFiles}</b> media file{result.mediaFiles === 1 ? '' : 's'} to{' '}
            <code>{result.outputDir}</code>. Open <code>{result.indexPath}</code> in a browser.
            {result.warnings.length > 0 && (
              <Box component="ul" sx={{ mt: 1, mb: 0, pl: 2 }}>
                {result.warnings.map((w, i) => (
                  <li key={i}>
                    <Typography variant="caption">{w}</Typography>
                  </li>
                ))}
              </Box>
            )}
          </Alert>
        )}
      </CardContent>
    </Card>
  );
}
