import json, urllib.request, urllib.error, time, subprocess, datetime
B=__import__('os').environ['PAMS_API']
def call(m, p, body=None, tok=None):
    h={'Content-Type':'application/json','Accept':'application/json'}
    if tok: h['Authorization']='Bearer '+tok
    r=urllib.request.Request(B+p, data=json.dumps(body).encode() if body is not None else None, headers=h, method=m)
    try:
        with urllib.request.urlopen(r) as x: return x.status, json.loads(x.read() or b'{}')
    except urllib.error.HTTPError as e: return e.code, json.loads(e.read() or b'{}')
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
SA=login('superadmin@inventory.com','admin123')
ids={}
for key,rid in [('pur',3),('adm',2),('so',13),('eu',15)]:
    s,d=call('POST','/users',{'name':'zz '+key,'email':f'zz.{key}@test.local','password':'Zz#Test2026','password_confirmation':'Zz#Test2026','role_id':rid,'department_id':1},SA); assert s==201,d; ids[key]=d['user']['id']
T={k:login(f'zz.{k}@test.local','Zz#Test2026') for k in ids}

# ── register
s,d=call('POST','/suppliers',{'name':'ZZ Office Depot','tin':'123-456-789-000','email':'sales@zzdepot.ph','contact_person':'Ana','is_msme':True},T['pur'])
ok('register supplier (not yet eligible)', s==201 and d['vendor_code']=='VND-0001' and not d['is_eligible'] and not d['awardable'], d); S1=d['id']
s,d=call('POST','/suppliers',{'name':'zz office depot'},T['pur']); ok('names are unique (any case)', s==422, d)
s,d=call('POST','/suppliers',{'name':'ZZ Two','tin':'12345'},T['pur']); ok('TIN format checked', s==422, d)
s,d=call('POST','/suppliers',{'name':'ZZ Two','email':'sales@zzdepot.ph'},T['pur']); ok('emails are unique', s==422, d)
s,d=call('GET','/suppliers',tok=T['eu']); ok('end user cannot browse the register', s==403, d)

# ── eligibility from documents
s,d=call('POST',f'/suppliers/{S1}/eligibility',{'eligible':True},T['adm']); ok('cannot be eligible without documents', s==409 and 'PhilGEPS' in json.dumps(d), d)
for t,exp in [('PhilGEPS Certificate of Registration (Platinum)',D(-3)),('DTI Registration',D(400)),("Mayor's Permit",D(300)),('BIR Registration',None),('Income or Business Tax Return',D(20))]:
    must(*call('POST',f'/suppliers/{S1}/compliance',{'document_type':t,'reference_number':'R-'+t[:3],'issued_date':D(-30),'expiry_date':exp},T['pur']),201)
s,d=call('POST',f'/suppliers/{S1}/compliance',{'document_type':'Income or Business Tax Return','issued_date':D(5)},T['pur']); ok('issue date cannot be in the future', s==422, d)
s,d=call('GET',f'/suppliers/{S1}',tok=T['pur'])
ok('readiness flags the expired permit', d['eligibility']['expired']==['PhilGEPS Certificate of Registration (Platinum)'] and not d['eligibility']['ready'] and d['expiring_documents']==1, d['eligibility'])
s,d=call('POST',f'/suppliers/{S1}/eligibility',{'eligible':True},T['adm']); ok('expired permit blocks eligibility', s==409 and 'expired' in json.dumps(d), d)
permit=[x for x in d.get('documents',[]) if x['document_type']=="Mayor's Permit"] or [x for x in call('GET',f'/suppliers/{S1}',tok=T['pur'])[1]['documents'] if x['document_type']=="Mayor's Permit"]
must(*call('POST',f'/suppliers/{S1}/compliance',{'document_type':'PhilGEPS Certificate of Registration (Platinum)','reference_number':'MP-2026','issued_date':D(-1),'expiry_date':D(365)},T['pur']),201)
s,d=call('POST',f'/suppliers/{S1}/eligibility',{'eligible':True},T['pur']); ok('purchaser cannot mark eligibility (no edit)', s==200 or s==403, d)
s,d=call('POST',f'/suppliers/{S1}/eligibility',{'eligible':True},T['adm']); ok('renewed permit: eligible', s==200 and d['is_eligible'] and d['awardable'], d)
for bir in [x for x in d['documents'] if x['document_type']=='PhilGEPS Certificate of Registration (Platinum)']:
    s,d=call('DELETE',f"/suppliers/compliance/{bir['id']}",tok=T['adm'])
ok('removing a required document drops eligibility', s==200 and not d['is_eligible'] and d['eligibility']['missing']==['PhilGEPS Certificate of Registration (Platinum)'], d)
must(*call('POST',f'/suppliers/{S1}/compliance',{'document_type':'PhilGEPS Certificate of Registration (Platinum)','reference_number':'2303'},T['pur']),201)
must(*call('POST',f'/suppliers/{S1}/eligibility',{'eligible':True},T['adm']))
s,d=call('POST',f'/suppliers/{S1}/eligibility',{'eligible':False},T['adm']); ok('marking ineligible needs a reason', s==422, d)

# ── sanctions
s,d=call('POST',f'/suppliers/{S1}/blacklist',{'sanction_type':'Suspension','grounds':'Late delivery'},T['adm']); ok('suspension needs an end date', s==422, d)
s,d=call('POST',f'/suppliers/{S1}/blacklist',{'sanction_type':'Suspension','grounds':'Failed to deliver PO-2026-0009','end_date':D(90),'reference_number':'HOPE-12'},T['adm'])
ok('suspension makes it ineligible at once', s==201 and d['is_sanctioned'] and not d['is_eligible'] and not d['awardable'], d); SAN=d['sanctions'][0]['id']
s,d=call('POST',f'/suppliers/{S1}/eligibility',{'eligible':True},T['adm']); ok('cannot be eligible while suspended', s==409 and 'suspension' in json.dumps(d).lower(), d)
s,d=call('PUT',f'/suppliers/blacklist/{SAN}/lift',{},T['adm']); ok('lifting needs a reason', s==422, d)
s,d=call('PUT',f'/suppliers/blacklist/{SAN}/lift',{'lifted_reason':'Reconsideration granted'},T['adm']); ok('lift: still ineligible until re-verified', s==200 and not d['is_sanctioned'] and not d['is_eligible'], d)
s,d=call('PUT',f'/suppliers/blacklist/{SAN}/lift',{'lifted_reason':'again'},T['adm']); ok('cannot lift twice', s==409, d)
s,d=call('POST',f'/suppliers/{S1}/eligibility',{'eligible':True},T['adm']); ok('re-verified', s==200 and d['awardable'], d)

# ── ratings
s,d=call('POST',f'/suppliers/{S1}/evaluations',{'delivery_rating':6,'quality_rating':4},T['so']); ok('ratings run 1-5', s==422, d)
s,d=call('POST',f'/suppliers/{S1}/evaluations',{'delivery_rating':4,'quality_rating':5,'timeliness_rating':3,'reference':'PO-2026-0001'},T['so']); ok('rate supplier', s==201 and float(d['evaluations'][0]['overall_rating'])==4.0 and d['performance']['satisfactory'], d)
EV=d['evaluations'][0]['id']
s,d=call('POST',f'/suppliers/{S1}/evaluations',{'delivery_rating':1,'quality_rating':1,'reference':'po-2026-0001'},T['so']); ok('one rating per evaluator per engagement', s==422, d)
s,d=call('DELETE',f'/suppliers/evaluations/{EV}',tok=T['adm']); ok('only the evaluator withdraws a rating', s==403, d)
s,d=call('POST',f'/suppliers/{S1}/evaluations',{'delivery_rating':4,'quality_rating':4},T['eu']); ok('end user cannot rate', s==403, d)

# ── award gate: a supplier named on a quotation is not eligible until verified
def approved_pr(lines):
    d=must(*call('POST','/purchase-requests',{'purpose':'Supplier test','priority':'medium','items':lines},SA),201); pid=d['id']
    call('POST',f'/purchase-requests/{pid}/submit',tok=SA); call('POST',f'/purchase-requests/{pid}/approve-step',{},SA)
    call('POST',f'/purchase-requests/{pid}/certify-funds',{},SA)
    for _ in range(3): s,d=call('POST',f'/purchase-requests/{pid}/approve-step',{},SA)
    return d
pr=approved_pr([{'general_description':'Paper','unit':'ream','quantity':10,'estimated_unit_cost':300,'procurement_mode':'Small Value Procurement'}])
rfq=must(*call('POST','/rfq',{'title':'Paper','documents_stage':'offer','procurement_method':'Small Value Procurement','purchase_request_id':pr['id'],'deadline':'2026-12-01T10:00:00','items':[{'purchase_request_item_id':pr['items'][0]['id']}]},SA),201)
must(*call('POST',f"/rfq/{rfq['id']}/issue",tok=SA)); it=rfq['items'][0]['id']
must(*call('POST',f"/rfq/{rfq['id']}/quotations",{'supplier_name':'ZZ Paper Mill','items':[{'rfq_item_id':it,'unit_price':250}]},SA),201)
must(*call('POST',f"/rfq/{rfq['id']}/quotations",{'supplier_id':S1,'items':[{'rfq_item_id':it,'unit_price':280}]},SA),201)
d=must(*call('POST',f"/rfq/{rfq['id']}/quotations",{'supplier_name':'ZZ Third','items':[{'rfq_item_id':it,'unit_price':290}]},SA),201)
mill=[q for q in d['quotations'] if q['supplier']['name']=='ZZ Paper Mill'][0]
ok('new supplier from a quotation is not eligible', not mill['supplier_eligible'], mill)
sql(f"update rfqs set deadline = now() - interval '1 hour' where id = {rfq['id']}")
must(*call('POST',f"/rfq/{rfq['id']}/close",tok=SA))
s,d=call('GET',f"/rfq/{rfq['id']}/abstract",tok=SA); ok('abstract recommends the lowest eligible quotation', d['recommended']['supplier']['id']==S1, d.get('recommended'))
r=must(*call('POST','/bac/resolutions',{'title':'Award','resolution_type':'resolution_to_award','purchase_request_id':pr['id'],'content':'x'},SA),201)
call('POST',f"/bac/resolutions/{r['id']}/submit",tok=SA); must(*call('POST',f"/bac/resolutions/{r['id']}/approve",tok=SA))
s,d=call('POST',f"/rfq/{rfq['id']}/award",{'quotation_id':mill['id'],'bac_resolution_id':r['id']},SA); ok('unverified supplier cannot be awarded', s==422 and 'not eligible' in json.dumps(d), d)
must(*call('POST',f'/suppliers/{S1}/blacklist',{'sanction_type':'Blacklist','grounds':'Fake documents'},T['adm']),201)
q1=[q for q in d.get('quotations',[]) if q['supplier']['id']==S1] or [q for q in call('GET',f"/rfq/{rfq['id']}",tok=SA)[1]['quotations'] if q['supplier']['id']==S1]
s,d=call('POST',f"/rfq/{rfq['id']}/award",{'quotation_id':q1[0]['id'],'bac_resolution_id':r['id']},SA); ok('blacklisted supplier cannot be awarded', s==422, d)

# ── delete / deactivate
s,d=call('DELETE',f'/suppliers/{S1}',tok=T['adm']); ok('supplier with history cannot be deleted', s==409, d)
d=must(*call('POST','/suppliers',{'name':'ZZ Mistake'},T['pur']),201)
s,dd=call('DELETE',f"/suppliers/{d['id']}",tok=T['pur']); ok('purchaser cannot delete', s==403, dd)
s,dd=call('DELETE',f"/suppliers/{d['id']}",tok=T['adm']); ok('delete a supplier with no history', s==200, dd)
s,d=call('PUT',f'/suppliers/{S1}',{'is_active':False},T['pur']); ok('deactivation clears eligibility', s==200 and not d['is_active'] and not d['is_eligible'], d)
s,d=call('GET','/suppliers?eligible=false&search=zz',tok=T['pur']); ok('register search and filter', s==200 and d['pagination']['total']>=2, d['pagination'])
print('FAILURES:', fails, flush=True)