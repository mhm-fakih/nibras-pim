import { useEffect, useState } from 'react';
import { Box } from '@mui/material';
import type { SxProps, Theme } from '@mui/material';
import { fetchBlob } from '../api/client';

/**
 * Fetch an authenticated `/api` image URL as a blob and return an object URL (null while loading or
 * on failure — e.g. a 404 "no cover"). Revokes the object URL on URL change / unmount.
 */
export function useAuthImage(url: string): string | null {
  const [src, setSrc] = useState<string | null>(null);
  useEffect(() => {
    let active = true;
    let objectUrl: string | null = null;
    setSrc(null);
    fetchBlob(url)
      .then((blob) => {
        if (!active) return;
        objectUrl = URL.createObjectURL(blob);
        setSrc(objectUrl);
      })
      .catch(() => {
        /* leave src null → the image self-hides, matching the old onError behaviour */
      });
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [url]);
  return src;
}

/**
 * An `<img>` for a token-protected `/api` URL: fetches it with the bearer token and renders the
 * resulting object URL. Renders nothing until it loads (and if it fails), so it self-hides like a
 * missing cover.
 */
export function AuthImage({ url, alt, sx }: { url: string; alt?: string; sx?: SxProps<Theme> }) {
  const src = useAuthImage(url);
  if (!src) return null;
  return <Box component="img" src={src} alt={alt} sx={sx} />;
}
