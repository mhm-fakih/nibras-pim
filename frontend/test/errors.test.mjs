import { errorDetail } from '../src/util/errors';

/**
 * `errorDetail` is the sentence every failed request in the app ends up wearing, and it is the one
 * piece of code that has to be right precisely when nothing else is working. These checks are
 * mostly about the *unreachable* cases, because those are the ones that were wrong: fourteen call
 * sites each ended in `?? 'Could not do the thing.'`, which blamed the operation for a backend
 * that was never reached at all.
 */
export default function run() {
  let pass = 0;
  let fail = 0;
  const check = (name, got, want) => {
    const ok = typeof want === 'function' ? want(got) : got === want;
    if (ok) {
      pass++;
      console.log('  ok  ', name);
    } else {
      fail++;
      console.log('  FAIL', name, '\n        got:', JSON.stringify(got));
    }
  };

  // The bug this exists for: the backend was down, and the UI blamed the URL being imported.
  check(
    'a down backend does not blame the operation',
    errorDetail({ code: 'ERR_NETWORK', request: {} }, 'Could not fetch that page.'),
    (s) => !s.includes('Could not fetch that page') && /reach Nibras/.test(s),
  );

  // Same real cause, different shape: Caddy is up, Tomcat is not.
  check(
    '502 from the proxy names the backend, not the page',
    errorDetail({ response: { status: 502, data: '<html>502 Bad Gateway</html>' } }, 'Could not fetch that page.'),
    (s) => /not answering/.test(s) && !s.includes('Could not fetch that page'),
  );

  // When the server DID answer with a reason, that reason beats everything this function knows.
  check(
    "the server's own words win",
    errorDetail(
      { response: { status: 400, data: { detail: 'Refusing to fetch x — private address.' } } },
      'Could not fetch that page.',
    ),
    'Refusing to fetch x — private address.',
  );

  check(
    'a `message` body is read as well as a ProblemDetail `detail`',
    errorDetail({ response: { status: 400, data: { message: 'That is not a valid URL.' } } }, 'nope'),
    'That is not a valid URL.',
  );

  // A proxy or container error page is a STRING. Reading `.detail` through it must neither throw
  // nor let markup reach the screen.
  check(
    'an HTML error body never reaches the user',
    errorDetail({ response: { status: 500, data: '<!doctype html><title>Whitelabel</title>' } }, 'Save failed.'),
    (s) => !s.includes('<') && /HTTP 500/.test(s),
  );

  check(
    '403 explains the permission rather than the operation',
    errorDetail({ response: { status: 403, data: {} } }, 'Save failed.'),
    (s) => /permission/i.test(s),
  );

  check(
    'an unnamed 4xx still carries the operation and the code',
    errorDetail({ response: { status: 418, data: {} } }, 'Save failed.'),
    'Save failed. (HTTP 418)',
  );

  // Different causes, different fixes: wait vs. start the server vs. turn on wifi.
  check(
    'a timeout is distinguished from a refusal',
    errorDetail({ code: 'ECONNABORTED' }, 'Save failed.'),
    (s) => /too long/.test(s),
  );

  Object.defineProperty(globalThis, 'navigator', { value: { onLine: false }, configurable: true });
  check(
    'being offline is named as being offline',
    errorDetail({ code: 'ERR_NETWORK' }, 'Save failed.'),
    (s) => /offline/.test(s),
  );
  Object.defineProperty(globalThis, 'navigator', { value: { onLine: true }, configurable: true });

  // Whatever arrives, the result is shown to a person — never "undefined" or "[object Object]".
  for (const junk of [undefined, null, 'a string', new Error('boom'), {}]) {
    check(
      `junk input (${String(junk)}) still yields a sentence`,
      errorDetail(junk, 'Save failed.'),
      (s) => typeof s === 'string' && s.length > 10 && !s.includes('undefined') && !s.includes('[object'),
    );
  }

  console.log(`  ${pass} passed, ${fail} failed`);
  return fail;
}
