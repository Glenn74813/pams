from pams_db import sql
import json, urllib.request, urllib.error, time, datetime
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
FY=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).year
_,role=call('POST','/roles',{'name':'ZZ Stock Viewer','permissions':['view_warehouse']},SA)
s,d=call('POST','/users',{'name':'zz wh','email':'zz.wh@test.local','password':'Zz#Test2026','password_confirmation':'Zz#Test2026','role_id':role.get('id') or role['data']['id'],'department_id':1},SA); assert s==201,d
WH=login('zz.wh@test.local','Zz#Test2026')
_,c=call('POST','/inventory/categories',{'name':'ZZ Supplies'},SA); CAT=c.get('id') or c['category']['id']
def item(name, mn, mx, cls='consumable'):
    s,d=call('POST','/inventory/items',{'name':name,'category_id':CAT,'unit_of_measure':'ream','classification':cls,'min_stock':mn,'max_stock':mx},SA); assert s in (200,201),d
    return (d.get('item') or d)['id']
PAPER=item('ZZ Bond paper A4', 20, 200)          # nothing on hand: order 200
PEN=item('ZZ Ballpen black', 50, 500)            # planned for only 120
GLUE=item('ZZ Glue stick', 10, 40)               # not planned
LAPTOP=item('ZZ Laptop', 1, 5, 'ppe')            # PPE: bought per asset, never restocked to a level
line=lambda desc,q,c,**kw: dict(general_description=desc,unit='ream',quantity=q,estimated_unit_cost=c,procurement_mode='Small Value Procurement',schedule='Q1',fund_source='General Appropriations Act (GAA)',**kw)

s,d=call('GET','/purchase-requests/reorder',tok=WH); ok('reorder needs a purchase request permission', s==403, d)
s,d=call('GET','/purchase-requests/reorder',tok=SA)
ok('no approved APP: every item is listed apart', s==200 and d['app'] is None and not d['groups'] and {u['item_id'] for u in d['unplanned']}=={PAPER,PEN,GLUE}
   and 'no approved APP' in d['unplanned'][0]['reason'], d)

s,d=call('POST','/procurement-planning/ppmps',{'fiscal_year':FY,'title':'x','items':[line('Paper',1,1,item_id=999999)]},SA); ok('unknown catalogue item refused', s==422 and 'catalogue item' in json.dumps(d), d)
s,d=call('POST','/procurement-planning/ppmps',{'department_id':1,'fiscal_year':FY,'title':'Supplies','items':[line('Bond paper, A4',300,250,item_id=PAPER),line('Ballpen, black',120,10,item_id=PEN),line('Stapler',5,300)]},SA)
ok('PPMP line names its stock item', s==201 and d['items'][0]['item']['id']==PAPER and d['items'][0]['item']['name']=='ZZ Bond paper A4' and d['items'][2]['item'] is None, d); P1=d['id']
call('POST',f'/procurement-planning/ppmps/{P1}/submit',tok=SA); call('POST',f'/procurement-planning/ppmps/{P1}/approve',tok=SA)
s,d=call('POST','/procurement-planning/apps/generate',{'fiscal_year':FY},SA); A=d['id']
ok('APP line keeps the stock item', s==201 and {i['general_description']:(i['item'] or {}).get('id') for i in d['items']}=={'Bond paper, A4':PAPER,'Ballpen, black':PEN,'Stapler':None}, d)
s,d=call('GET','/purchase-requests/reorder',tok=SA); ok('a draft APP does not count', d['app'] is None, d.get('app'))
call('POST',f'/procurement-planning/apps/{A}/approve',{},SA)

s,d=call('GET','/purchase-requests/reorder',tok=SA)
g=d['groups'][0] if d.get('groups') else {'lines':[]}
L={l['item_id']:l for l in g['lines']}
ok('items matched to the approved APP', s==200 and d['app']['id']==A and len(d['groups'])==1 and g['department']['id']==1 and set(L)=={PAPER,PEN}, d)
ok('quantity is the suggested order', L.get(PAPER,{}).get('quantity')==200 and L[PAPER]['suggested']==200 and float(L[PAPER]['unit_cost'])==250, L.get(PAPER))
ok('quantity is capped at what is left in the APP', L.get(PEN,{}).get('quantity')==120 and L[PEN]['suggested']==500 and L[PEN]['remaining_quantity']==120, L.get(PEN))
U={u['item_id']:u for u in d['unplanned']}
ok('unplanned item listed with its reason', set(U)=={GLUE} and 'Not planned' in U[GLUE]['reason'], d['unplanned'])
ok('PPE left out', LAPTOP not in L and LAPTOP not in U)
s,d=call('GET',f'/purchase-requests/reorder?item_ids={PAPER}',tok=SA); ok('only the chosen items', [l['item_id'] for gr in d['groups'] for l in gr['lines']]==[PAPER] and not d['unplanned'], d)

s,d=call('POST','/purchase-requests',{'department_id':1,'app_id':A,'purpose':'Replenishment','priority':'medium',
                                      'items':[{'app_item_id':L[PAPER]['app_item_id'],'general_description':L[PAPER]['general_description'],'unit':'ream','quantity':200,'estimated_unit_cost':250},
                                               {'app_item_id':L[PEN]['app_item_id'],'general_description':L[PEN]['general_description'],'unit':'ream','quantity':120,'estimated_unit_cost':10}]},SA)
ok('purchase request drafted from the reorder lines', s==201, d)
s,d=call('GET','/purchase-requests/reorder',tok=SA)
L={l['item_id']:l for gr in d['groups'] for l in gr['lines']}; U={u['item_id']:u for u in d['unplanned']}
ok('the rest of the APP line is offered next', L.get(PAPER,{}).get('quantity')==100 and L[PAPER]['remaining_quantity']==100, L.get(PAPER))
ok('a fully requested line is listed apart', PEN in U and 'fully requested' in U[PEN]['reason'], d)
print('fails', fails)
