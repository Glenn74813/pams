set session_replication_role = replica;
do $$ declare t text; begin
  for t in select table_name from information_schema.tables where table_schema='public' and table_type='BASE TABLE'
    and table_name not in ('flyway_schema_history','roles','departments','users','settings','system_settings','signatories','report_signatories','revoked_tokens','locations')
  loop execute format('delete from %I', t); end loop;
end $$;
delete from users where email <> 'superadmin@inventory.com';
delete from roles where is_system = false;
set session_replication_role = origin;
