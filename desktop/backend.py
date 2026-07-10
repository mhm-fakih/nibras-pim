"""Optional nibras backend client.

Its ONLY job is to refresh the type/category *codes* so a capture maps to the right backend module
and department when it's later imported. It is purely additive: every call fails soft (returns None
/ empty), so a missing, misconfigured, or offline backend never affects note-taking — the app keeps
using its cached or default codes. No third-party dependencies (stdlib urllib only).

Endpoints (see the backend):
  POST /api/auth/login                        -> { token, user }         (JWT bearer)
  GET  /api/modules                           -> [ { code, label, moduleKey, enabled, sortOrder } ]
  GET  /api/departments?bookmarked=true&size= -> { content: [ { code, name, summary, bookmarked } ] }
"""

from __future__ import annotations

import json
import urllib.error
import urllib.request

_TIMEOUT = 6  # seconds — short so a slow/unreachable server never stalls a sync


class BackendClient:
    def __init__(self, base_url: str, user: str, password: str):
        self.base = base_url.rstrip("/")
        self.user = user
        self.password = password
        self.token: str | None = None

    def _request(self, method: str, path: str, data: dict | None = None, auth: bool = True):
        headers = {"Accept": "application/json"}
        body = None
        if data is not None:
            body = json.dumps(data).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if auth and self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        req = urllib.request.Request(self.base + path, data=body, headers=headers, method=method)
        with urllib.request.urlopen(req, timeout=_TIMEOUT) as resp:
            return json.loads(resp.read().decode("utf-8"))

    def login(self) -> bool:
        res = self._request("POST", "/api/auth/login",
                            {"username": self.user, "password": self.password}, auth=False)
        self.token = (res or {}).get("token")
        return bool(self.token)

    def modules(self) -> list[tuple[str, str]]:
        """(code, label) for enabled modules that carry a code, in sortOrder — the capture 'types'."""
        data = self._request("GET", "/api/modules") or []
        mods = [m for m in data if m.get("code") and m.get("enabled", True)]
        mods.sort(key=lambda m: m.get("sortOrder") or 0)
        return [(m["code"], m.get("label") or m.get("moduleKey") or m["code"]) for m in mods]

    def bookmarked_departments(self) -> list[tuple[str, str]]:
        """(code, name) for bookmarked departments — the capture 'categories'. The department's name
        is its summary column (see the backend entity)."""
        data = self._request("GET", "/api/departments?bookmarked=true&size=500") or {}
        content = data.get("content", data if isinstance(data, list) else [])
        out: list[tuple[str, str]] = []
        for d in content:
            code = d.get("code")
            if not code:
                continue
            name = d.get("name") or d.get("summary") or d.get("displayTitle") or code
            out.append((code, name))
        return out


def fetch_taxonomy(base_url: str, user: str, password: str):
    """Return (types, categories) as [(code, name)] lists fetched from the backend, or (None, None)
    on ANY failure — no URL, network down, bad credentials, server error, malformed response. The
    caller then keeps whatever codes it already has (cached or default). Never raises."""
    if not base_url:
        return None, None
    try:
        client = BackendClient(base_url, user, password)
        if not client.login():
            return None, None
        return client.modules(), client.bookmarked_departments()
    except (urllib.error.URLError, OSError, ValueError, KeyError, TypeError):
        return None, None
