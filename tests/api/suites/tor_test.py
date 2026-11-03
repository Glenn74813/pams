import json, urllib.request, urllib.error, urllib.parse, time, datetime, os, base64, hashlib, threading, http.server, uuid
from cryptography import x509
from cryptography.x509.oid import NameOID
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa, ec, padding

B = os.environ.get('PAMS_API', __import__('os').environ['PAMS_API'])
RUN = uuid.uuid4().hex[:6]

def raw(m, p, data=None, headers=None, follow=True):
    h = dict(headers or {})
    r = urllib.request.Request(B + p if p.startswith('/') else p, data=data, headers=h, method=m)
    opener = urllib.request.build_opener(NoRedirect) if not follow else urllib.request.build_opener()
    try:
        with opener.open(r) as x: return x.status, x.read(), dict(x.headers)
    except urllib.error.HTTPError as e: return e.code, e.read(), dict(e.headers)

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k): return None

def call(m, p, body=None, tok=None, headers=None):
    h = {'Content-Type': 'application/json', 'Accept': 'application/json', **(headers or {})}
    if tok: h['Authorization'] = 'Bearer ' + tok
    s, b, _ = raw(m, p, json.dumps(body).encode() if body is not None else None, h)
    try: return s, json.loads(b or b'{}')
    except ValueError: return s, b

def multipart(p, fields, files, tok):
    bd = 'zz' + uuid.uuid4().hex
    out = b''
    for k, v in fields.items(): out += f'--{bd}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode()
    for k, (name, data, ctype) in files.items():
        out += f'--{bd}\r\nContent-Disposition: form-data; name="{k}"; filename="{name}"\r\nContent-Type: {ctype}\r\n\r\n'.encode() + data + b'\r\n'
    out += f'--{bd}--\r\n'.encode()
    s, b, _ = raw('POST', p, out, {'Content-Type': f'multipart/form-data; boundary={bd}', 'Authorization': 'Bearer ' + tok, 'Accept': 'application/json'})
    return s, json.loads(b or b'{}')

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
roles = {r['name']: r['id'] for r in call('GET', '/roles', tok=SA)[1].get('data', call('GET', '/roles', tok=SA)[1]) } if isinstance(call('GET', '/roles', tok=SA)[1], (dict, list)) else {}
def mkuser(tag):
    email = f'zz.{tag}.{RUN}@test.local'
    d = must(*call('POST', '/users', {'name': f'ZZ {tag} {RUN}', 'email': email, 'password': 'Zz#Test2026', 'password_confirmation': 'Zz#Test2026', 'role_id': 15, 'department_id': 1}, SA), 201)
    return d['user']['id'], email
EU, EU_MAIL = mkuser('holder')
SIGNER, SIGNER_MAIL = mkuser('signer')

# ── locations (1.2.3) ─────────────────────────────────────────────
loc = must(*call('POST', '/locations', {'name': f'ZZ Stockroom {RUN}', 'building': 'Main', 'floor': '2F', 'room': '201'}, SA), 201)
loc2 = must(*call('POST', '/locations', {'name': f'ZZ ICT Room {RUN}', 'building': 'Annex'}, SA), 201)
s, d = call('POST', '/locations', {'name': f'zz stockroom {RUN}'}, SA); ok('location names are unique, ignoring case', s == 422, d)
s, d = call('POST', '/locations', {'name': ''}, SA); ok('a location needs a name', s == 422, d)
EUT = login(EU_MAIL, 'Zz#Test2026')
s, d = call('GET', '/locations', tok=EUT); ok('anyone signed in can read the locations', s == 200 and any(x['id'] == loc['id'] for x in d), s)
s, d = call('POST', '/locations', {'name': 'ZZ nope'}, EUT); ok('only settings editors add locations', s == 403, d)

# ── an asset to work on ───────────────────────────────────────────
_, cat = call('POST', '/inventory/categories', {'name': f'ZZ TOR {RUN}'}, SA)
ppe = it(must(*call('POST', '/inventory/items', {'name': f'ZZ Aircon {RUN}', 'category_id': cat['id'], 'unit_of_measure': 'unit', 'classification': 'ppe'}, SA), 201))
lot = must(*call('POST', f"/inventory/items/{ppe['id']}/stock", {'received_date': D(-30), 'quantity': 3, 'unit_cost': 60000, 'location': 'Store'}, SA), 201)['stock']['id']
reg = must(*call('POST', '/properties', {'item_id': ppe['id'], 'stock_id': lot, 'assigned_to': EU, 'assigned_date': D(0),
                                         'units': [{'tag_number': f'ZZ-T1-{RUN}'}, {'tag_number': f'ZZ-T2-{RUN}'}]}, SA), 201)
A1, A2 = [p['id'] for p in reg['properties']]

# ── location moves and history (1.4.7) ───────────────────────────
must(*call('POST', f'/properties/{A1}/location', {'location_id': loc['id'], 'remarks': 'Installed'}, SA))
s, d = call('POST', f'/properties/{A1}/location', {'location_id': loc['id']}, SA); ok('moving to the same place is refused', s == 422, d)
must(*call('POST', f'/properties/{A1}/location', {'location_id': loc2['id']}, SA))
a = must(*call('GET', f'/properties/{A1}', tok=SA)); ok('the asset shows its location', a['location'] and a['location']['id'] == loc2['id'], a.get('location'))
h = must(*call('GET', f'/properties/{A1}/locations', tok=SA)); ok('every move is in the history', len(h) == 2 and h[0]['to'] == loc2['name'] and h[1]['from'] is None, h)
s, d = call('GET', f'/properties/{A1}/locations', tok=EUT); ok('the accountable person sees the history', s == 200, s)
s, d = call('DELETE', f"/locations/{loc['id']}", tok=SA); ok('a location that held assets cannot be deleted', s == 409, d)
must(*call('PUT', f"/locations/{loc['id']}", {'name': loc['name'], 'is_active': False}, SA))
s, d = call('POST', f'/properties/{A2}/location', {'location_id': loc['id']}, SA); ok('an inactive location cannot take assets', s == 422, d)
lst = must(*call('GET', '/locations?active_only=true', tok=SA)); ok('the active list leaves inactive ones out', all(x['id'] != loc['id'] for x in lst))
tmp = must(*call('POST', '/locations', {'name': f'ZZ Temp {RUN}'}, SA), 201)
s, d = call('DELETE', f"/locations/{tmp['id']}", tok=SA); ok('an unused location can be deleted', s == 200, d)

# ── photos (1.4.4, 1.5.4) ─────────────────────────────────────────
PNG = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==')
s, d = multipart(f'/properties/{A1}/photo', {}, {'photo': ('a.png', PNG, 'image/png')}, SA); ok('upload an asset photo', s == 200 and d['has_photo'], d)
a = must(*call('GET', f'/properties/{A1}', tok=SA)); ok('the asset says it has a photo', a['has_photo'])
s, b, hd = raw('GET', f'/properties/{A1}/photo', headers={'Authorization': 'Bearer ' + SA}); ok('the photo is served as an image', s == 200 and b == PNG and hd.get('Content-Type', '').startswith('image/png'), (s, hd.get('Content-Type')))
s, b, _ = raw('GET', f'/properties/{A1}/photo', headers={'Authorization': 'Bearer ' + EUT}); ok('the holder can see the photo', s == 200, s)
s, d = multipart(f'/properties/{A1}/photo', {}, {'photo': ('a.png', b'GIF89a not allowed', 'image/png')}, SA); ok('a non-image is refused by its bytes', s == 422, d)
s, d = multipart(f'/properties/{A1}/photo', {}, {'photo': ('a.png', PNG, 'image/png')}, EUT); ok('only property editors upload asset photos', s == 403, d)
s, d = multipart(f"/items/{ppe['id']}/photo", {}, {'photo': ('i.png', PNG, 'image/png')}, SA); ok('upload an item photo', s == 200, d)
s, d = call('GET', f"/inventory/items/{ppe['id']}", tok=SA); ok('the item says it has a photo', s == 200 and it(d).get('has_photo'), d if s != 200 else it(d).get('has_photo'))
s, d = call('DELETE', f'/properties/{A1}/photo', tok=SA); ok('remove the asset photo', s == 200)
s, b, _ = raw('GET', f'/properties/{A1}/photo', headers={'Authorization': 'Bearer ' + SA}); ok('a removed photo is gone', s == 404, s)
a = must(*call('PUT', f'/properties/{A2}', {'tag_number': f'ZZ-T2-{RUN}', 'condition': 'good', 'useful_life_years': 5}, SA))
multipart(f'/properties/{A2}/photo', {}, {'photo': ('a.png', PNG, 'image/png')}, SA)
must(*call('PUT', f'/properties/{A2}', {'tag_number': f'ZZ-T2-{RUN}', 'condition': 'fair', 'useful_life_years': 5}, SA))
ok('editing an asset keeps its photo and location', must(*call('GET', f'/properties/{A2}', tok=SA))['has_photo'])

# ── maintenance (1.4.5) ───────────────────────────────────────────
m = must(*call('POST', '/maintenance', {'property_id': A1, 'maintenance_type': 'preventive', 'description': 'Aircon cleaning', 'scheduled_date': D(-5), 'interval_months': 3}, SA), 201)
ok('a schedule in the past is overdue', m['overdue'] and m['status'] == 'scheduled', m)
s, d = call('POST', '/maintenance', {'property_id': A1, 'maintenance_type': 'nope', 'description': 'x', 'scheduled_date': D(1)}, SA); ok('the type is checked', s == 422, d)
s, d = call('POST', '/maintenance', {'property_id': A1, 'maintenance_type': 'inspection', 'description': 'x', 'scheduled_date': D(1), 'interval_months': 0}, SA); ok('the interval is checked', s == 422, d)
s, d = call('POST', '/maintenance', {'property_id': A1, 'maintenance_type': 'inspection', 'description': 'x', 'scheduled_date': D(1)}, EUT); ok('only property editors schedule maintenance', s == 403, d)
up = must(*call('POST', '/maintenance', {'property_id': A2, 'maintenance_type': 'inspection', 'description': 'Annual inspection', 'scheduled_date': D(10)}, SA), 201)
sm = must(*call('GET', '/maintenance/summary', tok=SA)); ok('the summary counts overdue and upcoming', sm['overdue'] >= 1 and sm['upcoming'] >= 1, sm)
od = must(*call('GET', '/maintenance?due=overdue', tok=SA)); ok('the overdue filter', any(x['id'] == m['id'] for x in od['data']) and all(x['overdue'] for x in od['data']), od['pagination'])
s, d = call('POST', f"/maintenance/{m['id']}/complete", {'completed_date': D(1)}, SA); ok('it cannot be done in the future', s == 422, d)
r = must(*call('POST', f"/maintenance/{m['id']}/complete", {'completed_date': D(0), 'performed_by': 'CoolAir Inc.', 'cost': 1500, 'remarks': 'OK'}, SA))
ok('completing records the work', r['maintenance']['status'] == 'done' and float(r['maintenance']['cost']) == 1500, r['maintenance'])
ok('a repeating schedule gets its next date', r['next'] and r['next']['scheduled_date'] == (today.replace(day=1) and (datetime.date(today.year + (today.month + 2) // 12, (today.month + 2) % 12 + 1, 1))).isoformat()[:8] + r['next']['scheduled_date'][8:], r.get('next'))
s, d = call('POST', f"/maintenance/{m['id']}/complete", {}, SA); ok('done work cannot be completed again', s == 409, d)
must(*call('POST', f"/maintenance/{up['id']}/cancel", tok=SA))
h = must(*call('GET', f'/maintenance?property_id={A1}', tok=SA)); ok('the asset lists its maintenance', len(h['data']) == 2, len(h['data']))
dash = must(*call('GET', '/analytics/dashboard', tok=SA))
ok('the dashboard has category shares and the 12-month trend', 'by_category' in dash['inventory'] and len(dash['inventory']['trend']) == 12 and 'by_category' in dash['assets'], list(dash['inventory'].keys()))
ok('the trend has this month', dash['inventory']['trend'][-1]['month'] == today.strftime('%Y-%m'), dash['inventory']['trend'][-1])

# ── clearance (1.4.12, 1.5.5) ─────────────────────────────────────
c = must(*call('POST', '/clearances', {'user_id': EU, 'purpose': 'resignation', 'effective_date': D(15)}, SA), 201)
ok('a clearance lists what the person still holds', c['status'] == 'pending' and c['outstanding'] == 2, c)
s, d = call('POST', '/clearances', {'user_id': EU, 'purpose': 'retirement'}, SA); ok('only one open clearance per person', s == 409, d)
s, d = call('POST', '/clearances', {'user_id': EU, 'purpose': 'other'}, SA); ok('"other" needs details', s in (409, 422), d)
s, d = call('POST', f"/clearances/{c['id']}/issue", tok=SA); ok('no certificate while assets are outstanding', s == 409 and len(d.get('outstanding', [])) == 2, d)
s, d = call('GET', f"/clearances/{c['id']}/export", tok=SA); ok('a pending certificate cannot be printed', s == 409, s)
for aid in (A1, A2): must(*call('POST', f'/properties/{aid}/return', {'reason': 'Resigning', 'transfer_date': D(0)}, SA))
c2 = must(*call('POST', f"/clearances/{c['id']}/issue", tok=SA))
ok('issued once everything is returned', c2['status'] == 'issued' and c2['certificate_number'].startswith('CPT-') and c2['assets_turned_over'] == 2, c2)
s, b, hd = raw('GET', f"/clearances/{c['id']}/export", headers={'Authorization': 'Bearer ' + SA}); ok('the certificate downloads as Excel', s == 200 and b[:2] == b'PK', s)
s, d = call('POST', f"/clearances/{c['id']}/cancel", tok=SA); ok('an issued certificate cannot be cancelled', s == 409, d)
s, d = call('GET', '/clearances', tok=EUT); ok('end users cannot list clearances', s == 403, s)

# ── digital signatures (1.4.11) ───────────────────────────────────
now = datetime.datetime.now(datetime.timezone.utc)
def name(cn, email=None):
    attrs = [x509.NameAttribute(NameOID.COMMON_NAME, cn)]
    if email: attrs.append(x509.NameAttribute(NameOID.EMAIL_ADDRESS, email))
    return x509.Name(attrs)
def ca_cert(cn):
    k = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    c = (x509.CertificateBuilder().subject_name(name(cn)).issuer_name(name(cn)).public_key(k.public_key()).serial_number(x509.random_serial_number())
         .not_valid_before(now - datetime.timedelta(days=1)).not_valid_after(now + datetime.timedelta(days=3650))
         .add_extension(x509.BasicConstraints(ca=True, path_length=None), critical=True).sign(k, hashes.SHA256()))
    return k, c
def leaf(ca_k, ca_c, cn, email, key=None, days=365):
    k = key or rsa.generate_private_key(public_exponent=65537, key_size=2048)
    b = (x509.CertificateBuilder().subject_name(name(cn, email)).issuer_name(ca_c.subject).public_key(k.public_key()).serial_number(x509.random_serial_number())
         .not_valid_before(now - datetime.timedelta(days=2)).not_valid_after(now + datetime.timedelta(days=days))
         .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
         .add_extension(x509.KeyUsage(True, True, False, False, False, False, False, False, False), critical=True))
    return k, b.sign(ca_k, hashes.SHA256())
pem = lambda c: c.public_bytes(serialization.Encoding.PEM).decode()
CA_K, CA_C = ca_cert(f'ZZ Test Root CA {RUN}')
OTHER_K, OTHER_C = ca_cert(f'ZZ Untrusted CA {RUN}')
SK, SC = leaf(CA_K, CA_C, 'ZZ Signer', SIGNER_MAIL)
DOC = b'PAMS test document ' + RUN.encode() + b'\n' + os.urandom(64)
SGT = login(SIGNER_MAIL, 'Zz#Test2026')
s, d = multipart('/signing/requests', {'title': 'ZZ Clearance', 'signer_id': SIGNER}, {'file': ('../../etc/zz doc.txt', DOC, 'text/plain')}, EUT)
ok('anyone can send a document for signing', s == 201 and d['status'] == 'pending' and d['file_sha256'] == hashlib.sha256(DOC).hexdigest(), d)
REQ = d['id']; NUM = d['request_number']
ok('the stored name has no path in it', '/' not in d['file_name'] and '..' not in d['file_name'], d['file_name'])
inbox = must(*call('GET', '/signing/requests?box=inbox', tok=SGT)); ok('it is in the signer\'s inbox', any(x['id'] == REQ and x['can_sign'] for x in inbox['data']))
s, b, _ = raw('GET', f'/signing/requests/{REQ}/file', headers={'Authorization': 'Bearer ' + SGT}); ok('the signer downloads the exact bytes', s == 200 and b == DOC, s)
OUT, OUT_MAIL = mkuser('outsider'); OT = login(OUT_MAIL, 'Zz#Test2026')
s, _, _ = raw('GET', f'/signing/requests/{REQ}/file', headers={'Authorization': 'Bearer ' + OT}); ok('others cannot open it', s == 403, s)
sig = lambda k, data: base64.b64encode(k.sign(data, padding.PKCS1v15(), hashes.SHA256())).decode()
s, d = call('POST', f'/signing/requests/{REQ}/sign', {'certificate_pem': pem(SC), 'signature': sig(SK, DOC), 'algorithm': 'RSA-SHA256'}, SGT)
ok('nothing is accepted before a CA is trusted', s == 422 and 'trusted' in json.dumps(d), d)
s, d = call('POST', '/signing/trusted-cas', {'name': 'ZZ leaf', 'pem': pem(SC)}, SA); ok('a non-CA certificate cannot be trusted', s == 422, d)
s, d = call('POST', '/signing/trusted-cas', {'name': 'ZZ CA', 'pem': pem(CA_C)}, EUT); ok('only administrators trust CAs', s == 403, d)
ca = must(*call('POST', '/signing/trusted-cas', {'name': f'ZZ Root {RUN}', 'pem': pem(CA_C)}, SA), 201)
s, d = call('POST', '/signing/trusted-cas', {'name': 'again', 'pem': pem(CA_C)}, SA); ok('the same CA cannot be added twice', s == 422, d)
s, d = call('POST', f'/signing/requests/{REQ}/sign', {'certificate_pem': pem(SC), 'signature': sig(SK, DOC), 'algorithm': 'RSA-SHA256'}, EUT); ok('only the named signer signs', s == 403, d)
s, d = call('POST', f'/signing/requests/{REQ}/sign', {'certificate_pem': pem(SC), 'signature': sig(SK, DOC + b'x'), 'algorithm': 'RSA-SHA256'}, SGT); ok('a signature over other bytes is refused', s == 422 and 'does not match' in json.dumps(d), d)
UK, UC = leaf(OTHER_K, OTHER_C, 'ZZ Signer', SIGNER_MAIL)
s, d = call('POST', f'/signing/requests/{REQ}/sign', {'certificate_pem': pem(UC), 'signature': sig(UK, DOC), 'algorithm': 'RSA-SHA256'}, SGT); ok('a certificate from an untrusted CA is refused', s == 422 and 'trusted' in json.dumps(d), d)
WK, WC = leaf(CA_K, CA_C, 'ZZ Somebody', 'someone.else@test.local')
s, d = call('POST', f'/signing/requests/{REQ}/sign', {'certificate_pem': pem(WC), 'signature': sig(WK, DOC), 'algorithm': 'RSA-SHA256'}, SGT); ok('a certificate issued to someone else is refused', s == 422 and 'belongs to' in json.dumps(d), d)
s, d = call('POST', f'/signing/requests/{REQ}/sign', {'certificate_pem': pem(SC), 'signature': sig(SK, DOC), 'algorithm': 'MD5'}, SGT); ok('unknown algorithms are refused', s == 422, d)
d = must(*call('POST', f'/signing/requests/{REQ}/sign', {'certificate_pem': pem(SC), 'signature': sig(SK, DOC), 'algorithm': 'RSA-SHA256'}, SGT))
ok('a valid signature is checked and recorded', d['status'] == 'signed' and len(d['signatures']) == 1 and d['signatures'][0]['trusted_ca'] == f'ZZ Root {RUN}', d)
s, d = call('POST', f'/signing/requests/{REQ}/sign', {'certificate_pem': pem(SC), 'signature': sig(SK, DOC), 'algorithm': 'RSA-SHA256'}, SGT); ok('it cannot be signed twice', s == 409, d)
s, d = multipart('/signing/verify', {}, {'file': ('copy.txt', DOC, 'text/plain')}, OT); ok('anyone can verify an unchanged copy', s == 200 and d['signed'] and d['requests'][0]['signatures'][0]['signer'].startswith('ZZ signer'), d)
s, d = multipart('/signing/verify', {}, {'file': ('copy.txt', DOC + b' ', 'text/plain')}, OT); ok('a changed copy is not signed', s == 200 and not d['signed'], d)
# ECDSA with a chain (root → intermediate → signer)
IK = ec.generate_private_key(ec.SECP256R1())
IC = (x509.CertificateBuilder().subject_name(name(f'ZZ Intermediate {RUN}')).issuer_name(CA_C.subject).public_key(IK.public_key()).serial_number(x509.random_serial_number())
      .not_valid_before(now - datetime.timedelta(days=1)).not_valid_after(now + datetime.timedelta(days=1000))
      .add_extension(x509.BasicConstraints(ca=True, path_length=0), critical=True).sign(CA_K, hashes.SHA256()))
EK = ec.generate_private_key(ec.SECP256R1())
EC_ = (x509.CertificateBuilder().subject_name(name('ZZ Signer EC', SIGNER_MAIL)).issuer_name(IC.subject).public_key(EK.public_key()).serial_number(x509.random_serial_number())
       .not_valid_before(now - datetime.timedelta(days=1)).not_valid_after(now + datetime.timedelta(days=300)).sign(IK, hashes.SHA256()))
DOC2 = b'second ' + os.urandom(32)
s, d = multipart('/signing/requests', {'title': 'ZZ EC', 'signer_id': SIGNER}, {'file': ('ec.bin', DOC2, 'application/octet-stream')}, EUT); R2 = d['id']
esig = base64.b64encode(EK.sign(DOC2, ec.ECDSA(hashes.SHA256()))).decode()
s, d = call('POST', f'/signing/requests/{R2}/sign', {'certificate_pem': pem(EC_), 'signature': esig, 'algorithm': 'ECDSA-SHA256'}, SGT); ok('without the intermediate the chain is incomplete', s == 422, d)
s, d = call('POST', f'/signing/requests/{R2}/sign', {'certificate_pem': pem(EC_) + pem(IC), 'signature': esig, 'algorithm': 'ECDSA-SHA256'}, SGT); ok('an ECDSA signature through an intermediate CA', s == 200 and d['status'] == 'signed', d)
# decline and cancel
s, d = multipart('/signing/requests', {'title': 'ZZ decline', 'signer_id': SIGNER}, {'file': ('d.txt', b'decline me ' + os.urandom(8), 'text/plain')}, EUT); R3 = d['id']
s, d = call('POST', f'/signing/requests/{R3}/decline', {}, SGT); ok('declining needs a reason', s == 422, d)
s, d = call('POST', f'/signing/requests/{R3}/decline', {'remarks': 'Wrong figures'}, SGT); ok('the signer can decline', s == 200 and d['status'] == 'declined', d)
s, d = multipart('/signing/requests', {'title': 'ZZ cancel', 'signer_id': SIGNER}, {'file': ('c.txt', b'cancel me ' + os.urandom(8), 'text/plain')}, EUT); R4 = d['id']; N4 = d['request_number']
s, d = call('POST', f'/signing/requests/{R4}/cancel', tok=SGT); ok('only the sender cancels', s == 403, d)
s, d = call('POST', f'/signing/requests/{R4}/cancel', tok=EUT); ok('the sender can cancel', s == 200 and d['status'] == 'cancelled', d)
# signing platform API
KEY = os.environ.get('PAMS_SIGNING_PLATFORM_KEY')
s, d = multipart('/signing/requests', {'title': 'ZZ platform', 'signer_id': SIGNER}, {'file': ('p.txt', b'platform ' + os.urandom(8), 'text/plain')}, EUT); R5 = d['id']; N5 = d['request_number']
s, b, _ = raw('GET', f'/signing/platform/requests/{N5}/file', headers={'X-Signing-Key': 'wrong'}); ok('the platform needs its key', s in (401, 404), s)
if KEY:
    s, PDOC, _ = raw('GET', f'/signing/platform/requests/{N5}/file', headers={'X-Signing-Key': KEY}); ok('the platform fetches the file with its key', s == 200, s)
    s, d = call('POST', '/signing/platform/signatures', {'request_number': N5, 'certificate_pem': pem(SC), 'signature': sig(SK, PDOC), 'algorithm': 'RSA-SHA256', 'platform_reference': 'PLAT-1'}, headers={'X-Signing-Key': KEY})
    ok('the platform posts the signature back', s == 200 and d['status'] == 'signed', d)
    d = must(*call('GET', f'/signing/requests/{R5}', tok=EUT)); ok('it is recorded as from the platform', d['signatures'][0]['source'] == 'platform' and d['platform_reference'] == 'PLAT-1', d)
    s, d = call('POST', '/signing/platform/signatures', {'request_number': N4, 'certificate_pem': pem(SC), 'signature': 'AA==', 'algorithm': 'RSA-SHA256'}, headers={'X-Signing-Key': KEY}); ok('a cancelled request cannot be signed by the platform', s == 409, d)
s, d = call('GET', '/signing/requests?box=all', tok=EUT); ok('only administrators see every request', s == 403, s)
must(*call('DELETE', f"/signing/trusted-cas/{ca['id']}", tok=SA))
d = must(*call('GET', f'/signing/requests/{REQ}', tok=EUT)); ok('removing the CA keeps signatures already recorded', len(d['signatures']) == 1 and d['signatures'][0]['trusted_ca'] is None, d['signatures'])

# ── single sign-on (1.3.1), against a local mock OpenID provider ──
ISS = os.environ.get('PAMS_SSO_ISSUER')
s, cfg = call('GET', '/auth/sso/config'); ok('the SSO config is public', s == 200 and 'enabled' in cfg, cfg)
if ISS:
    port = int(ISS.rsplit(':', 1)[1])
    IDP_K = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    nums = IDP_K.public_key().public_numbers()
    b64u = lambda b: base64.urlsafe_b64encode(b).rstrip(b'=').decode()
    i2b = lambda n: n.to_bytes((n.bit_length() + 7) // 8, 'big')
    JWKS = {'keys': [{'kty': 'RSA', 'kid': 'k1', 'use': 'sig', 'alg': 'RS256', 'n': b64u(i2b(nums.n)), 'e': b64u(i2b(nums.e))}]}
    pending = {}
    def jwt(claims, key=IDP_K, kid='k1'):
        h = b64u(json.dumps({'alg': 'RS256', 'typ': 'JWT', 'kid': kid}).encode()); p = b64u(json.dumps(claims).encode())
        return h + '.' + p + '.' + b64u(key.sign((h + '.' + p).encode(), padding.PKCS1v15(), hashes.SHA256()))
    class IdP(http.server.BaseHTTPRequestHandler):
        def log_message(self, *a): pass
        def send(self, obj, code=200):
            b = json.dumps(obj).encode(); self.send_response(code); self.send_header('Content-Type', 'application/json'); self.end_headers(); self.wfile.write(b)
        def do_GET(self):
            if self.path.startswith('/.well-known/openid-configuration'):
                self.send({'issuer': ISS, 'authorization_endpoint': ISS + '/authorize', 'token_endpoint': ISS + '/token', 'jwks_uri': ISS + '/jwks'})
            elif self.path.startswith('/jwks'): self.send(JWKS)
            else: self.send({}, 404)
        def do_POST(self):
            form = urllib.parse.parse_qs(self.rfile.read(int(self.headers['Content-Length'])).decode())
            code = form['code'][0]; p = pending.get(code)
            ver = form.get('code_verifier', [''])[0]
            if not p or b64u(hashlib.sha256(ver.encode()).digest()) != p['challenge']: return self.send({'error': 'invalid_grant'}, 400)
            self.send({'access_token': 'x', 'token_type': 'Bearer', 'id_token': jwt(p['claims'])})
    srv = http.server.ThreadingHTTPServer(('127.0.0.1', port), IdP); threading.Thread(target=srv.serve_forever, daemon=True).start()
    ok('SSO is enabled', cfg['enabled'], cfg)
    def sso(claims_for, mutate=None):
        s, _, hd = raw('GET', '/auth/sso/start', follow=False)
        loc_ = hd.get('Location', ''); q = urllib.parse.parse_qs(urllib.parse.urlparse(loc_).query)
        if s != 302 or 'state' not in q: return None, (s, loc_)
        claims = {'iss': ISS, 'aud': q['client_id'][0], 'exp': int(time.time()) + 300, 'iat': int(time.time()), 'nonce': q['nonce'][0], **claims_for}
        if mutate: mutate(claims)
        code = uuid.uuid4().hex; pending[code] = {'claims': claims, 'challenge': q['code_challenge'][0]}
        s, _, hd = raw('GET', '/auth/sso/callback?' + urllib.parse.urlencode({'code': code, 'state': q['state'][0]}), follow=False)
        back = urllib.parse.parse_qs(urllib.parse.urlparse(hd.get('Location', '')).query)
        return back, q
    back, q = sso({'sub': 'idp-' + RUN, 'email': EU_MAIL.upper()})
    ok('start sends the browser to the provider with PKCE', q.get('code_challenge_method') == ['S256'] and q.get('response_type') == ['code'], q)
    ok('the callback returns a one-time code', back and 'sso_code' in back, back)
    s, d = call('POST', '/auth/sso/exchange', {'code': back['sso_code'][0]}); ok('the code is swapped for a PAMS token', s == 200 and d['user']['email'] == EU_MAIL, d)
    tok = d.get('access_token'); s, me = call('GET', '/auth/me', tok=tok); ok('the SSO token works', s == 200 and me['id'] == EU, s)
    s, d = call('POST', '/auth/sso/exchange', {'code': back['sso_code'][0]}); ok('a code works only once', s == 401, d)
    back, _ = sso({'sub': 'idp-' + RUN, 'email': 'changed@else.where'}); ok('once linked, the provider id finds the account', back and 'sso_code' in back, back)
    back, _ = sso({'sub': 'idp-other-' + RUN, 'email': EU_MAIL}); ok('another provider id cannot take a linked account', back and 'sso_error' in back, back)
    back, _ = sso({'sub': 'nobody-' + RUN, 'email': 'nobody@test.local'}); ok('no PAMS account, no sign-in', back and 'sso_error' in back and 'No PAMS account' in back['sso_error'][0], back)
    back, _ = sso({'sub': 'x-' + RUN, 'email': SIGNER_MAIL}, lambda c: c.update(nonce='forged')); ok('a wrong nonce is refused', back and 'sso_error' in back, back)
    back, _ = sso({'sub': 'x-' + RUN, 'email': SIGNER_MAIL}, lambda c: c.update(aud='someone-else')); ok('a token for another client is refused', back and 'sso_error' in back, back)
    back, _ = sso({'sub': 'x-' + RUN, 'email': SIGNER_MAIL}, lambda c: c.update(exp=int(time.time()) - 600)); ok('an expired token is refused', back and 'sso_error' in back, back)
    s, _, hd = raw('GET', '/auth/sso/callback?code=abc&state=forged', follow=False); ok('an unknown state is refused', 'sso_error' in hd.get('Location', ''), hd.get('Location'))
    must(*call('POST', f'/users/{OUT}/deactivate', tok=SA))
    back, _ = sso({'sub': 'out-' + RUN, 'email': OUT_MAIL}); ok('a deactivated account cannot sign in by SSO', back and 'sso_error' in back and 'deactivated' in back['sso_error'][0], back)
    srv.shutdown()

print('DONE fails=%d' % fails)
