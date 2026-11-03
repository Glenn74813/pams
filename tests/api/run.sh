#!/bin/bash
# Runs the API suites against a backend on a disposable database, wiping its data before each suite.
#   PostgreSQL:  PAMS_API=http://127.0.0.1:8097/api tests/api/run.sh [suite ...]
#   SQL Server:  PAMS_DB=mssql PAMS_API=http://127.0.0.1:18099/api tests/api/run.sh [suite ...]
# Refuses to run against the usual database ports, so it cannot wipe working data by mistake.
set -uo pipefail
cd "$(dirname "$0")"
: "${PAMS_API:?Set PAMS_API to the test backend, e.g. http://127.0.0.1:8097/api}"
export PGPORT=${PGPORT:-55432} PAMS_MSSQL_DB=${PAMS_MSSQL_DB:-pams_test}
if [ "${PAMS_DB:-}" = mssql ]; then
  [ "$PAMS_MSSQL_DB" = pams ] && { echo "Refusing to wipe the working SQL Server database 'pams'."; exit 2; }
else
  [ "$PGPORT" = 5432 ] && { echo "Refusing to wipe the PostgreSQL database on port 5432."; exit 2; }
fi
case "$PAMS_API" in *:8095/*|*:8096/*) echo "Refusing to test the working backends on 8095/8096."; exit 2;; esac
ENV=../../backend-java/.env
export PGPASSWORD=$(grep ^DB_PASSWORD "$ENV" | cut -d= -f2-)
SA=$(grep ^MSSQL_SA_PASSWORD "$ENV" | cut -d= -f2-)
sqlfile() {
  if [ "${PAMS_DB:-}" = mssql ]; then
    docker cp "$1" pams-mssql:/tmp/pams-test.sql >/dev/null
    docker exec pams-mssql /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$SA" -d "$PAMS_MSSQL_DB" -b -I -i /tmp/pams-test.sql >/dev/null
  else
    psql -h 127.0.0.1 -U inventory -d inventory -q -f "$1" >/dev/null 2>&1
  fi
}
db=$([ "${PAMS_DB:-}" = mssql ] && echo mssql || echo pg)
sqlfile sql/fixtures_$db.sql
suites=${*:-rfq_test bid_test po_test con_test sup_test ps_test proc_test iss_test asset_test asm_test fix_test fix2_test tor_test period_test gaps_test cnas_test}
mkdir -p out  # also made by callers that redirect into it
export PYTHONPATH=$PWD
status=0
for t in $suites; do
  sqlfile sql/wipe_$db.sql
  timeout 900 python3 suites/$t.py > out/${db}_$t.txt 2>&1; code=$?
  pass=$(grep -c '^PASS' out/${db}_$t.txt); fail=$(grep -c '^FAIL ' out/${db}_$t.txt)
  echo "$t: $pass passed, $fail failed$([ $code -ne 0 ] && echo ", stopped (exit $code)")"
  [ $fail -ne 0 ] || [ $code -ne 0 ] && status=1
done
sqlfile sql/wipe_$db.sql
exit $status
