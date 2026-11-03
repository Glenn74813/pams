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
    s,d=call('POST','/purchase-requests',{'purpose':'Bidding test','priority':'medium','items':lines},SA); assert s==201,d; pid=d['id']
    call('POST',f'/purchase-requests/{pid}/submit',tok=SA); call('POST',f'/purchase-requests/{pid}/approve-step',{},SA)
    call('POST',f'/purchase-requests/{pid}/certify-funds',{},SA)
    for _ in range(3): s,d=call('POST',f'/purchase-requests/{pid}/approve-step',{},SA)
    assert d['status']=='approved',d; return d
CB='Competitive Bidding'
pr=approved_pr([{'general_description':'Laptop','unit':'unit','quantity':10,'estimated_unit_cost':60000,'procurement_mode':CB},
                {'general_description':'Chair','unit':'pc','quantity':5,'estimated_unit_cost':3000},
                {'general_description':'Pens','unit':'box','quantity':10,'estimated_unit_cost':100,'procurement_mode':'Small Value Procurement'}])
PR=pr['id']; LAP,CHR,PEN=[i['id'] for i in pr['items']]
# BAC with a held, quorate meeting
for name,des,uid in [('A Chair','chairperson',ids['chair']),('B Vice','vice_chairperson',None),('C M','member',None),('D M','member',None),('E M','member',None),('F Sec','secretariat',ids['sec'])]:
    call('POST','/bac/members',{'full_name':name,'designation':des,'user_id':uid},T['sec'])
_,m=call('POST','/bac/meetings',{'title':'Bidding award','meeting_date':'2026-10-28T09:00:00'},T['sec'])
call('POST',f"/bac/meetings/{m['id']}/attendance",{'attendees':[{'name':n,'committee_role':r,'present':True} for n,r in [('A Chair','chairperson'),('B Vice','vice_chairperson'),('C M','member')]]},T['sec'])
call('PUT',f"/bac/meetings/{m['id']}",{'status':'completed'},T['sec'])
_,res=call('POST','/bac/resolutions',{'title':'Award laptops','resolution_type':'resolution_to_award','bac_meeting_id':m['id'],'purchase_request_id':PR,'content':'Award to LCRB.'},T['sec'])
call('POST',f"/bac/resolutions/{res['id']}/submit",tok=T['sec']); s,d=call('POST',f"/bac/resolutions/{res['id']}/approve",tok=T['chair']); assert d['status']=='approved',d
RES=res['id']

# ── drafting
s,d=call('GET','/bidding/purchase-requests',tok=T['sec'])
o=[p for p in d['data'] if p['id']==PR]
ok('PR offered with only bidding lines', len(o)==1 and {l['id'] for l in o[0]['lines']}=={LAP,CHR}, d)
base={'title':'Laptops and chairs','purchase_request_id':PR}
s,d=call('POST','/bidding',{**base,'items':[{'purchase_request_item_id':PEN}]},T['sec']); ok('line planned for Shopping refused', s==422, d)
s,d=call('POST','/bidding',{**base,'items':[{'purchase_request_item_id':LAP},{'purchase_request_item_id':CHR}]},T['eu']); ok('end user cannot draft biddings', s==403, d)
s,d=call('POST','/bidding',{**base,'items':[{'purchase_request_item_id':LAP},{'purchase_request_item_id':CHR}]},T['sec'])
ok('draft bidding', s==201 and d['bidding_number']=='PB-2026-0001' and float(d['abc'])==615000, d); BID=d['id']
s,d=call('POST','/rfq',{'title':'x','documents_stage':'offer','procurement_method':'Small Value Procurement','purchase_request_id':PR,'deadline':'2026-12-01T10:00:00','items':[{'purchase_request_item_id':CHR}]},T['sec'])
ok('line on a bidding cannot go on an RFQ', s==422 and 'bidding' in json.dumps(d), d)
s,d=call('POST',f'/bidding/{BID}/invitation',{'submission_deadline':'2026-01-01T10:00:00'},T['sec']); ok('deadline must be in the future', s==422, d)
s,d=call('POST',f'/bidding/{BID}/invitation',{'itb_reference':'ITB-01','submission_deadline':'2026-12-01T10:00:00'},T['sec']); ok('post Invitation to Bid', s==200 and d['status']=='invitation', d)
s,d=call('PUT',f'/bidding/{BID}',{**base,'items':[{'purchase_request_item_id':LAP}]},T['sec']); ok('terms fixed after ITB', s==409, d)
s,d=call('POST',f'/bidding/{BID}/open-bids',tok=T['sec']); ok('cannot open bids before the deadline', s==409, d)
sql(f"update biddings set submission_deadline = now() - interval '1 hour', bid_opening_date = now() - interval '1 hour' where id = {BID}")
s,d=call('POST',f'/bidding/{BID}/open-bids',tok=T['sec']); ok('open bids', s==200 and d['status']=='bid_opening', d)

# ── bids
bid=lambda name,amt,form,sec_amt: {'supplier_name':name,'bid_amount':amt,'bid_security_form':form,'bid_security_amount':sec_amt,'bid_security_posted':True}
for args in [('ZZ A',550000,'cash',12300),('ZZ B',530000,'bid_securing_declaration',None),('ZZ C',700000,'surety_bond',30750),('ZZ D',500000,'cash',5000)]:
    s,d=call('POST',f'/bidding/{BID}/bidders',bid(*args),T['sec']); assert s==201,d
s,d=call('POST',f'/bidding/{BID}/bidders',bid('ZZ A',1,'cash',1),T['sec']); ok('one bid per supplier', s==422, d)
bd={x['supplier']['name']:x for x in d['bidders']} if s==201 else {x['supplier']['name']:x for x in call('GET',f'/bidding/{BID}',tok=T['sec'])[1]['bidders']}
ok('security sufficiency computed', bd['ZZ A']['bid_security_ok'] and bd['ZZ B']['bid_security_ok'] and not bd['ZZ D']['bid_security_ok'] and float(bd['ZZ A']['bid_security_required'])==12300, {k:(v['bid_security_ok'],v['bid_security_required']) for k,v in bd.items()})
ok('bid over ABC flagged', bd['ZZ C']['over_abc'], bd['ZZ C'])
ev=lambda name,stage,result,remarks=None: call('PUT',f"/bidding/bidders/{bd[name]['id']}",{'stage':stage,'result':result,'remarks':remarks},T['sec'])
s,d=ev('ZZ D','eligibility','passed'); ok('eligibility needs sufficient bid security', s==422 and 'security' in json.dumps(d), d)
s,d=ev('ZZ D','eligibility','failed'); ok('failing needs a reason', s==422, d)
s,d=ev('ZZ D','eligibility','failed','Bid security short'); ok('D fails eligibility', s==200 and d['status']=='evaluation', d)
s,d=ev('ZZ A','technical','passed'); ok('stages run in order', s==422, d)
for n in ['ZZ A','ZZ B','ZZ C']:
    ev(n,'eligibility','passed'); ev(n,'technical','passed')
s,d=ev('ZZ C','financial','passed'); ok('bid above ABC cannot pass financial', s==422 and 'ABC' in json.dumps(d), d)
ev('ZZ C','financial','failed','Bid exceeds ABC')
s,d=ev('ZZ A','post_qualification','passed'); ok('post-qualification waits for all financial results', s in (409,422), d)
ev('ZZ A','financial','passed'); s,d=ev('ZZ B','financial','passed')
bd={x['supplier']['name']:x for x in d['bidders']}
ok('LCB ranking: B 1st, A 2nd', bd['ZZ B']['computed_rank']==1 and bd['ZZ A']['computed_rank']==2 and d['next_post_qual_bidder_id']==bd['ZZ B']['id'], {k:v['computed_rank'] for k,v in bd.items()})
s,d=ev('ZZ A','post_qualification','passed'); ok('post-qualify the top-ranked first', s==422, d)
s,d=ev('ZZ B','post_qualification','failed','Failed site inspection'); ok('B fails post-qual; turn passes to A', s==200 and d['next_post_qual_bidder_id']==bd['ZZ A']['id'], d.get('next_post_qual_bidder_id'))
s,d=ev('ZZ A','eligibility','failed','x'); ok('earlier results final once post-qual starts', s==409, d)
s,d=ev('ZZ A','post_qualification','passed'); ok('A passes post-qual and is recommended', s==200 and d['recommended_bidder_id']==bd['ZZ A']['id'], d.get('recommended_bidder_id'))

# ── protest, award
s,d=call('POST',f'/bidding/{BID}/protests',{'protest_type':'Request for Reconsideration','supplier_id':bd['ZZ B']['supplier']['id'],'grounds':'Post-qualification was unfair'},T['sec']); ok('file request for reconsideration', s==201 and d['open_protests']==1, d)
PROT=d['protests'][0]['id']
s,d=call('POST',f'/bidding/{BID}/award',{'bidder_id':bd['ZZ A']['id'],'bac_resolution_id':RES},T['chair']); ok('award waits for pending protests', s==409 and 'protest' in json.dumps(d), d)
s,d=call('PUT',f'/bidding/protests/{PROT}',{'status':'denied'},T['sec']); ok('denial needs the resolution', s==422, d)
s,d=call('PUT',f'/bidding/protests/{PROT}',{'status':'denied','resolution':'BAC affirmed the post-qualification findings'},T['sec']); ok('deny the request', s==200 and d['open_protests']==0, d)
s,d=call('POST',f'/bidding/{BID}/award',{'bidder_id':bd['ZZ A']['id'],'bac_resolution_id':RES},T['sec']); ok('secretariat cannot award', s==403, d)
s,d=call('POST',f'/bidding/{BID}/award',{'bidder_id':bd['ZZ B']['id'],'bac_resolution_id':RES},T['chair']); ok('cannot award a bidder that failed post-qual', s==422, d)
s,d=call('POST',f'/bidding/{BID}/award',{'bidder_id':bd['ZZ A']['id']},T['chair']); ok('award needs a BAC resolution', s==422, d)
s,d=call('POST',f'/bidding/{BID}/award',{'bidder_id':bd['ZZ A']['id'],'bac_resolution_id':RES,'contract_recommendation':'Award to ZZ A'},T['chair'])
ok('award to A', s==200 and d['status']=='awarded' and d['awarded_supplier']['name']=='ZZ A' and float(d['awarded_amount'])==550000, d)
s,d=call('POST',f'/bidding/{BID}/noa',{},T['chair']); ok('NOA numbered', s==200 and d['noa_number']=='NOA-2026-0001', d)
s,d=call('POST',f'/bidding/{BID}/ntp',{},T['chair']); ok('NTP numbered', s==200 and d['ntp_number']=='NTP-2026-0001', d)
tl={i['key']:i for i in d['timeline']}
ok('timeline computed', 'opening_to_noa' in tl and 'itb_to_deadline' in tl, d['timeline'])
s,d=call('GET',f"/bac/resolutions/{RES}",tok=T['sec']); ok('resolution shows the bidding it backs', d['used_by']==['PB-2026-0001'], d.get('used_by'))

# ── failed biddings
pr2=approved_pr([{'general_description':'Generator','unit':'unit','quantity':1,'estimated_unit_cost':900000,'procurement_mode':CB}])
def run_to_opening(title):
    s,d=call('POST','/bidding',{'title':title,'purchase_request_id':pr2['id'],'items':[{'purchase_request_item_id':pr2['items'][0]['id']}]},T['sec']); assert s==201,d; b=d['id']
    s,d=call('POST',f'/bidding/{b}/invitation',{'submission_deadline':'2026-12-01T10:00:00'},T['sec']); assert s==200,d
    sql(f"update biddings set submission_deadline = now() - interval '1 hour', bid_opening_date = now() - interval '1 hour' where id = {b}")
    call('POST',f'/bidding/{b}/open-bids',tok=T['sec']); return b
b1=run_to_opening('Generator')
s,d=call('POST',f'/bidding/{b1}/declare-failed',{},T['sec']); ok('failure needs a ground', s==422, d)
s,d=call('POST',f'/bidding/{b1}/declare-failed',{'reason':'No bids received'},T['sec']); ok('first failed bidding', s==200 and d['bidding']['failed_count']==1 and not d['may_negotiate'], d)
b2=run_to_opening('Generator (re-bid)')
s,d=call('POST',f'/bidding/{b2}/declare-failed',{'reason':'All bids over ABC'},T['sec']); ok('second failure allows negotiated procurement', d['bidding']['failed_count']==2 and d['may_negotiate'], d)
s,d=call('GET','/bidding/purchase-requests',tok=T['sec']); ok('failed biddings free the PR lines', any(p['id']==pr2['id'] and p['failed_biddings']==2 for p in d['data']), d)

print('MEARB is covered by gaps_test')
