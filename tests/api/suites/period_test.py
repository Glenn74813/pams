import json, urllib.request, urllib.error, urllib.parse, time, datetime, os, re, zipfile, io, subprocess, tempfile, uuid, html
B = os.environ.get('PAMS_API', __import__('os').environ['PAMS_API'])
RUN = uuid.uuid4().hex[:6]
OUT = os.environ.get('PDF_OUT')

def raw(p, tok, params=None):
    u = B + p + ('?' + urllib.parse.urlencode(params) if params else '')
    r = urllib.request.Request(u, headers={'Authorization': 'Bearer ' + tok})
    try:
        with urllib.request.urlopen(r) as x: return x.status, x.read(), dict(x.headers)
    except urllib.error.HTTPError as e: return e.code, e.read(), dict(e.headers)
def call(m, p, body=None, tok=None):
    h = {'Content-Type': 'application/json', 'Accept': 'application/json'}
    if tok: h['Authorization'] = 'Bearer ' + tok
    r = urllib.request.Request(B + p, data=json.dumps(body).encode() if body is not None else None, headers=h, method=m)
    try:
        with urllib.request.urlopen(r) as x: return x.status, json.loads(x.read() or b'{}')
    except urllib.error.HTTPError as e: return e.code, json.loads(e.read() or b'{}')
def login(e, pw):
    for _ in range(8):
        s, d = call('POST', '/auth/login', {'email': e, 'password': pw})
        if s == 200: return d['access_token']
        time.sleep(15)
    raise SystemExit((e, s, d))
fails = 0
def ok(label, cond, extra=''):
    global fails
    if not cond: fails += 1
    print(('PASS ' if cond else 'FAIL ') + label, '' if cond else extra, flush=True)
def must(s, d, code=200):
    assert s == code, (s, d); return d
today = datetime.date.today(); D = lambda n: (today + datetime.timedelta(days=n)).isoformat()
it = lambda x: x.get('item', x)
SA = login('superadmin@inventory.com', 'admin123')
u = must(*call('POST', '/users', {'name': f'ZZ Holder {RUN}', 'email': f'zz.h.{RUN}@test.local', 'password': 'Zz#Test2026', 'password_confirmation': 'Zz#Test2026', 'role_id': 15, 'department_id': 1}, SA), 201)['user']['id']
u2 = must(*call('POST', '/users', {'name': f'ZZ Second {RUN}', 'email': f'zz.s.{RUN}@test.local', 'password': 'Zz#Test2026', 'password_confirmation': 'Zz#Test2026', 'role_id': 15, 'department_id': 1}, SA), 201)['user']['id']
EUT = login(f'zz.h.{RUN}@test.local', 'Zz#Test2026')
_, cat = call('POST', '/inventory/categories', {'name': f'ZZ Period {RUN}'}, SA)
ppe = it(must(*call('POST', '/inventory/items', {'name': f'ZZ Ñ Generator {RUN}', 'category_id': cat['id'], 'unit_of_measure': 'unit', 'classification': 'ppe'}, SA), 201))
lot = must(*call('POST', f"/inventory/items/{ppe['id']}/stock", {'received_date': D(-20), 'quantity': 4, 'unit_cost': 80000, 'location': 'Store'}, SA), 201)['stock']['id']
reg = must(*call('POST', '/properties', {'item_id': ppe['id'], 'stock_id': lot, 'assigned_to': u, 'assigned_date': D(0), 'units': [{'tag_number': f'ZZ-P{i}-{RUN}'} for i in range(1, 5)]}, SA), 201)
P1, P2, P3, P4 = [p['id'] for p in reg['properties']]
PAR = reg['accountability_number']
semi = it(must(*call('POST', '/inventory/items', {'name': f'ZZ Stapler {RUN}', 'category_id': cat['id'], 'unit_of_measure': 'pc', 'classification': 'semi_expendable', 'stock_entries': [{'quantity': 10, 'unit_cost': 900, 'received_date': D(-5)}]}, SA), 201))
ink = it(must(*call('POST', '/inventory/items', {'name': f'ZZ Ink {RUN}', 'category_id': cat['id'], 'unit_of_measure': 'bottle', 'classification': 'consumable', 'min_stock': 8, 'max_stock': 30, 'stock_entries': [{'quantity': 12, 'unit_cost': 250, 'received_date': D(-5)}]}, SA), 201))
rq = must(*call('POST', '/requests', {'purpose': 'ZZ use', 'priority': 'medium', 'items': [{'item_id': ink['id'], 'quantity': 6}, {'item_id': semi['id'], 'quantity': 2}]}, EUT), 201); RQ = rq.get('data', rq).get('id') or rq['request']['id']
must(*call('PUT', f'/requests/{RQ}/approve', tok=SA))
t = must(*call('POST', f'/properties/{P1}/transfer', {'to_user': u2, 'reason': f'Reassigned {RUN}', 'transfer_date': D(0)}, SA))['transfer']
must(*call('POST', f'/properties/{P2}/return', {'reason': 'Back', 'transfer_date': D(0)}, SA))
g = must(*call('POST', '/gate-pass', {'property_id': P3, 'received_by': 'Juan', 'purpose': f'Seminar {RUN}', 'expected_return_date': D(3)}, SA), 201)
must(*call('POST', f"/gate-pass/{g['id']}/return", {'condition_on_return': 'lost', 'actual_return_date': D(0), 'returned_by': 'Juan'}, SA))
dd = must(*call('POST', '/disposal/requests', {'disposal_category': 'property', 'property_id': P2, 'disposal_type': 'destruction', 'reason': f'Beyond repair {RUN}'}, SA), 201); DI = dd.get('id') or dd['data']['id']
for st in ('submit', 'approve'): call('POST', f'/disposal/requests/{DI}/{st}', {}, SA)
must(*call('POST', f'/disposal/requests/{DI}/complete', {'disposal_date': D(0)}, SA))

# ── registers: every format, any dates ─────────────────────────────
REGS = {'par': PAR, 'ics': None, 'property-transactions': f'ZZ-P1-{RUN}', 'stock-movements': f'ZZ Ink {RUN}', 'physical-counts': None, 'wmr': f'Beyond repair {RUN}', 'iirup': f'Beyond repair {RUN}',
        'rlsddp': f'ZZ-P3-{RUN}', 'gate-passes': f'Seminar {RUN}', 'transfers': f'Reassigned {RUN}', 'reorder-point': f'ZZ Ink {RUN}'}
y1 = today.replace(month=1, day=1).isoformat(); y2 = today.replace(month=12, day=31).isoformat()
for name, needle in REGS.items():
    p = f'/reports/registers/{name}/export'
    s, b, h = raw(p, SA, {'from': y1, 'to': y2, 'format': 'xlsx'}); ok(f'{name}: Excel', s == 200 and b[:2] == b'PK', (s, b[:200]))
    s, b, h = raw(p, SA, {'from': y1, 'to': y2, 'format': 'pdf'}); ok(f'{name}: PDF', s == 200 and b[:4] == b'%PDF' and h.get('Content-Type') == 'application/pdf', (s, b[:200]))
    s, c, h = raw(p, SA, {'from': y1, 'to': y2, 'format': 'csv'}); txt = c.decode('utf-8-sig'); ok(f'{name}: CSV', s == 200 and 'text/csv' in h.get('Content-Type', ''), (s, c[:200]))
    if needle: ok(f'{name}: shows the record', needle in txt, txt[:600])
    s, c, _ = raw(p, SA, {'from': D(400), 'to': D(430), 'format': 'csv'})
    if name != 'reorder-point' and needle: ok(f'{name}: another period leaves it out', needle not in c.decode('utf-8-sig'), c[:400])
    s, c, _ = raw(p, SA, {'format': 'csv'}); ok(f'{name}: dates are optional', s == 200, s)
    s, _, _ = raw(p, EUT, {'format': 'csv'}); ok(f'{name}: needs report rights', s == 403, s)
s, c, _ = raw('/reports/registers/ics/export', SA, {'format': 'csv'}); ok('ICS register lists the semi-expendable issue with amount', f'ZZ Stapler {RUN}' in c.decode('utf-8-sig') and ('1,800.00' in c.decode('utf-8-sig') or '1800.00' in c.decode('utf-8-sig')), c.decode('utf-8-sig')[-500:])
s, c, _ = raw('/reports/registers/par/export', SA, {'format': 'csv'}); ok('PAR register lists each asset', f'ZZ-P4-{RUN}' in c.decode('utf-8-sig'))
s, c, _ = raw('/reports/registers/ics/export', SA, {'format': 'csv'}); ok('ICS register leaves PPE out', f'ZZ-P4-{RUN}' not in c.decode('utf-8-sig'))
s, c, _ = raw('/reports/registers/stock-movements/export', SA, {'format': 'csv', 'item_id': ink['id']}); txt = c.decode('utf-8-sig'); ok('stock movements for one item', f'ZZ Ink {RUN}' in txt and f'ZZ Stapler {RUN}' not in txt, txt[:400])
s, c, _ = raw('/reports/registers/reorder-point/export', SA, {'format': 'csv'}); txt = c.decode('utf-8-sig')
ink_row = [l for l in txt.splitlines() if f'ZZ Ink {RUN}' in l]; ok('reorder point: on hand, issued and order to the maximum', ink_row and ',6,8,30,6,24,' in ink_row[0].replace('"', ''), ink_row)
s, c, _ = raw('/reports/registers/iirup/export', SA, {'format': 'csv'}); ok('IIRUP register amount', ('80,000.00' in c.decode('utf-8-sig') or '80000.00' in c.decode('utf-8-sig')), c[-300:])
s, c, _ = raw('/reports/registers/physical-counts/export', SA, {'format': 'csv', 'count_type': 'ppe'}); ok('counts by type', s == 200, s)
s, b, _ = raw('/reports/registers/physical-counts/export', SA, {'count_type': 'nope'}); ok('count type is checked', s == 422, s)
s, b, _ = raw('/reports/registers/par/export', SA, {'format': 'doc'}); ok('format is checked', s == 422, s)
s, b, _ = raw('/reports/registers/par/export', SA, {'from': D(5), 'to': D(1)}); ok('end before start is refused', s == 422, s)
s, b, _ = raw('/reports/registers/nope/export', SA); ok('an unknown register is not found', s == 404, s)

# ── COA forms as PDF: same words as the Excel file ─────────────────
def sstrings(xlsx):
    z = zipfile.ZipFile(io.BytesIO(xlsx)); out = []
    if 'xl/sharedStrings.xml' in z.namelist():
        for si in re.findall(r'<si>(.*?)</si>', z.read('xl/sharedStrings.xml').decode('utf-8'), re.S):
            out.append(html.unescape(''.join(re.findall(r'<t[^>]*>(.*?)</t>', si, re.S))))
    return out
def pdftext(pdf):
    with tempfile.NamedTemporaryFile(suffix='.pdf') as f:
        f.write(pdf); f.flush()
        return subprocess.run(['pdftotext', '-raw', f.name, '-'], capture_output=True, text=True).stdout
FORMS = {
    'RSMI': (f'/reports/rsmi/export', {'year': today.year, 'month': today.month}), 'Stock Card': (f"/reports/stock-card/{ink['id']}/export", {}),
    'RPCI': ('/reports/rpci/export', {}), 'RPCSP': ('/reports/rpcsp/export', {}), 'RPCPPE': ('/reports/rpcppe/export', {}),
    'Property Card': (f'/reports/property-card/{P1}/export', {}), 'WMR': ('/reports/wmr/export', {'disposal_ids': DI}), 'IIRUP': ('/reports/iirup/export', {'disposal_ids': DI}),
    'RLSDDP': ('/reports/rlsddp/export', {'property_id': P3, 'status': 'lost', 'circumstances': 'Lost during the seminar'}),
    'RIS': (f'/requests/{RQ}/export-ris', {}), 'ICS': (f'/requests/{RQ}/export-ics', {}), 'PTR': (f"/properties/transfers/{t['id']}/export", {}),
    'PPE Ledger': (f'/reports/ppe-ledger-card/{P1}/export', {}), 'Supplies Ledger': (f"/reports/supplies-ledger-card/{ink['id']}/export", {}),
    'Semi Card': (f"/reports/semi-expendable-card/{semi['id']}/export", {}), 'Semi Ledger': (f"/reports/semi-expendable-ledger-card/{semi['id']}/export", {}),
    'RegSPI': ('/reports/regspi/export', {'from': y1, 'to': y2}), 'RSPI': ('/reports/rspi/export', {'from': y1, 'to': y2}), 'IRP': ('/reports/irp/export', {'par_number': PAR}),
    'COS Letter': (f'/reports/cos-undertaking/{u2}/export', {}),
}
norm = lambda s: re.sub(r'\s+', '', s)
for name, (path, params) in FORMS.items():
    s, x, _ = raw(path, SA, params); s2, pdf, h = raw(path, SA, {**params, 'format': 'pdf'})
    good = s == 200 and s2 == 200 and pdf[:4] == b'%PDF' and h.get('Content-Type') == 'application/pdf'
    ok(f'{name}: PDF download', good, (s, s2, pdf[:200]))
    if not good: continue
    ok(f'{name}: PDF file name', '.pdf' in h.get('Content-Disposition', '') and '.xlsx' not in h.get('Content-Disposition', ''), h.get('Content-Disposition'))
    text = norm(pdftext(pdf))
    strings = [norm(v) for v in sstrings(x) if v.strip() and v.strip() not in ('☑', '☐', '✓')]
    missing = [v for v in strings if v not in text]
    ok(f'{name}: every Excel text is in the PDF ({len(strings)})', not missing, missing[:8])
    if OUT:
        os.makedirs(OUT, exist_ok=True); open(os.path.join(OUT, name.replace(' ', '_') + '.pdf'), 'wb').write(pdf)
s, b, h = raw('/reports/rsmi/export', SA, {'year': today.year, 'month': today.month, 'format': 'xlsx'}); ok('format=xlsx stays Excel', b[:2] == b'PK', b[:20])
s, d = call('GET', '/audit-trails?search=RSMI', tok=SA)
print('DONE fails=%d' % fails)
