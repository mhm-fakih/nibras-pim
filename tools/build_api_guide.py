#!/usr/bin/env python3
"""
Turns the backend's OpenAPI document into a short guide for adding records from a script.

    python3 tools/build_api_guide.py dist/nibras-api-openapi.json dist/nibras-api-adding-records.md

Run through tools/dump_api_docs.sh, which fetches the spec first. Generated rather than written by
hand so it cannot drift: a new module or a renamed field appears the next time it is run.
"""

import json
import sys

# Bookkeeping the server owns. Listing these as things to send would be actively misleading.
SERVER_MANAGED = {'id', 'version', 'dateCreated', 'lastUpdated', 'deletedOn', 'displayTitle'}


def main():
    spec_path = sys.argv[1] if len(sys.argv) > 1 else 'dist/nibras-api-openapi.json'
    out_path = sys.argv[2] if len(sys.argv) > 2 else 'dist/nibras-api-adding-records.md'
    d = json.load(open(spec_path, encoding='utf-8'))
    paths, schemas = d['paths'], d.get('components', {}).get('schemas', {})

    def deref(ref):
        return schemas.get(ref.split('/')[-1], {}) if ref else {}

    # "Create a record here" = POST on a top-level /api/<collection>.
    creates = []
    for p, ops in sorted(paths.items()):
        if 'post' not in ops or not (p.startswith('/api/') and p.count('/') == 2):
            continue
        ref = (ops['post'].get('requestBody', {}).get('content', {})
               .get('application/json', {}).get('schema', {}).get('$ref', ''))
        sch = deref(ref)
        if sch:
            creates.append((p, ref.split('/')[-1], sch))

    out = []
    w = out.append
    w('# Nibras PIM — adding records over the API')
    w('')
    w("Generated from the running backend's OpenAPI document by `tools/dump_api_docs.sh`. The")
    w('machine-readable spec is beside this file as `nibras-api-openapi.json` (and `.yaml`) — point a')
    w('client generator at that; this page is the short version for writing a script by hand.')
    w('')
    w('- OpenAPI **%s**, %d paths, %d schemas.' % (d.get('openapi'), len(paths), len(schemas)))
    w('- Live copies: `/v3/api-docs`, `/v3/api-docs.yaml`, Swagger UI at `/swagger-ui.html`.')
    w('')
    w('## 1. Authenticate first')
    w('')
    w('Every `/api/**` path needs a bearer token. An unauthenticated request is rejected with **401**')
    w('*before routing*, so from outside a missing endpoint and a forbidden one look identical — do')
    w('not read a 401 as "that path does not exist".')
    w('')
    w('```bash')
    w('BASE=http://127.0.0.1:8081          # or your server')
    w('TOKEN=$(curl -s -X POST "$BASE/api/auth/login" \\')
    w("  -H 'Content-Type: application/json' \\")
    w('  -d \'{"username":"YOUR_USER","password":"YOUR_PASSWORD"}\' | jq -r .token)')
    w('')
    w('curl -s "$BASE/api/auth/me" -H "Authorization: Bearer $TOKEN"   # check it worked')
    w('```')
    w('')
    w('Login answers `{"token": "...", "user": {...}}`. Writes also need the `content.write`')
    w("permission; `/api/auth/me` reports the caller's.")
    w('')
    w('## 2. Create a record')
    w('')
    w('`POST /api/<collection>` with the record as JSON; the created record comes back with its `id`.')
    w('Send only the fields you mean to set — the schemas mark nothing as required, and `id`,')
    w('`version`, `dateCreated` and `lastUpdated` are the server\'s to manage.')
    w('')
    w('```bash')
    w('curl -s -X POST "$BASE/api/tasks" \\')
    w('  -H "Authorization: Bearer $TOKEN" -H \'Content-Type: application/json\' \\')
    w('  -d \'{"summary":"Written by a script","priority":3}\'')
    w('```')
    w('')
    w('Update with `PUT /api/<collection>/{id}`, delete with `DELETE /api/<collection>/{id}`.')
    w('')
    w('**A `PUT` must carry the `version` it read.** If the record moved on meanwhile the write is')
    w('refused with **409** and the message *"This record was modified by someone else. Reload and')
    w('try again."* — that is the optimistic lock, and it is what stops two writers silently')
    w('overwriting one another. Read, modify, send back, and retry the read on a 409.')
    w('')
    w('## 3. The collections')
    w('')
    w('| Create | Body schema | Settable fields |')
    w('|---|---|---|')
    for p, name, sch in creates:
        n = len([k for k in sch.get('properties', {}) if k not in SERVER_MANAGED])
        w('| `POST %s` | `%s` | %d |' % (p, name, n))
    w('')
    w('## 4. Fields, per collection')
    w('')
    for p, name, sch in creates:
        props, req = sch.get('properties', {}), set(sch.get('required') or [])
        w('### `POST %s` → `%s`' % (p, name))
        w('')
        w('Required: %s' % (', '.join('`%s`' % r for r in sorted(req)) if req
                            else 'nothing is required by the schema.'))
        w('')
        w('| Field | Type | |')
        w('|---|---|---|')
        for k, v in props.items():
            if k in SERVER_MANAGED:
                continue
            t = v.get('type') or v.get('$ref', '').split('/')[-1] or 'object'
            if t == 'array':
                it = v.get('items', {})
                t = 'array of ' + (it.get('type') or it.get('$ref', '').split('/')[-1] or 'object')
            if v.get('format'):
                t += ' (%s)' % v['format']
            w('| `%s` | %s | %s |' % (k, t, 'required' if k in req else ''))
        w('')

    open(out_path, 'w', encoding='utf-8').write('\n'.join(out) + '\n')
    print('%d collections documented -> %s' % (len(creates), out_path))


if __name__ == '__main__':
    main()
