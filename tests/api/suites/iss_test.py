import json, urllib.request, urllib.error, time, datetime
B=__import__('os').environ['PAMS_API']
def call(m, p, body=None, tok=None):
    h={'Content-Type':'application/json','Accept':'application/json'}
    if tok: h['Authorization']='Bearer '+tok
    r=urllib.request.Request(B+p, data=json.dumps(body).encode() if body is not None else None, headers=h, method=m)
    try:
        with urllib.request.urlopen(r) as x: return x.status, json.loads(x.read() or b'{}')
    except urllib.error.HTTPError as e: return e.code, json.loads(e.read() or b'{}')
def login(email, pw):
    for _ in range(60):
        try: s,d=call('POST','/auth/login',{'email':email,'password':pw}); break
        except Exception: time.sleep(1)
    assert s==200,(s,d); return d['access_token']
def ok(label, cond, extra=''):
    print(('PASS ' if cond else 'FAIL ')+label, extra if not cond else '')
SA=login('superadmin@inventory.com','admin123')
today=datetime.date.today()
# catalogue
_,cat=call('POST','/inventory/categories',{'name':'ZZ Test Supplies'},SA); C=cat['id']
_,pen=call('POST','/inventory/items',{'name':'ZZ Ballpen','category_id':C,'unit_of_measure':'piece','min_stock':20,'max_stock':100,'classification':'consumable',
  'stock_entries':[{'quantity':10,'unit_cost':8,'expiration_date':str(today-datetime.timedelta(days=5)),'received_date':str(today-datetime.timedelta(days=60)),'lot_number':'OLD'},
                   {'quantity':12,'unit_cost':10,'expiration_date':str(today+datetime.timedelta(days=10)),'lot_number':'SOON'},
                   {'quantity':20,'unit_cost':12,'lot_number':'FRESH'}]},SA)
_,stp=call('POST','/inventory/items',{'name':'ZZ Stapler','category_id':C,'unit_of_measure':'unit','classification':'semi_expendable','stock_entries':[{'quantity':5,'unit_cost':800}]},SA)
_,lap=call('POST','/inventory/items',{'name':'ZZ Laptop','category_id':C,'unit_of_measure':'unit','classification':'ppe','stock_entries':[{'quantity':2,'unit_cost':65000}]},SA)
P,ST,L=pen['id'],stp['id'],lap['id']
ok('ballpen on hand 42', pen['current_stock']==42, pen['current_stock'])
# people
_,role=call('POST','/roles',{'name':'ZZ Stock Controller','permissions':['create_adjustments','approve_adjustments','view_inventory']},SA)
for email,rid in [('zz.req@test.local',4),('zz.appr@test.local',2),('zz.ctl@test.local',role['data']['id'])]:
    s,d=call('POST','/users',{'name':email.split('@')[0],'email':email,'password':'Zz#Test2026','password_confirmation':'Zz#Test2026','role_id':rid,'department_id':1},SA); assert s==201,d
REQ,APPR,CTL=[login(e,'Zz#Test2026') for e in ['zz.req@test.local','zz.appr@test.local','zz.ctl@test.local']]
# request
s,r=call('POST','/requests',{'purpose':'Office use','priority':'high','items':[{'item_id':P,'quantity':25},{'item_id':ST,'quantity':2},{'item_id':L,'quantity':1}]},REQ)
ok('requester creates request', s==201, (s,r)); RID=r['id']
ok('request number', r['request_number'].startswith('REQ-'), r.get('request_number'))
s,d=call('POST','/requests',{'purpose':'x','items':[{'item_id':P,'quantity':1},{'item_id':P,'quantity':2}]},REQ); ok('duplicate lines refused', s==422, d)
s,d=call('PUT',f'/requests/{RID}/approve-items',{'items':[{'id':r['items'][0]['id'],'status':'approved'}]},REQ); ok('requester cannot approve (no permission)', s==403, d)
s,d=call('GET','/requests',tok=REQ); ok('requester sees own list', s==200 and d['pagination']['total']==1, d)
lines={l['item_id']:l['id'] for l in r['items']}
s,d=call('PUT',f'/requests/{RID}/approve-items',{'items':[{'id':lines[P],'status':'approved','approved_quantity':40}]},APPR); ok('cannot issue more than requested', s==422, d)
s,d=call('PUT',f'/requests/{RID}/approve-items',{'items':[{'id':lines[P],'status':'approved','approved_quantity':25},{'id':lines[ST],'status':'approved'},{'id':lines[L],'status':'rejected'}]},APPR)
ok('approver issues', s==200, d)
if s==200:
    rv=d['data']; print('   status',rv['status'],'RIS',rv['ris_number'],'ICS',rv['ics_number'],'PAR',rv['par_number'],'warnings',d['warnings'])
    ok('partially fulfilled (laptop rejected)', rv['status']=='partially_fulfilled')
    ok('RIS + ICS numbered, no PAR', rv['ris_number'] and rv['ics_number'] and not rv['par_number'])
    ok('near-expiry warning', any('SOON' in w for w in d['warnings']), d['warnings'])
_,pi=call('GET',f'/inventory/items/{P}',tok=SA)
lots={x['lot_number']:x['quantity'] for x in pi['stocks']}
ok('FEFO: expired lot untouched, SOON emptied, FRESH -13', lots=={'OLD':10,'SOON':0,'FRESH':7}, lots)
ok('ballpen on hand 17', pi['current_stock']==17, pi['current_stock'])
_,card=call('GET',f'/reports/stock-card/{P}/preview',tok=SA); last=card['rows'][-1]
ok('stock card issue row', last['issue']==25 and last['balance']==17 and 'RIS-' in last['reference'], last)
s,d=call('PUT',f'/requests/{RID}/approve',None,APPR); ok('cannot re-process', s==409, d)
# approve-all path with only expired + usable capped
s,r2=call('POST','/requests',{'purpose':'More pens','items':[{'item_id':P,'quantity':12}]},REQ); R2=r2['id']
s,d=call('PUT',f'/requests/{R2}/approve',None,APPR); ok('approve-all caps at usable stock', s==200 and d['data']['items'][0]['approved_quantity']==7 and d['data']['status']=='partially_fulfilled', (s,d.get('data',{}).get('items'), d))
# reject path + own-approval as superadmin creator
s,r3=call('POST','/requests',{'purpose':'Laptop','items':[{'item_id':L,'quantity':1}]},REQ); R3=r3['id']
s,d=call('PUT',f'/requests/{R3}/reject',{'reason':''},APPR); ok('reject needs reason', s==422, d)
s,d=call('PUT',f'/requests/{R3}/reject',{'reason':'Not in PPMP'},APPR); ok('rejected', s==200 and d['data']['status']=='rejected', d)
# notifications for requester
import subprocess
# semi-expendable ledger
s,d=call('GET','/semi-expendable?outstanding=true',tok=SA); semi=d['data']
ok('ICS accountability created', len(semi)==1 and semi[0]['outstanding']==2 and semi[0]['ics_number'], semi)
s,d=call('GET','/semi-expendable',tok=REQ); ok('requester sees own accountability', s==200 and len(d['data'])==1, d)
s,d=call('POST',f"/semi-expendable/{semi[0]['id']}/return",{'quantity':3},SA); ok('cannot return more than outstanding', s==422, d)
s,d=call('POST',f"/semi-expendable/{semi[0]['id']}/return",{'quantity':1,'restock':True,'remarks':'spare'},SA); ok('return 1 to stock', s==200 and d['outstanding']==1 and d['status']=='partially_returned', d)
_,si=call('GET',f'/inventory/items/{ST}',tok=SA); ok('stapler back to 4', si['current_stock']==4, si['current_stock'])
# adjustments
s,a=call('POST','/stock-adjustments',{'item_id':P,'adjustment_type':'decrease','quantity':10,'reason':'Expired, condemned'},CTL); ok('controller requests decrease', s==201, a); AID=a['id']
s,d=call('POST',f'/stock-adjustments/{AID}/approve',None,CTL); ok('maker cannot approve own', s==403, d)
s,d=call('POST','/stock-adjustments',{'item_id':P,'adjustment_type':'decrease','quantity':999,'reason':'x'},CTL); ok('decrease > on hand refused', s==422, d)
s,d=call('POST',f'/stock-adjustments/{AID}/approve',None,SA); ok('another approver approves', s==200 and d['status']=='approved' and d['new_quantity']==0, d)
_,pi=call('GET',f'/inventory/items/{P}',tok=SA); lots={x['lot_number']:x['quantity'] for x in pi['stocks']}
ok('decrease wrote off the expired lot', lots.get('OLD')==0 and pi['current_stock']==0, (lots, pi['current_stock']))
s,a2=call('POST','/stock-adjustments',{'item_id':P,'adjustment_type':'increase','quantity':5,'unit_cost':11,'reason':'Found in storeroom'},CTL)
s,d=call('POST',f"/stock-adjustments/{a2['id']}/approve",None,SA); ok('increase approved', s==200 and d['new_quantity']==5, d)
_,pi=call('GET',f'/inventory/items/{P}',tok=SA); ok('increase created a costed lot, WAC 11', pi['current_stock']==5 and float(pi['weighted_average_cost'])==11.0, (pi['current_stock'],pi['weighted_average_cost']))
_,card=call('GET',f'/reports/stock-card/{P}/preview',tok=SA); ok('stock card ends at on-hand', card['rows'][-1]['balance']==5, card['rows'][-3:])
print('CARD', [(x['reference'][:38],x['receipt'],x['issue'],x['balance']) for x in card['rows']])
s,d=call('GET','/stock-adjustments',tok=REQ); ok('requester cannot list adjustments', s==403, d)
# PAR for a request: PPE lines only (semi-expendable goes on the ICS, supplies on the RIS), at the cost at issue
import subprocess, tempfile
s,r4=call('POST','/requests',{'purpose':'Laptop and stapler','items':[{'item_id':L,'quantity':1},{'item_id':ST,'quantity':1},{'item_id':P,'quantity':1}]},REQ); R4=r4['id']
s,d=call('PUT',f'/requests/{R4}/approve',None,APPR); ok('PPE, semi-expendable and supply issued', s==200 and d['data'].get('par_number') and d['data'].get('ics_number'), d)
def pdf_text(path):
    rq=urllib.request.Request(B+path, headers={'Authorization':'Bearer '+SA})
    with urllib.request.urlopen(rq) as x, tempfile.NamedTemporaryFile(suffix='.pdf') as f:
        f.write(x.read()); f.flush()
        return subprocess.run(['pdftotext','-raw',f.name,'-'],capture_output=True,text=True).stdout
par=pdf_text(f'/requests/{R4}/export-par?format=pdf')
ok('PAR lists the PPE line', 'ZZ Laptop' in par and '65,000.00' in par, par[:600])
ok('PAR leaves out semi-expendable and supplies', 'ZZ Stapler' not in par and 'ZZ Ballpen' not in par, par[:600])
ok('PAR carries the request PAR number', d['data'].get('par_number','?') in par, par[:300])
ics=pdf_text(f'/requests/{R4}/export-ics?format=pdf')
ok('ICS still lists the semi-expendable line only', 'ZZ Stapler' in ics and 'ZZ Laptop' not in ics, ics[:600])
