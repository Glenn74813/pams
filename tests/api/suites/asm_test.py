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
today=datetime.date.today(); D=lambda n: (today+datetime.timedelta(days=n)).isoformat()
it=lambda x: x.get('item',x)

SA=login('superadmin@inventory.com','admin123')
s,d=call('POST','/users',{'name':'zz enduser','email':'zz.eu@test.local','password':'Zz#Test2026','password_confirmation':'Zz#Test2026','role_id':15,'department_id':1},SA); assert s==201,d
EU=login('zz.eu@test.local','Zz#Test2026'); EUID=d['user']['id']
_,cat=call('POST','/inventory/categories',{'name':'ZZ Assembly'},SA)
lots=[{'brand_name':'Generic','specifications':'ATX chassis','quantity':2,'unit_cost':3000,'received_date':D(0)},
      {'brand_name':'AMD','specifications':'Ryzen 5 5600','quantity':3,'unit_cost':9000,'received_date':D(0),'warranty_months':36},
      {'brand_name':'Kingston','specifications':'16GB','quantity':4,'unit_cost':2500,'received_date':D(0)}]
pc=it(must(*call('POST','/inventory/items',{'name':'ZZ Clone PC','category_id':cat['id'],'unit_of_measure':'unit','classification':'semi_expendable','is_assembly':True,'stock_entries':lots},SA),201))
plain=it(must(*call('POST','/inventory/items',{'name':'ZZ Monitor','category_id':cat['id'],'unit_of_measure':'unit','classification':'semi_expendable','stock_entries':[{'quantity':1,'unit_cost':8000,'received_date':D(0)}]},SA),201))
lot=lambda item, spec: int(sql(f"select id from stocks where item_id = {item} and specifications = '{spec}'").splitlines()[0])
qty=lambda sid: int(sql(f"select quantity from stocks where id = {sid}").splitlines()[0])
onhand=lambda item: int(sql(f"select current_stock from items where id = {item}").splitlines()[0])
CH,CPU,RAM=lot(pc['id'],'ATX chassis'),lot(pc['id'],'Ryzen 5 5600'),lot(pc['id'],'16GB')
reg=must(*call('POST','/properties',{'item_id':pc['id'],'stock_id':CH,'assigned_to':EUID,'assigned_date':D(0),'units':[{'tag_number':'ZZ-PC1'}]},SA),201)
P=reg['properties'][0]['id']
mlot=int(sql(f"select id from stocks where item_id = {plain['id']}").splitlines()[0])
M=must(*call('POST','/properties',{'item_id':plain['id'],'stock_id':mlot,'assigned_to':EUID,'assigned_date':D(0),'units':[{'tag_number':'ZZ-MON1'}]},SA),201)['properties'][0]['id']

s,a=call('GET',f'/properties/{P}/assembly',tok=SA)
ok('assembly view lists the part lots', s==200 and a['is_assembly'] and {l['id'] for l in a['lots']}>={CPU,RAM} and a['parts']==[], a)
before=onhand(pc['id'])
s,a=call('POST',f'/properties/{P}/assembly-components',{'description':'Processor','specifications':'Ryzen 5 5600','stock_id':CPU,'serial_number':'CPU-001'},SA)
ok('part drawn from the chosen lot', s==201 and len(a['parts'])==1 and float(a['total'])==9000 and qty(CPU)==2, (s,a))
ok('part carries the lot warranty', s==201 and a['parts'][0]['warranty_months']==36 and a['parts'][0]['warranty_start']==D(0), a)
ok('item on hand drops by the part', onhand(pc['id'])==before-1, (before,onhand(pc['id'])))
s,a=call('POST',f'/properties/{P}/assembly-components',{'description':'16GB','specifications':'Kingston','quantity':2},SA)
ok('lot matched by brand and description', s==201 and float(a['total'])==14000 and qty(RAM)==2, (s,a))
s,d=call('POST',f'/properties/{P}/assembly-components',{'description':'Processor','specifications':'ryzen 5 5600 '},SA)
ok('same part twice refused', s==422, d)
s,d=call('POST',f'/properties/{P}/assembly-components',{'description':'Fan','quantity':0},SA)
ok('quantity at least 1', s==422, d)
s,d=call('POST',f'/properties/{P}/assembly-components',{'description':''},SA)
ok('part needs a description', s==422, d)
s,d=call('POST',f'/properties/{P}/assembly-components',{'description':'Thermal paste','remarks':'from spare'},SA)
ok('part without a lot costs nothing', s==201 and float(d['total'])==14000, d)
cost=float(sql(f"select acquisition_cost from properties where id = {P}").splitlines()[0])
ok('unit cost is the sum of its parts', cost==14000, cost)
ok('still semi-expendable under the threshold', a['classification']=='semi_expendable' and sql(f"select case when cast(is_expandable as int) = 1 then 'ppe' when cast(is_semi_expandable as int) = 1 then 'semi_expendable' else 'consumable' end from items where id = {pc['id']}").strip()=='semi_expendable', a['classification'])
s,d=call('POST',f'/properties/{M}/assembly-components',{'description':'Stand'},SA)
ok('non-assembly asset takes no parts', s==422, d)
s,d=call('POST',f'/properties/{P}/assembly-components',{'description':'SSD'},EU)
ok('end user cannot add parts', s==403, d)
s,d=call('GET',f'/properties/{P}/assembly',tok=EU)
ok('holder can view the parts list', s==200 and len(d['parts'])==3, d)

must(*call('PUT','/settings',{'capitalization_threshold':20000},SA))
s,a=call('POST',f'/properties/{P}/assembly-components',{'description':'Processor 2','specifications':'Ryzen 5 5600','stock_id':CPU},SA)
ok('unit reaching the threshold makes the item PPE', s==201 and float(a['total'])==23000 and a['classification']=='ppe' and sql(f"select case when cast(is_expandable as int) = 1 then 'ppe' when cast(is_semi_expandable as int) = 1 then 'semi_expendable' else 'consumable' end from items where id = {pc['id']}").strip()=='ppe', (s,a))
ram=[x for x in a['parts'] if x['description']=='16GB'][0]
s,a=call('DELETE',f"/properties/assembly-components/{ram['id']}",tok=SA)
ok('removed part returns to its lot', s==200 and qty(RAM)==4 and float(a['total'])==18000, (s,a))
ok('item stays PPE after dropping below', sql(f"select case when cast(is_expandable as int) = 1 then 'ppe' when cast(is_semi_expandable as int) = 1 then 'semi_expendable' else 'consumable' end from items where id = {pc['id']}").strip()=='ppe')
logs=sql(f"select action, quantity from inventory_logs where item_id = {pc['id']} and reference_id = {P} order by id")
ok('stock card shows parts out and back', logs.count('stock_out')>=3 and 'stock_in' in logs, logs)
aud=sql(f"select action from audit_trails where auditable_id = {P} and action like 'component_%'")
ok('parts audited', 'component_added' in aud and 'component_removed' in aud, aud)

# whole-asset linking
s,d=call('POST',f'/properties/{P}/components',{'property_id':M},SA)
ok('monitor attached to the PC', s==201 and [x['tag_number'] for x in d]==['ZZ-MON1'], d)
s,d=call('POST',f'/properties/{P}/components',{'property_id':P},SA)
ok('asset cannot contain itself', s==422, d)
s,d=call('POST',f'/properties/{M}/components',{'property_id':P},SA)
ok('only assemblies take components', s==422, d)
s,d=call('DELETE',f'/properties/components/{M}',tok=SA)
ok('monitor detached', s==200 and sql(f"select parent_id from properties where id = {M}").strip() in ('','NULL'), d)
print('FAILURES:',fails)
