import { useSearchParams } from 'react-router-dom';
import { Box, Typography } from '@mui/material';
import { SlideDeckView } from '../components/SlideViewer';
import { useDocumentTitle } from '../hooks/useDocumentTitle';

/** A record's document as slides on a page of its own (/slides?module=&id=&repo=&path=). */
export function SlidesPage() {
  const [params] = useSearchParams();
  const module = params.get('module') ?? '';
  const id = Number(params.get('id'));
  const repo = Number(params.get('repo') ?? 1);
  const path = params.get('path') ?? '';
  useDocumentTitle(path ? path.split('/').pop() : undefined);
  if (!module || !id || !path) {
    return <Typography sx={{ p: 3 }}>No document given.</Typography>;
  }
  return (
    <Box sx={{ height: '100vh' }}>
      <SlideDeckView source={{ module, id, repo, path }} />
    </Box>
  );
}
