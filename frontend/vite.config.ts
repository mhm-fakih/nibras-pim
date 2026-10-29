import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { readFileSync, writeFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, resolve } from 'path';

const here = dirname(fileURLToPath(import.meta.url));
const pkg = JSON.parse(readFileSync(resolve(here, 'package.json'), 'utf8'));

// Auto-incrementing build number, persisted in build-number.json (gitignored). It bumps on every
// production build (`vite build`); dev just reads the current value. Exposed as __BUILD_NUMBER__.
function buildNumber(command: string): number {
  const file = resolve(here, 'build-number.json');
  let n = 0;
  try {
    n = JSON.parse(readFileSync(file, 'utf8')).build ?? 0;
  } catch {
    n = 0;
  }
  if (command === 'build') {
    n += 1;
    writeFileSync(file, JSON.stringify({ build: n }, null, 2) + '\n');
  }
  return n;
}

// Backend the dev server proxies to; override with BACKEND_PORT when the API runs elsewhere.
const backend = `http://127.0.0.1:${process.env.BACKEND_PORT ?? 8081}`;

// This release's Mint-style nickname (bump alongside the version in package.json).
const APP_CODENAME = 'Serenity';

export default defineConfig(({ command }) => ({
  base: './',
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version),
    __APP_CODENAME__: JSON.stringify(APP_CODENAME),
    __BUILD_NUMBER__: JSON.stringify(buildNumber(command)),
  },
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: backend, changeOrigin: true },
      '/feeds': { target: backend, changeOrigin: true },
    },
  },
}));
