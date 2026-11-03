import json, urllib.request, urllib.error, time
B=__import__('os').environ['PAMS_API']
def call(m, p, body=None, tok=None):
    h={'Content-Type':'application/json','Accept':'application/json'}
    if tok: h['Authorization']='Bearer '+tok
    r=urllib.request.Request(B+p, data=json.dumps(body).encode() if body is not None else None, headers=h, method=m)
    try:
        with urllib.request.urlopen(r) as x: return x.status, json.loads(x.read() or b'{}')
    except urllib.error.HTTPError as e: return e.code, json.loads(e.read() or b'{}')
def login(e,pw):
    s,d=call('POST','/auth/login',{'email':e,'password':pw}); assert s==200,(e,s,d); return d['access_token']
fails=0
def ok(label, cond, extra=''):
    global fails
    if not cond: fails+=1
    print(('PASS ' if cond else 'FAIL ')+label, '' if cond else extra)
SA=login('superadmin@inventory.com','admin123')
_,c=call('POST','/inventory/categories',{'name':'ZZ Equipment'},SA); C=c['id']
_,lap=call('POST','/inventory/items',{'name':'ZZ Laptop','category_id':C,'unit_of_measure':'unit','classification':'ppe','stock_entries':[{'quantity':4,'unit_cost':65000,'received_date':'2026-01-15','brand_name':'Lenovo','warranty_months':12}]},SA)
_,chr_=call('POST','/inventory/items',{'name':'ZZ Office Chair','category_id':C,'unit_of_measure':'unit','classification':'semi_expendable','stock_entries':[{'quantity':4,'unit_cost':5000,'received_date':'2026-03-01'}]},SA)
_,pen=call('POST','/inventory/items',{'name':'ZZ Pen','category_id':C,'unit_of_measure':'pc','classification':'consumable','stock_entries':[{'quantity':10,'unit_cost':10}]},SA)
L,CH,PEN=lap['id'],chr_['id'],pen['id']
LLOT=lap['stocks'][0]['id']; CLOT=chr_['stocks'][0]['id']; PLOT=pen['stocks'][0]['id']
ids={}
for key,email,rid in [('po','zz.po@test.local',12),('adm','zz.adm@test.local',2),('a','zz.a@test.local',4),('b','zz.b@test.local',4)]:
    s,d=call('POST','/users',{'name':'zz '+key,'email':email,'password':'Zz#Test2026','password_confirmation':'Zz#Test2026','role_id':rid,'department_id':1},SA); assert s==201,d; ids[key]=d['user']['id']
PO,ADM,A,Bt=[login(f'zz.{k}@test.local','Zz#Test2026') for k in ['po','adm','a','b']]
# register
s,d=call('POST','/properties',{'item_id':PEN,'stock_id':PLOT,'assigned_to':ids['a'],'units':[{}]},PO); ok('consumables are not property', s==422, d)
s,d=call('POST','/properties',{'item_id':L,'stock_id':LLOT,'assigned_to':ids['a'],'units':[{'serial_number':'SN1'},{'serial_number':'SN1'}]},PO); ok('duplicate serials refused', s==422, d)
s,d=call('POST','/properties',{'item_id':L,'stock_id':LLOT,'assigned_to':ids['a'],'condition':'excellent','units':[{'serial_number':'SN1'},{'serial_number':'SN2'}]},PO)
ok('register 2 laptops under a PAR', s==201 and d['accountability_number'].startswith('PAR-'), d); P1,P2=[p['id'] for p in d['properties']]; PAR=d['accountability_number']
ok('tags generated', all(p['tag_number'].startswith('PROP-') for p in d['properties']), d['properties'])
_,li=call('GET',f'/inventory/items/{L}',tok=SA); ok('laptop stock 4 -> 2', li['current_stock']==2, li['current_stock'])
s,d=call('GET','/properties',tok=A); ok('holder sees own 2', s==200 and d['pagination']['total']==2, d)
s,d=call('GET','/properties',tok=Bt); ok('other requester sees 0', d['pagination']['total']==0, d)
s,d=call('GET',f'/properties/{P1}',tok=Bt); ok('cannot open someone else\'s asset', s==403, d)
s,d=call('GET','/properties/my-assets',tok=A); ok('my assets value 130k', d['summary']['total']==2 and float(d['summary']['total_value'])==130000, d.get('summary'))
s,d=call('PUT',f'/properties/{P1}',{'assigned_to':ids['b'],'condition':'good'},PO); ok('update cannot change holder', s==200 and d['holder']['id']==ids['a'] and d['condition']=='good', d)
# transfer
s,d=call('POST',f'/properties/{P1}/transfer',{'to_user':ids['b'],'reason':''},PO); ok('transfer needs reason', s==422, d)
s,d=call('POST',f'/properties/{P1}/transfer',{'to_user':ids['b'],'reason':'Reassigned to finance'},PO); ok('transfer under PTR', s==200 and d['transfer']['reference_number'].startswith('PTR-') and d['property']['holder']['id']==ids['b'], d)
s,d=call('GET',f'/properties/{P1}',tok=Bt); ok('new holder can open it', s==200, d)
# gate pass
s,g=call('POST','/gate-pass',{'property_id':P2,'received_by':'zz a','purpose':'Field audit','expected_return_date':'2026-10-01','date_released':'2026-09-20'},PO); ok('gate pass issued', s==201 and g['gate_pass_number'].startswith('GP-'), g); G=g['id']
ok('gate pass shows overdue', g['overdue'] and g['status']=='overdue', g)
s,d=call('POST','/gate-pass',{'property_id':P2,'received_by':'x','purpose':'y'},PO); ok('one open pass per asset', s==422, d)
s,d=call('POST',f'/properties/{P2}/return',{'reason':'Returning'},PO); ok('cannot turn in while out on pass', s==409, d)
s,d=call('POST',f'/gate-pass/{G}/return',{'condition_on_return':'damaged','returned_by':'zz a'},PO); ok('gate pass returned', s==200 and d['status']=='returned', d)
_,pv=call('GET',f'/properties/{P2}',tok=SA); ok('damaged return sets condition poor', pv['condition']=='poor', pv['condition'])
# disposal
s,d=call('POST','/disposal/requests',{'property_id':P2,'disposal_type':'condemnation','reason':'Broken screen'},PO); ok('cannot dispose an issued asset', s==422, d)
s,d=call('POST',f'/properties/{P2}/return',{'reason':'Unserviceable','condition':'poor'},PO); ok('turn-in under PRS', s==200 and d['transfer']['reference_number'].startswith('PRS-') and d['property']['holder'] is None and d['property']['status']=='available', d)
s,dr=call('POST','/disposal/requests',{'property_id':P2,'disposal_type':'public_auction','reason':'Broken screen'},PO); ok('disposal drafted', s==201 and dr['disposal_number'].startswith('DSP-'), dr); D=dr['id']
s,d=call('POST','/disposal/requests',{'property_id':P2,'disposal_type':'condemnation','reason':'dup'},PO); ok('one open disposal per asset', s==422, d)
s,d=call('PUT',f'/disposal/requests/{D}',{'property_id':P2,'disposal_type':'sale','reason':'Broken screen, sell as scrap'},PO); ok('draft editable', s==200 and d['disposal_type']=='sale', d)
s,d=call('POST',f'/disposal/requests/{D}/approve',None,ADM); ok('cannot approve a draft', s==409, d)
s,d=call('POST',f'/disposal/requests/{D}/submit',None,PO); ok('submitted', s==200 and d['status']=='submitted', d)
s,d=call('POST',f'/disposal/requests/{D}/approve',None,PO); ok('property officer cannot approve', s==403, d)
s,d=call('POST',f'/disposal/requests/{D}/approve',None,ADM); ok('admin approves', s==200 and d['status']=='approved', d)
s,d=call('POST',f'/disposal/requests/{D}/complete',{'proceeds':2500},ADM); ok('completed with proceeds', s==200 and d['status']=='completed' and float(d['proceeds'])==2500 and d['book_value'] is not None, d)
_,pv=call('GET',f'/properties/{P2}',tok=SA); ok('asset disposed', pv['status']=='disposed' and not pv['can_transfer'], pv['status'])
s,d=call('POST','/gate-pass',{'property_id':P2,'received_by':'x','purpose':'y'},PO); ok('no gate pass for disposed asset', s==422, d)
# depreciation
s,d=call('POST','/depreciation/run',{'period':'2026-09'},PO); ok('depreciation posted Feb-Sep', s==200 and d['months_posted']==8 and d['assets']==1, d)
s,d=call('POST','/depreciation/run',{'period':'2026-09'},PO); ok('re-run posts nothing', s==200 and d['months_posted']==0, d)
_,dep=call('GET',f'/properties/{P1}/depreciation',tok=SA)
ok('monthly 1029.17, accumulated 8233.36', float(dep['monthly_depreciation'])==1029.17 and float(dep['accumulated_depreciation'])==8233.36, (dep['monthly_depreciation'],dep['accumulated_depreciation']))
ok('schedule ends at residual 3250', float(dep['schedule'][-1]['book_value'])==3250.0, dep['schedule'][-1])
s,d=call('PUT',f'/properties/{P1}',{'useful_life_years':10},PO); ok('life fixed once depreciation posted', s==409, d)
# semi-expendable lifecycle
s,d=call('POST','/properties',{'item_id':CH,'stock_id':CLOT,'assigned_to':ids['a'],'units':[{}]},PO); ok('chair under ICS', s==201 and d['accountability_number'].startswith('ICS-') and not d['properties'][0]['is_depreciable'], d); CHP=d['properties'][0]['id']
s,d=call('POST',f'/properties/{CHP}/status',{'action':'retire'},PO); ok('cannot retire while issued', s==409, d)
s,d=call('POST',f'/properties/{CHP}/return',{'reason':'Not needed'},PO); ok('return under RRSP', s==200 and d['transfer']['reference_number'].startswith('RRSP-'), d)
for act,want in [('repair','under_repair'),('in_service','available'),('retire','retired'),('reinstate','available')]:
    s,d=call('POST',f'/properties/{CHP}/status',{'action':act},PO); ok(f'status {act} -> {want}', s==200 and d['status']==want, d)
s,d=call('POST',f'/properties/{CHP}/issue',{'to_user':ids['b'],'reason':'For new staff'},PO); ok('re-issue under new ICS', s==200 and d['transfer']['reference_number'].startswith('ICS-') and d['property']['holder']['id']==ids['b'], d)
s,h=call('GET',f'/properties/{CHP}/transfers',tok=SA); ok('history: issue + return', [t['transfer_type'] for t in h]==['reissue','return','reissue'], h)
# delete
s,d=call('DELETE',f'/properties/{P1}',tok=SA); ok('cannot delete asset with history', s==409, d)
s,d=call('POST','/properties',{'item_id':L,'stock_id':LLOT,'assigned_to':ids['a'],'units':[{}]},PO); P3=d['properties'][0]['id']
_,li=call('GET',f'/inventory/items/{L}',tok=SA); before=li['current_stock']
s,d=call('DELETE',f'/properties/{P3}',tok=SA); ok('undo a mistaken registration', s==200, d)
_,li=call('GET',f'/inventory/items/{L}',tok=SA); ok('unit back in its lot', li['current_stock']==before+1, (before, li['current_stock']))
# inventory write-off
s,w=call('POST','/disposal/requests',{'disposal_category':'inventory','item_id':PEN,'quantity':4,'disposal_type':'destruction','reason':'Dried out'},PO); W=w['id']
call('POST',f'/disposal/requests/{W}/submit',None,PO); call('POST',f'/disposal/requests/{W}/approve',None,ADM)
s,d=call('POST',f'/disposal/requests/{W}/complete',{'proceeds':5},ADM); ok('no proceeds for destruction', s==422, d)
s,d=call('POST',f'/disposal/requests/{W}/complete',None,ADM); ok('stock written off', s==200 and float(d['book_value'])==40.0, d)
_,pi=call('GET',f'/inventory/items/{PEN}',tok=SA); ok('pens 10 -> 6', pi['current_stock']==6, pi['current_stock'])
s,d=call('GET','/disposal/requests',tok=A); ok('requester has no disposal access', s==403, d)
print('FAILURES', fails)