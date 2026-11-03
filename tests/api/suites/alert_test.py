import json, urllib.request, urllib.error, os, uuid, sys
# usage: alert_test.py <api that fails backups> [<api of the same database that succeeds>]
FAIL_API = os.environ['PAMS_API_FAILING_BACKUP']; OK_API = os.environ.get('PAMS_API')
RUN = uuid.uuid4().hex[:6]
def call(api, m, p, body=None, tok=None):
    h = {'Content-Type': 'application/json', 'Accept': 'application/json'}
    if tok: h['Authorization'] = 'Bearer ' + tok
    r = urllib.request.Request(api + p, data=json.dumps(body).encode() if body is not None else None, headers=h, method=m)
    try:
        with urllib.request.urlopen(r) as x: return x.status, json.loads(x.read() or b'{}')
    except urllib.error.HTTPError as e: return e.code, json.loads(e.read() or b'{}')
fails = 0
def ok(label, cond, extra=''):
    global fails
    if not cond: fails += 1
    print(('PASS ' if cond else 'FAIL ') + label, '' if cond else extra, flush=True)
SA = call(FAIL_API, 'POST', '/auth/login', {'email': 'superadmin@inventory.com', 'password': 'admin123'})[1]['access_token']
def mine(tok):
    s, d = call(FAIL_API, 'GET', '/notifications?per_page=100', tok=tok)
    assert s == 200, d
    rows = d.get('data', d.get('notifications', d)) if isinstance(d, dict) else d
    return [n for n in rows if n.get('type') == 'job_failed']
# someone with the permission and someone without
role = call(FAIL_API, 'POST', '/roles', {'name': f'ZZ Sysadmin {RUN}', 'description': 'test', 'permissions': ['manage_system_settings', 'view_dashboard']}, SA)
assert role[0] in (200, 201), role
rid = role[1].get('id') or role[1].get('role', {}).get('id')
def user(tag, role_id):
    mail = f'zz.{tag}.{RUN}@test.local'
    s, d = call(FAIL_API, 'POST', '/users', {'name': f'ZZ {tag} {RUN}', 'email': mail, 'password': 'Zz#Test2026', 'password_confirmation': 'Zz#Test2026', 'role_id': role_id, 'department_id': 1}, SA)
    assert s == 201, d
    return call(FAIL_API, 'POST', '/auth/login', {'email': mail, 'password': 'Zz#Test2026'})[1]['access_token']
ADM = user('admin', rid); EU = user('eu', 15)
ok('no alert yet today', mine(SA) == [] and mine(ADM) == [], mine(SA))
s, d = call(FAIL_API, 'POST', '/monitoring/alerts/run', {'job': 'backup'}, ADM)
ok('the backup fails and says so on screen', s == 200 and d['results']['backup'].startswith('Failed:'), d)
print('  reason:', d['results']['backup'][:120])
n = mine(ADM)
ok('the administrator is notified', len(n) == 1 and n[0]['title'] == 'Scheduled job failed — backup', n)
ok('the reason and next step are in the message', n and d['results']['backup'][7:].strip()[:30] in n[0]['message'] and 'Monitoring' in n[0]['message'], n)
ok('the link points at the scheduled jobs', n and n[0].get('related_type') == 'ScheduledJob', n)
ok('Super Admin is notified', len(mine(SA)) == 1, mine(SA))
ok('an ordinary user is not', mine(EU) == [], mine(EU))
s, d = call(FAIL_API, 'POST', '/monitoring/alerts/run', {'job': 'backup'}, ADM)
ok('a second failure the same day does not repeat it', len(mine(ADM)) == 1, mine(ADM))
s, d = call(FAIL_API, 'POST', '/monitoring/alerts/run', {'job': 'clean_notifications'}, ADM)
ok('a job that works raises nothing', s == 200 and not d['results']['clean_notifications'].startswith('Failed:') and len(mine(ADM)) == 1, d)
if OK_API:
    s, d = call(OK_API, 'POST', '/monitoring/alerts/run', {'job': 'backup'}, ADM)
    ok('a successful backup raises nothing', s == 200 and d['results']['backup'].startswith('Backup written') and len(mine(ADM)) == 1, d)
print('DONE fails=%d' % fails)
