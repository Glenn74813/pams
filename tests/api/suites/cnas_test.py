import json, os, urllib.request, urllib.error, datetime, sys, uuid, zipfile, io
B=os.environ['PAMS_API']; RUN=uuid.uuid4().hex[:6]
def raw(m,p,body=None,tok=None):
    h={'Content-Type':'application/json','Accept':'*/*'}
    if tok: h['Authorization']='Bearer '+tok
    r=urllib.request.Request(B+p,data=json.dumps(body).encode() if body is not None else None,headers=h,method=m)
    try:
        with urllib.request.urlopen(r) as x: return x.status,x.read()
    except urllib.error.HTTPError as e: return e.code,e.read()
def call(*a,**k):
    s,b=raw(*a,**k)
    try: return s,json.loads(b or b'{}')
    except ValueError: return s,{'raw':b[:200].decode('latin1')}
fails=0
def ok(l,c,x=''):
    global fails
    if not c: fails+=1
    print(('PASS ' if c else 'FAIL ')+l,'' if c else x,flush=True)
today=datetime.date.today(); PS='Procurement from PS-DBM'; OLD=(today-datetime.timedelta(days=60)).isoformat()
s,d=call('POST','/auth/login',{'email':'superadmin@inventory.com','password':'admin123'}); assert s==200,d; SA=d['access_token']
s,d=call('POST','/purchase-requests',{'purpose':f'ZZ CNAS check {RUN}','priority':'medium','items':[
  {'general_description':f'ZZ Stapler {RUN}','unit':'piece','quantity':5,'estimated_unit_cost':300,'procurement_mode':PS},
  {'general_description':f'ZZ Pen {RUN}','unit':'piece','quantity':5,'estimated_unit_cost':10,'procurement_mode':PS}]},SA); assert s==201,d; PR=d['id']
call('POST',f'/purchase-requests/{PR}/submit',tok=SA); call('POST',f'/purchase-requests/{PR}/approve-step',{},SA); call('POST',f'/purchase-requests/{PR}/certify-funds',{},SA)
for _ in range(3): s,d=call('POST',f'/purchase-requests/{PR}/approve-step',{},SA)
assert d['status']=='approved',d; STAP,PEN=[i['id'] for i in d['items']]
s,o=call('POST','/ps-dbm/orders',{'purchase_request_id':PR,'items':[{'purchase_request_item_id':STAP,'quantity':5,'unit_price':280},{'purchase_request_item_id':PEN,'quantity':5,'unit_price':8}]},SA); assert s==201,o; A=o['id']
s,o=call('POST',f'/ps-dbm/orders/{A}/submit',{'vs_reference':f'VS-{RUN}','submitted_date':OLD},SA); assert s==200,o
line=[l for l in o['items'] if l['purchase_request_item_id']==STAP][0]
rfq=lambda m,i:call('POST','/rfq',{'title':f'ZZ {RUN}','documents_stage':'offer','procurement_method':m,'purchase_request_id':PR,'deadline':(today+datetime.timedelta(days=30)).isoformat()+'T10:00:00','items':[{'purchase_request_item_id':i}]},SA)
s,d=rfq('Small Value Procurement',STAP); ok('before release the line cannot go to an RFQ',s==422 and 'PS-DBM' in json.dumps(d),d)
s,o=call('POST',f"/ps-dbm/orders/{A}/items/{line['id']}/not-available",{'release_ground':'zero_inventory','cnas_number':f'CNAS-{RUN}','cnas_date':OLD},SA)
ok('a 60-day-old CNAS is recorded',s==200,o)
st=[l for l in o['items'] if l['purchase_request_item_id']==STAP][0]
ok('no validity date is sent',st['cnas_date']==OLD and 'cnas_valid_until' not in st,st)
s,d=rfq('Direct Contracting',STAP); ok('other modes stay refused',s==422,d)
s,d=rfq('Small Value Procurement',PEN); ok('a line not released stays refused',s==422 and 'PS-DBM' in json.dumps(d),d)
s,r=rfq('Small Value Procurement',STAP); ok('the line goes to SVP 60 days after the CNAS',s==201,r)
s,b=raw('GET',f"/reports/cnas/{line['id']}/export",tok=SA)
txt=''
if s==200 and b[:2]==b'PK':
    z=zipfile.ZipFile(io.BytesIO(b)); txt=' '.join(z.read(n).decode('utf8','ignore') for n in z.namelist() if n.endswith('.xml'))
ok('the CNAS form downloads without a validity sentence',s==200 and 'other modes of procurement' in txt and 'valid for' not in txt.lower(),(s,b[:150]))
# clean up what this test made
rid=r.get('id')
if rid: print('rfq delete',call('DELETE',f'/rfq/{rid}',tok=SA)[0])
print('order cancel',call('POST',f'/ps-dbm/orders/{A}/cancel',{'reason':'test'},SA)[0])
print('pr delete',call('DELETE',f'/purchase-requests/{PR}',tok=SA)[0],'PR',PR,'APR',A)
print('DONE fails=%d'%fails)
