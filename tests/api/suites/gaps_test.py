import json, urllib.request, urllib.error, datetime, os, sys, uuid
from pams_db import sql
B=os.environ['PAMS_API']; RUN=uuid.uuid4().hex[:6]
def call(m,p,body=None,tok=None):
    h={'Content-Type':'application/json','Accept':'application/json'}
    if tok: h['Authorization']='Bearer '+tok
    r=urllib.request.Request(B+p,data=json.dumps(body).encode() if body is not None else None,headers=h,method=m)
    try:
        with urllib.request.urlopen(r) as x: return x.status,json.loads(x.read() or b'{}')
    except urllib.error.HTTPError as e:
        try: return e.code,json.loads(e.read() or b'{}')
        except ValueError: return e.code,{}
fails=0
def ok(l,c,x=''):
    global fails
    if not c: fails+=1
    print(('PASS ' if c else 'FAIL ')+l,'' if c else str(x)[:400],flush=True)
today=datetime.date.today(); iso=lambda n=0:(today+datetime.timedelta(days=n)).isoformat()
SVP='Small Value Procurement'; NP='Negotiated Procurement'; CB='Competitive Bidding'; UO='Unsolicited Offer with Bid Matching'; PS='Procurement from PS-DBM'
PLAT='PhilGEPS Certificate of Registration (Platinum)'
SA=call('POST','/auth/login',{'email':'superadmin@inventory.com','password':'admin123'})[1]['access_token']
def api(m,p,b=None,code=None):
    s,d=call(m,p,b,SA)
    if code: assert s==code,(m,p,s,d)
    return s,d
def pr(lines):
    d=api('POST','/purchase-requests',{'purpose':f'ZZ gaps {RUN}','priority':'medium','items':lines},201)[1]; i=d['id']
    api('POST',f'/purchase-requests/{i}/submit'); api('POST',f'/purchase-requests/{i}/approve-step',{}); api('POST',f'/purchase-requests/{i}/certify-funds',{})
    for _ in range(3): s,d=api('POST',f'/purchase-requests/{i}/approve-step',{})
    assert d['status']=='approved',d; return d
n=[0]
def supplier(kind='business',docs=(),eligible=False):
    n[0]+=1
    d=api('POST','/suppliers',{'name':f'ZZ Sup {RUN} {n[0]}','supplier_kind':kind},201)[1]
    for t in docs: api('POST',f"/suppliers/{d['id']}/compliance",{'document_type':t,'reference_number':'R-1','expiry_date':iso(200)},201)
    if eligible: api('POST',f"/suppliers/{d['id']}/eligibility",{'eligible':True},200)
    return d['id']
def line(desc,cost,mode,qty=1,cat=None):
    l={'general_description':f'{desc} {RUN}','unit':'lot','quantity':qty,'estimated_unit_cost':cost,'procurement_mode':mode}
    if cat: l['procurement_category']=cat
    return l
members=call('GET','/bac/members',None,SA)[1]
if not (members.get('data') if isinstance(members,dict) else members):
    me=call('GET','/auth/me',None,SA)[1]; me=me.get('user',me)
    for name,des,uid in [('A Chair','chairperson',me.get('id')),('B Vice','vice_chairperson',None),('C M','member',None),('D M','member',None),('E M','member',None),('F Sec','secretariat',None)]:
        print('member',call('POST','/bac/members',{'full_name':name,'designation':des,'user_id':uid},SA)[0])
def resolution(prid):
    m=api('POST','/bac/meetings',{'title':f'ZZ {RUN}','meeting_date':iso(0)+'T09:00:00'},201)[1]
    api('POST',f"/bac/meetings/{m['id']}/attendance",{'attendees':[{'name':x,'committee_role':r,'present':True} for x,r in [('A','chairperson'),('B','vice_chairperson'),('C','member')]]})
    api('PUT',f"/bac/meetings/{m['id']}",{'status':'completed'})
    r=api('POST','/bac/resolutions',{'title':f'Award {RUN}','resolution_type':'resolution_to_award','bac_meeting_id':m['id'],'purchase_request_id':prid,'content':'Award.'},201)[1]
    api('POST',f"/bac/resolutions/{r['id']}/submit"); d=api('POST',f"/bac/resolutions/{r['id']}/approve")[1]; assert d['status']=='approved',d
    return r['id']
def rfq(p,method,cost_ids,ground=None,stage='offer',extra=None,code=None):
    body={'title':f'ZZ {RUN}','documents_stage':'offer','procurement_method':method,'purchase_request_id':p,'deadline':iso(10)+'T10:00:00','justification':'Grounds recorded.','documents_stage':stage,'negotiated_ground':ground,'items':[{'purchase_request_item_id':i} for i in cost_ids]}
    body.update(extra or {})
    return call('POST','/rfq',body,SA)
def to_award(rid):
    api('POST',f'/rfq/{rid}/issue',None,200)
    return rid
def quote(rid,sup,price):
    d=api('GET',f'/rfq/{rid}')[1]
    return call('POST',f'/rfq/{rid}/quotations',{'supplier_id':sup,'submitted_at':iso(0),'items':[{'rfq_item_id':i['id'],'unit_price':price} for i in d['items']]},SA)
def close(rid):
    sql(f"update rfqs set deadline = now() - interval '1 hour' where id = {rid}")
    return api('POST',f'/rfq/{rid}/close',None,200)

print('--- eligibility by mode')
p=pr([line('Paper',40000,SVP),line('Chairs',80000,SVP),line('Big',600000,SVP),line('Direct',40000,'Direct Acquisition')])
L=[i['id'] for i in p['items']]; R=resolution(p['id'])
s,d=rfq(p['id'],SVP,[L[0]],stage=None); assert s==201,d; r0=d['id']
s,d=call('POST',f'/rfq/{r0}/issue',None,SA); ok('an SVP RFQ states when documents come in before it is issued',s==422 and 'documents' in json.dumps(d),d)
call('POST',f'/rfq/{r0}/cancel',{},SA)
s,d=rfq(p['id'],SVP,[L[0]]); assert s==201,d; r1=d['id']; to_award(r1)
ok('the RFQ lists the documents for its mode and amount',d['required_documents']==['PhilGEPS Organization ID',"Mayor's or Business Permit"],d['required_documents'])
bare=supplier(); s,d=quote(r1,bare,30000); assert s==201,d
q=d['quotations'][0]; ok('a supplier with nothing on file shows what is missing',not q['supplier_eligible'] and any('missing PhilGEPS' in x for x in q['eligibility_problems']),q)
close(r1)
s,d=call('POST',f'/rfq/{r1}/award',{'quotation_id':q['id'],'bac_resolution_id':R,'remarks':'one quote'},SA); ok('award refused for missing documents',s==422 and 'missing' in json.dumps(d),d)
api('POST',f'/suppliers/{bare}/compliance',{'document_type':'PhilGEPS Organization ID','reference_number':'X','expiry_date':iso(100)},201)
api('POST',f'/suppliers/{bare}/compliance',{'document_type':"Mayor's Permit",'reference_number':'X','expiry_date':iso(-1)},201)
s,d=api('GET',f'/rfq/{r1}'); ok('an expired permit is named',any('expired Mayor' in x for x in d['quotations'][0]['eligibility_problems']),d['quotations'][0])
api('POST',f'/suppliers/{bare}/compliance',{'document_type':"Mayor's Permit renewal receipt",'reference_number':'OR-1'},201)
s,d=api('GET',f'/rfq/{r1}'); q=d['quotations'][0]; ok('a renewal receipt covers the expired permit (4.3.2 a)',q['supplier_eligible'],q)
s,d=call('POST',f'/rfq/{r1}/award',{'quotation_id':q['id'],'bac_resolution_id':R,'remarks':'one quote'},SA); ok('awarded on the circular list, without being marked eligible or holding a tax clearance',s==200 and d['status']=='awarded',d)
s,d=rfq(p['id'],SVP,[L[1]]); r2=d['id']; ok('over P50k adds the OSS',('Omnibus Sworn Statement' in d['required_documents']),d['required_documents'])
s,d=rfq(p['id'],SVP,[L[2]]); ok('over P500k adds the tax return',s==201 and 'Income or Business Tax Return' in d['required_documents'],d)
s,d=rfq(p['id'],'Direct Acquisition',[L[3]],stage=None); ok('Direct Acquisition asks for no documents',s==201 and d['required_documents']==[],d); r3=d['id']
to_award(r3); nod=supplier(); s,d=quote(r3,nod,30000); ok('and needs none on file',d['quotations'][0]['supplier_eligible'],d)
plat=supplier(docs=[PLAT]); s,d=api('GET',f'/suppliers/{plat}')
row={x['mode']:x for x in d['by_mode']}
ok('Platinum stands in for the ID and permit (4.3.2 b)',row['Direct Contracting']['missing']==['missing Income or Business Tax Return'],row['Direct Contracting'])
ok('competitive selection ready with Platinum',row['Competitive selection']['missing']==[],row['Competitive selection'])
s,d=call('POST',f'/suppliers/{plat}/eligibility',{'eligible':True},SA); ok('verified on Platinum alone, not five documents',s==200 and d['is_eligible'],d)
s,d=call('POST',f'/suppliers/{nod}/eligibility',{'eligible':True},SA); ok('not verified without Platinum',s==409,d)
ind=supplier('individual',docs=['PhilGEPS Organization ID','BIR Registration','Curriculum Vitae']); row={x['mode']:x for x in api('GET',f'/suppliers/{ind}')[1]['by_mode']}
ok('an individual consultant shows BIR registration instead of a permit (4.5)',row['Negotiated: highly technical consultants']['missing']==[],row)
gov=supplier('government'); row={x['mode']:x for x in api('GET',f'/suppliers/{gov}')[1]['by_mode']}
ok('a government lessor needs nothing',row['Negotiated: lease of real property or venue']['required']==[],row)
art=supplier('artist'); row={x['mode']:x for x in api('GET',f'/suppliers/{art}')[1]['by_mode']}
ok('an artist not in business needs nothing (4.6)',row['Negotiated: scientific, artistic, exclusive technology, media']['required']==[],row)
s,d=call('PUT',f'/suppliers/{art}',{'name':f'ZZ Art {RUN}','supplier_kind':'alien'},SA); ok('unknown kind refused',s==422,d)

print('--- failed Limited Source Bidding and Competitive Dialogue')
p=pr([line('LSB',100000,'Limited Source Bidding')]); l=p['items'][0]['id']; P=p['id']
s,d=rfq(P,NP,[l],'two_failed_biddings',extra={'invited_suppliers':['A','B','C']}); ok('no failures yet, no negotiation',s==422,d)
s,d=rfq(P,'Limited Source Bidding',[l],stage=None); assert s==201,d; a=d['id']; to_award(a)
s,d=call('POST',f'/rfq/{a}/declare-failed',{},SA); ok('the ground is needed',s==422,d)
s,d=call('POST',f'/rfq/{a}/declare-failed',{'remarks':'No bids'},SA); ok('LSB declared failed',s==200 and d['status']=='failed',d)
s,d=call('POST',f'/rfq/{a}/cancel',{'remarks':'x'},SA); ok('a failed RFQ cannot be cancelled',s==409,d)
s,d=rfq(P,NP,[l],'two_failed_biddings',extra={'invited_suppliers':['A','B','C']}); ok('one failure is not enough',s==422 and '1 failed' in json.dumps(d),d)
s,d=rfq(P,'Limited Source Bidding',[l],stage=None); ok('failing frees the line for a re-bid',s==201,d); b=d['id']; to_award(b)
call('POST',f'/rfq/{b}/declare-failed',{'remarks':'None responsive'},SA)
s,d=rfq(P,NP,[l],'two_failed_biddings',extra={'invited_suppliers':['A','B','C'],'items':[{'purchase_request_item_id':l,'abc_unit_cost':100000}]}); ok('two failed LSBs open negotiation',s==201,d)
if s==201:
    ok('Platinum needed unless the BAC revised',d['required_documents']==[PLAT],d['required_documents']); nid=d['id']
    s,d2=call('PUT',f'/rfq/{nid}',{'title':'x','documents_stage':'offer','procurement_method':NP,'purchase_request_id':P,'deadline':iso(10)+'T10:00:00','negotiated_ground':'two_failed_biddings','invited_suppliers':['A','B','C'],'eligibility_revised':True,'documents_stage':'offer','items':[{'purchase_request_item_id':l}]},SA)
    ok('revised requirements are the BAC\'s to set',s==200 and d2['required_documents']==[],d2)
sc=pr([line('Svc',100000,SVP)]); s,d=rfq(sc['id'],SVP,[sc['items'][0]['id']]); to_award(d['id'])
s,d=call('POST',f"/rfq/{d['id']}/declare-failed",{'remarks':'x'},SA); ok('other modes are cancelled, not failed',s==409,d)

print('--- sealed prices')
def bidding(method='mearb',mode=CB,cost=1000000):
    p=pr([line('Item',cost,mode)])
    body={'title':f'ZZ {RUN}','purchase_request_id':p['id'],'items':[{'purchase_request_item_id':p['items'][0]['id']}],'evaluation_method':method}
    if method=='mearb': body.update(price_weight=40,technical_weight=60,min_quality_score=70)
    if method=='marb': body.update(price_weight=0,technical_weight=100)
    d=api('POST','/bidding',body,201)[1]; return p,d
def post(b,rated=True):
    if rated: api('PUT',f"/bidding/{b}/criteria",{'criteria':[{'name':'Quality','weight':100}]},200)
    sql(f"update biddings set pre_proc_date = now() where id = {b}")
    return call('POST',f'/bidding/{b}/invitation',{'itb_reference':'I','submission_deadline':iso(9)+'T10:00:00'},SA)
def opened(b):
    sql(f"update biddings set submission_deadline = now() - interval '1 hour', bid_opening_date = now() - interval '1 hour' where id = {b}")
    return api('POST',f'/bidding/{b}/open-bids',None,200)
ready=[EL for EL in [PLAT,'Income or Business Tax Return']]
p,d=bidding(); BID=d['id']; s,x=post(BID); assert s==200,x; opened(BID)
A,C,E=[supplier(docs=ready,eligible=True) for _ in range(3)]
s,d=call('POST',f'/bidding/{BID}/bidders',{'supplier_id':A,'bid_amount':900000,'bid_security_form':'bid_securing_declaration','bid_security_posted':True},SA); ok('no price at opening under MEARB',s==422 and 'sealed' in json.dumps(d),d)
for x in (A,C,E): s,d=call('POST',f'/bidding/{BID}/bidders',{'supplier_id':x,'bid_security_form':'bid_securing_declaration','bid_security_posted':True},SA); assert s==201,d
ok('shown as sealed',d['sealed'] and d['can_open_financial'],d)
bd={x['supplier']['id']:x for x in d['bidders']}; crit=d['criteria'][0]['id']
s,d=call('PUT',f"/bidding/bidders/{bd[A]['id']}",{'bid_amount':900000},SA); ok('price refused while sealed',s==422,d)
s,d=call('POST',f'/bidding/{BID}/open-financial',None,SA); ok('cannot open before technical evaluation',s==409,d)
for x,score in ((A,90),(C,80),(E,50)):
    for st in ('eligibility','technical'): s,d=call('PUT',f"/bidding/bidders/{bd[x]['id']}",{'stage':st,'result':'passed'},SA); assert s==200,d
s,d=call('PUT',f"/bidding/bidders/{bd[A]['id']}",{'stage':'financial','result':'passed'},SA); ok('no financial result while sealed',s==409,d)
s,d=call('POST',f'/bidding/{BID}/open-financial',None,SA); ok('every bid is scored first',s==409 and 'Score' in json.dumps(d),d)
for x,score in ((A,90),(C,80),(E,50)): s,d=call('PUT',f"/bidding/bidders/{bd[x]['id']}/scores",{'scores':[{'bidding_criterion_id':crit,'score':score}]},SA); assert s==200,d
s,d=call('POST',f'/bidding/{BID}/open-financial',None,SA); ok('financial proposals opened',s==200 and not d['sealed'],d)
res={x['supplier']['id']:x for x in d['bidders']}
ok('below the minimum stays unopened',res[E]['results']['technical']=='failed' and 'not opened' in res[E]['remarks'],res[E])
s,d=call('PUT',f"/bidding/bidders/{bd[E]['id']}",{'bid_amount':100},SA); ok('its price cannot be entered',s==409,d)
s,d=call('PUT',f"/bidding/bidders/{bd[A]['id']}/scores",{'scores':[{'bidding_criterion_id':crit,'score':99}]},SA); ok('scores are final once opened',s==409,d)
s,d=call('PUT',f"/bidding/bidders/{bd[A]['id']}",{'stage':'technical','result':'failed','remarks':'x'},SA); ok('technical results are final once opened',s==409,d)
for x,price in ((A,950000),(C,800000)):
    s,d=call('PUT',f"/bidding/bidders/{bd[x]['id']}",{'bid_amount':price},SA); assert s==200,d
    s,d=call('PUT',f"/bidding/bidders/{bd[x]['id']}",{'stage':'financial','result':'passed'},SA); assert s==200,d
res={x['supplier']['id']:x for x in d['bidders']}
ok('ranked on the combined score',res[A]['computed_rank'] is not None and res[A]['mearb_score'] is not None,res[A])
p,d=bidding('marb'); M=d['id']; s,x=post(M); assert s==200,x; opened(M)
for x in (A,C): s,d=call('POST',f'/bidding/{M}/bidders',{'supplier_id':x,'bid_security_form':'bid_securing_declaration','bid_security_posted':True},SA); assert s==201,d
bd={x['supplier']['id']:x for x in d['bidders']}; crit=d['criteria'][0]['id']
for x,score in ((A,90),(C,80)):
    for st in ('eligibility','technical'): call('PUT',f"/bidding/bidders/{bd[x]['id']}",{'stage':st,'result':'passed'},SA)
    call('PUT',f"/bidding/bidders/{bd[x]['id']}/scores",{'scores':[{'bidding_criterion_id':crit,'score':score}]},SA)
s,d=call('POST',f'/bidding/{M}/open-financial',None,SA); res={x['supplier']['id']:x for x in d['bidders']}
ok('MARB opens only the highest rating',res[C]['results']['financial']=='failed' and res[A]['results']['financial']=='pending',res)
p,d=bidding('price'); s,x=post(d['id'],False); assert s==200,x; opened(d['id'])
s,d=call('POST',f"/bidding/{d['id']}/bidders",{'supplier_id':A,'bid_amount':900000,'bid_security_form':'bid_securing_declaration','bid_security_posted':True},SA); ok('lowest-price bids still read the price at opening',s==201 and d['bidders'][0]['bid_amount'] is not None,d)

print('--- partly insufficient PS-DBM stock')
p=pr([line('Paper',100,PS,qty=20)]); l=p['items'][0]['id']
o=api('POST','/ps-dbm/orders',{'purchase_request_id':p['id'],'items':[{'purchase_request_item_id':l,'quantity':20,'unit_price':90}]},201)[1]
o=api('POST',f"/ps-dbm/orders/{o['id']}/submit",{'vs_reference':f'VS-{RUN}'},200)[1]; li=o['items'][0]['id']
s,d=call('POST',f"/ps-dbm/orders/{o['id']}/items/{li}/not-available",{'release_ground':'zero_inventory','cnas_number':'C','available_quantity':5},SA); ok('only the stock ground keeps part',s==422,d)
s,d=call('POST',f"/ps-dbm/orders/{o['id']}/items/{li}/not-available",{'release_ground':'insufficient_stock','cnas_number':'SPR-1','available_quantity':20},SA); ok('must be short of the order',s==422,d)
s,d=call('POST',f"/ps-dbm/orders/{o['id']}/items/{li}/not-available",{'release_ground':'insufficient_stock','cnas_number':'SPR-1','available_quantity':12},SA)
ok('the line splits',s==200 and sorted((x['quantity'],x['availability']) for x in d['items'])==[(8,'not_available'),(12,'available')] and float(d['total_amount'])==1080,d)
s,d=rfq(p['id'],SVP,[l]); ok('only the shortfall goes elsewhere',s==201 and d['items'][0]['quantity']==8,d)

print('--- unsolicited offer')
s,d=rfq(pr([line('X',100,UO)])['id'],UO,[1]); ok('no longer an RFQ method',s==422 and 'Unsolicited' in json.dumps(d),d)
def offer(sup,received=None,cat='Goods'):
    return call('POST','/unsolicited-offers',{'supplier_id':sup,'title':f'ZZ {RUN}','concept':'New sensor','category':cat,'proposed_amount':900000,'received_date':received or iso(0)},SA)
O=supplier(docs=ready,eligible=True)
s,d=offer(O,cat='Infrastructure Projects'); ok('goods and consulting only',s==422,d)
s,d=offer(O,iso(-25)); assert s==201,d; ok('numbered and due in 20 days',d['offer_number'].startswith('UO-') and d['overdue'],d)
s,d=call('POST',f"/unsolicited-offers/{d['id']}/assess",{'needed':True},SA); ok('past 20 days it is deemed rejected',s==200 and d['status']=='rejected' and 'Deemed' in d['closing_reason'],d)
s,d=offer(O); i=d['id']; u=lambda st,b=None:call('POST',f'/unsolicited-offers/{i}/{st}',b if b is not None else {},SA)
s,d=u('assess',{'needed':True}); ok('notified',d['status']=='awaiting_documents',d)
s,d=u('documents',{'concept_details':True,'proof_of_concept':True,'eligibility_documents':True}); ok('all four sets are needed',s==422,d)
s,d=u('documents',{'concept_details':True,'proof_of_concept':True,'eligibility_documents':True,'terms_and_consent':True}); ok('evaluation starts',d['status']=='evaluation',d)
s,d=u('evaluate',{'favorable':True,'funds_available':True,'eligibility_verified':True}); ok('the new concept basis is needed',s==422,d)
s,d=u('evaluate',{'favorable':True,'new_concept_basis':'substantial_improvement','funds_available':True,'eligibility_verified':True,'negotiation_start':iso(6)}); ok('negotiation within 5 days',s==422,d)
s,d=u('evaluate',{'favorable':True,'new_concept_basis':'substantial_improvement','funds_available':True,'eligibility_verified':True}); ok('favorable',d['status']=='negotiation',d)
s,d=u('negotiation',{'successful':True,'negotiated_amount':850000}); ok('the certification is needed',s==422,d)
s,d=u('negotiation',{'successful':True,'negotiated_amount':850000,'certification_number':'CERT-1'}); ok('original offeror',d['status']=='original_offeror',d)
p,b=bidding('price',UO,900000); BB=b['id']
s,d=post(BB,False); ok('lines planned as an offer wait for the link',s==409 and 'Unsolicited' in json.dumps(d),d)
s,d=u('link-bidding',{'bidding_id':BB}); ok('bid security comes first',s==409,d)
s,d=u('bid-security',{'form':'cash','amount':100}); ok('security is checked against the price',s==422 and '17,000' in json.dumps(d),d)
s,d=u('bid-security',{'form':'bid_securing_declaration'}); ok('security recorded',s==200 and d['bid_security_date'],d)
mear=bidding()[1]['id']; s,d=u('link-bidding',{'bidding_id':mear}); ok('only a lowest-price bidding',s==422,d)
s,d=u('link-bidding',{'bidding_id':BB}); ok('linked',d['status']=='comparative_bidding',d)
s,d=post(BB,False); assert s==200,d; opened(BB)
RES=resolution(p['id'])
L1,L2=[supplier(docs=ready,eligible=True) for _ in range(2)]
for x,a in ((L1,700000),(L2,750000)): s,d=call('POST',f'/bidding/{BB}/bidders',{'supplier_id':x,'bid_amount':a,'bid_security_form':'bid_securing_declaration','bid_security_posted':True},SA); assert s==201,d
bd={x['supplier']['id']:x for x in d['bidders']}
for x in (L1,L2):
    for st in ('eligibility','technical','financial'): s,d=call('PUT',f"/bidding/bidders/{bd[x]['id']}",{'stage':st,'result':'passed'},SA); assert s==200,d
s,d=u('start-matching'); ok('needs a post-qualified lowest bid',s==409,d)
s,d=call('PUT',f"/bidding/bidders/{bd[L1]['id']}",{'stage':'post_qualification','result':'passed'},SA); assert s==200,d
s,d=call('POST',f'/bidding/{BB}/award',{'bidder_id':bd[L1]['id'],'bac_resolution_id':RES},SA); ok('no award before bid matching',s==409,d)
s,d=u('start-matching'); ok('lowest bid taken',d['status']=='bid_matching' and float(d['lccrb_amount'])==700000 and d['due_date']==iso(15),d)
s,d=call('POST',f'/bidding/{BB}/award',{'bidder_id':bd[L1]['id'],'bac_resolution_id':RES},SA); ok('still waiting for the answer',s==409 and '15' in json.dumps(d),d)
s,d=u('match',{'matched':True,'amount':710000}); ok('must equal or beat the lowest',s==422,d)
s,d=u('match',{'matched':True,'amount':700000}); ok('matched',d['match_result']=='matched',d)
s,d=api('GET',f'/bidding/{BB}'); ok('the bidding shows the original offeror for award',d['award_to_original_offeror'] and d['can_award'] and d['original_offeror']['id']==O,d)
s,d=call('POST',f'/bidding/{BB}/award',{'bidder_id':None,'bac_resolution_id':RES},SA); ok('awarded to the original offeror at the matched price',s==200 and d['awarded_supplier']['id']==O and float(d['awarded_amount'])==700000,d)
s,d=call('GET',f'/unsolicited-offers/{i}',None,SA); ok('offer closed as won',d['status']=='awarded_original',d)
s,d=call('POST',f'/bidding/{BB}/noa',{},SA); ok('NOA follows as usual',s==200 and d['noa_number'],d)
def to_bidding(sup):
    s,d=offer(sup); j=d['id']; f=lambda st,b=None:call('POST',f'/unsolicited-offers/{j}/{st}',b if b is not None else {},SA)
    f('assess',{'needed':True}); f('documents',{'concept_details':True,'proof_of_concept':True,'eligibility_documents':True,'terms_and_consent':True})
    f('evaluate',{'favorable':True,'new_concept_basis':'state_of_the_art','funds_available':True,'eligibility_verified':True})
    f('negotiation',{'successful':True,'negotiated_amount':850000,'certification_number':'C2'}); f('bid-security',{'form':'bid_securing_declaration'})
    p,b=bidding('price',UO,900000); s,d=f('link-bidding',{'bidding_id':b['id']}); assert s==200,d
    s,d=post(b['id'],False); assert s==200,d; opened(b['id']); return j,f,p,b['id']
O2=supplier(docs=ready,eligible=True); j,f,p2,B2=to_bidding(O2)
s,d=f('start-matching'); ok('no comparative bid: original offeror wins',d['match_result']=='no_comparative',d)
R2=resolution(p2['id']); s,d=call('POST',f'/bidding/{B2}/award',{'bidder_id':None,'bac_resolution_id':R2},SA); ok('awarded at the negotiated price',s==200 and float(d['awarded_amount'])==850000,d)
O3=supplier(docs=ready,eligible=True); j,f,p3,B3=to_bidding(O3)
s,d=call('POST',f'/bidding/{B3}/bidders',{'supplier_id':L1,'bid_amount':700000,'bid_security_form':'bid_securing_declaration','bid_security_posted':True},SA); x=d['bidders'][0]['id']
for st in ('eligibility','technical','financial','post_qualification'): s,d=call('PUT',f'/bidding/bidders/{x}',{'stage':st,'result':'passed'},SA); assert s==200,d
f('start-matching'); s,d=f('match',{'matched':False}); ok('not matched recorded',d['match_result']=='not_matched',d)
R3=resolution(p3['id']); s,d=call('POST',f'/bidding/{B3}/award',{'bidder_id':x,'bac_resolution_id':R3},SA); ok('the lowest bid wins',s==200 and d['awarded_supplier']['id']==L1,d)
ok('offer closed as lost',call('GET',f'/unsolicited-offers/{j}',None,SA)[1]['status']=='awarded_comparative')
W=supplier(docs=ready,eligible=True)
s,d=offer(W); w=d['id']; call('POST',f'/unsolicited-offers/{w}/assess',{'needed':True},SA)
s,d=call('POST',f'/unsolicited-offers/{w}/withdraw',{},SA); ok('withdrawal within 5 days costs nothing',d['status']=='withdrawn' and not d['late_withdrawal'],d)
ends=[]
for k in range(3):
    s,d=offer(W); w=d['id']; call('POST',f'/unsolicited-offers/{w}/assess',{'needed':True},SA)
    sql(f"update unsolicited_offers set notice_date = cast('{iso(-10)}' as date) where id = {w}")
    s,d=call('POST',f'/unsolicited-offers/{w}/withdraw',{},SA); ok(f'late withdrawal {k+1} bars the offeror',s==200 and d['late_withdrawal'],d); ends.append(d['closing_reason'])
ok('1 year, 2 years, then for good',str(today.year+1) in ends[0] and str(today.year+2) in ends[1] and 'permanently' in ends[2],ends)
s,d=api('GET',f'/suppliers/{W}'); ok('the supplier is sanctioned',d['is_sanctioned'] and not d['is_eligible'],d['sanction'])
s,d=call('GET','/unsolicited-offers?search='+RUN,None,SA); ok('list and search',s==200 and d['pagination']['total']>=6,d)

print('--- valid permit before payment (4.3.2 a)')
p=pr([line('Folders',30000,SVP)]); R=resolution(p['id'])
s,d=rfq(p['id'],SVP,[p['items'][0]['id']]); rid=d['id']; to_award(rid)
ren=supplier(docs=['PhilGEPS Organization ID'])
api('POST',f'/suppliers/{ren}/compliance',{'document_type':"Mayor's Permit",'reference_number':'MP-OLD','expiry_date':iso(-10)},201)
api('POST',f'/suppliers/{ren}/compliance',{'document_type':"Mayor's Permit renewal receipt",'reference_number':'OR-9'},201)
s,d=quote(rid,ren,25000); assert s==201,d; q=d['quotations'][0]['id']; close(rid)
s,d=call('POST',f'/rfq/{rid}/award',{'quotation_id':q,'bac_resolution_id':R,'remarks':'one quote'},SA); ok('awarded on the renewal receipt',s==200,d)
api('POST',f'/rfq/{rid}/noa',{},200)
s,po=call('POST','/procurement/purchase-orders',{'source_type':'rfq','source_id':rid,'delivery_date':iso(10)},SA); assert s==201,po
api('POST',f"/procurement/purchase-orders/{po['id']}/send",None,200)
s,sc=call('POST','/payments/tranches',{'type':'purchase_order','id':po['id'],'description':'Full payment upon delivery','amount':float(po['total_amount'])},SA); assert s in (200,201),sc
tid=sc['tranches'][0]['id']
s,d=call('POST',f'/payments/tranches/{tid}/for-payment',{},SA); assert s==200,d
s,d=call('POST',f'/payments/tranches/{tid}/pay',{'payment_method':'check','payment_reference':f'CHK-{RUN}','amount_paid':float(po['total_amount'])},SA)
ok('payment waits for the valid permit',s==409 and 'Permit' in json.dumps(d),d)
api('POST',f'/suppliers/{ren}/compliance',{'document_type':"Mayor's Permit",'reference_number':'MP-NEW','expiry_date':iso(300)},201)
s,d=call('POST',f'/payments/tranches/{tid}/pay',{'payment_method':'check','payment_reference':f'CHK-{RUN}','amount_paid':float(po['total_amount'])},SA)
ok('paid once the valid permit is on file',s==200,d)
print('DONE fails=%d'%fails)
