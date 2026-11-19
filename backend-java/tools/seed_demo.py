#!/usr/bin/env python3
"""
Fills a PAMS database with demo records: ten in every module, at different stages, all created through the API
so every business rule, number series and audit entry is the real thing.

    PAMS_API=http://127.0.0.1:8095/api python3 tools/seed_demo.py
    PAMS_DB=mssql PAMS_API=http://127.0.0.1:8096/api python3 tools/seed_demo.py
    python3 tools/seed_demo.py --next-year-only    # only next fiscal year's PPMPs and draft APP, on an already seeded database
    python3 tools/seed_demo.py --link-items        # link the plan lines of an already seeded database to the stock items they buy

Run it on an empty database (fresh migration, or after clearing the data). It needs:
  - the backend running at PAMS_API with the seeded Super Admin (superadmin@inventory.com / admin123);
  - direct database access, used only to move a few deadlines into the past (an RFQ or bidding can only be
    closed after its deadline) and to date one stock lot as expired: psql with DB_PASSWORD from backend-java/.env,
    or with PAMS_DB=mssql the SQL Server container (PAMS_MSSQL_CONTAINER, default pams-mssql; database
    PAMS_MSSQL_DB, default pams) with MSSQL_SA_PASSWORD from .env.
Some records exist once by rule (one APP per fiscal year, one petty cash fund here). Demo staff sign in with the
password in DEMO_PASSWORD below.
"""
import datetime
import json
import os
import pathlib
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

API = os.environ.get('PAMS_API', 'http://127.0.0.1:8095/api')
MSSQL = os.environ.get('PAMS_DB') == 'mssql'
DEMO_PASSWORD = 'Demo#Pams2026'
ENV = pathlib.Path(__file__).resolve().parent.parent / '.env'
TODAY = datetime.date.today()
YEAR = TODAY.year


def day(n):
    return (TODAY + datetime.timedelta(days=n)).isoformat()


def manila_now():
    return (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=8)).replace(tzinfo=None)


class Fail(Exception):
    pass


def call(method, path, body=None, tok=None, expect=(200, 201)):
    headers = {'Content-Type': 'application/json', 'Accept': 'application/json'}
    if tok:
        headers['Authorization'] = 'Bearer ' + tok
    req = urllib.request.Request(API + path, data=json.dumps(body).encode() if body is not None else None, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as r:
            status, data = r.status, json.loads(r.read() or b'{}')
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            status, data = e.code, json.loads(raw or b'{}')
        except ValueError:
            status, data = e.code, {'raw': raw[:300].decode('utf-8', 'replace')}
    if expect and status not in (expect if isinstance(expect, tuple) else (expect,)):
        raise Fail(f'{method} {path} -> {status}: {json.dumps(data)[:600]}')
    return data


def optional(method, path, body=None, tok=None):
    """A step that adds colour but may be refused by a rule; the seed goes on and says so."""
    try:
        return call(method, path, body, tok)
    except Fail as e:
        print(f'  (skipped: {e})', flush=True)
        return None


def upload(path, fields, filename, content, tok):
    boundary = uuid.uuid4().hex
    parts = []
    for k, v in fields.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: application/pdf\r\n\r\n'.encode()
                 + content + b'\r\n')
    parts.append(f'--{boundary}--\r\n'.encode())
    req = urllib.request.Request(API + path, data=b''.join(parts), method='POST',
                                 headers={'Authorization': 'Bearer ' + tok, 'Content-Type': f'multipart/form-data; boundary={boundary}'})
    try:
        with urllib.request.urlopen(req) as r:
            return json.loads(r.read())
    except urllib.error.HTTPError as e:
        raise Fail(f'POST {path} -> {e.code}: {e.read()[:400]!r}')


def env(key):
    return next((l.split('=', 1)[1].strip() for l in ENV.read_text().splitlines() if l.startswith(key + '=')), '')


def sql(q):
    """Runs one portable UPDATE on the database behind the API."""
    if MSSQL:
        subprocess.run(['docker', 'exec', os.environ.get('PAMS_MSSQL_CONTAINER', 'pams-mssql'), '/opt/mssql-tools18/bin/sqlcmd', '-C', '-S', 'localhost',
                        '-U', 'sa', '-P', env('MSSQL_SA_PASSWORD'), '-d', os.environ.get('PAMS_MSSQL_DB', 'pams'), '-b', '-I', '-Q', 'SET NOCOUNT ON; ' + q],
                       check=True, capture_output=True)
        return
    subprocess.run(['psql', '-h', os.environ.get('PGHOST', '127.0.0.1'), '-U', os.environ.get('PGUSER', 'inventory'),
                    '-d', os.environ.get('PGDATABASE', 'inventory'), '-Atqc', q], check=True, env={**os.environ, 'PGPASSWORD': env('DB_PASSWORD')})


def login(email, password):
    for _ in range(10):
        req = urllib.request.Request(API + '/auth/login', data=json.dumps({'email': email, 'password': password}).encode(),
                                     headers={'Content-Type': 'application/json'}, method='POST')
        try:
            with urllib.request.urlopen(req) as r:
                return json.loads(r.read())['access_token']
        except urllib.error.HTTPError as e:
            if e.code != 429:
                raise Fail(f'login {email}: {e.code} {e.read()[:200]!r}')
            time.sleep(15)  # five sign-ins a minute per address
    raise Fail(f'login {email}: still rate-limited')


def pdf(text):
    """A one-page PDF with a line of text, enough for the document register."""
    stream = f'BT /F1 14 Tf 72 720 Td ({text}) Tj ET'.encode()
    objs = [b'<< /Type /Catalog /Pages 2 0 R >>', b'<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
            b'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
            b'<< /Length ' + str(len(stream)).encode() + b' >>\nstream\n' + stream + b'\nendstream',
            b'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>']
    out, offsets = b'%PDF-1.4\n', []
    for i, o in enumerate(objs, 1):
        offsets.append(len(out))
        out += f'{i} 0 obj\n'.encode() + o + b'\nendobj\n'
    xref = len(out)
    out += f'xref\n0 {len(objs) + 1}\n0000000000 65535 f \n'.encode() + b''.join(f'{o:010d} 00000 n \n'.encode() for o in offsets)
    return out + f'trailer << /Size {len(objs) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n'.encode()


def step(title):
    print(f'· {title}', flush=True)


def lots_of(item_id, tok):
    lots = call('GET', f'/inventory/items/{item_id}/lots', tok=tok)
    return lots.get('data', lots) if isinstance(lots, dict) else lots


# UACS object codes (Revised Chart of Accounts) for the demo plan lines. A unit cost of ₱50,000 or more is capital
# outlay (PPE); equipment and furniture below it are semi-expendable; the rest are supplies. Illustrative: the agency's
# accountant confirms the codes it charges to.
UACS_PPE = [(('laptop', 'desktop', 'switch', 'printer', 'plotter', 'document management', 'cctv', 'camera'), '1060503000'),  # ICT equipment
            (('air-conditioning', 'vault'), '1060502000'),                                                                    # office equipment
            (('generator',), '1060501000'),                                                                                   # machinery
            (('analyzer', 'dental'), '1060511000'),                                                                           # medical equipment
            (('vehicle',), '1060601000'),                                                                                     # motor vehicles
            (('gnss', 'total station'), '1060514000')]                                                                        # technical and scientific
UACS_SEMI = [(('projector', 'ups', 'printer'), '5020321003'),                                         # semi-expendable ICT equipment
             (('solar',), '5020321001'),                                                               # semi-expendable machinery
             (('chair', 'cabinet', 'whiteboard', 'table', 'rack'), '5020322001'),                      # semi-expendable furniture and fixtures
             (('law books',), '5020322002')]                                                           # semi-expendable books
UACS_SUPPLIES = [(('bond paper', 'ballpen', 'paper clip', 'folder', 'record book', 'staple', 'envelope', 'toner', 'drafting'), '5020301000'),  # office supplies
                 (('alcohol', 'first aid', 'thermometer'), '5020308000'),                                # medical, dental and laboratory supplies
                 (('medicines',), '5020307000')]                                                        # drugs and medicines


def uacs(desc, unit_cost):
    d = desc.lower()
    for words, code in UACS_SUPPLIES:
        if any(w in d for w in words):
            return code
    if unit_cost >= 50000:
        return next((code for words, code in UACS_PPE if any(w in d for w in words)), '1060599000')       # other machinery and equipment
    return next((code for words, code in UACS_SEMI if any(w in d for w in words)), '5020321002' if any(
        w in d for w in ('extinguisher', 'fan', 'monitor', 'counter', 'truck', 'jack', 'surge', 'label', 'air-conditioning')) else '5020399000')  # semi-expendable office equipment; other supplies


def catalogue(SA):
    """Stock item ids by lower-case name."""
    out = {}
    for i in call('GET', '/inventory/items/options', tok=SA):
        out[i['name'].lower()] = i['id']
    return out


def item_for(desc, items):
    """The stock item a plan line buys: the same name, or one a prefix of the other (\"Ballpen, black\" and \"Ballpen, black, 0.5mm\")."""
    d = desc.lower()
    return next((i for name, i in items.items() if d == name or name.startswith(d) or d.startswith(name)), None)


def plan_next_year(SA, depts):
    """Next fiscal year's PPMPs, being prepared now: drafts, submitted, under review, returned and approved, and the
    draft APP consolidated from those approved so far. The planning page opens on the coming year."""
    step(f'PPMPs and the draft APP for FY {YEAR + 1}')
    GAA = 'General Appropriations Act (GAA)'
    PS, SVP, CB = 'Procurement from PS-DBM', 'Small Value Procurement', 'Competitive Bidding'
    items = catalogue(SA)

    def line(desc, unit, qty, cost, mode, q):
        return {'general_description': desc, 'code': uacs(desc, cost), 'unit': unit, 'quantity': qty, 'estimated_unit_cost': cost, 'procurement_mode': mode,
                'procurement_category': 'Goods', 'schedule': q, 'fund_source': GAA, 'item_id': item_for(desc, items)}
    plans = [
        ('ADMIN', 'approved', [line('Bond paper, A4, 80gsm', 'ream', 120, 265, PS, 'Q1'), line('Office table, executive', 'unit', 4, 12500, SVP, 'Q2'),
                               line('Air-conditioning unit, 1.5HP inverter', 'unit', 60, 38000, CB, 'Q2')]),
        ('FIN', 'approved', [line('Ballpen, black', 'piece', 600, 10, PS, 'Q1'), line('Money counter and detector', 'unit', 2, 15500, SVP, 'Q1'),
                             line('Fireproof vault, 1.5 tons', 'unit', 1, 450000, SVP, 'Q3')]),
        ('IT', 'approved', [line('Laptop computer, 14", Core i7', 'unit', 40, 72000, CB, 'Q1'), line('UPS, 1500VA', 'unit', 15, 6800, SVP, 'Q2'),
                            line('Paper clips, jumbo', 'box', 60, 25, PS, 'Q1')]),
        ('HRMO', 'under_review', [line('Isopropyl alcohol 70%, 500 ml', 'bottle', 120, 100, PS, 'Q1'), line('Training kits for orientation', 'set', 40, 650, SVP, 'Q2')]),
        ('PDO', 'returned', [line('Plotter, A0 size', 'unit', 1, 280000, SVP, 'Q2'), line('Drafting supplies', 'lot', 1, 45000, SVP, 'Q1')]),
        ('LEGAL', 'submitted', [line('Law books, latest editions', 'set', 3, 28000, SVP, 'Q1'), line('Folder, tagboard, legal size', 'pack', 40, 170, PS, 'Q1')]),
        ('ENG', 'submitted', [line('Total station, survey grade', 'unit', 4, 650000, CB, 'Q2'), line('Safety helmets and vests', 'set', 30, 950, SVP, 'Q1')]),
        ('PIO', 'draft', [line('Mirrorless camera with lens kit', 'unit', 2, 95000, SVP, 'Q2'), line('Envelope, brown, long', 'box', 25, 300, PS, 'Q1')]),
        ('MED', 'draft', [line('Medicines and medical supplies', 'lot', 1, 2500000, CB, 'Q1'), line('Digital thermometer', 'piece', 30, 450, SVP, 'Q1')]),
        ('SPU', 'draft', [line('Pallet jack, 2.5 tons', 'unit', 1, 28000, SVP, 'Q2'), line('Storage rack, steel, 5-layer', 'unit', 8, 7800, SVP, 'Q2')]),
    ]
    approved = 0
    for code, stage, lines in plans:
        p = call('POST', '/procurement-planning/ppmps', {'department_id': depts[code], 'fiscal_year': YEAR + 1, 'title': f'PPMP FY {YEAR + 1} — {code}',
                                                         'items': lines}, SA)
        if stage == 'draft':
            continue
        call('POST', f"/procurement-planning/ppmps/{p['id']}/submit", tok=SA)
        if stage == 'submitted':
            continue
        call('POST', f"/procurement-planning/ppmps/{p['id']}/review", tok=SA)
        if stage == 'under_review':
            continue
        if stage == 'returned':
            call('POST', f"/procurement-planning/ppmps/{p['id']}/return", {'return_reason': 'Attach the market survey for the plotter and split the drafting supplies by item.'}, SA)
            continue
        call('POST', f"/procurement-planning/ppmps/{p['id']}/approve", tok=SA)
        approved += 1
    if approved:
        call('POST', '/procurement-planning/apps/generate', {'fiscal_year': YEAR + 1}, SA)  # left as a draft for review


def main():
    SA = login('superadmin@inventory.com', 'admin123')
    if '--fill-codes' in sys.argv:
        # Accounting codes on plan lines, by the rules above, for plans seeded before they had them (a direct UPDATE: approved plans are locked).
        step('UACS codes on PPMP lines')
        ids, page = [], 1
        while True:
            r = call('GET', f'/procurement-planning/ppmps?page={page}', tok=SA)
            ids += [p['id'] for p in r['data']]
            if page >= r['pagination']['last_page']:
                break
            page += 1
        for pid in ids:
            for l in call('GET', f'/procurement-planning/ppmps/{pid}', tok=SA)['items']:
                code = uacs(l['general_description'], float(l['estimated_unit_cost']))
                if l.get('code') != code:
                    sql(f"update ppmp_items set code = '{code}' where id = {l['id']}")
        print('Done.')
        return
    if '--link-items' in sys.argv:
        # Stock items on plan lines seeded before lines could name them (a direct UPDATE: approved plans are locked); APP lines copy them.
        step('stock items on PPMP and APP lines')
        items = catalogue(SA)
        ids, page = [], 1
        while True:
            r = call('GET', f'/procurement-planning/ppmps?page={page}', tok=SA)
            ids += [p['id'] for p in r['data']]
            if page >= r['pagination']['last_page']:
                break
            page += 1
        for pid in ids:
            for l in call('GET', f'/procurement-planning/ppmps/{pid}', tok=SA)['items']:
                item = item_for(l['general_description'], items)
                if item and not l.get('item'):
                    sql(f"update ppmp_items set item_id = {item} where id = {l['id']}")
        sql('update app_items set item_id = (select pi.item_id from ppmp_items pi where pi.id = app_items.ppmp_item_id) where item_id is null and ppmp_item_id is not null')
        print('Done.')
        return
    if '--next-year-only' in sys.argv:
        plan_next_year(SA, {d['code']: d['id'] for d in call('GET', '/departments', tok=SA)['data']})
        print('Done.')
        return

    # ── offices and people ──────────────────────────────────────────────
    step('offices and demo staff')
    depts = {d['code']: d['id'] for d in call('GET', '/departments', tok=SA)['data']}
    for code, name in [('ADMIN', 'Administrative Division'), ('FIN', 'Finance Division'), ('SPU', 'Supply and Property Unit'),
                       ('HRMO', 'Human Resource Management Office'), ('PDO', 'Planning and Development Office'), ('LEGAL', 'Legal Services Office'),
                       ('ENG', 'Engineering Division'), ('PIO', 'Public Information Office'), ('MED', 'Medical and Dental Unit'), ('IT', 'Information Technology')]:
        if code not in depts:
            depts[code] = call('POST', '/departments', {'name': name, 'code': code}, SA)['id']
    staff = [('Maria Santos', 'maria.santos', 3, 'SPU'), ('Jose Reyes', 'jose.reyes', 13, 'SPU'), ('Ana Cruz', 'ana.cruz', 6, 'SPU'),
             ('Ramon Dela Cruz', 'ramon.delacruz', 12, 'ADMIN'), ('Liza Mendoza', 'liza.mendoza', 15, 'FIN'),
             ('Carlo Bautista', 'carlo.bautista', 7, 'IT'), ('Grace Villanueva', 'grace.villanueva', 8, 'FIN'),
             ('Paolo Garcia', 'paolo.garcia', 10, 'ADMIN'), ('Joy Aquino', 'joy.aquino', 11, 'SPU'), ('Ben Torres', 'ben.torres', 15, 'HRMO'),
             ('Nora Flores', 'nora.flores', 5, 'ADMIN'), ('Rosa Navarro', 'rosa.navarro', 15, 'LEGAL'), ('Marco Lim', 'marco.lim', 15, 'ENG'),
             ('Dina Castro', 'dina.castro', 15, 'PIO'), ('Victor Ramos', 'victor.ramos', 15, 'MED'), ('Elena Pascual', 'elena.pascual', 9, 'FIN')]
    existing = {u['email']: u['id'] for u in call('GET', '/users/all', tok=SA)['data']}
    U = {}
    for name, handle, role, dept in staff:
        email = f'{handle}@agency.gov.ph'
        U[handle] = existing.get(email) or call('POST', '/users', {'name': name, 'email': email, 'password': DEMO_PASSWORD,
                                                                  'password_confirmation': DEMO_PASSWORD, 'role_id': role,
                                                                  'department_id': depts[dept]}, SA)['user']['id']
    T = {h: login(f'{h}@agency.gov.ph', DEMO_PASSWORD) for h in ('maria.santos', 'jose.reyes', 'ana.cruz', 'liza.mendoza', 'ben.torres', 'ramon.delacruz', 'nora.flores')}

    # ── suppliers ───────────────────────────────────────────────────────
    step('suppliers with compliance documents')
    SUP = {}
    for i, (name, contact, city) in enumerate([('Mega Office Supplies Trading', 'Rhea Lim', 'Quezon City'),
                                               ('Northpoint IT Solutions Inc.', 'Mark Uy', 'Makati City'),
                                               ('Bayanihan Furniture Corporation', 'Edwin Sison', 'Pasig City'),
                                               ('MedFirst Pharma Distribution', 'Cecille Ramos', 'Manila'),
                                               ('CleanPro Janitorial Supply', 'Arnel Diaz', 'Caloocan City'),
                                               ('Safeguard Fire Protection Inc.', 'Lorna Tan', 'Mandaluyong City'),
                                               ('Printworks Publishing Corp.', 'Dennis Co', 'San Juan City'),
                                               ('Healthline Medical Trading', 'Mylene Ortiz', 'Taguig City'),
                                               ('Voltmax Electrical Supply', 'Rodel Santos', 'Valenzuela City'),
                                               ('Primeline Builders and Equipment Corp.', 'Antonio Velasco', 'Parañaque City')], 1):
        s = call('POST', '/suppliers', {'name': name, 'contact_person': contact, 'email': f'sales{i}@{name.split()[0].lower()}.com.ph',
                                        'phone': f'(02) 8{i % 10}12-34{i % 10}0', 'address': f'{100 + i} Rizal Avenue, {city}', 'tin': f'{i:03d}-123-456-000',
                                        'philgeps_reg_number': f'2026-{i:02d}1234', 'credit_terms': '30 days', 'is_msme': i % 2 == 1}, SA)
        sid = s.get('id') or s.get('supplier', {}).get('id')
        for doc in ['PhilGEPS Certificate of Registration (Platinum)', 'PhilGEPS Organization ID', "Mayor's Permit", 'Income or Business Tax Return', 'Omnibus Sworn Statement']:
            call('POST', f'/suppliers/{sid}/compliance', {'document_type': doc, 'reference_number': f'{doc[:3].upper()}-{YEAR}-{i:03d}',
                                                          'issued_date': day(-60), 'expiry_date': day(300)}, SA)
        call('POST', f'/suppliers/{sid}/eligibility', {'eligible': True}, SA)
        SUP[name.split()[0]] = sid
    ratings = [(5, 4, 4, 5, 4), (4, 4, 3, 5, 4), (4, 3, 3, 4, 3), (5, 5, 5, 5, 4), (4, 4, 4, 4, 3),
               (5, 4, 5, 5, 5), (3, 4, 3, 4, 3), (4, 5, 4, 4, 4), (4, 3, 4, 3, 4), (5, 5, 4, 5, 4)]
    for sid, (q, d, t, c, a) in zip(list(SUP.values()), ratings):
        call('POST', f'/suppliers/{sid}/evaluations', {'evaluation_date': day(-15), 'quality_rating': q, 'delivery_rating': d, 'timeliness_rating': t,
                                                        'compliance_rating': c, 'after_sales_rating': a, 'reference': f'Mid-year rating {YEAR}',
                                                        'remarks': 'Semestral supplier performance evaluation'}, SA)

    # ── categories, items and opening stock ─────────────────────────────
    step('categories, items and opening stock')
    CAT = {}
    for c in ['Office Supplies', 'Janitorial Supplies', 'ICT Equipment', 'Furniture and Fixtures', 'Medical Supplies', 'Safety Equipment',
              'Electrical Supplies', 'Medical Equipment', 'Printing Supplies', 'Communication Equipment']:
        r = call('POST', '/inventory/categories', {'name': c}, SA)
        CAT[c] = r.get('id') or r.get('category', {}).get('id')
    item_defs = [
        ('bond', 'Bond paper, A4, 80gsm', 'Office Supplies', 'ream', 'consumable', 20, 200, [(120, 245, None, 'PS-DBM')]),
        ('pen', 'Ballpen, black, 0.5mm', 'Office Supplies', 'piece', 'consumable', 50, 500, [(300, 8, None, 'PS-DBM')]),
        ('toner', 'Toner cartridge, HP 85A', 'Office Supplies', 'piece', 'consumable', 4, 20, [(6, 3900, None, 'HP')]),
        ('alcohol', 'Isopropyl alcohol 70%, 500 ml', 'Medical Supplies', 'bottle', 'consumable', 30, 150, [(80, 95, day(400), 'Green Cross')]),
        ('disinfectant', 'Disinfectant solution, 1 gallon', 'Janitorial Supplies', 'gallon', 'consumable', 10, 60, [(25, 450, day(500), 'Lysol')]),
        ('firstaid', 'First aid kit, office type', 'Medical Supplies', 'set', 'consumable', 5, 30, [(12, 1500, day(700), 'MedFirst')]),
        ('envelope', 'Envelope, brown, long', 'Printing Supplies', 'box', 'consumable', 5, 40, [(15, 290, None, 'PS-DBM')]),
        ('chair', 'Office chair, ergonomic, with armrest', 'Furniture and Fixtures', 'unit', 'semi_expendable', 2, 20, [(8, 3500, None, 'Bayanihan')]),
        ('cabinet', 'Steel filing cabinet, 4-drawer', 'Furniture and Fixtures', 'unit', 'semi_expendable', 1, 10, [(4, 18000, None, 'Bayanihan')]),
        ('whiteboard', 'Whiteboard, 4 x 8 ft, with stand', 'Furniture and Fixtures', 'unit', 'semi_expendable', 1, 10, [(6, 4300, None, 'Printworks')]),
        ('extinguisher', 'Fire extinguisher, ABC dry chemical, 10 lbs', 'Safety Equipment', 'unit', 'semi_expendable', 4, 30, [(10, 2700, day(365), 'Safeguard')]),
        ('fan', 'Electric fan, stand type, 16"', 'Electrical Supplies', 'unit', 'semi_expendable', 2, 20, [(15, 2100, None, 'Voltmax')]),
        ('bpmonitor', 'Digital blood pressure monitor', 'Medical Equipment', 'unit', 'semi_expendable', 1, 10, [(6, 3000, None, 'Omron')]),
        ('radio', 'Two-way radio, handheld, UHF', 'Communication Equipment', 'unit', 'semi_expendable', 2, 20, [(8, 6500, None, 'Motorola')]),
        ('projector', 'Multimedia projector, 4000 lumens', 'ICT Equipment', 'unit', 'semi_expendable', 0, 0, [(3, 32000, None, 'Epson')]),
        ('laptop', 'Laptop computer, 14", Core i5, 16GB', 'ICT Equipment', 'unit', 'ppe', 0, 0, [(6, 62000, None, 'Lenovo')]),
        ('desktop', 'Desktop computer set, Core i5, 22" monitor', 'ICT Equipment', 'unit', 'ppe', 0, 0, [(4, 55000, None, 'Acer')]),
    ]
    IT = {}
    for key, name, cat, unit, cls, mn, mx, lots in item_defs:
        r = call('POST', '/inventory/items', {'name': name, 'category_id': CAT[cat], 'unit_of_measure': unit, 'classification': cls,
                                              'min_stock': mn, 'max_stock': mx or None}, SA)
        IT[key] = (r.get('item') or r)['id']
        for qty, cost, exp, brand in lots:
            body = {'received_date': day(-90), 'quantity': qty, 'unit_cost': cost, 'location': 'Main stockroom', 'brand_name': brand}
            if exp:
                body['expiration_date'] = exp
            call('POST', f"/inventory/items/{IT[key]}/stock", body, SA)
    # One lot already expired, for monitoring and write-offs.
    lot = call('POST', f"/inventory/items/{IT['alcohol']}/stock", {'received_date': day(-400), 'quantity': 12, 'unit_cost': 90,
                                                                   'expiration_date': day(30), 'location': 'Main stockroom', 'brand_name': 'Casino'}, SA)
    sql(f"update stocks set expiration_date = '{day(-5)}' where id = {lot['stock']['id']}")

    # ── BAC ─────────────────────────────────────────────────────────────
    step('BAC members, meetings and resolutions')
    for name, des, uid in [('Paolo Garcia', 'chairperson', U['paolo.garcia']), ('Grace Villanueva', 'vice_chairperson', None),
                           ('Carlo Bautista', 'member', None), ('Ramon Dela Cruz', 'member', None), ('Liza Mendoza', 'member', None),
                           ('Rosa Navarro', 'provisional_member', None), ('Marco Lim', 'twg', None), ('Victor Ramos', 'twg', None),
                           ('Joy Aquino', 'secretariat', U['joy.aquino']), ('Dina Castro', 'secretariat', None)]:
        call('POST', '/bac/members', {'full_name': name, 'designation': des, 'user_id': uid, 'position': des.replace('_', ' ').title(),
                                      'appointment_order_no': f'SO-{YEAR}-014'}, SA)
    roster = [('Paolo Garcia', 'chairperson'), ('Grace Villanueva', 'vice_chairperson'), ('Carlo Bautista', 'member'),
              ('Ramon Dela Cruz', 'member'), ('Joy Aquino', 'secretariat')]
    MEET = []
    for i, title in enumerate(['Pre-procurement conference: ICT equipment', 'Opening of quotations: office furniture',
                               'Post-qualification report: laptops', 'Award of SVP contracts', 'Pre-bid conference: generator set',
                               'Opening of bids: air-conditioning units', 'Post-qualification report: desktop computers',
                               'Mid-year procurement review', 'Pre-procurement conference: service vehicle', 'Opening of bids: CCTV system'], 1):
        m = call('POST', '/bac/meetings', {'title': title, 'meeting_date': f'{day(-50 + i * 5)}T09:00:00', 'venue': 'Conference Room A',
                                           'agenda': title}, SA)
        MEET.append(m['id'])
        if i <= 8:
            call('POST', f"/bac/meetings/{m['id']}/attendance", {'attendees': [{'name': n, 'committee_role': r, 'present': True} for n, r in roster]}, SA)
            call('PUT', f"/bac/meetings/{m['id']}", {'status': 'completed', 'minutes': f'Minutes of the {title.lower()}.'}, SA)

    def resolution(pr_id, title):
        r = call('POST', '/bac/resolutions', {'title': title, 'resolution_type': 'resolution_to_award', 'bac_meeting_id': MEET[3],
                                              'purchase_request_id': pr_id, 'content': f'RESOLVED to recommend the award of {title.lower()}.'}, SA)
        call('POST', f"/bac/resolutions/{r['id']}/submit", tok=SA)
        call('POST', f"/bac/resolutions/{r['id']}/approve", tok=SA)
        return r['id']

    # ── planning: PPMPs and the APP ─────────────────────────────────────
    step('PPMPs and the APP')
    GAA = 'General Appropriations Act (GAA)'
    PS, SVP, CB = 'Procurement from PS-DBM', 'Small Value Procurement', 'Competitive Bidding'
    # key, office, description, unit, quantity, unit cost, mode, quarter: one purchase request per key.
    PLAN = [
        # Small Value Procurement, each bought through an RFQ
        ('chairs', 'ADMIN', 'Office chair, ergonomic', 'unit', 10, 3800, SVP, 'Q2'),
        ('toner', 'ADMIN', 'Toner cartridge, HP 85A', 'piece', 12, 4000, SVP, 'Q2'),
        ('cabinets', 'FIN', 'Steel filing cabinet, 4-drawer', 'unit', 5, 19000, SVP, 'Q2'),
        ('disinfectant', 'HRMO', 'Disinfectant solution, 1 gallon', 'gallon', 50, 480, SVP, 'Q2'),
        ('firstaid', 'PDO', 'First aid kit, office type', 'set', 20, 1600, SVP, 'Q2'),
        ('extinguishers', 'LEGAL', 'Fire extinguisher, ABC dry chemical, 10 lbs', 'unit', 8, 2800, SVP, 'Q2'),
        ('projectors', 'ENG', 'Multimedia projector, 4000 lumens', 'unit', 3, 33000, SVP, 'Q3'),
        ('whiteboards', 'PIO', 'Whiteboard, 4 x 8 ft, with stand', 'unit', 6, 4500, SVP, 'Q2'),
        ('bpmonitors', 'MED', 'Digital blood pressure monitor', 'unit', 6, 3200, SVP, 'Q2'),
        ('fans', 'IT', 'Electric fan, stand type, 16"', 'unit', 10, 2200, SVP, 'Q2'),
        # Competitive Bidding
        ('laptops', 'IT', 'Laptop computer, 14", Core i7, 32GB', 'unit', 25, 95000, CB, 'Q2'),
        ('desktops', 'IT', 'Desktop workstation, Core i7, 27" monitor', 'unit', 30, 75000, CB, 'Q2'),
        ('switches', 'IT', 'Network switch, 48-port managed, PoE', 'unit', 20, 125000, CB, 'Q3'),
        ('aircon', 'ADMIN', 'Air-conditioning unit, 2HP inverter', 'unit', 50, 45000, CB, 'Q3'),
        ('printers', 'FIN', 'Multifunction printer, A3', 'unit', 55, 40000, CB, 'Q3'),
        ('solar', 'HRMO', 'Solar street light, 100W, with pole', 'unit', 60, 38000, CB, 'Q4'),
        ('genset', 'PDO', 'Generator set, 100 kVA, diesel', 'unit', 1, 2600000, CB, 'Q3'),
        ('cctv', 'LEGAL', 'CCTV system, 64 cameras, with NVR', 'lot', 1, 2400000, CB, 'Q3'),
        ('vehicle', 'ENG', 'Service vehicle, pick-up, 4x4', 'unit', 2, 1800000, CB, 'Q4'),
        ('dental', 'MED', 'Dental chair unit, complete', 'unit', 7, 350000, CB, 'Q3'),
        # Competitive Bidding on rated criteria: MEARB and MARB
        ('dms', 'IT', 'Integrated document management system', 'lot', 1, 4800000, CB, 'Q3'),
        ('analyzer', 'MED', 'Automated hematology analyzer', 'unit', 2, 1500000, CB, 'Q3'),
        ('gnss', 'ENG', 'Survey equipment, GNSS RTK set', 'set', 4, 600000, CB, 'Q3'),
        ('camera', 'PIO', 'Broadcast camera kit, 4K, with tripod and audio', 'lot', 3, 850000, CB, 'Q3'),
        # Common-use supplies from PS-DBM
        ('ps_bond', 'ADMIN', 'Bond paper, A4, 80gsm', 'ream', 100, 260, PS, 'Q1'),
        ('ps_pen', 'FIN', 'Ballpen, black', 'piece', 500, 10, PS, 'Q1'),
        ('ps_record', 'FIN', 'Record book, 300 pages', 'piece', 20, 75, PS, 'Q1'),
        ('ps_alcohol', 'HRMO', 'Isopropyl alcohol 70%, 500 ml', 'bottle', 100, 100, PS, 'Q1'),
        ('ps_bond2', 'PDO', 'Bond paper, A4, 80gsm (planning office)', 'ream', 50, 260, PS, 'Q1'),
        ('ps_folder', 'LEGAL', 'Folder, tagboard, legal size', 'pack', 30, 170, PS, 'Q1'),
        ('ps_staple', 'ENG', 'Staple wire, No. 35', 'box', 30, 35, PS, 'Q1'),
        ('ps_envelope', 'PIO', 'Envelope, brown, long', 'box', 20, 300, PS, 'Q1'),
        ('ps_tissue', 'MED', 'Tissue, toilet, 2-ply', 'pack', 40, 220, PS, 'Q1'),
        ('ps_clips', 'IT', 'Paper clips, jumbo', 'box', 50, 25, PS, 'Q1'),
    ]

    items = catalogue(SA)

    def line(desc, unit, qty, cost, mode, q):
        return {'general_description': desc, 'code': uacs(desc, cost), 'unit': unit, 'quantity': qty, 'estimated_unit_cost': cost, 'procurement_mode': mode,
                'procurement_category': 'Goods', 'schedule': q, 'fund_source': GAA, 'item_id': item_for(desc, items)}
    for code in ['ADMIN', 'FIN', 'HRMO', 'PDO', 'LEGAL', 'ENG', 'PIO', 'MED', 'IT']:
        p = call('POST', '/procurement-planning/ppmps', {'department_id': depts[code], 'fiscal_year': YEAR, 'title': f'PPMP FY {YEAR} — {code}',
                                                         'items': [line(*x[2:]) for x in PLAN if x[1] == code]}, SA)
        call('POST', f"/procurement-planning/ppmps/{p['id']}/submit", tok=SA)
        call('POST', f"/procurement-planning/ppmps/{p['id']}/approve", tok=SA)
    # The supply unit's plan is submitted but not yet approved, so it is not in the APP.
    p = call('POST', '/procurement-planning/ppmps', {'department_id': depts['SPU'], 'fiscal_year': YEAR, 'title': f'PPMP FY {YEAR} — SPU',
                                                     'items': [line('Hand truck, heavy duty', 'unit', 2, 6500, SVP, 'Q3'),
                                                               line('Storage rack, steel, 5-layer', 'unit', 6, 7800, SVP, 'Q3')]}, SA)
    call('POST', f"/procurement-planning/ppmps/{p['id']}/submit", tok=SA)
    app = call('POST', '/procurement-planning/apps/generate', {'fiscal_year': YEAR}, SA)
    app = app.get('app', app)
    call('POST', f"/procurement-planning/apps/{app['id']}/approve", {}, SA)
    app = call('GET', f"/procurement-planning/apps/{app['id']}", tok=SA)
    app = app.get('data', app)
    APPL = {(l['department']['id'] if l.get('department') else None, l['general_description']): l for l in app['items']}
    plan_next_year(SA, depts)

    # ── purchase requests ───────────────────────────────────────────────
    step('purchase requests')
    purposes = {'chairs': 'Replacement chairs for the Administrative Division', 'toner': 'Printer consumables, second quarter',
                'cabinets': 'Records storage for disbursement vouchers', 'disinfectant': 'Sanitation of work areas', 'firstaid': 'First aid kits for all floors',
                'extinguishers': 'Fire safety equipment for the legal office', 'projectors': 'Projectors for engineering briefings',
                'whiteboards': 'Whiteboards for the press and briefing rooms', 'bpmonitors': 'Equipment for the clinic',
                'fans': 'Electric fans for the server and staff rooms', 'laptops': 'Laptops for field staff', 'desktops': 'Replacement of old desktop units',
                'switches': 'Network upgrade, second floor', 'aircon': 'Air-conditioning of the records room', 'printers': 'Printing of financial reports',
                'solar': 'Lighting of the compound perimeter', 'genset': 'Standby power for the main building', 'cctv': 'Security cameras for the office',
                'vehicle': 'Vehicle for site inspections', 'dental': 'Dental chairs for the clinic',
                'dms': 'Digital records for all offices', 'analyzer': 'Laboratory equipment for the clinic',
                'gnss': 'Survey instruments for infrastructure projects', 'camera': 'Video coverage of agency events'}

    def pr(key):
        _, code, desc, unit, qty, cost, mode, _ = next(x for x in PLAN if x[0] == key)
        a = APPL[(depts[code], desc)]
        body = {'purpose': purposes.get(key, f'Common-use supplies: {desc.lower()}'), 'priority': 'medium', 'app_id': app['id'], 'department_id': depts[code],
                'items': [{'app_item_id': a['id'], 'general_description': desc, 'unit': a['unit'], 'quantity': qty,
                           'estimated_unit_cost': float(a['unit_cost']), 'procurement_mode': a['procurement_mode']}]}
        d = call('POST', '/purchase-requests', body, SA)
        call('POST', f"/purchase-requests/{d['id']}/submit", tok=SA)
        call('POST', f"/purchase-requests/{d['id']}/approve-step", {}, SA)
        call('POST', f"/purchase-requests/{d['id']}/certify-funds", {}, SA)
        for _ in range(3):
            d = call('POST', f"/purchase-requests/{d['id']}/approve-step", {}, SA)
        return d
    PR = {x[0]: pr(x[0]) for x in PLAN}
    # Still on their way: drafts and some at the department head.
    for purpose, desc, unit, qty, cost, submit in [('Storage racks for the supply room', 'Storage rack, steel, 5-layer', 'unit', 4, 7800, False),
                                                   ('Extension cords and surge protectors', 'Surge protector, 6-outlet', 'piece', 15, 850, True),
                                                   ('Hand truck for deliveries', 'Hand truck, heavy duty', 'unit', 2, 6500, False),
                                                   ('Label printer for property tags', 'Label printer, thermal', 'unit', 1, 9500, True)]:
        d = call('POST', '/purchase-requests', {'purpose': purpose, 'priority': 'low' if not submit else 'high', 'department_id': depts['SPU'],
                                                'items': [{'general_description': desc, 'unit': unit, 'quantity': qty, 'estimated_unit_cost': cost,
                                                           'procurement_mode': SVP}]}, T['maria.santos'])
        if submit:
            call('POST', f"/purchase-requests/{d['id']}/submit", tok=T['maria.santos'])

    # ── RFQs (alternative modes) ────────────────────────────────────────
    step('RFQs with quotations, awards, NOA and NTP')
    quotes = {'chairs': [('Bayanihan', 3500), ('Mega', 3650), ('CleanPro', 3700)], 'cabinets': [('Bayanihan', 17800), ('Mega', 18500), ('Northpoint', 18900)],
              'firstaid': [('MedFirst', 1450), ('CleanPro', 1550), ('Mega', 1580)], 'disinfectant': [('CleanPro', 440), ('MedFirst', 460), ('Mega', 470)],
              'extinguishers': [('Safeguard', 2650), ('CleanPro', 2700), ('Mega', 2750)], 'whiteboards': [('Bayanihan', 4200), ('Mega', 4350), ('Printworks', 4400)],
              'bpmonitors': [('MedFirst', 2950), ('Healthline', 3050), ('Mega', 3100)], 'fans': [('Voltmax', 1990), ('Mega', 2100), ('CleanPro', 2150)]}
    # awarded with NOA and NTP; quotations in, waiting for the deadline; issued; draft
    rfq_stage = {'chairs': 'award', 'cabinets': 'award', 'firstaid': 'award', 'extinguishers': 'award', 'whiteboards': 'award', 'bpmonitors': 'award',
                 'disinfectant': 'quoted', 'fans': 'quoted', 'projectors': 'issued', 'toner': 'draft'}
    RFQ = {}
    for i, (key, stage) in enumerate(rfq_stage.items(), 1):
        p = PR[key]
        r = call('POST', '/rfq', {'title': p['purpose'], 'procurement_method': SVP, 'purchase_request_id': p['id'], 'deadline': f'{day(20)}T10:00:00',
                                  'delivery_lead_days': 15, 'documents_stage': 'offer',
                                  'items': [{'purchase_request_item_id': x['id']} for x in p['items']]}, SA)
        RFQ[key] = r['id']
        if stage == 'draft':
            continue
        call('POST', f"/rfq/{r['id']}/issue", tok=SA)
        if stage == 'issued':
            continue
        for n, (sup, price) in enumerate(quotes[key], 1):
            call('POST', f"/rfq/{r['id']}/quotations", {'supplier_id': SUP[sup], 'quotation_number': f'Q-{YEAR}-{i:02d}{n}', 'submitted_at': day(-1),
                                                       'items': [{'rfq_item_id': r['items'][0]['id'], 'unit_price': price}]}, SA)
        if stage == 'quoted':
            continue
        past = (manila_now() - datetime.timedelta(hours=1)).strftime('%Y-%m-%d %H:%M:%S')
        sql(f"update rfqs set deadline = '{past}' where id = {r['id']}")
        d = call('POST', f"/rfq/{r['id']}/close", tok=SA)
        win = min(d['quotations'], key=lambda q: float(q['total_amount']))['id']
        call('POST', f"/rfq/{r['id']}/award", {'quotation_id': win, 'bac_resolution_id': resolution(p['id'], p['purpose'])}, SA)
        call('POST', f"/rfq/{r['id']}/noa", {}, SA)
        call('POST', f"/rfq/{r['id']}/ntp", {}, SA)

    # ── competitive bidding ─────────────────────────────────────────────
    step('competitive biddings at different stages')
    BID = {}

    def bidding(key, title, stage, bids):
        p = PR[key]
        b = call('POST', '/bidding', {'title': title, 'purchase_request_id': p['id'], 'items': [{'purchase_request_item_id': x['id']} for x in p['items']]}, SA)
        BID[key] = b['id']
        if stage == 'draft':
            return
        call('POST', f"/bidding/{b['id']}/invitation", {'itb_reference': f'ITB-{YEAR}-{len(BID):03d}', 'submission_deadline': f'{day(25)}T10:00:00'}, SA)
        if stage == 'invitation':
            return
        past = (manila_now() - datetime.timedelta(hours=1)).strftime('%Y-%m-%d %H:%M:%S')
        sql(f"update biddings set submission_deadline = '{past}', bid_opening_date = '{past}' where id = {b['id']}")
        call('POST', f"/bidding/{b['id']}/open-bids", tok=SA)
        # The first envelopes are rated before the second (financial) envelopes are opened and read (IRR 59.1, 59.2).
        ids = []
        for sup, amount in bids:
            d = call('POST', f"/bidding/{b['id']}/bidders", {'supplier_id': SUP[sup], 'bid_security_form': 'bid_securing_declaration',
                                                              'bid_security_posted': True}, SA)
            ids.append(([x for x in d['bidders'] if x['supplier']['id'] == SUP[sup]][0]['id'], amount))
        if stage == 'opening':
            return
        for bidder, _ in ids:
            for st in ['eligibility', 'technical']:
                call('PUT', f'/bidding/bidders/{bidder}', {'stage': st, 'result': 'passed'}, SA)
        call('POST', f"/bidding/{b['id']}/open-financial", tok=SA)
        for bidder, amount in ids:
            call('PUT', f'/bidding/bidders/{bidder}', {'bid_amount': amount}, SA)
            call('PUT', f'/bidding/bidders/{bidder}', {'stage': 'financial', 'result': 'passed'}, SA)
        if stage == 'evaluation':
            return
        d = call('GET', f"/bidding/{b['id']}", tok=SA)
        top = d['next_post_qual_bidder_id']
        d = call('PUT', f'/bidding/bidders/{top}', {'stage': 'post_qualification', 'result': 'passed'}, SA)
        call('POST', f"/bidding/{b['id']}/award", {'bidder_id': d['recommended_bidder_id'], 'bac_resolution_id': resolution(p['id'], title)}, SA)
        call('POST', f"/bidding/{b['id']}/noa", {}, SA)
        call('POST', f"/bidding/{b['id']}/ntp", {}, SA, expect=None)
    bidding('laptops', 'Supply and delivery of laptop computers', 'award', [('Northpoint', 2290000), ('Mega', 2335000)])
    bidding('desktops', 'Supply and delivery of desktop workstations', 'award', [('Northpoint', 2175000), ('Bayanihan', 2210000)])
    bidding('aircon', 'Supply and installation of air-conditioning units', 'award', [('Primeline', 2150000), ('CleanPro', 2182000)])
    bidding('genset', 'Supply and installation of a 100 kVA generator set', 'award', [('Primeline', 2520000), ('Voltmax', 2565000)])
    bidding('printers', 'Supply of A3 multifunction printers', 'evaluation', [('Printworks', 2145000), ('Northpoint', 2178000)])
    bidding('dental', 'Supply of dental chair units', 'evaluation', [('Healthline', 2380000), ('MedFirst', 2415000)])
    bidding('switches', 'Supply of managed network switches', 'opening', [('Northpoint', 2475000)])
    bidding('cctv', 'Supply and installation of a CCTV system', 'opening', [('Safeguard', 2350000), ('Voltmax', 2370000)])
    bidding('vehicle', 'Supply of 4x4 service vehicles', 'invitation', [])
    bidding('solar', 'Supply and installation of solar street lights', 'draft', [])

    # Rated criteria (IRR 58 b, 61): the first envelopes are rated and scored, then only the financial proposals that
    # qualify are opened: under MEARB those at or above the minimum quality score, under MARB the highest rating.
    def rated(key, title, method, stage, criteria, bids, pre_proc=False):
        p = PR[key]
        body = {'title': title, 'purchase_request_id': p['id'], 'items': [{'purchase_request_item_id': x['id']} for x in p['items']], 'evaluation_method': method}
        body.update({'price_weight': 40, 'technical_weight': 60, 'min_quality_score': 70} if method == 'mearb' else {'price_weight': 0, 'technical_weight': 100})
        body['criteria'] = [{'name': n, 'weight': w, 'is_environmental': env} for n, w, env in criteria]
        b = call('POST', '/bidding', body, SA)
        BID[key] = b['id']
        crit = [c['id'] for c in b['criteria']]
        if pre_proc:
            call('POST', f"/bidding/{b['id']}/pre-proc", {'pre_proc_date': f'{day(-30)}T09:00:00', 'pre_proc_notes': 'Specifications, ABC and rated criteria confirmed'}, SA)
        call('POST', f"/bidding/{b['id']}/invitation", {'itb_reference': f'ITB-{YEAR}-{len(BID):03d}', 'submission_deadline': f'{day(25)}T10:00:00'}, SA)
        past = (manila_now() - datetime.timedelta(hours=1)).strftime('%Y-%m-%d %H:%M:%S')
        sql(f"update biddings set submission_deadline = '{past}', bid_opening_date = '{past}' where id = {b['id']}")
        call('POST', f"/bidding/{b['id']}/open-bids", tok=SA)
        ids = []
        for sup, scores, amount in bids:
            d = call('POST', f"/bidding/{b['id']}/bidders", {'supplier_id': SUP[sup], 'bid_security_form': 'bid_securing_declaration', 'bid_security_posted': True}, SA)
            ids.append(([x for x in d['bidders'] if x['supplier']['id'] == SUP[sup]][0]['id'], scores, amount))
        if stage == 'opening':
            return
        for bidder, scores, _ in ids:
            for st in ['eligibility', 'technical']:
                call('PUT', f'/bidding/bidders/{bidder}', {'stage': st, 'result': 'passed'}, SA)
            call('PUT', f'/bidding/bidders/{bidder}/scores', {'scores': [{'bidding_criterion_id': c, 'score': sc} for c, sc in zip(crit, scores)]}, SA)
        if stage == 'scored':
            return
        d = call('POST', f"/bidding/{b['id']}/open-financial", tok=SA)
        opened = {x['id'] for x in d['bidders'] if not any(v == 'failed' for v in x['results'].values())}
        for bidder, _, amount in ids:
            if bidder in opened:
                call('PUT', f'/bidding/bidders/{bidder}', {'bid_amount': amount}, SA)
                call('PUT', f'/bidding/bidders/{bidder}', {'stage': 'financial', 'result': 'passed'}, SA)
        if stage == 'opened':
            return
        d = call('GET', f"/bidding/{b['id']}", tok=SA)
        d = call('PUT', f"/bidding/bidders/{d['next_post_qual_bidder_id']}", {'stage': 'post_qualification', 'result': 'passed'}, SA)
        call('POST', f"/bidding/{b['id']}/award", {'bidder_id': d['recommended_bidder_id'], 'bac_resolution_id': resolution(p['id'], title)}, SA)
        call('POST', f"/bidding/{b['id']}/noa", {}, SA)
    MEARB_CRITERIA = [('Technical merit', 50, False), ('Relevant experience', 30, False), ('Sustainability', 20, True)]
    MARB_CRITERIA = [('Quality of the equipment', 60, False), ('After-sales support', 40, False)]
    # Northpoint is best overall once price counts; Mega's price is lowest but it misses the minimum quality score.
    rated('dms', 'Integrated document management system', 'mearb', 'award', MEARB_CRITERIA,
          [('Northpoint', (90, 85, 80), 4650000), ('Printworks', (82, 78, 75), 4420000), ('Mega', (65, 60, 55), 4100000)], pre_proc=True)
    rated('analyzer', 'Supply of automated hematology analyzers', 'mearb', 'scored', MEARB_CRITERIA,
          [('Healthline', (88, 80, 75), 2840000), ('MedFirst', (84, 86, 70), 2920000), ('Mega', (60, 70, 50), 2600000)])
    rated('gnss', 'Supply of GNSS RTK survey equipment', 'marb', 'opened', MARB_CRITERIA,
          [('Primeline', (92, 88), 2300000), ('Voltmax', (80, 75), 2100000)])
    rated('camera', 'Supply of 4K broadcast camera kits', 'marb', 'opening', MARB_CRITERIA,
          [('Printworks', (85, 80), 2480000), ('Northpoint', (78, 82), 2420000)])

    # ── unsolicited offers ──────────────────────────────────────────────
    step('unsolicited offers at different stages')
    offers = [('Safeguard', 'AI-assisted fire detection for the records room', 1250000, 'received'),
              ('Northpoint', 'Paperless records management platform', 2400000, 'received'),
              ('Voltmax', 'Smart metering of office power use', 680000, 'received'),
              ('Primeline', 'Modular rainwater harvesting for the compound', 950000, 'not_needed'),
              ('Healthline', 'Telemedicine kiosk for the clinic', 1800000, 'awaiting_documents'),
              ('Printworks', 'On-demand printing of forms with tracking', 520000, 'awaiting_documents'),
              ('Northpoint', 'Self-service property tagging with RFID', 1600000, 'evaluation'),
              ('Mega', 'Vendor-managed inventory of common supplies', 750000, 'evaluation'),
              ('Voltmax', 'Solar-powered charging stations', 1100000, 'negotiation'),
              ('Safeguard', 'Integrated building safety monitoring', 2900000, 'original_offeror')]
    for sup, title, amount, stage in offers:
        o = call('POST', '/unsolicited-offers', {'supplier_id': SUP[sup], 'title': title, 'concept': f'{title}: a new solution not yet offered to the agency.',
                                                 'category': 'Goods', 'proposed_amount': amount, 'received_date': day(-20)}, SA)
        oid = o['id']
        if stage == 'received':
            continue
        if stage == 'not_needed':
            call('POST', f'/unsolicited-offers/{oid}/assess', {'needed': False, 'notice_date': day(-15), 'reason': 'Not in the agency\'s plans for the year.'}, SA)
            continue
        call('POST', f'/unsolicited-offers/{oid}/assess', {'needed': True, 'notice_date': day(-18)}, SA)
        if stage == 'awaiting_documents':
            continue
        call('POST', f'/unsolicited-offers/{oid}/documents', {'concept_details': True, 'proof_of_concept': True, 'eligibility_documents': True,
                                                              'terms_and_consent': True, 'date': day(-15)}, SA)
        if stage == 'evaluation':
            continue
        call('POST', f'/unsolicited-offers/{oid}/evaluate', {'favorable': True, 'new_concept_basis': 'significant_benefit', 'funds_available': True,
                                                             'eligibility_verified': True, 'date': day(-10), 'negotiation_start': day(-9)}, SA)
        if stage == 'negotiation':
            continue
        call('POST', f'/unsolicited-offers/{oid}/negotiation', {'successful': True, 'date': day(-5), 'negotiated_amount': amount * 0.95,
                                                                'terms': 'Price and delivery as negotiated', 'certification_number': f'OOS-{YEAR}-001'}, SA)

    # ── purchase orders, receiving, contracts ───────────────────────────
    step('purchase orders, inspection and acceptance, contracts')
    POS = {}
    po_stage = [('chairs', 'rfq', 'confirmed'), ('cabinets', 'rfq', 'confirmed'), ('firstaid', 'rfq', 'confirmed'), ('extinguishers', 'rfq', 'confirmed'),
                ('whiteboards', 'rfq', 'sent'), ('bpmonitors', 'rfq', 'draft'), ('laptops', 'bidding', 'confirmed'), ('desktops', 'bidding', 'confirmed'),
                ('aircon', 'bidding', 'confirmed'), ('genset', 'bidding', 'confirmed')]
    for key, kind, stage in po_stage:
        po = call('POST', '/procurement/purchase-orders', {'source_type': kind, 'source_id': RFQ[key] if kind == 'rfq' else BID[key], 'delivery_date': day(10),
                                                           'delivery_address': 'Supply and Property Unit, Main Building'}, T['maria.santos'])
        POS[key] = po['id']
        if stage == 'draft':
            continue
        call('POST', f"/procurement/purchase-orders/{po['id']}/send", tok=SA)
        if stage == 'confirmed':
            call('POST', f"/procurement/purchase-orders/{po['id']}/confirm", tok=SA)
    # Deliveries: accepted into stock, a partial delivery, and some awaiting acceptance.
    for key, target, brand, months, accept, short in [('chairs', 'chair', 'Bayanihan', 12, True, 0), ('cabinets', 'cabinet', 'Bayanihan', 12, False, 1),
                                                      ('firstaid', None, None, 0, False, 0), ('extinguishers', 'extinguisher', 'Safeguard', 12, True, 0),
                                                      ('laptops', 'laptop', 'Lenovo', 36, True, 0), ('desktops', 'desktop', 'Acer', 36, True, 0),
                                                      ('genset', None, None, 0, False, 0)]:
        po = call('GET', f'/procurement/purchase-orders/{POS[key]}', tok=SA)
        ln = po['items'][0]
        qty = ln['quantity'] - short
        iar = call('POST', '/receiving/inspection-reports', {'purchase_order_id': POS[key], 'delivery_date': day(0), 'inspection_date': day(0),
                                                             'items': [{'purchase_order_item_id': ln['id'], 'quantity_received': qty, 'quantity_accepted': qty}]},
                   T['ana.cruz'])
        if accept:
            call('POST', f"/receiving/inspection-reports/{iar['id']}/accept",
                 {'lines': [{'iar_item_id': iar['items'][0]['id'], 'item_id': IT[target], 'location': 'Main stockroom',
                             'brand_name': brand, 'warranty_months': months}]}, T['jose.reyes'])
    CON = {}
    for key, kind in [('chairs', 'rfq'), ('cabinets', 'rfq'), ('firstaid', 'rfq'), ('extinguishers', 'rfq'), ('whiteboards', 'rfq'), ('bpmonitors', 'rfq'),
                      ('laptops', 'bidding'), ('desktops', 'bidding'), ('aircon', 'bidding'), ('genset', 'bidding')]:
        signed = key in ('laptops', 'desktops', 'chairs', 'extinguishers', 'genset')
        c = call('POST', '/contracts', {'source_type': kind, 'source_id': RFQ[key] if kind == 'rfq' else BID[key], 'contract_type': 'Goods',
                                        'start_date': day(0), 'end_date': day(120), 'signed_date': day(0) if signed else None}, SA)
        CON[key] = c['id']
    for key, ref in [('laptops', 'SB-2026-0451'), ('desktops', 'SB-2026-0452'), ('genset', 'SB-2026-0453')]:
        c = call('GET', f"/contracts/{CON[key]}", tok=SA)
        call('POST', f"/contracts/{CON[key]}/securities", {'security_type': 'Performance Security', 'form': 'Surety Bond', 'reference_number': ref,
                                                          'amount': round(float(c['amount']) * 0.30, 2), 'posted_date': day(0), 'validity_date': day(180)}, SA)
        call('POST', f"/contracts/{CON[key]}/activate", tok=SA, expect=None)
    optional('POST', f"/contracts/{CON['genset']}/amendments", {'title': 'Additional automatic transfer switch', 'description': 'Adds an ATS to the scope',
                                                                 'amount_change': 45000, 'remarks': 'Requested by the end user'}, SA)
    optional('POST', f"/contracts/{CON['desktops']}/extensions", {'requested_days': 15, 'reason': 'Delayed shipment of monitors', 'requested_date': day(0)}, SA)

    # ── payments ────────────────────────────────────────────────────────
    step('payment schedules')
    for key, desc, pct in [('laptops', 'Progress billing: delivery and acceptance', 100), ('chairs', 'Full payment upon acceptance', 100),
                           ('cabinets', 'Partial delivery', 80), ('firstaid', 'Full payment upon acceptance', 100), ('desktops', 'Advance payment', 15),
                           ('extinguishers', 'Full payment upon acceptance', 100), ('whiteboards', 'Full payment upon delivery', 100),
                           ('bpmonitors', 'Full payment upon acceptance', 100), ('aircon', 'Advance payment', 15), ('genset', 'Progress billing: installation', 50)]:
        call('POST', '/payments/tranches', {'type': 'contract', 'id': CON[key], 'description': desc, 'percentage': pct, 'due_date': day(30)}, SA, expect=None)

    # ── PS-DBM ──────────────────────────────────────────────────────────
    step('PS-DBM catalogue and Agency Procurement Requests')
    CATL = {}
    for code, desc, unit, price in [('PS-OS-0001', 'Paper, multicopy, A4, 80gsm', 'ream', 245.50), ('PS-OS-0002', 'Ballpen, black, retractable', 'piece', 8.25),
                                    ('PS-MS-0003', 'Alcohol, ethyl, 70% solution, 500 ml', 'bottle', 92.00), ('PS-OS-0004', 'Paper clip, vinyl-coated, jumbo', 'box', 22.80),
                                    ('PS-OS-0005', 'Folder, tagboard, legal size', 'pack', 165.00), ('PS-OS-0006', 'Record book, 300 pages', 'piece', 72.00),
                                    ('PS-OS-0007', 'Staple wire, No. 35', 'box', 32.50), ('PS-OS-0008', 'Envelope, documentary, brown, long', 'box', 285.00),
                                    ('PS-JS-0009', 'Tissue paper, toilet, 2-ply', 'pack', 210.00), ('PS-OS-0010', 'Paper, multicopy, A4, 70gsm', 'ream', 230.00)]:
        CATL[code] = call('POST', '/ps-dbm/catalogue', {'code': code, 'description': desc, 'unit': unit, 'unit_price': price, 'price_as_of': day(-30)}, SA)['id']
    for key, code, stage, stock_item in [('ps_bond', 'PS-OS-0001', 'delivered', 'bond'), ('ps_pen', 'PS-OS-0002', 'delivered', 'pen'),
                                         ('ps_alcohol', 'PS-MS-0003', 'delivered', 'alcohol'), ('ps_clips', 'PS-OS-0004', 'paid', None),
                                         ('ps_envelope', 'PS-OS-0008', 'paid', None), ('ps_tissue', 'PS-JS-0009', 'submitted', None),
                                         ('ps_record', 'PS-OS-0006', 'submitted', None), ('ps_folder', 'PS-OS-0005', 'not_available', None),
                                         ('ps_bond2', 'PS-OS-0010', 'draft', None), ('ps_staple', 'PS-OS-0007', 'draft', None)]:
        p = PR[key]
        o = call('POST', '/ps-dbm/orders', {'purchase_request_id': p['id'], 'items': [{'purchase_request_item_id': x['id'], 'quantity': x['quantity'],
                                                                                         'ps_catalogue_item_id': CATL[code]} for x in p['items']]}, T['maria.santos'])
        if stage == 'draft':
            continue
        o = call('POST', f"/ps-dbm/orders/{o['id']}/submit", {'vs_reference': f'VS-{YEAR}-00{o["id"]}4512', 'submitted_date': day(-7)}, T['maria.santos'])
        if stage == 'not_available':
            # PS had no stock, so the line was certified not available.
            call('POST', f"/ps-dbm/orders/{o['id']}/items/{o['items'][0]['id']}/not-available", {'cnas_number': f'CNAS-{YEAR}-0217', 'cnas_date': day(-1)},
                 T['maria.santos'])
            continue
        if stage == 'submitted':
            continue
        o = call('POST', f"/ps-dbm/orders/{o['id']}/pay", {'payment_mode': 'lddap_ada', 'payment_reference': f'LDDAP-ADA {YEAR}-10-{o["id"]:03d}',
                                                           'payment_date': day(-5), 'amount_paid': float(o['total_amount'])}, T['maria.santos'])
        if stage == 'paid':
            continue
        o = call('POST', f"/ps-dbm/orders/{o['id']}/deliver", {'dr_number': f'PS-DR-{YEAR}-77{o["id"]}31', 'dr_date': day(-2), 'delivery_mode': 'pickup'}, T['maria.santos'])
        # Inspected and taken into stock.
        po = call('GET', f"/procurement/purchase-orders/{o['purchase_order']['id']}", tok=SA)
        ln = po['items'][0]
        iar = call('POST', '/receiving/inspection-reports', {'purchase_order_id': po['id'], 'delivery_date': day(-1), 'inspection_date': day(-1),
                                                             'items': [{'purchase_order_item_id': ln['id'], 'quantity_received': ln['quantity'],
                                                                        'quantity_accepted': ln['quantity']}]}, T['ana.cruz'])
        call('POST', f"/receiving/inspection-reports/{iar['id']}/accept", {'lines': [{'iar_item_id': iar['items'][0]['id'], 'item_id': IT[stock_item],
                                                                                      'location': 'Main stockroom', 'brand_name': 'PS-DBM'}]}, T['jose.reyes'])

    # ── supply requests and issuance ────────────────────────────────────
    step('supply requests and issuance')
    reqs = [
        ('liza.mendoza', 'FIN', 'Office supplies for Finance', [('bond', 10), ('pen', 50)], 'issue'),
        ('ben.torres', 'HRMO', 'Sanitation supplies', [('alcohol', 20), ('disinfectant', 5)], 'issue'),
        ('liza.mendoza', 'FIN', 'Chairs for new staff', [('chair', 2)], 'issue'),
        ('ben.torres', 'HRMO', 'Filing cabinet for 201 files', [('cabinet', 1)], 'pending'),
        ('liza.mendoza', 'FIN', 'Toner for the Finance printer', [('toner', 2)], 'reject'),
        ('ben.torres', 'HRMO', 'Chair for the HR records clerk', [('chair', 1)], 'issue'),
        ('liza.mendoza', 'FIN', 'Chairs for the cashier window', [('chair', 2)], 'issue'),
        ('ben.torres', 'HRMO', 'Filing cabinet for personnel records', [('cabinet', 1)], 'issue'),
        ('liza.mendoza', 'FIN', 'Filing cabinet for vouchers', [('cabinet', 1)], 'issue'),
        ('ben.torres', 'HRMO', 'Electric fans for the training room', [('fan', 3)], 'issue'),
        ('liza.mendoza', 'FIN', 'Envelopes for remittances', [('envelope', 2)], 'pending'),
        ('ben.torres', 'HRMO', 'Fire extinguisher for the HR office', [('extinguisher', 1)], 'issue'),
    ]
    for who, code, purpose, items, outcome in reqs:
        r = call('POST', '/requests', {'department_id': depts[code], 'purpose': purpose, 'priority': 'medium',
                                       'items': [{'item_id': IT[k], 'quantity': q} for k, q in items]}, T[who])
        rid = r.get('id') or r.get('request', {}).get('id') or r.get('data', {}).get('id')
        if outcome == 'issue':
            call('PUT', f'/requests/{rid}/approve', tok=SA)
        elif outcome == 'reject':
            call('PUT', f'/requests/{rid}/reject', {'reason': 'Toner already issued this quarter'}, SA)

    # ── stock adjustments and transfers ─────────────────────────────────
    step('stock adjustments and transfers')
    for k, kind, qty, reason, approve in [('alcohol', 'decrease', 12, 'Expired lot, for condemnation', True),
                                          ('pen', 'decrease', 5, 'Damaged in storage', True),
                                          ('bond', 'increase', 3, 'Count variance found in physical count', True),
                                          ('disinfectant', 'decrease', 1, 'Container leaked', False),
                                          ('firstaid', 'increase', 2, 'Donation from the provincial health office', False),
                                          ('envelope', 'decrease', 1, 'Water damage in the stockroom', True),
                                          ('toner', 'decrease', 1, 'Defective cartridge, not covered by warranty', True),
                                          ('fan', 'increase', 1, 'Unit found unrecorded in the annex', False),
                                          ('whiteboard', 'decrease', 1, 'Cracked during handling', False),
                                          ('radio', 'increase', 1, 'Returned unit found in storage', True)]:
        a = call('POST', '/stock-adjustments', {'item_id': IT[k], 'adjustment_type': kind, 'quantity': qty, 'reason': reason,
                                                'unit_cost': {'firstaid': 1500, 'fan': 2100, 'radio': 6500, 'bond': 245}.get(k) if kind == 'increase' else None},
                 T['ana.cruz'])
        if approve:
            call('POST', f"/stock-adjustments/{a['id']}/approve", {}, SA, expect=None)
    for k, qty, to in [('bond', 20, 'Annex stockroom'), ('pen', 50, 'Annex stockroom'), ('alcohol', 10, 'Clinic stockroom'), ('disinfectant', 5, 'Annex stockroom'),
                       ('firstaid', 3, 'Clinic stockroom'), ('envelope', 3, 'Annex stockroom'), ('fan', 2, 'Annex stockroom'), ('radio', 2, 'Security office'),
                       ('extinguisher', 2, 'Annex stockroom'), ('whiteboard', 1, 'Annex stockroom')]:
        lot = max(lots_of(IT[k], SA), key=lambda l: l['quantity'])
        call('POST', '/inventory/transfer-stock', {'stock_id': lot['id'], 'to_location': to, 'quantity': qty, 'transfer_date': day(-3),
                                                   'remarks': 'Moved closer to the users'}, SA)

    # ── assets, transfers, gate passes ──────────────────────────────────
    step('assets, transfers and gate passes')
    lot_id = max(lots_of(IT['laptop'], SA), key=lambda l: l['quantity'])['id']
    holders = ['maria.santos', 'jose.reyes', 'liza.mendoza', 'carlo.bautista', 'ramon.delacruz', 'grace.villanueva', 'paolo.garcia', 'joy.aquino',
               'elena.pascual', 'nora.flores']
    A = []
    for n, h in enumerate(holders, 1):
        reg = call('POST', '/properties', {'item_id': IT['laptop'], 'stock_id': lot_id, 'assigned_to': U[h], 'assigned_date': day(-20),
                                           'units': [{'tag_number': f'PROP-{YEAR}-LT{n:03d}', 'serial_number': f'PF3K{n:04d}X'}]}, SA)
        A.append(reg['properties'][0]['id'])
    dl = min(lots_of(IT['desktop'], SA), key=lambda l: l['id'])
    reg = call('POST', '/properties', {'item_id': IT['desktop'], 'stock_id': dl['id'], 'assigned_to': U['ramon.delacruz'], 'assigned_date': day(-300),
                                       'units': [{'tag_number': f'PROP-{YEAR}-DT{n:03d}', 'serial_number': f'DT9{n:03d}'} for n in range(1, 5)]}, SA)
    D = [p['id'] for p in reg['properties']]
    for aid, to, why in [(A[8], 'grace.villanueva', 'Reassigned within the Finance Division'), (A[9], 'ramon.delacruz', 'Custodian moved to the property office'),
                         (A[6], 'carlo.bautista', 'Reassigned to the IT help desk')]:
        optional('POST', f'/properties/{aid}/transfer', {'to_user': U[to], 'reason': why, 'date': day(-7), 'condition': 'good'}, SA)
    gate = [('Maria Santos', 'Supplier site inspection', 2), ('Jose Reyes', 'Training at the regional office', 5), ('Liza Mendoza', 'Field audit', 1),
            ('Carlo Bautista', 'Repair at the service center', 10), ('Ramon Dela Cruz', 'Inventory of field office property', 3),
            ('Grace Villanueva', 'Budget hearing at the regional office', 2), ('Paolo Garcia', 'Pre-bid conference off-site', 1),
            ('Joy Aquino', 'BAC documentation at the provincial office', 4), ('Elena Pascual', 'Audit exit conference', 2),
            ('Nora Flores', 'Cash examination at the field office', 3)]
    for n, (aid, (by, purpose, back)) in enumerate(zip(A, gate)):
        g = call('POST', '/gate-pass', {'property_id': aid, 'received_by': by, 'purpose': purpose, 'date_released': day(-3), 'expected_return_date': day(back)}, SA)
        if n % 2 == 0:
            call('POST', f"/gate-pass/{g['id']}/return", {'condition_on_return': 'good', 'actual_return_date': day(-1), 'returned_by': by}, SA)

    # ── locations and maintenance ───────────────────────────────────────
    step('asset locations and maintenance')
    known = {l['name']: l['id'] for l in (lambda r: r.get('data', r) if isinstance(r, dict) else r)(call('GET', '/locations', tok=SA))}
    LOC = []
    for name, bldg, floor, room in [('Supply and Property Office', 'Main Building', '1F', '104'), ('ICT Server Room', 'Main Building', '2F', '210'),
                                    ('Finance Division', 'Main Building', '2F', '201'), ('Records Storage', 'Annex', 'Ground', 'R-1'),
                                    ('Conference Room A', 'Main Building', '3F', '301'), ('Legal Services Office', 'Main Building', '3F', '305'),
                                    ('Engineering Office', 'Annex', '2F', 'A-201'), ('Public Information Office', 'Main Building', '1F', '110'),
                                    ('Medical and Dental Clinic', 'Annex', 'Ground', 'A-102'), ('BAC Secretariat Office', 'Main Building', '3F', '310')]:
        LOC.append(known.get(name) or call('POST', '/locations', {'name': name, 'building': bldg, 'floor': floor, 'room': room}, SA)['id'])
    for aid, loc in zip(A + D, [LOC[0], LOC[0], LOC[2], LOC[1], LOC[0], LOC[2], LOC[4], LOC[9], LOC[2], LOC[0], LOC[3], LOC[3], LOC[3], LOC[1]]):
        call('POST', f'/properties/{aid}/location', {'location_id': loc, 'remarks': 'Initial location'}, SA)
    for aid, kind, work, due, every, done in [
            (A[0], 'preventive', 'OS and antivirus update', -40, 3, ('ICT Unit', 0, None)),
            (A[1], 'preventive', 'Clean the keyboard and vents', 12, 6, None),
            (A[2], 'preventive', 'Battery health check', -10, 6, None),
            (A[3], 'corrective', 'Replace the laptop battery', -2, None, ('Laptop service center', 3850, 'Battery replaced')),
            (A[4], 'inspection', 'Annual inspection of property', 25, 12, None),
            (A[5], 'corrective', 'Replace the cracked screen hinge', -6, None, ('Laptop service center', 2200, 'Hinge replaced')),
            (A[6], 'preventive', 'OS and antivirus update', 30, 3, None),
            (A[7], 'preventive', 'Disk clean-up and backup', -3, 3, None),
            (D[3], 'preventive', 'Clean and dust the desktop, check fans', 7, 6, None),
            (A[9], 'inspection', 'Check before reassignment', -1, None, ('ICT Unit', 0, 'In good condition'))]:
        m = call('POST', '/maintenance', {'property_id': aid, 'maintenance_type': kind, 'description': work, 'scheduled_date': day(due), 'interval_months': every}, SA)
        if done:
            by, cost, remarks = done
            call('POST', f"/maintenance/{m['id']}/complete", {'completed_date': day(min(due + 2, 0)), 'performed_by': by, 'cost': cost, 'remarks': remarks}, SA)

    # ── physical counts ─────────────────────────────────────────────────
    step('physical counts')
    P = '/physical-inventory/counts'
    for ctype, purpose, stage in [('supplies', 'Year-end count of supplies', 'record'), ('semi_expendable', 'Semi-expendable property count', 'draft'),
                                  ('ppe', 'Annual PPE count (RPCPPE)', 'start'), ('supplies', 'Surprise count, medical supplies', 'draft'),
                                  ('supplies', 'Mid-year count of supplies', 'draft'), ('supplies', 'Count of janitorial supplies', 'draft'),
                                  ('semi_expendable', 'Count of furniture and fixtures', 'draft'), ('ppe', 'Count of ICT equipment', 'draft'),
                                  ('supplies', 'Turnover count, new supply officer', 'draft'), ('semi_expendable', 'Count of safety equipment', 'draft')]:
        category = {'medical': 'Medical Supplies', 'janitorial': 'Janitorial Supplies', 'furniture': 'Furniture and Fixtures', 'ICT': 'ICT Equipment',
                    'safety': 'Safety Equipment'}
        cat = next((CAT[v] for k, v in category.items() if k in purpose), None) if ctype == 'supplies' else None
        c = call('POST', P, {'count_type': ctype, 'count_date': day(0), 'purpose': purpose, 'category_id': cat}, SA)
        cid = c.get('count', c)['id']
        if stage == 'draft':
            continue
        d = call('POST', f'{P}/{cid}/start', tok=SA)
        if stage == 'record':
            lines = d.get('lines') or call('GET', f'{P}/{cid}', tok=SA).get('lines', [])
            call('POST', f'{P}/{cid}/record', {'lines': [{'id': l['id'], 'counted_quantity': max(0, l.get('system_quantity', l.get('book_quantity', 0)) - (1 if i == 0 else 0)),
                                                          'location': 'Main stockroom'} for i, l in enumerate(lines)]}, SA, expect=None)

    # ── disposal ────────────────────────────────────────────────────────
    step('disposals')
    for aid in D[:3]:
        call('POST', f'/properties/{aid}/return', {'reason': 'Unserviceable, beyond economical repair', 'condition': 'poor'}, SA)
    disposals = [
        ({'disposal_category': 'property', 'property_id': D[0], 'disposal_type': 'condemnation', 'reason': 'Unserviceable desktop, motherboard failure'}, 'complete'),
        ({'disposal_category': 'property', 'property_id': D[1], 'disposal_type': 'public_auction', 'reason': 'Obsolete desktop set'}, 'approve'),
        ({'disposal_category': 'property', 'property_id': D[2], 'disposal_type': 'donation', 'reason': 'Donation to a public school'}, 'approve'),
        ({'disposal_category': 'inventory', 'item_id': IT['disinfectant'], 'quantity': 2, 'disposal_type': 'destruction', 'reason': 'Containers damaged'}, 'submit'),
        ({'disposal_category': 'inventory', 'item_id': IT['pen'], 'quantity': 10, 'disposal_type': 'destruction', 'reason': 'Dried out ink'}, 'draft'),
        ({'disposal_category': 'inventory', 'item_id': IT['toner'], 'quantity': 1, 'disposal_type': 'destruction', 'reason': 'Leaking toner cartridge'}, 'complete'),
        ({'disposal_category': 'inventory', 'item_id': IT['bond'], 'quantity': 2, 'disposal_type': 'destruction', 'reason': 'Water-damaged reams'}, 'approve'),
        ({'disposal_category': 'inventory', 'item_id': IT['firstaid'], 'quantity': 1, 'disposal_type': 'destruction', 'reason': 'Contents expired'}, 'submit'),
        ({'disposal_category': 'inventory', 'item_id': IT['envelope'], 'quantity': 1, 'disposal_type': 'sale', 'reason': 'Surplus of an old design'}, 'submit'),
        ({'disposal_category': 'inventory', 'item_id': IT['whiteboard'], 'quantity': 1, 'disposal_type': 'donation', 'reason': 'Donation to a day care center'}, 'draft'),
    ]
    for body, stage in disposals:
        d = call('POST', '/disposal/requests', {**body, 'remarks': 'Inspected by the Inspection Committee'}, T['ramon.delacruz'])
        if stage == 'draft':
            continue
        call('POST', f"/disposal/requests/{d['id']}/submit", tok=T['ramon.delacruz'])
        if stage == 'submit':
            continue
        call('POST', f"/disposal/requests/{d['id']}/approve", {'remarks': 'Approved per IIRUP'}, SA)
        if stage == 'complete':
            call('POST', f"/disposal/requests/{d['id']}/complete", {'disposal_date': day(0)}, SA)

    # ── petty cash ──────────────────────────────────────────────────────
    step('petty cash fund, vouchers and a replenishment')
    f = call('POST', '/petty-cash/funds', {'fund_name': f'Petty Cash Fund {YEAR} — Administrative Division', 'custodian_id': U['nora.flores'],
                                           'amount': 50000, 'fund_cluster': '01'}, SA)
    fid = f.get('id') or f.get('fund', {}).get('id')
    for n, (payee, particulars, amount) in enumerate([('Pandayan Bookshop', 'Folders and fasteners', 850), ('Grab Philippines', 'Transport, delivery of documents', 320),
                                                      ('Ace Hardware', 'Electrical repair parts', 2450), ('LBC Express', 'Courier of reports to the regional office', 480),
                                                      ('Mercury Drug', 'Medicines for the first aid kit', 1275), ('National Book Store', 'Logbooks and index cards', 640),
                                                      ('Wilcon Depot', 'Plumbing fittings for the comfort room', 1980), ('Meralco', 'Reconnection fee, annex meter', 1500),
                                                      ('Shell Station', 'Fuel for the service vehicle, emergency trip', 2000), ('Jollibee', 'Meals for the BAC meeting', 2350)]):
        v = call('POST', f'/petty-cash/funds/{fid}/vouchers', {'payee': payee, 'particulars': particulars, 'amount': amount, 'voucher_date': day(-n)},
                 T['nora.flores'])
        if n < 7:
            vid = [x for x in v['vouchers'] if x['payee'] == payee][0]['id'] if 'vouchers' in v else v['id']
            call('POST', f'/petty-cash/vouchers/{vid}/approve', tok=SA)
    optional('POST', f'/petty-cash/funds/{fid}/replenishments', {'report_date': day(0), 'reference': f'RPCF-{YEAR}-001', 'remarks': 'First replenishment'},
             T['nora.flores'])

    # ── documents ───────────────────────────────────────────────────────
    step('documents')
    for title, dtype, owner, oid in [('Purchase request — office chairs', 'PR', 'PurchaseRequest', PR['chairs']['id']),
                                     ('Purchase request — laptops', 'PR', 'PurchaseRequest', PR['laptops']['id']),
                                     ('Notice of Award — laptops', 'NOA', None, None),
                                     ('Notice to Proceed — generator set', 'NTP', None, None),
                                     ('Purchase order — steel cabinets', 'PO', 'PurchaseOrder', POS['cabinets']),
                                     ('Purchase order — fire extinguishers', 'PO', 'PurchaseOrder', POS['extinguishers']),
                                     ('Contract — laptop computers', 'CONTRACT', 'Contract', CON['laptops']),
                                     ('Contract — generator set', 'CONTRACT', 'Contract', CON['genset']),
                                     ('BAC resolution — SVP awards', 'BAC_RESOLUTION', None, None),
                                     ('Minutes — mid-year procurement review', 'OTHER', None, None)]:
        fields = {'title': title, 'type': dtype, 'description': title}
        if owner:
            fields.update({'documentable_type': owner, 'documentable_id': oid})
        upload('/documents', fields, title.replace(' — ', '_').replace(' ', '_') + '.pdf', pdf(title), SA)

    # ── clearance and digital signing ───────────────────────────────────
    step('clearances and signing requests')
    for who, purpose, details, eff, issue in [('liza.mendoza', 'transfer', 'Transfer to the regional office', 30, False),
                                              ('carlo.bautista', 'end_of_contract', None, 14, False),
                                              ('ben.torres', 'resignation', None, 7, False),
                                              ('rosa.navarro', 'retirement', 'Compulsory retirement', 45, True),
                                              ('marco.lim', 'transfer', 'Detail to the central office', 20, True),
                                              ('dina.castro', 'resignation', None, 10, True),
                                              ('victor.ramos', 'leave', 'Study leave of one year', 60, False),
                                              ('grace.villanueva', 'retirement', 'Optional retirement', 90, False),
                                              ('joy.aquino', 'transfer', 'Transfer to the provincial office', 30, False),
                                              ('paolo.garcia', 'end_of_contract', None, 21, False)]:
        c = optional('POST', '/clearances', {'user_id': U[who], 'purpose': purpose, 'purpose_details': details, 'effective_date': day(eff)}, SA)
        if c and issue:
            optional('POST', f"/clearances/{c['id']}/issue", tok=SA)
    for title, signer in [('Property Card — laptop', 'ramon.delacruz'), ('RSMI — last month', 'liza.mendoza'), ('Inspection and Acceptance Report', 'ana.cruz'),
                          ('Clearance certificate — Ben Torres', 'ramon.delacruz'), ('Waste Materials Report', 'jose.reyes'),
                          ('Report of Physical Count — supplies', 'jose.reyes'), ('Purchase order — chairs', 'ramon.delacruz'),
                          ('Gate pass — laptop', 'ramon.delacruz'), ('Petty cash replenishment report', 'liza.mendoza'), ('IIRUP — desktops', 'ana.cruz')]:
        upload('/signing/requests', {'title': title, 'signer_id': U[signer]}, title.replace(' — ', '_').replace(' ', '_') + '.pdf', pdf(title), T['maria.santos'])

    print('Done. Demo staff sign in with', DEMO_PASSWORD, '(e.g. maria.santos@agency.gov.ph).')


if __name__ == '__main__':
    try:
        main()
    except Fail as e:
        print('Stopped:', e, file=sys.stderr)
        sys.exit(1)
