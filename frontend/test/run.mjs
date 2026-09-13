/**
 * The frontend's unit tests.
 *
 * There is no test framework here on purpose: everything worth testing in this app is either a
 * pure function in `src/util` or needs a real browser, and pulling in vitest + jsdom to cover the
 * first group would be a large dependency for a handful of assertions.
 *
 * So: each `*.test.mjs` imports the real TypeScript source by path, esbuild bundles it (which is
 * what resolves and strips the types), and node runs the result. A failing test exits non-zero.
 *
 * Run with `npm test`.
 */
import { build } from 'esbuild';
import { readdirSync, mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const here = resolve(import.meta.dirname);
const out = mkdtempSync(join(tmpdir(), 'nibras-test-'));
const tests = readdirSync(here).filter((f) => f.endsWith('.test.mjs')).sort();

let failed = 0;
for (const t of tests) {
  console.log(`\n── ${t}`);
  const bundled = join(out, t);
  await build({
    entryPoints: [join(here, t)],
    outfile: bundled,
    bundle: true,
    format: 'esm',
    platform: 'node',
    logLevel: 'warning',
  });
  const mod = await import(pathToFileURL(bundled).href);
  failed += await mod.default();
}

console.log(failed ? `\n${failed} check(s) FAILED` : '\nAll checks passed.');
process.exit(failed ? 1 : 0);
