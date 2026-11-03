import json, urllib.request, urllib.error, time, datetime, os
B=os.environ.get('PAMS_API',__import__('os').environ['PAMS_API'])
def call(m, p, body=None, tok=None):
    h={'Content-Type':'application/json','Accept':'application/json'}
    if tok: h['Authorization']='Bearer '+tok
    r=urllib.request.Request(B+p, data=json.dumps(body).encode() if body is not None else None, headers=h, method=m)
    try:
        with urllib.request.urlopen(r) as x: return x.status, json.loads(x.read() or b'{}')
    except urllib.error.HTTPError as e: return e.code, json.loads(e.read() or b'{}')
from pams_db import sql
_raw=call
def call(m, p, body=None, tok=None):
    # Suppliers named in quotations and bids start ineligible; verify them straight away (as the other suites do).
    r=_raw(m, p, body, tok)
    if m=='POST' and ('/quotations' in p or '/bidders' in p):
        sql("update suppliers set is_eligible = " + ("1" if os.environ.get('PAMS_DB') == 'mssql' else "true") + " where name like 'ZZ %'")
    return r
def login(e,pw):
    for _ in range(8):
        s,d=call('POST','/auth/login',{'email':e,'password':pw})
        if s==200: return d['access_token']
        time.sleep(15)
    raise SystemExit((e,s,d))
fails=0
def ok(label, cond, extra=''):
    global fails
    if not cond: fails+=1
    print(('PASS ' if cond else 'FAIL ')+label, '' if cond else extra, flush=True)
def must(s,d,code=200):
    assert s==code,(s,d); return d
today=datetime.date.today(); D=lambda n: (today+datetime.timedelta(days=n)).isoformat()
SA=login('superadmin@inventory.com','admin123')
CB='Competitive Bidding'
def approved_pr(lines):
    d=must(*call('POST','/purchase-requests',{'purpose':'Fix test','priority':'medium','items':lines},SA),201); pid=d['id']
    call('POST',f'/purchase-requests/{pid}/submit',tok=SA); call('POST',f'/purchase-requests/{pid}/approve-step',{},SA)
    call('POST',f'/purchase-requests/{pid}/certify-funds',{},SA)
    for _ in range(3): s,d=call('POST',f'/purchase-requests/{pid}/approve-step',{},SA)
    assert d['status']=='approved',d; return d
def resolution(pr_id):
    r=must(*call('POST','/bac/resolutions',{'title':'Award','resolution_type':'resolution_to_award','purchase_request_id':pr_id,'content':'Award.'},SA),201)
    call('POST',f"/bac/resolutions/{r['id']}/submit",tok=SA); must(*call('POST',f"/bac/resolutions/{r['id']}/approve",tok=SA)); return r['id']
def to_opening(pr, title):
    b=must(*call('POST','/bidding',{'title':title,'purchase_request_id':pr['id'],'items':[{'purchase_request_item_id':pr['items'][0]['id']}]},SA),201)['id']
    must(*call('POST',f'/bidding/{b}/invitation',{'submission_deadline':D(30)+'T10:00:00'},SA))
    sql(f"update biddings set submission_deadline = {past}, bid_opening_date = {past} where id = {b}")
    must(*call('POST',f'/bidding/{b}/open-bids',tok=SA)); return b
past="'" + (datetime.datetime.now()-datetime.timedelta(hours=1)).strftime('%Y-%m-%d %H:%M:%S') + "'"

# ── 17: late and corrected bids during evaluation; the bid security flag is real
pr=approved_pr([{'general_description':'ZZ Laptop','unit':'unit','quantity':1,'estimated_unit_cost':60000,'procurement_mode':CB}])
BID=to_opening(pr,'ZZ Laptop bidding')
bid=lambda name,amt,posted=True: {'supplier_name':name,'bid_amount':amt,'bid_security_form':'cash','bid_security_amount':1200,'bid_security_posted':posted}
d=must(*call('POST',f'/bidding/{BID}/bidders',bid('ZZ Ace',55000),SA),201)
bd=lambda d: {x['supplier']['name']:x for x in d['bidders']}
A=bd(d)['ZZ Ace']['id']
d=must(*call('PUT',f'/bidding/bidders/{A}',{'stage':'eligibility','result':'passed'},SA)); ok('#17 first result starts the evaluation', d['status']=='evaluation', d['status'])
s,d=call('POST',f'/bidding/{BID}/bidders',bid('ZZ Late',58000,False),SA); ok('#17 a bid entered late can still be recorded', s==201 and d['can_record_bids'], (s,d.get('error',d.get('message'))))
L=bd(d)['ZZ Late']['id']
ok('#17 an unticked bid security is kept as not posted', not bd(d)['ZZ Late']['bid_security_posted'], bd(d)['ZZ Late'])
s,d=call('PUT',f'/bidding/bidders/{L}',{'stage':'eligibility','result':'passed'},SA); ok('#17 no eligibility without the security', s==422 and 'security' in json.dumps(d), d)
s,d=call('PUT',f'/bidding/bidders/{A}',{'bid_amount':54000},SA); ok('#17 an evaluated bid cannot be corrected', s==409, d)
s,d=call('PUT',f'/bidding/bidders/{L}',{'bid_amount':57500,'bid_security_posted':True},SA); ok('#17 a bid not yet evaluated can be corrected', s==200 and float(bd(d)['ZZ Late']['bid_amount'])==57500, d)
for st in ['technical','financial']: must(*call('PUT',f'/bidding/bidders/{A}',{'stage':st,'result':'passed'},SA))
must(*call('PUT',f'/bidding/bidders/{L}',{'stage':'eligibility','result':'failed','remarks':'Late'},SA))
d=must(*call('PUT',f'/bidding/bidders/{A}',{'stage':'post_qualification','result':'passed'},SA))
s,d=call('POST',f'/bidding/{BID}/bidders',bid('ZZ Later',50000),SA); ok('#17 no new bids once post-qualification starts', s==409, d)

# ── 15: Negotiated Procurement after two failed biddings
pr2=approved_pr([{'general_description':'ZZ Generator','unit':'unit','quantity':1,'estimated_unit_cost':900000,'procurement_mode':CB}])
neg=lambda: call('POST','/rfq',{'title':'ZZ Generator (negotiated)','documents_stage':'offer','procurement_method':'Negotiated Procurement','negotiated_ground':'two_failed_biddings','invited_suppliers':['A','B','C'],'purchase_request_id':pr2['id'],'deadline':D(30)+'T10:00:00',
                                 'justification':'Two failed biddings','items':[{'purchase_request_item_id':pr2['items'][0]['id']}]},SA)
b1=to_opening(pr2,'ZZ Generator'); must(*call('POST',f'/bidding/{b1}/declare-failed',{'reason':'No bids received'},SA))
s,d=neg(); ok('#15 one failed bidding is not enough for negotiation', s==422 and 'two failed' in json.dumps(d), d)
b2=to_opening(pr2,'ZZ Generator re-bid'); must(*call('POST',f'/bidding/{b2}/declare-failed',{'reason':'All bids over ABC'},SA))
s,d=neg(); ok('#15 after two failures the bidding line goes to Negotiated Procurement', s==201, d)

# ── 21: a contract drafted before its PO is linked when the PO is made
pr3=approved_pr([{'general_description':'ZZ Toner','unit':'pc','quantity':10,'estimated_unit_cost':4000,'procurement_mode':'Small Value Procurement'}])
rfq=must(*call('POST','/rfq',{'title':'ZZ Toner','documents_stage':'offer','procurement_method':'Small Value Procurement','purchase_request_id':pr3['id'],'deadline':D(30)+'T10:00:00','delivery_lead_days':7,
   'items':[{'purchase_request_item_id':pr3['items'][0]['id']}]},SA),201)
must(*call('POST',f"/rfq/{rfq['id']}/issue",tok=SA)); TI=rfq['items'][0]['id']
for n,a in [('ZZ Alpha',3500),('ZZ Beta',3800),('ZZ Gamma',3900)]:
    must(*call('POST',f"/rfq/{rfq['id']}/quotations",{'supplier_name':n,'items':[{'rfq_item_id':TI,'unit_price':a}]},SA),201)
sql(f"update rfqs set deadline = {past} where id = {rfq['id']}")
d=must(*call('POST',f"/rfq/{rfq['id']}/close",tok=SA)); win=[q for q in d['quotations'] if q['supplier']['name']=='ZZ Alpha'][0]['id']
must(*call('POST',f"/rfq/{rfq['id']}/award",{'quotation_id':win,'bac_resolution_id':resolution(pr3['id'])},SA))
must(*call('POST',f"/rfq/{rfq['id']}/noa",{},SA))
c=must(*call('POST','/contracts',{'source_type':'rfq','source_id':rfq['id'],'contract_type':'Goods'},SA),201)
ok('#21 contract drafted before any PO', c['purchase_order'] is None, c.get('purchase_order'))
po=must(*call('POST','/procurement/purchase-orders',{'source_type':'rfq','source_id':rfq['id'],'delivery_date':D(7)},SA),201)
s,c=call('GET',f"/contracts/{c['id']}",tok=SA); ok('#21 the new PO is linked to the contract', c['purchase_order'] and c['purchase_order']['id']==po['id'], c.get('purchase_order'))
must(*call('POST',f"/procurement/purchase-orders/{po['id']}/cancel",{},SA))
s,c=call('GET',f"/contracts/{c['id']}",tok=SA); ok('#21 cancelling the PO unlinks it', c['purchase_order'] is None, c.get('purchase_order'))
po2=must(*call('POST','/procurement/purchase-orders',{'source_type':'rfq','source_id':rfq['id'],'delivery_date':D(7)},SA),201)
s,c=call('GET',f"/contracts/{c['id']}",tok=SA); ok('#21 and the replacement PO takes its place', c['purchase_order'] and c['purchase_order']['id']==po2['id'], c.get('purchase_order'))
print('FAILURES:',fails)
