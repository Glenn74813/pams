"""Settings → Integrations: PAMS reading another system's payment API, read-only. A small fake system runs here;
it listens on 127.0.0.1:$PAMS_FAKE_PORT (18765), which the backend under test must list in
PAMS_INTEGRATIONS_ALLOWED_HOSTS. Other ports on this machine stay refused."""
import base64, json, os, threading, time, urllib.request, urllib.error, uuid, datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs
from pams_db import sql

B = os.environ['PAMS_API']
RUN = uuid.uuid4().hex[:6]
TOKEN = 'tok-' + RUN
seen = []
oauth = {'issued': [], 'valid': set(), 'expires_in': 600}


class Fake(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def send(self, code, body, ctype='application/json', extra=None):
        data = body if isinstance(body, bytes) else json.dumps(body).encode()
        self.send_response(code)
        self.send_header('Content-Type', ctype)
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        u = urlparse(self.path)
        q = parse_qs(u.query)
        seen.append((self.command, self.path, dict(self.headers)))
        if u.path == '/api/payments':
            if self.headers.get('Authorization') != 'Bearer ' + TOKEN:
                return self.send(401, {'error': 'no'})
            ref = q.get('reference', [''])[0]
            return self.send(200, {'data': {'items': [
                {'check_number': 'CHK-1', 'amount': 1500.5, 'payee': {'name': 'ZZ Payee'}, 'reference': ref, 'released': True},
                {'check_number': 'CHK-2', 'amount': 99, 'payee': {'name': 'ZZ Payee'}, 'reference': ref, 'released': False}]}})
        if u.path == '/api/oauth-payments':
            h = self.headers.get('Authorization', '')
            if not h.startswith('Bearer ') or h[7:] not in oauth['valid']:
                return self.send(401, {'error': 'invalid_token'})
            return self.send(200, {'items': [{'dvNumber': 'DV-OA-1', 'amount': 500}]})
        if u.path == '/api/released':
            if self.headers.get('X-API-Key') != TOKEN:
                return self.send(403, {'error': 'no'})
            return self.send(200, [{'dv': 'DV-1', 'amount': 10}, {'dv': 'DV-2', 'amount': 20}])
        if u.path == '/redirect':
            return self.send(302, {}, extra={'Location': 'http://169.254.169.254/latest/meta-data'})
        if u.path == '/text':
            return self.send(200, b'<html>not json</html>', 'text/html')
        if u.path == '/big':
            return self.send(200, json.dumps([{'x': 'y' * 1000}] * 2500).encode())
        if u.path == '/slow':
            time.sleep(3)
            return self.send(200, [])
        if u.path == '/many':
            return self.send(200, [{'n': i} for i in range(600)])
        return self.send(404, {'error': 'not found'})

    def do_POST(self):
        u = urlparse(self.path)
        body = self.rfile.read(int(self.headers.get('Content-Length', 0))).decode()
        seen.append((self.command, self.path, dict(self.headers)))
        if u.path != '/api/auth/token':
            return self.send(404, {'error': 'not found'})
        expected = 'Basic ' + base64.b64encode(f'pams-{RUN}:s3cret {RUN}'.replace(' ', '+').encode()).decode()
        if parse_qs(body).get('grant_type') != ['client_credentials'] or self.headers.get('Authorization') != expected:
            return self.send(401, {'error': 'invalid_client'})
        t = f'oat-{len(oauth["issued"])}-{RUN}'
        oauth['issued'].append(t)
        oauth['valid'].add(t)
        return self.send(200, {'access_token': t, 'token_type': 'Bearer', 'expires_in': oauth['expires_in']})


PORT = int(os.environ.get('PAMS_FAKE_PORT', '18765'))
server = ThreadingHTTPServer(('127.0.0.1', PORT), Fake)
threading.Thread(target=server.serve_forever, daemon=True).start()
FAKE = f'http://127.0.0.1:{server.server_address[1]}'


def call(m, p, body=None, tok=None, params=None):
    h = {'Content-Type': 'application/json', 'Accept': 'application/json'}
    if tok:
        h['Authorization'] = 'Bearer ' + tok
    url = B + p + ('?' + '&'.join(f'{k}={v}' for k, v in params.items()) if params else '')
    r = urllib.request.Request(url, data=json.dumps(body).encode() if body is not None else None, headers=h, method=m)
    try:
        with urllib.request.urlopen(r) as x:
            return x.status, json.loads(x.read() or b'{}')
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read() or b'{}')
        except ValueError:
            return e.code, {}


fails = 0


def ok(label, cond, extra=''):
    global fails
    if not cond:
        fails += 1
    print(('PASS ' if cond else 'FAIL ') + label, '' if cond else str(extra)[:400], flush=True)


SA = call('POST', '/auth/login', {'email': 'superadmin@inventory.com', 'password': 'admin123'})[1]['access_token']
base = {'name': f'ZZ Accounting {RUN}', 'base_url': FAKE + '/api', 'path': '/payments?reference={reference}', 'auth_type': 'bearer', 'secret': TOKEN,
        'data_path': 'data.items', 'context': 'payable', 'columns': [{'label': 'Check no.', 'field': 'check_number'}, {'label': 'Amount', 'field': 'amount'},
                                                                   {'label': 'Payee', 'field': 'payee.name'}, {'label': 'Released', 'field': 'released'}]}

# ── what is refused when connecting
for label, patch, field in [
    ('only http or https', {'base_url': 'ftp://example.com'}, 'base_url'),
    ('no cloud metadata address', {'base_url': 'http://169.254.169.254/latest'}, 'base_url'),
    ('no credentials in the address', {'base_url': 'http://user:pw@127.0.0.1/api'}, 'base_url'),
    ('unknown placeholder refused', {'path': '/payments?x={secret}'}, 'path'),
    ('placeholders only when read by PO or contract', {'context': 'none'}, 'path'),
    ('a key is needed to sign in', {'secret': ''}, 'secret'),
    ('header sign-in needs the header name', {'auth_type': 'header', 'auth_header': 'Host'}, 'auth_header'),
    ('time limit at most 60 seconds', {'timeout_seconds': 120}, 'timeout_seconds'),
    ('a whole address in the path', {'path': FAKE + '/api/payments'}, 'path'),
    ('another port on this machine', {'base_url': f'http://127.0.0.1:{PORT + 1}/api'}, 'base_url'),
    ('OAuth needs the token address', {'auth_type': 'oauth', 'client_id': 'x'}, 'token_url'),
    ('OAuth needs the client id', {'auth_type': 'oauth', 'token_url': FAKE + '/api/auth/token'}, 'client_id'),
    ('the token address is checked too', {'auth_type': 'oauth', 'client_id': 'x', 'token_url': 'http://169.254.169.254/token'}, 'token_url'),
]:
    s, d = call('POST', '/integrations', {**base, **patch}, SA)
    ok(f'refused: {label}', s == 422 and field in d.get('errors', {}), (s, d))

s, a = call('POST', '/integrations', base, SA)
ok('connect a payment API', s == 201 and a['has_secret'] and 'secret' not in a and 'secret_cipher' not in a, a)
API = a['id']
stored = sql(f'select secret_cipher from external_apis where id = {API}')
ok('the key is stored encrypted', stored and TOKEN not in stored, stored)
s, d = call('POST', '/integrations', base, SA)
ok('names are unique', s == 422 and 'name' in d.get('errors', {}), d)

s, d = call('POST', '/integrations', {**base, 'name': f'ZZ Local {RUN}', 'base_url': f'http://127.0.0.1:{PORT + 1}/api'}, SA)
ok('the refusal says how to allow a system on this machine', 'PAMS_INTEGRATIONS_ALLOWED_HOSTS' in json.dumps(d), d)

# ── test call
s, t = call('POST', f'/integrations/{API}/test', {'values': {'reference': 'PO-TEST-1'}}, SA)
ok('test call reads the records', s == 200 and t['error'] is None and t['result']['columns'] == ['Check no.', 'Amount', 'Payee', 'Released'] and len(t['result']['rows']) == 2, t)
ok('values follow the column fields', t['result']['rows'][0] == ['CHK-1', 1500.5, 'ZZ Payee', True], t['result']['rows'])
ok('the placeholder is filled and URL-encoded', any('/api/payments?reference=PO-TEST-1' in p for _, p, _ in seen), seen[-1:])
ok('only GET requests are sent', all(m == 'GET' for m, _, _ in seen), {m for m, _, _ in seen})
s, l = call('GET', '/integrations', tok=SA)
row = [x for x in l['data'] if x['id'] == API][0]
ok('the last call is shown', row['last_status'].startswith('OK: 2') and row['last_called_at'], row)

# ── failures are explained, not crashes
def failing(patch, values=None):
    s, x = call('PUT', f'/integrations/{API}', {**base, **patch, 'secret': ''}, SA)
    assert s == 200, x
    return call('POST', f'/integrations/{API}/test', {'values': values or {'reference': 'X'}}, SA)[1]


call('PUT', f'/integrations/{API}', {**base, 'secret': 'wrong'}, SA)
ok('a refused key is explained', 'refused the credentials' in (call('POST', f'/integrations/{API}/test', {'values': {'reference': 'X'}}, SA)[1]['error'] or ''))
call('PUT', f'/integrations/{API}', base, SA)
ok('a redirect is not followed', 'redirect' in (failing({'base_url': FAKE, 'path': '/redirect', 'data_path': None})['error'] or '').lower())
ok('a web page instead of the API is explained', 'web page' in (failing({'base_url': FAKE, 'path': '/text', 'data_path': None})['error'] or ''))
ok('a wrong path is explained', '404' in (failing({'base_url': FAKE, 'path': '/nothing-here', 'data_path': None})['error'] or ''))
ok('a too-large answer is refused', '2 MB' in (failing({'base_url': FAKE, 'path': '/big', 'data_path': None})['error'] or ''))
ok('a slow system times out', 'did not answer within 1' in (failing({'base_url': FAKE, 'path': '/slow', 'data_path': None, 'timeout_seconds': 1})['error'] or ''))
d = failing({'base_url': FAKE, 'path': '/many', 'data_path': None, 'columns': []})
ok('at most 500 records, columns found by themselves', d['result'] and len(d['result']['rows']) == 500 and d['result']['truncated'] and d['result']['columns'] == ['n'], d['result'] and d['result']['columns'])
ok('a missing data path is explained', 'Nothing was found' in (failing({'data_path': 'nowhere'}, {'reference': 'X'})['error'] or ''))
s, a = call('PUT', f'/integrations/{API}', base, SA)
ok('restored, and the stored key is kept when none is typed', s == 200 and a['has_secret'], a)
s, t = call('POST', f'/integrations/{API}/test', {'values': {'reference': 'X'}}, SA)
ok('and works again', t['error'] is None, t)

# ── a list of its own, with a header key
s, L = call('POST', '/integrations', {'name': f'ZZ Disbursements {RUN}', 'base_url': FAKE, 'path': '/api/released', 'auth_type': 'header', 'auth_header': 'X-API-Key',
                                       'secret': TOKEN, 'context': 'none', 'columns': [{'label': 'DV', 'field': 'dv'}, {'label': 'Amount', 'field': 'amount'}]}, SA)
ok('connect a list', s == 201, L)
s, d = call('GET', f"/integrations/{L['id']}/data", tok=SA)
ok('the list is read with the header key', s == 200 and d['result']['rows'] == [['DV-1', 10], ['DV-2', 20]], d)
s, d = call('GET', f'/integrations/{API}/data', tok=SA)
ok('a per-PO connection is not read as a list', s == 409, d)

# ── OAuth 2.0 client credentials
oa = {'name': f'ZZ OAuth {RUN}', 'base_url': FAKE + '/api', 'path': '/oauth-payments', 'auth_type': 'oauth', 'token_url': FAKE + '/api/auth/token',
      'client_id': f'pams-{RUN}', 'secret': f's3cret {RUN}', 'data_path': 'items', 'context': 'none'}
s, O = call('POST', '/integrations', oa, SA)
ok('connect with OAuth client credentials', s == 201 and O['auth_type'] == 'oauth' and O['client_id'] == f'pams-{RUN}' and O['has_secret'], O)
s, d = call('GET', f"/integrations/{O['id']}/data", tok=SA)
ok('a token is fetched and used', s == 200 and d['error'] is None and d['result']['rows'] == [['DV-OA-1', 500]], d)
token_posts = [x for x in seen if x[0] == 'POST']
ok('the token request is a form POST with the client in Basic', token_posts and token_posts[-1][2].get('Content-Type', '').startswith('application/x-www-form-urlencoded'), token_posts[-1:])
call('GET', f"/integrations/{O['id']}/data", tok=SA)
ok('the token is kept while it is valid', len(oauth['issued']) == 1, oauth['issued'])
oauth['valid'].clear()
s, d = call('GET', f"/integrations/{O['id']}/data", tok=SA)
ok('a revoked token is replaced once', d['error'] is None and len(oauth['issued']) == 2, (d, oauth['issued']))
s, x = call('PUT', f"/integrations/{O['id']}", {**oa, 'secret': 'wrong'}, SA)
s, d = call('GET', f"/integrations/{O['id']}/data", tok=SA)
ok('a wrong client secret is explained', 'client id or secret' in (d['error'] or ''), d)
s, x = call('GET', '/integrations', tok=SA)
ok('the client secret is not sent back', all('secret' not in r for r in x['data']), x)

# ── per purchase order
# Tables with triggers on SQL Server cannot return the new id, so it is looked up after the insert.
sid = call('POST', '/suppliers', {'name': f'ZZ Int Supplier {RUN}', 'tin': '123-456-789-000'}, SA)[1]['id']
sql("insert into purchase_orders (po_number, supplier_id, status, order_date, total_amount, created_at, updated_at) "
    f"values ('PO-INT-{RUN}', {sid}, 'sent', now(), 1599.50, now(), now())")
po = sql(f"select id from purchase_orders where po_number = 'PO-INT-{RUN}'").splitlines()[0]
s, d = call('GET', '/integrations/payable', tok=SA, params={'type': 'purchase_order', 'id': po})
ok('a PO shows what each system holds for it', s == 200 and len(d['data']) == 1 and d['reference'] == f'PO-INT-{RUN}' and len(d['data'][0]['result']['rows']) == 2, d)
ok('looked up by the PO number PAMS holds', any(f'reference=PO-INT-{RUN}' in p for _, p, _ in seen[-3:]), seen[-1:])
s, d = call('GET', '/integrations/payable', tok=SA, params={'type': 'thing', 'id': po})
ok('only POs and contracts', s == 422, d)

# ── who sees what
role = call('POST', '/roles', {'name': f'ZZ Payments Viewer {RUN}', 'description': 't', 'permissions': ['view_payments']}, SA)[1]
rid = role.get('id') or (role.get('role') or role.get('data') or {}).get('id')
mail = f'zz.int.{RUN}@test.local'
s, u = call('POST', '/users', {'name': 'ZZ Viewer', 'email': mail, 'password': 'Zz#Test2026', 'password_confirmation': 'Zz#Test2026', 'role_id': rid, 'department_id': 1}, SA)
V = call('POST', '/auth/login', {'email': mail, 'password': 'Zz#Test2026'})[1]['access_token']
s, l = call('GET', '/integrations', tok=V)
mine = [x for x in l['data'] if x['id'] == API][0]
ok('a payments viewer sees the connections, not their address or key', s == 200 and not l['can_manage'] and mine['base_url'] is None and mine['auth_type'] is None, mine)
s, d = call('GET', f"/integrations/{L['id']}/data", tok=V)
ok('and reads the records', s == 200 and d['api']['base_url'] is None and len(d['result']['rows']) == 2, d)
s, d = call('POST', '/integrations', {**base, 'name': 'ZZ x'}, V)
ok('but cannot connect a system', s == 403, d)
s, d = call('POST', f'/integrations/{API}/test', {'values': {}}, V)
ok('or call one at will', s == 403, d)
s, u2 = call('POST', '/users', {'name': 'ZZ Nobody', 'email': f'zz.no.{RUN}@test.local', 'password': 'Zz#Test2026', 'password_confirmation': 'Zz#Test2026', 'role_id': 15, 'department_id': 1}, SA)
N = call('POST', '/auth/login', {'email': f'zz.no.{RUN}@test.local', 'password': 'Zz#Test2026'})[1]['access_token']
s, d = call('GET', '/integrations', tok=N)
ok('an end user sees nothing', s == 403, d)

# ── switched off, removed, audited
call('PUT', f"/integrations/{L['id']}", {'name': f'ZZ Disbursements {RUN}', 'base_url': FAKE, 'path': '/api/released', 'auth_type': 'header', 'auth_header': 'X-API-Key',
                                         'context': 'none', 'is_active': False}, SA)
s, d = call('GET', f"/integrations/{L['id']}/data", tok=V)
ok('a switched-off connection is not read', s == 409, d)
s, d = call('DELETE', f"/integrations/{L['id']}", tok=SA)
ok('remove a connection', s == 200, d)
acts = sql(f"select string_agg(action, ',' order by action) from audit_trails where auditable_type like '%ExternalApi' and auditable_id in ({API}, {L['id']})")
ok('connecting, changing and removing are audited', all(a in acts for a in ('created', 'updated', 'deleted')), acts)
server.shutdown()
print('DONE fails=%d' % fails)
