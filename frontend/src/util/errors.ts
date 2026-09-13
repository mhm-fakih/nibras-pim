/**
 * What to tell the user when a request failed.
 *
 * Separate from `api/client` on purpose: that module reads `document.baseURI` at import time, and
 * this is the one piece of the app that has to be right when nothing else is working — so it must
 * be exercisable outside a browser.
 */

/** The API base, named in the unreachable-server message so it is clear WHAT could not be reached. */
function apiBase(): string {
  if (typeof document === 'undefined') return '/api';
  return `${new URL(document.baseURI).pathname}api`;
}

/**
 * The server's own words for a failed request, or — when there were none — an honest account of
 * why there were none.
 *
 * <h4>Why the "no response" case gets so much attention</h4>
 * Every call site used to end in `?? 'Could not do the thing.'`, and that string was shown for two
 * completely different situations: the backend considered the request and refused it, or the
 * backend was never reached at all. The second is the common one in this deployment — Tomcat
 * restarting after a redeploy, the laptop asleep, Caddy up with nothing behind it — and blaming
 * the operation for it actively misleads. "Could not fetch that page" sent someone looking at a
 * perfectly good URL while the real answer was that Nibras itself was down.
 *
 * So: a ProblemDetail wins, a status without one is named by what that status means, and no
 * response at all is reported as what it is — with the distinction between "this device is
 * offline", "it never answered", and "it took too long", because those have different fixes.
 *
 * `fallback` describes the OPERATION ("Could not fetch that page.") and is only used where the
 * operation is genuinely what failed. It is deliberately dropped from the unreachable-server
 * messages: appending it there produces the self-contradicting "Could not fetch that page. The
 * server did not answer."
 */
export function errorDetail(e: unknown, fallback: string): string {
  const err = e as {
    code?: string;
    response?: { status?: number; data?: unknown };
  };
  const res = err?.response;

  // A ProblemDetail body. Guarded as an object because an error from a proxy (Caddy's 502 page) or
  // a container (Tomcat's whitelabel) arrives as an HTML *string*, where `.detail` would be
  // undefined on a value that isn't safe to read through in the first place.
  const data = res?.data;
  if (data && typeof data === 'object') {
    const body = data as { detail?: string; title?: string; message?: string; error?: string };
    const detail = body.detail || body.message || body.error || body.title;
    if (detail && typeof detail === 'string') return detail;
  }

  const status = res?.status;
  if (status) {
    // A status the server chose, but with nothing readable in the body. Name what the status
    // means rather than showing a bare number, which tells a user nothing.
    if (status === 401) return 'Your session has expired. Sign in again.';
    if (status === 403) return "Not allowed — your account doesn't have permission for this.";
    if (status === 404) return `${fallback} The server has no such endpoint or record (HTTP 404).`;
    if (status === 413) return 'That is too large for the server to accept.';
    // 502/503/504 come from the reverse proxy, not the app: Caddy is up and Tomcat is not. This is
    // exactly the "backend was down" case, and it is worth saying so plainly.
    if (status === 502 || status === 503 || status === 504) {
      return 'Nibras is not answering — the backend is down, restarting, or still starting up.';
    }
    if (status >= 500) return `The server hit an error handling this (HTTP ${status}). Check the server log.`;
    return `${fallback} (HTTP ${status})`;
  }

  // No response at all: the request never reached a server, or none came back.
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    return 'This device is offline — the request never left the browser.';
  }
  if (err?.code === 'ECONNABORTED' || err?.code === 'ETIMEDOUT') {
    return 'The server took too long to answer. It may still be working on it.';
  }
  if (err?.code === 'ERR_CANCELED') return 'The request was cancelled.';
  return `Could not reach Nibras at ${apiBase()} — the backend may be down, or the connection `
    + 'dropped. Nothing was sent.';
}
