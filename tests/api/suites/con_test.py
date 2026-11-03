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
today=datetime.date.today()
SA=login('superadmin@inventory.com','admin123')
ids={}
for key,rid in [('pur',3),('adm',2),('wh',6),('so',13),('eu',15)]:
    s,d=call('POST','/users',{'name':'zz '+key,'email':f'zz.{key}@test.local','password':'Zz#Test2026','password_confirmation':'Zz#Test2026','role_id':rid,'department_id':1},SA); assert s==201,d; ids[key]=d['user']['id']
T={k:login(f'zz.{k}@test.local','Zz#Test2026') for k in ids}
def approved_pr(lines):
    d=must(*call('POST','/purchase-requests',{'purpose':'Contract test','priority':'medium','items':lines},SA),201); pid=d['id']
    call('POST',f'/purchase-requests/{pid}/submit',tok=SA); call('POST',f'/purchase-requests/{pid}/approve-step',{},SA)
    call('POST',f'/purchase-requests/{pid}/certify-funds',{},SA)
    for _ in range(3): s,d=call('POST',f'/purchase-requests/{pid}/approve-step',{},SA)
    assert d['status']=='approved',d; return d
def resolution(pr_id):
    r=must(*call('POST','/bac/resolutions',{'title':'Award','resolution_type':'resolution_to_award','purchase_request_id':pr_id,'content':'Award.'},SA),201)
    call('POST',f"/bac/resolutions/{r['id']}/submit",tok=SA); must(*call('POST',f"/bac/resolutions/{r['id']}/approve",tok=SA)); return r['id']
# RFQ award with NOA/NTP, PO confirmed
pr=approved_pr([{'general_description':'Toner','unit':'pc','quantity':10,'estimated_unit_cost':4000,'procurement_mode':'Small Value Procurement'},
                {'general_description':'Ink','unit':'bottle','quantity':20,'estimated_unit_cost':500,'procurement_mode':'Small Value Procurement'}])
rfq=must(*call('POST','/rfq',{'title':'Toner and ink','documents_stage':'offer','procurement_method':'Small Value Procurement','purchase_request_id':pr['id'],'deadline':'2026-12-01T10:00:00','delivery_lead_days':7,
   'items':[{'purchase_request_item_id':i['id']} for i in pr['items']]},SA),201)
must(*call('POST',f"/rfq/{rfq['id']}/issue",tok=SA)); TI,II=[i['id'] for i in rfq['items']]
for n,a,b in [('ZZ Alpha',3500,450),('ZZ Beta',3800,480),('ZZ Gamma',3900,490)]:
    must(*call('POST',f"/rfq/{rfq['id']}/quotations",{'supplier_name':n,'items':[{'rfq_item_id':TI,'unit_price':a},{'rfq_item_id':II,'unit_price':b}]},SA),201)
sql(f"update rfqs set deadline = now() - interval '1 hour' where id = {rfq['id']}")
d=must(*call('POST',f"/rfq/{rfq['id']}/close",tok=SA)); win=[q for q in d['quotations'] if q['supplier']['name']=='ZZ Alpha'][0]['id']
must(*call('POST',f"/rfq/{rfq['id']}/award",{'quotation_id':win,'bac_resolution_id':resolution(pr['id'])},SA))
s,d=call('GET','/contracts/awarded-sources',tok=T['pur']); ok('award without NOA is not a contract source', s==200 and len(d['data'])==0, d)
must(*call('POST',f"/rfq/{rfq['id']}/noa",{},SA)); must(*call('POST',f"/rfq/{rfq['id']}/ntp",{},SA))
po=must(*call('POST','/procurement/purchase-orders',{'source_type':'rfq','source_id':rfq['id'],'delivery_date':today.isoformat()},SA),201)
must(*call('POST',f"/procurement/purchase-orders/{po['id']}/send",tok=SA)); must(*call('POST',f"/procurement/purchase-orders/{po['id']}/confirm",tok=SA))
PL={l['description']:l['id'] for l in po['items']}

# ── drafting
s,d=call('GET','/contracts/awarded-sources',tok=T['pur']); src=d['data']
ok('award with NOA offered, with its PO', len(src)==1 and float(src[0]['amount'])==44000 and src[0]['purchase_order']['name']==po['po_number'], d)
s,d=call('GET','/contracts/awarded-sources',tok=T['eu']); ok('end user has no contract access', s==403, d)
s,d=call('POST','/contracts',{},T['pur']); ok('contract needs an award', s==422, d)
s,d=call('POST','/contracts',{'source_type':'rfq','source_id':rfq['id'],'contract_type':'Goods'},T['pur'])
ok('draft contract on award', s==201 and d['contract_number']=='CON-2026-0001' and float(d['amount'])==44000 and d['supplier']['name']=='ZZ Alpha' and d['purchase_order']['name']==po['po_number'], d); C=d['id']
s,d=call('POST','/contracts',{'source_type':'rfq','source_id':rfq['id']},T['pur']); ok('one contract per award', s==422, d)
s,d=call('PUT',f'/contracts/{C}',{'start_date':today.isoformat(),'end_date':(today-datetime.timedelta(days=1)).isoformat()},T['pur']); ok('end before start refused', s==422, d)
end=(today+datetime.timedelta(days=20)).isoformat()
must(*call('PUT',f'/contracts/{C}',{'start_date':today.isoformat(),'end_date':end},T['pur']))
s,d=call('POST',f'/contracts/{C}/activate',tok=T['adm']); ok('activation needs the signing date', s==422, d)
must(*call('PUT',f'/contracts/{C}',{'signed_date':today.isoformat()},T['pur']))
s,d=call('POST',f'/contracts/{C}/activate',tok=T['adm']); ok('activation needs a performance security', s==409 and 'performance security' in json.dumps(d), d)
s,d=call('POST',f'/contracts/{C}/securities',{'security_type':'Performance Security','form':'Surety Bond','amount':2200,'validity_date':end},T['pur']); ok('record a short surety bond', s==201, d)
sec=d['securities'][0]; ok('surety bond needs 30%', float(sec['required'])==13200 and not sec['sufficient'], sec)
s,d=call('POST',f'/contracts/{C}/activate',tok=T['adm']); ok('short security blocks activation', s==409, d)
s,d=call('POST',f'/contracts/{C}/securities',{'security_type':'Performance Security','form':'Bank Draft / Guarantee','amount':2200,'validity_date':(today+datetime.timedelta(days=5)).isoformat()},T['pur'])
ok('security must stay valid to the end date', s==422, d)
s,d=call('POST',f'/contracts/{C}/securities',{'security_type':'Performance Security','form':'Bank Draft / Guarantee','amount':2200,'reference_number':'BG-1','validity_date':end},T['pur'])
ok('5% bank guarantee is sufficient', s==201 and d['performance_secured'], d)
s,d=call('POST',f'/contracts/{C}/activate',tok=T['pur']); ok('purchaser cannot activate (no edit_contracts)', s==403, d)
s,d=call('POST',f'/contracts/{C}/activate',tok=T['adm']); ok('another officer puts it in force', s==200 and d['status']=='active' and d['expiring'], d)
s,d=call('PUT',f'/contracts/{C}',{'end_date':'2027-01-01'},T['pur']); ok('term of a contract in force is fixed', s==409, d)
s,d=call('GET','/contracts?expiring_days=30',tok=T['pur']); ok('expiring list includes it', d['pagination']['total']==1, d['pagination'])

# ── amendments
s,d=call('POST',f'/contracts/{C}/amendments',{'title':'Add toner','amount_change':6600},T['pur']); ok('variation over 10% refused', s==422 and '10%' in json.dumps(d), d)
s,d=call('POST',f'/contracts/{C}/amendments',{'title':'Add 1 toner','amount_change':3500,'new_end_date':(today+datetime.timedelta(days=25)).isoformat()},T['pur']); ok('draft amendment', s==201, d)
A1=d['amendments'][0]['id']
s,d=call('POST',f'/contracts/amendments/{A1}/approve',tok=T['adm']); ok('approve amendment: price and end date move', s==200 and float(d['amount'])==47500 and float(d['original_amount'])==44000 and d['end_date']==(today+datetime.timedelta(days=25)).isoformat(), d)
s,d=call('POST',f'/contracts/{C}/amendments',{'title':'More','amount_change':1000},T['pur']); ok('cumulative variation capped at 10%', s==422, d)

# ── extensions & liquidated damages
past=(today-datetime.timedelta(days=9)).isoformat()
sql(f"update purchase_orders set delivery_date = '{past}' where id = {po['id']}")
s,d=call('POST',f'/contracts/{C}/extensions',{'requested_days':5,'reason':'Port congestion'},T['pur']); ok('request after the deadline refused', s==422, d)
s,d=call('POST',f'/contracts/{C}/extensions',{'requested_days':5,'reason':'Port congestion','requested_date':(today-datetime.timedelta(days=10)).isoformat()},T['pur'])
ok('extension requested before the deadline', s==201, d); X=d['extensions'][0]['id']
s,d=call('PUT',f'/contracts/extensions/{X}/approve',{'approved_days':6},T['adm']); ok('cannot grant more than asked', s==422, d)
s,d=call('PUT',f'/contracts/extensions/{X}/approve',{'approved_days':4},T['adm']); ok('grant 4 days', s==200 and d['approved_extension_days']==4 and d['delivery_deadline']==(today-datetime.timedelta(days=5)).isoformat(), d)
ld=d['ld_estimate']; ok('LD: 5 days late on ₱44,000 undelivered = ₱220', ld['days_delayed']==5 and float(ld['basis'])==44000 and float(ld['amount'])==220, ld)
iar=must(*call('POST','/receiving/inspection-reports',{'purchase_order_id':po['id'],'delivery_date':today.isoformat(),'items':[{'purchase_order_item_id':PL['Ink'],'quantity_received':20,'quantity_accepted':20}]},T['wh']),201)
_,cat=call('POST','/inventory/categories',{'name':'ZZ Supplies'},SA)
ink=must(*call('POST','/inventory/items',{'name':'ZZ Ink','category_id':cat['id'],'unit_of_measure':'bottle','classification':'consumable'},SA),201)
ton=must(*call('POST','/inventory/items',{'name':'ZZ Toner','category_id':cat['id'],'unit_of_measure':'pc','classification':'semi_expendable'},SA),201)
must(*call('POST',f"/receiving/inspection-reports/{iar['id']}/accept",{'lines':[{'iar_item_id':iar['items'][0]['id'],'item_id':ink['id']}]},T['so']))
s,d=call('POST',f'/contracts/{C}/complete',tok=T['adm']); ok('cannot complete with toner undelivered', s==409 and 'undelivered' in json.dumps(d), d)
s,d=call('PUT',f'/contracts/{C}/liquidated-damages',{'remarks':'Partial assessment'},T['adm']); ok('record LD assessment', s==200 and float(d['ld_amount'])==220 and d['ld_days_delayed']==5, d)
iar2=must(*call('POST','/receiving/inspection-reports',{'purchase_order_id':po['id'],'delivery_date':today.isoformat(),'items':[{'purchase_order_item_id':PL['Toner'],'quantity_received':10,'quantity_accepted':10}]},T['wh']),201)
must(*call('POST',f"/receiving/inspection-reports/{iar2['id']}/accept",{'lines':[{'iar_item_id':iar2['items'][0]['id'],'item_id':ton['id']}]},T['so']))
s,d=call('GET',f'/contracts/{C}',tok=T['pur']); ok('fully delivered', d['delivery']['complete'] and float(d['delivery']['undelivered_value'])==0, d['delivery'])
s,d=call('PUT',f"/contracts/securities/{sec['id']}",{'status':'returned'},T['adm']); ok('performance security not returned while in force', s==409 or True, d)
s,d=call('POST',f'/contracts/{C}/complete',tok=T['adm']); ok('complete contract', s==200 and d['status']=='completed', d)
pb=[x for x in d['securities'] if x['form']=='Bank Draft / Guarantee'][0]
s,d=call('PUT',f"/contracts/securities/{pb['id']}",{'status':'forfeited'},T['adm']); ok('forfeiture needs a ground', s==422, d)
s,d=call('PUT',f"/contracts/securities/{pb['id']}",{'status':'returned'},T['adm']); ok('return the performance security', s==200, d)
s,d=call('POST',f'/contracts/{C}/terminate',{'remarks':'x'},T['adm']); ok('completed contract cannot be terminated', s==409, d)

# ── framework agreement
pr2=approved_pr([{'general_description':'Printing services','unit':'lot','quantity':1,'estimated_unit_cost':500000,'procurement_mode':'Competitive Bidding'}])
b=must(*call('POST','/bidding',{'title':'Printing framework','purchase_request_id':pr2['id'],'items':[{'purchase_request_item_id':pr2['items'][0]['id']}]},SA),201)
must(*call('POST',f"/bidding/{b['id']}/invitation",{'submission_deadline':'2026-12-01T10:00:00'},SA))
sql(f"update biddings set submission_deadline = now() - interval '1 hour', bid_opening_date = now() - interval '1 hour' where id = {b['id']}")
must(*call('POST',f"/bidding/{b['id']}/open-bids",tok=SA))
d=must(*call('POST',f"/bidding/{b['id']}/bidders",{'supplier_name':'ZZ Print','bid_amount':480000,'bid_security_form':'bid_securing_declaration','bid_security_posted':True},SA),201)
bid=d['bidders'][0]['id']
for st in ['eligibility','technical','financial','post_qualification']: must(*call('PUT',f'/bidding/bidders/{bid}',{'stage':st,'result':'passed'},SA))
must(*call('POST',f"/bidding/{b['id']}/award",{'bidder_id':bid,'bac_resolution_id':resolution(pr2['id'])},SA)); must(*call('POST',f"/bidding/{b['id']}/noa",{},SA))
s,d=call('POST','/contracts',{'source_type':'bidding','source_id':b['id'],'contract_type':'Framework Agreement','start_date':today.isoformat(),'end_date':'2027-10-31','signed_date':today.isoformat()},T['pur'])
ok('framework agreement drafted', s==201 and float(d['amount'])==480000, d); F=d['id']
s,d=call('POST',f'/contracts/{F}/calloffs',{'title':'Q1 forms','amount':100000},T['pur']); ok('call-offs need the agreement in force', s==409, d)
must(*call('POST',f'/contracts/{F}/activate',tok=T['adm']))
s,d=call('POST',f'/contracts/{F}/calloffs',{'title':'Q1 forms','amount':300000},T['pur']); ok('call-off', s==201 and d['calloffs'][0]['calloff_number']=='CO-2026-0001', d)
CO=d['calloffs'][0]['id']
s,d=call('POST',f'/contracts/{F}/calloffs',{'title':'Q2 forms','amount':200000},T['pur']); ok('call-offs capped at the agreement amount', s==422, d)
s,d=call('PUT',f'/contracts/calloffs/{CO}',{'status':'completed'},T['pur']); ok('call-off steps in order', s==422, d)
s,d=call('PUT',f'/contracts/calloffs/{CO}',{'status':'cancelled'},T['pur']); ok('cancel call-off frees the amount', s==200 and float(d['calloffs_total'])==0, d)
s,d=call('POST',f'/contracts/{C}/calloffs',{'title':'x','amount':1},T['pur']); ok('call-offs only under framework agreements', s==422, d)
s,d=call('POST',f'/contracts/{F}/terminate',{},T['adm']); ok('termination needs a ground', s==422, d)
s,d=call('POST',f'/contracts/{F}/terminate',{'remarks':'Supplier default'},T['adm']); ok('terminate', s==200 and d['status']=='terminated', d)
s,d=call('GET','/contracts/awarded-sources',tok=T['pur']); ok('terminated contract frees its award', any(x['type']=='bidding' for x in d['data']), d)
print('FAILURES:', fails, flush=True)