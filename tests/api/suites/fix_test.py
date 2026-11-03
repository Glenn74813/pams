import json, urllib.request, urllib.error, time, datetime, os, re, io, zipfile
B=os.environ.get('PAMS_API',__import__('os').environ['PAMS_API'])
MS=os.environ.get('PAMS_DB')=='mssql'
TRUE='1' if MS else 'true'
def call(m, p, body=None, tok=None):
    h={'Content-Type':'application/json','Accept':'application/json'}
    if tok: h['Authorization']='Bearer '+tok
    r=urllib.request.Request(B+p, data=json.dumps(body).encode() if body is not None else None, headers=h, method=m)
    try:
        with urllib.request.urlopen(r) as x: return x.status, json.loads(x.read() or b'{}')
    except urllib.error.HTTPError as e:
        raw=e.read()
        try: return e.code, json.loads(raw or b'{}')
        except ValueError: return e.code, {'raw': raw[:200].decode('utf-8','replace')}
def raw(m, p, tok=None):
    h={'Authorization':'Bearer '+tok} if tok else {}
    r=urllib.request.Request(B+p, headers=h, method=m)
    try:
        with urllib.request.urlopen(r) as x: return x.status, x.read()
    except urllib.error.HTTPError as e: return e.code, e.read()
def login(e,pw):
    for _ in range(8):
        s,d=call('POST','/auth/login',{'email':e,'password':pw})
        if s==200: return d['access_token']
        time.sleep(15)
    raise SystemExit((e,s,d))
from pams_db import sql
fails=0
def ok(label, cond, extra=''):
    global fails
    if not cond: fails+=1
    print(('PASS ' if cond else 'FAIL ')+label, '' if cond else extra, flush=True)
def must(s,d,code=200):
    assert s==code,(s,d); return d
today=datetime.date.today(); D=lambda n: (today+datetime.timedelta(days=n)).isoformat()
TS=lambda days: "'" + (datetime.datetime.now()+datetime.timedelta(days=days)).strftime('%Y-%m-%d %H:%M:%S') + "'"
it=lambda x: x.get('item',x)
def sql_fails(q):
    try:
        sql(q); return False
    except Exception:
        return True
PW='Zz#Test2026'

SA=login('superadmin@inventory.com','admin123')
def role(name, perms):
    return must(*call('POST','/roles',{'name':name,'description':'fix test','permissions':perms},SA),201)['role' if False else 'id'] if False else must(*call('POST','/roles',{'name':name,'description':'fix test','permissions':perms},SA),201)
def role_id(r): return r.get('id') or r.get('role',{}).get('id') or r.get('data',{}).get('id')
roles={k:role_id(role('ZZ '+k,p)) for k,p in {
    'analytics':['view_analytics'], 'reports':['view_reports'], 'rejecter':['reject_requests','view_requests'],
    'alt':['approve_procurement','view_procurement'], 'disposer':['approve_disposal','view_disposal']}.items()}
ids={}
def user(key, rid):
    d=must(*call('POST','/users',{'name':'zz '+key,'email':f'zz.{key}@test.local','password':PW,'password_confirmation':PW,'role_id':rid,'department_id':1},SA),201)
    ids[key]=d['user']['id']
for k,r in [('eu',15),('d',15),('dh',7),('bo',8)]: user(k,r)
for k in roles: user(k, roles[k])
T={k:login(f'zz.{k}@test.local',PW) for k in ids}

# ── 3, 4: supplier options and analytics
s,d=call('GET','/suppliers/options',tok=T['eu']); ok('#3 end user cannot list supplier options', s==403, (s,d))
s,d=call('GET','/suppliers/options',tok=SA); ok('#3 picker users can', s==200 and 'data' in d, d)
s,d=call('GET','/analytics/dashboard',tok=T['analytics']); ok('#4 view_analytics opens every dashboard section', s==200 and all(k in d for k in ('inventory','assets','procurement','suppliers')), list(d))
s,d=call('GET','/analytics/dashboard',tok=T['eu']); ok('#4 end user sees only own figures', s==200 and not any(k in d for k in ('inventory','assets','procurement','suppliers')), list(d))

# ── 45: sealed, append-only audit trail
s,v=call('GET','/audit/verify',tok=SA); ok('#45 new audit rows are sealed and intact', s==200 and v['altered']==[] and v['intact']>0, v)
row=sql("select id from audit_trails where seal is not null order by id").splitlines()[0]
ok('#45 audit rows cannot be edited', sql_fails(f"update audit_trails set description = 'x' where id = {row}"))
ok('#45 audit rows cannot be deleted', sql_fails(f"delete from audit_trails where id = {row}"))
off=("disable trigger audit_trails_append_only on audit_trails" if MS else "alter table audit_trails disable trigger audit_trails_append_only")
on=("enable trigger audit_trails_append_only on audit_trails" if MS else "alter table audit_trails enable trigger audit_trails_append_only")
orig=sql(f"select description from audit_trails where id = {row}")
sql(off); sql(f"update audit_trails set description = 'tampered' where id = {row}"); sql(on)
s,v=call('GET','/audit/verify',tok=SA); ok('#45 a row changed behind PAMS is detected', int(row) in v['altered'], v)
sql(off); sql(f"update audit_trails set description = '{orig.replace(chr(39), chr(39)*2)}' where id = {row}"); sql(on)
s,v=call('GET','/audit/verify',tok=SA); ok('#45 restored row is intact again', v['altered']==[], v)

# ── 44: deactivate, unlock, offices, password expiry
TD=T['d']
must(*call('POST',f"/users/{ids['d']}/deactivate",tok=SA))
s,d=call('GET','/auth/me',tok=TD); ok('#44 a deactivated user is signed out at once', s==401, (s,d))
s,d=call('POST','/auth/login',{'email':'zz.d@test.local','password':PW}); ok('#44 and cannot sign in', s==403, (s,d))
s,d=call('GET','/users/all',tok=SA); ok('#44 pickers leave deactivated users out', ids['d'] not in [u['id'] for u in d['data']], d)
s,d=call('POST',f"/users/{ids['d']}/deactivate",tok=SA); ok('#44 already deactivated', s==409, d)
s,d=call('POST',"/users/1/deactivate",tok=SA); ok('#44 cannot deactivate yourself', s==403, d)
s,d=call('POST',f"/users/{ids['d']}/deactivate",tok=T['eu']); ok('#44 end user cannot deactivate', s==403, d)
must(*call('POST',f"/users/{ids['d']}/activate",tok=SA))
time.sleep(61)
for _ in range(5): call('POST','/auth/login',{'email':'zz.d@test.local','password':'wrong-Pw1!'})
s,d=call('GET','/users?search=zz.d@',tok=SA); row=[u for u in d['data'] if u['id']==ids['d']]
ok('#44 users list shows the lockout', row and row[0]['locked_until'] is not None and row[0]['is_active'], row)
s,d=call('POST',f"/users/{ids['eu']}/unlock",tok=SA); ok('#44 unlocking an unlocked user is refused', s==409, d)
must(*call('POST',f"/users/{ids['d']}/unlock",tok=SA))
time.sleep(61)
s,d=call('POST','/auth/login',{'email':'zz.d@test.local','password':PW}); ok('#44 unlocked user signs in', s==200, (s,d)); TD=d.get('access_token')
ok('#44 deactivation, activation and unlock audited', all(a in sql(f"select action from audit_trails where auditable_id = {ids['d']} and auditable_type like '%User'") for a in ('deactivated','activated','unlocked')))

s,dp=call('POST','/departments',{'name':'ZZ Records Office','code':'zzrec'},SA); ok('#44 add an office', s==201 and dp['code']=='ZZREC', dp)
s,d=call('POST','/departments',{'name':'zz records office','code':'ZZX'},SA); ok('#44 duplicate office name refused', s==422, d)
s,d=call('PUT',f"/departments/{dp['id']}",{'name':'ZZ Records Section','code':'ZZREC'},SA); ok('#44 rename an office', s==200 and d['name']=='ZZ Records Section', d)
s,d=call('GET','/departments',tok=SA); ok('#44 offices listed with user counts', s==200 and any(x['id']==1 and x['users']>0 for x in d['data']), d)
s,d=call('DELETE','/departments/1',tok=SA); ok('#44 an office in use cannot be deleted', s==409, d)
s,d=call('POST','/departments',{'name':'ZZ Other','code':'ZZO'},T['eu']); ok('#44 end user cannot add offices', s==403, d)
s,d=call('DELETE',f"/departments/{dp['id']}",tok=SA); ok('#44 delete an unused office', s==200, d)

must(*call('PUT','/settings',{'capitalization_threshold':50000,'password_expiry_days':30},SA))
sql(f"update users set password_changed_at = {TS(-40)} where id = {ids['d']}")
s,d=call('GET','/auth/me',tok=TD); ok('#44 expired password flagged', s==200 and d.get('password_expired') is True, d)
s,d=call('GET','/inventory/items',tok=TD); ok('#44 nothing else works until it is changed', s==403 and d.get('password_expired'), (s,d))
s,d=call('GET','/settings',tok=TD); ok('#44 password rules still readable', s==200, d)
s,d=call('POST','/auth/change-password',{'current_password':PW,'password':PW,'password_confirmation':PW},TD); ok('#44 the same password is refused', s==422, d)
NEW='Zz#Renewed2027'
s,d=call('POST','/auth/change-password',{'current_password':PW,'password':NEW,'password_confirmation':NEW},TD); ok('#44 change the expired password', s==200, d)
s,d=call('GET','/auth/me',tok=TD); ok('#44 no longer expired', s==200 and not d.get('password_expired'), d)
must(*call('PUT','/settings',{'capitalization_threshold':50000,'password_expiry_days':0},SA))

# ── assets: lost on a gate pass (35), disposal remarks (36), IIRUP / RPCPPE (38, 39)
_,cat=call('POST','/inventory/categories',{'name':'ZZ Fixes'},SA)
ppe=it(must(*call('POST','/inventory/items',{'name':'ZZ Projector','category_id':cat['id'],'unit_of_measure':'unit','classification':'ppe'},SA),201))
lot=must(*call('POST',f"/inventory/items/{ppe['id']}/stock",{'received_date':D(-10),'quantity':3,'unit_cost':70000,'location':'Store'},SA),201)['stock']['id']
reg=must(*call('POST','/properties',{'item_id':ppe['id'],'stock_id':lot,'assigned_to':ids['eu'],'assigned_date':D(0),'units':[{'tag_number':'ZZ-F1'},{'tag_number':'ZZ-F2'}]},SA),201)
A1,A2=[p['id'] for p in reg['properties']]
g=must(*call('POST','/gate-pass',{'property_id':A1,'received_by':'Juan','purpose':'Seminar','expected_return_date':D(3)},SA),201)
s,d=call('PUT',f"/gate-pass/{g['id']}",{'property_id':A1,'received_by':'Juan dela Cruz','purpose':'Seminar in Cebu','expected_return_date':D(4)},SA); ok('#35 edit a gate pass that is out', s==200 and d['received_by']=='Juan dela Cruz', d)
s,d=call('DELETE',f"/gate-pass/{g['id']}",tok=SA); ok('#35 a pass still out cannot be deleted', s==409, d)
must(*call('POST',f"/gate-pass/{g['id']}/return",{'condition_on_return':'lost','remarks':'Left at venue'},SA))
s,a=call('GET',f'/properties/{A1}',tok=SA); ok('#35 lost return marks the asset lost, custodian kept', a['status']=='lost' and a['holder']['id']==ids['eu'], a)
s,d=call('POST',f'/properties/{A1}/transfer',{'to_user':ids['d'],'reason':'x'},SA); ok('#35 a lost asset cannot be transferred', s==409, d)
s,d=call('POST','/gate-pass',{'property_id':A1,'received_by':'x','purpose':'y'},SA); ok('#35 nor leave on another gate pass', s==422, d)
s,d=call('POST',f'/properties/{A1}/status',{'action':'found'},SA); ok('#35 mark found puts it back in use', s==200 and d.get('status',d.get('property',{}).get('status'))=='assigned', d)
s,d=call('DELETE',f"/gate-pass/{g['id']}",tok=T['eu']); ok('#35 end user cannot delete gate passes', s==403, d)
s,d=call('DELETE',f"/gate-pass/{g['id']}",tok=SA); ok('#35 delete a returned pass', s==200, d)
s,d=call('GET','/gate-pass?status=',tok=SA); ok('#35 deleted pass leaves the list', g['id'] not in [x['id'] for x in d['data']], d)

must(*call('POST',f'/properties/{A2}/return',{'reason':'Unserviceable','condition':'poor'},SA))
dsp=must(*call('POST','/disposal/requests',{'disposal_category':'property','category':'property','property_id':A2,'disposal_type':'destruction','reason':'Beyond repair','remarks':'Requester note'},SA),201)
must(*call('POST',f"/disposal/requests/{dsp['id']}/submit",tok=SA))
s,d=call('GET',f"/reports/iirup/export?disposal_ids={dsp['id']}",tok=SA); ok('#38 IIRUP refuses a disposal not yet completed', s==422, (s,d))
s,d=call('POST',f"/disposal/requests/{dsp['id']}/reject",{},T['disposer']); ok('#36 rejecting needs remarks', s==422, d)
s,d=call('POST',f"/disposal/requests/{dsp['id']}/approve",{'remarks':'Inspected, condemn'},T['disposer'])
ok('#36 approver remarks kept apart from the requester\'s', s==200 and d['remarks']=='Requester note' and d['approval_remarks']=='Inspected, condemn', d)
must(*call('POST',f"/disposal/requests/{dsp['id']}/complete",{'disposal_date':D(0)},T['disposer']))
s,d=call('GET','/disposal/requests?status=completed&per_page=200',tok=T['reports']); ok('#39 report viewers list completed disposals', s==200 and dsp['id'] in [x['id'] for x in d['data']], (s,d))
s,d=call('GET',f"/reports/iirup/preview?disposal_ids={dsp['id']}",tok=SA)
acq=datetime.date.fromisoformat(D(-10)).strftime('%m/%d/%Y')
ok('#38 IIRUP preview: date acquired and mode of disposal', d['rows'][0]['date_acquired']==acq and d['rows'][0]['disposal']=='Destruction' and 'condition' not in d['rows'][0], d)
s,b=raw('GET',f"/reports/iirup/export?disposal_ids={dsp['id']}",SA); ok('#38 IIRUP exports a completed disposal', s==200 and b[:2]==b'PK', s)
x=zipfile.ZipFile(io.BytesIO(b)); strings=x.read('xl/sharedStrings.xml').decode()
sheet=x.read('xl/worksheets/sheet1.xml').decode()
ok('#38 IIRUP prices the asset at its own cost', '70000' in sheet, re.findall(r'<v>[\d.]+</v>', sheet)[:12])
s,d=call('GET','/reports/rpcppe/preview',tok=SA); tags=[r['property_no'] for r in d['rows']]
ok('#39 RPCPPE leaves disposed assets out', 'ZZ-F1' in tags and 'ZZ-F2' not in tags, tags)
for path in ['/properties?page=1&search=ZZ-F','/properties/transfers/all?page=1','/requests?page=1','/receiving/inspection-reports?page=1']:
    s,d=call('GET',path,tok=T['reports']); ok(f'#39 report viewer can read {path.split("?")[0]}', s==200, (s,d))

# ── issuance: usable stock (28), cost at issue (29), reject_requests (26)
ink=it(must(*call('POST','/inventory/items',{'name':'ZZ Fix Ink','category_id':cat['id'],'unit_of_measure':'bottle','classification':'consumable'},SA),201))
old=must(*call('POST',f"/inventory/items/{ink['id']}/stock",{'received_date':D(-60),'quantity':5,'unit_cost':40,'expiration_date':D(30)},SA),201)['stock']['id']
must(*call('POST',f"/inventory/items/{ink['id']}/stock",{'received_date':D(-1),'quantity':3,'unit_cost':60,'expiration_date':D(200)},SA),201)
sql(f"update stocks set expiration_date = '{D(-1)}' where id = {old}")
dead=it(must(*call('POST','/inventory/items',{'name':'ZZ Fix Expired','category_id':cat['id'],'unit_of_measure':'bottle','classification':'consumable'},SA),201))
dl=must(*call('POST',f"/inventory/items/{dead['id']}/stock",{'received_date':D(-60),'quantity':4,'unit_cost':10,'expiration_date':D(30)},SA),201)['stock']['id']
sql(f"update stocks set expiration_date = '{D(-1)}' where id = {dl}")
s,d=call('POST','/requests',{'purpose':'x','priority':'medium','items':[{'item_id':dead['id'],'quantity':1}]},T['eu']); ok('#28 nothing usable, nothing to request', s==422, d)
rq=must(*call('POST','/requests',{'purpose':'Office use','priority':'medium','items':[{'item_id':ink['id'],'quantity':6}]},T['eu']),201)
RQ=rq.get('id') or rq.get('request',{}).get('id')
s,r=call('GET',f'/requests/{RQ}',tok=SA); r=r.get('data',r); line=r['items'][0]
ok('#28 usable stock excludes the expired lot', line['usable_stock']==3 and line['current_stock']==8, line)
s,d=call('PUT',f'/requests/{RQ}/approve-items',{'items':[{'id':line['id'],'status':'approved','approved_quantity':6}]},SA); ok('#28 issuing more than is usable is refused, naming expired stock', s==422 and 'expired' in json.dumps(d), d)
avg=float(sql(f"select weighted_average_cost from items where id = {ink['id']}").splitlines()[0])
must(*call('PUT',f'/requests/{RQ}/approve-items',{'items':[{'id':line['id'],'status':'approved','approved_quantity':3}]},SA))
must(*call('POST',f"/inventory/items/{ink['id']}/stock",{'received_date':D(0),'quantity':10,'unit_cost':200,'expiration_date':D(300)},SA),201)
s,r=call('GET',f'/requests/{RQ}',tok=SA); r=r.get('data',r)
ok('#29 the line keeps its cost at issue', abs(float(r['items'][0]['unit_cost'])-avg)<0.01 and abs(float(sql(f"select weighted_average_cost from items where id = {ink['id']}").splitlines()[0])-avg)>1, (r['items'][0]['unit_cost'], avg))
rq2=must(*call('POST','/requests',{'purpose':'More','priority':'low','items':[{'item_id':ink['id'],'quantity':1}]},T['eu']),201); RQ2=rq2.get('id') or rq2.get('request',{}).get('id')
s,r=call('GET',f'/requests/{RQ2}',tok=T['rejecter']); r=r.get('data',r); ok('#26 reject_requests alone may reject but not issue', r['can_reject'] and not r['can_process'], r)
s,d=call('PUT',f'/requests/{RQ2}/reject',{'reason':'Not needed'},T['rejecter']); ok('#26 and the rejection goes through', s==200, d)

# ── 32: one re-order rule
pap=it(must(*call('POST','/inventory/items',{'name':'ZZ Fix Paper','category_id':cat['id'],'unit_of_measure':'ream','classification':'consumable','min_stock':10,'max_stock':50},SA),201))
must(*call('POST',f"/inventory/items/{pap['id']}/stock",{'received_date':D(0),'quantity':4,'unit_cost':200},SA),201)
s,d=call('GET','/inventory/items?page=1&search=ZZ Fix Paper'.replace(' ','%20'),tok=SA); wi=[x for x in d['data'] if x['id']==pap['id']][0]
s,m=call('GET',f"/monitoring/stock?category_id={cat['id']}",tok=SA)
mrow=[x for x in (m.get('rows') or m.get('items') or m.get('data') or []) if x.get('id')==pap['id']]
ok('#32 Warehouse and Monitoring suggest the same order', wi['suggested_order']==46 and mrow and mrow[0]['suggested_order']==46, (wi.get('suggested_order'), mrow[:1], list(m)[:6]))

# ── 42: signatory names on the count reports
must(*call('PUT','/settings/signatories',{'signatories':[{'role_key':'inventory_committee_chair','user_id':ids['dh']},{'role_key':'coa_representative','user_id':ids['bo']}]},SA))
s,b=raw('GET','/reports/rpci/export?inventory_type=consumable',SA)
st=zipfile.ZipFile(io.BytesIO(b)).read('xl/sharedStrings.xml').decode() if s==200 else ''
ok('#42 RPCI prints the committee chair and COA representative', 'ZZ DH' in st and 'ZZ BO' in st, (s, re.findall(r'ZZ [A-Z]+', st)))
must(*call('PUT','/settings/signatories',{'signatories':[{'role_key':'inventory_committee_chair','user_id':None},{'role_key':'coa_representative','user_id':None}]},SA))

# ── 14: approve_procurement as alternate signatory
def pr_lines(mode, n=1): return [{'general_description':f'ZZ line {mode}','unit':'pc','quantity':n,'estimated_unit_cost':1000,'procurement_mode':mode}]
pr=must(*call('POST','/purchase-requests',{'purpose':'Alt signatory','priority':'medium','items':pr_lines('Small Value Procurement')},SA),201); P=pr['id']
must(*call('POST',f'/purchase-requests/{P}/submit',tok=SA))
s,d=call('GET',f'/purchase-requests/{P}',tok=T['alt']); d=d.get('data',d); ok('#14 alternate signatory may sign the open step', d.get('can_act') or d.get('can_sign') or d.get('can_approve'), {k:v for k,v in d.items() if k.startswith('can_')})
s,d=call('POST',f'/purchase-requests/{P}/approve-step',{},T['alt']); ok('#14 alternate signs the department step', s==200, d)
call('POST',f'/purchase-requests/{P}/certify-funds',{},SA)
s,d=call('POST',f'/purchase-requests/{P}/approve-step',{},T['alt']); ok('#14 but not a second step of the same PR', s==403 and 'another step' in json.dumps(d), d)

# ── 11: APP delete flag and budget floor
Y=today.year+2
lines=[{'general_description':'ZZ Fix Bond','unit':'ream','quantity':10,'estimated_unit_cost':250,'procurement_mode':'Small Value Procurement','procurement_category':'Goods','schedule':'Q1','fund_source':'General Appropriations Act (GAA)'}]
pp=must(*call('POST','/procurement-planning/ppmps',{'department_id':1,'fiscal_year':Y,'title':'ZZ Fix plan','items':lines},T['dh']),201)
must(*call('POST',f"/procurement-planning/ppmps/{pp['id']}/submit",tok=T['dh'])); must(*call('POST',f"/procurement-planning/ppmps/{pp['id']}/approve",tok=T['bo']))
s,ap=call('POST','/procurement-planning/apps/generate',{'fiscal_year':Y},T['bo']); ap=ap.get('app',ap); AP=ap['id']
s,ap=call('GET',f'/procurement-planning/apps/{AP}',tok=SA); ap=ap.get('data',ap); AI=ap['items'][0]
# Purchase requests draw on approved APPs only, and an approved APP is locked.
must(*call('POST',f'/procurement-planning/apps/{AP}/approve',tok=SA))
pr=must(*call('POST','/purchase-requests',{'purpose':'APP use','priority':'medium','app_id':AP,'fiscal_year':Y,'items':[{'app_item_id':AI['id'],'general_description':'ZZ Fix Bond','unit':'ream','quantity':4,'estimated_unit_cost':250,'procurement_mode':'Small Value Procurement'}]},SA),201)
P=pr['id']; must(*call('POST',f'/purchase-requests/{P}/submit',tok=SA)); call('POST',f'/purchase-requests/{P}/approve-step',{},SA); call('POST',f'/purchase-requests/{P}/certify-funds',{},SA)
for _ in range(3): s,d=call('POST',f'/purchase-requests/{P}/approve-step',{},SA)
line=lambda budget: {'id':AI['id'],'general_description':AI['general_description'],'unit':AI.get('unit'),'quantity':AI['quantity'],'estimated_budget':budget,
                     'procurement_mode':AI.get('procurement_mode'),'procurement_category':AI.get('procurement_category'),'schedule':AI.get('schedule'),'fund_source':AI.get('fund_source')}
s,d=call('PUT',f'/procurement-planning/apps/{AP}',{'items':[line(500)]},T['bo']); ok('#11 an approved APP, with PRs drawn on it, cannot be changed', s==409, d)
pr2=must(*call('POST','/purchase-requests',{'purpose':'Cancelled','priority':'low','app_id':AP,'fiscal_year':Y,'items':[{'app_item_id':AI['id'],'general_description':'ZZ Fix Bond','unit':'ream','quantity':1,'estimated_unit_cost':250,'procurement_mode':'Small Value Procurement'}]},SA),201)
must(*call('POST',f"/purchase-requests/{pr2['id']}/cancel",tok=SA))
sql(f"update purchase_requests set status = 'cancelled' where id = {P}")
s,ap=call('GET',f'/procurement-planning/apps/{AP}',tok=T['bo']); ap=ap.get('data',ap)
s,d=call('DELETE',f'/procurement-planning/apps/{AP}',tok=T['bo'])
ok('#11 Delete is offered only when the server allows it', ap['can_delete'] is False and s==409, (ap.get('can_delete'), s, d))

# ── 21: a contract drafted before its PO is linked when the PO is made
c=sql("select count(*) from contracts")
print('contracts present:', c)

print('FAILURES:',fails)
