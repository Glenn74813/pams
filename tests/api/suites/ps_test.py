import json, urllib.request, urllib.error, time, datetime, os
B=os.environ.get('PAMS_API',__import__('os').environ['PAMS_API'])
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
today=datetime.date.today(); TODAY=today.isoformat(); Y=today.year
PS='Procurement from PS-DBM'
SA=login('superadmin@inventory.com','admin123')
ids={}
for key,rid in [('pur',3),('eu',15),('wh',6)]:
    d=must(*call('POST','/users',{'name':'zz '+key,'email':f'zz.{key}@test.local','password':'Zz#Test2026','password_confirmation':'Zz#Test2026','role_id':rid,'department_id':1},SA),201); ids[key]=d['user']['id']
T={k:login(f'zz.{k}@test.local','Zz#Test2026') for k in ids}
def approved_pr(lines):
    d=must(*call('POST','/purchase-requests',{'purpose':'PS-DBM test','priority':'medium','items':lines},SA),201); pid=d['id']
    call('POST',f'/purchase-requests/{pid}/submit',tok=SA); call('POST',f'/purchase-requests/{pid}/approve-step',{},SA)
    call('POST',f'/purchase-requests/{pid}/certify-funds',{},SA)
    for _ in range(3): s,d=call('POST',f'/purchase-requests/{pid}/approve-step',{},SA)
    assert d['status']=='approved',d; return d

# ── catalogue
s,c1=call('POST','/ps-dbm/catalogue',{'code':'ZZ-PS-0001','description':'Paper, multicopy, A4, 80gsm','unit':'ream','unit_price':245},T['pur']); ok('add PS catalogue item', s==201 and float(c1['unit_price'])==245, c1)
s,c2=call('POST','/ps-dbm/catalogue',{'code':'ZZ-PS-0002','description':'Ballpen, black','unit':'piece','unit_price':8},T['pur']); ok('second catalogue item', s==201, c2)
s,d=call('POST','/ps-dbm/catalogue',{'code':'zz-ps-0001','description':'x','unit':'pc','unit_price':1},T['pur']); ok('PS code is unique', s==422, d)
s,d=call('POST','/ps-dbm/catalogue',{'code':'ZZ-PS-9','description':'x','unit':'pc','unit_price':1},T['eu']); ok('end user cannot edit the catalogue', s==403, d)
s,d=call('GET','/ps-dbm/catalogue?search=ballpen',tok=T['pur']); ok('search the catalogue', [x['code'] for x in d['data']]==['ZZ-PS-0002'], d)

# ── PR with PS-DBM lines
pr=approved_pr([{'general_description':'Bond paper A4','unit':'ream','quantity':20,'estimated_unit_cost':260,'procurement_mode':PS},
                {'general_description':'Ballpen black','unit':'piece','quantity':50,'estimated_unit_cost':10,'procurement_mode':PS},
                {'general_description':'Stapler heavy duty','unit':'piece','quantity':5,'estimated_unit_cost':300,'procurement_mode':PS},
                {'general_description':'Printer toner','unit':'piece','quantity':2,'estimated_unit_cost':4000,'procurement_mode':'Small Value Procurement'}])
PR=pr['id']; BOND,PEN,STAP,TON=[i['id'] for i in pr['items']]
ok('PS-DBM is a procurement mode on PR lines', pr['items'][0].get('procurement_mode')==PS, pr['items'][0])
s,d=call('GET','/ps-dbm/purchase-requests',tok=T['pur']); o=[p for p in d['data'] if p['id']==PR]
ok('PR offered with its PS-DBM lines only', len(o)==1 and {l['id'] for l in o[0]['lines']}=={BOND,PEN,STAP}, d)
s,d=call('POST','/rfq',{'title':'x','documents_stage':'offer','procurement_method':'Small Value Procurement','purchase_request_id':PR,'deadline':(today+datetime.timedelta(days=30)).isoformat()+'T10:00:00','items':[{'purchase_request_item_id':STAP}]},SA)
ok('PS-DBM line cannot go to an RFQ', s==422 and 'PS-DBM' in json.dumps(d), d)

base=lambda items: {'purchase_request_id':PR,'items':items}
s,d=call('POST','/ps-dbm/orders',base([{'purchase_request_item_id':TON,'quantity':1,'unit_price':100}]),T['pur']); ok('only PS-DBM lines go on an APR', s==422, d)
s,d=call('POST','/ps-dbm/orders',base([{'purchase_request_item_id':STAP,'quantity':5,'unit_price':350}]),T['pur']); ok('PS price above the PR estimate refused', s==422 and 'approved for' in json.dumps(d), d)
s,d=call('POST','/ps-dbm/orders',base([{'purchase_request_item_id':PEN,'quantity':60,'ps_catalogue_item_id':c2['id']}]),T['pur']); ok('more than the PR line refused', s==422, d)
s,d=call('POST','/ps-dbm/orders',base([{'purchase_request_item_id':BOND,'quantity':20}]),T['eu']); ok('end user cannot prepare APRs', s==403, d)
lines=[{'purchase_request_item_id':BOND,'quantity':20,'ps_catalogue_item_id':c1['id']},{'purchase_request_item_id':PEN,'quantity':50,'ps_catalogue_item_id':c2['id']},
       {'purchase_request_item_id':STAP,'quantity':5,'unit_price':280}]
s,o=call('POST','/ps-dbm/orders',base(lines),T['pur'])
ok('draft APR at PS prices', s==201 and o['apr_number']==f'APR-{Y}-0001' and float(o['total_amount'])==6700 and o['status']=='draft', o); APR=o['id']
ok('catalogue description and unit used', o['items'][0]['description']=='Paper, multicopy, A4, 80gsm' and o['items'][0]['catalogue_code']=='ZZ-PS-0001', o['items'][0])
s,d=call('POST','/ps-dbm/orders',base([{'purchase_request_item_id':BOND,'quantity':1,'ps_catalogue_item_id':c1['id']}]),T['pur']); ok('a line is procured once', s==422 and 'left' in json.dumps(d), d)
lines[1]['quantity']=40
s,o=call('PUT',f'/ps-dbm/orders/{APR}',base(lines),T['pur']); ok('edit the draft', s==200 and float(o['total_amount'])==6620, o.get('total_amount'))
s,d=call('POST',f'/ps-dbm/orders/{APR}/pay',{'payment_mode':'cash','payment_reference':'x','amount_paid':6620},T['pur']); ok('no payment before the Virtual Store order', s==409, d)
s,d=call('POST',f'/ps-dbm/orders/{APR}/submit',{},T['pur']); ok('Virtual Store reference required', s==422, d)
s,o=call('POST',f'/ps-dbm/orders/{APR}/submit',{'vs_reference':'VS-2026-000123','submitted_date':TODAY},T['pur']); ok('placed in the Virtual Store', s==200 and o['status']=='submitted', o)
s,d=call('PUT',f'/ps-dbm/orders/{APR}',base(lines),T['pur']); ok('submitted APR is locked', s==409, d)
stap=[l for l in o['items'] if l['purchase_request_item_id']==STAP][0]
s,o=call('POST',f"/ps-dbm/orders/{APR}/items/{stap['id']}/not-available",{'cnas_number':'CNAS-001','cnas_date':TODAY},T['pur'])
ok('CNAS takes the line off the request', s==200 and float(o['total_amount'])==5220 and o['status']=='submitted', o.get('total_amount'))
st=[l for l in o['items'] if l['purchase_request_item_id']==STAP][0]
ok('no validity date', 'cnas_valid_until' not in st, st)
rfq=lambda method: call('POST','/rfq',{'title':'ZZ Stapler','documents_stage':'offer','procurement_method':method,'purchase_request_id':PR,'deadline':(today+datetime.timedelta(days=30)).isoformat()+'T10:00:00','items':[{'purchase_request_item_id':STAP}]},SA)
s,d=rfq('Direct Contracting'); ok('after a CNAS, only Shopping or SVP', s==422, d)
s,d=rfq('Small Value Procurement'); ok('after a CNAS the line goes to SVP', s==201, d)
s,d=call('POST',f'/ps-dbm/orders/{APR}/pay',{'payment_mode':'lddap_ada','payment_reference':'ADA-1','amount_paid':6620},T['pur']); ok('payment must equal the request total', s==422, d)
s,o=call('POST',f'/ps-dbm/orders/{APR}/pay',{'payment_mode':'lddap_ada','payment_reference':'LDDAP-ADA 2026-10-001','payment_date':TODAY,'amount_paid':5220},T['pur'])
ok('payment recorded', s==200 and o['status']=='paid' and float(o['amount_paid'])==5220, o)
s,d=call('POST',f'/ps-dbm/orders/{APR}/cancel',{'reason':'x'},T['pur']); ok('a paid request cannot be cancelled', s==409, d)
s,o=call('POST',f'/ps-dbm/orders/{APR}/deliver',{'dr_number':'PS-DR-55501','dr_date':TODAY,'delivery_mode':'pickup'},T['pur'])
ok('Delivery Receipt opens an order to PS-DBM', s==200 and o['status']=='delivered' and o['purchase_order'] and o['purchase_order']['name']==o['apr_number'] and o['purchase_order_status']=='confirmed', o)
PO=o['purchase_order']['id']
s,po=call('GET',f'/procurement/purchase-orders/{PO}',tok=SA)
ok('the order is to PS-DBM, at PS prices, from the APR', po['supplier']['name']=='Procurement Service - DBM' and float(po['total_amount'])==5220 and po['source']['mode']==PS and po['source']['number']==o['apr_number'], {k:po.get(k) for k in ('supplier','total_amount','source')})
s,d=call('POST',f'/procurement/purchase-orders/{PO}/cancel',{'reason':'x'},SA); ok('the PS order is managed from its APR', s==409 and 'Agency Procurement Request' in json.dumps(d), d)
PL={l['description']:l['id'] for l in po['items']}
s,iar=call('POST','/receiving/inspection-reports',{'purchase_order_id':PO,'delivery_date':TODAY,'inspection_date':TODAY,
     'items':[{'purchase_order_item_id':PL['Paper, multicopy, A4, 80gsm'],'quantity_received':20,'quantity_accepted':20},{'purchase_order_item_id':PL['Ballpen, black'],'quantity_received':40,'quantity_accepted':40}]},T['wh'])
ok('PS delivery inspected in Receiving', s==201 and iar['status'] in ('complete','passed'), (s,iar))

# ── cancellation frees the lines; access
pr2=approved_pr([{'general_description':'Folder','unit':'piece','quantity':100,'estimated_unit_cost':6,'procurement_mode':PS}])
o2=must(*call('POST','/ps-dbm/orders',{'purchase_request_id':pr2['id'],'items':[{'purchase_request_item_id':pr2['items'][0]['id'],'quantity':100,'unit_price':5.5}]},T['pur']),201)
must(*call('POST',f"/ps-dbm/orders/{o2['id']}/submit",{'vs_reference':'VS-2'},T['pur']))
s,d=call('POST',f"/ps-dbm/orders/{o2['id']}/cancel",{},T['pur']); ok('cancelling needs a reason', s==422, d)
s,d=call('POST',f"/ps-dbm/orders/{o2['id']}/cancel",{'reason':'Funds realigned'},T['pur']); ok('cancel before payment', s==200 and d['status']=='cancelled', d)
s,d=call('GET','/ps-dbm/purchase-requests',tok=T['pur']); ok('cancelled request frees the PR lines', any(p['id']==pr2['id'] for p in d['data']), d)
o3=must(*call('POST','/ps-dbm/orders',{'purchase_request_id':pr2['id'],'items':[{'purchase_request_item_id':pr2['items'][0]['id'],'quantity':100,'unit_price':5.5}]},T['pur']),201)
s,d=call('DELETE',f"/ps-dbm/orders/{o3['id']}",tok=T['pur']); ok('delete a draft', s==200, d)
s,d=call('GET','/ps-dbm/orders',tok=T['eu']); ok('end user cannot list APRs', s==403, d)
s,d=call('GET','/ps-dbm/orders?status=delivered',tok=SA); ok('list by status', [x['apr_number'] for x in d['data']]==[f'APR-{Y}-0001'], d)
s,d=call('DELETE',f"/ps-dbm/catalogue/{c1['id']}",tok=T['pur']); ok('catalogue item on a request is kept', s==409, d)
s,d=call('GET',f'/reports/planning/apr?fiscal_year={Y}',tok=SA)
ok('Agency Procurement Report counts PS-DBM', s==200 and PS in json.dumps(d), (s, str(d)[:300]))
print('FAILURES:',fails)
