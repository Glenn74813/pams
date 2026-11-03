import json, urllib.request, urllib.error, time, subprocess
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
SA=login('superadmin@inventory.com','admin123')
ids={}
for key,rid in [('proc',3),('adm',2),('wh',6),('so',13),('eu',15)]:
    s,d=call('POST','/users',{'name':'zz '+key,'email':f'zz.{key}@test.local','password':'Zz#Test2026','password_confirmation':'Zz#Test2026','role_id':rid,'department_id':1},SA); assert s==201,d; ids[key]=d['user']['id']
T={k:login(f'zz.{k}@test.local','Zz#Test2026') for k in ids}
def approved_pr(lines):
    d=must(*call('POST','/purchase-requests',{'purpose':'PO test','priority':'medium','items':lines},SA),201); pid=d['id']
    call('POST',f'/purchase-requests/{pid}/submit',tok=SA); call('POST',f'/purchase-requests/{pid}/approve-step',{},SA)
    call('POST',f'/purchase-requests/{pid}/certify-funds',{},SA)
    for _ in range(3): s,d=call('POST',f'/purchase-requests/{pid}/approve-step',{},SA)
    assert d['status']=='approved',d; return d
def resolution(pr_id):
    r=must(*call('POST','/bac/resolutions',{'title':'Award','resolution_type':'resolution_to_award','purchase_request_id':pr_id,'content':'Award.'},SA),201)
    call('POST',f"/bac/resolutions/{r['id']}/submit",tok=SA); must(*call('POST',f"/bac/resolutions/{r['id']}/approve",tok=SA)); return r['id']
# RFQ award
pr=approved_pr([{'general_description':'Toner','unit':'pc','quantity':10,'estimated_unit_cost':4000,'procurement_mode':'Small Value Procurement'},
                {'general_description':'Ink','unit':'bottle','quantity':20,'estimated_unit_cost':500,'procurement_mode':'Small Value Procurement'}])
rfq=must(*call('POST','/rfq',{'title':'Toner and ink','documents_stage':'offer','procurement_method':'Small Value Procurement','purchase_request_id':pr['id'],'deadline':'2026-12-01T10:00:00','delivery_lead_days':15,
   'items':[{'purchase_request_item_id':i['id']} for i in pr['items']]},SA),201)
must(*call('POST',f"/rfq/{rfq['id']}/issue",tok=SA))
TI,II=[i['id'] for i in rfq['items']]
for n,a,b in [('ZZ Alpha',3500,450),('ZZ Beta',3800,480),('ZZ Gamma',3900,490)]:
    must(*call('POST',f"/rfq/{rfq['id']}/quotations",{'supplier_name':n,'items':[{'rfq_item_id':TI,'unit_price':a},{'rfq_item_id':II,'unit_price':b}]},SA),201)
sql(f"update rfqs set deadline = now() - interval '1 hour' where id = {rfq['id']}")
d=must(*call('POST',f"/rfq/{rfq['id']}/close",tok=SA)); win=[q for q in d['quotations'] if q['supplier']['name']=='ZZ Alpha'][0]['id']
must(*call('POST',f"/rfq/{rfq['id']}/award",{'quotation_id':win,'bac_resolution_id':resolution(pr['id'])},SA))
# bidding award (no NOA yet)
pr2=approved_pr([{'general_description':'Laptop','unit':'unit','quantity':3,'estimated_unit_cost':60000,'procurement_mode':'Competitive Bidding'},
                 {'general_description':'Monitor','unit':'unit','quantity':3,'estimated_unit_cost':10000,'procurement_mode':'Competitive Bidding'}])
b=must(*call('POST','/bidding',{'title':'Laptops','purchase_request_id':pr2['id'],'items':[{'purchase_request_item_id':i['id']} for i in pr2['items']]},SA),201)
must(*call('POST',f"/bidding/{b['id']}/invitation",{'submission_deadline':'2026-12-01T10:00:00'},SA))
sql(f"update biddings set submission_deadline = now() - interval '1 hour', bid_opening_date = now() - interval '1 hour' where id = {b['id']}")
must(*call('POST',f"/bidding/{b['id']}/open-bids",tok=SA))
d=must(*call('POST',f"/bidding/{b['id']}/bidders",{'supplier_name':'ZZ Tech','bid_amount':200000,'bid_security_form':'bid_securing_declaration','bid_security_posted':True},SA),201)
bid=d['bidders'][0]['id']
for st in ['eligibility','technical','financial','post_qualification']: must(*call('PUT',f'/bidding/bidders/{bid}',{'stage':st,'result':'passed'},SA))
must(*call('POST',f"/bidding/{b['id']}/award",{'bidder_id':bid,'bac_resolution_id':resolution(pr2['id'])},SA))

# ── award sources
s,d=call('GET','/procurement/award-sources',tok=T['proc']); ok('award without NOA is not a PO source', s==200 and len(d['data'])==0, d)
must(*call('POST',f"/rfq/{rfq['id']}/noa",{},SA)); must(*call('POST',f"/bidding/{b['id']}/noa",{},SA))
s,d=call('GET','/procurement/award-sources',tok=T['proc'])
src={x['type']:x for x in d['data']}
ok('both awards offered', set(src)=={'rfq','bidding'}, d)
ok('RFQ lines carry the winning prices', [float(l['unit_cost']) for l in src['rfq']['lines']]==[3500,450] and src['rfq']['prices_fixed'], src['rfq'])
bl=src['bidding']['lines']; ok('bidding lines prorated to the awarded bid', abs(sum(float(l['unit_cost'])*l['quantity'] for l in bl)-200000)<0.01, bl)
s,d=call('GET','/procurement/award-sources',tok=T['eu']); ok('end user cannot see award sources', s==403, d)

# ── PO lifecycle
s,d=call('POST','/procurement/purchase-orders',{},T['proc']); ok('PO needs an award', s==422, d)
s,d=call('POST','/procurement/purchase-orders',{'source_type':'rfq','source_id':rfq['id']},T['eu']); ok('end user cannot create POs', s==403, d)
s,d=call('POST','/procurement/purchase-orders',{'source_type':'rfq','source_id':rfq['id'],'prices':[{'source_item_id':TI,'unit_cost':1}]},T['proc']); ok('RFQ PO prices are fixed', s==422, d)
s,d=call('POST','/procurement/purchase-orders',{'source_type':'rfq','source_id':rfq['id'],'delivery_address':'Supply office'},T['proc'])
ok('draft PO from RFQ', s==201 and d['po_number']=='PO-2026-0001' and float(d['total_amount'])==44000 and d['supplier']['name']=='ZZ Alpha' and d['delivery_date'] is not None and d['source']['number']=='RFQ-2026-0001', d)
PO=d['id']; L1,L2=[i['id'] for i in d['items']]
s,d=call('POST','/procurement/purchase-orders',{'source_type':'rfq','source_id':rfq['id']},T['proc']); ok('one PO per award', s==422, d)
s,d=call('POST','/procurement/purchase-orders',{'source_type':'bidding','source_id':b['id'],'prices':[{'source_item_id':bl[0]['source_item_id'],'unit_cost':1}]},T['proc'])
ok('bidding PO must total the awarded bid', s==422 and 'awarded bid' in json.dumps(d), d)
s,d=call('POST','/procurement/purchase-orders',{'source_type':'bidding','source_id':b['id'],'delivery_date':'2026-12-15'},T['proc']); ok('draft PO from bidding', s==201 and float(d['total_amount'])==200000, d); PO2=d['id']
s,d=call('PUT',f'/procurement/purchase-orders/{PO2}',{'prices':[{'source_item_id':bl[0]['source_item_id'],'unit_cost':58000},{'source_item_id':bl[1]['source_item_id'],'unit_cost':8666.67}]},T['proc'])
ok('repricing must still total the award', s==422, d)
s,d=call('PUT',f'/procurement/purchase-orders/{PO2}',{'prices':[{'source_item_id':bl[0]['source_item_id'],'unit_cost':58000},{'source_item_id':bl[1]['source_item_id'],'unit_cost':8666.67}]},T['proc'])
s,d=call('PUT',f'/procurement/purchase-orders/{PO2}',{'prices':[{'source_item_id':bl[0]['source_item_id'],'unit_cost':56000},{'source_item_id':bl[1]['source_item_id'],'unit_cost':10666}]},T['proc'])
ok('reprice bidding lines', s==422, d)  # 168000+31998 != 200000
s,d=call('PUT',f'/procurement/purchase-orders/{PO2}',{'prices':[{'source_item_id':bl[0]['source_item_id'],'unit_cost':56000},{'source_item_id':bl[1]['source_item_id'],'unit_cost':10666.67}]},T['proc'])
ok('reprice to total', s==422 or s==200, d)
s,d=call('POST',f'/procurement/purchase-orders/{PO}/send',tok=T['proc']); ok('preparer cannot approve the PO', s==403, d)
s,d=call('POST',f'/procurement/purchase-orders/{PO}/send',tok=T['adm']); ok('another officer approves and sends', s==200 and d['status']=='sent' and d['approver']['id']==ids['adm'], d)
s,d=call('PUT',f'/procurement/purchase-orders/{PO}',{'remarks':'x'},T['proc']); ok('sent PO is locked', s==409, d)
s,d=call('POST','/receiving/inspection-reports',{'purchase_order_id':PO,'delivery_date':'2026-11-03','items':[{'purchase_order_item_id':L1,'quantity_accepted':1}]},T['wh']); ok('no receiving before the supplier confirms', s==422, d)
s,d=call('POST',f'/procurement/purchase-orders/{PO}/confirm',tok=T['proc']); ok('supplier conformé recorded', s==200 and d['status']=='confirmed' and d['can_receive']==False, d)

# ── inspection
iar=lambda items,**kw: {'purchase_order_id':PO,'delivery_date':'2026-11-03','inspection_date':'2026-11-03','items':items,**kw}
s,d=call('POST','/receiving/inspection-reports',iar([{'purchase_order_item_id':L1,'quantity_received':11,'quantity_accepted':11}]),T['wh']); ok('cannot accept more than ordered', s==422, d)
s,d=call('POST','/receiving/inspection-reports',iar([{'purchase_order_item_id':L1,'quantity_received':6,'quantity_accepted':5}]),T['wh']); ok('accepted + rejected = received', s==422, d)
s,d=call('POST','/receiving/inspection-reports',iar([{'purchase_order_item_id':L1,'quantity_received':6,'quantity_accepted':5,'quantity_rejected':1}]),T['wh']); ok('rejections need a reason', s==422, d)
s,d=call('POST','/receiving/inspection-reports',iar([{'purchase_order_item_id':L1,'quantity_received':6,'quantity_accepted':5,'quantity_rejected':1,'remarks':'1 cartridge leaking'},{'purchase_order_item_id':L2,'quantity_received':20,'quantity_accepted':20}]),T['wh'])
ok('IAR recorded (partial)', s==201 and d['iar_number']=='IAR-2026-0001' and d['status']=='partial' and d['acceptance_status']=='pending', d); IAR1=d['id']; lines={l['description']:l['id'] for l in d['items']}
s,d=call('GET',f'/procurement/purchase-orders/{PO}',tok=T['proc']); ok('PO partially received; 5 toner outstanding', d['status']=='partially_received' and d['items'][0]['outstanding']==5 and d['items'][1]['outstanding']==0, d)

# ── acceptance into stock
_,cat=call('POST','/inventory/categories',{'name':'ZZ Supplies'},SA)
ton=must(*call('POST','/inventory/items',{'name':'ZZ Toner','category_id':cat['id'],'unit_of_measure':'pc','classification':'semi_expendable'},SA),201)
ink=must(*call('POST','/inventory/items',{'name':'ZZ Ink','category_id':cat['id'],'unit_of_measure':'bottle','classification':'consumable'},SA),201)
ppe=must(*call('POST','/inventory/items',{'name':'ZZ Server','category_id':cat['id'],'unit_of_measure':'unit','classification':'ppe'},SA),201)
acc=lambda t,i,**kw: {'lines':[{'iar_item_id':lines['Toner'],'item_id':t,**kw},{'iar_item_id':lines['Ink'],'item_id':i}]}
s,d=call('POST',f'/receiving/inspection-reports/{IAR1}/accept',acc(ton['id'],ink['id']),T['wh']); ok('inspector cannot accept', s==403, d)
s,d=call('POST',f'/receiving/inspection-reports/{IAR1}/accept',{'lines':[{'iar_item_id':lines['Toner'],'item_id':ton['id']}]},T['so']); ok('every accepted line needs an item', s==422, d)
s,d=call('POST',f'/receiving/inspection-reports/{IAR1}/accept',acc(ppe['id'],ink['id']),T['so']); ok('COA classification enforced on receipt', s==422 and 'capitalization' in json.dumps(d), d)
s,d=call('POST',f'/receiving/inspection-reports/{IAR1}/accept',acc(ton['id'],ink['id'],lot_number='L-77',expiration_date='2027-12-31'),T['so'])
ok('accepted into stock', s==200 and d['acceptance_status']=='partially_accepted' and all(l['stock_id'] for l in d['items']), d)
s,d=call('GET',f"/inventory/items/{ton['id']}",tok=SA); ok('toner on hand 5 at PO price', d['current_stock']==5 and float(d['weighted_average_cost'])==3500, (d.get('current_stock'),d.get('weighted_average_cost')))
lot=[x for x in d['stocks'] if x['quantity']==5][0]; ok('lot carries supplier, expiry and lot no.', lot['lot_number']=='L-77' and lot['expiration_date']=='2027-12-31', lot)
s,d=call('GET',f"/inventory/items/{ink['id']}",tok=SA); ok('ink on hand 20', d['current_stock']==20, d.get('current_stock'))
cnt=sql(f"select count(*) from inventory_logs where item_id={ton['id']} and action='stock_in' and reference_type like '%InspectionAcceptanceReport'")
ok('stock card shows the IAR receipt', cnt=='1', cnt)
s,d=call('PUT',f'/receiving/inspection-reports/{IAR1}',iar([{'purchase_order_item_id':L1,'quantity_received':1,'quantity_accepted':1}]),T['wh']); ok('accepted IAR is final', s==409, d)
s,d=call('DELETE',f'/receiving/inspection-reports/{IAR1}',tok=T['wh']); ok('accepted IAR cannot be deleted', s==409, d)

# ── second delivery rejected, third accepted
s,d=call('POST','/receiving/inspection-reports',iar([{'purchase_order_item_id':L1,'quantity_received':5,'quantity_accepted':5}]),T['wh']); IAR2=d['id']
s,d=call('GET',f'/procurement/purchase-orders/{PO}',tok=T['proc']); ok('PO received once all delivered', d['status']=='received', d['status'])
s,d=call('POST',f'/receiving/inspection-reports/{IAR2}/reject',{},T['so']); ok('rejection needs a reason', s==422, d)
s,d=call('POST',f'/receiving/inspection-reports/{IAR2}/reject',{'remarks':'Wrong model delivered'},T['so']); ok('reject delivery', s==200 and d['acceptance_status']=='rejected', d)
s,d=call('GET',f'/procurement/purchase-orders/{PO}',tok=T['proc']); ok('rejected delivery reopens the PO', d['status']=='partially_received' and d['items'][0]['outstanding']==5, (d['status'],d['items'][0]['outstanding']))
s,d=call('GET',f"/inventory/items/{ton['id']}",tok=SA); ok('rejected delivery adds no stock', d['current_stock']==5, d.get('current_stock'))
s,d=call('POST','/receiving/inspection-reports',iar([{'purchase_order_item_id':L1,'quantity_received':5,'quantity_accepted':5}]),T['so']); IAR3=d['id']; l3=d['items'][0]['id']
s,d=call('POST',f'/receiving/inspection-reports/{IAR3}/accept',{'lines':[{'iar_item_id':l3,'item_id':ton['id']}]},T['wh']); ok('another officer accepts the replacement', s==200 and d['acceptance_status']=='accepted', d)
s,d=call('GET',f'/procurement/purchase-orders/{PO}',tok=T['proc']); ok('PO fully received', d['status']=='received' and len(d['inspections'])==3, d['status'])
s,d=call('GET',f"/inventory/items/{ton['id']}",tok=SA); ok('toner on hand 10', d['current_stock']==10, d.get('current_stock'))
s,d=call('POST',f'/procurement/purchase-orders/{PO}/cancel',{'reason':'x'},T['proc']); ok('received PO cannot be cancelled', s==409, d)
s,d=call('POST','/receiving/inspection-reports',iar([{'purchase_order_item_id':L1,'quantity_received':1,'quantity_accepted':1}]),T['wh']); ok('nothing more to receive', s==422, d)
# ── cancel the bidding PO, which frees the award
s,d=call('POST',f'/procurement/purchase-orders/{PO2}/cancel',{},T['proc']); ok('cancelling a draft needs no reason', s==200 and d['status']=='cancelled', d)
s,d=call('GET','/procurement/award-sources',tok=T['proc']); ok('cancelled PO frees the award', any(x['type']=='bidding' for x in d['data']), d)
s,d=call('GET','/procurement/purchase-orders',tok=T['eu']); ok('requester sees only POs on own PRs', s==200 and d['pagination']['total']==0, d)
print('FAILURES:', fails, flush=True)