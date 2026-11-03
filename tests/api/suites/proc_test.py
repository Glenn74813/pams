from pams_db import sql
import json, sys, urllib.request, urllib.error, time
B=__import__('os').environ['PAMS_API']
def call(m, p, body=None, tok=None):
    h={'Content-Type':'application/json','Accept':'application/json'}
    if tok: h['Authorization']='Bearer '+tok
    r=urllib.request.Request(B+p, data=json.dumps(body).encode() if body is not None else None, headers=h, method=m)
    try:
        with urllib.request.urlopen(r) as x: return x.status, json.loads(x.read() or b'{}')
    except urllib.error.HTTPError as e: return e.code, json.loads(e.read() or b'{}')
def login(e,pw):
    for _ in range(3):
        s,d=call('POST','/auth/login',{'email':e,'password':pw})
        if s==200: return d['access_token']
        time.sleep(13)
    raise SystemExit((e,s,d))
fails=0
def ok(label, cond, extra=''):
    global fails
    if not cond: fails+=1
    print(('PASS ' if cond else 'FAIL ')+label, '' if cond else extra)
SA=login('superadmin@inventory.com','admin123')
# A second office for the cross-department checks; the copy of the database may have only one.
D2=sql('select min(id) from departments where id > (select min(id) from departments)')
D2=int(D2) if D2 else call('POST','/departments',{'name':'ZZ Second Office','code':'ZZ2ND'},SA)[1]['id']
_,role=call('POST','/roles',{'name':'ZZ Dept Planner','permissions':['create_ppmp']},SA)
ids={}
spec=[('pl',3,1),('dh',7,1),('bo',8,1),('adm',2,1),('acc',9,1),('bac',11,1),('eu',15,1),('eu2',15,D2),('dp',role.get('id') or role['data']['id'],D2)]
for key,rid,dept in spec:
    s,d=call('POST','/users',{'name':'zz '+key,'email':f'zz.{key}@test.local','password':'Zz#Test2026','password_confirmation':'Zz#Test2026','role_id':rid,'department_id':dept},SA); assert s==201,d; ids[key]=d['user']['id']
T={}
for k,_,_ in spec:
    T[k]=login(f'zz.{k}@test.local','Zz#Test2026')
FY=2027
line=lambda desc,q,c,mode='Small Value Procurement',sched='Q1',**kw: dict(general_description=desc,unit='pc',quantity=q,estimated_unit_cost=c,procurement_mode=mode,schedule=sched,fund_source='General Appropriations Act (GAA)',**kw)

# ── PPMP
s,d=call('GET','/procurement-planning/options',tok=T['eu']); ok('options load', s==200 and 'Small Value Procurement' in d['modes'] and float(d['ceilings']['Small Value Procurement'])==2000000, d)
s,d=call('POST','/procurement-planning/ppmps',{'fiscal_year':FY,'title':'x','items':[line('Server',1,2500000)]},T['pl']); ok('ceiling enforced on PPMP', s==422 and 'ceiling' in json.dumps(d), d)
s,d=call('POST','/procurement-planning/ppmps',{'fiscal_year':FY,'title':'x','items':[line('Pen',1,5,sched='March')]},T['pl']); ok('schedule must be a quarter', s==422, d)
s,d=call('POST','/procurement-planning/ppmps',{'fiscal_year':FY,'title':'IT PPMP','items':[line('Laptop',10,50000,code='5060405003'),line('Bond paper',100,250,sched='Q2')]},T['pl'])
ok('planner creates PPMP', s==201 and d['ppmp_number']==f'PPMP-{FY}-0001' and float(d['total_estimated_budget'])==525000 and d['items'][0]['code']=='5060405003', d); P1=d['id']
s,d=call('POST','/procurement-planning/ppmps',{'department_id':1,'fiscal_year':FY,'title':'Finance PPMP','items':[line('Calculator',5,1200)]},T['dp'])
ok('department planner is pinned to own dept', s==201 and d['department']['id']==D2, d); P2=d['id']
s,d=call('GET','/procurement-planning/ppmps',tok=T['dp']); ok('dept planner sees only own dept', d['pagination']['total']==1, d['pagination'])
s,d=call('GET',f'/procurement-planning/ppmps/{P1}',tok=T['dp']); ok('dept planner cannot open other dept PPMP', s==403, d)
s,d=call('PUT',f'/procurement-planning/ppmps/{P1}',{'title':'hack','items':[line('a',1,1)]},T['dp']); ok('dept planner cannot edit other dept PPMP', s==403, d)
s,d=call('GET',f'/procurement-planning/ppmps/{P1}',tok=T['eu']); ok('end user has no PPMP access', s==403, d)
s,d=call('POST',f'/procurement-planning/ppmps/{P1}/approve',tok=T['bo']); ok('cannot approve a draft', s==409, d)
s,d=call('POST',f'/procurement-planning/ppmps/{P1}/submit',tok=T['pl']); ok('submit PPMP', s==200 and d['status']=='submitted', d)
s,d=call('PUT',f'/procurement-planning/ppmps/{P1}',{'title':'x','items':[line('a',1,1)]},T['pl']); ok('submitted PPMP is locked', s==409, d)
s,d=call('POST',f'/procurement-planning/ppmps/{P1}/return',{'return_reason':''},T['bo']); ok('return needs a reason', s==422, d)
s,d=call('POST',f'/procurement-planning/ppmps/{P1}/return',{'return_reason':'Split the laptops by quarter'},T['bo']); ok('return PPMP bumps revision', s==200 and d['status']=='returned' and d['revision']==1, d)
s,d=call('PUT',f'/procurement-planning/ppmps/{P1}',{'title':'IT PPMP rev 1','items':[line('Laptop',10,50000,code='5060405003'),line('Bond paper',100,250,sched='Q2')]},T['pl']); ok('edit returned PPMP', s==200, d)
call('POST',f'/procurement-planning/ppmps/{P1}/submit',tok=T['pl'])
s,d=call('POST',f'/procurement-planning/ppmps/{P1}/review',tok=T['bo']); ok('review PPMP', s==200 and d['status']=='under_review' and d['reviewer']['id']==ids['bo'], d)
s,d=call('POST',f'/procurement-planning/ppmps/{P1}/approve',tok=T['bo']); ok('approve PPMP', s==200 and d['status']=='approved', d)
s,d=call('DELETE',f'/procurement-planning/ppmps/{P1}',tok=T['pl']); ok('approved PPMP cannot be deleted', s==409, d)
# self-approval: admin prepares and tries to approve own
s,d=call('POST','/procurement-planning/ppmps',{'fiscal_year':FY,'title':'Admin PPMP','items':[line('Printer',2,15000,mode='Small Value Procurement',sched='Q3')]},T['adm']); P3=d['id']
call('POST',f'/procurement-planning/ppmps/{P3}/submit',tok=T['adm'])
s,d=call('POST',f'/procurement-planning/ppmps/{P3}/approve',tok=T['adm']); ok('preparer cannot approve own PPMP', s==403, d)
s,d=call('POST',f'/procurement-planning/ppmps/{P3}/approve',tok=T['bo']); ok('someone else approves it', s==200, d)

# ── APP
s,d=call('POST','/procurement-planning/apps/generate',{'fiscal_year':FY},T['adm']); ok('consolidate APP', s==201 and d['app_number']==f'APP-{FY}-001' and d['items_count']==3 and d['ppmp_count']==2, d); A1=d['id']
s,d=call('POST','/procurement-planning/apps/generate',{'fiscal_year':FY},T['adm']); ok('no duplicate APP when nothing new', s==409, d)
call('POST',f'/procurement-planning/ppmps/{P2}/submit',tok=T['dp']); call('POST',f'/procurement-planning/ppmps/{P2}/approve',tok=T['bo'])
s,d=call('GET',f'/procurement-planning/apps/{A1}',tok=T['adm']); ok('APP shows a pending PPMP', d['pending_ppmps']==1, d.get('pending_ppmps'))
s,d=call('POST','/procurement-planning/apps/generate',{'fiscal_year':FY},T['adm']); ok('reconsolidate adds the new PPMP to the same APP', s==200 and d['id']==A1 and d['items_count']==4 and d['pending_ppmps']==0, d)
s,d=call('GET',f'/procurement-planning/ppmps/{P2}',tok=T['dp']); ok('PPMP shows its APP', d['app_number']==f'APP-{FY}-001', d.get('app_number'))
s,d=call('GET','/procurement-planning/apps',tok=T['eu']); ok('PR raisers only see approved APPs', s==200 and d['pagination']['total']==0, d)
app=call('GET',f'/procurement-planning/apps/{A1}',tok=T['adm'])[1]
lap=[i for i in app['items'] if i['general_description']=='Laptop'][0]
s,d=call('PUT',f'/procurement-planning/apps/{A1}',{'items':[{**lap,'estimated_budget':2500000,'procurement_mode':'Small Value Procurement'}]},T['adm']); ok('ceiling enforced on APP edit', s==422, d)
s,d=call('PUT',f'/procurement-planning/apps/{A1}',{'title':'APP FY 2027','items':[{**lap,'quantity':12,'estimated_budget':600000}]},T['adm']); ok('edit APP line', s==200 and d['title']=='APP FY 2027' and float(d['total_budget'])==600000+25000+30000+6000, d.get('total_budget'))
s,d=call('POST',f'/procurement-planning/apps/{A1}/approve',tok=T['adm']); ok('consolidator cannot approve own APP', s==403, d)
s,d=call('POST',f'/procurement-planning/apps/{A1}/approve',tok=T['bo']); ok('approve APP', s==200 and d['status']=='approved', d)
s,d=call('POST','/procurement-planning/apps/generate',{'fiscal_year':FY},T['adm']); ok('approved APP cannot be reconsolidated', s==409, d)
s,d=call('PUT',f'/procurement-planning/apps/{A1}',{'title':'x'},T['adm']); ok('approved APP is locked', s==409, d)
s,d=call('DELETE',f'/procurement-planning/apps/{A1}',tok=T['adm']); ok('approved APP cannot be deleted', s==409, d)
s,d=call('GET','/procurement-planning/apps',tok=T['eu']); ok('PR raiser now sees the approved APP', d['pagination']['total']==1, d['pagination'])

# ── Purchase requests
paper=[i for i in app['items'] if i['general_description']=='Bond paper'][0]
pr=lambda items,**kw: {'purpose':'Office equipment','priority':'high','app_id':A1,'items':items,**kw}
s,d=call('POST','/purchase-requests',pr([{'general_description':'Laptop','quantity':13,'estimated_unit_cost':50000,'app_item_id':lap['id']}]),T['eu']); ok('APP quantity cap', s==422 and 'remaining APP balance' in json.dumps(d), d)
s,d=call('POST','/purchase-requests',pr([{'general_description':'Laptop','quantity':12,'estimated_unit_cost':60000,'app_item_id':lap['id']}]),T['eu']); ok('APP budget cap', s==422 and 'APP budget' in json.dumps(d), d)
s,d=call('POST','/purchase-requests',{'purpose':'x','items':[{'general_description':'Laptop','quantity':1,'estimated_unit_cost':1,'app_item_id':lap['id']}]},T['eu']); ok('APP line needs its APP', s==422, d)
s,d=call('POST','/purchase-requests',pr([{'general_description':'Laptop','quantity':8,'estimated_unit_cost':50000,'app_item_id':lap['id']},{'general_description':'Bond paper','quantity':40,'estimated_unit_cost':250,'app_item_id':paper['id']}],department_id=D2),T['eu'])
ok('end user raises PR (own dept, fields filled from APP)', s==201 and d['pr_number'].startswith('PR-2026') and d['department']['id']==1 and float(d['total_amount'])==410000 and d['items'][0]['procurement_mode']=='Small Value Procurement' and d['items'][0]['fund_source'].startswith('General'), d); R1=d['id']
s,d=call('POST','/purchase-requests',pr([{'general_description':'Laptop','quantity':5,'estimated_unit_cost':50000,'app_item_id':lap['id']}]),T['eu2']); ok('drafts count against the APP balance', s==422 and '4 of 12' in json.dumps(d), d)
s,d=call('GET','/purchase-requests',tok=T['eu2']); ok('other end user sees none', d['pagination']['total']==0, d['pagination'])
s,d=call('GET',f'/purchase-requests/{R1}',tok=T['eu2']); ok('other end user cannot open it', s==403, d)
s,d=call('PUT',f'/purchase-requests/{R1}',pr([{'general_description':'Laptop','quantity':8,'estimated_unit_cost':50000,'app_item_id':lap['id']},{'general_description':'Bond paper','quantity':40,'estimated_unit_cost':250,'app_item_id':paper['id']}]),T['eu2']); ok('other end user cannot edit it', s==403, d)
s,d=call('POST',f'/purchase-requests/{R1}/submit',tok=T['eu']); ok('submit PR builds 4-step chain', s==200 and d['status']=='submitted' and len(d['approvals'])==4 and d['stage_label']=='Department Head', d)
s,d=call('PUT',f'/purchase-requests/{R1}',pr([{'general_description':'Laptop','quantity':1,'estimated_unit_cost':1}]),T['eu']); ok('routing PR is locked', s==409, d)
s,d=call('GET','/purchase-requests?awaiting_me=true',tok=T['dh']); ok('dept head sees it awaiting', d['pagination']['total']==1, d['pagination'])
s,d=call('GET','/purchase-requests?awaiting_me=true',tok=T['bo']); ok('budget officer does not (yet)', d['pagination']['total']==0, d['pagination'])
s,d=call('POST',f'/purchase-requests/{R1}/approve-step',{},T['bo']); ok('wrong signatory refused', s==403, d)
s,d=call('POST',f'/purchase-requests/{R1}/approve-step',{'remarks':'ok'},T['dh']); ok('dept head signs', s==200 and d['status']=='under_review' and d['stage_label']=='Budget Officer', d)
s,d=call('POST',f'/purchase-requests/{R1}/approve-step',{},T['bo']); ok('budget step needs a CAF', s==409 and 'CAF' in d.get('error',''), d)
s,d=call('POST',f'/purchase-requests/{R1}/certify-funds',{'caf_amount':100},T['bo']); ok('CAF must cover the total', s==422, d)
s,d=call('POST',f'/purchase-requests/{R1}/certify-funds',{'caf_remarks':'Fund 101'},T['bo']); ok('certify funds', s==200 and d['caf_number']=='CAF-2026-0001' and d['funds_covered'], d)
s,d=call('POST',f'/purchase-requests/{R1}/certify-funds',{},T['acc']); ok('no second CAF while covered', s==409, d)
s,d=call('POST',f'/purchase-requests/{R1}/approve-step',{},T['bo']); ok('budget officer signs', s==200 and d['stage_label']=='Accounting', d)
s,d=call('POST',f'/purchase-requests/{R1}/return',{'return_reason':'Lower the laptop spec'},T['acc']); ok('accounting returns it', s==200 and d['status']=='returned' and d['return_reason'], d)
s,d=call('PUT',f'/purchase-requests/{R1}',pr([{'general_description':'Laptop','quantity':8,'estimated_unit_cost':52000,'app_item_id':lap['id']},{'general_description':'Bond paper','quantity':40,'estimated_unit_cost':250,'app_item_id':paper['id']}]),T['eu']); ok('requester revises (total rises)', s==200 and float(d['total_amount'])==426000 and not d['funds_covered'], d)
call('POST',f'/purchase-requests/{R1}/submit',tok=T['eu'])
call('POST',f'/purchase-requests/{R1}/approve-step',{},T['dh'])
s,d=call('POST',f'/purchase-requests/{R1}/certify-funds',{},T['bo']); ok('new CAF for the higher total', s==200 and d['caf_number']=='CAF-2026-0002', d)
call('POST',f'/purchase-requests/{R1}/approve-step',{},T['bo']); call('POST',f'/purchase-requests/{R1}/approve-step',{},T['acc'])
s,d=call('POST',f'/purchase-requests/{R1}/approve-step',{},T['bac']); ok('BAC signs, PR approved', s==200 and d['status']=='approved' and d['stage_label']=='Completed' and all(a['status']=='approved' for a in d['approvals']), d)
s,d=call('POST',f'/purchase-requests/{R1}/cancel',tok=T['eu']); ok('approved PR cannot be cancelled', s==409, d)
# requester who is also a signatory
s,d=call('POST','/purchase-requests',{'purpose':'Dept head own','items':[{'general_description':'Stapler','quantity':2,'estimated_unit_cost':300}]},T['dh']); ok('dept head cannot raise PRs (no create perm)', s==403, d)
s,d=call('POST','/purchase-requests',{'purpose':'Own','items':[{'general_description':'Stapler','quantity':2,'estimated_unit_cost':300,'procurement_mode':'Small Value Procurement'}]},T['pl']); R2=d['id']
call('POST',f'/purchase-requests/{R2}/submit',tok=T['pl'])
s,d=call('POST',f'/purchase-requests/{R2}/cancel',tok=T['pl']); ok('cancel routing PR', s==200 and d['status']=='cancelled', d)
s,d=call('DELETE',f'/purchase-requests/{R2}',tok=T['pl']); ok('delete cancelled PR', s==200, d)
s,d=call('DELETE',f'/purchase-requests/{R1}',tok=T['eu']); ok('approved PR cannot be deleted', s==409, d)

# ── utilization
s,d=call('GET',f'/procurement-planning/apps/{A1}',tok=T['bo'])
l2=[i for i in d['items'] if i['id']==lap['id']][0]
ok('APP line: requested 8, remaining 4, utilized 416k', l2['requested_quantity']==8 and l2['remaining_quantity']==4 and float(l2['utilized_amount'])==416000 and l2['status']=='ongoing', l2)
s,d=call('GET',f'/procurement-planning/budget-summary?fiscal_year={FY}',tok=T['acc'])
ok('budget summary derives utilization', s==200 and float(d['total_utilized'])==426000 and float(d['total_estimated'])==661000 and len(d['by_department'])==2 and d['ppmp_approved_count']==3, d)
s,d=call('GET',f'/procurement-planning/budget-summary?fiscal_year={FY}',tok=T['eu']); ok('end user cannot see budget summary', s==403, d)
s,d=call('DELETE',f'/procurement-planning/ppmps/{P1}',tok=T['eu']); ok('end user cannot delete PPMPs', s==403, d)
print('FAILURES:', fails)