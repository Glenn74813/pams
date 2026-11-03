#!/usr/bin/env python3
"""
Fills a PAMS database with demo records: at least five in every module, at different stages, all
created through the API so every business rule, number series and audit entry is the real thing.

    PAMS_API=http://127.0.0.1:8095/api python3 tools/seed_demo.py

Run it on an empty test database (fresh migration, or after clearing the test data). It needs:
  - the backend running at PAMS_API with the seeded Super Admin (superadmin@inventory.com / admin123);
  - psql and the DB_PASSWORD in backend-java/.env, used only to move a few deadlines into the past
    (an RFQ or bidding can only be closed after its deadline).
Demo staff sign in with the password in DEMO_PASSWORD below. PostgreSQL only.
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
DEMO_PASSWORD = 'Demo#Pams2026'
ENV = pathlib.Path(__file__).resolve().parent.parent / '.env'
TODAY = datetime.date.today()
YEAR = TODAY.year


def day(n):
    return (TODAY + datetime.timedelta(days=n)).isoformat()


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


def sql(q):
    password = next((l.split('=', 1)[1].strip() for l in ENV.read_text().splitlines() if l.startswith('DB_PASSWORD=')), '')
    subprocess.run(['psql', '-h', os.environ.get('PGHOST', '127.0.0.1'), '-U', os.environ.get('PGUSER', 'inventory'),
                    '-d', os.environ.get('PGDATABASE', 'inventory'), '-Atqc', q], check=True, env={**os.environ, 'PGPASSWORD': password})


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


def main():
    SA = login('superadmin@inventory.com', 'admin123')

    # ── offices and people ──────────────────────────────────────────────
    step('offices and demo staff')
    depts = {d['code']: d['id'] for d in call('GET', '/departments', tok=SA)['data']}
    for code, name in [('ADMIN', 'Administrative Division'), ('FIN', 'Finance Division'), ('SPU', 'Supply and Property Unit'),
                       ('HRMO', 'Human Resource Management Office'), ('PDO', 'Planning and Development Office')]:
        if code not in depts:
            depts[code] = call('POST', '/departments', {'name': name, 'code': code}, SA)['id']
    staff = [('Maria Santos', 'maria.santos', 3, 'SPU'), ('Jose Reyes', 'jose.reyes', 13, 'SPU'), ('Ana Cruz', 'ana.cruz', 6, 'SPU'),
             ('Ramon Dela Cruz', 'ramon.delacruz', 12, 'ADMIN'), ('Liza Mendoza', 'liza.mendoza', 15, 'FIN'),
             ('Carlo Bautista', 'carlo.bautista', 7, 'IT'), ('Grace Villanueva', 'grace.villanueva', 8, 'FIN'),
             ('Paolo Garcia', 'paolo.garcia', 10, 'ADMIN'), ('Joy Aquino', 'joy.aquino', 11, 'SPU'), ('Ben Torres', 'ben.torres', 15, 'HRMO'),
             ('Nora Flores', 'nora.flores', 5, 'ADMIN')]
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
                                               ('CleanPro Janitorial Supply', 'Arnel Diaz', 'Caloocan City')], 1):
        s = call('POST', '/suppliers', {'name': name, 'contact_person': contact, 'email': f'sales{i}@{name.split()[0].lower()}.com.ph',
                                        'phone': f'(02) 8{i}12-34{i}0', 'address': f'{100 + i} Rizal Avenue, {city}', 'tin': f'00{i}-123-456-000',
                                        'philgeps_reg_number': f'2026-0{i}1234', 'credit_terms': '30 days', 'is_msme': i % 2 == 1}, SA)
        sid = s.get('id') or s.get('supplier', {}).get('id')
        for doc in ['PhilGEPS Certificate of Registration (Platinum)', 'PhilGEPS Organization ID', "Mayor's Permit", 'Income or Business Tax Return', 'Omnibus Sworn Statement']:
            call('POST', f'/suppliers/{sid}/compliance', {'document_type': doc, 'reference_number': f'{doc[:3].upper()}-{YEAR}-{i:03d}',
                                                          'issued_date': day(-60), 'expiry_date': day(300)}, SA)
        call('POST', f'/suppliers/{sid}/eligibility', {'eligible': True}, SA)
        SUP[name.split()[0]] = sid
    for sid, (q, d, t, c, a) in zip(list(SUP.values()), [(5, 4, 4, 5, 4), (4, 4, 3, 5, 4), (4, 3, 3, 4, 3), (5, 5, 5, 5, 4), (4, 4, 4, 4, 3)]):
        call('POST', f'/suppliers/{sid}/evaluations', {'evaluation_date': day(-15), 'quality_rating': q, 'delivery_rating': d, 'timeliness_rating': t,
                                                        'compliance_rating': c, 'after_sales_rating': a, 'reference': f'Mid-year rating {YEAR}',
                                                        'remarks': 'Semestral supplier performance evaluation'}, SA)

    # ── categories, items and opening stock ─────────────────────────────
    step('categories, items and opening stock')
    CAT = {}
    for c in ['Office Supplies', 'Janitorial Supplies', 'ICT Equipment', 'Furniture and Fixtures', 'Medical Supplies']:
        r = call('POST', '/inventory/categories', {'name': c}, SA)
        CAT[c] = r.get('id') or r.get('category', {}).get('id')
    item_defs = [
        ('bond', 'Bond paper, A4, 80gsm', 'Office Supplies', 'ream', 'consumable', 20, 200, [(120, 245, None, 'PS-DBM')]),
        ('pen', 'Ballpen, black, 0.5mm', 'Office Supplies', 'piece', 'consumable', 50, 500, [(300, 8, None, 'PS-DBM')]),
        ('toner', 'Toner cartridge, HP 85A', 'Office Supplies', 'piece', 'consumable', 4, 20, [(6, 3900, None, 'HP')]),
        ('alcohol', 'Isopropyl alcohol 70%, 500 ml', 'Medical Supplies', 'bottle', 'consumable', 30, 150, [(80, 95, day(400), 'Green Cross')]),
        ('disinfectant', 'Disinfectant solution, 1 gallon', 'Janitorial Supplies', 'gallon', 'consumable', 10, 60, [(25, 450, day(500), 'Lysol')]),
        ('firstaid', 'First aid kit, office type', 'Medical Supplies', 'set', 'consumable', 5, 30, [(12, 1500, day(700), 'MedFirst')]),
        ('chair', 'Office chair, ergonomic, with armrest', 'Furniture and Fixtures', 'unit', 'semi_expendable', 2, 20, [(8, 3500, None, 'Bayanihan')]),
        ('cabinet', 'Steel filing cabinet, 4-drawer', 'Furniture and Fixtures', 'unit', 'semi_expendable', 1, 10, [(4, 18000, None, 'Bayanihan')]),
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
                           ('Joy Aquino', 'secretariat', U['joy.aquino'])]:
        call('POST', '/bac/members', {'full_name': name, 'designation': des, 'user_id': uid, 'position': des.replace('_', ' ').title(),
                                      'appointment_order_no': f'SO-{YEAR}-014'}, SA)
    roster = [('Paolo Garcia', 'chairperson'), ('Grace Villanueva', 'vice_chairperson'), ('Carlo Bautista', 'member'),
              ('Ramon Dela Cruz', 'member'), ('Joy Aquino', 'secretariat')]
    MEET = []
    for i, title in enumerate(['Pre-procurement conference: ICT equipment', 'Opening of quotations: office furniture',
                               'Post-qualification report: laptops', 'Award of SVP contracts', 'Mid-year procurement review'], 1):
        m = call('POST', '/bac/meetings', {'title': title, 'meeting_date': f'{day(-40 + i * 6)}T09:00:00', 'venue': 'Conference Room A',
                                           'agenda': title}, SA)
        MEET.append(m['id'])
        if i <= 4:
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

    def line(desc, unit, qty, cost, mode, q):
        return {'general_description': desc, 'unit': unit, 'quantity': qty, 'estimated_unit_cost': cost, 'procurement_mode': mode,
                'procurement_category': 'Goods', 'schedule': q, 'fund_source': GAA}
    plans = {
        'ADMIN': [line('Bond paper, A4, 80gsm', 'ream', 100, 260, PS, 'Q1'), line('Office chair, ergonomic', 'unit', 10, 3800, SVP, 'Q2'),
                  line('Air-conditioning unit, 2HP inverter', 'unit', 6, 45000, CB, 'Q3'), line('Toner cartridge, HP 85A', 'piece', 12, 4000, SVP, 'Q2')],
        'FIN': [line('Ballpen, black', 'piece', 500, 10, PS, 'Q1'), line('Steel filing cabinet, 4-drawer', 'unit', 5, 19000, SVP, 'Q2'),
                line('Multifunction printer, A3', 'unit', 5, 40000, CB, 'Q3')],
        'HRMO': [line('Isopropyl alcohol 70%, 500 ml', 'bottle', 100, 100, PS, 'Q1'), line('Disinfectant solution, 1 gallon', 'gallon', 50, 480, SVP, 'Q2')],
        'IT': [line('Laptop computer, 14", Core i5', 'unit', 10, 65000, CB, 'Q2'), line('Desktop computer set', 'unit', 8, 58000, CB, 'Q2'),
               line('Network switch, 24-port managed', 'unit', 4, 85000, CB, 'Q3'), line('Paper clips, jumbo', 'box', 50, 25, PS, 'Q1')],
        'PDO': [line('First aid kit, office type', 'set', 20, 1600, SVP, 'Q2'), line('Bond paper, A4, 80gsm', 'ream', 50, 260, PS, 'Q1')],
        # Submitted but not yet approved, so it is not in the APP.
        'SPU': [line('Hand truck, heavy duty', 'unit', 2, 6500, SVP, 'Q3'), line('Storage rack, steel, 5-layer', 'unit', 6, 7800, SVP, 'Q3')],
    }
    for code, lines in plans.items():
        p = call('POST', '/procurement-planning/ppmps', {'department_id': depts[code], 'fiscal_year': YEAR, 'title': f'PPMP FY {YEAR} — {code}',
                                                         'items': lines}, SA)
        call('POST', f"/procurement-planning/ppmps/{p['id']}/submit", tok=SA)
        if code != 'SPU':
            call('POST', f"/procurement-planning/ppmps/{p['id']}/approve", tok=SA)
    app = call('POST', '/procurement-planning/apps/generate', {'fiscal_year': YEAR}, SA)
    app = app.get('app', app)
    call('POST', f"/procurement-planning/apps/{app['id']}/approve", {}, SA)
    app = call('GET', f"/procurement-planning/apps/{app['id']}", tok=SA)
    app = app.get('data', app)
    APPL = {(l['department']['id'] if l.get('department') else None, l['general_description']): l for l in app['items']}

    # ── purchase requests ───────────────────────────────────────────────
    step('purchase requests')

    def pr(code, desc, qty, purpose, approve=True, requester='liza.mendoza'):
        a = APPL[(depts[code], desc)]
        body = {'purpose': purpose, 'priority': 'medium', 'app_id': app['id'], 'department_id': depts[code],
                'items': [{'app_item_id': a['id'], 'general_description': desc, 'unit': a['unit'], 'quantity': qty,
                           'estimated_unit_cost': float(a['unit_cost']), 'procurement_mode': a['procurement_mode']}]}
        d = call('POST', '/purchase-requests', body, SA)
        if approve:
            call('POST', f"/purchase-requests/{d['id']}/submit", tok=SA)
            call('POST', f"/purchase-requests/{d['id']}/approve-step", {}, SA)
            call('POST', f"/purchase-requests/{d['id']}/certify-funds", {}, SA)
            for _ in range(3):
                d = call('POST', f"/purchase-requests/{d['id']}/approve-step", {}, SA)
        return d
    PR = {
        'chairs': pr('ADMIN', 'Office chair, ergonomic', 10, 'Replacement chairs for the Administrative Division'),
        'cabinets': pr('FIN', 'Steel filing cabinet, 4-drawer', 5, 'Records storage for disbursement vouchers'),
        'firstaid': pr('PDO', 'First aid kit, office type', 20, 'First aid kits for all floors'),
        'disinfectant': pr('HRMO', 'Disinfectant solution, 1 gallon', 50, 'Sanitation of work areas'),
        'toner': pr('ADMIN', 'Toner cartridge, HP 85A', 12, 'Printer consumables, second quarter'),
        'laptops': pr('IT', 'Laptop computer, 14", Core i5', 10, 'Laptops for field staff'),
        'desktops': pr('IT', 'Desktop computer set', 8, 'Replacement of old desktop units'),
        'aircon': pr('ADMIN', 'Air-conditioning unit, 2HP inverter', 6, 'Air-conditioning of the records room'),
        'switches': pr('IT', 'Network switch, 24-port managed', 4, 'Network upgrade, second floor'),
        'printers': pr('FIN', 'Multifunction printer, A3', 5, 'Printing of financial reports'),
        'ps_bond': pr('ADMIN', 'Bond paper, A4, 80gsm', 100, 'Common-use supplies, first quarter'),
        'ps_pen': pr('FIN', 'Ballpen, black', 500, 'Common-use supplies for Finance'),
        'ps_alcohol': pr('HRMO', 'Isopropyl alcohol 70%, 500 ml', 100, 'Health and safety supplies'),
        'ps_clips': pr('IT', 'Paper clips, jumbo', 50, 'Office supplies for IT'),
    }
    # Two still on their way: a draft and one at the department head.
    draft = call('POST', '/purchase-requests', {'purpose': 'Storage racks for the supply room', 'priority': 'low', 'department_id': depts['SPU'],
                                                'items': [{'general_description': 'Storage rack, steel, 5-layer', 'unit': 'unit', 'quantity': 4,
                                                           'estimated_unit_cost': 7800, 'procurement_mode': SVP}]}, T['maria.santos'])
    review = call('POST', '/purchase-requests', {'purpose': 'Extension cords and surge protectors', 'priority': 'high', 'department_id': depts['SPU'],
                                                 'items': [{'general_description': 'Surge protector, 6-outlet', 'unit': 'piece', 'quantity': 15,
                                                            'estimated_unit_cost': 850, 'procurement_mode': SVP}]}, T['maria.santos'])
    call('POST', f"/purchase-requests/{review['id']}/submit", tok=T['maria.santos'])

    # ── RFQs (alternative modes) ────────────────────────────────────────
    step('RFQs with quotations, awards, NOA and NTP')
    quotes = {'chairs': [('Bayanihan', 3500), ('Mega', 3650), ('CleanPro', 3700)], 'cabinets': [('Bayanihan', 17800), ('Mega', 18500), ('Northpoint', 18900)],
              'firstaid': [('MedFirst', 1450), ('CleanPro', 1550), ('Mega', 1580)], 'disinfectant': [('CleanPro', 440), ('MedFirst', 460), ('Mega', 470)],
              'toner': [('Northpoint', 3900), ('Mega', 3950), ('Bayanihan', 3990)]}
    RFQ = {}
    for i, key in enumerate(['chairs', 'cabinets', 'firstaid', 'disinfectant', 'toner']):
        p = PR[key]
        r = call('POST', '/rfq', {'title': p['purpose'], 'procurement_method': SVP, 'purchase_request_id': p['id'], 'deadline': f'{day(20)}T10:00:00',
                                  'delivery_lead_days': 15, 'items': [{'purchase_request_item_id': x['id']} for x in p['items']]}, SA)
        RFQ[key] = r['id']
        if key == 'toner':
            continue  # stays a draft
        call('POST', f"/rfq/{r['id']}/issue", tok=SA)
        for n, (sup, price) in enumerate(quotes[key], 1):
            call('POST', f"/rfq/{r['id']}/quotations", {'supplier_id': SUP[sup], 'quotation_number': f'Q-{YEAR}-{i}{n}', 'submitted_at': day(-1),
                                                       'items': [{'rfq_item_id': r['items'][0]['id'], 'unit_price': price}]}, SA)
        if key == 'disinfectant':
            continue  # quotations in, awaiting the deadline
        sql(f"update rfqs set deadline = now() - interval '1 hour' where id = {r['id']}")
        d = call('POST', f"/rfq/{r['id']}/close", tok=SA)
        win = min(d['quotations'], key=lambda q: float(q['total_amount']))['id']
        call('POST', f"/rfq/{r['id']}/award", {'quotation_id': win, 'bac_resolution_id': resolution(p['id'], p['purpose'])}, SA)
        call('POST', f"/rfq/{r['id']}/noa", {}, SA)
        call('POST', f"/rfq/{r['id']}/ntp", {}, SA)

    # ── competitive bidding ─────────────────────────────────────────────
    step('competitive biddings at different stages')
    past = (datetime.datetime.now() - datetime.timedelta(hours=1)).strftime('%Y-%m-%d %H:%M:%S')
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
        sql(f"update biddings set submission_deadline = '{past}', bid_opening_date = '{past}' where id = {b['id']}")
        call('POST', f"/bidding/{b['id']}/open-bids", tok=SA)
        ids = []
        for sup, amount in bids:
            d = call('POST', f"/bidding/{b['id']}/bidders", {'supplier_id': SUP[sup], 'bid_amount': amount, 'bid_security_form': 'bid_securing_declaration',
                                                              'bid_security_posted': True}, SA)
            ids.append([x for x in d['bidders'] if x['supplier']['id'] == SUP[sup]][0]['id'])
        if stage == 'opening':
            return
        for bidder in ids:
            for st in ['eligibility', 'technical', 'financial']:
                call('PUT', f'/bidding/bidders/{bidder}', {'stage': st, 'result': 'passed'}, SA)
        if stage == 'evaluation':
            return
        d = call('GET', f"/bidding/{b['id']}", tok=SA)
        top = d['next_post_qual_bidder_id']
        d = call('PUT', f'/bidding/bidders/{top}', {'stage': 'post_qualification', 'result': 'passed'}, SA)
        call('POST', f"/bidding/{b['id']}/award", {'bidder_id': d['recommended_bidder_id'], 'bac_resolution_id': resolution(p['id'], title)}, SA)
        call('POST', f"/bidding/{b['id']}/noa", {}, SA)
        call('POST', f"/bidding/{b['id']}/ntp", {}, SA, expect=None)
    bidding('laptops', 'Supply and delivery of laptop computers', 'award', [('Northpoint', 612000), ('Mega', 640000)])
    bidding('desktops', 'Supply and delivery of desktop computer sets', 'award', [('Northpoint', 448000), ('Bayanihan', 455000)])
    bidding('aircon', 'Supply and installation of air-conditioning units', 'evaluation', [('CleanPro', 262000), ('Bayanihan', 258000)])
    bidding('switches', 'Supply of managed network switches', 'opening', [('Northpoint', 330000)])
    bidding('printers', 'Supply of A3 multifunction printers', 'invitation', [])

    # ── purchase orders, receiving, contracts ───────────────────────────
    step('purchase orders, inspection and acceptance, contracts')
    POS = {}
    for key, kind, src in [('chairs', 'rfq', RFQ['chairs']), ('cabinets', 'rfq', RFQ['cabinets']), ('firstaid', 'rfq', RFQ['firstaid']),
                           ('laptops', 'bidding', BID['laptops'])]:
        po = call('POST', '/procurement/purchase-orders', {'source_type': kind, 'source_id': src, 'delivery_date': day(10),
                                                           'delivery_address': 'Supply and Property Unit, Main Building'}, T['maria.santos'])
        POS[key] = po['id']
        call('POST', f"/procurement/purchase-orders/{po['id']}/send", tok=SA)
        if key != 'firstaid':
            call('POST', f"/procurement/purchase-orders/{po['id']}/confirm", tok=SA)
    target = {'chairs': 'chair', 'cabinets': 'cabinet', 'laptops': 'laptop'}
    for key in ['chairs', 'cabinets', 'laptops']:
        po = call('GET', f'/procurement/purchase-orders/{POS[key]}', tok=SA)
        line = po['items'][0]
        qty = line['quantity'] if key != 'cabinets' else line['quantity'] - 1
        iar = call('POST', '/receiving/inspection-reports', {'purchase_order_id': POS[key], 'delivery_date': day(0), 'inspection_date': day(0),
                                                             'items': [{'purchase_order_item_id': line['id'], 'quantity_received': qty, 'quantity_accepted': qty}]},
                   T['ana.cruz'])
        if key != 'cabinets':
            call('POST', f"/receiving/inspection-reports/{iar['id']}/accept",
                 {'lines': [{'iar_item_id': iar['items'][0]['id'], 'item_id': IT[target[key]], 'location': 'Main stockroom',
                             'brand_name': 'Lenovo' if key == 'laptops' else 'Bayanihan', 'warranty_months': 36 if key == 'laptops' else 12}]}, T['jose.reyes'])
    # The first-aid kits: supplier confirmed, delivery awaiting inspection.
    call('POST', f"/procurement/purchase-orders/{POS['firstaid']}/confirm", tok=SA)
    po = call('GET', f"/procurement/purchase-orders/{POS['firstaid']}", tok=SA)
    call('POST', '/receiving/inspection-reports', {'purchase_order_id': POS['firstaid'], 'delivery_date': day(0), 'inspection_date': day(0),
                                                   'items': [{'purchase_order_item_id': po['items'][0]['id'], 'quantity_received': po['items'][0]['quantity'],
                                                              'quantity_accepted': po['items'][0]['quantity']}]}, T['ana.cruz'])
    CON = {}
    for key, kind, src, ctype in [('chairs', 'rfq', RFQ['chairs'], 'Goods'), ('cabinets', 'rfq', RFQ['cabinets'], 'Goods'),
                                  ('firstaid', 'rfq', RFQ['firstaid'], 'Goods'), ('laptops', 'bidding', BID['laptops'], 'Goods'),
                                  ('desktops', 'bidding', BID['desktops'], 'Goods')]:
        c = call('POST', '/contracts', {'source_type': kind, 'source_id': src, 'contract_type': ctype, 'start_date': day(0), 'end_date': day(120),
                                        'signed_date': day(0) if key in ('laptops', 'chairs') else None}, SA)
        CON[key] = c['id']
    c = call('GET', f"/contracts/{CON['laptops']}", tok=SA)
    call('POST', f"/contracts/{CON['laptops']}/securities", {'security_type': 'Performance Security', 'form': 'Surety Bond', 'reference_number': 'SB-2026-0451',
                                                            'amount': round(float(c['amount']) * 0.30, 2), 'posted_date': day(0), 'validity_date': day(180)}, SA)
    call('POST', f"/contracts/{CON['laptops']}/activate", tok=SA, expect=None)

    # ── payments ────────────────────────────────────────────────────────
    step('payment schedules')
    for key, desc, pct in [('laptops', 'Progress billing: delivery and acceptance', 100), ('chairs', 'Full payment upon acceptance', 100),
                           ('cabinets', 'Partial delivery', 80), ('firstaid', 'Full payment upon acceptance', 100), ('desktops', 'Advance payment', 15)]:
        call('POST', '/payments/tranches', {'type': 'contract', 'id': CON[key], 'description': desc, 'percentage': pct, 'due_date': day(30)}, SA, expect=None)

    # ── PS-DBM ──────────────────────────────────────────────────────────
    step('PS-DBM catalogue and Agency Procurement Requests')
    CATL = {}
    for code, desc, unit, price in [('PS-OS-0001', 'Paper, multicopy, A4, 80gsm', 'ream', 245.50), ('PS-OS-0002', 'Ballpen, black, retractable', 'piece', 8.25),
                                    ('PS-MS-0003', 'Alcohol, ethyl, 70% solution, 500 ml', 'bottle', 92.00), ('PS-OS-0004', 'Paper clip, vinyl-coated, jumbo', 'box', 22.80),
                                    ('PS-OS-0005', 'Folder, tagboard, legal size', 'pack', 165.00)]:
        CATL[code] = call('POST', '/ps-dbm/catalogue', {'code': code, 'description': desc, 'unit': unit, 'unit_price': price, 'price_as_of': day(-30)}, SA)['id']
    for key, code, stage in [('ps_bond', 'PS-OS-0001', 'delivered'), ('ps_pen', 'PS-OS-0002', 'paid'), ('ps_alcohol', 'PS-MS-0003', 'submitted'),
                             ('ps_clips', 'PS-OS-0004', 'draft')]:
        p = PR[key]
        o = call('POST', '/ps-dbm/orders', {'purchase_request_id': p['id'], 'items': [{'purchase_request_item_id': x['id'], 'quantity': x['quantity'],
                                                                                         'ps_catalogue_item_id': CATL[code]} for x in p['items']]}, T['maria.santos'])
        if stage == 'draft':
            continue
        o = call('POST', f"/ps-dbm/orders/{o['id']}/submit", {'vs_reference': f'VS-{YEAR}-00{o["id"]}4512', 'submitted_date': day(-7)}, T['maria.santos'])
        if stage == 'submitted':
            continue
        o = call('POST', f"/ps-dbm/orders/{o['id']}/pay", {'payment_mode': 'lddap_ada', 'payment_reference': f'LDDAP-ADA {YEAR}-10-{o["id"]:03d}',
                                                           'payment_date': day(-5), 'amount_paid': float(o['total_amount'])}, T['maria.santos'])
        if stage == 'paid':
            continue
        o = call('POST', f"/ps-dbm/orders/{o['id']}/deliver", {'dr_number': f'PS-DR-{YEAR}-77{o["id"]}31', 'dr_date': day(-2), 'delivery_mode': 'pickup'}, T['maria.santos'])
        # The bond paper from PS, inspected and taken into stock.
        po = call('GET', f"/procurement/purchase-orders/{o['purchase_order']['id']}", tok=SA)
        line_ = po['items'][0]
        iar = call('POST', '/receiving/inspection-reports', {'purchase_order_id': po['id'], 'delivery_date': day(-1), 'inspection_date': day(-1),
                                                             'items': [{'purchase_order_item_id': line_['id'], 'quantity_received': line_['quantity'],
                                                                        'quantity_accepted': line_['quantity']}]}, T['ana.cruz'])
        call('POST', f"/receiving/inspection-reports/{iar['id']}/accept", {'lines': [{'iar_item_id': iar['items'][0]['id'], 'item_id': IT['bond'],
                                                                                      'location': 'Main stockroom', 'brand_name': 'PS-DBM'}]}, T['jose.reyes'])
    # A fifth request: PS had no stock, so the line was certified not available.
    extra = call('POST', '/purchase-requests', {'purpose': 'Folders for records', 'priority': 'low', 'department_id': depts['HRMO'],
                                                'items': [{'general_description': 'Folder, tagboard, legal size', 'unit': 'pack', 'quantity': 20,
                                                           'estimated_unit_cost': 170, 'procurement_mode': PS}]}, SA)
    call('POST', f"/purchase-requests/{extra['id']}/submit", tok=SA)
    call('POST', f"/purchase-requests/{extra['id']}/approve-step", {}, SA)
    call('POST', f"/purchase-requests/{extra['id']}/certify-funds", {}, SA)
    for _ in range(3):
        extra = call('POST', f"/purchase-requests/{extra['id']}/approve-step", {}, SA)
    o = call('POST', '/ps-dbm/orders', {'purchase_request_id': extra['id'], 'items': [{'purchase_request_item_id': extra['items'][0]['id'], 'quantity': 20,
                                                                                         'ps_catalogue_item_id': CATL['PS-OS-0005']}]}, T['maria.santos'])
    o = call('POST', f"/ps-dbm/orders/{o['id']}/submit", {'vs_reference': f'VS-{YEAR}-0098812'}, T['maria.santos'])
    call('POST', f"/ps-dbm/orders/{o['id']}/items/{o['items'][0]['id']}/not-available", {'cnas_number': f'CNAS-{YEAR}-0217', 'cnas_date': day(-1)}, T['maria.santos'])

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
    ]
    for who, code, purpose, items, outcome in reqs:
        r = call('POST', '/requests', {'department_id': depts[code], 'purpose': purpose, 'priority': 'medium',
                                       'items': [{'item_id': IT[k], 'quantity': q} for k, q in items]}, T[who])
        rid = r.get('id') or r.get('request', {}).get('id') or r.get('data', {}).get('id')
        if outcome == 'issue':
            call('PUT', f'/requests/{rid}/approve', tok=SA)
        elif outcome == 'reject':
            call('PUT', f'/requests/{rid}/reject', {'reason': 'Toner already issued this quarter'}, SA)

    # ── stock adjustments ───────────────────────────────────────────────
    step('stock adjustments')
    for k, kind, qty, reason, approve in [('alcohol', 'decrease', 12, 'Expired lot, for condemnation', True),
                                          ('pen', 'decrease', 5, 'Damaged in storage', True),
                                          ('bond', 'increase', 3, 'Count variance found in physical count', True),
                                          ('disinfectant', 'decrease', 1, 'Container leaked', False),
                                          ('firstaid', 'increase', 2, 'Donation from the provincial health office', False)]:
        a = call('POST', '/stock-adjustments', {'item_id': IT[k], 'adjustment_type': kind, 'quantity': qty, 'reason': reason,
                                                'unit_cost': 1500 if k == 'firstaid' else None}, T['ana.cruz'])
        if approve:
            call('POST', f"/stock-adjustments/{a['id']}/approve", {}, SA, expect=None)

    # ── assets, gate passes ─────────────────────────────────────────────
    step('assets and gate passes')
    lots = call('GET', f"/inventory/items/{IT['laptop']}/lots", tok=SA)
    lots = lots.get('data', lots) if isinstance(lots, dict) else lots
    lot_id = max(lots, key=lambda l: l['quantity'])['id']
    holders = [U['maria.santos'], U['jose.reyes'], U['liza.mendoza'], U['carlo.bautista'], U['ramon.delacruz']]
    A = []
    for n, holder in enumerate(holders, 1):
        reg = call('POST', '/properties', {'item_id': IT['laptop'], 'stock_id': lot_id, 'assigned_to': holder, 'assigned_date': day(-20),
                                           'units': [{'tag_number': f'PROP-{YEAR}-LT{n:03d}', 'serial_number': f'PF3K{n:04d}X'}]}, SA)
        A.append(reg['properties'][0]['id'])
    dl = call('GET', f"/inventory/items/{IT['desktop']}/lots", tok=SA)
    dl = dl.get('data', dl) if isinstance(dl, dict) else dl
    reg = call('POST', '/properties', {'item_id': IT['desktop'], 'stock_id': dl[0]['id'], 'assigned_to': U['ramon.delacruz'], 'assigned_date': day(-300),
                                       'units': [{'tag_number': f'PROP-{YEAR}-DT{n:03d}', 'serial_number': f'DT9{n:03d}'} for n in range(1, 4)]}, SA)
    D = [p['id'] for p in reg['properties']]
    for n, (aid, by, purpose, back) in enumerate([(A[0], 'Maria Santos', 'Supplier site inspection', 2), (A[1], 'Jose Reyes', 'Training at the regional office', 5),
                                                  (A[2], 'Liza Mendoza', 'Field audit', 1), (A[3], 'Carlo Bautista', 'Repair at the service center', 10),
                                                  (A[4], 'Ramon Dela Cruz', 'Inventory of field office property', 3)]):
        g = call('POST', '/gate-pass', {'property_id': aid, 'received_by': by, 'purpose': purpose, 'date_released': day(-3), 'expected_return_date': day(back)}, SA)
        if n < 2:
            call('POST', f"/gate-pass/{g['id']}/return", {'condition_on_return': 'good', 'actual_return_date': day(-1), 'returned_by': by}, SA)

    # ── locations and maintenance ───────────────────────────────────────
    step('asset locations and maintenance')
    LOC = []
    for name, bldg, floor, room in [('Supply and Property Office', 'Main Building', '1F', '104'), ('ICT Server Room', 'Main Building', '2F', '210'),
                                    ('Finance Division', 'Main Building', '2F', '201'), ('Records Storage', 'Annex', 'Ground', 'R-1'),
                                    ('Conference Room A', 'Main Building', '3F', '301')]:
        LOC.append(call('POST', '/locations', {'name': name, 'building': bldg, 'floor': floor, 'room': room}, SA)['id'])
    for aid, loc in zip(A + D, [LOC[0], LOC[2], LOC[2], LOC[1], LOC[0], LOC[1], LOC[3], LOC[4]]):
        call('POST', f'/properties/{aid}/location', {'location_id': loc, 'remarks': 'Initial location'}, SA)
    for aid, kind, work, due, every in [(D[0], 'preventive', 'Clean and dust the desktop, check fans', -10, 6), (D[1], 'preventive', 'Clean and dust the desktop, check fans', 12, 6),
                                        (A[3], 'corrective', 'Replace the laptop battery', -2, None), (D[2], 'inspection', 'Annual inspection of the UPS and power supply', 25, 12),
                                        (A[0], 'preventive', 'OS and antivirus update', -40, 3)]:
        m = call('POST', '/maintenance', {'property_id': aid, 'maintenance_type': kind, 'description': work, 'scheduled_date': day(due), 'interval_months': every}, SA)
        if due == -40:
            call('POST', f"/maintenance/{m['id']}/complete", {'completed_date': day(-38), 'performed_by': 'ICT Unit', 'cost': 0}, SA)
        if kind == 'corrective':
            call('POST', f"/maintenance/{m['id']}/complete", {'completed_date': day(-1), 'performed_by': 'Laptop service center', 'cost': 3850, 'remarks': 'Battery replaced'}, SA)

    # ── physical counts ─────────────────────────────────────────────────
    step('physical counts')
    P = '/physical-inventory/counts'
    for ctype, purpose, stage in [('supplies', 'Year-end count of supplies', 'record'), ('semi_expendable', 'Semi-expendable property count', 'draft'),
                                  ('ppe', 'Annual PPE count (RPCPPE)', 'start'), ('supplies', 'Surprise count, medical supplies', 'draft'),
                                  ('supplies', 'Mid-year count of supplies', 'draft')]:
        c = call('POST', P, {'count_type': ctype, 'count_date': day(0), 'purpose': purpose,
                             'category_id': CAT['Medical Supplies'] if 'medical' in purpose else None}, SA)
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
    for i, aid in enumerate(D):
        call('POST', f'/properties/{aid}/return', {'reason': 'Unserviceable, beyond economical repair', 'condition': 'poor'}, SA)
    disposals = [
        {'disposal_category': 'property', 'property_id': D[0], 'disposal_type': 'condemnation', 'reason': 'Unserviceable desktop, motherboard failure'},
        {'disposal_category': 'property', 'property_id': D[1], 'disposal_type': 'public_auction', 'reason': 'Obsolete desktop set'},
        {'disposal_category': 'property', 'property_id': D[2], 'disposal_type': 'donation', 'reason': 'Donation to a public school'},
        {'disposal_category': 'inventory', 'item_id': IT['disinfectant'], 'quantity': 2, 'disposal_type': 'destruction', 'reason': 'Containers damaged'},
        {'disposal_category': 'inventory', 'item_id': IT['pen'], 'quantity': 10, 'disposal_type': 'destruction', 'reason': 'Dried out ink'},
    ]
    for n, body in enumerate(disposals):
        d = call('POST', '/disposal/requests', {**body, 'remarks': 'Inspected by the Inspection Committee'}, T['ramon.delacruz'])
        if n == 4:
            continue  # draft
        call('POST', f"/disposal/requests/{d['id']}/submit", tok=T['ramon.delacruz'])
        if n == 3:
            continue  # awaiting approval
        call('POST', f"/disposal/requests/{d['id']}/approve", {'remarks': 'Approved per IIRUP'}, SA)
        if n == 0:
            call('POST', f"/disposal/requests/{d['id']}/complete", {'disposal_date': day(0)}, SA)

    # ── petty cash ──────────────────────────────────────────────────────
    step('petty cash fund and vouchers')
    f = call('POST', '/petty-cash/funds', {'fund_name': f'Petty Cash Fund {YEAR} — Administrative Division', 'custodian_id': U['nora.flores'],
                                           'amount': 20000, 'fund_cluster': '01'}, SA)
    fid = f.get('id') or f.get('fund', {}).get('id')
    for n, (payee, particulars, amount) in enumerate([('Pandayan Bookshop', 'Folders and fasteners', 850), ('Grab Philippines', 'Transport, delivery of documents', 320),
                                                      ('Ace Hardware', 'Electrical repair parts', 2450), ('LBC Express', 'Courier of reports to the regional office', 480),
                                                      ('Mercury Drug', 'Medicines for the first aid kit', 1275)]):
        v = call('POST', f'/petty-cash/funds/{fid}/vouchers', {'payee': payee, 'particulars': particulars, 'amount': amount, 'voucher_date': day(-n)},
                 T['nora.flores'])
        if n < 3:
            vid = [x for x in v['vouchers'] if x['payee'] == payee][0]['id'] if 'vouchers' in v else v['id']
            call('POST', f'/petty-cash/vouchers/{vid}/approve', tok=SA)

    # ── documents ───────────────────────────────────────────────────────
    step('documents')
    for title, dtype, owner, oid in [('Purchase request — office chairs', 'PR', 'PurchaseRequest', PR['chairs']['id']),
                                     ('Notice of Award — laptops', 'NOA', None, None),
                                     ('Purchase order — steel cabinets', 'PO', 'PurchaseOrder', POS['cabinets']),
                                     ('Contract — laptop computers', 'CONTRACT', 'Contract', CON['laptops']),
                                     ('BAC resolution — SVP awards', 'BAC_RESOLUTION', None, None)]:
        fields = {'title': title, 'type': dtype, 'description': title}
        if owner:
            fields.update({'documentable_type': owner, 'documentable_id': oid})
        upload('/documents', fields, title.split(' —')[0].replace(' ', '_') + '.pdf', pdf(title), SA)

    # ── clearance and digital signing ───────────────────────────────────
    step('clearances and signing requests')
    call('POST', '/clearances', {'user_id': U['liza.mendoza'], 'purpose': 'transfer', 'purpose_details': 'Transfer to the regional office', 'effective_date': day(30)}, SA)
    call('POST', '/clearances', {'user_id': U['carlo.bautista'], 'purpose': 'end_of_contract', 'effective_date': day(14)}, SA)
    c = call('POST', '/clearances', {'user_id': U['ben.torres'], 'purpose': 'resignation', 'effective_date': day(7)}, SA)
    call('POST', f"/clearances/{c['id']}/issue", tok=SA)
    for title, signer in [('Property Card — laptop', 'ramon.delacruz'), ('RSMI — last month', 'liza.mendoza'), ('Inspection and Acceptance Report', 'ana.cruz'),
                          ('Clearance certificate — Ben Torres', 'ramon.delacruz'), ('Waste Materials Report', 'jose.reyes')]:
        upload('/signing/requests', {'title': title, 'signer_id': U[signer]}, title.split(' —')[0].replace(' ', '_') + '.pdf', pdf(title), T['maria.santos'])

    print('Done. Demo staff sign in with', DEMO_PASSWORD, '(e.g. maria.santos@agency.gov.ph).')


if __name__ == '__main__':
    try:
        main()
    except Fail as e:
        print('Stopped:', e, file=sys.stderr)
        sys.exit(1)
