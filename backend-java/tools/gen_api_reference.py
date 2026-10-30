#!/usr/bin/env python3
"""Writes docs/api-reference.md from the Spring controllers, so the reference follows the code.

    python3 backend-java/tools/gen_api_reference.py          # regenerate the file
    python3 backend-java/tools/gen_api_reference.py --check URL TOKEN   # also ask a running server about every route

--check sends OPTIONS (which changes nothing) to each route and reports any the server does not
answer with the documented method.
"""
import os
import re
import sys
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, '..', 'src', 'main', 'java')
OUT = os.path.join(HERE, '..', '..', 'docs', 'api-reference.md')

# Reachable without a token (config/SecurityConfig.java); the signing platform sends its own key.
PUBLIC = [('POST', '/api/auth/login'), ('GET', '/api/auth/sso/config'), ('GET', '/api/auth/sso/start'), ('GET', '/api/auth/sso/callback'),
          ('POST', '/api/auth/sso/exchange'), ('GET', '/api/public/qr/'), ('*', '/api/signing/platform/')]
VERBS = {'GetMapping': 'GET', 'PostMapping': 'POST', 'PutMapping': 'PUT', 'DeleteMapping': 'DELETE', 'PatchMapping': 'PATCH'}
SKIP_PARAMS = ('HttpServletRequest', 'HttpServletResponse', 'Authentication', 'Pageable', 'AuthPrincipal')


def snake(name):
    return re.sub(r'(?<=[a-z0-9])([A-Z])', r'_\1', name).lower()


def balanced(text, start, open_ch='(', close_ch=')'):
    """Index just past the bracket that closes the one at text[start], ignoring brackets in strings."""
    depth, i, quote = 0, start, None
    while i < len(text):
        ch = text[i]
        if quote:
            if ch == '\\':
                i += 1
            elif ch == quote:
                quote = None
        elif ch in '"\'':
            quote = ch
        elif ch == open_ch:
            depth += 1
        elif ch == close_ch:
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return len(text)


def split_top(text):
    """Splits on commas outside brackets, generics and strings."""
    parts, depth, cur, quote = [], 0, '', None
    for ch in text:
        if quote:
            cur += ch
            if ch == quote:
                quote = None
            continue
        if ch in '"\'':
            quote = ch
        elif ch in '(<{[':
            depth += 1
        elif ch in ')>}]':
            depth -= 1
        elif ch == ',' and depth == 0:
            parts.append(cur.strip())
            cur = ''
            continue
        cur += ch
    if cur.strip():
        parts.append(cur.strip())
    return parts


def doc_before(text, pos):
    """The Javadoc directly above an annotation block, as one line of text."""
    head = text[:pos].rstrip()
    while True:
        m = re.search(r'(@\w+(\([^\n]*\))?)\s*$', head)
        if not m:
            break
        head = head[:m.start()].rstrip()
    if not head.endswith('*/'):
        return ''
    start = head.rfind('/**')
    if start < 0:
        return ''
    body = re.sub(r'^\s*\*\s?', '', head[start + 3:-2], flags=re.M)
    body = re.sub(r'\{@(?:code|link)\s+([^}]*)\}', r'`\1`', body)
    return ' '.join(body.split())


def strings(expr, constants):
    """A Java string expression: literals and known constants joined with +."""
    out = ''
    for part in re.split(r'\s*\+\s*', expr.strip()):
        if part.startswith('"'):
            out += part.strip('"')
        elif part in constants:
            out += constants[part]
    return out


def annotation_paths(args, constants):
    if not args:
        return ['']
    m = re.search(r'(?:value|path)\s*=\s*(\{[^}]*\}|"[^"]*"|\w+)', args)
    expr = m.group(1) if m else args
    if expr.startswith('{'):
        return [strings(p, constants) for p in split_top(expr[1:-1])]
    return [strings(expr, constants)]


def access_text(expr, constants):
    if expr in constants:
        expr = constants[expr]
    elif not expr.startswith('"'):
        expr = strings(expr, constants) or expr
    expr = expr.strip('"')
    names = re.findall(r"'([a-z_]+)'", expr)
    return ', '.join(f'`{n}`' for n in names) if names else f'`{expr}`'


class Records:
    """Every record in the code base; a name repeated across files resolves to the one in the asking file first."""

    def __init__(self):
        self.by_name = {}
        self.imports = {}

    def add(self, path, text):
        self.imports[path] = re.findall(r'^import\s+(?:static\s+)?([\w.*]+);', text, re.M)
        for m in re.finditer(r'\brecord\s+(\w+)\s*(<[^>]*>)?\s*\(', text):
            end = balanced(text, m.end() - 1)
            fields = []
            for raw in split_top(text[m.end():end - 1]):
                raw = re.sub(r'@\w+(\.\w+)*(\((?:[^()]|\([^()]*\))*\))?\s*', '', raw).strip()
                if ' ' not in raw:
                    continue
                typ, name = raw.rsplit(' ', 1)
                fields.append((snake(name), typ.strip()))
            self.by_name.setdefault(m.group(1), []).append((path, fields))

    def owner(self, name, path):
        """The file a type belongs to, by Java's rules: a qualified name, the same file, an import, or the same package. None when unsure."""
        bare = name.split('.')[-1]
        options = [p for p, _ in self.by_name.get(bare, [])]
        if '.' in name:
            outer = name.split('.')[-2] + '.java'
            return next((p for p in options if os.path.basename(p) == outer), None)
        if path in options:
            return path
        for imp in self.imports.get(path, []):
            parts = imp.split('.')
            if parts[-1] == bare and len(parts) >= 2:
                # import a.b.Outer.Name (nested) or a.b.Name (its own file)
                for candidate in (parts[-2] + '.java', bare + '.java'):
                    hit = next((p for p in options if os.path.basename(p) == candidate), None)
                    if hit:
                        return hit
            if parts[-1] == '*':
                hit = next((p for p in options if os.path.basename(p) == parts[-2] + '.java'), None)
                if hit:
                    return hit
        return next((p for p in options if os.path.dirname(p) == os.path.dirname(path) and os.path.basename(p) == bare + '.java'), None)

    def key(self, name, path):
        """The heading for a type: its name, plus the controller when several records share the name."""
        bare = name.split('.')[-1]
        options = self.by_name.get(bare, [])
        if len({str(f) for _, f in options}) < 2:
            return bare
        owner = self.owner(name, path) or path
        return f"{bare} ({os.path.basename(owner).replace('.java', '').replace('Controller', '')})"

    def find(self, name, path):
        owner = self.owner(name, path)
        for p, fields in self.by_name.get(name.split('.')[-1], []):
            if p == owner:
                return fields
        return None


JSON_TYPES = {'String': 'string', 'Long': 'integer', 'long': 'integer', 'Integer': 'integer', 'int': 'integer', 'Boolean': 'boolean', 'boolean': 'boolean', 'BigDecimal': 'number',
              'double': 'number', 'Double': 'number', 'LocalDate': 'date', 'LocalDateTime': 'date-time', 'OffsetDateTime': 'date-time', 'Instant': 'date-time'}


def json_type(typ):
    typ = typ.strip()
    m = re.match(r'(?:List|Set|Collection)<(.+)>$', typ)
    if m:
        return json_type(m.group(1)) + '[]'
    if typ.startswith('Map'):
        return 'object'
    return JSON_TYPES.get(typ, typ.split('.')[-1])


def named_types(typ):
    # Keeps qualified names whole, so a nested record resolves to the class that declares it.
    return [t for t in re.findall(r'[A-Z][\w.]*', typ) if t not in JSON_TYPES and t not in ('List', 'Set', 'Collection', 'Map', 'Object', 'PageResponse', 'ResponseEntity', 'Void')]


def parse_controllers():
    records = Records()
    files = []
    for root, _, names in os.walk(SRC):
        for n in names:
            if n.endswith('.java'):
                path = os.path.join(root, n)
                text = open(path, encoding='utf-8').read()
                records.add(path, text)
                if '@RestController' in text:
                    files.append((path, text))
    controllers = []
    for path, text in sorted(files):
        constants = {m.group(1): m.group(2) for m in re.finditer(r'String\s+(\w+)\s*=\s*"((?:[^"\\]|\\.)*)"\s*;', text)}
        cls = re.search(r'public\s+class\s+(\w+)', text)
        cls_block = text[:cls.start()]
        base_m = re.search(r'@RequestMapping\(([^)]*)\)', cls_block)
        base = annotation_paths(base_m.group(1), constants)[0] if base_m else ''
        cls_auth_m = re.search(r'@PreAuthorize\((.*)\)\s*$', cls_block, re.M)
        cls_auth = access_text(cls_auth_m.group(1), constants) if cls_auth_m else None
        routes = []
        for m in re.finditer(r'@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\b', text[cls.end():]):
            pos = cls.end() + m.start()
            after = pos + len(m.group(0))
            args = ''
            if after < len(text) and text[after] == '(':
                close = balanced(text, after)
                args = text[after + 1:close - 1]
                after = close
            verbs = [VERBS[m.group(1)]] if m.group(1) in VERBS else re.findall(r'RequestMethod\.(\w+)', args)
            if not verbs:
                continue
            sig = re.search(r'\b(?:public|protected)?\s*([\w<>\[\], .?]+?)\s+(\w+)\s*\(', text[after:])
            # Skip the annotations between the mapping and the signature.
            rest = text[after:]
            while True:
                a = re.match(r'\s*@\w+(\.\w+)*', rest)
                if not a:
                    break
                skip = a.end()
                if rest[skip:skip + 1] == '(':
                    skip = balanced(rest, skip)
                rest = rest[skip:]
            sig = re.match(r'\s*(?:public\s+|protected\s+)?(.+?)\s+(\w+)\s*\(', rest, re.S)
            if not sig:
                continue
            params_start = sig.end() - 1
            params_end = balanced(rest, params_start)
            block = text[max(0, pos - 400):after]
            mapping_notes = text[pos - 400 if pos > 400 else 0:pos]
            between = text[after:len(text) - len(rest)]
            auth_m = re.search(r'@PreAuthorize\(((?:[^()]|\([^()]*\))*)\)', between) or re.search(r'@PreAuthorize\(((?:[^()]|\([^()]*\))*)\)\s*(?:@\w+(?:\([^)]*\))?\s*)*$', mapping_notes)
            access = access_text(auth_m.group(1), constants) if auth_m else cls_auth
            query, path_vars, body, headers, files_in = [], [], None, [], []
            for raw in split_top(rest[params_start + 1:params_end - 1]):
                if any(s in raw for s in SKIP_PARAMS):
                    continue
                am = re.match(r'@(RequestParam|PathVariable|RequestBody|RequestHeader|RequestPart)(\((?:[^()]|\([^()]*\))*\))?', raw.replace('@Valid ', ''))
                if not am:
                    continue
                inner = am.group(2) or ''
                decl = raw.replace('@Valid ', '')[am.end():].strip()
                decl = re.sub(r'@\w+(\((?:[^()]|\([^()]*\))*\))?\s*', '', decl)
                typ, var = decl.rsplit(' ', 1)
                nm = re.search(r'name\s*=\s*"([^"]+)"', inner) or re.search(r'value\s*=\s*"([^"]+)"', inner) or re.match(r'\(\s*"([^"]+)"', inner)
                name = nm.group(1) if nm else var
                default = re.search(r'defaultValue\s*=\s*"([^"]*)"', inner)
                required = 'required = false' not in inner and 'required=false' not in inner and not default
                kind = am.group(1)
                if kind == 'PathVariable':
                    path_vars.append(name)
                elif kind == 'RequestBody':
                    body = typ.strip()
                elif kind == 'RequestHeader':
                    headers.append(name)
                elif typ.strip() in ('MultipartFile', 'List<MultipartFile>'):
                    files_in.append((name, required))
                else:
                    query.append((name, json_type(typ), required, default.group(1) if default else None))
            for mp in annotation_paths(args, constants):
                routes.append({'verbs': verbs, 'path': join_path(base, mp), 'doc': doc_before(text, pos), 'access': access, 'query': query, 'body': body, 'headers': headers,
                               'files': files_in, 'returns': clean_return(sig.group(1)), 'file': path})
        if routes:
            routes.sort(key=lambda r: (r['path'], r['verbs']))
            controllers.append({'name': cls.group(1), 'doc': doc_before(text, text.rfind('@', 0, cls.start()) if False else cls.start()), 'routes': routes, 'file': path})
    return controllers, records


def join_path(base, sub):
    p = (base.rstrip('/') + '/' + sub.lstrip('/')).rstrip('/')
    return p or '/'


def clean_return(typ):
    typ = re.sub(r'\b(public|protected|static|final)\b', '', typ).strip()
    return typ


def is_public(verb, path):
    for v, p in PUBLIC:
        if (v == '*' or v == verb) and (path == p or (p.endswith('/') and path.startswith(p))):
            return True
    return False


def title(name):
    name = re.sub(r'Controller$', '', name)
    return re.sub(r'(?<=[a-z])(?=[A-Z])', ' ', name)


def describe_return(typ, records, path):
    if 'byte[]' in typ:
        return 'a file download'
    if typ in ('void', 'ResponseEntity<Void>', 'ResponseEntity<?>'):
        return 'no JSON body' if 'Void' in typ or typ == 'void' else 'JSON'
    inner = re.sub(r'^ResponseEntity<(.*)>$', r'\1', typ)
    if inner.startswith('Map'):
        return 'a JSON object'
    m = re.match(r'PageResponse<(.+)>$', inner)
    if m:
        return f'a page of {linked(m.group(1), records, path)}'
    m = re.match(r'(?:List|Set|Collection)<(.+)>$', inner)
    if m:
        return f'a list of {linked(m.group(1), records, path)}'
    return linked(inner, records, path)


def linked(typ, records, path):
    if typ.startswith('Map'):
        return 'objects'
    bare = typ.split('.')[-1]
    if typ in JSON_TYPES or not re.match(r'^[A-Z]\w+$', bare) or records.find(typ, path) is None:
        return f'`{typ}`'
    key = records.key(typ, path)
    return f"[`{key}`](#{re.sub(r'[^a-z0-9 -]', '', key.lower()).replace(' ', '-')})"


INTRO = """# PAMS API reference

*Generated from the controllers by `backend-java/tools/gen_api_reference.py`; do not edit by hand. Run the script after changing an endpoint.*

PAMS is a JSON API over HTTPS, served from the same address as the web app (for example `https://pams.agency.gov.ph/api/...`).
During development the backend listens on `http://localhost:8095`.

## Signing in

Send the email and password to `POST /api/auth/login`:

```json
{ "email": "user@agency.gov.ph", "password": "...", "otp": "123456" }
```

`otp` is needed only for accounts with two-factor authentication; without it the answer is `{ "two_factor_required": true }`. A successful answer holds
`access_token`, `token_type`, `expires_in` (seconds) and `user`. Send the token on every other request:

```
Authorization: Bearer <access_token>
```

Tokens last 60 minutes. `POST /api/auth/refresh` gives a new one and cancels the old; `POST /api/auth/logout` cancels it. Failed logins lock the account for
15 minutes after 5 tries, and each address may try 5 times a minute.

## Conventions

- **Names** are `snake_case` in JSON and query strings.
- **Dates** are `YYYY-MM-DD`; timestamps are ISO 8601 with the Manila offset (`2026-10-30T14:45:11+08:00`).
- **Pages:** lists that page answer `{ "data": [...], "pagination": { "total", "per_page", "current_page", "last_page" } }` and take `page` (from 1).
- **Files:** uploads are `multipart/form-data`. A download answers with the file and a `Content-Disposition` naming it.
- **COA forms as PDF:** add `format=pdf` to any form's Excel download. List reports take `format=xlsx`, `csv` or `pdf`.

## Errors

| Status | Meaning | Body |
|---|---|---|
| 400 | Malformed request | `{ "error": "..." }` |
| 401 | No token, or it is expired or cancelled | `{ "status": "..." }` |
| 403 | Signed in, but not allowed | `{ "error": "..." }`; an expired password also sets `password_expired` |
| 404 | No such record or route | `{ "error": "..." }` |
| 409 | The record's state does not allow this | `{ "error": "..." }` |
| 422 | Invalid input | `{ "message": "Validation failed", "errors": { "field": ["..."] } }` |
| 429 | Too many login attempts | `{ "error": "...", "locked_until": "..." }` |

## Permissions

**Requires** below lists the permissions, any one of which opens the endpoint; Super Admin passes every check. Where it says *signed in*, any account may call it,
but the handler may still narrow what it returns (your own records, for example) or answer 403 for a record you may not touch. Roles and their permissions are
managed under Users & Roles.

## Single sign-on

With an OpenID Connect provider configured, send the browser to `GET /api/auth/sso/start`. The provider returns it to `GET /api/auth/sso/callback`, which redirects to
`/login?sso_code=...` (or `/login?sso_error=...`). Swap the one-time code for the usual token with `POST /api/auth/sso/exchange` within 60 seconds.

## Digital signing platform

An external platform is told about each new signing request by a `POST` to `PAMS_SIGNING_PLATFORM_URL`, with the header `X-Signing-Key` and this body:

```json
{ "request_number": "SIG-2026-0001", "title": "...", "sha256": "...", "signer_email": "...", "file_url": "...", "callback_url": "..." }
```

It fetches `file_url` and posts the detached signature over the file's exact bytes to `callback_url`, both with the same header. Accepted algorithms: `RSA-SHA256`, `RSA-SHA384`, `RSA-SHA512`,
`ECDSA-SHA256`, `ECDSA-SHA384`, `ECDSA-SHA512`. The signature must be Base64 and the certificate PEM, followed by any intermediates, issued by a CA under Digital Signatures → Trusted CAs.

"""


def write(controllers, records):
    lines = [INTRO]
    total = sum(len(r['verbs']) for c in controllers for r in c['routes'])
    lines.append(f'## Endpoints\n\n{total} endpoints in {len(controllers)} groups.\n')
    for c in sorted(controllers, key=lambda x: x['name']):
        lines.append(f"- [{title(c['name'])}](#{title(c['name']).lower().replace(' ', '-')})")
    lines.append('')
    schemas = {}
    for c in sorted(controllers, key=lambda x: x['name']):
        lines.append(f"### {title(c['name'])}\n")
        if c['doc']:
            lines.append(c['doc'] + '\n')
        for r in c['routes']:
            for verb in r['verbs']:
                lines.append(f"**`{verb} {r['path']}`**\n")
                if r['doc']:
                    lines.append(r['doc'] + '\n')
                facts = []
                if is_public(verb, r['path']):
                    facts.append('Requires: nothing (public)' if 'platform' not in r['path'] else 'Requires: the `X-Signing-Key` header')
                else:
                    facts.append('Requires: ' + (r['access'] or '*signed in*'))
                if r['query']:
                    q = ', '.join(f"`{n}`" + ('' if req else ' (optional)') + (f' default `{d}`' if d not in (None, '') else '') + (f' {t}' if t != 'string' else '') for n, t, req, d in r['query'])
                    facts.append(('Form fields: ' if r['files'] else 'Query: ') + q)
                if r['files']:
                    facts.append('Upload: ' + ', '.join(f"`{n}`" + ('' if req else ' (optional)') for n, req in r['files']))
                if r['body']:
                    fields = records.find(r['body'], r['file'])
                    if fields:
                        facts.append('Body: ' + ', '.join(f'`{n}` {json_type(t)}' for n, t in fields))
                    else:
                        facts.append('Body: JSON object')
                facts.append('Returns: ' + describe_return(r['returns'], records, r['file']))
                for t in named_types(r['returns']):
                    if records.find(t, r['file']) is not None:
                        schemas.setdefault(records.key(t, r['file']), (r['file'], records.find(t, r['file'])))
                lines.extend(f'- {f}' for f in facts)
                lines.append('')
    # Types a returned record refers to are listed too.
    pending = list(schemas.items())
    while pending:
        _, (path, fields) = pending.pop()
        for _, typ in fields:
            for t in named_types(typ):
                k = records.key(t, path)
                if k not in schemas and records.find(t, path) is not None:
                    schemas[k] = (path, records.find(t, path))
                    pending.append((k, schemas[k]))
    lines.append('## Response types\n')
    for name in sorted(schemas):
        lines.append(f'### {name}\n')
        lines.append(', '.join(f'`{n}` {json_type(t)}' for n, t in schemas[name][1]) + '\n')
    with open(OUT, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))
    return total


def check(controllers, base, token):
    """Every documented route must exist with its method; OPTIONS changes nothing on the server."""
    bad, seen = [], 0
    for c in controllers:
        for r in c['routes']:
            url = base.rstrip('/') + re.sub(r'\{[^}]+\}', '1', r['path'])
            req = urllib.request.Request(url, method='OPTIONS', headers={'Authorization': 'Bearer ' + token})
            try:
                with urllib.request.urlopen(req) as res:
                    allow = res.headers.get('Allow', '')
            except urllib.error.HTTPError as e:
                allow = e.headers.get('Allow', '') or f'HTTP {e.code}'
            for verb in r['verbs']:
                seen += 1
                if verb not in allow:
                    bad.append(f"{verb} {r['path']} -> {allow}")
    print(f'checked {seen} endpoints; {len(bad)} not answered as documented')
    for b in bad:
        print(' ', b)
    return not bad


if __name__ == '__main__':
    found, recs = parse_controllers()
    print('wrote', write(found, recs), 'endpoints to', os.path.normpath(OUT))
    if len(sys.argv) >= 4 and sys.argv[1] == '--check':
        sys.exit(0 if check(found, sys.argv[2], sys.argv[3]) else 1)
