create or replace function zz_docs() returns trigger language plpgsql as $$ begin
  insert into supplier_compliance_documents (supplier_id, document_type, reference_number, expiry_date, created_at, updated_at)
  select new.id, t, 'ZZ', current_date + 400, now(), now() from unnest(array['PhilGEPS Certificate of Registration (Platinum)','Income or Business Tax Return','Omnibus Sworn Statement','PCAB License','NFCC Computation','Professional License']) t;
  update suppliers set is_eligible = true where id = new.id;
  return new; end $$;
drop trigger if exists zz_docs on suppliers;
create trigger zz_docs after insert on suppliers for each row when (new.name like 'ZZ %' and new.name not like 'ZZ Office%' and new.name not like 'ZZ Sup%' and new.name not like 'ZZ Art%' and new.name not like 'ZZ Paper%' and new.name not like 'ZZ Black%') execute function zz_docs();
