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
SA=login('superadmin@inventory.com','admin123')
ids={}
for key,rid in [('sec',11),('chair',10),('eu',15)]:
    s,d=call('POST','/users',{'name':'zz '+key,'email':f'zz.{key}@test.local','password':'Zz#Test2026','password_confirmation':'Zz#Test2026','role_id':rid,'department_id':1},SA); assert s==201,d; ids[key]=d['user']['id']
T={k:login(f'zz.{k}@test.local','Zz#Test2026') for k in ids}

def approved_pr(lines):
    s,d=call('POST','/purchase-requests',{'purpose':'RFQ test','priority':'medium','items':lines},SA); assert s==201,d; pid=d['id']
    call('POST',f'/purchase-requests/{pid}/submit',tok=SA); call('POST',f'/purchase-requests/{pid}/approve-step',{},SA)
    call('POST',f'/purchase-requests/{pid}/certify-funds',{},SA)
    for _ in range(3): s,d=call('POST',f'/purchase-requests/{pid}/approve-step',{},SA)
    assert d['status']=='approved',d; return d
pr=approved_pr([{'general_description':'Toner','unit':'pc','quantity':10,'estimated_unit_cost':4000,'procurement_mode':'Small Value Procurement'},
                {'general_description':'Ink','unit':'bottle','quantity':20,'estimated_unit_cost':500,'procurement_mode':'Small Value Procurement'}])
PR=pr['id']; TON,INK=[i['id'] for i in pr['items']]

# BAC composition
s,d=call('GET','/bac/members',tok=T['sec']); ok('empty BAC is not constituted', s==200 and not d['compliance']['is_properly_constituted'] and len(d['compliance']['issues'])>=3, d)
roster=[('A Chair','chairperson',ids['chair']),('B Vice','vice_chairperson',None),('C Member','member',None),('D Member','member',None),('E Member','member',None),('F Sec','secretariat',ids['sec'])]
for name,des,uid in roster:
    s,d=call('POST','/bac/members',{'full_name':name,'designation':des,'user_id':uid},T['sec']); assert s==201,d
s,d=call('POST','/bac/members',{'full_name':'x','designation':'boss'},T['sec']); ok('bad designation refused', s==422, d)
s,d=call('GET','/bac/members',tok=T['eu']); ok('end user has no BAC access', s==403, d)
s,d=call('GET','/bac/members',tok=T['sec']); ok('BAC properly constituted (5 voting + secretariat)', d['compliance']['is_properly_constituted'] and d['compliance']['voting_members']==5, d['compliance'])

# meetings & quorum
s,d=call('POST','/bac/meetings',{'title':'Award of toner RFQ','meeting_date':'2026-10-27T09:00:00','venue':'Conf room'},T['sec']); ok('schedule meeting', s==201 and d['meeting_number']=='BAC-MTG-2026-001', d); M1=d['id']
s,d=call('PUT',f'/bac/meetings/{M1}',{'status':'completed'},T['sec']); ok('cannot complete without attendance', s==422, d)
att=lambda present: [{'name':n,'committee_role':r,'present':(n in present)} for n,r,_ in roster]
s,d=call('POST',f'/bac/meetings/{M1}/attendance',{'attendees':att({'A Chair','C Member','D Member','F Sec'})},T['sec']); ok('attendance: 3 of 5 voting = quorum', s==200 and d['quorum']['met'] and d['quorum']['voting_present']==3, d.get('quorum'))
s,d=call('PUT',f'/bac/meetings/{M1}',{'status':'completed','minutes':'Recommended award to lowest.'},T['sec']); ok('complete meeting', s==200 and d['status']=='completed', d)
s,d=call('PUT',f'/bac/meetings/{M1}',{'title':'changed'},T['sec']); ok('held meeting details locked', s==409, d)
s,d=call('POST','/bac/meetings',{'title':'Thin meeting','meeting_date':'2026-10-27T14:00:00'},T['sec']); M2=d['id']
call('POST',f'/bac/meetings/{M2}/attendance',{'attendees':att({'A Chair','F Sec'})},T['sec'])
s,d=call('PUT',f'/bac/meetings/{M2}',{'status':'completed'},T['sec']); ok('thin meeting completes but no quorum', d['quorum']['met']==False, d.get('quorum'))

# resolutions
s,d=call('POST','/bac/resolutions',{'title':'Award toner','resolution_type':'resolution_to_award','bac_meeting_id':M1},T['sec']); ok('award resolution needs a PR', s==422, d)
s,d=call('POST','/bac/resolutions',{'title':'Award toner','resolution_type':'resolution_to_award','bac_meeting_id':M1,'purchase_request_id':PR,'content':'RESOLVED to award to the lowest responsive quotation.'},T['sec'])
ok('draft award resolution', s==201 and d['resolution_number']=='BAC-RES-2026-001', d); R1=d['id']
s,d=call('POST',f'/bac/resolutions/{R1}/approve',tok=T['chair']); ok('cannot approve a draft', s==409, d)
call('POST',f'/bac/resolutions/{R1}/submit',tok=T['sec'])
s,d=call('POST',f'/bac/resolutions/{R1}/approve',tok=T['sec']); ok('drafter cannot approve own resolution', s==403, d)
s,d=call('PUT',f'/bac/resolutions/{R1}',{'title':'x'},T['sec']); ok('submitted resolution locked', s==409, d)
s,d=call('POST','/bac/resolutions',{'title':'Thin','resolution_type':'other','bac_meeting_id':M2,'content':'x'},T['sec']); R2=d['id']
call('POST',f'/bac/resolutions/{R2}/submit',tok=T['sec'])
s,d=call('POST',f'/bac/resolutions/{R2}/approve',tok=T['chair']); ok('no quorum, no approval', s==409 and 'quorum' in d.get('error',''), d)
s,d=call('POST',f'/bac/resolutions/{R1}/approve',tok=T['chair']); ok('chair approves award resolution', s==200 and d['status']=='approved', d)
s,d=call('POST',f'/bac/meetings/{M1}/attendance',{'attendees':att({'A Chair'})},T['sec']); ok('attendance final once a resolution is approved', s==409, d)
s,d=call('DELETE',f'/bac/meetings/{M1}',tok=T['sec']); ok('held meeting cannot be deleted', s==409, d)

# RFQ drafting
base={'title':'Toner and ink','documents_stage':'offer','procurement_method':'Small Value Procurement','purchase_request_id':PR,'deadline':'2026-11-30T17:00:00'}
s,d=call('GET','/rfq/purchase-requests',tok=T['sec']); ok('approved PR offered with its lines', any(p['id']==PR and len(p['lines'])==2 for p in d['data']), d)
s,d=call('POST','/rfq',{**base,'documents_stage':'offer','procurement_method':'Competitive Dialogue','items':[{'purchase_request_item_id':TON}]},T['sec']); ok('method must match the planned mode', s==422 and 'planned under' in json.dumps(d), d)
s,d=call('POST','/rfq',{**base,'items':[{'purchase_request_item_id':TON,'quantity':11}]},T['sec']); ok('quantity within the PR line', s==422, d)
s,d=call('POST','/rfq',{**base,'items':[{'purchase_request_item_id':TON,'abc_unit_cost':4500}]},T['sec']); ok('ABC within the PR estimate', s==422 and 'approved for' in json.dumps(d), d)
s,d=call('POST','/rfq',{**base,'items':[{'purchase_request_item_id':TON},{'purchase_request_item_id':INK,'quantity':20}]},T['sec'])
ok('draft RFQ from PR lines', s==201 and d['rfq_number']=='RFQ-2026-0001' and float(d['abc'])==50000 and d['department']['name'], d); Q=d['id']
s,d=call('POST','/rfq',{**base,'items':[{'purchase_request_item_id':TON,'quantity':1}]},T['sec']); ok('PR line cannot go on two RFQs', s==422 and 'only 0' in json.dumps(d), d)
s,d=call('POST','/rfq',{**base,'items':[{'purchase_request_item_id':TON}]},T['eu']); ok('end user cannot draft RFQs', s==403, d)
s,d=call('POST',f'/rfq/{Q}/quotations',{'supplier_name':'X','items':[]},T['sec']); ok('no quotations on a draft', s==409, d)
call('PUT',f'/rfq/{Q}',{**base,'deadline':'2026-01-01T17:00:00','items':[{'purchase_request_item_id':TON},{'purchase_request_item_id':INK}]},T['sec'])
s,d=call('POST',f'/rfq/{Q}/issue',tok=T['sec']); ok('deadline must be in the future to issue', s==422, d)
call('PUT',f'/rfq/{Q}',{**base,'items':[{'purchase_request_item_id':TON},{'purchase_request_item_id':INK}]},T['sec'])
s,d=call('POST',f'/rfq/{Q}/issue',tok=T['sec']); ok('issue RFQ', s==200 and d['status']=='issued', d)
items=d['items']; TI,II=items[0]['id'],items[1]['id']
s,d=call('PUT',f'/rfq/{Q}',{**base,'items':[{'purchase_request_item_id':TON}]},T['sec']); ok('issued RFQ locked', s==409, d)

# quotations
quote=lambda name,t,i,**kw: {'supplier_name':name,'items':[{'rfq_item_id':TI,'unit_price':t},{'rfq_item_id':II,'unit_price':i}],**kw}
s,d=call('POST',f'/rfq/{Q}/quotations',{'supplier_name':'ZZ Alpha','items':[{'rfq_item_id':TI,'unit_price':3500}]},T['sec']); ok('quotation must price every line', s==422 and 'missing' in json.dumps(d), d)
s,d=call('POST',f'/rfq/{Q}/quotations',quote('ZZ Alpha',3500,450),T['sec']); ok('record quotation (new supplier)', s==201 and d['quotations_count']==1, d)
s,d=call('POST',f'/rfq/{Q}/quotations',quote('ZZ Alpha',3000,400),T['sec']); ok('one quotation per supplier', s==422, d)
s,d=call('POST',f'/rfq/{Q}/quotations',quote('ZZ Beta',3400,420),T['sec']); ok('second quotation', s==201, d)
s,d=call('POST',f'/rfq/{Q}/quotations',quote('ZZ Gamma',4200,600),T['sec']); ok('third quotation (over ABC)', s==201, d)
qs={q['supplier']['name']:q for q in d['quotations']}
ok('Gamma flagged over ABC', qs['ZZ Gamma']['over_abc'] and not qs['ZZ Beta']['over_abc'], {k:v['over_abc'] for k,v in qs.items()})
s,d=call('POST',f'/rfq/{Q}/close',tok=T['sec']); ok('cannot close before the deadline', s==409, d)
s,d=call('GET',f'/rfq/{Q}/abstract',tok=T['chair']); ok('abstract recommends lowest within ABC', s==200 and d['recommended']['supplier']['name']=='ZZ Beta' and not d['insufficient_quotations'] and len(d['rows'])==2, d.get('recommended'))
sql(f"update rfqs set deadline = now() - interval '1 hour' where id = {Q}")
s,d=call('POST',f'/rfq/{Q}/close',tok=T['sec']); ok('close after deadline', s==200 and d['status']=='closed', d)

# award
award=lambda name,**kw: {'quotation_id':qs[name]['id'],**kw}
s,d=call('POST',f'/rfq/{Q}/award',award('ZZ Beta',bac_resolution_id=R1),T['sec']); ok('secretariat cannot award', s==403, d)
s,d=call('POST',f'/rfq/{Q}/award',award('ZZ Gamma',bac_resolution_id=R1),T['chair']); ok('over-ABC quotation cannot win', s==422, d)
s,d=call('POST',f'/rfq/{Q}/award',award('ZZ Alpha',bac_resolution_id=R1),T['chair']); ok('only the lowest responsive wins', s==422 and 'lowest' in json.dumps(d), d)
s,d=call('POST',f'/rfq/{Q}/award',award('ZZ Beta'),T['chair']); ok('award needs a BAC resolution', s==422 and 'resolution' in json.dumps(d), d)
s,d=call('POST',f'/rfq/{Q}/award',award('ZZ Beta',bac_resolution_id=R2),T['chair']); ok('unapproved resolution refused', s==422, d)
s,d=call('PUT',f"/rfq/quotations/{qs['ZZ Beta']['id']}/responsive",{'is_responsive':False},T['sec']); ok('non-responsive needs a reason', s==422, d)
s,d=call('PUT',f"/rfq/quotations/{qs['ZZ Beta']['id']}/responsive",{'is_responsive':False,'remarks':'Brand not compatible'},T['sec']); ok('mark Beta non-responsive', s==200, d)
s,d=call('POST',f'/rfq/{Q}/award',award('ZZ Alpha',bac_resolution_id=R1),T['chair']); ok('next lowest (Alpha) awarded', s==200 and d['status']=='awarded' and d['awarded_supplier']['name']=='ZZ Alpha' and d['bac_resolution']['name']=='BAC-RES-2026-001', d)
s,d=call('DELETE',f"/rfq/quotations/{qs['ZZ Gamma']['id']}",tok=T['sec']); ok('quotations frozen after award', s==409, d)
s,d=call('POST',f'/rfq/{Q}/ntp',{},T['chair']); ok('NTP needs NOA first', s==409, d)
s,d=call('POST',f'/rfq/{Q}/noa',{},T['chair']); ok('NOA numbered', s==200 and d['noa_number']=='NOA-2026-0001', d)
s,d=call('POST',f'/rfq/{Q}/noa',{},T['chair']); ok('NOA only once', s==409, d)
s,d=call('POST',f'/rfq/{Q}/ntp',{'date':'2026-01-01'},T['chair']); ok('NTP cannot predate NOA', s==422, d)
s,d=call('POST',f'/rfq/{Q}/ntp',{},T['chair']); ok('NTP numbered', s==200 and d['ntp_number']=='NTP-2026-0001', d)
s,d=call('POST',f'/rfq/{Q}/cancel',{'remarks':'x'},T['sec']); ok('awarded RFQ cannot be cancelled', s==409, d)
s,d=call('DELETE',f'/bac/resolutions/{R1}',tok=T['sec']); ok('resolution backing an award cannot be deleted', s==409, d)
s,d=call('GET',f'/bac/resolutions/{R1}',tok=T['sec']); ok('resolution shows the RFQ it backs', d['used_by']==['RFQ-2026-0001'], d.get('used_by'))

# single source
pr2=approved_pr([{'general_description':'License renewal','unit':'lot','quantity':1,'estimated_unit_cost':80000,'procurement_mode':'Direct Contracting'}])
s,d=call('POST','/rfq',{'title':'License','documents_stage':'offer','procurement_method':'Direct Contracting','purchase_request_id':pr2['id'],'deadline':'2026-11-30T17:00:00','items':[{'purchase_request_item_id':pr2['items'][0]['id']}]},T['sec'])
ok('direct contracting needs a justification', s==422 and 'justification' in json.dumps(d), d)
s,d=call('POST','/rfq',{'title':'License','documents_stage':'offer','procurement_method':'Direct Contracting','purchase_request_id':pr2['id'],'deadline':'2026-11-30T17:00:00','justification':'Exclusive distributor','items':[{'purchase_request_item_id':pr2['items'][0]['id']}]},T['sec']); Q2=d['id']
call('POST',f'/rfq/{Q2}/issue',tok=T['sec'])
call('POST',f"/rfq/{Q2}/quotations",{'supplier_name':'ZZ Vendor','items':[{'rfq_item_id':d['items'][0]['id'],'unit_price':78000}]},T['sec'])
s,d=call('POST',f"/rfq/{Q2}/quotations",{'supplier_name':'ZZ Other','items':[{'rfq_item_id':d['items'][0]['id'],'unit_price':70000}]},T['sec']); ok('single source takes one quotation', s==409, d)
s,d=call('POST',f'/rfq/{Q2}/cancel',{},T['sec']); ok('cancelling an issued RFQ needs a reason', s==422, d)
s,d=call('POST',f'/rfq/{Q2}/cancel',{'remarks':'Vendor withdrew'},T['sec']); ok('cancel RFQ', s==200 and d['status']=='cancelled', d)
s,d=call('GET','/rfq/purchase-requests',tok=T['sec']); ok('cancelled RFQ frees its PR lines', any(p['id']==pr2['id'] for p in d['data']) and not any(p['id']==PR for p in d['data']), [p['pr_number'] for p in d['data']])
print('FAILURES:', fails, flush=True)