"""Raw SQL for the API tests on either database. PAMS_DB=mssql runs statements on the SQL Server test
container (translating the few PostgreSQL forms the tests use); otherwise psql on PostgreSQL.
Results come back as psql -At would print them (booleans as t/f, NULL as an empty string)."""
import datetime, os, re, subprocess

MSSQL = os.environ.get('PAMS_DB') == 'mssql'
ENV = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'backend-java', '.env')


def _pw(key):
    for line in open(ENV):
        if line.startswith(key + '='):
            return line.split('=', 1)[1].strip()


def _manila():
    return (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=8)).replace(tzinfo=None)


def _ts(dt):
    return "cast('" + dt.strftime('%Y-%m-%d %H:%M:%S') + "' as datetime2)"


def translate(q):
    now = _manila()
    q = re.sub(r"now\(\) - interval '(\d+) (hour|minute|day)s?'", lambda m: _ts(now - datetime.timedelta(**{m.group(2) + 's': int(m.group(1))})), q)
    q = q.replace('now()', _ts(now))
    q = q.replace('::text', '')
    q = q.replace("table_schema='public'", "table_schema='dbo'")
    q = re.sub(r'\bfalse\b', '0', re.sub(r'\btrue\b', '1', q)) if q.lstrip().lower().startswith('insert') else q
    m = re.match(r'(?is)insert into (\w+) \((.*?)\) values (.*) returning id\s*$', q)
    if m:
        q = f'insert into {m.group(1)} ({m.group(2)}) output inserted.id values {m.group(3)}'
    m = re.match(r"(?is)select string_agg\(distinct (\w+), '(.)' order by \1\) from (\w+) where (.*)$", q)
    if m:
        q = f"select string_agg({m.group(1)}, '{m.group(2)}') within group (order by {m.group(1)}) from (select distinct {m.group(1)} from {m.group(3)} where {m.group(4)}) d"
    q = re.sub(r"string_agg\(([^,]+), '(.)' order by ([^)]+)\)", r"string_agg(\1, '\2') within group (order by \3)", q)
    m = re.match(r'(?is)select (.*?) from (.*) limit 1\s*$', q)
    if m:
        q = f'select top 1 {m.group(1)} from {m.group(2)}'
    m = re.match(r'(?is)select (.*?) from (.*)$', q)
    if m and '||' in m.group(1):
        q = 'select concat(' + ', '.join(p.strip() for p in m.group(1).split('||')) + ') from ' + m.group(2)
    m = re.match(r'(?is)select (\w+ is (?:not )?null) from (.*)$', q)
    if m:
        q = f"select case when {m.group(1)} then 't' else 'f' end from {m.group(2)}"
    if 'plpgsql' in q and 'zz_boom' in q:
        q = ("create trigger zz_boom on departments after insert as "
             "if exists (select 1 from inserted where code = 'ZZBOOM') throw 50000, 'zz boom from trigger', 1;")
    elif q.startswith('drop trigger if exists zz_boom'):
        q = 'drop trigger if exists zz_boom'
    return q


def sql(q):
    if not MSSQL:
        return subprocess.run(['psql', '-h', os.environ.get('PGHOST', '127.0.0.1'), '-U', os.environ.get('PGUSER', 'inventory'), '-d', os.environ.get('PGDATABASE', 'inventory'), '-Atc', q],
                              capture_output=True, text=True, check=True).stdout.strip()
    t = translate(q)
    r = subprocess.run(['docker', 'exec', os.environ.get('PAMS_MSSQL_CONTAINER', 'pams-mssql'), '/opt/mssql-tools18/bin/sqlcmd', '-C', '-S', 'localhost', '-U', 'sa', '-P', _pw('MSSQL_SA_PASSWORD'),
                        '-d', os.environ.get('PAMS_MSSQL_DB', 'pams_test'), '-b', '-I', '-h', '-1', '-W', '-s', '|', '-Q', 'set nocount on; ' + t], capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f'sqlcmd failed for {t!r}: {r.stdout}{r.stderr}')
    lines = [l for l in r.stdout.splitlines() if l.strip() != '']
    return '\n'.join('' if l == 'NULL' else l for l in lines).strip()